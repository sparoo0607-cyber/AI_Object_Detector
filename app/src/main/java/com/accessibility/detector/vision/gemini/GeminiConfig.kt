package com.accessibility.detector.vision.gemini

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration & dynamic API key manager for Gemini Multimodal Vision.
 * Protects secrets by avoiding hardcoded credentials in source code.
 */
object GeminiConfig {

    private const val PREFS_NAME = "sahey_gemini_prefs"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model_name"

    // Default fast multimodal vision model
    const val DEFAULT_MODEL = "gemini-1.5-flash"
    const val BASE_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

    fun getApiKey(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_API_KEY, "") ?: ""
        if (key.isNotBlank()) return key

        // Fallback to system property or environment if available in build/runtime
        return System.getProperty("GEMINI_API_KEY") ?: ""
    }

    fun setApiKey(context: Context, apiKey: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun getModelName(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun isGeminiConfigured(context: Context): Boolean {
        return getApiKey(context).isNotBlank()
    }

    const val SYSTEM_PROMPT = """You are the visual reasoning engine of SAHEY Vision Assist.
The user is visually impaired.
Analyze the provided camera image carefully.
Prioritize potential safety hazards.
Never invent objects, hazards, distances, or events.
If evidence is uncertain, mark the result as possible or uncertain.
Never claim an exact distance without reliable depth information.
Use relative spatial descriptions such as: ahead, left, right, nearby, very close.
Prioritize danger over ordinary scene descriptions.
Return ONLY a valid JSON object matching this schema:
{
  "danger_detected": boolean,
  "danger_type": "fire" | "slippery_floor" | "vehicle" | "stairs" | "obstacle" | "drop" | "none",
  "priority": "critical" | "high" | "hazard" | "normal",
  "direction": "front" | "left" | "right" | "nearby" | "unknown",
  "confidence": number,
  "message": "concise voice phrase"
}
Do not include markdown or formatting outside the JSON object."""
}
