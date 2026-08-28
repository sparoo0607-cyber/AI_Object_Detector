package com.accessibility.detector.enhance

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.accessibility.detector.BuildConfig
import com.accessibility.detector.core.SahayConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * SAHAY ASSISTIVE INTELLIGENCE ENGINE (Gemini 2.0 Flash Multimodal Brain).
 *
 * Architecture:
 * - Gemini acts as the Central Brain with an explicit Accessibility System Instruction.
 * - On-device ML models (Object Detector, OCR, Currency, Sound) operate as specialized
 *   perception tools feeding local sensor context ([LocalContext]).
 * - Gemini synthesizes camera frame + tool outputs into actionable, safe, concise
 *   spoken Telugu/configured language guidance for visually impaired users.
 */
object GeminiEnhancer {

    private const val TAG = "SahayBrain"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val inFlight = AtomicBoolean(false)

    /**
     * Structured snapshot of outputs from local specialized perception tools.
     */
    data class LocalContext(
        val detectedObjects: List<String> = emptyList(),   // e.g. ["Chair", "Table"]
        val ocrText: String? = null,                       // e.g. "VISAKHAPATNAM BUS STATION - PLATFORM 3"
        val currencyLabel: String? = null,                 // e.g. "500 Rupees Note"
        val soundEvent: String? = null                     // e.g. "Bus Horn / Alarm"
    )

    fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    fun isAvailable(): Boolean = isConfigured()

    /**
     * Executes the central SAHAY Multimodal Intelligence pass.
     */
    fun describeScene(
        bitmap: Bitmap,
        localContext: LocalContext = LocalContext(),
        targetLang: String = "te",
        onResult: (String) -> Unit
    ) {
        if (!isAvailable()) return
        if (!inFlight.compareAndSet(false, true)) return

        thread(name = "SahayBrainThread") {
            try {
                val jpeg = downscaleToJpegBase64(bitmap, maxDim = 512)
                val systemPrompt = buildSahaySystemInstruction(targetLang)
                val userContentPrompt = buildUserPromptWithToolData(localContext)

                val body = JSONObject().apply {
                    // System Instruction defining SAHAY domain behavior
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", systemPrompt)))
                    })

                    // Multimodal user content (Text prompt + Camera Frame)
                    put("contents", JSONArray().put(JSONObject().apply {
                        put("parts", JSONArray()
                            .put(JSONObject().put("text", userContentPrompt))
                            .put(JSONObject().put("inline_data", JSONObject()
                                .put("mime_type", "image/jpeg")
                                .put("data", jpeg))))
                    }))

                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", 120)
                        put("temperature", 0.2) // Low temperature for high accuracy & safety
                    })
                }

                val url = URL("$ENDPOINT?key=${BuildConfig.GEMINI_API_KEY}")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 6000
                    readTimeout = 8000
                    doOutput = true
                }
                conn.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = conn.responseCode
                if (code == 200) {
                    val text = conn.inputStream.bufferedReader().readText()
                    val result = JSONObject(text)
                        .getJSONArray("candidates").getJSONObject(0)
                        .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                        .getString("text").trim()
                    if (result.isNotEmpty()) mainHandler.post { onResult(result) }
                } else {
                    val errBody = conn.errorStream?.bufferedReader()?.readText() ?: ""
                    Log.e(TAG, "Gemini API Error (HTTP $code): $errBody")
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Sahay Brain execution error (${e.javaClass.simpleName}: ${e.message})")
            } finally {
                inFlight.set(false)
            }
        }
    }

    /**
     * Builds the domain-specific System Instruction for SAHAY.
     */
    private fun buildSahaySystemInstruction(langCode: String): String {
        val langName = when (langCode) {
            "hi" -> "Hindi"
            "te" -> "Telugu"
            else -> "English"
        }

        return """
            You are SAHAY, an assistive intelligence system designed for blind and visually impaired individuals.
            Your responsibility is NOT to describe every aesthetic detail of the photo.
            Your primary goal is to identify information that is immediately useful, actionable, and important for safe navigation.
            
            Prioritization hierarchy:
            1. Immediate safety hazards & obstacles (steps, tripping hazards, vehicles, doors, directions to move safely).
            2. Text & Signboards (read bus numbers, platform numbers, store signs naturally).
            3. Currency identification (clearly state money value).
            4. Audio alerts / context.
            5. Clear directional guidance (ahead, left, right).
            
            Response Rules:
            - Output MUST be in $langName language.
            - Keep responses concise (1 to 2 short spoken sentences) optimized for Text-To-Speech (TTS).
            - Never invent information or claim false certainty.
            - Speak directly to the user as their real-time guide. Do not mention "photo", "image", "camera", or "AI".
        """.trimIndent()
    }

    /**
     * Formats sensor outputs from local tool execution.
     */
    private fun buildUserPromptWithToolData(ctx: LocalContext): String {
        val sb = StringBuilder("Specialized local tool detection outputs:\n")

        if (ctx.detectedObjects.isNotEmpty()) {
            sb.append("- Objects Tool: ${ctx.detectedObjects.joinToString(", ")}\n")
        }
        if (!ctx.ocrText.isNullOrBlank()) {
            sb.append("- OCR Tool (Text detected): \"${ctx.ocrText}\"\n")
        }
        if (!ctx.currencyLabel.isNullOrBlank()) {
            sb.append("- Currency Tool: ${ctx.currencyLabel}\n")
        }
        if (!ctx.soundEvent.isNullOrBlank()) {
            sb.append("- Audio Tool: ${ctx.soundEvent}\n")
        }

        sb.append("\nUsing the camera image and these tool outputs, synthesize a direct, helpful, and concise response in Telugu for the blind user.")
        return sb.toString()
    }

    private fun downscaleToJpegBase64(bitmap: Bitmap, maxDim: Int): String {
        val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

