package dev.bastian.pixela.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.bastian.pixela.R
import kotlin.math.roundToInt

/** Hue (0..360), saturation, value and alpha (0..1) of a color. */
data class Hsva(val hue: Float, val saturation: Float, val value: Float, val alpha: Float) {
    fun toArgb(): Int = Color.hsv(hue, saturation, value, alpha).toArgb()

    companion object {
        fun from(argb: Int): Hsva {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, hsv)
            return Hsva(hsv[0], hsv[1], hsv[2], (argb ushr 24) / 255f)
        }
    }
}

/** A color picker built with Compose: gradient sliders for hue, saturation, value and alpha. */
@Composable
fun ColorPickerDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var hsva by remember { mutableStateOf(Hsva.from(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFBDBDBD))) {
                    Box(Modifier.fillMaxWidth().height(48.dp).background(Color(hsva.toArgb())))
                }
                GradientSlider(
                    label = stringResource(R.string.color_hue),
                    value = hsva.hue / 360f,
                    onValueChange = { hsva = hsva.copy(hue = it * 360f) },
                    brush = Brush.horizontalGradient(List(7) { Color.hsv(it * 60f, 1f, 1f) }),
                )
                GradientSlider(
                    label = stringResource(R.string.color_saturation),
                    value = hsva.saturation,
                    onValueChange = { hsva = hsva.copy(saturation = it) },
                    brush = Brush.horizontalGradient(
                        listOf(Color.hsv(hsva.hue, 0f, hsva.value), Color.hsv(hsva.hue, 1f, hsva.value)),
                    ),
                )
                GradientSlider(
                    label = stringResource(R.string.color_value),
                    value = hsva.value,
                    onValueChange = { hsva = hsva.copy(value = it) },
                    brush = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hsva.hue, hsva.saturation, 1f))),
                )
                GradientSlider(
                    label = stringResource(R.string.color_alpha),
                    value = hsva.alpha,
                    onValueChange = { hsva = hsva.copy(alpha = it) },
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.hsv(hsva.hue, hsva.saturation, hsva.value)),
                    ),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(hsva.toArgb()) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A horizontal slider whose track is painted with [brush]; tap or drag anywhere on it to set [value] (0..1). */
@Composable
private fun GradientSlider(label: String, value: Float, onValueChange: (Float) -> Unit, brush: Brush) {
    var width by remember { mutableFloatStateOf(1f) }
    val thumb = 28.dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }
    fun valueAt(x: Float) = ((x - thumbPx / 2f) / (width - thumbPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(
            Modifier
                .fillMaxWidth()
                .height(thumb)
                .semantics { contentDescription = label }
                .clip(RoundedCornerShape(14.dp))
                .background(brush)
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) { detectTapGestures { onValueChange(valueAt(it.x)) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onValueChange(valueAt(change.position.x))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset { IntOffset((value.coerceIn(0f, 1f) * (width - thumbPx)).roundToInt(), 0) }
                    .size(thumb)
                    .border(3.dp, Color.White, CircleShape)
                    .border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape),
            )
        }
    }
}
