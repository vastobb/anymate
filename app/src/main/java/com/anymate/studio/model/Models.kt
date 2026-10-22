package com.anymate.studio.model

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Everything the user (or an agent) can paint with. */
enum class ToolType(val label: String, val hint: String) {
    PENCIL("Pencil", "Crisp, textured line. Great for roughs and notes."),
    PEN("Ink Pen", "Smooth pressure-shaped ink. Great for clean line art."),
    BRUSH("Brush", "Soft, expressive paint for colour and texture."),
    ERASER("Eraser", "Removes paint from the active layer."),
    FILL("Fill", "Flood-fills an enclosed area with the active colour."),
    SHAPE("Shape", "Draw lines, rectangles, ellipses and arrows."),
    TEXT("Text", "Tap the canvas to place type."),
    EYEDROPPER("Picker", "Sample a colour from the canvas."),
    HAND("Hand", "Pan the canvas. Pinch with two fingers to zoom.")
}

enum class ShapeKind(val label: String) {
    LINE("Line"), RECT("Rectangle"), ELLIPSE("Ellipse"), ARROW("Arrow")
}

/** Project-space point. Pressure is 0..1, used to taper ink. */
data class Vec2(val x: Float, val y: Float, val pressure: Float = 1f)

sealed interface Element {
    val id: Long
}

data class StrokeElement(
    override val id: Long,
    val points: List<Vec2>,
    val color: Long,
    val width: Float,
    val opacity: Float = 1f,
    val tool: ToolType = ToolType.PENCIL
) : Element

data class ShapeElement(
    override val id: Long,
    val kind: ShapeKind,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val strokeColor: Long,
    val fillColor: Long? = null,
    val width: Float = 4f,
    val opacity: Float = 1f
) : Element

data class PolygonElement(
    override val id: Long,
    val points: List<Vec2>,
    val closed: Boolean = true,
    val strokeColor: Long,
    val fillColor: Long? = null,
    val width: Float = 3f,
    val opacity: Float = 1f
) : Element

data class TextElement(
    override val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val size: Float,
    val color: Long,
    val opacity: Float = 1f,
    val bold: Boolean = true
) : Element

/**
 * A cropped raster patch. Used by flood fill, imported art and any operation
 * that is fundamentally pixel based. The bitmap is immutable by convention.
 */
data class RasterElement(
    override val id: Long,
    val bitmap: Bitmap,
    val x: Int,
    val y: Int,
    val opacity: Float = 1f
) : Element

class FrameLayer {
    val elements = mutableStateListOf<Element>()

    fun snapshot(): List<Element> = elements.toList()
}

class Frame(val id: Long, layerCount: Int) {
    val layers = mutableStateListOf<FrameLayer>()
    var revision by mutableIntStateOf(0)

    init {
        repeat(layerCount) { layers.add(FrameLayer()) }
    }

    fun bump() {
        revision++
    }

    fun deepCopy(newId: Long): Frame {
        val copy = Frame(newId, 0)
        layers.forEach { layer ->
            val copied = FrameLayer()
            copied.elements.addAll(layer.elements)
            copy.layers.add(copied)
        }
        copy.revision = revision
        return copy
    }
}

class LayerMeta(id: Long, name: String) {
    val id: Long = id
    var name by mutableStateOf(name)
    var visible by mutableStateOf(true)
    var locked by mutableStateOf(false)
    var opacity by mutableFloatStateOf(1f)
}

/** Curated, art-directed starting palette. Full HSV picking is also available. */
val PremiumSwatches: List<Long> = listOf(
    0xFF0C0B0F, 0xFF2B2733, 0xFF6B6478, 0xFFA8A1B3, 0xFFF7F4EC, 0xFFFFFFFF,
    0xFF7C3AED, 0xFFA78BFA, 0xFF6366F1, 0xFF3B82F6, 0xFF22D3EE, 0xFF2DD4BF,
    0xFF34D399, 0xFFA3E635, 0xFFFACC15, 0xFFFB923C, 0xFFF97316, 0xFFEF4444,
    0xFFE11D48, 0xFFEC4899, 0xFFF472B6, 0xFF8B5E3C, 0xFFD6B48C, 0xFFFDE68A
)

val DefaultFpsChoices = listOf(12, 24, 30, 60)

data class CanvasPreset(val name: String, val width: Int, val height: Int) {
    val label: String get() = "$width × $height"
}

val CanvasPresets = listOf(
    CanvasPreset("Square", 900, 900),
    CanvasPreset("Portrait", 900, 1600),
    CanvasPreset("Landscape", 1600, 900),
    CanvasPreset("Story", 900, 1600)
)
