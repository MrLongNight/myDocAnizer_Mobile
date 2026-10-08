package com.example.ui.views.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.ScannerSettings
import com.example.ui.DocAnizerViewModel

@Composable
fun StepScanCaptureAndTrigger(
    viewModel: DocAnizerViewModel,
    scannerSettings: ScannerSettings
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Scan-Modi, Stativ & Stapelverarbeitung",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Passe die Kamera-Erfassung an deine Arbeitsweise an – von manuellen Einzelbelegen bis zum berührungsfreien Dauerscan ganzer Aktenordner.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Standard-Scanmodus:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isAutoMode = scannerSettings.scanMode == "AUTO"
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isAutoMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = if (isAutoMode) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.updateScannerSettings(scannerSettings.copy(scanMode = "AUTO", triggerMode = "AUTO_DETECT")) }
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("⚡ Auto-Scan", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Text("Erkennt Dokumentengrenzen automatisch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val isManualMode = scannerSettings.scanMode == "MANUAL"
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isManualMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = if (isManualMode) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable { viewModel.updateScannerSettings(scannerSettings.copy(scanMode = "MANUAL", triggerMode = "MANUAL")) }
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text("⚙️ Manuell", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Text("Auslösen nur per Klick oder Taste", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (scannerSettings.tripodAutoScan) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, if (scannerSettings.tripodAutoScan) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoCameraFront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text("🔭 Stativ-Dauerscan (Hands-Free)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("Automatischer Auslöser & Nachlauf nach Erkennung", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = scannerSettings.tripodAutoScan,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(tripodAutoScan = it)) },
                        modifier = Modifier.testTag("switch_wizard_tripod")
                    )
                }

                if (scannerSettings.tripodAutoScan) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱️ Vorlauf vor Scan (nach Erkennung):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${scannerSettings.preScanDelaySeconds}s",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = scannerSettings.preScanDelaySeconds.toFloat(),
                            onValueChange = { newVal ->
                                val sec = newVal.toInt().coerceIn(1, 60)
                                viewModel.updateScannerSettings(scannerSettings.copy(preScanDelaySeconds = sec, autoScanDelayMs = sec * 1000L))
                            },
                            valueRange = 1f..60f,
                            steps = 58,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔄 Nachlauf bis Wiedererkennung:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${scannerSettings.postScanDelaySeconds}s",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = scannerSettings.postScanDelaySeconds.toFloat(),
                            onValueChange = { newVal ->
                                val sec = newVal.toInt().coerceIn(1, 60)
                                viewModel.updateScannerSettings(scannerSettings.copy(postScanDelaySeconds = sec))
                            },
                            valueRange = 1f..60f,
                            steps = 58,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (scannerSettings.isBulkMode) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, if (scannerSettings.isBulkMode) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text("📚 Stapelverarbeitung (Bulk-Modus)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text("Direkt speichern & sofort bereit fürs nächste Blatt", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = scannerSettings.isBulkMode,
                    onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(isBulkMode = it)) },
                    modifier = Modifier.testTag("switch_wizard_bulk")
                )
            }
        }

        val isManualMode = scannerSettings.scanMode == "MANUAL"
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lautstärke-Tasten als Auslöser",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isManualMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        Text(
                            text = if (isManualMode) "Lauter/Leiser-Taste am Handy löst Scan aus" else "Nur im manuellen Modus verfügbar",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isManualMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                    Switch(
                        checked = scannerSettings.enableHardwareButtons && isManualMode,
                        enabled = isManualMode,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(enableHardwareButtons = it)) }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                Text("Akustische Scan-Bestätigung:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("SUCCESS_BEEP" to "Piepston", "CLICK" to "Klick", "CHIME" to "Dezent", "MUTE" to "Aus").forEach { (prof, lbl) ->
                        val isSel = scannerSettings.soundProfile == prof
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.updateScannerSettings(scannerSettings.copy(soundProfile = prof)) }
                        ) {
                            Text(
                                text = lbl,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Vibrations-Impuls bei Auslösung", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = scannerSettings.enableVibration,
                        onCheckedChange = { viewModel.updateScannerSettings(scannerSettings.copy(enableVibration = it)) }
                    )
                }
            }
        }
    }
}
