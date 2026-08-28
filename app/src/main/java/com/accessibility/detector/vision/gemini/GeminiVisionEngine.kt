package com.accessibility.detector.vision.gemini

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType
import com.accessibility.detector.core.SpatialPosition
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
    private val minCooldownMs: Long = 2500L
    private var isBusy: Boolean = false
    private var lastAnnouncedDangerSignature: String = ""

    /**
     * Selectively evaluates a suspicious frame (e.g. fire/smoke, fire on screen, slippery floor, obstacle).
     * Enforces strict rate-limiting, debug logging, and offline fallback.
     */
    fun analyzeSuspiciousFrame(
        bitmap: Bitmap,
        hazardHint: String,
        isScreenFireCandidate: Boolean = false,
        onResult: (PerceptionEvent?) -> Unit
    ) {
        val now = SystemClock.uptimeMillis()
        if (isBusy || (now - lastGeminiCallTime < minCooldownMs)) {
            return
        }

        Log.d(TAG_VISION_DEBUG, "VISION_AI: Trigger = $hazardHint (isScreenFire=$isScreenFireCandidate)")

        // 1. If Gemini Cloud API is configured, run deep multimodal analysis
        if (GeminiConfig.isGeminiConfigured(context)) {
            isBusy = true
            lastGeminiCallTime = now

            scope.launch {
                try {
                    val base64Jpeg = compressBitmapToBase64(bitmap)
                    val prompt = if (isScreenFireCandidate) {
                        "A screen (laptop/TV/phone) is in view with potential flame/fire imagery. Determine if fire is displayed on screen ('fire_on_screen'), real fire ('fire'), or a normal display without fire ('none'). Return JSON."
                    } else {
                        "Potential situation detected: '$hazardHint'. Verify if there is an active safety hazard (fire, smoke, vehicle, slippery floor, drop, or obstacle). Return JSON."
                    }

                    Log.d(TAG_VISION_DEBUG, "VISION_AI: Sending selected frame to Gemini Vision...")
                    val rawJson = service.analyzeImage(base64Jpeg, prompt)

                    if (!rawJson.isNullOrBlank()) {
                        val parsed = GeminiResponseParser.parse(rawJson)
                        if (parsed != null && parsed.dangerDetected) {
                            val signature = "${parsed.dangerType}_${parsed.direction}"
                            if (signature != lastAnnouncedDangerSignature) {
                                lastAnnouncedDangerSignature = signature

                                Log.d(TAG_VISION_DEBUG, "GEMINI: danger_type = ${parsed.dangerType}, confidence = ${parsed.confidence}")
                                Log.d(TAG_VISION_DEBUG, "FINAL: ${parsed.dangerType}, priority = ${parsed.priority}, TTS: \"${parsed.message}\"")

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
            return
        }

        // 2. Offline Fallback: If Gemini is offline or not configured, apply local danger reasoning
        Log.d(TAG_VISION_DEBUG, "VISION_AI: Gemini not configured or offline. Applying on-device hazard decision.")
        if (isScreenFireCandidate) {
            val event = PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Screen Fire",
                spokenText = "Fire visible on the screen.",
                confidence = 0.90f,
                priority = EventPriority.DANGER,
                spatialPosition = SpatialPosition.CENTER
            )
            onResult(event)
        } else if (hazardHint.contains("fire", ignoreCase = true) || hazardHint.contains("flame", ignoreCase = true)) {
            val event = PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Fire Hazard",
                spokenText = "Warning. Fire detected.",
                confidence = 0.92f,
                priority = EventPriority.CRITICAL,
                spatialPosition = SpatialPosition.CENTER
            )
            onResult(event)
        } else if (hazardHint.contains("smoke", ignoreCase = true)) {
            val event = PerceptionEvent(
                type = PerceptionType.DANGER,
                label = "Smoke Hazard",
                spokenText = "Warning. Smoke detected.",
                confidence = 0.86f,
                priority = EventPriority.DANGER,
                spatialPosition = SpatialPosition.CENTER
            )
            onResult(event)
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
                Log.d(TAG_VISION_DEBUG, "VISION_AI: User Ask AI query: \"$question\"")

                val rawJson = service.analyzeImage(base64Jpeg, question)
                if (!rawJson.isNullOrBlank()) {
                    val parsed = GeminiResponseParser.parse(rawJson)
                    if (parsed != null) {
                        Log.d(TAG_VISION_DEBUG, "GEMINI Ask AI Result: \"${parsed.message}\"")
                        val event = GeminiResponseParser.toPerceptionEvent(parsed)
                        withContext(Dispatchers.Main) {
                            onResult(event)
                        }
                        return@launch
                    }
                }

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
        private const val TAG_VISION_DEBUG = "VISION_AI"
    }
}
