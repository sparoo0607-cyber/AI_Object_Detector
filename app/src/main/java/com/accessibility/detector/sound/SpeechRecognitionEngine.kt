package com.accessibility.detector.sound

import android.content.Context
import android.content.Intent
import android.os.Build
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
 * Enterprise-grade Speech Recognition Engine for Sound & Language Assist.
 * Supports On-Device Offline Speech Recognition for Telugu ("te-IN") and English ("en-US"),
 * partial streaming results, RMS monitoring, and automated reconnection loops.
 */
class SpeechRecognitionEngine(
    private val context: Context,
    private val listener: LiveSpeechListener
) : RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    val modelManager = OfflineSpeechModelManager(context)

    var isListening: Boolean = false
        private set

    var shouldKeepListening: Boolean = false
    var currentLocale: Locale = Locale("te", "IN") // Default to Telugu (India)
    var recognitionMode: SpeechRecognitionMode = SpeechRecognitionMode.PREFER_OFFLINE
    var isActuallyOfflineRecognizer: Boolean = false
        private set

    fun startContinuousListening(
        locale: Locale = currentLocale,
        mode: SpeechRecognitionMode = recognitionMode
    ) {
        shouldKeepListening = true
        currentLocale = locale
        recognitionMode = mode
        mainHandler.post {
            startInternal()
        }
    }

    private fun startInternal() {
        if (!shouldKeepListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onSpeechError("Speech recognition service not available on this device")
            return
        }

        try {
            cleanupRecognizer()

            // 1. Prefer dedicated On-Device Recognizer on Android 12+ (API 31+) if supported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                try {
                    speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                        setRecognitionListener(this@SpeechRecognitionEngine)
                    }
                    isActuallyOfflineRecognizer = true
                    Log.d(TAG, "Created hardware On-Device SpeechRecognizer for offline recognition")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed creating on-device recognizer: ${e.message}, falling back to standard recognizer")
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(this@SpeechRecognitionEngine)
                    }
                    isActuallyOfflineRecognizer = false
                }
            } else {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@SpeechRecognitionEngine)
                }
                isActuallyOfflineRecognizer = false
            }

            // 2. Configure Speech Recognition Intent
            val languageTag = when (currentLocale.language) {
                "te" -> "te-IN"
                "hi" -> "hi-IN"
                "ta" -> "ta-IN"
                "kn" -> "kn-IN"
                else -> currentLocale.toLanguageTag()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra("android.speech.extra.DICTATION_MODE", true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)

                if (recognitionMode == SpeechRecognitionMode.OFFLINE_ONLY || recognitionMode == SpeechRecognitionMode.PREFER_OFFLINE) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            listener.onListeningStateChanged(true)
            Log.d(TAG, "SpeechRecognizer started listening in $languageTag (Mode: $recognitionMode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition: ${e.message}", e)
            isListening = false
            listener.onListeningStateChanged(false)
            listener.onSpeechError("Speech engine start error: ${e.localizedMessage}")

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

        val isOffline = !modelManager.isInternetAvailable(context)
        val isTelugu = currentLocale.language == "te"

        val msg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No voice detected"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Voice timeout"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                if (isOffline && isTelugu) {
                    "Telugu offline speech model is not installed. Please download the Telugu voice model in Settings."
                } else if (isOffline) {
                    "Offline speech recognition model not available for ${currentLocale.displayName}"
                } else {
                    "Network error during voice recognition"
                }
            }
            SpeechRecognizer.ERROR_AUDIO -> "Audio hardware busy"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            else -> "Speech code: $error"
        }
        Log.d(TAG, "SpeechRecognizer status: $msg ($error)")

        // Only schedule restart if not a fatal permissions error
        if (shouldKeepListening && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            val retryDelay = if (error == SpeechRecognizer.ERROR_NETWORK) 2000L else 300L
            scheduleRestart(retryDelay)
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
