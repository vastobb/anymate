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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.model.ShapeKind
import com.anymate.studio.model.ToolType
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet
import com.anymate.studio.util.toComposeColor
import kotlin.math.roundToInt

@Composable
fun ToolSettingsSheet(
    engine: AnimationEngine,
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit
) {
    val tool = engine.selectedTool
    StudioBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        SheetHeader(
            title = tool.label,
            subtitle = tool.hint,
            onClose = onDismiss
        )
        Spacer(Modifier.height(18.dp))

        if (tool in setOf(ToolType.PENCIL, ToolType.PEN, ToolType.BRUSH, ToolType.ERASER, ToolType.SHAPE, ToolType.TEXT)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Ink800)
                        .border(1.dp, Hairline, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size((engine.brushSize.coerceIn(2f, 36f)).dp)
                            .clip(CircleShape)
                            .background(engine.strokeColor.toComposeColor())
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        SectionLabel(if (tool == ToolType.TEXT) "Text size" else "Brush size")
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${engine.brushSize.roundToInt()} px",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = com.anymate.studio.ui.theme.Mono
                        )
                    }
                    StudioSlider(
                        value = engine.brushSize,
                        onValueChange = { engine.setBrushSize(it) },
                        range = engine.sizeRangeFor(tool),
                        activeColor = Violet
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row {
                SectionLabel("Opacity")
                Spacer(Modifier.weight(1f))
                Text(formatPercent(engine.brushOpacity), color = TextSecondary, fontSize = 11.sp, fontFamily = com.anymate.studio.ui.theme.Mono)
            }
            StudioSlider(
                value = engine.brushOpacity,
                onValueChange = { engine.setBrushOpacity(it) },
                range = 0.02f..1f,
                modifier = Modifier.fillMaxWidth(),
                activeColor = com.anymate.studio.ui.theme.Cyan
            )
        }

        if (tool == ToolType.SHAPE) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Shape")
            Spacer(Modifier.height(9.dp))
            val kinds = listOf(
                ShapeKind.LINE to "Line",
                ShapeKind.RECT to "Rectangle",
                ShapeKind.ELLIPSE to "Ellipse",
                ShapeKind.ARROW to "Arrow"
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (kind, label) ->
                            StudioChip(
                                text = label,
                                selected = engine.shapeKind == kind,
                                onClick = { engine.setShapeKind(kind) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = "Fill shape",
                subtitle = "Uses the active colour as a solid fill",
                checked = engine.shapeFilled,
                onCheckedChange = { engine.setShapeFilled(it) }
            )
        }

        if (tool == ToolType.TEXT) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink800)
                    .padding(14.dp)
            ) {
                Text("Tap the canvas to place type", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("The point you tap is the text baseline. Line breaks are supported.", color = TextMuted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }

        if (tool == ToolType.FILL) {
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink800)
                    .padding(14.dp)
            ) {
                Text("Flood fill", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("Tap an enclosed area. Tolerance follows anti-aliased edges on the active layer.", color = TextMuted, fontSize = 11.sp, lineHeight = 17.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Layer")
        Spacer(Modifier.height(8.dp))
        val layer = engine.layerOrNull()
        val locked = layer?.locked == true
        ToggleRow(
            title = "Lock active layer",
            subtitle = "Protects art from taps and AI edits",
            checked = locked,
            onCheckedChange = { engine.setLayerLocked(engine.currentLayer, it) }
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                text = "Clear layer",
                icon = Icons.Rounded.DeleteSweep,
                modifier = Modifier.weight(1f),
                onClick = {
                    try {
                        engine.clearLayer()
                        onNotice("Layer cleared")
                    } catch (t: Throwable) {
                        onNotice(t.message ?: "Could not clear layer")
                    }
                }
            )
            GhostButton(
                text = "Clear frame",
                icon = Icons.Rounded.CleaningServices,
                modifier = Modifier.weight(1f),
                onClick = {
                    try {
                        engine.clearFrame()
                        onNotice("Frame cleared")
                    } catch (t: Throwable) {
                        onNotice(t.message ?: "Could not clear frame")
                    }
                }
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}
