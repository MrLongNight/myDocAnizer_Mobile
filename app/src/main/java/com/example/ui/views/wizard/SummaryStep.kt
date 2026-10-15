package com.example.ui.views.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.*
import com.example.service.HuggingFaceModelInfo
import com.example.ui.DocAnizerViewModel

@Composable
fun StepReadySummary(
    viewModel: DocAnizerViewModel,
    scannerSettings: ScannerSettings,
    pdfSettings: PdfSettings,
    availableModels: List<HuggingFaceModelInfo>,
    biometricEnabled: Boolean,
    isManualReconfig: Boolean = false
) {
    val activeModel = availableModels.firstOrNull { it.isSelected } ?: availableModels.firstOrNull()
    val securityMethod by viewModel.securityMethod.collectAsState()
    val llmConfig by viewModel.llmInferenceConfig.collectAsState()
    val cloudSyncConfig by viewModel.cloudSyncConfig.collectAsState()
    val appViewLevel by viewModel.appViewLevel.collectAsState()
    val p2pServerStatus by viewModel.p2pServerStatus.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            Text(
                text = if (isManualReconfig) "Zusammenfassung" else "Alles bereit! 🎉",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isManualReconfig) "Deine Änderungen im Überblick." else "Dein Tresor ist fertig eingerichtet.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Kompaktere Darstellung der Sektionen
        SummarySection(
            icon = Icons.Default.Tune,
            title = "Ansicht & Layout",
            color = MaterialTheme.colorScheme.primary
        ) {
            Text("Modus: ${appViewLevel.name}", style = MaterialTheme.typography.labelSmall)
            Text("Alle Einstellungen sind jederzeit im Menü änderbar.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val storageText = when (pdfSettings.baseStorageLocation) {
            "DOCUMENTS" -> "Dokumente (/Documents)"
            "CUSTOM" -> "Eigener Ordner"
            else -> "App-Tresor (Sandbox)"
        }
        SummarySection(
            icon = Icons.Default.Folder,
            title = "Ablage & Dateinamen",
            color = MaterialTheme.colorScheme.primary
        ) {
            Text("• Ort: $storageText", style = MaterialTheme.typography.labelSmall)
            Text("• Namens-Muster aktiv", style = MaterialTheme.typography.labelSmall)
        }

        val isAutoCapture = scannerSettings.triggerMode == "AUTO_DETECT" || scannerSettings.scanMode == "AUTO"
        SummarySection(
            icon = Icons.Default.CameraAlt,
            title = "Scanner",
            color = MaterialTheme.colorScheme.secondary
        ) {
            Text("• Modus: ${if (isAutoCapture) "Auto-Scan" else "Manuell"}", style = MaterialTheme.typography.labelSmall)
            Text("• Auto-Entzerrung & Filter aktiv", style = MaterialTheme.typography.labelSmall)
        }

        SummarySection(
            icon = Icons.Default.Psychology,
            title = "KI Agent & OCR",
            color = MaterialTheme.colorScheme.tertiary
        ) {
            Text("• Modell: ${activeModel?.name ?: "SmolLM2"} (Offline)", style = MaterialTheme.typography.labelSmall)
            Text("• Texterkennung & Verschlagwortung aktiv", style = MaterialTheme.typography.labelSmall)
        }

        val secMethodLabel = when (securityMethod) {
            "PASSKEY" -> "Passkey"
            "PASSWORD" -> "Passwort"
            else -> "Biometrie"
        }
        SummarySection(
            icon = Icons.Default.Security,
            title = "Sicherheit",
            color = Color(0xFF16A34A)
        ) {
            Text("• Schutz: $secMethodLabel (AES-256)", style = MaterialTheme.typography.labelSmall)
            Text("• Biometrie: ${if (biometricEnabled) "An" else "Aus"}", style = MaterialTheme.typography.labelSmall)
        }

        val backupCount = buildList {
            add("Lokal")
            if (cloudSyncConfig.enableManualUsbExport) add("USB")
            if (cloudSyncConfig.enableGoogleDrive) add("Drive")
            if (cloudSyncConfig.enableWebDavNas) add("NAS")
        }.size
        SummarySection(
            icon = Icons.Default.CloudSync,
            title = "Backup",
            color = MaterialTheme.colorScheme.primary
        ) {
            Text("• Stufen: $backupCount Ziele eingerichtet", style = MaterialTheme.typography.labelSmall)
            Text("• 100% End-to-End verschlüsselt", style = MaterialTheme.typography.labelSmall)
        }

        if (!isManualReconfig) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Explore, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Im Anschluss startet eine kurze Tour durch die App.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                content()
            }
        }
    }
}
