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
 * Combines SSD object hazards, FireSmoke chromatic pre-filtering, and triggers Gemini deep verification.
 */
class DangerDetectionEngine(
    private val listener: DangerDetectionListener,
    val riskAssessment: RiskAssessment = RiskAssessment(),
    val fireSmokeDetector: FireSmokeDetector = FireSmokeDetector()
) {

    private var consecutiveFireFrames = 0
    private val requiredFireFrames = 2 // Temporal confirmation to prevent single-frame flickering

    fun analyzeHazards(results: List<DetectionResult>, bitmap: Bitmap? = null) {
        // 1. Analyze Object-based Hazards (Vehicles, Stairs, Drop edges, Obstacles)
        for (result in results) {
            val hazardEvent = riskAssessment.evaluateObjectRisk(result)
            if (hazardEvent != null) {
                listener.onHazardDetected(hazardEvent)
                return
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

                    // Trigger Gemini deep verification + immediate local safety event
                    listener.onPotentialHazardPreFiltered(hint, fireResult.isInsideScreen)

                    if (fireResult.isInsideScreen) {
                        listener.onHazardDetected(
                            PerceptionEvent(
                                type = PerceptionType.DANGER,
                                label = "Screen Fire",
                                spokenText = "Fire visible on the screen.",
                                confidence = fireResult.fireConfidence,
                                priority = EventPriority.DANGER,
                                spatialPosition = SpatialPosition.CENTER
                            )
                        )
                    } else if (fireResult.hasFireVisualCues) {
                        listener.onHazardDetected(
                            PerceptionEvent(
                                type = PerceptionType.DANGER,
                                label = "Fire Hazard",
                                spokenText = "Warning. Fire detected.",
                                confidence = fireResult.fireConfidence,
                                priority = EventPriority.CRITICAL,
                                spatialPosition = SpatialPosition.CENTER
                            )
                        )
                    } else {
                        listener.onHazardDetected(
                            PerceptionEvent(
                                type = PerceptionType.DANGER,
                                label = "Smoke Hazard",
                                spokenText = "Warning. Smoke detected.",
                                confidence = 0.85f,
                                priority = EventPriority.DANGER,
                                spatialPosition = SpatialPosition.CENTER
                            )
                        )
                    }
                    return
                }
            } else {
                consecutiveFireFrames = 0
            }

            // 3. Surface-based Slippery/Wet Floor cues
            val surfaceEvent = riskAssessment.evaluateFloorHazards(bitmap)
            if (surfaceEvent != null) {
                listener.onHazardDetected(surfaceEvent)
            }
        }
    }
}
