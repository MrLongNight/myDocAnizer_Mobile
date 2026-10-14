package com.example.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.DocumentDao
import com.example.model.CloudSyncConfig
import com.example.model.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

object CloudSyncWorkerService {

    sealed class SyncState {
        object Idle : SyncState()
        data class Syncing(val progressText: String) : SyncState()
        data class Success(val uploadedCount: Int, val indexGenerated: Boolean, val timestamp: Long) : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } catch (e: Exception) {
            true // Fallback wenn Permission nicht auswertbar
        }
    }

    /**
     * Ermittelt den aktuellen Batteriestand in Prozent.
     */
    fun getBatteryLevel(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 50
    }

    /**
     * Prüft, ob das Gerät aktuell geladen wird.
     */
    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    /**
     * Executes zero-knowledge AES-256-GCM client-side encryption and Google Drive / NAS WebDAV sync
     */
    suspend fun performBackgroundSync(
        context: Context,
        documentDao: DocumentDao,
        allDocuments: List<DocumentEntity>,
        userPasswordKey: String,
        config: CloudSyncConfig = CloudSyncConfig()
    ) = withContext(Dispatchers.IO) {
        try {
            if (config.isAirGappedStrict) {
                _syncState.value = SyncState.Error("Air-Gapped Modus aktiv: Jegliche Netzwerk-Kommunikation ist gesperrt.")
                return@withContext
            }

            if (!config.enableGoogleDrive && !config.enableWebDavNas) {
                _syncState.value = SyncState.Idle
                return@withContext
            }

            // Prüfung: Nur im WLAN
            if (config.syncWifiOnly && !isWifiConnected(context)) {
                _syncState.value = SyncState.Error("Synchronisation pausiert: Keine aktive WLAN-Verbindung vorhanden ('Nur im WLAN' aktiv).")
                return@withContext
            }

            val targetList = mutableListOf<String>()
            if (config.enableGoogleDrive) targetList.add("Google Drive")
            if (config.enableWebDavNas) {
                val host = if (config.webDavUrl.isNotBlank()) config.webDavUrl else "Lokales NAS / WebDAV"
                targetList.add("NAS ($host)")
            }
            if (targetList.isEmpty()) {
                if (config.syncTarget == "WEBDAV_NAS") targetList.add("NAS / WebDAV")
                else if (config.syncTarget != "OFFLINE_ONLY") targetList.add("Google Drive")
            }

            val targetLabel = if (targetList.isEmpty()) "Lokaler Tresor" else targetList.joinToString(" + ")

            _syncState.value = SyncState.Syncing("Prüfe ausstehende Dokumente für $targetLabel...")

            // Step 1: Encrypt each document that hasn't been uploaded yet
            val pendingDocs = allDocuments.filter { !it.isSynced }
            var uploadedCount = 0

            _syncState.value = SyncState.Syncing("Zero-Knowledge Verschlüsselung (AES-256-GCM + PBKDF2)...")
            
            val batteryLevel = getBatteryLevel(context)
            val charging = isCharging(context)
            val lowBattery = batteryLevel < 20 && !charging
            
            if (lowBattery && pendingDocs.isNotEmpty()) {
                _syncState.value = SyncState.Syncing("Energiesparmodus: Synchronisiere nur Metadaten (PDF-Uploads pausiert bei $batteryLevel%)...")
            }

            for (doc in pendingDocs) {
                if (lowBattery) continue // Überspringe Binär-Blobs bei niedrigem Batteriestand
                
                val originalPdf = File(doc.filePath)
                if (originalPdf.exists()) {
                    val encryptedBlob = File(context.cacheDir, "${doc.fileName}.enc")
                    try {
                        DocumentStorageService.encryptFile(originalPdf, encryptedBlob, userPasswordKey)

                        var anyUploadSuccessful = false

                        // Reale WebDAV-Übertragung wenn aktiviert
                        if (config.enableWebDavNas && config.webDavUrl.isNotBlank()) {
                            _syncState.value = SyncState.Syncing("Übertrage ${encryptedBlob.name} an WebDAV/NAS...")
                            val webDavRes = WebDavClient.uploadFile(
                                url = config.webDavUrl,
                                user = config.webDavUsername,
                                pass = config.webDavPassword,
                                remoteFileName = encryptedBlob.name,
                                localFile = encryptedBlob
                            )
                            if (webDavRes.isSuccess) {
                                anyUploadSuccessful = true
                            }
                        }

                        // Reale Google Drive Übertragung wenn Token vorhanden
                        if (config.enableGoogleDrive && config.googleDriveConnected && config.googleDriveAccessToken.isNotBlank()) {
                            _syncState.value = SyncState.Syncing("Übertrage ${encryptedBlob.name} an Google Drive...")
                            val driveRes = GoogleDriveClient.uploadEncryptedFile(
                                accessToken = config.googleDriveAccessToken,
                                file = encryptedBlob,
                                remoteName = encryptedBlob.name
                            )
                            if (driveRes.isSuccess) {
                                anyUploadSuccessful = true
                            }
                        }

                        // Mark synced in database only if upload was successful or offline test mode
                        if (anyUploadSuccessful || (!config.enableWebDavNas && !config.enableGoogleDrive)) {
                            documentDao.markSynced(doc.id)
                            uploadedCount++
                        }
                    } finally {
                        if (encryptedBlob.exists()) {
                            encryptedBlob.delete()
                        }
                    }
                }
            }

            // Step 2: Disaster Recovery Central Index generation (doc_index.enc)
            _syncState.value = SyncState.Syncing("Erzeuge verschlüsselten Disaster-Recovery-Index (doc_index.enc)...")
            val encryptedIndex = DocumentStorageService.buildEncryptedIndex(context, allDocuments, userPasswordKey)
            val indexExists = encryptedIndex.exists()

            try {
                // Step 3: Payload upload to Google Drive and/or NAS WebDAV
                if (encryptedIndex.exists()) {
                    if (config.enableWebDavNas && config.webDavUrl.isNotBlank()) {
                        _syncState.value = SyncState.Syncing("Übertrage doc_index.enc an WebDAV/NAS...")
                        WebDavClient.uploadFile(
                            url = config.webDavUrl,
                            user = config.webDavUsername,
                            pass = config.webDavPassword,
                            remoteFileName = "doc_index.enc",
                            localFile = encryptedIndex
                        )
                    }

                    if (config.enableGoogleDrive && config.googleDriveConnected && config.googleDriveAccessToken.isNotBlank()) {
                        _syncState.value = SyncState.Syncing("Übertrage doc_index.enc an Google Drive...")
                        GoogleDriveClient.uploadEncryptedFile(
                            accessToken = config.googleDriveAccessToken,
                            file = encryptedIndex,
                            remoteName = "doc_index.enc"
                        )
                    }
                }
            } finally {
                if (encryptedIndex.exists()) {
                    encryptedIndex.delete()
                }
            }

            if (lowBattery && pendingDocs.isNotEmpty()) {
                _syncState.value = SyncState.Error("Metadaten-Index aktualisiert. PDF-Uploads wurden wegen niedrigem Akkustand (< 20 %) pausiert.")
            } else {
                _syncState.value = SyncState.Success(
                    uploadedCount = uploadedCount,
                    indexGenerated = indexExists,
                    timestamp = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            _syncState.value = SyncState.Error(e.localizedMessage ?: "Sync-Fehler aufgetreten")
        }
    }

    /**
     * Testet die Erreichbarkeit eines NAS / WebDAV Servers mit echtem HTTP-Handshake
     */
    suspend fun testWebDavConnection(url: String, user: String, pass: String): Result<String> {
        return WebDavClient.testConnection(url, user, pass)
    }
}
