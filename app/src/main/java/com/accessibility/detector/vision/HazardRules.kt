package com.accessibility.detector.vision

object HazardRules {

    val VEHICLE_LABELS = setOf(
        "car", "bus", "truck", "motorcycle", "bicycle", "train"
    )

    val OBSTACLE_LABELS = setOf(
        "chair", "couch", "bed", "dining table", "bench", "fire hydrant",
        "stop sign", "potted plant", "suitcase", "trash can", "backpack"
    )

    val PEDESTRIAN_LABELS = setOf(
        "person"
    )

    val FIRE_LABELS = setOf(
        "fire", "flame", "smoke", "lighter", "match"
    )

    fun isVehicle(label: String): Boolean = VEHICLE_LABELS.contains(label.lowercase())
    fun isObstacle(label: String): Boolean = OBSTACLE_LABELS.contains(label.lowercase())
    fun isPedestrian(label: String): Boolean = PEDESTRIAN_LABELS.contains(label.lowercase())
    fun isFireOrSmoke(label: String): Boolean = FIRE_LABELS.contains(label.lowercase())
}
