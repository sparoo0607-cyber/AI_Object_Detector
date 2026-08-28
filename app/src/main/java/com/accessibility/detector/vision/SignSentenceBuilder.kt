package com.accessibility.detector.vision

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

interface SignSentenceListener {
    fun onSentenceUpdated(sentence: String, latestWord: String)
    fun onSentenceSpoken(sentence: String)
}

/**
 * Real-time Sign Language Sentence Builder for Vision Assist Sign Mode.
 * Accumulates recognized ASL gesture signs and fingerspelling into coherent sentences,
 * filters duplicate frames, and automatically speaks the sentence when signing pauses.
 */
class SignSentenceBuilder(
    private val listener: SignSentenceListener
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val words = mutableListOf<String>()
    private var lastAddedGesture: String = ""
    private var lastSignTimestamp = 0L

    var isSignModeActive: Boolean = false
        private set

    private val autoSpeakDelayMs = 2200L // Speeches sentence after ~2.2 seconds of signing pause
    private var autoSpeakRunnable: Runnable? = null

    fun setSignMode(active: Boolean): Boolean {
        isSignModeActive = active
        if (!active) {
            clearSentence()
            cancelAutoSpeak()
        }
        return isSignModeActive
    }

    fun toggleSignMode(): Boolean {
        return setSignMode(!isSignModeActive)
    }

    fun processSignDetection(detection: SignDetection) {
        if (!isSignModeActive) return

        val now = SystemClock.uptimeMillis()
        val gestureWord = mapGestureToWord(detection.letter, detection.gestureName)

        // Debounce repeated gestures within 1.5 seconds unless gesture changes
        if (gestureWord == lastAddedGesture && (now - lastSignTimestamp < 1500L)) {
            return
        }

        lastAddedGesture = gestureWord
        lastSignTimestamp = now

        words.add(gestureWord)
        val fullSentence = getSentence()
        listener.onSentenceUpdated(fullSentence, gestureWord)

        // Schedule auto-speak when user finishes signing sentence
        scheduleAutoSpeak(fullSentence)
    }

    private fun scheduleAutoSpeak(sentence: String) {
        cancelAutoSpeak()
        if (sentence.isBlank()) return

        autoSpeakRunnable = Runnable {
            if (isSignModeActive && words.isNotEmpty()) {
                val toSpeak = getSentence()
                if (toSpeak.isNotBlank()) {
                    listener.onSentenceSpoken(toSpeak)
                    // Reset gesture memory for next sentence
                    lastAddedGesture = ""
                }
            }
        }
        mainHandler.postDelayed(autoSpeakRunnable!!, autoSpeakDelayMs)
    }

    private fun cancelAutoSpeak() {
        autoSpeakRunnable?.let { mainHandler.removeCallbacks(it) }
        autoSpeakRunnable = null
    }

    fun getSentence(): String {
        return words.joinToString(" ")
    }

    fun clearSentence() {
        cancelAutoSpeak()
        words.clear()
        lastAddedGesture = ""
        listener.onSentenceUpdated("", "")
    }

    fun forceSpeakSentence(): String {
        cancelAutoSpeak()
        val sentence = getSentence()
        if (sentence.isNotBlank()) {
            listener.onSentenceSpoken(sentence)
        }
        return sentence
    }

    private fun mapGestureToWord(letter: Char, rawGesture: String): String {
        return when (letter) {
            'B' -> "Hello"
            'S' -> "Stop"
            'Y' -> "Yes"
            'N' -> "No"
            'H' -> "Help"
            'T' -> "Thank you"
            'W' -> "Water"
            'F' -> "Food"
            'L' -> "Please"
            'V' -> "Peace"
            'C' -> "Come"
            'G' -> "Go"
            else -> letter.toString() // Fingerspelling letter (A, D, E, I, K, M, O, P, Q, R, U, X)
        }
    }

    fun reset() {
        cancelAutoSpeak()
        clearSentence()
        isSignModeActive = false
    }

    companion object {
        private const val TAG = "SignSentenceBuilder"
    }
}
