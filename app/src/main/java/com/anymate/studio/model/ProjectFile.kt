package com.anymate.studio.model

import kotlinx.serialization.Serializable

/**
 * On-disk project format. It is intentionally human-inspectable: every frame
 * keeps its layer stack and every drawing keeps its parameters, so a project
 * can be versioned, diffed or shared as a single JSON file.
 */
@Serializable
data class ProjectFile(
    val format: Int = 1,
    val name: String = "Untitled",
    val width: Int = 900,
    val height: Int = 900,
    val fps: Int = 24,
    val background: Long = 0xFFF7F4EC,
    val currentFrame: Int = 0,
    val layers: List<LayerFile> = emptyList(),
    val frames: List<FrameFile> = emptyList()
)

@Serializable
data class LayerFile(
    val name: String = "Layer",
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f
)

@Serializable
data class FrameFile(
    val layers: List<List<ElementFile>> = emptyList()
)

@Serializable
sealed class ElementFile {

    @Serializable
    data class Stroke(
        val points: List<List<Float>> = emptyList(),
        val color: Long = 0xFF000000,
        val width: Float = 4f,
        val opacity: Float = 1f,
        val tool: String = "PENCIL"
    ) : ElementFile()

    @Serializable
    data class Shape(
        val kind: String = "ELLIPSE",
        val x1: Float = 0f,
        val y1: Float = 0f,
        val x2: Float = 0f,
        val y2: Float = 0f,
        val stroke: Long = 0xFF000000,
        val fill: Long? = null,
        val width: Float = 4f,
        val opacity: Float = 1f
    ) : ElementFile()

    @Serializable
    data class Poly(
        val points: List<List<Float>> = emptyList(),
        val closed: Boolean = true,
        val stroke: Long = 0xFF000000,
        val fill: Long? = null,
        val width: Float = 3f,
        val opacity: Float = 1f
    ) : ElementFile()

    @Serializable
    data class Text(
        val text: String = "",
        val x: Float = 0f,
        val y: Float = 0f,
        val size: Float = 64f,
        val color: Long = 0xFF000000,
        val opacity: Float = 1f,
        val bold: Boolean = true
    ) : ElementFile()

    @Serializable
    data class Raster(
        val x: Int = 0,
        val y: Int = 0,
        val png: String = "",
        val opacity: Float = 1f
    ) : ElementFile()
}
