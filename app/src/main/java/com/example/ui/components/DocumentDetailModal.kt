package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.model.DocTypeItem
import com.example.model.DocumentEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailModal(
    document: DocumentEntity,
    availableDocTypes: List<DocTypeItem>,
    onUpdateDocType: (Long, String) -> Unit,
    onDelete: (DocumentEntity) -> Unit,
    onDismissRequest: () -> Unit,
    onUpdateContractReminder: ((Long, Long?, Long?, Int, Boolean, String, String, Double?) -> Unit)? = null,
    allCustomFields: List<com.example.model.CustomFieldEntity> = emptyList(),
    documentCustomFieldValues: List<com.example.model.DocumentCustomFieldValueEntity> = emptyList(),
    onUpdateCustomFieldValue: ((Long, String, String) -> Unit)? = null,
    onUpdateDocumentIconAndLogo: ((Long, String, String) -> Unit)? = null,
    onRunSecondRun: ((Long, String?) -> Unit)? = null,
    onReRunFirstRun: ((Long) -> Unit)? = null,
    isAiAnalyzing: Boolean = false,
    lastAiResult: com.example.service.LlmClassificationResult? = null
) {
    val context = LocalContext.current
    var currentDocType by remember { mutableStateOf(document.docType) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showIconPickerDialog by remember { mutableStateOf(false) }
    var userCorrectionInput by remember { mutableStateOf("") }
    var showCorrectionField by remember { mutableStateOf(false) }

    // Team 4: Deterministischer Quality Gatekeeper Cross-Check (AP-3)
    val customFieldsMap = remember(allCustomFields, documentCustomFieldValues) {
        val map = mutableMapOf<String, String>()
        allCustomFields.forEach { field ->
            val v = documentCustomFieldValues.find { it.documentId == document.id && it.customFieldId == field.id }?.fieldValue
            if (!v.isNullOrBlank()) {
                map[field.name] = v
            }
        }
        map
    }
    var dismissedModalWarnings by remember { mutableStateOf(setOf<String>()) }
    val qualityReport = remember(document, customFieldsMap) {
        com.example.service.QualityGatekeeper.auditDocument(document, customFieldsMap)
    }

    val isImage = document.fileName.endsWith(".jpg", ignoreCase = true) ||
            document.fileName.endsWith(".png", ignoreCase = true)

    if (showIconPickerDialog) {
        UniversalIconAndLogoPickerDialog(
            title = "Dokument-Icon & Logo",
            subtitle = document.title,
            currentIcon = document.customIcon,
            currentLogo = document.companyLogo,
            currentCustomLogoUri = "",
            isFolder = false,
            onSave = { iconName, companyLogo, _ ->
                onUpdateDocumentIconAndLogo?.invoke(document.id, iconName, companyLogo)
                showIconPickerDialog = false
            },
            onDismiss = { showIconPickerDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { showIconPickerDialog = true }
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        DocumentOrFolderIcon(
                            iconName = document.customIcon,
                            companyLogo = document.companyLogo,
                            isFolder = false,
                            isImage = isImage,
                            size = 52.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = document.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { showIconPickerDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Icon / Logo anpassen", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metadaten-Karten
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DetailRow("Format", if (isImage) "Original Bilddatei (JPG)" else "Archiv-PDF (A4)")
                    val prefixE1 = if (document.mainCategoryId.isNotBlank()) "${document.mainCategoryId}_" else ""
                    val prefixE2 = if (document.subCategoryId.isNotBlank()) "${document.subCategoryId}-" else ""
                    DetailRow("Ablageort", "$prefixE1${document.mainCategory} ➔ $prefixE2${document.subCategory}")
                    DetailRow("Seitenanzahl", if (isImage) "1 Bild" else "${document.pageCount} Seite(n)")
                    DetailRow("Farbmodus", if (document.colorMode == "COLOR") "Farbe" else "Schwarz/Weiß (S/W)")
                    DetailRow("Dateigröße", document.fileSizeFormatted)
                    DetailRow("Erstellt am", formatDate(document.createdAt))
                }
            }

            // OCR-Text anzeigen falls vorhanden
            if (document.ocrText.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Indexierter OCR-Volltext (${document.ocrText.length} Zeichen)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = document.ocrText.take(280) + if (document.ocrText.length > 280) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 🛡️ Team 4: Deterministischer Quality Gatekeeper Prüfhinweis (AP-3)
            val activeModalWarnings = qualityReport.warnings.filter { it !in dismissedModalWarnings }
            if (activeModalWarnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_modal_quality_warning")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
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
                                    text = activeModalWarnings.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(
                                onClick = { dismissedModalWarnings = dismissedModalWarnings + activeModalWarnings.toSet() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Bestätigen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Schnellkorrektur-Chips
                        val modalCorrectiveChecks = qualityReport.fieldChecks.filter { it.suggestedCorrection != null && it.fieldName !in dismissedModalWarnings }
                        if (modalCorrectiveChecks.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                modalCorrectiveChecks.forEach { check ->
                                    val matchingField = allCustomFields.find { it.name.equals(check.fieldName, ignoreCase = true) }
                                    if (matchingField != null) {
                                        AssistChip(
                                            onClick = {
                                                onUpdateCustomFieldValue?.invoke(document.id, matchingField.id, check.suggestedCorrection!!)
                                                dismissedModalWarnings = dismissedModalWarnings + check.fieldName
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
            }

            // 🤖 Lokale KI-Dokumentenanalyse (Two-Stage Pipeline: Triage & Tiefenscan)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ondevice_ai_agent),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Lokale KI-Analyse",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Badge First Run vs. Second Run
                        val isSecondRunDone = lastAiResult?.explanation?.contains("Team 2 Tiefenanalyse") == true
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSecondRunDone) Color(0xFF10B981).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isSecondRunDone) "⚡ Tiefenanalyse aktiv" else "📬 Triage (First Run)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSecondRunDone) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = lastAiResult?.explanation ?: "Dokument wurde mit schneller Triage erfasst. Starte bei Bedarf eine tiefere Volltext-Analyse für Verträge, Fristen und Zusatzfelder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Ausklappbares Feld für gezielte Benutzer-Korrekturanweisung
                    if (showCorrectionField) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = userCorrectionInput,
                            onValueChange = { userCorrectionInput = it },
                            label = { Text("Gezielte Korrektur an die KI") },
                            placeholder = { Text("z.B. Absender ist Congstar, Vertragsnummer ergänzen") },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onRunSecondRun?.invoke(document.id, userCorrectionInput.takeIf { it.isNotBlank() })
                            },
                            enabled = !isAiAnalyzing,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isAiAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Analysiere...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tiefenanalyse (Run 2)", fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                showCorrectionField = !showCorrectionField
                            }
                        ) {
                            Icon(
                                imageVector = if (showCorrectionField) Icons.Default.Close else Icons.Default.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                onReRunFirstRun?.invoke(document.id)
                            },
                            enabled = !isAiAnalyzing
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Triage wiederholen",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Optionaler Doc-Typ
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Doc-Typ (Optional)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (currentDocType.isNotBlank()) currentDocType else "Kein Doc-Typ zugewiesen",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    TextButton(onClick = { showTypePicker = true }) {
                        Text("Ändern")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // VERTRÄGE, LAUFZEITEN & KALENDER-ERINNERUNGEN
            var showContractDialog by remember { mutableStateOf(false) }
            val sdfDate = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
            val hasDeadlineInfo = document.cancellationDeadline != null || document.contractEndDate != null

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (hasDeadlineInfo) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
                                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Vertrag, Laufzeit & Fristen",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(onClick = { showContractDialog = true }) {
                            Text(if (hasDeadlineInfo) "Bearbeiten" else "+ Frist setzen")
                        }
                    }

                    if (hasDeadlineInfo) {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (document.contractEndDate != null) {
                            DetailRow("Vertragsende / Laufzeit", sdfDate.format(Date(document.contractEndDate)))
                        }
                        if (document.cancellationDeadline != null) {
                            val days = ((document.cancellationDeadline - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).toInt()
                            DetailRow("Kündigungsfrist", "${sdfDate.format(Date(document.cancellationDeadline))} (${if (days > 0) "in $days Tagen" else "überfällig"})")
                        }
                        if (document.amount != null && document.amount > 0.0) {
                            DetailRow("Betrag / Beitrag", String.format(Locale.GERMAN, "%.2f €", document.amount))
                        }
                        if (document.reminderNotes.isNotBlank()) {
                            DetailRow("Notiz", document.reminderNotes)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val targetDate = document.cancellationDeadline ?: document.contractEndDate ?: System.currentTimeMillis()
                                    val desc = "Kündigungsfrist für ${document.title} (${document.sender}). Notiz: ${document.reminderNotes}"
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                            data = android.provider.CalendarContract.Events.CONTENT_URI
                                            putExtra(android.provider.CalendarContract.Events.TITLE, "Kündigungsfrist: ${document.title}")
                                            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, desc)
                                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, targetDate)
                                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, targetDate + 3600000L)
                                            putExtra(android.provider.CalendarContract.Events.ALL_DAY, true)
                                            putExtra(android.provider.CalendarContract.Events.HAS_ALARM, 1)
                                        }
                                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Kalender-App konnte nicht geöffnet werden", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("In Google Kalender eintragen", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        Text(
                            text = "Hinterlege Laufzeiten und Kündigungsfristen, um rechtzeitig in myDocAnizer oder im Android-Kalender erinnert zu werden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // GIROCODE / SEPA-ÜBERWEISUNGS CARD
            Spacer(modifier = Modifier.height(12.dp))
            com.example.ui.components.GiroCodePaymentCard(
                ocrText = document.ocrText,
                defaultSender = document.sender,
                amountFromDb = document.amount
            )

            // VORDEFINIERTE ZUSATZFELDER (CUSTOM FIELDS)
            val applicableFields = remember(allCustomFields, document) {
                allCustomFields
                    .filter {
                        it.scope == com.example.model.CustomFieldScope.GLOBAL ||
                        it.targetMainCategory.isBlank() ||
                        it.targetMainCategory.equals(document.mainCategory, ignoreCase = true)
                    }
                    .distinctBy { it.name.trim().lowercase() }
            }

            if (applicableFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Zusatzfelder & Metadaten",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        applicableFields.forEach { field ->
                            var fieldEditing by remember { mutableStateOf(false) }
                            val existingVal = documentCustomFieldValues.find {
                                it.documentId == document.id && it.customFieldId == field.id
                            }?.fieldValue ?: field.defaultValue
                            var tempVal by remember(existingVal) { mutableStateOf(existingVal) }

                            val fieldCheck = qualityReport.getCheckForField(field.name)
                            val hasFieldIssue = fieldCheck != null && (!fieldCheck.isFoundInText || !fieldCheck.isPlausible) && field.name !in dismissedModalWarnings

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = field.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
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
                                        text = if (existingVal.isNotBlank()) existingVal else "— Nicht gesetzt —",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hasFieldIssue) MaterialTheme.colorScheme.error
                                        else if (existingVal.isNotBlank()) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    if (hasFieldIssue && fieldCheck?.suggestedCorrection != null) {
                                        Text(
                                            text = "Vorschlag: ${fieldCheck.suggestedCorrection}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                onUpdateCustomFieldValue?.invoke(document.id, field.id, fieldCheck.suggestedCorrection)
                                                dismissedModalWarnings = dismissedModalWarnings + field.name
                                            }
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (hasFieldIssue) {
                                        IconButton(
                                            onClick = { dismissedModalWarnings = dismissedModalWarnings + field.name },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Done, contentDescription = "Wert bestätigen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    TextButton(onClick = { fieldEditing = true }) {
                                        Text(if (existingVal.isNotBlank()) "Bearbeiten" else "Setzen")
                                    }
                                }
                            }

                            if (fieldEditing) {
                                AlertDialog(
                                    onDismissRequest = { fieldEditing = false },
                                    title = { Text(field.name, fontWeight = FontWeight.Bold) },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (field.description.isNotBlank()) {
                                                Text(field.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            if (field.type == com.example.model.CustomFieldType.SELECTION && field.options.isNotBlank()) {
                                                val opts = field.options.split(",").map { it.trim() }
                                                opts.forEach { opt ->
                                                    FilterChip(
                                                        selected = tempVal == opt,
                                                        onClick = { tempVal = opt },
                                                        label = { Text(opt) }
                                                    )
                                                }
                                            } else if (field.type == com.example.model.CustomFieldType.BOOLEAN) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    FilterChip(selected = tempVal == "Ja", onClick = { tempVal = "Ja" }, label = { Text("Ja") })
                                                    FilterChip(selected = tempVal == "Nein", onClick = { tempVal = "Nein" }, label = { Text("Nein") })
                                                }
                                            } else {
                                                OutlinedTextField(
                                                    value = tempVal,
                                                    onValueChange = { tempVal = it },
                                                    label = { Text("Wert eingeben") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            onUpdateCustomFieldValue?.invoke(document.id, field.id, tempVal)
                                            fieldEditing = false
                                        }) {
                                            Text("Speichern")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { fieldEditing = false }) {
                                            Text("Abbrechen")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showContractDialog) {
                var editEndDate by remember { mutableStateOf(if (document.contractEndDate != null) sdfDate.format(Date(document.contractEndDate)) else "") }
                var editCancelDate by remember { mutableStateOf(if (document.cancellationDeadline != null) sdfDate.format(Date(document.cancellationDeadline)) else "") }
                var editReminderDays by remember { mutableStateOf(document.reminderDaysBefore) }
                var editNotes by remember { mutableStateOf(document.reminderNotes) }
                var editAmount by remember { mutableStateOf(if (document.amount != null) document.amount.toString() else "") }
                var editMode by remember { mutableStateOf(document.calendarEventType) }

                AlertDialog(
                    onDismissRequest = { showContractDialog = false },
                    title = { Text("Vertrag & Fristen verwalten", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = editEndDate,
                                onValueChange = { editEndDate = it },
                                label = { Text("Vertragsende (z.B. 31.12.2025)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editCancelDate,
                                onValueChange = { editCancelDate = it },
                                label = { Text("Kündigungsfrist (z.B. 30.09.2025)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editAmount,
                                onValueChange = { editAmount = it },
                                label = { Text("Monats- oder Jahresbetrag (€)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = { Text("Notiz / Kündigungsklausel") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Erinnerungsart:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = editMode == "INTERNAL",
                                    onClick = { editMode = "INTERNAL" },
                                    label = { Text("Nur myDocAnizer") }
                                )
                                FilterChip(
                                    selected = editMode == "EXTERNAL",
                                    onClick = { editMode = "EXTERNAL" },
                                    label = { Text("Nur Kalender") }
                                )
                                FilterChip(
                                    selected = editMode == "BOTH",
                                    onClick = { editMode = "BOTH" },
                                    label = { Text("Beides") }
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val parseEndDate = try {
                                if (editEndDate.isNotBlank()) sdfDate.parse(editEndDate)?.time else null
                            } catch (e: Exception) { null }

                            val parseCancelDate = try {
                                if (editCancelDate.isNotBlank()) sdfDate.parse(editCancelDate)?.time else null
                            } catch (e: Exception) { null }

                            val parseAmount = editAmount.toDoubleOrNull()

                            onUpdateContractReminder?.invoke(
                                document.id,
                                parseEndDate,
                                parseCancelDate,
                                editReminderDays,
                                (parseEndDate != null || parseCancelDate != null),
                                editMode,
                                editNotes,
                                parseAmount
                            )
                            showContractDialog = false
                        }) {
                            Text("Speichern")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showContractDialog = false }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }

            if (showTypePicker) {
                AlertDialog(
                    onDismissRequest = { showTypePicker = false },
                    title = { Text("Doc-Typ auswählen") },
                    text = {
                        Column {
                            TextButton(
                                onClick = {
                                    currentDocType = ""
                                    onUpdateDocType(document.id, "")
                                    showTypePicker = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("— Kein Doc-Typ —", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                            availableDocTypes.forEach { type ->
                                TextButton(
                                    onClick = {
                                        currentDocType = type.name
                                        onUpdateDocType(document.id, type.name)
                                        showTypePicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(type.name)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showTypePicker = false }) {
                            Text("Schließen")
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Aktionen: In den Papierkorb verschieben & Fertig
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onDelete(document)
                        onDismissRequest()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("In Papierkorb")
                }

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Fertig")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    return sdf.format(Date(timestamp))
}
