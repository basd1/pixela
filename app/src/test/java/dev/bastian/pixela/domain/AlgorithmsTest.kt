package dev.bastian.pixela.domain

import dev.bastian.pixela.domain.tools.StrokeRecorder
import dev.bastian.pixela.domain.tools.bresenhamLine
import dev.bastian.pixela.domain.tools.floodRegion
import dev.bastian.pixela.domain.tools.rectangle
import dev.bastian.pixela.domain.tools.shifted
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlgorithmsTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun line(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
        val points = mutableListOf<Pair<Int, Int>>()
        bresenhamLine(x0, y0, x1, y1) { x, y -> points += x to y }
        return points
    }

    @Test
    fun `line covers the eight octants with connected pixels and exact endpoints`() {
        val cx = 10
        val cy = 10
        val targets = listOf(
            20 to 13, 13 to 20, 7 to 20, 0 to 13, 0 to 7, 7 to 0, 13 to 0, 20 to 7, // eight octants
            20 to 10, 10 to 20, 0 to 10, 10 to 0, 20 to 20, 0 to 0, 20 to 0, 0 to 20, // axes and diagonals
        )
        for ((tx, ty) in targets) {
            val points = line(cx, cy, tx, ty)
            assertEquals(cx to cy, points.first(), "start for $tx,$ty")
            assertEquals(tx to ty, points.last(), "end for $tx,$ty")
            assertEquals(max(abs(tx - cx), abs(ty - cy)) + 1, points.size, "length for $tx,$ty")
            points.zipWithNext().forEach { (a, b) ->
                assertTrue(abs(a.first - b.first) <= 1 && abs(a.second - b.second) <= 1, "gap in line to $tx,$ty")
            }
        }
    }

    @Test
    fun `line of a single point plots it once`() {
        assertEquals(listOf(3 to 4), line(3, 4, 3, 4))
    }

    @Test
    fun `reversed line paints the same pixels`() {
        val forward = line(1, 2, 9, 5).toSet()
        val backward = line(9, 5, 1, 2).toSet()
        assertEquals(forward.size, backward.size)
    }

    @Test
    fun `rectangle outline leaves the inside empty`() {
        val points = mutableSetOf<Pair<Int, Int>>()
        rectangle(2, 2, 5, 4, filled = false) { x, y -> points += x to y }
        assertEquals(10, points.size) // 4x3 border = 12 - 2 inner
        assertTrue((3 to 3) !in points)
        assertTrue((4 to 3) !in points)
        assertTrue((2 to 2) in points && (5 to 4) in points)
    }

    @Test
    fun `filled rectangle works from any corner order`() {
        val points = mutableSetOf<Pair<Int, Int>>()
        rectangle(5, 4, 2, 2, filled = true) { x, y -> points += x to y }
        assertEquals(12, points.size)
    }

    @Test
    fun `flood fill stays inside a closed area`() {
        val buffer = PixelBuffer(6, 6)
        // Closed 3x3 box outline in red with transparent inside at (2..4, 2..4) border at 1..5? use 1..4 ring.
        rectangle(1, 1, 4, 4, filled = false) { x, y -> buffer[x, y] = red }
        val inside = floodRegion(buffer, 2, 2)
        assertEquals(setOf(buffer.index(2, 2), buffer.index(3, 2), buffer.index(2, 3), buffer.index(3, 3)), inside.toSet())
        val outside = floodRegion(buffer, 0, 0)
        assertEquals(36 - 12 - 4, outside.size)
    }

    @Test
    fun `flood fill of an empty canvas selects every pixel`() {
        val buffer = PixelBuffer(64, 64)
        assertEquals(64 * 64, floodRegion(buffer, 10, 10).size)
    }

    @Test
    fun `flood fill with the same color changes nothing`() {
        val buffer = PixelBuffer(4, 4)
        buffer.fill(red)
        val recorder = StrokeRecorder(1, buffer)
        for (i in floodRegion(buffer, 1, 1)) recorder.setIndex(i, red)
        assertEquals(null, recorder.finish())
    }

    @Test
    fun `flood fill outside the canvas is empty`() {
        assertEquals(0, floodRegion(PixelBuffer(4, 4), 4, 0).size)
    }

    @Test
    fun `flood fill does not leak diagonally`() {
        val buffer = PixelBuffer(3, 3)
        buffer[1, 0] = blue
        buffer[0, 1] = blue
        val region = floodRegion(buffer, 0, 0)
        assertEquals(listOf(buffer.index(0, 0)), region.toList())
    }

    @Test
    fun `stroke recorder reset restores the pixels`() {
        val buffer = PixelBuffer(4, 4)
        val recorder = StrokeRecorder(1, buffer)
        recorder.set(1, 1, red)
        recorder.set(9, 9, red) // ignored
        recorder.reset()
        assertEquals(TRANSPARENT, buffer[1, 1])
        assertEquals(null, recorder.finish())
    }

    @Test
    fun `stroke recorder keeps the first value when a pixel is painted twice`() {
        val buffer = PixelBuffer(4, 4)
        buffer[0, 0] = blue
        val recorder = StrokeRecorder(1, buffer)
        recorder.set(0, 0, red)
        recorder.set(0, 0, TRANSPARENT)
        recorder.set(1, 0, red)
        val change = recorder.finish()!!
        assertEquals(listOf(buffer.index(0, 0), buffer.index(1, 0)), change.indices.toList().sorted())
        assertEquals(setOf(TRANSPARENT, red), change.after.toSet())
        assertEquals(setOf(blue, TRANSPARENT), change.before.toSet())
    }

    @Test
    fun `shifted moves content and drops what leaves the canvas`() {
        val buffer = PixelBuffer(3, 3)
        buffer[0, 0] = red
        buffer[2, 2] = blue
        val moved = shifted(buffer, 1, 0)
        assertEquals(red, moved[1, 0])
        assertEquals(TRANSPARENT, moved[0, 0])
        assertEquals(TRANSPARENT, moved[2, 2]) // blue went off the right edge
        assertEquals(1, moved.pixels.count { it != TRANSPARENT })
    }
}

