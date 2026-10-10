package com.example.ui.views

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.ImageProcessingService
import com.example.ui.DocAnizerViewModel

enum class PcConnectionType {
    WEB_BROWSER,      // 1. Lokales Web-Portal im Browser mit Zero-Knowledge AES-256 Verschlüsselung
    WATCHFOLDER_NAS,  // 2. Netzwerk-Scanner / SMBv3 / HTTPS-WebDAV Watchfolder
    DESKTOP_CLIENT,   // 3. Desktop-Scanner Tool (TLS 1.3 & mTLS für ScanSnap/Brother)
    USB_CABLE         // 4. USB-Kabel Direktübertragung (MTP / Air-Gapped)
}

/**
 * Eigenständiger, optionaler Einrichtungs-Assistent für PC-zu-App Schnittstellen & PC-Scannen.
 * Kann am Ende des Ersteinrichtungs-Wizards oder im Settings-Menü gestartet werden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcCompanionWizardDialog(
    viewModel: DocAnizerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val p2pServerStatus by viewModel.p2pServerStatus.collectAsState()
    val hostPin by viewModel.p2pHostPairingPin.collectAsState()

    var activeStep by rememberSaveable { mutableIntStateOf(1) } // 1: Auswahl, 2: Setup, 3: Ziel-Ablage, 4: Fertig
    var selectedType by rememberSaveable { mutableStateOf(PcConnectionType.WEB_BROWSER) }
    var autoSortWithAi by rememberSaveable { mutableStateOf(false) }
    var isSimulatingTestScan by remember { mutableStateOf(false) }
    var testScanFeedback by remember { mutableStateOf<String?>(null) }

    // Beim Öffnen sicherstellen, dass P2P-Server aktiv ist für Web & P2P
    LaunchedEffect(Unit) {
        if (!p2pServerStatus.isRunning) {
            viewModel.startP2pServer()
        }
        if (hostPin.isNullOrBlank()) {
            viewModel.startP2pHostPairingMode()
        }
    }

    BackHandler {
        if (activeStep > 1) {
            activeStep--
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pc_companion_wizard_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Header mit Schließen-Button & Schrittzähler
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Computer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "PC- & Scanner-Anbindung",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Schritt $activeStep von 4: " + when (activeStep) {
                                    1 -> "Verbindungsweg wählen"
                                    2 -> "Schnittstelle einrichten"
                                    3 -> "Ablage-Verhalten"
                                    else -> "Bereit & Test"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { activeStep.toFloat() / 4f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Inhalt des aktuellen Schritts
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (activeStep) {
                        1 -> com.example.ui.views.p2p.companion.Step1SelectConnectionType(
                            selectedType = selectedType,
                            onSelect = { selectedType = it }
                        )
                        2 -> com.example.ui.views.p2p.companion.Step2ConfigureConnection(
                            connectionType = selectedType,
                            serverIp = p2pServerStatus.localIp,
                            serverPort = p2pServerStatus.port,
                            pin = hostPin.orEmpty(),
                            context = context,
                            viewModel = viewModel
                        )
                        3 -> com.example.ui.views.p2p.companion.Step3ConfigureDestination(
                            autoSortWithAi = autoSortWithAi,
                            onToggleAutoSort = { autoSortWithAi = it }
                        )
                        4 -> com.example.ui.views.p2p.companion.Step4SummaryAndTest(
                            connectionType = selectedType,
                            serverIp = p2pServerStatus.localIp,
                            serverPort = p2pServerStatus.port,
                            autoSortWithAi = autoSortWithAi,
                            isSimulating = isSimulatingTestScan,
                            feedback = testScanFeedback,
                            onSendTestScan = {
                                isSimulatingTestScan = true
                                testScanFeedback = null
                                // Simuliert die Übertragung eines Testdokuments vom PC
                                val testBmp = ImageProcessingService.createSampleDocumentBitmap(
                                    title = "PC_Test_Dokument",
                                    sender = "Desktop-Scanner",
                                    modeBw = false
                                )
                                viewModel.saveSinglePageBulk(testBmp) { doc ->
                                    isSimulatingTestScan = false
                                    testScanFeedback = "✅ Test-Dokument '${doc.title}' erfolgreich empfangen & archiviert!"
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer Buttons (Zurück & Weiter/Fertig)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeStep > 1) {
                        TextButton(onClick = { activeStep-- }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Zurück")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text("Abbrechen")
                        }
                    }

                    Button(
                        onClick = {
                            if (activeStep < 4) {
                                activeStep++
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.testTag("btn_pc_wizard_next")
                    ) {
                        Text(if (activeStep == 4) "Einrichtung abschließen" else "Weiter")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (activeStep == 4) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
