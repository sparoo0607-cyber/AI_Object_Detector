package com.accessibility.detector.vision.gemini

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class GeminiCallResult {
    data class Success(val text: String) : GeminiCallResult()
    data class Error(val message: String, val isQuotaOrAuth: Boolean = false) : GeminiCallResult()
}

/**
 * Low-level HTTP Service communicating with the official Google Gemini Multimodal REST API.
 */
class GeminiVisionService(
    private val context: Context
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeImage(
        base64Jpeg: String,
        prompt: String = "Describe the scene for a visually impaired user.",
        systemInstructionText: String = GeminiConfig.ACCESSIBILITY_SYSTEM_PROMPT,
        asJson: Boolean = false
    ): GeminiCallResult = withContext(Dispatchers.IO) {
        val apiKey = GeminiConfig.getApiKey(context)
        if (apiKey.isBlank()) {
            Log.d(TAG, "Gemini API key is not configured.")
            return@withContext GeminiCallResult.Error("Gemini API key not configured.", isQuotaOrAuth = true)
        }

        val candidateModels = listOf(
            GeminiConfig.getModelName(context),
            "gemini-1.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash-8b",
            "gemini-1.5-pro"
        ).distinct()

        val requestBodyJson = buildRequestBody(base64Jpeg, prompt, systemInstructionText, asJson)
        val requestBody = requestBodyJson.toString().toRequestBody(jsonMediaType)

        var lastError = "AI scene analysis is currently unavailable."
        var isAuthError = false

        for (model in candidateModels) {
            val urlWithKey = "${GeminiConfig.BASE_ENDPOINT}/$model:generateContent?key=$apiKey"
            val urlWithoutKey = "${GeminiConfig.BASE_ENDPOINT}/$model:generateContent"
            
            // Try standard key query/header
            val authAttempts = listOf(
                Pair(urlWithKey, mapOf("x-goog-api-key" to apiKey)),
                Pair(urlWithoutKey, mapOf("Authorization" to "Bearer $apiKey")),
                Pair(urlWithKey, emptyMap<String, String>())
            )

            for ((requestUrl, headers) in authAttempts) {
                try {
                    val requestBuilder = Request.Builder()
                        .url(requestUrl)
                        .post(requestBody)

                    for ((hKey, hVal) in headers) {
                        requestBuilder.addHeader(hKey, hVal)
                    }

                    val request = requestBuilder.build()
                    val response = httpClient.newCall(request).execute()
                    val responseBodyStr = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        val extracted = extractTextFromGeminiResponse(responseBodyStr)
                        if (!extracted.isNullOrBlank()) {
                            Log.d(TAG, "Gemini Vision succeeded with model: $model")
                            return@withContext GeminiCallResult.Success(extracted)
                        }
                    } else {
                        Log.w(TAG, "Gemini call ($model) HTTP ${response.code}: $responseBodyStr")
                        if (response.code == 400 || response.code == 401 || response.code == 403) {
                            isAuthError = true
                        }
                        lastError = "HTTP ${response.code}"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Gemini network call failed for $model: ${e.message}")
                    lastError = e.localizedMessage ?: "Network error"
                }
            }
        }

        return@withContext GeminiCallResult.Error(lastError, isQuotaOrAuth = isAuthError)
    }

    private fun buildRequestBody(
        base64Jpeg: String,
        userPrompt: String,
        systemInstructionText: String,
        asJson: Boolean
    ): JSONObject {
        val root = JSONObject()

        // 1. System instruction
        if (systemInstructionText.isNotBlank()) {
            val systemInstruction = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemInstructionText) })
                })
            }
            root.put("system_instruction", systemInstruction)
        }

        // 2. Contents: prompt + inline base64 image
        val partsArray = JSONArray().apply {
            put(JSONObject().apply { put("text", userPrompt) })
            put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64Jpeg)
                })
            })
        }

        val contentsArray = JSONArray().apply {
            put(JSONObject().apply { put("parts", partsArray) })
        }
        root.put("contents", contentsArray)

        // 3. Generation config
        val generationConfig = JSONObject().apply {
            put("temperature", 0.3)
            put("maxOutputTokens", 512)
            if (asJson) {
                put("response_mime_type", "application/json")
            }
        }
        root.put("generationConfig", generationConfig)

        return root
    }

    private fun extractTextFromGeminiResponse(jsonStr: String): String? {
        try {
            val root = JSONObject(jsonStr)
            val candidates = root.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null

            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null

            val rawText = parts.getJSONObject(0).optString("text", "").trim()
            return rawText
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Gemini response envelope: ${e.message}")
            return null
        }
    }

    companion object {
        private const val TAG = "GeminiVisionService"
    }
}
