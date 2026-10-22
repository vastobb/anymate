package com.anymate.studio.ui

import android.graphics.Canvas as AndroidCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.engine.Rasterizer
import com.anymate.studio.model.ShapeElement
import com.anymate.studio.model.ShapeKind
import com.anymate.studio.model.StrokeElement
import com.anymate.studio.model.ToolType
import com.anymate.studio.model.Vec2
import com.anymate.studio.ui.theme.AnyMateMetrics
import com.anymate.studio.ui.theme.Cyan
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink850
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.Ink950
import com.anymate.studio.ui.theme.Mono
import com.anymate.studio.ui.theme.Success
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet
import com.anymate.studio.util.toComposeColor
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class ViewTransform(val left: Float, val top: Float, val scale: Float)

/** Which studio chrome is surrounding the canvas. */
enum class StudioLayout { PORTRAIT, LANDSCAPE }

private data class StageInsets(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun stageInsets(
    layout: StudioLayout,
    compact: Boolean,
    inspectorOpen: Boolean
): StageInsets = if (layout == StudioLayout.LANDSCAPE) {
    StageInsets(
        left = if (compact) 56f else 66f,
        top = if (compact) 50f else 56f,
        right = when {
            !inspectorOpen -> 18f
            compact -> 188f
            else -> 216f
        },
        bottom = if (compact) 76f else 90f
    )
} else {
    StageInsets(left = 66f, top = 18f, right = 14f, bottom = 20f)
}

private data class StrokeDraft(
    val tool: ToolType,
    val frameIndex: Int,
    val layerIndex: Int,
    val start: Offset
) {
    val points = ArrayList<Vec2>(256)
    var end: Offset = start
    var canceled: Boolean = false
}

private fun viewTransform(
    engine: AnimationEngine,
    width: Float,
    height: Float,
    leftInset: Float,
    topInset: Float,
    rightInset: Float,
    bottomInset: Float
): ViewTransform {
    val availableW = (width - leftInset - rightInset).coerceAtLeast(32f)
    val availableH = (height - topInset - bottomInset).coerceAtLeast(32f)
    val base = min(availableW / engine.canvasWidth, availableH / engine.canvasHeight)
    val scale = base * engine.zoom
    val w = engine.canvasWidth * scale
    val h = engine.canvasHeight * scale
    return ViewTransform(
        left = leftInset + (availableW - w) / 2f + engine.panX,
        top = topInset + (availableH - h) / 2f + engine.panY,
        scale = scale
    )
}

@Composable
fun CanvasStage(
    engine: AnimationEngine,
    showPerf: Boolean,
    layout: StudioLayout = StudioLayout.PORTRAIT,
    inspectorOpen: Boolean = true,
    showOverlays: Boolean = true,
    onOpenMcp: () -> Unit,
    onOpenLayers: () -> Unit,
    onTextRequest: (Float, Float) -> Unit,
    onBlocked: (String) -> Unit,
    onColorPicked: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var draftTick by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<StrokeDraft?>(null) }
    val compact = AnyMateMetrics.compact
    val insets = stageInsets(layout, compact, inspectorOpen)

    val drawingTools = remember {
        setOf(
            ToolType.PENCIL, ToolType.PEN, ToolType.BRUSH,
            ToolType.ERASER, ToolType.SHAPE
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E0B14), Color(0xFF09080C))
                )
            )
            .drawBehind {
                // Quiet studio dot grid.
                val gap = 26.dp.toPx()
                val radius = 1.1.dp.toPx()
                var y = gap
                while (y < size.height) {
                    var x = gap
                    while (x < size.width) {
                        drawCircle(
                            color = Color(0x0FFFFFFF),
                            radius = radius,
                            center = Offset(x, y)
                        )
                        x += gap
                    }
                    y += gap
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Animation canvas" }
                .pointerInput(engine.selectedTool) {
                    val leftInset = insets.left.dp.toPx()
                    val topInset = insets.top.dp.toPx()
                    val rightInset = insets.right.dp.toPx()
                    val bottomInset = insets.bottom.dp.toPx()
                    val tapSlop = 14.dp.toPx()

                    fun transform() = viewTransform(
                        engine,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        leftInset, topInset, rightInset, bottomInset
                    )

                    fun toProject(position: Offset): Offset {
                        val t = transform()
                        return Offset(
                            (position.x - t.left) / t.scale,
                            (position.y - t.top) / t.scale
                        )
                    }

                    fun zoomAround(centroid: Offset, targetZoom: Float) {
                        val old = transform()
                        val anchor = Offset(
                            (centroid.x - old.left) / old.scale,
                            (centroid.y - old.top) / old.scale
                        )
                        engine.updateZoom(targetZoom)
                        val availableW = (size.width - leftInset - rightInset).coerceAtLeast(32f)
                        val availableH = (size.height - topInset - bottomInset).coerceAtLeast(32f)
                        val base = min(
                            availableW / engine.canvasWidth,
                            availableH / engine.canvasHeight
                        )
                        val newScale = base * engine.zoom
                        val canvasW = engine.canvasWidth * newScale
                        val canvasH = engine.canvasHeight * newScale
                        val centeredLeft = leftInset + (availableW - canvasW) / 2f
                        val centeredTop = topInset + (availableH - canvasH) / 2f
                        engine.panTo(
                            centroid.x - anchor.x * newScale - centeredLeft,
                            centroid.y - anchor.y * newScale - centeredTop
                        )
                    }

                    suspend fun AwaitPointerEventScope.trackPinch(initial: PointerEvent) {
                        var event = initial
                        var lastDistance = 0f
                        while (true) {
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                val a = pressed[0].position
                                val b = pressed[1].position
                                val distance = (a - b).getDistance().coerceAtLeast(1f)
                                val centroid = (a + b) / 2f
                                if (lastDistance > 0f) {
                                    zoomAround(centroid, engine.zoom * (distance / lastDistance))
                                }
                                lastDistance = distance
                                pressed.forEach { it.consume() }
                            }
                            if (event.changes.none { it.pressed }) break
                            event = awaitPointerEvent()
                        }
                    }

                    fun cancelDraft() {
                        draft?.canceled = true
                        draft = null
                        draftTick++
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (engine.isPlaying) {
                            engine.pause()
                            return@awaitEachGesture
                        }
                        val tool = engine.selectedTool
                        val frameIndex = engine.currentIndex
                        val layerIndex = engine.currentLayer

                        if (tool == ToolType.HAND) {
                            var last = down.position
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    trackPinch(event)
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.position - last
                                last = change.position
                                engine.panBy(delta.x, delta.y)
                                change.consume()
                            }
                            return@awaitEachGesture
                        }

                        if (tool == ToolType.FILL || tool == ToolType.EYEDROPPER || tool == ToolType.TEXT) {
                            var tapped = false
                            var pinch = false
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    pinch = true
                                    trackPinch(event)
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    tapped = (change.position - down.position).getDistance() <= tapSlop
                                    break
                                }
                                if ((change.position - down.position).getDistance() > tapSlop) {
                                    break
                                }
                            }
                            if (tapped && !pinch) {
                                val project = toProject(down.position)
                                when (tool) {
                                    ToolType.TEXT -> onTextRequest(project.x, project.y)
                                    ToolType.FILL -> scope.launch {
                                        try {
                                            val ok = engine.fillAt(project.x, project.y)
                                            if (!ok) onBlocked("Nothing to fill here.")
                                            else haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        } catch (t: Throwable) {
                                            onBlocked(t.message ?: "Could not fill.")
                                        }
                                    }
                                    ToolType.EYEDROPPER -> scope.launch {
                                        try {
                                            val color = engine.sampleColorAt(project.x, project.y)
                                            if (color != null) {
                                                engine.updateColor(color)
                                                onColorPicked(color)
                                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                            }
                                        } catch (t: Throwable) {
                                            onBlocked(t.message ?: "Could not sample colour.")
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                            return@awaitEachGesture
                        }

                        if (tool !in drawingTools) return@awaitEachGesture
                        if (engine.layerLocked(layerIndex)) {
                            onBlocked("Layer \"${engine.layers.getOrNull(layerIndex)?.name ?: "?"}\" is locked.")
                            return@awaitEachGesture
                        }

                        val startProject = toProject(down.position)
                        val newDraft = StrokeDraft(tool, frameIndex, layerIndex, startProject)
                        newDraft.points.add(
                            Vec2(
                                startProject.x,
                                startProject.y,
                                down.pressure.coerceIn(0.15f, 1f)
                            )
                        )
                        draft = newDraft
                        draftTick++
                        down.consume()

                        var canceledByPinch = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    canceledByPinch = true
                                    cancelDraft()
                                    trackPinch(event)
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val project = toProject(change.position)
                                if (!change.pressed) {
                                    if (tool == ToolType.SHAPE) newDraft.end = project
                                    else addPoint(newDraft.points, project, change.pressure)
                                    draftTick++
                                    change.consume()
                                    break
                                }
                                if (tool == ToolType.SHAPE) {
                                    newDraft.end = project
                                } else {
                                    addPoint(newDraft.points, project, change.pressure)
                                }
                                draftTick++
                                change.consume()
                            }

                            if (!canceledByPinch && !newDraft.canceled) {
                                try {
                                    if (tool == ToolType.SHAPE) {
                                        val dx = newDraft.end.x - newDraft.start.x
                                        val dy = newDraft.end.y - newDraft.start.y
                                        if (hypot(dx, dy) > 4f) {
                                            engine.addShape(
                                                kind = engine.shapeKind,
                                                start = Vec2(newDraft.start.x, newDraft.start.y),
                                                end = Vec2(newDraft.end.x, newDraft.end.y),
                                                filled = engine.shapeFilled,
                                                color = engine.strokeColor,
                                                width = engine.brushSize,
                                                opacity = engine.brushOpacity,
                                                frameIndex = frameIndex,
                                                layerIndex = layerIndex,
                                                select = true
                                            )
                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        }
                                    } else {
                                        engine.commitStroke(
                                            points = newDraft.points.toList(),
                                            tool = tool,
                                            color = engine.strokeColor,
                                            width = engine.brushSize,
                                            opacity = engine.brushOpacity,
                                            frameIndex = frameIndex,
                                            layerIndex = layerIndex,
                                            select = true
                                        )
                                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    }
                                } catch (t: Throwable) {
                                    onBlocked(t.message ?: "Could not draw.")
                                }
                            }
                        } finally {
                            // Always drop the live preview, even when the gesture is
                            // cancelled (tool change, sheet dismissal, disposal).
                            draft = null
                            draftTick++
                        }
                    }
                }
        ) {
            val transform = viewTransform(
                engine,
                size.width,
                size.height,
                insets.left.dp.toPx(), insets.top.dp.toPx(),
                insets.right.dp.toPx(), insets.bottom.dp.toPx()
            )
            val sheetW = engine.canvasWidth * transform.scale
            val sheetH = engine.canvasHeight * transform.scale
            val corner = 5.dp.toPx()

            // Soft ambient glow around the artboard.
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x337C3AED), Color.Transparent),
                    center = Offset(transform.left + sheetW / 2f, transform.top + sheetH / 2f),
                    radius = max(sheetW, sheetH) * 0.72f
                ),
                topLeft = Offset(transform.left - 40.dp.toPx(), transform.top - 40.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(sheetW + 80.dp.toPx(), sheetH + 80.dp.toPx()),
                cornerRadius = CornerRadius(corner + 20.dp.toPx())
            )

            // Contact shadow.
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(transform.left + 2.dp.toPx(), transform.top + 8.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(sheetW, sheetH),
                cornerRadius = CornerRadius(corner)
            )

            val clip = Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(transform.left, transform.top, transform.left + sheetW, transform.top + sheetH),
                        CornerRadius(corner)
                    )
                )
            }
            clipPath(clip) {
                translate(transform.left, transform.top) {
                    scale(transform.scale, transform.scale, pivot = Offset.Zero) {
                        drawRect(engine.background.toComposeColor())
                        val frame = engine.currentFrame()
                        val layers = engine.layers.toList()
                        if (engine.onionSkin && !engine.isPlaying && engine.frames.size > 1) {
                            val prev = engine.frames.getOrNull(engine.currentIndex - 1)
                            val next = engine.frames.getOrNull(engine.currentIndex + 1)
                            drawIntoCanvas { canvas ->
                                val native = canvas.nativeCanvas
                                if (prev != null) {
                                    val save = native.saveLayerAlpha(
                                        0f, 0f,
                                        engine.canvasWidth.toFloat(), engine.canvasHeight.toFloat(),
                                        40, AndroidCanvas.ALL_SAVE_FLAG
                                    )
                                    Rasterizer.drawLayers(
                                        native, engine.canvasWidth, engine.canvasHeight,
                                        prev, layers
                                    )
                                    native.restoreToCount(save)
                                }
                                if (next != null) {
                                    val save = native.saveLayerAlpha(
                                        0f, 0f,
                                        engine.canvasWidth.toFloat(), engine.canvasHeight.toFloat(),
                                        26, AndroidCanvas.ALL_SAVE_FLAG
                                    )
                                    Rasterizer.drawLayers(
                                        native, engine.canvasWidth, engine.canvasHeight,
                                        next, layers
                                    )
                                    native.restoreToCount(save)
                                }
                            }
                        }
                        drawIntoCanvas { canvas ->
                            Rasterizer.drawLayers(
                                canvas.nativeCanvas, engine.canvasWidth, engine.canvasHeight,
                                frame, layers
                            )
                        }

                        draftTick // observe live stroke changes without recomposition
                        draft?.takeIf { !it.canceled }?.let { live ->
                            drawIntoCanvas { canvas ->
                                val native = canvas.nativeCanvas
                                if (live.tool == ToolType.SHAPE) {
                                    Rasterizer.drawPreview(
                                        native,
                                        ShapeElement(
                                            id = -2L,
                                            kind = engine.shapeKind,
                                            x1 = live.start.x, y1 = live.start.y,
                                            x2 = live.end.x, y2 = live.end.y,
                                            strokeColor = engine.strokeColor,
                                            fillColor = if (engine.shapeFilled) engine.strokeColor else null,
                                            width = engine.brushSize,
                                            opacity = engine.brushOpacity
                                        )
                                    )
                                } else {
                                    Rasterizer.drawPreview(
                                        native,
                                        StrokeElement(
                                            id = -1L,
                                            points = live.points.toList(),
                                            color = engine.strokeColor,
                                            width = engine.brushSize,
                                            opacity = engine.brushOpacity,
                                            tool = live.tool
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            drawRoundRect(
                color = Color.White.copy(alpha = 0.12f),
                topLeft = Offset(transform.left, transform.top),
                size = androidx.compose.ui.geometry.Size(sheetW, sheetH),
                cornerRadius = CornerRadius(corner),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )
        }

        if (showOverlays) {
            FrameBadges(
                engine = engine,
                showPerf = showPerf,
                onOpenLayers = onOpenLayers,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 76.dp, top = 12.dp)
            )

            ViewControls(
                engine = engine,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
            )
        }

        // MCP entrance: the studio is driven by hand or by an agent over MCP.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 14.dp)
                .clip(CircleShape)
                .background(Ink850.copy(alpha = 0.97f))
                .border(1.dp, Violet.copy(alpha = 0.36f), CircleShape)
                .clickable(onClick = onOpenMcp)
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .size(21.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Violet, Cyan))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Hub, null,
                    tint = Color(0xFF0C0718), modifier = Modifier.size(12.dp)
                )
            }
            Text("Connect", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        if (showOverlays) {
            EmptyCanvasHint(
                engine = engine,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = 50.dp, end = 12.dp)
            )
        }
    }
}

@Composable
private fun FrameBadges(
    engine: AnimationEngine,
    showPerf: Boolean,
    onOpenLayers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xE615121C))
                .border(1.dp, Hairline, RoundedCornerShape(11.dp))
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Movie, null, tint = Violet, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "%02d / %02d".format(engine.currentIndex + 1, engine.frames.size),
                color = TextPrimary,
                fontFamily = Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "FRAME",
                color = TextMuted,
                fontSize = 8.sp,
                letterSpacing = 1.sp
            )
        }
        val layer = engine.layerOrNull()
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xD915121C))
                .border(1.dp, Hairline, RoundedCornerShape(11.dp))
                .clickable(onClick = onOpenLayers)
                .padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (layer?.locked == true) Icons.Rounded.Lock else Icons.Rounded.Layers,
                null,
                tint = if (layer?.locked == true) com.anymate.studio.ui.theme.Amber else Cyan,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                layer?.name ?: "Layer",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        if (showPerf) {
            PerfBadge()
        }
    }
}

@Composable
private fun ViewControls(engine: AnimationEngine, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xD915121C))
                .border(1.dp, Hairline, RoundedCornerShape(13.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            StudioIconButton(
                icon = Icons.Rounded.FilterNone,
                contentDescription = if (engine.onionSkin) "Hide onion skin" else "Show onion skin",
                selected = engine.onionSkin,
                onClick = {
                    engine.updateOnionSkin(!engine.onionSkin)
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                },
                size = 36.dp
            )
            StudioIconButton(
                icon = Icons.Rounded.FitScreen,
                contentDescription = "Fit canvas to screen",
                onClick = {
                    engine.resetView()
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                },
                size = 36.dp
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0xD915121C))
                .border(1.dp, Hairline, RoundedCornerShape(13.dp))
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioIconButton(
                icon = Icons.Rounded.ZoomOut,
                contentDescription = "Zoom out",
                onClick = { engine.updateZoom(engine.zoom / 1.25f) },
                size = 32.dp
            )
            Text(
                "${(engine.zoom * 100).roundToInt()}%",
                color = TextSecondary,
                fontFamily = Mono,
                fontSize = 9.sp,
                modifier = Modifier.width(38.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            StudioIconButton(
                icon = Icons.Rounded.ZoomIn,
                contentDescription = "Zoom in",
                onClick = { engine.updateZoom(engine.zoom * 1.25f) },
                size = 32.dp
            )
        }
    }
}

@Composable
private fun EmptyCanvasHint(engine: AnimationEngine, modifier: Modifier = Modifier) {
    val frame = engine.currentFrame()
    val showHint = engine.frames.size == 1 && frame.revision == 0 && !engine.agentBusy
    if (!showHint) return
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xB315121C))
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Start with a line",
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(3.dp))
        Text(
            "Draw with one finger · pinch to zoom · ask the Director",
            color = TextMuted,
            fontSize = 10.sp
        )
    }
}

private fun addPoint(points: MutableList<Vec2>, project: Offset, pressure: Float) {
    val last = points.lastOrNull()
    if (last == null) {
        points.add(Vec2(project.x, project.y, pressure.coerceIn(0.15f, 1f)))
        return
    }
    val distance = hypot(project.x - last.x, project.y - last.y)
    if (distance >= 1.05f) {
        points.add(Vec2(project.x, project.y, pressure.coerceIn(0.15f, 1f)))
    }
}

@Composable
private fun PerfBadge() {
    var fps by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        var frames = 0
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                frames++
                if (now - last >= 1_000_000_000L) {
                    fps = frames
                    frames = 0
                    last = now
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ink900.copy(alpha = 0.9f))
            .border(1.dp, Hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(Success))
        Text(
            "$fps FPS",
            color = TextSecondary,
            fontFamily = Mono,
            fontSize = 8.sp
        )
    }
}
