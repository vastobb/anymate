package com.anymate.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.ExportKind
import com.anymate.studio.StudioViewModel
import com.anymate.studio.ui.theme.AccentGradient
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
fun ExportSheet(vm: StudioViewModel, onDismiss: () -> Unit) {
    val engine = vm.engine
    val state = vm.exportState
    var quality by remember { mutableIntStateOf(720) }
    val running = state?.running == true

    StudioBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        SheetHeader(
            title = "Export",
            subtitle = "${engine.frames.size} frames · ${engine.fps} fps · ${engine.canvasWidth}×${engine.canvasHeight}",
            onClose = onDismiss
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("GIF quality")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(480, 720, 1080).forEach { option ->
                StudioChip(
                    text = if (option == 1080) "1080 px" else "$option px",
                    selected = quality == option,
                    onClick = { quality = option },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        ExportCard(
            icon = Icons.Rounded.Movie,
            title = "Animated GIF",
            description = "A seamless loop that autoplays in chats, docs and browsers. GIF timing rounds to hundredths of a second, so 60 fps projects export near 50 fps.",
            accent = Violet,
            buttonLabel = "Export GIF",
            enabled = !running,
            onClick = { vm.startExport(ExportKind.GIF, quality) }
        )
        Spacer(Modifier.height(10.dp))
        ExportCard(
            icon = Icons.Rounded.Image,
            title = "Current frame PNG",
            description = "Full-resolution still of the frame on your canvas. Perfect for posters, thumbnails and review.",
            accent = Cyan,
            buttonLabel = "Export PNG",
            enabled = !running,
            onClick = { vm.startExport(ExportKind.PNG) },
            primary = false
        )
        Spacer(Modifier.height(10.dp))
        ExportCard(
            icon = Icons.Rounded.FileDownload,
            title = "PNG sequence",
            description = "Every frame as a numbered PNG inside a ZIP — ready for After Effects, Blender, Procreate or any editor.",
            accent = com.anymate.studio.ui.theme.Amber,
            buttonLabel = "Export frames",
            enabled = !running,
            onClick = { vm.startExport(ExportKind.ZIP) },
            primary = false
        )

        Spacer(Modifier.height(10.dp))
        ExportCard(
            icon = Icons.Rounded.FolderOpen,
            title = "AnyMate project",
            description = "The complete editable document — frames, layers, strokes, fills and type — in one .anymate file. Share it, back it up or move it to another phone.",
            accent = Violet,
            buttonLabel = "Share project file",
            enabled = !running,
            onClick = { vm.shareProject() },
            primary = false
        )

        if (state != null) {
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Ink800)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (state.error == null) Success.copy(alpha = 0.14f) else Danger.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (state.running) Icons.Rounded.Movie else Icons.Rounded.FileDownload,
                            null,
                            tint = when {
                                state.error != null -> Danger
                                state.running -> Violet
                                else -> Success
                            },
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                state.error != null -> "Could not export"
                                state.running -> "Rendering on device"
                                else -> "Ready to share"
                            },
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            state.error ?: state.label,
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontFamily = Mono,
                            maxLines = 2
                        )
                    }
                    if (state.running) {
                        StudioIconButton(
                            icon = Icons.Rounded.Stop,
                            contentDescription = "Cancel export",
                            onClick = vm::cancelExport,
                            size = 34.dp,
                            tint = Danger
                        )
                    }
                }
                if (state.running) {
                    Spacer(Modifier.height(11.dp))
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Violet,
                        trackColor = Ink900
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        formatPercent(state.progress),
                        color = TextSecondary,
                        fontFamily = Mono,
                        fontSize = 9.sp
                    )
                }
                val readyFile = state.file
                if (!state.running && readyFile != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        readyFile.name,
                        color = TextSecondary,
                        fontFamily = Mono,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatBytes(readyFile.length()),
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientButton(
                            text = "Share",
                            icon = Icons.Rounded.Share,
                            onClick = vm::shareExport,
                            modifier = Modifier.weight(1f),
                            height = 46.dp
                        )
                        GhostButton(
                            text = "Done",
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.65f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Exports are rendered on this phone. Files are kept in the app cache and can be shared anywhere.",
            color = TextMuted,
            fontSize = 10.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ExportCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    accent: Color,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
    primary: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ink800.copy(alpha = 0.7f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.size(11.dp))
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(9.dp))
        Text(description, color = TextMuted, fontSize = 11.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(13.dp))
        if (primary) {
            GradientButton(
                text = buttonLabel,
                icon = Icons.Rounded.FileDownload,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                height = 46.dp
            )
        } else {
            GhostButton(
                text = buttonLabel,
                icon = Icons.Rounded.FileDownload,
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                tint = accent
            )
        }
    }
}
