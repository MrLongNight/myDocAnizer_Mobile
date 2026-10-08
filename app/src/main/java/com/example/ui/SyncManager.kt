package com.example.ui

import com.example.model.*
import com.example.service.PairingResult
import com.example.service.P2pSyncManager
import com.example.service.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SyncManager(
    private val p2pSyncManager: P2pSyncManager,
    private val settingsRepo: SettingsRepository,
    private val scope: CoroutineScope
) {
    // P2P Sync
    val p2pPairedDevices: StateFlow<List<PairedDevice>> = p2pSyncManager.pairedDevices
    val p2pServerStatus: StateFlow<P2pServerStatus> = p2pSyncManager.serverStatus
    val p2pHostPairingPin: StateFlow<String?> = p2pSyncManager.hostPairingPin
    val p2pPendingPairingRequests: StateFlow<List<IncomingPairingRequest>> = p2pSyncManager.pendingPairingRequests
    val p2pSyncProgress: StateFlow<P2pSyncProgressState> = p2pSyncManager.syncProgress
    val p2pActiveConflicts: StateFlow<List<SyncConflict>> = p2pSyncManager.activeConflicts
    val p2pSyncHistory: StateFlow<List<P2pSyncLog>> = p2pSyncManager.syncHistory
    val p2pLocalDeviceName: StateFlow<String> = p2pSyncManager.localDeviceName

    fun setP2pDeviceName(name: String) = p2pSyncManager.setLocalDeviceName(name)
    fun startP2pServer() = p2pSyncManager.startServer()
    fun stopP2pServer() = p2pSyncManager.stopServer()
    fun startP2pHostPairingMode(): String = p2pSyncManager.startHostPairingMode()
    fun stopP2pHostPairingMode() = p2pSyncManager.stopHostPairingMode()
    fun authorizeP2pPairingRequest(requestId: String, allow: Boolean) = p2pSyncManager.authorizePairingRequest(requestId, allow)
    
    fun pairWithP2pDevice(ip: String, port: Int = 8765, pin: String, onResult: (Result<PairingResult>) -> Unit) {
        scope.launch {
            val res = p2pSyncManager.pairWithRemoteDevice(ip, port, pin)
            onResult(res)
        }
    }

    fun syncWithP2pDevice(device: PairedDevice, onResult: (Result<P2pSyncLog>) -> Unit = {}) {
        scope.launch {
            val res = p2pSyncManager.syncWithDevice(device)
            onResult(res)
        }
    }

    fun syncAllP2pDevices(onComplete: (Int, Int) -> Unit = { _, _ -> }) {
        p2pSyncManager.syncAllPairedDevices(onComplete)
    }

    fun resolveP2pConflict(conflict: SyncConflict, action: ConflictResolutionAction) {
        scope.launch {
            p2pSyncManager.resolveConflict(conflict, action)
        }
    }

    fun removeP2pPairedDevice(deviceId: String) = p2pSyncManager.removePairedDevice(deviceId)

    fun pairWithDesktopLightApp(qrJson: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val res = p2pSyncManager.pairWithDesktopQrData(qrJson)
            res.onSuccess { dev ->
                onResult(true, "✅ Erfolgreich mit Desktop '${dev.name}' verbunden!")
            }.onFailure { err ->
                onResult(false, "Verbindung fehlgeschlagen: ${err.localizedMessage ?: "Unbekannter Fehler"}")
            }
        }
    }

    // Cloud Sync Settings
    val cloudSyncConfig: StateFlow<CloudSyncConfig> = settingsRepo.cloudSyncConfig
    val autoCloudSyncEnabled: StateFlow<Boolean> = settingsRepo.autoCloudSyncEnabled
    val lastSyncTimestamp: StateFlow<Long> = settingsRepo.lastSyncTimestamp
    val syncPasswordKey: StateFlow<String> = settingsRepo.syncPasswordKey

    fun updateCloudSyncConfig(config: CloudSyncConfig) = settingsRepo.updateCloudSyncConfig(config)
    fun setAutoCloudSyncEnabled(enabled: Boolean) = settingsRepo.setAutoCloudSyncEnabled(enabled)
    fun setSyncPasswordKey(key: String) = settingsRepo.setSyncPasswordKey(key)

    // USB Backup-Sticks
    val usbBackupDrives: StateFlow<List<UsbBackupDrive>> = settingsRepo.usbBackupDrives
    fun addUsbBackupDrive(name: String, location: String) = settingsRepo.addUsbBackupDrive(name, location)
    fun deleteUsbBackupDrive(id: String) = settingsRepo.deleteUsbBackupDrive(id)
    fun syncUsbDrive(id: String, count: Int) = settingsRepo.syncUsbDrive(id, count)
}
