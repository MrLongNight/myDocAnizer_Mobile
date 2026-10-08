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

data class SettingsTabItem(val title: String, val icon: ImageVector)

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
        SettingsTabItem("Lokaler KI Agent", Icons.Default.Psychology),
        SettingsTabItem("Module & Scan", Icons.Default.Extension),
        SettingsTabItem("Sicherheit", Icons.Default.Security),
        SettingsTabItem("Cloud & Sync", Icons.Default.CloudSync),
        SettingsTabItem("Design", Icons.Default.Palette),
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
                1 -> com.example.ui.views.rules.OnDeviceAiAgentSettingsTab(viewModel)
                2 -> ModuleSettingsTab(viewModel)
                3 -> SecuritySettingsTab(viewModel)
                4 -> CloudSyncSettingsTab(viewModel)
                5 -> AppearanceSettingsTab(viewModel)
                6 -> DeveloperSettingsTab(viewModel)
                else -> ModuleSettingsTab(viewModel)
            }
        }
    }
}
