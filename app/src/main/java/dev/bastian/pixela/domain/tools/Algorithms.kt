package dev.bastian.pixela.domain.tools

import dev.bastian.pixela.domain.PixelBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Calls [plot] for every pixel of the Bresenham line between the two points, both ends included. */
inline fun bresenhamLine(x0: Int, y0: Int, x1: Int, y1: Int, plot: (Int, Int) -> Unit) {
    val dx = abs(x1 - x0)
    val dy = -abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx + dy
    var x = x0
    var y = y0
    while (true) {
        plot(x, y)
        if (x == x1 && y == y1) break
        val e2 = 2 * err
        if (e2 >= dy) {
            err += dy
            x += sx
        }
        if (e2 <= dx) {
            err += dx
            y += sy
        }
    }
}

/** Calls [plot] for the pixels of the rectangle spanned by two corners, as an outline or filled. */
inline fun rectangle(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean, plot: (Int, Int) -> Unit) {
    val left = min(x0, x1)
    val right = max(x0, x1)
    val top = min(y0, y1)
    val bottom = max(y0, y1)
    for (y in top..bottom) {
        for (x in left..right) {
            if (filled || x == left || x == right || y == top || y == bottom) plot(x, y)
        }
    }
}

/**
 * Indices of the 4-connected region of equal color that contains the given pixel. Iterative (explicit
 * stack), so large regions cannot overflow the call stack.
 */
fun floodRegion(buffer: PixelBuffer, x: Int, y: Int): IntArray {
    if (!buffer.inBounds(x, y)) return IntArray(0)
    val width = buffer.width
    val target = buffer[x, y]
    val visited = BooleanArray(buffer.pixels.size)
    val stack = IntArray(buffer.pixels.size)
    var top = 0
    val result = IntArray(buffer.pixels.size)
    var count = 0
    val start = buffer.index(x, y)
    stack[top++] = start
    visited[start] = true
    while (top > 0) {
        val i = stack[--top]
        result[count++] = i
        val cx = i % width
        val cy = i / width
        if (cx > 0) top = push(buffer, visited, stack, top, i - 1, target)
        if (cx < width - 1) top = push(buffer, visited, stack, top, i + 1, target)
        if (cy > 0) top = push(buffer, visited, stack, top, i - width, target)
        if (cy < buffer.height - 1) top = push(buffer, visited, stack, top, i + width, target)
    }
    return result.copyOf(count)
}

private fun push(buffer: PixelBuffer, visited: BooleanArray, stack: IntArray, top: Int, index: Int, target: Int): Int {
    if (visited[index] || buffer.pixels[index] != target) return top
    visited[index] = true
    stack[top] = index
    return top + 1
}

/** A copy of [source] with its content moved by (dx, dy); pixels pushed outside are lost. */
fun shifted(source: PixelBuffer, dx: Int, dy: Int): PixelBuffer {
    val out = PixelBuffer(source.width, source.height)
    for (y in 0 until source.height) {
        val ny = y + dy
        if (ny !in 0 until source.height) continue
        for (x in 0 until source.width) {
            val nx = x + dx
            if (nx in 0 until source.width) out[nx, ny] = source[x, y]
        }
    }
    return out
}
