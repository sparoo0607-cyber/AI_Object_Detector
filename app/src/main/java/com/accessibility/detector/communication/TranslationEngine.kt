package com.accessibility.detector.communication

enum class SupportedLanguage(val code: String, val displayName: String) {
    AUTO("auto", "Auto-Detect"),
    ENGLISH("en", "English"),
    TELUGU("te", "తెలుగు (Telugu)"),
    HINDI("hi", "हिंदी (Hindi)"),
    TAMIL("ta", "தமிழ் (Tamil)"),
    KANNADA("kn", "ಕನ್ನಡ (Kannada)"),
    MALAYALAM("ml", "മലയാളം (Malayalam)"),
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
        // English -> Other languages
        "hello" to mapOf(
            SupportedLanguage.TELUGU to "హలో (Namaskaram)",
            SupportedLanguage.HINDI to "नमस्ते (Namaste)",
            SupportedLanguage.TAMIL to "வணக்கம் (Vanakkam)",
            SupportedLanguage.KANNADA to "ನಮಸ್ಕಾರ (Namaskara)",
            SupportedLanguage.MALAYALAM to "നമസ്കാരം (Namaskaram)",
            SupportedLanguage.SPANISH to "Hola"
        ),
        "thank you" to mapOf(
            SupportedLanguage.TELUGU to "ధన్యవాదాలు (Dhanyavadalu)",
            SupportedLanguage.HINDI to "धन्यवाद (Dhanyavaad)",
            SupportedLanguage.TAMIL to "நன்றி (Nandri)",
            SupportedLanguage.KANNADA to "ಧನ್ಯವಾದಗಳು (Dhanyavadagalu)",
            SupportedLanguage.MALAYALAM to "നന്ദി (Nandi)",
            SupportedLanguage.SPANISH to "Gracias"
        ),
        "i need help" to mapOf(
            SupportedLanguage.TELUGU to "నాకు సహాయం కావాలి (Naaku sahayam kaavali)",
            SupportedLanguage.HINDI to "मुझे मदद चाहिए (Mujhe madad chahiye)",
            SupportedLanguage.TAMIL to "எனக்கு உதவி வேண்டும் (Enakku udhavi vendum)",
            SupportedLanguage.KANNADA to "ನನಗೆ ಸಹಾಯ ಬೇಕು (Nanage sahaya beku)",
            SupportedLanguage.MALAYALAM to "എനിക്ക് സഹായം വേണം (Enikku sahayam venam)",
            SupportedLanguage.SPANISH to "Necesito ayuda"
        ),
        "help" to mapOf(
            SupportedLanguage.TELUGU to "సహాయం (Sahayam)",
            SupportedLanguage.HINDI to "मदद (Madad)",
            SupportedLanguage.TAMIL to "உதவி (Udhavi)",
            SupportedLanguage.KANNADA to "ಸಹಾಯ (Sahaya)",
            SupportedLanguage.MALAYALAM to "സഹായം (Sahayam)",
            SupportedLanguage.SPANISH to "Ayuda"
        ),
        "i need some water" to mapOf(
            SupportedLanguage.TELUGU to "నాకు మంచి నీరు కావాలి (Naaku neeru kaavali)",
            SupportedLanguage.HINDI to "मुझे पानी चाहिए (Mujhe paani chahiye)",
            SupportedLanguage.TAMIL to "எனக்கு தண்ணீர் வேண்டும் (Enakku thanneer vendum)",
            SupportedLanguage.KANNADA to "ನನಗೆ ನೀರು ಬೇಕು (Nanage neeru beku)",
            SupportedLanguage.MALAYALAM to "എനിക്ക് വെള്ളം വേണം (Enikku vellam venam)",
            SupportedLanguage.SPANISH to "Necesito agua"
        ),
        "i need food" to mapOf(
            SupportedLanguage.TELUGU to "నాకు ఆహారం కావాలి (Naaku aahaaram kaavali)",
            SupportedLanguage.HINDI to "मुझे खाना चाहिए (Mujhe khaana chahiye)",
            SupportedLanguage.TAMIL to "எனக்கு உணவு வேண்டும் (Enakku unavu vendum)",
            SupportedLanguage.KANNADA to "ನನಗೆ ಊಟ ಬೇಕು (Nanage oota beku)",
            SupportedLanguage.MALAYALAM to "എനിക്ക് ഭക്ഷണം വേണം (Enikku bhakshanam venam)",
            SupportedLanguage.SPANISH to "Necesito comida"
        ),
        "please take me to the hospital" to mapOf(
            SupportedLanguage.TELUGU to "దయచేసి నన్ను ఆసుపత్రికి తీసుకెళ్లండి",
            SupportedLanguage.HINDI to "कृपया मुझे अस्पताल ले जाएं",
            SupportedLanguage.TAMIL to "தயவுசெய்து என்னை மருத்துவமனைக்கு அழைத்துச் செல்லுங்கள்",
            SupportedLanguage.KANNADA to "ದಯವಿಟ್ಟು ನನ್ನನ್ನು ಆಸ್ಪತ್ರೆಗೆ ಕರೆದುಕೊಂಡು ಹೋಗಿ",
            SupportedLanguage.MALAYALAM to "ദയവായി എന്നെ ആശുപത്രിയിൽ എത്തിക്കൂ",
            SupportedLanguage.SPANISH to "Por favor lléveme al hospital"
        ),
        "danger" to mapOf(
            SupportedLanguage.TELUGU to "ప్రమాదం (Pramadam)",
            SupportedLanguage.HINDI to "खतरा (Khatra)",
            SupportedLanguage.TAMIL to "ஆபத்து (Aabathu)",
            SupportedLanguage.SPANISH to "Peligro"
        ),
        "where is the exit" to mapOf(
            SupportedLanguage.TELUGU to "బయటకు దారి ఎక్కడ ఉంది?",
            SupportedLanguage.HINDI to "निकास कहाँ है?",
            SupportedLanguage.TAMIL to "வெளியேறும் வழி எங்கே?",
            SupportedLanguage.SPANISH to "¿Dónde está la salida?"
        ),
        "where is the bus station" to mapOf(
            SupportedLanguage.TELUGU to "బస్ స్టేషన్ ఎక్కడ ఉంది? (Bus station ekkada undi?)",
            SupportedLanguage.HINDI to "बस स्टेशन कहाँ है? (Bus station kahaan hai?)",
            SupportedLanguage.TAMIL to "பேருந்து நிலையம் எங்கே உள்ளது?",
            SupportedLanguage.SPANISH to "¿Dónde está la estación de autobuses?"
        ),
        "where are you going" to mapOf(
            SupportedLanguage.TELUGU to "మీరు ఎక్కడికి వెళ్తున్నారు? (Meeru ekkadiki velthunnaru?)",
            SupportedLanguage.HINDI to "आप कहाँ जा रहे हैं? (Aap kahaan ja rahe hain?)",
            SupportedLanguage.TAMIL to "நீங்கள் எங்கே போகிறீர்கள்?",
            SupportedLanguage.SPANISH to "¿A dónde vas?"
        ),
        "yes" to mapOf(
            SupportedLanguage.TELUGU to "అవును (Avunu)",
            SupportedLanguage.HINDI to "हाँ (Haan)",
            SupportedLanguage.TAMIL to "ஆம் (Aam)",
            SupportedLanguage.SPANISH to "Sí"
        ),
        "no" to mapOf(
            SupportedLanguage.TELUGU to "కాదు / లేదు (Kaadhu)",
            SupportedLanguage.HINDI to "नहीं (Nahi)",
            SupportedLanguage.TAMIL to "இல்லை (Illai)",
            SupportedLanguage.SPANISH to "No"
        )
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
}
