package com.anymate.studio.mcp

import android.graphics.Bitmap
import com.anymate.studio.model.PolygonElement
import com.anymate.studio.model.ShapeElement
import com.anymate.studio.model.ShapeKind
import com.anymate.studio.model.StrokeElement
import com.anymate.studio.model.TextElement
import com.anymate.studio.model.ToolType
import com.anymate.studio.model.Vec2
import com.anymate.studio.util.toHex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * Animation-first MCP tools: motion paths, on-ones, frame surgery, grids and
 * contact sheets — the things a real animator needs an agent to be able to do.
 */
internal fun ToolRegistry.registerAnimationTools() {

    tool(
        "anymate_draw_curve",
        "Draw a smooth Catmull-Rom curve through a list of points. Use it for motion paths, tails, ropes and handwriting. Fewer control points = smoother, wider arcs.",
        schema(
            "points" to pointsArray("Control points (3+ recommended)"),
            "color" to str("Stroke colour hex"),
            "width" to number("Stroke width px", 0.3, 200.0),
            "opacity" to number("0..1", 0.0, 1.0),
            "tool" to enumStr("Stroke tool", listOf("pen", "pencil", "brush")),
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "layer" to int("Layer index; default current", 0.0, 100.0),
            required = listOf("points")
        )
    ) { args ->
        val control = args.points()
        require(control.size >= 2) { "A curve needs at least two points." }
        val samples = sampleSpline(control, (control.size * 12).coerceIn(24, 480))
        val strokeTool = when (args.string("tool")?.lowercase() ?: "pen") {
            "pencil" -> ToolType.PENCIL
            "brush" -> ToolType.BRUSH
            else -> ToolType.PEN
        }
        engine.commitStroke(
            points = samples,
            tool = strokeTool,
            color = args.color("color") ?: engine.strokeColor,
            width = args.float("width") ?: engine.brushSize,
            opacity = args.float("opacity") ?: engine.brushOpacity,
            frameIndex = args.int("frame") ?: -1,
            layerIndex = args.int("layer") ?: -1,
            select = true
        )
        ToolResult("Drew a smooth curve through ${control.size} points (${samples.size} samples).")
    }

    tool(
        "anymate_motion_path",
        "Animate a shape along a path across a run of frames. The path is smoothed with Catmull-Rom so 4-6 points give a natural arc. Creates the frames for you.",
        schema(
            "path" to pointsArray("Path control points"),
            "frames" to int("How many frames to create", 2.0, 400.0),
            "shape" to enumStr("Shape to move", listOf("ellipse", "rect", "line")),
            "size" to number("Shape size in px", 2.0, 400.0),
            "color" to str("Fill colour hex"),
            "stroke" to str("Outline colour hex"),
            "easing" to enumStr("Timing", listOf("linear", "ease", "smooth", "bounce")),
            "start_frame" to int("Where to start; default after the last frame", 0.0, 2000.0),
            "layer" to int("Layer index; default current", 0.0, 100.0),
            required = listOf("path")
        )
    ) { args ->
        val control = args.points()
        require(control.size >= 2) { "A path needs at least two points." }
        val count = (args.int("frames") ?: 24).coerceIn(2, 400)
        val shape = args.string("shape")?.lowercase() ?: "ellipse"
        val size = (args.float("size") ?: 40f).coerceIn(2f, 400f)
        val color = args.color("color") ?: engine.strokeColor
        val stroke = args.color("stroke") ?: color
        val easing = args.string("easing")?.lowercase() ?: "smooth"
        val layer = args.int("layer") ?: engine.currentLayer
        val startFrame = (args.int("start_frame") ?: engine.frames.size).coerceAtMost(engine.frames.size)

        engine.beginUndoGroup("Agent motion path")
        try {
            for (i in 0 until count) {
                val t = applyEasing(i.toFloat() / (count - 1).coerceAtLeast(1), easing)
                val point = sampleSplineAt(control, t)
                val frameIndex: Int
                if (i == 0 && startFrame < engine.frames.size) {
                    frameIndex = startFrame
                } else {
                    engine.addFrame(index = startFrame + i, select = false)
                    frameIndex = engine.frames.lastIndex
                }
                val half = size / 2f
                val kind = when (shape) {
                    "rect" -> ShapeKind.RECT
                    "line" -> ShapeKind.LINE
                    else -> ShapeKind.ELLIPSE
                }
                engine.addShape(
                    kind = kind,
                    start = if (kind == ShapeKind.LINE) Vec2(point.x - half, point.y)
                    else Vec2(point.x - half, point.y - half),
                    end = if (kind == ShapeKind.LINE) Vec2(point.x + half, point.y)
                    else Vec2(point.x + half, point.y + half),
                    filled = kind != ShapeKind.LINE,
                    color = if (kind == ShapeKind.LINE) stroke else color,
                    width = 2f,
                    opacity = 1f,
                    frameIndex = frameIndex,
                    layerIndex = layer,
                    select = true
                )
            }
        } finally {
            engine.endUndoGroup()
        }
        ToolResult("Animated a $shape along ${control.size} path points across $count frames ($easing).")
    }

    tool(
        "anymate_offset_frame",
        "Shift every drawing on a frame by a number of pixels. Great for building on-ones/twos or nudging a pose.",
        schema(
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "dx" to number("Horizontal offset px"),
            "dy" to number("Vertical offset px"),
            required = listOf("dx", "dy")
        )
    ) { args ->
        val moved = engine.offsetFrame(
            args.int("frame") ?: -1,
            args.requireFloat("dx"),
            args.requireFloat("dy")
        )
        ToolResult("Shifted $moved elements on frame ${engine.currentIndex}.")
    }

    tool(
        "anymate_reorder_frame",
        "Move a frame to a different position on the timeline.",
        schema(
            "from" to int("Frame to move", 0.0, 2000.0),
            "to" to int("Destination index", 0.0, 2000.0),
            required = listOf("from", "to")
        )
    ) { args ->
        val current = engine.reorderFrame(args.requireInt("from"), args.requireInt("to"))
        ToolResult("Frame moved. Current frame is now $current.")
    }

    tool(
        "anymate_merge_layers",
        "Flatten every layer of a frame into one layer — handy before exporting or re-posing a pose.",
        schema(
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "layer" to int("Target layer index", 0.0, 100.0)
        )
    ) { args ->
        val count = engine.mergeLayers(args.int("frame") ?: -1, args.int("layer") ?: 0)
        ToolResult("Merged $count elements into one layer.")
    }

    tool(
        "anymate_get_frame",
        "Describe what is drawn on a frame: layer index, element id and kind for every element. Use the ids with anymate_replace_layer or anymate_delete_element.",
        schema("frame" to int("Frame index; default current", 0.0, 2000.0))
    ) { args ->
        val index = args.int("frame") ?: engine.currentIndex
        val lines = engine.describeFrame(index)
        ToolResult(
            "Frame $index has ${lines.size} elements.\n" +
                lines.joinToString("\n").ifBlank { "(empty frame)" }
        )
    }

    tool(
        "anymate_replace_layer",
        "Replace every element on a layer with a new list. Each element is one of: shape, stroke, polygon, text. This is the precise way to rebuild a frame.",
        schema(
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "layer" to int("Layer index; default current", 0.0, 100.0),
            "elements" to buildJsonObject {
                put("type", "array")
                put("description", "Element specs: shape | stroke | polygon | text")
                put("items", buildJsonObject { put("type", "object") })
            },
            required = listOf("elements")
        )
    ) { args ->
        val frameIndex = args.int("frame") ?: engine.currentIndex
        val layerIndex = args.int("layer") ?: engine.currentLayer
        val specs = args["elements"]?.jsonArray
            ?: throw IllegalArgumentException("elements[] is required")
        val elements = specs.mapNotNull { spec ->
            val obj = spec.jsonObject
            when (obj.text("type")?.lowercase()) {
                "shape" -> ShapeElement(
                    id = engine.nextElementId(),
                    kind = when (obj.text("kind")?.lowercase()) {
                        "line" -> ShapeKind.LINE
                        "rect", "rectangle" -> ShapeKind.RECT
                        "arrow" -> ShapeKind.ARROW
                        else -> ShapeKind.ELLIPSE
                    },
                    x1 = obj.float("x1") ?: 0f,
                    y1 = obj.float("y1") ?: 0f,
                    x2 = obj.float("x2") ?: 0f,
                    y2 = obj.float("y2") ?: 0f,
                    strokeColor = obj.color("stroke") ?: engine.strokeColor,
                    fillColor = obj.color("fill"),
                    width = obj.float("width") ?: 3f,
                    opacity = obj.float("opacity") ?: 1f
                )
                "stroke" -> {
                    val points = obj.points2()
                    if (points.isEmpty()) null else StrokeElement(
                        id = engine.nextElementId(),
                        points = points,
                        color = obj.color("color") ?: obj.color("stroke") ?: engine.strokeColor,
                        width = obj.float("width") ?: 4f,
                        opacity = obj.float("opacity") ?: 1f,
                        tool = when (obj.text("tool")?.lowercase()) {
                            "pencil" -> ToolType.PENCIL
                            "brush" -> ToolType.BRUSH
                            "eraser" -> ToolType.ERASER
                            else -> ToolType.PEN
                        }
                    )
                }
                "polygon" -> {
                    val points = obj.points2()
                    if (points.size < 2) null else PolygonElement(
                        id = engine.nextElementId(),
                        points = points,
                        closed = obj.bool("closed") ?: true,
                        strokeColor = obj.color("stroke") ?: engine.strokeColor,
                        fillColor = obj.color("fill"),
                        width = obj.float("width") ?: 3f,
                        opacity = obj.float("opacity") ?: 1f
                    )
                }
                "text" -> TextElement(
                    id = engine.nextElementId(),
                    text = obj.text("text") ?: "",
                    x = obj.float("x") ?: 0f,
                    y = obj.float("y") ?: 0f,
                    size = obj.float("size") ?: 64f,
                    color = obj.color("color") ?: engine.strokeColor,
                    opacity = obj.float("opacity") ?: 1f
                )
                else -> null
            }
        }
        engine.replaceLayerElements(frameIndex, layerIndex, elements)
        ToolResult("Replaced layer $layerIndex on frame $frameIndex with ${elements.size} elements.")
    }

    tool(
        "anymate_delete_element",
        "Delete a single element by id. Get ids from anymate_get_frame.",
        schema(
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "layer" to int("Layer index; default current", 0.0, 100.0),
            "element_id" to int("Element id"),
            required = listOf("element_id")
        )
    ) { args ->
        val frameIndex = args.int("frame") ?: engine.currentIndex
        val layerIndex = args.int("layer") ?: engine.currentLayer
        val id = args.requireInt("element_id").toLong()
        val frame = engine.ensureFrame(frameIndex)
        val layer = frame.layers.getOrNull(layerIndex)
            ?: throw IllegalArgumentException("Layer $layerIndex does not exist.")
        val remaining = layer.snapshot().filterNot { it.id == id }
        if (remaining.size != layer.elements.size) {
            engine.replaceLayerElements(frameIndex, layerIndex, remaining)
            ToolResult("Deleted element $id.")
        } else {
            ToolResult("Element $id not found.")
        }
    }

    tool(
        "anymate_set_grid",
        "Show or hide the drawing grid that sits under the artwork.",
        schema(
            "enabled" to bool("Show the grid"),
            "spacing" to number("Grid spacing in project pixels", 8.0, 600.0),
            "color" to str("Grid colour hex with alpha")
        )
    ) { args ->
        engine.updateGrid(
            enabled = args.bool("enabled") ?: true,
            spacing = args.float("spacing") ?: engine.gridSpacing,
            color = args.color("color") ?: engine.gridColor
        )
        ToolResult("Grid ${if (engine.gridEnabled) "on" else "off"} · ${engine.gridSpacing.toInt()} px.")
    }

    tool(
        "anymate_set_canvas",
        "Resize the canvas. The artwork keeps its coordinates, so resize before drawing.",
        schema(
            "width" to int("Canvas width px", 240.0, 4096.0),
            "height" to int("Canvas height px", 240.0, 4096.0),
            required = listOf("width", "height")
        )
    ) { args ->
        engine.updateCanvasSize(args.requireInt("width"), args.requireInt("height"))
        ToolResult("Canvas is now ${engine.canvasWidth}×${engine.canvasHeight}.")
    }

    tool(
        "anymate_set_playback_range",
        "Set the in/out points of the loop that plays back.",
        schema(
            "start" to int("First frame of the loop", 0.0, 2000.0),
            "end" to int("Last frame of the loop", 0.0, 2000.0),
            required = listOf("start", "end")
        )
    ) { args ->
        engine.updatePlaybackRange(args.requireInt("start"), args.requireInt("end"))
        ToolResult("Loop range ${engine.rangeStart}..${engine.rangeEnd}.")
    }

    tool(
        "anymate_set_project_name",
        "Rename the project.",
        schema("name" to str("New project name"), required = listOf("name"))
    ) { args ->
        engine.renameProject(args.requireString("name"))
        ToolResult("Project renamed to \"${engine.projectName}\".")
    }

    tool(
        "anymate_get_render_info",
        "Get the timing and size of the animation: canvas, fps, frame count, duration and loop range.",
        obj()
    ) { _ ->
        val info = buildJsonObject {
            put("project", engine.projectName)
            put("width", engine.canvasWidth)
            put("height", engine.canvasHeight)
            put("fps", engine.fps)
            put("frames", engine.frames.size)
            put("duration_seconds", engine.frames.size.toFloat() / engine.fps.coerceAtLeast(1))
            put("loop", engine.loop)
            put("range_start", engine.rangeStart)
            put("range_end", engine.rangeEnd)
            put("layers", JsonArray(engine.layers.map { JsonPrimitive(it.name) }))
            put("background", engine.background.toHex())
        }
        ToolResult(info.toString())
    }

    tool(
        "anymate_render_sprite_sheet",
        "Render every frame into one contact-sheet PNG and return it as an image. Perfect for reviewing a whole animation in a single look.",
        schema(
            "columns" to int("How many frames per row", 1.0, 12.0),
            "cell_size" to int("Cell size in px", 48.0, 640.0)
        )
    ) { args ->
        val bitmap = exporter.renderSpriteSheet(
            engine,
            args.int("columns") ?: 6,
            args.int("cell_size") ?: 180
        )
        val png = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
        val width = bitmap.width
        val height = bitmap.height
        bitmap.recycle()
        ToolResult(
            text = "Sprite sheet with ${engine.frames.size} frames (${width}×$height px).",
            images = listOf(ToolImage("image/png", png.toByteArray()))
        )
    }
}

// ---- Helpers ---------------------------------------------------------------

private fun kotlinx.serialization.json.JsonObject.text(name: String): String? =
    this[name]?.takeIf { it !is kotlinx.serialization.json.JsonNull }
        ?.jsonPrimitive?.contentOrNull

private fun kotlinx.serialization.json.JsonObject.points2(): List<Vec2> {
    val array = this["points"]?.jsonArray ?: return emptyList()
    return array.mapNotNull { entry ->
        val pair = entry.jsonArray
        val x = pair.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
        val y = pair.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
        val pressure = pair.getOrNull(2)?.jsonPrimitive?.floatOrNull ?: 1f
        Vec2(x, y, pressure.coerceIn(0.05f, 1f))
    }
}

/** Uniform Catmull-Rom interpolation across the control points. */
private fun sampleSplineAt(points: List<Vec2>, t: Float): Vec2 {
    if (points.size == 1) return points[0]
    val clamped = t.coerceIn(0f, 1f)
    val segments = points.size - 1
    val scaled = clamped * segments
    val index = floor(scaled).toInt().coerceIn(0, segments - 1)
    val local = scaled - index
    val p0 = points[(index - 1).coerceIn(0, points.lastIndex)]
    val p1 = points[index.coerceIn(0, points.lastIndex)]
    val p2 = points[(index + 1).coerceIn(0, points.lastIndex)]
    val p3 = points[(index + 2).coerceIn(0, points.lastIndex)]
    val t2 = local * local
    val t3 = t2 * local
    fun component(a: Float, b: Float, c: Float, d: Float): Float = 0.5f * (
        2f * b +
            (-a + c) * local +
            (2f * a - 5f * b + 4f * c - d) * t2 +
            (-a + 3f * b - 3f * c + d) * t3
        )
    return Vec2(
        component(p0.x, p1.x, p2.x, p3.x),
        component(p0.y, p1.y, p2.y, p3.y),
        1f
    )
}

private fun sampleSpline(points: List<Vec2>, count: Int): List<Vec2> {
    if (points.size < 2) return points
    val steps = count.coerceIn(2, 600)
    return (0 until steps).map { i ->
        sampleSplineAt(points, i.toFloat() / (steps - 1))
    }
}

/** Easing curves an animator actually uses. */
private fun applyEasing(t: Float, easing: String): Float {
    val x = t.coerceIn(0f, 1f)
    return when (easing.lowercase()) {
        "linear" -> x
        "ease" -> x * x * (3f - 2f * x)
        "bounce" -> {
            val n = 7.5625f
            val d = 2.75f
            var v = x
            if (v < 1f / d) n * v * v
            else if (v < 2f / d) {
                v -= 1.5f / d
                n * v * v + 0.75f
            } else if (v < 2.5f / d) {
                v -= 2.25f / d
                n * v * v + 0.9375f
            } else {
                v -= 2.625f / d
                n * v * v + 0.984375f
            }
        }
        else -> {
            // "smooth" — a gentle in-out with a slight overshoot at the end
            val eased = x * x * (3f - 2f * x)
            eased + sin(x * Math.PI.toFloat()) * 0.02f
        }
    }
}
