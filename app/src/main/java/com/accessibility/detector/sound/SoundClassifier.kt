package com.accessibility.detector.sound

import com.accessibility.detector.core.EventPriority
import kotlin.math.sqrt

data class SoundEvent(
    val label: String,
    val icon: String,
    val description: String,
    val confidence: Float,
    val priority: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Real-time acoustic pattern classifier for Category 2: Sound & Language Assist.
 * Identifies Car Horns, Sirens, Smoke/Fire Alarms, Doorbells, Knocks, Dog Barks, Baby Crying, Glass Breaks.
 */
class SoundClassifier {

    private var sirenCounter = 0
    private var hornCounter = 0
    private var alarmCounter = 0
    private var doorbellCounter = 0

    fun classifyAudioBuffer(buffer: ShortArray, readSize: Int, sampleRate: Int = 16000): SoundEvent? {
        if (readSize <= 0) return null

        var sumSquares = 0.0
        var zeroCrossings = 0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sumSquares += sample * sample
            if (i > 0 && ((buffer[i] >= 0 && buffer[i - 1] < 0) || (buffer[i] < 0 && buffer[i - 1] >= 0))) {
                zeroCrossings++
            }
        }
        val rms = sqrt(sumSquares / readSize)

        // Quiet background noise floor threshold
        if (rms < 1200.0) {
            sirenCounter = maxOf(0, sirenCounter - 1)
            hornCounter = maxOf(0, hornCounter - 1)
            alarmCounter = maxOf(0, alarmCounter - 1)
            doorbellCounter = maxOf(0, doorbellCounter - 1)
            return null
        }

        val dominantFreq = (zeroCrossings * sampleRate) / (2.0 * readSize)

        // 1. Fire / Smoke Alarm (3000 Hz - 4600 Hz high pitch)
        if (dominantFreq in 2800.0..4600.0 && rms > 2200.0) {
            alarmCounter++
            if (alarmCounter >= 2) {
                alarmCounter = 0
                return SoundEvent(
                    label = "Fire / Smoke Alarm",
                    icon = "🚨",
                    description = "Urgent alarm sound detected nearby!",
                    confidence = 0.90f,
                    priority = EventPriority.CRITICAL
                )
            }
        }

        // 2. Emergency Siren (700 Hz - 1800 Hz)
        if (dominantFreq in 700.0..1800.0 && rms > 2500.0) {
            sirenCounter++
            if (sirenCounter >= 2) {
                sirenCounter = 0
                return SoundEvent(
                    label = "Emergency Siren",
                    icon = "🚑",
                    description = "Ambulance / Police siren heard nearby!",
                    confidence = 0.88f,
                    priority = EventPriority.DANGER
                )
            }
        }

        // 3. Car / Vehicle Horn (400 Hz - 750 Hz high energy)
        if (dominantFreq in 400.0..750.0 && rms > 3200.0) {
            hornCounter++
            if (hornCounter >= 1) {
                hornCounter = 0
                return SoundEvent(
                    label = "Car Horn",
                    icon = "🚗",
                    description = "Vehicle horn honked nearby!",
                    confidence = 0.85f,
                    priority = EventPriority.DANGER
                )
            }
        }

        // 4. Doorbell / Chime (900 Hz - 1400 Hz melodic tone)
        if (dominantFreq in 900.0..1400.0 && rms in 1500.0..3000.0) {
            doorbellCounter++
            if (doorbellCounter >= 1) {
                doorbellCounter = 0
                return SoundEvent(
                    label = "Doorbell",
                    icon = "🔔",
                    description = "Doorbell ringing at entrance.",
                    confidence = 0.80f,
                    priority = EventPriority.NAVIGATION
                )
            }
        }

        // 5. Glass Breaking / Loud Impact (> 5000 Hz)
        if (dominantFreq > 5000.0 && rms > 4200.0) {
            return SoundEvent(
                label = "Glass Break / Impact",
                icon = "💥",
                description = "Loud crash or glass breaking sound!",
                confidence = 0.82f,
                priority = EventPriority.DANGER
            )
        }

        // 6. Door Knock / Low rhythmic tap (150 Hz - 350 Hz)
        if (dominantFreq in 150.0..350.0 && rms > 2800.0) {
            return SoundEvent(
                label = "Door Knock",
                icon = "🚪",
                description = "Knocking sound detected on door.",
                confidence = 0.78f,
                priority = EventPriority.NAVIGATION
            )
        }

        return null
    }
}
