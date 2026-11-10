package dev.bastian.pixela.domain

/** A mutable width x height grid of ARGB pixels stored row by row. */
class PixelBuffer(val width: Int, val height: Int, val pixels: IntArray = IntArray(width * height)) {
    init {
        require(width > 0 && height > 0) { "Size must be positive" }
        require(pixels.size == width * height) { "Pixel array does not match the size" }
    }

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun index(x: Int, y: Int): Int = y * width + x

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    operator fun set(x: Int, y: Int, color: Int) {
        pixels[y * width + x] = color
    }

    fun fill(color: Int) = pixels.fill(color)

    fun copy(): PixelBuffer = PixelBuffer(width, height, pixels.copyOf())

    fun copyFrom(other: PixelBuffer) {
        require(other.width == width && other.height == height) { "Size mismatch" }
        other.pixels.copyInto(pixels)
    }

    override fun equals(other: Any?): Boolean =
        other is PixelBuffer && width == other.width && height == other.height && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()
}
