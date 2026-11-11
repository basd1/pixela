package dev.bastian.pixela.ui.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.pixela.domain.EditorEngine
import dev.bastian.pixela.domain.ImageExporter
import dev.bastian.pixela.domain.ImageImporter
import dev.bastian.pixela.domain.ProjectRepository
import dev.bastian.pixela.domain.ProjectSnapshot
import dev.bastian.pixela.domain.RecentColorsStore
import dev.bastian.pixela.domain.Tool
import dev.bastian.pixela.domain.composite
import dev.bastian.pixela.domain.withRecent
import dev.bastian.pixela.ui.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class LayerItem(val id: Int, val name: String, val visible: Boolean, val opacity: Float)

/** Everything the editor chrome shows. It does not change while a stroke moves, only the canvas revision does. */
data class EditorUiState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val projectName: String = "",
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

/** One-off things the screen reacts to: show a message or start a share intent. */
sealed interface EditorEvent {
    data class Saved(val uri: String) : EditorEvent
    data class ReadyToShare(val uri: String) : EditorEvent
    data object ExportFailed : EditorEvent
    data object ImportFailed : EditorEvent
}

class EditorViewModel(
    private val projectId: String,
    private val strings: Strings,
    private val recents: RecentColorsStore,
    private val projects: ProjectRepository,
    private val exporter: ImageExporter,
    private val importer: ImageImporter,
    private val saveScope: CoroutineScope,
    private val autosaveDelayMs: Long = AUTOSAVE_DELAY_MS,
) : ViewModel() {
    private var engine: EditorEngine? = null

    /** Zoom and pan; created once the project is loaded. */
    var canvas: CanvasState? = null
        private set

    private var recentColors: List<Int> = emptyList()
    private var projectName = ""
    private var scratch = IntArray(0)
    private var autosaveJob: Job? = null
    private var dirty = false

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _revision = MutableStateFlow(0)

    /** Grows with every pixel or layer change; only the canvas draw code observes it. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            recentColors = recents.colors.first()
            val project = projects.load(projectId)
            if (project == null) {
                _state.value = EditorUiState(loading = false, loadFailed = true)
                return@launch
            }
            projectName = project.name
            engine = EditorEngine(project.document)
            canvas = CanvasState(project.document.width, project.document.height)
            scratch = IntArray(project.document.width * project.document.height)
            refresh()
        }
    }

    private fun snapshot(engine: EditorEngine) = EditorUiState(
        loading = false,
        projectName = projectName,
        tool = engine.tool,
        color = engine.color,
        mirror = engine.mirror,
        filledShapes = engine.filledShapes,
        showGrid = canvas?.showGrid ?: true,
        canUndo = engine.history.canUndo,
        canRedo = engine.history.canRedo,
        layers = engine.document.layers.map { LayerItem(it.id, it.name, it.visible, it.opacity) },
        activeLayerId = engine.document.activeLayerId,
        palette = engine.document.palette,
        recentColors = recentColors,
        canvasSize = engine.document.width,
    )

    /** Publishes the state; call after every action that is not a stroke step. */
    private fun refresh() {
        val current = engine ?: return
        _state.value = snapshot(current)
        _revision.value = current.revision
    }

    /** Runs a document change on the engine, publishes it and schedules an autosave. */
    private inline fun edit(block: (EditorEngine) -> Unit) {
        val current = engine ?: return
        block(current)
        refresh()
        markDirty()
    }

    private fun markDirty() {
        dirty = true
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(autosaveDelayMs)
            saveNow()
        }
    }

    /** Saves right away if there are unsaved changes; the screen calls this when the editor is left or stopped. */
    fun flush() {
        autosaveJob?.cancel()
        engine?.cancelStroke()
        refresh()
        saveNow()
    }

    private fun saveNow() {
        val current = engine ?: return
        if (!dirty || current.isDrawing) return
        dirty = false
        val snapshot = ProjectSnapshot.of(current.document)
        // The app scope, not viewModelScope: leaving the editor must not cancel the write.
        saveScope.launch { projects.save(projectId, snapshot) }
    }

    /** Copies the current composition into [bitmap]; the canvas calls it only when [revision] moved. */
    fun render(bitmap: Bitmap) {
        val current = engine ?: return
        composite(current.document, scratch)
        bitmap.setPixels(scratch, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    fun selectTool(tool: Tool) {
        val current = engine ?: return
        current.cancelStroke()
        current.tool = tool
        refresh()
    }

    /** Picks a color from the palette, the color dialog or the recent list. */
    fun selectColor(color: Int) {
        val current = engine ?: return
        current.color = color
        recentColors = recentColors.withRecent(color)
        viewModelScope.launch { recents.save(recentColors) }
        refresh()
    }

    fun toggleMirror() {
        val current = engine ?: return
        current.mirror = !current.mirror
        refresh()
    }

    fun toggleFilledShapes() {
        val current = engine ?: return
        current.filledShapes = !current.filledShapes
        refresh()
    }

    fun toggleGrid() {
        val view = canvas ?: return
        view.showGrid = !view.showGrid
        refresh()
    }

    fun onDown(x: Int, y: Int) {
        val current = engine ?: return
        current.pointerDown(x, y)
        refresh()
    }

    fun onMove(x: Int, y: Int) {
        val current = engine ?: return
        current.pointerMove(x, y)
        _revision.value = current.revision
        if (current.color != _state.value.color) refresh()
    }

    fun onUp() = edit { it.pointerUp() }

    fun onCancel() {
        val current = engine ?: return
        current.cancelStroke()
        refresh()
    }

    fun undo() = edit { it.undo() }

    fun redo() = edit { it.redo() }

    fun addLayer() = edit { it.addLayer(strings.layerName(it.document.nextLayerId)) }

    fun selectLayer(id: Int) = edit { it.selectLayer(id) }

    fun toggleLayerVisible(id: Int) = edit {
        val layer = it.document.layer(id)
        it.updateLayer(id, layer.name, !layer.visible, layer.opacity)
    }

    fun renameLayer(id: Int, name: String) = edit {
        val layer = it.document.layer(id)
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) it.updateLayer(id, trimmed, layer.visible, layer.opacity)
    }

    fun duplicateLayer(id: Int) = edit { it.duplicateLayer(id, strings.copyOf(it.document.layer(id).name)) }

    fun deleteLayer(id: Int) = edit { it.deleteLayer(id) }

    fun mergeLayerDown(id: Int) = edit { it.mergeDown(id) }

    /** Moves a layer one step towards the top ([up] true) or the bottom of the stack. */
    fun moveLayer(id: Int, up: Boolean) = edit {
        val index = it.document.layers.indexOfFirst { layer -> layer.id == id }
        it.moveLayer(index, if (up) index + 1 else index - 1)
    }

    fun previewLayerOpacity(id: Int, opacity: Float) {
        val current = engine ?: return
        current.previewOpacity(id, opacity)
        refresh()
    }

    fun commitLayerOpacity() = edit { it.commitOpacity() }

    /** Exports the composition; [share] writes a temporary PNG for the share sheet instead of using the gallery. */
    fun export(scale: Int, share: Boolean) {
        val current = engine ?: return
        val pixels = composite(current.document)
        val width = current.document.width
        val height = current.document.height
        viewModelScope.launch {
            val result = if (share) {
                exporter.prepareShare(projectName, pixels, width, height, scale)
            } else {
                exporter.saveToGallery(projectName, pixels, width, height, scale)
            }
            val event = result.fold(
                onSuccess = { if (share) EditorEvent.ReadyToShare(it) else EditorEvent.Saved(it) },
                onFailure = { EditorEvent.ExportFailed },
            )
            _events.send(event)
        }
    }

    /** Adds the chosen image as a new layer, reduced to the canvas size. */
    fun importImage(uri: String) {
        val current = engine ?: return
        viewModelScope.launch {
            val result = importer.import(uri, current.document.width, current.document.height)
            result.fold(
                onSuccess = { pixels ->
                    edit { it.importLayer(strings.layerName(it.document.nextLayerId), pixels) }
                },
                onFailure = { _events.send(EditorEvent.ImportFailed) },
            )
        }
    }

    /** The composited pixels, for tests and exports. */
    fun compositePixels(): IntArray = engine?.let { composite(it.document) } ?: IntArray(0)

    companion object {
        const val AUTOSAVE_DELAY_MS = 800L
    }
}
