package com.example.ui.views.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.p2p.PairingWizardDialog
import com.example.ui.views.wizard.cloud.*
import kotlinx.coroutines.launch

@Composable
fun CloudSyncSettingsTab(viewModel: DocAnizerViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncState by viewModel.syncState.collectAsState()
    val syncPasswordKey by viewModel.syncPasswordKey.collectAsState()
    val cloudSyncConfig by viewModel.cloudSyncConfig.collectAsState()
    val usbDrives by viewModel.usbDrives.collectAsState()
    val p2pServerStatus by viewModel.p2pServerStatus.collectAsState()
    val p2pPairedDevices by viewModel.p2pPairedDevices.collectAsState()

    var webDavUrl by remember(cloudSyncConfig.webDavUrl) { mutableStateOf(cloudSyncConfig.webDavUrl) }
    var webDavUser by remember(cloudSyncConfig.webDavUsername) { mutableStateOf(cloudSyncConfig.webDavUsername) }
    var webDavPass by remember(cloudSyncConfig.webDavPassword) { mutableStateOf(cloudSyncConfig.webDavPassword) }

    var showAddUsbDialog by remember { mutableStateOf(false) }
    var newUsbName by remember { mutableStateOf("") }

    var isExportingBackup by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var selectedRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    var isTestingDrive by remember { mutableStateOf(false) }
    var driveStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingNas by remember { mutableStateOf(false) }
    var nasStatusMessage by remember { mutableStateOf<String?>(null) }

    var showAllDetails by rememberSaveable { mutableStateOf(false) }
    var expandedTier by rememberSaveable { mutableStateOf<Int?>(null) }
    var showPairingDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val safetyScore = calculateBackupSafetyScore(cloudSyncConfig)

    val restoreFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            showRestoreDialog = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Safety Score Banner
        BackupSafetyScoreBanner(
            safetyScore = safetyScore,
            onShowEducationDialog = { }
        )

        // Tiers Expansion Toggle Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speicher-Stufen & Synchronisation",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clickable { showAllDetails = !showAllDetails }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (showAllDetails) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (showAllDetails) "Kompaktansicht" else "Alle Details ausklappen",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // STUFE 1: USB-Stick Backup
        BackupTierUsbCard(
            enableManualUsb = cloudSyncConfig.enableManualUsbExport,
            onToggleManualUsb = { checked ->
                viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(enableManualUsbExport = checked))
            },
            isExpanded = showAllDetails || cloudSyncConfig.enableManualUsbExport || expandedTier == 1,
            onToggleExpand = { expandedTier = if (expandedTier == 1) null else 1 },
            usbSyncWarningOnScan = cloudSyncConfig.usbSyncWarningOnScan,
            onToggleSyncWarning = { checked ->
                viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(usbSyncWarningOnScan = checked))
            },
            usbDrives = usbDrives,
            onSyncUsbDrive = { driveId -> viewModel.syncUsbDrive(driveId) { _, _ -> } },
            onRemoveUsbDrive = { driveId -> viewModel.removeUsbDrive(driveId) },
            onShowAddUsbDialog = { showAddUsbDialog = true }
        )

        // STUFE 2: Google Drive Cloud-Tresor
        BackupTierDriveCard(
            enableDrive = cloudSyncConfig.enableGoogleDrive,
            onToggleDrive = { checked ->
                viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(enableGoogleDrive = checked))
            },
            isExpanded = showAllDetails || cloudSyncConfig.enableGoogleDrive || expandedTier == 2,
            onToggleExpand = { expandedTier = if (expandedTier == 2) null else 2 },
            isTestingDrive = isTestingDrive,
            driveStatusMessage = driveStatusMessage,
            onTestDriveConnection = {
                isTestingDrive = true
                driveStatusMessage = null
                coroutineScope.launch {
                    viewModel.testCloudSync("GOOGLE_DRIVE") { success, msg ->
                        isTestingDrive = false
                        driveStatusMessage = if (success) "✅ Google Drive Tresor erfolgreich verbunden!" else "Fehler: $msg"
                    }
                }
            }
        )

        // STUFE 3: Heim-NAS / WebDAV
        BackupTierNasCard(
            enableNas = cloudSyncConfig.enableWebDavNas,
            onToggleNas = { checked ->
                viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(enableWebDavNas = checked))
            },
            isExpanded = showAllDetails || cloudSyncConfig.enableWebDavNas || expandedTier == 3,
            onToggleExpand = { expandedTier = if (expandedTier == 3) null else 3 },
            webDavUrl = webDavUrl,
            onWebDavUrlChange = {
                webDavUrl = it
                viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(webDavUrl = it))
            },
            webDavUser = webDavUser,
            onWebDavUserChange = {
                webDavUser = it
                viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(webDavUsername = it))
            },
            webDavPass = webDavPass,
            onWebDavPassChange = {
                webDavPass = it
                viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(webDavPassword = it))
            },
            isTestingNas = isTestingNas,
            nasStatusMessage = nasStatusMessage,
            onTestNasConnection = {
                isTestingNas = true
                nasStatusMessage = null
                coroutineScope.launch {
                    viewModel.testCloudSync("WEBDAV_NAS") { success, msg ->
                        isTestingNas = false
                        nasStatusMessage = if (success) "✅ Heim-NAS erfolgreich erreichbar!" else "Fehler: $msg"
                    }
                }
            }
        )

        // STUFE 4: Multi-Device Sync & PC Companion
        BackupTierMultiDeviceCard(
            p2pServerStatus = p2pServerStatus,
            p2pPairedDevices = p2pPairedDevices,
            onToggleP2pServer = { start ->
                if (start) viewModel.startP2pServer() else viewModel.stopP2pServer()
            },
            isExpanded = showAllDetails || p2pServerStatus.isRunning || expandedTier == 4,
            onToggleExpand = { expandedTier = if (expandedTier == 4) null else 4 },
            onStartPairingDialog = {
                if (!p2pServerStatus.isRunning) viewModel.startP2pServer()
                showPairingDialog = true
            },
            onLaunchPcCompanionWizard = {
                if (!p2pServerStatus.isRunning) viewModel.startP2pServer()
                viewModel.launchPcCompanionWizard()
            }
        )

        // 1-KLICK-SICHERUNG & ARCHIV-EXPORT
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sicherung & Archiv-Export", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        isExportingBackup = true
                        viewModel.exportEncryptedBackup { file, error ->
                            isExportingBackup = false
                            exportFeedback = if (file != null) "Erfolg: ${file.name}" else "Fehler: $error"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verschlüsseltes Archiv (.enc) erstellen")
                }
                OutlinedButton(
                    onClick = { restoreFilePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Backup wiederherstellen")
                }
                exportFeedback?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        // HINWEIS: ZENTRALER MASTER-SCHLÜSSEL AUS SICHERHEIT
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        "Zentraler Master-Key aktiv",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Alle Datensicherungen, Cloud-Tresore und Dual-Sync-Übertragungen nutzen automatisch deinen zentralen Master-Schlüssel aus dem Bereich 'Sicherheit'. Eine separate Schlüsseleingabe hier ist nicht mehr nötig.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showRestoreDialog && selectedRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Wiederherstellen?") },
            text = { Text("Möchtest du das Backup importieren? Es wird mit deinem zentralen Master-Schlüssel aus dem Bereich 'Sicherheit' entschlüsselt.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.restoreEncryptedBackup(selectedRestoreUri!!, syncPasswordKey) { _, _ ->
                        showRestoreDialog = false
                    }
                }) { Text("Ja") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showAddUsbDialog) {
        AlertDialog(
            onDismissRequest = { showAddUsbDialog = false },
            title = { Text("Neuen USB-Stick registrieren") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vergebe einen Namen für deinen USB-Stick:")
                    OutlinedTextField(
                        value = newUsbName,
                        onValueChange = { newUsbName = it },
                        label = { Text("Name (z.B. Tresor-Stick Rot)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = newUsbName.isNotBlank(),
                    onClick = {
                        viewModel.addUsbDrive(newUsbName, "Zuhause")
                        newUsbName = ""
                        showAddUsbDialog = false
                    }
                ) { Text("Registrieren") }
            },
            dismissButton = {
                TextButton(onClick = { showAddUsbDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showPairingDialog) {
        PairingWizardDialog(
            viewModel = viewModel,
            serverIp = p2pServerStatus.localIp,
            serverPort = p2pServerStatus.port,
            onDismiss = { showPairingDialog = false }
        )
    }
}
