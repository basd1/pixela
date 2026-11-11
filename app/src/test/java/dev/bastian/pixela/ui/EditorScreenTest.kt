package dev.bastian.pixela.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.bastian.pixela.FakeExporter
import dev.bastian.pixela.FakeImporter
import dev.bastian.pixela.FakeProjectRepository
import dev.bastian.pixela.FakeRecents
import dev.bastian.pixela.FakeStrings
import dev.bastian.pixela.domain.TRANSPARENT
import dev.bastian.pixela.domain.Tool
import dev.bastian.pixela.ui.editor.CANVAS_TAG
import dev.bastian.pixela.ui.editor.EditorScreen
import dev.bastian.pixela.ui.editor.EditorViewModel
import dev.bastian.pixela.ui.editor.LAYERS_PANEL_TAG
import dev.bastian.pixela.ui.theme.PixelaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun viewModel() = EditorViewModel(
        projectId = "p1",
        strings = FakeStrings(),
        recents = FakeRecents(),
        projects = FakeProjectRepository(size = 16),
        exporter = FakeExporter(),
        importer = FakeImporter(Result.success(IntArray(256))),
        saveScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun launch(vm: EditorViewModel) {
        compose.setContent { PixelaTheme { EditorScreen(projectId = "p1", onBack = {}, viewModel = vm) } }
        compose.waitForIdle()
    }

    /** Centre of a canvas pixel, in the coordinates of the canvas node. */
    private fun EditorViewModel.centerOf(x: Int, y: Int): Offset {
        val viewport = canvas!!.viewport!!
        return Offset(viewport.pixelToScreenX(x) + viewport.scale / 2, viewport.pixelToScreenY(y) + viewport.scale / 2)
    }

    @Test
    fun `selecting the line tool and dragging draws a line on the canvas`() {
        val vm = viewModel()
        launch(vm)
        compose.onNodeWithContentDescription("Line").performClick()
        compose.waitForIdle()
        assertEquals(Tool.Line, vm.state.value.tool)

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

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `ctrl z undoes and ctrl shift z redoes from the keyboard`() {
        val vm = viewModel()
        launch(vm)
        val point = vm.centerOf(5, 5)
        compose.onNodeWithTag(CANVAS_TAG).performTouchInput {
            down(point)
            up()
        }
        compose.waitForIdle()
        assertEquals(1, vm.compositePixels().count { it != TRANSPARENT })

        compose.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        compose.waitForIdle()
        assertEquals(0, vm.compositePixels().count { it != TRANSPARENT })

        compose.onRoot().performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.Z)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        compose.waitForIdle()
        assertEquals(1, vm.compositePixels().count { it != TRANSPARENT })
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `a phone shows the layers panel only in the sheet`() {
        launch(viewModel())

        compose.onNodeWithTag(LAYERS_PANEL_TAG).assertDoesNotExist()
        compose.onNodeWithContentDescription("Layers").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(LAYERS_PANEL_TAG).assertExists()
    }

    @Test
    @Config(qualifiers = "w1000dp-h700dp")
    fun `a wide window keeps the layers panel and palette always visible`() {
        val vm = viewModel()
        launch(vm)

        compose.onNodeWithTag(LAYERS_PANEL_TAG).assertExists()
        compose.onNodeWithContentDescription("Layers").assertDoesNotExist()
        compose.onNodeWithContentDescription("Current color").assertExists()

        compose.onNodeWithContentDescription("Fill bucket").performClick()
        compose.waitForIdle()
        assertEquals(Tool.Fill, vm.state.value.tool)
    }
}
