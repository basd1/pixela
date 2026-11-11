package dev.bastian.pixela.ui.editor

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.pixela.R
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun EditorScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: EditorViewModel = koinViewModel(parameters = { parametersOf(projectId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // Autosave also runs on a timer; this makes sure nothing is lost when the editor is left or stopped.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.flush() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flush()
        }
    }

    var exporting by rememberSaveable { mutableStateOf(false) }
    var pendingSaveScale by remember { mutableIntStateOf(0) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Without the permission the export still runs and reports a visible failure.
        viewModel.export(pendingSaveScale, share = false)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.importImage(it.toString()) }
    }

    val savedMessage = stringResource(R.string.message_saved)
    val exportFailed = stringResource(R.string.message_export_failed)
    val importFailed = stringResource(R.string.message_import_failed)
    val shareTitle = stringResource(R.string.export_share)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Saved -> snackbar.showSnackbar(savedMessage)
                is EditorEvent.ReadyToShare -> {
                    val uri = Uri.parse(event.uri)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        // The clip data is what lets the chooser itself read the preview.
                        clipData = ClipData.newRawUri(null, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }
                EditorEvent.ExportFailed -> snackbar.showSnackbar(exportFailed)
                EditorEvent.ImportFailed -> snackbar.showSnackbar(importFailed)
            }
        }
    }

    val canvasState = viewModel.canvas
    when {
        state.loading -> CenteredBox { CircularProgressIndicator() }
        state.loadFailed || canvasState == null -> CenteredBox {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.message_load_failed))
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        }
        else -> EditorLoaded(
            state = state,
            viewModel = viewModel,
            canvasState = canvasState,
            snackbar = snackbar,
            onBack = onBack,
            onExport = { exporting = true },
            onImport = { imagePicker.launch("image/*") },
        )
    }

    if (exporting) {
        ExportDialog(
            canvasSize = state.canvasSize,
            onSave = { scale ->
                exporting = false
                val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    pendingSaveScale = scale
                    storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    viewModel.export(scale, share = false)
                }
            },
            onShare = { scale ->
                exporting = false
                viewModel.export(scale, share = true)
            },
            onDismiss = { exporting = false },
        )
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorLoaded(
    state: EditorUiState,
    viewModel: EditorViewModel,
    canvasState: CanvasState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
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
    var pickingColor by rememberSaveable { mutableStateOf(false) }
    var showLayers by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }

    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_BREAKPOINT_DP
    Scaffold(
        modifier = Modifier
            .onPreviewKeyEvent { handleShortcut(it, viewModel::undo, viewModel::redo) }
            .focusRequester(focus)
            .focusable(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.projectName, maxLines = 1) },
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
                    if (!wide) {
                        IconButton(onClick = { showLayers = true }) {
                            Icon(Icons.Filled.Layers, stringResource(R.string.layers_title))
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_export)) }, onClick = {
                                menuOpen = false
                                onExport()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_import)) }, onClick = {
                                menuOpen = false
                                onImport()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.action_reset_view)) }, onClick = {
                                menuOpen = false
                                canvasState.resetView()
                            })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (!wide) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    PaletteBar(state, onColor = viewModel::selectColor, onCustom = { pickingColor = true })
                    ToolBar(
                        state = state,
                        onTool = viewModel::selectTool,
                        onMirror = viewModel::toggleMirror,
                        onFilled = viewModel::toggleFilledShapes,
                        onGrid = viewModel::toggleGrid,
                    )
                }
            }
        },
    ) { padding ->
        val canvas: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier) {
                PixelCanvas(
                    state = canvasState,
                    canvasSize = state.canvasSize,
                    revision = revision,
                    render = viewModel::render,
                    callbacks = callbacks,
                )
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                ToolRail(
                    state = state,
                    onTool = viewModel::selectTool,
                    onMirror = viewModel::toggleMirror,
                    onFilled = viewModel::toggleFilledShapes,
                    onGrid = viewModel::toggleGrid,
                    modifier = Modifier.padding(8.dp),
                )
                canvas(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(SIDE_PANEL_WIDTH).fillMaxHeight().padding(vertical = 8.dp)) {
                    PalettePanel(
                        state,
                        onColor = viewModel::selectColor,
                        onCustom = { pickingColor = true },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    LayersPanel(state, layerActions, Modifier.weight(1f))
                }
            }
        } else {
            canvas(Modifier.fillMaxSize().padding(padding))
        }
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
    if (showLayers && !wide) {
        ModalBottomSheet(
            onDismissRequest = { showLayers = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LayersPanel(state, layerActions, Modifier.navigationBarsPadding())
        }
    }
}

/** Windows at least this wide (dp) get the side panels instead of the bottom bar and the layers sheet. */
private const val WIDE_BREAKPOINT_DP = 840
private val SIDE_PANEL_WIDTH = 300.dp

/** Ctrl or Cmd + Z undoes, with Shift or Ctrl/Cmd + Y it redoes. Returns whether the key was used. */
internal fun handleShortcut(event: KeyEvent, undo: () -> Unit, redo: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown || !(event.isCtrlPressed || event.isMetaPressed)) return false
    return when (event.key) {
        Key.Z -> {
            if (event.isShiftPressed) redo() else undo()
            true
        }
        Key.Y -> {
            redo()
            true
        }
        else -> false
    }
}
