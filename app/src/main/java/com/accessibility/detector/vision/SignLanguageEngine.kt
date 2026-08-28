package com.accessibility.detector.vision

import android.graphics.Bitmap
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType

interface SignLanguageListener {
    fun onSignDetected(event: PerceptionEvent, signDetection: SignDetection)
}

/**
 * Sign Language Engine converting recognized gestures into Text and Voice.
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
