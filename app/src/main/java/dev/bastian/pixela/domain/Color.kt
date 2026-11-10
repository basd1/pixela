package dev.bastian.pixela.domain

/** Helpers for non-premultiplied ARGB colors packed in an Int. 0 is fully transparent. */
const val TRANSPARENT = 0

fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    (a and 0xFF shl 24) or (r and 0xFF shl 16) or (g and 0xFF shl 8) or (b and 0xFF)

fun Int.alpha(): Int = this ushr 24 and 0xFF
fun Int.red(): Int = this ushr 16 and 0xFF
fun Int.green(): Int = this ushr 8 and 0xFF
fun Int.blue(): Int = this and 0xFF

/** Default 16-color palette (PICO-8 inspired). */
val DefaultPalette: List<Int> = listOf(
    0xFF000000, 0xFF1D2B53, 0xFF7E2553, 0xFF008751,
    0xFFAB5236, 0xFF5F574F, 0xFFC2C3C7, 0xFFFFF1E8,
    0xFFFF004D, 0xFFFFA300, 0xFFFFEC27, 0xFF00E436,
    0xFF29ADFF, 0xFF83769C, 0xFFFF77A8, 0xFFFFCCAA,
).map { it.toInt() }

/**
 * "Source over" blend of [src] (with an extra [opacity] factor in 0..1) onto [dst], non-premultiplied.
 */
fun blendOver(dst: Int, src: Int, opacity: Float = 1f): Int {
    val sa = src.alpha() * opacity.coerceIn(0f, 1f) / 255f
    if (sa <= 0f) return dst
    val da = dst.alpha() / 255f
    val outA = sa + da * (1f - sa)
    if (outA <= 0f) return TRANSPARENT
    fun mix(s: Int, d: Int): Int = Math.round((s * sa + d * da * (1f - sa)) / outA)
    return argb(
        Math.round(outA * 255f),
        mix(src.red(), dst.red()),
        mix(src.green(), dst.green()),
        mix(src.blue(), dst.blue()),
    )
}
