package com.anymate.studio.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anymate.studio.ui.theme.AccentGradient
import com.anymate.studio.ui.theme.Amber
import com.anymate.studio.ui.theme.Danger
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink700
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink850
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.Mono
import com.anymate.studio.ui.theme.Success
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet
import com.anymate.studio.util.toComposeColor
import com.anymate.studio.util.toHex
import kotlin.math.roundToInt

@Composable
fun StudioIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = TextSecondary,
    activeTint: Color = Violet,
    background: Color = Color.Transparent,
    size: Dp = 42.dp,
    iconScale: Float = 0.48f
) {
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (selected) Violet.copy(alpha = 0.13f) else background)
            .then(
                if (selected) Modifier.border(1.dp, Violet.copy(alpha = 0.34f), shape)
                else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) activeTint else tint.copy(alpha = if (enabled) 0.9f else 0.28f),
            modifier = Modifier.size(size * iconScale)
        )
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
    color: Color = Ink850.copy(alpha = 0.94f),
    borderColor: Color = Hairline,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.dp, borderColor, shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 52.dp
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (enabled) AccentGradient else Brush.linearGradient(listOf(Ink700, Ink700)))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button; contentDescription = text },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon, null,
                    tint = if (enabled) Color(0xFF120A24) else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text,
                color = if (enabled) Color(0xFF120A24) else TextMuted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.1.sp
            )
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tint: Color = TextSecondary
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .border(1.dp, Hairline, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { role = Role.Button; contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(16.dp))
        }
        Text(
            text, color = tint.copy(alpha = if (enabled) 1f else 0.35f),
            fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StudioChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selectedColor: Color = Violet
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(if (selected) selectedColor.copy(alpha = 0.16f) else Ink800.copy(alpha = 0.7f))
            .border(1.dp, if (selected) selectedColor.copy(alpha = 0.5f) else Hairline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
            .semantics { role = Role.Button; this.selected = selected; contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) selectedColor else TextMuted, modifier = Modifier.size(14.dp))
        }
        Text(
            text,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = TextMuted,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp
    )
}

@Composable
fun StudioSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    activeColor: Color = Violet
) {
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onValueChange,
        valueRange = range,
        modifier = modifier.height(30.dp),
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFF7F4FF),
            activeTrackColor = activeColor,
            inactiveTrackColor = Color(0xFF3A3446),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
    )
}

@Composable
fun ColorSwatch(
    color: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    showRing: Boolean = true
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                drawCheckerboard(Color(0xFFDDD8CE), Color(0xFFF7F4EC), 5.dp.toPx())
            }
            .clip(shape)
            .background(color.toComposeColor())
            .then(
                if (showRing) Modifier.border(2.dp, Color(0xFFF7F4FF).copy(alpha = 0.85f), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { role = Role.Button; contentDescription = "Colour ${color.toHex()}" }
    )
}

fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckerboard(
    colorA: Color,
    colorB: Color,
    cell: Float
) {
    val cols = (size.width / cell).toInt() + 1
    val rows = (size.height / cell).toInt() + 1
    for (row in 0..rows) {
        for (col in 0..cols) {
            drawRect(
                color = if ((row + col) % 2 == 0) colorA else colorB,
                topLeft = Offset(col * cell, row * cell),
                size = Size(cell, cell)
            )
        }
    }
}

@Composable
fun BrandMark(size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val side = this.size.minDimension
        drawRoundRect(
            brush = AccentGradient,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.28f),
            size = androidx.compose.ui.geometry.Size(side, side)
        )
        val play = androidx.compose.ui.graphics.Path().apply {
            moveTo(side * 0.38f, side * 0.32f)
            cubicTo(
                side * 0.35f, side * 0.29f,
                side * 0.32f, side * 0.31f,
                side * 0.32f, side * 0.35f
            )
            lineTo(side * 0.32f, side * 0.65f)
            cubicTo(
                side * 0.32f, side * 0.69f,
                side * 0.35f, side * 0.71f,
                side * 0.38f, side * 0.69f
            )
            lineTo(side * 0.70f, side * 0.53f)
            cubicTo(
                side * 0.73f, side * 0.515f,
                side * 0.73f, side * 0.485f,
                side * 0.70f, side * 0.47f
            )
            close()
        }
        drawPath(play, Color(0xFFF7F5FF))
        drawCircle(
            color = Color.White.copy(alpha = 0.48f),
            radius = side * 0.027f,
            center = Offset(side * 0.72f, side * 0.28f)
        )
    }
}

@Composable
fun LiveDot(color: Color = Success, size: Dp = 7.dp) {
    val transition = rememberInfiniteTransition(label = "live-dot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "live-alpha"
    )
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) trailing()
        Spacer(Modifier.width(4.dp))
        StudioIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Close",
            onClick = onClose,
            size = 38.dp
        )
    }
}

@Composable
fun StudioSheetSurface(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1A1623), Color(0xFF100D15)))
            )
            .padding(horizontal = 20.dp),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Color(0xFF100D15),
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = { DragHandle() }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1A1623), Color(0xFF100D15)))
                )
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp),
            content = content
        )
    }
}

@Composable
fun DragHandle() {
    Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF4A4358))
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0C0B0F),
                checkedTrackColor = Violet,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Ink700,
                uncheckedBorderColor = Hairline
            )
        )
    }
}

@Composable
fun SettingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true
) {
    var visible by remember { mutableStateOf(false) }
    Column(modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            placeholder = { Text(placeholder, color = TextMuted.copy(alpha = 0.7f), fontSize = 13.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = if (password) Mono else androidx.compose.ui.text.font.FontFamily.Default
            ),
            visualTransformation = if (password && !visible) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else androidx.compose.ui.text.input.VisualTransformation.None,
            trailingIcon = if (password) {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (visible) "Hide key" else "Show key",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            keyboardOptions = keyboardOptions,
            shape = RoundedCornerShape(13.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet.copy(alpha = 0.8f),
                unfocusedBorderColor = Hairline,
                focusedContainerColor = Ink900.copy(alpha = 0.6f),
                unfocusedContainerColor = Ink900.copy(alpha = 0.45f),
                cursorColor = Violet
            )
        )
    }
}

@Composable
fun CopyValueRow(
    label: String,
    value: String,
    onCopied: (String) -> Unit,
    masked: Boolean = false
) {
    val clipboard = LocalClipboardManager.current
    var revealed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Ink900.copy(alpha = 0.7f))
            .border(1.dp, Hairline, RoundedCornerShape(13.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                if (masked && !revealed) "•".repeat(value.length.coerceAtMost(24)) else value,
                color = TextPrimary,
                fontFamily = Mono,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (masked) {
            StudioIconButton(
                icon = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (revealed) "Hide" else "Reveal",
                onClick = { revealed = !revealed },
                size = 32.dp
            )
        }
        StudioIconButton(
            icon = Icons.Rounded.ContentCopy,
            contentDescription = "Copy $label",
            onClick = {
                clipboard.setText(AnnotatedString(value))
                onCopied(label)
            },
            size = 32.dp,
            tint = Violet
        )
    }
}

@Composable
fun StudioDialog(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = Ink850,
                border = BorderStroke(1.dp, Color(0x26FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp), content = content)
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink850,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(message, fontSize = 13.sp, lineHeight = 20.sp) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(); onDismiss() },
                colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) Danger else Violet)
            ) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = TextMuted)) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, tint: Color = TextSecondary) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = Mono)
    }
}

fun formatPercent(value: Float): String = "${(value * 100).roundToInt()}%"

fun formatBytes(bytes: Long): String = when {
    bytes > 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576f)
    bytes > 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
