package dev.bastian.pixela.domain

import dev.bastian.pixela.domain.history.History
import dev.bastian.pixela.domain.history.LayerCommands
import dev.bastian.pixela.domain.history.PixelChange
import dev.bastian.pixela.domain.tools.StrokeRecorder
import dev.bastian.pixela.domain.tools.bresenhamLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val doc = Document.blank(8, "Layer 1")
    private val history = History()

    private fun stroke(color: Int, x0: Int, y0: Int, x1: Int, y1: Int) {
        val layer = doc.activeLayer
        val recorder = StrokeRecorder(layer.id, layer.buffer)
        bresenhamLine(x0, y0, x1, y1) { x, y -> recorder.set(x, y, color) }
        history.record(recorder.finish()!!)
    }

    private fun filled(): Int = doc.activeLayer.buffer.pixels.count { it != TRANSPARENT }

    @Test
    fun `a whole stroke is one undo step`() {
        stroke(red, 0, 0, 7, 0)
        assertEquals(8, filled())
        assertEquals(1, history.undoSize)
        assertTrue(history.undo(doc))
        assertEquals(0, filled())
        assertTrue(history.redo(doc))
        assertEquals(8, filled())
    }

    @Test
    fun `a stroke stores only the changed pixels`() {
        stroke(red, 0, 0, 2, 0)
        val change = PixelChange.diff(1, IntArray(64), doc.activeLayer.buffer.pixels)!!
        assertEquals(3, change.indices.size)
    }

    @Test
    fun `undo and redo with an empty history do nothing`() {
        assertFalse(history.undo(doc))
        assertFalse(history.redo(doc))
    }

    @Test
    fun `a new change clears the redo stack`() {
        stroke(red, 0, 0, 3, 0)
        history.undo(doc)
        assertTrue(history.canRedo)
        stroke(blue, 0, 1, 3, 1)
        assertFalse(history.canRedo)
        assertFalse(history.redo(doc))
    }

    @Test
    fun `history is limited to one hundred steps`() {
        repeat(105) { stroke(argb(255, it + 1, 0, 0), 0, 0, 7, 0) }
        assertEquals(100, history.undoSize)
        var undone = 0
        while (history.undo(doc)) undone++
        assertEquals(100, undone)
    }

    @Test
    fun `adding a layer is undoable and selects it`() {
        history.execute(doc, LayerCommands.add(doc, "Layer 2"))
        assertEquals(2, doc.layers.size)
        assertEquals(2, doc.activeLayerId)
        history.undo(doc)
        assertEquals(1, doc.layers.size)
        assertEquals(1, doc.activeLayerId)
        history.redo(doc)
        assertEquals(listOf("Layer 1", "Layer 2"), doc.layers.map { it.name })
    }

    @Test
    fun `deleting a layer brings its pixels back on undo`() {
        stroke(red, 0, 0, 3, 0)
        history.execute(doc, LayerCommands.add(doc, "Layer 2"))
        history.execute(doc, LayerCommands.delete(doc, 1)!!)
        assertEquals(listOf(2), doc.layers.map { it.id })
        history.undo(doc)
        assertEquals(listOf(1, 2), doc.layers.map { it.id })
        assertEquals(4, doc.layer(1).buffer.pixels.count { it != TRANSPARENT })
    }

    @Test
    fun `the last layer cannot be deleted`() {
        assertNull(LayerCommands.delete(doc, 1))
    }

    @Test
    fun `reordering layers is undoable`() {
        history.execute(doc, LayerCommands.add(doc, "B"))
        history.execute(doc, LayerCommands.add(doc, "C"))
        history.execute(doc, LayerCommands.move(doc, 2, 0)!!)
        assertEquals(listOf("C", "Layer 1", "B"), doc.layers.map { it.name })
        history.undo(doc)
        assertEquals(listOf("Layer 1", "B", "C"), doc.layers.map { it.name })
    }

    @Test
    fun `visibility opacity and name changes are undoable`() {
        val command = LayerCommands.update(doc, 1, "Sky", visible = false, opacity = 0.5f)
        history.execute(doc, assertNotNull(command))
        assertEquals("Sky", doc.layer(1).name)
        assertFalse(doc.layer(1).visible)
        assertEquals(0.5f, doc.layer(1).opacity)
        history.undo(doc)
        assertEquals("Layer 1", doc.layer(1).name)
        assertTrue(doc.layer(1).visible)
        assertEquals(1f, doc.layer(1).opacity)
        assertNull(LayerCommands.update(doc, 1, "Layer 1", visible = true, opacity = 1f))
    }

    @Test
    fun `duplicate copies the pixels`() {
        stroke(red, 0, 0, 3, 0)
        history.execute(doc, LayerCommands.duplicate(doc, 1, "Copy"))
        assertEquals(2, doc.layers.size)
        assertEquals(doc.layer(1).buffer, doc.layer(2).buffer)
        doc.layer(2).buffer[0, 5] = blue
        assertEquals(TRANSPARENT, doc.layer(1).buffer[0, 5]) // the copy is independent
    }

    @Test
    fun `merge down paints into the lower layer and undo restores both layers`() {
        stroke(red, 0, 0, 3, 0)
        history.execute(doc, LayerCommands.add(doc, "Top"))
        stroke(blue, 0, 1, 1, 1)
        val before = doc.layer(1).buffer.copy()
        history.execute(doc, LayerCommands.mergeDown(doc, 2)!!)
        assertEquals(1, doc.layers.size)
        assertEquals(blue, doc.layer(1).buffer[0, 1])
        assertEquals(red, doc.layer(1).buffer[0, 0])
        history.undo(doc)
        assertEquals(2, doc.layers.size)
        assertEquals(before, doc.layer(1).buffer)
        assertEquals(blue, doc.layer(2).buffer[0, 1])
        assertNull(LayerCommands.mergeDown(doc, 1)) // bottom layer has nothing below
    }

    @Test
    fun `merge down respects layer opacity`() {
        history.execute(doc, LayerCommands.add(doc, "Top"))
        doc.layer(2).buffer[0, 0] = red
        doc.layer(2).opacity = 0.5f
        history.execute(doc, LayerCommands.mergeDown(doc, 2)!!)
        assertEquals(128, doc.layer(1).buffer[0, 0].alpha())
    }
}

