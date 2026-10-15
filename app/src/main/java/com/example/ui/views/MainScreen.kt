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
import com.example.ui.components.TourElementBounds

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
    val tourTargetBounds = remember { mutableStateMapOf<Int, TourElementBounds>() }

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
    val showFirstStepsTour by viewModel.showFirstStepsTour.collectAsStateWithLifecycle()
    val currentTourStep by viewModel.tourStep.collectAsStateWithLifecycle()

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
                    com.example.ui.navigation.MainTopAppBar(
                        currentTab = currentTab,
                        onNavigateToDocAnizer = {
                            currentTab = MainNavigationTab.DOCANIZER
                            isFabExpanded = false
                            showScanImportSheet = false
                        },
                        onNavigateToSettings = {
                            isFabExpanded = false
                            showScanImportSheet = false
                            currentTab = MainNavigationTab.SETTINGS
                        }
                    )
                }
            },
            bottomBar = {
                com.example.ui.navigation.MainBottomBar(
                    viewModel = viewModel,
                    currentTab = currentTab,
                    isGestureModeEnabled = isGestureModeEnabled,
                    batchQueueSize = batchQueue.size,
                    isFabExpanded = isFabExpanded,
                    onTabSelected = { tab ->
                        isFabExpanded = false
                        currentTab = tab
                    },
                    onOpenAiChatDrawer = {
                        isFabExpanded = false
                        viewModel.openAiChatDrawer()
                    },
                    onToggleFabExpand = {
                        isFabExpanded = !isFabExpanded
                    },
                    activeTourStep = if (showFirstStepsTour) currentTourStep else null,
                    onTourElementClicked = { step ->
                        when (step) {
                            1 -> {
                                isFabExpanded = true
                                viewModel.setTourStep(2)
                            }
                            2 -> {
                                isFabExpanded = false
                                currentTab = MainNavigationTab.SCANNER
                                viewModel.setTourStep(3)
                            }
                            3 -> {
                                viewModel.setTourStep(4)
                            }
                            4 -> {
                                viewModel.triggerSampleScanForTour()
                                viewModel.setTourStep(5)
                            }
                            5 -> {
                                viewModel.clearPendingRuleSuggestion()
                                currentTab = MainNavigationTab.DOCANIZER
                                viewModel.setTourStep(6)
                            }
                            6 -> {
                                currentTab = MainNavigationTab.DOCANIZER
                                viewModel.setTourStep(7)
                            }
                            7 -> {
                                viewModel.openAiChatDrawer()
                                viewModel.setTourStep(8)
                            }
                            8 -> {
                                viewModel.setTourStep(9)
                            }
                            9 -> {
                                viewModel.closeAiChatDrawer()
                                currentTab = MainNavigationTab.DASHBOARD
                                viewModel.setTourStep(10)
                            }
                            10 -> {
                                currentTab = MainNavigationTab.DASHBOARD
                                viewModel.setTourStep(11)
                            }
                            11 -> {
                                viewModel.dismissFirstStepsTour(markCompleted = true)
                                currentTab = MainNavigationTab.DASHBOARD
                            }
                        }
                    },
                    onReportElementBounds = { step, bounds ->
                        if (showFirstStepsTour) {
                            val existing = tourTargetBounds[step]
                            if (existing == null ||
                                kotlin.math.abs(existing.centerX - bounds.centerX) > 0.5f ||
                                kotlin.math.abs(existing.centerY - bounds.centerY) > 0.5f ||
                                kotlin.math.abs(existing.widthPx - bounds.widthPx) > 0.5f ||
                                kotlin.math.abs(existing.heightPx - bounds.heightPx) > 0.5f ||
                                existing.shapeType != bounds.shapeType
                            ) {
                                tourTargetBounds[step] = bounds
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            val pendingUsbSyncWarning by viewModel.pendingUsbSyncWarning.collectAsStateWithLifecycle()
            val backupSyncHealth by viewModel.backupSyncHealth.collectAsStateWithLifecycle()
            val isSyncHealthWarningDismissed by viewModel.isSyncHealthWarningDismissed.collectAsStateWithLifecycle()
            val activeDownload by viewModel.activeDownloadingModel.collectAsStateWithLifecycle()

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
                // Globaler LLM Hintergrund-Download-Banner (bleibt aktiv beim Wechseln aller Screens)
                AnimatedVisibility(
                    visible = activeDownload != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    activeDownload?.let { downloading ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(0.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentTab = MainNavigationTab.SETTINGS
                                    viewModel.setTargetSettingsSubTab(2)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { downloading.downloadProgress },
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📥 KI-Modell Download läuft im Hintergrund: ${downloading.name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${(downloading.downloadProgress * 100).toInt()}% • ${(downloading.downloadedBytes / (1024 * 1024))} MB / ${downloading.downloadSizeMb} MB • Tippen für KI-Einstellungen",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.cancelDownload(downloading.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Pausieren", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
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
                                },
                                onReportElementBounds = { step, bounds ->
                                    val existing = tourTargetBounds[step]
                                    if (existing == null ||
                                        kotlin.math.abs(existing.centerX - bounds.centerX) > 0.5f ||
                                        kotlin.math.abs(existing.centerY - bounds.centerY) > 0.5f ||
                                        kotlin.math.abs(existing.widthPx - bounds.widthPx) > 0.5f ||
                                        kotlin.math.abs(existing.heightPx - bounds.heightPx) > 0.5f ||
                                        existing.shapeType != bounds.shapeType
                                    ) {
                                        tourTargetBounds[step] = bounds
                                    }
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
            com.example.ui.navigation.MainGestureZones(
                isGestureModeEnabled = isGestureModeEnabled,
                onOpenChatbot = {
                    isFabExpanded = false
                    viewModel.openAiChatDrawer()
                },
                onOpenScanImport = {
                    if (isGestureModeEnabled) {
                        showScanImportSheet = true
                    } else {
                        isFabExpanded = true
                    }
                }
            )
        }

        com.example.ui.navigation.FabSpeedDialOverlay(
            isFabExpanded = isFabExpanded,
            onDismiss = { isFabExpanded = false },
            onImportClick = {
                isFabExpanded = false
                currentTab = MainNavigationTab.IMPORT
            },
            onScanClick = {
                isFabExpanded = false
                currentTab = MainNavigationTab.SCANNER
                if (showFirstStepsTour && currentTourStep == 2) {
                    viewModel.setTourStep(3)
                }
            },
            onReportElementBounds = { step, bounds ->
                val existing = tourTargetBounds[step]
                if (existing == null ||
                    kotlin.math.abs(existing.centerX - bounds.centerX) > 0.5f ||
                    kotlin.math.abs(existing.centerY - bounds.centerY) > 0.5f ||
                    kotlin.math.abs(existing.widthPx - bounds.widthPx) > 0.5f ||
                    kotlin.math.abs(existing.heightPx - bounds.heightPx) > 0.5f ||
                    existing.shapeType != bounds.shapeType
                ) {
                    tourTargetBounds[step] = bounds
                }
            }
        )

        if (showScanImportSheet) {
            com.example.ui.navigation.ScanImportBottomSheet(
                batchQueueSize = batchQueue.size,
                onDismiss = { showScanImportSheet = false },
                onScanClick = {
                    showScanImportSheet = false
                    currentTab = MainNavigationTab.SCANNER
                },
                onImportClick = {
                    showScanImportSheet = false
                    currentTab = MainNavigationTab.IMPORT
                },
                onBatchInboxClick = {
                    showScanImportSheet = false
                    currentTab = MainNavigationTab.BATCH_INBOX
                },
                onSettingsClick = {
                    showScanImportSheet = false
                    currentTab = MainNavigationTab.SETTINGS
                }
            )
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
                        // Tour nur starten, wenn sie nicht im Wizard explizit übersprungen wurde
                        if (!viewModel.isFirstStepsTourCompleted.value) {
                            viewModel.startFirstStepsTour()
                        }
                    }
                }
            )
        }

        // Optionaler PC- & Desktop-Scanner Assistent
        val showPcCompanionWizard by viewModel.showPcCompanionWizard.collectAsStateWithLifecycle()
        if (showPcCompanionWizard) {
            PcCompanionWizardDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.dismissPcCompanionWizard() }
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
                onClose = {
                    viewModel.closeAiChatDrawer()
                    if (showFirstStepsTour && (currentTourStep == 8 || currentTourStep == 9)) {
                        currentTab = MainNavigationTab.DASHBOARD
                        viewModel.setTourStep(10)
                    }
                },
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
                },
                onReportElementBounds = { step, bounds ->
                    val existing = tourTargetBounds[step]
                    if (existing == null ||
                        kotlin.math.abs(existing.centerX - bounds.centerX) > 0.5f ||
                        kotlin.math.abs(existing.centerY - bounds.centerY) > 0.5f
                    ) {
                        tourTargetBounds[step] = bounds
                    }
                }
            )
        }

        // Virtuelle Erste-Schritte Tour (Simpel & effektiv bei manuellem Aufruf oder geführter Tour)
        // Platziert NACH dem Drawer, damit der Spotlight-Fokus und die Tour-Karten
        // auch über dem geöffneten KI-Assistenten (Schritte 8 & 9) sauber gerendert werden!
        if (showFirstStepsTour) {
            FirstStepsTourModal(
                viewModel = viewModel,
                targetBounds = tourTargetBounds,
                onDismiss = {
                    viewModel.dismissFirstStepsTour(markCompleted = true)
                    isFabExpanded = false
                    viewModel.closeAiChatDrawer()
                    viewModel.clearPendingRuleSuggestion()
                },
                onStepInteracted = { step ->
                    when (step) {
                        1 -> {
                            isFabExpanded = true
                            viewModel.setTourStep(2)
                        }
                        2 -> {
                            isFabExpanded = false
                            currentTab = MainNavigationTab.SCANNER
                            viewModel.setTourStep(3)
                        }
                        3 -> {
                            viewModel.setTourStep(4)
                        }
                        4 -> {
                            viewModel.triggerSampleScanForTour()
                            viewModel.setTourStep(5)
                        }
                        5 -> {
                            viewModel.clearPendingRuleSuggestion()
                            currentTab = MainNavigationTab.DOCANIZER
                            viewModel.setTourStep(6)
                        }
                        6 -> {
                            currentTab = MainNavigationTab.DOCANIZER
                            viewModel.setTourStep(7)
                        }
                        7 -> {
                            viewModel.openAiChatDrawer()
                            viewModel.setTourStep(8)
                        }
                        8 -> {
                            viewModel.setTourStep(9)
                        }
                        9 -> {
                            viewModel.closeAiChatDrawer()
                            currentTab = MainNavigationTab.DASHBOARD
                            viewModel.setTourStep(10)
                        }
                        10 -> {
                            currentTab = MainNavigationTab.DASHBOARD
                            viewModel.setTourStep(11)
                        }
                        11 -> {
                            viewModel.dismissFirstStepsTour(markCompleted = true)
                            currentTab = MainNavigationTab.DASHBOARD
                        }
                    }
                },
                onNavigateToTab = { targetTab ->
                    currentTab = targetTab
                }
            )
        }
    }
}
