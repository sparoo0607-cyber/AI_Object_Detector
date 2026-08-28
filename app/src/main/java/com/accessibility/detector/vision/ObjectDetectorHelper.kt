package com.accessibility.detector.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.PerceptionType
import com.accessibility.detector.core.ProximityLevel
import com.accessibility.detector.core.SpatialPosition
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * TensorFlow Lite SSD MobileNet on-device object detection helper for Category 1: Vision Assist.
 */
class ObjectDetectorHelper(
    private val context: Context,
    private val threshold: Float = 0.45f,
    private val maxResults: Int = 5,
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

    fun detectObjects(imageProxy: ImageProxy) {
        if (objectDetector == null) {
            imageProxy.close()
            return
        }

        val startTime = SystemClock.uptimeMillis()

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

            val imgW = tensorImage.width
            val imgH = tensorImage.height

            val mappedResults = mutableListOf<DetectionResult>()
            results?.forEach { detection ->
                val primaryCategory = detection.categories.maxByOrNull { it.score }
                if (primaryCategory != null && primaryCategory.score >= threshold) {
                    val formattedLabel = primaryCategory.label
                        .split(" ")
                        .joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        }

                    val box = detection.boundingBox
                    val spatialPos = calculateSpatialPosition(box, imgW)
                    val proximity = calculateProximity(box, imgW, imgH)

                    mappedResults.add(
                        DetectionResult(
                            boundingBox = box,
                            label = formattedLabel,
                            score = primaryCategory.score,
                            type = PerceptionType.OBJECT,
                            spatialPosition = spatialPos,
                            proximity = proximity
                        )
                    )
                }
            }

            detectorListener.onResults(
                results = mappedResults,
                inferenceTime = inferenceTime,
                imageHeight = imgH,
                imageWidth = imgW
            )
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.localizedMessage}", e)
            detectorListener.onError("Inference error: ${e.localizedMessage}")
        } finally {
            imageProxy.close()
        }
    }

    private fun calculateSpatialPosition(box: RectF, imageWidth: Int): SpatialPosition {
        val centerX = box.centerX()
        val thirdWidth = imageWidth / 3f
        return when {
            centerX < thirdWidth -> SpatialPosition.LEFT
            centerX > thirdWidth * 2 -> SpatialPosition.RIGHT
            else -> SpatialPosition.CENTER
        }
    }

    private fun calculateProximity(box: RectF, imageWidth: Int, imageHeight: Int): ProximityLevel {
        val areaRatio = (box.width() * box.height()) / (imageWidth.toFloat() * imageHeight.toFloat())
        return when {
            areaRatio > 0.35f -> ProximityLevel.VERY_CLOSE
            areaRatio > 0.15f -> ProximityLevel.NEARBY
            areaRatio > 0.04f -> ProximityLevel.AHEAD
            else -> ProximityLevel.DISTANT
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
