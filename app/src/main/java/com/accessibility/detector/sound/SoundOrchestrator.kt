package com.accessibility.detector.sound

import android.content.Context
import com.accessibility.detector.communication.LanguageDetector
import com.accessibility.detector.communication.SupportedLanguage
import com.accessibility.detector.communication.TranslationEngine
import com.accessibility.detector.communication.TranslationResult
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.HapticManager

interface SoundOrchestratorCallback {
    fun onNewSoundEvent(event: SoundEvent)
    fun onLiveCaption(text: String, isPartial: Boolean)
    fun onTranslation(result: TranslationResult)
    fun onListeningStatus(isListening: Boolean, message: String)
}

/**
 * Sound & Language Orchestrator for Category 2: Sound & Language Assist.
 * Operates purely with Microphone / Audio input (NO CAMERA).
 */
class SoundOrchestrator(
    private val context: Context,
    private val callback: SoundOrchestratorCallback
) : SoundAwarenessListener, LiveSpeechListener {

    val soundEngine = SoundAwarenessEngine(context, this)
    val speechEngine = SpeechRecognitionEngine(context, this)
    val translationEngine = TranslationEngine()
    val languageDetector = LanguageDetector()
    val hapticManager = HapticManager(context)

    var targetLanguage: SupportedLanguage = SupportedLanguage.ENGLISH
    var isLiveTranslationEnabled: Boolean = true

    fun startSoundAssist() {
        soundEngine.startListening()
        speechEngine.startListening()
        callback.onListeningStatus(true, "Listening to surrounding sounds and speech...")
    }

    fun stopSoundAssist() {
        soundEngine.stopListening()
        speechEngine.stopListening()
        callback.onListeningStatus(false, "Sound assist paused")
    }

    // --- Sound Awareness Listener ---
    override fun onSoundEvent(event: SoundEvent) {
        // 1. Multi-Pattern Vibration based on sound hazard level
        when (event.priority) {
            EventPriority.CRITICAL -> hapticManager.playCriticalSosPattern()
            EventPriority.DANGER -> hapticManager.playSoundAlertPattern()
            else -> hapticManager.playNormalPulse()
        }

        // 2. Pass to UI
        callback.onNewSoundEvent(event)
    }

    override fun onSoundEngineState(isActive: Boolean, message: String) {
        callback.onListeningStatus(isActive, message)
    }

    // --- Live Speech Listener ---
    override fun onSpeechRecognized(text: String) {
        callback.onLiveCaption(text, isPartial = false)

        if (isLiveTranslationEnabled) {
            val detected = languageDetector.detectLanguage(text)
            val translation = translationEngine.translate(
                text = text,
                sourceLang = detected,
                targetLang = targetLanguage
            )
            callback.onTranslation(translation)
        }

        // Resume continuous listening loop for live captions
        speechEngine.startListening()
    }

    override fun onSpeechPartial(partialText: String) {
        callback.onLiveCaption(partialText, isPartial = true)
    }

    override fun onListeningStateChanged(isListening: Boolean) {
        // Update state
    }

    override fun onSpeechError(errorMessage: String) {
        // Automatically restart speech recognizer loop
        speechEngine.startListening()
    }

    fun shutdown() {
        soundEngine.stopListening()
        speechEngine.shutdown()
    }

    companion object {
        private const val TAG = "SoundOrchestrator"
    }
}
