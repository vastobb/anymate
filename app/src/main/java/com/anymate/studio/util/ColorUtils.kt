package com.anymate.studio.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs

fun Long.toComposeColor(): Color = Color(this.toInt())

fun Color.toArgbLong(): Long = this.toArgb().toLong() and 0xFFFFFFFFL

fun Long.withAlpha(alpha: Float): Long {
    val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    return (this and 0x00FFFFFFL) or (a.toLong() shl 24)
}

fun Long.alphaFraction(): Float = ((this shr 24) and 0xFF).toFloat() / 255f

fun Long.rgbOnly(): Long = this and 0x00FFFFFFL

fun Long.isLight(): Boolean {
    val r = ((this shr 16) and 0xFF) / 255f
    val g = ((this shr 8) and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    return (0.2126f * r + 0.7152f * g + 0.0722f * b) > 0.62f
}

fun Long.contrastOn(): Long = if (isLight()) 0xFF111014 else 0xFFF7F5FF

fun Long.toHex(includeAlpha: Boolean = false): String {
    val a = (this shr 24) and 0xFF
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    return if (includeAlpha) String.format("#%02X%02X%02X%02X", a, r, g, b)
    else String.format("#%02X%02X%02X", r, g, b)
}

fun parseHex(input: String, fallback: Long = 0xFF000000): Long {
    var s = input.trim().removePrefix("#")
    if (s.length == 3) {
        s = s.map { "$it$it" }.joinToString("")
    }
    val value = s.toLongOrNull(16) ?: return fallback
    return when (s.length) {
        6 -> 0xFF000000L or value
        8 -> value
        else -> fallback
    }
}

fun hsvColor(h: Float, s: Float, v: Float, alpha: Float = 1f): Long {
    val argb = android.graphics.Color.HSVToColor(
        (alpha.coerceIn(0f, 1f) * 255).toInt(),
        floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
    )
    return argb.toLong() and 0xFFFFFFFFL
}

fun Long.toHsv(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toInt(), hsv)
    return hsv
}

/** Perceptual-ish distance used by the colour picker's recents ordering. */
fun Long.distanceTo(other: Long): Float {
    val r1 = (this shr 16) and 0xFF
    val g1 = (this shr 8) and 0xFF
    val b1 = this and 0xFF
    val r2 = (other shr 16) and 0xFF
    val g2 = (other shr 8) and 0xFF
    val b2 = other and 0xFF
    return abs(r1 - r2) + abs(g1 - g2) + abs(b1 - b2)
}

fun lerpColor(a: Long, b: Long, fraction: Float): Long {
    val f = fraction.coerceIn(0f, 1f)
    fun channel(shift: Int): Long {
        val ca = (a shr shift) and 0xFF
        val cb = (b shr shift) and 0xFF
        return (ca + (cb - ca) * f).toLong().coerceIn(0, 255)
    }
    return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

fun rotateHue(color: Long, degrees: Float): Long {
    val hsv = color.toHsv()
    hsv[0] = ((hsv[0] + degrees) % 360f + 360f) % 360f
    return hsvColor(hsv[0], hsv[1], hsv[2], color.alphaFraction())
}
