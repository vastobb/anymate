package com.anymate.studio.mcp

import com.anymate.studio.engine.HumanStroke
import com.anymate.studio.model.ShapeKind
import com.anymate.studio.model.ToolType
import com.anymate.studio.model.Vec2
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.atan2

/**
 * Human drawing tools. Instead of one perfect spline, these build a line the
 * way a person does: block it in loosely, then search again with lighter,
 * wobblier passes. `detail` moves from "easy" (fast rough) to "detailed".
 */
internal fun ToolRegistry.registerHumanTools() {

    tool(
        "anymate_draw_human_stroke",
        "Draw a hand-drawn stroke through the points: natural wobble, tapered ends, slight overshoot " +
            "and one or two lighter search passes. detail='easy' blocks the line roughly, 'medium' is a " +
            "confident single take, 'detailed' adds refinement passes. Use this instead of anymate_draw_stroke " +
            "when the line should look human.",
        schema(
            "points" to pointsArray("Path the hand should follow"),
            "detail" to enumStr("How finished the line is", listOf("easy", "medium", "detailed")),
            "color" to str("Stroke colour hex"),
            "width" to number("Stroke width px", 0.3, 200.0),
            "opacity" to number("0..1", 0.0, 1.0),
            "tool" to enumStr("Stroke tool", listOf("pen", "pencil", "brush")),
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "layer" to int("Layer index; default current", 0.0, 100.0),
            required = listOf("points")
        )
    ) { args ->
        val points = args.points()
        require(points.isNotEmpty()) { "points cannot be empty" }
        val detail = args.string("detail") ?: "medium"
        val strokeTool = when (args.string("tool")?.lowercase() ?: "pen") {
            "pencil" -> ToolType.PENCIL
            "brush" -> ToolType.BRUSH
            else -> ToolType.PEN
        }
        val color = args.color("color") ?: engine.strokeColor
        val width = args.float("width") ?: engine.brushSize
        val opacity = args.float("opacity") ?: engine.brushOpacity
        val frame = args.int("frame") ?: -1
        val layer = args.int("layer") ?: -1
        val seed = seedOf(points)
        val specs = HumanStroke.polyline(points, detail, seed)
        engine.beginUndoGroup("Human stroke")
        var drawn = 0
        specs.forEach { spec ->
            if (engine.commitStroke(
                    points = spec.points,
                    tool = strokeTool,
                    color = color,
                    width = (width * spec.widthScale).coerceAtLeast(0.3f),
                    opacity = (opacity * spec.opacityScale).coerceIn(0f, 1f),
                    frameIndex = frame,
                    layerIndex = layer,
                    select = false
                )
            ) drawn++
        }
        engine.endUndoGroup()
        ToolResult("Drew a hand-made ${detail} stroke with $drawn pencil passes over ${points.size} guide points.")
    }

    tool(
        "anymate_draw_human_shape",
        "Draw a line, arrow, rectangle or ellipse the way a person sketches it: separate confident " +
            "sides, overshooting corners, wobble and optional refinement. Works for blocking a scene " +
            "before details. detail='easy' for roughs, 'detailed' for finished line art.",
        schema(
            "shape" to enumStr("Shape", listOf("line", "arrow", "rect", "ellipse")),
            "x1" to number("Start X"), "y1" to number("Start Y"),
            "x2" to number("End X"), "y2" to number("End Y"),
            "detail" to enumStr("How finished the line is", listOf("easy", "medium", "detailed")),
            "color" to str("Stroke colour hex"),
            "width" to number("Stroke width px", 0.3, 200.0),
            "opacity" to number("0..1", 0.0, 1.0),
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "layer" to int("Layer index; default current", 0.0, 100.0),
            required = listOf("shape", "x1", "y1", "x2", "y2")
        )
    ) { args ->
        val kind = when (args.requireString("shape").lowercase()) {
            "line" -> ShapeKind.LINE
            "arrow" -> ShapeKind.ARROW
            "rect", "rectangle", "box" -> ShapeKind.RECT
            else -> ShapeKind.ELLIPSE
        }
        val x1 = args.requireFloat("x1")
        val y1 = args.requireFloat("y1")
        val x2 = args.requireFloat("x2")
        val y2 = args.requireFloat("y2")
        val detail = args.string("detail") ?: "medium"
        val color = args.color("color") ?: engine.strokeColor
        val width = args.float("width") ?: engine.brushSize
        val opacity = args.float("opacity") ?: engine.brushOpacity
        val frame = args.int("frame") ?: -1
        val layer = args.int("layer") ?: -1
        val seed = ((x1 + y1) * 7).toInt() + ((x2 - y2) * 3).toInt()
        val specs = HumanStroke.shape(kind, x1, y1, x2, y2, detail, seed)
        engine.beginUndoGroup("Human ${kind.name.lowercase()}")
        var drawn = 0
        specs.forEach { spec ->
            if (engine.commitStroke(
                    points = spec.points,
                    tool = ToolType.PEN,
                    color = color,
                    width = (width * spec.widthScale).coerceAtLeast(0.3f),
                    opacity = (opacity * spec.opacityScale).coerceIn(0f, 1f),
                    frameIndex = frame,
                    layerIndex = layer,
                    select = false
                )
            ) drawn++
        }
        engine.endUndoGroup()
        ToolResult("Sketched a ${kind.name.lowercase()} with $drawn human passes.")
    }

    tool(
        "anymate_draw_human_figure",
        "Block in a human figure: head, spine, arms and legs drawn as separate confident strokes. " +
            "Poses: idle, walk, run, wave, sit. Use it for character blocking, then add details with " +
            "anymate_draw_human_stroke.",
        schema(
            "pose" to enumStr("Figure pose", listOf("idle", "walk", "run", "wave", "sit")),
            "x" to number("Left of the figure box"), "y" to number("Top of the figure box"),
            "size" to number("Figure height in px", 20.0, 1600.0),
            "detail" to enumStr("Line finish", listOf("easy", "medium", "detailed")),
            "color" to str("Stroke colour hex"),
            "width" to number("Stroke width px", 0.3, 100.0),
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "layer" to int("Layer index; default current", 0.0, 100.0),
            required = listOf("pose", "x", "y", "size")
        )
    ) { args ->
        val pose = args.string("pose") ?: "idle"
        val x = args.requireFloat("x")
        val y = args.requireFloat("y")
        val size = args.requireFloat("size")
        val detail = args.string("detail") ?: "medium"
        val color = args.color("color") ?: engine.strokeColor
        val width = args.float("width") ?: engine.brushSize
        val frame = args.int("frame") ?: -1
        val layer = args.int("layer") ?: -1
        val specs = HumanStroke.figure(pose, x, y, size, detail, (x + y).toInt())
        engine.beginUndoGroup("Figure $pose")
        var drawn = 0
        specs.forEach { spec ->
            if (engine.commitStroke(
                    points = spec.points,
                    tool = ToolType.PEN,
                    color = color,
                    width = (width * spec.widthScale).coerceAtLeast(0.3f),
                    opacity = spec.opacityScale.coerceIn(0f, 1f),
                    frameIndex = frame,
                    layerIndex = layer,
                    select = false
                )
            ) drawn++
        }
        engine.endUndoGroup()
        ToolResult("Blocked a $pose figure with $drawn strokes.")
    }
}

internal fun seedOf(points: List<Vec2>): Int {
    if (points.isEmpty()) return 0
    val first = points.first()
    val last = points.last()
    return ((first.x + first.y) * 31 + (last.x - last.y) * 17).toInt()
}

/** Reads a points-like argument that may be `{x,y}` objects or `[x,y]` pairs. */
internal fun JsonObject.pointsLoose(name: String = "points"): List<Vec2> {
    val element = this[name] ?: throw IllegalArgumentException("$name is required")
    val array = element as? kotlinx.serialization.json.JsonArray
        ?: throw IllegalArgumentException("$name must be an array")
    if (array.size > 20_000) throw IllegalArgumentException("Too many points (max 20000)")
    return array.map { entry ->
        when (entry) {
            is kotlinx.serialization.json.JsonArray -> Vec2(
                entry.getOrNull(0)?.jsonPrimitive?.floatOrNullSafe() ?: 0f,
                entry.getOrNull(1)?.jsonPrimitive?.floatOrNullSafe() ?: 0f
            )
            is JsonObject -> {
                val x = entry["x"]?.jsonPrimitive?.floatOrNullSafe() ?: 0f
                val y = entry["y"]?.jsonPrimitive?.floatOrNullSafe() ?: 0f
                Vec2(x, y)
            }
            else -> throw IllegalArgumentException("points must contain {x,y} objects or [x,y] pairs")
        }
    }
}

internal fun kotlinx.serialization.json.JsonPrimitive.floatOrNullSafe(): Float? =
    content.toFloatOrNull()

internal fun angleOf(a: Vec2, b: Vec2): Float = atan2(b.y - a.y, b.x - a.x)
