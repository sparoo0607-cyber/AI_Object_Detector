package com.accessibility.detector.vision

import android.graphics.RectF
import android.os.SystemClock
import com.google.mlkit.vision.text.Text

data class ExtractedTextBlock(
    val text: String,
    val boundingBox: RectF,
    val confidence: Float = 0.90f
)

/**
 * Utility to process, clean, and deduplicate OCR text.
 * Implements robust text signature matching and long cooldown to prevent repetitive reading prompts.
 */
class TextProcessor {

    private var lastRecognizedSignature: String = ""
    private var lastRecognizedTimestamp: Long = 0L
    private val textRePromptCooldownMs = 12000L // 12 seconds cooldown on the same/similar text

    fun processMlKitText(text: Text, imageWidth: Int, imageHeight: Int): List<ExtractedTextBlock> {
        val blocks = mutableListOf<ExtractedTextBlock>()

        for (block in text.textBlocks) {
            val rawText = block.text.trim()
            val box = block.boundingBox

            if (rawText.length >= 2 && box != null) {
                val cleanText = sanitizeText(rawText)
                if (cleanText.isNotBlank()) {
                    blocks.add(
                        ExtractedTextBlock(
                            text = cleanText,
                            boundingBox = RectF(box),
                            confidence = 0.90f
                        )
                    )
                }
            }
        }

        return blocks.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
    }

    /**
     * Checks if this is genuinely new text that hasn't been prompted or read recently.
     */
    fun isNewText(fullText: String): Boolean {
        val signature = generateSignature(fullText)
        if (signature.length < 3) return false

        val now = SystemClock.uptimeMillis()
        val timeSinceLast = now - lastRecognizedTimestamp

        // If it matches the recent signature and is within cooldown, do not treat as new
        if (signature == lastRecognizedSignature && timeSinceLast < textRePromptCooldownMs) {
            return false
        }

        // Check similarity: if > 70% of characters match the previous signature, suppress re-prompt
        if (timeSinceLast < textRePromptCooldownMs && calculateSimilarity(signature, lastRecognizedSignature) > 0.65f) {
            return false
        }

        lastRecognizedSignature = signature
        lastRecognizedTimestamp = now
        return true
    }

    fun markTextAsRead(fullText: String) {
        lastRecognizedSignature = generateSignature(fullText)
        lastRecognizedTimestamp = SystemClock.uptimeMillis() + 8000L // Extra buffer after reading
    }

    fun resetSignature() {
        lastRecognizedSignature = ""
        lastRecognizedTimestamp = 0L
    }

    private fun generateSignature(fullText: String): String {
        return fullText.take(100).lowercase().replace(Regex("[^a-z0-9]"), "")
    }

    private fun calculateSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0f
        val commonPrefix = s1.commonPrefixWith(s2).length
        val maxLen = maxOf(s1.length, s2.length)
        return commonPrefix.toFloat() / maxLen
    }

    fun sanitizeText(text: String): String {
        return text.replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
