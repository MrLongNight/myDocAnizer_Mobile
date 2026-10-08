package com.example.ui.views.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    if (showAiInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAiInfoDialog = false },
            icon = { Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("🧠 Lokaler KI Agent: Modelle", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "myDocAnizer führt modernste Sprachmodelle direkt auf deinem Smartphone aus – komplett offline ohne jegliche Cloud-Abhängigkeit.\n\n" +
                            "• Kompakte Modelle (z.B. SmolLM2 135M): Ultraschnell bei Rechnungen, Quittungen und Kassenbons.\n" +
                            "• Ausgewogene Modelle (z.B. Qwen2.5 0.5B): Hohe Präzision bei strukturierten Verträgen und mehrseitigen Dokumenten.\n" +
                            "• Größere Modelle (z.B. Gemma 2B): Maximale Sprachintelligenz für tiefe semantische Klauselprüfungen.",
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    text = "100% Offline direkt auf dem Gerät. Wähle das bevorzugte On-Device Modell für den lokalen KI Agenten:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showAiInfoDialog = true }) {
                Icon(Icons.Default.Info, contentDescription = "Info zum lokalen KI Agenten", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hardware: ${String.format(java.util.Locale.US, "%.1f", hardwareInfo.totalRamGb)} GB RAM (${hardwareInfo.cpuCores} CPU-Kerne)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Einstufung: ${hardwareInfo.performanceTier} • Verfügbarer RAM: ${String.format(java.util.Locale.US, "%.1f", hardwareInfo.availableRamGb)} GB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val recModel = availableModels.find { it.isHardwareRecommended }
                if (recModel != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "⭐ Empfehlung für dein Smartphone:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = recModel.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        availableModels.forEach { model ->
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
                    .clickable { viewModel.selectModel(model.id) }
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.selectModel(model.id) },
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
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (model.compatibilityLevel == ModelCompatibilityLevel.OPTIMAL) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF16A34A).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Optimal (${model.ramBadge})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF16A34A),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else if (model.recommendedRamGb > hardwareInfo.totalRamGb) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "⚠️ Hoher RAM-Bedarf (${model.ramBadge})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = model.quantFormat,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (model.isHardwareRecommended && model.hardwareRecommendationReason.isNotBlank()) {
                        Text(
                            text = "💡 ${model.hardwareRecommendationReason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}
