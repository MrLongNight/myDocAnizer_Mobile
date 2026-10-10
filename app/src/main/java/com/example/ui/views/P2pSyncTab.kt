package com.example.ui.views

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.p2p.*

@Composable
fun P2pSyncTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val pairedDevices by viewModel.p2pPairedDevices.collectAsState()
    val serverStatus by viewModel.p2pServerStatus.collectAsState()
    val pendingRequests by viewModel.p2pPendingPairingRequests.collectAsState()
    val syncProgress by viewModel.p2pSyncProgress.collectAsState()
    val activeConflicts by viewModel.p2pActiveConflicts.collectAsState()
    val syncHistory by viewModel.p2pSyncHistory.collectAsState()
    val localDeviceName by viewModel.p2pLocalDeviceName.collectAsState()

    var showPairingDialog by remember { mutableStateOf(false) }
    var showEditDeviceNameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. INFO BANNERS
        P2pInfoBanner(
            onLaunchPcCompanion = { viewModel.launchPcCompanionWizard() }
        )

        // 2. SERVER STATUS CARD
        P2pServerStatusCard(
            serverStatus = serverStatus,
            localDeviceName = localDeviceName,
            onToggleServer = { start ->
                if (start) viewModel.startP2pServer() else viewModel.stopP2pServer()
            },
            onEditDeviceNameClick = { showEditDeviceNameDialog = true }
        )

        // 3. PENDING REQUESTS, CONFLICTS & ACTIVE SYNC PROGRESS
        P2pConflictAndProgressCard(
            pendingRequests = pendingRequests,
            activeConflicts = activeConflicts,
            syncProgress = syncProgress,
            onAuthorizeRequest = { requestId, allow ->
                viewModel.authorizeP2pPairingRequest(requestId, allow)
            },
            onResolveConflict = { conflict, action ->
                viewModel.resolveP2pConflict(conflict, action)
            }
        )

        // 4. PAIRED DEVICES LIST & SYNC CONTROLS
        P2pPairedDevicesList(
            pairedDevices = pairedDevices,
            syncHistory = syncHistory,
            onPairNewDeviceClick = { showPairingDialog = true },
            onSyncDevice = { device ->
                viewModel.syncWithP2pDevice(device) { res ->
                    res.onSuccess {
                        Toast.makeText(context, "Sync mit ${device.name} erfolgreich!", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, "Fehler: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onRemoveDevice = { deviceId -> viewModel.removeP2pPairedDevice(deviceId) },
            onSyncAllDevices = {
                viewModel.syncAllP2pDevices { success, conflicts ->
                    Toast.makeText(
                        context,
                        "Sync abgeschlossen ($success Geräte synchronisiert, $conflicts Konflikte)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // DIALOGS
    if (showPairingDialog) {
        PairingWizardDialog(
            viewModel = viewModel,
            serverIp = serverStatus.localIp,
            serverPort = serverStatus.port,
            onDismiss = {
                viewModel.stopP2pHostPairingMode()
                showPairingDialog = false
            }
        )
    }

    if (showEditDeviceNameDialog) {
        var tempName by remember { mutableStateOf(localDeviceName) }
        AlertDialog(
            onDismissRequest = { showEditDeviceNameDialog = false },
            title = { Text("Eigener Gerätename") },
            text = {
                OutlinedTextField(
                    value = tempName,
                    onValueChange = { tempName = it },
                    label = { Text("Gerätename im WLAN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (tempName.isNotBlank()) {
                        viewModel.setP2pDeviceName(tempName)
                    }
                    showEditDeviceNameDialog = false
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDeviceNameDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
