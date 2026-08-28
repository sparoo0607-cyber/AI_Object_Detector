package com.accessibility.detector.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Real hand-landmark extraction using MediaPipe Tasks Vision.
 *
 * This replaces the previous skin-colour pixel-blob heuristic. It produces the exact
 * 21-point normalised hand landmark array that the Python `create_dataset.py` MediaPipe
 * pipeline used to train `sign_language_model.tflite`, so [SignClassifier] can run that
 * model on a matching input distribution.
 *
 * Requires the model asset:  app/src/main/assets/hand_landmarker.task
 *   Download (float16, ~7.5 MB):
 *   https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
 *
 * If the asset is missing the helper degrades gracefully: [isReady] is false and
 * [detect] returns null, so the sign feature is simply unavailable rather than fabricated.
 */
class HandLandmarkerHelper(context: Context) {

    data class HandLandmarks(
        /** 21 (x, y) points, each normalised to [0,1] in the upright image. */
        val points: List<Pair<Float, Float>>,
        /** 21 z values (relative depth, wrist-relative). */
        val depths: List<Float>,
        /** Tight bounding box of the hand in normalised (upright) image coordinates. */
        val normalizedBox: RectF,
        /** Pixel dimensions of the upright image the landmarks refer to. */
        val imageWidth: Int,
        val imageHeight: Int
    )

    private var landmarker: HandLandmarker? = null
    val isReady: Boolean get() = landmarker != null

    init {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()
            landmarker = HandLandmarker.createFromOptions(context, options)
            Log.d(TAG, "MediaPipe HandLandmarker initialised from $MODEL_ASSET")
        } catch (e: Throwable) {
            Log.w(
                TAG,
                "HandLandmarker unavailable (${e.message}). " +
                    "Place $MODEL_ASSET in app/src/main/assets/ to enable sign language."
            )
            landmarker = null
        }
    }

    /**
     * Runs hand-landmark detection on a camera bitmap.
     *
     * @param rotationDegrees CameraX `imageInfo.rotationDegrees` (0/90/180/270). The frame is
     *                        rotated upright before detection so landmark normalisation matches
     *                        the (upright) training distribution of the sign model.
     * Returns null when no hand is confidently present or the model is unavailable.
     */
    fun detect(bitmap: Bitmap, rotationDegrees: Int = 0): HandLandmarks? {
        val lm = landmarker ?: return null
        return try {
            val upright = rotateUpright(bitmap, rotationDegrees)
            val mpImage = BitmapImageBuilder(upright).build()
            val result: HandLandmarkerResult = lm.detect(mpImage)

            val hands = result.landmarks()
            if (hands.isEmpty() || hands[0].isEmpty()) return null

            val pts = hands[0].map { it.x() to it.y() }
            val depths = hands[0].map { it.z() }

            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE; var maxY = Float.MIN_VALUE
            for ((x, y) in pts) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }

            HandLandmarks(
                points = pts,
                depths = depths,
                normalizedBox = RectF(minX, minY, maxX, maxY),
                imageWidth = upright.width,
                imageHeight = upright.height
            )
        } catch (e: Throwable) {
            Log.w(TAG, "HandLandmarker.detect failed: ${e.message}")
            null
        }
    }

    private fun rotateUpright(src: Bitmap, rotationDegrees: Int): Bitmap {
        val r = ((rotationDegrees % 360) + 360) % 360
        if (r == 0) return src
        return try {
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(r.toFloat()) }, true)
        } catch (e: Throwable) {
            Log.w(TAG, "Bitmap rotation failed ($r°): ${e.message}")
            src
        }
    }

    fun close() {
        try {
            landmarker?.close()
        } catch (_: Throwable) {
        } finally {
            landmarker = null
        }
    }

    companion object {
        private const val TAG = "HandLandmarkerHelper"
        const val MODEL_ASSET = "hand_landmarker.task"
    }
}
