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
import com.accessibility.detector.core.DetectionResult
import com.accessibility.detector.core.PerceptionType
import com.accessibility.detector.vision.ExtractedTextBlock
import com.accessibility.detector.vision.SignDetection
import kotlin.math.max

/**
 * Multi-layer custom canvas overlay for Category 1: Vision Assist.
 * Renders Objects (Green), Hazards/Danger (Red), OCR Text (Yellow), and Sign Language (Cyan).
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: List<DetectionResult> = emptyList()
    private var ocrBlocks: List<ExtractedTextBlock> = emptyList()
    private var activeSign: SignDetection? = null

    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1f
    private val textBounds = Rect()

    // Paints
    private val objectBoxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent_green)
        strokeWidth = 7f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val dangerBoxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent_red)
        strokeWidth = 10f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val ocrBoxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent_yellow)
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val signBoxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.accent_cyan)
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.parseColor("#B3000000")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val badgeBorderPaint = Paint().apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    /** Max object badges drawn at once — keeps labels from stacking on top of each other. */
    private val maxObjectBadges = 4

    fun setPerceptionResults(
        detectedObjects: List<DetectionResult>,
        detectedOcr: List<ExtractedTextBlock>,
        detectedSign: SignDetection?,
        imgWidth: Int,
        imgHeight: Int
    ) {
        results = detectedObjects
        ocrBlocks = detectedOcr
        activeSign = detectedSign
        imageWidth = maxOf(1, imgWidth)
        imageHeight = maxOf(1, imgHeight)

        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }

    fun clear() {
        results = emptyList()
        ocrBlocks = emptyList()
        activeSign = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaledWidth = imageWidth * scaleFactor
        val scaledHeight = imageHeight * scaleFactor
        val offsetX = (width - scaledWidth) / 2f
        val offsetY = (height - scaledHeight) / 2f

        fun mapRect(box: RectF) = RectF(
            maxOf(0f, box.left * scaleFactor + offsetX),
            maxOf(0f, box.top * scaleFactor + offsetY),
            minOf(width.toFloat(), box.right * scaleFactor + offsetX),
            minOf(height.toFloat(), box.bottom * scaleFactor + offsetY)
        )

        // 1. OCR: box outlines only (no text badges — that is what used to overlap).
        for (ocrBlock in ocrBlocks) {
            canvas.drawRoundRect(mapRect(ocrBlock.boundingBox), 8f, 8f, ocrBoxPaint)
        }

        // 2. Objects / hazards: draw all boxes, but label only the few most confident.
        val ranked = results.sortedByDescending { it.score }
        ranked.forEachIndexed { index, result ->
            val rect = mapRect(result.boundingBox)
            val isDanger = result.type == PerceptionType.DANGER ||
                result.label.lowercase().let {
                    it.contains("car") || it.contains("truck") || it.contains("bus") || it.contains("motorcycle")
                }
            canvas.drawRoundRect(rect, 14f, 14f, if (isDanger) dangerBoxPaint else objectBoxPaint)

            if (isDanger || index < maxObjectBadges) {
                val accent = ContextCompat.getColor(
                    context, if (isDanger) R.color.accent_red else R.color.accent_green
                )
                val label = if (isDanger) "⚠ ${result.label}" else result.label
                drawBadge(canvas, rect, label, accent)
            }
        }

        // 3. Active sign: one clean badge.
        activeSign?.let { sign ->
            val rect = mapRect(sign.boundingBox)
            canvas.drawRoundRect(rect, 16f, 16f, signBoxPaint)
            drawBadge(canvas, rect, sign.gestureName, ContextCompat.getColor(context, R.color.accent_cyan))
        }
    }

    private fun drawBadge(canvas: Canvas, targetRect: RectF, rawText: String, borderColor: Int) {
        val text = if (rawText.length > 22) rawText.take(21) + "…" else rawText
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textWidth = textPaint.measureText(text)
        val textHeight = textBounds.height().toFloat()

        val padH = 12f
        val padV = 8f
        val badgeH = textHeight + padV * 2

        val badgeTop = if (targetRect.top - badgeH - 4f < 0f) targetRect.top + 6f
        else targetRect.top - badgeH - 4f
        val badgeLeft = targetRect.left
        val bgRect = RectF(badgeLeft, badgeTop, badgeLeft + textWidth + padH * 2, badgeTop + badgeH)

        badgeBorderPaint.color = borderColor
        canvas.drawRoundRect(bgRect, 10f, 10f, textBackgroundPaint)
        canvas.drawRoundRect(bgRect, 10f, 10f, badgeBorderPaint)
        canvas.drawText(text, badgeLeft + padH, badgeTop + padV + textHeight - 2f, textPaint)
    }
}
