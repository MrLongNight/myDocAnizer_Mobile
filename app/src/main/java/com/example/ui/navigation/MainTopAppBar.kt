package com.example.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppLogoBanner
import com.example.ui.views.MainNavigationTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(
    currentTab: MainNavigationTab,
    onNavigateToDocAnizer: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier.height(84.dp),
        title = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AppLogoBanner(
                    iconHeight = 76.dp,
                    fontSize = 21f,
                    includeContainer = false,
                    showTagline = false
                )
            }
        },
        navigationIcon = {
            if (currentTab == MainNavigationTab.SETTINGS || currentTab == MainNavigationTab.IMPORT) {
                IconButton(
                    onClick = onNavigateToDocAnizer,
                    modifier = Modifier.testTag("btn_top_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück zum Dokumenten Tresor"
                    )
                }
            }
        },
        actions = {
            if (currentTab != MainNavigationTab.SETTINGS) {
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_top_settings")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Einstellungen öffnen",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
