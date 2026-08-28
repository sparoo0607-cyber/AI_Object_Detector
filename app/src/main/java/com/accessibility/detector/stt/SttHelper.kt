package com.accessibility.detector.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * LISTEN / Live captions — Android's SpeechRecognizer.
 *
 * Honest limitation, same as the web prototype: on most devices this
 * uses Google's cloud speech service and needs a data/WiFi
 * connection. A minority of devices offer a genuine on-device
 * recognizer (Android 13+, `EXTRA_PREFER_OFFLINE`) — we request it
 * when available, but do not claim it works everywhere.
 */
class SttHelper(private val context: Context) {

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
    }
    var listener: Listener? = null

    private var recognizer: SpeechRecognizer? = null
    private var shouldRestart = false

    private fun localeFor(lang: String): String = when (lang) {
        "hi" -> "hi-IN"
        "te" -> "te-IN"
        else -> "en-US"
    }

    fun start(lang: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener?.onError("Speech recognition unavailable on this device")
            return
        }
        shouldRestart = true
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Live captions need a data or WiFi connection"
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "" // silence — not a real error
                        else -> "Speech recognition error ($error)"
                    }
                    if (msg.isNotEmpty()) listener?.onError(msg)
                    if (shouldRestart) restart(lang)
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) listener?.onFinal(text)
                    if (shouldRestart) restart(lang)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!text.isNullOrBlank()) listener?.onPartial(text)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(buildIntent(lang))
        }
    }

    private fun buildIntent(lang: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeFor(lang))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true) // used when the device genuinely supports it
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    private fun restart(lang: String) {
        try { recognizer?.startListening(buildIntent(lang)) } catch (e: Exception) {
            Log.w(TAG, "STT restart failed", e)
        }
    }

    fun stop() {
        shouldRestart = false
        try { recognizer?.stopListening(); recognizer?.destroy() } catch (e: Exception) { /* ignore */ }
        recognizer = null
    }

    companion object { private const val TAG = "SttHelper" }
}
