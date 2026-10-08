package com.example.ui.views.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.DocAnizerViewModel

@Composable
fun StepLlmInferenceSettings(
    viewModel: DocAnizerViewModel
) {
    val llmConfig by viewModel.llmInferenceConfig.collectAsState()
    var showInferenceInfoDialog by remember { mutableStateOf(false) }

    if (showInferenceInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInferenceInfoDialog = false },
            icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("⚙️ Lokaler KI Agent: Inferenz & Parameter", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Passe die Inferenz an deine Prioritäten an:\n\n" +
                            "• Temperatur: Niedrig (0.00 - 0.20) für höchste Exaktheit bei Zahlen, Datumsangaben & IBAN. Höher für freie Texterklärungen.\n" +
                            "• Aufgaben-Fokus: Schärft den lokalen System-Prompt auf Verträge, Rechnungen oder allgemeine Dokumente.\n" +
                            "• CPU-Threads: Mehr Threads steigern die Verarbeitungsgeschwindigkeit, während weniger Threads Akku sparen.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showInferenceInfoDialog = false }) {
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
                    text = "Lokaler KI Agent: Inferenz & Leistung",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Präzision, Erkennungsfokus & Prozessornutzung steuern.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showInferenceInfoDialog = true }) {
                Icon(Icons.Default.Info, contentDescription = "Info zu Inferenz", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Temperatur (Präzision)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = String.format(java.util.Locale.US, "%.2f", llmConfig.temperature),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = llmConfig.temperature,
                    onValueChange = { viewModel.setLlmTemperature(it) },
                    valueRange = 0.0f..0.7f,
                    steps = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (llmConfig.temperature <= 0.15f) "0.00: Deterministisch & exakt (optimal für Beträge, IBAN & Fristen)"
                           else if (llmConfig.temperature <= 0.40f) "0.35: Ausgewogen (ideal für strukturierte Belege)"
                           else "0.70: Flexibel (besser für freie Textzusammenfassungen)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Aufgaben-Spezialisierung", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = llmConfig.systemPromptFocus == "STANDARD",
                        onClick = { viewModel.setSystemPromptPreset("STANDARD") },
                        label = { Text("Allrounder") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = llmConfig.systemPromptFocus == "FINANCE",
                        onClick = { viewModel.setSystemPromptPreset("FINANCE") },
                        label = { Text("Finanzen") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = llmConfig.systemPromptFocus == "CONTRACTS",
                        onClick = { viewModel.setSystemPromptPreset("CONTRACTS") },
                        label = { Text("Verträge") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CPU-Thread Zuweisung", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = llmConfig.threadCount == 2,
                        onClick = { viewModel.setLlmCpuThreads(2) },
                        label = { Text("2 Threads (Akkusparend)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = llmConfig.threadCount >= 4,
                        onClick = { viewModel.setLlmCpuThreads(4) },
                        label = { Text("4 Threads (Schnell)") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Alle Parameter wirken nur lokal auf diesem Gerät. Keine Daten verlassen dein Smartphone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
