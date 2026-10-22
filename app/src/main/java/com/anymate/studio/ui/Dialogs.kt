package com.anymate.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.StudioViewModel
import com.anymate.studio.model.CanvasPresets
import com.anymate.studio.model.DefaultFpsChoices
import com.anymate.studio.ui.theme.AccentGradient
import com.anymate.studio.ui.theme.Cyan
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet

@Composable
fun NewProjectDialog(
    vm: StudioViewModel,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("New scene") }
    var presetIndex by remember { mutableIntStateOf(0) }
    var fps by remember { mutableIntStateOf(24) }
    val activePreset = CanvasPresets[presetIndex]

    StudioDialog(onDismiss = onDismiss) {
        Text("New project", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "A clean canvas. The current project is replaced, so export anything you want to keep.",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(16.dp))
        SettingField(
            label = "Project name",
            value = name,
            onValueChange = { name = it },
            placeholder = "Untitled"
        )
        Spacer(Modifier.height(14.dp))
        SectionLabel("Canvas")
        Spacer(Modifier.height(7.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CanvasPresets.take(3).chunked(2).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    row.forEach { preset ->
                        val index = CanvasPresets.indexOf(preset)
                        StudioChip(
                            text = "${preset.name} · ${preset.label}",
                            selected = presetIndex == index,
                            onClick = { presetIndex = index },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionLabel("Frame rate")
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            DefaultFpsChoices.forEach { option ->
                StudioChip(
                    text = "$option",
                    selected = fps == option,
                    onClick = { fps = option },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Type 60 for fluid motion. 24 for a cinematic feel. 12 for snappy, hand-drawn loops.",
            color = TextMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            GradientButton(
                text = "Create",
                onClick = {
                    vm.createProject(
                        name = name.ifBlank { "Untitled" },
                        preset = activePreset,
                        fps = fps
                    )
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
                height = 46.dp
            )
        }
    }
}

@Composable
fun WelcomeDialog(vm: StudioViewModel) {
    StudioDialog(onDismiss = vm::dismissWelcome) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(54.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "ANYMATE",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )
                Text(
                    "draw · animate · direct",
                    color = TextMuted,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Animate anything.",
            color = TextPrimary,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "A touch-first 2D studio with an AI director and a real MCP surface built in. One canvas, shared by you and any agent.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(18.dp))
        WelcomeFeature(
            Icons.Rounded.Brush,
            "Touch-first studio",
            "Pencil, ink, brush, fill, shapes, layers, onion skin and 12/24/30/60 fps."
        )
        WelcomeFeature(
            Icons.Rounded.AutoAwesome,
            "Director on the canvas",
            "Ask for a loop and watch every frame appear live. Works offline, or connect your own model."
        )
        WelcomeFeature(
            Icons.Rounded.Wifi,
            "MCP inside",
            "Claude, Cursor, VS Code or your own agent gets every tool, live snapshots and exports."
        )
        Spacer(Modifier.height(18.dp))
        GradientButton(
            text = "Start creating",
            icon = Icons.Rounded.AutoAwesome,
            onClick = vm::dismissWelcome,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The MCP server starts with the app on port 8420. Your token is in Settings.",
            color = TextMuted,
            fontSize = 9.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun WelcomeFeature(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Violet.copy(alpha = 0.12f))
                .border(1.dp, Violet.copy(alpha = 0.2f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Violet, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
fun TextEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("ANYMATE") }
    StudioDialog(onDismiss = onDismiss) {
        Text("Place text", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "The tap point is the baseline of the first line. Use line breaks for stacked type.",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { value = it.take(400) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 6,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (value.isNotBlank()) onConfirm(value)
            }),
            shape = RoundedCornerShape(13.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet,
                unfocusedBorderColor = Hairline,
                focusedContainerColor = Ink900,
                unfocusedContainerColor = Ink900,
                cursorColor = Violet
            )
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
            GradientButton(
                "Add text",
                icon = Icons.Rounded.TextFields,
                onClick = {
                    if (value.isNotBlank()) onConfirm(value)
                },
                modifier = Modifier.weight(1f),
                height = 46.dp
            )
        }
    }
}
