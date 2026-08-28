package com.accessibility.detector.communication

/**
 * Language detector determining the script / dialect of incoming text.
 */
class LanguageDetector {

    fun detectLanguage(text: String): SupportedLanguage {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SupportedLanguage.ENGLISH

        // Telugu unicode range: \u0C00-\u0C7F
        if (trimmed.any { it in '\u0C00'..'\u0C7F' }) {
            return SupportedLanguage.TELUGU
        }
        // Hindi / Devanagari unicode range: \u0900-\u097F
        if (trimmed.any { it in '\u0900'..'\u097F' }) {
            return SupportedLanguage.HINDI
        }
        // Tamil unicode range: \u0B80-\u0BFF
        if (trimmed.any { it in '\u0B80'..'\u0BFF' }) {
            return SupportedLanguage.TAMIL
        }
        // Kannada unicode range: \u0C80-\u0CFF
        if (trimmed.any { it in '\u0C80'..'\u0CFF' }) {
            return SupportedLanguage.KANNADA
        }
        // Malayalam unicode range: \u0D00-\u0D7F
        if (trimmed.any { it in '\u0D00'..'\u0D7F' }) {
            return SupportedLanguage.MALAYALAM
        }

        return SupportedLanguage.ENGLISH
    }
}
