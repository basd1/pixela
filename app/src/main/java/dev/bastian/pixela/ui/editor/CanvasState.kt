package dev.bastian.pixela.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.bastian.pixela.domain.Viewport

/**
 * Zoom and pan of the canvas. It lives in the ViewModel (so rotation keeps it) and is read only from
 * the canvas draw phase and gesture code: moving it never recomposes the rest of the editor.
 */
class CanvasState(private val canvasWidth: Int, private val canvasHeight: Int) {
    var viewport: Viewport? by mutableStateOf(null)
        private set
    var showGrid: Boolean by mutableStateOf(true)

    private var viewWidth = 0f
    private var viewHeight = 0f
    private var userAdjusted = false

    /** Called when the canvas view is measured; refits the canvas unless the user already zoomed or panned. */
    fun onViewSize(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        val first = viewport == null
        viewWidth = width
        viewHeight = height
        if (first || !userAdjusted) {
            viewport = Viewport.fit(width, height, canvasWidth, canvasHeight)
        } else {
            viewport = viewport?.clamped(width, height, canvasWidth, canvasHeight)
        }
    }

    /** Applies one multi-touch step: zoom by [zoom] around ([focusX], [focusY]) and move by the pan amount. */
    fun transform(zoom: Float, focusX: Float, focusY: Float, panX: Float, panY: Float) {
        val current = viewport ?: return
        userAdjusted = true
        viewport = current.zoomed(zoom, focusX, focusY).panned(panX, panY)
            .clamped(viewWidth, viewHeight, canvasWidth, canvasHeight)
    }

    fun resetView() {
        userAdjusted = false
        if (viewWidth > 0f) viewport = Viewport.fit(viewWidth, viewHeight, canvasWidth, canvasHeight)
    }
}
