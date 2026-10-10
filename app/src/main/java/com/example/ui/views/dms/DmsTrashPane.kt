package com.example.ui.views.dms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DocumentEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reiter: Mülleimer / Papierkorb
 * Soft-Delete Ablage mit Wiederherstellung, dauerhafter Bereinigung & einstellbarer Aufbewahrungsdauer
 */
@Composable
fun DmsTrashPane(
    trashDocuments: List<DocumentEntity>,
    trashRetentionDays: Int,
    onRestoreDocument: (DocumentEntity) -> Unit,
    onPermanentlyDeleteDocument: (DocumentEntity) -> Unit,
    onEmptyTrash: () -> Unit,
    onSetRetentionDays: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    var showEmptyConfirmDialog by remember { mutableStateOf(false) }
    var showRetentionMenu by remember { mutableStateOf(false) }

    if (showEmptyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Papierkorb unwiderruflich leeren?") },
            text = {
                Text("Alle ${trashDocuments.size} Dokumente im Papierkorb werden endgültig vom Smartphone gelöscht und können nicht mehr wiederhergestellt werden.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEmptyTrash()
                        showEmptyConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Endgültig leeren")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirmDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Papierkorb Einstellungs- & Aktionsleiste
        item(key = "trash_header_actions") {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Mülleimer / Papierkorb", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (trashDocuments.isNotEmpty()) {
                            TextButton(
                                onClick = { showEmptyConfirmDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Papierkorb leeren", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Automatische Aufbewahrungsdauer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatische Löschung nach:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (trashRetentionDays == 0) "Nie (Nur manuell leeren)" else "$trashRetentionDays Tagen",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box {
                            OutlinedButton(
                                onClick = { showRetentionMenu = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Frist ändern", style = MaterialTheme.typography.labelSmall)
                            }

                            DropdownMenu(
                                expanded = showRetentionMenu,
                                onDismissRequest = { showRetentionMenu = false }
                            ) {
                                val retentionOptions = listOf(
                                    7 to "7 Tage",
                                    14 to "14 Tage",
                                    30 to "30 Tage (Standard)",
                                    60 to "60 Tage",
                                    90 to "90 Tage",
                                    0 to "Nie automatisch löschen"
                                )
                                retentionOptions.forEach { (days, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontWeight = if (trashRetentionDays == days) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            onSetRetentionDays(days)
                                            showRetentionMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (trashDocuments.isEmpty()) {
            item(key = "trash_empty_state") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Der Papierkorb ist leer",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gelöschte Dokumente landen zuerst hier und können jederzeit wiederhergestellt werden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(trashDocuments, key = { "trash_${it.id}" }) { doc ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "Ursprünglicher Ordner: ${doc.mainCategory}/${doc.subCategory} • Absender: ${doc.sender.ifBlank { "Unbekannt" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (doc.deletedAt != null) {
                            Text(
                                text = "Gelöscht am: ${sdf.format(Date(doc.deletedAt))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onRestoreDocument(doc) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wiederherstellen", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { onPermanentlyDeleteDocument(doc) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Endgültig löschen", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
