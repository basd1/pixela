package dev.bastian.pixela.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bastian.pixela.R
import kotlin.math.roundToInt

/** Everything the layer list can ask for; wired to the ViewModel by the editor. */
class LayerActions(
    val onAdd: () -> Unit,
    val onSelect: (Int) -> Unit,
    val onToggleVisible: (Int) -> Unit,
    val onOpacity: (Int, Float) -> Unit,
    val onOpacityDone: () -> Unit,
    val onMove: (Int, Boolean) -> Unit,
    val onRename: (Int, String) -> Unit,
    val onDuplicate: (Int) -> Unit,
    val onMergeDown: (Int) -> Unit,
    val onDelete: (Int) -> Unit,
)

const val LAYERS_PANEL_TAG = "layers_panel"

/** The layer stack, top layer first, with add, visibility, opacity, reorder and the overflow operations. */
@Composable
fun LayersPanel(state: EditorUiState, actions: LayerActions, modifier: Modifier = Modifier) {
    Column(modifier.testTag(LAYERS_PANEL_TAG)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.layers_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = actions.onAdd) { Icon(Icons.Filled.Add, stringResource(R.string.layer_add)) }
        }
        val topFirst = state.layers.asReversed()
        LazyColumn(Modifier.fillMaxWidth()) {
            items(topFirst, key = { it.id }) { layer ->
                val index = state.layers.indexOfFirst { it.id == layer.id }
                LayerRow(
                    layer = layer,
                    active = layer.id == state.activeLayerId,
                    canMoveUp = index < state.layers.lastIndex,
                    canMoveDown = index > 0,
                    canDelete = state.layers.size > 1,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: LayerItem,
    active: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    actions: LayerActions,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val background = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Column(
        Modifier
            .fillMaxWidth()
            .background(background)
            .clickable { actions.onSelect(layer.id) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.onToggleVisible(layer.id) }) {
                if (layer.visible) {
                    Icon(Icons.Filled.Visibility, stringResource(R.string.layer_hide, layer.name))
                } else {
                    Icon(Icons.Filled.VisibilityOff, stringResource(R.string.layer_show, layer.name))
                }
            }
            Text(layer.name, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = { actions.onMove(layer.id, true) }, enabled = canMoveUp) {
                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.layer_move_up, layer.name))
            }
            IconButton(onClick = { actions.onMove(layer.id, false) }, enabled = canMoveDown) {
                Icon(Icons.Filled.ArrowDownward, stringResource(R.string.layer_move_down, layer.name))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.layer_more, layer.name))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layer_rename)) },
                        onClick = {
                            menuOpen = false
                            renaming = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layer_duplicate)) },
                        onClick = {
                            menuOpen = false
                            actions.onDuplicate(layer.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layer_merge_down)) },
                        enabled = canMoveDown,
                        onClick = {
                            menuOpen = false
                            actions.onMergeDown(layer.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layer_delete)) },
                        enabled = canDelete,
                        onClick = {
                            menuOpen = false
                            actions.onDelete(layer.id)
                        },
                    )
                }
            }
        }
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.layer_opacity_percent, (layer.opacity * 100).roundToInt()),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = layer.opacity,
                onValueChange = { actions.onOpacity(layer.id, it) },
                onValueChangeFinished = actions.onOpacityDone,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
        }
    }
    if (renaming) RenameDialog(layer.name, onConfirm = {
        actions.onRename(layer.id, it)
        renaming = false
    }, onDismiss = { renaming = false })
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layer_rename)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
