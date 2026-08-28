package com.accessibility.detector.sound

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

interface LiveSpeechListener {
    fun onSpeechRecognized(text: String)
    fun onSpeechPartial(partialText: String)
    fun onRmsAudioLevel(rmsdB: Float)
    fun onListeningStateChanged(isListening: Boolean)
    fun onSpeechError(errorMessage: String)
}

/**
 * Robust continuous Speech Recognition Engine for Category 2: Sound & Language Assist.
 * Runs on Main Looper, provides partial streaming captions, acoustic RMS monitoring, and automatic loop restart.
 */
class SpeechRecognitionEngine(
    private val context: Context,
    private val listener: LiveSpeechListener
) : RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    var shouldKeepListening: Boolean = false
    var currentLocale: Locale = Locale.US

    fun startContinuousListening(locale: Locale = currentLocale) {
        shouldKeepListening = true
        currentLocale = locale
        mainHandler.post {
            startInternal()
        }
    }

    private fun startInternal() {
        if (!shouldKeepListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onSpeechError("Speech recognition not available on this device")
            return
        }

        try {
            cleanupRecognizer()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@SpeechRecognitionEngine)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra("android.speech.extra.DICTATION_MODE", true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            listener.onListeningStateChanged(true)
            Log.d(TAG, "SpeechRecognizer started listening in ${currentLocale.displayName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition: ${e.message}", e)
            isListening = false
            listener.onListeningStateChanged(false)
            listener.onSpeechError("Speech engine start error: ${e.localizedMessage}")

            // Retry after brief delay if continuous mode is enabled
            if (shouldKeepListening) {
                scheduleRestart(1000)
            }
        }
    }

    fun stopListening() {
        shouldKeepListening = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer: ${e.message}")
            } finally {
                isListening = false
                listener.onListeningStateChanged(false)
            }
        }
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up speech recognizer: ${e.message}")
        }
    }

    private fun scheduleRestart(delayMs: Long = 300) {
        if (!shouldKeepListening) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (shouldKeepListening) {
                startInternal()
            }
        }, delayMs)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        isListening = true
        listener.onListeningStateChanged(true)
        Log.d(TAG, "Ready for speech input")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "Human voice detected, speech started")
    }

    override fun onRmsChanged(rmsdB: Float) {
        listener.onRmsAudioLevel(rmsdB)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "End of speech segment")
    }

    override fun onError(error: Int) {
        isListening = false
        listener.onListeningStateChanged(false)

        val msg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No voice detected"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Voice timeout"
            SpeechRecognizer.ERROR_NETWORK -> "Network issue for voice recognition"
            SpeechRecognizer.ERROR_AUDIO -> "Audio hardware busy"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            else -> "Speech code: $error"
        }
        Log.d(TAG, "SpeechRecognizer status: $msg ($error)")

        // For common timeouts and silence, restart seamlessly
        if (shouldKeepListening) {
            scheduleRestart(250)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        listener.onListeningStateChanged(false)

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val best = matches?.firstOrNull()
        if (!best.isNullOrBlank()) {
            Log.d(TAG, "Recognized text: \"$best\"")
            listener.onSpeechRecognized(best)
        }

        // Seamlessly continue listening for next speech phrase
        if (shouldKeepListening) {
            scheduleRestart(200)
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
        shouldKeepListening = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            cleanupRecognizer()
            isListening = false
        }
    }

    companion object {
        private const val TAG = "SpeechRecognitionEngine"
    }
}
