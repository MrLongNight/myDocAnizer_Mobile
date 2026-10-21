package com.example.ui.views.rules

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.CustomFieldEntity
import com.example.model.DocRule
import com.example.service.DocRuleRepository
import com.example.service.llm.CustomFieldHarmonizer
import com.example.service.llm.HarmonizationStatus
import com.example.service.llm.RuleFieldAuditReport
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentRulesSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val docRules by viewModel.docRules.collectAsStateWithLifecycle()
    val isGeneratingAi by viewModel.isGeneratingAi.collectAsStateWithLifecycle()

    var showAiRuleDialog by remember { mutableStateOf(false) }
    var showCreateRuleDialog by remember { mutableStateOf(false) }
    var showTestingDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<DocRule?>(null) }
    var conflictingRule by remember { mutableStateOf<DocRule?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ablage- & Erkennungsregeln (${docRules.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showTestingDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp).testTag("btn_test_rules")
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Testen", style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(
                    onClick = { showAiRuleDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp).testTag("btn_open_ai_rule_generator")
                ) {
                    com.example.ui.components.LocalAiAgentIcon(
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("KI", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { showCreateRuleDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(34.dp).testTag("btn_add_rule_manual")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Neu", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            RulesListContent(
                rules = docRules,
                onToggleRule = { id, enabled -> viewModel.toggleDocRule(id, enabled) },
                onEditRule = { rule -> editingRule = rule },
                onDeleteRule = { id -> viewModel.deleteDocRule(id) },
                onOpenAiAssistant = { showAiRuleDialog = true },
                onAddNewRule = { showCreateRuleDialog = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showTestingDialog) {
        RuleTestingDialog(
            viewModel = viewModel,
            rules = docRules,
            onDismiss = { showTestingDialog = false }
        )
    }

    if (showAiRuleDialog) {
        AiRuleGeneratorDialog(
            viewModel = viewModel,
            isGenerating = isGeneratingAi,
            onDismiss = { showAiRuleDialog = false },
            onAddApprovedRules = { approvedRules ->
                viewModel.addDocRules(approvedRules)
                showAiRuleDialog = false
            }
        )
    }

    if (showCreateRuleDialog) {
        EditRuleDialog(
            rule = DocRule(
                name = "",
                matchKeywords = emptyList(),
                excludeKeywords = emptyList(),
                targetMainCategoryId = "A01",
                targetSubCategoryId = "B1.01",
                targetDocType = "Rechnung",
                detectedSender = "",
                targetTags = emptyList()
            ),
            isNew = true,
            viewModel = viewModel,
            onDismiss = { showCreateRuleDialog = false },
            onSave = { newRule ->
                val conflict = DocRuleRepository.findPotentialRuleConflict(docRules, newRule)
                if (conflict != null) {
                    conflictingRule = conflict
                } else {
                    viewModel.addDocRule(newRule)
                    showCreateRuleDialog = false
                }
            }
        )
    }

    conflictingRule?.let { conflict ->
        AlertDialog(
            onDismissRequest = { conflictingRule = null },
            title = { Text("Mögliche Regelüberschneidung") },
            text = {
                Text(
                    "Die neue oder geänderte Regel könnte gleichzeitig mit „${conflict.name}“ passen. " +
                        "Ich habe sie nicht gespeichert, damit nicht unbemerkt konkurrierende Regeln entstehen. " +
                        "Passe die Auslöser an oder prüfe die vorhandene Regel."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        conflictingRule = null
                        showCreateRuleDialog = false
                        editingRule = conflict
                    }
                ) { Text("Vorhandene Regel prüfen") }
            },
            dismissButton = {
                TextButton(onClick = { conflictingRule = null }) { Text("Auslöser anpassen") }
            }
        )
    }

    editingRule?.let { rule ->
        EditRuleDialog(
            rule = rule,
            isNew = false,
            viewModel = viewModel,
            onDismiss = { editingRule = null },
            onSave = { updated ->
                val conflict = DocRuleRepository.findPotentialRuleConflict(docRules, updated)
                if (conflict != null) {
                    conflictingRule = conflict
                } else {
                    viewModel.updateDocRule(updated)
                    editingRule = null
                }
            }
        )
    }
}

@Composable
fun RulesListContent(
    rules: List<DocRule>,
    onToggleRule: (String, Boolean) -> Unit,
    onEditRule: (DocRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onOpenAiAssistant: () -> Unit,
    onAddNewRule: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (rules.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.FolderSpecial,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Noch keine Regeln definiert",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Lass dir vom lokalen KI Agenten passende Regeln vorschlagen oder erstelle manuell eigene Schlagwort-Filter für deine Dokumente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onAddNewRule) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manuell anlegen")
                    }
                    Button(
                        onClick = onOpenAiAssistant,
                        modifier = Modifier.testTag("btn_empty_create_rules_ai")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mit KI erstellen")
                    }
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Diese Regeln greifen im Scan-Betrieb sofort (unter 5ms). Nur wenn kein Schlagwort passt, analysiert das lokale On-Device LLM den Text.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            items(rules, key = { it.id }) { rule ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().testTag("rule_card_${rule.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (rule.isAiGenerated) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "KI-erstellt",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Ziel: ${rule.targetMainCategoryId}_${rule.targetSubCategoryId} • Typ: ${rule.targetDocType.ifBlank { "Rechnung" }} • ${rule.detectedSender.ifBlank { "Auto" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { onToggleRule(rule.id, it) },
                                modifier = Modifier.testTag("switch_rule_${rule.id}")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rule.matchKeywords.take(4).forEach { kw ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = "+ $kw",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            rule.excludeKeywords.firstOrNull()?.let { excl ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = "Ausschluss: -$excl",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onEditRule(rule) }) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prüfen / Anpassen")
                            }
                            IconButton(onClick = { onDeleteRule(rule.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiRuleGeneratorDialog(
    viewModel: DocAnizerViewModel,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onAddApprovedRules: (List<DocRule>) -> Unit
) {
    var userPrompt by remember { mutableStateOf("") }
    var generatedDrafts by remember { mutableStateOf<List<DocRule>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val existingFields by viewModel.customFields.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Regel-Vorschlag von KI generieren", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Beschreibe, welche Unterlagen du hast (z.B. 'Ich habe Versicherungspolicen von Allianz und Stromrechnungen von Stadtwerke München'). Die lokale KI erstellt daraus feste Regeln inklusive passender Zusatzfelder für deine Dashboard-Widgets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    placeholder = { Text("z.B. Vodafone Handyvertrag und Allianz Kfz Versicherung...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                if (isGenerating) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Lokales Modell generiert Regeln...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (generatedDrafts.isNotEmpty()) {
                    Text(
                        text = "Vorgeschlagene Regeln (Bitte prüfen & freigeben):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )

                    generatedDrafts.forEachIndexed { index, draft ->
                        val auditReport = remember(draft, existingFields) {
                            CustomFieldHarmonizer.auditRuleFields(
                                ruleName = draft.name,
                                docType = draft.targetDocType,
                                sender = draft.detectedSender,
                                currentCustomFields = draft.targetCustomFields,
                                existingFields = existingFields
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(draft.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = draft.targetDocType,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text("Schlagwörter: ${draft.matchKeywords.joinToString(", ")}", fontSize = 11.sp)
                                Text("Ziel: ${draft.targetMainCategoryId}_${draft.targetSubCategoryId} (${draft.detectedSender})", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)

                                // Smarte Zusatzfeld-Zuordnung
                                if (draft.targetCustomFields.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                            .padding(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Zusatzfelder (${auditReport.assignedFields.size}):",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            auditReport.assignedFields.forEach { fieldItem ->
                                                val isExisting = fieldItem.status == HarmonizationStatus.MATCHED_EXISTING
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = if (isExisting) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isExisting) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(10.dp),
                                                            tint = if (isExisting) Color(0xFF047857) else MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                        Text(
                                                            text = "${fieldItem.fieldName}${if (isExisting) " (Tresor)" else " (Neu)"}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isExisting) Color(0xFF047857) else MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (auditReport.supportedDashboardWidgets.isNotEmpty()) {
                                            Text(
                                                text = "Aktiviert: ${auditReport.supportedDashboardWidgets.first()}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
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
            if (generatedDrafts.isEmpty()) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val results = viewModel.generateRulesWithAi(userPrompt)
                            generatedDrafts = results
                        }
                    },
                    enabled = userPrompt.isNotBlank() && !isGenerating
                ) {
                    Text("Regeln vorschlagen")
                }
            } else {
                Button(onClick = { onAddApprovedRules(generatedDrafts) }) {
                    Text("Freigeben & Speichern (${generatedDrafts.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun EditRuleDialog(
    rule: DocRule,
    isNew: Boolean = false,
    viewModel: DocAnizerViewModel? = null,
    onDismiss: () -> Unit,
    onSave: (DocRule) -> Unit
) {
    var name by remember { mutableStateOf(rule.name) }
    var keywordsStr by remember { mutableStateOf(rule.matchKeywords.joinToString(", ")) }
    var excludeStr by remember { mutableStateOf(rule.excludeKeywords.joinToString(", ")) }
    var sender by remember { mutableStateOf(rule.detectedSender) }
    var docType by remember { mutableStateOf(rule.targetDocType.ifBlank { "Rechnung" }) }
    var mainCat by remember { mutableStateOf(rule.targetMainCategoryId) }
    var subCat by remember { mutableStateOf(rule.targetSubCategoryId) }
    var tagsStr by remember { mutableStateOf(rule.targetTags.joinToString(", ")) }

    var customFieldsMap by remember { mutableStateOf(rule.targetCustomFields) }
    var showAddCustomField by remember { mutableStateOf(false) }
    var newFieldKey by remember { mutableStateOf("") }
    var newFieldValue by remember { mutableStateOf("") }

    val existingFields = viewModel?.customFields?.collectAsStateWithLifecycle()?.value ?: emptyList()

    val auditReport = remember(name, docType, sender, customFieldsMap, existingFields) {
        CustomFieldHarmonizer.auditRuleFields(
            ruleName = name,
            docType = docType,
            sender = sender,
            currentCustomFields = customFieldsMap,
            existingFields = existingFields
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) "Neue Schlagwort-Regel anlegen" else "Schlagwort-Regel anpassen",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Regel-Name (z.B. Vodafone DSL)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywordsStr,
                    onValueChange = { keywordsStr = it },
                    label = { Text("Pflicht-Schlagwörter (Kommagetrennt)") },
                    placeholder = { Text("Vodafone, Internet, Kundenkonto") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = excludeStr,
                    onValueChange = { excludeStr = it },
                    label = { Text("Ausschluss-Wörter (optional)") },
                    placeholder = { Text("Mahnung, Kündigung") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sender,
                    onValueChange = { sender = it },
                    label = { Text("Absender / Firma") },
                    placeholder = { Text("Vodafone GmbH") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = docType,
                    onValueChange = { docType = it },
                    label = { Text("Dokument-Typ") },
                    placeholder = { Text("Rechnung / Vertrag") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mainCat,
                        onValueChange = { mainCat = it },
                        label = { Text("Ebene 1 (z.B. A01)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = subCat,
                        onValueChange = { subCat = it },
                        label = { Text("Ebene 2 (z.B. B1.01)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = tagsStr,
                    onValueChange = { tagsStr = it },
                    label = { Text("Tags / Schlagworte (Kommagetrennt)") },
                    placeholder = { Text("Telekom, Monatsabrechnung") },
                    modifier = Modifier.fillMaxWidth()
                )

                // -------------------------------------------------------------
                // ZENTRALER BEREICH: SMARTE ZUSATZFELDER & DASHBOARD-ZUORDNUNG
                // -------------------------------------------------------------
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Zusatzfelder & Dashboard (${customFieldsMap.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            FilledTonalButton(
                                onClick = { showAddCustomField = !showAddCustomField },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp).testTag("btn_rule_add_custom_field")
                            ) {
                                Icon(if (showAddCustomField) Icons.Default.Close else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (showAddCustomField) "Schließen" else "Feld+", fontSize = 11.sp)
                            }
                        }

                        // Dashboard-Nutzen Banner
                        if (auditReport.supportedDashboardWidgets.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "💡 Smarte App-Funktionen aktiv:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    auditReport.supportedDashboardWidgets.forEach { widgetBenefit ->
                                        Text(
                                            text = "• $widgetBenefit",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Formular zum manuellen Hinzufügen eines Felds
                        if (showAddCustomField) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = newFieldKey,
                                        onValueChange = { newFieldKey = it },
                                        label = { Text("Feldname (z.B. Zählernummer, Betrag)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = newFieldValue,
                                        onValueChange = { newFieldValue = it },
                                        label = { Text("Standardwert oder Muster (optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        Button(
                                            onClick = {
                                                if (newFieldKey.isNotBlank()) {
                                                    val updated = customFieldsMap.toMutableMap()
                                                    updated[newFieldKey.trim()] = newFieldValue.trim().ifBlank { "Standard" }
                                                    customFieldsMap = updated
                                                    newFieldKey = ""
                                                    newFieldValue = ""
                                                    showAddCustomField = false
                                                }
                                            },
                                            enabled = newFieldKey.isNotBlank(),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Zuordnen", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Liste der aktuell zugewiesenen Felder
                        if (auditReport.assignedFields.isEmpty()) {
                            Text(
                                text = "Noch keine Zusatzfelder zugewiesen. Wähle unten empfohlene Felder für smarte Widgets aus:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                auditReport.assignedFields.forEach { assignedItem ->
                                    val isExisting = assignedItem.status == HarmonizationStatus.MATCHED_EXISTING
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isExisting) Color(0xFF10B981).copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = assignedItem.fieldName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = if (isExisting) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                    ) {
                                                        Text(
                                                            text = if (isExisting) "Im Tresor vorhanden" else "Wird neu angelegt",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isExisting) Color(0xFF047857) else MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                                if (assignedItem.dashboardBenefit.isNotBlank()) {
                                                    Text(
                                                        text = assignedItem.dashboardBenefit,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    val updated = customFieldsMap.toMutableMap()
                                                    updated.remove(assignedItem.fieldName)
                                                    customFieldsMap = updated
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Entfernen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Empfehlungen für smarte Dashboard-Widgets (Ein-Klick-Hinzufügen)
                        if (auditReport.missingRecommendedFields.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Empfehlungen für smarte Dashboard-Funktionen:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                auditReport.missingRecommendedFields.forEach { recItem ->
                                    val isExisting = recItem.status == HarmonizationStatus.MATCHED_EXISTING
                                    AssistChip(
                                        onClick = {
                                            val updated = customFieldsMap.toMutableMap()
                                            updated[recItem.fieldName] = recItem.valueHint.ifBlank { "Standard" }
                                            customFieldsMap = updated
                                        },
                                        label = {
                                            Text(
                                                text = "+ ${recItem.fieldName}${if (isExisting) " (Tresor)" else " (Neu)"}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isExisting) Icons.Default.CheckCircle else Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = if (isExisting) Color(0xFF047857) else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Schnellauswahl aus weiteren existierenden Feldern im System
                        val remainingExisting = existingFields.filter { ext ->
                            customFieldsMap.keys.none { it.equals(ext.name, ignoreCase = true) } &&
                            auditReport.missingRecommendedFields.none { it.fieldName.equals(ext.name, ignoreCase = true) }
                        }
                        if (remainingExisting.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Weitere vorhandene Felder im Tresor:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                remainingExisting.take(6).forEach { fieldEntity ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val updated = customFieldsMap.toMutableMap()
                                            updated[fieldEntity.name] = "Standard"
                                            customFieldsMap = updated
                                        },
                                        label = { Text(fieldEntity.name, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val kws = keywordsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val excludes = excludeStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    val tags = tagsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onSave(
                        rule.copy(
                            name = name.ifBlank { "Neue Regel" },
                            matchKeywords = kws,
                            excludeKeywords = excludes,
                            detectedSender = sender,
                            targetDocType = docType.ifBlank { "Rechnung" },
                            targetMainCategoryId = mainCat.ifBlank { "A01" },
                            targetSubCategoryId = subCat.ifBlank { "B1.01" },
                            targetTags = tags,
                            targetCustomFields = customFieldsMap
                        )
                    )
                },
                enabled = keywordsStr.isNotBlank() || name.isNotBlank()
            ) {
                Text(if (isNew) "Regel erstellen" else "Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
