package com.example.ui.views.dashboard.custom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChecklistItem
import com.example.model.CustomDashboardWidget
import com.example.model.DocumentEntity
import java.text.NumberFormat

@Composable
fun FilterDocumentsWidgetContent(
    widget: CustomDashboardWidget,
    scopedDocs: List<DocumentEntity>,
    accentColor: Color,
    currFmt: NumberFormat,
    onNavigateToCategory: (String) -> Unit
) {
    val matchingDocs = remember(scopedDocs, widget.targetCategory) {
        scopedDocs.filter {
            it.mainCategory.equals(widget.targetCategory, ignoreCase = true) ||
            it.subCategory.equals(widget.targetCategory, ignoreCase = true)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${matchingDocs.size} Beleg(e) in '${widget.targetCategory}'",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(
            onClick = { onNavigateToCategory(widget.targetCategory) },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("Im Tresor öffnen", fontSize = 11.sp, color = accentColor)
            Spacer(modifier = Modifier.width(3.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp), tint = accentColor)
        }
    }

    if (matchingDocs.isNotEmpty()) {
        matchingDocs.take(3).forEach { doc ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(doc.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if ((doc.amount ?: 0.0) > 0) {
                        Text(currFmt.format(doc.amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = accentColor)
                    }
                }
            }
        }
    }
}

@Composable
fun TagFilterWidgetContent(
    widget: CustomDashboardWidget,
    scopedDocs: List<DocumentEntity>
) {
    val searchTag = widget.targetTag.removePrefix("#").trim()
    val matchingDocs = remember(scopedDocs, searchTag) {
        scopedDocs.filter {
            it.tags.contains(searchTag, ignoreCase = true) ||
            it.title.contains(searchTag, ignoreCase = true)
        }
    }

    Text(
        text = "${matchingDocs.size} Belege mit '${widget.targetTag}' im gewählten Zeitraum",
        style = MaterialTheme.typography.bodySmall
    )

    if (matchingDocs.isNotEmpty()) {
        matchingDocs.take(3).forEach { doc ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(doc.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(doc.mainCategory, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun StatCounterWidgetContent(
    widget: CustomDashboardWidget,
    scopedDocs: List<DocumentEntity>,
    accentColor: Color,
    currFmt: NumberFormat
) {
    val matchingDocs = remember(scopedDocs, widget.targetCategory) {
        scopedDocs.filter { it.mainCategory.equals(widget.targetCategory, ignoreCase = true) }
    }
    val sumAmount = matchingDocs.mapNotNull { it.amount }.sum()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Anzahl Belege", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${matchingDocs.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
            }
        }
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Gesamtsumme", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(currFmt.format(sumAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
            }
        }
    }
}

@Composable
fun NoteMemoWidgetContent(
    widget: CustomDashboardWidget,
    accentColor: Color,
    onUpdateNote: (String) -> Unit
) {
    var isEditingText by remember { mutableStateOf(false) }
    var tempText by remember(widget.noteContent) { mutableStateOf(widget.noteContent) }

    if (isEditingText) {
        OutlinedTextField(
            value = tempText,
            onValueChange = { tempText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            placeholder = { Text("Notiz tippen...") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { isEditingText = false }) { Text("Abbrechen") }
            Button(
                onClick = {
                    onUpdateNote(tempText)
                    isEditingText = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
            ) {
                Text("Speichern")
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isEditingText = true }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = if (widget.noteContent.isBlank()) "Tippe hier, um eine Notiz zu erfassen..." else widget.noteContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (widget.noteContent.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ChecklistWidgetContent(
    widget: CustomDashboardWidget,
    onUpdateChecklist: (List<ChecklistItem>) -> Unit
) {
    var newTodoText by remember { mutableStateOf("") }
    val items = widget.checklistItems

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val updated = items.map {
                            if (it.id == item.id) it.copy(isDone = !it.isDone) else it
                        }
                        onUpdateChecklist(updated)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isDone,
                    onCheckedChange = { checked ->
                        val updated = items.map {
                            if (it.id == item.id) it.copy(isDone = checked) else it
                        }
                        onUpdateChecklist(updated)
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (!item.linkedDocTitle.isNullOrBlank() || item.dueDate != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (!item.linkedDocTitle.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = item.linkedDocTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (item.dueDate != null) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.secondary)
                                        Text(
                                            text = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date(item.dueDate)),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                IconButton(
                    onClick = {
                        val updated = items.filter { it.id != item.id }
                        onUpdateChecklist(updated)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Löschen", modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = newTodoText,
                onValueChange = { newTodoText = it },
                placeholder = { Text("Neuer Punkt...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = {
                    if (newTodoText.isNotBlank()) {
                        val updated = items + ChecklistItem(text = newTodoText.trim(), isDone = false)
                        onUpdateChecklist(updated)
                        newTodoText = ""
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun BudgetGoalWidgetContent(
    widget: CustomDashboardWidget,
    scopedDocs: List<DocumentEntity>,
    accentColor: Color,
    currFmt: NumberFormat
) {
    val totalSpent = remember(scopedDocs) {
        scopedDocs.mapNotNull { it.amount }.sum()
    }
    val target = if (widget.targetAmount > 0) widget.targetAmount else 500.0
    val progress = (totalSpent / target).toFloat().coerceIn(0f, 1f)
    val isExceeded = totalSpent > target

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Ausgegeben: ${currFmt.format(totalSpent)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text("Limit: ${currFmt.format(target)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isExceeded) Color(0xFFEF4444) else accentColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (isExceeded) {
            Text(
                text = "⚠️ Limit um ${currFmt.format(totalSpent - target)} überschritten!",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
