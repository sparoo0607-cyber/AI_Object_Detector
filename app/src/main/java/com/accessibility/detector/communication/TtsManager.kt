package com.accessibility.detector.communication

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Text-to-Speech manager for SAHEY providing clean utterance queuing,
 * cancellation, interruptible speech, and multi-language support.
 */
class TtsManager(
    context: Context,
    private val onInitStatus: ((isReady: Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    var isInitialized = false
        private set

    var speechRate: Float = 1.0f
        set(value) {
            field = value
            textToSpeech?.setSpeechRate(value)
        }

    private val utteranceCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    var onSpeakingStateChanged: ((isSpeaking: Boolean) -> Unit)? = null

    init {
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech: ${e.message}", e)
            onInitStatus?.invoke(false)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Default language US not supported on this device")
            }
            textToSpeech?.setSpeechRate(speechRate)
            setupUtteranceListener()
            isInitialized = true
            onInitStatus?.invoke(true)
            Log.d(TAG, "TextToSpeech initialized successfully")
        } else {
            Log.e(TAG, "TTS Initialization failed with status code: $status")
            isInitialized = false
            onInitStatus?.invoke(false)
        }
    }

    private fun setupUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    onSpeakingStateChanged?.invoke(true)
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    onSpeakingStateChanged?.invoke(false)
                    if (utteranceId != null) {
                        utteranceCallbacks.remove(utteranceId)?.invoke()
                    }
                }
            }

            @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
            override fun onError(utteranceId: String?) {
                handleUtteranceComplete(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                handleUtteranceComplete(utteranceId)
            }

            private fun handleUtteranceComplete(utteranceId: String?) {
                mainHandler.post {
                    onSpeakingStateChanged?.invoke(false)
                    if (utteranceId != null) {
                        utteranceCallbacks.remove(utteranceId)?.invoke()
                    }
                }
            }
        })
    }

    fun setLanguage(locale: Locale) {
        if (isInitialized) {
            textToSpeech?.setLanguage(locale)
        }
    }

    fun speak(
        text: String,
        interrupt: Boolean = false,
        utteranceId: String = System.currentTimeMillis().toString(),
        onDone: (() -> Unit)? = null
    ) {
        if (!isInitialized || textToSpeech == null) {
            Log.w(TAG, "TTS not ready to speak: \"$text\"")
            onDone?.invoke()
            return
        }

        if (onDone != null) {
            utteranceCallbacks[utteranceId] = onDone
        }

        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        textToSpeech?.speak(text, queueMode, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            textToSpeech?.stop()
        }
    }

    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking ?: false
    }

    fun shutdown() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
