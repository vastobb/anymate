package com.anymate.studio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.anymate.studio.model.Element
import com.anymate.studio.model.ElementFile
import com.anymate.studio.model.FrameFile
import com.anymate.studio.model.FrameLayer
import com.anymate.studio.model.LayerFile
import com.anymate.studio.model.LayerMeta
import com.anymate.studio.model.PolygonElement
import com.anymate.studio.model.ProjectFile
import com.anymate.studio.model.RasterElement
import com.anymate.studio.model.ShapeElement
import com.anymate.studio.model.ShapeKind
import com.anymate.studio.model.StrokeElement
import com.anymate.studio.model.TextElement
import com.anymate.studio.model.ToolType
import com.anymate.studio.model.Vec2
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Persistence for the live document. The project is written as one JSON file
 * (autosave in internal storage, plus a shareable export) so a session always
 * survives a restart, a crash or a phone reboot.
 */
class ProjectStore(
    private val context: Context,
    private val engine: AnimationEngine
) {
    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val projectDir: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    val autosaveFile: File get() = File(projectDir, "autosave.anymate")

    // ---- Capture -----------------------------------------------------------
    fun capture(): ProjectFile {
        val layers = engine.layers.map { layer ->
            LayerFile(
                name = layer.name,
                visible = layer.visible,
                locked = layer.locked,
                opacity = layer.opacity
            )
        }
        val frames = engine.frames.map { frame ->
            FrameFile(
                layers = frame.layers.map { frameLayer ->
                    frameLayer.elements.map { element -> encode(element) }
                }
            )
        }
        return ProjectFile(
            name = engine.projectName,
            width = engine.canvasWidth,
            height = engine.canvasHeight,
            fps = engine.fps,
            background = engine.background,
            currentFrame = engine.currentIndex,
            layers = layers,
            frames = frames
        )
    }

    private fun encode(element: Element): ElementFile = when (element) {
        is StrokeElement -> ElementFile.Stroke(
            points = element.points.map { listOf(it.x, it.y, it.pressure) },
            color = element.color,
            width = element.width,
            opacity = element.opacity,
            tool = element.tool.name
        )
        is ShapeElement -> ElementFile.Shape(
            kind = element.kind.name,
            x1 = element.x1, y1 = element.y1, x2 = element.x2, y2 = element.y2,
            stroke = element.strokeColor,
            fill = element.fillColor,
            width = element.width,
            opacity = element.opacity
        )
        is PolygonElement -> ElementFile.Poly(
            points = element.points.map { listOf(it.x, it.y, it.pressure) },
            closed = element.closed,
            stroke = element.strokeColor,
            fill = element.fillColor,
            width = element.width,
            opacity = element.opacity
        )
        is TextElement -> ElementFile.Text(
            text = element.text,
            x = element.x, y = element.y,
            size = element.size,
            color = element.color,
            opacity = element.opacity,
            bold = element.bold
        )
        is RasterElement -> ElementFile.Raster(
            x = element.x,
            y = element.y,
            png = encodeBitmap(element.bitmap),
            opacity = element.opacity
        )
    }

    private fun encodeBitmap(bitmap: Bitmap): String {
        return try {
            if (bitmap.isRecycled) return ""
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (_: Throwable) {
            ""
        }
    }

    // ---- Restore -----------------------------------------------------------
    fun restore(file: ProjectFile): Boolean {
        if (file.frames.isEmpty()) return false
        return try {
            engine.restoreProject(file, decodeElements = { elementFile ->
                decode(elementFile)
            })
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun decode(elementFile: ElementFile): Element? = when (elementFile) {
        is ElementFile.Stroke -> StrokeElement(
            id = engine.nextElementId(),
            points = elementFile.points.map {
                Vec2(
                    it.getOrNull(0) ?: 0f,
                    it.getOrNull(1) ?: 0f,
                    (it.getOrNull(2) ?: 1f).coerceIn(0.05f, 1f)
                )
            },
            color = elementFile.color,
            width = elementFile.width,
            opacity = elementFile.opacity,
            tool = runCatching { ToolType.valueOf(elementFile.tool) }.getOrDefault(ToolType.PENCIL)
        )
        is ElementFile.Shape -> ShapeElement(
            id = engine.nextElementId(),
            kind = runCatching { ShapeKind.valueOf(elementFile.kind) }.getOrDefault(ShapeKind.ELLIPSE),
            x1 = elementFile.x1, y1 = elementFile.y1,
            x2 = elementFile.x2, y2 = elementFile.y2,
            strokeColor = elementFile.stroke,
            fillColor = elementFile.fill,
            width = elementFile.width,
            opacity = elementFile.opacity
        )
        is ElementFile.Poly -> PolygonElement(
            id = engine.nextElementId(),
            points = elementFile.points.map {
                Vec2(it.getOrNull(0) ?: 0f, it.getOrNull(1) ?: 0f, it.getOrNull(2) ?: 1f)
            },
            closed = elementFile.closed,
            strokeColor = elementFile.stroke,
            fillColor = elementFile.fill,
            width = elementFile.width,
            opacity = elementFile.opacity
        )
        is ElementFile.Text -> TextElement(
            id = engine.nextElementId(),
            text = elementFile.text,
            x = elementFile.x, y = elementFile.y,
            size = elementFile.size,
            color = elementFile.color,
            opacity = elementFile.opacity,
            bold = elementFile.bold
        )
        is ElementFile.Raster -> {
            val bitmap = decodeBitmap(elementFile.png) ?: return null
            RasterElement(
                id = engine.nextElementId(),
                bitmap = bitmap,
                x = elementFile.x,
                y = elementFile.y,
                opacity = elementFile.opacity
            )
        }
    }

    private fun decodeBitmap(encoded: String): Bitmap? {
        if (encoded.isBlank()) return null
        return try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Throwable) {
            null
        }
    }

    // ---- IO ----------------------------------------------------------------
    fun saveAutosave(): Boolean = writeTo(autosaveFile)

    fun writeTo(target: File): Boolean {
        return try {
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeText(json.encodeToString(ProjectFile.serializer(), capture()))
            if (target.exists()) target.delete()
            temp.renameTo(target)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun readFrom(source: File): Boolean {
        return try {
            val text = source.readText()
            val file = json.decodeFromString(ProjectFile.serializer(), text)
            restore(file)
        } catch (_: Throwable) {
            false
        }
    }

    fun readAutosave(): Boolean {
        val file = autosaveFile
        if (!file.exists() || file.length() < 32) return false
        return readFrom(file)
    }
}
