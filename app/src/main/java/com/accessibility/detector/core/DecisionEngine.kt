package com.accessibility.detector.core

/**
 * SAHAY DECISION ENGINE — confidence banding + attention scoring +
 * feature-flag gating + duplicate suppression, all driven by
 * SahayConfig. Kotlin port of core/decision.js. Nothing here is a
 * hardcoded literal; every number comes from SahayConfig.
 */
object DecisionEngine {

    private fun confidenceBand(event: SahayEvent): String = when (event.type) {
        "text_detected" -> when {
            event.confidence < SahayConfig.getThreshold("ocr_low") -> "low"
            event.confidence < SahayConfig.getThreshold("ocr_medium") -> "medium"
            else -> "high"
        }
        "currency_detected" -> if (event.confidence < SahayConfig.getThreshold("currency_low")) "low" else "high"
        "sound_soft", "sound_sustained", "sound_impulsive" -> when {
            event.confidence < SahayConfig.getThreshold("sound_low") -> "low"
            event.confidence < SahayConfig.getThreshold("sound_medium") -> "medium"
            else -> "high"
        }
        else -> "high"
    }

    private fun featureEnabledFor(type: String): Boolean = when (type) {
        "object_detected" -> SahayConfig.isFeatureEnabled("objects")
        "text_detected" -> SahayConfig.isFeatureEnabled("ocr")
        "currency_detected" -> SahayConfig.isFeatureEnabled("currency")
        "speech_detected" -> SahayConfig.isFeatureEnabled("captions")
        "sound_soft", "sound_sustained", "sound_impulsive" -> SahayConfig.isFeatureEnabled("soundAlerts")
        else -> true
    }

    fun decide(event: SahayEvent): SahayDecision {
        if (!featureEnabledFor(event.type)) {
            return SahayDecision(event, suppressed = true)
        }

        val band = confidenceBand(event)
        val isLowConfidenceType = (event.type == "text_detected" || event.type == "currency_detected") && band == "low"
        val attentionKey = if (isLowConfidenceType) "low_confidence" else event.type

        val cooldown = SahayConfig.getCooldownMs(attentionKey)
        if (ContextEngine.isDuplicate(attentionKey, event.content, cooldown)) {
            return SahayDecision(event, suppressed = true, attentionKey = attentionKey)
        }
        ContextEngine.remember(attentionKey, event.content)

        return SahayDecision(
            event = event,
            suppressed = false,
            confidenceBand = band,
            priority = SahayConfig.getPriority(attentionKey),
            attentionKey = attentionKey,
            channels = SahayDecision.Channels(
                voice = SahayConfig.getChannel(attentionKey, "voice"),
                caption = SahayConfig.getChannel(attentionKey, "caption"),
                haptic = SahayConfig.getChannel(attentionKey, "haptic"),
                visual = SahayConfig.getChannel(attentionKey, "visual"),
            ),
        )
    }
}
