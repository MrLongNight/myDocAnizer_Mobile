package com.example.ui.views.p2p

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.PairingResult
import com.example.ui.DocAnizerViewModel

/**
 * Dialog-Assistent zum Koppeln zweier Android-Smartphones/Tablets per P2P
 */
@Composable
fun PairingWizardDialog(
    viewModel: DocAnizerViewModel,
    serverIp: String,
    serverPort: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Host (Code anzeigen), 1: Client (IP eingeben)
    var activeHostPin by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        activeHostPin = viewModel.startP2pHostPairingMode()
    }

    var peerIpInput by remember { mutableStateOf("192.168.1.") }
    var peerPortInput by remember { mutableStateOf("8765") }
    var peerPinInput by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCameraQrScanner by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zwei Android-Apps verbinden") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("1. Code & QR-Code") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("2. Scannen / Eingabe") }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // HOST MODUS
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Scanne diesen QR-Code mit der Kamera deines Zweitgeräts oder gib die Verbindungsdaten manuell ein:",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )

                        val hostPayload = """{"v":2,"id":"android-${serverIp.hashCode()}","name":"myDocAnizer App","ip":"$serverIp","port":$serverPort,"token":"$activeHostPin"}"""

                        com.example.ui.components.PairingQrCodeCard(
                            qrData = hostPayload,
                            title = "Dual-Sync Pairing QR-Code",
                            subtitle = "Auf dem Zweitgerät '2. Scannen / Eingabe' antippen & abfotografieren"
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("IP & Port:", style = MaterialTheme.typography.labelSmall)
                                    Text("$serverIp:$serverPort", style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("PIN:", style = MaterialTheme.typography.labelSmall)
                                    Text(activeHostPin.ifBlank { "..." }, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                } else {
                    // CLIENT MODUS
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. QR-CODE CAMERA SCANNER BUTTON
                        Button(
                            onClick = { showCameraQrScanner = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(androidx.compose.material.icons.Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("📷 QR-Code mit Kamera scannen", fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(
                            text = "Oder trage IP-Adresse und PIN manuell ein:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = peerIpInput,
                            onValueChange = { peerIpInput = it },
                            label = { Text("IP-Adresse des Zweitgeräts") },
                            placeholder = { Text("192.168.1.50") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = peerPortInput,
                                onValueChange = { peerPortInput = it },
                                label = { Text("Port") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = peerPinInput,
                                onValueChange = { peerPinInput = it },
                                label = { Text("6-Stelliger PIN") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        errorMessage?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                isConnecting = true
                                errorMessage = null
                                val portInt = peerPortInput.toIntOrNull() ?: 8765
                                viewModel.pairWithP2pDevice(
                                    ip = peerIpInput.trim(),
                                    port = portInt,
                                    pin = peerPinInput.trim()
                                ) { res ->
                                    isConnecting = false
                                    res.onSuccess { pairingRes ->
                                        when (pairingRes) {
                                            is PairingResult.Success -> {
                                                Toast.makeText(context, "Erfolgreich mit ${pairingRes.device.name} gekoppelt!", Toast.LENGTH_LONG).show()
                                                onDismiss()
                                            }
                                            is PairingResult.Pending -> {
                                                Toast.makeText(context, "Autorisierungsanfrage gesendet. Auf Empfängergerät bestätigen!", Toast.LENGTH_LONG).show()
                                                onDismiss()
                                            }
                                        }
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Verbindungsfehler"
                                    }
                                }
                            },
                            enabled = !isConnecting && peerIpInput.isNotBlank() && peerPinInput.length >= 4,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Verbinde im WLAN...")
                            } else {
                                Icon(Icons.Default.Devices, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gerät koppeln")
                            }
                        }
                    }
                }

                if (showCameraQrScanner) {
                    com.example.ui.components.PairingQrScannerModal(
                        onDismiss = { showCameraQrScanner = false },
                        onQrScanned = { scannedIp, scannedPort, scannedPin ->
                            peerIpInput = scannedIp
                            peerPortInput = scannedPort.toString()
                            peerPinInput = scannedPin
                            showCameraQrScanner = false
                            if (scannedIp.isNotBlank() && scannedPin.isNotBlank()) {
                                isConnecting = true
                                viewModel.pairWithP2pDevice(scannedIp, scannedPort, scannedPin) { res ->
                                    isConnecting = false
                                    res.onSuccess { pairingRes ->
                                        Toast.makeText(context, "Erfolgreich gekoppelt!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }.onFailure { err ->
                                        errorMessage = err.localizedMessage ?: "Verbindungsfehler"
                                    }
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}
