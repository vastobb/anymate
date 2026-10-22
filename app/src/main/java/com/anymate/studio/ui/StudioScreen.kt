package com.anymate.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.rounded.Hub
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

    var showMcp by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    var showToolSettings by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showNewProject by remember { mutableStateOf(false) }
    var showTextEntry by remember { mutableStateOf(false) }
    var textPoint by remember { mutableStateOf(Offset.Zero) }
    var inspectorOpen by remember { mutableStateOf(true) }

    fun openExclusive(target: String) {
        showMcp = target == "mcp"
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Ink950, Color(0xFF070609)))
            )
    ) {
        val landscape = maxWidth > maxHeight
        Box(modifier = Modifier.fillMaxSize()) {
            if (landscape) {
                LandscapeStudio(
                    vm = vm,
                    inspectorOpen = inspectorOpen,
                    onToggleInspector = { inspectorOpen = !inspectorOpen },
                    onOpenMcp = { openExclusive("mcp") },
                    onOpenLayers = { openExclusive("layers") },
                    onOpenColor = { openExclusive("color") },
                    onOpenToolSettings = { openExclusive("tool") },
                    onOpenSettings = { openExclusive("settings") },
                    onOpenExport = { openExclusive("export") },
                    onOpenNewProject = { openExclusive("new") },
                    onTextRequest = { x, y ->
                        textPoint = Offset(x, y)
                        showTextEntry = true
                    },
                    onBlocked = { vm.notify(it) },
                    onColorPicked = { vm.rememberColor(it) }
                )
            } else {
                PortraitStudio(
                    vm = vm,
                    onOpenMcp = { openExclusive("mcp") },
                    onOpenLayers = { openExclusive("layers") },
                    onOpenColor = { openExclusive("color") },
                    onOpenToolSettings = { openExclusive("tool") },
                    onOpenSettings = { openExclusive("settings") },
                    onOpenExport = { openExclusive("export") },
                    onOpenNewProject = { openExclusive("new") },
                    onTextRequest = { x, y ->
                        textPoint = Offset(x, y)
                        showTextEntry = true
                    },
                    onBlocked = { vm.notify(it) },
                    onColorPicked = { vm.rememberColor(it) }
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 100.dp)
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

    if (showMcp) {
        McpSheet(vm = vm, onDismiss = { showMcp = false })
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

/** Portrait fallback for phones that are rotated or tablets in portrait. */
@Composable
private fun PortraitStudio(
    vm: StudioViewModel,
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        StudioTopBar(
            vm = vm,
            onOpenMcp = onOpenMcp,
            onOpenSettings = onOpenSettings,
            onOpenExport = onOpenExport
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CanvasStage(
                engine = engine,
                showPerf = vm.perfHud,
                layout = StudioLayout.PORTRAIT,
                onOpenMcp = onOpenMcp,
                onOpenLayers = onOpenLayers,
                onTextRequest = onTextRequest,
                onBlocked = onBlocked,
                onColorPicked = onColorPicked
            )
            ToolRail(
                engine = engine,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        StudioStatusBar(
            vm = vm,
            onOpenMcp = onOpenMcp,
            onOpenSettings = onOpenSettings
        )
        QuickToolBar(
            engine = engine,
            onOpenColor = onOpenColor,
            onOpenLayers = onOpenLayers,
            onOpenToolSettings = onOpenToolSettings
        )
        Timeline(
            engine = engine,
            onNewProject = onOpenNewProject,
            onOpenExport = onOpenExport,
            onNotice = vm::notify
        )
    }
}

@Composable
private fun StudioTopBar(
    vm: StudioViewModel,
    onOpenMcp: () -> Unit,
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
        StudioIconButton(
            icon = Icons.Rounded.Hub,
            contentDescription = "Connect an agent",
            onClick = onOpenMcp,
            size = 38.dp,
            tint = if (vm.server.running) Success else TextMuted
        )
        StudioIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = "Studio settings",
            onClick = onOpenSettings,
            size = 38.dp
        )
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
                tint = Color(0xFF0B0714),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun StudioStatusBar(
    vm: StudioViewModel,
    onOpenMcp: () -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(33.dp)
            .background(Ink900)
            .drawBehind {
                drawLine(
                    color = Color(0x12FFFFFF),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .clickable { if (active) onOpenMcp() else onOpenSettings() }
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                active -> "AGENT LIVE · ${engine.agentLabel.ifBlank {
                    latestEvent?.tool?.removePrefix("anymate_")?.replace('_', ' ') ?: "editing"
                }}"
                vm.server.running -> "MCP LIVE · ${vm.server.localIp}:${vm.server.port}"
                else -> "LOCAL STUDIO · MCP OFF"
            },
            color = if (active) TextPrimary else TextMuted,
            fontSize = 8.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${engine.fps} FPS · ${engine.frames.size} F · ${(engine.zoom * 100).roundToInt()}%",
            color = TextMuted,
            fontFamily = Mono,
            fontSize = 8.sp,
            maxLines = 1
        )
    }
}
