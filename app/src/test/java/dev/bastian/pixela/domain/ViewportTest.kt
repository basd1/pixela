package dev.bastian.pixela.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewportTest {
    @Test
    fun `identity zoom without offset maps screen to pixels directly`() {
        val vp = Viewport(1f, 0f, 0f)
        assertEquals(PixelPos(5, 7), vp.screenToPixel(5.5f, 7.9f))
    }

    @Test
    fun `scale divides the screen coordinates`() {
        val vp = Viewport(10f, 0f, 0f)
        assertEquals(PixelPos(0, 0), vp.screenToPixel(9.99f, 0f))
        assertEquals(PixelPos(1, 0), vp.screenToPixel(10f, 0f))
        assertEquals(PixelPos(3, 4), vp.screenToPixel(35f, 45f))
    }

    @Test
    fun `offset moves the canvas origin`() {
        val vp = Viewport(8f, 100f, 50f)
        assertEquals(PixelPos(0, 0), vp.screenToPixel(100f, 50f))
        assertEquals(PixelPos(2, 1), vp.screenToPixel(116f, 58f))
    }

    @Test
    fun `points left of or above the canvas map to negative pixels`() {
        val vp = Viewport(8f, 100f, 50f)
        assertEquals(PixelPos(-1, -1), vp.screenToPixel(99f, 49f))
        assertEquals(PixelPos(-13, 0), vp.screenToPixel(0f, 50f))
    }

    @Test
    fun `pixel to screen is the inverse of screen to pixel`() {
        val vp = Viewport(12.5f, -30f, 77f)
        for (x in listOf(0, 3, 15, 63)) {
            for (y in listOf(0, 9, 31)) {
                val center = vp.screenToPixel(vp.pixelToScreenX(x) + 1f, vp.pixelToScreenY(y) + 1f)
                assertEquals(PixelPos(x, y), center)
            }
        }
    }

    @Test
    fun `zoom keeps the pixel under the focal point fixed`() {
        val vp = Viewport(10f, 20f, 30f)
        val focus = 150f to 200f
        val before = vp.screenToPixel(focus.first, focus.second)
        val zoomed = vp.zoomed(3f, focus.first, focus.second)
        assertEquals(30f, zoomed.scale)
        assertEquals(before, zoomed.screenToPixel(focus.first, focus.second))
    }

    @Test
    fun `zoom is limited to the scale range`() {
        val vp = Viewport(10f, 0f, 0f)
        assertEquals(Viewport.MAX_SCALE, vp.zoomed(1000f, 0f, 0f).scale)
        assertEquals(Viewport.MIN_SCALE, vp.zoomed(0.0001f, 0f, 0f).scale)
    }

    @Test
    fun `pan shifts the mapping`() {
        val vp = Viewport(10f, 0f, 0f).panned(25f, -10f)
        assertEquals(PixelPos(0, 1), vp.screenToPixel(25f, 0f))
    }

    @Test
    fun `fit centers the whole canvas in the view`() {
        val vp = Viewport.fit(1000f, 2000f, 32, 32, margin = 20f)
        assertEquals(960f / 32f, vp.scale)
        assertEquals(PixelPos(0, 0), vp.screenToPixel(vp.offsetX + 1f, vp.offsetY + 1f))
        assertEquals(PixelPos(31, 31), vp.screenToPixel(vp.offsetX + 32 * vp.scale - 1f, vp.offsetY + 32 * vp.scale - 1f))
        assertEquals(vp.offsetX, 1000f - (vp.offsetX + 32 * vp.scale), 0.01f)
    }

    @Test
    fun `clamp keeps part of the canvas visible`() {
        val far = Viewport(10f, 5000f, -5000f).clamped(1000f, 1000f, 16, 16, keep = 50f)
        assertEquals(950f, far.offsetX)
        assertTrue(far.offsetY + 16 * far.scale >= 50f - 0.01f)
        val inside = Viewport(10f, 100f, 100f)
        assertEquals(inside, inside.clamped(1000f, 1000f, 16, 16))
    }
}
