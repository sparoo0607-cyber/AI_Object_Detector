package com.accessibility.detector.detection

import android.content.Context
import androidx.camera.core.ImageProxy

interface ObjectDetectionListener {
    fun onObjectsDetected(
        results: List<DetectionResult>,
        inferenceTimeMs: Long,
        imageHeight: Int,
        imageWidth: Int
    )
    fun onObjectDetectionError(error: String)
}

/**
 * Perception Engine wrapping on-device object detection for SAHEY.
 */
class ObjectDetectionEngine(
    context: Context,
    private val listener: ObjectDetectionListener
) : ObjectDetectorHelper.DetectorListener {

    private val detectorHelper = ObjectDetectorHelper(
        context = context,
        threshold = 0.45f,
        maxResults = 5,
        numThreads = 2,
        detectorListener = this
    )

    fun processFrame(imageProxy: ImageProxy) {
        detectorHelper.detectObjects(imageProxy)
    }

    override fun onResults(
        results: List<DetectionResult>,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int
    ) {
        listener.onObjectsDetected(results, inferenceTime, imageHeight, imageWidth)
    }

    override fun onError(error: String) {
        listener.onObjectDetectionError(error)
    }

    fun shutdown() {
        detectorHelper.close()
    }
}
