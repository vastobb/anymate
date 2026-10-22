package com.anymate.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.BuildConfig
import com.anymate.studio.StudioViewModel
import com.anymate.studio.ui.theme.Amber
import com.anymate.studio.ui.theme.Cyan
import com.anymate.studio.ui.theme.Danger
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.Mono
import com.anymate.studio.ui.theme.Success
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.ThemeKind
import com.anymate.studio.ui.theme.Violet

@Composable
fun SettingsSheet(
    vm: StudioViewModel,
    onDismiss: () -> Unit,
    onNewProject: () -> Unit
) {
    val server = vm.server
    val engine = vm.engine
    val uriHandler = LocalUriHandler.current

    StudioBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        SheetHeader(
            title = "Studio settings",
            subtitle = "Appearance, playback, canvas and connections",
            onClose = onDismiss
        )
        Spacer(Modifier.height(16.dp))

        // ---- Appearance ----------------------------------------------------
        SettingsSection(icon = Icons.Rounded.Palette, title = "Appearance", tint = Violet) {
            SectionLabel("Theme")
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ThemeKind.entries.forEach { theme ->
                    val palette = theme.palette
                    val selected = vm.themeKind == theme
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) Violet.copy(alpha = 0.10f) else Ink900.copy(alpha = 0.45f)
                            )
                            .border(
                                1.dp,
                                if (selected) Violet.copy(alpha = 0.42f) else Hairline,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { vm.updateTheme(theme) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(palette.ink950)
                                    .border(1.dp, Hairline, CircleShape)
                            )
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(palette.ink800)
                                    .border(1.dp, Hairline, CircleShape)
                            )
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(palette.primary)
                            )
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(palette.secondary)
                            )
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(palette.paper)
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                theme.label,
                                color = if (selected) TextPrimary else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                            Text(theme.blurb, color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                        if (selected) {
                            Icon(
                                Icons.Rounded.AutoAwesome, null,
                                tint = Violet, modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            ToggleRow(
                title = "Compact layout",
                subtitle = "Tighter tools, shorter timeline, denser panels — more canvas",
                checked = vm.compactLayout,
                onCheckedChange = { vm.updateCompact(it) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---- MCP -----------------------------------------------------------
        SettingsSection(icon = Icons.Rounded.Wifi, title = "MCP server", tint = Cyan) {
            ToggleRow(
                title = "Enable MCP",
                subtitle = "Let Claude, Cursor, VS Code or any agent drive this canvas",
                checked = vm.mcpEnabled,
                onCheckedChange = { vm.updateMcpEnabled(it) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink900.copy(alpha = 0.7f))
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveDot(if (server.running) Success else TextMuted, 7.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (server.running) "Listening on ${server.localIp}:${server.port}" else "Server is off",
                    color = if (server.running) TextPrimary else TextMuted,
                    fontSize = 11.sp,
                    fontFamily = Mono,
                    modifier = Modifier.weight(1f)
                )
                Text("${server.toolCallCount} calls", color = TextMuted, fontSize = 9.sp, fontFamily = Mono)
            }
            server.lastError?.let { error ->
                Spacer(Modifier.height(7.dp))
                Text(error, color = Danger, fontSize = 10.sp)
            }
            Spacer(Modifier.height(10.dp))
            CopyValueRow(
                label = "MCP URL",
                value = server.mcpUrl,
                onCopied = { vm.notify("Copied MCP URL") }
            )
            Spacer(Modifier.height(7.dp))
            CopyValueRow(
                label = "Bearer token",
                value = server.token,
                masked = true,
                onCopied = { vm.notify("Copied MCP token") }
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = "New token",
                    icon = Icons.Rounded.Refresh,
                    onClick = { vm.regenerateMcpToken() },
                    modifier = Modifier.weight(1f)
                )
                GhostButton(
                    text = "USB loopback",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = { vm.notify("adb reverse tcp:${server.port} tcp:${server.port}") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Playback ------------------------------------------------------
        SettingsSection(icon = Icons.Rounded.Speed, title = "Playback & performance", tint = Amber) {
            ToggleRow(
                title = "Loop playback",
                subtitle = "Restart from frame 1 at the end",
                checked = engine.loop,
                onCheckedChange = { vm.updateLoop(it) }
            )
            ToggleRow(
                title = "Onion skin",
                subtitle = "Ghost previous and next frames while drawing",
                checked = engine.onionSkin,
                onCheckedChange = { vm.updateOnion(it) }
            )
            ToggleRow(
                title = "Performance HUD",
                subtitle = "Show live render FPS on the canvas",
                checked = vm.perfHud,
                onCheckedChange = { vm.updatePerfHud(it) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ink900.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Bolt, null, tint = Cyan, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Vector rendering · vsync draw · batched agent ingest · lazy thumbnails. Built for 60 fps bursts.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Canvas --------------------------------------------------------
        SettingsSection(icon = Icons.Rounded.ViewWeek, title = "Canvas", tint = Cyan) {
            InfoRow(Icons.Rounded.Memory, "Resolution", "${engine.canvasWidth} × ${engine.canvasHeight}")
            InfoRow(Icons.Rounded.Speed, "Frame rate", "${engine.fps} fps")
            InfoRow(Icons.Rounded.AddCircle, "Frames", "${engine.frames.size}")
            InfoRow(Icons.Rounded.Memory, "Autosave", "On · this device")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = "New project",
                    icon = Icons.Rounded.AddCircle,
                    onClick = {
                        onDismiss()
                        onNewProject()
                    },
                    modifier = Modifier.weight(1f)
                )
                GhostButton(
                    text = "Fit view",
                    icon = Icons.Rounded.FitScreen,
                    onClick = { engine.resetView() },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- About ---------------------------------------------------------
        SettingsSection(icon = Icons.Rounded.Settings, title = "About", tint = TextMuted) {
            InfoRow(Icons.Rounded.AutoAwesome, "AnyMate", "v${BuildConfig.VERSION_NAME}")
            InfoRow(Icons.Rounded.CloudOff, "Offline first", "No account required")
            InfoRow(Icons.Rounded.Hub, "Protocol", "Model Context Protocol")
            Spacer(Modifier.height(6.dp))
            Text(
                "A touch-first 2D animation studio with a first-class MCP surface. One canvas, shared by the artist and any agent.",
                color = TextMuted,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(
                text = "Open project on GitHub",
                icon = Icons.Rounded.ContentCopy,
                onClick = { uriHandler.openUri("https://github.com/vastobb/anymate") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun SettingsSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink800.copy(alpha = 0.55f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}
