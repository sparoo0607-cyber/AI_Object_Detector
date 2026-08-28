package com.accessibility.detector.core

import android.content.Context
import androidx.camera.core.ImageProxy
import com.accessibility.detector.audio.SoundAwarenessEngine
import com.accessibility.detector.audio.SoundAwarenessListener
import com.accessibility.detector.danger.DangerDetectionEngine
import com.accessibility.detector.danger.DangerDetectionListener
import com.accessibility.detector.detection.DetectionResult
import com.accessibility.detector.detection.EventPriority
import com.accessibility.detector.detection.ObjectDetectionEngine
import com.accessibility.detector.detection.ObjectDetectionListener
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType
import com.accessibility.detector.detection.ProximityLevel
import com.accessibility.detector.detection.SpatialPosition
import com.accessibility.detector.ocr.ExtractedTextBlock
import com.accessibility.detector.ocr.TextReaderEngine
import com.accessibility.detector.ocr.TextReaderListener
import com.accessibility.detector.sign.SignDetection
import com.accessibility.detector.sign.SignLanguageEngine
import com.accessibility.detector.sign.SignLanguageListener
import com.accessibility.detector.speech.SpeechEngine
import com.accessibility.detector.speech.SpeechRecognitionListener
import com.accessibility.detector.speech.TtsManager
import com.accessibility.detector.translation.SupportedLanguage
import com.accessibility.detector.translation.TranslationEngine
import com.accessibility.detector.translation.TranslationResult

interface OrchestratorUiCallback {
    fun onVisionResultsUpdated(
        objects: List<DetectionResult>,
        ocrBlocks: List<ExtractedTextBlock>,
        activeSign: SignDetection?,
        imageWidth: Int,
        imageHeight: Int
    )
    fun onLiveAnnouncement(event: PerceptionEvent)
    fun onSpeechStatus(isListening: Boolean, text: String?)
    fun onTranslationResult(result: TranslationResult)
    fun onSubsystemStatusChanged(module: String, status: SubsystemStatus)
}

/**
 * SAHEY Multimodal AI Orchestrator.
 * Unifies all 7 perception engines into an automated, context-aware assistive platform.
 */
class SaheyAIOrchestrator(
    private val context: Context,
    private val uiCallback: OrchestratorUiCallback
) : ObjectDetectionListener,
    DangerDetectionListener,
    TextReaderListener,
    SoundAwarenessListener,
    SignLanguageListener,
    SpeechRecognitionListener {

    val modelManager = ModelManager()
    val inferenceScheduler = InferenceScheduler()
    val hapticManager = HapticManager(context)
    val ttsManager = TtsManager(context, onInitStatus = { isReady ->
        if (isReady) {
            modelManager.updateStatus(ModelManager.MOD_SPEECH, SubsystemStatus.ACTIVE)
        } else {
            modelManager.updateStatus(ModelManager.MOD_SPEECH, SubsystemStatus.UNAVAILABLE)
        }
    })

    val announcementManager = AnnouncementManager(
        ttsManager = ttsManager,
        hapticManager = hapticManager,
        onAnnouncementDispatched = { event ->
            uiCallback.onLiveAnnouncement(event)
        }
    )

    // Engines
    private val objectEngine = ObjectDetectionEngine(context, this)
    private val dangerEngine = DangerDetectionEngine(this)
    private val textEngine = TextReaderEngine(this)
    private val soundEngine = SoundAwarenessEngine(context, this)
    private val signEngine = SignLanguageEngine(this)
    val speechEngine = SpeechEngine(context, this)
    val translationEngine = TranslationEngine()

    // Subsystem toggle flags (configurable via settings)
    var isObjectDetectionEnabled = true
    var isDangerDetectionEnabled = true
    var isOcrEnabled = true
    var isSignDetectionEnabled = true
    var isSoundAwarenessEnabled = true

    // State cache
    private var lastOcrBlocks: List<ExtractedTextBlock> = emptyList()
    private var lastActiveSign: SignDetection? = null

    init {
        modelManager.updateStatus(ModelManager.MOD_OBJECTS, SubsystemStatus.ACTIVE)
        modelManager.updateStatus(ModelManager.MOD_DANGER, SubsystemStatus.ACTIVE)
        modelManager.updateStatus(ModelManager.MOD_OCR, SubsystemStatus.ACTIVE)
        modelManager.updateStatus(ModelManager.MOD_SIGN, SubsystemStatus.ACTIVE)
        modelManager.updateStatus(ModelManager.MOD_SOUND, SubsystemStatus.STANDBY)
        modelManager.updateStatus(ModelManager.MOD_SPEECH, SubsystemStatus.ACTIVE)
        modelManager.updateStatus(ModelManager.MOD_TRANSLATE, SubsystemStatus.ACTIVE)
    }

    fun startSoundAwareness() {
        if (isSoundAwarenessEnabled) {
            soundEngine.startListening()
        }
    }

    fun stopSoundAwareness() {
        soundEngine.stopListening()
    }

    /**
     * Master Camera Frame Pipeline.
     * Routes camera frames to appropriate AI vision engines based on scheduler.
     */
    fun processCameraFrame(imageProxy: ImageProxy) {
        if (isObjectDetectionEnabled && inferenceScheduler.shouldRunObjectDetection()) {
            objectEngine.processFrame(imageProxy)
        } else {
            imageProxy.close()
        }
    }

    /**
     * Triggers OCR analysis explicitly on-demand or on scheduler.
     */
    fun triggerOcr(imageProxy: ImageProxy) {
        if (isOcrEnabled) {
            textEngine.processFrame(imageProxy)
        } else {
            imageProxy.close()
        }
    }

    // --- Object Detection Listener ---
    override fun onObjectsDetected(
        results: List<DetectionResult>,
        inferenceTimeMs: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        // 1. Analyze for Danger / Hazards
        if (isDangerDetectionEnabled) {
            dangerEngine.analyzeHazards(results)
        }

        // 2. Analyze for Sign Language
        if (isSignDetectionEnabled) {
            signEngine.analyzeSignLanguage(results)
        }

        // 3. Normal Object Announcement (if no hazard preempted)
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

        // 4. Update UI Overlay
        uiCallback.onVisionResultsUpdated(
            objects = results,
            ocrBlocks = lastOcrBlocks,
            activeSign = lastActiveSign,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }

    override fun onObjectDetectionError(error: String) {
        modelManager.updateStatus(ModelManager.MOD_OBJECTS, SubsystemStatus.UNAVAILABLE)
    }

    // --- Danger Detection Listener ---
    override fun onHazardDetected(hazardEvent: PerceptionEvent) {
        announcementManager.postEvent(hazardEvent)
    }

    // --- OCR Listener ---
    override fun onTextRecognized(blocks: List<ExtractedTextBlock>, event: PerceptionEvent?) {
        lastOcrBlocks = blocks
        if (event != null) {
            announcementManager.postEvent(event)
        }
    }

    override fun onTextReaderError(error: String) {
        modelManager.updateStatus(ModelManager.MOD_OCR, SubsystemStatus.UNAVAILABLE)
    }

    // --- Sign Language Listener ---
    override fun onSignDetected(event: PerceptionEvent, signDetection: SignDetection) {
        lastActiveSign = signDetection
        announcementManager.postEvent(event)
    }

    // --- Sound Awareness Listener ---
    override fun onSoundDetected(event: PerceptionEvent) {
        announcementManager.postEvent(event)
    }

    override fun onSoundEngineStatus(isActive: Boolean, message: String) {
        val status = if (isActive) SubsystemStatus.ACTIVE else SubsystemStatus.STANDBY
        modelManager.updateStatus(ModelManager.MOD_SOUND, status)
        uiCallback.onSubsystemStatusChanged(ModelManager.MOD_SOUND, status)
    }

    // --- Speech Recognition Listener ---
    override fun onSpeechResult(text: String) {
        uiCallback.onSpeechStatus(false, text)

        // Translate or process speech
        val translation = translationEngine.translate(
            text = text,
            sourceLang = SupportedLanguage.ENGLISH,
            targetLang = SupportedLanguage.TELUGU
        )
        uiCallback.onTranslationResult(translation)

        // Speak translated output
        val event = PerceptionEvent(
            type = PerceptionType.TRANSLATION,
            label = "Translation: ${translation.translatedText}",
            spokenText = translation.translatedText,
            confidence = 1.0f,
            priority = EventPriority.NAVIGATION
        )
        announcementManager.postEvent(event)
    }

    override fun onSpeechPartial(partialText: String) {
        uiCallback.onSpeechStatus(true, partialText)
    }

    override fun onSpeechListening(isListening: Boolean) {
        uiCallback.onSpeechStatus(isListening, null)
    }

    override fun onSpeechError(errorMessage: String) {
        uiCallback.onSpeechStatus(false, "Speech Error: $errorMessage")
    }

    fun setMute(muted: Boolean) {
        announcementManager.isMuted = muted
    }

    fun toggleSafetyShieldMode(): Boolean {
        val newState = !announcementManager.isSafetyShieldMode
        announcementManager.isSafetyShieldMode = newState
        if (newState) {
            hapticManager.playImportantPulse()
            ttsManager.speak("Safety mode activated. Monitoring hazards.")
        } else {
            hapticManager.playNormalPulse()
            ttsManager.speak("Standard live assist mode.")
        }
        return newState
    }

    fun shutdown() {
        objectEngine.shutdown()
        textEngine.close()
        soundEngine.stopListening()
        speechEngine.shutdown()
        ttsManager.shutdown()
    }

    companion object {
        private const val TAG = "SaheyAIOrchestrator"
    }
}
