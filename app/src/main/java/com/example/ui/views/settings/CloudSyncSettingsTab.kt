package com.example.ui.views.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.service.CloudSyncWorkerService
import com.example.service.WebDavClient
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.launch
import java.util.*

@Composable
fun CloudSyncSettingsTab(viewModel: DocAnizerViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncState by viewModel.syncState.collectAsState()
    val syncPasswordKey by viewModel.syncPasswordKey.collectAsState()
    val autoSyncEnabled by viewModel.autoCloudSyncEnabled.collectAsState()
    val lastSyncTime by viewModel.lastSyncTimestamp.collectAsState()
    val allDocs by viewModel.allDocuments.collectAsState()
    val cloudSyncConfig by viewModel.cloudSyncConfig.collectAsState()

    var passwordInput by remember(syncPasswordKey) { mutableStateOf(syncPasswordKey) }
    var showPassword by remember { mutableStateOf(false) }

    var webDavUrl by remember(cloudSyncConfig.webDavUrl) { mutableStateOf(cloudSyncConfig.webDavUrl) }
    var webDavUser by remember(cloudSyncConfig.webDavUsername) { mutableStateOf(cloudSyncConfig.webDavUsername) }
    var webDavPass by remember(cloudSyncConfig.webDavPassword) { mutableStateOf(cloudSyncConfig.webDavPassword) }
    var isTestingWebDav by remember { mutableStateOf(false) }
    var webDavTestResult by remember { mutableStateOf<String?>(null) }
    var webDavTestSuccess by remember { mutableStateOf(false) }

    val usbDrives by viewModel.usbDrives.collectAsState()
    var showAddUsbDialog by remember { mutableStateOf(false) }
    var newUsbName by remember { mutableStateOf("") }

    var isExportingBackup by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var restoreFeedback by remember { mutableStateOf<String?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var selectedRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var restorePasswordInput by remember { mutableStateOf(syncPasswordKey) }
    var activeInfoDetailKey by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    if (activeInfoDetailKey != null) {
        val detail = STORAGE_INFO_DETAILS[activeInfoDetailKey]
        if (detail != null) {
            AlertDialog(
                onDismissRequest = { activeInfoDetailKey = null },
                title = { Text(detail.title) },
                text = { Text(detail.howItWorks + "\n\n" + detail.securityExplanation) },
                confirmButton = { Button(onClick = { activeInfoDetailKey = null }) { Text("OK") } }
            )
        }
    }

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
        // Zero-Knowledge Banner
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Ende-zu-Ende Verschlüsselung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Backups werden lokal verschlüsselt.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 1-KLICK-SICHERUNG
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

        // MASTER-PASSWORT
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Master-Passwort & Verschlüsselungs-Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Master-Passwort") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { viewModel.setSyncPasswordKey(passwordInput) }) {
                    Text("Passwort speichern")
                }
            }
        }

        // CLOUD & NAS
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cloud & NAS Synchronisation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                // WebDAV
                Text("WebDAV / NAS", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(value = webDavUrl, onValueChange = { webDavUrl = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = webDavUser, onValueChange = { webDavUser = it }, label = { Text("Benutzer") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = webDavPass, onValueChange = { webDavPass = it }, label = { Text("Passwort") }, modifier = Modifier.fillMaxWidth())
                
                Button(onClick = {
                    viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(
                        webDavUrl = webDavUrl,
                        webDavUsername = webDavUser,
                        webDavPassword = webDavPass,
                        enableWebDavNas = true
                    ))
                }) {
                    Text("WebDAV Einstellungen speichern")
                }
            }
        }
    }

    if (showRestoreDialog && selectedRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Wiederherstellen?") },
            text = { Text("Möchtest du das Backup importieren?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.restoreEncryptedBackup(selectedRestoreUri!!, restorePasswordInput) { _, _ ->
                        showRestoreDialog = false
                    }
                }) { Text("Ja") }
            }
        )
    }
}
