package com.accessibility.detector.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.nio.ByteBuffer

/**
 * Data class representing a detected object with high level attributes.
 */
data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val score: Float
)

/**
 * Helper class to initialize and manage on-device TensorFlow Lite Object Detection.
 */
class ObjectDetectorHelper(
    private val context: Context,
    private val threshold: Float = 0.50f,
    private val maxResults: Int = 4,
    private val numThreads: Int = 2,
    private val detectorListener: DetectorListener
) {

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
            results: List<DetectionResult>,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        )
    }

    private var objectDetector: ObjectDetector? = null
    private var bitmapBuffer: Bitmap? = null

    init {
        setupObjectDetector()
    }

    fun setupObjectDetector() {
        val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(threshold)
            .setMaxResults(maxResults)

        val baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads)
        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        val modelName = "mobilenet_ssd.tflite"

        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                modelName,
                optionsBuilder.build()
            )
            Log.d(TAG, "ObjectDetector successfully loaded from $modelName")
        } catch (e: Exception) {
            val errMsg = "Failed to load model: ${e.localizedMessage}"
            Log.e(TAG, errMsg, e)
            detectorListener.onError(errMsg)
        }
    }

    /**
     * Detect objects in real-time camera image stream.
     */
    fun detectObjects(imageProxy: ImageProxy) {
        if (objectDetector == null) {
            imageProxy.close()
            return
        }

        val startTime = SystemClock.uptimeMillis()

        // Create bitmap buffer if not already created
        if (bitmapBuffer == null ||
            bitmapBuffer?.width != imageProxy.width ||
            bitmapBuffer?.height != imageProxy.height
        ) {
            bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        }

        // Copy imageProxy YUV to Bitmap
        val bitmap = imageProxy.toBitmap()

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-rotationDegrees / 90))
            .build()

        var tensorImage = TensorImage.fromBitmap(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        try {
            val results: List<Detection>? = objectDetector?.detect(tensorImage)
            val inferenceTime = SystemClock.uptimeMillis() - startTime

            val mappedResults = mutableListOf<DetectionResult>()
            results?.forEach { detection ->
                val primaryCategory = detection.categories.maxByOrNull { it.score }
                if (primaryCategory != null && primaryCategory.score >= threshold) {
                    // Clean up and capitalize label (e.g., "cell phone" -> "Cell Phone")
                    val formattedLabel = primaryCategory.label
                        .split(" ")
                        .joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }

                    mappedResults.add(
                        DetectionResult(
                            boundingBox = detection.boundingBox,
                            label = formattedLabel,
                            score = primaryCategory.score
                        )
                    )
                }
            }

            detectorListener.onResults(
                results = mappedResults,
                inferenceTime = inferenceTime,
                imageHeight = tensorImage.height,
                imageWidth = tensorImage.width
            )
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.localizedMessage}", e)
            detectorListener.onError("Inference error: ${e.localizedMessage}")
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        try {
            objectDetector?.close()
            objectDetector = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ObjectDetector", e)
        }
    }

    companion object {
        private const val TAG = "ObjectDetectorHelper"
    }
}
