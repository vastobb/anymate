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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.StudioViewModel
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

/**
 * The connection panel. AnyMate no longer contains an assistant — the studio is
 * driven by hand, and by any MCP agent that can reach this device.
 */
@Composable
fun McpSheet(vm: StudioViewModel, onDismiss: () -> Unit) {
    val server = vm.server
    StudioBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        SheetHeader(
            title = "Connect an agent",
            subtitle = "AnyMate speaks Model Context Protocol over your network",
            onClose = onDismiss
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (server.running) Violet.copy(alpha = 0.10f) else Ink900.copy(alpha = 0.6f)
                )
                .border(
                    1.dp,
                    if (server.running) Violet.copy(alpha = 0.35f) else Hairline,
                    RoundedCornerShape(16.dp)
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (server.running) Violet.copy(alpha = 0.18f) else Ink800),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    if (server.running) Icons.Rounded.Wifi else Icons.Rounded.Bolt,
                    null,
                    tint = if (server.running) Violet else TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (server.running) "Server is live" else "Server is stopped",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (server.running) {
                        "Listening on ${server.localIp}:${server.port} · ${server.toolCallCount} tool calls"
                    } else {
                        "Turn the server on to let an agent draw on this canvas."
                    },
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp
                )
            }
            androidx.compose.material3.Switch(
                checked = vm.mcpEnabled,
                onCheckedChange = { vm.updateMcpEnabled(it) },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Ink900,
                    checkedTrackColor = Violet,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Ink800,
                    uncheckedBorderColor = Hairline
                )
            )
        }

        server.lastError?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error, color = Danger, fontSize = 10.sp)
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Connection")
        Spacer(Modifier.height(8.dp))
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
                onClick = {
                    vm.notify("adb reverse tcp:${server.port} tcp:${server.port} → ${server.loopbackUrl}")
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("Client config")
        Spacer(Modifier.height(8.dp))
        val httpConfig = """
            {
              "mcpServers": {
                "anymate": {
                  "type": "http",
                  "url": "${server.mcpUrl}",
                  "headers": { "Authorization": "Bearer ${server.token}" }
                }
              }
            }
        """.trimIndent()
        CodeBlock(httpConfig, "Copy HTTP config") { vm.notify("Copied HTTP client config") }

        Spacer(Modifier.height(10.dp))
        val stdioConfig = """
            {
              "mcpServers": {
                "anymate": {
                  "command": "node",
                  "args": ["/path/to/anymate/mcp-bridge/index.mjs"],
                  "env": {
                    "ANYMATE_URL": "${server.mcpUrl}",
                    "ANYMATE_TOKEN": "${server.token}"
                  }
                }
              }
            }
        """.trimIndent()
        CodeBlock(stdioConfig, "Copy stdio config") { vm.notify("Copied stdio client config") }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Ink900.copy(alpha = 0.6f))
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            androidx.compose.material3.Icon(
                Icons.Rounded.Security, null,
                tint = Cyan, modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Anyone with the token on your network can draw on this canvas. Keep it private and switch the server off when you don't need it. Full guide: docs/MCP.md",
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CodeBlock(code: String, action: String, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Ink900.copy(alpha = 0.75f))
            .border(1.dp, Hairline, RoundedCornerShape(13.dp))
            .padding(11.dp)
    ) {
        Text(
            code,
            color = TextSecondary,
            fontFamily = Mono,
            fontSize = 9.sp,
            lineHeight = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onCopy)
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                Icons.Rounded.ContentCopy, null,
                tint = Violet, modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(action, color = Violet, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}
