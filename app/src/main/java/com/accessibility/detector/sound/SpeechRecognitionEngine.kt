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
 * Enterprise-grade, resilient Speech Recognition Engine for Sound & Language Assist.
 * Features:
 * 1. Intelligent dual-fallback (Dedicated On-Device Recognizer -> Standard Google SpeechRecognizer).
 * 2. Continuous listening loop that handles silence timeouts gracefully without user disruption.
 * 3. Real-time partial results streaming for instant UI feedback.
 * 4. Resilient multi-language support (Telugu, English, Hindi, Tamil, Kannada, etc.).
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
    var currentLocale: Locale = Locale("te", "IN") // Default: Telugu (India)
    var recognitionMode: SpeechRecognitionMode = SpeechRecognitionMode.PREFER_OFFLINE
    var isActuallyOfflineRecognizer: Boolean = false
        private set

    private var consecutiveErrors = 0
    private var isUsingFallback = false

    fun startContinuousListening(
        locale: Locale = currentLocale,
        mode: SpeechRecognitionMode = recognitionMode
    ) {
        shouldKeepListening = true
        currentLocale = locale
        recognitionMode = mode
        consecutiveErrors = 0
        isUsingFallback = false

        mainHandler.post {
            startInternal()
        }
    }

    private fun startInternal() {
        if (!shouldKeepListening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition service not available on this device")
            listener.onSpeechError("Speech recognition service not available on this device")
            return
        }

        try {
            cleanupRecognizer()

            // 1. Create Recognizer (Hardware On-Device or Standard)
            val useOnDevice = !isUsingFallback &&
                    (recognitionMode == SpeechRecognitionMode.PREFER_OFFLINE || recognitionMode == SpeechRecognitionMode.OFFLINE_ONLY) &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

            if (useOnDevice) {
                try {
                    speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                        setRecognitionListener(this@SpeechRecognitionEngine)
                    }
                    isActuallyOfflineRecognizer = true
                    Log.d(TAG, "Initialized On-Device SpeechRecognizer")
                } catch (e: Exception) {
                    Log.w(TAG, "On-device init failed (${e.message}), using standard SpeechRecognizer")
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

            // 2. Prepare Clean Recognizer Intent
            val languageTag = when (currentLocale.language) {
                "te" -> "te-IN"
                "hi" -> "hi-IN"
                "ta" -> "ta-IN"
                "kn" -> "kn-IN"
                "ml" -> "ml-IN"
                "es" -> "es-ES"
                else -> currentLocale.toLanguageTag()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)

                // Silence thresholds for natural speech dictation
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)

                if (!isUsingFallback && (recognitionMode == SpeechRecognitionMode.OFFLINE_ONLY || recognitionMode == SpeechRecognitionMode.PREFER_OFFLINE)) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            listener.onListeningStateChanged(true)
            Log.d(TAG, "Listening started for $languageTag (Mode: $recognitionMode, Fallback: $isUsingFallback)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition: ${e.message}", e)
            isListening = false
            listener.onListeningStateChanged(false)
            listener.onSpeechError("Speech start error: ${e.localizedMessage}")

            if (shouldKeepListening) {
                scheduleRestart(1000)
            }
        }
    }

    fun stopListening() {
        shouldKeepListening = false
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping recognizer: ${e.message}")
            } finally {
                cleanupRecognizer()
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

    private fun scheduleRestart(delayMs: Long = 250) {
        if (!shouldKeepListening) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (shouldKeepListening) {
                startInternal()
            }
        }, delayMs)
    }

    // --- RecognitionListener Callbacks ---
    override fun onReadyForSpeech(params: Bundle?) {
        isListening = true
        consecutiveErrors = 0
        listener.onListeningStateChanged(true)
        Log.d(TAG, "Microphone ready for speech input")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "Human voice started speaking")
    }

    override fun onRmsChanged(rmsdB: Float) {
        listener.onRmsAudioLevel(rmsdB)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "Speech segment ended, processing results")
    }

    override fun onError(error: Int) {
        isListening = false
        listener.onListeningStateChanged(false)

        val isTimeoutOrNoMatch = (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

        if (isTimeoutOrNoMatch) {
            // Normal silence interval: quietly loop back to listening without scaring user
            consecutiveErrors = 0
            if (shouldKeepListening) {
                scheduleRestart(200)
            }
            return
        }

        consecutiveErrors++
        Log.w(TAG, "SpeechRecognizer error code: $error (Consecutive: $consecutiveErrors)")

        // If offline/on-device recognizer failed, automatically fallback to standard recognizer
        if (isActuallyOfflineRecognizer || (recognitionMode == SpeechRecognitionMode.PREFER_OFFLINE && !isUsingFallback)) {
            Log.i(TAG, "Switching to standard recognizer fallback")
            isUsingFallback = true
        }

        val isOffline = !modelManager.isInternetAvailable(context)
        val isTelugu = currentLocale.language == "te"

        val msg = when (error) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                if (isOffline && isTelugu) {
                    "Telugu offline speech model is not installed. Please download it in Voice Settings."
                } else if (isOffline) {
                    "Offline speech model not available for ${currentLocale.displayName}"
                } else {
                    "Network error during voice recognition"
                }
            }
            SpeechRecognizer.ERROR_AUDIO -> "Microphone busy / audio record error"
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition initializing..."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy, resetting..."
            SpeechRecognizer.ERROR_SERVER -> "Speech server error"
            else -> "Speech code: $error"
        }

        if (consecutiveErrors > 2) {
            listener.onSpeechError(msg)
        }

        // Restart listener unless permissions are denied
        if (shouldKeepListening && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            val retryDelay = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 800L else 400L
            scheduleRestart(retryDelay)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        consecutiveErrors = 0
        listener.onListeningStateChanged(false)

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val best = matches?.firstOrNull()
        if (!best.isNullOrBlank()) {
            Log.d(TAG, "Recognized human speech: \"$best\"")
            listener.onSpeechRecognized(best)
        }

        if (shouldKeepListening) {
            scheduleRestart(150)
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
