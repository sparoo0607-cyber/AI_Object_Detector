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
 * Manages quick accessible communication phrases, Type-to-Speak, Offline ML Kit Translation, and 2-way dialog.
 */
class CommunicationManager(
    private val context: Context,
    val ttsManager: TtsManager,
    val translationEngine: TranslationEngine = TranslationEngine(context),
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
    private var lastSpokenLanguage: SupportedLanguage = SupportedLanguage.ENGLISH

    fun speakText(text: String, interrupt: Boolean = true) {
        if (text.isBlank()) return
        lastSpokenPhrase = text
        lastSpokenLanguage = languageDetector.detectLanguage(text)
        hapticManager.playTranslationPulse()
        ttsManager.speak(text, interrupt, lastSpokenLanguage)
    }

    fun repeatLastPhrase() {
        if (lastSpokenPhrase.isNotBlank()) {
            hapticManager.playTranslationPulse()
            ttsManager.speak(lastSpokenPhrase, true, lastSpokenLanguage)
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    /**
     * Primary Asynchronous Translation & TTS announcement.
     */
    fun translateAndSpeak(
        text: String,
        sourceLanguage: SupportedLanguage = SupportedLanguage.AUTO,
        targetLanguage: SupportedLanguage = SupportedLanguage.TELUGU,
        onComplete: (TranslationResult) -> Unit
    ) {
        translationEngine.translate(
            text = text,
            sourceLang = sourceLanguage,
            targetLang = targetLanguage
        ) { result ->
            if (result.translatedText.isNotBlank() && result.isSuccessful) {
                lastSpokenPhrase = result.translatedText
                lastSpokenLanguage = result.targetLanguage
                hapticManager.playTranslationPulse()
                ttsManager.speak(result.translatedText, true, result.targetLanguage)
            }
            onComplete(result)
        }
    }

    /**
     * Synchronous translation fallback.
     */
    fun translateSync(
        text: String,
        targetLanguage: SupportedLanguage
    ): TranslationResult {
        val detectedSource = languageDetector.detectLanguage(text)
        val translation = translationEngine.translateSync(
            text = text,
            sourceLang = detectedSource,
            targetLang = targetLanguage
        )

        if (translation.translatedText.isNotBlank() && translation.isSuccessful) {
            lastSpokenPhrase = translation.translatedText
            lastSpokenLanguage = translation.targetLanguage
            hapticManager.playTranslationPulse()
            ttsManager.speak(translation.translatedText, true, translation.targetLanguage)
        }
        return translation
    }
}
