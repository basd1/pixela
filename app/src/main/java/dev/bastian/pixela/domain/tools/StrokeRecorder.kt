package dev.bastian.pixela.domain.tools

import dev.bastian.pixela.domain.PixelBuffer
import dev.bastian.pixela.domain.history.PixelChange

/**
 * Writes pixels into a layer while remembering the first value of every touched pixel, so a whole
 * stroke becomes a single [PixelChange] and a shape preview can be undone with [reset].
 */
class StrokeRecorder(private val layerId: Int, private val buffer: PixelBuffer) {
    private val before = IntArray(buffer.pixels.size)
    private val touched = BooleanArray(buffer.pixels.size)
    private var touchedIndices = IntArray(64)
    private var touchedCount = 0

    /** Sets a pixel; coordinates outside the buffer are ignored. Returns true when the value changed. */
    fun set(x: Int, y: Int, color: Int): Boolean {
        if (!buffer.inBounds(x, y)) return false
        return setIndex(buffer.index(x, y), color)
    }

    fun setIndex(index: Int, color: Int): Boolean {
        if (!touched[index]) {
            touched[index] = true
            before[index] = buffer.pixels[index]
            if (touchedCount == touchedIndices.size) touchedIndices = touchedIndices.copyOf(touchedCount * 2)
            touchedIndices[touchedCount++] = index
        }
        if (buffer.pixels[index] == color) return false
        buffer.pixels[index] = color
        return true
    }

    /** Restores every touched pixel and forgets them. */
    fun reset() {
        for (n in 0 until touchedCount) {
            val i = touchedIndices[n]
            buffer.pixels[i] = before[i]
            touched[i] = false
        }
        touchedCount = 0
    }

    /** The accumulated change, or null if no pixel ended up different from where it started. */
    fun finish(): PixelChange? {
        var changed = 0
        for (n in 0 until touchedCount) {
            val i = touchedIndices[n]
            if (buffer.pixels[i] != before[i]) changed++
        }
        if (changed == 0) return null
        val idx = IntArray(changed)
        val b = IntArray(changed)
        val a = IntArray(changed)
        var k = 0
        for (n in 0 until touchedCount) {
            val i = touchedIndices[n]
            if (buffer.pixels[i] != before[i]) {
                idx[k] = i
                b[k] = before[i]
                a[k] = buffer.pixels[i]
                k++
            }
        }
        return PixelChange(layerId, idx, b, a)
    }
}
