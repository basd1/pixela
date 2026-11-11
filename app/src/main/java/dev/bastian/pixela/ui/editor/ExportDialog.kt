package dev.bastian.pixela.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.bastian.pixela.R
import dev.bastian.pixela.domain.ExportScales

/** Chooses the PNG scale, then either saves it to the gallery or shares it. */
@Composable
fun ExportDialog(canvasSize: Int, onSave: (Int) -> Unit, onShare: (Int) -> Unit, onDismiss: () -> Unit) {
    var scale by rememberSaveable { mutableIntStateOf(8) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (option in ExportScales) {
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = scale == option, role = Role.RadioButton) { scale = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = scale == option, onClick = null)
                        Text(
                            stringResource(R.string.export_scale_option, option, canvasSize * option, canvasSize * option),
                            Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onShare(scale) }) { Text(stringResource(R.string.export_share)) }
                TextButton(onClick = { onSave(scale) }) { Text(stringResource(R.string.export_save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
