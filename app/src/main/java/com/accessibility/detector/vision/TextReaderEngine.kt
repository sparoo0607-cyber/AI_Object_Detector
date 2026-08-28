package com.accessibility.detector.vision

import android.graphics.Bitmap
import android.util.Log
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

interface TextReaderListener {
    fun onTextDiscovered(
        blocks: List<ExtractedTextBlock>,
        fullText: String,
        isNewContent: Boolean
    )
    fun onTextReaderError(error: String)
}

/**
 * On-device real-time OCR engine using Google ML Kit Text Recognition.
 */
class TextReaderEngine(
    private val listener: TextReaderListener,
    val textProcessor: TextProcessor = TextProcessor()
) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var isBusy = false
    var cachedFullText: String = ""
        private set

    fun processBitmap(bitmap: Bitmap, rotationDegrees: Int) {
        if (isBusy) return
        isBusy = true

        val image = InputImage.fromBitmap(bitmap, rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = textProcessor.processMlKitText(
                    visionText,
                    bitmap.width,
                    bitmap.height
                )

                if (blocks.isNotEmpty()) {
                    val fullText = blocks.joinToString(" ") { it.text }
                    cachedFullText = fullText
                    val isNew = textProcessor.isNewText(fullText)
                    listener.onTextDiscovered(blocks, fullText, isNew)
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR error: ${e.message}", e)
                listener.onTextReaderError("OCR Error: ${e.localizedMessage}")
            }
            .addOnCompleteListener {
                isBusy = false
            }
    }

    fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TextRecognizer", e)
        }
    }

    companion object {
        private const val TAG = "TextReaderEngine"
    }
}
