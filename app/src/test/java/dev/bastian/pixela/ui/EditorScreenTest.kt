package dev.bastian.pixela.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import dev.bastian.pixela.domain.RecentColorsStore
import dev.bastian.pixela.domain.TRANSPARENT
import dev.bastian.pixela.ui.editor.CANVAS_TAG
import dev.bastian.pixela.ui.editor.EditorScreen
import dev.bastian.pixela.ui.editor.EditorViewModel
import dev.bastian.pixela.ui.theme.PixelaTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private class FakeStrings : Strings {
        override fun layerName(number: Int) = "Layer $number"
        override fun newProjectName(number: Int) = "Drawing $number"
        override fun copyOf(name: String) = "$name copy"
    }

    private class FakeRecents : RecentColorsStore {
        private val state = MutableStateFlow<List<Int>>(emptyList())
        override val colors: Flow<List<Int>> = state
        override suspend fun save(colors: List<Int>) {
            state.value = colors
        }
    }

    private fun viewModel() = EditorViewModel(16, FakeStrings(), FakeRecents())

    private fun launch(vm: EditorViewModel) {
        compose.setContent { PixelaTheme { EditorScreen(size = 16, onBack = {}, viewModel = vm) } }
        compose.waitForIdle()
    }

    /** Centre of a canvas pixel, in the coordinates of the canvas node. */
    private fun EditorViewModel.centerOf(x: Int, y: Int): Offset {
        val viewport = canvas.viewport!!
        return Offset(viewport.pixelToScreenX(x) + viewport.scale / 2, viewport.pixelToScreenY(y) + viewport.scale / 2)
    }

    @Test
    fun `selecting the line tool and dragging draws a line on the canvas`() {
        val vm = viewModel()
        launch(vm)
        compose.onNodeWithContentDescription("Line").performClick()
        compose.waitForIdle()
        assertEquals(dev.bastian.pixela.domain.Tool.Line, vm.state.value.tool)

        val start = vm.centerOf(2, 3)
        val end = vm.centerOf(9, 3)
        compose.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(start)
            moveTo(end)
            up()
        }
        compose.waitForIdle()

        val pixels = vm.compositePixels()
        val filled = pixels.indices.filter { pixels[it] != TRANSPARENT }
        assertEquals((2..9).map { 3 * 16 + it }, filled)
        assertTrue(vm.state.value.canUndo)
    }

    @Test
    fun `undo button reverts the stroke and redo restores it`() {
        val vm = viewModel()
        launch(vm)
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        val point = vm.centerOf(5, 5)
        compose.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(point)
            up()
        }
        compose.waitForIdle()
        assertEquals(1, vm.compositePixels().count { it != TRANSPARENT })
        compose.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(0, vm.compositePixels().count { it != TRANSPARENT })
        compose.onNodeWithContentDescription("Redo").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, vm.compositePixels().count { it != TRANSPARENT })
    }

    @Test
    fun `a second finger cancels the stroke without leaving a mark`() {
        val vm = viewModel()
        launch(vm)
        val a = vm.centerOf(4, 4)
        val b = vm.centerOf(12, 12)
        compose.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(0, a)
            moveTo(0, vm.centerOf(6, 4))
            down(1, b)
            moveBy(1, Offset(5f, 5f))
            up(1)
            up(0)
        }
        compose.waitForIdle()
        assertEquals(0, vm.compositePixels().count { it != TRANSPARENT })
        assertEquals(false, vm.state.value.canUndo)
    }
}
