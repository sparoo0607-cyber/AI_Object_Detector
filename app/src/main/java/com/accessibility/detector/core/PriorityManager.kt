package com.accessibility.detector.core

/**
 * Priority Manager for the SAHEY AI Multimodal Platform.
 * Assigns priorities and decides speech preemption rules.
 */
class PriorityManager {

    /**
     * Determines whether incomingEvent has high enough priority to interrupt currentSpeakingEvent.
     */
    fun shouldInterrupt(currentPriority: Int, incomingPriority: Int): Boolean {
        // High priority hazards (>= 80) always preempt normal objects / background speech (<= 50)
        return incomingPriority >= EventPriority.DANGER && incomingPriority > currentPriority
    }

    /**
     * Categorizes an event into priority tier based on PerceptionType and contextual risk.
     */
    fun calculatePriority(type: PerceptionType, isImminentHazard: Boolean = false): Int {
        return when {
            isImminentHazard -> EventPriority.CRITICAL
            type == PerceptionType.DANGER -> EventPriority.DANGER
            type == PerceptionType.TRANSLATION -> EventPriority.NAVIGATION
            type == PerceptionType.TEXT -> EventPriority.TEXT
            type == PerceptionType.SIGN -> EventPriority.SIGN
            type == PerceptionType.SOUND -> EventPriority.SOUND
            type == PerceptionType.SPEECH -> EventPriority.SPEECH
            type == PerceptionType.OBJECT -> EventPriority.OBJECT
            else -> EventPriority.BACKGROUND
        }
    }
}
