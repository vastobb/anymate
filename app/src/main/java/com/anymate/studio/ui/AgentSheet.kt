package com.anymate.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anymate.studio.ChatRole
import com.anymate.studio.StudioViewModel
import com.anymate.studio.ui.theme.Cyan
import com.anymate.studio.ui.theme.Danger
import com.anymate.studio.ui.theme.Hairline
import com.anymate.studio.ui.theme.Ink800
import com.anymate.studio.ui.theme.Ink850
import com.anymate.studio.ui.theme.Ink900
import com.anymate.studio.ui.theme.Mono
import com.anymate.studio.ui.theme.Success
import com.anymate.studio.ui.theme.TextMuted
import com.anymate.studio.ui.theme.TextPrimary
import com.anymate.studio.ui.theme.TextSecondary
import com.anymate.studio.ui.theme.Violet

private data class AgentPrompt(
    val label: String,
    val prompt: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val quickPrompts = listOf(
    AgentPrompt("Bouncing ball", "Make a bouncing ball loop with squash, stretch and a soft shadow", Icons.Rounded.Brush),
    AgentPrompt("Walk cycle", "Create a walk cycle at 60 fps", Icons.Rounded.AutoAwesome),
    AgentPrompt("Fireworks", "Make looping fireworks at 30 fps", Icons.Rounded.FilterNone),
    AgentPrompt("Kinetic type", "Animate the text \"HELLO\" as kinetic typography", Icons.Rounded.TextFields)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSheet(
    vm: StudioViewModel,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val engine = vm.engine
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf("") }

    fun submit() {
        val value = input.trim()
        if (value.isNotEmpty() && !vm.thinking) {
            vm.sendPrompt(value)
            input = ""
            keyboard?.hide()
        }
    }

    LaunchedEffect(vm.chat.size, vm.thinking) {
        if (vm.chat.isNotEmpty()) {
            runCatching {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Ink850,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .navigationBarsPadding()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1B1623), Color(0xFF0F0C13)))
                )
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Violet, Cyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome, null,
                        tint = Color(0xFF0B0714), modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "AnyMate Director",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        if (vm.agentOffline) "Local Director · offline" else "${vm.agentModel.ifBlank { "Connected model" }} · live tools",
                        color = TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StudioIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Agent settings",
                    onClick = {
                        onDismiss()
                        onOpenSettings()
                    },
                    size = 36.dp
                )
                StudioIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    size = 36.dp
                )
            }

            Spacer(Modifier.height(11.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(quickPrompts) { prompt ->
                    StudioChip(
                        text = prompt.label,
                        icon = prompt.icon,
                        selected = false,
                        onClick = {
                            if (!vm.thinking) {
                                vm.sendPrompt(prompt.prompt)
                            }
                        },
                        modifier = Modifier.then(
                            if (vm.thinking) Modifier.alpha(0.45f) else Modifier
                        )
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.chat, key = { it.id }) { message ->
                    ChatBubble(message)
                }
                if (vm.thinking) {
                    item(key = "typing") {
                        TypingBubble(
                            status = vm.agentStatus.ifBlank { "Thinking" },
                            events = engine.agentEvents.take(2).map { it.tool to it.ok }
                        )
                    }
                }
            }

            if (vm.thinking) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(Ink800)
                        .border(1.dp, Violet.copy(alpha = 0.24f), RoundedCornerShape(15.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(Violet, 7.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "DIRECTOR LIVE",
                            color = Violet,
                            fontSize = 8.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            vm.agentStatus.ifBlank { "Working on the canvas" },
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        StudioIconButton(
                            icon = Icons.Rounded.Stop,
                            contentDescription = "Stop the agent",
                            onClick = vm::stopAgent,
                            size = 30.dp,
                            tint = Danger
                        )
                    }
                    if (engine.agentProgress > 0f) {
                        Spacer(Modifier.height(7.dp))
                        LinearProgressIndicator(
                            progress = { engine.agentProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape),
                            color = Violet,
                            trackColor = Ink900
                        )
                    }
                }
            }

            // Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(700) },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (vm.thinking) "Director is working…" else "Describe a scene or motion…",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    maxLines = 4,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    ),
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet.copy(alpha = 0.55f),
                        unfocusedBorderColor = Hairline,
                        focusedContainerColor = Ink900,
                        unfocusedContainerColor = Ink900,
                        cursorColor = Violet
                    )
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (vm.thinking || input.isBlank()) {
                                Brush.linearGradient(listOf(Ink800, Ink800))
                            } else {
                                Brush.linearGradient(listOf(Violet, Cyan))
                            }
                        )
                        .then(
                            if (!vm.thinking && input.isNotBlank()) {
                                Modifier.clickable { submit() }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (vm.thinking || input.isBlank()) TextMuted else Color(0xFF0B0714),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = TextMuted, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    "Every brush, frame and layer · actions are live · batches undo as one step",
                    color = TextMuted,
                    fontSize = 8.sp
                )
                if (vm.agentOffline) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Connect model →",
                        color = Cyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            onDismiss()
                            onOpenSettings()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: com.anymate.studio.ChatMessage) {
    when (message.role) {
        ChatRole.SYSTEM -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    message.text,
                    color = TextMuted,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Ink900)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        ChatRole.USER -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .clip(RoundedCornerShape(16.dp, 16.dp, 5.dp, 16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF4C2B8F), Color(0xFF34245F))))
                        .border(1.dp, Violet.copy(alpha = 0.27f), RoundedCornerShape(16.dp, 16.dp, 5.dp, 16.dp))
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) {
                    Text(
                        message.text,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
        ChatRole.ASSISTANT -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Violet, Cyan))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome, null,
                        tint = Color(0xFF0B0714), modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .widthIn(max = 310.dp)
                        .clip(RoundedCornerShape(5.dp, 16.dp, 16.dp, 16.dp))
                        .background(if (message.isError) Danger.copy(alpha = 0.09f) else Ink800)
                        .border(
                            1.dp,
                            if (message.isError) Danger.copy(alpha = 0.3f) else Hairline,
                            RoundedCornerShape(5.dp, 16.dp, 16.dp, 16.dp)
                        )
                        .padding(horizontal = 13.dp, vertical = 10.dp)
                ) {
                    Text(
                        cleanAgentText(message.text),
                        color = if (message.isError) Color(0xFFFFC8CE) else TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(status: String, events: List<Pair<String, Boolean>>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(25.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Violet, Cyan))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFF0B0714), modifier = Modifier.size(13.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp, 16.dp, 16.dp, 16.dp))
                .background(Ink800)
                .border(1.dp, Hairline, RoundedCornerShape(5.dp, 16.dp, 16.dp, 16.dp))
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(Violet, 6.dp)
                Spacer(Modifier.width(6.dp))
                Text(status, color = TextSecondary, fontSize = 11.sp)
            }
            events.forEach { (tool, ok) ->
                Text(
                    "  ${if (ok) "✓" else "!"} ${tool.removePrefix("anymate_").replace('_', ' ')}",
                    color = if (ok) TextMuted else Danger,
                    fontFamily = Mono,
                    fontSize = 8.sp
                )
            }
        }
    }
}

private fun cleanAgentText(text: String): String {
    return text
        .replace("**", "")
        .replace(Regex("_([^_]+)_"), "$1")
}
