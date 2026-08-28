package com.accessibility.detector.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.accessibility.detector.R
import com.accessibility.detector.ml.DetectionResult
import kotlin.math.max

/**
 * Custom View to draw high-contrast bounding boxes and label badges over camera preview.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: List<DetectionResult> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f
    private var bounds = Rect()

    private val boxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent_green)
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val cornerPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent_cyan)
        strokeWidth = 12f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.parseColor("#E6000000") // 90% opaque dark background
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textBorderPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent_green)
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 44f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val scorePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent_cyan)
        textSize = 38f
        isAntiAlias = true
        isFakeBoldText = true
    }

    fun setResults(
        detectionResults: List<DetectionResult>,
        imgHeight: Int,
        imgWidth: Int
    ) {
        results = detectionResults
        imageHeight = imgHeight
        imageWidth = imgWidth

        // Scale factor maps image resolution to screen view coordinates
        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }

    fun clear() {
        results = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (results.isEmpty()) return

        // Compute offsets to center the image crop
        val scaledWidth = imageWidth * scaleFactor
        val scaledHeight = imageHeight * scaleFactor
        val offsetX = (width - scaledWidth) / 2f
        val offsetY = (height - scaledHeight) / 2f

        for (result in results) {
            val boundingBox = result.boundingBox

            val left = boundingBox.left * scaleFactor + offsetX
            val top = boundingBox.top * scaleFactor + offsetY
            val right = boundingBox.right * scaleFactor + offsetX
            val bottom = boundingBox.bottom * scaleFactor + offsetY

            // Clamp coordinates to view dimensions
            val clampedRect = RectF(
                max(0f, left),
                max(0f, top),
                minOf(width.toFloat(), right),
                minOf(height.toFloat(), bottom)
            )

            // Draw bounding box rectangle
            canvas.drawRoundRect(clampedRect, 16f, 16f, boxPaint)

            // Draw stylish corner highlights
            val cornerLen = minOf((clampedRect.width() * 0.2f), (clampedRect.height() * 0.2f), 40f)
            // Top-left
            canvas.drawLine(clampedRect.left, clampedRect.top, clampedRect.left + cornerLen, clampedRect.top, cornerPaint)
            canvas.drawLine(clampedRect.left, clampedRect.top, clampedRect.left, clampedRect.top + cornerLen, cornerPaint)
            // Top-right
            canvas.drawLine(clampedRect.right, clampedRect.top, clampedRect.right - cornerLen, clampedRect.top, cornerPaint)
            canvas.drawLine(clampedRect.right, clampedRect.top, clampedRect.right, clampedRect.top + cornerLen, cornerPaint)
            // Bottom-left
            canvas.drawLine(clampedRect.left, clampedRect.bottom, clampedRect.left + cornerLen, clampedRect.bottom, cornerPaint)
            canvas.drawLine(clampedRect.left, clampedRect.bottom, clampedRect.left, clampedRect.bottom - cornerLen, cornerPaint)
            // Bottom-right
            canvas.drawLine(clampedRect.right, clampedRect.bottom, clampedRect.right - cornerLen, clampedRect.bottom, cornerPaint)
            canvas.drawLine(clampedRect.right, clampedRect.bottom, clampedRect.right, clampedRect.bottom - cornerLen, cornerPaint)

            // Prepare label text
            val labelText = result.label
            val confidencePct = (result.score * 100).toInt()
            val scoreText = " $confidencePct%"

            textPaint.getTextBounds(labelText, 0, labelText.length, bounds)
            val labelWidth = textPaint.measureText(labelText)
            val scoreWidth = scorePaint.measureText(scoreText)
            val totalTextWidth = labelWidth + scoreWidth
            val textHeight = bounds.height()

            val paddingH = 20f
            val paddingV = 16f

            // Position badge above bounding box (or inside if too close to top)
            val badgeTop = if (clampedRect.top - textHeight - paddingV * 2 < 0) {
                clampedRect.top + 10f
            } else {
                clampedRect.top - textHeight - paddingV * 2 - 8f
            }
            val badgeBottom = badgeTop + textHeight + paddingV * 2
            val badgeLeft = clampedRect.left
            val badgeRight = badgeLeft + totalTextWidth + paddingH * 2

            val textBackgroundRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
            canvas.drawRoundRect(textBackgroundRect, 14f, 14f, textBackgroundPaint)
            canvas.drawRoundRect(textBackgroundRect, 14f, 14f, textBorderPaint)

            // Draw label & score text
            val textY = badgeTop + paddingV + textHeight - 4f
            canvas.drawText(labelText, badgeLeft + paddingH, textY, textPaint)
            canvas.drawText(scoreText, badgeLeft + paddingH + labelWidth, textY, scorePaint)
        }
    }
}
