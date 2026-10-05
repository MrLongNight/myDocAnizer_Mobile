package com.example.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.AppLogoBanner
import com.example.ui.components.FirstStepsTourModal

enum class MainNavigationTab {
    SCANNER,
    DOCANIZER,
    DASHBOARD,
    BATCH_INBOX,
    IMPORT,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: DocAnizerViewModel = viewModel()
) {
    val biometricAuthEnabled by viewModel.biometricAuthEnabled.collectAsStateWithLifecycle()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()

    // Wenn Biometrie aktiviert und App noch gesperrt ist, Lock-Screen anzeigen
    if (biometricAuthEnabled && !isAppUnlocked) {
        AppLockScreen(viewModel = viewModel)
        return
    }

    // App startet direkt im Dokumenten Tresor Modus
    var currentTab by rememberSaveable { mutableStateOf(MainNavigationTab.DOCANIZER) }
    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    var showScanImportSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val batchQueue by viewModel.batchQueue.collectAsStateWithLifecycle()
    val isWizardCompleted by viewModel.isWizardCompleted.collectAsStateWithLifecycle()
    val showManualWizard by viewModel.showManualConfigWizard.collectAsStateWithLifecycle()
    var sessionDismissedWizard by rememberSaveable { mutableStateOf(false) }

    // Reiner Gesten-Modus (Immersive Fullscreen)
    val isGestureModeEnabled by viewModel.isGestureModeEnabled.collectAsStateWithLifecycle()

    val targetTab by viewModel.targetNavigationTab.collectAsStateWithLifecycle()
    LaunchedEffect(targetTab) {
        targetTab?.let { tabStr ->
            when (tabStr) {
                "SCANNER" -> currentTab = MainNavigationTab.SCANNER
                "DOCANIZER" -> currentTab = MainNavigationTab.DOCANIZER
                "DASHBOARD" -> currentTab = MainNavigationTab.DASHBOARD
                "BATCH_INBOX" -> currentTab = MainNavigationTab.BATCH_INBOX
                "SETTINGS" -> currentTab = MainNavigationTab.SETTINGS
                "IMPORT" -> currentTab = MainNavigationTab.IMPORT
            }
            viewModel.setTargetNavigationTab(null)
        }
    }

    val p2pPendingRequests by viewModel.p2pPendingPairingRequests.collectAsStateWithLifecycle()
    val p2pConflicts by viewModel.p2pActiveConflicts.collectAsStateWithLifecycle()

    // Animation für das Drehen des Plus-Icons im FAB (0° -> 45° wie ein Schließen-Kreuz)
    val fabRotation by animateFloatAsState(
        targetValue = if (isFabExpanded) 45f else 0f,
        label = "fab_rotation"
    )

    // Android System BackHandler: FAB schließen, Sheet schließen oder zurück zum Haupt-Tab
    BackHandler(enabled = isFabExpanded) {
        isFabExpanded = false
    }

    BackHandler(enabled = !isFabExpanded && showScanImportSheet) {
        showScanImportSheet = false
    }

    BackHandler(enabled = !isFabExpanded && !showScanImportSheet && currentTab != MainNavigationTab.DOCANIZER) {
        currentTab = MainNavigationTab.DOCANIZER
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (currentTab != MainNavigationTab.SCANNER) {
                    TopAppBar(
                        modifier = Modifier.height(84.dp),
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
                                    onClick = {
                                        currentTab = MainNavigationTab.DOCANIZER
                                        isFabExpanded = false
                                        showScanImportSheet = false
                                    },
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
                            // Settings Button ganz oben rechts mit klarem Touch-Target
                            if (currentTab != MainNavigationTab.SETTINGS) {
                                IconButton(
                                    onClick = {
                                        isFabExpanded = false
                                        showScanImportSheet = false
                                        currentTab = MainNavigationTab.SETTINGS
                                    },
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
                                // Symmetrischer 48dp Platzhalter im Settings-Modus
                                Spacer(modifier = Modifier.width(48.dp))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            },
            bottomBar = {
                // Symmetrischer Standard-Modus: NavigationBar mit überstehenden FABs sichtbar
                // Reiner Gesten-Modus: NavigationBar vollständig und sanft ausgeblendet
                AnimatedVisibility(
                    visible = !isGestureModeEnabled && currentTab != MainNavigationTab.SCANNER,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // 1. Untere Navigationsleiste mit den 3 Haupt-Tabs
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .padding(horizontal = 10.dp)
                                .testTag("main_bottom_nav_bar")
                        ) {
                            // 1. Dokumente Tab
                            val isDocanizerSelected = currentTab == MainNavigationTab.DOCANIZER
                            NavigationBarItem(
                                selected = isDocanizerSelected,
                                onClick = {
                                    isFabExpanded = false
                                    currentTab = MainNavigationTab.DOCANIZER
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isDocanizerSelected) Icons.Default.FolderSpecial else Icons.Default.Folder,
                                        contentDescription = "Dokumente",
                                        modifier = Modifier.size(if (isDocanizerSelected) 24.dp else 22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "Dokumente",
                                        fontWeight = if (isDocanizerSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = if (isDocanizerSelected) 11.5.sp else 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = navItemColors,
                                modifier = Modifier
                                    .weight(1.25f)
                                    .testTag("nav_item_docanizer")
                            )

                            // Platzhalter-Slot für den linken KI-Chatbot FAB
                            Spacer(modifier = Modifier.weight(0.85f))

                            // 2. Dashboard Tab
                            val isDashboardSelected = currentTab == MainNavigationTab.DASHBOARD
                            NavigationBarItem(
                                selected = isDashboardSelected,
                                onClick = {
                                    isFabExpanded = false
                                    currentTab = MainNavigationTab.DASHBOARD
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isDashboardSelected) Icons.Default.Dashboard else Icons.Default.DashboardCustomize,
                                        contentDescription = "Dashboard",
                                        modifier = Modifier.size(if (isDashboardSelected) 24.dp else 22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "Dashboard",
                                        fontWeight = if (isDashboardSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = if (isDashboardSelected) 11.5.sp else 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = navItemColors,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("nav_item_dashboard")
                            )

                            // Platzhalter-Slot für den rechten Scan/Import FAB
                            Spacer(modifier = Modifier.weight(0.85f))

                            // 3. Scan Eingang Tab
                            val isBatchSelected = currentTab == MainNavigationTab.BATCH_INBOX
                            NavigationBarItem(
                                selected = isBatchSelected,
                                onClick = {
                                    isFabExpanded = false
                                    currentTab = MainNavigationTab.BATCH_INBOX
                                },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (batchQueue.isNotEmpty()) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.secondary,
                                                    contentColor = Color.White
                                                ) { Text("${batchQueue.size}") }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isBatchSelected) Icons.Default.AllInbox else Icons.Default.Inbox,
                                            contentDescription = "Scan Eingang",
                                            modifier = Modifier.size(if (isBatchSelected) 24.dp else 22.dp)
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = "Scan Eingang",
                                        fontWeight = if (isBatchSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        fontSize = if (isBatchSelected) 11.5.sp else 11.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = navItemColors,
                                modifier = Modifier
                                    .weight(1.25f)
                                    .testTag("nav_item_batch_inbox")
                            )
                        }

                        // 2. Die beiden über die obere Leistenkante hinausragenden FABs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Platzhalter für Dokumente Tab
                            Spacer(modifier = Modifier.weight(1.25f))

                            // FAB 1 (Links): KI-Chatbot Button ragt über die Leiste hinaus (offset y = -18.dp)
                            Box(
                                modifier = Modifier
                                    .weight(0.85f)
                                    .offset(y = (-18).dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FloatingActionButton(
                                    onClick = {
                                        isFabExpanded = false
                                        viewModel.openAiChatDrawer()
                                    },
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary,
                                    shape = CircleShape,
                                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                                    modifier = Modifier
                                        .size(52.dp)
                                        .testTag("main_chatbot_fab")
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_ondevice_ai_agent),
                                        contentDescription = "myDocAnizer KI-Assistent & Chatbot öffnen",
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            // Platzhalter für Dashboard Tab
                            Spacer(modifier = Modifier.weight(1.1f))

                            // FAB 2 (Rechts): Scan/Import Speed-Dial FAB (herausklappend!)
                            Box(
                                modifier = Modifier
                                    .weight(0.85f)
                                    .offset(y = (-18).dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // 1. Herausklappende Speed-Dial Menüelemente
                                    AnimatedVisibility(
                                        visible = isFabExpanded,
                                        enter = fadeIn(tween(180)) + slideInVertically(initialOffsetY = { it / 2 }) + scaleIn(initialScale = 0.8f),
                                        exit = fadeOut(tween(150)) + slideOutVertically(targetOffsetY = { it / 2 }) + scaleOut(targetScale = 0.8f)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        ) {
                                            // Option A: Datei-Import Button
                                            Surface(
                                                onClick = {
                                                    isFabExpanded = false
                                                    currentTab = MainNavigationTab.IMPORT
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                shadowElevation = 6.dp,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .height(42.dp)
                                                    .testTag("fab_option_import")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FileUpload,
                                                        contentDescription = "Datei importieren",
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "Import",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            // Option B: Dokument scannen Button
                                            Surface(
                                                onClick = {
                                                    isFabExpanded = false
                                                    currentTab = MainNavigationTab.SCANNER
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shadowElevation = 6.dp,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                                modifier = Modifier
                                                    .height(42.dp)
                                                    .testTag("fab_option_scanner")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DocumentScanner,
                                                        contentDescription = "Dokument scannen",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        text = "Scannen",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 2. Haupt-FAB (+ rotiert um 45° zum X bei Aktivierung)
                                    FloatingActionButton(
                                        onClick = {
                                            isFabExpanded = !isFabExpanded
                                        },
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary,
                                        shape = CircleShape,
                                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
                                        modifier = Modifier
                                            .size(52.dp)
                                            .testTag("main_add_fab")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = if (isFabExpanded) "Menü schließen" else "Scan- & Importmenü öffnen",
                                            modifier = Modifier
                                                .size(26.dp)
                                                .graphicsLayer { rotationZ = fabRotation }
                                        )
                                    }
                                }
                            }

                            // Platzhalter für Scan Eingang Tab
                            Spacer(modifier = Modifier.weight(1.25f))
                        }
                    }
                }
            }
        ) { innerPadding ->
            val pendingUsbSyncWarning by viewModel.pendingUsbSyncWarning.collectAsState()
            val backupSyncHealth by viewModel.backupSyncHealth.collectAsState()
            val isSyncHealthWarningDismissed by viewModel.isSyncHealthWarningDismissed.collectAsState()

            val showSyncWarning = pendingUsbSyncWarning != null || (backupSyncHealth.totalConfiguredLocations > 0 && backupSyncHealth.hasWarnings && !isSyncHealthWarningDismissed)
            val displayWarningMsg = pendingUsbSyncWarning ?: run {
                if (backupSyncHealth.totalConfiguredLocations > 0) {
                    "⚠️ ${backupSyncHealth.unsyncedLocationsCount} von ${backupSyncHealth.totalConfiguredLocations} Backup-Zielen nicht synchron: " +
                    (backupSyncHealth.warnings.firstOrNull() ?: "Sicherung ausstehend")
                } else {
                    "⚠️ " + (backupSyncHealth.warnings.firstOrNull() ?: "Kein Backup eingerichtet! Dokumente liegen nur ungesichert auf diesem Smartphone.")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(
                    visible = showSyncWarning,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SyncProblem,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = displayWarningMsg,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            FilledTonalButton(
                                onClick = {
                                    viewModel.dismissUsbSyncWarning()
                                    viewModel.dismissSyncHealthWarning()
                                    currentTab = MainNavigationTab.SETTINGS
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("Sync / Details", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.dismissUsbSyncWarning()
                                    viewModel.dismissSyncHealthWarning()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Schließen",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // P2P Konflikt-Banner
                AnimatedVisibility(
                    visible = p2pConflicts.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(0.dp),
                        border = BorderStroke(1.dp, Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "⚠️ ${p2pConflicts.size} Sync-Konflikt(e) beim WLAN-Sync entdeckt!",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { currentTab = MainNavigationTab.SETTINGS },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                            ) {
                                Text("Lösen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Crossfade(
                    targetState = currentTab,
                    modifier = Modifier.weight(1f),
                    label = "tab_crossfade"
                ) { tab ->
                    when (tab) {
                        MainNavigationTab.SCANNER -> {
                            ScannerView(
                                viewModel = viewModel,
                                onDocumentScanned = { _ ->
                                    // Nach dem erfolgreichen Scan direkt zum Dokumenten Tresor wechseln
                                    currentTab = MainNavigationTab.DOCANIZER
                                },
                                onNavigateToBatchInbox = {
                                    currentTab = MainNavigationTab.BATCH_INBOX
                                },
                                onNavigateBack = {
                                    currentTab = MainNavigationTab.DOCANIZER
                                }
                            )
                        }
                        MainNavigationTab.DOCANIZER -> {
                            DmsExplorerView(
                                viewModel = viewModel,
                                onNavigateToImport = { currentTab = MainNavigationTab.IMPORT }
                            )
                        }
                        MainNavigationTab.DASHBOARD -> {
                            DashboardView(
                                viewModel = viewModel,
                                onNavigateToScan = { currentTab = MainNavigationTab.SCANNER },
                                onNavigateToImport = { currentTab = MainNavigationTab.IMPORT }
                            )
                        }
                        MainNavigationTab.BATCH_INBOX -> {
                            BatchInboxView(
                                viewModel = viewModel,
                                onBackToScanner = { currentTab = MainNavigationTab.SCANNER }
                            )
                        }
                        MainNavigationTab.IMPORT -> {
                            ImportView(
                                viewModel = viewModel,
                                onNavigateToDocAnizer = { currentTab = MainNavigationTab.DOCANIZER }
                            )
                        }
                        MainNavigationTab.SETTINGS -> {
                            SettingsView(viewModel = viewModel)
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. INTUITIVE WISCHGESTEN-ZONEN (EDGE-SWIPES):
        // Unsichtbare Wisch-Zonen am linken und rechten Bildschirmrand.
        // ZWINGEND mit Modifier.systemGestureExclusion(), um Konflikte mit der Android
        // System-"Zurück"-Geste (seit Android 10) vollständig zu eliminieren!
        // Funktioniert im reinen Gesten-Modus und als zusätzlicher Schnellzugriff im Standard-Modus.
        // =========================================================================
        val isAiChatDrawerOpen by viewModel.isAiChatDrawerOpen.collectAsStateWithLifecycle()

        if (currentTab != MainNavigationTab.SCANNER && !isAiChatDrawerOpen && !showScanImportSheet) {
            // Wisch-Zone Links (Alignment.CenterStart): Wisch von links nach rechts -> Öffnet Chatbot
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 92.dp, bottom = 100.dp)
                    .fillMaxHeight()
                    .width(40.dp)
                    .systemGestureExclusion()
                    .testTag("gesture_zone_left_chatbot")
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = { totalDrag = 0f },
                            onDragCancel = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                if (totalDrag > 32f) {
                                    change.consume()
                                    totalDrag = 0f
                                    isFabExpanded = false
                                    viewModel.openAiChatDrawer()
                                }
                            }
                        )
                    }
            )

            // Wisch-Zone Rechts (Alignment.CenterEnd): Wisch von rechts nach links -> Öffnet Scan/Import-Panel
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 92.dp, bottom = 100.dp)
                    .fillMaxHeight()
                    .width(40.dp)
                    .systemGestureExclusion()
                    .testTag("gesture_zone_right_scan_import")
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragEnd = { totalDrag = 0f },
                            onDragCancel = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                if (totalDrag < -32f) {
                                    change.consume()
                                    totalDrag = 0f
                                    if (isGestureModeEnabled) {
                                        showScanImportSheet = true
                                    } else {
                                        isFabExpanded = true
                                    }
                                }
                            }
                        )
                    }
            )
        }

        // =========================================================================
        // 4. FAB SPEED-DIAL SCRIM (Abdunklung & Schließen bei Klick außerhalb)
        // =========================================================================
        AnimatedVisibility(
            visible = isFabExpanded,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isFabExpanded = false
                    }
            )
        }

        // =========================================================================
        // 5. SCAN / IMPORT SCHNELLZUGRIFFS-PANEL (MODAL BOTTOM SHEET)
        // Ausgelöst durch Wischgeste im Gesten-Modus oder direkte Schnellaktionen
        // =========================================================================
        if (showScanImportSheet) {
            ModalBottomSheet(
                onDismissRequest = { showScanImportSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Schnellzugriff & Scan/Import",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Gesten-Schnellmenü für deinen Dokumenten Tresor",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showScanImportSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: Dokument scannen
                    Surface(
                        onClick = {
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.SCANNER
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_option_scanner")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DocumentScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dokument scannen",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Kamera-Scan mit automatischer Kantenerkennung & OCR",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Option 2: Datei-Import
                    Surface(
                        onClick = {
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.IMPORT
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_option_import")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Dateien & PDFs importieren",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Lokale Dokumente oder Vorlagen in den Tresor laden",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Option 3: Scan InBox
                    Surface(
                        onClick = {
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.BATCH_INBOX
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_option_batch_inbox")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AllInbox,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan InBox (${batchQueue.size} in Warteschlange)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Stapelverarbeitung & unfertige Dokumente prüfen",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Option 4: Einstellungen
                    Surface(
                        onClick = {
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.SETTINGS
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_option_settings")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Einstellungen & Konfiguration",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tresor, Design, Wischgesten & Backup anpassen",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Geführter Ersteinrichtungs- & Konfigurations-Assistent
        if ((!isWizardCompleted && !sessionDismissedWizard) || showManualWizard) {
            SetupWizardDialog(
                viewModel = viewModel,
                isManualReconfig = showManualWizard,
                onDismissSession = {
                    if (showManualWizard) {
                        viewModel.dismissManualConfigWizard()
                    } else {
                        sessionDismissedWizard = true
                    }
                },
                onFinish = {
                    if (showManualWizard) {
                        viewModel.dismissManualConfigWizard()
                    } else {
                        viewModel.setWizardCompleted(true)
                    }
                    viewModel.startFirstStepsTour()
                }
            )
        }

        // Optionaler PC- & Desktop-Scanner Assistent
        val showPcCompanionWizard by viewModel.showPcCompanionWizard.collectAsState()
        if (showPcCompanionWizard) {
            PcCompanionWizardDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.dismissPcCompanionWizard() }
            )
        }

        // Virtuelle Erste-Schritte Tour (Simpel & effektiv bei manuellem Aufruf oder geführter Tour)
        val showFirstStepsTour by viewModel.showFirstStepsTour.collectAsStateWithLifecycle()
        if (showFirstStepsTour) {
            FirstStepsTourModal(
                viewModel = viewModel,
                onDismiss = { viewModel.dismissFirstStepsTour(markCompleted = true) },
                onNavigateToTab = { targetTab ->
                    currentTab = targetTab
                }
            )
        }

        // Globaler Autorisierungsdialog für eingehende Kopplungsanfragen
        if (p2pPendingRequests.isNotEmpty()) {
            val req = p2pPendingRequests.first()
            AlertDialog(
                onDismissRequest = { /* Nicht schließen ohne Entscheidung */ },
                icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Zweitgerät autorisieren?", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Das Gerät „${req.clientDeviceName}“ (IP: ${req.clientIp}) möchte sich für den Offline Master-Master-Sync mit deinem Dokumenten Tresor koppeln.")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Eingegebener PIN:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = req.pinEntered,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "Möchtest du dieses Gerät als vertrauenswürdigen Master-Sync-Partner autorisieren? Alle Dokumente werden dann bidirektional abgeglichen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.authorizeP2pPairingRequest(req.requestId, allow = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Text("Autorisieren & Koppeln")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.authorizeP2pPairingRequest(req.requestId, allow = false) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Ablehnen")
                    }
                }
            )
        }

        // 3. Premium App Rating Dialog (ASO & Feedback Call-to-Action)
        val showSuccessRatingPrompt by viewModel.showSuccessRatingPrompt.collectAsStateWithLifecycle()

        if (showSuccessRatingPrompt) {
            val context = androidx.compose.ui.platform.LocalContext.current

            AlertDialog(
                onDismissRequest = { 
                    viewModel.dismissSuccessRatingPrompt()
                },
                icon = { Icon(Icons.Default.RateReview, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
                title = { Text("Deine ehrliche Meinung zählt!", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Wir glauben an 100% Transparenz, herausragende Qualität und ehrlichen Support statt undurchsichtiger Tricks. Als unabhängiges, werbefreies Projekt freuen wir uns über jede ehrliche Bewertung im Play Store – egal ob Lob, Anregung oder konstruktive Kritik.\n\nDeine Meinung hilft uns, myDocAnizer stetig weiterzuentwickeln, und zeigt anderen, dass wir uns aktiv um die App und unsere Community kümmern!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setHasRatedOrSkipped(true)
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("market://details?id=${context.packageName}")
                                ).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val webIntent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                                )
                                context.startActivity(webIntent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("⭐ Im Play Store bewerten")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(
                            onClick = {
                                // Später erinnern (Nutzt professionelles Snooze: Zeigt den Dialog erst nach 14 Tagen & 10 Starts wieder!)
                                viewModel.snoozeRatingPrompt()
                            }
                        ) {
                            Text("Später", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = {
                                viewModel.setHasRatedOrSkipped(true)
                                try {
                                    val mailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                        data = android.net.Uri.parse("mailto:support@mydocanizer.com")
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Ehrliches Feedback myDocAnizer-Mobile")
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(mailIntent)
                                } catch (_: Exception) {}
                            }
                        ) {
                            Text("Feedback per E-Mail", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }

        // MINIMALER ON-DEVICE KI-ASSISTENT DRAWER (vom linken Rand)
        if (isAiChatDrawerOpen) {
            com.example.ui.components.AiDocumentChatDrawer(
                isOpen = true,
                onClose = { viewModel.closeAiChatDrawer() },
                viewModel = viewModel,
                onExecuteAction = { actionType ->
                    when (actionType) {
                        "OPEN_SCANNER" -> {
                            isFabExpanded = false
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.SCANNER
                        }
                        "OPEN_DASHBOARD" -> {
                            isFabExpanded = false
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.DASHBOARD
                        }
                        "OPEN_SETTINGS" -> {
                            isFabExpanded = false
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.SETTINGS
                        }
                        "OPEN_IMPORT" -> {
                            isFabExpanded = false
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.IMPORT
                        }
                        else -> {
                            isFabExpanded = false
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.DOCANIZER
                        }
                    }
                }
            )
        }
    }
}
