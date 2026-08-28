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

/**
 * Low-level HTTP Service communicating with Google Gemini Multimodal REST API.
 */
class GeminiVisionService(
    private val context: Context
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5000, TimeUnit.MILLISECONDS)
        .readTimeout(6000, TimeUnit.MILLISECONDS)
        .writeTimeout(5000, TimeUnit.MILLISECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun analyzeImage(
        base64Jpeg: String,
        prompt: String = "Analyze this camera frame for potential safety hazards or key scene elements."
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = GeminiConfig.getApiKey(context)
        if (apiKey.isBlank()) {
            Log.d(TAG, "Gemini API key is not configured.")
            return@withContext null
        }

        val model = GeminiConfig.getModelName(context)
        val url = "${GeminiConfig.BASE_ENDPOINT}/$model:generateContent?key=$apiKey"

        try {
            val requestBodyJson = buildRequestBody(base64Jpeg, prompt)
            val requestBody = requestBodyJson.toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.w(TAG, "Gemini API error code: ${response.code}, body: $responseBodyStr")
                    return@withContext null
                }

                return@withContext extractTextFromGeminiResponse(responseBodyStr)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini network call failed: ${e.message}")
            return@withContext null
        }
    }

    private fun buildRequestBody(base64Jpeg: String, userPrompt: String): JSONObject {
        val root = JSONObject()

        // 1. System instruction
        val systemInstruction = JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", GeminiConfig.SYSTEM_PROMPT) })
            })
        }
        root.put("system_instruction", systemInstruction)

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

        // 3. Generation config for JSON output
        val generationConfig = JSONObject().apply {
            put("temperature", 0.2)
            put("maxOutputTokens", 256)
            put("response_mime_type", "application/json")
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

            return parts.getJSONObject(0).optString("text", "")
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing Gemini response envelope: ${e.message}")
            return null
        }
    }

    companion object {
        private const val TAG = "GeminiVisionService"
    }
}
