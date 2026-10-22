package com.anymate.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
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
            subtitle = "Connections, AI, playback and canvas",
            onClose = onDismiss
        )
        Spacer(Modifier.height(16.dp))

        // ---- MCP ---------------------------------------------------------
        SettingsSection(
            icon = Icons.Rounded.Wifi,
            title = "MCP server",
            tint = Cyan
        ) {
            ToggleRow(
                title = "Enable MCP",
                subtitle = "Let Claude, Cursor, VS Code or a custom agent drive this canvas",
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
                Text(
                    "${server.toolCallCount} calls",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontFamily = Mono
                )
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
                    text = "Loopback",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = {
                        vm.notify("USB: adb reverse tcp:${server.port} tcp:${server.port}")
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(11.dp))
            Text(
                "Use header · Authorization: Bearer <token>. Or append ?token=… for clients without headers. " +
                    "For USB, run adb reverse tcp:${server.port} tcp:${server.port} and connect to ${server.loopbackUrl}. " +
                    "The server runs while AnyMate is open.",
                color = TextMuted,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(Violet.copy(alpha = 0.08f))
                    .padding(11.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Rounded.Security, null, tint = Violet, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Anyone with the token on your network can draw on this canvas. Keep the token private and turn MCP off when you don't need it.",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- AI ----------------------------------------------------------
        SettingsSection(
            icon = Icons.Rounded.AutoAwesome,
            title = "AI agent",
            tint = Violet
        ) {
            ToggleRow(
                title = "Local Director (offline)",
                subtitle = "Built-in motion templates — no key, no network. Turn off to connect a model.",
                checked = vm.agentOffline,
                onCheckedChange = { vm.updateAgentOffline(it) }
            )
            if (!vm.agentOffline) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StudioChip(
                        text = "OpenAI",
                        onClick = {
                            vm.updateAgentEndpoint("https://api.openai.com/v1")
                            vm.updateAgentModel("gpt-4o-mini")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    StudioChip(
                        text = "OpenRouter",
                        onClick = {
                            vm.updateAgentEndpoint("https://openrouter.ai/api/v1")
                            vm.updateAgentModel("openai/gpt-4o-mini")
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StudioChip(
                        text = "Ollama (LAN)",
                        onClick = {
                            vm.updateAgentEndpoint("http://192.168.1.2:11434/v1")
                            vm.updateAgentModel("llama3.2")
                        },
                        modifier = Modifier.weight(1f)
                    )
                    StudioChip(
                        text = "Groq",
                        onClick = {
                            vm.updateAgentEndpoint("https://api.groq.com/openai/v1")
                            vm.updateAgentModel("llama-3.3-70b-versatile")
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingField(
                    label = "Endpoint",
                    value = vm.agentEndpoint,
                    onValueChange = vm::setAgentEndpoint,
                    placeholder = "https://api.openai.com/v1"
                )
                Spacer(Modifier.height(10.dp))
                SettingField(
                    label = "Model",
                    value = vm.agentModel,
                    onValueChange = vm::setAgentModel,
                    placeholder = "gpt-4o-mini"
                )
                Spacer(Modifier.height(10.dp))
                SettingField(
                    label = "API key",
                    value = vm.agentKey,
                    onValueChange = vm::setAgentKey,
                    placeholder = "sk-… (stored on this device)",
                    password = true
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Any OpenAI-compatible /chat/completions endpoint works. The model receives every studio tool and can see PNG snapshots of its own work. Tool calls appear live on your canvas.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 16.sp
                )
            } else {
                Spacer(Modifier.height(9.dp))
                Text(
                    "The Local Director paints paragraph-length prompts like “walk cycle at 60 fps” or “fireworks” using built-in motion systems. Connect a model for fully bespoke animation.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Playback ----------------------------------------------------
        SettingsSection(
            icon = Icons.Rounded.Speed,
            title = "Playback & performance",
            tint = Amber
        ) {
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

        // ---- Canvas ------------------------------------------------------
        SettingsSection(
            icon = Icons.Rounded.Palette,
            title = "Canvas",
            tint = Cyan
        ) {
            InfoRow(Icons.Rounded.Memory, "Resolution", "${engine.canvasWidth} × ${engine.canvasHeight}")
            InfoRow(Icons.Rounded.Speed, "Frame rate", "${engine.fps} fps")
            InfoRow(Icons.Rounded.AddCircle, "Frames", "${engine.frames.size}")
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

        // ---- About -------------------------------------------------------
        SettingsSection(
            icon = Icons.Rounded.Settings,
            title = "About",
            tint = TextMuted
        ) {
            InfoRow(Icons.Rounded.AutoAwesome, "AnyMate", "v${BuildConfig.VERSION_NAME}")
            InfoRow(Icons.Rounded.CloudOff, "Offline first", "No account required")
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
