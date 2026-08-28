package com.accessibility.detector.sign

import android.graphics.Bitmap
import com.accessibility.detector.detection.DetectionResult
import com.accessibility.detector.detection.EventPriority
import com.accessibility.detector.detection.PerceptionEvent
import com.accessibility.detector.detection.PerceptionType

interface SignLanguageListener {
    fun onSignDetected(event: PerceptionEvent, signDetection: SignDetection)
}

/**
 * Perception engine detecting and recognizing sign language gestures in real time.
 */
class SignLanguageEngine(
    private val listener: SignLanguageListener,
    private val signClassifier: SignClassifier = SignClassifier()
) {

    fun analyzeHandGestures(bitmap: Bitmap, detectionResults: List<DetectionResult>) {
        val detection = signClassifier.analyzeFrame(bitmap, detectionResults)
        if (detection != null) {
            val event = PerceptionEvent(
                type = PerceptionType.SIGN,
                label = "Sign: ${detection.gestureName}",
                spokenText = detection.spokenText,
                confidence = detection.confidence,
                priority = EventPriority.SIGN
            )
            listener.onSignDetected(event, detection)
        }
    }

    fun reset() {
        signClassifier.reset()
    }
}
