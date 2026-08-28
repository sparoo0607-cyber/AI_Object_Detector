package com.accessibility.detector.vision

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.ImageProxy
import com.accessibility.detector.communication.TtsManager
import com.accessibility.detector.core.AnnouncementManager
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.HapticManager
import com.accessibility.detector.core.InferenceScheduler
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType
import com.accessibility.detector.core.SpatialPosition
import com.accessibility.detector.sound.LiveSpeechListener
import com.accessibility.detector.sound.SpeechRecognitionEngine
import com.accessibility.detector.vision.gemini.GeminiVisionEngine

interface VisionUiCallback {
    fun onVisionResultsUpdated(
        objects: List<DetectionResult>,
        ocrBlocks: List<ExtractedTextBlock>,
        activeSign: SignDetection?,
        imageWidth: Int,
        imageHeight: Int
    )
    fun onLiveAnnouncement(event: PerceptionEvent)
    fun onVoiceConfirmationState(isWaitingForConfirmation: Boolean, prompt: String)
    fun onGeminiReasoningStatus(isAnalyzing: Boolean, statusMessage: String)
}

/**
 * Master Vision Orchestrator for Category 1: Vision Assist.
 * Coordinates Local Real-Time AI (SSD Object Detection, Fire & Danger Radar, Sign Language, OCR)
 * and Hybrid Cloud AI (Gemini Multimodal Visual Reasoning Engine).
 */
class VisionOrchestrator(
    private val context: Context,
    private val uiCallback: VisionUiCallback
) : ObjectDetectionListener,
    DangerDetectionListener,
    SignLanguageListener,
    TextReaderListener,
    LiveSpeechListener {

    val hapticManager = HapticManager(context)
    val inferenceScheduler = InferenceScheduler()
    val ttsManager = TtsManager(context)
    val announcementManager = AnnouncementManager(
        ttsManager = ttsManager,
        hapticManager = hapticManager,
        onAnnouncementDispatched = { event ->
            uiCallback.onLiveAnnouncement(event)
        }
    )

    private val objectEngine = ObjectDetectionEngine(context, this)
    private val dangerEngine = DangerDetectionEngine(this)
    private val signEngine = SignLanguageEngine(this)
    val textEngine = TextReaderEngine(this)
    val voiceConfirmSpeechEngine = SpeechRecognitionEngine(context, this)
    val geminiVisionEngine = GeminiVisionEngine(context)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isWaitingForVoiceConfirmation = false
    private var isCurrentlyReadingText = false
    private var pendingOcrTextToRead = ""

    // State cache
    private var lastOcrBlocks: List<ExtractedTextBlock> = emptyList()
    private var lastActiveSign: SignDetection? = null
    private var lastDetectionResults: List<DetectionResult> = emptyList()
    private var latestFrameBitmap: Bitmap? = null

    /**
     * Camera frame processing pipeline.
     */
    fun processCameraFrame(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        var frameBitmap: Bitmap? = null
        val shouldOcr = inferenceScheduler.shouldRunOcr()
        val shouldSign = inferenceScheduler.shouldRunSignDetection()

        try {
            frameBitmap = imageProxy.toBitmap()
            latestFrameBitmap = frameBitmap
        } catch (e: Exception) {
            // Fallback
        }

        // 1. Local Object & Hazard detection
        if (inferenceScheduler.shouldRunObjectDetection()) {
            objectEngine.processFrame(imageProxy)
        } else {
            imageProxy.close()
        }

        // 2. OCR Text detection (Paused while reading or waiting for confirmation)
        if (frameBitmap != null && shouldOcr && !isWaitingForVoiceConfirmation && !isCurrentlyReadingText) {
            textEngine.processBitmap(frameBitmap, rotationDegrees)
        }

        // 3. Sign Language gesture detection
        if (frameBitmap != null && shouldSign) {
            signEngine.analyzeHandGestures(frameBitmap, lastDetectionResults)
        }
    }

    // --- Object Detection Listener ---
    override fun onObjectsDetected(
        results: List<DetectionResult>,
        inferenceTimeMs: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        lastDetectionResults = results

        // 1. Danger & Hazard check (Highest Priority: Real Fire, Fire on Screen, Vehicles, Slippery Floors, Obstacles)
        dangerEngine.analyzeHazards(results, latestFrameBitmap)

        // 2. Normal Object Announcement (if not actively reading text and no critical hazard)
        if (results.isNotEmpty() && !isCurrentlyReadingText && !isWaitingForVoiceConfirmation) {
            val primary = results.maxByOrNull { it.score }
            if (primary != null) {
                val dirPhrase = when (primary.spatialPosition) {
                    SpatialPosition.LEFT -> "on your left"
                    SpatialPosition.RIGHT -> "on your right"
                    SpatialPosition.CENTER -> "ahead"
                    SpatialPosition.UNKNOWN -> ""
                }
                val spoken = if (dirPhrase.isNotBlank()) "${primary.label} $dirPhrase." else primary.label

                val event = PerceptionEvent(
                    type = PerceptionType.OBJECT,
                    label = primary.label,
                    spokenText = spoken,
                    confidence = primary.score,
                    priority = EventPriority.OBJECT,
                    spatialPosition = primary.spatialPosition,
                    proximity = primary.proximity
                )
                announcementManager.postEvent(event)
            }
        }

        // 3. Update Overlay
        uiCallback.onVisionResultsUpdated(
            objects = results,
            ocrBlocks = lastOcrBlocks,
            activeSign = lastActiveSign,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    override fun onObjectDetectionError(error: String) {
        Log.w(TAG, "Object detection error: $error")
    }

    // --- Danger Detection Listener ---
    override fun onHazardDetected(hazardEvent: PerceptionEvent) {
        announcementManager.postEvent(hazardEvent)
    }

    override fun onPotentialHazardPreFiltered(hazardHint: String, isScreenFire: Boolean) {
        val bitmap = latestFrameBitmap ?: return
        geminiVisionEngine.analyzeSuspiciousFrame(bitmap, hazardHint, isScreenFire) { geminiEvent ->
            if (geminiEvent != null) {
                announcementManager.postEvent(geminiEvent)
            }
        }
    }

    // --- Sign Language Listener ---
    override fun onSignDetected(event: PerceptionEvent, signDetection: SignDetection) {
        lastActiveSign = signDetection
        if (!isCurrentlyReadingText) {
            announcementManager.postEvent(event)
        }
    }

    // --- OCR Text Listener ---
    override fun onTextDiscovered(
        blocks: List<ExtractedTextBlock>,
        fullText: String,
        isNewContent: Boolean
    ) {
        lastOcrBlocks = blocks
        if (isNewContent && !isWaitingForVoiceConfirmation && !isCurrentlyReadingText) {
            pendingOcrTextToRead = fullText
            promptVoiceConfirmation()
        }
    }

    override fun onTextReaderError(error: String) {
        Log.w(TAG, "Text reader error: $error")
    }

    /**
     * Starts the interactive Voice Confirmation Flow for reading text.
     */
    private fun promptVoiceConfirmation() {
        if (isCurrentlyReadingText) return

        isWaitingForVoiceConfirmation = true
        hapticManager.playTextCapturePulse()

        val prompt = "Text detected. Would you like me to read it?"
        uiCallback.onVoiceConfirmationState(true, prompt)

        ttsManager.speak(prompt, interrupt = true)

        // After speech prompt finishes, listen for "Yes" / "Read"
        mainHandler.postDelayed({
            if (isWaitingForVoiceConfirmation && !isCurrentlyReadingText) {
                voiceConfirmSpeechEngine.startContinuousListening()
            }
        }, 2200)

        // Timeout fallback after 6 seconds
        mainHandler.postDelayed({
            if (isWaitingForVoiceConfirmation && !isCurrentlyReadingText) {
                cancelVoiceConfirmation()
            }
        }, 6500)
    }

    /**
     * Reads the detected text out loud immediately without being interrupted by re-prompts.
     */
    fun readTextImmediately() {
        val text = if (pendingOcrTextToRead.isNotBlank()) pendingOcrTextToRead else textEngine.cachedFullText
        if (text.isNotBlank()) {
            isWaitingForVoiceConfirmation = false
            isCurrentlyReadingText = true
            voiceConfirmSpeechEngine.stopListening()
            uiCallback.onVoiceConfirmationState(false, "")

            // Mark this text signature in the engine so it does not immediately re-prompt
            textEngine.textProcessor.markTextAsRead(text)

            // Play tactile feedback
            hapticManager.playTextCapturePulse()

            // Announce via TTS directly with interrupt to override prompt
            ttsManager.speak(text, interrupt = true)

            // Update UI card
            val event = PerceptionEvent(
                type = PerceptionType.TEXT,
                label = "OCR Text",
                spokenText = text,
                confidence = 0.95f,
                priority = EventPriority.TEXT
            )
            uiCallback.onLiveAnnouncement(event)

            // Calculate reading duration estimate (approx 120 words per minute + 3 sec buffer)
            val wordCount = text.split(Regex("\\s+")).size
            val readingDurationMs = maxOf(4000L, (wordCount * 450L) + 2000L)

            mainHandler.postDelayed({
                isCurrentlyReadingText = false
                pendingOcrTextToRead = ""
            }, readingDurationMs)
        }
    }

    fun cancelVoiceConfirmation() {
        isWaitingForVoiceConfirmation = false
        voiceConfirmSpeechEngine.stopListening()
        uiCallback.onVoiceConfirmationState(false, "")
        if (pendingOcrTextToRead.isNotBlank()) {
            textEngine.textProcessor.markTextAsRead(pendingOcrTextToRead)
        }
        pendingOcrTextToRead = ""
    }

    /**
     * User-initiated "Ask AI" triggered via button or voice query ("What is around me?").
     */
    fun askGeminiWhatIsAroundMe(onFinished: ((String) -> Unit)? = null) {
        val bitmap = latestFrameBitmap
        if (bitmap == null) {
            val fallbackMsg = "Scanning environment in front of you."
            ttsManager.speak(fallbackMsg)
            onFinished?.invoke(fallbackMsg)
            return
        }

        uiCallback.onGeminiReasoningStatus(true, "Gemini AI is analyzing the scene...")
        ttsManager.speak("Analyzing what is around you...")

        geminiVisionEngine.askAi(bitmap, "Describe the scene concisely for a visually impaired user in 1-2 sentences.") { event ->
            uiCallback.onGeminiReasoningStatus(false, "")
            if (event != null) {
                announcementManager.postEvent(event)
                onFinished?.invoke(event.spokenText)
            } else {
                val localSummary = if (lastDetectionResults.isNotEmpty()) {
                    val labels = lastDetectionResults.take(3).joinToString(", ") { it.label }
                    "In front of you: $labels."
                } else {
                    "Scene is clear ahead."
                }
                ttsManager.speak(localSummary)
                onFinished?.invoke(localSummary)
            }
        }
    }

    // --- Live Speech Listener for Voice Confirmation & Voice Commands ---
    override fun onSpeechRecognized(text: String) {
        val normalized = text.lowercase().trim()

        if (isWaitingForVoiceConfirmation && !isCurrentlyReadingText) {
            if (normalized.contains("yes") || normalized.contains("read") ||
                normalized.contains("yeah") || normalized.contains("sure") ||
                normalized.contains("ok") || normalized.contains("చదువు")
            ) {
                readTextImmediately()
                return
            } else if (normalized.contains("no") || normalized.contains("stop") || normalized.contains("వద్దు")) {
                cancelVoiceConfirmation()
                return
            }
        }

        // Voice Command: "What is around me?" or "Is there any danger?"
        if (!isCurrentlyReadingText && (
            normalized.contains("what is around") || normalized.contains("what's around") ||
            normalized.contains("describe") || normalized.contains("danger") ||
            normalized.contains("చుట్టూ ఏమి ఉంది")
        )) {
            askGeminiWhatIsAroundMe()
        }
    }

    override fun onSpeechPartial(partialText: String) {
        val normalized = partialText.lowercase().trim()
        if (isWaitingForVoiceConfirmation && !isCurrentlyReadingText && (normalized.contains("yes") || normalized.contains("read"))) {
            readTextImmediately()
        }
    }

    override fun onRmsAudioLevel(rmsdB: Float) {}

    override fun onListeningStateChanged(isListening: Boolean) {}

    override fun onSpeechError(errorMessage: String) {
        if (isWaitingForVoiceConfirmation) {
            // Keep waiting until timeout
        }
    }

    fun setMute(muted: Boolean) {
        announcementManager.isMuted = muted
    }

    fun toggleSafetyShieldMode(): Boolean {
        val newState = !announcementManager.isSafetyShieldMode
        announcementManager.isSafetyShieldMode = newState
        if (newState) {
            hapticManager.playDangerPattern()
            ttsManager.speak("Safety mode activated. Monitoring hazards.")
        } else {
            hapticManager.playNormalPulse()
            ttsManager.speak("Standard vision assist mode.")
        }
        return newState
    }

    fun shutdown() {
        objectEngine.shutdown()
        textEngine.close()
        voiceConfirmSpeechEngine.shutdown()
        ttsManager.shutdown()
    }

    companion object {
        private const val TAG = "VisionOrchestrator"
    }
}
