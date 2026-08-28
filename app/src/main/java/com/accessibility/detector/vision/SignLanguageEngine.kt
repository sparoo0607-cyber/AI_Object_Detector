package com.accessibility.detector.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.EventPriority
import com.accessibility.detector.core.PerceptionEvent
import com.accessibility.detector.core.PerceptionType

interface SignLanguageListener {
    fun onSignDetected(event: PerceptionEvent, signDetection: SignDetection)
}

/**
 * Sign Language Engine for Category 1: Vision Assist.
 *
 * Real pipeline:  camera frame -> MediaPipe [HandLandmarkerHelper] -> 21 landmarks
 *                 -> [SignClassifier] (landmark TFLite model) -> spoken sign.
 *
 * The feature reports itself unavailable (rather than guessing) when either the MediaPipe
 * `hand_landmarker.task` asset or the sign TFLite model is missing.
 */
class SignLanguageEngine(
    context: Context?,
    private val listener: SignLanguageListener
) {
    val signClassifier: SignClassifier = SignClassifier(context)
    private val handLandmarker: HandLandmarkerHelper? = context?.let { HandLandmarkerHelper(it) }

    /** True only when both landmark detection and a sign model are usable. */
    val isAvailable: Boolean
        get() = (handLandmarker?.isReady == true) && signClassifier.isAvailable

    private var warnedUnavailable = false

    /**
     * @param detectionResults kept for source compatibility; no longer used for ROI
     *                          (MediaPipe locates the hand directly).
     * @param rotationDegrees  CameraX `imageInfo.rotationDegrees` for the frame.
     */
    fun analyzeHandGestures(
        bitmap: Bitmap,
        detectionResults: List<DetectionResult> = emptyList(),
        rotationDegrees: Int = 0
    ) {
        val landmarker = handLandmarker
        if (landmarker == null || !landmarker.isReady || !signClassifier.isAvailable) {
            if (!warnedUnavailable) {
                Log.w(
                    TAG,
                    "Sign language disabled: missing ${HandLandmarkerHelper.MODEL_ASSET} " +
                        "or sign_language_model.tflite. See app/src/main/assets/README.md."
                )
                warnedUnavailable = true
            }
            return
        }

        val hand = landmarker.detect(bitmap, rotationDegrees) ?: return
        val detection = signClassifier.classify(hand) ?: return

        val event = PerceptionEvent(
            type = PerceptionType.SIGN,
            label = detection.gestureName,
            spokenText = detection.spokenText,
            confidence = detection.confidence,
            priority = EventPriority.SIGN
        )
        listener.onSignDetected(event, detection)
    }

    fun reset() {
        signClassifier.reset()
    }

    fun close() {
        signClassifier.close()
        handLandmarker?.close()
    }

    companion object {
        private const val TAG = "SignLanguageEngine"
    }
}
