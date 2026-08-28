package com.accessibility.detector.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Low-level TextToSpeech wrapper with utterance tracking and interruption capability.
 */
class TtsManager(
    private val context: Context,
    private val onInitStatus: ((isReady: Boolean) -> Unit)? = null,
    private val onSpeechDone: (() -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    var isInitialized: Boolean = false
        private set

    var isSpeaking: Boolean = false
        private set

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "US English language not supported, falling back to default device locale.")
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setSpeechRate(1.08f) // Crisp, snappy pace for accessibility
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    onSpeechDone?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    Log.e(TAG, "TTS speaking error on utterance: $utteranceId")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    isSpeaking = false
                    Log.e(TAG, "TTS speaking error ($errorCode) on utterance: $utteranceId")
                }
            })

            isInitialized = true
            Log.d(TAG, "TTS engine initialized successfully")
            onInitStatus?.invoke(true)
        } else {
            Log.e(TAG, "Failed to initialize TTS engine with status code: $status")
            isInitialized = false
            onInitStatus?.invoke(false)
        }
    }

    fun speak(text: String, interrupt: Boolean = true): Boolean {
        if (!isInitialized || text.isBlank()) return false
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "SAHEY_${System.currentTimeMillis()}")
        }
        val result = tts?.speak(text, queueMode, params, "SAHEY_${System.currentTimeMillis()}")
        return result == TextToSpeech.SUCCESS
    }

    fun setLanguage(locale: Locale) {
        try {
            tts?.setLanguage(locale)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set TTS language: ${e.message}")
        }
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.5f))
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            isSpeaking = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
