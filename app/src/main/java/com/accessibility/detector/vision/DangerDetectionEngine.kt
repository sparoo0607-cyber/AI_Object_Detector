package com.accessibility.detector.vision

import android.graphics.Bitmap
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.PerceptionEvent

interface DangerDetectionListener {
    fun onHazardDetected(hazardEvent: PerceptionEvent)
}

/**
 * Intelligent Danger & Hazard Detection Engine for Category 1: Vision Assist.
 * Pre-filters hazards locally and alerts user immediately.
 */
class DangerDetectionEngine(
    private val listener: DangerDetectionListener,
    val riskAssessment: RiskAssessment = RiskAssessment()
) {

    fun analyzeHazards(results: List<DetectionResult>, bitmap: Bitmap? = null) {
        // 1. Analyze Object-based Hazards (Vehicles, Fire, Stairs, Drop edges, Obstacles)
        for (result in results) {
            val hazardEvent = riskAssessment.evaluateObjectRisk(result)
            if (hazardEvent != null) {
                listener.onHazardDetected(hazardEvent)
                return
            }
        }

        // 2. Analyze Surface-based Slippery/Wet Floor cues
        if (bitmap != null) {
            val surfaceEvent = riskAssessment.evaluateFloorHazards(bitmap)
            if (surfaceEvent != null) {
                listener.onHazardDetected(surfaceEvent)
            }
        }
    }
}
