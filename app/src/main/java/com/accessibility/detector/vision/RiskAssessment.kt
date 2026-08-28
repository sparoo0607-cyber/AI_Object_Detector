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
 * Evaluates contextual spatial risks, obstacle pathways, and floor hazards.
 */
class RiskAssessment {

    /**
     * Assesses a detected object to determine if it represents an active safety hazard.
     */
    fun evaluateObjectRisk(result: DetectionResult): PerceptionEvent? {
        val label = result.label.lowercase()
        val pos = result.spatialPosition
        val prox = result.proximity

        // 1. Fire / Flame / Smoke label from the object detector (rare — COCO has no fire class).
        //    Treated as an advisory; the verified fire path is DangerDetectionEngine -> Gemini.
        if (HazardRules.isFireOrSmoke(label)) {
            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Possible Fire",
                spokenText = "Possible fire nearby. Please verify.",
                confidence = result.score,
                priority = EventPriority.DANGER,
                spatialPosition = pos,
                proximity = prox
            )
        }

        // 2. Approaching Vehicle (CRITICAL / HIGH DANGER)
        if (HazardRules.isVehicle(label)) {
            val directionPhrase = when (pos) {
                SpatialPosition.LEFT -> "from your left"
                SpatialPosition.RIGHT -> "from your right"
                SpatialPosition.CENTER -> "ahead"
                SpatialPosition.UNKNOWN -> ""
            }

            val spoken = if (prox == ProximityLevel.VERY_CLOSE || prox == ProximityLevel.NEARBY) {
                if (directionPhrase.isNotBlank()) "Warning! Vehicle approaching $directionPhrase." else "Warning. Vehicle approaching."
            } else {
                if (directionPhrase.isNotBlank()) "Vehicle $directionPhrase." else "Vehicle detected."
            }

            val priority = if (prox == ProximityLevel.VERY_CLOSE) EventPriority.CRITICAL else EventPriority.DANGER

            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Vehicle: ${result.label}",
                spokenText = spoken,
                confidence = result.score,
                priority = priority,
                spatialPosition = pos,
                proximity = prox
            )
        }

        // 3. Stairs (NAVIGATION HAZARD)
        if (HazardRules.isStairs(label)) {
            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Stairs",
                spokenText = "Stairs ahead.",
                confidence = result.score,
                priority = EventPriority.NAVIGATION,
                spatialPosition = pos,
                proximity = prox
            )
        }

        // 4. Drop / Edge (HIGH DANGER)
        if (HazardRules.isDropOrEdge(label)) {
            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Drop Hazard",
                spokenText = "Warning. Possible drop ahead.",
                confidence = result.score,
                priority = EventPriority.DANGER,
                spatialPosition = pos,
                proximity = prox
            )
        }

        // 5. Pathway Obstacles (NAVIGATION HAZARD)
        if (HazardRules.isObstacle(label) && (prox == ProximityLevel.VERY_CLOSE || prox == ProximityLevel.NEARBY)) {
            val dir = when (pos) {
                SpatialPosition.LEFT -> "on your left"
                SpatialPosition.RIGHT -> "on your right"
                SpatialPosition.CENTER -> "ahead"
                SpatialPosition.UNKNOWN -> ""
            }
            val text = if (dir.isNotBlank()) "Obstacle $dir: ${result.label}." else "Obstacle ahead."

            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Obstacle: ${result.label}",
                spokenText = text,
                confidence = result.score,
                priority = EventPriority.NAVIGATION,
                spatialPosition = pos,
                proximity = prox
            )
        }

        return null
    }

    /**
     * Optical floor specular reflection analysis for wet / slippery surface estimation.
     */
    fun evaluateFloorHazards(bitmap: Bitmap): PerceptionEvent? {
        val width = bitmap.width
        val height = bitmap.height

        val startY = (height * 0.65f).toInt()
        val endY = (height * 0.95f).toInt()
        val startX = (width * 0.20f).toInt()
        val endX = (width * 0.80f).toInt()

        var brightPixelCount = 0
        var totalSampled = 0
        var meanIntensity = 0.0

        val step = 8
        for (y in startY until endY step step) {
            for (x in startX until endX step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b)

                meanIntensity += luminance
                if (luminance > 225) brightPixelCount++
                totalSampled++
            }
        }

        if (totalSampled > 0) {
            meanIntensity /= totalSampled
            val ratio = brightPixelCount.toFloat() / totalSampled

            // Specular-highlight ratio is a weak cue (also fires on glossy tile, sun glare,
            // polished floors). Surface it as an advisory at navigation priority, not a warning.
            if (ratio in 0.14f..0.45f && meanIntensity in 140.0..220.0) {
                return PerceptionEvent(
                    type = PerceptionType.DANGER,
                    label = "Reflective Floor",
                    spokenText = "The floor ahead looks reflective — it may be wet. Please check.",
                    confidence = 0.5f,
                    priority = EventPriority.NAVIGATION,
                    spatialPosition = SpatialPosition.CENTER,
                    proximity = ProximityLevel.NEARBY
                )
            }
        }

        return null
    }
}
