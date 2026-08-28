package com.accessibility.detector.sound

import android.content.Context
import android.os.SystemClock
import com.accessibility.detector.communication.LanguageDetector
import com.accessibility.detector.communication.SupportedLanguage
import com.accessibility.detector.communication.TranslationEngine
import com.accessibility.detector.communication.TranslationResult
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.HapticManager
import java.util.Locale

interface SoundOrchestratorCallback {
    fun onNewSoundEvent(event: SoundEvent)
    fun onLiveCaption(text: String, isPartial: Boolean)
    fun onTranslation(result: TranslationResult)
    fun onListeningStatus(isListening: Boolean, message: String)
    fun onAudioWaveLevel(level: Float)
}

/**
 * Sound & Language Orchestrator for Category 2: Sound & Language Assist.
 * Operates purely with Microphone / Audio input (NO CAMERA).
 * Handles continuous human voice recognition (Telugu Offline ASR / English), live captions,
 * ML Kit translations, and acoustic volume alerts (sirens, car horns, alarms).
 */
class SoundOrchestrator(
    private val context: Context,
    private val callback: SoundOrchestratorCallback
) : LiveSpeechListener, SoundAwarenessListener {

    val speechEngine = SpeechRecognitionEngine(context, this)
    val translationEngine = TranslationEngine(context)
    val languageDetector = LanguageDetector()
    val hapticManager = HapticManager(context)

    /** Real environmental-sound recognition (YAMNet, with heuristic fallback). */
    val soundAwarenessEngine = SoundAwarenessEngine(context, this)

    var inputSpeechLanguage: SupportedLanguage = SupportedLanguage.TELUGU
    var targetLanguage: SupportedLanguage = SupportedLanguage.ENGLISH
    var isLiveTranslationEnabled: Boolean = true
    /**
     * Environmental-sound recognition shares the microphone with speech recognition.
     * On some devices only one mic consumer can run at a time — disable this if live
     * captions stop working while sound alerts are on.
     */
    var isEnvironmentalSoundEnabled: Boolean = true
    var speechRecognitionMode: SpeechRecognitionMode = SpeechRecognitionMode.PREFER_OFFLINE

    private var lastLoudSpikeTime = 0L

    fun startSoundAssist(
        speechLang: SupportedLanguage = inputSpeechLanguage,
        mode: SpeechRecognitionMode = speechRecognitionMode
    ) {
        inputSpeechLanguage = speechLang
        speechRecognitionMode = mode

        val locale = when (speechLang) {
            SupportedLanguage.TELUGU -> Locale("te", "IN")
            SupportedLanguage.HINDI -> Locale("hi", "IN")
            SupportedLanguage.TAMIL -> Locale("ta", "IN")
            SupportedLanguage.KANNADA -> Locale("kn", "IN")
            SupportedLanguage.MALAYALAM -> Locale("ml", "IN")
            SupportedLanguage.SPANISH -> Locale("es", "ES")
            else -> Locale.US
        }

        speechEngine.startContinuousListening(locale, mode)
        if (isEnvironmentalSoundEnabled) {
            soundAwarenessEngine.startListening()
        }
        val modeText = if (mode == SpeechRecognitionMode.OFFLINE_ONLY) "Offline Mode" else "Listening"
        callback.onListeningStatus(true, "🎙️ $modeText (${speechLang.displayName})")
    }

    fun stopSoundAssist() {
        speechEngine.stopListening()
        soundAwarenessEngine.stopListening()
        callback.onListeningStatus(false, "Sound assist paused")
    }

    // --- Live Speech Listener Callbacks ---
    override fun onSpeechRecognized(text: String) {
        callback.onLiveCaption(text, isPartial = false)

        if (isLiveTranslationEnabled) {
            val detected = languageDetector.detectLanguage(text)
            val actualSource = if (inputSpeechLanguage != SupportedLanguage.AUTO) inputSpeechLanguage else detected

            translationEngine.translate(
                text = text,
                sourceLang = actualSource,
                targetLang = targetLanguage
            ) { translation ->
                callback.onTranslation(translation)
            }
        }
    }

    override fun onSpeechPartial(partialText: String) {
        callback.onLiveCaption(partialText, isPartial = true)
    }

    override fun onRmsAudioLevel(rmsdB: Float) {
        callback.onAudioWaveLevel(rmsdB)

        // Generic loudness spike is only a coarse fallback cue. When the YAMNet model is
        // active it does the real classification, so suppress the ambiguous spike alert.
        if (!soundAwarenessEngine.activeModeLabel.startsWith("YAMNet")) {
            val now = SystemClock.uptimeMillis()
            if (rmsdB > 9.5f && (now - lastLoudSpikeTime > 3000L)) {
                lastLoudSpikeTime = now
                onSoundEvent(
                    SoundEvent(
                        label = "Loud Sound",
                        icon = "🔊",
                        description = "Loud acoustic spike (${rmsdB.toInt()} dB) — source unverified.",
                        confidence = 0.55f,
                        priority = EventPriority.NAVIGATION
                    )
                )
            }
        }
    }

    // --- SoundAwarenessListener ---
    override fun onSoundEvent(event: SoundEvent) {
        // 1. Distinct tactile vibration pattern
        when (event.priority) {
            EventPriority.CRITICAL -> hapticManager.playCriticalSosPattern()
            EventPriority.DANGER -> hapticManager.playSoundAlertPattern()
            else -> hapticManager.playNormalPulse()
        }

        // 2. Pass to UI
        callback.onNewSoundEvent(event)
    }

    override fun onSoundEngineState(isActive: Boolean, message: String) {
        android.util.Log.d(TAG, "Sound engine: active=$isActive — $message")
    }

    override fun onListeningStateChanged(isListening: Boolean) {
        val statusMsg = if (isListening) {
            "Listening for ${inputSpeechLanguage.displayName} speech..."
        } else {
            "Restarting listener..."
        }
        callback.onListeningStatus(isListening, statusMsg)
    }

    override fun onSpeechError(errorMessage: String) {
        callback.onListeningStatus(false, errorMessage)
    }

    fun shutdown() {
        speechEngine.shutdown()
        soundAwarenessEngine.shutdown()
        translationEngine.close()
    }

    companion object {
        private const val TAG = "SoundOrchestrator"
    }
}
