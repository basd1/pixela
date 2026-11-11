package dev.bastian.pixela.domain

import dev.bastian.pixela.domain.history.Command
import dev.bastian.pixela.domain.history.History
import dev.bastian.pixela.domain.history.LayerCommands
import dev.bastian.pixela.domain.tools.StrokeRecorder
import dev.bastian.pixela.domain.tools.bresenhamLine
import dev.bastian.pixela.domain.tools.floodRegion
import dev.bastian.pixela.domain.tools.rectangle
import dev.bastian.pixela.domain.tools.shifted

enum class Tool { Pencil, Eraser, Fill, Picker, Line, Rectangle, Move }

/**
 * The editing session: applies tools to the active layer and records every user action in the
 * [History]. It has no Android dependencies. [revision] grows whenever pixels or layers change, so a
 * view can tell when it has to redraw without comparing pixels.
 */
class EditorEngine(val document: Document, val history: History = History()) {
    var tool: Tool = Tool.Pencil
    var color: Int = document.palette.firstOrNull() ?: 0xFF000000.toInt()
    var mirror: Boolean = false
    var filledShapes: Boolean = false

    var revision: Int = 0
        private set

    private var recorder: StrokeRecorder? = null
    private var startX = 0
    private var startY = 0
    private var lastX = 0
    private var lastY = 0
    private var moveSnapshot: PixelBuffer? = null

    /** True between [pointerDown] and [pointerUp] or [cancelStroke]. */
    val isDrawing: Boolean get() = recorder != null

    fun pointerDown(x: Int, y: Int) {
        if (recorder != null) cancelStroke()
        val layer = document.activeLayer
        startX = x
        startY = y
        lastX = x
        lastY = y
        when (tool) {
            Tool.Picker -> pick(x, y)
            Tool.Fill -> fill(x, y)
            else -> {
                recorder = StrokeRecorder(layer.id, layer.buffer)
                if (tool == Tool.Move) moveSnapshot = layer.buffer.copy()
                drawStep(x, y)
            }
        }
    }

    fun pointerMove(x: Int, y: Int) {
        if (tool == Tool.Picker) {
            if (x != lastX || y != lastY) pick(x, y)
            lastX = x
            lastY = y
            return
        }
        if (recorder == null || (x == lastX && y == lastY)) return
        drawStep(x, y)
        lastX = x
        lastY = y
    }

    fun pointerUp() {
        val current = recorder ?: return
        recorder = null
        moveSnapshot = null
        val change = current.finish() ?: return
        history.record(change)
        revision++
    }

    /** Drops the stroke in progress and restores the pixels it touched, leaving no mark and no history step. */
    fun cancelStroke() {
        val current = recorder ?: return
        current.reset()
        recorder = null
        moveSnapshot = null
        revision++
    }

    private fun drawStep(x: Int, y: Int) {
        val rec = recorder ?: return
        when (tool) {
            Tool.Pencil -> freehand(rec, color, x, y)
            Tool.Eraser -> freehand(rec, TRANSPARENT, x, y)
            Tool.Line -> {
                rec.reset()
                bresenhamLine(startX, startY, x, y) { px, py -> rec.set(px, py, color) }
            }
            Tool.Rectangle -> {
                rec.reset()
                rectangle(startX, startY, x, y, filledShapes) { px, py -> rec.set(px, py, color) }
            }
            Tool.Move -> {
                val moved = shifted(moveSnapshot!!, x - startX, y - startY)
                for (i in moved.pixels.indices) rec.setIndex(i, moved.pixels[i])
            }
            Tool.Fill, Tool.Picker -> Unit
        }
        revision++
    }

    /** Joins the previous position with the new one so fast strokes leave no gaps. */
    private fun freehand(rec: StrokeRecorder, paint: Int, x: Int, y: Int) {
        val width = document.width
        bresenhamLine(lastX, lastY, x, y) { px, py ->
            rec.set(px, py, paint)
            if (mirror) rec.set(width - 1 - px, py, paint)
        }
    }

    private fun fill(x: Int, y: Int) {
        val layer = document.activeLayer
        if (!layer.buffer.inBounds(x, y)) return
        val rec = StrokeRecorder(layer.id, layer.buffer)
        for (i in floodRegion(layer.buffer, x, y)) rec.setIndex(i, color)
        val change = rec.finish() ?: return
        history.record(change)
        revision++
    }

    private fun pick(x: Int, y: Int) {
        if (!document.layers.first().buffer.inBounds(x, y)) return
        val picked = composite(document)[y * document.width + x]
        if (picked.alpha() != 0) color = picked
    }

    fun undo() {
        cancelStroke()
        if (history.undo(document)) revision++
    }

    fun redo() {
        cancelStroke()
        if (history.redo(document)) revision++
    }

    fun selectLayer(id: Int) {
        if (document.layers.any { it.id == id }) {
            cancelStroke()
            document.activeLayerId = id
            revision++
        }
    }

    fun addLayer(name: String) = run(LayerCommands.add(document, name))

    fun importLayer(name: String, pixels: IntArray) = run(LayerCommands.addWithPixels(document, name, pixels))

    fun duplicateLayer(id: Int, name: String) = run(LayerCommands.duplicate(document, id, name))

    fun deleteLayer(id: Int) = run(LayerCommands.delete(document, id))

    fun moveLayer(from: Int, to: Int) = run(LayerCommands.move(document, from, to))

    fun mergeDown(id: Int) = run(LayerCommands.mergeDown(document, id))

    /** Changes name, visibility and opacity of a layer as one undoable step. */
    fun updateLayer(id: Int, name: String, visible: Boolean, opacity: Float) =
        run(LayerCommands.update(document, id, name, visible, opacity))

    private fun run(command: Command?) {
        if (command == null) return
        cancelStroke()
        history.execute(document, command)
        revision++
    }

    private var pendingOpacity: Pair<Int, Float>? = null

    /** Shows an opacity while a slider is dragged, without touching the history. */
    fun previewOpacity(id: Int, opacity: Float) {
        if (pendingOpacity == null) pendingOpacity = id to document.layer(id).opacity
        document.layer(id).opacity = opacity.coerceIn(0f, 1f)
        revision++
    }

    /** Ends a slider drag: the whole drag becomes one undoable step from the original opacity. */
    fun commitOpacity() {
        val (id, original) = pendingOpacity ?: return
        pendingOpacity = null
        val layer = document.layer(id)
        val chosen = layer.opacity
        layer.opacity = original
        updateLayer(id, layer.name, layer.visible, chosen)
    }
}
