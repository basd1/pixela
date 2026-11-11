package dev.bastian.pixela.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Export scales offered in the UI. */
val ExportScales = listOf(1, 4, 8, 16)

/** Nearest-neighbor upscale: every pixel becomes a [factor] x [factor] block. */
fun scaleNearest(pixels: IntArray, width: Int, height: Int, factor: Int): IntArray {
    require(factor >= 1) { "Scale must be at least 1" }
    require(pixels.size == width * height) { "Pixel array does not match the size" }
    if (factor == 1) return pixels.copyOf()
    val outWidth = width * factor
    val out = IntArray(outWidth * height * factor)
    for (y in 0 until height * factor) {
        val srcRow = (y / factor) * width
        val dstRow = y * outWidth
        for (x in 0 until outWidth) out[dstRow + x] = pixels[srcRow + x / factor]
    }
    return out
}

/** Size and position of an image scaled down to fit inside a canvas, keeping its aspect ratio and centered. */
data class FitRect(val x: Int, val y: Int, val width: Int, val height: Int)

fun fitInside(sourceWidth: Int, sourceHeight: Int, canvasWidth: Int, canvasHeight: Int): FitRect {
    require(sourceWidth > 0 && sourceHeight > 0) { "Source must not be empty" }
    val ratio = min(canvasWidth.toFloat() / sourceWidth, canvasHeight.toFloat() / sourceHeight)
    val w = max(1, (sourceWidth * ratio).roundToInt()).coerceAtMost(canvasWidth)
    val h = max(1, (sourceHeight * ratio).roundToInt()).coerceAtMost(canvasHeight)
    return FitRect((canvasWidth - w) / 2, (canvasHeight - h) / 2, w, h)
}

/** Places [scaled] (a [width] x [height] image) into a transparent canvas at the position of [rect]. */
fun placeOnCanvas(scaled: IntArray, rect: FitRect, canvasWidth: Int, canvasHeight: Int): IntArray {
    require(scaled.size == rect.width * rect.height) { "Scaled image does not match the rectangle" }
    val out = IntArray(canvasWidth * canvasHeight)
    for (y in 0 until rect.height) {
        for (x in 0 until rect.width) out[(rect.y + y) * canvasWidth + rect.x + x] = scaled[y * rect.width + x]
    }
    return out
}
