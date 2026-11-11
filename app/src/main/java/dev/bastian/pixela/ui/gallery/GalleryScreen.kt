package dev.bastian.pixela.ui.gallery

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bastian.pixela.R
import dev.bastian.pixela.domain.ProjectSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onOpen: (String) -> Unit, viewModel: GalleryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.gallery_new)) },
            )
        },
    ) { padding ->
        if (state.loaded && state.projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.gallery_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { onOpen(project.id) },
                        onRename = { renaming = project },
                        onDuplicate = { viewModel.duplicate(project) },
                        onDelete = { deleting = project },
                    )
                }
            }
        }
    }

    if (creating) {
        NewProjectDialog(
            onCreate = { name, size ->
                creating = false
                viewModel.create(name, size, onOpen)
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { project ->
        NameDialog(
            title = stringResource(R.string.dialog_rename_title),
            initial = project.name,
            onConfirm = {
                viewModel.rename(project.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.dialog_delete_title)) },
            text = { Text(stringResource(R.string.dialog_delete_message, project.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(project.id)
                    deleting = null
                }) { Text(stringResource(R.string.layer_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column {
            Thumbnail(project, Modifier.fillMaxWidth().aspectRatio(1f))
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(project.name, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.size_label, project.size, project.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.project_more, project.name))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.layer_rename)) }, onClick = {
                            menuOpen = false
                            onRename()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.layer_duplicate)) }, onClick = {
                            menuOpen = false
                            onDuplicate()
                        })
                        DropdownMenuItem(text = { Text(stringResource(R.string.layer_delete)) }, onClick = {
                            menuOpen = false
                            onDelete()
                        })
                    }
                }
            }
        }
    }
}

/** The saved PNG, scaled with nearest neighbor over a gray backdrop; reloaded whenever the project changes. */
@Composable
private fun Thumbnail(project: ProjectSummary, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(null, project.thumbnailPath, project.modifiedAt) {
        value = withContext(Dispatchers.IO) {
            File(project.thumbnailPath).takeIf { it.exists() }
                ?.let { BitmapFactory.decodeFile(it.path) }?.asImageBitmap()
        }
    }
    Canvas(modifier.clip(MaterialTheme.shapes.medium).background(Color(0xFFE3E3E3))) {
        image?.let {
            drawImage(
                it,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

@Composable
private fun NewProjectDialog(onCreate: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var size by rememberSaveable { mutableIntStateOf(32) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.field_size), style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    for (option in listOf(16, 32, 64)) {
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = size == option, role = Role.RadioButton) { size = option },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = size == option, onClick = null)
                            Text(stringResource(R.string.size_label, option, option), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, size) }) { Text(stringResource(R.string.action_create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.field_name)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
