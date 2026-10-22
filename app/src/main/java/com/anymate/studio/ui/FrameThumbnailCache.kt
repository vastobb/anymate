package com.anymate.studio.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.engine.Rasterizer
import com.anymate.studio.model.Frame
import com.anymate.studio.model.FrameLayer
import com.anymate.studio.model.LayerMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Small, lazy frame thumbnails. Rendering happens against a copied snapshot so
 * an agent can keep writing at 60 fps without racing the thumbnail worker.
 */
object FrameThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(96) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    suspend fun load(engine: AnimationEngine, frame: Frame, size: Int = 176): ImageBitmap? {
        val key = buildString {
            append(frame.id).append(':').append(frame.revision)
            append(':').append(engine.thumbnailEpoch)
            append(':').append(engine.background)
            append(':').append(engine.canvasWidth).append('x').append(engine.canvasHeight)
            append(':').append(size)
        }
        cache.get(key)?.let { return it.asImageBitmap() }

        val snapshot = withContext(Dispatchers.Main) {
            val layersCopy = engine.layers.map { meta ->
                LayerMeta(meta.id, meta.name).apply {
                    visible = meta.visible
                    opacity = meta.opacity
                }
            }
            val frameCopy = Frame(frame.id, 0)
            frame.layers.forEach { layer ->
                val copy = FrameLayer()
                copy.elements.addAll(layer.elements)
                frameCopy.layers.add(copy)
            }
            ThumbSnapshot(
                frameCopy, layersCopy, engine.canvasWidth, engine.canvasHeight,
                engine.background, size
            )
        }
        return withContext(Dispatchers.Default) {
            val ratio = snapshot.width.toFloat() / snapshot.height
            val outW = if (ratio >= 1f) snapshot.size else (snapshot.size * ratio).roundToInt().coerceAtLeast(32)
            val outH = if (ratio >= 1f) (snapshot.size / ratio).roundToInt().coerceAtLeast(32) else snapshot.size
            val bitmap = Rasterizer.renderFrame(
                snapshot.frame,
                snapshot.layers,
                snapshot.width,
                snapshot.height,
                snapshot.background,
                max(32, outW),
                max(32, outH),
                includeBackground = true
            )
            cache.put(key, bitmap)
            bitmap.asImageBitmap()
        }
    }

    private data class ThumbSnapshot(
        val frame: Frame,
        val layers: List<LayerMeta>,
        val width: Int,
        val height: Int,
        val background: Long,
        val size: Int
    )

    fun clear() {
        cache.evictAll()
    }
}
