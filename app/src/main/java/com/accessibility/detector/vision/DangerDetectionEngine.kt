package com.accessibility.detector.vision

import android.graphics.Bitmap
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.PerceptionEvent

interface DangerDetectionListener {
    fun onHazardDetected(hazardEvent: PerceptionEvent)
}

/**
 * Intelligent Danger & Hazard Detection Engine for Category 1: Vision Assist.
 */
class DangerDetectionEngine(
    private val listener: DangerDetectionListener,
    private val riskAssessment: RiskAssessment = RiskAssessment()
) {

    fun analyzeHazards(results: List<DetectionResult>, bitmap: Bitmap? = null) {
        // 1. Analyze Object-based Hazards
        for (result in results) {
            val hazardEvent = riskAssessment.evaluateRisk(result)
            if (hazardEvent != null) {
                listener.onHazardDetected(hazardEvent)
                return
            }
        }

        // 2. Analyze Surface-based Slippery/Wet Floor cues
        if (bitmap != null) {
            val surfaceEvent = riskAssessment.evaluateSlipperyFloor(bitmap)
            if (surfaceEvent != null) {
                listener.onHazardDetected(surfaceEvent)
            }
        }
    }
}
