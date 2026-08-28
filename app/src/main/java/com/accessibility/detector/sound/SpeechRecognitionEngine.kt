package com.accessibility.detector.sound

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

interface LiveSpeechListener {
    fun onSpeechRecognized(text: String)
    fun onSpeechPartial(partialText: String)
    fun onListeningStateChanged(isListening: Boolean)
    fun onSpeechError(errorMessage: String)
}

/**
 * Speech Recognition Engine for Category 2: Live Captions & Live Transcription.
 */
class SpeechRecognitionEngine(
    private val context: Context,
    private val listener: LiveSpeechListener
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    var currentLocale: Locale = Locale.US

    fun startListening(locale: Locale = currentLocale) {
        if (isListening) return
        currentLocale = locale

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onSpeechError("Speech recognition not available on this device")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@SpeechRecognitionEngine)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            listener.onListeningStateChanged(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition: ${e.message}", e)
            isListening = false
            listener.onListeningStateChanged(false)
            listener.onSpeechError("Speech error: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognizer: ${e.message}")
        } finally {
            isListening = false
            listener.onListeningStateChanged(false)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isListening = false
        listener.onListeningStateChanged(false)
    }

    override fun onError(error: Int) {
        isListening = false
        listener.onListeningStateChanged(false)
        val msg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection required for cloud speech"
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            else -> "Speech recognition error ($error)"
        }
        listener.onSpeechError(msg)
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        listener.onListeningStateChanged(false)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val best = matches?.firstOrNull()
        if (!best.isNullOrBlank()) {
            listener.onSpeechRecognized(best)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partial = matches?.firstOrNull()
        if (!partial.isNullOrBlank()) {
            listener.onSpeechPartial(partial)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun shutdown() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech recognizer: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SpeechRecognitionEngine"
    }
}
