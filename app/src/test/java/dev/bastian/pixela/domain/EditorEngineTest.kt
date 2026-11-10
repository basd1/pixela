package dev.bastian.pixela.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorEngineTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val engine = EditorEngine(Document.blank(16, "Layer 1")).also { it.color = red }
    private val buffer get() = engine.document.activeLayer.buffer

    private fun count(color: Int = red) = buffer.pixels.count { it == color }

    private fun drag(vararg points: Pair<Int, Int>) {
        engine.pointerDown(points.first().first, points.first().second)
        points.drop(1).forEach { engine.pointerMove(it.first, it.second) }
        engine.pointerUp()
    }

    @Test
    fun `pencil paints one pixel on tap`() {
        drag(3 to 4)
        assertEquals(red, buffer[3, 4])
        assertEquals(1, count())
    }

    @Test
    fun `a fast stroke is joined with a line so there are no gaps`() {
        drag(0 to 0, 15 to 0) // two samples far apart
        assertEquals(16, count())
    }

    @Test
    fun `eraser clears pixels`() {
        drag(0 to 0, 5 to 0)
        engine.tool = Tool.Eraser
        drag(2 to 0, 3 to 0)
        assertEquals(4, count())
        assertEquals(TRANSPARENT, buffer[2, 0])
    }

    @Test
    fun `mirror paints the horizontally opposite pixel`() {
        engine.mirror = true
        drag(2 to 5)
        assertEquals(red, buffer[2, 5])
        assertEquals(red, buffer[13, 5])
        assertEquals(2, count())
    }

    @Test
    fun `the whole stroke undoes in one step and redoes`() {
        drag(0 to 0, 5 to 5, 10 to 2)
        val painted = count()
        assertTrue(painted > 10)
        engine.undo()
        assertEquals(0, count())
        engine.redo()
        assertEquals(painted, count())
    }

    @Test
    fun `cancelling a stroke leaves no mark and no history step`() {
        engine.pointerDown(1, 1)
        engine.pointerMove(8, 8)
        assertTrue(engine.isDrawing)
        engine.cancelStroke()
        assertFalse(engine.isDrawing)
        assertEquals(0, count())
        assertFalse(engine.history.canUndo)
    }

    @Test
    fun `strokes that do not change a pixel are not recorded`() {
        drag(1 to 1)
        drag(1 to 1)
        assertEquals(1, engine.history.undoSize)
    }

    @Test
    fun `pointer moves outside the canvas are clipped`() {
        drag(14 to 2, 20 to 2, -5 to 2)
        assertEquals(16, count())
    }

    @Test
    fun `fill paints the connected region and is undoable`() {
        engine.tool = Tool.Fill
        engine.color = blue
        drag(3 to 3)
        assertEquals(16 * 16, count(blue))
        engine.undo()
        assertEquals(0, count(blue))
    }

    @Test
    fun `fill is bounded by a drawn outline`() {
        engine.tool = Tool.Rectangle
        drag(2 to 2, 6 to 6)
        engine.tool = Tool.Fill
        engine.color = blue
        drag(4 to 4)
        assertEquals(9, count(blue))
        assertEquals(red, buffer[2, 2])
    }

    @Test
    fun `picker takes the composited color and ignores transparent pixels`() {
        drag(5 to 5)
        engine.tool = Tool.Picker
        engine.color = blue
        drag(5 to 5)
        assertEquals(red, engine.color)
        drag(9 to 9)
        assertEquals(red, engine.color)
        assertEquals(1, engine.history.undoSize)
    }

    @Test
    fun `line preview only keeps the final line`() {
        engine.tool = Tool.Line
        engine.pointerDown(0, 0)
        engine.pointerMove(15, 15)
        engine.pointerMove(15, 0)
        engine.pointerUp()
        assertEquals(16, count())
        assertEquals(red, buffer[15, 0])
        assertEquals(TRANSPARENT, buffer[7, 7])
    }

    @Test
    fun `rectangle outline and filled`() {
        engine.tool = Tool.Rectangle
        drag(1 to 1, 4 to 3)
        assertEquals(10, count())
        engine.undo()
        engine.filledShapes = true
        drag(1 to 1, 4 to 3)
        assertEquals(12, count())
    }

    @Test
    fun `move shifts the layer content and undoes as one step`() {
        drag(0 to 0, 2 to 0)
        engine.tool = Tool.Move
        drag(5 to 5, 8 to 7)
        assertEquals(red, buffer[3, 2])
        assertEquals(red, buffer[5, 2])
        assertEquals(TRANSPARENT, buffer[0, 0])
        engine.undo()
        assertEquals(red, buffer[0, 0])
        assertEquals(3, count())
    }

    @Test
    fun `layer edits bump the revision and are undoable`() {
        val start = engine.revision
        engine.addLayer("Layer 2")
        assertTrue(engine.revision > start)
        assertEquals(2, engine.document.layers.size)
        engine.undo()
        assertEquals(1, engine.document.layers.size)
    }

    @Test
    fun `drawing happens on the active layer only`() {
        engine.addLayer("Top")
        drag(1 to 1)
        assertEquals(TRANSPARENT, engine.document.layer(1).buffer[1, 1])
        assertEquals(red, engine.document.layer(2).buffer[1, 1])
        engine.selectLayer(1)
        drag(2 to 2)
        assertEquals(red, engine.document.layer(1).buffer[2, 2])
    }

    @Test
    fun `an opacity drag is a single undo step back to the original value`() {
        engine.previewOpacity(1, 0.8f)
        engine.previewOpacity(1, 0.4f)
        engine.commitOpacity()
        assertEquals(0.4f, engine.document.layer(1).opacity)
        assertEquals(1, engine.history.undoSize)
        engine.undo()
        assertEquals(1f, engine.document.layer(1).opacity)
        engine.redo()
        assertEquals(0.4f, engine.document.layer(1).opacity)
    }

    @Test
    fun `hiding a layer removes it from the composition and undo brings it back`() {
        drag(0 to 0)
        engine.updateLayer(1, "Layer 1", visible = false, opacity = 1f)
        assertEquals(TRANSPARENT, composite(engine.document)[0])
        engine.undo()
        assertEquals(red, composite(engine.document)[0])
    }
}
