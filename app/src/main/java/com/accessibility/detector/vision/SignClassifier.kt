package com.accessibility.detector.vision

import android.content.Context
import android.graphics.RectF
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

data class SignDetection(
    val gestureName: String,
    val spokenText: String,
    val confidence: Float,
    val boundingBox: RectF,
    val letter: Char
)

/**
 * Sign / fingerspelling classifier driven by **real MediaPipe hand landmarks**
 * (see [HandLandmarkerHelper]).
 *
 * Pipeline:
 *   1. `sign_language_model.tflite`         — 42-feature landmark model (x-minX, y-minY per point).
 *                                             Labels come from `sign_language_labels.json`
 *                                             (the shipped model knows A / B / L only).
 *   2. `sign_language_ml_alphabets.tflite`  — optional 63-feature (x,y,z) 26-class A–Z model.
 *
 * Both are optional. If neither model loads, [classify] returns null and the caller
 * reports the feature as unavailable — nothing is fabricated, and confidences are the
 * model's real soft-max output (no clamping).
 */
class SignClassifier(private val context: Context? = null) {

    private var landmark42Interpreter: Interpreter? = null
    private var alphabet63Interpreter: Interpreter? = null

    private var labels42: Array<String> = arrayOf("A", "B", "L")
    private var out42: Array<FloatArray> = Array(1) { FloatArray(3) }

    private val alphabet26 = ('A'..'Z').toList().toCharArray()
    private val out63 = Array(1) { FloatArray(26) }

    private val in42: ByteBuffer =
        ByteBuffer.allocateDirect(42 * 4).order(ByteOrder.nativeOrder())
    private val in63: ByteBuffer =
        ByteBuffer.allocateDirect(63 * 4).order(ByteOrder.nativeOrder())

    /** Minimum soft-max probability before a prediction is surfaced at all. */
    var confidenceThreshold: Float = 0.60f

    private var stableGesture: String? = null
    private var stableFrames = 0
    private val requiredStableFrames = 3

    private val spokenPhrases = mapOf(
        'A' to "Sign A.", 'B' to "Sign B: Hello.", 'C' to "Sign C: Come.",
        'D' to "Sign D.", 'E' to "Sign E.", 'F' to "Sign F: Food.",
        'G' to "Sign G: Go.", 'H' to "Sign H: Help.", 'I' to "Sign I.",
        'J' to "Sign J.", 'K' to "Sign K.", 'L' to "Sign L: Please.",
        'M' to "Sign M.", 'N' to "Sign N: No.", 'O' to "Sign O.",
        'P' to "Sign P.", 'Q' to "Sign Q.", 'R' to "Sign R.",
        'S' to "Sign S: Stop.", 'T' to "Sign T: Thank you.", 'U' to "Sign U.",
        'V' to "Sign V: Peace.", 'W' to "Sign W: Water.", 'X' to "Sign X.",
        'Y' to "Sign Y: Yes.", 'Z' to "Sign Z."
    )

    val isAvailable: Boolean
        get() = landmark42Interpreter != null || alphabet63Interpreter != null

    init {
        initModels()
    }

    private fun initModels() {
        val ctx = context ?: return
        val opts = Interpreter.Options().apply { setNumThreads(2) }

        try {
            landmark42Interpreter = Interpreter(loadModel(ctx, "sign_language_model.tflite"), opts)
            loadLabels42(ctx)
            Log.d(TAG, "Loaded sign_language_model.tflite (${labels42.size} classes: ${labels42.joinToString()})")
        } catch (e: Exception) {
            Log.w(TAG, "sign_language_model.tflite not loaded: ${e.message}")
        }

        try {
            alphabet63Interpreter = Interpreter(loadModel(ctx, "sign_language_ml_alphabets.tflite"), opts)
            Log.d(TAG, "Loaded sign_language_ml_alphabets.tflite (26 classes A-Z)")
        } catch (e: Exception) {
            Log.w(TAG, "sign_language_ml_alphabets.tflite not loaded: ${e.message}")
        }
    }

    private fun loadLabels42(ctx: Context) {
        try {
            val json = JSONObject(
                ctx.assets.open("sign_language_labels.json").bufferedReader().use { it.readText() }
            )
            val arr = json.optJSONArray("labels") ?: return
            if (arr.length() > 0) {
                labels42 = Array(arr.length()) { arr.getString(it) }
                out42 = Array(1) { FloatArray(labels42.size) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Using default 42-model labels: ${labels42.joinToString()}")
        }
    }

    private fun loadModel(ctx: Context, name: String): MappedByteBuffer {
        // Standard TFLite asset-mmap pattern (matches the official examples).
        val fd = ctx.assets.openFd(name)
        val input = FileInputStream(fd.fileDescriptor)
        val channel = input.channel
        return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    /**
     * Classify a single detected hand ([HandLandmarkerHelper.HandLandmarks] carries the
     * upright image dimensions used to map the normalised box to overlay pixels).
     */
    fun classify(hand: HandLandmarkerHelper.HandLandmarks): SignDetection? {
        if (!isAvailable || hand.points.size < 21) {
            stableGesture = null; stableFrames = 0
            return null
        }

        val prediction = run42(hand.points) ?: run63(hand.points, hand.depths)
        if (prediction == null || prediction.second < confidenceThreshold) {
            stableGesture = null; stableFrames = 0
            return null
        }

        val (letter, confidence) = prediction
        val gestureName = "Sign $letter"

        // Temporal smoothing: only emit after N consistent frames.
        if (gestureName == stableGesture) {
            stableFrames++
        } else {
            stableGesture = gestureName
            stableFrames = 1
        }
        if (stableFrames < requiredStableFrames) return null

        val box = RectF(
            hand.normalizedBox.left * hand.imageWidth,
            hand.normalizedBox.top * hand.imageHeight,
            hand.normalizedBox.right * hand.imageWidth,
            hand.normalizedBox.bottom * hand.imageHeight
        )

        return SignDetection(
            gestureName = gestureName,
            spokenText = spokenPhrases[letter] ?: "Sign $letter.",
            confidence = confidence,
            boundingBox = box,
            letter = letter
        )
    }

    /**
     * 42-feature model. Matches Python `create_dataset.py`:
     *   for each of 21 landmarks: (x - min_x), (y - min_y)
     */
    private fun run42(points: List<Pair<Float, Float>>): Pair<Char, Float>? {
        val interp = landmark42Interpreter ?: return null
        return try {
            val minX = points.minOf { it.first }
            val minY = points.minOf { it.second }
            in42.rewind()
            for (i in 0 until 21) {
                val (x, y) = points[i]
                in42.putFloat(x - minX)
                in42.putFloat(y - minY)
            }
            interp.run(in42, out42)
            val (idx, prob) = argmaxSoftmax(out42[0])
            val label = labels42.getOrNull(idx)?.firstOrNull() ?: return null
            label.uppercaseChar() to prob
        } catch (e: Exception) {
            Log.w(TAG, "42-feature inference error: ${e.message}")
            null
        }
    }

    /**
     * 63-feature model: (x, y, z) per landmark, translated so the wrist (point 0) is the origin
     * and scaled by the hand's bounding-box diagonal for scale invariance.
     */
    private fun run63(points: List<Pair<Float, Float>>, depths: List<Float>): Pair<Char, Float>? {
        val interp = alphabet63Interpreter ?: return null
        if (depths.size < 21) return null
        return try {
            val (wx, wy) = points[0]
            val wz = depths[0]
            val spanX = points.maxOf { it.first } - points.minOf { it.first }
            val spanY = points.maxOf { it.second } - points.minOf { it.second }
            val scale = maxOf(spanX, spanY, 1e-4f)

            in63.rewind()
            for (i in 0 until 21) {
                in63.putFloat((points[i].first - wx) / scale)
                in63.putFloat((points[i].second - wy) / scale)
                in63.putFloat((depths[i] - wz) / scale)
            }
            interp.run(in63, out63)
            val (idx, prob) = argmaxSoftmax(out63[0])
            (alphabet26.getOrNull(idx) ?: return null) to prob
        } catch (e: Exception) {
            Log.w(TAG, "63-feature inference error: ${e.message}")
            null
        }
    }

    /**
     * Returns (argmaxIndex, probability). Applies a soft-max only if the raw output does
     * not already look like a probability distribution (some exported heads emit logits).
     */
    private fun argmaxSoftmax(raw: FloatArray): Pair<Int, Float> {
        val sum = raw.sum()
        val looksNormalised = raw.all { it in 0f..1.0001f } && kotlin.math.abs(sum - 1f) < 0.05f
        val probs = if (looksNormalised) raw else softmax(raw)
        var bestIdx = 0
        var best = Float.NEGATIVE_INFINITY
        for (i in probs.indices) if (probs[i] > best) { best = probs[i]; bestIdx = i }
        return bestIdx to best
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        val exps = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
        val s = exps.sum().takeIf { it > 0f } ?: 1f
        return FloatArray(exps.size) { exps[it] / s }
    }

    fun reset() {
        stableGesture = null
        stableFrames = 0
    }

    fun close() {
        try {
            landmark42Interpreter?.close(); landmark42Interpreter = null
            alphabet63Interpreter?.close(); alphabet63Interpreter = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing interpreters: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SignClassifier"
    }
}
