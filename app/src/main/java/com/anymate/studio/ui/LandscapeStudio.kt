package com.anymate.studio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.StudioViewModel
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.model.DefaultFpsChoices
import com.anymate.studio.model.Frame
import com.anymate.studio.model.PremiumSwatches
import com.anymate.studio.ui.theme.AccentGradient
import com.anymate.studio.ui.theme.Amber
import com.anymate.studio.ui.theme.AnyMateMetrics
import com.anymate.studio.ui.theme.Cyan
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink700
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink850
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.Mono
import com.anymate.studio.ui.theme.Success
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet
import com.anymate.studio.ui.theme.ThemeKind
import com.anymate.studio.util.toComposeColor
import kotlinx.coroutines.delay

/**
 * Landscape studio: the canvas owns the screen. Tools float on the left, the
 * inspector docks on the right and the timeline is a slim strip at the bottom,
 * so there is always room to draw.
 */
@Composable
fun LandscapeStudio(
    vm: StudioViewModel,
    inspectorOpen: Boolean,
    onToggleInspector: () -> Unit,
    onOpenMcp: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenColor: () -> Unit,
    onOpenToolSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenNewProject: () -> Unit,
    onTextRequest: (Float, Float) -> Unit,
    onBlocked: (String) -> Unit,
    onColorPicked: (Long) -> Unit
) {
    val engine = vm.engine
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        CanvasStage(
            engine = engine,
            showPerf = vm.perfHud,
            layout = StudioLayout.LANDSCAPE,
            inspectorOpen = inspectorOpen,
            showOverlays = false,
            onOpenMcp = onOpenMcp,
            onOpenLayers = onOpenLayers,
            onTextRequest = onTextRequest,
            onBlocked = onBlocked,
            onColorPicked = onColorPicked
        )

        LandscapeTopBar(
            vm = vm,
            onOpenMcp = onOpenMcp,
            onOpenSettings = onOpenSettings,
            onOpenExport = onOpenExport,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )

        ToolRail(
            engine = engine,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp, top = 60.dp, bottom = 90.dp),
            topPadding = 4.dp,
            bottomPadding = 4.dp
        )

        if (inspectorOpen) {
            InspectorPanel(
                vm = vm,
                onOpenColor = onOpenColor,
                onOpenLayers = onOpenLayers,
                onOpenToolSettings = onOpenToolSettings,
                onOpenMcp = onOpenMcp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp, top = 60.dp, bottom = 90.dp)
            )
        }

        StudioIconButton(
            icon = Icons.Rounded.Tune,
            contentDescription = "Toggle inspector",
            selected = inspectorOpen,
            onClick = onToggleInspector,
            tint = TextSecondary,
            background = Ink850.copy(alpha = 0.96f),
            size = 40.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    end = when {
                        !inspectorOpen -> 8.dp
                        AnyMateMetrics.compact -> 194.dp
                        else -> 218.dp
                    },
                    top = 60.dp,
                    bottom = 90.dp
                )
        )

        LandscapeTimeline(
            engine = engine,
            onOpenNewProject = onOpenNewProject,
            onNotice = vm::notify,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LandscapeTopBar(
    vm: StudioViewModel,
    onOpenMcp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = vm.engine
    val server = vm.server
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink850.copy(alpha = 0.95f))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(22.dp)
        Spacer(Modifier.width(9.dp))
        Text(
            "ANYMATE",
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.width(10.dp))
        Text(
            engine.projectName,
            color = TextMuted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(110.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "%02d/%02d".format(engine.currentIndex + 1, engine.frames.size),
            color = TextSecondary,
            fontFamily = Mono,
            fontSize = 10.sp
        )
        Spacer(Modifier.weight(1f))

        StudioIconButton(
            icon = Icons.Rounded.Undo,
            contentDescription = "Undo",
            onClick = { engine.undo() },
            enabled = engine.canUndo,
            size = 34.dp
        )
        StudioIconButton(
            icon = Icons.Rounded.Redo,
            contentDescription = "Redo",
            onClick = { engine.redo() },
            enabled = engine.canRedo,
            size = 34.dp
        )
        StudioIconButton(
            icon = Icons.Rounded.FitScreen,
            contentDescription = "Fit canvas",
            onClick = { engine.resetView() },
            size = 34.dp
        )
        StudioIconButton(
            icon = Icons.Rounded.Palette,
            contentDescription = "Switch theme",
            onClick = {
                val next = ThemeKind.entries[(vm.themeKind.ordinal + 1) % ThemeKind.entries.size]
                vm.updateTheme(next)
            },
            size = 34.dp,
            tint = vm.themeKind.palette.primary
        )
        StudioIconButton(
            icon = Icons.Rounded.Hub,
            contentDescription = "Connect an agent",
            onClick = onOpenMcp,
            size = 34.dp,
            tint = if (server.running) Success else TextMuted
        )
        StudioIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "Studio settings",
            onClick = onOpenSettings,
            size = 34.dp
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(AccentGradient)
                .clickable(onClick = onOpenExport),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.FileDownload,
                contentDescription = "Export",
                tint = Color(0xFF0B0714),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun InspectorPanel(
    vm: StudioViewModel,
    onOpenColor: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenToolSettings: () -> Unit,
    onOpenMcp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = vm.engine
    val compact = AnyMateMetrics.compact
    Column(
        modifier = modifier
            .width(if (compact) 176.dp else 200.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink850.copy(alpha = 0.95f))
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Colour
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Colour")
                Spacer(Modifier.weight(1f))
                ColorSwatch(engine.strokeColor, onClick = onOpenColor, size = 22.dp)
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PremiumSwatches.take(if (compact) 6 else 8).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(if (compact) 20.dp else 23.dp)
                            .clip(CircleShape)
                            .background(color.toComposeColor())
                            .border(
                                1.dp,
                                if (color == engine.strokeColor) Color.White else Color.White.copy(alpha = 0.18f),
                                CircleShape
                            )
                            .clickable { engine.updateColor(color) }
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                engine.strokeColor.toString().let { String.format("#%08X", it) }.takeLast(6),
                color = TextMuted,
                fontFamily = Mono,
                fontSize = 9.sp
            )
        }

        // Tool
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Tool")
                Spacer(Modifier.weight(1f))
                Text(
                    engine.selectedTool.label,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                StudioIconButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = "Tool options",
                    onClick = onOpenToolSettings,
                    size = 26.dp,
                    tint = Violet
                )
            }
            Spacer(Modifier.height(2.dp))
            Row {
                Text("SIZE", color = TextMuted, fontSize = 8.sp, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${engine.brushSize.toInt()} px",
                    color = TextSecondary,
                    fontFamily = Mono,
                    fontSize = 9.sp
                )
            }
            StudioSlider(
                value = engine.brushSize,
                onValueChange = { engine.updateBrushSize(it) },
                range = engine.sizeRangeFor(),
                modifier = Modifier.fillMaxWidth(),
                activeColor = Violet
            )
            Row {
                Text("OPACITY", color = TextMuted, fontSize = 8.sp, letterSpacing = 0.8.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    formatPercent(engine.brushOpacity),
                    color = TextSecondary,
                    fontFamily = Mono,
                    fontSize = 9.sp
                )
            }
            StudioSlider(
                value = engine.brushOpacity,
                onValueChange = { engine.updateBrushOpacity(it) },
                range = 0.02f..1f,
                modifier = Modifier.fillMaxWidth(),
                activeColor = Cyan
            )
        }

        // Layers
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Layers")
                Spacer(Modifier.weight(1f))
                StudioIconButton(
                    icon = Icons.Rounded.Layers,
                    contentDescription = "All layers",
                    onClick = onOpenLayers,
                    size = 26.dp,
                    tint = TextSecondary
                )
            }
            Spacer(Modifier.height(5.dp))
            engine.layers.forEachIndexed { index, layer ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (engine.currentLayer == index) Violet.copy(alpha = 0.12f) else Color.Transparent
                        )
                        .clickable { engine.selectLayer(index) }
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (layer.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = null,
                        tint = if (layer.visible) Cyan else TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        layer.name,
                        color = if (engine.currentLayer == index) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (layer.locked) {
                        Text("LOCK", color = Amber, fontSize = 7.sp, letterSpacing = 0.6.sp)
                    }
                }
            }
        }

        // Canvas
        Column {
            SectionLabel("Canvas")
            Spacer(Modifier.height(5.dp))
            Text(
                "${engine.canvasWidth} × ${engine.canvasHeight} · ${engine.fps} fps",
                color = TextSecondary,
                fontFamily = Mono,
                fontSize = 9.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Ink900.copy(alpha = 0.7f))
                    .clickable(onClick = onOpenMcp)
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Hub, null,
                    tint = if (vm.server.running) Success else TextMuted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (vm.server.running) "MCP live · ${vm.server.port}" else "MCP off",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun LandscapeTimeline(
    engine: AnimationEngine,
    onOpenNewProject: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var fpsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        var lastFollowed = -1
        while (true) {
            delay(140)
            val target = engine.currentIndex.coerceIn(0, engine.frames.lastIndex.coerceAtLeast(0))
            if (target != lastFollowed) {
                lastFollowed = target
                runCatching { listState.scrollToItem(target) }
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink850.copy(alpha = 0.95f))
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(
                    if (engine.frames.size > 1) AccentGradient
                    else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Ink700, Ink700))
                )
                .clickable(enabled = engine.frames.size > 1) {
                    engine.togglePlayback()
                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (engine.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (engine.isPlaying) "Pause" else "Play",
                tint = if (engine.frames.size > 1) Color(0xFF0B0714) else TextMuted,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Box {
            StudioChip(
                text = "${engine.fps} FPS",
                onClick = { fpsMenu = true },
                icon = Icons.Rounded.Speed
            )
            DropdownMenu(
                expanded = fpsMenu,
                onDismissRequest = { fpsMenu = false },
                containerColor = Ink800
            ) {
                DefaultFpsChoices.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "$option fps",
                                color = if (engine.fps == option) Violet else TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = Mono
                            )
                        },
                        onClick = {
                            engine.updateFps(option)
                            fpsMenu = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))

        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).height(54.dp),
            contentPadding = PaddingValues(end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(engine.frames, key = { _, frame -> frame.id }) { index, frame ->
                LandscapeFrameCard(
                    engine = engine,
                    frame = frame,
                    index = index,
                    onClick = {
                        engine.selectFrame(index)
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    }
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Ink900.copy(alpha = 0.6f))
                        .border(1.dp, Hairline, RoundedCornerShape(9.dp))
                        .clickable {
                            try {
                                engine.addFrame()
                            } catch (t: Throwable) {
                                onNotice(t.message ?: "Could not add frame")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        StudioIconButton(
            icon = Icons.Rounded.ContentCopy,
            contentDescription = "Duplicate frame",
            onClick = {
                try {
                    engine.duplicateFrame()
                } catch (t: Throwable) {
                    onNotice(t.message ?: "Could not duplicate")
                }
            },
            size = 32.dp
        )
        StudioIconButton(
            icon = Icons.Rounded.Delete,
            contentDescription = "Delete frame",
            onClick = {
                try {
                    val deleted = engine.deleteFrame()
                    onNotice(if (deleted) "Frame deleted" else "Only frame cleared")
                } catch (t: Throwable) {
                    onNotice(t.message ?: "Could not delete")
                }
            },
            size = 32.dp
        )
        StudioIconButton(
            icon = Icons.Rounded.AddCircle,
            contentDescription = "New project",
            onClick = onOpenNewProject,
            size = 32.dp
        )
    }
}

@Composable
private fun LandscapeFrameCard(
    engine: AnimationEngine,
    frame: Frame,
    index: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(9.dp)
    var image by remember(frame.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frame.id, frame.revision, engine.thumbnailEpoch, engine.background) {
        if (frame.revision == 0 && frame.layers.all { it.elements.isEmpty() }) {
            image = null
            return@LaunchedEffect
        }
        delay(if (engine.agentBusy) 160 else 40)
        image = FrameThumbnailCache.load(engine, frame, 140)
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(Ink900)
            .clickable(onClick = onClick)
            .drawBehind {
                val selected = engine.currentIndex == index
                drawRoundRect(
                    color = if (selected) Violet else Hairline,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = if (selected) 1.6.dp.toPx() else 1.dp.toPx()
                    )
                )
            }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().background(engine.background.toComposeColor()),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = image
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight()
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCC0B0A0F))
                .padding(horizontal = 3.dp, vertical = 1.dp)
        ) {
            Text(
                (index + 1).toString().padStart(2, '0'),
                color = TextSecondary,
                fontFamily = Mono,
                fontSize = 7.sp
            )
        }
    }
}
