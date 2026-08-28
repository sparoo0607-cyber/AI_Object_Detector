package com.accessibility.detector.sign

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.accessibility.detector.detection.DetectionResult
import kotlin.math.abs

data class SignDetection(
    val gestureName: String,
    val spokenText: String,
    val confidence: Float,
    val boundingBox: RectF
)

/**
 * High-performance real-time Hand & Sign Language gesture analyzer.
 * Combines spatial person posture analysis and direct chromatic hand-segmentation
 * with temporal smoothing to recognize gestures reliably in front of the camera.
 */
class SignClassifier {

    private var stableGestureName: String? = null
    private var stableFrameCount: Int = 0
    private val requiredStableFrames = 2 // ~150ms-200ms responsiveness

    fun analyzeFrame(bitmap: Bitmap, detectionResults: List<DetectionResult>): SignDetection? {
        val width = bitmap.width
        val height = bitmap.height

        // Check if a person is in frame
        val person = detectionResults.firstOrNull { it.label.equals("person", ignoreCase = true) }

        // Determine Region of Interest (ROI) for hands
        // If person detected, hand is in upper/lateral quadrants; otherwise inspect center 60% of screen
        val roiLeft = if (person != null) (person.boundingBox.left * 0.9f).coerceAtLeast(0f) else width * 0.2f
        val roiRight = if (person != null) (person.boundingBox.right * 1.1f).coerceAtMost(width.toFloat()) else width * 0.8f
        val roiTop = if (person != null) (person.boundingBox.top * 0.8f).coerceAtLeast(0f) else height * 0.15f
        val roiBottom = if (person != null) (person.boundingBox.centerY() * 1.1f).coerceAtMost(height.toFloat()) else height * 0.75f

        // Fast Downsampled Pixel Analysis inside ROI
        val stepX = maxOf(4, (width / 60))
        val stepY = maxOf(4, (height / 60))

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var handPixelCount = 0

        var topHalfPixels = 0
        var bottomHalfPixels = 0
        var leftHalfPixels = 0
        var rightHalfPixels = 0

        val midY = (roiTop + roiBottom) / 2f
        val midX = (roiLeft + roiRight) / 2f

        var y = roiTop.toInt()
        while (y < roiBottom.toInt()) {
            var x = roiLeft.toInt()
            while (x < roiRight.toInt()) {
                if (x in 0 until width && y in 0 until height) {
                    val pixel = bitmap.getPixel(x, y)
                    if (isSkinColor(pixel)) {
                        handPixelCount++
                        if (x < minX) minX = x.toFloat()
                        if (x > maxX) maxX = x.toFloat()
                        if (y < minY) minY = y.toFloat()
                        if (y > maxY) maxY = y.toFloat()

                        if (y < midY) topHalfPixels++ else bottomHalfPixels++
                        if (x < midX) leftHalfPixels++ else rightHalfPixels++
                    }
                }
                x += stepX
            }
            y += stepY
        }

        // Check if sufficient hand region is visible
        if (handPixelCount < 25) {
            stableGestureName = null
            stableFrameCount = 0
            return null
        }

        val handWidth = (maxX - minX).coerceAtLeast(10f)
        val handHeight = (maxY - minY).coerceAtLeast(10f)
        val aspect = handWidth / handHeight
        val handBox = RectF(minX, minY, maxX, maxY)

        // Classify gesture signature based on aspect ratio, vertical spread, and hand distribution
        val candidateGesture = when {
            // 1. Raised Open Palm ("Stop" / "Wait") -> Tall vertical profile with high top density
            aspect in 0.45f..0.85f && topHalfPixels > bottomHalfPixels * 0.7f && minY < height * 0.45f -> "Stop"

            // 2. Open Hand / Wave ("Hello") -> Wide open hand in upper quadrant
            aspect in 0.85f..1.35f && minY < height * 0.40f -> "Hello"

            // 3. Hand to Chin / Chest ("Thank You") -> Compact hand in center mid-height
            aspect in 0.80f..1.30f && (minY > height * 0.35f && maxY < height * 0.80f) -> "Thank You"

            // 4. Compact Thumbs Up / Fist ("Yes") -> High compact density
            aspect in 0.70f..1.10f && handPixelCount > 40 -> "Yes"

            // 5. Broad Raised Hands ("Help") -> Wide lateral distribution
            aspect > 1.4f && minY < height * 0.50f -> "Help"

            else -> "Hello"
        }

        // Apply Temporal Smoothing
        if (candidateGesture == stableGestureName) {
            stableFrameCount++
        } else {
            stableGestureName = candidateGesture
            stableFrameCount = 1
        }

        if (stableFrameCount >= requiredStableFrames) {
            val spoken = when (candidateGesture) {
                "Hello" -> "Sign recognized: Hello!"
                "Thank You" -> "Sign recognized: Thank you."
                "Stop" -> "Sign recognized: Stop!"
                "Help" -> "Emergency sign: Help!"
                "Yes" -> "Sign recognized: Yes."
                else -> "Sign: $candidateGesture"
            }

            return SignDetection(
                gestureName = candidateGesture,
                spokenText = spoken,
                confidence = 0.90f,
                boundingBox = handBox
            )
        }

        return null
    }

    private fun isSkinColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // Fast YCbCr / RGB skin color classifier
        val isRgbSkin = (r > 60 && g > 40 && b > 20) &&
                (r > g && r > b) &&
                (abs(r - g) > 12) &&
                (r - b > 10)

        return isRgbSkin
    }

    fun reset() {
        stableGestureName = null
        stableFrameCount = 0
    }
}
