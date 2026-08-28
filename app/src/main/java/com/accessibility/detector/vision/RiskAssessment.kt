package com.accessibility.detector.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType
import com.accessibility.detector.core.ProximityLevel
import com.accessibility.detector.core.SpatialPosition

/**
 * Assesses spatial risk, proximity, fire cues, surface reflectiveness, and approach dynamics.
 */
class RiskAssessment {

    /**
     * Evaluates a detected object to see if it qualifies as an active hazard or navigation risk.
     */
    fun evaluateRisk(result: DetectionResult): PerceptionEvent? {
        val label = result.label.lowercase()
        val pos = result.spatialPosition
        val prox = result.proximity

        val isVehicle = HazardRules.isVehicle(label)
        val isObstacle = HazardRules.isObstacle(label)
        val isPedestrian = HazardRules.isPedestrian(label)
        val isFire = HazardRules.isFireOrSmoke(label)

        // 1. Fire / Smoke Alert
        if (isFire) {
            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Fire / Smoke",
                spokenText = "Warning. Fire detected.",
                confidence = result.score,
                priority = EventPriority.CRITICAL,
                spatialPosition = pos,
                proximity = prox
            )
        }

        // 2. Vehicle Hazards with directional awareness
        if (isVehicle) {
            val directionPhrase = when (pos) {
                SpatialPosition.LEFT -> "on your left"
                SpatialPosition.RIGHT -> "on your right"
                SpatialPosition.CENTER -> "directly ahead"
                SpatialPosition.UNKNOWN -> "ahead"
            }

            val isImminent = prox == ProximityLevel.VERY_CLOSE || (pos == SpatialPosition.CENTER && prox == ProximityLevel.NEARBY)
            val priority = if (isImminent) EventPriority.CRITICAL else EventPriority.DANGER

            val spokenText = if (isImminent) {
                "Warning! Vehicle $directionPhrase, very close!"
            } else {
                "${result.label} $directionPhrase."
            }

            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "${result.label} ($directionPhrase)",
                spokenText = spokenText,
                confidence = result.score,
                priority = priority,
                spatialPosition = pos,
                proximity = prox
            )
        }

        // 3. Direct Obstacle Hazards (Center path obstruction)
        if (isObstacle && (pos == SpatialPosition.CENTER || prox == ProximityLevel.VERY_CLOSE)) {
            val proxPhrase = when (prox) {
                ProximityLevel.VERY_CLOSE -> "very close"
                ProximityLevel.NEARBY -> "nearby"
                else -> "ahead"
            }

            val spokenText = "Obstacle ahead: ${result.label} $proxPhrase."
            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Obstacle: ${result.label}",
                spokenText = spokenText,
                confidence = result.score,
                priority = EventPriority.NAVIGATION,
                spatialPosition = pos,
                proximity = prox
            )
        }

        // 4. Pedestrian Crossing / Path entry
        if (isPedestrian && prox == ProximityLevel.VERY_CLOSE) {
            val directionPhrase = when (pos) {
                SpatialPosition.LEFT -> "on your left"
                SpatialPosition.RIGHT -> "on your right"
                else -> "directly ahead"
            }
            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Person ($directionPhrase)",
                spokenText = "Person $directionPhrase.",
                confidence = result.score,
                priority = EventPriority.NAVIGATION,
                spatialPosition = pos,
                proximity = prox
            )
        }

        return null
    }

    /**
     * Analyzes lower third of camera bitmap for high specular reflection / spilled liquid on floor.
     */
    fun evaluateSlipperyFloor(bitmap: Bitmap): PerceptionEvent? {
        val width = bitmap.width
        val height = bitmap.height
        val startY = (height * 0.70f).toInt()

        var brightReflectivePixels = 0
        var sampledCount = 0
        val step = maxOf(4, width / 40)

        var y = startY
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val lum = 0.299 * r + 0.587 * g + 0.114 * b

                if (lum > 225 && (r > 200 && g > 200 && b > 200)) {
                    brightReflectivePixels++
                }
                sampledCount++
                x += step
            }
            y += step
        }

        if (sampledCount > 0 && (brightReflectivePixels.toFloat() / sampledCount) > 0.45f) {
            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Slippery Floor",
                spokenText = "Warning. Possible slippery floor ahead.",
                confidence = 0.80f,
                priority = EventPriority.NAVIGATION
            )
        }

        return null
    }
}
