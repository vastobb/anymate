package com.anymate.studio.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.LruCache
import com.anymate.studio.AppFonts
import com.anymate.studio.model.Element
import com.anymate.studio.model.Frame
import com.anymate.studio.model.FrameLayer
import com.anymate.studio.model.LayerMeta
import com.anymate.studio.model.PolygonElement
import com.anymate.studio.model.RasterElement
import com.anymate.studio.model.ShapeElement
import com.anymate.studio.model.ShapeKind
import com.anymate.studio.model.StrokeElement
import com.anymate.studio.model.TextElement
import com.anymate.studio.model.ToolType
import com.anymate.studio.model.Vec2
import com.anymate.studio.util.alphaFraction
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Project-space software renderer. It is the single source of truth for the
 * editor, thumbnails, GIF export and MCP snapshots, so what an agent sees is
 * exactly what the artist sees.
 */
/** Optional drawing grid rendered beneath the artwork. */
data class GridSpec(
    val enabled: Boolean = false,
    val spacing: Float = 80f,
    val color: Long = 0x22000000
)

object Rasterizer {

    private data class CachedStroke(val path: Path, val avgPressure: Float, val isDot: Boolean)

    private val strokeCache = object : LruCache<Long, CachedStroke>(2048) {}
    private val polygonCache = object : LruCache<Long, Path>(1024) {}
    private val clearXfer = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    fun clearCaches() {
        strokeCache.evictAll()
        polygonCache.evictAll()
    }

    fun drawFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        background: Long,
        frame: Frame,
        layers: List<LayerMeta>,
        includeBackground: Boolean = true,
        grid: GridSpec? = null
    ) {
        if (includeBackground) canvas.drawColor(background.toInt())
        drawGrid(canvas, width, height, grid)
        drawLayers(canvas, width, height, frame, layers)
    }

    fun drawGrid(canvas: Canvas, width: Int, height: Int, spec: GridSpec?) {
        if (spec == null || !spec.enabled) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = spec.color.toInt()
        paint.strokeWidth = 1f
        val step = spec.spacing.coerceAtLeast(4f)
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
            x += step
        }
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += step
        }
    }

    fun drawLayers(
        canvas: Canvas,
        width: Int,
        height: Int,
        frame: Frame,
        layers: List<LayerMeta>
    ) {
        for (i in layers.indices) {
            val meta = layers[i]
            if (!meta.visible || meta.opacity <= 0.002f) continue
            val layer = frame.layers.getOrNull(i) ?: continue
            if (layer.elements.isEmpty()) continue
            val hasEraser = layer.elements.any { it is StrokeElement && it.tool == ToolType.ERASER }
            val isolated = meta.opacity < 0.999f || hasEraser
            if (isolated) {
                canvas.saveLayerAlpha(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    (meta.opacity.coerceIn(0f, 1f) * 255).toInt(),
                    Canvas.ALL_SAVE_FLAG
                )
            }
            for (element in layer.elements) {
                drawElement(canvas, element)
            }
            if (isolated) canvas.restore()
        }
    }

    fun renderFrame(
        frame: Frame,
        layers: List<LayerMeta>,
        projectWidth: Int,
        projectHeight: Int,
        background: Long,
        outWidth: Int,
        outHeight: Int,
        includeBackground: Boolean = true,
        grid: GridSpec? = null
    ): Bitmap {
        val safeW = outWidth.coerceAtLeast(1)
        val safeH = outHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (includeBackground) canvas.drawColor(background.toInt())
        val scale = min(safeW.toFloat() / projectWidth, safeH.toFloat() / projectHeight)
        val dx = (safeW - projectWidth * scale) / 2f
        val dy = (safeH - projectHeight * scale) / 2f
        val count = canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(scale, scale)
        drawGrid(canvas, projectWidth, projectHeight, grid)
        drawLayers(canvas, projectWidth, projectHeight, frame, layers)
        canvas.restoreToCount(count)
        return bitmap
    }

    /** Transparent layer render at full project resolution. Used by flood fill. */
    fun renderLayer(
        layer: FrameLayer,
        projectWidth: Int,
        projectHeight: Int
    ): Bitmap = renderElements(layer.elements, projectWidth, projectHeight)

    fun renderElements(
        elements: List<Element>,
        projectWidth: Int,
        projectHeight: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            projectWidth.coerceAtLeast(1),
            projectHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        val hasEraser = elements.any { it is StrokeElement && it.tool == ToolType.ERASER }
        if (hasEraser) {
            canvas.saveLayerAlpha(
                0f, 0f, projectWidth.toFloat(), projectHeight.toFloat(),
                255, Canvas.ALL_SAVE_FLAG
            )
        }
        for (element in elements) drawElement(canvas, element)
        if (hasEraser) canvas.restore()
        return bitmap
    }

    fun renderCurrentFrame(
        frame: Frame,
        layers: List<LayerMeta>,
        width: Int,
        height: Int,
        background: Long
    ): Bitmap = renderFrame(frame, layers, width, height, background, width, height)

    fun drawElement(canvas: Canvas, element: Element) {
        when (element) {
            is StrokeElement -> drawStroke(canvas, element)
            is ShapeElement -> drawShape(canvas, element)
            is PolygonElement -> drawPolygon(canvas, element)
            is TextElement -> drawText(canvas, element)
            is RasterElement -> drawRaster(canvas, element)
        }
    }

    /** Uncached path build for the in-progress stroke preview. */
    fun drawPreview(canvas: Canvas, element: Element) {
        when (element) {
            is StrokeElement -> renderStroke(canvas, element, buildStroke(element))
            is ShapeElement -> drawShape(canvas, element)
            is PolygonElement -> drawPolygon(canvas, element)
            is TextElement -> drawText(canvas, element)
            is RasterElement -> drawRaster(canvas, element)
        }
    }

    private fun basePaint(element: Element, alpha: Float): Paint {
        val color = when (element) {
            is StrokeElement -> element.color
            is ShapeElement -> element.strokeColor
            is PolygonElement -> element.strokeColor
            is TextElement -> element.color
            is RasterElement -> Color.WHITE.toLong()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.color = (color and 0x00FFFFFFL).toInt() or (0xFF shl 24)
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        return paint
    }

    private fun drawStroke(canvas: Canvas, stroke: StrokeElement) {
        if (stroke.points.isEmpty() || stroke.width <= 0f) return
        val cached = strokeCache.get(stroke.id) ?: buildStroke(stroke).also {
            strokeCache.put(stroke.id, it)
        }
        renderStroke(canvas, stroke, cached)
    }

    private fun renderStroke(canvas: Canvas, stroke: StrokeElement, cached: CachedStroke) {
        if (stroke.points.isEmpty() || stroke.width <= 0f) return
        val colorAlpha = stroke.color.alphaFraction()
        val elementAlpha = stroke.opacity * colorAlpha

        if (stroke.tool == ToolType.ERASER) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = max(0.6f, stroke.width)
            paint.xfermode = clearXfer
            if (cached.isDot) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(
                    stroke.points.first().x, stroke.points.first().y, stroke.width / 2f, paint
                )
            } else {
                canvas.drawPath(cached.path, paint)
            }
            return
        }

        val paint = basePaint(stroke, elementAlpha)
        val pressureWidth = stroke.width * cached.avgPressure.coerceIn(0.35f, 1.4f)
        paint.strokeWidth = max(0.55f, pressureWidth)

        if (cached.isDot) {
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                stroke.points.first().x,
                stroke.points.first().y,
                max(0.55f, pressureWidth / 2f),
                paint
            )
            return
        }

        when (stroke.tool) {
            ToolType.BRUSH -> {
                paint.alpha = (elementAlpha * 0.18f * 255).toInt().coerceIn(1, 255)
                paint.strokeWidth = max(1f, pressureWidth * 1.8f)
                canvas.drawPath(cached.path, paint)
                paint.alpha = (elementAlpha * 0.55f * 255).toInt().coerceIn(1, 255)
                paint.strokeWidth = max(1f, pressureWidth * 1.05f)
                canvas.drawPath(cached.path, paint)
                paint.alpha = (elementAlpha * 0.9f * 255).toInt().coerceIn(1, 255)
                paint.strokeWidth = max(0.8f, pressureWidth * 0.55f)
                canvas.drawPath(cached.path, paint)
            }
            ToolType.PEN -> {
                canvas.drawPath(cached.path, paint)
                // A slightly brighter core gives ink a premium, dense edge.
                paint.alpha = (elementAlpha * 0.35f * 255).toInt().coerceIn(1, 255)
                paint.strokeWidth = max(0.5f, pressureWidth * 0.5f)
                canvas.drawPath(cached.path, paint)
            }
            else -> canvas.drawPath(cached.path, paint)
        }
    }

    private fun buildStroke(stroke: StrokeElement): CachedStroke {
        val points = stroke.points
        val avg = (points.sumOf { it.pressure.toDouble() } / points.size).toFloat()
            .takeIf { it.isFinite() } ?: 1f
        val path = Path()
        if (points.size == 1) {
            path.addCircle(points[0].x, points[0].y, max(0.5f, stroke.width / 2f), Path.Direction.CW)
            return CachedStroke(path, avg, true)
        }
        path.moveTo(points[0].x, points[0].y)
        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
        } else {
            for (i in 1 until points.size - 1) {
                val p = points[i]
                val next = points[i + 1]
                path.quadTo(p.x, p.y, (p.x + next.x) / 2f, (p.y + next.y) / 2f)
            }
            val last = points.last()
            path.lineTo(last.x, last.y)
        }
        return CachedStroke(path, avg, false)
    }

    private fun drawShape(canvas: Canvas, shape: ShapeElement) {
        val left = min(shape.x1, shape.x2)
        val top = min(shape.y1, shape.y2)
        val right = max(shape.x1, shape.x2)
        val bottom = max(shape.y1, shape.y2)
        val alpha = shape.opacity * shape.strokeColor.alphaFraction()

        if (shape.fillColor != null && shape.kind != ShapeKind.LINE && shape.kind != ShapeKind.ARROW) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            fill.style = Paint.Style.FILL
            fill.color = (shape.fillColor and 0x00FFFFFFL).toInt() or (0xFF shl 24)
            fill.alpha = (shape.opacity * shape.fillColor.alphaFraction() * 255f).toInt()
                .coerceIn(0, 255)
            when (shape.kind) {
                ShapeKind.RECT -> canvas.drawRect(left, top, right, bottom, fill)
                ShapeKind.ELLIPSE -> canvas.drawOval(RectF(left, top, right, bottom), fill)
                else -> Unit
            }
        }

        val paint = basePaint(shape, alpha)
        paint.strokeWidth = max(0.5f, shape.width)
        when (shape.kind) {
            ShapeKind.RECT -> canvas.drawRect(left, top, right, bottom, paint)
            ShapeKind.ELLIPSE -> canvas.drawOval(RectF(left, top, right, bottom), paint)
            ShapeKind.LINE -> canvas.drawLine(shape.x1, shape.y1, shape.x2, shape.y2, paint)
            ShapeKind.ARROW -> {
                canvas.drawLine(shape.x1, shape.y1, shape.x2, shape.y2, paint)
                val angle = atan2(shape.y2 - shape.y1, shape.x2 - shape.x1)
                val head = (12f + shape.width * 2.2f).coerceAtLeast(8f)
                val a1 = angle + Math.toRadians(150.0).toFloat()
                val a2 = angle - Math.toRadians(150.0).toFloat()
                canvas.drawLine(
                    shape.x2, shape.y2,
                    shape.x2 + cos(a1) * head, shape.y2 + sin(a1) * head, paint
                )
                canvas.drawLine(
                    shape.x2, shape.y2,
                    shape.x2 + cos(a2) * head, shape.y2 + sin(a2) * head, paint
                )
            }
        }
    }

    private fun drawPolygon(canvas: Canvas, polygon: PolygonElement) {
        if (polygon.points.size < 2) return
        val path = polygonCache.get(polygon.id) ?: Path().apply {
            moveTo(polygon.points[0].x, polygon.points[0].y)
            for (i in 1 until polygon.points.size) lineTo(polygon.points[i].x, polygon.points[i].y)
            if (polygon.closed) close()
        }.also { polygonCache.put(polygon.id, it) }

        if (polygon.closed && polygon.fillColor != null) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
            fill.style = Paint.Style.FILL
            fill.color = (polygon.fillColor and 0x00FFFFFFL).toInt() or (0xFF shl 24)
            fill.alpha = (polygon.opacity * polygon.fillColor.alphaFraction() * 255f).toInt()
                .coerceIn(0, 255)
            canvas.drawPath(path, fill)
        }
        if (polygon.width > 0f) {
            val paint = basePaint(polygon, polygon.opacity * polygon.strokeColor.alphaFraction())
            paint.strokeWidth = max(0.5f, polygon.width)
            canvas.drawPath(path, paint)
        }
    }

    private fun drawText(canvas: Canvas, text: TextElement) {
        if (text.text.isEmpty() || text.size <= 0f) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        paint.color = (text.color and 0x00FFFFFFL).toInt() or (0xFF shl 24)
        paint.alpha = (text.opacity * text.color.alphaFraction() * 255f).toInt().coerceIn(0, 255)
        paint.textSize = text.size
        paint.typeface = if (text.bold) AppFonts.bold() else AppFonts.regular()
        val leading = text.size * 1.18f
        text.text.split("\n").forEachIndexed { i, line ->
            canvas.drawText(line, text.x, text.y + i * leading, paint)
        }
    }

    private fun drawRaster(canvas: Canvas, raster: RasterElement) {
        if (raster.bitmap.isRecycled) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.alpha = (raster.opacity.coerceIn(0f, 1f) * 255f).toInt()
        canvas.drawBitmap(raster.bitmap, raster.x.toFloat(), raster.y.toFloat(), paint)
    }

    fun samplePixel(
        frame: Frame,
        layers: List<LayerMeta>,
        width: Int,
        height: Int,
        background: Long,
        x: Float,
        y: Float
    ): Long? {
        if (x < 0 || y < 0 || x >= width || y >= height) return null
        val bitmap = renderCurrentFrame(frame, layers, width, height, background)
        return try {
            val argb = bitmap.getPixel(x.toInt().coerceIn(0, width - 1), y.toInt().coerceIn(0, height - 1))
            (argb.toLong() and 0xFFFFFFFFL)
        } finally {
            bitmap.recycle()
        }
    }

    data class FillResult(val bitmap: Bitmap, val x: Int, val y: Int)

    /**
     * Flood fills the active layer at project resolution and returns a cropped
     * bitmap patch, or null when nothing changed. Runs off the UI thread.
     */
    fun floodFillPatch(
        source: Bitmap,
        startX: Int,
        startY: Int,
        fillColor: Long,
        tolerance: Int
    ): FillResult? {
        val w = source.width
        val h = source.height
        if (startX !in 0 until w || startY !in 0 until h) return null
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val seed = pixels[startY * w + startX]
        val fill = fillColor.toInt()
        if (seed == fill) return null

        val tol = tolerance.coerceIn(0, 255)
        val visited = BooleanArray(w * h)
        val filled = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        val start = startY * w + startX
        queue[tail++] = start
        visited[start] = true
        var minX = startX
        var maxX = startX
        var minY = startY
        var maxY = startY
        var count = 0

        while (head < tail) {
            val index = queue[head++]
            val color = pixels[index]
            if (maxChannelDistance(color, seed) > tol) continue

            filled[index] = true
            pixels[index] = fill
            count++
            val px = index % w
            val py = index / w
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py

            if (px > 0) {
                val n = index - 1
                if (!visited[n]) { visited[n] = true; queue[tail++] = n }
            }
            if (px < w - 1) {
                val n = index + 1
                if (!visited[n]) { visited[n] = true; queue[tail++] = n }
            }
            if (py > 0) {
                val n = index - w
                if (!visited[n]) { visited[n] = true; queue[tail++] = n }
            }
            if (py < h - 1) {
                val n = index + w
                if (!visited[n]) { visited[n] = true; queue[tail++] = n }
            }
        }

        if (count == 0) return null
        val patchW = maxX - minX + 1
        val patchH = maxY - minY + 1
        val patch = IntArray(patchW * patchH)
        for (y in minY..maxY) {
            val srcRow = y * w
            val outRow = (y - minY) * patchW
            for (x in minX..maxX) {
                if (filled[srcRow + x]) patch[outRow + (x - minX)] = fill
            }
        }
        val bitmap = Bitmap.createBitmap(patch, patchW, patchH, Bitmap.Config.ARGB_8888).also {
            it.density = source.density
        }
        return FillResult(bitmap, minX, minY)
    }

    private fun maxChannelDistance(a: Int, b: Int): Int {
        val da = abs(((a shr 24) and 0xFF) - ((b shr 24) and 0xFF))
        val dr = abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF))
        val dg = abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF))
        val db = abs((a and 0xFF) - (b and 0xFF))
        return max(max(da, dr), max(dg, db))
    }
}
