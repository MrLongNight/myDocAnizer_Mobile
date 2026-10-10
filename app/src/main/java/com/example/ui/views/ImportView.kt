package com.example.ui.views

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocumentEntity
import com.example.model.ImportTemplateItem
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.import.ImportFilePickerPane
import com.example.ui.views.import.ImportMetadataFormPane
import com.example.ui.views.import.ImportOcrAndFormatPane
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportView(
    viewModel: DocAnizerViewModel,
    onNavigateToDocAnizer: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val importTemplates by viewModel.importTemplates.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val docTypes by viewModel.docTypes.collectAsState()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedPdfBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedPdfName by remember { mutableStateOf<String?>(null) }

    var title by remember { mutableStateOf("") }
    var selectedMainCategory by remember { mutableStateOf("Verträge") }
    var selectedSubCategory by remember { mutableStateOf("Telekommunikation") }
    var selectedDocType by remember { mutableStateOf("laufender Vertrag") }
    var tagsInput by remember { mutableStateOf("Mobilfunk, Vertrag") }

    var targetFormat by remember { mutableStateOf("PDF") }
    var isColorMode by remember { mutableStateOf(true) }

    var runOcr by remember { mutableStateOf(true) }
    var isOcrRunning by remember { mutableStateOf(false) }
    var recognizedOcrText by remember { mutableStateOf("") }
    var isOcrExpanded by remember { mutableStateOf(false) }

    var activeTemplateId by remember { mutableStateOf<String?>(importTemplates.firstOrNull()?.id) }

    var showCreateTemplateDialog by remember { mutableStateOf(false) }
    var archivedDocResult by remember { mutableStateOf<DocumentEntity?>(null) }

    var showFolderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(importTemplates) {
        if (title.isBlank()) {
            importTemplates.firstOrNull()?.let { first ->
                selectedMainCategory = first.mainCategory
                selectedSubCategory = first.subCategory
                selectedDocType = first.defaultDocType
                tagsInput = first.defaultTags.joinToString(", ")
                targetFormat = first.targetFormat
                runOcr = first.runOcrByDefault
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bmp = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
                selectedBitmap = bmp
                selectedPdfBytes = null
                selectedPdfName = null
                if (title.isBlank()) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.GERMANY)
                    title = "Import_${sdf.format(Date())}"
                }
                if (runOcr && bmp != null && recognizedOcrText.isBlank()) {
                    coroutineScope.launch {
                        isOcrRunning = true
                        val text = viewModel.performOcrAnalysis(bmp, "", title)
                        recognizedOcrText = text
                        isOcrRunning = false
                        isOcrExpanded = true
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes()
                }
                selectedPdfBytes = bytes
                selectedPdfName = uri.lastPathSegment ?: "Dokument.pdf"
                selectedBitmap = null
                targetFormat = "PDF"
                if (title.isBlank()) {
                    title = selectedPdfName?.removeSuffix(".pdf") ?: "PDF_Import"
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun applyTemplate(tmpl: ImportTemplateItem) {
        activeTemplateId = tmpl.id
        selectedMainCategory = tmpl.mainCategory
        selectedSubCategory = tmpl.subCategory
        selectedDocType = tmpl.defaultDocType
        tagsInput = tmpl.defaultTags.joinToString(", ")
        targetFormat = tmpl.targetFormat
        runOcr = tmpl.runOcrByDefault
        isColorMode = tmpl.colorMode == "COLOR"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. HEADER
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dokument- & Beleg-Import",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Wiederkehrende Profile, Bild- oder PDF-Archivierung mit OCR-Analyse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. TEMPLATES
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bookmarks,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Templates für wiederkehrende Importe",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(
                    onClick = { showCreateTemplateDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Neues Template", style = MaterialTheme.typography.labelSmall)
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(importTemplates, key = { it.id }) { tmpl ->
                    val isSelected = activeTemplateId == tmpl.id
                    ElevatedCard(
                        onClick = { applyTemplate(tmpl) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .widthIn(min = 160.dp, max = 220.dp)
                            .testTag("import_template_${tmpl.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = when (tmpl.iconType) {
                                        "contract" -> Icons.Default.Gavel
                                        "salary" -> Icons.Default.Payments
                                        "shield" -> Icons.Default.Security
                                        "receipt" -> Icons.Default.ReceiptLong
                                        else -> Icons.Default.Description
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(tmpl.targetFormat, fontSize = 10.sp) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = tmpl.name,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${tmpl.mainCategory} ➔ ${tmpl.subCategory}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (tmpl.defaultDocType.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Typ: ${tmpl.defaultDocType}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. FILE PICKER PANE
        ImportFilePickerPane(
            selectedBitmap = selectedBitmap,
            selectedPdfBytes = selectedPdfBytes,
            selectedPdfName = selectedPdfName,
            photoPickerLauncher = photoPickerLauncher,
            pdfPickerLauncher = pdfPickerLauncher
        )

        // 4. FORMAT & OCR PANE
        ImportOcrAndFormatPane(
            targetFormat = targetFormat,
            onTargetFormatChange = { targetFormat = it },
            isColorMode = isColorMode,
            onColorModeChange = { isColorMode = it },
            runOcr = runOcr,
            onRunOcrChange = { runOcr = it },
            isOcrRunning = isOcrRunning,
            recognizedOcrText = recognizedOcrText,
            onOcrTextChanged = { recognizedOcrText = it },
            isOcrExpanded = isOcrExpanded,
            onToggleOcrExpanded = { isOcrExpanded = !isOcrExpanded },
            onStartOcr = {
                val bmp = selectedBitmap
                if (bmp != null) {
                    coroutineScope.launch {
                        isOcrRunning = true
                        val text = viewModel.performOcrAnalysis(bmp, "", title)
                        recognizedOcrText = text
                        isOcrRunning = false
                        isOcrExpanded = true
                    }
                }
            },
            showImageFormatOptions = selectedBitmap != null,
            canRunOcr = selectedBitmap != null
        )

        // 5. METADATA & FOLDER PANE
        ImportMetadataFormPane(
            title = title,
            onTitleChange = { title = it },
            selectedMainCategory = selectedMainCategory,
            selectedSubCategory = selectedSubCategory,
            onOpenFolderPicker = { showFolderPicker = true },
            docTypes = docTypes,
            selectedDocType = selectedDocType,
            onDocTypeSelected = { selectedDocType = it },
            tagsInput = tagsInput,
            onTagsChange = { tagsInput = it }
        )

        // 6. ACTIONS
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val tagsList = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    viewModel.importDocumentExtended(
                        bitmap = selectedBitmap,
                        pdfBytes = selectedPdfBytes,
                        title = title.ifBlank { "Import_${SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date())}" },
                        sender = "",
                        mainCategory = selectedMainCategory,
                        subCategory = selectedSubCategory,
                        docType = selectedDocType,
                        tags = tagsList,
                        targetFormat = targetFormat,
                        ocrTextProvided = recognizedOcrText,
                        runOcr = runOcr,
                        isColor = isColorMode
                    ) { createdDoc ->
                        archivedDocResult = createdDoc
                        viewModel.triggerUsbSyncReminderIfNeeded()
                        selectedBitmap = null
                        selectedPdfBytes = null
                        selectedPdfName = null
                        recognizedOcrText = ""
                        title = ""
                    }
                },
                enabled = selectedBitmap != null || selectedPdfBytes != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_execute_import")
            ) {
                Icon(Icons.Default.Archive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (targetFormat == "IMAGE") "Als Bild-Dokument archivieren" else "Als PDF archivieren",
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = { showCreateTemplateDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Als wiederkehrendes Template speichern")
            }
        }
    }

    if (showFolderPicker) {
        AlertDialog(
            onDismissRequest = { showFolderPicker = false },
            title = { Text("Zielordner wählen") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { tmpl ->
                        Surface(
                            onClick = {
                                selectedMainCategory = tmpl.mainCategory
                                selectedSubCategory = tmpl.subCategory
                                showFolderPicker = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedMainCategory == tmpl.mainCategory && selectedSubCategory == tmpl.subCategory)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("${tmpl.mainCategory} ➔ ${tmpl.subCategory}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFolderPicker = false }) {
                    Text("Schließen")
                }
            }
        )
    }

    if (showCreateTemplateDialog) {
        var newTmplName by remember { mutableStateOf("") }
        var newTmplDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateTemplateDialog = false },
            title = { Text("Neues wiederkehrendes Template anlegen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Speichert die aktuellen Einstellungen (Zielordner: $selectedMainCategory / $selectedSubCategory, Doc-Typ: $selectedDocType, Format: $targetFormat) für zukünftige Importe.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newTmplName,
                        onValueChange = { newTmplName = it },
                        label = { Text("Template-Name *") },
                        placeholder = { Text("z.B. Monatliche Vodafone Rechnung") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newTmplDesc,
                        onValueChange = { newTmplDesc = it },
                        label = { Text("Kurzbeschreibung (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTmplName.isNotBlank()) {
                            val tagsList = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val newTmpl = ImportTemplateItem(
                                name = newTmplName.trim(),
                                description = newTmplDesc.trim(),
                                mainCategory = selectedMainCategory,
                                subCategory = selectedSubCategory,
                                defaultDocType = selectedDocType,
                                defaultSender = "",
                                defaultTags = tagsList,
                                targetFormat = targetFormat,
                                runOcrByDefault = runOcr,
                                colorMode = if (isColorMode) "COLOR" else "BW",
                                iconType = if (selectedDocType.contains("vertrag", ignoreCase = true)) "contract" else "receipt"
                            )
                            viewModel.addImportTemplate(newTmpl)
                            activeTemplateId = newTmpl.id
                            showCreateTemplateDialog = false
                        }
                    },
                    enabled = newTmplName.isNotBlank()
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTemplateDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    archivedDocResult?.let { doc ->
        AlertDialog(
            onDismissRequest = { archivedDocResult = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Dokument erfolgreich archiviert!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Titel: ${doc.title}", fontWeight = FontWeight.Bold)
                    Text("Ablageort: ${doc.mainCategory} ➔ ${doc.subCategory}")
                    Text("Format: ${if (doc.fileName.endsWith(".jpg", ignoreCase = true)) "Bilddatei (JPG)" else "PDF-Dokument"}")
                    if (doc.ocrText.isNotBlank()) {
                        Text("OCR-Volltext: ${doc.ocrText.length} Zeichen indexiert", color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        archivedDocResult = null
                        onNavigateToDocAnizer()
                    }
                ) {
                    Text("Im DocAnizer anzeigen")
                }
            },
            dismissButton = {
                TextButton(onClick = { archivedDocResult = null }) {
                    Text("Weiteren Beleg importieren")
                }
            }
        )
    }
}
