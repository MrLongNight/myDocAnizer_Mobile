package com.example.ui.views.rules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DocRule
import com.example.ui.DocAnizerViewModel

@Composable
fun RuleTestingDialog(
    viewModel: DocAnizerViewModel,
    rules: List<DocRule>,
    onDismiss: () -> Unit
) {
    var sampleText by remember { mutableStateOf("Rechnung Nr. 2026-981 von Vodafone GmbH über Internet und DSL Dienste. Betrag: 49.99 EUR.") }
    var testResultText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Regelwerk live testen", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Geben Sie Testtext ein (z. B. Belegtext oder Rechnungs-Snippet), um zu prüfen, welche Regel sofort (unter 5ms) greifen würde.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = sampleText,
                    onValueChange = { sampleText = it },
                    label = { Text("Dokumenten-Testtext / OCR Snippet") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                Button(
                    onClick = {
                        val matched = rules.filter { it.isEnabled }.firstOrNull { rule ->
                            val textLower = sampleText.lowercase()
                            val matchesAllKeywords = rule.matchKeywords.all { kw -> textLower.contains(kw.lowercase()) }
                            val hasNoExcluded = rule.excludeKeywords.none { excl -> textLower.contains(excl.lowercase()) }
                            matchesAllKeywords && hasNoExcluded
                        }
                        if (matched != null) {
                            testResultText = "✅ Treffer! Regel '${matched.name}' greift.\nZiel: ${matched.targetMainCategoryId}_${matched.targetSubCategoryId}\nTyp: ${matched.targetDocType}\nAbsender: ${matched.detectedSender.ifBlank { "Auto" }}"
                        } else {
                            testResultText = "ℹ️ Kein direkter Regel-Treffer. Das Dokument würde an das lokale On-Device LLM für semantische Analyse übergeben werden."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Regeln gegen Text testen")
                }

                testResultText?.let { result ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}
