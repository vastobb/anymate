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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush as GradientBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.model.DefaultFpsChoices
import com.anymate.studio.model.Frame
import com.anymate.studio.ui.theme.AccentGradient
import com.anymate.studio.ui.theme.AnyMateMetrics
import com.anymate.studio.ui.theme.Cyan
import com.anymate.studio.ui.theme.Danger
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink700
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.Ink950
import com.anymate.studio.ui.theme.Mono
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet
import com.anymate.studio.util.toComposeColor
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun Timeline(
    engine: AnimationEngine,
    onNewProject: () -> Unit,
    onOpenExport: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var fpsMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val compact = AnyMateMetrics.compact

    LaunchedEffect(Unit) {
        var lastFollowed = -1
        while (true) {
            delay(120)
            val target = engine.currentIndex.coerceIn(0, engine.frames.lastIndex.coerceAtLeast(0))
            if (target != lastFollowed) {
                lastFollowed = target
                runCatching {
                    if (engine.isPlaying || engine.agentBusy) listState.scrollToItem(target)
                    else listState.animateScrollToItem(target)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                GradientBrush.verticalGradient(listOf(Color(0xFF121017), Ink950))
            )
            .drawBehind {
                drawLine(
                    color = Color(0x1FFFFFFF),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 42.dp else 48.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Timeline")
            Spacer(Modifier.width(8.dp))
            Text(
                "${engine.frames.size} frame${if (engine.frames.size == 1) "" else "s"}",
                color = TextMuted,
                fontFamily = Mono,
                fontSize = 9.sp
            )
            Spacer(Modifier.weight(1f))

            Box {
                StudioChip(
                    text = "${engine.fps} FPS",
                    selected = false,
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
                                    fontSize = 13.sp,
                                    fontFamily = Mono
                                )
                            },
                            onClick = {
                                engine.updateFps(option)
                                fpsMenu = false
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (engine.frames.size > 1) AccentGradient else GradientBrush.linearGradient(listOf(Ink700, Ink700)))
                    .clickable(enabled = engine.frames.size > 1) {
                        engine.togglePlayback()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (engine.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (engine.isPlaying) "Pause" else "Play",
                    tint = if (engine.frames.size > 1) Color(0xFF120A24) else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(3.dp))

            StudioIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "Add frame",
                onClick = {
                    try {
                        engine.addFrame()
                        onNotice("Frame added")
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (t: Throwable) {
                        onNotice(t.message ?: "Could not add frame")
                    }
                },
                size = 36.dp,
                tint = Violet,
                background = Ink800
            )

            Spacer(Modifier.width(3.dp))

            Box {
                StudioIconButton(
                    icon = Icons.Rounded.MoreVert,
                    contentDescription = "Frame options",
                    onClick = { moreMenu = true },
                    size = 36.dp
                )
                DropdownMenu(
                    expanded = moreMenu,
                    onDismissRequest = { moreMenu = false },
                    containerColor = Ink800
                ) {
                    TimelineMenuItem("Duplicate frame", Icons.Rounded.ContentCopy) {
                        moreMenu = false
                        try {
                            engine.duplicateFrame()
                            onNotice("Frame duplicated")
                        } catch (t: Throwable) {
                            onNotice(t.message ?: "Could not duplicate")
                        }
                    }
                    TimelineMenuItem("Clear frame", Icons.Rounded.CleaningServices) {
                        moreMenu = false
                        try {
                            engine.clearFrame()
                            onNotice("Frame cleared")
                        } catch (t: Throwable) {
                            onNotice(t.message ?: "Could not clear")
                        }
                    }
                    TimelineMenuItem("Clear active layer", Icons.Rounded.Layers) {
                        moreMenu = false
                        try {
                            engine.clearLayer()
                            onNotice("Layer cleared")
                        } catch (t: Throwable) {
                            onNotice(t.message ?: "Could not clear")
                        }
                    }
                    TimelineMenuItem("Delete frame", Icons.Rounded.Delete, Danger) {
                        moreMenu = false
                        try {
                            val deleted = engine.deleteFrame()
                            onNotice(if (deleted) "Frame deleted" else "Only frame cleared")
                        } catch (t: Throwable) {
                            onNotice(t.message ?: "Could not delete")
                        }
                    }
                    TimelineMenuItem("Export…", Icons.Rounded.FileDownload) {
                        moreMenu = false
                        onOpenExport()
                    }
                    TimelineMenuItem("New project", Icons.Rounded.AddCircle) {
                        moreMenu = false
                        onNewProject()
                    }
                }
            }
        }

        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(if (compact) 68.dp else 82.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            itemsIndexed(engine.frames, key = { _, frame -> frame.id }) { index, frame ->
                FrameCard(
                    engine = engine,
                    frame = frame,
                    index = index,
                    onClick = {
                        engine.selectFrame(index)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )
            }
            item {
                AddFrameCard(
                    onClick = {
                        try {
                            engine.addFrame()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } catch (t: Throwable) {
                            onNotice(t.message ?: "Could not add frame")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FrameCard(
    engine: AnimationEngine,
    frame: Frame,
    index: Int,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .width(58.dp)
            .height(66.dp)
            .clip(shape)
            .background(Ink900)
            .clickable(onClick = onClick)
            .drawBehind {
                val selected = engine.currentIndex == index
                drawRoundRect(
                    color = if (selected) Violet else Hairline,
                    cornerRadius = CornerRadius(9.dp.toPx()),
                    style = Stroke(width = if (selected) 1.7.dp.toPx() else 1.dp.toPx())
                )
                if (selected) {
                    drawRect(
                        brush = AccentGradient,
                        topLeft = Offset.Zero,
                        size = Size(size.width, 2.dp.toPx())
                    )
                }
            }
    ) {
        FrameThumbnail(engine, frame, Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(3.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCC0C0B0F))
                .padding(horizontal = 4.dp, vertical = 1.dp)
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

@Composable
private fun FrameThumbnail(
    engine: AnimationEngine,
    frame: Frame,
    modifier: Modifier = Modifier
) {
    var image by remember(frame.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(frame.id, frame.revision, engine.thumbnailEpoch, engine.background) {
        if (frame.revision == 0 && frame.layers.all { it.elements.isEmpty() }) {
            image = null
            return@LaunchedEffect
        }
        delay(if (engine.agentBusy) 160 else 35)
        image = FrameThumbnailCache.load(engine, frame, 176)
    }
    Box(
        modifier = modifier.background(engine.background.toComposeColor()),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Frame ${frame.id}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AddFrameCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .width(58.dp)
            .height(66.dp)
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = Color(0xFF3A3446),
                    cornerRadius = CornerRadius(9.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(5.dp.toPx(), 5.dp.toPx())
                        )
                    )
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Add, null, tint = TextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(2.dp))
            Text("ADD", color = TextMuted, fontSize = 7.sp, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun TimelineMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = TextSecondary,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 13.sp, color = TextPrimary) },
        leadingIcon = {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        },
        onClick = onClick
    )
}
