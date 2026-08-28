package com.accessibility.detector.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * SEE / Signboard OCR — Google ML Kit Text Recognition, fully
 * on-device after the model is bundled in the APK.
 *
 * Honest limitation, disclosed in Admin > AI Models: ML Kit ships
 * on-device recognizers for Latin and Devanagari (Hindi) script.
 * Telugu script recognition has no on-device ML Kit model as of this
 * build — English and Hindi signboards are read; Telugu-script
 * signboards are a roadmap item (see Admin > AI Models).
 *
 * ML Kit's public API also does not expose a numeric per-result
 * confidence score (unlike Tesseract on the web prototype), so
 * `confidence` here is a presence-based proxy — 0.85 when text of
 * reasonable length is found, 0 when nothing usable is found — not a
 * true model confidence. This is stated plainly rather than faking a
 * precise number.
 */
class OcrHelper {

    private val latinRecognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val devanagariRecognizer: TextRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    data class Result(val text: String, val confidence: Float, val scriptTried: String)

    fun recognize(bitmap: Bitmap, preferredLang: String, onResult: (Result) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = if (preferredLang == "hi") devanagariRecognizer else latinRecognizer
        val scriptLabel = if (preferredLang == "hi") "Devanagari" else "Latin"

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text.trim().replace(Regex("\\s+"), " ")
                if (raw.length >= 2) {
                    onResult(Result(raw, 0.85f, scriptLabel))
                } else {
                    onResult(Result("", 0f, scriptLabel))
                }
            }
            .addOnFailureListener {
                onResult(Result("", 0f, scriptLabel))
            }
    }

    fun close() {
        latinRecognizer.close()
        devanagariRecognizer.close()
    }
}
