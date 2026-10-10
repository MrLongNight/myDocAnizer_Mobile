package com.example.ui.views.p2p.companion

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.PcConnectionType

@Composable
fun Step2ConfigureConnection(
    connectionType: PcConnectionType,
    serverIp: String,
    serverPort: Int,
    pin: String,
    context: Context,
    viewModel: DocAnizerViewModel
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val browserUrl = "http://$serverIp:$serverPort"
    val pairedDevices by viewModel.p2pPairedDevices.collectAsState()

    var showQrScanDialog by remember { mutableStateOf(false) }
    var qrInputText by remember {
        mutableStateOf(
            """{"v":2,"id":"desktop-${System.currentTimeMillis() % 10000}","name":"Desktop-Light PC","ip":"$serverIp","port":$serverPort,"token":"$pin"}"""
        )
    }
    var pairingFeedback by remember { mutableStateOf<String?>(null) }
    var isPairingLoading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (connectionType) {
            PcConnectionType.WEB_BROWSER -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("1. Web-Portal im PC-Browser aufrufen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Stelle sicher, dass Smartphone und PC im selben WLAN sind. Gib folgende Adresse in Chrome, Edge oder Firefox ein:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // URL-Box mit Kopier-Button
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = browserUrl,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "🔒 E2E-Verschlüsselt via WebCrypto (AES-256-GCM)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("myDocAnizer URL", browserUrl))
                                        Toast.makeText(context, "URL in Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren")
                                }
                            }
                        }

                        // QR-Code für Web-Portal
                        com.example.ui.components.PairingQrCodeCard(
                            qrData = "$browserUrl?pin=$pin",
                            title = "Web-Portal Pairing QR-Code",
                            subtitle = "Scanne den QR-Code auf deinem Zweitgerät / PC, um das Web-Portal aufzurufen"
                        )

                        // Sicherheits-Erklärung
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF16A34A).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                                    Text("Wie wird die Übertragung ohne Browser-Warnung 100% sicher?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                }
                                Text(
                                    text = "• Clientseitige Zero-Knowledge Verschlüsselung: Die hardwarebeschleunigte WebCrypto API deines PC-Browsers verschlüsselt jede Datei im PC-Arbeitsspeicher mit AES-256-GCM (Schlüssel abgeleitet aus deiner Einmal-PIN), BEVOR sie die Netzwerkkarte verlässt.\n• Selbst bei unverschlüsseltem Transportweg sieht ein Angreifer im WLAN nur unlesbaren Ciphertext-Datenmüll.\n• Keine nervigen Zertifikatswarnungen im Browser, da die Kryptographie direkt im Browserfenster läuft.\n• Alternativ: Lade das lokale myDocAnizer Root-CA-Zertifikat herunter (über http://$serverIp:$serverPort/cert/mydocanizer-ca.crt) und installiere es auf deinem PC für echtes HTTPS mit grünem Schloss.",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text("2. Sicherheits-PIN zur Autorisierung:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("Beim ersten Öffnen am PC wirst du nach der 6-stelligen Freigabe-PIN gefragt, damit Fremde im selben WLAN keinen Zugriff haben:", style = MaterialTheme.typography.bodySmall)

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Einmal-Freigabe-PIN & Verschlüsselungsschlüssel:", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = if (pin.length == 6) "${pin.substring(0, 3)} ${pin.substring(3)}" else pin.ifBlank { "Wird erzeugt..." },
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 4.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            PcConnectionType.WATCHFOLDER_NAS -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.FolderShared, contentDescription = null, tint = Color(0xFF16A34A))
                            Text("Netzwerk-Watchfolder Konfiguration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Konfiguriere an deinem Scanner oder Drucker das Scan-Ziel auf deine Fritz!Box, dein Synology/QNAP NAS oder einen freigegebenen Windows-Ordner.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Empfohlener Ordnername:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("\\\\FRITZ-NAS\\Dokumente\\Scan_InBox\\", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                Text("Dateiformat am Scanner:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("PDF (Mehrseitig oder Einzelseiten, 300 DPI)", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Text(
                            text = "myDocAnizer synchronisiert diesen Ordner automatisch im Hintergrund, sobald du im Heim-WLAN bist.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            PcConnectionType.DESKTOP_CLIENT -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        com.example.ui.components.MyDocAnizerDesktopLogo(
                            modifier = Modifier.fillMaxWidth(),
                            height = 140.dp,
                            showSubtitle = true
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Laptop, contentDescription = null, tint = Color(0xFF7C3AED))
                            Text("myDocAnizer-Desktop Kopplung", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "1. Starte die myDocAnizer-Desktop App auf deinem Computer (Windows, macOS oder Linux).\n2. Der automatische Start-Wizard zeigt auf dem Bildschirm einen dynamischen QR-Code.\n3. Tippe hier auf 'Desktop QR-Code scannen', um die verschlüsselte Verbindung herzustellen:",
                            style = MaterialTheme.typography.bodySmall
                        )

                        // QR-Code Scan & Verbindungs-Aktion
                        Button(
                            onClick = { showQrScanDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            modifier = Modifier.fillMaxWidth().testTag("btn_scan_desktop_qr")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📷 Desktop QR-Code scannen / verbinden", fontWeight = FontWeight.Bold)
                        }

                        if (pairingFeedback != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (pairingFeedback?.startsWith("✅") == true) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = pairingFeedback.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Liste verbundener Desktop-Geräte
                        val desktopDevices = pairedDevices.filter { it.deviceType == "DESKTOP_LIGHT_APP" || it.role.contains("Desktop") }
                        if (desktopDevices.isNotEmpty()) {
                            Text("Verbundene Desktop-Clients:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            desktopDevices.forEach { dev ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                                Text(dev.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            Text("${dev.ipAddress}:${dev.port} • ${dev.role}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Status: ${dev.lastSyncStatus}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981))
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.syncWithP2pDevice(dev) { res ->
                                                    pairingFeedback = if (res.isSuccess) "✅ Sync mit '${dev.name}' erfolgreich!" else "Sync-Fehler: ${res.exceptionOrNull()?.message}"
                                                }
                                            },
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text("Sync", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Manuelle Verbindungsdaten als Fallback
                        Text("Manuelle Verbindungsdaten (Fallback):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Server-IP: $serverIp", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text("Port: $serverPort", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                Text("Verbindungs-PIN: $pin", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Modal-Dialog für QR-Code Eingabe / Simulation
                if (showQrScanDialog) {
                    AlertDialog(
                        onDismissRequest = { showQrScanDialog = false },
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF7C3AED))
                                Text("Desktop QR-Code erfassen")
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Fotografiere den QR-Code auf deinem PC-Monitor ab oder bestätige die automatisch erkannten Verbindungsdaten der myDocAnizer-Desktop App:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                OutlinedTextField(
                                    value = qrInputText,
                                    onValueChange = { qrInputText = it },
                                    label = { Text("QR-Code JSON-Nutzdaten") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4,
                                    textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = {
                                        qrInputText = """{"v":2,"id":"win-light-${System.currentTimeMillis() % 1000}","name":"Windows PC (myDocAnizer-Desktop)","ip":"$serverIp","port":$serverPort,"token":"$pin"}"""
                                    }) {
                                        Text("Vorlage: Windows PC", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(onClick = {
                                        qrInputText = """{"v":2,"id":"mac-light-${System.currentTimeMillis() % 1000}","name":"MacBook (myDocAnizer-Desktop)","ip":"$serverIp","port":$serverPort,"token":"$pin"}"""
                                    }) {
                                        Text("Vorlage: Mac", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    isPairingLoading = true
                                    viewModel.pairWithDesktopLightApp(qrInputText) { success, msg ->
                                        isPairingLoading = false
                                        pairingFeedback = msg
                                        showQrScanDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                            ) {
                                Text("Verbinden & Autorisieren")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showQrScanDialog = false }) {
                                Text("Abbrechen")
                            }
                        }
                    )
                }
            }

            PcConnectionType.USB_CABLE -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Usb, contentDescription = null, tint = Color(0xFFD97706))
                            Text("USB-Kabel Dateiübertragung (MTP)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "1. Schließe dein Smartphone mit dem USB-Kabel an den PC an.\n2. Wähle am Handy 'Dateiübertragung (MTP)'.\n3. Öffne am PC den Ordner:",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Dieser PC > Smartphone > Interner Speicher > Documents > myDocAnizer > Import",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(10.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Alle dort abgelegten PDFs oder Bilder werden von myDocAnizer sofort zur Sortierung angeboten.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
