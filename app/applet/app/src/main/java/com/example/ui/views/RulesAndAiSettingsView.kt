package com.example.ui.views

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.rules.DocumentRulesSettingsTab as RulesDocumentRulesSettingsTab
import com.example.ui.views.rules.OnDeviceAiAgentSettingsTab as RulesOnDeviceAiAgentSettingsTab

@Composable
fun RulesAndAiSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    RulesDocumentRulesSettingsTab(viewModel = viewModel, modifier = modifier)
}

@Composable
fun DocumentRulesSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    RulesDocumentRulesSettingsTab(viewModel = viewModel, modifier = modifier)
}

@Composable
fun OnDeviceAiAgentSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    RulesOnDeviceAiAgentSettingsTab(viewModel = viewModel, modifier = modifier)
}

@Composable
fun SystemDiagnosticsAndLogTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    AuditLogContent(
        viewModel = viewModel,
        modifier = modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogContent(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val modelBenchmarks by viewModel.modelBenchmarks.collectAsStateWithLifecycle()
    val activeModel = availableModels.find { it.isSelected } ?: availableModels.firstOrNull()
    var selectedCategoryFilter by remember { mutableStateOf<com.example.service.LogCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showBenchmarkOverview by remember { mutableStateOf(false) }

    val filteredLogs = remember(auditLogs, selectedCategoryFilter, searchQuery) {
        auditLogs.filter { entry ->
            val matchesCategory = selectedCategoryFilter == null || entry.category == selectedCategoryFilter
            val matchesSearch = searchQuery.isBlank() ||
                entry.message.contains(searchQuery, ignoreCase = true) ||
                entry.details.contains(searchQuery, ignoreCase = true) ||
                entry.tag.contains(searchQuery, ignoreCase = true) ||
                (entry.reasoningTrace?.contains(searchQuery, ignoreCase = true) == true)
            matchesCategory && matchesSearch
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("audit_log_tab"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Status- & Export-Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "System-Audit & KI-Protokolle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Echtzeit-Diagnose, Latenzen & Reasoning-Traces",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { viewModel.clearAuditLogs() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Protokolle löschen")
                        }
                        Button(
                            onClick = {
                                val report = viewModel.exportAuditLogsReport()
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AuditLog", report))
                                Toast.makeText(context, "Protokolle in Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportieren", fontSize = 12.sp)
                        }
                    }
                }

                // Statistiken & Benchmarks Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Einträge gesamt: ${auditLogs.size} (gefiltert: ${filteredLogs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        onClick = { showBenchmarkOverview = !showBenchmarkOverview },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showBenchmarkOverview) "Benchmarks verbergen" else "Modell-Benchmarks", fontSize = 11.sp)
                    }
                }

                if (showBenchmarkOverview) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Hardware & LLM Leistungs-Benchmarks", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (modelBenchmarks.isEmpty()) {
                                Text("Noch keine Benchmarks aufgezeichnet. Führe Testabfragen im Chat oder bei Dokumentscans durch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                modelBenchmarks.forEach { (modelName, bench) ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(modelName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text("∅ ${bench.avgLatencyMs}ms | RAM Δ: ${bench.avgRamDeltaMb}MB | Tokens/s: ${bench.tokensPerSec}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Such- & Filterleiste
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Protokolle durchsuchen (Tag, Text, OCR, Think)...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Log-Liste
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (auditLogs.isEmpty()) "Noch keine Protokolleinträge vorhanden." else "Keine Einträge für den Filter gefunden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredLogs, key = { it.id }) { entry ->
                    AuditLogItemCard(entry)
                }
            }
        }
    }
}

@Composable
fun AuditLogItemCard(entry: com.example.service.AuditLogEntry) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (entry.isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(0.5.dp, if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.category.icon)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = entry.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "[${entry.tag}]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = entry.formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            // Hardware Telemetrie Badges (wenn vorhanden)
            if (entry.ramDeltaMb != null || entry.latencyMs != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (entry.latencyMs != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "⚡ ${entry.latencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (entry.ramDeltaMb != null && entry.ramDeltaMb > 0f) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = "🧠 RAM-Δ: ${entry.ramDeltaMb}MB",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
            if (entry.details.isNotBlank()) {
                if (expanded) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = entry.details,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Text(
                        text = "Details: ${entry.details.take(90)}${if (entry.details.length > 90) "..." else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Detaillierter Reasoning-Trace (Gedankengang) wenn vorhanden & ausgeklappt
            if (expanded && !entry.reasoningTrace.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("KI-Gedankengang & Reasoning-Trace (<think>):", style = MaterialTheme.typography.labelSmall, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = entry.reasoningTrace,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }
        }
    }
}
