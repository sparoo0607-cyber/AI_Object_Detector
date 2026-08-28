package com.accessibility.detector.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

interface SpeechRecognitionListener {
    fun onSpeechResult(text: String)
    fun onSpeechPartial(partialText: String)
    fun onSpeechListening(isListening: Boolean)
    fun onSpeechError(errorMessage: String)
}

/**
 * Speech Recognition engine using native Android SpeechRecognizer.
 */
class SpeechEngine(
    private val context: Context,
    private val listener: SpeechRecognitionListener
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    var currentLocale: Locale = Locale.US

    fun startListening(locale: Locale = currentLocale) {
        if (isListening) return
        currentLocale = locale

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onSpeechError("Speech recognition not available on device")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@SpeechEngine)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            listener.onSpeechListening(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition: ${e.message}", e)
            isListening = false
            listener.onSpeechListening(false)
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
            listener.onSpeechListening(false)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "Ready for speech")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "Speech started")
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isListening = false
        listener.onSpeechListening(false)
    }

    override fun onError(error: Int) {
        isListening = false
        listener.onSpeechListening(false)
        val msg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
            SpeechRecognizer.ERROR_NETWORK -> "Network required for speech recognition"
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            else -> "Speech recognition error code: $error"
        }
        listener.onSpeechError(msg)
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        listener.onSpeechListening(false)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestResult = matches?.firstOrNull()
        if (!bestResult.isNullOrBlank()) {
            listener.onSpeechResult(bestResult)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestPartial = matches?.firstOrNull()
        if (!bestPartial.isNullOrBlank()) {
            listener.onSpeechPartial(bestPartial)
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
        private const val TAG = "SpeechEngine"
    }
}
