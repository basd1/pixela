package dev.bastian.pixela.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageOpsTest {
    @Test
    fun `scale one is a copy`() {
        val src = intArrayOf(1, 2, 3, 4)
        val out = scaleNearest(src, 2, 2, 1)
        assertTrue(src.contentEquals(out))
        out[0] = 9
        assertEquals(1, src[0])
    }

    @Test
    fun `scale replicates every pixel into a block with the expected dimensions`() {
        val src = intArrayOf(1, 2, 3, 4)
        val out = scaleNearest(src, 2, 2, 3)
        assertEquals(6 * 6, out.size)
        // Row 0 of the output: three 1s then three 2s.
        assertEquals(listOf(1, 1, 1, 2, 2, 2), out.take(6))
        assertEquals(listOf(3, 3, 3, 4, 4, 4), out.drop(18).take(6))
    }

    @Test
    fun `export scales produce the advertised sizes for every canvas size`() {
        for (size in listOf(16, 32, 64)) {
            for (scale in ExportScales) {
                assertEquals(size * scale * size * scale, scaleNearest(IntArray(size * size), size, size, scale).size)
            }
        }
    }

    @Test
    fun `invalid input is rejected`() {
        assertFailsWith<IllegalArgumentException> { scaleNearest(IntArray(4), 2, 2, 0) }
        assertFailsWith<IllegalArgumentException> { scaleNearest(IntArray(5), 2, 2, 2) }
    }

    @Test
    fun `fit keeps the aspect ratio and centers the image`() {
        assertEquals(FitRect(0, 0, 32, 32), fitInside(1000, 1000, 32, 32))
        assertEquals(FitRect(0, 8, 32, 16), fitInside(200, 100, 32, 32))
        assertEquals(FitRect(8, 0, 16, 32), fitInside(100, 200, 32, 32))
    }

    @Test
    fun `fit never collapses to nothing and never exceeds the canvas`() {
        val tiny = fitInside(1000, 1, 16, 16)
        assertEquals(1, tiny.height)
        assertEquals(16, tiny.width)
        val small = fitInside(4, 4, 64, 64)
        assertEquals(64, small.width)
    }

    @Test
    fun `placing puts the image at the rectangle and leaves the rest transparent`() {
        val rect = FitRect(1, 2, 2, 1)
        val out = placeOnCanvas(intArrayOf(7, 8), rect, 4, 4)
        assertEquals(7, out[2 * 4 + 1])
        assertEquals(8, out[2 * 4 + 2])
        assertEquals(2, out.count { it != 0 })
    }
}
