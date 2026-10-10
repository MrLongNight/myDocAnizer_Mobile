package com.example.ui.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DocTypeItem
import com.example.model.DocumentEntity
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.DocumentDetailModal
import com.example.ui.views.dms.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class ActiveFilterSheet {
    NONE,
    SELECT_DIMENSION,
    CATEGORY,
    DOC_TYPE,
    PAGE_COUNT,
    DATE_RANGE,
    SORT,
    CUSTOM_FIELD
}

/**
 * DocAnizer Explorer:
 * - Reduziertes, übersichtliches Suchfeld ("Suchen...")
 * - State-of-the-Art Progressive Disclosure: Filter werden nur gezielt ausgewählt und eingeblendet
 * - Interaktive Baum-Struktur (Tree View) für Dokument-Typen mit Aufklapp-Funktion
 * - Direkte Anbindung an die dedizierte Import-Funktion
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmsExplorerView(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier,
    onNavigateToImport: () -> Unit = {}
) {
    val documents by viewModel.filteredDocuments.collectAsStateWithLifecycle()
    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val storageUsage by viewModel.storageUsage.collectAsStateWithLifecycle()
    val pdfSettings by viewModel.pdfSettings.collectAsStateWithLifecycle()
    var isStorageOverviewExpanded by remember { mutableStateOf(false) }
    val filterDocType by viewModel.filterDocType.collectAsStateWithLifecycle()
    val filterMainCategory by viewModel.filterMainCategory.collectAsStateWithLifecycle()
    val filterSubCategory by viewModel.filterSubCategory.collectAsStateWithLifecycle()
    val filterDateRange by viewModel.filterDateRange.collectAsStateWithLifecycle()
    val filterPageFilter by viewModel.filterPageFilter.collectAsStateWithLifecycle()
    val filterSortBy by viewModel.filterSortBy.collectAsStateWithLifecycle()
    val filterCustomFieldId by viewModel.filterCustomFieldId.collectAsStateWithLifecycle()
    val filterCustomFieldValue by viewModel.filterCustomFieldValue.collectAsStateWithLifecycle()
    val activeFilterCount by viewModel.activeFilterCount.collectAsStateWithLifecycle()
    val docTypes by viewModel.docTypes.collectAsStateWithLifecycle()

    // Papierkorb, Fristen & Kalender, KI-Chat & Zusatzfelder
    val trashDocs by viewModel.trashDocuments.collectAsStateWithLifecycle()
    val trashCount by viewModel.trashCount.collectAsStateWithLifecycle()
    val contractDeadlines by viewModel.contractDeadlines.collectAsStateWithLifecycle()
    val trashRetentionDays by viewModel.trashRetentionDays.collectAsStateWithLifecycle()
    val calendarIntegrationMode by viewModel.calendarIntegrationMode.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val foldersHierarchy by viewModel.folders.collectAsStateWithLifecycle()

    val customFields by viewModel.customFields.collectAsStateWithLifecycle()
    val allCustomFieldValues by viewModel.allDocumentCustomFieldValues.collectAsStateWithLifecycle()
    val isSecondRunGenerating by viewModel.isSecondRunGenerating.collectAsStateWithLifecycle()
    val lastAiAnalysisResult by viewModel.lastAiAnalysisResult.collectAsStateWithLifecycle()
    var showCreateCustomFieldDialog by remember { mutableStateOf(false) }
    var fieldToEdit by remember { mutableStateOf<com.example.model.CustomFieldEntity?>(null) }

    var activeSubTab by remember { mutableStateOf(0) } // 0: Ordner, 1: Fristen & Kalender, 2: Papierkorb
    var selectedDocumentForDetail by remember { mutableStateOf<DocumentEntity?>(null) }
    var showAiChatDialog by remember { mutableStateOf(false) }

    // Dialog-Zustände für Ordner-Verwaltung
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var parentForNewSubFolder by remember { mutableStateOf<String?>(null) }
    var folderToRename by remember { mutableStateOf<Pair<String, String?>?>(null) } // (mainCat, subCat)
    var folderToDelete by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var folderToChangeIconAndLogo by remember { mutableStateOf<Pair<String, String?>?>(null) } // Pair(mainFolderIdOrName, subFolderIdOrName?)
    var showCreateDocTypeDialog by remember { mutableStateOf(false) }

    // Aktiver Filter-Konfigurations-Dialog
    var currentFilterSheet by remember { mutableStateOf(ActiveFilterSheet.NONE) }

    // Aufgeklappte Doc-Typ Knoten in der Baum-Struktur (Set von DocType-Namen)
    var expandedDocTypes by remember { mutableStateOf(setOf<String>()) }

    // Dynamisch verfügbare Ordner aus bestehenden Dokumenten und Templates ermitteln
    val availableMainCategories = remember(allDocs, templates) {
        (allDocs.map { it.mainCategory } + templates.map { it.mainCategory }).filter { it.isNotBlank() }.distinct().sorted()
    }
    val availableSubCategories = remember(allDocs, templates, filterMainCategory) {
        if (filterMainCategory != null) {
            (allDocs.filter { it.mainCategory.equals(filterMainCategory, ignoreCase = true) }.map { it.subCategory } +
             templates.filter { it.mainCategory.equals(filterMainCategory, ignoreCase = true) }.map { it.subCategory })
                .filter { it.isNotBlank() }.distinct().sorted()
        } else {
            (allDocs.map { it.subCategory } + templates.map { it.subCategory }).filter { it.isNotBlank() }.distinct().sorted()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("docanizer_view")
    ) {
        // OBERE SUCHLEISTE & PROGRESSIVE DISCLOSURE FILTER-BAR
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // 1. Mittig platziertes, schlankes Suchfeld mit integriertem Filter-Button (innenliegend)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Suchen...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Suche",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Löschen",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Filter-Button mit Badge direkt innerhalb des Suchelements platziert
                                BadgedBox(
                                    badge = {
                                        if (activeFilterCount > 0) {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                                Text("$activeFilterCount")
                                            }
                                        }
                                    }
                                ) {
                                    IconButton(
                                        onClick = { currentFilterSheet = ActiveFilterSheet.SELECT_DIMENSION },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .testTag("btn_open_filter_menu")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Filter konfigurieren",
                                            tint = if (activeFilterCount > 0)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("docanizer_search_input")
                    )
                }

                // 2. STATE OF THE ART DYNAMISCHE FILTER-LEISTE (Progressive Disclosure)
                // Zeigt bei aktiven Filtern die explizit aktivierten Filter-Pills an
                if (activeFilterCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Aktiver Ordner-Filter
                        if (filterMainCategory != null) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.CATEGORY },
                                label = {
                                    Text(
                                        if (filterSubCategory != null) "📁 $filterMainCategory / $filterSubCategory"
                                        else "📁 Ordner: $filterMainCategory"
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable {
                                                viewModel.setFilterMainCategory(null)
                                                viewModel.setFilterSubCategory(null)
                                            }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Aktiver Doc-Typ Filter
                    if (filterDocType != null) {
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.DOC_TYPE },
                                label = {
                                    Text(if (filterDocType == "__NONE__") "🏷️ Ohne Doc-Typ" else "🏷️ Typ: $filterDocType")
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.setFilterDocType(null) }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Aktiver Seitenanzahl-Filter
                    if (filterPageFilter != "ALL") {
                        val pageLabel = when (filterPageFilter) {
                            "SINGLE" -> "1 Seite"
                            "MULTI" -> "Mehrseitig (≥2)"
                            "3_PLUS" -> "≥3 Seiten"
                            "5_PLUS" -> "≥5 Seiten"
                            else -> filterPageFilter
                        }
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.PAGE_COUNT },
                                label = { Text("📄 Seiten: $pageLabel") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.setFilterPageFilter("ALL") }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Aktiver Zeitraum-Filter
                    if (filterDateRange != "ALL") {
                        val dateLabel = when (filterDateRange) {
                            "TODAY" -> "Heute"
                            "WEEK" -> "Letzte 7 Tage"
                            "MONTH" -> "Letzte 30 Tage"
                            "YEAR" -> "Dieses Jahr"
                            else -> filterDateRange
                        }
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.DATE_RANGE },
                                label = { Text("📅 $dateLabel") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.setFilterDateRange("ALL") }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Aktiver Sortier-Filter (falls abweichend von Standard)
                    if (filterSortBy != "DATE_DESC") {
                        val sortLabel = when (filterSortBy) {
                            "DATE_ASC" -> "Älteste zuerst"
                            "TITLE_ASC" -> "Titel A-Z"
                            "TITLE_DESC" -> "Titel Z-A"
                            "PAGES_DESC" -> "Meiste Seiten"
                            else -> "Sortierung"
                        }
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.SORT },
                                label = { Text("↕️ $sortLabel") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.setSortBy("DATE_DESC") }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // 6. Zusatzfeld Filter Chip
                    if (filterCustomFieldId != null) {
                        val activeField = customFields.firstOrNull { it.id == filterCustomFieldId }
                        val label = "${activeField?.name ?: "Zusatzfeld"}${if (!filterCustomFieldValue.isNullOrBlank()) ": $filterCustomFieldValue" else ""}"
                        item {
                            InputChip(
                                selected = true,
                                onClick = { currentFilterSheet = ActiveFilterSheet.CUSTOM_FIELD },
                                label = { Text("📋 $label") },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen",
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.setFilterCustomField(null, null) }
                                    )
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }

                    // Alle zurücksetzen
                    if (activeFilterCount > 0) {
                        item {
                            TextButton(
                                onClick = { viewModel.resetAdvancedFilters() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Zurücksetzen", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

                Spacer(modifier = Modifier.height(8.dp))

                // Sub-Reiter: Ordner, Fristen, Papierkorb (3 Tabs fest nebeneinander)
                TabRow(
                    selectedTabIndex = activeSubTab.coerceAtMost(2),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().testTag("dms_explorer_tab_row")
                ) {
                    Tab(
                        selected = activeSubTab == 0,
                        onClick = { activeSubTab = 0 },
                        text = {
                            Text(
                                text = "Ordner",
                                maxLines = 1,
                                fontSize = 12.sp,
                                fontWeight = if (activeSubTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tab_dms_folders")
                    )
                    Tab(
                        selected = activeSubTab == 1,
                        onClick = { activeSubTab = 1 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Fristen",
                                    maxLines = 1,
                                    fontSize = 12.sp,
                                    fontWeight = if (activeSubTab == 1) FontWeight.Bold else FontWeight.Medium
                                )
                                if (contractDeadlines.isNotEmpty()) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("${contractDeadlines.size}", fontSize = 9.sp)
                                    }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tab_dms_deadlines")
                    )
                    Tab(
                        selected = activeSubTab == 2,
                        onClick = { activeSubTab = 2 },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Papierkorb",
                                    maxLines = 1,
                                    fontSize = 12.sp,
                                    fontWeight = if (activeSubTab == 2) FontWeight.Bold else FontWeight.Medium
                                )
                                if (trashCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("$trashCount", color = MaterialTheme.colorScheme.onError, fontSize = 9.sp)
                                    }
                                }
                            }
                        },
                        icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("tab_dms_trash")
                    )
                }

                // Speicherplatz-Ausnutzung Leiste (Klickbar für Detail-Übersicht)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable { isStorageOverviewExpanded = !isStorageOverviewExpanded }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "App-Speicher: ${storageUsage.totalAppFormatted} (${storageUsage.totalDocumentCount} Dok. • ${storageUsage.totalPagesCount} Seiten)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = if (isStorageOverviewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isStorageOverviewExpanded && storageUsage.folderUsageList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Speicherplatz nach Ordnern (Ebene 1):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            storageUsage.folderUsageList.forEach { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (folder.mainCategoryId.isNotBlank()) "${folder.mainCategoryId}_${folder.mainCategory}" else folder.mainCategory,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${folder.documentCount} Dok. • ${folder.formattedSize}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // HAUPTBEREICH: DOKUMENTE & BAUMSTRUKTUR
        if (searchQuery.isNotBlank() || activeFilterCount > 0) {
            // Direkte Trefferliste für schnelle Übersicht bei aktiver Suche
            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Keine passenden Dokumente gefunden",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Überprüfe deine Suchbegriffe oder passe die aktiven Filter an.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    item(key = "search_header_summary") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${documents.size} Treffer gefunden",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = when (filterSortBy) {
                                        "DATE_ASC" -> "Älteste zuerst"
                                        "TITLE_ASC" -> "Titel A-Z"
                                        "TITLE_DESC" -> "Titel Z-A"
                                        "PAGES_DESC" -> "Meiste Seiten"
                                        else -> "Neueste zuerst"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    items(documents, key = { it.id }) { doc ->
                        DocumentCard(
                            document = doc,
                            searchQuery = searchQuery,
                            onClick = { selectedDocumentForDetail = doc }
                        )
                    }
                }
            }
        } else if (activeSubTab == 0) {
            // 1. ORDNER STRUKTUR (Ebene 1 Hauptkategorie / Ebene 2 Unterordner)
            // Kombiniert alle Kategorien aus Dokumenten und definierten Templates
            val allMainCats = remember(allDocs, templates) {
                (allDocs.map { it.mainCategory } + templates.map { it.mainCategory }).filter { it.isNotBlank() }.distinct().sorted()
            }

            // Vorabberechnung der Ordner- und Unterordnergrößen
            val folderSizes = remember(allDocs) {
                val mainSizes = mutableMapOf<String, String>()
                val subSizes = mutableMapOf<String, String>()

                fun formatBytes(bytes: Long): String = when {
                    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
                    bytes >= 1024 -> "${bytes / 1024} KB"
                    else -> "$bytes B"
                }

                allDocs.groupBy { it.mainCategory }.forEach { (mainCat, mainDocs) ->
                    val mainBytes = mainDocs.sumOf { doc ->
                        val f = File(doc.filePath)
                        if (f.exists()) f.length() else 0L
                    }
                    mainSizes[mainCat] = formatBytes(mainBytes)

                    mainDocs.groupBy { it.subCategory }.forEach { (subCat, subDocs) ->
                        val subBytes = subDocs.sumOf { doc ->
                            val f = File(doc.filePath)
                            if (f.exists()) f.length() else 0L
                        }
                        subSizes["${mainCat}_$subCat"] = formatBytes(subBytes)
                    }
                }
                Pair(mainSizes, subSizes)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Aktionsleiste für Ordner Struktur
                item(key = "folder_structure_action_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ordner Struktur",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FilledTonalButton(
                            onClick = {
                                parentForNewSubFolder = null
                                showCreateFolderDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_create_main_folder")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Neuer Ordner", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (allMainCats.isEmpty()) {
                    item(key = "empty_folder_state") {
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
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Noch keine Ordner vorhanden",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Erstelle jetzt deinen ersten Hauptordner für strukturierte Ablage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        parentForNewSubFolder = null
                                        showCreateFolderDialog = true
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Hauptordner anlegen")
                                }
                            }
                        }
                    }
                }

                allMainCats.forEach { mainCat ->
                    val mainDocs = allDocs.filter { it.mainCategory == mainCat }
                    val mainTmpls = templates.filter { it.mainCategory == mainCat }
                    val mainPrefix = mainDocs.firstOrNull { it.mainCategoryId.isNotBlank() }?.mainCategoryId
                        ?: mainTmpls.firstOrNull { it.mainCategoryId.isNotBlank() }?.mainCategoryId.orEmpty()
                    val mainDocsSizeFormatted = folderSizes.first[mainCat] ?: "0 B"

                    val matchedFolder = foldersHierarchy.find { it.name.equals(mainCat, ignoreCase = true) }
                    val folderIcon = matchedFolder?.iconName ?: ""
                    val folderLogo = matchedFolder?.companyLogo ?: ""
                    val folderCustomLogoUri = matchedFolder?.customLogoUri ?: ""

                    item(key = "header_$mainCat") {
                        var showMainCatMenu by remember { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    com.example.ui.components.DocumentOrFolderIcon(
                                        iconName = folderIcon,
                                        companyLogo = folderLogo,
                                        customLogoUri = folderCustomLogoUri,
                                        isFolder = true,
                                        size = 26.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    if (mainPrefix.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = mainPrefix,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = mainCat,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${mainDocs.size} ($mainDocsSizeFormatted)",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { showMainCatMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "Ordner-Aktionen",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showMainCatMenu,
                                        onDismissRequest = { showMainCatMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Unterordner erstellen") },
                                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                            onClick = {
                                                showMainCatMenu = false
                                                parentForNewSubFolder = mainCat
                                                showCreateFolderDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Ordner umbenennen") },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                showMainCatMenu = false
                                                folderToRename = Pair(mainCat, null)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Icon & Logo anpassen") },
                                            leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                                            onClick = {
                                                showMainCatMenu = false
                                                folderToChangeIconAndLogo = Pair(mainCat, null)
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("Ordner löschen", color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                showMainCatMenu = false
                                                folderToDelete = Pair(mainCat, null)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Unterordner ermitteln
                    val subCats = (mainDocs.map { it.subCategory } + mainTmpls.map { it.subCategory })
                        .filter { it.isNotBlank() }.distinct().sorted()

                    if (subCats.isEmpty()) {
                        item(key = "sub_empty_$mainCat") {
                            Text(
                                text = "Keine Unterordner vorhanden. Klicke auf '...' um einen Unterordner anzulegen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(start = 24.dp, top = 2.dp, bottom = 4.dp)
                            )
                        }
                    }

                    subCats.forEach { subCat ->
                        val subDocs = mainDocs.filter { it.subCategory == subCat }
                        val subTmpls = mainTmpls.filter { it.subCategory == subCat }
                        val subPrefix = subDocs.firstOrNull { it.subCategoryId.isNotBlank() }?.subCategoryId
                            ?: subTmpls.firstOrNull { it.subCategoryId.isNotBlank() }?.subCategoryId.orEmpty()
                        val subSizeFormatted = folderSizes.second["${mainCat}_$subCat"] ?: "0 B"

                        val matchedSubFolder = matchedFolder?.subFolders?.find { it.name.equals(subCat, ignoreCase = true) }
                        val subFolderIcon = matchedSubFolder?.iconName ?: ""
                        val subFolderLogo = matchedSubFolder?.companyLogo ?: ""
                        val subFolderCustomLogoUri = matchedSubFolder?.customLogoUri ?: ""

                        item(key = "sub_${mainCat}_$subCat") {
                            var showSubCatMenu by remember { mutableStateOf(false) }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "↳ ",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        com.example.ui.components.DocumentOrFolderIcon(
                                            iconName = subFolderIcon,
                                            companyLogo = subFolderLogo,
                                            customLogoUri = subFolderCustomLogoUri,
                                            isFolder = true,
                                            size = 22.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (subPrefix.isNotBlank()) "$subPrefix-$subCat" else subCat,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${subDocs.size} Dok. • $subSizeFormatted",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { showSubCatMenu = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Unterordner-Aktionen",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showSubCatMenu,
                                            onDismissRequest = { showSubCatMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Unterordner umbenennen") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = {
                                                    showSubCatMenu = false
                                                    folderToRename = Pair(mainCat, subCat)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Icon & Logo anpassen") },
                                                leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
                                                onClick = {
                                                    showSubCatMenu = false
                                                    folderToChangeIconAndLogo = Pair(mainCat, subCat)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Unterordner löschen", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showSubCatMenu = false
                                                    folderToDelete = Pair(mainCat, subCat)
                                                }
                                            )
                                        }
                                    }
                                }

                                if (subDocs.isEmpty()) {
                                    Text(
                                        text = "Dieser Unterordner ist leer.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                } else {
                                    subDocs.forEach { doc ->
                                        DocumentCard(
                                            document = doc,
                                            searchQuery = searchQuery,
                                            onClick = { selectedDocumentForDetail = doc }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (activeSubTab == 1) {
            // 2. FRISTEN- & KALENDER-ZENTRALE
            com.example.ui.views.dms.DmsDeadlinesPane(
                documentsWithDeadlines = contractDeadlines,
                calendarIntegrationMode = calendarIntegrationMode,
                onSelectDocument = { selectedDocumentForDetail = it },
                onSetCalendarMode = { viewModel.setCalendarIntegrationMode(it) }
            )
        } else if (activeSubTab == 2) {
            // 3. MÜLLEIMER / PAPIERKORB
            com.example.ui.views.dms.DmsTrashPane(
                trashDocuments = trashDocs,
                trashRetentionDays = trashRetentionDays,
                onRestoreDocument = { viewModel.restoreDocument(it) },
                onPermanentlyDeleteDocument = { viewModel.permanentlyDeleteDocument(it) },
                onEmptyTrash = { viewModel.emptyTrash() },
                onSetRetentionDays = { viewModel.setTrashRetentionDays(it) }
            )
        }
    }

    // MODAL-SHEET: FILTER-DIMENSION AUSWÄHLEN (Progressive Disclosure Auswahl)
    if (currentFilterSheet == ActiveFilterSheet.SELECT_DIMENSION) {
        ModalBottomSheet(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter auswählen & anpassen",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (activeFilterCount > 0) {
                        TextButton(
                            onClick = {
                                viewModel.resetAdvancedFilters()
                                currentFilterSheet = ActiveFilterSheet.NONE
                            }
                        ) {
                            Text("Alle zurücksetzen", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Ordner
                FilterCategoryTile(
                    icon = Icons.Default.Folder,
                    title = "Ordner / Kategorie",
                    currentValue = if (filterMainCategory != null) "$filterMainCategory" + (filterSubCategory?.let { " / $it" } ?: "") else "Alle Ordner",
                    isActive = filterMainCategory != null,
                    onClick = { currentFilterSheet = ActiveFilterSheet.CATEGORY }
                )

                // 2. Dokumenten-Gruppen
                FilterCategoryTile(
                    icon = Icons.Default.Bookmarks,
                    title = "Dokumenten-Gruppen",
                    currentValue = filterDocType ?: "Alle Gruppen",
                    isActive = filterDocType != null,
                    onClick = { currentFilterSheet = ActiveFilterSheet.DOC_TYPE }
                )

                // 3. Seitenanzahl
                FilterCategoryTile(
                    icon = Icons.Default.FilterFrames,
                    title = "Seitenanzahl",
                    currentValue = when (filterPageFilter) {
                        "SINGLE" -> "Nur 1 Seite"
                        "MULTI" -> "Mehrseitig (≥2)"
                        "3_PLUS" -> "≥3 Seiten"
                        "5_PLUS" -> "≥5 Seiten"
                        else -> "Alle Seiten"
                    },
                    isActive = filterPageFilter != "ALL",
                    onClick = { currentFilterSheet = ActiveFilterSheet.PAGE_COUNT }
                )

                // 4. Zeitraum
                FilterCategoryTile(
                    icon = Icons.Default.DateRange,
                    title = "Zeitraum / Datum",
                    currentValue = when (filterDateRange) {
                        "TODAY" -> "Heute"
                        "WEEK" -> "Letzte 7 Tage"
                        "MONTH" -> "Letzte 30 Tage"
                        "YEAR" -> "Dieses Jahr"
                        else -> "Gesamter Zeitraum"
                    },
                    isActive = filterDateRange != "ALL",
                    onClick = { currentFilterSheet = ActiveFilterSheet.DATE_RANGE }
                )

                // 5. Sortierung
                FilterCategoryTile(
                    icon = Icons.Default.Sort,
                    title = "Sortierung",
                    currentValue = when (filterSortBy) {
                        "DATE_ASC" -> "Älteste zuerst"
                        "TITLE_ASC" -> "Titel A-Z"
                        "TITLE_DESC" -> "Titel Z-A"
                        "PAGES_DESC" -> "Meiste Seiten"
                        else -> "Neueste zuerst"
                    },
                    isActive = filterSortBy != "DATE_DESC",
                    onClick = { currentFilterSheet = ActiveFilterSheet.SORT }
                )

                // 6. Zusatzfelder
                val activeCustomField = customFields.firstOrNull { it.id == filterCustomFieldId }
                FilterCategoryTile(
                    icon = Icons.Default.Category,
                    title = "Zusatzfeld-Filter",
                    currentValue = if (activeCustomField != null) "${activeCustomField.name}${if (!filterCustomFieldValue.isNullOrBlank()) ": $filterCustomFieldValue" else ""}" else "Alle Zusatzfelder",
                    isActive = filterCustomFieldId != null,
                    onClick = { currentFilterSheet = ActiveFilterSheet.CUSTOM_FIELD }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // SPEZIFISCHER FILTER-DIALOG: ORDNER
    if (currentFilterSheet == ActiveFilterSheet.CATEGORY) {
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Ordner-Filter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hauptordner wählen:", style = MaterialTheme.typography.labelMedium)
                    Surface(
                        onClick = {
                            viewModel.setFilterMainCategory(null)
                            viewModel.setFilterSubCategory(null)
                            currentFilterSheet = ActiveFilterSheet.NONE
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (filterMainCategory == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Alle Ordner (Filter entfernen)", modifier = Modifier.padding(12.dp))
                    }
                    availableMainCategories.forEach { cat ->
                        Surface(
                            onClick = {
                                viewModel.setFilterMainCategory(cat)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterMainCategory == cat) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📁 $cat", modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // SPEZIFISCHER FILTER-DIALOG: DOKUMENT-TYP
    if (currentFilterSheet == ActiveFilterSheet.DOC_TYPE) {
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Dokument-Typ Filter") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            viewModel.setFilterDocType(null)
                            currentFilterSheet = ActiveFilterSheet.NONE
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (filterDocType == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Alle Dokument-Typen", modifier = Modifier.padding(12.dp))
                    }
                    docTypes.forEach { dt ->
                        Surface(
                            onClick = {
                                viewModel.setFilterDocType(dt.name)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterDocType == dt.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(dt.colorHex))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(dt.name)
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            viewModel.setFilterDocType("__NONE__")
                            currentFilterSheet = ActiveFilterSheet.NONE
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (filterDocType == "__NONE__") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Ohne Dokument-Typ", modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // SPEZIFISCHER FILTER-DIALOG: SEITENANZAHL
    if (currentFilterSheet == ActiveFilterSheet.PAGE_COUNT) {
        val options = listOf(
            "ALL" to "Alle Seiten anzeigen",
            "SINGLE" to "Nur 1 Seite (Einzelseiter)",
            "MULTI" to "Mehrseitig (≥2 Seiten)",
            "3_PLUS" to "Mindestens 3 Seiten",
            "5_PLUS" to "Mindestens 5 Seiten"
        )
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Seitenanzahl") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (key, label) ->
                        Surface(
                            onClick = {
                                viewModel.setFilterPageFilter(key)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterPageFilter == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // SPEZIFISCHER FILTER-DIALOG: ZEITRAUM
    if (currentFilterSheet == ActiveFilterSheet.DATE_RANGE) {
        val dateOptions = listOf(
            "ALL" to "Gesamter Zeitraum",
            "TODAY" to "Heute",
            "WEEK" to "Letzte 7 Tage",
            "MONTH" to "Letzte 30 Tage",
            "YEAR" to "Dieses Jahr"
        )
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Zeitraum") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dateOptions.forEach { (key, label) ->
                        Surface(
                            onClick = {
                                viewModel.setFilterDateRange(key)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterDateRange == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // SPEZIFISCHER FILTER-DIALOG: SORTIERUNG
    if (currentFilterSheet == ActiveFilterSheet.SORT) {
        val sortOptions = listOf(
            "DATE_DESC" to "Neueste zuerst",
            "DATE_ASC" to "Älteste zuerst",
            "TITLE_ASC" to "Titel A-Z",
            "TITLE_DESC" to "Titel Z-A",
            "PAGES_DESC" to "Meiste Seiten"
        )
        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = { Text("Sortierung") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sortOptions.forEach { (key, label) ->
                        Surface(
                            onClick = {
                                viewModel.setSortBy(key)
                                currentFilterSheet = ActiveFilterSheet.NONE
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (filterSortBy == key) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { currentFilterSheet = ActiveFilterSheet.NONE }) {
                    Text("Fertig")
                }
            }
        )
    }

    // SPEZIFISCHER FILTER-DIALOG: ZUSATZFELDER
    if (currentFilterSheet == ActiveFilterSheet.CUSTOM_FIELD) {
        var selectedFieldForFilter by remember { mutableStateOf<com.example.model.CustomFieldEntity?>(customFields.firstOrNull { it.id == filterCustomFieldId } ?: customFields.firstOrNull()) }
        var filterValueInput by remember { mutableStateOf(filterCustomFieldValue ?: "") }

        AlertDialog(
            onDismissRequest = { currentFilterSheet = ActiveFilterSheet.NONE },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Zusatzfeld-Filter")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Wähle ein Zusatzfeld aus, nach dem gefiltert werden soll:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (customFields.isEmpty()) {
                        Text("Keine Zusatzfelder vorhanden. Diese können in den Einstellungen angelegt oder automatisch per KI erkannt werden.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            customFields.forEach { field ->
                                val isSelected = selectedFieldForFilter?.id == field.id
                                Surface(
                                    onClick = { selectedFieldForFilter = field },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(field.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                        Text(field.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        selectedFieldForFilter?.let { field ->
                            HorizontalDivider()
                            Text("Wert filtern (optional):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                            if (field.type == com.example.model.CustomFieldType.SELECTION && field.options.isNotBlank()) {
                                val opts = field.options.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    opts.forEach { opt ->
                                        val isChosen = filterValueInput == opt
                                        FilterChip(
                                            selected = isChosen,
                                            onClick = { filterValueInput = if (isChosen) "" else opt },
                                            label = { Text(opt, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = filterValueInput,
                                    onValueChange = { filterValueInput = it },
                                    label = { Text("Filterwert (leer = alle mit diesem Feld)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            },
            dismissButton = {
                if (filterCustomFieldId != null) {
                    TextButton(onClick = {
                        viewModel.setFilterCustomField(null, null)
                        currentFilterSheet = ActiveFilterSheet.NONE
                    }) {
                        Text("Filter löschen", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    selectedFieldForFilter?.let { field ->
                        viewModel.setFilterCustomField(field.id, filterValueInput.ifBlank { null })
                    }
                    currentFilterSheet = ActiveFilterSheet.NONE
                }) {
                    Text("Anwenden")
                }
            }
        )
    }

    // DETAIL-MODAL FÜR EIN DOKUMENT
    selectedDocumentForDetail?.let { doc ->
        DocumentDetailModal(
            document = doc,
            availableDocTypes = docTypes,
            allCustomFields = customFields,
            documentCustomFieldValues = allCustomFieldValues,
            onUpdateCustomFieldValue = { docId, fieldId, value ->
                viewModel.setDocumentCustomFieldValue(docId, fieldId, value)
            },
            onUpdateDocType = { docId, newType ->
                viewModel.updateDocumentDocType(docId, newType)
            },
            onDelete = { d ->
                viewModel.deleteDocument(d)
            },
            onUpdateContractReminder = { docId, endDate, cancelDate, remDays, hasRem, eventType, notes, amt ->
                viewModel.updateContractReminders(docId, endDate, cancelDate, remDays, hasRem, eventType, notes, amt)
            },
            onUpdateDocumentIconAndLogo = { docId, iconName, companyLogo ->
                viewModel.updateDocumentIconAndLogo(docId, iconName, companyLogo)
            },
            onRunSecondRun = { docId, correction ->
                viewModel.runSecondRunAnalysis(docId, correction) {
                    selectedDocumentForDetail = viewModel.allDocuments.value.find { it.id == docId }
                }
            },
            onReRunFirstRun = { docId ->
                viewModel.reRunFirstRunTriage(docId) {
                    selectedDocumentForDetail = viewModel.allDocuments.value.find { it.id == docId }
                }
            },
            isAiAnalyzing = isSecondRunGenerating,
            lastAiResult = lastAiAnalysisResult[doc.id],
            onDismissRequest = { selectedDocumentForDetail = null }
        )
    }

    // DIALOG: NEUER ORDNER / UNTERORDNER
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            parentCategory = parentForNewSubFolder,
            existingMainCategories = availableMainCategories,
            prefixesEnabled = pdfSettings.useIdPrefixes,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { mainCat, mainPrefix, subCat, subPrefix ->
                viewModel.saveFolderStructure(mainCat, mainPrefix, subCat, subPrefix)
                showCreateFolderDialog = false
            }
        )
    }

    // DIALOG: ORDNER / UNTERORDNER UMBENENNEN (Präfix bleibt geschützt)
    folderToRename?.let { (mainCat, subCat) ->
        val isSub = subCat != null
        val currentPrefix = if (isSub) {
            allDocs.firstOrNull { it.mainCategory == mainCat && it.subCategory == subCat }?.subCategoryId
                ?: templates.firstOrNull { it.mainCategory == mainCat && it.subCategory == subCat }?.subCategoryId.orEmpty()
        } else {
            allDocs.firstOrNull { it.mainCategory == mainCat }?.mainCategoryId
                ?: templates.firstOrNull { it.mainCategory == mainCat }?.mainCategoryId.orEmpty()
        }
        val currentName = subCat ?: mainCat

        RenameFolderDialog(
            currentName = currentName,
            prefix = currentPrefix,
            isSubCategory = isSub,
            onDismiss = { folderToRename = null },
            onConfirm = { newName ->
                if (isSub && subCat != null) {
                    viewModel.renameSubCategory(mainCat, subCat, newName)
                } else {
                    viewModel.renameMainCategory(mainCat, newName)
                }
                folderToRename = null
            }
        )
    }

    // DIALOG: ORDNER LÖSCHEN (MIT DOPPELTER SICHERHEITSABFRAGE WENN INHALTE EXISTIEREN)
    folderToDelete?.let { (mainCat, subCat) ->
        val isSub = subCat != null
        val affectedDocs = if (isSub) {
            allDocs.filter { it.mainCategory == mainCat && it.subCategory == subCat }
        } else {
            allDocs.filter { it.mainCategory == mainCat }
        }
        val folderDisplayName = if (isSub) "$mainCat ↳ $subCat" else mainCat

        DeleteFolderDialog(
            folderName = folderDisplayName,
            docCount = affectedDocs.size,
            onDismiss = { folderToDelete = null },
            onConfirm = {
                viewModel.deleteFolderStructure(mainCat, subCat, deleteContainedDocuments = true)
                folderToDelete = null
            }
        )
    }

    // DIALOG: ORDNER-SYMBOL & FIRMENLOGO ANPASSEN (INKLUSIVE BILD-UPLOAD AUS GALERIE)
    folderToChangeIconAndLogo?.let { (mainCat, subCat) ->
        val isSub = subCat != null
        val matchedFolderItem = foldersHierarchy.find { it.name.equals(mainCat, ignoreCase = true) || it.id.equals(mainCat, ignoreCase = true) }
        val currentIcon = if (isSub) {
            matchedFolderItem?.subFolders?.find { it.name.equals(subCat, ignoreCase = true) || it.id.equals(subCat, ignoreCase = true) }?.iconName ?: ""
        } else {
            matchedFolderItem?.iconName ?: ""
        }
        val currentLogo = if (isSub) {
            matchedFolderItem?.subFolders?.find { it.name.equals(subCat, ignoreCase = true) || it.id.equals(subCat, ignoreCase = true) }?.companyLogo ?: ""
        } else {
            matchedFolderItem?.companyLogo ?: ""
        }
        val currentCustomLogoUri = if (isSub) {
            matchedFolderItem?.subFolders?.find { it.name.equals(subCat, ignoreCase = true) || it.id.equals(subCat, ignoreCase = true) }?.customLogoUri ?: ""
        } else {
            matchedFolderItem?.customLogoUri ?: ""
        }
        val folderDisplayName = if (isSub) "$mainCat ↳ $subCat" else mainCat

        com.example.ui.components.UniversalIconAndLogoPickerDialog(
            title = "Ordner-Symbol & Logo",
            subtitle = folderDisplayName,
            currentIcon = currentIcon,
            currentLogo = currentLogo,
            currentCustomLogoUri = currentCustomLogoUri,
            isFolder = true,
            onSave = { iconName, companyLogo, customLogoUri ->
                viewModel.updateFolderIconAndLogo(mainCat, subCat, iconName, companyLogo, customLogoUri)
                folderToChangeIconAndLogo = null
            },
            onDismiss = { folderToChangeIconAndLogo = null }
        )
    }

    // DIALOG: ZUSATZFELD ANLEGEN ODER BEARBEITEN
    if (showCreateCustomFieldDialog || fieldToEdit != null) {
        CreateOrEditCustomFieldDialog(
            existingField = fieldToEdit,
            availableMainCategories = availableMainCategories,
            allCustomFields = customFields,
            onDismiss = {
                showCreateCustomFieldDialog = false
                fieldToEdit = null
            },
            onConfirm = { field ->
                if (fieldToEdit != null) {
                    viewModel.updateCustomField(field)
                } else {
                    viewModel.addCustomField(field)
                }
                showCreateCustomFieldDialog = false
                fieldToEdit = null
            }
        )
    }

    // DIALOG: NEUE DOKUMENTEN-GRUPPE ANLEGEN
    if (showCreateDocTypeDialog) {
        CreateDocTypeDialog(
            onDismiss = { showCreateDocTypeDialog = false },
            onConfirm = { name, prefix, colorHex ->
                viewModel.addDocType(DocTypeItem(name = name, description = prefix, colorHex = colorHex))
                showCreateDocTypeDialog = false
            }
        )
    }
}

@Composable
private fun FilterCategoryTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    currentValue: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isActive)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun DocTypeTreeNodeHeader(
    docTypeName: String,
    docCount: Int,
    isExpanded: Boolean,
    colorHex: Long,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "chevron"
    )

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("tree_node_$docTypeName")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = if (isExpanded) "Zuklappen" else "Aufklappen",
                modifier = Modifier
                    .size(22.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(colorHex))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = docTypeName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isExpanded) "Tippen zum Zuklappen" else "Tippen zum Aufklappen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "$docCount Dok.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DocumentCard(
    document: DocumentEntity,
    searchQuery: String = "",
    onClick: () -> Unit
) {
    val isImage = document.fileName.endsWith(".jpg", ignoreCase = true) ||
            document.fileName.endsWith(".png", ignoreCase = true)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("document_card_${document.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.example.ui.components.DocumentOrFolderIcon(
                iconName = document.customIcon,
                companyLogo = document.companyLogo,
                isFolder = false,
                isImage = isImage,
                size = 44.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))

                val metadataLine = buildString {
                    append("📁 ")
                    append(document.mainCategory)
                    if (document.subCategory.isNotBlank()) {
                        append(" / ")
                        append(document.subCategory)
                    }
                    append(" • ")
                    if (isImage) {
                        append("Bild-Datei")
                    } else if (document.pageCount > 1) {
                        append("${document.pageCount} Seiten")
                    } else {
                        append("1 Seite")
                    }
                    append(" • ")
                    append(formatDate(document.createdAt))
                }

                Text(
                    text = metadataLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // OCR-Treffer Vorschau bei Volltextsuche
                if (searchQuery.isNotBlank() && document.ocrText.isNotBlank()) {
                    val tokens = searchQuery.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
                    val matchedToken = tokens.firstOrNull { document.ocrText.contains(it, ignoreCase = true) }
                    if (matchedToken != null) {
                        val idx = document.ocrText.indexOf(matchedToken, ignoreCase = true)
                        val start = (idx - 25).coerceAtLeast(0)
                        val end = (idx + matchedToken.length + 35).coerceAtMost(document.ocrText.length)
                        val snippet = document.ocrText.substring(start, end).replace("\n", " ").trim()

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🔍 Treffer im Text: „…$snippet…“",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (document.docType.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = document.docType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isImage) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "JPG",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (document.pageCount > 1) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "${document.pageCount} Seiten",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = document.fileSizeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
    return sdf.format(Date(timestamp))
}

// Note: Folder dialogs (CreateFolderDialog, RenameFolderDialog, DeleteFolderDialog, CreateDocTypeDialog) extracted to dms/ subpackage.


// Note: CalendarAndDeadlinesTab, TrashRecycleBinTab and CustomFieldsManagementTab extracted to dms/ subpackage.


// Note: CreateOrEditCustomFieldDialog extracted to dms/ subpackage.

