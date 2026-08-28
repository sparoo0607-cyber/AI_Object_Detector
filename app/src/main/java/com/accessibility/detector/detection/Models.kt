package com.accessibility.detector.detection

import android.graphics.RectF

enum class SpatialPosition {
    LEFT, CENTER, RIGHT, UNKNOWN
}

enum class ProximityLevel {
    VERY_CLOSE, NEARBY, AHEAD, DISTANT
}

enum class PerceptionType {
    OBJECT,
    DANGER,
    TEXT,
    SIGN,
    SOUND,
    SPEECH,
    TRANSLATION
}

object EventPriority {
    const val CRITICAL = 100
    const val DANGER = 80
    const val NAVIGATION = 70
    const val TEXT = 50
    const val SIGN = 50
    const val SOUND = 50
    const val OBJECT = 30
    const val BACKGROUND = 10
}

data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val score: Float,
    val type: PerceptionType = PerceptionType.OBJECT,
    val spatialPosition: SpatialPosition = SpatialPosition.UNKNOWN,
    val proximity: ProximityLevel = ProximityLevel.AHEAD,
    val customDescription: String? = null
)

data class PerceptionEvent(
    val id: String = System.currentTimeMillis().toString(),
    val type: PerceptionType,
    val label: String,
    val spokenText: String,
    val confidence: Float,
    val priority: Int,
    val spatialPosition: SpatialPosition = SpatialPosition.UNKNOWN,
    val proximity: ProximityLevel = ProximityLevel.AHEAD,
    val timestamp: Long = System.currentTimeMillis()
)
