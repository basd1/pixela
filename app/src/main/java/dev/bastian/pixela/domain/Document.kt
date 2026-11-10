package dev.bastian.pixela.domain

/** One drawing layer. Name, visibility and opacity are mutable; history commands restore them on undo. */
class Layer(
    val id: Int,
    var name: String,
    var visible: Boolean = true,
    var opacity: Float = 1f,
    val buffer: PixelBuffer,
) {
    fun copy(id: Int = this.id, name: String = this.name): Layer =
        Layer(id, name, visible, opacity, buffer.copy())
}

/** The edited image: ordered layers (index 0 is the bottom), the active layer and the palette. */
class Document(
    val width: Int,
    val height: Int,
    layers: List<Layer>,
    activeLayerId: Int,
    palette: List<Int> = DefaultPalette,
    nextLayerId: Int = (layers.maxOfOrNull { it.id } ?: 0) + 1,
) {
    init {
        require(layers.isNotEmpty()) { "A document needs at least one layer" }
        require(layers.any { it.id == activeLayerId }) { "Active layer is not in the document" }
    }

    var layers: List<Layer> = layers
    var activeLayerId: Int = activeLayerId
    var palette: List<Int> = palette
    var nextLayerId: Int = nextLayerId
        private set

    val activeLayer: Layer get() = layers.first { it.id == activeLayerId }

    fun layer(id: Int): Layer = layers.first { it.id == id }

    fun newLayerId(): Int = nextLayerId++

    companion object {
        fun blank(size: Int, layerName: String): Document {
            val layer = Layer(1, layerName, buffer = PixelBuffer(size, size))
            return Document(size, size, listOf(layer), layer.id)
        }
    }
}

/** Mixes the visible layers from bottom to top with "source over" and layer opacity. */
fun composite(document: Document, out: IntArray = IntArray(document.width * document.height)): IntArray {
    out.fill(TRANSPARENT)
    for (layer in document.layers) {
        if (!layer.visible || layer.opacity <= 0f) continue
        val src = layer.buffer.pixels
        for (i in out.indices) {
            val s = src[i]
            if (s.alpha() == 0) continue
            out[i] = if (layer.opacity >= 1f && s.alpha() == 255) s else blendOver(out[i], s, layer.opacity)
        }
    }
    return out
}
