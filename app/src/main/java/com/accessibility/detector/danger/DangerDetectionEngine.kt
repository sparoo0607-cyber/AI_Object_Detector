package com.accessibility.detector.danger

import com.accessibility.detector.detection.DetectionResult
import com.accessibility.detector.detection.PerceptionEvent

interface DangerDetectionListener {
    fun onHazardDetected(hazardEvent: PerceptionEvent)
}

/**
 * Intelligent Danger & Hazard Detection Engine for mobility safety.
 */
class DangerDetectionEngine(
    private val listener: DangerDetectionListener,
    private val riskAssessment: RiskAssessment = RiskAssessment()
) {

    fun analyzeHazards(results: List<DetectionResult>) {
        for (result in results) {
            val hazardEvent = riskAssessment.evaluateRisk(result)
            if (hazardEvent != null) {
                listener.onHazardDetected(hazardEvent)
                break // Announce highest risk hazard per frame
            }
        }
    }
}
