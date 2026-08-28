package com.accessibility.detector.tts

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Text-to-Speech manager with intelligent anti-repetition and speech debouncing logic.
 */
class TtsManager(
    private val context: Context,
    private val onTtsStateChanged: ((statusText: String) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    var isMuted: Boolean = false
        set(value) {
            field = value
            if (value) {
                stop()
                onTtsStateChanged?.invoke("Voice output muted")
            } else {
                onTtsStateChanged?.invoke("Voice output active")
            }
        }

    // Debouncing and anti-repetition parameters
    private var lastSpokenLabel: String? = null
    private var lastSpokenTimestamp: Long = 0L
    private var lastObservedLabel: String? = null
    private var lastObservedTimestamp: Long = 0L

    // Cooldown duration in milliseconds for repeating the SAME object
    private val sameObjectCooldownMs = 2500L

    // Minimum delay between speaking DIFFERENT objects to avoid voice stuttering
    private val differentObjectMinGapMs = 600L

    // Object disappearance timeout: if no detection happens for 1.5s, reset active state
    private val disappearanceTimeoutMs = 1500L

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

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
            tts?.setSpeechRate(1.05f) // Slightly faster for quick responsiveness
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS speaking started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS speaking completed: $utteranceId")
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS speaking error on utterance: $utteranceId")
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.e(TAG, "TTS speaking error ($errorCode) on utterance: $utteranceId")
                }
            })

            isInitialized = true
            Log.d(TAG, "TTS engine initialized successfully")
            onTtsStateChanged?.invoke("Voice ready")
        } else {
            Log.e(TAG, "Failed to initialize TTS engine with status code: $status")
            isInitialized = false
            onTtsStateChanged?.invoke("TTS Initialization Failed")
        }
    }

    /**
     * Considers speaking the detected object based on the anti-repetition rules:
     * 1. If muted or not initialized, do nothing.
     * 2. If object is the SAME as last spoken: require cooldown of 2.5 seconds.
     * 3. If object is DIFFERENT: allow speaking immediately (with small 600ms gap to avoid jitter).
     * 4. If object disappeared and reappeared after cooldown, speak it.
     */
    fun considerSpeaking(label: String, @Suppress("UNUSED_PARAMETER") confidence: Float = 1.0f): Boolean {
        if (isMuted || !isInitialized || label.isBlank()) {
            return false
        }

        val currentTime = SystemClock.uptimeMillis()

        // Check if previous object had disappeared
        val timeSinceLastObserved = currentTime - lastObservedTimestamp
        val objectWasAbsent = timeSinceLastObserved > disappearanceTimeoutMs

        lastObservedLabel = label
        lastObservedTimestamp = currentTime

        val isSameObject = label.equals(lastSpokenLabel, ignoreCase = true)
        val timeSinceLastSpoken = currentTime - lastSpokenTimestamp

        val shouldSpeak = when {
            // Case 1: First detection ever
            lastSpokenLabel == null -> true

            // Case 2: Same object, but cooldown duration has passed or it disappeared & reappeared
            isSameObject -> timeSinceLastSpoken >= sameObjectCooldownMs || (objectWasAbsent && timeSinceLastSpoken >= 1500L)

            // Case 3: Completely different object, ensure brief gap to prevent cut-off
            else -> timeSinceLastSpoken >= differentObjectMinGapMs
        }

        if (shouldSpeak) {
            speak(label)
            lastSpokenLabel = label
            lastSpokenTimestamp = currentTime
            return true
        }

        return false
    }

    /**
     * Force reset speech state if no objects are detected in the frame.
     */
    fun onNoObjectsDetected() {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastObservedTimestamp > disappearanceTimeoutMs) {
            lastObservedLabel = null
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DETECTION_${System.currentTimeMillis()}")
        triggerHapticFeedback()
        onTtsStateChanged?.invoke(text)
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to perform haptic feedback", e)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
