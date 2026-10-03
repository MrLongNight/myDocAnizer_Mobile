package com.example.ui.views

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DocRule
import com.example.model.PendingRuleSuggestion
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.launch

@Composable
fun PendingRuleSuggestionDialog(
    suggestion: PendingRuleSuggestion,
    availableDocTypes: List<com.example.model.DocTypeItem> = emptyList(),
    viewModel: DocAnizerViewModel? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var ruleName by remember { mutableStateOf(suggestion.suggestedRule.name) }
    var sender by remember { mutableStateOf(suggestion.suggestedRule.detectedSender) }
    var docType by remember { mutableStateOf(suggestion.suggestedRule.targetDocType) }
    var keywordsStr by remember { mutableStateOf(suggestion.suggestedRule.matchKeywords.joinToString(", ")) }
    var excludeKeywordsStr by remember { mutableStateOf(suggestion.suggestedRule.excludeKeywords.joinToString(", ")) }
    var mainCatId by remember { mutableStateOf(suggestion.suggestedRule.targetMainCategoryId) }
    var subCatId by remember { mutableStateOf(suggestion.suggestedRule.targetSubCategoryId) }
    var targetFolderPath by remember { mutableStateOf(suggestion.suggestedRule.targetFolderPath) }
    var selectedIcon by remember { mutableStateOf(suggestion.suggestedRule.targetIcon.ifBlank { "description" }) }
    var selectedLogo by remember { mutableStateOf(suggestion.suggestedRule.targetLogo) }
    var showIconPicker by remember { mutableStateOf(false) }
    var customFieldsMap by remember { mutableStateOf(suggestion.suggestedRule.targetCustomFields) }

    // Neuer Tab-Zustand im Dialog: 0 = Regel-Felder & Zusatzdaten, 1 = OCR-Volltext & Textkopie
    var activeDialogTab by remember { mutableIntStateOf(0) }

    // Interaktive KI-Verfeinerung
    var aiRefinePrompt by remember { mutableStateOf("") }
    var isRefiningWithAi by remember { mutableStateOf(false) }

    var showAddFieldDialog by remember { mutableStateOf(false) }
    var newFieldKey by remember { mutableStateOf("") }
    var newFieldValue by remember { mutableStateOf("") }

    // Team 4: Deterministischer Quality Gatekeeper Cross-Check
    var dismissedWarnings by remember { mutableStateOf(setOf<String>()) }
    val qualityReport = remember(suggestion.ocrText, sender, docType, customFieldsMap) {
        com.example.service.QualityGatekeeper.auditExtraction(
            ocrText = suggestion.ocrText,
            sender = sender,
            docType = docType,
            customFields = customFieldsMap
        )
    }

    var copyNotificationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copyNotificationMessage) {
        if (copyNotificationMessage != null) {
            kotlinx.coroutines.delay(2000L)
            copyNotificationMessage = null
        }
    }

    if (showIconPicker) {
        com.example.ui.components.UniversalIconAndLogoPickerDialog(
            title = "Ziel-Icon & Firmenlogo",
            subtitle = ruleName,
            currentIcon = selectedIcon,
            currentLogo = selectedLogo,
            currentCustomLogoUri = "",
            isFolder = false,
            onSave = { icon, logo, _ ->
                selectedIcon = icon
                selectedLogo = logo
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }

    if (showAddFieldDialog) {
        AlertDialog(
            onDismissRequest = { showAddFieldDialog = false },
            title = { Text("Neues Zusatzfeld hinzufügen", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newFieldKey,
                        onValueChange = { newFieldKey = it },
                        label = { Text("Feldname (z.B. Zählernummer)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newFieldValue,
                        onValueChange = { newFieldValue = it },
                        label = { Text("Wert (z.B. 849201)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFieldKey.isNotBlank()) {
                            val updated = customFieldsMap.toMutableMap()
                            updated[newFieldKey.trim()] = newFieldValue.trim()
                            customFieldsMap = updated
                            newFieldKey = ""
                            newFieldValue = ""
                            showAddFieldDialog = false
                        }
                    },
                    enabled = newFieldKey.isNotBlank()
                ) {
                    Text("Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFieldDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = { suggestion.onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header-Zeile mit KI-Logo & Live-Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "KI-Regel- & Ablage-Vorschlag",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Vorschlag anpassen, verfeinern oder anwenden",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Live-Icon & Logo Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { showIconPicker = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            com.example.ui.components.DocumentOrFolderIcon(
                                iconName = selectedIcon,
                                companyLogo = selectedLogo,
                                isFolder = false,
                                size = 36.dp
                            )
                        }
                    }
                }

                // Sub-Reiter: [Regel & Felder] vs [OCR-Volltext & Textauswahl]
                TabRow(
                    selectedTabIndex = activeDialogTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = activeDialogTab == 0,
                        onClick = { activeDialogTab = 0 },
                        text = { Text("Regel & Zusatzfelder (${customFieldsMap.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = activeDialogTab == 1,
                        onClick = { activeDialogTab = 1 },
                        text = { Text("OCR-Volltext (${suggestion.ocrText.length} Z.)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }

                // Kopier-Benachrichtigung
                AnimatedVisibility(visible = copyNotificationMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = copyNotificationMessage ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // TAB 0: Regel-Felder, KI-Verfeinerung & Zusatzfelder
                if (activeDialogTab == 0) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Interaktives KI-Anweisungs- / Verfeinerungsfeld
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Anweisung an die KI zur Regel-Verfeinerung:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = aiRefinePrompt,
                                        onValueChange = { aiRefinePrompt = it },
                                        placeholder = { Text("z.B. Kennzeichen B-MW 1234 als Feld ergänzen...", fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            if (aiRefinePrompt.isNotBlank() && viewModel != null) {
                                                coroutineScope.launch {
                                                    isRefiningWithAi = true
                                                    val kwList = keywordsStr.split(",")
                                                        .map { it.trim().lowercase() }
                                                        .filter { it.isNotEmpty() }
                                                    val currentDraft = suggestion.suggestedRule.copy(
                                                        name = ruleName,
                                                        detectedSender = sender,
                                                        targetDocType = docType,
                                                        targetMainCategoryId = mainCatId,
                                                        targetSubCategoryId = subCatId,
                                                        matchKeywords = kwList,
                                                        targetCustomFields = customFieldsMap
                                                    )
                                                    val refined = viewModel.refineRuleSuggestion(currentDraft, aiRefinePrompt, suggestion.ocrText)
                                                    ruleName = refined.name
                                                    sender = refined.detectedSender
                                                    docType = refined.targetDocType
                                                    mainCatId = refined.targetMainCategoryId
                                                    subCatId = refined.targetSubCategoryId
                                                    keywordsStr = refined.matchKeywords.joinToString(", ")
                                                    customFieldsMap = refined.targetCustomFields
                                                    selectedIcon = refined.targetIcon
                                                    selectedLogo = refined.targetLogo
                                                    aiRefinePrompt = ""
                                                    isRefiningWithAi = false
                                                }
                                            }
                                        },
                                        enabled = aiRefinePrompt.isNotBlank() && !isRefiningWithAi,
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        if (isRefiningWithAi) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("KI anpassen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Regelname & Absender
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text("Bezeichnung / Regelname") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = sender,
                                onValueChange = { sender = it },
                                label = { Text("Erkannter Absender") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = docType,
                                onValueChange = { docType = it },
                                label = { Text("Dokumenttyp") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = mainCatId,
                                onValueChange = { mainCatId = it },
                                label = { Text("E1-Kategorie") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = subCatId,
                                onValueChange = { subCatId = it },
                                label = { Text("E2-Unterkategorie") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        // Transparenter Abgleich mit bestehenden DocRules (AP-2)
                        val existingSimilarRule: DocRule? = remember(sender, docType) {
                            viewModel?.ruleRepo?.findMatchingOrSimilarRule(sender, docType)
                        }

                        if (existingSimilarRule != null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Ähnliche Regel existiert bereits: '${existingSimilarRule.name}'",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "Ablagepfad: ${existingSimilarRule.targetFolderPath.ifBlank { "Standardordner" }} • Du kannst diesen Vorschlag als Variante speichern oder anpassen.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        // Team 4: Deterministischer Quality Gatekeeper Prüfhinweis (AP-3)
                        val activeWarnings = qualityReport.warnings.filter { it !in dismissedWarnings }
                        if (activeWarnings.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("card_quality_gatekeeper_warning")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Team 4 Revision: Prüfhinweis (Score: ${(qualityReport.overallConfidence * 100).toInt()}%)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                text = activeWarnings.joinToString(" • "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                        TextButton(
                                            onClick = { dismissedWarnings = dismissedWarnings + activeWarnings.toSet() },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Bestätigen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Schnellkorrektur-Chips
                                    val correctiveChecks = qualityReport.fieldChecks.filter { it.suggestedCorrection != null && it.fieldName !in dismissedWarnings }
                                    if (correctiveChecks.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.horizontalScroll(rememberScrollState())
                                        ) {
                                            correctiveChecks.forEach { check ->
                                                AssistChip(
                                                    onClick = {
                                                        val updated = customFieldsMap.toMutableMap()
                                                        updated[check.fieldName] = check.suggestedCorrection!!
                                                        customFieldsMap = updated
                                                        dismissedWarnings = dismissedWarnings + check.warningMessage.orEmpty()
                                                    },
                                                    label = {
                                                        Text(
                                                            text = "${check.fieldName} korrigieren: '${check.suggestedCorrection}'",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Vorgeschlagener flexibler Ordnerpfad (Ebene 1 / Ebene 2)
                        OutlinedTextField(
                            value = targetFolderPath,
                            onValueChange = { targetFolderPath = it },
                            label = { Text("Vorgeschlagener Ordnerpfad (Ebene 1 / 2)") },
                            leadingIcon = {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            placeholder = { Text("z.B. Vodafone/Rechnungen oder Finanzen/Mobilfunk") },
                            singleLine = true,
                            supportingText = { Text("Gemäß aktiver Ordner-Strategie von der Fachabteilung vorgeschlagen") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = keywordsStr,
                            onValueChange = { keywordsStr = it },
                            label = { Text("Schlagwörter (Komma-getrennt)") },
                            supportingText = { Text("Triggert künftig diesen Dokumenttyp automatisch") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Strukturierte Zusatzfelder-Verwaltung mit Hinzufügen/Löschen
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                            imageVector = Icons.Default.Label,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Zusatzfelder (${customFieldsMap.size}):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = { showAddFieldDialog = true },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("btn_add_custom_field_inline")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Feld+", fontSize = 11.sp)
                                    }
                                }

                                if (customFieldsMap.isEmpty()) {
                                    Text(
                                        text = "Keine Zusatzfelder zugewiesen. Tippe auf 'Feld+', um Schlüssel-Wert Paare wie Rechnungsbetrag, Zählernummer oder Kennzeichen anzulegen.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                } else {
                                     customFieldsMap.forEach { (key, value) ->
                                        val fieldCheck = qualityReport.getCheckForField(key)
                                        val hasFieldIssue = fieldCheck != null && (!fieldCheck.isFoundInText || !fieldCheck.isPlausible) && key !in dismissedWarnings

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (hasFieldIssue) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = key,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (hasFieldIssue) {
                                                        Text(
                                                            text = "⚠️ Bitte prüfen",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = value,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (hasFieldIssue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                )
                                                if (hasFieldIssue && fieldCheck?.suggestedCorrection != null) {
                                                    Text(
                                                        text = "Vorschlag: ${fieldCheck.suggestedCorrection}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.clickable {
                                                            val updated = customFieldsMap.toMutableMap()
                                                            updated[key] = fieldCheck.suggestedCorrection
                                                            customFieldsMap = updated
                                                            dismissedWarnings = dismissedWarnings + key
                                                        }
                                                    )
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (hasFieldIssue) {
                                                    IconButton(
                                                        onClick = { dismissedWarnings = dismissedWarnings + key },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.Done, contentDescription = "Wert bestätigen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(value))
                                                        copyNotificationMessage = "Wert '$value' kopiert!"
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren", modifier = Modifier.size(14.dp))
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val updated = customFieldsMap.toMutableMap()
                                                        updated.remove(key)
                                                        customFieldsMap = updated
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 1: OCR-Volltext Inspektor mit Textkopie
                if (activeDialogTab == 1) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Wähle Text aus oder kopiere den gesamten Inhalt:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(suggestion.ocrText))
                                    copyNotificationMessage = "Gesamter OCR-Text in Zwischenablage kopiert!"
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Alles kopieren", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            SelectionContainer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = suggestion.ocrText.ifBlank { "Kein OCR-Text erkannt." },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Aktions-Buttons: Einmalig anwenden vs. Dauerhafte Regel speichern
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val kwList = keywordsStr.split(",")
                                .map { it.trim().lowercase() }
                                .filter { it.isNotEmpty() }
                            val updatedRule = suggestion.suggestedRule.copy(
                                name = ruleName.ifBlank { "Scan $sender" },
                                detectedSender = sender.ifBlank { "Unbekannt" },
                                targetDocType = docType,
                                targetMainCategoryId = mainCatId,
                                targetSubCategoryId = subCatId,
                                matchKeywords = if (kwList.isNotEmpty()) kwList else listOf(sender.lowercase()),
                                targetIcon = selectedIcon,
                                targetLogo = selectedLogo,
                                targetFolderPath = targetFolderPath,
                                targetCustomFields = customFieldsMap
                            )
                            suggestion.onSingleUseOnly(updatedRule)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_single_use_rule")
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("1x Anwenden", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val kwList = keywordsStr.split(",")
                                .map { it.trim().lowercase() }
                                .filter { it.isNotEmpty() }
                            val updatedRule = suggestion.suggestedRule.copy(
                                name = ruleName.ifBlank { "Scan $sender" },
                                detectedSender = sender.ifBlank { "Unbekannt" },
                                targetDocType = docType,
                                targetMainCategoryId = mainCatId,
                                targetSubCategoryId = subCatId,
                                matchKeywords = if (kwList.isNotEmpty()) kwList else listOf(sender.lowercase()),
                                targetIcon = selectedIcon,
                                targetLogo = selectedLogo,
                                targetFolderPath = targetFolderPath,
                                targetCustomFields = customFieldsMap
                            )
                            suggestion.onSaveAsPermanentRule(updatedRule)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("btn_save_permanent_rule"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Regel speichern", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

