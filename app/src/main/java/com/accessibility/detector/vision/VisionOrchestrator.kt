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
}

/**
 * Master Vision Orchestrator for Category 1: Vision Assist.
 * Coordinates Object Detection, Danger Radar, Sign Language, and OCR with Voice Confirmation.
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isWaitingForVoiceConfirmation = false
    private var pendingOcrTextToRead = ""

    // State cache
    private var lastOcrBlocks: List<ExtractedTextBlock> = emptyList()
    private var lastActiveSign: SignDetection? = null
    private var lastDetectionResults: List<DetectionResult> = emptyList()

    /**
     * Camera frame processing pipeline.
     */
    fun processCameraFrame(imageProxy: ImageProxy) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        var frameBitmap: Bitmap? = null
        val shouldOcr = inferenceScheduler.shouldRunOcr()
        val shouldSign = inferenceScheduler.shouldRunSignDetection()

        if (shouldOcr || shouldSign) {
            try {
                frameBitmap = imageProxy.toBitmap()
            } catch (e: Exception) {
                // Fallback
            }
        }

        // 1. Object & Hazard detection
        if (inferenceScheduler.shouldRunObjectDetection()) {
            objectEngine.processFrame(imageProxy)
        } else {
            imageProxy.close()
        }

        // 2. OCR Text detection
        if (frameBitmap != null && shouldOcr && !isWaitingForVoiceConfirmation) {
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

        // 1. Danger & Hazard check (Highest Priority)
        dangerEngine.analyzeHazards(results)

        // 2. Normal Object Announcement
        if (results.isNotEmpty()) {
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

    // --- Sign Language Listener ---
    override fun onSignDetected(event: PerceptionEvent, signDetection: SignDetection) {
        lastActiveSign = signDetection
        announcementManager.postEvent(event)
    }

    // --- OCR Text Listener ---
    override fun onTextDiscovered(
        blocks: List<ExtractedTextBlock>,
        fullText: String,
        isNewContent: Boolean
    ) {
        lastOcrBlocks = blocks
        if (isNewContent && !isWaitingForVoiceConfirmation) {
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
        isWaitingForVoiceConfirmation = true
        hapticManager.playTextCapturePulse()

        val prompt = "Text detected. Would you like me to read it?"
        uiCallback.onVoiceConfirmationState(true, prompt)

        ttsManager.speak(prompt, interrupt = true)

        // After speech prompt finishes, listen for "Yes" / "Read"
        mainHandler.postDelayed({
            if (isWaitingForVoiceConfirmation) {
                voiceConfirmSpeechEngine.startListening()
            }
        }, 2200)

        // Timeout fallback after 6 seconds
        mainHandler.postDelayed({
            if (isWaitingForVoiceConfirmation) {
                cancelVoiceConfirmation()
            }
        }, 6500)
    }

    fun readTextImmediately() {
        val text = if (pendingOcrTextToRead.isNotBlank()) pendingOcrTextToRead else textEngine.cachedFullText
        if (text.isNotBlank()) {
            isWaitingForVoiceConfirmation = false
            voiceConfirmSpeechEngine.stopListening()
            uiCallback.onVoiceConfirmationState(false, "")

            val event = PerceptionEvent(
                type = PerceptionType.TEXT,
                label = "OCR Text",
                spokenText = text,
                confidence = 0.95f,
                priority = EventPriority.TEXT
            )
            announcementManager.postEvent(event)
        }
    }

    private fun cancelVoiceConfirmation() {
        isWaitingForVoiceConfirmation = false
        voiceConfirmSpeechEngine.stopListening()
        uiCallback.onVoiceConfirmationState(false, "")
    }

    // --- Live Speech Listener for Voice Confirmation ---
    override fun onSpeechRecognized(text: String) {
        val normalized = text.lowercase().trim()
        if (isWaitingForVoiceConfirmation) {
            if (normalized.contains("yes") || normalized.contains("read") ||
                normalized.contains("yeah") || normalized.contains("sure") ||
                normalized.contains("ok") || normalized.contains("చదువు")
            ) {
                readTextImmediately()
            } else if (normalized.contains("no") || normalized.contains("stop") || normalized.contains("వద్దు")) {
                cancelVoiceConfirmation()
            }
        }
    }

    override fun onSpeechPartial(partialText: String) {
        val normalized = partialText.lowercase().trim()
        if (isWaitingForVoiceConfirmation && (normalized.contains("yes") || normalized.contains("read"))) {
            readTextImmediately()
        }
    }

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
