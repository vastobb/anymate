package com.anymate.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.StudioViewModel
import com.anymate.studio.ui.theme.AccentGradient
import com.anymate.studio.ui.theme.Cyan
import com.anymate.studio.ui.theme.Danger
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun StudioScreen(vm: StudioViewModel) {
    val engine = vm.engine
    val snackbar = remember { SnackbarHostState() }

    var showAgent by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showToolSettings by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showNewProject by remember { mutableStateOf(false) }
    var showTextEntry by remember { mutableStateOf(false) }
    var textPoint by remember { mutableStateOf(Offset.Zero) }

    fun openExclusive(target: String) {
        showAgent = target == "agent"
        showColor = target == "color"
        showLayers = target == "layers"
        showToolSettings = target == "tool"
        showSettings = target == "settings"
        showExport = target == "export"
        showNewProject = target == "new"
    }

    LaunchedEffect(vm.notice?.id) {
        val notice = vm.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(
            message = notice.text,
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        vm.consumeNotice()
    }

    LaunchedEffect(engine.isPlaying, engine.fps, engine.frames.size) {
        if (!engine.isPlaying) return@LaunchedEffect
        var accumulator = 0L
        var last = withFrameNanos { it }
        while (engine.isPlaying) {
            withFrameNanos { now ->
                val delta = now - last
                last = now
                accumulator += delta
                val frameNanos = 1_000_000_000L / engine.fps.coerceAtLeast(1)
                if (accumulator >= frameNanos) {
                    val steps = (accumulator / frameNanos).toInt().coerceAtMost(8)
                    accumulator %= frameNanos
                    engine.advancePlayback(steps)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0F0C15), Ink950, Color(0xFF070609)))
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Violet.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(size.width * 0.55f, 0f),
                            radius = size.width * 0.85f
                        ),
                        radius = size.width * 0.85f,
                        center = Offset(size.width * 0.55f, 0f)
                    )
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            StudioTopBar(
                vm = vm,
                onOpenSettings = { openExclusive("settings") },
                onOpenExport = { openExclusive("export") }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CanvasStage(
                    engine = engine,
                    showPerf = vm.perfHud,
                    onOpenAgent = { openExclusive("agent") },
                    onOpenLayers = { openExclusive("layers") },
                    onTextRequest = { x, y ->
                        textPoint = Offset(x, y)
                        showTextEntry = true
                    },
                    onBlocked = { vm.notify(it) },
                    onColorPicked = { color -> vm.rememberColor(color) }
                )
                ToolRail(
                    engine = engine,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            StudioStatusBar(
                vm = vm,
                onOpenAgent = { openExclusive("agent") },
                onOpenSettings = { openExclusive("settings") }
            )

            QuickToolBar(
                engine = engine,
                onOpenColor = { openExclusive("color") },
                onOpenLayers = { openExclusive("layers") },
                onOpenToolSettings = { openExclusive("tool") }
            )

            Timeline(
                engine = engine,
                onNewProject = { openExclusive("new") },
                onOpenExport = { openExclusive("export") },
                onNotice = vm::notify
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 154.dp)
        ) { data ->
            Snackbar(
                shape = RoundedCornerShape(13.dp),
                containerColor = Ink800,
                contentColor = TextPrimary
            ) {
                Text(data.visuals.message, fontSize = 12.sp)
            }
        }
    }

    if (showAgent) {
        AgentSheet(
            vm = vm,
            onDismiss = { showAgent = false },
            onOpenSettings = { openExclusive("settings") }
        )
    }
    if (showColor) {
        ColorSheet(
            engine = engine,
            recentColors = vm.recentColors,
            onDismiss = {
                vm.rememberColor(engine.strokeColor)
                showColor = false
            }
        )
    }
    if (showLayers) {
        LayersSheet(
            engine = engine,
            onDismiss = { showLayers = false },
            onNotice = vm::notify
        )
    }
    if (showToolSettings) {
        ToolSettingsSheet(
            engine = engine,
            onDismiss = { showToolSettings = false },
            onNotice = vm::notify
        )
    }
    if (showSettings) {
        SettingsSheet(
            vm = vm,
            onDismiss = { showSettings = false },
            onNewProject = { showNewProject = true }
        )
    }
    if (showExport) {
        ExportSheet(vm = vm, onDismiss = { showExport = false })
    }
    if (showNewProject) {
        NewProjectDialog(vm = vm, onDismiss = { showNewProject = false })
    }
    if (showTextEntry) {
        TextEntryDialog(
            onDismiss = { showTextEntry = false },
            onConfirm = { value ->
                try {
                    engine.addText(
                        text = value,
                        x = textPoint.x,
                        y = textPoint.y,
                        size = engine.brushSize,
                        color = engine.strokeColor
                    )
                    vm.notify("Text placed")
                } catch (t: Throwable) {
                    vm.notify(t.message ?: "Could not place text")
                }
                showTextEntry = false
            }
        )
    }
    if (vm.showWelcome) {
        WelcomeDialog(vm)
    }
}

@Composable
private fun StudioTopBar(
    vm: StudioViewModel,
    onOpenSettings: () -> Unit,
    onOpenExport: () -> Unit
) {
    val engine = vm.engine
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .drawBehind {
                drawLine(
                    color = Color(0x14FFFFFF),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(29.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "ANYMATE",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.4.sp
            )
            Text(
                engine.projectName,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StudioIconButton(
            icon = Icons.Rounded.Undo,
            contentDescription = "Undo",
            onClick = { engine.undo() },
            enabled = engine.canUndo,
            size = 36.dp
        )
        StudioIconButton(
            icon = Icons.Rounded.Redo,
            contentDescription = "Redo",
            onClick = { engine.redo() },
            enabled = engine.canRedo,
            size = 36.dp
        )
        Box {
            StudioIconButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Studio and MCP settings",
                onClick = onOpenSettings,
                size = 38.dp
            )
            if (vm.server.running) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 7.dp, end = 7.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Success)
                )
            }
        }
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentGradient)
                .clickable(onClick = onOpenExport),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                Icons.Rounded.FileDownload,
                contentDescription = "Export",
                tint = Color(0xFF100A20),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StudioStatusBar(
    vm: StudioViewModel,
    onOpenAgent: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val engine = vm.engine
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
        }
    }
    val recentAgent = engine.lastToolAt > 0 && now - engine.lastToolAt < 1800
    val active = engine.agentBusy || recentAgent
    val latestEvent = engine.agentEvents.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(33.dp)
            .background(
                if (active) {
                    Brush.horizontalGradient(
                        listOf(Violet.copy(alpha = 0.17f), Cyan.copy(alpha = 0.07f), Ink900)
                    )
                } else {
                    Brush.horizontalGradient(listOf(Ink900, Ink900))
                }
            )
            .drawBehind {
                drawLine(
                    color = Color(0x12FFFFFF),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
                if (active && engine.agentBusy && engine.agentProgress > 0f) {
                    drawRect(
                        brush = AccentGradient,
                        topLeft = Offset(0f, size.height - 2.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(
                            size.width * engine.agentProgress.coerceIn(0f, 1f),
                            2.dp.toPx()
                        )
                    )
                }
            }
            .clickable {
                if (active) onOpenAgent() else onOpenSettings()
            }
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LiveDot(
                color = when {
                    active -> Violet
                    vm.server.running -> Success
                    else -> TextMuted
                },
                6.dp
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = when {
                    active -> "LIVE · ${engine.agentLabel.ifBlank {
                        latestEvent?.tool?.removePrefix("anymate_")?.replace('_', ' ') ?: "Agent editing"
                    }}"
                    vm.server.running -> "MCP LIVE · ${vm.server.localIp}:${vm.server.port}"
                    else -> "LOCAL STUDIO · MCP OFF"
                },
                color = if (active) TextPrimary else TextMuted,
                fontSize = 8.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = if (active) 0.5.sp else 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (vm.thinking) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Danger.copy(alpha = 0.13f))
                        .clickable { vm.stopAgent() }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Stop, null,
                        tint = Danger, modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("STOP", color = Danger, fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
            } else {
                Text(
                    "${engine.fps} FPS · ${engine.frames.size} F · ${(engine.zoom * 100).roundToInt()}%",
                    color = TextMuted,
                    fontFamily = Mono,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}
