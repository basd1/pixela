package dev.bastian.pixela.ui

import dev.bastian.pixela.domain.RecentColorsStore
import dev.bastian.pixela.domain.TRANSPARENT
import dev.bastian.pixela.domain.alpha
import dev.bastian.pixela.ui.editor.EditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private class FakeStrings : Strings {
        override fun layerName(number: Int) = "Layer $number"
        override fun newProjectName(number: Int) = "Drawing $number"
        override fun copyOf(name: String) = "$name copy"
    }

    private class FakeRecents : RecentColorsStore {
        val state = MutableStateFlow<List<Int>>(emptyList())
        override val colors: Flow<List<Int>> = state
        override suspend fun save(colors: List<Int>) {
            state.value = colors
        }
    }

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()
    private val recents = FakeRecents()
    private lateinit var vm: EditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = EditorViewModel(8, FakeStrings(), recents)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun tap(x: Int, y: Int) {
        vm.onDown(x, y)
        vm.onUp()
    }

    @Test
    fun `layers start with one named layer`() {
        assertEquals(listOf("Layer 1"), vm.state.value.layers.map { it.name })
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
        assertEquals(listOf(red, blue), vm.state.value.recentColors)
        assertEquals(listOf(red, blue), recents.state.value)
    }
}
