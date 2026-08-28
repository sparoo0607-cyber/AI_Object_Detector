package com.accessibility.detector.vision.gemini

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration & dynamic API key manager for Gemini Multimodal Vision.
 */
object GeminiConfig {

    private const val PREFS_NAME = "sahey_gemini_prefs"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_MODEL = "gemini_model_name"

    // Default API Key provided for instant out-of-the-box operation
    private const val DEFAULT_FALLBACK_KEY = ""

    // Default fast multimodal vision model
    const val DEFAULT_MODEL = "gemini-1.5-flash"
    const val BASE_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

    fun getApiKey(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_API_KEY, "") ?: ""
        if (key.isNotBlank()) return key

        val sysProp = System.getProperty("GEMINI_API_KEY") ?: ""
        if (sysProp.isNotBlank()) return sysProp

        return DEFAULT_FALLBACK_KEY
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

    const val SYSTEM_PROMPT = """You are performing safety-focused visual analysis for SAHEY Vision Assist for visually impaired users.
Analyze the camera image carefully.
Determine whether the supplied image contains visible fire or smoke:
1. flames, burning material, fire-like glowing regions, or active fire.
2. visible smoke or heavy smoke clouds.
3. other hazards: slippery floor, approaching vehicles, stairs, or drops.

CRITICAL SCREEN VS REAL FIRE RULE:
- Do not classify a laptop, television, monitor, or phone screen itself as fire.
- If the image shows a photograph, wallpaper, or video of fire displayed on a screen (e.g. laptop, TV, monitor), classify danger_type as "fire_on_screen" and message as "Fire visible on the screen."
- If the image contains a real physical fire or flames in the environment, classify danger_type as "fire" and message as "Warning. Fire detected."
- If smoke is visible, classify danger_type as "smoke" and message as "Warning. Smoke detected."
- If it is a normal laptop or red/orange object without fire, return danger_detected: false, danger_type: "none".

Return ONLY a valid JSON object matching this schema:
{
  "danger_detected": boolean,
  "danger_type": "fire" | "fire_on_screen" | "smoke" | "slippery_floor" | "vehicle" | "stairs" | "obstacle" | "drop" | "none",
  "priority": "critical" | "high" | "hazard" | "normal",
  "direction": "front" | "left" | "right" | "nearby" | "unknown",
  "confidence": number,
  "message": "concise voice phrase"
}
Do not include markdown or text outside the JSON object."""
}
