package com.accessibility.detector.ocr

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.accessibility.detector.detection.DetectionResult
import com.accessibility.detector.detection.EventPriority
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

interface TextReaderListener {
    fun onTextRecognized(
        blocks: List<ExtractedTextBlock>,
        event: PerceptionEvent?
    )
    fun onTextReaderError(error: String)
}

/**
 * On-device real-time OCR engine using Google ML Kit Text Recognition.
 */
class TextReaderEngine(
    private val listener: TextReaderListener,
    private val textProcessor: TextProcessor = TextProcessor()
) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var isBusy = false

    @OptIn(ExperimentalGetImage::class)
    fun processFrame(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isBusy) {
            imageProxy.close()
            return
        }

        isBusy = true
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val blocks = textProcessor.processMlKitText(
                    visionText,
                    imageProxy.width,
                    imageProxy.height
                )

                val prominentBlock = blocks.firstOrNull()
                val event = prominentBlock?.let {
                    PerceptionEvent(
                        type = PerceptionType.TEXT,
                        label = it.text,
                        spokenText = "Text detected: ${it.text}",
                        confidence = 0.90f,
                        priority = EventPriority.TEXT
                    )
                }

                listener.onTextRecognized(blocks, event)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR Recognition error: ${e.message}", e)
                listener.onTextReaderError("OCR Error: ${e.localizedMessage}")
            }
            .addOnCompleteListener {
                isBusy = false
                imageProxy.close()
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
