package com.accessibility.detector.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * SAHAY ACTION ENGINE — executes a Decision Engine verdict: speaks,
 * shows a caption, vibrates, and/or raises a visual cue, with
 * priority interruption (a horn cuts off a signboard mid-sentence).
 * Kotlin port of core/action.js.
 */
class ActionEngine(private val context: Context) {

    interface Listener {
        fun onCaption(text: String, interim: Boolean)
        fun onVisual(decision: SahayDecision)
    }
    var listener: Listener? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speakingPriority = -1

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                applyLanguage(SahayConfig.defaultLanguage)
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { speakingPriority = -1 }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { speakingPriority = -1 }
                    override fun onError(utteranceId: String?, errorCode: Int) { speakingPriority = -1 }
                })
            }
        }
    }

    private fun localeFor(lang: String): Locale = when (lang) {
        "hi" -> Locale("hi", "IN")
        "te" -> Locale("te", "IN")
        else -> Locale.US
    }

    /** Returns false if this language isn't available on-device — the
     * honest signal the UI should show instead of silently speaking
     * in the wrong language. */
    fun applyLanguage(lang: String): Boolean {
        val t = tts ?: return false
        val result = t.setLanguage(localeFor(lang))
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun execute(decision: SahayDecision) {
        if (decision.suppressed) return
        val e = decision.event

        if (decision.channels.voice && e.content.isNotBlank()) {
            speak(e.content, e.language, decision.priority)
        }
        if (decision.channels.caption) {
            listener?.onCaption(e.content, e.interim)
        }
        if (decision.channels.haptic) {
            haptic(hapticLevelFor(decision.priority))
        }
        if (decision.channels.visual) {
            listener?.onVisual(decision)
        }
    }

    fun speak(text: String, lang: String, priority: Int) {
        val t = tts ?: return
        if (!ttsReady || text.isBlank()) return

        val currentlySpeaking = t.isSpeaking
        if (currentlySpeaking && priority < speakingPriority) return // strictly lower priority — don't interrupt
        if (currentlySpeaking && priority >= speakingPriority) t.stop()

        applyLanguage(lang)
        speakingPriority = priority
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SAHAY_${System.currentTimeMillis()}")
    }

    private fun hapticLevelFor(priority: Int) = when {
        priority >= 8 -> HapticLevel.HIGH
        priority >= 5 -> HapticLevel.MEDIUM
        else -> HapticLevel.LOW
    }
    enum class HapticLevel { LOW, MEDIUM, HIGH }

    fun haptic(level: HapticLevel) {
        val pattern = when (level) {
            HapticLevel.LOW -> longArrayOf(0, 80)
            HapticLevel.MEDIUM -> longArrayOf(0, 80, 60, 80)
            HapticLevel.HIGH -> longArrayOf(0, 120, 60, 120, 60, 120)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (ex: Exception) {
            Log.w("ActionEngine", "Haptic feedback failed", ex)
        }
    }

    fun shutdown() {
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { /* ignore */ }
    }
}
