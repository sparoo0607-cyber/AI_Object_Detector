package com.accessibility.detector.sign

import android.graphics.RectF
import com.accessibility.detector.detection.DetectionResult
import com.accessibility.detector.detection.EventPriority
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType

data class SignDetection(
    val gestureName: String,
    val spokenText: String,
    val confidence: Float,
    val boundingBox: RectF
)

/**
 * Sign Language classifier utilizing spatial geometry, aspect ratios, and temporal smoothing.
 */
class SignClassifier {

    private var stableGestureName: String? = null
    private var stableFrameCount: Int = 0
    private val requiredStableFrames = 3 // ~300ms stability threshold

    fun classifySignGesture(results: List<DetectionResult>): SignDetection? {
        val personOrHand = results.firstOrNull {
            it.label.equals("person", ignoreCase = true) ||
            it.label.contains("hand", ignoreCase = true)
        } ?: return null

        val box = personOrHand.boundingBox
        val aspect = box.width() / (box.height().coerceAtLeast(1f))

        // Classify gesture signature based on geometry & posture
        val candidate = when {
            // High raised vertical palm -> "Stop"
            aspect in 0.45f..0.75f && box.top < 0.25f -> "Stop"

            // Broad open upper chest hand -> "Hello"
            aspect in 0.6f..1.1f && box.top < 0.35f -> "Hello"

            // Lower centered compact posture -> "Thank You"
            aspect in 0.8f..1.3f && box.centerY() in 0.35f..0.65f -> "Thank You"

            // Compact clenched profile -> "Yes"
            aspect in 0.9f..1.1f -> "Yes"

            // Open wide posture -> "Help"
            aspect > 1.2f -> "Help"

            else -> null
        } ?: return null

        // Apply Temporal Smoothing
        if (candidate == stableGestureName) {
            stableFrameCount++
        } else {
            stableGestureName = candidate
            stableFrameCount = 1
        }

        if (stableFrameCount >= requiredStableFrames) {
            val spoken = when (candidate) {
                "Hello" -> "Sign recognized: Hello!"
                "Thank You" -> "Sign recognized: Thank you."
                "Stop" -> "Sign recognized: Stop!"
                "Help" -> "Emergency sign: Help!"
                "Yes" -> "Sign recognized: Yes."
                else -> "Sign: $candidate"
            }

            return SignDetection(
                gestureName = candidate,
                spokenText = spoken,
                confidence = 0.88f,
                boundingBox = box
            )
        }

        return null
    }

    fun reset() {
        stableGestureName = null
        stableFrameCount = 0
    }
}
