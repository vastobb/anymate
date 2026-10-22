package com.anymate.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.model.PremiumSwatches
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.Mono
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet
import com.anymate.studio.util.alphaFraction
import com.anymate.studio.util.hsvColor
import com.anymate.studio.util.parseHex
import com.anymate.studio.util.toComposeColor
import com.anymate.studio.util.toHex
import com.anymate.studio.util.toHsv
import kotlin.math.roundToInt

@Composable
fun ColorSheet(
    engine: AnimationEngine,
    recentColors: List<Long>,
    onDismiss: () -> Unit
) {
    val initial = remember { engine.strokeColor }
    val hsv = remember { initial.toHsv() }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(initial.alphaFraction()) }
    var hexText by remember { mutableStateOf(initial.toHex()) }

    val color = hsvColor(hue, saturation, value, alpha.coerceAtLeast(0.02f))
    LaunchedEffect(color) {
        engine.setColor(color)
        hexText = color.toHex()
    }

    fun applyColor(colorValue: Long) {
        val values = colorValue.toHsv()
        hue = values[0]
        saturation = values[1]
        value = values[2]
        alpha = colorValue.alphaFraction().coerceAtLeast(0.02f)
        engine.setColor(colorValue)
    }

    StudioBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier
            .heightIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SheetHeader(
            title = "Colour lab",
            subtitle = "Full HSV, alpha and the curated studio palette",
            onClose = onDismiss,
            trailing = {
                StudioIconButton(
                    icon = Icons.Rounded.Colorize,
                    contentDescription = "Pick colour from canvas",
                    onClick = {
                        engine.selectTool(com.anymate.studio.model.ToolType.EYEDROPPER)
                        onDismiss()
                    },
                    selected = engine.selectedTool == com.anymate.studio.model.ToolType.EYEDROPPER,
                    size = 38.dp
                )
            }
        )
        Spacer(Modifier.height(16.dp))

        // Saturation / value field.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    fun update(position: Offset) {
                        saturation = (position.x / size.width).coerceIn(0f, 1f)
                        value = (1f - position.y / size.height).coerceIn(0f, 1f)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        update(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            update(change.position)
                            change.consume()
                        }
                    }
                }
        ) {
            val hueColor = hsvColor(hue, 1f, 1f).toComposeColor()
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val thumb = Offset(saturation * size.width, (1f - value) * size.height)
            drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = 10.dp.toPx(), center = thumb)
            drawCircle(color = Color.White, radius = 8.dp.toPx(), center = thumb)
            drawCircle(color = color.toComposeColor(), radius = 6.dp.toPx(), center = thumb)
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(color.toComposeColor())
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(color.toHex(), color = TextPrimary, fontFamily = Mono, fontSize = 16.sp)
                Text(
                    "H ${hue.roundToInt()}°  S ${(saturation * 100).roundToInt()}%  V ${(value * 100).roundToInt()}%  A ${(alpha * 100).roundToInt()}%",
                    color = TextMuted,
                    fontFamily = Mono,
                    fontSize = 9.sp
                )
            }
            OutlinedTextField(
                value = hexText,
                onValueChange = { input ->
                    hexText = input
                    if (input.removePrefix("#").length in setOf(6, 8)) {
                        val parsed = parseHex(input, -1)
                        if (parsed != -1L) applyColor(parsed)
                    }
                },
                modifier = Modifier.width(112.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TextPrimary, fontSize = 11.sp, fontFamily = Mono
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Violet,
                    unfocusedBorderColor = Hairline,
                    focusedContainerColor = Ink900,
                    unfocusedContainerColor = Ink900,
                    cursorColor = Violet
                )
            )
        }

        Spacer(Modifier.height(18.dp))

        SectionLabel("Hue")
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    fun update(position: Offset) {
                        hue = ((position.x / size.width) * 360f).coerceIn(0f, 359.9f)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        update(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            update(change.position)
                            change.consume()
                        }
                    }
                }
        ) {
            val stops = (0..6).map { i ->
                hsvColor((i * 60f).coerceAtMost(359f), 1f, 1f).toComposeColor()
            }
            drawRect(Brush.horizontalGradient(stops))
            val x = (hue / 360f) * size.width
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 10.dp.toPx(), center = Offset(x, size.height / 2f))
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, size.height / 2f))
        }

        Spacer(Modifier.height(14.dp))

        SectionLabel("Alpha  ·  ${formatPercent(alpha)}")
        Spacer(Modifier.height(8.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    fun update(position: Offset) {
                        alpha = (position.x / size.width).coerceIn(0.02f, 1f)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        update(down.position)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            update(change.position)
                            change.consume()
                        }
                    }
                }
        ) {
            drawCheckerboard(Color(0xFF2A2433), Color(0xFF3A3446), 6.dp.toPx())
            val opaque = color.toComposeColor()
            drawRect(
                Brush.horizontalGradient(
                    listOf(opaque.copy(alpha = 0f), opaque.copy(alpha = 1f))
                )
            )
            val x = alpha * size.width
            drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(x, size.height / 2f))
            drawCircle(color.toComposeColor(), radius = 5.dp.toPx(), center = Offset(x, size.height / 2f))
        }

        if (recentColors.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SectionLabel("Recent")
            Spacer(Modifier.height(9.dp))
            SwatchRows(
                colors = recentColors,
                current = color,
                onPick = ::applyColor
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Studio palette")
        Spacer(Modifier.height(9.dp))
        SwatchRows(
            colors = PremiumSwatches,
            current = color,
            onPick = ::applyColor
        )

        Spacer(Modifier.height(16.dp))
        GhostButton(
            text = "Pick a colour from the canvas",
            icon = Icons.Rounded.Colorize,
            onClick = {
                engine.selectTool(com.anymate.studio.model.ToolType.EYEDROPPER)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SwatchRows(colors: List<Long>, current: Long, onPick: (Long) -> Unit) {
    val rows = colors.chunked(8)
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { color ->
                    val selected = color == current
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color.toComposeColor())
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                            .clickable { onPick(color) }
                    )
                }
            }
        }
    }
}
