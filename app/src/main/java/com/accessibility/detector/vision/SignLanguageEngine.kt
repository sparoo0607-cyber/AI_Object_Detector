package com.accessibility.detector.vision

import android.content.Context
import android.graphics.Bitmap
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType

interface SignLanguageListener {
    fun onSignDetected(event: PerceptionEvent, signDetection: SignDetection)
}

/**
 * Sign Language Engine converting recognized ASL gestures into Text, Overlay Badges, and Spoken Voice.
 * Powered by the 24-class Sign Language CNN model.
 */
class SignLanguageEngine(
    context: Context? = null,
    private val listener: SignLanguageListener
) {
    val signClassifier: SignClassifier = SignClassifier(context)

    fun analyzeHandGestures(bitmap: Bitmap, detectionResults: List<DetectionResult>) {
        val detection = signClassifier.analyzeFrame(bitmap, detectionResults)
        if (detection != null) {
            val event = PerceptionEvent(
                type = PerceptionType.SIGN,
                label = detection.gestureName,
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

    fun close() {
        signClassifier.close()
    }
}
