package com.accessibility.detector.communication

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Text-to-Speech manager for SAHEY.
 *
 * Smoothness:
 *  - a request made before the engine finishes initialising is queued and spoken on init
 *    (no more silent "TTS not ready" taps).
 *  - [onSpeakingChanged] reports start/stop so the UI can reflect state.
 * Accuracy:
 *  - [speak] can switch the voice to the phrase's language, so a Telugu / Hindi translation
 *    is spoken with the right voice instead of the English one.
 */
class TtsManager(
    context: Context,
    private val onInitStatus: ((isReady: Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    var isInitialized = false
        private set

    /** Invoked on the main thread with true when an utterance starts, false when it ends. */
    var onSpeakingChanged: ((Boolean) -> Unit)? = null

    var speechRate: Float = 0.96f
        set(value) {
            field = value
            textToSpeech?.setSpeechRate(value)
        }

    private var currentLocale: Locale = Locale.US
    private var pendingText: String? = null
    private var pendingInterrupt: Boolean = true
    private var pendingLocale: Locale? = null

    init {
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TextToSpeech: ${e.message}", e)
            onInitStatus?.invoke(false)
        }
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS init failed: $status")
            isInitialized = false
            onInitStatus?.invoke(false)
            return
        }

        applyLocale(Locale.US)
        textToSpeech?.setSpeechRate(speechRate)
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = post { onSpeakingChanged?.invoke(true) }
            override fun onDone(utteranceId: String?) = post { onSpeakingChanged?.invoke(false) }
            @Deprecated("kept for API < 21 signature")
            override fun onError(utteranceId: String?) = post { onSpeakingChanged?.invoke(false) }
            override fun onError(utteranceId: String?, errorCode: Int) = post { onSpeakingChanged?.invoke(false) }
        })

        isInitialized = true
        onInitStatus?.invoke(true)
        Log.d(TAG, "TextToSpeech ready")

        pendingText?.let { text ->
            val loc = pendingLocale
            pendingText = null
            pendingLocale = null
            speak(text, pendingInterrupt, loc)
        }
    }

    private fun post(block: () -> Unit) {
        textToSpeech?.let { android.os.Handler(android.os.Looper.getMainLooper()).post(block) } ?: block()
    }

    fun setLanguage(locale: Locale) {
        if (isInitialized) applyLocale(locale)
    }

    /** Sets the voice for a [SupportedLanguage]; falls back to English if that voice is missing. */
    fun setPreferredLanguage(language: SupportedLanguage) {
        localeOf(language)?.let { setLanguage(it) }
    }

    private fun applyLocale(locale: Locale) {
        val tts = textToSpeech ?: return
        if (locale == currentLocale) return
        val res = tts.isLanguageAvailable(locale)
        if (res == TextToSpeech.LANG_AVAILABLE ||
            res == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            res == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        ) {
            tts.language = locale
            currentLocale = locale
        } else {
            Log.w(TAG, "TTS voice for $locale not installed (code $res); keeping $currentLocale")
        }
    }

    /**
     * @param language optional — switch the voice to this language for this utterance.
     */
    fun speak(
        text: String,
        interrupt: Boolean = false,
        language: SupportedLanguage?,
        utteranceId: String = System.currentTimeMillis().toString()
    ) {
        speak(text, interrupt, localeOf(language), utteranceId)
    }

    fun speak(
        text: String,
        interrupt: Boolean = false,
        locale: Locale? = null,
        utteranceId: String = System.currentTimeMillis().toString()
    ) {
        if (text.isBlank()) return
        if (!isInitialized || textToSpeech == null) {
            pendingText = text
            pendingInterrupt = interrupt
            pendingLocale = locale
            Log.d(TAG, "TTS not ready — queued: \"${text.take(40)}\"")
            return
        }
        locale?.let { applyLocale(it) }
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        textToSpeech?.speak(text, mode, null, utteranceId)
    }

    fun stop() {
        pendingText = null
        if (isInitialized) textToSpeech?.stop()
        onSpeakingChanged?.invoke(false)
    }

    fun isSpeaking(): Boolean = textToSpeech?.isSpeaking ?: false

    fun shutdown() {
        try {
            pendingText = null
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

        fun localeOf(language: SupportedLanguage?): Locale? = when (language) {
            SupportedLanguage.ENGLISH -> Locale.US
            SupportedLanguage.TELUGU -> Locale("te", "IN")
            SupportedLanguage.HINDI -> Locale("hi", "IN")
            SupportedLanguage.TAMIL -> Locale("ta", "IN")
            SupportedLanguage.KANNADA -> Locale("kn", "IN")
            SupportedLanguage.MALAYALAM -> Locale("ml", "IN")
            SupportedLanguage.SPANISH -> Locale("es", "ES")
            else -> null
        }
    }
}
