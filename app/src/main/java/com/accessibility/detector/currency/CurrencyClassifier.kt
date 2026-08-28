package com.accessibility.detector.currency

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * SEE / Currency — a real trained TFLite image classifier (MobileNet-based,
 * Teachable-Machine-exported), not a color heuristic. Recognizes ₹50, ₹100,
 * ₹200, ₹500, ₹2000 from `assets/currency_model.tflite` +
 * `assets/currency_labels.txt`.
 *
 * Model provenance: publicly trained Indian-currency image classifier
 * (Teachable Machine MobileNet export, 224x224 RGB, [-1,1]-normalized
 * input, softmax output) — bundled and run fully on-device via the
 * standard TFLite Interpreter API, the same runtime already used by the
 * object detector in this app.
 */
class CurrencyClassifier(context: Context) {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val inputSize: Int

    data class Result(val label: String, val confidence: Float)

    init {
        val afd = context.assets.openFd("currency_model.tflite")
        val buffer = FileInputStream(afd.fileDescriptor).channel.map(
            FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
        )
        interpreter = Interpreter(buffer, Interpreter.Options().apply { setNumThreads(2) })
        val inputShape = interpreter.getInputTensor(0).shape() // [1, H, W, 3]
        inputSize = inputShape[1]
        labels = context.assets.open("currency_labels.txt").bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun classify(bitmap: Bitmap): Result {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).apply { order(ByteOrder.nativeOrder()) }
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        for (px in pixels) {
            input.putFloat((((px shr 16) and 0xFF) - 127.5f) / 127.5f)
            input.putFloat((((px shr 8) and 0xFF) - 127.5f) / 127.5f)
            input.putFloat(((px and 0xFF) - 127.5f) / 127.5f)
        }
        input.rewind()

        val outputShape = interpreter.getOutputTensor(0).shape()
        val output = Array(1) { FloatArray(outputShape[1]) }
        interpreter.run(input, output)

        val scores = output[0]
        var bestIdx = 0
        for (i in scores.indices) if (scores[i] > scores[bestIdx]) bestIdx = i

        val rawLabel = labels.getOrElse(bestIdx) { "Unknown" }
        val denomination = Regex("\\d+").find(rawLabel)?.value
        val label = if (denomination != null) "₹$denomination" else rawLabel
        return Result(label, scores[bestIdx])
    }

    fun close() = interpreter.close()
}
