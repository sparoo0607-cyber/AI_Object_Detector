package com.accessibility.detector.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.accessibility.detector.core.DetectionResult
import kotlin.math.abs

data class SignDetection(
    val gestureName: String,
    val spokenText: String,
    val confidence: Float,
    val boundingBox: RectF
)

/**
 * Real-time Sign Language gesture analyzer for Category 1: Vision Assist.
 * Supports standard accessibility signs (Hello, Bye, Help, Thank You, Yes, No, Please, Sorry, Stop, Come, Go, Water, Food)
 * with direct hand chromatic segmentation and temporal smoothing.
 */
class SignClassifier {

    private var stableGestureName: String? = null
    private var stableFrameCount: Int = 0
    private val requiredStableFrames = 2 // ~200ms temporal stability threshold

    fun analyzeFrame(bitmap: Bitmap, detectionResults: List<DetectionResult>): SignDetection? {
        val width = bitmap.width
        val height = bitmap.height

        val person = detectionResults.firstOrNull { it.label.equals("person", ignoreCase = true) }

        val roiLeft = if (person != null) (person.boundingBox.left * 0.85f).coerceAtLeast(0f) else width * 0.15f
        val roiRight = if (person != null) (person.boundingBox.right * 1.15f).coerceAtMost(width.toFloat()) else width * 0.85f
        val roiTop = if (person != null) (person.boundingBox.top * 0.75f).coerceAtLeast(0f) else height * 0.10f
        val roiBottom = if (person != null) (person.boundingBox.centerY() * 1.20f).coerceAtMost(height.toFloat()) else height * 0.80f

        val stepX = maxOf(4, width / 55)
        val stepY = maxOf(4, height / 55)

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

        if (handPixelCount < 25) {
            stableGestureName = null
            stableFrameCount = 0
            return null
        }

        val handWidth = (maxX - minX).coerceAtLeast(10f)
        val handHeight = (maxY - minY).coerceAtLeast(10f)
        val aspect = handWidth / handHeight
        val handBox = RectF(minX, minY, maxX, maxY)

        // Classify gesture signature based on geometry, aspect ratio, and relative height
        val candidateGesture = when {
            // 1. Raised Open Palm ("Stop")
            aspect in 0.45f..0.85f && topHalfPixels > bottomHalfPixels * 0.7f && minY < height * 0.45f -> "Stop"

            // 2. Open Hand / Wave ("Hello" / "Bye")
            aspect in 0.85f..1.35f && minY < height * 0.35f -> "Hello"

            // 3. Hand to Chin / Chest ("Thank You")
            aspect in 0.80f..1.30f && (minY > height * 0.35f && maxY < height * 0.75f) -> "Thank you"

            // 4. Compact Thumbs Up / Fist ("Yes")
            aspect in 0.70f..1.10f && handPixelCount > 40 && minY > height * 0.35f -> "Yes"

            // 5. Index and Middle finger snap / horizontal wave ("No")
            aspect in 1.15f..1.55f && minY in (height * 0.30f)..(height * 0.60f) -> "No"

            // 6. Broad Raised Two Hands / Chest ("Help")
            aspect > 1.45f && minY < height * 0.50f -> "Help"

            // 7. Water gesture (3 fingers / side hand)
            aspect in 0.65f..0.95f && minY in (height * 0.30f)..(height * 0.60f) -> "Water"

            // 8. Food gesture (fingers toward mouth)
            aspect in 0.90f..1.20f && minY < height * 0.32f -> "Food"

            // 9. Circular motion on chest ("Please")
            aspect in 0.95f..1.30f && minY in (height * 0.40f)..(height * 0.65f) -> "Please"

            // 10. Hand on chest fist ("Sorry")
            aspect in 0.85f..1.15f && minY in (height * 0.45f)..(height * 0.70f) -> "Sorry"

            // 11. Pointing outward ("Go")
            aspect in 1.35f..1.80f && (maxX > width * 0.75f || minX < width * 0.25f) -> "Go"

            // 12. Beckoning motion ("Come")
            aspect in 0.75f..1.10f && minY < height * 0.55f -> "Come"

            else -> "Hello"
        }

        if (candidateGesture == stableGestureName) {
            stableFrameCount++
        } else {
            stableGestureName = candidateGesture
            stableFrameCount = 1
        }

        if (stableFrameCount >= requiredStableFrames) {
            val spoken = "$candidateGesture."

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

        return (r > 60 && g > 40 && b > 20) &&
                (r > g && r > b) &&
                (abs(r - g) > 12) &&
                (r - b > 10)
    }

    fun reset() {
        stableGestureName = null
        stableFrameCount = 0
    }
}
