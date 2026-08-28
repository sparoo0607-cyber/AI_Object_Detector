package com.accessibility.detector.ocr

import android.graphics.RectF
import com.google.mlkit.vision.text.Text

data class ExtractedTextBlock(
    val text: String,
    val boundingBox: RectF,
    val confidence: Float = 0.90f
)

/**
 * Utility to process, sanitize, and prioritize recognized text blocks.
 */
class TextProcessor {

    private var lastSpokenText: String? = null
    private var lastSpokenTimestamp: Long = 0L

    fun processMlKitText(text: Text, imageWidth: Int, imageHeight: Int): List<ExtractedTextBlock> {
        val blocks = mutableListOf<ExtractedTextBlock>()

        for (block in text.textBlocks) {
            val rawText = block.text.trim()
            val box = block.boundingBox

            if (rawText.length >= 2 && box != null) {
                // Filter out non-alphanumeric noise
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

        // Sort by area / centrality (largest/most prominent text first)
        return blocks.sortedByDescending { it.boundingBox.width() * it.boundingBox.height() }
    }

    fun sanitizeText(text: String): String {
        return text.replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
