package dev.bastian.pixela.domain

import dev.bastian.pixela.domain.history.History
import dev.bastian.pixela.domain.history.LayerCommands
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectCodecTest {
    @Test
    fun `pixel codec round trips empty, solid and noisy arrays`() {
        val noisy = IntArray(64 * 64) { Random(it).nextInt() }
        val solid = IntArray(64 * 64) { 0xFF336699.toInt() }
        val half = IntArray(100) { if (it < 50) 0 else -1 }
        for (pixels in listOf(IntArray(256), solid, noisy, half, intArrayOf(7))) {
            assertTrue(pixels.contentEquals(PixelCodec.decode(PixelCodec.encode(pixels), pixels.size)))
        }
    }

    @Test
    fun `runs compress an empty canvas to a handful of bytes`() {
        val encoded = PixelCodec.encode(IntArray(64 * 64))
        assertTrue(encoded.length < 30, "encoded length was ${encoded.length}")
    }

    @Test
    fun `decoding rejects data that does not match the size`() {
        val encoded = PixelCodec.encode(IntArray(16))
        assertFailsWith<IllegalArgumentException> { PixelCodec.decode(encoded, 15) }
        assertFailsWith<IllegalArgumentException> { PixelCodec.decode(encoded, 17) }
        assertFailsWith<IllegalArgumentException> { PixelCodec.decode("not base64 !!!", 16) }
    }

    private fun sampleDocument(): Document {
        val doc = Document.blank(16, "Background")
        val history = History()
        history.execute(doc, LayerCommands.add(doc, "Sprite"))
        history.execute(doc, LayerCommands.add(doc, "Shadow"))
        doc.layer(1).buffer.fill(0xFF102030.toInt())
        doc.layer(2).buffer[3, 4] = 0xFFFF0000.toInt()
        doc.layer(2).buffer[15, 15] = 0x80FFFFFF.toInt()
        doc.layer(3).buffer[0, 0] = 0xFF00FF00.toInt()
        doc.layer(2).opacity = 0.35f
        doc.layer(3).visible = false
        doc.activeLayerId = 2
        doc.palette = DefaultPalette.reversed()
        return doc
    }

    @Test
    fun `a document survives a serialization round trip`() {
        val original = sampleDocument()
        val restored = assertNotNull(ProjectCodec.decode(ProjectCodec.encode(original)))

        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(original.activeLayerId, restored.activeLayerId)
        assertEquals(original.nextLayerId, restored.nextLayerId)
        assertEquals(original.palette, restored.palette)
        assertEquals(original.layers.size, restored.layers.size)
        for ((a, b) in original.layers.zip(restored.layers)) {
            assertEquals(a.id, b.id)
            assertEquals(a.name, b.name)
            assertEquals(a.visible, b.visible)
            assertEquals(a.opacity, b.opacity)
            assertEquals(a.buffer, b.buffer)
        }
        assertTrue(composite(original).contentEquals(composite(restored)))
    }

    @Test
    fun `a restored document keeps handing out fresh layer ids`() {
        val restored = assertNotNull(ProjectCodec.decode(ProjectCodec.encode(sampleDocument())))
        val id = restored.newLayerId()
        assertTrue(restored.layers.none { it.id == id })
    }

    @Test
    fun `damaged or foreign files decode to null instead of crashing`() {
        val valid = ProjectCodec.encode(sampleDocument())
        assertNull(ProjectCodec.decode(""))
        assertNull(ProjectCodec.decode("{not json"))
        assertNull(ProjectCodec.decode(valid.take(valid.length / 2)))
        assertNull(ProjectCodec.decode(valid.replace("\"width\":16", "\"width\":9999")))
        assertNull(ProjectCodec.decode(valid.replace("\"version\":1", "\"version\":99")))
    }

    @Test
    fun `unknown fields from newer writers are ignored`() {
        val valid = ProjectCodec.encode(sampleDocument())
        val extended = valid.replaceFirst("{", "{\"futureField\":true,")
        assertNotNull(ProjectCodec.decode(extended))
    }
}
