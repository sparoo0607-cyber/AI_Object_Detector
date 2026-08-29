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

enum class VisionAiState {
    LISTENING,
    CAPTURING,
    ANALYZING,
    RESPONDING,
    ERROR
}

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
    fun onSignModeStateChanged(isActive: Boolean)
    fun onSignSentenceUpdated(sentence: String, latestWord: String)
    fun onAiStateChanged(state: VisionAiState, message: String, detail: String)
}

/**
 * Master Vision Orchestrator for Category 1: Vision Assist.
 * Coordinates Local Real-Time AI (SSD Object Detection, Fire & Danger Radar, Sign Language, OCR)
 * and Hybrid Cloud AI (Google Gemini Multimodal Visual Reasoning Engine).
 * Supports Dedicated "Sign Mode" for continuous gesture-to-sentence translation.
 */
class VisionOrchestrator(
    private val context: Context,
    private val uiCallback: VisionUiCallback
) : ObjectDetectionListener,
    DangerDetectionListener,
    SignLanguageListener,
    TextReaderListener,
    LiveSpeechListener,
    SignSentenceListener {

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
    private val signEngine = SignLanguageEngine(context, this)
    val textEngine = TextReaderEngine(this)
    val voiceConfirmSpeechEngine = SpeechRecognitionEngine(context, this)
    val geminiVisionEngine = GeminiVisionEngine(context)
    val signSentenceBuilder = SignSentenceBuilder(this)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isWaitingForVoiceConfirmation = false
    private var isCurrentlyReadingText = false
    private var pendingOcrTextToRead = ""
    private var isAiProcessing = false

    // State cache
    private var lastOcrBlocks: List<ExtractedTextBlock> = emptyList()
    private var lastActiveSign: SignDetection? = null
    private var lastDetectionResults: List<DetectionResult> = emptyList()
    private var latestFrameBitmap: Bitmap? = null

    init {
        // Start continuous microphone listening for voice commands and AI questions
        startListening()
    }

    fun startListening() {
        try {
            voiceConfirmSpeechEngine.startContinuousListening(java.util.Locale.US)
            uiCallback.onAiStateChanged(
                VisionAiState.LISTENING,
                "🎙️ Listening...",
                "Ask \"What do you see?\" or any question"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Voice command recognizer init: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            voiceConfirmSpeechEngine.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Voice command recognizer stop: ${e.message}")
        }
    }

    /**
     * Camera frame processing pipeline.
     */
    fun processCameraFrame(imageProxy: ImageProxy) {
        val frameBitmap = imageProxy.toBitmap()
        latestFrameBitmap = frameBitmap
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val shouldObject = inferenceScheduler.shouldRunObjectDetection()
        val shouldOcr = inferenceScheduler.shouldRunOcr()
        val shouldSign = inferenceScheduler.shouldRunSignDetection()

        // In dedicated Sign Mode, give highest priority to Sign Language
        if (signSentenceBuilder.isSignModeActive) {
            if (frameBitmap != null) {
                signEngine.analyzeHandGestures(frameBitmap, lastDetectionResults)
            }
            if (shouldObject) {
                objectEngine.processFrame(imageProxy)
            } else {
                imageProxy.close()
            }
            return
        }

        // Standard Vision Assist Multi-Perception Pipeline
        if (shouldObject) {
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

        // 1. Danger & Hazard check (Highest Priority: Fire, Vehicles, Obstacles)
        dangerEngine.analyzeHazards(results, latestFrameBitmap)

        // In Sign Mode, suppress regular object announcements (chairs, tables, etc.)
        if (!signSentenceBuilder.isSignModeActive) {
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

    // --- Danger & Hazard Listener ---
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

        if (signSentenceBuilder.isSignModeActive) {
            // Feed to Sentence Builder
            signSentenceBuilder.processSignDetection(signDetection)
        } else {
            if (!isCurrentlyReadingText) {
                announcementManager.postEvent(event)
            }
        }
    }

    // --- SignSentenceListener Callbacks ---
    override fun onSentenceUpdated(sentence: String, latestWord: String) {
        if (latestWord.isNotBlank()) {
            hapticManager.playNormalPulse()
        }
        uiCallback.onSignSentenceUpdated(sentence, latestWord)
    }

    override fun onSentenceSpoken(sentence: String) {
        val event = PerceptionEvent(
            type = PerceptionType.SIGN,
            label = "Sign Sentence",
            spokenText = sentence,
            confidence = 0.96f,
            priority = EventPriority.SIGN
        )
        uiCallback.onLiveAnnouncement(event)
        ttsManager.speak(sentence, interrupt = true)
    }

    /**
     * Toggles Sign Mode (via UI button, volume double-click, or voice command).
     */
    fun toggleSignMode(): Boolean {
        val newState = signSentenceBuilder.toggleSignMode()
        if (newState) {
            hapticManager.playDangerPattern()
            ttsManager.speak("Sign mode activated. Translating gestures into sentences.", interrupt = true)
        } else {
            hapticManager.playNormalPulse()
            ttsManager.speak("Standard vision assist mode.", interrupt = true)
        }
        uiCallback.onSignModeStateChanged(newState)
        return newState
    }

    fun setSignMode(active: Boolean): Boolean {
        val newState = signSentenceBuilder.setSignMode(active)
        if (newState) {
            hapticManager.playDangerPattern()
            ttsManager.speak("Sign mode activated. Translating gestures into sentences.", interrupt = true)
        } else {
            hapticManager.playNormalPulse()
            ttsManager.speak("Standard vision assist mode.", interrupt = true)
        }
        uiCallback.onSignModeStateChanged(newState)
        return newState
    }

    // --- OCR Text Listener ---
    override fun onTextDiscovered(
        blocks: List<ExtractedTextBlock>,
        fullText: String,
        isNewContent: Boolean
    ) {
        lastOcrBlocks = blocks
        if (signSentenceBuilder.isSignModeActive) return

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
        if (isCurrentlyReadingText || signSentenceBuilder.isSignModeActive) return

        isWaitingForVoiceConfirmation = true
        hapticManager.playTextCapturePulse()

        val prompt = "Text detected. Would you like me to read it?"
        uiCallback.onVoiceConfirmationState(true, prompt)

        ttsManager.speak(prompt, interrupt = true)

        mainHandler.postDelayed({
            if (isWaitingForVoiceConfirmation && !isCurrentlyReadingText) {
                voiceConfirmSpeechEngine.startContinuousListening()
            }
        }, 2200)

        mainHandler.postDelayed({
            if (isWaitingForVoiceConfirmation && !isCurrentlyReadingText) {
                cancelVoiceConfirmation()
            }
        }, 6500)
    }

    fun readTextImmediately() {
        val text = if (pendingOcrTextToRead.isNotBlank()) pendingOcrTextToRead else textEngine.cachedFullText
        if (text.isNotBlank()) {
            isWaitingForVoiceConfirmation = false
            isCurrentlyReadingText = true
            uiCallback.onVoiceConfirmationState(false, "")

            textEngine.textProcessor.markTextAsRead(text)
            hapticManager.playTextCapturePulse()
            ttsManager.speak(text, interrupt = true)

            val event = PerceptionEvent(
                type = PerceptionType.TEXT,
                label = "OCR Text",
                spokenText = text,
                confidence = 0.95f,
                priority = EventPriority.TEXT
            )
            uiCallback.onLiveAnnouncement(event)

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
        uiCallback.onVoiceConfirmationState(false, "")
        if (pendingOcrTextToRead.isNotBlank()) {
            textEngine.textProcessor.markTextAsRead(pendingOcrTextToRead)
        }
        pendingOcrTextToRead = ""
    }

    /**
     * User-initiated or Voice-triggered Visual Q&A powered by the REAL Google Gemini Multimodal API.
     * Flow:
     * 1. Show CAPTURING ("📷 Capturing scene...")
     * 2. Capture a fresh camera frame
     * 3. Show ANALYZING ("🤖 Analyzing with AI...")
     * 4. Send image + user question to Google Gemini
     * 5. Show RESPONDING ("🔊 Speaking...") and speak answer aloud via TTS
     * 6. Automatically resume LISTENING ("🎙️ Listening...") when TTS finishes.
     */
    fun askGeminiVisionWithPrompt(promptText: String = "Describe what you see in the scene.") {
        if (isAiProcessing) {
            Log.d(TAG, "Gemini Vision request dropped: already processing an active request.")
            return
        }

        isAiProcessing = true

        // 1. CAPTURING STATE
        uiCallback.onAiStateChanged(
            VisionAiState.CAPTURING,
            "📷 Capturing scene...",
            "Taking fresh camera frame"
        )
        hapticManager.playNormalPulse()

        // Temporarily pause continuous speech listening so mic does not pick up TTS speech or background noise
        stopListening()

        // Allow CameraX analyzer a brief moment to commit the freshest frame
        mainHandler.postDelayed({
            val freshBitmap = latestFrameBitmap
            if (freshBitmap == null) {
                isAiProcessing = false
                uiCallback.onAiStateChanged(
                    VisionAiState.ERROR,
                    "⚠️ I couldn't analyze the scene. Please try again.",
                    "AI scene analysis is currently unavailable."
                )
                ttsManager.speak("AI scene analysis is currently unavailable.", interrupt = true) {
                    resumeListeningState()
                }
                return@postDelayed
            }

            // 2. ANALYZING STATE
            uiCallback.onAiStateChanged(
                VisionAiState.ANALYZING,
                "🤖 Analyzing with AI...",
                "Google Gemini Multimodal Analysis"
            )

            val cleanedQuestion = cleanAiQuery(promptText)
            Log.d(TAG, "Sending prompt to Gemini Vision: \"$cleanedQuestion\"")

            geminiVisionEngine.askGeminiVision(freshBitmap, cleanedQuestion) { answer, isHazard, isSuccess ->
                if (!isSuccess || answer.isBlank()) {
                    isAiProcessing = false
                    uiCallback.onAiStateChanged(
                        VisionAiState.ERROR,
                        "⚠️ I couldn't analyze the scene. Please try again.",
                        "AI scene analysis is currently unavailable."
                    )
                    ttsManager.speak("AI scene analysis is currently unavailable.", interrupt = true) {
                        resumeListeningState()
                    }
                    return@askGeminiVision
                }

                // 3. RESPONDING STATE
                if (isHazard) {
                    hapticManager.playDangerPattern()
                } else {
                    hapticManager.playNormalPulse()
                }

                uiCallback.onAiStateChanged(
                    VisionAiState.RESPONDING,
                    "🔊 Speaking...",
                    answer
                )

                val event = PerceptionEvent(
                    type = if (isHazard) PerceptionType.DANGER else PerceptionType.OBJECT,
                    label = if (isHazard) "AI Hazard Alert" else "AI Scene Description",
                    spokenText = answer,
                    confidence = 0.98f,
                    priority = if (isHazard) EventPriority.CRITICAL else EventPriority.OBJECT
                )
                uiCallback.onLiveAnnouncement(event)

                // 4. Speak response via TTS and resume listening when completed
                ttsManager.speak(answer, interrupt = true) {
                    isAiProcessing = false
                    resumeListeningState()
                }
            }
        }, 120)
    }

    fun resumeListeningState() {
        if (!signSentenceBuilder.isSignModeActive) {
            startListening()
        }
    }

    // --- Live Speech Listener for Voice Confirmation & Voice Commands ---
    override fun onSpeechRecognized(text: String) {
        if (isAiProcessing || isCurrentlyReadingText) return

        val normalized = text.lowercase().trim()
        Log.d(TAG, "Speech recognized in Vision Assist: \"$text\" (normalized: \"$normalized\")")

        // 1. Voice Command: Sign Mode Toggle
        if (normalized.contains("turn on sign mode") || normalized.contains("turn on the sign mode") ||
            normalized.contains("start sign mode") || normalized.contains("sign mode") ||
            normalized.contains("open sign language") || normalized.contains("enable sign mode")) {
            setSignMode(true)
            return
        }

        if (normalized.contains("turn off sign mode") || normalized.contains("turn off the sign mode") ||
            normalized.contains("stop sign mode") || normalized.contains("exit sign mode") ||
            normalized.contains("disable sign mode")) {
            setSignMode(false)
            return
        }

        // 2. Voice Command: OCR Confirmation
        if (isWaitingForVoiceConfirmation) {
            if (normalized.contains("yes") || normalized.contains("read") || normalized.contains("sure") ||
                normalized.contains("please") || normalized.contains("okay") || normalized.contains("ok")) {
                readTextImmediately()
            } else if (normalized.contains("no") || normalized.contains("cancel") || normalized.contains("stop")) {
                cancelVoiceConfirmation()
            }
            return
        }

        // 3. AI Voice Command & Visual Q&A Intent Detection
        if (isAiVisionIntent(normalized)) {
            askGeminiVisionWithPrompt(text)
        }
    }

    private fun isAiVisionIntent(text: String): Boolean {
        val norm = text.lowercase().trim()

        if (norm.startsWith("ask ai") || norm.startsWith("ai ") || norm.startsWith("hey ai") ||
            norm.startsWith("ask gemini") || norm.startsWith("sahey") || norm == "ask ai") {
            return true
        }

        val triggers = listOf(
            "what do you see",
            "what can you see",
            "describe this",
            "describe the scene",
            "describe what is in front",
            "describe what's in front",
            "explain what is in front",
            "explain what's in front",
            "tell me what is visible",
            "what is this",
            "what is that",
            "read this",
            "read that",
            "what does this say",
            "what does that say",
            "read that sign",
            "read this sign",
            "what is in front of me",
            "what's in front of me",
            "what is around me",
            "what's around me",
            "what is on the left",
            "what is on the right",
            "what is on the table",
            "is that dangerous",
            "is there any danger",
            "what color is",
            "is there anyone",
            "is anyone in front",
            "tell me more"
        )

        for (trigger in triggers) {
            if (norm.contains(trigger)) return true
        }

        if (norm.startsWith("what is") || norm.startsWith("what are") || norm.startsWith("who is") ||
            norm.startsWith("where is") || norm.startsWith("how many") || norm.startsWith("is there") ||
            norm.startsWith("can you read") || norm.startsWith("can you tell") || norm.startsWith("tell me about")) {
            return true
        }

        return false
    }

    private fun cleanAiQuery(text: String): String {
        var clean = text.trim()
        val lower = clean.lowercase()

        if (lower == "ask ai" || lower == "ai" || lower == "hey ai" || lower == "describe" || lower == "describe this") {
            return "Describe what is around the user in 1-2 concise sentences for a visually impaired person."
        }

        if (lower.startsWith("ask ai what do you see") || lower.startsWith("what do you see") || lower == "what do you see?") {
            return "Describe what you see in front of the user clearly and concisely."
        }

        if (lower.startsWith("ask ai") || lower.startsWith("hey ai")) {
            clean = clean.replace(Regex("^(?i)(ask ai|hey ai|sahey|gemini)[,:\\s]*"), "").trim()
        }

        if (clean.isBlank()) {
            return "Describe what is around the user in 1-2 concise sentences for a visually impaired person."
        }

        return clean
    }

    override fun onSpeechPartial(partialText: String) {
        val normalized = partialText.lowercase().trim()
        if (normalized.contains("turn on sign mode") || normalized.contains("start sign mode")) {
            setSignMode(true)
        } else if (normalized.contains("turn off sign mode") || normalized.contains("stop sign mode")) {
            setSignMode(false)
        }
    }

    override fun onRmsAudioLevel(rmsdB: Float) {}
    override fun onListeningStateChanged(isListening: Boolean) {}
    override fun onSpeechError(errorMessage: String) {}

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
        signEngine.close()
        textEngine.close()
        voiceConfirmSpeechEngine.shutdown()
        ttsManager.shutdown()
        signSentenceBuilder.reset()
    }

    companion object {
        private const val TAG = "VisionOrchestrator"
    }
}
