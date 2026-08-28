package com.accessibility.detector.danger

object HazardRules {

    val VEHICLE_LABELS = setOf(
        "car", "bus", "truck", "motorcycle", "bicycle", "train"
    )

    val OBSTACLE_LABELS = setOf(
        "chair", "couch", "bed", "dining table", "bench", "fire hydrant",
        "stop sign", "potted plant", "suitcase", "trash can"
    )

    val PEDESTRIAN_LABELS = setOf(
        "person"
    )

    fun isVehicle(label: String): Boolean = VEHICLE_LABELS.contains(label.lowercase())

    fun isObstacle(label: String): Boolean = OBSTACLE_LABELS.contains(label.lowercase())

    fun isPedestrian(label: String): Boolean = PEDESTRIAN_LABELS.contains(label.lowercase())
}
