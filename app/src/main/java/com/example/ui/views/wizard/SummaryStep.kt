package com.example.ui.views.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(
                text = if (isManualReconfig) "Konfiguration bereit zur Übernahme" else "Bereit zum Loslegen! 🎉",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isManualReconfig) "Deine Anpassungen wurden vorbereitet." else "Dein persönlicher Dokumenten-Tresor ist vollständig eingerichtet.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ansicht: ${appViewLevel.name} • Jederzeit flexibel anpassbar",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Alle Parameter lassen sich später im Menü unter ⚙️ Einstellungen oder 'Ansicht & Layout' ändern.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        val storageText = when (pdfSettings.baseStorageLocation) {
            "DOCUMENTS" -> "Geräte-Dokumente (/Documents)"
            "CUSTOM" -> "Benutzerdefiniert (${pdfSettings.customStoragePath.takeLast(20).ifBlank { "Eigener Ordner" }})"
            else -> "Geschützter App-Tresor (Sandbox)"
        }
        val prefixOpt = PREFIX_STYLE_OPTIONS.firstOrNull { it.id == pdfSettings.folderPrefixStyle } ?: PREFIX_STYLE_OPTIONS.first()
        val namingOpt = DOC_NAMING_STYLE_OPTIONS.firstOrNull { it.id == pdfSettings.docNamingStyle } ?: DOC_NAMING_STYLE_OPTIONS.first()

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("📁 Ablagestruktur & Dateinamen", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text("• Speicherort: $storageText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Ordner-Nummerierung: ${prefixOpt.title} (z.B. ${prefixOpt.example})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Dateinamen-Muster: ${namingOpt.title} (z.B. ${namingOpt.example})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val isAutoCapture = scannerSettings.triggerMode == "AUTO_DETECT" || scannerSettings.scanMode == "AUTO" || scannerSettings.tripodAutoScan
        val triggerText = if (isAutoCapture) "⚡ Auto-Scan (automatische Erkennung)" else "⚙️ Manuell (per Klick/Taste)"
        val colorText = when (pdfSettings.defaultColorMode) {
            "BW" -> "Immer Schwarz/Weiß (Kompakt)"
            "COLOR" -> "Immer Farbe (Original)"
            else -> "Vollautomatisch (Text S/W, Logos farbig)"
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Text("📸 Scanner & Bildoptimierung", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text("• Auslöser: $triggerText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Farbmodus: $colorText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Filter: Auto-Entzerrung (Deskew) & Schattenbereinigung aktiv", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val promptFocusText = when (llmConfig.systemPromptFocus) {
            "FINANCE" -> "Finanzen & Belege"
            "CONTRACTS" -> "Verträge & Fristen"
            else -> "Allrounder"
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                    Text("🧠 Lokaler KI Agent & OCR", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text("• Modell: ${activeModel?.name ?: "SmolLM2 135M"} (100% Offline & DSGVO-sicher)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Fokus: $promptFocusText • Inferenz: ${llmConfig.threadCount} CPU-Threads", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• OCR: Lokale Texterkennung & automatische Verschlagwortung aktiv", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val secMethodLabel = when (securityMethod) {
            "PASSKEY" -> "🔑 Passkey (FIDO2 Hardware-Keystore)"
            "PASSWORD" -> "🔒 Zero-Knowledge Master-Passwort (AES-256)"
            else -> "📱 Biometrie / PIN"
        }
        val bioLabel = if (biometricEnabled) "Aktiviert" else "Deaktiviert"

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                    Text("🔒 Sicherheit & Verschlüsselung", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text("• Schutz-Verfahren: $secMethodLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Biometrische Schnell-Entsperrung: $bioLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Standard: AES-256 GCM • Android Hardware-Sicherheitsmodul", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val backupTiers = buildList {
            add("1. Smartphone-Speicher (Offline)")
            if (cloudSyncConfig.enableManualUsbExport) add("2. USB-Stick Export")
            if (cloudSyncConfig.enableGoogleDrive) add("3. Google Drive Vault")
            if (cloudSyncConfig.enableWebDavNas) add("4. Heim-NAS (WebDAV)")
            if (p2pServerStatus.isRunning) add("5. Lokale Geräte-Verbindung")
        }.joinToString(" • ")

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("🛡️ Backup & Ausfallsicherheit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text("• Aktive Stufen: $backupTiers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Verschlüsselung: 100% End-to-End mit Zero-Knowledge Master-Key", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (!isManualReconfig) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "💡 Interaktive Erste-Schritte Tour",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Nach 'Einrichtung abschließen' startet automatisch eine kurze interaktive Führung (Scanner, InBox, KI-Agent & Dashboard). Du kannst die Tour jederzeit oben rechts oder unten mit 'Tour überspringen' abbrechen.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
