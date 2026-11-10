package dev.bastian.pixela.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Square
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bastian.pixela.R
import dev.bastian.pixela.domain.Tool

private class ToolEntry(val tool: Tool, val icon: ImageVector, val label: Int)

private val toolEntries = listOf(
    ToolEntry(Tool.Pencil, Icons.Filled.Edit, R.string.tool_pencil),
    ToolEntry(Tool.Eraser, Icons.Filled.AutoFixNormal, R.string.tool_eraser),
    ToolEntry(Tool.Fill, Icons.Filled.FormatColorFill, R.string.tool_fill),
    ToolEntry(Tool.Picker, Icons.Filled.Colorize, R.string.tool_picker),
    ToolEntry(Tool.Line, Icons.Filled.Straighten, R.string.tool_line),
    ToolEntry(Tool.Rectangle, Icons.Filled.CropSquare, R.string.tool_rectangle),
    ToolEntry(Tool.Move, Icons.Filled.OpenWith, R.string.tool_move),
)

/** Tool buttons plus the mirror, filled-shape and grid toggles. */
@Composable
fun ToolBar(
    state: EditorUiState,
    onTool: (Tool) -> Unit,
    onMirror: () -> Unit,
    onFilled: () -> Unit,
    onGrid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (entry in toolEntries) {
            FilledTonalIconToggleButton(checked = state.tool == entry.tool, onCheckedChange = { onTool(entry.tool) }) {
                Icon(entry.icon, stringResource(entry.label))
            }
        }
        FilledTonalIconToggleButton(checked = state.mirror, onCheckedChange = { onMirror() }) {
            Icon(Icons.Filled.Flip, stringResource(R.string.toggle_mirror))
        }
        FilledTonalIconToggleButton(checked = state.filledShapes, onCheckedChange = { onFilled() }) {
            Icon(Icons.Filled.Square, stringResource(R.string.toggle_filled))
        }
        FilledTonalIconToggleButton(checked = state.showGrid, onCheckedChange = { onGrid() }) {
            Icon(Icons.Filled.GridOn, stringResource(R.string.toggle_grid))
        }
    }
}

/** The current color, a custom color button, recent colors and the palette in one scrolling row. */
@Composable
fun PaletteBar(
    state: EditorUiState,
    onColor: (Int) -> Unit,
    onCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Swatch(state.color, selected = true, description = stringResource(R.string.color_current), onClick = onCustom)
        IconButton(onClick = onCustom) {
            Icon(Icons.Filled.Palette, stringResource(R.string.color_custom))
        }
        for (color in state.recentColors) {
            Swatch(color, selected = false, description = stringResource(R.string.color_recent), onClick = { onColor(color) })
        }
        for (color in state.palette) {
            Swatch(color, selected = color == state.color, description = stringResource(R.string.color_palette), onClick = { onColor(color) })
        }
    }
}

@Composable
private fun Swatch(color: Int, selected: Boolean, description: String, onClick: () -> Unit) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFBDBDBD))
            .background(Color(color))
            .border(BorderStroke(if (selected) 3.dp else 1.dp, outline), RoundedCornerShape(6.dp))
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
    ) {}
}
