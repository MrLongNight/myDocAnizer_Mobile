package com.example.ui.views.wizard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.HuggingFaceModelInfo
import com.example.model.ModelCompatibilityLevel
import com.example.ui.DocAnizerViewModel

@Composable
fun StepLlmModelSelection(
    viewModel: DocAnizerViewModel,
    availableModels: List<HuggingFaceModelInfo>
) {
    val hardwareInfo by viewModel.deviceHardwareInfo.collectAsState()
    var showAiInfoDialog by remember { mutableStateOf(false) }

    val recModel = availableModels.find { it.isHardwareRecommended } ?: availableModels.firstOrNull()
    val isAnyDownloading = availableModels.any { it.isDownloading }
    val isAnyDownloaded = availableModels.any { it.isDownloaded }

    if (showAiInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAiInfoDialog = false },
            icon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("🧠 Lokaler KI Agent: Details", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "myDocAnizer führt modernste Sprachmodelle direkt auf deinem Smartphone aus – komplett offline ohne jegliche Cloud-Abhängigkeit.\n\n" +
                            "• Kompakte Modelle (z.B. SmolLM2 360M / Qwen 2.5 0.5B): Ultraschnell bei Rechnungen, Quittungen und Kassenbons.\n" +
                            "• Ausgewogene Modelle (z.B. Llama 3.2 1B): Hohe Präzision bei Verträgen und mehrseitigen Dokumenten.\n" +
                            "• Größere Modelle (z.B. Gemma 2B): Maximale Sprachintelligenz für komplexe semantische Analysen.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showAiInfoDialog = false }) {
                    Text("Verstanden", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lokaler KI Agent: Modellauswahl",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "100% Offline direkt auf dem Smartphone ohne Cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showAiInfoDialog = true }) {
                Icon(Icons.Default.Info, contentDescription = "Info zum lokalen KI Agenten", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // 1. TRANSPARENTE FEATURE-AUSFALL-INFO (WAS GEHT VERLOREN OHNE KI?)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Vorteile eines lokalen KI-Modells für deinen Tresor",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "• Automatische Erkennung von Rechnungsbetrag, MwSt., Datum & Händler\n• Automatische Verschlagwortung (Tags) & Ordner-Vorschläge im Tresor\n• Offline-Dokumentenassistent & Volltext-Fragen zu deinen Unterlagen",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp).padding(top = 1.dp)
                    )
                    Text(
                        text = "Ohne Modell: Alle Metadaten müssen manuell erfasst werden; KI-Analyse & Chat stehen nicht zur Verfügung.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        // 2. HARDWARE-INFO & EMPFOHLENES MODELL SCHNELL-AKTION
        if (recModel != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "⭐ Empfehlung für dein Smartphone (${hardwareInfo.performanceTier}):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = "${recModel.downloadSizeMb} MB",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = recModel.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = recModel.descriptionDe,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )

                    // Download-Status & Sofort-Aktion
                    if (recModel.isDownloaded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                            Text(
                                text = "Vollständig heruntergeladen & einsatzbereit!",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (recModel.isDownloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            LinearProgressIndicator(
                                progress = { recModel.downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Lädt im Hintergrund... ${(recModel.downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Du kannst direkt auf 'Weiter' tippen!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.selectModel(recModel.id)
                                viewModel.startBackgroundDownload(recModel.id)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth().testTag("btn_wizard_download_rec_model")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Empfohlenes Modell wählen & im Hintergrund laden",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Hinweis zu Hintergrund-Downloads
        Text(
            text = "Alle Modelle (Auswahl startet Download im Hintergrund – kein Warten nötig):",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 3. LAZYCOLUMN MODELL-KATALOG
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableModels, key = { it.id }) { model ->
                val isSelected = model.isSelected
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = when {
                        isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        model.isHardwareRecommended -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        else -> null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectModel(model.id)
                            if (!model.isDownloaded && !model.isDownloading) {
                                viewModel.startBackgroundDownload(model.id)
                            }
                        }
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectModel(model.id)
                                    if (!model.isDownloaded && !model.isDownloading) {
                                        viewModel.startBackgroundDownload(model.id)
                                    }
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = model.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${model.downloadSizeMb} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = model.descriptionDe,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }

                        // Badges & Download-Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (model.isHardwareRecommended) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "⭐ Hardware-Empfehlung",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            if (model.isDownloaded) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF16A34A).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "✓ Einsatzbereit",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF16A34A),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            } else if (model.isDownloading) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        progress = { model.downloadProgress },
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp
                                    )
                                    Text(
                                        text = "${(model.downloadProgress * 100).toInt()}% im Hintergrund",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    IconButton(
                                        onClick = { viewModel.cancelDownload(model.id) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Pausieren", modifier = Modifier.size(10.dp))
                                    }
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = { viewModel.startBackgroundDownload(model.id) },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(22.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Laden", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = model.quantFormat,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
