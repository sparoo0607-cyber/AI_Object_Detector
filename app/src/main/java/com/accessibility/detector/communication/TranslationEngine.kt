package com.accessibility.detector.communication

import android.content.Context
import android.util.Log
import com.accessibility.detector.vision.gemini.GeminiConfig
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class SupportedLanguage(val code: String, val displayName: String) {
    AUTO("auto", "Auto-Detect"),
    ENGLISH("en", "English"),
    TELUGU("te", "తెలుగు (Telugu)"),
    HINDI("hi", "हिंदी (Hindi)"),
    TAMIL("ta", "தமிழ் (Tamil)"),
    KANNADA("kn", "ಕನ್ನಡ (Kannada)"),
    MALAYALAM("ml", "മലയാളం (Malayalam)"),
    SPANISH("es", "Español (Spanish)")
}

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: SupportedLanguage,
    val targetLanguage: SupportedLanguage,
    val isOffline: Boolean = true,
    val isSuccessful: Boolean = true,
    val errorMessage: String? = null
)

/**
 * Enterprise-grade Offline-First Multilingual Translation Engine for SAHEY.
 * Preferred Pipeline:
 * 1. Fast On-Device Google ML Kit Translation (if language models are downloaded)
 * 2. High-Accuracy Accessibility Dictionary Matrix (Instant local fallback)
 * 3. Online Gemini / Cloud Translation (if online & offline pack missing)
 * 4. Graceful Error Handling (Never crashes, explains language pack status clearly)
 */
class TranslationEngine(private val context: Context? = null) {

    val offlineLanguageManager = if (context != null) OfflineLanguageManager(context) else null
    val languageDetector = LanguageDetector()

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val translatorClients = ConcurrentHashMap<String, Translator>()
    private val inMemoryTranslationCache = ConcurrentHashMap<String, String>()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Built-in offline accessibility phrase dictionary
    private val translationDict = mapOf(
        "hello" to mapOf(
            SupportedLanguage.TELUGU to "హలో",
            SupportedLanguage.HINDI to "नमस्ते",
            SupportedLanguage.TAMIL to "வணக்கம்",
            SupportedLanguage.KANNADA to "ನಮಸ್ಕಾರ",
            SupportedLanguage.MALAYALAM to "നമസ്കാരം",
            SupportedLanguage.SPANISH to "Hola"
        ),
        "thank you" to mapOf(
            SupportedLanguage.TELUGU to "ధన్యవాదాలు",
            SupportedLanguage.HINDI to "धन्यवाद",
            SupportedLanguage.TAMIL to "நன்றி",
            SupportedLanguage.KANNADA to "ಧನ್ಯವಾದಗಳು",
            SupportedLanguage.MALAYALAM to "നന്ദി",
            SupportedLanguage.SPANISH to "Gracias"
        ),
        "i need help" to mapOf(
            SupportedLanguage.TELUGU to "నాకు సహాయం కావాలి",
            SupportedLanguage.HINDI to "मुझे मदद चाहिए",
            SupportedLanguage.TAMIL to "எனக்கு உதவி வேண்டும்",
            SupportedLanguage.KANNADA to "ನನಗೆ ಸಹಾಯ ಬೇಕು",
            SupportedLanguage.MALAYALAM to "എനിക്ക് സഹായം വേണം",
            SupportedLanguage.SPANISH to "Necesito ayuda"
        ),
        "help" to mapOf(
            SupportedLanguage.TELUGU to "సహాయం",
            SupportedLanguage.HINDI to "मदद",
            SupportedLanguage.TAMIL to "உதவி",
            SupportedLanguage.KANNADA to "ಸಹಾಯ",
            SupportedLanguage.MALAYALAM to "സഹായം",
            SupportedLanguage.SPANISH to "Ayuda"
        ),
        "i need some water" to mapOf(
            SupportedLanguage.TELUGU to "నాకు మంచి నీరు కావాలి",
            SupportedLanguage.HINDI to "मुझे पानी चाहिए",
            SupportedLanguage.TAMIL to "எனக்கு தண்ணீர் வேண்டும்",
            SupportedLanguage.KANNADA to "ನನಗೆ ನೀರು ಬೇಕು",
            SupportedLanguage.MALAYALAM to "എനിക്ക് വെള്ളം വേണം",
            SupportedLanguage.SPANISH to "Necesito agua"
        ),
        "i need food" to mapOf(
            SupportedLanguage.TELUGU to "నాకు ఆహారం కావాలి",
            SupportedLanguage.HINDI to "मुझे खाना चाहिए",
            SupportedLanguage.TAMIL to "எனக்கு உணவு வேண்டும்",
            SupportedLanguage.KANNADA to "ನನಗೆ ಊಟ ಬೇಕು",
            SupportedLanguage.MALAYALAM to "എനിക്ക് ഭക്ഷണം വേണം",
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
        "where is the exit" to mapOf(
            SupportedLanguage.TELUGU to "బయటకు దారి ఎక్కడ ఉంది?",
            SupportedLanguage.HINDI to "निकास कहाँ है?",
            SupportedLanguage.TAMIL to "வெளியேறும் வழி எங்கே?",
            SupportedLanguage.SPANISH to "¿Dónde está la salida?"
        ),
        "where is the bus station" to mapOf(
            SupportedLanguage.TELUGU to "బస్ స్టేషన్ ఎక్కడ ఉంది?",
            SupportedLanguage.HINDI to "बस स्टेशन कहाँ है?",
            SupportedLanguage.TAMIL to "பேరుந்து நிலையம் எங்கே உள்ளது?",
            SupportedLanguage.SPANISH to "¿Dónde está la estación de autobuses?"
        ),
        "where are you going" to mapOf(
            SupportedLanguage.TELUGU to "మీరు ఎక్కడికి వెళ్తున్నారు?",
            SupportedLanguage.HINDI to "आप कहाँ जा रहे हैं?",
            SupportedLanguage.TAMIL to "நீங்கள் எங்கே போகிறீர்கள்?",
            SupportedLanguage.SPANISH to "¿A dónde vas?"
        ),
        "how are you" to mapOf(
            SupportedLanguage.TELUGU to "మీరు ఎలా ఉన్నారు?",
            SupportedLanguage.HINDI to "आप कैसे हैं?",
            SupportedLanguage.TAMIL to "நீங்கள் எப்படி இருக்கிறீர்கள்?",
            SupportedLanguage.KANNADA to "ನೀವು ಹೇಗಿದ್ದೀರಿ?",
            SupportedLanguage.MALAYALAM to "സുഖമാണോ?",
            SupportedLanguage.SPANISH to "¿Cómo estás?"
        ),
        "yes" to mapOf(
            SupportedLanguage.TELUGU to "అవును",
            SupportedLanguage.HINDI to "हाँ",
            SupportedLanguage.TAMIL to "ஆம்",
            SupportedLanguage.SPANISH to "Sí"
        ),
        "no" to mapOf(
            SupportedLanguage.TELUGU to "కాదు",
            SupportedLanguage.HINDI to "नहीं",
            SupportedLanguage.TAMIL to "இல்லை",
            SupportedLanguage.SPANISH to "No"
        )
    )

    /**
     * Primary Asynchronous Translation API.
     */
    fun translate(
        text: String,
        sourceLang: SupportedLanguage = SupportedLanguage.AUTO,
        targetLang: SupportedLanguage = SupportedLanguage.TELUGU,
        onResult: (TranslationResult) -> Unit
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onResult(
                TranslationResult(
                    originalText = text,
                    translatedText = "",
                    sourceLanguage = sourceLang,
                    targetLanguage = targetLang
                )
            )
            return
        }

        // Auto-detect source language if required
        val actualSource = if (sourceLang == SupportedLanguage.AUTO) {
            languageDetector.detectLanguage(trimmed)
        } else {
            sourceLang
        }

        if (actualSource == targetLang) {
            onResult(
                TranslationResult(
                    originalText = text,
                    translatedText = text,
                    sourceLanguage = actualSource,
                    targetLanguage = targetLang
                )
            )
            return
        }

        // 1. Check in-memory cache
        val cacheKey = "${actualSource.code}_${targetLang.code}_${trimmed.lowercase()}"
        val cached = inMemoryTranslationCache[cacheKey]
        if (cached != null) {
            onResult(
                TranslationResult(
                    originalText = text,
                    translatedText = cached,
                    sourceLanguage = actualSource,
                    targetLanguage = targetLang,
                    isOffline = true
                )
            )
            return
        }

        // 2. Try Dictionary matrix for quick phrases
        val dictMatch = lookupDictionary(trimmed, actualSource, targetLang)
        if (dictMatch != null) {
            inMemoryTranslationCache[cacheKey] = dictMatch
            onResult(
                TranslationResult(
                    originalText = text,
                    translatedText = dictMatch,
                    sourceLanguage = actualSource,
                    targetLanguage = targetLang,
                    isOffline = true
                )
            )
            return
        }

        // 3. Try On-Device ML Kit Offline Translation
        val srcCode = offlineLanguageManager?.getMlKitLanguageCode(actualSource)
        val tgtCode = offlineLanguageManager?.getMlKitLanguageCode(targetLang)

        if (srcCode != null && tgtCode != null) {
            val pairKey = "${srcCode}_$tgtCode"
            val translator = translatorClients.getOrPut(pairKey) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(srcCode)
                    .setTargetLanguage(tgtCode)
                    .build()
                Translation.getClient(options)
            }

            translator.translate(trimmed)
                .addOnSuccessListener { translated ->
                    inMemoryTranslationCache[cacheKey] = translated
                    onResult(
                        TranslationResult(
                            originalText = text,
                            translatedText = translated,
                            sourceLanguage = actualSource,
                            targetLanguage = targetLang,
                            isOffline = true
                        )
                    )
                }
                .addOnFailureListener { mlKitError ->
                    Log.w(TAG, "ML Kit offline translate failed (${actualSource.name}->${targetLang.name}): ${mlKitError.message}")
                    // 4. Fallback to Online Translation if internet is available
                    handleFallbackTranslation(text, actualSource, targetLang, onResult)
                }
            return
        }

        // Fallback
        handleFallbackTranslation(text, actualSource, targetLang, onResult)
    }

    /**
     * Synchronous translation method (convenience overload for immediate calls).
     */
    fun translate(
        text: String,
        sourceLang: SupportedLanguage = SupportedLanguage.AUTO,
        targetLang: SupportedLanguage = SupportedLanguage.TELUGU
    ): TranslationResult {
        return translateSync(text, sourceLang, targetLang)
    }

    /**
     * Synchronous translation fallback for immediate UI rendering where async is unavailable.
     */
    fun translateSync(
        text: String,
        sourceLang: SupportedLanguage = SupportedLanguage.ENGLISH,
        targetLang: SupportedLanguage = SupportedLanguage.TELUGU
    ): TranslationResult {
        val actualSource = if (sourceLang == SupportedLanguage.AUTO) languageDetector.detectLanguage(text) else sourceLang
        val dictMatch = lookupDictionary(text, actualSource, targetLang)
        if (dictMatch != null) {
            return TranslationResult(
                originalText = text,
                translatedText = dictMatch,
                sourceLanguage = actualSource,
                targetLanguage = targetLang,
                isOffline = true
            )
        }
        return TranslationResult(
            originalText = text,
            translatedText = text,
            sourceLanguage = actualSource,
            targetLanguage = targetLang,
            isOffline = true
        )
    }

    private fun handleFallbackTranslation(
        text: String,
        sourceLang: SupportedLanguage,
        targetLang: SupportedLanguage,
        onResult: (TranslationResult) -> Unit
    ) {
        val isOnline = context?.let { offlineLanguageManager?.isInternetAvailable(it) } ?: false

        if (isOnline && context != null && GeminiConfig.isGeminiConfigured(context)) {
            // Online Gemini Fallback
            scope.launch {
                val onlineResult = translateViaGemini(text, sourceLang, targetLang)
                withContext(Dispatchers.Main) {
                    if (onlineResult != null) {
                        onResult(
                            TranslationResult(
                                originalText = text,
                                translatedText = onlineResult,
                                sourceLanguage = sourceLang,
                                targetLanguage = targetLang,
                                isOffline = false
                            )
                        )
                    } else {
                        onResult(
                            TranslationResult(
                                originalText = text,
                                translatedText = "Translation language pack is not available. Please download the ${targetLang.displayName} pack in Settings.",
                                sourceLanguage = sourceLang,
                                targetLanguage = targetLang,
                                isOffline = false,
                                isSuccessful = false,
                                errorMessage = "Model not downloaded"
                            )
                        )
                    }
                }
            }
        } else {
            // Offline without model downloaded
            onResult(
                TranslationResult(
                    originalText = text,
                    translatedText = "Translation language pack is not available. Please download the ${targetLang.displayName} pack when online.",
                    sourceLanguage = sourceLang,
                    targetLanguage = targetLang,
                    isOffline = true,
                    isSuccessful = false,
                    errorMessage = "Language pack not downloaded"
                )
            )
        }
    }

    private suspend fun translateViaGemini(
        text: String,
        sourceLang: SupportedLanguage,
        targetLang: SupportedLanguage
    ): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = context?.let { GeminiConfig.getApiKey(it) } ?: return@withContext null
            val url = "${GeminiConfig.BASE_ENDPOINT}/${GeminiConfig.DEFAULT_MODEL}:generateContent?key=$apiKey"

            val prompt = "Translate the following text from ${sourceLang.displayName} to ${targetLang.displayName}. Return ONLY the direct translation text without notes, quotes, or formatting:\n$text"

            val payload = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(requestBody).build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val jsonRoot = JSONObject(body)
            val candidates = jsonRoot.optJSONArray("candidates") ?: return@withContext null
            if (candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return@withContext parts.getJSONObject(0).optString("text").trim()
                }
            }
            return@withContext null
        } catch (e: Exception) {
            Log.w(TAG, "Gemini online translation fallback failed: ${e.message}")
            return@withContext null
        }
    }

    private fun lookupDictionary(
        text: String,
        source: SupportedLanguage,
        target: SupportedLanguage
    ): String? {
        val normalized = text.lowercase().replace(Regex("[?.,!¡¿]"), "").trim()

        // English -> Target
        if (source == SupportedLanguage.ENGLISH) {
            val match = translationDict[normalized]?.get(target)
            if (match != null) return match
        }

        // Target -> English (Reverse lookup)
        if (target == SupportedLanguage.ENGLISH) {
            for ((engKey, map) in translationDict) {
                val regional = map[source]?.lowercase()?.replace(Regex("[?.,!¡¿]"), "")?.trim()
                if (regional != null && (regional == normalized || normalized.contains(regional))) {
                    return engKey.replaceFirstChar { it.uppercase() }
                }
            }
        }

        return null
    }

    fun close() {
        for (translator in translatorClients.values) {
            try {
                translator.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
        translatorClients.clear()
    }

    companion object {
        private const val TAG = "TranslationEngine"
    }
}
