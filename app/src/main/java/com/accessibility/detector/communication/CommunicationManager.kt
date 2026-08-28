package com.accessibility.detector.communication

import android.content.Context
import com.accessibility.detector.core.HapticManager

data class QuickPhrase(
    val title: String,
    val text: String,
    val icon: String = "💬"
)

/**
 * Communication Manager for Category 3: Speak & Translate.
 * Manages quick accessible communication phrases, Type-to-Speak, Translation, and 2-way dialog.
 */
class CommunicationManager(
    private val context: Context,
    val ttsManager: TtsManager,
    val translationEngine: TranslationEngine = TranslationEngine(),
    val languageDetector: LanguageDetector = LanguageDetector(),
    val hapticManager: HapticManager = HapticManager(context)
) {

    val defaultQuickPhrases = listOf(
        QuickPhrase("HELP", "I need help.", "🆘"),
        QuickPhrase("WATER", "I need some water.", "💧"),
        QuickPhrase("FOOD", "I need food.", "🍲"),
        QuickPhrase("HOSPITAL", "Please take me to the hospital.", "🏥"),
        QuickPhrase("THANK YOU", "Thank you.", "🙏"),
        QuickPhrase("YES", "Yes.", "✅"),
        QuickPhrase("NO", "No.", "❌")
    )

    private var lastSpokenPhrase: String = ""

    fun speakText(text: String, interrupt: Boolean = true) {
        if (text.isBlank()) return
        lastSpokenPhrase = text
        hapticManager.playTranslationPulse()
        ttsManager.speak(text, interrupt = interrupt)
    }

    fun repeatLastPhrase() {
        if (lastSpokenPhrase.isNotBlank()) {
            hapticManager.playTranslationPulse()
            ttsManager.speak(lastSpokenPhrase, interrupt = true)
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun translateAndSpeak(
        text: String,
        targetLanguage: SupportedLanguage
    ): TranslationResult {
        val detectedSource = languageDetector.detectLanguage(text)
        val translation = translationEngine.translate(
            text = text,
            sourceLang = detectedSource,
            targetLang = targetLanguage
        )

        hapticManager.playTranslationPulse()
        ttsManager.speak(translation.translatedText, interrupt = true)
        return translation
    }
}
