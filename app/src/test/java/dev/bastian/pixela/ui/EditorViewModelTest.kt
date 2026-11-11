package dev.bastian.pixela.ui

import app.cash.turbine.test
import dev.bastian.pixela.FakeExporter
import dev.bastian.pixela.FakeImporter
import dev.bastian.pixela.FakeProjectRepository
import dev.bastian.pixela.FakeRecents
import dev.bastian.pixela.FakeStrings
import dev.bastian.pixela.domain.ProjectCodec
import dev.bastian.pixela.domain.TRANSPARENT
import dev.bastian.pixela.domain.alpha
import dev.bastian.pixela.ui.editor.EditorEvent
import dev.bastian.pixela.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val recents = FakeRecents()
    private val repository = FakeProjectRepository(size = 8)
    private val exporter = FakeExporter()
    private lateinit var vm: EditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = newViewModel()
        scope.runCurrent()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(importer: FakeImporter = FakeImporter(Result.success(IntArray(64)))) = EditorViewModel(
        projectId = "p1",
        strings = FakeStrings(),
        recents = recents,
        projects = repository,
        exporter = exporter,
        importer = importer,
        saveScope = scope,
        autosaveDelayMs = 1_000,
    )

    private fun tap(x: Int, y: Int) {
        vm.onDown(x, y)
        vm.onUp()
    }

    @Test
    fun `the project is loaded into the state`() {
        assertEquals(false, vm.state.value.loading)
        assertEquals("Test", vm.state.value.projectName)
        assertEquals(listOf("Layer 1"), vm.state.value.layers.map { it.name })
    }

    @Test
    fun `a missing project is reported as a load failure`() {
        repository.json.remove("p1")
        val other = newViewModel()
        scope.runCurrent()
        assertTrue(other.state.value.loadFailed)
    }

    @Test
    fun `hiding a layer changes the composition and undo restores it`() {
        vm.selectColor(red)
        tap(1, 1)
        vm.toggleLayerVisible(1)
        assertEquals(TRANSPARENT, vm.compositePixels()[9])
        assertEquals(false, vm.state.value.layers[0].visible)
        vm.undo()
        assertEquals(red, vm.compositePixels()[9])
    }

    @Test
    fun `reordering layers changes which one is on top and undo restores it`() {
        vm.selectColor(red)
        tap(1, 1)
        vm.addLayer()
        vm.selectColor(blue)
        tap(1, 1)
        assertEquals(blue, vm.compositePixels()[9])
        vm.moveLayer(2, up = false)
        assertEquals(listOf(2, 1), vm.state.value.layers.map { it.id })
        assertEquals(red, vm.compositePixels()[9])
        vm.undo()
        assertEquals(blue, vm.compositePixels()[9])
    }

    @Test
    fun `opacity drag changes the composition and a single undo restores full opacity`() {
        vm.selectColor(red)
        tap(1, 1)
        vm.previewLayerOpacity(1, 0.7f)
        vm.previewLayerOpacity(1, 0.5f)
        assertTrue(vm.compositePixels()[9].alpha() in 127..128)
        vm.commitLayerOpacity()
        assertEquals(0.5f, vm.state.value.layers[0].opacity)
        vm.undo()
        assertEquals(1f, vm.state.value.layers[0].opacity)
        assertEquals(red, vm.compositePixels()[9])
    }

    @Test
    fun `rename duplicate merge and delete work and are undoable`() {
        vm.renameLayer(1, "  Sky ")
        assertEquals("Sky", vm.state.value.layers[0].name)
        vm.renameLayer(1, "   ")
        assertEquals("Sky", vm.state.value.layers[0].name)
        vm.duplicateLayer(1)
        assertEquals(listOf("Sky", "Sky copy"), vm.state.value.layers.map { it.name })
        vm.mergeLayerDown(2)
        assertEquals(1, vm.state.value.layers.size)
        vm.undo()
        assertEquals(2, vm.state.value.layers.size)
        vm.deleteLayer(2)
        assertEquals(1, vm.state.value.layers.size)
        vm.deleteLayer(1) // the last layer cannot be deleted
        assertEquals(1, vm.state.value.layers.size)
    }

    @Test
    fun `chosen colors are remembered as recent and saved`() {
        vm.selectColor(red)
        vm.selectColor(blue)
        vm.selectColor(red)
        scope.runCurrent()
        assertEquals(listOf(red, blue), vm.state.value.recentColors)
        assertEquals(listOf(red, blue), recents.state.value)
    }

    @Test
    fun `autosave waits for the debounce and writes only once for a burst of changes`() {
        vm.selectColor(red)
        tap(1, 1)
        scope.advanceTimeBy(500)
        tap(2, 2)
        scope.advanceTimeBy(900) // 900ms after the last change: still waiting
        scope.runCurrent()
        assertEquals(0, repository.saves.size)
        scope.advanceTimeBy(200)
        scope.runCurrent()
        assertEquals(1, repository.saves.size)
        val saved = assertNotNull(ProjectCodec.decode(repository.json.getValue("p1")))
        assertEquals(2, saved.layers[0].buffer.pixels.count { it == red })
    }

    @Test
    fun `flush saves immediately and a second flush with nothing new does nothing`() {
        vm.selectColor(red)
        tap(3, 3)
        vm.flush()
        scope.runCurrent()
        assertEquals(1, repository.saves.size)
        vm.flush()
        scope.runCurrent()
        assertEquals(1, repository.saves.size)
    }

    @Test
    fun `a stroke in progress is not saved half drawn`() {
        vm.selectColor(red)
        vm.onDown(0, 0)
        vm.onMove(7, 0)
        vm.flush() // cancels the stroke first
        scope.runCurrent()
        val saved = assertNotNull(ProjectCodec.decode(repository.json.getValue("p1")))
        assertEquals(0, saved.layers[0].buffer.pixels.count { it == red })
    }

    @Test
    fun `export to gallery reports success and failure as events`() = runTest(dispatcher) {
        vm.events.test {
            vm.export(scale = 4, share = false)
            advanceUntilIdle()
            assertTrue(awaitItem() is EditorEvent.Saved)
            exporter.fail = true
            vm.export(scale = 4, share = false)
            advanceUntilIdle()
            assertEquals(EditorEvent.ExportFailed, awaitItem())
            vm.export(scale = 8, share = true)
            advanceUntilIdle()
            assertTrue(awaitItem() is EditorEvent.ReadyToShare)
        }
        assertEquals(listOf("save:Test:4:32x32", "save:Test:4:32x32", "share:Test:8"), exporter.calls)
    }

    @Test
    fun `importing an image adds a layer with its pixels and can be undone`() {
        val pixels = IntArray(64).also { it[10] = blue }
        vm = newViewModel(FakeImporter(Result.success(pixels)))
        scope.runCurrent()
        vm.importImage("content://image/1")
        scope.runCurrent()
        assertEquals(2, vm.state.value.layers.size)
        assertEquals(blue, vm.compositePixels()[10])
        vm.undo()
        assertEquals(1, vm.state.value.layers.size)
    }

    @Test
    fun `a failed import tells the screen and keeps the document`() = runTest(dispatcher) {
        vm = newViewModel(FakeImporter(Result.failure(java.io.IOException("not an image"))))
        advanceUntilIdle()
        vm.events.test {
            vm.importImage("content://image/bad")
            advanceUntilIdle()
            assertEquals(EditorEvent.ImportFailed, awaitItem())
        }
        assertEquals(1, vm.state.value.layers.size)
    }
}
