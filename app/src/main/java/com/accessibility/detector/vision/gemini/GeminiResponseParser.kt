package com.accessibility.detector.vision.gemini

import android.util.Log
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType
import com.accessibility.detector.core.SpatialPosition
import org.json.JSONObject

data class GeminiReasoningResult(
    val dangerDetected: Boolean,
    val dangerType: String,
    val priority: Int,
    val direction: SpatialPosition,
    val confidence: Float,
    val message: String
)

/**
 * Validates and parses structured JSON responses from Gemini Multimodal API.
 */
object GeminiResponseParser {

    private const val TAG = "GeminiResponseParser"

    fun parse(rawResponse: String): GeminiReasoningResult? {
        try {
            // Clean up any potential markdown code fence wrapping ```json ... ```
            var cleanJson = rawResponse.trim()
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.removePrefix("```json").trim()
            }
            if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.removePrefix("```").trim()
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.removeSuffix("```").trim()
            }

            val startIndex = cleanJson.indexOf("{")
            val endIndex = cleanJson.lastIndexOf("}")
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                cleanJson = cleanJson.substring(startIndex, endIndex + 1)
            }

            val root = JSONObject(cleanJson)

            val dangerDetected = root.optBoolean("danger_detected", false)
            val dangerType = root.optString("danger_type", "none").lowercase()
            val priorityStr = root.optString("priority", "normal").lowercase()
            val directionStr = root.optString("direction", "unknown").lowercase()
            val confidence = root.optDouble("confidence", 0.85).toFloat()
            val message = root.optString("message", "").trim()

            val priority = when (priorityStr) {
                "critical" -> EventPriority.CRITICAL
                "high" -> EventPriority.DANGER
                "hazard" -> EventPriority.NAVIGATION
                else -> if (dangerDetected) EventPriority.DANGER else EventPriority.OBJECT
            }

            val direction = when {
                directionStr.contains("left") -> SpatialPosition.LEFT
                directionStr.contains("right") -> SpatialPosition.RIGHT
                directionStr.contains("front") || directionStr.contains("ahead") -> SpatialPosition.CENTER
                else -> SpatialPosition.UNKNOWN
            }

            if (message.isBlank()) {
                return null
            }

            return GeminiReasoningResult(
                dangerDetected = dangerDetected,
                dangerType = dangerType,
                priority = priority,
                direction = direction,
                confidence = confidence,
                message = message
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse Gemini structured JSON: ${e.message}\nRaw: $rawResponse")
            return null
        }
    }

    fun toPerceptionEvent(result: GeminiReasoningResult): PerceptionEvent {
        return PerceptionEvent(
            type = if (result.dangerDetected) PerceptionType.DANGER else PerceptionType.OBJECT,
            label = "AI Reasoning: ${result.dangerType}",
            spokenText = result.message,
            confidence = result.confidence,
            priority = result.priority,
            spatialPosition = result.direction
        )
    }
}
