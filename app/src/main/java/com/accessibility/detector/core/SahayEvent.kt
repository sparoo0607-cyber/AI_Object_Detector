package com.accessibility.detector.core

/**
 * Structured perception event — the Kotlin equivalent of the JSON
 * event shape from the web prototype's directive (section 3):
 * { type, confidence, source, content, language, urgency }
 */
data class SahayEvent(
    val type: String, // object_detected | text_detected | currency_detected | speech_detected | sound_soft | sound_sustained | sound_impulsive
    val confidence: Float, // 0..1
    val content: String,
    val source: String, // "camera" | "microphone"
    val language: String = SahayConfig.defaultLanguage,
    val interim: Boolean = false,
)

data class SahayDecision(
    val event: SahayEvent,
    val suppressed: Boolean,
    val confidenceBand: String = "high", // "low" | "medium" | "high"
    val priority: Int = 1,
    val attentionKey: String = event.type,
    val channels: Channels = Channels(),
) {
    data class Channels(
        val voice: Boolean = false,
        val caption: Boolean = false,
        val haptic: Boolean = false,
        val visual: Boolean = false,
    )
}
