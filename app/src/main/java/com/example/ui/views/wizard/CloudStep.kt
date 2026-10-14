package com.example.ui.views.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CloudSyncConfig
import com.example.model.calculateBackupSafetyScore
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.p2p.PairingWizardDialog
import com.example.ui.views.wizard.cloud.*
import kotlinx.coroutines.launch

@Composable
fun StepBackupAndRecoveryStrategy(
    viewModel: DocAnizerViewModel,
    enableDrive: Boolean,
    onToggleDrive: (Boolean) -> Unit,
    enableNas: Boolean,
    onToggleNas: (Boolean) -> Unit,
    enableManualUsb: Boolean,
    onToggleManualUsb: (Boolean) -> Unit,
    autoMirror: Boolean,
    onToggleAutoMirror: (Boolean) -> Unit,
    autoSync: Boolean,
    onToggleAutoSync: (Boolean) -> Unit,
    webDavUrl: String,
    onWebDavUrlChange: (String) -> Unit,
    webDavUser: String,
    onWebDavUserChange: (String) -> Unit,
    webDavPass: String,
    onWebDavPassChange: (String) -> Unit
) {
    val cloudSyncConfig by viewModel.cloudSyncConfig.collectAsState()
    val usbDrives by viewModel.usbDrives.collectAsState()
    val p2pServerStatus by viewModel.p2pServerStatus.collectAsState()
    val p2pPairedDevices by viewModel.p2pPairedDevices.collectAsState()

    var showWizardPairingDialog by remember { mutableStateOf(false) }
    var showEducationDialog by remember { mutableStateOf(false) }
    var showAddUsbDialog by remember { mutableStateOf(false) }
    var newUsbName by remember { mutableStateOf("") }
    var driveStatusMessage by remember { mutableStateOf<String?>(null) }
    var nasStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingDrive by remember { mutableStateOf(false) }
    var isTestingNas by remember { mutableStateOf(false) }
    var showAllDetails by rememberSaveable { mutableStateOf(false) }
    var expandedTier by rememberSaveable { mutableStateOf<Int?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val currentConfig = cloudSyncConfig.copy(
        enableGoogleDrive = enableDrive,
        enableWebDavNas = enableNas,
        enableLocalVault = true,
        enableManualUsbExport = enableManualUsb,
        autoMirrorToBackupDir = autoMirror,
        isAirGappedStrict = false
    )
    val safetyScore = calculateBackupSafetyScore(currentConfig)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(
                text = "Backup & Ausfallsicherheit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Schütze deine Unterlagen vor Geräteverlust, Brand und Katastrophen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Safety Score Banner & Education Card
        BackupSafetyScoreBanner(
            safetyScore = safetyScore,
            onShowEducationDialog = { showEducationDialog = true }
        )

        // Local Storage Base Bar
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Basis: Lokaler Smartphone-Speicher (100% Offline)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

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
            enableManualUsb = enableManualUsb,
            onToggleManualUsb = onToggleManualUsb,
            isExpanded = showAllDetails || enableManualUsb || expandedTier == 1,
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
            enableDrive = enableDrive,
            onToggleDrive = onToggleDrive,
            isExpanded = showAllDetails || enableDrive || expandedTier == 2,
            onToggleExpand = { expandedTier = if (expandedTier == 2) null else 2 },
            isTestingDrive = isTestingDrive,
            driveStatusMessage = driveStatusMessage,
            onTestDriveConnection = {
                isTestingDrive = true
                driveStatusMessage = null
                coroutineScope.launch {
                    viewModel.testCloudSync("GOOGLE_DRIVE") { success, msg ->
                        isTestingDrive = false
                        driveStatusMessage = if (success) "✅ Google Drive Tresor erfolgreich verbunden & einsatzbereit!" else "Fehler: $msg"
                    }
                }
            }
        )

        // STUFE 3: Heim-NAS / WebDAV
        BackupTierNasCard(
            enableNas = enableNas,
            onToggleNas = onToggleNas,
            isExpanded = showAllDetails || enableNas || expandedTier == 3,
            onToggleExpand = { expandedTier = if (expandedTier == 3) null else 3 },
            webDavUrl = webDavUrl,
            onWebDavUrlChange = onWebDavUrlChange,
            webDavUser = webDavUser,
            onWebDavUserChange = onWebDavUserChange,
            webDavPass = webDavPass,
            onWebDavPassChange = onWebDavPassChange,
            isTestingNas = isTestingNas,
            nasStatusMessage = nasStatusMessage,
            onTestNasConnection = {
                isTestingNas = true
                nasStatusMessage = null
                coroutineScope.launch {
                    viewModel.testCloudSync("WEBDAV_NAS") { success, msg ->
                        isTestingNas = false
                        nasStatusMessage = if (success) "✅ Heim-NAS erfolgreich erreichbar & eingerichtet!" else "Fehler: $msg"
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
                showWizardPairingDialog = true
            },
            onLaunchPcCompanionWizard = {
                if (!p2pServerStatus.isRunning) viewModel.startP2pServer()
                viewModel.launchPcCompanionWizard()
            }
        )

        // Auto-Sync after scan Switch
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Sync nach jedem Scan", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Sichert neue Belege direkt im Hintergrund", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoSync,
                    onCheckedChange = onToggleAutoSync,
                    enabled = enableDrive || enableNas
                )
            }
        }
    }

    // Dialogs
    if (showWizardPairingDialog) {
        PairingWizardDialog(
            viewModel = viewModel,
            serverIp = p2pServerStatus.localIp,
            serverPort = p2pServerStatus.port,
            onDismiss = {
                viewModel.stopP2pHostPairingMode()
                showWizardPairingDialog = false
            }
        )
    }

    if (showEducationDialog) {
        AlertDialog(
            onDismissRequest = { showEducationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Datensicherheit: Die 3-2-1 Strategie")
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Warum ist eine Verteilung auf mehrere Speicherorte sinnvoll?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Elementarschäden & Haushalts-Unfälle:\nBei unglücklichen Zwischenfällen wie einem schweren Leitungswasserschaden oder Brand können Geräte im selben Zimmer oder Haushalt gleichzeitig unbrauchbar werden. Ein räumlich getrennter USB-Stick (z.B. bei Angehörigen oder im Schließfach) oder ein verschlüsselter Speicher stellt sicher, dass Dokumente auch in solchen Fällen erhalten bleiben.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Verlust, Diebstahl oder Gerätedefekt:\nSmartphones können verloren gehen, einen Platinenschaden erleiden oder gestohlen werden. Mit einer externen Sicherheitskopie stellst du deine Unterlagen auf einem neuen Gerät innerhalb weniger Minuten wieder her.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Die bewährte 3-2-1 Backup-Regel:\n• 3 Kopien aller wichtigen Dateien anlegen\n• 2 verschiedene Speichermedien nutzen (z.B. Smartphone-Speicher + USB-Stick)\n• 1 Kopie an einem getrennten Ort aufbewahren (Offsite-Cloud oder USB-Stick außerhalb des Haushalts)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showEducationDialog = false }) {
                    Text("Verstanden")
                }
            }
        )
    }

    if (showAddUsbDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddUsbDialog = false
                newUsbName = ""
            },
            title = { Text("USB-Stick registrieren") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Gib dem USB-Stick einen sprechenden Namen (z.B. nach Aufbewahrungsort):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newUsbName,
                        onValueChange = { newUsbName = it },
                        label = { Text("Stick-Name") },
                        placeholder = { Text("z.B. USB #2 (Eltern / Extern)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUsbName.isNotBlank()) {
                            viewModel.addUsbDrive(newUsbName.trim())
                            showAddUsbDialog = false
                            newUsbName = ""
                        }
                    },
                    enabled = newUsbName.isNotBlank()
                ) {
                    Text("Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddUsbDialog = false
                    newUsbName = ""
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
