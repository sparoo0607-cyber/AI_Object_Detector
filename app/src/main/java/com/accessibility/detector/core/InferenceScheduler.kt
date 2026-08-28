package com.accessibility.detector.core

import android.os.SystemClock

/**
 * Throttles inference invocations across multiple AI models to guarantee 60 FPS UI performance
 * and minimize battery drain.
 */
class InferenceScheduler {

    private var lastObjectInferenceTime = 0L
    private var lastOcrInferenceTime = 0L
    private var lastSignInferenceTime = 0L

    // Throttle intervals in milliseconds
    var objectIntervalMs: Long = 50L  // ~20 FPS
    var ocrIntervalMs: Long = 800L    // ~1.2 FPS (OCR is computationally heavier)
    var signIntervalMs: Long = 66L    // ~15 FPS

    fun shouldRunObjectDetection(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastObjectInferenceTime >= objectIntervalMs) {
            lastObjectInferenceTime = now
            return true
        }
        return false
    }

    fun shouldRunOcr(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastOcrInferenceTime >= ocrIntervalMs) {
            lastOcrInferenceTime = now
            return true
        }
        return false
    }

    fun shouldRunSignDetection(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastSignInferenceTime >= signIntervalMs) {
            lastSignInferenceTime = now
            return true
        }
        return false
    }

    fun forceRunOcr() {
        lastOcrInferenceTime = 0L
    }
}
