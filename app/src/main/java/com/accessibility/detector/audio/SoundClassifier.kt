package com.accessibility.detector.audio

import com.accessibility.detector.detection.EventPriority
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType
import kotlin.math.abs
import kotlin.math.sqrt

data class SoundDetection(
    val label: String,
    val spokenWarning: String,
    val confidence: Float,
    val priority: Int
)

/**
 * Lightweight real-time acoustic pattern analyzer for safety-related sound classification.
 */
class SoundClassifier {

    private var sirenCounter = 0
    private var hornCounter = 0
    private var alarmCounter = 0

    fun classifyAudioBuffer(buffer: ShortArray, readSize: Int, sampleRate: Int = 16000): SoundDetection? {
        if (readSize <= 0) return null

        // 1. Calculate RMS energy (volume amplitude)
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

        // If environment is quiet, reset counters
        if (rms < 1200.0) {
            sirenCounter = maxOf(0, sirenCounter - 1)
            hornCounter = maxOf(0, hornCounter - 1)
            alarmCounter = maxOf(0, alarmCounter - 1)
            return null
        }

        // 2. Estimate dominant zero-crossing frequency
        val dominantFreq = (zeroCrossings * sampleRate) / (2.0 * readSize)

        // 3. Match Acoustic Profiles
        // Profile A: High Pitch Alarm / Smoke Detector (3000 Hz - 4500 Hz)
        if (dominantFreq in 2800.0..4800.0 && rms > 2200.0) {
            alarmCounter++
            if (alarmCounter >= 2) {
                alarmCounter = 0
                return SoundDetection(
                    label = "Smoke / Fire Alarm",
                    spokenWarning = "Warning! Smoke or Fire alarm sound detected!",
                    confidence = 0.88f,
                    priority = EventPriority.CRITICAL
                )
            }
        }

        // Profile B: Siren / Emergency Vehicle (700 Hz - 1800 Hz)
        if (dominantFreq in 700.0..1800.0 && rms > 2500.0) {
            sirenCounter++
            if (sirenCounter >= 2) {
                sirenCounter = 0
                return SoundDetection(
                    label = "Emergency Siren",
                    spokenWarning = "Emergency siren heard nearby!",
                    confidence = 0.85f,
                    priority = EventPriority.DANGER
                )
            }
        }

        // Profile C: Car Horn (400 Hz - 750 Hz, high intensity spike)
        if (dominantFreq in 400.0..750.0 && rms > 3200.0) {
            hornCounter++
            if (hornCounter >= 1) {
                hornCounter = 0
                return SoundDetection(
                    label = "Vehicle Horn",
                    spokenWarning = "Car horn detected nearby!",
                    confidence = 0.82f,
                    priority = EventPriority.DANGER
                )
            }
        }

        // Profile D: Loud Impact / Glass Breaking (> 5000 Hz, loud burst)
        if (dominantFreq > 5000.0 && rms > 4000.0) {
            return SoundDetection(
                label = "Glass Break / Impact",
                spokenWarning = "Loud impact or glass sound detected!",
                confidence = 0.78f,
                priority = EventPriority.NAVIGATION
            )
        }

        return null
    }
}
