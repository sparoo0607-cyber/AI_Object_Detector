package com.accessibility.detector.danger

import com.accessibility.detector.detection.DetectionResult
import com.accessibility.detector.detection.EventPriority
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType
import com.accessibility.detector.detection.ProximityLevel
import com.accessibility.detector.detection.SpatialPosition

/**
 * Assesses spatial risk, proximity, and approach dynamics to generate natural speech alerts.
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

        // 1. Vehicle Hazards
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
                "Warning! ${result.label} approaching $directionPhrase, very close!"
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

        // 2. Direct Obstacle Hazards (Center path obstruction)
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

        // 3. Pedestrian Crossing/Ahead
        if (isPedestrian && prox == ProximityLevel.VERY_CLOSE) {
            val directionPhrase = when (pos) {
                SpatialPosition.LEFT -> "on your left"
                SpatialPosition.RIGHT -> "on your right"
                else -> "directly ahead"
            }
            return PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Person ($directionPhrase)",
                spokenText = "Person $directionPhrase, close to you.",
                confidence = result.score,
                priority = EventPriority.NAVIGATION,
                spatialPosition = pos,
                proximity = prox
            )
        }

        return null
    }
}
