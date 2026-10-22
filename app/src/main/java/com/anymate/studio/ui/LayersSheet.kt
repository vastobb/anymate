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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.ui.theme.Amber
import com.anymate.studio.ui.theme.Cyan
import com.anymate.studio.ui.theme.Danger
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.Mono
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet

@Composable
fun LayersSheet(
    engine: AnimationEngine,
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var renameIndex by remember { mutableStateOf<Int?>(null) }
    var deleteIndex by remember { mutableStateOf<Int?>(null) }
    var renameText by remember { mutableStateOf("") }

    StudioBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        SheetHeader(
            title = "Layers",
            subtitle = "Top layers paint over lower layers",
            onClose = onDismiss,
            trailing = {
                StudioIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add layer",
                    onClick = {
                        engine.addLayer()
                        onNotice("Layer added")
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    tint = Violet,
                    size = 38.dp
                )
            }
        )
        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (index in engine.layers.lastIndex downTo 0) {
                val layer = engine.layers[index]
                val selected = engine.currentLayer == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (selected) Violet.copy(alpha = 0.10f) else Ink800.copy(alpha = 0.65f))
                        .border(
                            width = 1.dp,
                            color = if (selected) Violet.copy(alpha = 0.45f) else Hairline,
                            shape = RoundedCornerShape(15.dp)
                        )
                        .clickable {
                            engine.selectLayer(index)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StudioIconButton(
                            icon = if (layer.visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = if (layer.visible) "Hide layer" else "Show layer",
                            onClick = { engine.setLayerVisible(index, !layer.visible) },
                            size = 36.dp,
                            tint = if (layer.visible) Cyan else TextMuted
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    layer.name,
                                    color = if (selected) TextPrimary else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1
                                )
                                if (index == engine.layers.lastIndex) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "TOP",
                                        color = TextMuted,
                                        fontSize = 7.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            Text(
                                if (layer.locked) "Locked" else "${(layer.opacity * 100).toInt()}% · ${if (layer.visible) "visible" else "hidden"}",
                                color = if (layer.locked) Amber else TextMuted,
                                fontSize = 9.sp,
                                fontFamily = Mono
                            )
                        }
                        StudioIconButton(
                            icon = if (layer.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = if (layer.locked) "Unlock layer" else "Lock layer",
                            onClick = { engine.setLayerLocked(index, !layer.locked) },
                            size = 36.dp,
                            tint = if (layer.locked) Amber else TextMuted
                        )
                        Box {
                            StudioIconButton(
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = "Layer options",
                                onClick = { menuIndex = index },
                                size = 36.dp
                            )
                            DropdownMenu(
                                expanded = menuIndex == index,
                                onDismissRequest = { menuIndex = null },
                                containerColor = Ink800
                            ) {
                                LayerMenuItem("Rename", Icons.Rounded.Edit) {
                                    menuIndex = null
                                    renameIndex = index
                                    renameText = layer.name
                                }
                                if (index < engine.layers.lastIndex) {
                                    LayerMenuItem("Move up", Icons.Rounded.ArrowUpward) {
                                        menuIndex = null
                                        engine.moveLayer(index, index + 1)
                                        onNotice("Layer moved up")
                                    }
                                }
                                if (index > 0) {
                                    LayerMenuItem("Move down", Icons.Rounded.ArrowDownward) {
                                        menuIndex = null
                                        engine.moveLayer(index, index - 1)
                                        onNotice("Layer moved down")
                                    }
                                }
                                LayerMenuItem("Clear layer", Icons.Rounded.DeleteSweep) {
                                    menuIndex = null
                                    engine.clearLayer(layerIndex = index)
                                    onNotice("Layer cleared")
                                }
                                LayerMenuItem("Delete layer", Icons.Rounded.Delete, Danger) {
                                    menuIndex = null
                                    if (engine.layers.size > 1) deleteIndex = index
                                    else onNotice("A project needs at least one layer")
                                }
                            }
                        }
                    }
                    if (selected) {
                        Row(
                            modifier = Modifier.padding(start = 10.dp, end = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("OPACITY", color = TextMuted, fontSize = 8.sp, letterSpacing = 1.sp)
                            Spacer(Modifier.width(8.dp))
                            StudioSlider(
                                value = layer.opacity,
                                onValueChange = { engine.setLayerOpacity(index, it) },
                                range = 0f..1f,
                                modifier = Modifier.weight(1f),
                                activeColor = Violet
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${(layer.opacity * 100).toInt()}%",
                                color = TextSecondary,
                                fontFamily = Mono,
                                fontSize = 9.sp,
                                modifier = Modifier.width(32.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Structure changes (add, move, delete) reset undo history. Drawing on layers is undoable.",
            color = TextMuted,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        GhostButton(
            text = "Add layer",
            icon = Icons.Rounded.Add,
            onClick = { engine.addLayer() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
    }

    renameIndex?.let { index ->
        StudioDialog(onDismiss = { renameIndex = null }) {
            Text("Rename layer", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = renameText,
                onValueChange = { renameText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                shape = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 14.sp),
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
                GhostButton("Cancel", onClick = { renameIndex = null }, modifier = Modifier.weight(1f))
                GradientButton(
                    "Rename",
                    onClick = {
                        engine.renameLayer(index, renameText)
                        renameIndex = null
                    },
                    modifier = Modifier.weight(1f),
                    height = 44.dp
                )
            }
        }
    }

    deleteIndex?.let { index ->
        ConfirmDialog(
            title = "Delete layer?",
            message = "This removes \"${engine.layers.getOrNull(index)?.name ?: "layer"}\" from every frame. Layer deletions cannot be undone.",
            confirmLabel = "Delete layer",
            destructive = true,
            onConfirm = {
                try {
                    engine.deleteLayer(index)
                    onNotice("Layer deleted")
                } catch (t: Throwable) {
                    onNotice(t.message ?: "Could not delete layer")
                }
            },
            onDismiss = { deleteIndex = null }
        )
    }
}

@Composable
private fun LayerMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = TextSecondary,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, fontSize = 13.sp, color = TextPrimary) },
        leadingIcon = { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) },
        onClick = onClick
    )
}
