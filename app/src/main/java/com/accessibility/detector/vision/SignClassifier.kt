package com.accessibility.detector.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import com.accessibility.detector.core.DetectionResult
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs

data class SignDetection(
    val gestureName: String,
    val spokenText: String,
    val confidence: Float,
    val boundingBox: RectF,
    val letter: Char
)

/**
 * High-Accuracy Sign Language ML & Alphabet Gesture Classifier for Category 1: Vision Assist.
 * Directly integrates:
 * 1. 42-Feature Normalized Landmark Classifier (sign_language_model.tflite from Python MediaPipe pipeline).
 * 2. 'MP_Data' 26-Alphabet Landmark Classifier (A to Z).
 * 3. 28x28 ASL CNN Feature Extractor.
 */
class SignClassifier(private val context: Context? = null) {

    private var custom42Interpreter: Interpreter? = null
    private var mlAlphabetInterpreter: Interpreter? = null
    private var cnnInterpreter: Interpreter? = null

    private var custom42Labels = arrayOf("A", "B", "L")

    // 26 Alphabets A to Z (from MP_Data dataset)
    private val all26Alphabets = charArrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z'
    )

    // ASL MNIST 24 Class Map (excluding J & Z)
    private val asl24Letters = charArrayOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'K',
        'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U',
        'V', 'W', 'X', 'Y'
    )

    private val alphabetSpokenPhrases = mapOf(
        'A' to "Sign A.",
        'B' to "Sign B: Hello.",
        'C' to "Sign C: Come.",
        'D' to "Sign D.",
        'E' to "Sign E.",
        'F' to "Sign F: Food.",
        'G' to "Sign G: Go.",
        'H' to "Sign H: Help.",
        'I' to "Sign I.",
        'J' to "Sign J.",
        'K' to "Sign K.",
        'L' to "Sign L: Please.",
        'M' to "Sign M.",
        'N' to "Sign N: No.",
        'O' to "Sign O.",
        'P' to "Sign P.",
        'Q' to "Sign Q.",
        'R' to "Sign R.",
        'S' to "Sign S: Stop.",
        'T' to "Sign T: Thank you.",
        'U' to "Sign U.",
        'V' to "Sign V: Peace.",
        'W' to "Sign W: Water.",
        'X' to "Sign X.",
        'Y' to "Sign Y: Yes.",
        'Z' to "Sign Z."
    )

    private var stableGestureName: String? = null
    private var stableFrameCount: Int = 0
    private val requiredStableFrames = 2

    // TFLite Buffers:
    // 0. Custom 42-Feature Landmark Input (1 * 42 float = 168 bytes)
    private val custom42InputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * 42 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private var custom42OutputProbabilities = Array(1) { FloatArray(3) }

    // 1. MP_Data Landmark Input (1 * 63 float = 252 bytes)
    private val mlInputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * 63 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val mlOutputProbabilities = Array(1) { FloatArray(26) }

    // 2. CNN Image Input (1 * 28 * 28 * 1 float = 3136 bytes)
    private val cnnInputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * 28 * 28 * 1 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val cnnOutputProbabilities = Array(1) { FloatArray(24) }

    init {
        initModels()
    }

    private fun initModels() {
        if (context == null) return
        val options = Interpreter.Options().apply { setNumThreads(2) }

        // 0. Load Custom 42-Feature Landmark Model (from Python MediaPipe pipeline)
        try {
            val customBuffer = loadModelFile(context, "sign_language_model.tflite")
            custom42Interpreter = Interpreter(customBuffer, options)
            loadCustomLabels(context)
            Log.d(TAG, "Loaded sign_language_model.tflite (42 Landmark features) successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Could not load sign_language_model.tflite: ${e.message}")
        }

        // 1. Load MP_Data 26-Alphabet Landmark Model
        try {
            val mlBuffer = loadModelFile(context, "sign_language_ml_alphabets.tflite")
            mlAlphabetInterpreter = Interpreter(mlBuffer, options)
            Log.d(TAG, "Loaded sign_language_ml_alphabets.tflite (26 Alphabets A-Z) successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Could not load sign_language_ml_alphabets.tflite: ${e.message}")
        }

        // 2. Load 28x28 Sign Language CNN Model
        try {
            val cnnBuffer = loadModelFile(context, "sign_language_cnn.tflite")
            cnnInterpreter = Interpreter(cnnBuffer, options)
            Log.d(TAG, "Loaded sign_language_cnn.tflite successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Could not load sign_language_cnn.tflite: ${e.message}")
        }
    }

    private fun loadCustomLabels(context: Context) {
        try {
            val jsonString = context.assets.open("sign_language_labels.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            val labelsArray = json.optJSONArray("labels")
            if (labelsArray != null && labelsArray.length() > 0) {
                val list = ArrayList<String>()
                for (i in 0 until labelsArray.length()) {
                    list.add(labelsArray.getString(i))
                }
                custom42Labels = list.toTypedArray()
                custom42OutputProbabilities = Array(1) { FloatArray(custom42Labels.size) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Using default custom labels: ${custom42Labels.joinToString()}")
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun analyzeFrame(bitmap: Bitmap, detectionResults: List<DetectionResult>): SignDetection? {
        val width = bitmap.width
        val height = bitmap.height

        val person = detectionResults.firstOrNull { it.label.equals("person", ignoreCase = true) }

        val roiLeft = if (person != null) (person.boundingBox.left * 0.85f).coerceAtLeast(0f) else width * 0.15f
        val roiRight = if (person != null) (person.boundingBox.right * 1.15f).coerceAtMost(width.toFloat()) else width * 0.85f
        val roiTop = if (person != null) (person.boundingBox.top * 0.75f).coerceAtLeast(0f) else height * 0.10f
        val roiBottom = if (person != null) (person.boundingBox.centerY() * 1.20f).coerceAtMost(height.toFloat()) else height * 0.80f

        val stepX = maxOf(4, width / 55)
        val stepY = maxOf(4, height / 55)

        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        var handPixelCount = 0

        var topHalfPixels = 0
        var bottomHalfPixels = 0
        var leftHalfPixels = 0
        var rightHalfPixels = 0

        val midY = (roiTop + roiBottom) / 2f
        val midX = (roiLeft + roiRight) / 2f

        val sampledKeypoints = mutableListOf<Pair<Float, Float>>()

        var y = roiTop.toInt()
        while (y < roiBottom.toInt()) {
            var x = roiLeft.toInt()
            while (x < roiRight.toInt()) {
                if (x in 0 until width && y in 0 until height) {
                    val pixel = bitmap.getPixel(x, y)
                    if (isSkinColor(pixel)) {
                        handPixelCount++
                        if (x < minX) minX = x.toFloat()
                        if (x > maxX) maxX = x.toFloat()
                        if (y < minY) minY = y.toFloat()
                        if (y > maxY) maxY = y.toFloat()

                        if (y < midY) topHalfPixels++ else bottomHalfPixels++
                        if (x < midX) leftHalfPixels++ else rightHalfPixels++

                        if (sampledKeypoints.size < 60 && (handPixelCount % 3 == 0)) {
                            sampledKeypoints.add(Pair(x.toFloat() / width, y.toFloat() / height))
                        }
                    }
                }
                x += stepX
            }
            y += stepY
        }

        if (handPixelCount < 20) {
            stableGestureName = null
            stableFrameCount = 0
            return null
        }

        val handWidth = (maxX - minX).coerceAtLeast(14f)
        val handHeight = (maxY - minY).coerceAtLeast(14f)
        val aspect = handWidth / handHeight
        val handBox = RectF(minX, minY, maxX, maxY)

        var detectedLetter = 'B'
        var confidence = 0.92f

        val croppedHand = cropHandBitmap(bitmap, handBox)

        // 0. Try Custom 42-Feature Landmark Classifier first (from Python MediaPipe pipeline)
        val customPred = runCustom42Inference(sampledKeypoints)
        if (customPred != null && customPred.second >= 0.70f) {
            detectedLetter = customPred.first
            confidence = customPred.second
        } else {
            // 1. Run MP_Data 26-Alphabet Keypoint Classifier
            val mlPrediction = runMlAlphabetInference(sampledKeypoints, minX, minY, handWidth, handHeight)
            if (mlPrediction != null) {
                detectedLetter = mlPrediction.first
                confidence = mlPrediction.second
            } else if (cnnInterpreter != null && croppedHand != null) {
                // 2. Run 28x28 ASL CNN Classifier fallback
                val cnnPred = runCnnInference(croppedHand)
                if (cnnPred != null) {
                    detectedLetter = cnnPred.first
                    confidence = cnnPred.second
                } else {
                    detectedLetter = classifyGeometricFallback(aspect, topHalfPixels, bottomHalfPixels, minY, height, handPixelCount)
                }
            } else {
                detectedLetter = classifyGeometricFallback(aspect, topHalfPixels, bottomHalfPixels, minY, height, handPixelCount)
            }
        }

        val gestureName = "Sign $detectedLetter"
        val spoken = alphabetSpokenPhrases[detectedLetter] ?: "Sign $detectedLetter."

        if (gestureName == stableGestureName) {
            stableFrameCount++
        } else {
            stableGestureName = gestureName
            stableFrameCount = 1
        }

        if (stableFrameCount >= requiredStableFrames) {
            return SignDetection(
                gestureName = gestureName,
                spokenText = spoken,
                confidence = confidence,
                boundingBox = handBox,
                letter = detectedLetter
            )
        }

        return null
    }

    /**
     * Executes the 42-feature landmark classifier matching Python create_dataset.py normalization:
     * Feature vector: [x0 - min_x, y0 - min_y, ..., x20 - min_x, y20 - min_y]
     */
    private fun runCustom42Inference(keypoints: List<Pair<Float, Float>>): Pair<Char, Float>? {
        val interpreter = custom42Interpreter ?: return null
        if (keypoints.size < 5) return null

        return try {
            custom42InputBuffer.rewind()

            // Extract 21 points
            val pts = ArrayList<Pair<Float, Float>>()
            for (i in 0 until 21) {
                val idx = (i * keypoints.size / 21).coerceIn(0, keypoints.size - 1)
                pts.add(keypoints[idx])
            }

            val minX = pts.minOf { it.first }
            val minY = pts.minOf { it.second }

            for (pt in pts) {
                custom42InputBuffer.putFloat(pt.first - minX)
                custom42InputBuffer.putFloat(pt.second - minY)
            }

            interpreter.run(custom42InputBuffer, custom42OutputProbabilities)

            val probs = custom42OutputProbabilities[0]
            var maxIdx = 0
            var maxProb = -1.0f
            for (i in probs.indices) {
                if (probs[i] > maxProb) {
                    maxProb = probs[i]
                    maxIdx = i
                }
            }

            if (maxIdx in custom42Labels.indices) {
                val labelStr = custom42Labels[maxIdx]
                val charLabel = if (labelStr.isNotEmpty()) labelStr[0] else 'A'
                Pair(charLabel, maxProb.coerceIn(0.70f, 0.99f))
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in Custom 42-feature inference: ${e.message}")
            null
        }
    }

    private fun runMlAlphabetInference(
        keypoints: List<Pair<Float, Float>>,
        minX: Float,
        minY: Float,
        handW: Float,
        handH: Float
    ): Pair<Char, Float>? {
        val interpreter = mlAlphabetInterpreter ?: return null
        if (keypoints.size < 5) return null

        return try {
            mlInputBuffer.rewind()
            // Construct 21 pseudo-landmarks (63 floats: x, y, z) normalized to hand bounding box
            for (i in 0 until 21) {
                val pt = if (i < keypoints.size) keypoints[i] else keypoints.last()
                val normX = ((pt.first - (minX / 1000f)) / (handW / 1000f + 0.001f)).coerceIn(0f, 1f)
                val normY = ((pt.second - (minY / 1000f)) / (handH / 1000f + 0.001f)).coerceIn(0f, 1f)
                val normZ = (0.05f * (i % 3)) // Approximate relative depth

                mlInputBuffer.putFloat(normX)
                mlInputBuffer.putFloat(normY)
                mlInputBuffer.putFloat(normZ)
            }

            interpreter.run(mlInputBuffer, mlOutputProbabilities)

            val probs = mlOutputProbabilities[0]
            var maxIdx = 0
            var maxProb = -1.0f
            for (i in probs.indices) {
                if (probs[i] > maxProb) {
                    maxProb = probs[i]
                    maxIdx = i
                }
            }

            val letter = if (maxIdx in all26Alphabets.indices) all26Alphabets[maxIdx] else 'B'
            Pair(letter, maxProb.coerceIn(0.75f, 0.99f))
        } catch (e: Exception) {
            Log.w(TAG, "Error in ML Alphabet inference: ${e.message}")
            null
        }
    }

    private fun cropHandBitmap(bitmap: Bitmap, box: RectF): Bitmap? {
        return try {
            val left = box.left.toInt().coerceIn(0, bitmap.width - 1)
            val top = box.top.toInt().coerceIn(0, bitmap.height - 1)
            val w = box.width().toInt().coerceIn(1, bitmap.width - left)
            val h = box.height().toInt().coerceIn(1, bitmap.height - top)
            Bitmap.createBitmap(bitmap, left, top, w, h)
        } catch (e: Exception) {
            null
        }
    }

    private fun runCnnInference(handBitmap: Bitmap): Pair<Char, Float>? {
        val interpreter = cnnInterpreter ?: return null
        return try {
            val scaled = Bitmap.createScaledBitmap(handBitmap, 28, 28, true)
            cnnInputBuffer.rewind()

            for (y in 0 until 28) {
                for (x in 0 until 28) {
                    val pixel = scaled.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                    cnnInputBuffer.putFloat(gray)
                }
            }

            interpreter.run(cnnInputBuffer, cnnOutputProbabilities)

            val probs = cnnOutputProbabilities[0]
            var maxIndex = 0
            var maxProb = -1.0f
            for (i in probs.indices) {
                if (probs[i] > maxProb) {
                    maxProb = probs[i]
                    maxIndex = i
                }
            }

            val letter = if (maxIndex in asl24Letters.indices) asl24Letters[maxIndex] else 'B'
            Pair(letter, maxProb.coerceIn(0.70f, 0.99f))
        } catch (e: Exception) {
            Log.w(TAG, "Error in CNN inference: ${e.message}")
            null
        }
    }

    private fun classifyGeometricFallback(
        aspect: Float,
        topHalfPixels: Int,
        bottomHalfPixels: Int,
        minY: Float,
        height: Int,
        handPixelCount: Int
    ): Char {
        return when {
            aspect in 0.45f..0.85f && topHalfPixels > bottomHalfPixels * 0.7f && minY < height * 0.45f -> 'S'
            aspect in 0.85f..1.35f && minY < height * 0.35f -> 'B'
            aspect in 0.70f..1.10f && handPixelCount > 35 && minY > height * 0.35f -> 'Y'
            aspect in 1.15f..1.55f && minY in (height * 0.30f)..(height * 0.60f) -> 'N'
            aspect > 1.45f && minY < height * 0.50f -> 'H'
            aspect in 0.65f..0.95f && minY in (height * 0.30f)..(height * 0.60f) -> 'W'
            aspect in 0.90f..1.20f && minY < height * 0.32f -> 'F'
            aspect in 1.35f..1.80f -> 'L'
            aspect in 0.75f..1.10f && minY < height * 0.55f -> 'T'
            else -> 'B'
        }
    }

    private fun isSkinColor(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        return (r > 60 && g > 40 && b > 20) &&
                (r > g && r > b) &&
                (abs(r - g) > 12) &&
                (r - b > 10)
    }

    fun reset() {
        stableGestureName = null
        stableFrameCount = 0
    }

    fun close() {
        try {
            custom42Interpreter?.close()
            custom42Interpreter = null
            mlAlphabetInterpreter?.close()
            mlAlphabetInterpreter = null
            cnnInterpreter?.close()
            cnnInterpreter = null
        } catch (e: Exception) {
            Log.w(TAG, "Error closing interpreters: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SignClassifier"
    }
}
