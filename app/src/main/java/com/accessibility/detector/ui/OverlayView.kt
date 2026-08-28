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
import com.accessibility.detector.detection.DetectionResult
import com.accessibility.detector.detection.PerceptionType
import com.accessibility.detector.ocr.ExtractedTextBlock
import com.accessibility.detector.sign.SignDetection
import kotlin.math.max

/**
 * Multi-layer custom canvas overlay for rendering Objects (Green), Hazards (Red),
 * OCR Text (Yellow), and Sign Language (Cyan).
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
        color = Color.parseColor("#E6000000") // 90% opaque dark
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val badgeBorderPaint = Paint().apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

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

        // 1. Draw Object & Danger Bounding Boxes
        for (result in results) {
            val box = result.boundingBox
            val left = box.left * scaleFactor + offsetX
            val top = box.top * scaleFactor + offsetY
            val right = box.right * scaleFactor + offsetX
            val bottom = box.bottom * scaleFactor + offsetY

            val rect = RectF(
                maxOf(0f, left),
                maxOf(0f, top),
                minOf(width.toFloat(), right),
                minOf(height.toFloat(), bottom)
            )

            val isDanger = result.type == PerceptionType.DANGER ||
                    result.label.lowercase().contains("car") ||
                    result.label.lowercase().contains("truck") ||
                    result.label.lowercase().contains("bus") ||
                    result.label.lowercase().contains("motorcycle")

            val currentPaint = if (isDanger) dangerBoxPaint else objectBoxPaint
            val accentColor = if (isDanger) {
                ContextCompat.getColor(context, R.color.accent_red)
            } else {
                ContextCompat.getColor(context, R.color.accent_green)
            }

            canvas.drawRoundRect(rect, 14f, 14f, currentPaint)

            // Draw label pill
            val labelText = if (isDanger) "⚠️ ${result.label}" else "${result.label} ${(result.score * 100).toInt()}%"
            drawBadge(canvas, rect, labelText, accentColor)
        }

        // 2. Draw OCR Text Highlights
        for (ocrBlock in ocrBlocks) {
            val box = ocrBlock.boundingBox
            val left = box.left * scaleFactor + offsetX
            val top = box.top * scaleFactor + offsetY
            val right = box.right * scaleFactor + offsetX
            val bottom = box.bottom * scaleFactor + offsetY

            val rect = RectF(
                maxOf(0f, left),
                maxOf(0f, top),
                minOf(width.toFloat(), right),
                minOf(height.toFloat(), bottom)
            )

            canvas.drawRoundRect(rect, 8f, 8f, ocrBoxPaint)
            drawBadge(canvas, rect, "📖 ${ocrBlock.text}", ContextCompat.getColor(context, R.color.accent_yellow))
        }

        // 3. Draw Sign Language Gesture Highlights
        activeSign?.let { sign ->
            val box = sign.boundingBox
            val left = box.left * scaleFactor + offsetX
            val top = box.top * scaleFactor + offsetY
            val right = box.right * scaleFactor + offsetX
            val bottom = box.bottom * scaleFactor + offsetY

            val rect = RectF(
                maxOf(0f, left),
                maxOf(0f, top),
                minOf(width.toFloat(), right),
                minOf(height.toFloat(), bottom)
            )

            canvas.drawRoundRect(rect, 16f, 16f, signBoxPaint)
            drawBadge(canvas, rect, "🤟 Sign: ${sign.gestureName}", ContextCompat.getColor(context, R.color.accent_cyan))
        }
    }

    private fun drawBadge(canvas: Canvas, targetRect: RectF, text: String, borderColor: Int) {
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        val textWidth = textPaint.measureText(text)
        val textHeight = textBounds.height()

        val paddingH = 16f
        val paddingV = 12f

        val badgeTop = if (targetRect.top - textHeight - paddingV * 2 < 0) {
            targetRect.top + 8f
        } else {
            targetRect.top - textHeight - paddingV * 2 - 6f
        }
        val badgeBottom = badgeTop + textHeight + paddingV * 2
        val badgeLeft = targetRect.left
        val badgeRight = badgeLeft + textWidth + paddingH * 2

        val bgRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)
        badgeBorderPaint.color = borderColor

        canvas.drawRoundRect(bgRect, 12f, 12f, textBackgroundPaint)
        canvas.drawRoundRect(bgRect, 12f, 12f, badgeBorderPaint)

        canvas.drawText(text, badgeLeft + paddingH, badgeTop + paddingV + textHeight - 2f, textPaint)
    }
}
