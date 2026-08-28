package com.accessibility.detector.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import com.accessibility.detector.core.DetectionResult
import kotlin.math.abs

data class FirePreFilterResult(
    val hasFireVisualCues: Boolean,
    val hasSmokeVisualCues: Boolean,
    val isInsideScreen: Boolean,
    val fireConfidence: Float,
    val screenLabel: String?
)

/**
 * High-speed on-device chromatic and luminance pre-filter for fire, flame, and smoke cues.
 * Inspects both full-frame visual environment and screen ROIs (laptop, TV, cell phone).
 */
class FireSmokeDetector {

    /**
     * Rapidly scans a camera bitmap and local object bounding boxes for fire/flame and smoke patterns.
     */
    fun analyzeFrame(bitmap: Bitmap, detectionResults: List<DetectionResult>): FirePreFilterResult {
        val width = bitmap.width
        val height = bitmap.height

        // 1. Check if a screen is detected (laptop, tv, cell phone, monitor)
        val screenObject = detectionResults.firstOrNull {
            it.label.equals("laptop", ignoreCase = true) ||
            it.label.equals("tv", ignoreCase = true) ||
            it.label.equals("cell phone", ignoreCase = true) ||
            it.label.equals("monitor", ignoreCase = true)
        }

        var screenFireScore = 0f
        var isInsideScreen = false

        if (screenObject != null) {
            val roi = screenObject.boundingBox
            screenFireScore = calculateFireScoreInRoi(bitmap, roi)
            if (screenFireScore > 0.18f) {
                isInsideScreen = true
            }
        }

        // 2. Full frame fire & smoke scan
        val fullFrameBox = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val fullFireScore = calculateFireScoreInRoi(bitmap, fullFrameBox)
        val smokeScore = calculateSmokeScore(bitmap)

        val hasFire = screenFireScore > 0.18f || fullFireScore > 0.15f
        val hasSmoke = smokeScore > 0.22f
        val maxFireConfidence = maxOf(screenFireScore, fullFireScore)

        return FirePreFilterResult(
            hasFireVisualCues = hasFire,
            hasSmokeVisualCues = hasSmoke,
            isInsideScreen = isInsideScreen,
            fireConfidence = maxFireConfidence,
            screenLabel = screenObject?.label
        )
    }

    /**
     * Analyzes fiery chromatic signatures: High R, R > G > B, high saturation, and intense luminance.
     */
    private fun calculateFireScoreInRoi(bitmap: Bitmap, roi: RectF): Float {
        val width = bitmap.width
        val height = bitmap.height

        val left = roi.left.toInt().coerceIn(0, width - 1)
        val right = roi.right.toInt().coerceIn(0, width - 1)
        val top = roi.top.toInt().coerceIn(0, height - 1)
        val bottom = roi.bottom.toInt().coerceIn(0, height - 1)

        if (right <= left || bottom <= top) return 0f

        val stepX = maxOf(4, (right - left) / 35)
        val stepY = maxOf(4, (bottom - top) / 35)

        var totalSampled = 0
        var flamePixelCount = 0
        val hsv = FloatArray(3)

        for (y in top until bottom step stepY) {
            for (x in left until right step stepX) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                Color.colorToHSV(pixel, hsv)
                val hue = hsv[0] // 0..360 (Red: 0..30, Orange: 30..45, Yellow: 45..60)
                val saturation = hsv[1]
                val value = hsv[2]

                // Flame Chromatic Rule:
                // 1. Red is dominant: R > G and G > B
                // 2. High intensity: R > 175 and Value > 0.70
                // 3. Flame Hue range: Red/Orange/Yellow (0..60 degrees) with moderate-to-high saturation (>= 0.40)
                val isFlameColor = (r > 175 && r > g && g >= (b * 0.9f)) &&
                        (hue in 0f..65f || hue in 350f..360f) &&
                        (saturation in 0.35f..1.0f) &&
                        (value > 0.65f)

                if (isFlameColor) {
                    flamePixelCount++
                }
                totalSampled++
            }
        }

        if (totalSampled == 0) return 0f
        return flamePixelCount.toFloat() / totalSampled
    }

    /**
     * Analyzes grayish, low-saturation turbulent smoke visual clusters.
     */
    private fun calculateSmokeScore(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val step = maxOf(6, width / 40)

        var smokePixelCount = 0
        var totalSampled = 0

        for (y in 0 until (height * 0.85).toInt() step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val diffRG = abs(r - g)
                val diffGB = abs(g - b)
                val avg = (r + g + b) / 3

                // Smoke is grayish, semi-translucent (low color variance among RGB channels, intensity in mid-high range)
                val isSmokeGray = (diffRG < 15 && diffGB < 15) && (avg in 110..225)
                if (isSmokeGray) {
                    smokePixelCount++
                }
                totalSampled++
            }
        }

        if (totalSampled == 0) return 0f
        return smokePixelCount.toFloat() / totalSampled
    }
}
