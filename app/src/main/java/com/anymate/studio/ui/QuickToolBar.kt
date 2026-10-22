package com.anymate.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.ui.theme.Amber
import com.anymate.studio.ui.theme.AnyMateMetrics
import com.anymate.studio.ui.theme.Cyan
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.Mono
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet
import kotlin.math.roundToInt

@Composable
fun QuickToolBar(
    engine: AnimationEngine,
    onOpenColor: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenToolSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(Ink900)
            .drawBehind {
                drawLine(
                    color = Color(0x1FFFFFFF),
                    start = Offset.Zero,
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(horizontal = 12.dp, vertical = if (AnyMateMetrics.compact) 3.dp else 7.dp)
    ) {
        val wide = maxWidth >= 420.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ColorSwatch(
                color = engine.strokeColor,
                onClick = onOpenColor,
                size = 36.dp
            )
            Column(modifier = Modifier.weight(1f).padding(top = 1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (engine.selectedTool.name == "TEXT") "TEXT SIZE" else "SIZE",
                        color = TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${engine.brushSize.roundToInt()} px",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = Mono
                    )
                }
                StudioSlider(
                    value = engine.brushSize,
                    onValueChange = { engine.updateBrushSize(it) },
                    range = engine.sizeRangeFor(),
                    modifier = Modifier.fillMaxWidth(),
                    activeColor = Violet
                )
            }
            if (wide) {
                Column(modifier = Modifier.weight(0.8f).padding(top = 1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "OPACITY",
                            color = TextMuted,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.1.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatPercent(engine.brushOpacity),
                            color = TextSecondary,
                            fontSize = 9.sp,
                            fontFamily = Mono
                        )
                    }
                    StudioSlider(
                        value = engine.brushOpacity,
                        onValueChange = { engine.updateBrushOpacity(it) },
                        range = 0.02f..1f,
                        modifier = Modifier.fillMaxWidth(),
                        activeColor = Cyan
                    )
                }
            }
            val layer = engine.layerOrNull()
            StudioIconButton(
                icon = if (layer?.locked == true) Icons.Rounded.Lock else Icons.Rounded.Layers,
                contentDescription = "Layers",
                onClick = onOpenLayers,
                tint = if (layer?.locked == true) Amber else TextSecondary,
                background = Ink800,
                size = 40.dp
            )
            StudioIconButton(
                icon = Icons.Rounded.Tune,
                contentDescription = "Tool options",
                onClick = onOpenToolSettings,
                tint = Violet,
                background = Ink800,
                size = 40.dp
            )
        }
    }
}
