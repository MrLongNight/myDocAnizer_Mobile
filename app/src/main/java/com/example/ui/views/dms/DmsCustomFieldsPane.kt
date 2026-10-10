package com.example.ui.views.dms

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CustomFieldEntity
import com.example.model.CustomFieldScope
import com.example.model.CustomFieldType
import com.example.model.DocumentCustomFieldValueEntity
import com.example.model.DocumentEntity

/**
 * Reiter: Vordefinierte & Globale Zusatzfelder
 */
@Composable
fun DmsCustomFieldsPane(
    customFields: List<CustomFieldEntity>,
    customFieldValues: List<DocumentCustomFieldValueEntity>,
    documents: List<DocumentEntity>,
    availableMainCategories: List<String>,
    onAddFieldClick: () -> Unit,
    onEditField: (CustomFieldEntity) -> Unit,
    onDeleteField: (String) -> Unit,
    onSelectDocument: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedScopeFilter by remember { mutableStateOf("ALL") }

    val distinctFields = remember(customFields) {
        customFields.distinctBy { it.name.trim().lowercase() }
    }

    val filteredFields = remember(distinctFields, selectedScopeFilter) {
        when (selectedScopeFilter) {
            "GLOBAL" -> distinctFields.filter { it.scope == CustomFieldScope.GLOBAL }
            "FOLDER" -> distinctFields.filter { it.scope == CustomFieldScope.FOLDER_SPECIFIC }
            else -> distinctFields
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item(key = "custom_fields_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Vordefinierte & Globale Zusatzfelder",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Zusatzfelder können per Regel oder manuell bestimmten Dokumenten zugewiesen werden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedScopeFilter == "ALL",
                            onClick = { selectedScopeFilter = "ALL" },
                            label = { Text("Alle (${customFields.size})") }
                        )
                        FilterChip(
                            selected = selectedScopeFilter == "GLOBAL",
                            onClick = { selectedScopeFilter = "GLOBAL" },
                            label = { Text("Global") }
                        )
                        FilterChip(
                            selected = selectedScopeFilter == "FOLDER",
                            onClick = { selectedScopeFilter = "FOLDER" },
                            label = { Text("Ordner-spezifisch") }
                        )
                    }

                    FilledTonalButton(
                        onClick = onAddFieldClick,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_add_custom_field")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Neues Feld", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (filteredFields.isEmpty()) {
            item(key = "empty_custom_fields") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Keine Zusatzfelder in dieser Kategorie",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Erstelle ein neues Zusatzfeld, um Verträgen oder Dokumenten Beträge, Fristen und Status-Metadaten zuzuweisen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        items(filteredFields, key = { it.id }) { field ->
            val assignedValues = customFieldValues.filter { it.customFieldId == field.id && it.fieldValue.isNotBlank() }
            val assignedDocIds = assignedValues.map { it.documentId }.toSet()
            val assignedDocs = documents.filter { assignedDocIds.contains(it.id) }
            var isExpanded by remember { mutableStateOf(false) }
            var showMenu by remember { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = when (field.type) {
                                    CustomFieldType.AMOUNT -> Icons.Default.AttachMoney
                                    CustomFieldType.DATE -> Icons.Default.Event
                                    CustomFieldType.SELECTION -> Icons.Default.List
                                    CustomFieldType.BOOLEAN -> Icons.Default.CheckCircleOutline
                                    else -> Icons.Default.TextFields
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )

                            Column {
                                Text(
                                    text = field.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (field.description.isNotBlank()) {
                                    Text(
                                        text = field.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Box {
                            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Bearbeiten") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onEditField(field)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Löschen", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteField(field.id)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                text = field.type.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (field.scope == CustomFieldScope.GLOBAL) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = if (field.scope == CustomFieldScope.GLOBAL) "Global" else "Ordner: ${field.targetMainCategory.ifBlank { "Alle" }}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (field.scope == CustomFieldScope.GLOBAL) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        if (assignedDocs.isNotEmpty()) {
                            TextButton(
                                onClick = { isExpanded = !isExpanded },
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Text("${assignedDocs.size} Dok.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Text(
                                text = "Keine Zuweisung",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (isExpanded && assignedDocs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))

                        assignedDocs.forEach { doc ->
                            val valItem = assignedValues.find { it.documentId == doc.id }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable { onSelectDocument(doc) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(doc.sender, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (valItem != null) {
                                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                            Text(valItem.fieldValue, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateOrEditCustomFieldDialog(
    existingField: CustomFieldEntity?,
    availableMainCategories: List<String>,
    allCustomFields: List<CustomFieldEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (CustomFieldEntity) -> Unit
) {
    var name by remember { mutableStateOf(existingField?.name.orEmpty()) }
    var description by remember { mutableStateOf(existingField?.description.orEmpty()) }
    var selectedType by remember { mutableStateOf(existingField?.type ?: CustomFieldType.TEXT) }
    var options by remember { mutableStateOf(existingField?.options.orEmpty()) }
    var selectedScope by remember { mutableStateOf(existingField?.scope ?: CustomFieldScope.GLOBAL) }
    var targetMainCat by remember { mutableStateOf(existingField?.targetMainCategory.orEmpty()) }

    val trimmedName = name.trim()
    val isDuplicateName = remember(trimmedName, existingField, allCustomFields) {
        trimmedName.isNotBlank() && allCustomFields.any {
            it.id != existingField?.id && it.name.trim().equals(trimmedName, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingField != null) "Zusatzfeld bearbeiten" else "Neues Zusatzfeld anlegen", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Feldbezeichnung (z. B. Monatliche Kosten)") },
                    isError = isDuplicateName,
                    supportingText = {
                        if (isDuplicateName) {
                            Text(
                                text = "Ein Zusatzfeld mit dieser Bezeichnung existiert bereits. Bitte eindeutige Feldbezeichnung eingeben.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            Text(
                                text = "Nur eindeutige Feldbezeichnungen erlaubt.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Beschreibung (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Feldtyp:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = selectedType == CustomFieldType.TEXT,
                            onClick = { selectedType = CustomFieldType.TEXT },
                            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(CustomFieldType.TEXT.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedType == CustomFieldType.AMOUNT,
                            onClick = { selectedType = CustomFieldType.AMOUNT },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(CustomFieldType.AMOUNT.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = selectedType == CustomFieldType.DATE,
                            onClick = { selectedType = CustomFieldType.DATE },
                            leadingIcon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(CustomFieldType.DATE.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedType == CustomFieldType.SELECTION,
                            onClick = { selectedType = CustomFieldType.SELECTION },
                            leadingIcon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(CustomFieldType.SELECTION.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = selectedType == CustomFieldType.BOOLEAN,
                            onClick = { selectedType = CustomFieldType.BOOLEAN },
                            leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(CustomFieldType.BOOLEAN.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                if (selectedType == CustomFieldType.SELECTION) {
                    OutlinedTextField(
                        value = options,
                        onValueChange = { options = it },
                        label = { Text("Auswahloptionen (kommagetrennt)") },
                        placeholder = { Text("Bezahlt,Offen,Prüfen,Gekündigt") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Geltungsbereich (Scope):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedScope == CustomFieldScope.GLOBAL,
                        onClick = { selectedScope = CustomFieldScope.GLOBAL },
                        label = { Text("Global (Alle Ordner)") }
                    )
                    FilterChip(
                        selected = selectedScope == CustomFieldScope.FOLDER_SPECIFIC,
                        onClick = { selectedScope = CustomFieldScope.FOLDER_SPECIFIC },
                        label = { Text("Ordner-spezifisch") }
                    )
                }

                if (selectedScope == CustomFieldScope.FOLDER_SPECIFIC) {
                    Text("Ziel-Ordner wählen:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableMainCategories.forEach { cat ->
                            FilterChip(
                                selected = targetMainCat.equals(cat, ignoreCase = true),
                                onClick = { targetMainCat = cat },
                                label = { Text("📁 $cat") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && !isDuplicateName) {
                        val entity = existingField?.copy(
                            name = name.trim(),
                            description = description.trim(),
                            type = selectedType,
                            options = options.trim(),
                            scope = selectedScope,
                            targetMainCategory = targetMainCat
                        ) ?: CustomFieldEntity(
                            name = name.trim(),
                            description = description.trim(),
                            type = selectedType,
                            options = options.trim(),
                            scope = selectedScope,
                            targetMainCategory = targetMainCat
                        )
                        onConfirm(entity)
                    }
                },
                enabled = name.isNotBlank() && !isDuplicateName
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
