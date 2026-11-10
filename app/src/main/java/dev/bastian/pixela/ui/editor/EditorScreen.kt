package dev.bastian.pixela.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.pixela.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun EditorScreen(
    size: Int,
    onBack: () -> Unit,
    viewModel: EditorViewModel = koinViewModel(parameters = { parametersOf(size) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val revision = viewModel.revision.collectAsState()
    val callbacks = remember(viewModel) {
        CanvasCallbacks(viewModel::onDown, viewModel::onMove, viewModel::onUp, viewModel::onCancel)
    }
    val layerActions = remember(viewModel) {
        LayerActions(
            onAdd = viewModel::addLayer,
            onSelect = viewModel::selectLayer,
            onToggleVisible = viewModel::toggleLayerVisible,
            onOpacity = viewModel::previewLayerOpacity,
            onOpacityDone = viewModel::commitLayerOpacity,
            onMove = viewModel::moveLayer,
            onRename = viewModel::renameLayer,
            onDuplicate = viewModel::duplicateLayer,
            onMergeDown = viewModel::mergeLayerDown,
            onDelete = viewModel::deleteLayer,
        )
    }
    var pickingColor by remember { mutableStateOf(false) }
    var showLayers by rememberSaveable { mutableStateOf(false) }
    EditorContent(
        state = state,
        onBack = onBack,
        viewModel = viewModel,
        onCustomColor = { pickingColor = true },
        onLayers = { showLayers = true },
    ) {
        PixelCanvas(
            state = viewModel.canvas,
            canvasSize = state.canvasSize,
            revision = revision,
            render = viewModel::render,
            callbacks = callbacks,
        )
    }
    if (pickingColor) {
        ColorPickerDialog(
            initial = state.color,
            onConfirm = {
                viewModel.selectColor(it)
                pickingColor = false
            },
            onDismiss = { pickingColor = false },
        )
    }
    if (showLayers) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { showLayers = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LayersPanel(state, layerActions, Modifier.navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    state: EditorUiState,
    onBack: () -> Unit,
    viewModel: EditorViewModel,
    onCustomColor: () -> Unit,
    onLayers: () -> Unit,
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
                    IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.action_undo))
                    }
                    IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.action_redo))
                    }
                    IconButton(onClick = onLayers) {
                        Icon(Icons.Filled.Layers, stringResource(R.string.layers_title))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                PaletteBar(state, onColor = viewModel::selectColor, onCustom = onCustomColor)
                ToolBar(
                    state = state,
                    onTool = viewModel::selectTool,
                    onMirror = viewModel::toggleMirror,
                    onFilled = viewModel::toggleFilledShapes,
                    onGrid = viewModel::toggleGrid,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { canvas() }
    }
}
