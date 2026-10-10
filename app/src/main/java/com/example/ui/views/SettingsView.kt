package com.example.ui.views

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppViewLevel
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.settings.*

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSettingsTab by remember { mutableStateOf(0) }
    val appViewLevel by viewModel.appViewLevel.collectAsState()
    val targetSettingsSubTab by viewModel.targetSettingsSubTab.collectAsState()

    LaunchedEffect(targetSettingsSubTab) {
        targetSettingsSubTab?.let { subTab ->
            selectedSettingsTab = subTab
            viewModel.setTargetSettingsSubTab(null)
        }
    }

    val tabs = listOf(
        SettingsTabItem("Dokumenten-Regeln", Icons.Default.Rule),
        SettingsTabItem("Zusatzfelder", Icons.Default.Category),
        SettingsTabItem("Lokaler KI Agent", Icons.Default.Psychology),
        SettingsTabItem("Module & Scan", Icons.Default.Extension),
        SettingsTabItem("Sicherheit", Icons.Default.Security),
        SettingsTabItem("WLAN & Dual-Sync", Icons.Default.Devices),
        SettingsTabItem("Cloud & Backup", Icons.Default.CloudSync),
        SettingsTabItem("Design", Icons.Default.Palette),
        SettingsTabItem("Gesten & Barrierefreiheit", Icons.Default.AccessibilityNew),
        SettingsTabItem("Entwickler & Hilfe", Icons.Default.BugReport)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_view")
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedSettingsTab,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedSettingsTab == index,
                    onClick = { selectedSettingsTab = index },
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
