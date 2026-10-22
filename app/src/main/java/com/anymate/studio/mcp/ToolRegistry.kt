package com.anymate.studio.mcp

import android.graphics.Bitmap
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.engine.Director
import com.anymate.studio.engine.ExportManager
import com.anymate.studio.engine.Rasterizer
import com.anymate.studio.model.CanvasPreset
import com.anymate.studio.model.PolygonElement
import com.anymate.studio.model.PremiumSwatches
import com.anymate.studio.model.ShapeElement
import com.anymate.studio.model.ShapeKind
import com.anymate.studio.model.ToolType
import com.anymate.studio.model.Vec2
import com.anymate.studio.util.parseHex
import com.anymate.studio.util.toHex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ToolImage(val mimeType: String, val data: ByteArray)

data class ToolResult(
    val text: String,
    val images: List<ToolImage> = emptyList(),
    val isError: Boolean = false
)

data class ToolDef(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val handler: suspend (JsonObject) -> ToolResult
)

/**
 * One execution surface shared by the MCP server and the in-app AI agent.
 * Every mutation lands on the Compose main thread, so the artist sees the
 * agent's work live while it happens.
 */
class ToolRegistry(
    internal val engine: AnimationEngine,
    internal val exporter: ExportManager
) {
    private val serial = Mutex()
    var serverBaseUrl: String = ""
    var accessToken: String = ""

    val tools: LinkedHashMap<String, ToolDef> = linkedMapOf()

    init {
        registerCoreTools()
        registerAnimationTools()
        registerHumanTools()
        registerFrameTools()
    }

    val toolSchemas: JsonArray
        get() = JsonArray(tools.values.map { tool ->
            buildJsonObject {
                put("name", tool.name)
                put("description", tool.description)
                put("inputSchema", tool.inputSchema)
            }
        })

    suspend fun execute(name: String, args: JsonObject): ToolResult {
        return serial.withLock {
            withContext(Dispatchers.Main) {
                executeInternal(name, args)
            }
        }
    }

    /** Must be called on the main thread. */
    private suspend fun executeInternal(name: String, args: JsonObject): ToolResult {
        val tool = tools[name] ?: return ToolResult(
            "Unknown tool: $name. Call tools/list to see every tool.", isError = true
        )
        return try {
            val result = tool.handler(args)
            engine.logTool(name, summary(name, args), ok = !result.isError)
            result
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            val message = t.message ?: t::class.simpleName ?: "Unknown error"
            engine.logTool(name, message, ok = false)
            ToolResult("$name failed: $message", isError = true)
        }
    }

    private fun summary(name: String, args: JsonObject): String = when (name) {
        "anymate_draw_stroke" -> {
            val pts = args["points"]?.jsonArray?.size ?: 0
            val tool = args["tool"]?.stringOrNull() ?: "pen"
            "drawing $tool · $pts points · frame ${args["frame"]?.intOrNull() ?: "current"}"
        }
        "anymate_draw_shape" -> "${args["shape"]?.stringOrNull() ?: "shape"} → frame ${args["frame"]?.intOrNull() ?: "current"}"
        "anymate_draw_text" -> "\"${args["text"]?.stringOrNull()?.take(28) ?: ""}\""
        "anymate_fill" -> "flood fill at frame ${args["frame"]?.intOrNull() ?: "current"}"
        "anymate_add_frame" -> "new frame"
        "anymate_duplicate_frame" -> "duplicate frame"
        "anymate_generate" -> args["prompt"]?.stringOrNull()?.take(70) ?: "local director"
        "anymate_batch" -> "${args["calls"]?.jsonArray?.size ?: 0} batched calls"
        "anymate_snapshot" -> "preview snapshot"
        "anymate_export_gif" -> "exporting GIF"
        else -> args.toString().take(110)
    }

    // ---- Tool registration ------------------------------------------------
    internal fun tool(
        name: String,
        description: String,
        schema: JsonObject,
        handler: suspend (JsonObject) -> ToolResult
    ) {
        tools[name] = ToolDef(name, description, schema, handler)
    }

    private fun registerCoreTools() {
        // Project
        tool(
            "anymate_get_state",
            "Get the live project state: canvas size, FPS, frame count, current frame, layers, tool settings and MCP connection. Call this before editing so you know the document.",
            obj(),
        ) { _ ->
            val layerJson = buildJsonArray {
                engine.layers.forEachIndexed { i, layer ->
                    add(buildJsonObject {
                        put("index", i)
                        put("name", layer.name)
                        put("visible", layer.visible)
                        put("locked", layer.locked)
                        put("opacity", layer.opacity)
                    })
                }
            }
            val state = buildJsonObject {
                put("project", engine.projectName)
                put("width", engine.canvasWidth)
                put("height", engine.canvasHeight)
                put("fps", engine.fps)
                put("frame_count", engine.frames.size)
                put("current_frame", engine.currentIndex)
                put("layer_count", engine.layers.size)
                put("current_layer", engine.currentLayer)
                put("playing", engine.isPlaying)
                put("loop", engine.loop)
                put("background", engine.background.toHex())
                put("tool", engine.selectedTool.name.lowercase())
                put("brush_size", engine.brushSize)
                put("brush_opacity", engine.brushOpacity)
                put("color", engine.strokeColor.toHex())
                put("layers", layerJson)
                put("mcp_url", if (serverBaseUrl.isBlank()) "server not running" else "$serverBaseUrl/mcp")
                put("tool_count", tools.size)
                put("agent_calls", engine.toolCallCount)
            }
            ToolResult(
                "Live AnyMate project. Coordinates are project pixels (0,0 top-left).\n${state}\n" +
                    "All drawing tools are available; changes are visible to the artist instantly."
            )
        }

        tool(
            "anymate_new_project",
            "Create a new blank project. DESTRUCTIVE: replaces the current document and clears undo history. Use only when the user asks for a fresh scene.",
            schema(
                "name" to str("Project name"),
                "width" to int("Canvas width in px", 240.0, 2048.0),
                "height" to int("Canvas height in px", 240.0, 2048.0),
                "fps" to int("Frames per second (1-60)", 1.0, 60.0),
                "background" to str("Background as #RRGGBB"),
                "preset" to enumStr("Canvas preset", listOf("square", "portrait", "landscape"))
            )
        ) { args ->
            val presetName = args.string("preset") ?: "square"
            val preset = when (presetName.lowercase()) {
                "portrait" -> CanvasPreset("Portrait", 900, 1600)
                "landscape" -> CanvasPreset("Landscape", 1600, 900)
                else -> CanvasPreset("Square", 900, 900)
            }
            val w = args.int("width") ?: preset.width
            val h = args.int("height") ?: preset.height
            val fps = args.int("fps") ?: 24
            val bg = args.color("background") ?: 0xFFF7F4EC
            engine.newProject(
                name = args.string("name") ?: "Untitled",
                preset = CanvasPreset(presetName, w, h),
                fpsValue = fps,
                backgroundColor = bg
            )
            ToolResult("Created a new ${w}×${h} project at $fps fps.")
        }

        tool(
            "anymate_rename_project",
            "Rename the current project.",
            schema("name" to str("New project name"), required = listOf("name"))
        ) { args ->
            engine.renameProject(args.requireString("name"))
            ToolResult("Project renamed to \"${engine.projectName}\".")
        }

        tool(
            "anymate_set_fps",
            "Set playback FPS (1-60). Standard choices are 12, 24, 30 and 60.",
            schema("fps" to int("Frames per second", 1.0, 60.0), required = listOf("fps"))
        ) { args ->
            engine.updateFps(args.requireInt("fps"))
            ToolResult("Project is now ${engine.fps} fps.")
        }

        tool(
            "anymate_set_background",
            "Set the canvas background colour.",
            schema("color" to str("#RRGGBB or #AARRGGBB"), required = listOf("color"))
        ) { args ->
            engine.updateBackground(args.requireColor("color"))
            ToolResult("Background set to ${engine.background.toHex()}.")
        }

        // Frames
        tool(
            "anymate_list_frames",
            "List frames with layer element counts. Use start/limit for long timelines.",
            schema("start" to int("First frame index", 0.0, 2000.0), "limit" to int("Maximum frames to return", 1.0, 200.0))
        ) { args ->
            val start = (args.int("start") ?: 0).coerceIn(0, engine.frames.lastIndex)
            val limit = (args.int("limit") ?: 60).coerceIn(1, 200)
            val end = (start + limit).coerceAtMost(engine.frames.size)
            val list = buildJsonArray {
                for (i in start until end) {
                    val frame = engine.frames[i]
                    add(buildJsonObject {
                        put("index", i)
                        put("current", i == engine.currentIndex)
                        put("elements", JsonArray(frame.layers.map { JsonPrimitive(it.elements.size) }))
                    })
                }
            }
            ToolResult("Frames $start..${end - 1} of ${engine.frames.size}\n$list")
        }

        tool(
            "anymate_add_frame",
            "Add a blank frame (or a copy of the current frame) after an index. Live canvas shows the new frame immediately.",
            schema(
                "index" to int("Insert at index; default current+1", 0.0, 2000.0),
                "copy" to bool("Copy the current frame contents"),
                "select" to bool("Select the new frame (default true)")
            )
        ) { args ->
            val frame = engine.addFrame(
                index = args.int("index"),
                copyCurrent = args.bool("copy") ?: false,
                select = args.bool("select") ?: true
            )
            val idx = engine.frames.indexOf(frame)
            ToolResult("Added frame at index $idx. Total ${engine.frames.size} frames.")
        }

        tool(
            "anymate_duplicate_frame",
            "Duplicate a frame, preserving every layer and drawing.",
            schema("index" to int("Frame to duplicate; default current", 0.0, 2000.0))
        ) { args ->
            val frame = engine.duplicateFrame(args.int("index") ?: -1)
            ToolResult("Duplicated frame. Now at index ${engine.frames.indexOf(frame)}.")
        }

        tool(
            "anymate_delete_frame",
            "Delete a frame. Deleting the only frame clears it instead.",
            schema("index" to int("Frame index; default current", 0.0, 2000.0))
        ) { args ->
            val existed = engine.deleteFrame(args.int("index") ?: -1)
            if (existed) ToolResult("Frame deleted. ${engine.frames.size} frames remain.")
            else ToolResult("Cannot delete the only frame; it was cleared.")
        }

        tool(
            "anymate_select_frame",
            "Select and display a frame on the artist's canvas.",
            schema("index" to int("Frame index", 0.0, 2000.0), required = listOf("index"))
        ) { args ->
            engine.selectFrame(args.requireInt("index"))
            ToolResult("Selected frame ${engine.currentIndex}.")
        }

        tool(
            "anymate_clear_frame",
            "Erase all drawings on every layer of a frame.",
            schema("index" to int("Frame index; default current", 0.0, 2000.0))
        ) { args ->
            engine.clearFrame(args.int("index") ?: -1)
            ToolResult("Frame cleared.")
        }

        // Layers
        tool(
            "anymate_add_layer",
            "Add a layer on top of the stack and select it.",
            schema("name" to str("Layer name"))
        ) { args ->
            val index = engine.addLayer(args.string("name"))
            ToolResult("Added layer $index (\"${engine.layers[index].name}\").")
        }

        tool(
            "anymate_delete_layer",
            "Delete a layer from every frame. A project must keep at least one layer.",
            schema("index" to int("Layer index; default current", 0.0, 100.0))
        ) { args ->
            engine.deleteLayer(args.int("index") ?: -1)
            ToolResult("Layer deleted. ${engine.layers.size} layers remain.")
        }

        tool(
            "anymate_set_layer",
            "Change a layer's name, visibility, lock or opacity.",
            schema(
                "index" to int("Layer index; default current", 0.0, 100.0),
                "name" to str("New name"),
                "visible" to bool("Show the layer"),
                "locked" to bool("Prevent edits (agents must unlock before drawing)"),
                "opacity" to number("Layer opacity 0..1", 0.0, 1.0)
            )
        ) { args ->
            val idx = args.int("index") ?: engine.currentLayer
            args.string("name")?.let { engine.renameLayer(idx, it) }
            args.bool("visible")?.let { engine.setLayerVisible(idx, it) }
            args.bool("locked")?.let { engine.setLayerLocked(idx, it) }
            args.float("opacity")?.let { engine.setLayerOpacity(idx, it) }
            ToolResult("Updated layer $idx: ${engine.layers[idx].name}.")
        }

        tool(
            "anymate_select_layer",
            "Select the active layer for future drawing.",
            schema("index" to int("Layer index", 0.0, 100.0), required = listOf("index"))
        ) { args ->
            engine.selectLayer(args.requireInt("index"))
            ToolResult("Selected layer ${engine.currentLayer}.")
        }

        // Tools and colour
        tool(
            "anymate_set_tool",
            "Choose the active drawing tool. Options: ${ToolType.entries.joinToString { it.name.lowercase() }}.",
            schema(
                "tool" to enumStr("Tool name", ToolType.entries.map { it.name.lowercase() }),
                "size" to number("Brush width in project pixels", 0.3, 300.0),
                "opacity" to number("Paint opacity 0.02..1", 0.02, 1.0),
                "shape" to enumStr("Shape when tool=shape", listOf("line", "rect", "ellipse", "arrow")),
                "shape_filled" to bool("Fill new shapes"),
                required = listOf("tool")
            )
        ) { args ->
            val toolName = args.requireString("tool").uppercase()
            val tool = ToolType.entries.firstOrNull { it.name == toolName }
                ?: throw IllegalArgumentException("Unknown tool. Use one of ${ToolType.entries.joinToString()}")
            engine.selectTool(tool)
            args.float("size")?.let { engine.updateBrushSize(it) }
            args.float("opacity")?.let { engine.updateBrushOpacity(it) }
            args.string("shape")?.let { shape ->
                val kind = when (shape.lowercase()) {
                    "line" -> ShapeKind.LINE
                    "rect", "rectangle" -> ShapeKind.RECT
                    "arrow" -> ShapeKind.ARROW
                    else -> ShapeKind.ELLIPSE
                }
                engine.updateShapeKind(kind)
            }
            args.bool("shape_filled")?.let { engine.updateShapeFilled(it) }
            ToolResult("Active tool: ${engine.selectedTool.name.lowercase()}, size ${engine.brushSize}.")
        }

        tool(
            "anymate_set_color",
            "Set the active paint colour. Accepts #RGB, #RRGGBB or #AARRGGBB.",
            schema("color" to str("Hex colour"), required = listOf("color"))
        ) { args ->
            val color = args.requireColor("color")
            engine.updateColor(color)
            ToolResult("Active colour ${color.toHex()}.")
        }

        tool(
            "anymate_get_palette",
            "Get AnyMate's curated palette plus the current colour.",
            obj()
        ) { _ ->
            val colors = PremiumSwatches.map { it.toHex() } + engine.strokeColor.toHex()
            ToolResult("Palette: ${colors.joinToString(", ")}")
        }

        // Drawing
        tool(
            "anymate_draw_stroke",
            "Draw a pressure-aware stroke on a frame. Points use project pixel coordinates. Tools: pencil, pen, brush, eraser. For 60 fps motion, batch many frames and target ~1-4 strokes per frame. A new frame is not created automatically.",
            schema(
                "points" to pointsArray("At least one point"),
                "tool" to enumStr("Stroke tool", listOf("pencil", "pen", "brush", "eraser")),
                "color" to str("Stroke colour hex (ignored for eraser)"),
                "width" to number("Stroke width in px", 0.3, 300.0),
                "opacity" to number("0..1", 0.0, 1.0),
                "frame" to int("Frame index; default current", 0.0, 2000.0),
                "layer" to int("Layer index; default current", 0.0, 100.0),
                required = listOf("points")
            )
        ) { args ->
            val points = args.points()
            require(points.isNotEmpty()) { "points cannot be empty" }
            val tool = when (args.string("tool")?.lowercase() ?: "pen") {
                "pencil" -> ToolType.PENCIL
                "brush" -> ToolType.BRUSH
                "eraser" -> ToolType.ERASER
                else -> ToolType.PEN
            }
            val ok = engine.commitStroke(
                points = points,
                tool = tool,
                color = args.color("color") ?: engine.strokeColor,
                width = args.float("width") ?: engine.brushSize,
                opacity = args.float("opacity") ?: engine.brushOpacity,
                frameIndex = args.int("frame") ?: -1,
                layerIndex = args.int("layer") ?: -1,
                select = true
            )
            ToolResult("Drew ${points.size}-point ${tool.name.lowercase()} stroke on frame ${engine.currentIndex}, layer ${engine.currentLayer}.")
        }

        tool(
            "anymate_draw_shape",
            "Draw a geometric line, rectangle, ellipse or arrow with independent stroke and fill colours.",
            schema(
                "shape" to enumStr("Shape kind", listOf("line", "rect", "ellipse", "arrow")),
                "x1" to number("Start X"), "y1" to number("Start Y"),
                "x2" to number("End X"), "y2" to number("End Y"),
                "stroke_color" to str("Stroke hex"),
                "fill_color" to str("Fill hex; omit for no fill"),
                "width" to number("Stroke width px", 0.0, 200.0),
                "opacity" to number("0..1", 0.0, 1.0),
                "frame" to int("Frame index; default current", 0.0, 2000.0),
                "layer" to int("Layer index; default current", 0.0, 100.0),
                required = listOf("shape", "x1", "y1", "x2", "y2")
            )
        ) { args ->
            val kind = when (args.requireString("shape").lowercase()) {
                "line" -> ShapeKind.LINE
                "rect", "rectangle" -> ShapeKind.RECT
                "arrow" -> ShapeKind.ARROW
                else -> ShapeKind.ELLIPSE
            }
            val stroke = args.color("stroke_color") ?: engine.strokeColor
            val fill = args.color("fill_color")
            val element = ShapeElement(
                id = engine.nextElementId(),
                kind = kind,
                x1 = args.requireFloat("x1"), y1 = args.requireFloat("y1"),
                x2 = args.requireFloat("x2"), y2 = args.requireFloat("y2"),
                strokeColor = stroke,
                fillColor = fill,
                width = args.float("width") ?: engine.brushSize,
                opacity = args.float("opacity") ?: 1f
            )
            engine.addElement(element, args.int("frame") ?: -1, args.int("layer") ?: -1, select = true)
            ToolResult("Drew ${kind.name.lowercase()} on frame ${engine.currentIndex}.")
        }

        tool(
            "anymate_draw_polygon",
            "Draw a filled or outlined polygon. Useful for character silhouettes, stars, arrows and abstract shapes.",
            schema(
                "points" to pointsArray("Polygon vertices"),
                "closed" to bool("Close the path (default true)"),
                "fill_color" to str("Fill hex"),
                "stroke_color" to str("Stroke hex"),
                "width" to number("Stroke width px", 0.0, 200.0),
                "opacity" to number("0..1", 0.0, 1.0),
                "frame" to int("Frame index; default current", 0.0, 2000.0),
                "layer" to int("Layer index; default current", 0.0, 100.0),
                required = listOf("points")
            )
        ) { args ->
            val points = args.points()
            require(points.size >= 2) { "A polygon needs at least two points." }
            val element = PolygonElement(
                id = engine.nextElementId(),
                points = points,
                closed = args.bool("closed") ?: true,
                strokeColor = args.color("stroke_color") ?: engine.strokeColor,
                fillColor = args.color("fill_color"),
                width = args.float("width") ?: 3f,
                opacity = args.float("opacity") ?: 1f
            )
            engine.addElement(element, args.int("frame") ?: -1, args.int("layer") ?: -1, select = true)
            ToolResult("Drew ${points.size}-point polygon on frame ${engine.currentIndex}.")
        }

        tool(
            "anymate_draw_text",
            "Place text. x,y is the baseline of the first line. Supports \\n line breaks.",
            schema(
                "text" to str("Text to draw"),
                "x" to number("Baseline X"), "y" to number("Baseline Y"),
                "size" to number("Font size px", 4.0, 600.0),
                "color" to str("Hex colour"),
                "frame" to int("Frame index; default current", 0.0, 2000.0),
                "layer" to int("Layer index; default current", 0.0, 100.0),
                required = listOf("text", "x", "y")
            )
        ) { args ->
            engine.addText(
                text = args.requireString("text"),
                x = args.requireFloat("x"),
                y = args.requireFloat("y"),
                size = args.float("size") ?: 72f,
                color = args.color("color") ?: engine.strokeColor,
                frameIndex = args.int("frame") ?: -1,
                layerIndex = args.int("layer") ?: -1
            )
            ToolResult("Text placed on frame ${engine.currentIndex}.")
        }

        tool(
            "anymate_fill",
            "Flood-fill the enclosed area under a point on the active layer.",
            schema(
                "x" to number("X"), "y" to number("Y"),
                "color" to str("Fill hex"),
                "tolerance" to int("Colour tolerance 0..255", 0.0, 255.0),
                "frame" to int("Frame index; default current", 0.0, 2000.0),
                "layer" to int("Layer index; default current", 0.0, 100.0),
                required = listOf("x", "y")
            )
        ) { args ->
            val ok = engine.fillAt(
                x = args.requireFloat("x"),
                y = args.requireFloat("y"),
                color = args.color("color") ?: engine.strokeColor,
                tolerance = args.int("tolerance") ?: 28,
                frameIndex = args.int("frame") ?: -1,
                layerIndex = args.int("layer") ?: -1
            )
            if (ok) ToolResult("Filled region on frame ${engine.currentIndex}.")
            else ToolResult("Nothing to fill at that point.", isError = true)
        }

        tool(
            "anymate_clear_layer",
            "Erase every element on one layer of a frame.",
            schema(
                "frame" to int("Frame index; default current", 0.0, 2000.0),
                "layer" to int("Layer index; default current", 0.0, 100.0)
            )
        ) { args ->
            engine.clearLayer(args.int("frame") ?: -1, args.int("layer") ?: -1)
            ToolResult("Layer cleared.")
        }

        // History
        tool("anymate_undo", "Undo the last drawing or frame operation.", obj()) { _ ->
            if (engine.undo()) ToolResult("Undone.") else ToolResult("Nothing to undo.", isError = true)
        }
        tool("anymate_redo", "Redo the last undone operation.", obj()) { _ ->
            if (engine.redo()) ToolResult("Redone.") else ToolResult("Nothing to redo.", isError = true)
        }

        // Playback
        tool("anymate_play", "Start live playback on the artist's screen.", obj()) { _ ->
            engine.play()
            ToolResult("Playing ${engine.frames.size} frames at ${engine.fps} fps.")
        }
        tool("anymate_pause", "Pause playback.", obj()) { _ ->
            engine.pause()
            ToolResult("Paused.")
        }
        tool(
            "anymate_seek",
            "Jump to a frame and pause.",
            schema("index" to int("Frame index", 0.0, 2000.0), required = listOf("index"))
        ) { args ->
            engine.selectFrame(args.requireInt("index"))
            ToolResult("At frame ${engine.currentIndex}.")
        }
        tool(
            "anymate_set_loop",
            "Enable or disable looped playback.",
            schema("enabled" to bool("Loop"), required = listOf("enabled"))
        ) { args ->
            engine.updateLoop(args.bool("enabled") ?: true)
            ToolResult("Loop = ${engine.loop}.")
        }
        tool(
            "anymate_set_onion_skin",
            "Toggle onion skin on the artist's canvas.",
            schema("enabled" to bool("Onion skin"), required = listOf("enabled"))
        ) { args ->
            engine.updateOnionSkin(args.bool("enabled") ?: true)
            ToolResult("Onion skin = ${engine.onionSkin}.")
        }

        // Preview
        tool(
            "anymate_snapshot",
            "Return a PNG of a frame plus project metadata. Use this to visually inspect your work; it is exactly what the artist sees. Images are returned as MCP image content.",
            schema(
                "frame" to int("Frame index; default current", 0.0, 2000.0),
                "max_size" to int("Longest edge in px (64-2048)", 64.0, 2048.0),
                "include_background" to bool("Include canvas background (default true)")
            )
        ) { args ->
            val snapshot = withContext(Dispatchers.Main) { exporter.snapshotOnMain(engine) }
            val index = (args.int("frame") ?: snapshot.currentIndex).coerceIn(0, engine.frames.lastIndex)
            val frame = snapshot.frames[index]
            val maxSize = (args.int("max_size") ?: 768).coerceIn(64, 2048)
            val scale = maxSize.toFloat() / max(snapshot.width, snapshot.height)
            val outW = (snapshot.width * scale).roundToInt().coerceAtLeast(2)
            val outH = (snapshot.height * scale).roundToInt().coerceAtLeast(2)
            val bitmap = withContext(Dispatchers.Default) {
                Rasterizer.renderFrame(
                    frame, snapshot.layers, snapshot.width, snapshot.height, snapshot.background,
                    outW, outH, includeBackground = args.bool("include_background") ?: true
                )
            }
            val png = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
            bitmap.recycle()
            ToolResult(
                text = "Snapshot of frame $index · ${snapshot.width}×${snapshot.height} · ${snapshot.fps} fps · ${snapshot.frames.size} frames.",
                images = listOf(ToolImage("image/png", png.toByteArray()))
            )
        }

        // Exports
        tool(
            "anymate_export_gif",
            "Render the whole timeline to an animated GIF on the device. Returns a download URL when the MCP server is running.",
            schema("max_size" to int("Longest edge 240-1080", 240.0, 1080.0))
        ) { args ->
            val progress: suspend (Float, String) -> Unit = { p, label ->
                withContext(Dispatchers.Main) { engine.updateAgentProgress(p, label) }
            }
            val file = exporter.exportGif(engine, args.int("max_size") ?: 720, progress)
            ToolResult(exportMessage(file, "GIF"))
        }

        tool(
            "anymate_export_png",
            "Export the current frame as a full-resolution PNG.",
            obj()
        ) { _ ->
            val progress: suspend (Float, String) -> Unit = { p, label ->
                withContext(Dispatchers.Main) { engine.updateAgentProgress(p, label) }
            }
            val file = exporter.exportPng(engine, progress)
            ToolResult(exportMessage(file, "PNG"))
        }

        tool(
            "anymate_export_frames",
            "Export every frame as a numbered PNG sequence inside a ZIP.",
            obj()
        ) { _ ->
            val progress: suspend (Float, String) -> Unit = { p, label ->
                withContext(Dispatchers.Main) { engine.updateAgentProgress(p, label) }
            }
            val file = exporter.exportZip(engine, progress)
            ToolResult(exportMessage(file, "PNG sequence ZIP"))
        }

        // Generative director + batch
        tool(
            "anymate_generate",
            "Run AnyMate's offline director: it paints a complete, looping animation live on the canvas using the drawing tools. Great for quick motion studies even without an external model. Appends a new sequence; choose prompts like 'bouncing ball', 'walk cycle', 'fireworks at 60 fps', 'pulse rings', 'kinetic text \"HELLO\"', 'heart loop', 'solar orbit'.",
            schema(
                "prompt" to str("Creative prompt"),
                "fps" to int("Optional FPS override (1-60)", 1.0, 60.0),
                "pace" to bool("Pace frame generation for a visible live build (default true)"),
                required = listOf("prompt")
            )
        ) { args ->
            args.int("fps")?.let { engine.updateFps(it) }
            engine.setAgentRunning(true, "Director · generating live")
            try {
                val result = Director.animate(
                    prompt = args.requireString("prompt"),
                    engine = engine,
                    pace = args.bool("pace") ?: true
                ) { done, total ->
                    engine.updateAgentProgress(done.toFloat() / total, "Directing frame $done/$total")
                }
                ToolResult(
                    "Director created \"${result.label}\": ${result.frameCount} frames at ${result.fps} fps, " +
                        "starting at frame ${result.startFrame}. Playback is live."
                )
            } finally {
                engine.setAgentRunning(false)
            }
        }

        tool(
            "anymate_batch",
            "Execute many AnyMate tool calls in one request. Use this for high-throughput agent animation: for example add 60 frames and draw on each in one batch. The whole batch is one undo step. Nested batches are not allowed. Set pace_ms to let the artist watch the work happen stroke by stroke instead of appearing at once.",
            schema(
                "pace_ms" to int("Delay between calls in ms, so the drawing appears live (0 = instant)", 0.0, 500.0),
                "calls" to buildJsonObject {
                    put("type", "array")
                    put("description", "Array of {name, args} calls")
                    put("items", buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {
                            put("name", str("Tool name"))
                            put("args", buildJsonObject { put("type", "object") })
                        })
                        put("required", JsonArray(listOf(JsonPrimitive("name"))))
                    })
                },
                required = listOf("calls")
            )
        ) { args ->
            val calls = args["calls"]?.jsonArray ?: throw IllegalArgumentException("calls[] is required")
            require(calls.size in 1..400) { "Batch must contain 1-400 calls." }
            val pace = (args.int("pace_ms") ?: 0).coerceIn(0, 500)
            engine.beginUndoGroup("Agent batch (${calls.size} calls)")
            var ok = 0
            var failed = 0
            val errors = ArrayList<String>()
            try {
                for ((i, element) in calls.withIndex()) {
                    val call = element.jsonObject
                    val name = call["name"]?.stringOrNull() ?: continue
                    if (name == "anymate_batch") {
                        failed++
                        errors.add("#$i nested batch rejected")
                        continue
                    }
                    val callArgs = call["args"]?.jsonObject ?: JsonObject(emptyMap())
                    val result = executeInternal(name, callArgs)
                    if (pace > 0 && i < calls.lastIndex) kotlinx.coroutines.delay(pace.toLong())
                    if (result.isError) {
                        failed++
                        if (errors.size < 6) errors.add("#$i $name: ${result.text.take(140)}")
                    } else {
                        ok++
                    }
                }
            } finally {
                engine.endUndoGroup()
            }
            val message = buildString {
                append("Batch complete: $ok succeeded, $failed failed across ${calls.size} calls.\n")
                append("Final state: frame ${engine.currentIndex}/${engine.frames.lastIndex}, ${engine.frames.size} frames, ${engine.fps} fps.")
                if (errors.isNotEmpty()) append("\nErrors:\n").append(errors.joinToString("\n"))
            }
            ToolResult(message, isError = failed > 0 && ok == 0)
        }
    }

    private fun exportMessage(file: java.io.File, label: String): String {        val url = if (serverBaseUrl.isBlank()) "" else "\nDownload: $serverBaseUrl/exports/${file.name}"
        val token = if (accessToken.isBlank()) "" else "\nAuth: Bearer token (see AnyMate MCP settings)"
        return "$label exported: ${file.absolutePath}\n${file.length() / 1024} KB$url$token"
    }

    // ---- JSON helpers -----------------------------------------------------
    internal fun obj() = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    internal fun schema(
        vararg props: Pair<String, JsonObject>,
        required: List<String> = emptyList()
    ): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(props.toMap()))
        if (required.isNotEmpty()) {
            put("required", JsonArray(required.map { JsonPrimitive(it) }))
        }
        put("additionalProperties", false)
    }

    internal fun str(desc: String) = buildJsonObject {
        put("type", "string"); put("description", desc)
    }

    internal fun int(desc: String, min: Double? = null, max: Double? = null) = buildJsonObject {
        put("type", "integer"); put("description", desc)
        min?.let { put("minimum", it) }
        max?.let { put("maximum", it) }
    }

    internal fun number(desc: String, min: Double? = null, max: Double? = null) = buildJsonObject {
        put("type", "number"); put("description", desc)
        min?.let { put("minimum", it) }
        max?.let { put("maximum", it) }
    }

    internal fun bool(desc: String) = buildJsonObject {
        put("type", "boolean"); put("description", desc)
    }

    internal fun enumStr(desc: String, values: List<String>) = buildJsonObject {
        put("type", "string"); put("description", desc)
        put("enum", JsonArray(values.map { JsonPrimitive(it) }))
    }

    internal fun pointsArray(desc: String) = buildJsonObject {
        put("type", "array")
        put("description", desc)
        put("items", buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("x", number("X"))
                put("y", number("Y"))
                put("pressure", number("0..1; default 1", 0.0, 1.0))
            })
            put("required", JsonArray(listOf(JsonPrimitive("x"), JsonPrimitive("y"))))
        })
        put("minItems", 1)
    }

    internal fun JsonObject.requireString(name: String): String =
        string(name) ?: throw IllegalArgumentException("$name is required")

    internal fun JsonObject.requireInt(name: String): Int =
        int(name) ?: throw IllegalArgumentException("$name is required")

    internal fun JsonObject.requireFloat(name: String): Float =
        float(name) ?: throw IllegalArgumentException("$name is required")

    internal fun JsonObject.requireColor(name: String): Long =
        color(name) ?: throw IllegalArgumentException("$name is required as a hex colour")

    internal fun JsonObject.string(name: String): String? =
        this[name]?.takeIf { it !is JsonNull }?.stringOrNull()

    internal fun JsonObject.int(name: String): Int? =
        this[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull

    internal fun JsonObject.float(name: String): Float? =
        this[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.floatOrNull

    internal fun JsonObject.bool(name: String): Boolean? =
        this[name]?.takeIf { it !is JsonNull }?.stringOrNull()?.toBooleanStrictOrNull()
            ?: this[name]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.toBooleanStrictOrNull()

    internal fun JsonObject.color(name: String): Long? {
        val element = this[name] ?: return null
        val primitive = element.jsonPrimitive
        return if (primitive.isString) {
            val text = primitive.content
            if (text.startsWith("#")) parseHex(text) else text.toLongOrNull()?.let { it and 0xFFFFFFFFL }
        } else {
            primitive.content.toLongOrNull()
        }
    }

    internal fun JsonObject.points(): List<Vec2> {
        val array = this["points"]?.jsonArray ?: throw IllegalArgumentException("points[] is required")
        if (array.size > 20_000) throw IllegalArgumentException("Too many points (max 20000)")
        return array.map { element ->
            if (element is JsonArray) {
                // Compact [x, y] (or [x, y, pressure]) pairs are accepted too.
                val x = element.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: 0f
                val y = element.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: 0f
                val pressure = element.getOrNull(2)?.jsonPrimitive?.floatOrNull ?: 1f
                Vec2(x, y, pressure.coerceIn(0f, 1f))
            } else {
                val objectValue = element.jsonObject
                val x = objectValue.requireFloat("x")
                val y = objectValue.requireFloat("y")
                val pressure = objectValue.float("pressure") ?: 1f
                Vec2(x, y, pressure.coerceIn(0f, 1f))
            }
        }
    }

    internal fun JsonElement.stringOrNull(): String? = try {
        if (this is JsonNull) null else jsonPrimitive.content
    } catch (_: Exception) {
        null
    }

    internal fun JsonElement.intOrNull(): Int? = try {
        if (this is JsonNull) null else jsonPrimitive.intOrNull
    } catch (_: Exception) {
        null
    }
}
