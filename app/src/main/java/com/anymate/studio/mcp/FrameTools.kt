package com.anymate.studio.mcp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Frame surgery for the "remake a video" workflow: place decoded frames as
 * bitmaps, then mirror, rotate, tint, retime and tween them.
 */
internal fun ToolRegistry.registerFrameTools() {

    tool(
        "anymate_draw_bitmap",
        "Place a PNG image (base64) on a frame. x,y is the top-left corner in project pixels. " +
            "Use it to bring a photo, a video frame or an exported drawing onto the canvas.",
        schema(
            "png_base64" to str("PNG bytes encoded as base64"),
            "x" to number("Left position px; omit to centre"),
            "y" to number("Top position px; omit to centre"),
            "fit" to bool("Scale down so the whole image fits the canvas"),
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "layer" to int("Layer index; default current", 0.0, 100.0),
            required = listOf("png_base64")
        )
    ) { args ->
        val encoded = args.requireString("png_base64")
        val raw = try {
            Base64.decode(encoded, Base64.DEFAULT)
        } catch (t: Throwable) {
            throw IllegalArgumentException("png_base64 is not valid base64: ${t.message}")
        }
        var bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size)
            ?: throw IllegalArgumentException("That data is not a readable image.")
        if (args.bool("fit") == true) {
            val ratio = minOf(
                engine.canvasWidth.toFloat() / bitmap.width,
                engine.canvasHeight.toFloat() / bitmap.height
            )
            if (ratio < 1f) {
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    Math.max(1, (bitmap.width * ratio).toInt()),
                    Math.max(1, (bitmap.height * ratio).toInt()),
                    true
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
        }
        val x = args.float("x") ?: ((engine.canvasWidth - bitmap.width) / 2f)
        val y = args.float("y") ?: ((engine.canvasHeight - bitmap.height) / 2f)
        engine.addRaster(
            bitmap = bitmap,
            x = x,
            y = y,
            frameIndex = args.int("frame") ?: -1,
            layerIndex = args.int("layer") ?: -1
        )
        ToolResult("Placed a ${bitmap.width}×${bitmap.height} image on frame ${engine.currentIndex}.")
    }

    tool(
        "anymate_transform_frame",
        "Mirror, rotate or scale everything on a frame. axis='horizontal' | 'vertical' | 'rotate180' | " +
            "'rotate90cw' | 'rotate90ccw'. scale defaults to 1 and dx/dy shift the result.",
        schema(
            "axis" to enumStr("Transform", listOf("horizontal", "vertical", "rotate180", "rotate90cw", "rotate90ccw")),
            "scale" to number("Uniform scale (1 = unchanged)", 0.05, 10.0),
            "dx" to number("Shift X px"),
            "dy" to number("Shift Y px"),
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            required = listOf("axis")
        )
    ) { args ->
        val axis = args.requireString("axis")
        val count = engine.transformFrame(
            index = args.int("frame") ?: -1,
            axis = axis,
            scale = (args.float("scale") ?: 1f).coerceIn(0.05f, 10f),
            dx = args.float("dx") ?: 0f,
            dy = args.float("dy") ?: 0f
        )
        ToolResult("Transformed $count elements ($axis).")
    }

    tool(
        "anymate_recolor_frame",
        "Repaint every vector element of a frame in one colour while keeping its alpha. Useful for " +
            "colour keys on an imported animation.",
        schema(
            "color" to str("Hex colour"),
            "frame" to int("Frame index; default current", 0.0, 2000.0),
            "layer" to int("Only this layer; default all"),
            required = listOf("color")
        )
    ) { args ->
        val color = args.requireColor("color")
        val changed = engine.recolorFrame(
            index = args.int("frame") ?: -1,
            color = color,
            layerIndex = args.int("layer") ?: -1
        )
        ToolResult("Recoloured $changed elements on frame ${engine.currentIndex}.")
    }

    tool("anymate_reverse_frames", "Play the whole timeline backwards.", obj()) { _ ->
        engine.reverseFrames()
        ToolResult("Reversed ${engine.frames.size} frames.")
    }

    tool(
        "anymate_hold_frames",
        "Repeat each frame count times — the animator's 'on twos' retime. count=2 doubles the " +
            "timeline length and slows the motion without changing the fps.",
        schema("count" to int("Repeats per frame", 2.0, 12.0), required = listOf("count"))
    ) { args ->
        val inserted = engine.holdFrames(args.int("count") ?: 2)
        ToolResult("Inserted $inserted held frames; the timeline is now ${engine.frames.size} frames.")
    }

    tool(
        "anymate_interpolate_frames",
        "Add count in-between frames between every pair of frames whose elements line up. The " +
            "positions, widths and opacity are interpolated, so a hand-drawn in-between falls out " +
            "of the tween.",
        schema("count" to int("In-betweens per gap", 1.0, 12.0), required = listOf("count"))
    ) { args ->
        val added = engine.interpolateFrames(args.int("count") ?: 1)
        if (added == 0) {
            ToolResult(
                "No pairs could be tweened. Frames need matching layers and element order.",
                isError = true
            )
        } else {
            ToolResult("Added $added in-between frames; the timeline is now ${engine.frames.size} frames.")
        }
    }
}
