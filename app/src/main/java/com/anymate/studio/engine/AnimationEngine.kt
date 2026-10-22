package com.anymate.studio.engine

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.anymate.studio.model.CanvasPreset
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
import com.anymate.studio.util.withAlpha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

data class AgentEvent(
    val id: Long,
    val tool: String,
    val summary: String,
    val ok: Boolean,
    val at: Long
)

/**
 * The live document. All state writes happen on the main thread; MCP/agent
 * calls are marshalled here by ToolRegistry. Rendering reads the same model
 * directly, so agent edits appear on the artist's canvas in the same frame.
 */
class AnimationEngine {

    companion object {
        const val MAX_FRAMES = 1200
        const val MAX_STROKE_POINTS = 20_000
        val DefaultLayers = listOf("Ink", "Colour", "FX")
    }

    private val ids = AtomicLong(10_000L)
    private fun nextId(): Long = ids.incrementAndGet()

    // ---- Document ---------------------------------------------------------
    var projectName by mutableStateOf("Untitled")
    var canvasWidth by mutableIntStateOf(900)
        private set
    var canvasHeight by mutableIntStateOf(900)
        private set
    var fps by mutableIntStateOf(24)
        private set
    var background by mutableLongStateOf(0xFFF7F4EC)
        private set

    val frames = mutableStateListOf<Frame>()
    val layers = mutableStateListOf<LayerMeta>()
    var currentIndex by mutableIntStateOf(0)
        private set
    var currentLayer by mutableIntStateOf(0)
        private set

    // ---- Drawing tool -----------------------------------------------------
    var selectedTool by mutableStateOf(ToolType.PENCIL)
        private set
    var brushSize by mutableFloatStateOf(5f)
        private set
    var brushOpacity by mutableFloatStateOf(1f)
        private set
    var strokeColor by mutableLongStateOf(0xFF7C3AED)
        private set
    var shapeKind by mutableStateOf(ShapeKind.ELLIPSE)
        private set
    var shapeFilled by mutableStateOf(false)
        private set

    private val toolSizes = mutableMapOf(
        ToolType.PENCIL to 5f,
        ToolType.PEN to 7f,
        ToolType.BRUSH to 34f,
        ToolType.ERASER to 38f,
        ToolType.FILL to 10f,
        ToolType.SHAPE to 5f,
        ToolType.TEXT to 64f,
        ToolType.EYEDROPPER to 10f,
        ToolType.HAND to 10f
    )

    // ---- Playback / view --------------------------------------------------
    var isPlaying by mutableStateOf(false)
        private set
    var loop by mutableStateOf(true)
        private set
    var onionSkin by mutableStateOf(false)
        private set
    var zoom by mutableFloatStateOf(1f)
        private set
    var panX by mutableFloatStateOf(0f)
        private set
    var panY by mutableFloatStateOf(0f)
        private set

    // ---- Recomposition signals -------------------------------------------
    var revision by mutableIntStateOf(0)
        private set
    var thumbnailEpoch by mutableIntStateOf(0)
        private set
    var historyEpoch by mutableIntStateOf(0)
        private set
    var undoCount by mutableIntStateOf(0)
        private set
    var redoCount by mutableIntStateOf(0)
        private set

    // ---- Agent / MCP live state ------------------------------------------
    val agentEvents = mutableStateListOf<AgentEvent>()
    var agentBusy by mutableStateOf(false)
        private set
    var agentLabel by mutableStateOf("")
        private set
    var agentProgress by mutableFloatStateOf(0f)
        private set
    var lastToolAt by mutableLongStateOf(0L)
        private set
    var toolCallCount by mutableIntStateOf(0)
        private set

    val canUndo: Boolean get() = undoCount > 0
    val canRedo: Boolean get() = redoCount > 0
    val frameCount: Int get() = frames.size

    private sealed interface UndoOp {
        data class Elements(
            val frameId: Long,
            val layerIndex: Int,
            val before: List<Element>,
            val after: List<Element>
        ) : UndoOp

        data class FrameInserted(val frame: Frame, val index: Int) : UndoOp
        data class FrameRemoved(val frame: Frame, val index: Int) : UndoOp
    }

    private data class UndoGroup(val label: String, val ops: List<UndoOp>)

    private val undoStack = ArrayDeque<UndoGroup>()
    private val redoStack = ArrayDeque<UndoGroup>()
    private var groupOps: MutableList<UndoOp>? = null
    private var groupLabel: String = ""
    private var groupDepth: Int = 0

    init {
        DefaultLayers.forEach { layers.add(LayerMeta(nextId(), it)) }
        frames.add(Frame(nextId(), layers.size))
    }

    // ---- Accessors --------------------------------------------------------
    fun frameOrNull(index: Int = currentIndex): Frame? = frames.getOrNull(index)

    fun currentFrame(): Frame = frames[currentIndex.coerceIn(0, frames.lastIndex.coerceAtLeast(0))]

    fun layerOrNull(index: Int = currentLayer): LayerMeta? = layers.getOrNull(index)

    fun layerLocked(index: Int): Boolean = layers.getOrNull(index)?.locked == true

    fun sizeRangeFor(tool: ToolType = selectedTool): ClosedFloatingPointRange<Float> = when (tool) {
        ToolType.TEXT -> 12f..240f
        ToolType.BRUSH, ToolType.ERASER -> 4f..160f
        ToolType.PENCIL, ToolType.PEN -> 1f..48f
        ToolType.SHAPE -> 1f..40f
        else -> 1f..80f
    }

    // ---- Project ----------------------------------------------------------
    fun newProject(
        name: String = "Untitled",
        preset: CanvasPreset = CanvasPreset("Square", 900, 900),
        fpsValue: Int = 24,
        backgroundColor: Long = 0xFFF7F4EC
    ) {
        projectName = name.ifBlank { "Untitled" }
        canvasWidth = preset.width.coerceIn(240, 2048)
        canvasHeight = preset.height.coerceIn(240, 2048)
        fps = fpsValue.coerceIn(1, 60)
        background = backgroundColor
        frames.clear()
        layers.clear()
        DefaultLayers.forEach { layers.add(LayerMeta(nextId(), it)) }
        frames.add(Frame(nextId(), layers.size))
        currentIndex = 0
        currentLayer = 0
        isPlaying = false
        zoom = 1f
        panX = 0f
        panY = 0f
        undoStack.clear()
        redoStack.clear()
        groupDepth = 0
        groupOps = null
        refreshHistory()
        Rasterizer.clearCaches()
        revision++
        thumbnailEpoch++
        historyEpoch++
    }

    fun renameProject(name: String) {
        projectName = name.ifBlank { "Untitled" }
    }

    fun updateFps(value: Int) {
        fps = value.coerceIn(1, 60)
    }

    fun updateBackground(color: Long) {
        background = color
        revision++
        thumbnailEpoch++
    }

    fun updateLoop(value: Boolean) {
        loop = value
    }

    fun updateOnionSkin(value: Boolean) {
        onionSkin = value
        revision++
    }

    fun bumpCanvas() {
        revision++
    }

    fun bumpThumbnails() {
        thumbnailEpoch++
        revision++
    }

    // ---- Tools ------------------------------------------------------------
    fun selectTool(tool: ToolType) {
        if (tool == selectedTool) return
        toolSizes[selectedTool] = brushSize
        selectedTool = tool
        brushSize = (toolSizes[tool] ?: brushSize)
            .coerceIn(sizeRangeFor(tool).start, sizeRangeFor(tool).endInclusive)
        revision++
    }

    fun updateBrushSize(value: Float) {
        val range = sizeRangeFor(selectedTool)
        brushSize = value.coerceIn(range.start, range.endInclusive)
        toolSizes[selectedTool] = brushSize
    }

    fun updateBrushOpacity(value: Float) {
        brushOpacity = value.coerceIn(0.02f, 1f)
    }

    fun setColor(color: Long) {
        strokeColor = color
    }

    fun updateShapeKind(kind: ShapeKind) {
        shapeKind = kind
        selectedTool = ToolType.SHAPE
    }

    fun updateShapeFilled(value: Boolean) {
        shapeFilled = value
    }

    // ---- View -------------------------------------------------------------
    fun updateZoom(value: Float) {
        zoom = value.coerceIn(0.5f, 8f)
    }

    fun panBy(dx: Float, dy: Float) {
        panX += dx
        panY += dy
    }

    fun panTo(x: Float, y: Float) {
        panX = x
        panY = y
    }

    fun resetView() {
        zoom = 1f
        panX = 0f
        panY = 0f
    }

    // ---- Frames -----------------------------------------------------------
    fun addFrame(
        index: Int? = null,
        copyCurrent: Boolean = false,
        select: Boolean = true
    ): Frame {
        require(frames.size < MAX_FRAMES) { "Frame limit reached ($MAX_FRAMES)." }
        val at = (index ?: (currentIndex + 1)).coerceIn(0, frames.size)
        val frame = if (copyCurrent) {
            frames[currentIndex.coerceIn(0, frames.lastIndex)].deepCopy(nextId())
        } else {
            Frame(nextId(), layers.size)
        }
        frames.add(at, frame)
        if (select) {
            currentIndex = at
            isPlaying = false
        }
        record(UndoOp.FrameInserted(frame, at))
        afterStructureChange(select)
        return frame
    }

    fun duplicateFrame(index: Int = -1): Frame {
        require(frames.size < MAX_FRAMES) { "Frame limit reached ($MAX_FRAMES)." }
        val sourceIndex = if (index < 0) currentIndex else index
        val source = frames.getOrNull(sourceIndex) ?: throw IllegalArgumentException("Frame $sourceIndex does not exist.")
        val frame = source.deepCopy(nextId())
        val at = (sourceIndex + 1).coerceAtMost(frames.size)
        frames.add(at, frame)
        currentIndex = at
        isPlaying = false
        record(UndoOp.FrameInserted(frame, at))
        afterStructureChange(true)
        return frame
    }

    fun deleteFrame(index: Int = -1): Boolean {
        val idx = if (index < 0) currentIndex else index
        val frame = frames.getOrNull(idx) ?: throw IllegalArgumentException("Frame $idx does not exist.")
        if (frames.size <= 1) {
            clearFrame(0)
            return false
        }
        val wasCurrent = currentIndex == idx
        frames.removeAt(idx)
        if (currentIndex > idx) {
            currentIndex--
        } else if (wasCurrent) {
            currentIndex = idx.coerceAtMost(frames.lastIndex)
        }
        isPlaying = false
        record(UndoOp.FrameRemoved(frame, idx))
        afterStructureChange(true)
        return true
    }

    fun selectFrame(index: Int) {
        if (index !in frames.indices) throw IllegalArgumentException("Frame $index does not exist.")
        currentIndex = index
        isPlaying = false
        revision++
    }

    fun selectLayer(index: Int) {
        if (index !in layers.indices) throw IllegalArgumentException("Layer $index does not exist.")
        currentLayer = index
        revision++
    }

    fun clearFrame(index: Int = -1) {
        val idx = if (index < 0) currentIndex else index
        val frame = frames.getOrNull(idx) ?: throw IllegalArgumentException("Frame $idx does not exist.")
        val ownGroup = groupDepth == 0
        if (ownGroup) beginUndoGroup("Clear frame")
        frame.layers.forEachIndexed { layerIndex, layer ->
            if (layer.elements.isNotEmpty()) {
                val before = layer.elements.toList()
                layer.elements.clear()
                record(UndoOp.Elements(frame.id, layerIndex, before, emptyList()))
            }
        }
        currentIndex = idx
        frame.bump()
        if (ownGroup) endUndoGroup()
        revision++
        thumbnailEpoch++
        isPlaying = false
    }

    fun clearLayer(frameIndex: Int = -1, layerIndex: Int = -1) {
        val fi = if (frameIndex < 0) currentIndex else frameIndex
        val li = if (layerIndex < 0) currentLayer else layerIndex
        val frame = frames.getOrNull(fi) ?: throw IllegalArgumentException("Frame $fi does not exist.")
        val layer = frame.layers.getOrNull(li) ?: throw IllegalArgumentException("Layer $li does not exist.")
        if (layer.elements.isEmpty()) return
        val before = layer.elements.toList()
        layer.elements.clear()
        record(UndoOp.Elements(frame.id, li, before, emptyList()))
        currentIndex = fi
        currentLayer = li
        frame.bump()
        revision++
        thumbnailEpoch++
        isPlaying = false
    }

    // ---- Layers -----------------------------------------------------------
    fun addLayer(name: String? = null): Int {
        val meta = LayerMeta(nextId(), name?.ifBlank { null } ?: "Layer ${layers.size + 1}")
        layers.add(meta)
        frames.forEach { it.layers.add(FrameLayer()) }
        currentLayer = layers.lastIndex
        thumbnailEpoch++
        revision++
        return currentLayer
    }

    fun deleteLayer(index: Int = -1): Boolean {
        val idx = if (index < 0) currentLayer else index
        if (idx !in layers.indices) throw IllegalArgumentException("Layer $idx does not exist.")
        if (layers.size <= 1) throw IllegalStateException("A project needs at least one layer.")
        layers.removeAt(idx)
        frames.forEach { frame ->
            if (idx in frame.layers.indices) frame.layers.removeAt(idx)
            frame.bump()
        }
        currentLayer = currentLayer.coerceAtMost(layers.lastIndex)
        clearHistory()
        thumbnailEpoch++
        revision++
        return true
    }

    fun moveLayer(from: Int, to: Int) {
        if (from !in layers.indices || to !in layers.indices || from == to) return
        val meta = layers.removeAt(from)
        layers.add(to, meta)
        frames.forEach { frame ->
            if (from in frame.layers.indices) {
                val layer = frame.layers.removeAt(from)
                frame.layers.add(to.coerceIn(0, frame.layers.size), layer)
            }
            frame.bump()
        }
        currentLayer = to
        clearHistory()
        thumbnailEpoch++
        revision++
    }

    fun setLayerVisible(index: Int, visible: Boolean) {
        val meta = layers.getOrNull(index) ?: throw IllegalArgumentException("Layer $index does not exist.")
        meta.visible = visible
        thumbnailEpoch++
        revision++
    }

    fun setLayerLocked(index: Int, locked: Boolean) {
        val meta = layers.getOrNull(index) ?: throw IllegalArgumentException("Layer $index does not exist.")
        meta.locked = locked
        revision++
    }

    fun setLayerOpacity(index: Int, opacity: Float) {
        val meta = layers.getOrNull(index) ?: throw IllegalArgumentException("Layer $index does not exist.")
        meta.opacity = opacity.coerceIn(0f, 1f)
        thumbnailEpoch++
        revision++
    }

    fun renameLayer(index: Int, name: String) {
        val meta = layers.getOrNull(index) ?: throw IllegalArgumentException("Layer $index does not exist.")
        meta.name = name.ifBlank { "Layer ${index + 1}" }
        revision++
    }

    // ---- Painting ---------------------------------------------------------
    fun commitStroke(
        points: List<Vec2>,
        tool: ToolType = selectedTool,
        color: Long = strokeColor,
        width: Float = brushSize,
        opacity: Float = brushOpacity,
        frameIndex: Int = -1,
        layerIndex: Int = -1,
        select: Boolean = true
    ): Boolean {
        if (points.isEmpty()) return false
        val safePoints = if (points.size > MAX_STROKE_POINTS) {
            points.filterIndexed { i, _ -> i % (points.size / MAX_STROKE_POINTS + 1) == 0 }
        } else points
        val element = StrokeElement(
            id = nextId(),
            points = safePoints,
            color = color,
            width = width.coerceIn(0.3f, 240f),
            opacity = opacity.coerceIn(0f, 1f),
            tool = tool
        )
        return addElement(element, frameIndex, layerIndex, select)
    }

    fun addElement(
        element: Element,
        frameIndex: Int = -1,
        layerIndex: Int = -1,
        select: Boolean = true
    ): Boolean = addElements(listOf(element), frameIndex, layerIndex, select) > 0

    fun addElements(
        elements: List<Element>,
        frameIndex: Int = -1,
        layerIndex: Int = -1,
        select: Boolean = true
    ): Int {
        if (elements.isEmpty()) return 0
        val fi = if (frameIndex < 0) currentIndex else frameIndex
        val li = if (layerIndex < 0) currentLayer else layerIndex
        val frame = frames.getOrNull(fi) ?: throw IllegalArgumentException("Frame $fi does not exist.")
        val layer = frame.layers.getOrNull(li) ?: throw IllegalArgumentException("Layer $li does not exist.")
        val meta = layers.getOrNull(li) ?: throw IllegalArgumentException("Layer $li does not exist.")
        if (meta.locked) throw IllegalStateException("Layer \"${meta.name}\" is locked. Unlock it first.")

        if (select) {
            currentIndex = fi
            currentLayer = li
            isPlaying = false
        }
        val before = layer.elements.toList()
        layer.elements.addAll(elements)
        record(UndoOp.Elements(frame.id, li, before, layer.elements.toList()))
        frame.bump()
        revision++
        return elements.size
    }

    fun addShape(
        kind: ShapeKind,
        start: Vec2,
        end: Vec2,
        filled: Boolean = shapeFilled,
        color: Long = strokeColor,
        width: Float = brushSize,
        opacity: Float = brushOpacity,
        frameIndex: Int = -1,
        layerIndex: Int = -1,
        select: Boolean = true
    ): Boolean {
        val element = ShapeElement(
            id = nextId(),
            kind = kind,
            x1 = start.x,
            y1 = start.y,
            x2 = end.x,
            y2 = end.y,
            strokeColor = color,
            fillColor = if (filled) color else null,
            width = width.coerceIn(0.3f, 120f),
            opacity = opacity.coerceIn(0f, 1f)
        )
        return addElement(element, frameIndex, layerIndex, select)
    }

    fun addPolygon(
        points: List<Vec2>,
        closed: Boolean = true,
        strokeColor: Long = this.strokeColor,
        fillColor: Long? = null,
        width: Float = brushSize,
        opacity: Float = brushOpacity,
        frameIndex: Int = -1,
        layerIndex: Int = -1,
        select: Boolean = true
    ): Boolean {
        if (points.size < 2) return false
        val element = PolygonElement(
            id = nextId(),
            points = points,
            closed = closed,
            strokeColor = strokeColor,
            fillColor = fillColor,
            width = width.coerceIn(0f, 120f),
            opacity = opacity.coerceIn(0f, 1f)
        )
        return addElement(element, frameIndex, layerIndex, select)
    }

    fun addText(
        text: String,
        x: Float,
        y: Float,
        size: Float = brushSize,
        color: Long = strokeColor,
        frameIndex: Int = -1,
        layerIndex: Int = -1,
        select: Boolean = true
    ): Boolean {
        if (text.isBlank()) return false
        val element = TextElement(
            id = nextId(),
            text = text,
            x = x,
            y = y,
            size = size.coerceIn(4f, 600f),
            color = color
        )
        return addElement(element, frameIndex, layerIndex, select)
    }

    suspend fun fillAt(
        x: Float,
        y: Float,
        color: Long = strokeColor,
        tolerance: Int = 24,
        frameIndex: Int = -1,
        layerIndex: Int = -1
    ): Boolean {
        val fi = if (frameIndex < 0) currentIndex else frameIndex
        val li = if (layerIndex < 0) currentLayer else layerIndex
        val frame = frames.getOrNull(fi) ?: throw IllegalArgumentException("Frame $fi does not exist.")
        val layer = frame.layers.getOrNull(li) ?: throw IllegalArgumentException("Layer $li does not exist.")
        val meta = layers.getOrNull(li) ?: throw IllegalArgumentException("Layer $li does not exist.")
        if (meta.locked) throw IllegalStateException("Layer \"${meta.name}\" is locked.")
        if (x < 0 || y < 0 || x >= canvasWidth || y >= canvasHeight) return false

        val snapshot = layer.elements.toList()
        val result = withContext(Dispatchers.Default) {
            val bitmap = Rasterizer.renderElements(snapshot, canvasWidth, canvasHeight)
            try {
                Rasterizer.floodFillPatch(bitmap, x.toInt(), y.toInt(), color, tolerance)
            } finally {
                bitmap.recycle()
            }
        } ?: return false

        val raster = RasterElement(
            id = nextId(),
            bitmap = result.bitmap,
            x = result.x,
            y = result.y,
            opacity = 1f
        )
        return addElement(raster, fi, li, select = true)
    }

    suspend fun sampleColorAt(x: Float, y: Float, frameIndex: Int = -1): Long? {
        val fi = if (frameIndex < 0) currentIndex else frameIndex
        val frame = frames.getOrNull(fi) ?: return null
        val layersSnapshot = layers.toList()
        val frameSnapshot = frame
        return withContext(Dispatchers.Default) {
            Rasterizer.samplePixel(
                frameSnapshot,
                layersSnapshot,
                canvasWidth,
                canvasHeight,
                background,
                x,
                y
            )
        }
    }

    // ---- Playback ---------------------------------------------------------
    fun play() {
        if (frames.size < 1) return
        isPlaying = true
        revision++
    }

    fun pause() {
        isPlaying = false
        revision++
    }

    fun togglePlayback() {
        if (isPlaying) pause() else play()
    }

    fun advancePlayback(steps: Int = 1) {
        if (!isPlaying || frames.isEmpty()) return
        var next = currentIndex + steps.coerceAtLeast(1)
        if (next >= frames.size) {
            if (loop) {
                next %= frames.size
            } else {
                next = frames.lastIndex
                isPlaying = false
            }
        }
        currentIndex = next.coerceIn(0, frames.lastIndex)
    }

    // ---- Undo / redo ------------------------------------------------------
    fun beginUndoGroup(label: String) {
        groupDepth++
        if (groupDepth == 1) {
            groupOps = mutableListOf()
            groupLabel = label
        }
    }

    fun endUndoGroup() {
        if (groupDepth <= 0) return
        groupDepth--
        if (groupDepth == 0) {
            val ops = groupOps
            groupOps = null
            if (!ops.isNullOrEmpty()) commitGroup(UndoGroup(groupLabel.ifBlank { "Edit" }, ops))
            groupLabel = ""
        }
    }

    private fun record(op: UndoOp) {
        val group = groupOps
        if (group != null) {
            group.add(op)
        } else {
            commitGroup(UndoGroup("Edit", listOf(op)))
        }
    }

    private fun commitGroup(group: UndoGroup) {
        undoStack.addLast(group)
        while (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        refreshHistory()
    }

    private fun refreshHistory() {
        undoCount = undoStack.size
        redoCount = redoStack.size
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        groupOps = null
        groupDepth = 0
        refreshHistory()
    }

    fun undo(): Boolean {
        if (groupDepth > 0) return false
        val group = undoStack.removeLastOrNull() ?: return false
        applyGroup(group, undo = true)
        redoStack.addLast(group)
        refreshHistory()
        historyEpoch++
        return true
    }

    fun redo(): Boolean {
        if (groupDepth > 0) return false
        val group = redoStack.removeLastOrNull() ?: return false
        applyGroup(group, undo = false)
        undoStack.addLast(group)
        refreshHistory()
        historyEpoch++
        return true
    }

    private fun applyGroup(group: UndoGroup, undo: Boolean) {
        pause()
        val ops = if (undo) group.ops.asReversed() else group.ops
        for (op in ops) {
            when (op) {
                is UndoOp.Elements -> {
                    val frameIndex = frames.indexOfFirst { it.id == op.frameId }
                    if (frameIndex < 0) continue
                    val frame = frames[frameIndex]
                    val layer = frame.layers.getOrNull(op.layerIndex) ?: continue
                    layer.elements.clear()
                    layer.elements.addAll(if (undo) op.before else op.after)
                    currentIndex = frameIndex
                    currentLayer = op.layerIndex.coerceIn(0, layers.lastIndex.coerceAtLeast(0))
                    frame.bump()
                }
                is UndoOp.FrameInserted -> {
                    if (undo) {
                        val idx = frames.indexOfFirst { it === op.frame || it.id == op.frame.id }
                        if (idx >= 0) {
                            frames.removeAt(idx)
                            if (frames.isEmpty()) frames.add(Frame(nextId(), layers.size))
                            currentIndex = currentIndex.coerceIn(0, frames.lastIndex)
                        }
                    } else {
                        if (frames.none { it.id == op.frame.id }) {
                            frames.add(op.index.coerceIn(0, frames.size), op.frame)
                        }
                        currentIndex = frames.indexOfFirst { it.id == op.frame.id }
                            .coerceAtLeast(0)
                    }
                }
                is UndoOp.FrameRemoved -> {
                    if (undo) {
                        if (frames.none { it.id == op.frame.id }) {
                            frames.add(op.index.coerceIn(0, frames.size), op.frame)
                        }
                        currentIndex = frames.indexOfFirst { it.id == op.frame.id }
                            .coerceAtLeast(0)
                    } else {
                        val idx = frames.indexOfFirst { it.id == op.frame.id }
                        if (idx >= 0) {
                            frames.removeAt(idx)
                            if (frames.isEmpty()) frames.add(Frame(nextId(), layers.size))
                            currentIndex = currentIndex.coerceIn(0, frames.lastIndex)
                        }
                    }
                }
            }
        }
        revision++
        thumbnailEpoch++
    }

    private fun afterStructureChange(select: Boolean) {
        if (select) currentIndex = currentIndex.coerceIn(0, frames.lastIndex)
        revision++
        thumbnailEpoch++
        isPlaying = false
    }

    // ---- Agent feed -------------------------------------------------------
    fun setAgentRunning(busy: Boolean, label: String = agentLabel) {
        agentBusy = busy
        agentLabel = label
        if (!busy) agentProgress = 0f
        revision++
    }

    fun updateAgentProgress(progress: Float, label: String? = null) {
        agentProgress = progress.coerceIn(0f, 1f)
        if (label != null) agentLabel = label
        lastToolAt = System.currentTimeMillis()
    }

    fun logTool(tool: String, summary: String, ok: Boolean = true) {
        toolCallCount++
        lastToolAt = System.currentTimeMillis()
        agentEvents.add(
            0,
            AgentEvent(
                id = nextId(),
                tool = tool,
                summary = summary.take(180),
                ok = ok,
                at = System.currentTimeMillis()
            )
        )
        while (agentEvents.size > 80) agentEvents.removeAt(agentEvents.lastIndex)
        revision++
    }

    fun clearAgentEvents() {
        agentEvents.clear()
    }

    // ---- Convenience for agents ------------------------------------------
    fun ensureFrame(index: Int): Frame =
        frames.getOrNull(index) ?: throw IllegalArgumentException("Frame $index does not exist. ${frames.size} frames available.")

    fun ensureLayer(index: Int): LayerMeta =
        layers.getOrNull(index) ?: throw IllegalArgumentException("Layer $index does not exist. ${layers.size} layers available.")

    fun nextElementId(): Long = nextId()

    /** Rebuilds the whole document from a stored project file. */
    fun restoreProject(
        file: com.anymate.studio.model.ProjectFile,
        decodeElements: (com.anymate.studio.model.ElementFile) -> Element?
    ) {
        frames.clear()
        layers.clear()
        val layerFiles = file.layers.ifEmpty {
            DefaultLayers.map { com.anymate.studio.model.LayerFile(name = it) }
        }
        layerFiles.forEach { layerFile ->
            layers.add(
                LayerMeta(nextId(), layerFile.name).apply {
                    visible = layerFile.visible
                    locked = layerFile.locked
                    opacity = layerFile.opacity
                }
            )
        }
        projectName = file.name.ifBlank { "Untitled" }
        canvasWidth = file.width.coerceIn(240, 2048)
        canvasHeight = file.height.coerceIn(240, 2048)
        fps = file.fps.coerceIn(1, 60)
        background = file.background
        file.frames.forEach { frameFile ->
            val frame = Frame(nextId(), layers.size)
            frameFile.layers.forEachIndexed { layerIndex, elements ->
                val layer = frame.layers.getOrNull(layerIndex) ?: return@forEachIndexed
                elements.forEach { elementFile ->
                    decodeElements(elementFile)?.let { layer.elements.add(it) }
                }
            }
            frames.add(frame)
        }
        if (frames.isEmpty()) frames.add(Frame(nextId(), layers.size))
        currentIndex = file.currentFrame.coerceIn(0, frames.lastIndex)
        currentLayer = 0
        isPlaying = false
        undoStack.clear()
        redoStack.clear()
        groupDepth = 0
        groupOps = null
        refreshHistory()
        Rasterizer.clearCaches()
        revision++
        thumbnailEpoch++
        historyEpoch++
    }

    fun layerFramesSnapshot(): List<FrameLayer> = currentFrame().layers.toList()

    fun largestFrameThumbnailSize(): Int = max(canvasWidth, canvasHeight)
}
