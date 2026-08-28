package com.accessibility.detector.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Manages specialized tactile haptic vibration language for visually impaired users.
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

    fun playNormalPulse() {
        if (!isHapticsEnabled) return
        vibrate(40)
    }

    fun playImportantPulse() {
        if (!isHapticsEnabled) return
        vibratePattern(longArrayOf(0, 50, 60, 50), intArrayOf(0, 180, 0, 180))
    }

    fun playDangerPattern() {
        if (!isHapticsEnabled) return
        vibratePattern(longArrayOf(0, 120, 80, 120, 80, 150), intArrayOf(0, 255, 0, 255, 0, 255))
    }

    fun playCriticalSosPattern() {
        if (!isHapticsEnabled) return
        vibratePattern(
            longArrayOf(0, 80, 50, 80, 50, 80, 150, 200, 100, 200, 100, 200, 150, 80, 50, 80, 50, 80),
            intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255, 0, 255)
        )
    }

    fun playSignConfirmation() {
        if (!isHapticsEnabled) return
        vibrate(60)
    }

    fun playTextCapturePulse() {
        if (!isHapticsEnabled) return
        vibrate(50)
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
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
