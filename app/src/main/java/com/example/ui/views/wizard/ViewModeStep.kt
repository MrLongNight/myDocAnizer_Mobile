package com.example.ui.views.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppViewLevel
import com.example.ui.DocAnizerViewModel

@Composable
fun StepViewModeAndRestore(
    viewModel: DocAnizerViewModel,
    isManualReconfig: Boolean = false,
    onStartRestore: () -> Unit = {}
) {
    val appViewLevel by viewModel.appViewLevel.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = "Ansichtsebene & Datenrettung",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Wähle den passenden Funktionsumfang für deine Arbeitsweise oder stelle Dokumente von einem früheren Gerät wieder her.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!isManualReconfig) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.SettingsBackupRestore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Neues Handy oder Datenrettung?",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bestehende Dokumente und Konfiguration aus einem USB-Stick oder Cloud-Backup wiederherstellen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onStartRestore,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Wiederherstellen", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (appViewLevel == AppViewLevel.STANDARD) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(
                width = if (appViewLevel == AppViewLevel.STANDARD) 2.dp else 1.dp,
                color = if (appViewLevel == AppViewLevel.STANDARD) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAppViewLevel(AppViewLevel.STANDARD) }
                .testTag("mode_standard_card")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadioButton(
                    selected = appViewLevel == AppViewLevel.STANDARD,
                    onClick = { viewModel.setAppViewLevel(AppViewLevel.STANDARD) }
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Standard-Ansicht (Empfohlen)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF16A34A).copy(alpha = 0.15f)) {
                            Text("9 Schritte", style = MaterialTheme.typography.labelSmall, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text(
                        text = "Fokussiert & einsteigerfreundlich: Speicherort, Kamera, automatische Farboptimierung, lokaler KI Agent & 3-2-1 Backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "✓ 1-Klick Scanner  ✓ Feste Kategorien  ✓ Lokaler Tresor",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (appViewLevel == AppViewLevel.ADVANCED) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(
                width = if (appViewLevel == AppViewLevel.ADVANCED) 2.dp else 1.dp,
                color = if (appViewLevel == AppViewLevel.ADVANCED) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAppViewLevel(AppViewLevel.ADVANCED) }
                .testTag("mode_advanced_card")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadioButton(
                    selected = appViewLevel == AppViewLevel.ADVANCED,
                    onClick = { viewModel.setAppViewLevel(AppViewLevel.ADVANCED) }
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Erweiterte Ansicht", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFD97706).copy(alpha = 0.15f)) {
                            Text("10 Schritte", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD97706), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text(
                        text = "Mehr Flexibilität: Aufgaben-Fokus (Finanzen/Verträge), Vorlagen & strukturierte Backup-Zeitpläne.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "✓ Flexible Workflows  ✓ Vorlagen & Regeln  ✓ Cloud & NAS Sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (appViewLevel == AppViewLevel.EXPERT) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = BorderStroke(
                width = if (appViewLevel == AppViewLevel.EXPERT) 2.dp else 1.dp,
                color = if (appViewLevel == AppViewLevel.EXPERT) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setAppViewLevel(AppViewLevel.EXPERT) }
                .testTag("mode_expert_card")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadioButton(
                    selected = appViewLevel == AppViewLevel.EXPERT,
                    onClick = { viewModel.setAppViewLevel(AppViewLevel.EXPERT) }
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Experten-Ansicht", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)) {
                            Text("11 Schritte", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Text(
                        text = "Volle Kontrolle: Feineinstellung von CPU-Threads, Temperatur, SQLite WAL-Wartung, FTS5 & Timeouts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "✓ KI-Inferenz Tuning  ✓ DB-Wartung & Index  ✓ Erweiterte Logs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Hinweis: Du kannst die Ansichtsebene jederzeit später im Menü unter 'Ansicht & Layout' oder in den Einstellungen anpassen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
