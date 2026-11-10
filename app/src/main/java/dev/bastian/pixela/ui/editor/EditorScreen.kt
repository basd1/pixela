package dev.bastian.pixela.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.pixela.R
import dev.bastian.pixela.domain.Tool
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun EditorScreen(size: Int, onBack: () -> Unit) {
    val viewModel: EditorViewModel = koinViewModel(parameters = { parametersOf(size) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val revision = viewModel.revision.collectAsState()
    val callbacks = remember(viewModel) {
        CanvasCallbacks(viewModel::onDown, viewModel::onMove, viewModel::onUp, viewModel::onCancel)
    }
    EditorContent(
        state = state,
        onBack = onBack,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onTool = viewModel::selectTool,
    ) {
        PixelCanvas(
            state = viewModel.canvas,
            canvasSize = state.canvasSize,
            revision = revision,
            render = viewModel::render,
            callbacks = callbacks,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    state: EditorUiState,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTool: (Tool) -> Unit,
    canvas: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onUndo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.action_undo))
                    }
                    IconButton(onClick = onRedo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.action_redo))
                    }
                },
            )
        },
        bottomBar = {
            ToolBar(state.tool, onTool)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { canvas() }
    }
}

private class ToolEntry(val tool: Tool, val icon: ImageVector, val label: Int)

private val tools = listOf(
    ToolEntry(Tool.Pencil, Icons.Filled.Edit, R.string.tool_pencil),
    ToolEntry(Tool.Eraser, Icons.Filled.AutoFixNormal, R.string.tool_eraser),
)

@Composable
private fun ToolBar(selected: Tool, onTool: (Tool) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            for (entry in tools) {
                FilledTonalIconToggleButton(checked = selected == entry.tool, onCheckedChange = { onTool(entry.tool) }) {
                    Icon(entry.icon, stringResource(entry.label))
                }
            }
        }
    }
}
