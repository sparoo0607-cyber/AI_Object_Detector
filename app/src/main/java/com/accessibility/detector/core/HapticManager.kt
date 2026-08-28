package com.accessibility.detector.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Advanced Multi-Pattern Tactile Vibration Engine for SAHEY.
 * Provides distinct, highly recognizable vibration signatures for different alert categories:
 * - Critical Danger (Urgent SOS heavy buzz)
 * - Navigation Hazard (Strong double pulse)
 * - Acoustic Sound Alert (Rapid staccato buzz)
 * - OCR Text Detected (Crisp double click)
 * - Sign Language Recognized (Ascending confirmation pulse)
 * - Translation / Speech (Gentle tap)
 * - Normal Object (Light micro-pulse)
 */
class HapticManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    var isHapticsEnabled: Boolean = true

    /**
     * 1. Critical Danger: Approaching vehicle, fire alarm, collision.
     * Heavy Urgent SOS: 3 short, 3 long, 3 short with MAX amplitude (255).
     */
    fun playCriticalSosPattern() {
        if (!isHapticsEnabled) return
        vibratePattern(
            longArrayOf(0, 150, 70, 150, 70, 150, 150, 350, 100, 350, 100, 350, 150, 150, 70, 150, 70, 150),
            intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255)
        )
    }

    /**
     * 2. Danger / Hazard Warning: Vehicle nearby, obstacle directly in path.
     * Heavy Double Pulse: 2 strong 180ms pulses with 100ms gap.
     */
    fun playDangerPattern() {
        if (!isHapticsEnabled) return
        vibratePattern(
            longArrayOf(0, 180, 100, 220),
            intArrayOf(0, 240, 0, 255)
        )
    }

    /**
     * 3. Acoustic Sound Alert: Siren, car horn, smoke alarm.
     * Rapid Staccato Alarm Flutter: 4 rapid 90ms bursts.
     */
    fun playSoundAlertPattern() {
        if (!isHapticsEnabled) return
        vibratePattern(
            longArrayOf(0, 90, 60, 90, 60, 90, 60, 120),
            intArrayOf(0, 220, 0, 220, 0, 220, 0, 255)
        )
    }

    /**
     * 4. OCR / Printed Text Captured.
     * Double Click Pulse: 2 crisp sharp clicks (50ms - 80ms).
     */
    fun playTextCapturePulse() {
        if (!isHapticsEnabled) return
        vibratePattern(
            longArrayOf(0, 60, 70, 80),
            intArrayOf(0, 180, 0, 220)
        )
    }

    /**
     * 5. Sign Language Gesture Recognized.
     * Harmonic Ascending Pulse: 70ms then 140ms.
     */
    fun playSignConfirmation() {
        if (!isHapticsEnabled) return
        vibratePattern(
            longArrayOf(0, 70, 60, 140),
            intArrayOf(0, 160, 0, 230)
        )
    }

    /**
     * 6. Translation / Speech Event.
     * Melodic Triple Tap: 3 soft rhythmic pulses.
     */
    fun playTranslationPulse() {
        if (!isHapticsEnabled) return
        vibratePattern(
            longArrayOf(0, 50, 50, 50, 50, 80),
            intArrayOf(0, 150, 0, 180, 0, 210)
        )
    }

    /**
     * 7. Normal Object Detected.
     * Subtle Micro-Pulse: 40ms light tap.
     */
    fun playNormalPulse() {
        if (!isHapticsEnabled) return
        vibrate(45)
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, 180))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun vibratePattern(timings: LongArray, amplitudes: IntArray) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration pattern failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "HapticManager"
    }
}
