package com.accessibility.detector.core

import java.util.concurrent.ConcurrentHashMap

enum class SubsystemStatus {
    ACTIVE,
    STANDBY,
    INITIALIZING,
    UNAVAILABLE,
    MUTED
}

/**
 * Registry of all 7 AI perception subsystems in SAHEY.
 */
class ModelManager {

    private val subsystemStatuses = ConcurrentHashMap<String, SubsystemStatus>()

    init {
        subsystemStatuses[MOD_OBJECTS] = SubsystemStatus.STANDBY
        subsystemStatuses[MOD_DANGER] = SubsystemStatus.STANDBY
        subsystemStatuses[MOD_OCR] = SubsystemStatus.STANDBY
        subsystemStatuses[MOD_SIGN] = SubsystemStatus.STANDBY
        subsystemStatuses[MOD_SOUND] = SubsystemStatus.STANDBY
        subsystemStatuses[MOD_SPEECH] = SubsystemStatus.STANDBY
        subsystemStatuses[MOD_TRANSLATE] = SubsystemStatus.STANDBY
    }

    fun updateStatus(moduleName: String, status: SubsystemStatus) {
        subsystemStatuses[moduleName] = status
    }

    fun getStatus(moduleName: String): SubsystemStatus {
        return subsystemStatuses[moduleName] ?: SubsystemStatus.UNAVAILABLE
    }

    fun getAllStatuses(): Map<String, SubsystemStatus> = HashMap(subsystemStatuses)

    companion object {
        const val MOD_OBJECTS = "Object Detection"
        const val MOD_DANGER = "Danger Radar"
        const val MOD_OCR = "Text OCR Reader"
        const val MOD_SIGN = "Sign Language"
        const val MOD_SOUND = "Sound Awareness"
        const val MOD_SPEECH = "Speech Recognition"
        const val MOD_TRANSLATE = "Live Translation"
    }
}
