package com.example.ui.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppViewLevel
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.settings.*

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

data class SettingsTabItem(val title: String, val icon: ImageVector)

@Composable
fun CustomFieldsSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val customFields by viewModel.customFields.collectAsStateWithLifecycle()
    val allCustomFieldValues by viewModel.allDocumentCustomFieldValues.collectAsStateWithLifecycle()
    val allDocuments by viewModel.allDocuments.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val availableMainCategories = remember(allDocuments, templates) {
        (allDocuments.map { it.mainCategory } + templates.map { it.mainCategory }).filter { it.isNotBlank() }.distinct().sorted()
    }

    var showCreateCustomFieldDialog by remember { mutableStateOf(false) }
    var editingFieldForDialog by remember { mutableStateOf<com.example.model.CustomFieldEntity?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        com.example.ui.views.dms.DmsCustomFieldsPane(
            customFields = customFields,
            customFieldValues = allCustomFieldValues,
            documents = allDocuments,
            availableMainCategories = availableMainCategories,
            onAddFieldClick = {
                editingFieldForDialog = null
                showCreateCustomFieldDialog = true
            },
            onEditField = { field ->
                editingFieldForDialog = field
                showCreateCustomFieldDialog = true
            },
            onDeleteField = { fieldId -> viewModel.deleteCustomField(fieldId) },
            onSelectDocument = { _ -> }
        )

        if (showCreateCustomFieldDialog) {
            com.example.ui.views.dms.CreateOrEditCustomFieldDialog(
                existingField = editingFieldForDialog,
                availableMainCategories = availableMainCategories,
                onDismiss = { showCreateCustomFieldDialog = false },
                onConfirm = { field ->
                    if (editingFieldForDialog != null) {
                        viewModel.updateCustomField(field)
                    } else {
                        viewModel.addCustomField(field)
                    }
                    showCreateCustomFieldDialog = false
                }
            )
        }
    }
}

data class SettingsTabSpec(
    val originalIndex: Int,
    val title: String,
    val icon: ImageVector,
    val minViewLevel: AppViewLevel
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSettingsTab by remember { mutableIntStateOf(3) }
    val appViewLevel by viewModel.appViewLevel.collectAsState()
    val targetSettingsSubTab by viewModel.targetSettingsSubTab.collectAsState()

    val allTabs = remember {
        listOf(
            SettingsTabSpec(0, "Dokumenten-Regeln", Icons.Default.Rule, AppViewLevel.ADVANCED),
            SettingsTabSpec(1, "Zusatzfelder", Icons.Default.Category, AppViewLevel.ADVANCED),
            SettingsTabSpec(2, "Lokaler KI Agent", Icons.Default.Psychology, AppViewLevel.ADVANCED),
            SettingsTabSpec(3, "Module & Scan", Icons.Default.Extension, AppViewLevel.STANDARD),
            SettingsTabSpec(4, "Sicherheit", Icons.Default.Security, AppViewLevel.STANDARD),
            SettingsTabSpec(5, "WLAN & Dual-Sync", Icons.Default.Devices, AppViewLevel.EXPERT),
            SettingsTabSpec(6, "Cloud & Backup", Icons.Default.CloudSync, AppViewLevel.STANDARD),
            SettingsTabSpec(7, "Design & Ansicht", Icons.Default.Palette, AppViewLevel.STANDARD),
            SettingsTabSpec(8, "Gesten & Barrierefreiheit", Icons.Default.AccessibilityNew, AppViewLevel.EXPERT),
            SettingsTabSpec(9, "Entwickler & Hilfe", Icons.Default.BugReport, AppViewLevel.EXPERT)
        )
    }

    val visibleTabs = remember(appViewLevel) {
        allTabs.filter { tab ->
            when (appViewLevel) {
                AppViewLevel.STANDARD -> tab.minViewLevel == AppViewLevel.STANDARD
                AppViewLevel.ADVANCED -> tab.minViewLevel == AppViewLevel.STANDARD || tab.minViewLevel == AppViewLevel.ADVANCED
                AppViewLevel.EXPERT -> true
            }
        }
    }

    LaunchedEffect(targetSettingsSubTab) {
        targetSettingsSubTab?.let { subTab ->
            val requiredTab = allTabs.find { it.originalIndex == subTab }
            if (requiredTab != null) {
                if (appViewLevel == AppViewLevel.STANDARD && requiredTab.minViewLevel != AppViewLevel.STANDARD) {
                    viewModel.setAppViewLevel(requiredTab.minViewLevel)
                } else if (appViewLevel == AppViewLevel.ADVANCED && requiredTab.minViewLevel == AppViewLevel.EXPERT) {
                    viewModel.setAppViewLevel(AppViewLevel.EXPERT)
                }
            }
            selectedSettingsTab = subTab
            viewModel.setTargetSettingsSubTab(null)
        }
    }

    LaunchedEffect(appViewLevel) {
        if (visibleTabs.none { it.originalIndex == selectedSettingsTab }) {
            selectedSettingsTab = visibleTabs.firstOrNull()?.originalIndex ?: 3
        }
    }

    val selectedVisibleIndex = remember(selectedSettingsTab, visibleTabs) {
        val idx = visibleTabs.indexOfFirst { it.originalIndex == selectedSettingsTab }
        if (idx >= 0) idx else 0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_view")
    ) {
        // Schlanke, perfekt formatierte Leiste für den Anzeigemodus
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Ansicht: ${when (appViewLevel) {
                            AppViewLevel.STANDARD -> "Standard (4 Tabs)"
                            AppViewLevel.ADVANCED -> "Erweitert (7 Tabs)"
                            AppViewLevel.EXPERT -> "Experte (10 Tabs)"
                        }}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        AppViewLevel.STANDARD to "Basis",
                        AppViewLevel.ADVANCED to "Erweitert",
                        AppViewLevel.EXPERT to "Experte"
                    ).forEach { (level, label) ->
                        val isSelected = appViewLevel == level
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = BorderStroke(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            onClick = { viewModel.setAppViewLevel(level) },
                            modifier = Modifier.height(26.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedVisibleIndex,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            visibleTabs.forEachIndexed { visibleIdx, tab ->
                Tab(
                    selected = selectedVisibleIndex == visibleIdx,
                    onClick = { selectedSettingsTab = tab.originalIndex },
                    text = { Text(tab.title, fontSize = 12.sp) },
                    icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (selectedSettingsTab) {
                0 -> com.example.ui.views.rules.DocumentRulesSettingsTab(viewModel)
                1 -> CustomFieldsSettingsTab(viewModel)
                2 -> com.example.ui.views.rules.OnDeviceAiAgentSettingsTab(viewModel)
                3 -> ModuleSettingsTab(viewModel)
                4 -> SecuritySettingsTab(viewModel)
                5 -> P2pSyncTab(viewModel)
                6 -> CloudSyncSettingsTab(viewModel)
                7 -> AppearanceSettingsTab(viewModel)
                8 -> AccessibilityAndGesturesSettingsTab(viewModel)
                9 -> DeveloperSettingsTab(viewModel)
                else -> ModuleSettingsTab(viewModel)
            }
        }
    }
}
