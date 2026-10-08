package com.example.ui.views.wizard

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DOC_NAMING_STYLE_OPTIONS
import com.example.model.PREFIX_STYLE_OPTIONS
import com.example.model.PdfSettings
import com.example.ui.DocAnizerViewModel

@Composable
fun StepStorageLocation(
    viewModel: DocAnizerViewModel,
    pdfSettings: PdfSettings
) {
    val context = LocalContext.current
    var folderFeedback by remember { mutableStateOf<String?>(null) }
    var activeInfoDialog by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val pathStr = uri.path ?: uri.toString()
            viewModel.updatePdfSettings(
                pdfSettings.copy(
                    baseStorageLocation = "CUSTOM",
                    customStoragePath = pathStr
                )
            )
            folderFeedback = "Ordner erfolgreich ausgewählt!"
        }
    }

    if (activeInfoDialog == "APP_STORAGE") {
        AlertDialog(
            onDismissRequest = { activeInfoDialog = null },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("🔒 Was ist der sichere App-Tresor?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Der App-Tresor nutzt den isolierten Speicherbereich von Android (sog. Sandbox).\n\n" +
                            "• Höchste Privatsphäre: Andere Apps auf deinem Handy haben keinen Zugriff auf diese Dokumente.\n" +
                            "• 0 Berechtigungen: Du musst der App keinerlei System-Berechtigungen auf deinen Gerätespeicher erteilen.\n" +
                            "• Geschützt: Ideal für vertrauliche Verträge, Ausweise, Quittungen und Finanzen.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { activeInfoDialog = null }) {
                    Text("Verstanden", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (activeInfoDialog == "CUSTOM_FOLDER") {
        AlertDialog(
            onDismissRequest = { activeInfoDialog = null },
            icon = { Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("📁 Was bedeutet 'Eigener Ordner'?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Hier speichert myDocAnizer Dokumente in einem Verzeichnis deiner Wahl (z.B. im Ordner 'Dokumente' oder auf der SD-Karte).\n\n" +
                            "• Freier Zugriff: Du kannst die PDF-Dateien auch mit anderen Dateimanagern oder PC-Sync-Programmen öffnen.\n" +
                            "• Gezielte Freigabe (Scoped Storage): Die App fordert KEINEN Vollzugriff auf dein Smartphone, sondern erhält ausschließlich für den von dir ausgewählten Ordner eine Berechtigung.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { activeInfoDialog = null }) {
                    Text("Verstanden", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (activeInfoDialog == "PREFIX_STYLE") {
        AlertDialog(
            onDismissRequest = { activeInfoDialog = null },
            icon = { Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("🔢 Wozu dient die Ordner-Nummerierung?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Dateimanager sortieren Ordner auf Android und PCs standardmäßig streng nach dem Alphabet.\n\n" +
                            "• Feste Reihenfolge: Durch vorangestellte Nummern (z.B. '01_Wohnung', '02_Finanzen' oder 'A01_Verträge') behält deine Ordnerstruktur immer eine feste, übersichtliche Ordnung.\n" +
                            "• Strukturierte Ordnerhierarchie: Schnelles und intuitives Wiederfinden deiner Dokumente.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { activeInfoDialog = null }) {
                    Text("Verstanden", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (activeInfoDialog == "DOC_NAMING") {
        AlertDialog(
            onDismissRequest = { activeInfoDialog = null },
            icon = { Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("📝 Warum automatische Dateinamen-Muster?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Ein einheitliches Namensmuster spart dir das mühsame manuelle Benennen jedes einzelnen Scans.\n\n" +
                            "• Chronologische Ordnung: Durch das Format 'JJJJ-MM-TT' am Anfang sortieren sich deine Dokumente auf jedem PC und Smartphone automatisch zeitlich korrekt.\n" +
                            "• Sofort erkennbar: Absender und Titel sind direkt im Dateinamen sichtbar, ohne dass du die PDF erst öffnen musst.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { activeInfoDialog = null }) {
                    Text("Verstanden", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(
                text = "Speicherort & Namensgebung",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Konfiguriere, wo deine gescannten Dokumente abgelegt und wie sie benannt werden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val isAppStorageSelected = pdfSettings.baseStorageLocation == "APP_STORAGE"
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isAppStorageSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = if (isAppStorageSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.updatePdfSettings(pdfSettings.copy(baseStorageLocation = "APP_STORAGE")) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isAppStorageSelected,
                    onClick = { viewModel.updatePdfSettings(pdfSettings.copy(baseStorageLocation = "APP_STORAGE")) },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sicherer App-Tresor (Empfohlen)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isAppStorageSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                    Text(
                        text = "Vollständig geschützt im isolierten App-Speicher (Sandbox). Höchste Sicherheit, 0 Berechtigungen erforderlich.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { activeInfoDialog = "APP_STORAGE" },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info zu App-Tresor",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        val isCustomSelected = pdfSettings.baseStorageLocation == "CUSTOM"
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isCustomSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = if (isCustomSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    viewModel.updatePdfSettings(pdfSettings.copy(baseStorageLocation = "CUSTOM"))
                    if (pdfSettings.customStoragePath.isBlank()) {
                        folderPickerLauncher.launch(null)
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isCustomSelected,
                    onClick = {
                        viewModel.updatePdfSettings(pdfSettings.copy(baseStorageLocation = "CUSTOM"))
                        if (pdfSettings.customStoragePath.isBlank()) {
                            folderPickerLauncher.launch(null)
                        }
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Eigener Ordner auf dem Gerät",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                    Text(
                        text = "Freie Ordnerwahl über offiziellen Android-Dateimanager. Zugriff nur auf diesen Ordner.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { activeInfoDialog = "CUSTOM_FOLDER" },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info zu Eigener Ordner",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (pdfSettings.baseStorageLocation == "CUSTOM") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (pdfSettings.customStoragePath.isNotBlank()) "Ordner ändern" else "Ordner auswählen", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (pdfSettings.customStoragePath.isNotBlank()) {
                Text(
                    text = "📁 ${pdfSettings.customStoragePath.takeLast(35)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))

        val currentFolderStrategy by viewModel.folderStrategy.collectAsState()
        var isFolderStrategyExpanded by remember { mutableStateOf(false) }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Wie sortierst du Dokumente primär in Ebene 1?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Bestimmt, wie die Fachabteilungen und der KI-Wizard neue Ordner auf oberster Ebene vorschlagen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            @OptIn(ExperimentalMaterial3Api::class)
            ExposedDropdownMenuBox(
                expanded = isFolderStrategyExpanded,
                onExpandedChange = { isFolderStrategyExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentFolderStrategy.title,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isFolderStrategyExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag("dropdown_wizard_folder_strategy")
                )

                ExposedDropdownMenu(
                    expanded = isFolderStrategyExpanded,
                    onDismissRequest = { isFolderStrategyExpanded = false }
                ) {
                    com.example.model.FolderStrategy.entries.forEach { strategy ->
                        val isSelected = strategy == currentFolderStrategy
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = strategy.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                    Text(
                                        text = strategy.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "📁 Bsp: ${strategy.examplePath}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            },
                            onClick = {
                                isFolderStrategyExpanded = false
                                viewModel.setFolderStrategy(strategy)
                            }
                        )
                    }
                }
            }
        }

        var isPrefixDropdownExpanded by remember { mutableStateOf(false) }
        val currentPrefixOption = PREFIX_STYLE_OPTIONS.firstOrNull { it.id == pdfSettings.folderPrefixStyle } ?: PREFIX_STYLE_OPTIONS.first()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ordner-Nummerierungs-Stil",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Nummeriert Ordner (z.B. 01_Wohnung) für eine feste alphabetische Sortierung.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { activeInfoDialog = "PREFIX_STYLE" },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info zu Ordner-Nummerierung",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            @OptIn(ExperimentalMaterial3Api::class)
            ExposedDropdownMenuBox(
                expanded = isPrefixDropdownExpanded,
                onExpandedChange = { isPrefixDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentPrefixOption.title,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isPrefixDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = isPrefixDropdownExpanded,
                    onDismissRequest = { isPrefixDropdownExpanded = false }
                ) {
                    PREFIX_STYLE_OPTIONS.forEach { option ->
                        val isSelected = option.id == pdfSettings.folderPrefixStyle
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(text = option.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                    Text(text = "📁 ${option.example}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                isPrefixDropdownExpanded = false
                                if (option.id == "NONE") {
                                    viewModel.updatePdfSettings(pdfSettings.copy(folderPrefixStyle = "NONE", useIdPrefixes = false))
                                } else {
                                    viewModel.updatePdfSettings(pdfSettings.copy(folderPrefixStyle = option.id, useIdPrefixes = true))
                                }
                            }
                        )
                    }
                }
            }
        }

        var isDocNamingDropdownExpanded by remember { mutableStateOf(false) }
        val currentDocNamingOption = DOC_NAMING_STYLE_OPTIONS.firstOrNull { it.id == pdfSettings.docNamingStyle } ?: DOC_NAMING_STYLE_OPTIONS.first()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dateinamen-Muster für Dokumente",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Bestimmt das automatische Namensschema für gescannte PDF-Dateien.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { activeInfoDialog = "DOC_NAMING" },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info zu Dateinamen-Muster",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            @OptIn(ExperimentalMaterial3Api::class)
            ExposedDropdownMenuBox(
                expanded = isDocNamingDropdownExpanded,
                onExpandedChange = { isDocNamingDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentDocNamingOption.title,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDocNamingDropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = isDocNamingDropdownExpanded,
                    onDismissRequest = { isDocNamingDropdownExpanded = false }
                ) {
                    DOC_NAMING_STYLE_OPTIONS.forEach { option ->
                        val isSelected = option.id == pdfSettings.docNamingStyle
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(text = option.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                    Text(text = "📄 ${option.example}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                isDocNamingDropdownExpanded = false
                                viewModel.updatePdfSettings(pdfSettings.copy(docNamingStyle = option.id))
                            }
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Live-Vorschau deiner Struktur",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val folderSample = when (pdfSettings.folderPrefixStyle) {
                            "STANDARD_DE" -> "01_Finanzen"
                            "AKTENPLAN" -> "A01_Vertraege"
                            "NUMERIC" -> "01_Dokumente"
                            else -> "Finanzen"
                        }
                        val sampleFileName = currentDocNamingOption.example

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("📁", fontSize = 13.sp)
                            Text(folderSample, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("↳ 📄", fontSize = 12.sp)
                            Text(sampleFileName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isAppStorageSelected) "🔒 Speicherort: Sicherer App-Tresor" else "📁 Speicherort: Eigener Ordner",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "100% DSGVO-konform",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
