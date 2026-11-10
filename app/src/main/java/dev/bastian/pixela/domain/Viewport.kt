package dev.bastian.pixela.domain

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A canvas pixel coordinate; may lie outside the canvas when the finger is outside it. */
data class PixelPos(val x: Int, val y: Int)

/**
 * Where the canvas sits on screen: [scale] screen pixels per canvas pixel and the screen position
 * of the canvas top-left corner. All the math is pure so it can be tested without a device.
 */
data class Viewport(val scale: Float, val offsetX: Float, val offsetY: Float) {

    /** The canvas pixel under a screen point. */
    fun screenToPixel(screenX: Float, screenY: Float): PixelPos =
        PixelPos(floor((screenX - offsetX) / scale).toInt(), floor((screenY - offsetY) / scale).toInt())

    /** The screen position of the top-left corner of a canvas pixel. */
    fun pixelToScreenX(x: Int): Float = offsetX + x * scale

    fun pixelToScreenY(y: Int): Float = offsetY + y * scale

    /** Zooms by [factor] keeping the canvas point under ([focusX], [focusY]) fixed. */
    fun zoomed(factor: Float, focusX: Float, focusY: Float, minScale: Float = MIN_SCALE, maxScale: Float = MAX_SCALE): Viewport {
        val newScale = (scale * factor).coerceIn(minScale, maxScale)
        val ratio = newScale / scale
        return Viewport(newScale, focusX - (focusX - offsetX) * ratio, focusY - (focusY - offsetY) * ratio)
    }

    fun panned(dx: Float, dy: Float): Viewport = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)

    /** Keeps at least [keep] screen pixels of the canvas inside the view, so it cannot be lost off screen. */
    fun clamped(viewWidth: Float, viewHeight: Float, canvasWidth: Int, canvasHeight: Int, keep: Float = 48f): Viewport {
        val w = canvasWidth * scale
        val h = canvasHeight * scale
        val minX = keep - w
        val maxX = viewWidth - keep
        val minY = keep - h
        val maxY = viewHeight - keep
        return copy(
            offsetX = offsetX.coerceIn(min(minX, maxX), max(minX, maxX)),
            offsetY = offsetY.coerceIn(min(minY, maxY), max(minY, maxY)),
        )
    }

    companion object {
        const val MIN_SCALE = 2f
        const val MAX_SCALE = 96f

        /** The largest viewport that shows the whole canvas, centered, with a margin around it. */
        fun fit(viewWidth: Float, viewHeight: Float, canvasWidth: Int, canvasHeight: Int, margin: Float = 16f): Viewport {
            val scale = max(
                MIN_SCALE,
                min((viewWidth - 2 * margin) / canvasWidth, (viewHeight - 2 * margin) / canvasHeight),
            )
            return Viewport(scale, (viewWidth - canvasWidth * scale) / 2f, (viewHeight - canvasHeight * scale) / 2f)
        }
    }
}
