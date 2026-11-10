package dev.bastian.pixela.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

const val CANVAS_TAG = "pixel_canvas"

/** Callbacks with canvas pixel coordinates; they come from the pure [dev.bastian.pixela.domain.Viewport] mapping. */
class CanvasCallbacks(
    val onDown: (Int, Int) -> Unit,
    val onMove: (Int, Int) -> Unit,
    val onUp: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * The drawing surface. The composition is rendered into one [Bitmap] (one pixel per canvas pixel)
 * that is drawn scaled with [FilterQuality.None]; nothing here draws one rectangle per pixel.
 * Revision, zoom and pan are read in the draw phase only, so strokes and gestures redraw this
 * canvas without recomposing it or the rest of the editor.
 */
@Composable
fun PixelCanvas(
    state: CanvasState,
    canvasSize: Int,
    revision: State<Int>,
    render: (Bitmap) -> Unit,
    callbacks: CanvasCallbacks,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(canvasSize) { Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val dark = isSystemInDarkTheme()
    val checker = remember(canvasSize, dark) { checkerImage(canvasSize, dark) }
    val renderedRevision = remember(bitmap) { intArrayOf(-1) }
    val gridColor = if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.18f)

    Canvas(
        modifier
            .fillMaxSize()
            .testTag(CANVAS_TAG)
            .onSizeChanged { state.onViewSize(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(state, callbacks) { handleGestures(state, callbacks) },
    ) {
        val viewport = state.viewport ?: return@Canvas
        val current = revision.value
        if (renderedRevision[0] != current) {
            render(bitmap)
            renderedRevision[0] = current
        }
        withTransform({
            translate(viewport.offsetX, viewport.offsetY)
            scale(viewport.scale, viewport.scale, pivot = Offset.Zero)
        }) {
            val size = IntSize(canvasSize, canvasSize)
            drawImage(checker, dstSize = size, filterQuality = FilterQuality.None)
            drawImage(image, dstSize = size, filterQuality = FilterQuality.None)
        }
        if (state.showGrid && viewport.scale >= GRID_MIN_SCALE) {
            val end = canvasSize * viewport.scale
            for (i in 0..canvasSize) {
                val p = i * viewport.scale
                drawLine(gridColor, Offset(viewport.offsetX + p, viewport.offsetY), Offset(viewport.offsetX + p, viewport.offsetY + end))
                drawLine(gridColor, Offset(viewport.offsetX, viewport.offsetY + p), Offset(viewport.offsetX + end, viewport.offsetY + p))
            }
        }
    }
}

private const val GRID_MIN_SCALE = 10f

/** A canvasSize x canvasSize bitmap with alternating light and dark pixels, scaled up it becomes the transparency checkerboard. */
private fun checkerImage(size: Int, dark: Boolean) = run {
    val light = if (dark) 0xFF3A3A3A.toInt() else 0xFFFFFFFF.toInt()
    val shade = if (dark) 0xFF2A2A2A.toInt() else 0xFFE3E3E3.toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(size * size) { if ((it % size + it / size) % 2 == 0) light else shade }
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    bmp.asImageBitmap()
}

/**
 * One finger draws; a second finger cancels the stroke without leaving a mark and the two fingers
 * then zoom and pan until every finger is lifted.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.handleGestures(
    state: CanvasState,
    callbacks: CanvasCallbacks,
) {
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        var drawing = false
        var transforming = false
        state.viewport?.screenToPixel(first.position.x, first.position.y)?.let {
            callbacks.onDown(it.x, it.y)
            drawing = true
        }
        first.consume()
        do {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                if (!transforming) {
                    transforming = true
                    if (drawing) {
                        callbacks.onCancel()
                        drawing = false
                    }
                }
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid()
                if (centroid != Offset.Unspecified) state.transform(zoom, centroid.x, centroid.y, pan.x, pan.y)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            } else if (!transforming && drawing) {
                val change = event.changes.firstOrNull { it.pressed }
                if (change != null && change.positionChanged()) {
                    state.viewport?.screenToPixel(change.position.x, change.position.y)?.let { callbacks.onMove(it.x, it.y) }
                    change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
        if (drawing) callbacks.onUp()
    }
}

