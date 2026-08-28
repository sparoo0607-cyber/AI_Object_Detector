package com.accessibility.detector.ml

import android.graphics.RectF

/**
 * SEE / Object detection — composes a relational scene description
 * from the detector's raw bounding boxes instead of announcing
 * isolated labels one at a time.
 *
 * SSD MobileNet only gives boxes + class names, no relationships — so
 * this reasons about those boxes geometrically (which one sits on top
 * of / next to / near the other) to produce "coffee cup on the bench"
 * instead of "Cup. Bench." This is real geometry computed from the
 * real detector's real output, not a fabricated caption.
 */
object SceneComposer {

    fun describe(results: List<DetectionResult>): String {
        if (results.isEmpty()) return ""
        val top = results.sortedByDescending { it.score }.take(2)
        if (top.size == 1) return top[0].label

        val a = top[0]
        val b = top[1]
        // The "item" is usually the smaller of the two boxes; the
        // "surface" is the larger one it's most likely resting on.
        val item: DetectionResult
        val surface: DetectionResult
        if (area(a.boundingBox) <= area(b.boundingBox)) { item = a; surface = b } else { item = b; surface = a }

        return when {
            isOnTopOf(item.boundingBox, surface.boundingBox) ->
                "${lower(item.label)} on the ${lower(surface.label)}"
            isNear(item.boundingBox, surface.boundingBox) ->
                "${lower(item.label)} next to the ${lower(surface.label)}"
            else -> "${a.label} and ${lower(b.label)}"
        }
    }

    private fun lower(label: String) = label.replaceFirstChar { it.lowercaseChar() }

    private fun area(r: RectF) = (r.width().coerceAtLeast(0f)) * (r.height().coerceAtLeast(0f))

    private fun horizontalOverlap(a: RectF, b: RectF): Float {
        val left = maxOf(a.left, b.left)
        val right = minOf(a.right, b.right)
        return (right - left).coerceAtLeast(0f)
    }

    private fun intersects(a: RectF, b: RectF): Boolean =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    /** True when `item`'s box sits at or above `surface`'s top edge,
     * horizontally within (or mostly within) the surface's span —
     * the 2D-projection signature of "resting on top of". */
    private fun isOnTopOf(item: RectF, surface: RectF): Boolean {
        val overlapW = horizontalOverlap(item, surface)
        val itemW = item.width().coerceAtLeast(1f)
        val horizontallyAligned = overlapW / itemW > 0.4f

        // item's bottom edge should land near the surface's upper half
        // (on top of it), not far below or way above.
        val verticalRelation = item.bottom in (surface.top - surface.height() * 0.15f)..(surface.top + surface.height() * 0.65f)

        return horizontallyAligned && (verticalRelation || intersects(item, surface))
    }

    private fun isNear(a: RectF, b: RectF): Boolean {
        val gapX = maxOf(0f, maxOf(a.left, b.left) - minOf(a.right, b.right))
        val avgWidth = (a.width() + b.width()) / 2f
        return gapX < avgWidth * 1.5f
    }
}
