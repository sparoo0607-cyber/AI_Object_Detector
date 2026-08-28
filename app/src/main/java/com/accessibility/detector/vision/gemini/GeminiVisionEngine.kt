package com.accessibility.detector.vision.gemini

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.accessibility.detector.core.PerceptionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * High-level Visual Reasoning Engine for SAHEY Vision Assist.
 * Acts as the selective deep reasoning layer on top of local real-time detection.
 */
class GeminiVisionEngine(
    private val context: Context,
    private val service: GeminiVisionService = GeminiVisionService(context)
) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var lastGeminiCallTime: Long = 0L
    private val minCooldownMs: Long = 3500L
    private var isBusy: Boolean = false
    private var lastAnnouncedDangerSignature: String = ""

    /**
     * Selectively evaluates a suspicious frame (e.g. potential fire, slippery floor, sudden obstacle).
     * Enforces strict rate-limiting and duplicate suppression.
     */
    fun analyzeSuspiciousFrame(
        bitmap: Bitmap,
        hazardHint: String,
        onResult: (PerceptionEvent?) -> Unit
    ) {
        val now = SystemClock.uptimeMillis()
        if (isBusy || (now - lastGeminiCallTime < minCooldownMs)) {
            return
        }

        if (!GeminiConfig.isGeminiConfigured(context)) {
            Log.d(TAG, "Gemini not configured; continuing with on-device detection.")
            return
        }

        isBusy = true
        lastGeminiCallTime = now

        scope.launch {
            try {
                val base64Jpeg = compressBitmapToBase64(bitmap)
                val prompt = "Potential situation detected: '$hazardHint'. Verify if there is an active safety hazard, obstacle, fire, vehicle, or slippery surface. Return JSON."

                val rawJson = service.analyzeImage(base64Jpeg, prompt)
                if (!rawJson.isNullOrBlank()) {
                    val parsed = GeminiResponseParser.parse(rawJson)
                    if (parsed != null && parsed.dangerDetected) {
                        val signature = "${parsed.dangerType}_${parsed.direction}"
                        if (signature != lastAnnouncedDangerSignature) {
                            lastAnnouncedDangerSignature = signature
                            val event = GeminiResponseParser.toPerceptionEvent(parsed)
                            withContext(Dispatchers.Main) {
                                onResult(event)
                            }
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini reasoning failed: ${e.message}")
            } finally {
                isBusy = false
            }
        }
    }

    /**
     * User-requested Visual Q&A (e.g. "What is around me?" or "Is there any danger?").
     */
    fun askAi(
        bitmap: Bitmap,
        question: String = "Describe what is around the user in 1-2 concise sentences for a visually impaired person.",
        onResult: (PerceptionEvent?) -> Unit
    ) {
        if (isBusy) {
            return
        }

        isBusy = true
        lastGeminiCallTime = SystemClock.uptimeMillis()

        scope.launch {
            try {
                val base64Jpeg = compressBitmapToBase64(bitmap)
                val rawJson = service.analyzeImage(base64Jpeg, question)

                if (!rawJson.isNullOrBlank()) {
                    val parsed = GeminiResponseParser.parse(rawJson)
                    if (parsed != null) {
                        val event = GeminiResponseParser.toPerceptionEvent(parsed)
                        withContext(Dispatchers.Main) {
                            onResult(event)
                        }
                        return@launch
                    }
                }

                // Fallback if structured parse returned empty
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ask AI error: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            } finally {
                isBusy = false
            }
        }
    }

    private suspend fun compressBitmapToBase64(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val maxDimension = 640
        val width = bitmap.width
        val height = bitmap.height
        val scale = if (width > maxDimension || height > maxDimension) {
            maxDimension.toFloat() / maxOf(width, height)
        } else {
            1.0f
        }

        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val byteArray = outputStream.toByteArray()

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        return@withContext Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    companion object {
        private const val TAG = "GeminiVisionEngine"
    }
}
