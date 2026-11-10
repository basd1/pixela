package dev.bastian.pixela.ui.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import dev.bastian.pixela.domain.Document
import dev.bastian.pixela.domain.EditorEngine
import dev.bastian.pixela.domain.Tool
import dev.bastian.pixela.domain.composite
import dev.bastian.pixela.ui.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LayerItem(val id: Int, val name: String, val visible: Boolean, val opacity: Float)

/** Everything the editor chrome shows. It does not change while a stroke moves, only the canvas revision does. */
data class EditorUiState(
    val tool: Tool = Tool.Pencil,
    val color: Int = 0,
    val mirror: Boolean = false,
    val filledShapes: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val layers: List<LayerItem> = emptyList(),
    val activeLayerId: Int = 0,
    val palette: List<Int> = emptyList(),
    val canvasSize: Int = 0,
)

class EditorViewModel(size: Int, private val strings: Strings) : ViewModel() {
    private val engine = EditorEngine(Document.blank(size, strings.layerName(1)))
    val canvas = CanvasState(size, size)

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _revision = MutableStateFlow(engine.revision)

    /** Grows with every pixel or layer change; only the canvas draw code observes it. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val scratch = IntArray(size * size)

    private fun snapshot() = EditorUiState(
        tool = engine.tool,
        color = engine.color,
        mirror = engine.mirror,
        filledShapes = engine.filledShapes,
        canUndo = engine.history.canUndo,
        canRedo = engine.history.canRedo,
        layers = engine.document.layers.map { LayerItem(it.id, it.name, it.visible, it.opacity) },
        activeLayerId = engine.document.activeLayerId,
        palette = engine.document.palette,
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
}

