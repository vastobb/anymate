package com.anymate.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.anymate.studio.engine.AnimationEngine
import com.anymate.studio.model.ToolType
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink850
import com.anymate.studio.ui.theme.Violet
import com.anymate.studio.ui.theme.Cyan

private data class ToolItem(val tool: ToolType, val icon: ImageVector)

private val toolItems = listOf(
    ToolItem(ToolType.PENCIL, Icons.Rounded.Edit),
    ToolItem(ToolType.PEN, Icons.Rounded.Create),
    ToolItem(ToolType.BRUSH, Icons.Rounded.Brush),
    ToolItem(ToolType.ERASER, Icons.Rounded.CleaningServices),
    ToolItem(ToolType.FILL, Icons.Rounded.FormatColorFill),
    ToolItem(ToolType.SHAPE, Icons.Rounded.CropSquare),
    ToolItem(ToolType.TEXT, Icons.Rounded.TextFields),
    ToolItem(ToolType.EYEDROPPER, Icons.Rounded.Colorize),
    ToolItem(ToolType.HAND, Icons.Rounded.PanTool)
)

@Composable
fun ToolRail(
    engine: AnimationEngine,
    modifier: Modifier = Modifier,
    onToolLongPress: (ToolType) -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val scroll = rememberScrollState()
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = 14.dp, bottom = 8.dp, start = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .width(58.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Ink850.copy(alpha = 0.96f))
                .border(1.dp, Hairline, RoundedCornerShape(20.dp))
                .verticalScroll(scroll)
                .padding(vertical = 6.dp, horizontal = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            toolItems.forEach { item ->
                val selected = engine.selectedTool == item.tool
                StudioIconButton(
                    icon = item.icon,
                    contentDescription = item.tool.label,
                    selected = selected,
                    onClick = {
                        engine.selectTool(item.tool)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    size = 46.dp,
                    iconScale = 0.44f,
                    activeTint = if (item.tool == ToolType.HAND) Cyan else Violet
                )
            }
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .width(22.dp)
                    .height(1.dp)
                    .background(Hairline)
            )
            Spacer(Modifier.height(3.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF4D455D))
            )
        }
    }
}
