package com.accessibility.detector.translation

import android.util.Log

enum class SupportedLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिंदी (Hindi)"),
    TELUGU("te", "తెలుగు (Telugu)"),
    SPANISH("es", "Español (Spanish)")
}

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: SupportedLanguage,
    val targetLanguage: SupportedLanguage
)

/**
 * Multilingual translation engine supporting fast on-device dictionary & rule translation
 * for accessibility phrases, notices, and conversational text.
 */
class TranslationEngine {

    // Multilingual dictionary matrix for key accessibility, safety, and everyday phrases
    private val translationDict = mapOf(
        // English -> Telugu
        "hello" to mapOf(SupportedLanguage.TELUGU to "హలో (Namaskaram)", SupportedLanguage.HINDI to "नमस्ते (Namaste)", SupportedLanguage.SPANISH to "Hola"),
        "thank you" to mapOf(SupportedLanguage.TELUGU to "ధన్యవాదాలు (Dhanyavadalu)", SupportedLanguage.HINDI to "धन्यवाद (Dhanyavaad)", SupportedLanguage.SPANISH to "Gracias"),
        "help" to mapOf(SupportedLanguage.TELUGU to "సహాయం చేయండి (Sahayam cheyandi)", SupportedLanguage.HINDI to "मदद करो (Madad karo)", SupportedLanguage.SPANISH to "Ayuda"),
        "danger" to mapOf(SupportedLanguage.TELUGU to "ప్రమాదం (Pramadam)", SupportedLanguage.HINDI to "खतरा (Khatra)", SupportedLanguage.SPANISH to "Peligro"),
        "warning" to mapOf(SupportedLanguage.TELUGU to "హెచ్చరిక (Hechcharika)", SupportedLanguage.HINDI to "चेतावनी (Chetavani)", SupportedLanguage.SPANISH to "Advertencia"),
        "exit" to mapOf(SupportedLanguage.TELUGU to "నిష్క్రమణ / బయటకు దారి (Exit)", SupportedLanguage.HINDI to "निकास (Nikaas)", SupportedLanguage.SPANISH to "Salida"),
        "entrance" to mapOf(SupportedLanguage.TELUGU to "ప్రవేశం (Pravesham)", SupportedLanguage.HINDI to "प्रवेश (Pravesh)", SupportedLanguage.SPANISH to "Entrada"),
        "stop" to mapOf(SupportedLanguage.TELUGU to "ఆగండి (Aagandi)", SupportedLanguage.HINDI to "रुको (Ruko)", SupportedLanguage.SPANISH to "Alto / Pare"),
        "water" to mapOf(SupportedLanguage.TELUGU to "నీరు (Neeru)", SupportedLanguage.HINDI to "पानी (Paani)", SupportedLanguage.SPANISH to "Agua"),
        "hospital" to mapOf(SupportedLanguage.TELUGU to "ఆసుపత్రి (Aasupatri)", SupportedLanguage.HINDI to "अस्पताल (Aspataal)", SupportedLanguage.SPANISH to "Hospital"),
        "police" to mapOf(SupportedLanguage.TELUGU to "పోలీసులు (Police)", SupportedLanguage.HINDI to "पुलिस (Police)", SupportedLanguage.SPANISH to "Policía"),
        "where is the exit" to mapOf(SupportedLanguage.TELUGU to "బయటకు దారి ఎక్కడ ఉంది?", SupportedLanguage.HINDI to "निकास कहाँ है?", SupportedLanguage.SPANISH to "¿Dónde está la salida?"),
        "where is the station" to mapOf(SupportedLanguage.TELUGU to "స్టేషన్ ఎక్కడ ఉంది?", SupportedLanguage.HINDI to "स्टेशन कहाँ है?", SupportedLanguage.SPANISH to "¿Dónde está la estación?"),

        // Spanish -> English / Telugu / Hindi
        "salida" to mapOf(SupportedLanguage.ENGLISH to "Exit", SupportedLanguage.TELUGU to "నిష్క్రమణ (Exit)", SupportedLanguage.HINDI to "निकास (Exit)"),
        "entrada" to mapOf(SupportedLanguage.ENGLISH to "Entrance", SupportedLanguage.TELUGU to "ప్రవేశం (Entrance)", SupportedLanguage.HINDI to "प्रवेश (Entrance)"),
        "peligro" to mapOf(SupportedLanguage.ENGLISH to "Danger", SupportedLanguage.TELUGU to "ప్రమాదం (Danger)", SupportedLanguage.HINDI to "खतरा (Danger)"),
        "hola" to mapOf(SupportedLanguage.ENGLISH to "Hello", SupportedLanguage.TELUGU to "హలో", SupportedLanguage.HINDI to "नमस्ते"),
        "gracias" to mapOf(SupportedLanguage.ENGLISH to "Thank you", SupportedLanguage.TELUGU to "ధన్యవాదాలు", SupportedLanguage.HINDI to "धन्यवाद"),
        "donde esta la estacion" to mapOf(SupportedLanguage.ENGLISH to "Where is the station?", SupportedLanguage.TELUGU to "స్టేషన్ ఎక్కడ ఉంది?", SupportedLanguage.HINDI to "स्टेशन कहाँ है?")
    )

    fun translate(
        text: String,
        sourceLang: SupportedLanguage = SupportedLanguage.ENGLISH,
        targetLang: SupportedLanguage = SupportedLanguage.TELUGU
    ): TranslationResult {
        val normalized = text.lowercase().replace(Regex("[?.,!¡¿]"), "").trim()

        // 1. Direct match in dictionary
        val match = translationDict[normalized]?.get(targetLang)
        if (match != null) {
            return TranslationResult(
                originalText = text,
                translatedText = match,
                sourceLanguage = sourceLang,
                targetLanguage = targetLang
            )
        }

        // 2. Word by word replacement fallback
        val words = normalized.split(" ")
        val translatedWords = words.map { word ->
            translationDict[word]?.get(targetLang) ?: word
        }
        val fallbackText = translatedWords.joinToString(" ")

        return TranslationResult(
            originalText = text,
            translatedText = if (fallbackText.isNotBlank()) fallbackText else text,
            sourceLanguage = sourceLang,
            targetLanguage = targetLang
        )
    }

    companion object {
        private const val TAG = "TranslationEngine"
    }
}
