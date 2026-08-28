package com.accessibility.detector.vision

import android.graphics.RectF
import com.google.mlkit.vision.text.Text

data class ExtractedTextBlock(
    val text: String,
    val boundingBox: RectF,
    val confidence: Float = 0.90f
)

/**
 * Utility to process, clean, and deduplicate OCR text.
 */
class TextProcessor {

    private var lastRecognizedSignature: String = ""

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

    fun isNewText(fullText: String): Boolean {
        val signature = fullText.take(60).lowercase().replace(Regex("[^a-z0-9]"), "")
        if (signature.isEmpty()) return false
        if (signature == lastRecognizedSignature) return false

        lastRecognizedSignature = signature
        return true
    }

    fun resetSignature() {
        lastRecognizedSignature = ""
    }

    fun sanitizeText(text: String): String {
        return text.replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
