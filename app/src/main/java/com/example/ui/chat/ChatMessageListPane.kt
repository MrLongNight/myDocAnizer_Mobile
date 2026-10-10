package com.example.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.ui.components.LocalAiAgentIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatMessageListPane(
    chatMessages: List<ChatMessage>,
    isGenerating: Boolean,
    listState: LazyListState,
    onExecuteAction: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onEditUserPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        items(chatMessages, key = { it.id }) { msg ->
            DrawerChatBubble(
                message = msg,
                onExecuteAction = onExecuteAction,
                onCopyText = onCopyText,
                onEditUserPrompt = onEditUserPrompt
            )
        }

        if (isGenerating) {
            item(key = "thinking_indicator") {
                DrawerThinkingIndicator()
            }
        }
    }
}

/**
 * Kompakte Chat-Blase im Drawer mit Roboter-KI-Icon und Edit-Button für Nutzer-Nachrichten
 */
@Composable
private fun DrawerChatBubble(
    message: ChatMessage,
    onExecuteAction: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onEditUserPrompt: (String) -> Unit
) {
    val isUser = message.isUser
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser && message.attachedDocTitle != null) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = "📎 ${message.attachedDocTitle}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 2.dp,
                bottomEnd = if (isUser) 2.dp else 14.dp
            ),
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = if (!isUser) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.96f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header für Bot oder User-Aktionen
                if (!isUser) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            LocalAiAgentIcon(modifier = Modifier.size(20.dp), tint = Color(0xFFA855F7))
                            Text("Assistent", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFA855F7))
                        }
                        IconButton(
                            onClick = { onCopyText(message.text) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onEditUserPrompt(message.text) },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Anfrage bearbeiten", modifier = Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }

                // Nachrichten-Inhalt mit Markdown & Brief-Parsing
                SelectionContainer {
                    DrawerMessageText(rawText = message.text, isUser = isUser, onCopyBlock = onCopyText)
                }

                // Interaktiver App-Aktionsbutton
                if (!isUser && message.actionType != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onExecuteAction(message.actionType) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = message.actionLabel ?: "Aktion ausführen",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Zeitstempel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

/**
 * Text-Renderer für Markdown und Musterschreiben
 */
@Composable
private fun DrawerMessageText(
    rawText: String,
    isUser: Boolean,
    onCopyBlock: (String) -> Unit
) {
    if (rawText.contains("```")) {
        val parts = rawText.split("```")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            parts.forEachIndexed { idx, part ->
                if (idx % 2 == 1) {
                    val clean = part.removePrefix("text").removePrefix("\n").trim()
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📄 Musterschreiben", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFA855F7))
                                TextButton(
                                    onClick = { onCopyBlock(clean) },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Kopieren", fontSize = 10.sp)
                                }
                            }
                            Text(
                                text = clean,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else if (part.isNotBlank()) {
                    DrawerAnnotatedText(text = part.trim(), isUser = isUser)
                }
            }
        }
    } else {
        DrawerAnnotatedText(text = rawText, isUser = isUser)
    }
}

@Composable
private fun DrawerAnnotatedText(text: String, isUser: Boolean) {
    val annotated = remember(text) {
        buildAnnotatedString {
            var cursor = 0
            val pattern = java.util.regex.Pattern.compile("\\*\\*(.*?)\\*\\*")
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                val bold = matcher.group(1) ?: ""
                if (start > cursor) append(text.substring(cursor, start))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                cursor = end
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        lineHeight = 18.sp
    )
}

@Composable
private fun DrawerThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_anim")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.widthIn(max = 220.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LocalAiAgentIcon(modifier = Modifier.size(20.dp), tint = Color(0xFFA855F7))
            Text("Assistent denkt nach...", style = MaterialTheme.typography.labelSmall)
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00E5FF).copy(alpha = alpha)))
        }
    }
}
