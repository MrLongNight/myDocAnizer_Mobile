package com.example.ui.views.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DOC_NAMING_STYLE_OPTIONS
import com.example.model.FolderStrategy
import com.example.model.PREFIX_STYLE_OPTIONS
import com.example.model.PRESET_OPTIMIZATION_TEMPLATES
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.CollapsibleSettingsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSettingsTab(viewModel: DocAnizerViewModel) {
    val pdfSettings by viewModel.pdfSettings.collectAsState()
    val scannerSettings by viewModel.scannerSettings.collectAsState()
    val templates by viewModel.templates.collectAsState()

    var isOptimizationExpanded by remember { mutableStateOf(false) }
    var isQualityExpanded by remember { mutableStateOf(false) }
    var isNamingExpanded by remember { mutableStateOf(false) }
    var isStorageExpanded by remember { mutableStateOf(false) }

    val allExpanded = isOptimizationExpanded && isQualityExpanded && isNamingExpanded && isStorageExpanded

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    val nextState = !allExpanded
                    isOptimizationExpanded = nextState
                    isQualityExpanded = nextState
                    isNamingExpanded = nextState
                    isStorageExpanded = nextState
                }
            ) {
                Text(
                    text = if (allExpanded) "Alle zuklappen" else "Alle aufklappen",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 1. DOKUMENTEN-OPTIMIERUNG & TEMPLATES
        val activeTemplate = PRESET_OPTIMIZATION_TEMPLATES.find { it.id == pdfSettings.activeOptimizationTemplateId }
            ?: PRESET_OPTIMIZATION_TEMPLATES.first()
        val optSubtitle = if (pdfSettings.isOptimizationEnabled) {
            "Aktiviert • ${activeTemplate.name}"
        } else {
            "Deaktiviert (Scans werden ohne Nachbearbeitung gespeichert)"
        }

        CollapsibleSettingsCard(
            title = "Dokumenten-Optimierung & Filter-Templates",
            subtitle = optSubtitle,
            icon = Icons.Default.AutoFixHigh,
            isExpanded = isOptimizationExpanded,
            onToggle = { isOptimizationExpanded = !isOptimizationExpanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatische Dokumenten-Optimierung", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("Verbessert Lesbarkeit und Schärfe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = pdfSettings.isOptimizationEnabled,
                    onCheckedChange = { viewModel.setOptimizationEnabled(it) }
                )
            }

            if (pdfSettings.isOptimizationEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Text("Optimierungs-Profil nach Dokumentenart:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

                PRESET_OPTIMIZATION_TEMPLATES.forEach { template ->
                    val isSelected = pdfSettings.activeOptimizationTemplateId == template.id
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.setOptimizationTemplate(template.id) }
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSelected, onClick = { viewModel.setOptimizationTemplate(template.id) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = template.name, style = MaterialTheme.typography.titleSmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                Text(text = template.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // 2. PDF-QUALITÄT
        CollapsibleSettingsCard(
            title = "PDF-Qualität, DPI & Farbmodus",
            subtitle = "${pdfSettings.defaultColorMode} • ${pdfSettings.dpi} DPI",
            icon = Icons.Default.Tune,
            isExpanded = isQualityExpanded,
            onToggle = { isQualityExpanded = !isQualityExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scan-Auflösung (DPI):", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(150, 300, 600).forEach { dpi ->
                        FilterChip(
                            selected = pdfSettings.dpi == dpi,
                            onClick = { viewModel.updatePdfSettings(pdfSettings.copy(dpi = dpi)) },
                            label = { Text("$dpi DPI") }
                        )
                    }
                }
            }
        }

        // 3. DATEINAMEN
        var isDocNamingDropdownExpanded by remember { mutableStateOf(false) }
        val activeDocNamingOption = DOC_NAMING_STYLE_OPTIONS.firstOrNull { it.id == pdfSettings.docNamingStyle } ?: DOC_NAMING_STYLE_OPTIONS.first()

        CollapsibleSettingsCard(
            title = "Dateinamen für Dokumente",
            subtitle = activeDocNamingOption.title,
            icon = Icons.Default.DriveFileRenameOutline,
            isExpanded = isNamingExpanded,
            onToggle = { isNamingExpanded = !isNamingExpanded }
        ) {
            ExposedDropdownMenuBox(
                expanded = isDocNamingDropdownExpanded,
                onExpandedChange = { isDocNamingDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = activeDocNamingOption.title,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Namensmuster") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDocNamingDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = isDocNamingDropdownExpanded,
                    onDismissRequest = { isDocNamingDropdownExpanded = false }
                ) {
                    DOC_NAMING_STYLE_OPTIONS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.title) },
                            onClick = {
                                isDocNamingDropdownExpanded = false
                                viewModel.updatePdfSettings(pdfSettings.copy(docNamingStyle = option.id))
                            }
                        )
                    }
                }
            }
        }

        // 4. SPEICHERPFADE
        val context = LocalContext.current
        val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                try { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
                viewModel.updatePdfSettings(pdfSettings.copy(baseStorageLocation = "CUSTOM", customStoragePath = uri.path ?: uri.toString()))
            }
        }

        CollapsibleSettingsCard(
            title = "Speicherort & Ordner-Struktur",
            subtitle = pdfSettings.baseStorageLocation,
            icon = Icons.Default.FolderSpecial,
            isExpanded = isStorageExpanded,
            onToggle = { isStorageExpanded = !isStorageExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val storageOptions = listOf(
                    Triple("APP_STORAGE", "Sicherer App-Tresor", "Höchste Sicherheit."),
                    Triple("DOCUMENTS", "Android Dokumente-Ordner", "Allgemeines Verzeichnis."),
                    Triple("CUSTOM", "Eigener Ordner", pdfSettings.customStoragePath)
                )

                storageOptions.forEach { (key, title, desc) ->
                    val isSelected = pdfSettings.baseStorageLocation == key
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.updatePdfSettings(pdfSettings.copy(baseStorageLocation = key))
                            if (key == "CUSTOM" && pdfSettings.customStoragePath.isBlank()) {
                                folderPickerLauncher.launch(null)
                            }
                        }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSelected, onClick = { viewModel.updatePdfSettings(pdfSettings.copy(baseStorageLocation = key)) })
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                Text(text = desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
