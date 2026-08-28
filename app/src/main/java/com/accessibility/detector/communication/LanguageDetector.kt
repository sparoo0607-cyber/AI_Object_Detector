package com.accessibility.detector.communication

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification

/**
 * Intelligent Language Detector combining Google ML Kit Language Identification
 * with fast on-device Unicode block detection for Indian regional scripts and English.
 */
class LanguageDetector {

    private val languageIdentifier = LanguageIdentification.getClient()

    /**
     * Synchronous / immediate heuristic detection based on Unicode script blocks.
     */
    fun detectLanguage(text: String): SupportedLanguage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SupportedLanguage.ENGLISH

        // 1. Telugu script block: \u0C00-\u0C7F
        if (trimmed.any { it in '\u0C00'..'\u0C7F' }) {
            return SupportedLanguage.TELUGU
        }
        // 2. Hindi / Devanagari script block: \u0900-\u097F
        if (trimmed.any { it in '\u0900'..'\u097F' }) {
            return SupportedLanguage.HINDI
        }
        // 3. Tamil script block: \u0B80-\u0BFF
        if (trimmed.any { it in '\u0B80'..'\u0BFF' }) {
            return SupportedLanguage.TAMIL
        }
        // 4. Kannada script block: \u0C80-\u0CFF
        if (trimmed.any { it in '\u0C80'..'\u0CFF' }) {
            return SupportedLanguage.KANNADA
        }
        // 5. Malayalam script block: \u0D00-\u0D7F
        if (trimmed.any { it in '\u0D00'..'\u0D7F' }) {
            return SupportedLanguage.MALAYALAM
        }

        return SupportedLanguage.ENGLISH
    }

    /**
     * Asynchronous ML Kit language identification.
     */
    fun identifyLanguageAsync(text: String, onResult: (SupportedLanguage) -> Unit) {
        val heuristic = detectLanguage(text)
        if (heuristic != SupportedLanguage.ENGLISH) {
            onResult(heuristic)
            return
        }

        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                val detected = when (languageCode) {
                    "te" -> SupportedLanguage.TELUGU
                    "hi" -> SupportedLanguage.HINDI
                    "ta" -> SupportedLanguage.TAMIL
                    "kn" -> SupportedLanguage.KANNADA
                    "ml" -> SupportedLanguage.MALAYALAM
                    "es" -> SupportedLanguage.SPANISH
                    "en" -> SupportedLanguage.ENGLISH
                    else -> detectLanguage(text)
                }
                onResult(detected)
            }
            .addOnFailureListener {
                onResult(detectLanguage(text))
            }
    }

    companion object {
        private const val TAG = "LanguageDetector"
    }
}
