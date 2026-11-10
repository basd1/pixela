package dev.bastian.pixela.ui.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.pixela.domain.Document
import dev.bastian.pixela.domain.EditorEngine
import dev.bastian.pixela.domain.RecentColorsStore
import dev.bastian.pixela.domain.Tool
import dev.bastian.pixela.domain.composite
import dev.bastian.pixela.domain.withRecent
import dev.bastian.pixela.ui.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LayerItem(val id: Int, val name: String, val visible: Boolean, val opacity: Float)

/** Everything the editor chrome shows. It does not change while a stroke moves, only the canvas revision does. */
data class EditorUiState(
    val tool: Tool = Tool.Pencil,
    val color: Int = 0,
    val mirror: Boolean = false,
    val filledShapes: Boolean = false,
    val showGrid: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val layers: List<LayerItem> = emptyList(),
    val activeLayerId: Int = 0,
    val palette: List<Int> = emptyList(),
    val recentColors: List<Int> = emptyList(),
    val canvasSize: Int = 0,
)

class EditorViewModel(
    size: Int,
    private val strings: Strings,
    private val recents: RecentColorsStore,
) : ViewModel() {
    private val engine = EditorEngine(Document.blank(size, strings.layerName(1)))
    val canvas = CanvasState(size, size)

    private var recentColors: List<Int> = emptyList()

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _revision = MutableStateFlow(engine.revision)

    /** Grows with every pixel or layer change; only the canvas draw code observes it. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val scratch = IntArray(size * size)

    init {
        viewModelScope.launch {
            recentColors = recents.colors.first()
            refresh()
        }
    }

    private fun snapshot() = EditorUiState(
        tool = engine.tool,
        color = engine.color,
        mirror = engine.mirror,
        filledShapes = engine.filledShapes,
        showGrid = canvas.showGrid,
        canUndo = engine.history.canUndo,
        canRedo = engine.history.canRedo,
        layers = engine.document.layers.map { LayerItem(it.id, it.name, it.visible, it.opacity) },
        activeLayerId = engine.document.activeLayerId,
        palette = engine.document.palette,
        recentColors = recentColors,
        canvasSize = engine.document.width,
    )

    private fun refresh() {
        _state.value = snapshot()
        _revision.value = engine.revision
    }

    /** Copies the current composition into [bitmap]; the canvas calls it only when [revision] moved. */
    fun render(bitmap: Bitmap) {
        composite(engine.document, scratch)
        bitmap.setPixels(scratch, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    fun selectTool(tool: Tool) {
        engine.cancelStroke()
        engine.tool = tool
        refresh()
    }

    /** Picks a color from the palette, the color dialog or the recent list. */
    fun selectColor(color: Int) {
        engine.color = color
        recentColors = recentColors.withRecent(color)
        viewModelScope.launch { recents.save(recentColors) }
        refresh()
    }

    fun toggleMirror() {
        engine.mirror = !engine.mirror
        refresh()
    }

    fun toggleFilledShapes() {
        engine.filledShapes = !engine.filledShapes
        refresh()
    }

    fun toggleGrid() {
        canvas.showGrid = !canvas.showGrid
        refresh()
    }

    fun onDown(x: Int, y: Int) {
        engine.pointerDown(x, y)
        refresh()
    }

    fun onMove(x: Int, y: Int) {
        engine.pointerMove(x, y)
        _revision.value = engine.revision
        if (engine.color != _state.value.color) refresh()
    }

    fun onUp() {
        engine.pointerUp()
        refresh()
    }

    fun onCancel() {
        engine.cancelStroke()
        refresh()
    }

    fun undo() {
        engine.undo()
        refresh()
    }

    fun redo() {
        engine.redo()
        refresh()
    }

    fun addLayer() {
        engine.addLayer(strings.layerName(engine.document.nextLayerId))
        refresh()
    }

    fun selectLayer(id: Int) {
        engine.selectLayer(id)
        refresh()
    }

    fun toggleLayerVisible(id: Int) {
        val layer = engine.document.layer(id)
        engine.updateLayer(id, layer.name, !layer.visible, layer.opacity)
        refresh()
    }

    fun renameLayer(id: Int, name: String) {
        val layer = engine.document.layer(id)
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) engine.updateLayer(id, trimmed, layer.visible, layer.opacity)
        refresh()
    }

    fun duplicateLayer(id: Int) {
        engine.duplicateLayer(id, strings.copyOf(engine.document.layer(id).name))
        refresh()
    }

    fun deleteLayer(id: Int) {
        engine.deleteLayer(id)
        refresh()
    }

    fun mergeLayerDown(id: Int) {
        engine.mergeDown(id)
        refresh()
    }

    /** Moves a layer one step towards the top ([up] true) or the bottom of the stack. */
    fun moveLayer(id: Int, up: Boolean) {
        val index = engine.document.layers.indexOfFirst { it.id == id }
        engine.moveLayer(index, if (up) index + 1 else index - 1)
        refresh()
    }

    fun previewLayerOpacity(id: Int, opacity: Float) {
        engine.previewOpacity(id, opacity)
        refresh()
    }

    fun commitLayerOpacity() {
        engine.commitOpacity()
        refresh()
    }


    /** The composited pixels, for tests and exports. */
    fun compositePixels(): IntArray = composite(engine.document)
}
