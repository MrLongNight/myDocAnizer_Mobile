package com.example.ui.views.settings

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AppAuditLogger
import com.example.service.LogLevel
import com.example.service.TestStatus
import com.example.service.SimulationResult
import com.example.util.UserProfileSimulation
import com.example.util.UserPersonaFactory
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.launch

@Composable
fun DeveloperSettingsTab(viewModel: DocAnizerViewModel) {
    var showConfirmSeederDialog by remember { mutableStateOf(false) }
    var isSeeding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val allDocs by viewModel.allDocuments.collectAsState()
    val customFields by viewModel.customFields.collectAsState()
    val hardwareInfo by viewModel.deviceHardwareInfo.collectAsState()
    val diagnosticSteps by viewModel.diagnosticSteps.collectAsState()
    val isStressTesting by viewModel.isStressTesting.collectAsState()
    val lastSimulationResult by viewModel.lastSimulationResult.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Automatische Routine 1 beim Öffnen des Tabs
    LaunchedEffect(Unit) {
        viewModel.runIntegrityCheck()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. BASIS-INTEGRITÄT (AUTO-RUN)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Routine 1: Basis-Integrität", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { viewModel.runIntegrityCheck() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Neu prüfen")
                    }
                }

                diagnosticSteps.forEach { step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when (step.status) {
                                TestStatus.SUCCESS -> Icons.Default.CheckCircle
                                TestStatus.ERROR -> Icons.Default.Error
                                TestStatus.WARNING -> Icons.Default.Warning
                                else -> Icons.Default.Circle
                            },
                            contentDescription = null,
                            tint = when (step.status) {
                                TestStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                TestStatus.ERROR -> MaterialTheme.colorScheme.error
                                TestStatus.WARNING -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(step.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            if (step.message.isNotBlank()) {
                                Text(step.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // 2. KI & REGELERKENNUNG (ROUTINE 2)
        val lastAnalysis by viewModel.lastAnalysisReport.collectAsState()
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Routine 2: KI & Entscheidungsmatrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    "Zeigt das Ergebnis der letzten Dokumenten-Analyse (OCR & Regeln). Hilfreich um zu verstehen, warum Dokumente falsch eingeordnet wurden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (lastAnalysis != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = lastAnalysis!!,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else {
                    Text("Noch keine Analyse-Daten vorhanden. Bitte scanne ein Dokument.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                
                OutlinedButton(
                    onClick = { /* Navigiere zum Scanner? Oder zeige Hinweis */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scanner öffnen")
                }
            }
        }

        // 3. DIAGNOSE-REPORT (COPY FOR SUPPORT)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Diagnose-Report für Support", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Kopiert alle System-Infos & Logs für die Fehleranalyse.", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { 
                        viewModel.copyDiagnosticReport(context)
                        android.widget.Toast.makeText(context, "Report in Zwischenablage kopiert!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kopieren")
                }
            }
        }

        // 3. FINANZ-WORKFLOW (ROUTINE 3)
        var financeCheckResult by remember { mutableStateOf<String?>(null) }
        var isCheckingFinance by remember { mutableStateOf(false) }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Routine 3: Finanz-Workflow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    "Prüft die Integrität des Finanz-Moduls und des Haushaltsbuchs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (financeCheckResult != null) {
                    Text(text = financeCheckResult!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isCheckingFinance = true
                            financeCheckResult = viewModel.runFinanceWorkflowCheck()
                            isCheckingFinance = false
                        }
                    },
                    enabled = !isCheckingFinance,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCheckingFinance) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Text("Finanz-Integrität prüfen")
                    }
                }
            }
        }

        // 4. STRESS-SIMULATION (ROUTINE 4 - REALISTISCHE BENUTZERPROFILE)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().testTag("card_routine_4_personas")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("Routine 4: Realistische Benutzer- & Stress-Simulation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    "Simuliert fiktive Nutzerprofile mit lebensnahem Dokumenten-Portfolio (Gehalt, Mietverträge, Rechnungen, Fristen, Steuerbescheide), um die Regelerkennung, Fristenverwaltung und Datenbank-Stabilität unter realistischen Einsatzbedingungen zu testen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val personas = UserPersonaFactory.ALL_PERSONAS
                var selectedProfile by remember { mutableStateOf(personas[0]) }
                var showDocumentPreview by remember { mutableStateOf(false) }

                Text("Fiktives Benutzerprofil wählen:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    personas.forEach { persona ->
                        val isSelected = selectedProfile.id == persona.id
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProfile = persona }
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val icon = when (persona.iconType) {
                                        "family" -> Icons.Default.Person
                                        "freelancer" -> Icons.Default.Work
                                        "senior" -> Icons.Default.Face
                                        "student" -> Icons.Default.School
                                        "landlord" -> Icons.Default.Home
                                        else -> Icons.Default.AccountBox
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(persona.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(persona.roleTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            "${persona.documentSpecs.size} Docs",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedProfile = persona }
                                    )
                                }

                                Text(
                                    persona.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )

                                // Highlight Tags Chips
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    persona.highlightTags.take(5).forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                "#$tag",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dokumenten-Vorschau Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDocumentPreview = !showDocumentPreview }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Dokumenten-Vorschau für '${selectedProfile.name}' (${selectedProfile.documentSpecs.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        if (showDocumentPreview) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = showDocumentPreview) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedProfile.documentSpecs.forEachIndexed { index, doc ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("${index + 1}.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text("${doc.sender} • ${doc.mainCategory}/${doc.subCategory}", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (doc.amount != null) {
                                    Text(
                                        String.format(java.util.Locale.GERMANY, "%.2f €", doc.amount),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (doc.cancellationDeadline != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                    ) {
                                        Text("Frist", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            if (index < selectedProfile.documentSpecs.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.startStressSimulation(selectedProfile)
                            android.widget.Toast.makeText(context, "Simulation für '${selectedProfile.name}' abgeschlossen!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isStressTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isStressTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onError)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verarbeite ${selectedProfile.name} Dokumente...")
                    } else {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulation '${selectedProfile.name}' starten (${selectedProfile.documentSpecs.size} Docs)")
                    }
                }

                // AUSWERTUNG / SIMULATION RESULT CARD
                if (lastSimulationResult != null) {
                    val result = lastSimulationResult!!
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Ergebnis: ${result.profileName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }

                            // 4 Metriken-Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Dokumente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${result.documentCount}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Finanzvolumen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(String.format(java.util.Locale.GERMANY, "%.2f €", result.totalAmount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Fristen erkannt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${result.deadlinesDetected}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Dauer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${result.durationMs} ms", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.copySimulationResult(context, result)
                                    android.widget.Toast.makeText(context, "Simulations-Report in Zwischenablage kopiert!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auswertung für Support / KI kopieren")
                            }
                        }
                    }
                }
            }
        }

        // ENTWICKLER-TESTDATEN GENERATOR
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_debug_data_seeder"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Entwickler-Testdaten (Debug Data Seeder)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Text(
                    text = "Generiert 12 realistische Testdokumente (Vodafone, Allianz, SWM etc.) für Emulator- und Funktionsprüfungen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Button(
                    onClick = { showConfirmSeederDialog = true },
                    enabled = !isSeeding,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth().testTag("btn_seed_debug_data")
                ) {
                    if (isSeeding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onTertiary)
                    } else {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testdaten generieren", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // DIAGNOSE-LOGGING MODUS
        val currentLogLevel by viewModel.logLevel.collectAsState()
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.HistoryEdu, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text("Diagnose-Logging (Ereignis-Protokoll)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogLevelOption(
                        level = LogLevel.NONE,
                        title = "No Log Mode",
                        description = "Logging deaktiviert.",
                        isSelected = currentLogLevel == LogLevel.NONE,
                        onClick = { viewModel.setLogLevel(LogLevel.NONE) }
                    )
                    LogLevelOption(
                        level = LogLevel.STANDARD,
                        title = "Standard Log Mode",
                        description = "Alles außer OCR-Inhalte.",
                        isSelected = currentLogLevel == LogLevel.STANDARD,
                        onClick = { viewModel.setLogLevel(LogLevel.STANDARD) }
                    )
                    LogLevelOption(
                        level = LogLevel.DEVELOP,
                        title = "Develop Log Mode",
                        description = "Vollständiges Logging inkl. OCR.",
                        isSelected = currentLogLevel == LogLevel.DEVELOP,
                        onClick = { viewModel.setLogLevel(LogLevel.DEVELOP) }
                    )
                }
                
                OutlinedButton(
                    onClick = { AppAuditLogger.clearLogs(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logs löschen")
                }
            }
        }
    }

    if (showConfirmSeederDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmSeederDialog = false },
            title = { Text("Testdaten generieren?") },
            text = { Text("Datenbank wird geleert und 12 Test-PDFs erstellt.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSeederDialog = false
                        isSeeding = true
                        viewModel.seedDebugData { 
                            isSeeding = false 
                            android.widget.Toast.makeText(context, "Fertig!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) { Text("Ja") }
            },
            dismissButton = { TextButton(onClick = { showConfirmSeederDialog = false }) { Text("Nein") } }
        )
    }
}

@Composable
fun LogLevelOption(
    level: LogLevel,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = isSelected, onClick = onClick)
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
