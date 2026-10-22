package com.anymate.studio.engine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.anymate.studio.model.Frame
import com.anymate.studio.model.FrameLayer
import com.anymate.studio.model.LayerMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ExportSnapshot(
    val projectName: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val background: Long,
    val frames: List<Frame>,
    val layers: List<LayerMeta>,
    val currentIndex: Int,
    val grid: GridSpec = GridSpec()
)

class ExportManager(private val context: Context) {

    val exportsDir: File
        get() = File(context.cacheDir, "exports").apply { mkdirs() }

    fun snapshotOnMain(engine: AnimationEngine): ExportSnapshot {
        val layerCopies = engine.layers.map { meta ->
            LayerMeta(meta.id, meta.name).apply {
                visible = meta.visible
                locked = false
                opacity = meta.opacity
            }
        }
        val frameCopies = engine.frames.map { frame ->
            val copy = Frame(frame.id + 7_000_000L, 0)
            frame.layers.forEach { layer ->
                val layerCopy = FrameLayer()
                layerCopy.elements.addAll(layer.elements)
                copy.layers.add(layerCopy)
            }
            copy
        }
        return ExportSnapshot(
            projectName = engine.projectName,
            width = engine.canvasWidth,
            height = engine.canvasHeight,
            fps = engine.fps,
            background = engine.background,
            frames = frameCopies,
            layers = layerCopies,
            currentIndex = engine.currentIndex,
            grid = GridSpec(engine.gridEnabled, engine.gridSpacing, engine.gridColor)
        )
    }

    suspend fun exportGif(
        engine: AnimationEngine,
        maxDimension: Int = 720,
        onProgress: suspend (Float, String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.Default) {
        val snapshot = withContext(Dispatchers.Main) { snapshotOnMain(engine) }
        require(snapshot.frames.isNotEmpty()) { "There are no frames to export." }
        val scale = maxDimension.toFloat() / max(snapshot.width, snapshot.height)
        val outW = (snapshot.width * scale).roundToInt().coerceAtLeast(2)
        val outH = (snapshot.height * scale).roundToInt().coerceAtLeast(2)

        onProgress(0.02f, "Building colour palette")
        val sampleCount = minOf(10, snapshot.frames.size)
        val samples = ArrayList<Bitmap>(sampleCount)
        try {
            for (i in 0 until sampleCount) {
                currentCoroutineContext().ensureActive()
                val idx = (i * (snapshot.frames.size - 1) / max(1, sampleCount - 1))
                    .coerceIn(0, snapshot.frames.lastIndex)
                val sampleScale = 180f / max(snapshot.width, snapshot.height)
                samples.add(
                    Rasterizer.renderFrame(
                        snapshot.frames[idx], snapshot.layers,
                        snapshot.width, snapshot.height, snapshot.background,
                        (snapshot.width * sampleScale).roundToInt().coerceAtLeast(2),
                        (snapshot.height * sampleScale).roundToInt().coerceAtLeast(2),
                        includeBackground = true,
                        grid = snapshot.grid
                    )
                )
            }
            val palette = GifQuantizer.buildPalette(samples)
            samples.forEach { it.recycle() }
            samples.clear()

            onProgress(0.06f, "Encoding ${snapshot.frames.size} frames")
            val file = File(exportsDir, "anymate_${timestamp()}.gif")
            FileOutputStream(file).use { stream ->
                val encoder = GifEncoder(stream, outW, outH, palette, snapshot.fps, loop = true)
                snapshot.frames.forEachIndexed { index, frame ->
                    currentCoroutineContext().ensureActive()
                    val bitmap = Rasterizer.renderFrame(
                        frame, snapshot.layers,
                        snapshot.width, snapshot.height, snapshot.background,
                        outW, outH, includeBackground = true, grid = snapshot.grid
                    )
                    encoder.writeFrame(bitmap)
                    bitmap.recycle()
                    if (index % 2 == 0 || index == snapshot.frames.lastIndex) {
                        val p = 0.06f + 0.92f * (index + 1) / snapshot.frames.size
                        onProgress(p, "Encoding frame ${index + 1}/${snapshot.frames.size}")
                    }
                }
                encoder.finish()
            }
            onProgress(1f, "GIF ready")
            file
        } finally {
            samples.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    suspend fun exportZip(
        engine: AnimationEngine,
        onProgress: suspend (Float, String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.Default) {
        val snapshot = withContext(Dispatchers.Main) { snapshotOnMain(engine) }
        require(snapshot.frames.isNotEmpty()) { "There are no frames to export." }
        val file = File(exportsDir, "anymate_frames_${timestamp()}.zip")
        FileOutputStream(file).use { fileStream ->
            ZipOutputStream(BufferedOutputStream(fileStream)).use { zip ->
                snapshot.frames.forEachIndexed { index, frame ->
                    currentCoroutineContext().ensureActive()
                    val bitmap = Rasterizer.renderFrame(
                        frame, snapshot.layers,
                        snapshot.width, snapshot.height, snapshot.background,
                        snapshot.width, snapshot.height, includeBackground = true, grid = snapshot.grid
                    )
                    val png = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
                    bitmap.recycle()
                    val entry = ZipEntry("frame_%04d.png".format(index + 1))
                    zip.putNextEntry(entry)
                    zip.write(png.toByteArray())
                    zip.closeEntry()
                    if (index % 2 == 0 || index == snapshot.frames.lastIndex) {
                        onProgress(
                            (index + 1).toFloat() / snapshot.frames.size,
                            "Packing frame ${index + 1}/${snapshot.frames.size}"
                        )
                    }
                }
            }
        }
        onProgress(1f, "PNG sequence ready")
        file
    }

    suspend fun exportPng(
        engine: AnimationEngine,
        onProgress: suspend (Float, String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.Default) {
        val snapshot = withContext(Dispatchers.Main) { snapshotOnMain(engine) }
        val frame = snapshot.frames.getOrNull(snapshot.currentIndex)
            ?: throw IllegalStateException("No current frame.")
        onProgress(0.3f, "Rendering current frame")
        val bitmap = Rasterizer.renderFrame(
            frame, snapshot.layers,
            snapshot.width, snapshot.height, snapshot.background,
            snapshot.width, snapshot.height, includeBackground = true, grid = snapshot.grid
        )
        val file = File(exportsDir, "anymate_frame_${timestamp()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        onProgress(1f, "PNG ready")
        file
    }

    /**
     * Renders every frame into one contact sheet. Agents use it to review a
     * whole animation in a single look.
     */
    suspend fun renderSpriteSheet(
        engine: AnimationEngine,
        columns: Int = 6,
        cellSize: Int = 180
    ): Bitmap = withContext(Dispatchers.Default) {
        val snapshot = withContext(Dispatchers.Main) { snapshotOnMain(engine) }
        val frames = snapshot.frames
        require(frames.isNotEmpty()) { "There are no frames to render." }
        val cols = columns.coerceIn(1, 12)
        val cell = cellSize.coerceIn(48, 640)
        val rows = ((frames.size + cols - 1) / cols).coerceAtLeast(1)
        val ratio = snapshot.width.toFloat() / snapshot.height
        val cellW: Int
        val cellH: Int
        if (ratio >= 1f) {
            cellW = cell
            cellH = (cell / ratio).toInt().coerceAtLeast(24)
        } else {
            cellH = cell
            cellW = (cell * ratio).toInt().coerceAtLeast(24)
        }
        val sheet = Bitmap.createBitmap(cellW * cols, cellH * rows, Bitmap.Config.ARGB_8888)
        val sheetCanvas = android.graphics.Canvas(sheet)
        sheetCanvas.drawColor(snapshot.background.toInt())
        frames.forEachIndexed { index, frame ->
            val column = index % cols
            val row = index / cols
            val tile = Rasterizer.renderFrame(
                frame, snapshot.layers,
                snapshot.width, snapshot.height, snapshot.background,
                cellW, cellH, includeBackground = false, grid = snapshot.grid
            )
            sheetCanvas.drawBitmap(
                tile,
                (column * cellW).toFloat(),
                (row * cellH).toFloat(),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            )
            tile.recycle()
        }
        sheet
    }

    fun share(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun cleanupOldExports() {
        val cutoff = System.currentTimeMillis() - 3L * 24 * 60 * 60 * 1000
        exportsDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
