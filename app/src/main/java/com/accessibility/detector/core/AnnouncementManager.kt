package com.accessibility.detector.core

import android.os.SystemClock
import android.util.Log
import com.accessibility.detector.detection.EventPriority
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType
import com.accessibility.detector.speech.TtsManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent Announcement System for SAHEY.
 * Enforces priority queuing, cooldown debouncing, speech preemption, and tactile haptic pairing.
 */
class AnnouncementManager(
    private val ttsManager: TtsManager,
    private val hapticManager: HapticManager,
    private val priorityManager: PriorityManager = PriorityManager(),
    private val onAnnouncementDispatched: ((event: PerceptionEvent) -> Unit)? = null
) {

    var isMuted: Boolean = false
        set(value) {
            field = value
            if (value) {
                ttsManager.stop()
            }
        }

    var isSafetyShieldMode: Boolean = false

    private val lastSpokenTimestamps = ConcurrentHashMap<String, Long>()
    private var currentSpeakingPriority: Int = 0
    private var lastSpokenEvent: PerceptionEvent? = null

    // Configurable cooldowns
    private val sameObjectCooldownMs = 2500L
    private val dangerCooldownMs = 1800L
    private val textCooldownMs = 4000L
    private val signCooldownMs = 2200L
    private val soundCooldownMs = 2500L
    private val differentEventMinGapMs = 500L

    /**
     * Attempts to announce a perception event based on priority, cooldown, and safety mode.
     */
    @Synchronized
    fun postEvent(event: PerceptionEvent): Boolean {
        // In Safety Shield Mode, suppress standard low-priority objects (<= 30)
        if (isSafetyShieldMode && event.priority <= EventPriority.OBJECT) {
            return false
        }

        val currentTime = SystemClock.uptimeMillis()
        val eventKey = "${event.type.name}_${event.label.lowercase()}"
        val lastTime = lastSpokenTimestamps[eventKey] ?: 0L
        val timeSinceLastSame = currentTime - lastTime

        val requiredCooldown = when (event.type) {
            PerceptionType.DANGER -> dangerCooldownMs
            PerceptionType.TEXT -> textCooldownMs
            PerceptionType.SIGN -> signCooldownMs
            PerceptionType.SOUND -> soundCooldownMs
            else -> sameObjectCooldownMs
        }

        // Check if same item is within cooldown
        val isSameItemInCooldown = timeSinceLastSame < requiredCooldown

        // Check if high-priority hazard should preempt currently playing speech
        val shouldPreempt = event.priority >= EventPriority.DANGER &&
                priorityManager.shouldInterrupt(currentSpeakingPriority, event.priority)

        if (isSameItemInCooldown && !shouldPreempt) {
            return false
        }

        // Check minimum gap between different announcements
        val lastEventTime = lastSpokenEvent?.timestamp ?: 0L
        val timeSinceLastEvent = currentTime - lastEventTime
        if (timeSinceLastEvent < differentEventMinGapMs && !shouldPreempt) {
            return false
        }

        // Dispatch announcement
        dispatchAnnouncement(event, shouldPreempt)

        lastSpokenTimestamps[eventKey] = currentTime
        lastSpokenEvent = event
        currentSpeakingPriority = event.priority

        return true
    }

    private fun dispatchAnnouncement(event: PerceptionEvent, interruptCurrent: Boolean) {
        // 1. Play tactile vibration
        when (event.type) {
            PerceptionType.DANGER -> {
                if (event.priority >= EventPriority.CRITICAL) {
                    hapticManager.playCriticalSosPattern()
                } else {
                    hapticManager.playDangerPattern()
                }
            }
            PerceptionType.SIGN -> hapticManager.playSignConfirmation()
            PerceptionType.TEXT -> hapticManager.playTextCapturePulse()
            PerceptionType.SOUND -> hapticManager.playImportantPulse()
            else -> hapticManager.playNormalPulse()
        }

        // 2. Speak if not muted
        if (!isMuted) {
            ttsManager.speak(event.spokenText, interrupt = interruptCurrent)
        }

        // 3. Notify UI
        onAnnouncementDispatched?.invoke(event)
        Log.d(TAG, "Dispatched [${event.type}] (Pri: ${event.priority}): ${event.spokenText}")
    }

    fun stopAll() {
        ttsManager.stop()
        currentSpeakingPriority = 0
    }

    companion object {
        private const val TAG = "AnnouncementManager"
    }
}
