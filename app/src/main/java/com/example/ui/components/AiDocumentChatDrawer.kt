package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChatMessage
import com.example.model.DocumentEntity
import com.example.model.SavedChatSession
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.ui.DocAnizerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimalistischer, seitlicher KI-Assistent Drawer (vom linken Bildrand ein- und ausfahrbar).
 * 100% lokal & offline, dezent gestaltetes Schiebeelement, Roboter-KI-Icon,
 * integrierte Undo-Funktion, Editieren der letzten Anfrage und Session-Management (temporär vs. dauerhaft).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDocumentChatDrawer(
    isOpen: Boolean,
    onClose: () -> Unit,
    viewModel: DocAnizerViewModel,
    onExecuteAction: (String) -> Unit
) {
    val cyanColor = Color(0xFF00E5FF)
    val lilaColor = Color(0xFFA855F7)

    if (!isOpen) {
        return
    }

    BackHandler {
        onClose()
    }

    val context = LocalContext.current
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isChatGenerating.collectAsStateWithLifecycle()
    val selectedDoc by viewModel.selectedChatDocument.collectAsStateWithLifecycle()
    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()

    val savedChatSessions by viewModel.savedChatSessions.collectAsStateWithLifecycle()
    val currentChatSessionId by viewModel.currentChatSessionId.collectAsStateWithLifecycle()
    val currentChatTitle by viewModel.currentChatTitle.collectAsStateWithLifecycle()
    val lastUndoAction by viewModel.lastUndoAction.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var showDocSelector by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSessionsDialog by remember { mutableStateOf(false) }
    var saveTitleInput by remember { mutableStateOf("") }
    var sessionToRename by remember { mutableStateOf<SavedChatSession?>(null) }
    var renameTitleInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    LaunchedEffect(chatMessages.size, isGenerating) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Abgedunkelter Hintergrund (Tippen schließt das Panel)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onClose() }
        )

        // 2. Minimalistisches Seiten-Panel vom linken Bildrand mit Schiebeelement-Griff
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.86f)
                    .widthIn(max = 360.dp)
                    .pointerInput(Unit) {
                        var totalCloseDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalCloseDrag = 0f },
                            onDragEnd = { totalCloseDrag = 0f },
                            onDragCancel = { totalCloseDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalCloseDrag += dragAmount
                                if (totalCloseDrag < -30f) { // Nach links wischen schließt das Panel
                                    change.consume()
                                    totalCloseDrag = 0f
                                    onClose()
                                }
                            }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .imePadding()
                ) {
                    // KOPFZEILE (Schlank & Minimal mit Roboter AI Icon und Session Status)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(cyanColor, lilaColor))),
                                contentAlignment = Alignment.Center
                            ) {
                                LocalAiAgentIcon(
                                    modifier = Modifier.size(28.dp),
                                    tint = Color.White
                                )
                            }
                            Column {
                                Text(
                                    text = "Lokaler KI Agent",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (currentChatSessionId == null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = "💭 Temporär",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "💾 $currentChatTitle",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Schnell-Aktionen in der Kopfzeile
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Chat speichern Button
                            IconButton(
                                onClick = {
                                    val suggested = chatMessages.firstOrNull { it.isUser }?.text?.take(28)
                                        ?: "Chat vom " + SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date())
                                    saveTitleInput = if (currentChatSessionId != null) currentChatTitle else suggested
                                    showSaveDialog = true
                                },
                                modifier = Modifier.size(30.dp).testTag("btn_drawer_save_chat")
                            ) {
                                Icon(
                                    Icons.Default.BookmarkAdd,
                                    contentDescription = "Chat speichern",
                                    tint = if (currentChatSessionId != null) lilaColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Gespeicherte Chats Liste öffnen
                            IconButton(
                                onClick = { showSessionsDialog = true },
                                modifier = Modifier.size(30.dp).testTag("btn_drawer_saved_sessions")
                            ) {
                                Icon(
                                    Icons.Default.FolderSpecial,
                                    contentDescription = "Gespeicherte Chats anzeigen",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Neuer / Leeren Chat
                            IconButton(
                                onClick = { viewModel.clearChat() },
                                modifier = Modifier.size(30.dp).testTag("btn_drawer_clear_chat")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Chat leeren",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Schließen
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(30.dp).testTag("btn_drawer_close")
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Schließen",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 1.dp
                    )

                    // UNDO-LEISTE FÜR VOM LLM AUSGEFÜHRTE AKTIONEN
                    com.example.ui.chat.ChatContextChipsPane(
                        lastUndoAction = lastUndoAction,
                        onUndoClick = { viewModel.undoLastLlmAction() }
                    )

                    // CHAT-NACHRICHTEN FEED
                    com.example.ui.chat.ChatMessageListPane(
                        chatMessages = chatMessages,
                        isGenerating = isGenerating,
                        listState = listState,
                        onExecuteAction = { actionType ->
                            onExecuteAction(actionType)
                            onClose()
                        },
                        onCopyText = { text ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("myDocAnizer", text))
                            Toast.makeText(context, "Kopiert", Toast.LENGTH_SHORT).show()
                        },
                        onEditUserPrompt = { text ->
                            inputText = text
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // UNTERE EINGABE-LEISTE UND FOKUS-DOKUMENT BANNER
                    com.example.ui.chat.ChatInputBarPane(
                        inputText = inputText,
                        onInputTextChange = { inputText = it },
                        isGenerating = isGenerating,
                        selectedDoc = selectedDoc,
                        lastUserPrompt = viewModel.getLastUserPrompt(),
                        cyanColor = cyanColor,
                        lilaColor = lilaColor,
                        onAttachFileClick = { showDocSelector = true },
                        onClearSelectedDoc = { viewModel.selectChatDocument(null) },
                        onSendMessage = { text -> viewModel.sendChatMessage(text) }
                    )
                }
            }
        }
    }

    // DIALOG: Dokument anheften
    if (showDocSelector) {
        AlertDialog(
            onDismissRequest = { showDocSelector = false },
            title = { Text("Dokument für Frage anheften", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    if (allDocs.isEmpty()) {
                        Text("Noch keine Dokumente vorhanden.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(allDocs) { d ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectChatDocument(d)
                                            showDocSelector = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Column {
                                            Text(d.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text("${d.sender} • ${d.mainCategory}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDocSelector = false }) { Text("Schließen") }
            }
        )
    }

    // DIALOG: Chat speichern
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Chatverlauf speichern", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Gib diesem Chatverlauf einen Namen, um ihn später wieder aufzurufen. Gespeicherte Chats werden bei weiteren Fragen automatisch aktualisiert.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = saveTitleInput,
                        onValueChange = { saveTitleInput = it },
                        label = { Text("Bezeichnung / Thema") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_save_chat_title")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveTitleInput.isNotBlank()) {
                            viewModel.saveCurrentChat(saveTitleInput.trim())
                            showSaveDialog = false
                            Toast.makeText(context, "Chat gespeichert", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_save_chat")
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    // DIALOG: Gespeicherte Chats Liste & Wechsel
    if (showSessionsDialog) {
        AlertDialog(
            onDismissRequest = { showSessionsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gespeicherte Chats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${savedChatSessions.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Button für neuen temporären Chat
                    Button(
                        onClick = {
                            viewModel.startNewTemporaryChat()
                            showSessionsDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth().testTag("btn_new_temp_chat"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Neuer temporärer Chat (Standard)")
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    if (savedChatSessions.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Noch keine dauerhaft gespeicherten Chats.\nNutze das 💾-Icon in der Kopfzeile, um einen Chat zu sichern.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(savedChatSessions, key = { it.id }) { session ->
                                val isSelected = currentChatSessionId == session.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.loadSavedChatSession(session)
                                            showSessionsDialog = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(session.updatedAt))
                                            Text(
                                                text = "$dateStr • ${session.messages.size} Nachrichten",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    sessionToRename = session
                                                    renameTitleInput = session.title
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Umbenennen", modifier = Modifier.size(15.dp))
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteSavedChatSession(session.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSessionsDialog = false }) { Text("Schließen") }
            }
        )
    }

    // DIALOG: Chat umbenennen
    sessionToRename?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("Chat umbenennen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameTitleInput,
                    onValueChange = { renameTitleInput = it },
                    label = { Text("Neuer Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameTitleInput.isNotBlank()) {
                            viewModel.renameSavedChatSession(session.id, renameTitleInput.trim())
                            sessionToRename = null
                        }
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) { Text("Abbrechen") }
            }
        )
    }
}
