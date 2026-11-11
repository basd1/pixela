package dev.bastian.pixela.domain.history

import dev.bastian.pixela.domain.Document
import dev.bastian.pixela.domain.Layer
import dev.bastian.pixela.domain.PixelBuffer
import dev.bastian.pixela.domain.blendOver

/** A reversible change to a [Document]. */
interface Command {
    fun apply(document: Document)
    fun revert(document: Document)
}

/** Changed pixels of one layer: only indices with their old and new value, never a canvas copy. */
class PixelChange(
    val layerId: Int,
    val indices: IntArray,
    val before: IntArray,
    val after: IntArray,
) : Command {
    override fun apply(document: Document) = write(document, after)
    override fun revert(document: Document) = write(document, before)

    private fun write(document: Document, values: IntArray) {
        val pixels = document.layer(layerId).buffer.pixels
        for (i in indices.indices) pixels[indices[i]] = values[i]
    }

    companion object {
        /** Builds a change from two full pixel arrays; null when nothing differs. */
        fun diff(layerId: Int, old: IntArray, new: IntArray): PixelChange? {
            var count = 0
            for (i in old.indices) if (old[i] != new[i]) count++
            if (count == 0) return null
            val idx = IntArray(count)
            val b = IntArray(count)
            val a = IntArray(count)
            var n = 0
            for (i in old.indices) {
                if (old[i] != new[i]) {
                    idx[n] = i
                    b[n] = old[i]
                    a[n] = new[i]
                    n++
                }
            }
            return PixelChange(layerId, idx, b, a)
        }
    }
}

class AddLayer(
    private val layer: Layer,
    private val index: Int,
    private val previousActive: Int,
) : Command {
    override fun apply(document: Document) {
        document.layers = document.layers.toMutableList().apply { add(index, layer) }
        document.activeLayerId = layer.id
    }

    override fun revert(document: Document) {
        document.layers = document.layers.filter { it.id != layer.id }
        document.activeLayerId = previousActive
    }
}

class DeleteLayer(
    private val layer: Layer,
    private val index: Int,
    private val newActive: Int,
) : Command {
    override fun apply(document: Document) {
        document.layers = document.layers.filter { it.id != layer.id }
        document.activeLayerId = newActive
    }

    override fun revert(document: Document) {
        document.layers = document.layers.toMutableList().apply { add(index, layer) }
        document.activeLayerId = layer.id
    }
}

class MoveLayer(private val from: Int, private val to: Int) : Command {
    override fun apply(document: Document) = move(document, from, to)
    override fun revert(document: Document) = move(document, to, from)

    private fun move(document: Document, source: Int, target: Int) {
        document.layers = document.layers.toMutableList().apply { add(target, removeAt(source)) }
    }
}

/** Changes name, visibility and opacity of a layer in one step. */
class UpdateLayer(
    private val layerId: Int,
    private val old: Props,
    private val new: Props,
) : Command {
    data class Props(val name: String, val visible: Boolean, val opacity: Float)

    override fun apply(document: Document) = set(document, new)
    override fun revert(document: Document) = set(document, old)

    private fun set(document: Document, props: Props) {
        val layer = document.layer(layerId)
        layer.name = props.name
        layer.visible = props.visible
        layer.opacity = props.opacity
    }
}

/** Paints the upper layer into the one below and removes it. */
class MergeDown(
    private val upper: Layer,
    private val upperIndex: Int,
    private val lowerChange: PixelChange?,
    private val lowerId: Int,
) : Command {
    override fun apply(document: Document) {
        lowerChange?.apply(document)
        document.layers = document.layers.filter { it.id != upper.id }
        document.activeLayerId = lowerId
    }

    override fun revert(document: Document) {
        lowerChange?.revert(document)
        document.layers = document.layers.toMutableList().apply { add(upperIndex, upper) }
        document.activeLayerId = upper.id
    }
}

/** Builders that look at the document and return the matching command, or null when the operation does nothing. */
object LayerCommands {
    fun add(document: Document, name: String): Command {
        val layer = Layer(document.newLayerId(), name, buffer = PixelBuffer(document.width, document.height))
        val index = document.layers.indexOfFirst { it.id == document.activeLayerId } + 1
        return AddLayer(layer, index, document.activeLayerId)
    }

    /** A new layer above the active one, filled with [pixels] (an imported image). */
    fun addWithPixels(document: Document, name: String, pixels: IntArray): Command {
        val layer = Layer(document.newLayerId(), name, buffer = PixelBuffer(document.width, document.height, pixels))
        val index = document.layers.indexOfFirst { it.id == document.activeLayerId } + 1
        return AddLayer(layer, index, document.activeLayerId)
    }

    fun duplicate(document: Document, id: Int, name: String): Command {
        val index = document.layers.indexOfFirst { it.id == id }
        val copy = document.layers[index].copy(id = document.newLayerId(), name = name)
        return AddLayer(copy, index + 1, document.activeLayerId)
    }

    fun delete(document: Document, id: Int): Command? {
        if (document.layers.size < 2) return null
        val index = document.layers.indexOfFirst { it.id == id }
        val newActive = document.layers[if (index > 0) index - 1 else 1].id
        return DeleteLayer(document.layers[index], index, newActive)
    }

    fun move(document: Document, from: Int, to: Int): Command? {
        if (from == to || from !in document.layers.indices || to !in document.layers.indices) return null
        return MoveLayer(from, to)
    }

    fun update(document: Document, id: Int, name: String, visible: Boolean, opacity: Float): Command? {
        val layer = document.layer(id)
        val old = UpdateLayer.Props(layer.name, layer.visible, layer.opacity)
        val new = UpdateLayer.Props(name, visible, opacity.coerceIn(0f, 1f))
        return if (old == new) null else UpdateLayer(id, old, new)
    }

    fun mergeDown(document: Document, id: Int): Command? {
        val index = document.layers.indexOfFirst { it.id == id }
        if (index < 1) return null
        val upper = document.layers[index]
        val lower = document.layers[index - 1]
        val merged = lower.buffer.pixels.copyOf()
        if (upper.visible) {
            for (i in merged.indices) merged[i] = blendOver(merged[i], upper.buffer.pixels[i], upper.opacity)
        }
        return MergeDown(upper, index, PixelChange.diff(lower.id, lower.buffer.pixels, merged), lower.id)
    }
}
