package com.accessibility.detector.core

import android.content.Context
import android.content.SharedPreferences

/**
 * SAHAY CORE — Central Configuration.
 *
 * Single source of truth for the whole app, mirroring shared/config.js
 * from the SAHAY web prototype. SharedPreferences plays the role
 * localStorage played on the web: both HomeActivity/MainActivity/
 * ListenActivity (the "Client") and AdminActivity read the same store,
 * so a change saved in Admin is visible the next time any screen reads
 * a value — no hardcoded thresholds anywhere else in the app.
 */
object SahayConfig {

    private const val PREFS = "sahay_config"
    private lateinit var prefs: SharedPreferences

    // ---- defaults (mirrors shared/config.js DEFAULT_CONFIG) ----
    object Defaults {
        const val LANGUAGE = "en" // "en" | "hi" | "te"
        const val PROFILE = "universal" // "visual" | "hearing" | "universal"
        const val ENVIRONMENT = "demo" // "demo" | "live"

        const val FEATURE_OBJECTS = true
        const val FEATURE_OCR = true
        const val FEATURE_CURRENCY = true
        const val FEATURE_CAPTIONS = true
        const val FEATURE_SOUND_ALERTS = true
        // ON by default: Gemini is now the sole voice output — all local
        // models feed their results into Gemini which speaks in Telugu.
        const val FEATURE_ONLINE_ENHANCEMENT = true

        const val THRESH_OCR_LOW = 0.35f
        const val THRESH_OCR_MEDIUM = 0.55f
        const val THRESH_CURRENCY_LOW = 0.55f
        const val THRESH_SOUND_LOW = 0.40f
        const val THRESH_SOUND_MEDIUM = 0.55f

        // Attention priorities (1-10) and cooldowns (ms) — same table as
        // shared/config.js `attention`.
        val PRIORITY = mapOf(
            "object_detected" to 3,
            "text_detected" to 4,
            "currency_detected" to 4,
            "speech_detected" to 3,
            "sound_soft" to 1,
            "sound_sustained" to 7,
            "sound_impulsive" to 9,
            "low_confidence" to 2,
        )
        val COOLDOWN_MS = mapOf(
            "object_detected" to 2500L,
            "text_detected" to 4000L,
            "currency_detected" to 4000L,
            "speech_detected" to 0L,
            "sound_soft" to 6000L,
            "sound_sustained" to 5000L,
            "sound_impulsive" to 3000L,
            "low_confidence" to 3000L,
        )
        // default output channels per event type: voice, caption, haptic, visual
        val CHANNELS = mapOf(
            "object_detected" to booleanArrayOf(true, false, false, true),
            "text_detected" to booleanArrayOf(true, false, false, true),
            "currency_detected" to booleanArrayOf(true, false, false, true),
            "speech_detected" to booleanArrayOf(false, true, false, false),
            "sound_soft" to booleanArrayOf(false, false, false, false),
            "sound_sustained" to booleanArrayOf(true, false, true, true),
            "sound_impulsive" to booleanArrayOf(true, false, true, true),
            "low_confidence" to booleanArrayOf(true, false, false, true),
        )
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // ---- language ----
    var defaultLanguage: String
        get() = prefs.getString("language_default", Defaults.LANGUAGE) ?: Defaults.LANGUAGE
        set(v) { prefs.edit().putString("language_default", v).apply(); audit("Default language set to $v") }

    // ---- profile / environment ----
    var profile: String
        get() = prefs.getString("profile", Defaults.PROFILE) ?: Defaults.PROFILE
        set(v) { prefs.edit().putString("profile", v).apply(); audit("Accessibility profile set to $v") }

    var environment: String
        get() = prefs.getString("environment", Defaults.ENVIRONMENT) ?: Defaults.ENVIRONMENT
        set(v) { prefs.edit().putString("environment", v).apply(); audit("Environment switched to $v") }

    // ---- feature flags ----
    fun isFeatureEnabled(key: String): Boolean = prefs.getBoolean("feature_$key", when (key) {
        "objects" -> Defaults.FEATURE_OBJECTS
        "ocr" -> Defaults.FEATURE_OCR
        "currency" -> Defaults.FEATURE_CURRENCY
        "captions" -> Defaults.FEATURE_CAPTIONS
        "soundAlerts" -> Defaults.FEATURE_SOUND_ALERTS
        "onlineEnhancement" -> Defaults.FEATURE_ONLINE_ENHANCEMENT
        else -> true
    })
    fun setFeatureEnabled(key: String, enabled: Boolean) {
        prefs.edit().putBoolean("feature_$key", enabled).apply()
        audit("Feature \"$key\" ${if (enabled) "enabled" else "disabled"}")
    }

    // ---- thresholds (stored 0-100 int for SeekBar convenience, read as 0-1 float) ----
    fun getThreshold(key: String): Float {
        val default = when (key) {
            "ocr_low" -> Defaults.THRESH_OCR_LOW
            "ocr_medium" -> Defaults.THRESH_OCR_MEDIUM
            "currency_low" -> Defaults.THRESH_CURRENCY_LOW
            "sound_low" -> Defaults.THRESH_SOUND_LOW
            "sound_medium" -> Defaults.THRESH_SOUND_MEDIUM
            else -> 0.5f
        }
        return prefs.getInt("thresh_$key", (default * 100).toInt()) / 100f
    }
    fun setThreshold(key: String, value01: Float) {
        prefs.edit().putInt("thresh_$key", (value01 * 100).toInt()).apply()
        audit("Threshold $key set to ${(value01 * 100).toInt()}%")
    }

    // ---- attention: priority + cooldown per event type ----
    fun getPriority(eventType: String): Int =
        prefs.getInt("priority_$eventType", Defaults.PRIORITY[eventType] ?: 3)
    fun setPriority(eventType: String, value: Int) {
        prefs.edit().putInt("priority_$eventType", value).apply()
        audit("Priority for $eventType set to $value")
    }
    fun getCooldownMs(eventType: String): Long =
        prefs.getLong("cooldown_$eventType", Defaults.COOLDOWN_MS[eventType] ?: 3000L)
    fun setCooldownMs(eventType: String, value: Long) {
        prefs.edit().putLong("cooldown_$eventType", value).apply()
        audit("Cooldown for $eventType set to ${value}ms")
    }

    // channels index: 0=voice 1=caption 2=haptic 3=visual
    private val CHANNEL_NAMES = listOf("voice", "caption", "haptic", "visual")
    fun getChannel(eventType: String, channel: String): Boolean {
        val idx = CHANNEL_NAMES.indexOf(channel)
        val default = Defaults.CHANNELS[eventType]?.getOrNull(idx) ?: false
        return prefs.getBoolean("chan_${eventType}_$channel", default)
    }
    fun setChannel(eventType: String, channel: String, value: Boolean) {
        prefs.edit().putBoolean("chan_${eventType}_$channel", value).apply()
        audit("Channel $channel for $eventType ${if (value) "enabled" else "disabled"}")
    }

    // ---- audit log (local, capped at 50 — same shape as the web admin) ----
    data class AuditEntry(val ts: Long, val message: String)
    private val auditLog = mutableListOf<AuditEntry>()
    private fun audit(message: String) {
        auditLog.add(0, AuditEntry(System.currentTimeMillis(), message))
        if (auditLog.size > 50) auditLog.removeAt(auditLog.size - 1)
        val serialized = auditLog.joinToString("\n") { "${it.ts}|${it.message}" }
        prefs.edit().putString("audit_log", serialized).apply()
    }
    fun getAudit(): List<AuditEntry> {
        if (auditLog.isEmpty()) {
            val raw = prefs.getString("audit_log", null)
            raw?.lines()?.forEach { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size == 2) auditLog.add(AuditEntry(parts[0].toLongOrNull() ?: 0L, parts[1]))
            }
        }
        return auditLog.toList()
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        auditLog.clear()
        audit("Configuration reset to defaults")
    }
}
