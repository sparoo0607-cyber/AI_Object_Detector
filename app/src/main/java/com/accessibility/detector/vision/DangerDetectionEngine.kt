package com.accessibility.detector.vision

import android.graphics.Bitmap
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType
import com.accessibility.detector.core.SpatialPosition

interface DangerDetectionListener {
    fun onHazardDetected(hazardEvent: PerceptionEvent)
    fun onPotentialHazardPreFiltered(hazardHint: String, isScreenFire: Boolean)
}

/**
 * Intelligent Danger & Hazard Detection Engine for Category 1: Vision Assist.
 * Evaluates all danger types (Vehicles, Stairs, Drops, Obstacles, Slippery Floors, Fire/Smoke)
 * without starvation or getting stuck on one single hazard.
 */
class DangerDetectionEngine(
    private val listener: DangerDetectionListener,
    val riskAssessment: RiskAssessment = RiskAssessment(),
    val fireSmokeDetector: FireSmokeDetector = FireSmokeDetector()
) {

    private var consecutiveFireFrames = 0
    private val requiredFireFrames = 3 // Requires 3 consecutive confirmed frames to eliminate false triggers

    fun analyzeHazards(results: List<DetectionResult>, bitmap: Bitmap? = null) {
        var topHazardEvent: PerceptionEvent? = null

        // 1. Analyze Object-based Hazards (Vehicles, Stairs, Drop edges, Obstacles)
        for (result in results) {
            val hazardEvent = riskAssessment.evaluateObjectRisk(result)
            if (hazardEvent != null) {
                // Keep the highest priority object hazard (e.g. Critical vehicle > Navigation stairs)
                if (topHazardEvent == null || hazardEvent.priority > topHazardEvent.priority) {
                    topHazardEvent = hazardEvent
                }
            }
        }

        // 2. Fire, Flame, and Smoke Pre-Filter (Full frame + Screen ROI analysis)
        if (bitmap != null) {
            val fireResult = fireSmokeDetector.analyzeFrame(bitmap, results)

            if (fireResult.hasFireVisualCues || fireResult.hasSmokeVisualCues) {
                consecutiveFireFrames++

                if (consecutiveFireFrames >= requiredFireFrames) {
                    val hint = when {
                        fireResult.isInsideScreen -> "fire_on_screen"
                        fireResult.hasFireVisualCues -> "fire"
                        else -> "smoke"
                    }

                    // Trigger Gemini deep visual verification
                    listener.onPotentialHazardPreFiltered(hint, fireResult.isInsideScreen)

                    val fireEvent = if (fireResult.isInsideScreen) {
                        PerceptionEvent(
                            type = PerceptionType.DANGER,
                            label = "Screen Fire",
                            spokenText = "Fire visible on the screen.",
                            confidence = fireResult.fireConfidence,
                            priority = EventPriority.DANGER,
                            spatialPosition = SpatialPosition.CENTER
                        )
                    } else if (fireResult.hasFireVisualCues) {
                        PerceptionEvent(
                            type = PerceptionType.DANGER,
                            label = "Fire Hazard",
                            spokenText = "Warning. Fire detected.",
                            confidence = fireResult.fireConfidence,
                            priority = EventPriority.CRITICAL,
                            spatialPosition = SpatialPosition.CENTER
                        )
                    } else {
                        PerceptionEvent(
                            type = PerceptionType.DANGER,
                            label = "Smoke Hazard",
                            spokenText = "Warning. Smoke detected.",
                            confidence = 0.85f,
                            priority = EventPriority.DANGER,
                            spatialPosition = SpatialPosition.CENTER
                        )
                    }

                    if (topHazardEvent == null || fireEvent.priority >= topHazardEvent.priority) {
                        topHazardEvent = fireEvent
                    }
                }
            } else {
                consecutiveFireFrames = 0
            }

            // 3. Surface-based Slippery/Wet Floor cues
            val surfaceEvent = riskAssessment.evaluateFloorHazards(bitmap)
            if (surfaceEvent != null) {
                if (topHazardEvent == null || surfaceEvent.priority > topHazardEvent.priority) {
                    topHazardEvent = surfaceEvent
                }
            }
        }

        // Dispatch the active top hazard if one was detected
        if (topHazardEvent != null) {
            listener.onHazardDetected(topHazardEvent)
        }
    }
}
