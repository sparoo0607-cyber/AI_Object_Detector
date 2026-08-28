package com.accessibility.detector.vision

import android.graphics.Bitmap
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.PerceptionEvent

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

        // 2. Fire / Flame / Smoke PRE-FILTER only.
        //    The on-device chromatic filter is not reliable enough to announce a hazard on
        //    its own, so it never emits a spoken event here. It only nominates a suspicious
        //    frame for verification by [GeminiVisionEngine], which is the single source of
        //    the fire/smoke announcement (Gemini-confirmed = definitive & CRITICAL;
        //    not configured = an advisory "please verify" phrase at reduced priority).
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
                    listener.onPotentialHazardPreFiltered(hint, fireResult.isInsideScreen)
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
