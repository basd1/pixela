package dev.bastian.pixela.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun twoLayers(): Document {
        val bottom = Layer(1, "bottom", buffer = PixelBuffer(2, 1))
        val top = Layer(2, "top", buffer = PixelBuffer(2, 1))
        return Document(2, 1, listOf(bottom, top), 2)
    }

    @Test
    fun `opaque top layer covers the bottom one`() {
        val doc = twoLayers()
        doc.layer(1).buffer[0, 0] = blue
        doc.layer(2).buffer[0, 0] = red
        val out = composite(doc)
        assertEquals(red, out[0])
        assertEquals(TRANSPARENT, out[1])
    }

    @Test
    fun `transparent pixels let the lower layer show`() {
        val doc = twoLayers()
        doc.layer(1).buffer[1, 0] = blue
        assertEquals(blue, composite(doc)[1])
    }

    @Test
    fun `half transparent source over opaque mixes the colors`() {
        val doc = twoLayers()
        doc.layer(1).buffer[0, 0] = blue
        doc.layer(2).buffer[0, 0] = argb(128, 255, 0, 0)
        val out = composite(doc)[0]
        assertEquals(255, out.alpha())
        assertEquals(128, out.red())
        assertEquals(127, out.blue())
    }

    @Test
    fun `layer opacity scales the alpha of its pixels`() {
        val doc = twoLayers()
        doc.layer(1).buffer[0, 0] = blue
        doc.layer(2).buffer[0, 0] = red
        doc.layer(2).opacity = 0.5f
        val out = composite(doc)[0]
        assertEquals(255, out.alpha())
        assertEquals(128, out.red())
        assertEquals(128, out.blue())
    }

    @Test
    fun `layer opacity over transparent keeps the color and lowers alpha`() {
        val doc = twoLayers()
        doc.layer(2).buffer[0, 0] = red
        doc.layer(2).opacity = 0.5f
        val out = composite(doc)[0]
        assertEquals(128, out.alpha())
        assertEquals(255, out.red())
        assertEquals(0, out.blue())
    }

    @Test
    fun `hidden and zero opacity layers are skipped`() {
        val doc = twoLayers()
        doc.layer(1).buffer[0, 0] = blue
        doc.layer(2).buffer[0, 0] = red
        doc.layer(2).visible = false
        assertEquals(blue, composite(doc)[0])
        doc.layer(2).visible = true
        doc.layer(2).opacity = 0f
        assertEquals(blue, composite(doc)[0])
    }

    @Test
    fun `layer order matters`() {
        val doc = twoLayers()
        doc.layer(1).buffer[0, 0] = blue
        doc.layer(2).buffer[0, 0] = red
        doc.layers = doc.layers.reversed()
        assertEquals(blue, composite(doc)[0])
    }
}

