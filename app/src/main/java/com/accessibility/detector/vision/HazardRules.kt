package com.accessibility.detector.vision

/**
 * Visual hazard classification rules for SAHEY Vision Assist.
 */
object HazardRules {

    val VEHICLE_LABELS = setOf(
        "car", "bus", "truck", "motorcycle", "bicycle", "train", "scooter"
    )

    val OBSTACLE_LABELS = setOf(
        "chair", "couch", "bed", "dining table", "bench", "fire hydrant",
        "stop sign", "potted plant", "suitcase", "trash can", "backpack", "traffic light"
    )

    val PEDESTRIAN_LABELS = setOf(
        "person"
    )

    val FIRE_LABELS = setOf(
        "fire", "flame", "smoke", "lighter", "match", "candle"
    )

    val STAIRS_LABELS = setOf(
        "stairs", "step", "staircase", "escalator"
    )

    val DROP_EDGE_LABELS = setOf(
        "drop", "edge", "cliff", "hole", "curb"
    )

    fun isVehicle(label: String): Boolean = VEHICLE_LABELS.contains(label.lowercase())
    fun isObstacle(label: String): Boolean = OBSTACLE_LABELS.contains(label.lowercase())
    fun isPedestrian(label: String): Boolean = PEDESTRIAN_LABELS.contains(label.lowercase())
    fun isFireOrSmoke(label: String): Boolean = FIRE_LABELS.contains(label.lowercase())
    fun isStairs(label: String): Boolean = STAIRS_LABELS.contains(label.lowercase())
    fun isDropOrEdge(label: String): Boolean = DROP_EDGE_LABELS.contains(label.lowercase())
}
