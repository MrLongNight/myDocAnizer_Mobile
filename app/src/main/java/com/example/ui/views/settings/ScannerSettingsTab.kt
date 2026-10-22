package com.example.ui.views.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.CollapsibleSettingsCard

@Composable
fun ScannerSettingsTab(viewModel: DocAnizerViewModel) {
    val scannerSettings by viewModel.scannerSettings.collectAsState()

    var isModeExpanded by remember { mutableStateOf(false) }
    var isTripodExpanded by remember { mutableStateOf(false) }
    var isTriggerExpanded by remember { mutableStateOf(false) }
    var isHardwareExpanded by remember { mutableStateOf(false) }
    var isFlashExpanded by remember { mutableStateOf(false) }
    var isFeedbackExpanded by remember { mutableStateOf(false) }
    var isCropPreviewExpanded by remember { mutableStateOf(false) }
    var isDebugExpanded by remember { mutableStateOf(false) }

    val allExpanded = isModeExpanded && isTripodExpanded && isTriggerExpanded && isHardwareExpanded && isFlashExpanded && isFeedbackExpanded && isCropPreviewExpanded && isDebugExpanded

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    val nextState = !allExpanded
                    isModeExpanded = nextState
                    isTripodExpanded = nextState
                    isTriggerExpanded = nextState
                    isHardwareExpanded = nextState
                    isFlashExpanded = nextState
                    isFeedbackExpanded = nextState
                    isCropPreviewExpanded = nextState
                    isDebugExpanded = nextState
                }
            ) {
                Text(
                    text = if (allExpanded) "Alle zuklappen" else "Alle aufklappen",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        // 1. Scan-Modus
        CollapsibleSettingsCard(
            title = "Scan-Modus",
            subtitle = if (scannerSettings.scanMode == "AUTO") "⚡ Auto-Scan (Standard)" else "⚙️ Manuell",
            icon = Icons.Default.Bolt,
            isExpanded = isModeExpanded,
            onToggle = { isModeExpanded = !isModeExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Standard-Scan-Modus",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Im Auto-Scan-Modus werden Dokumente ohne Zwischenklicks fortlaufend erfasst.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = scannerSettings.scanMode == "AUTO",
                        onClick = { viewModel.updateScannerSettings(scannerSettings.copy(scanMode = "AUTO")) },
                        label = { Text("⚡ Auto-Scan (Standard)") },
                        leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = scannerSettings.scanMode == "MANUAL",
                        onClick = { viewModel.updateScannerSettings(scannerSettings.copy(scanMode = "MANUAL")) },
                        label = { Text("⚙️ Manuell") },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        // 1b. Dokumenten-Vorschau & Zuschnitt
        CollapsibleSettingsCard(
            title = "Dokumenten-Vorschau & Zuschnitt",
            subtitle = if (scannerSettings.enableCropPreview) "Aktiv (Eckpunkt-Korrektur mit Lupe & Filter)" else "Deaktiviert (Sofort-Ablage)",
            icon = Icons.Default.Crop,
            isExpanded = isCropPreviewExpanded,
            onToggle = { isCropPreviewExpanded = !isCropPreviewExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📐 Interaktive Vorschau & Zuschnitt",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ermöglicht nach der Aufnahme das pixelgenaue Ausrichten der 4 Ecken mit Lupe sowie eine Filter-Vorschau (Auto / S/W / Farbe). Währenddessen analysiert die KI das Dokument bereits im Hintergrund.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = scannerSettings.enableCropPreview,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(enableCropPreview = it)) }
                    )
                }
            }
        }

        // 2. Stativ-Dauerscan
        CollapsibleSettingsCard(
            title = "Stativ-Dauerscan (Hands-Free)",
            subtitle = if (scannerSettings.tripodAutoScan) "Aktiv (${scannerSettings.preScanDelaySeconds}s Vorlauf / ${scannerSettings.postScanDelaySeconds}s Nachlauf)" else "Deaktiviert",
            icon = Icons.Default.VideoCameraFront,
            isExpanded = isTripodExpanded,
            onToggle = { isTripodExpanded = !isTripodExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔭 Stativ-Dauerscan aktivieren",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Handy im Stativ fixieren: Dokumente werden automatisch erkannt, die Beruhigungszeit abgewartet und sofort ausgelöst.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = scannerSettings.tripodAutoScan,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(tripodAutoScan = it)) }
                    )
                }

                if (scannerSettings.tripodAutoScan) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "⏱️ Vorlaufzeit vor Erkennungsstart:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Zeit bis die automatische Erkennung nach dem Start beginnt (0s = sofort)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = if (scannerSettings.preScanDelaySeconds == 0) "Sofort (0s)" else "${scannerSettings.preScanDelaySeconds} s",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = scannerSettings.preScanDelaySeconds.toFloat(),
                            onValueChange = { newVal ->
                                val sec = newVal.toInt().coerceIn(0, 60)
                                viewModel.updateScannerSettings(scannerSettings.copy(preScanDelaySeconds = sec, autoScanDelayMs = sec * 1000L))
                            },
                            valueRange = 0f..60f,
                            steps = 59,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "🔄 Pausenzeit nach Scan (Blattwechsel):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Pausendauer bis die Erkennung für das nächste Dokument wieder aktiv ist",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = if (scannerSettings.postScanDelaySeconds == 0) "Sofort (0s)" else "${scannerSettings.postScanDelaySeconds} s",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = scannerSettings.postScanDelaySeconds.toFloat(),
                            onValueChange = { newVal ->
                                val sec = newVal.toInt().coerceIn(0, 60)
                                viewModel.updateScannerSettings(scannerSettings.copy(postScanDelaySeconds = sec))
                            },
                            valueRange = 0f..60f,
                            steps = 59,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        text = "Blattwechsel-Empfindlichkeit:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LOW" to "Träge", "MEDIUM" to "Normal", "HIGH" to "Schnell").forEach { (lvl, label) ->
                            FilterChip(
                                selected = scannerSettings.sheetChangeSensitivity == lvl,
                                onClick = { viewModel.updateScannerSettings(scannerSettings.copy(sheetChangeSensitivity = lvl)) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Auslöser-Verhalten
        CollapsibleSettingsCard(
            title = "Auslöser-Methode & Countdown",
            subtitle = when (scannerSettings.triggerMode) {
                "AUTO_DETECT" -> "Automatisch bei Erkennung"
                "COUNTDOWN" -> "Selbstauslöser (${scannerSettings.countdownSeconds}s)"
                else -> "Manuell per Auslöser-Button"
            },
            icon = Icons.Default.Timer,
            isExpanded = isTriggerExpanded,
            onToggle = { isTriggerExpanded = !isTriggerExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val triggerOptions = listOf(
                    Triple("AUTO_DETECT", "Automatisch bei Erkennung", "Löst automatisch aus, sobald ein Dokument stabil erfasst ist"),
                    Triple("COUNTDOWN", "Zeitverzögerter Selbstauslöser", "Startet einen Countdown vor der Aufnahme"),
                    Triple("MANUAL", "Manuell (Display / Hardware)", "Löst ausschließlich per Klick oder Tastendruck aus")
                )

                triggerOptions.forEach { (key, title, desc) ->
                    val isSelected = scannerSettings.triggerMode == key
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateScannerSettings(scannerSettings.copy(triggerMode = key)) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.updateScannerSettings(scannerSettings.copy(triggerMode = key)) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (scannerSettings.triggerMode == "COUNTDOWN") {
                    Text(
                        text = "Countdown-Dauer:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2, 3, 5, 10).forEach { sec ->
                            FilterChip(
                                selected = scannerSettings.countdownSeconds == sec,
                                onClick = { viewModel.updateScannerSettings(scannerSettings.copy(countdownSeconds = sec)) },
                                label = { Text("$sec s") }
                            )
                        }
                    }
                }
            }
        }

        // 4. Hardware & Tasten
        CollapsibleSettingsCard(
            title = "Hardware-Tasten & Stapelscan",
            subtitle = "Lautstärketasten: ${if (scannerSettings.enableHardwareButtons) "An" else "Aus"} • Stapelscan: ${if (scannerSettings.isBulkMode) "An" else "Aus"}",
            icon = Icons.Default.Keyboard,
            isExpanded = isHardwareExpanded,
            onToggle = { isHardwareExpanded = !isHardwareExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val isManualMode = scannerSettings.scanMode == "MANUAL"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lautstärketasten als Auslöser",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isManualMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        Text(
                            text = if (isManualMode) "Lauter/Leiser-Tasten am Gerät lösen den Scan sofort aus." else "Nur im manuellen Scan-Modus verfügbar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = scannerSettings.enableHardwareButtons && isManualMode,
                        enabled = isManualMode,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(enableHardwareButtons = it)) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Stapelverarbeitung (Bulk-Modus)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Dokument sofort im Zielordner ablegen und direkt für das nächste Dokument bereit sein.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = scannerSettings.isBulkMode,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(isBulkMode = it)) }
                    )
                }
            }
        }

        // 5. Blitz & Beleuchtung
        CollapsibleSettingsCard(
            title = "Kamera-Licht & Blitzvoreinstellung",
            subtitle = when (scannerSettings.flashMode) {
                "FLASH" -> "Blitz aktiv"
                "TORCH" -> "Dauerlicht"
                else -> "Aus"
            },
            icon = Icons.Default.FlashOn,
            isExpanded = isFlashExpanded,
            onToggle = { isFlashExpanded = !isFlashExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("OFF" to "Aus", "FLASH" to "Blitz", "TORCH" to "Dauerlicht").forEach { (mode, label) ->
                        FilterChip(
                            selected = scannerSettings.flashMode == mode,
                            onClick = { viewModel.updateScannerSettings(scannerSettings.copy(flashMode = mode)) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        // Diagnose der Live-Dokumentenerkennung (standardmäßig deaktiviert)
        CollapsibleSettingsCard(
            title = "Dokumentenerkennung – Debug",
            subtitle = if (scannerSettings.debugDocumentDetection) "Diagnosewerte im Preview sichtbar" else "Aus",
            icon = Icons.Default.BugReport,
            isExpanded = isDebugExpanded,
            onToggle = { isDebugExpanded = !isDebugExpanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Erkennungswerte im Kamerabild", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Zeigt Confidence, Ablehnungsgrund, Schärfe und Bewegung direkt in der Scan-Vorschau.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = scannerSettings.debugDocumentDetection,
                    onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(debugDocumentDetection = it)) }
                )
            }
        }

        // 6. Akustisches Feedback & Vibration
        CollapsibleSettingsCard(
            title = "Signalton & Vibrationsfeedback",
            subtitle = "Ton: ${scannerSettings.soundProfile} • Vibration: ${if (scannerSettings.enableVibration) "An" else "Aus"}",
            icon = Icons.Default.VolumeUp,
            isExpanded = isFeedbackExpanded,
            onToggle = { isFeedbackExpanded = !isFeedbackExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SUCCESS_BEEP" to "Gong", "CLICK" to "Klick", "CHIME" to "Chime", "MUTE" to "Stumm").forEach { (profile, label) ->
                        FilterChip(
                            selected = scannerSettings.soundProfile == profile,
                            onClick = {
                                viewModel.updateScannerSettings(scannerSettings.copy(soundProfile = profile))
                                com.example.service.ScanFeedbackService.playCaptureSound(profile)
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Vibrations feedback", style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = scannerSettings.enableVibration,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(enableVibration = it)) }
                    )
                }
            }
        }
    }
}
