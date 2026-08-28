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
 * High-speed, high-precision on-device chromatic pre-filter for fire, flame, and smoke cues.
 * Distinguishes true fiery luminance from skin tones, warm ambient lighting, and standard red objects.
 */
class FireSmokeDetector {

    /**
     * Scans camera bitmap and screen ROIs (laptop, TV, cell phone, monitor) for high-temperature luminous flame signatures.
     */
    fun analyzeFrame(bitmap: Bitmap, detectionResults: List<DetectionResult>): FirePreFilterResult {
        val width = bitmap.width
        val height = bitmap.height

        // 1. Check if a screen is in view (laptop, tv, cell phone, monitor)
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
            screenFireScore = calculateFlameScoreInRoi(bitmap, roi)
            // Screen fire requires significant luminous flame concentration inside display bounds
            if (screenFireScore > 0.22f) {
                isInsideScreen = true
            }
        }

        // 2. Full frame fire & smoke scan (requiring high concentrated cluster threshold)
        val fullFrameBox = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val fullFireScore = calculateFlameScoreInRoi(bitmap, fullFrameBox)
        val smokeScore = calculateSmokeScore(bitmap)

        val hasFire = (isInsideScreen && screenFireScore > 0.22f) || fullFireScore > 0.28f
        val hasSmoke = smokeScore > 0.35f
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
     * Precision chromatic flame detector:
     * - Rejects skin tones (which have lower brightness and different R:G:B ratios)
     * - Rejects pure red objects (which lack the high-luminance yellow/gold G channel)
     * - Detects intense glowing flame cores (R > 215, G > 115, R > G > B, high HSV Value & Saturation)
     */
    private fun calculateFlameScoreInRoi(bitmap: Bitmap, roi: RectF): Float {
        val width = bitmap.width
        val height = bitmap.height

        val left = roi.left.toInt().coerceIn(0, width - 1)
        val right = roi.right.toInt().coerceIn(0, width - 1)
        val top = roi.top.toInt().coerceIn(0, height - 1)
        val bottom = roi.bottom.toInt().coerceIn(0, height - 1)

        if (right <= left || bottom <= top) return 0f

        val stepX = maxOf(4, (right - left) / 32)
        val stepY = maxOf(4, (bottom - top) / 32)

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
                val hue = hsv[0] // 0..360 (Flame core: 10..55)
                val sat = hsv[1]
                val value = hsv[2]

                // Real Flame / Fire Video Chromatic Signature:
                // 1. High absolute luminance: R >= 210, Value >= 0.82
                // 2. Golden-Yellow-Orange spectrum: G >= 100, R > G, G >= (B * 1.35f), B <= 140
                // 3. Strict Hue: 8..55 degrees (Orange/Yellow/Gold flame)
                // 4. Moderate-to-high saturation (sat >= 0.50)
                // 5. Exclude skin tones (skin is typically r < 200 or sat < 0.45 or value < 0.75)
                val isTrueFlame = (r >= 210 && g in 100..230 && b <= 140) &&
                        (r > g && g > b) &&
                        (hue in 8f..55f) &&
                        (sat in 0.48f..1.0f) &&
                        (value >= 0.80f)

                if (isTrueFlame) {
                    flamePixelCount++
                }
                totalSampled++
            }
        }

        if (totalSampled == 0) return 0f
        return flamePixelCount.toFloat() / totalSampled
    }

    /**
     * Analyzes dense grayish smoke plumes.
     */
    private fun calculateSmokeScore(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val step = maxOf(6, width / 35)

        var smokePixelCount = 0
        var totalSampled = 0

        for (y in 0 until (height * 0.75).toInt() step step) {
            for (x in 0 until width step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val diffRG = abs(r - g)
                val diffGB = abs(g - b)
                val diffRB = abs(r - b)
                val avg = (r + g + b) / 3

                // Dense smoke is grayish-white/dark (very low color difference across all 3 channels)
                val isDenseSmoke = (diffRG <= 8 && diffGB <= 8 && diffRB <= 10) && (avg in 95..215)
                if (isDenseSmoke) {
                    smokePixelCount++
                }
                totalSampled++
            }
        }

        if (totalSampled == 0) return 0f
        return smokePixelCount.toFloat() / totalSampled
    }
}
