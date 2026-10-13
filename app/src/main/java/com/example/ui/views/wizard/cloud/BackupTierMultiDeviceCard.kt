package com.example.ui.views.wizard.cloud

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.P2pServerStatus
import com.example.model.PairedDevice

@Composable
fun BackupTierMultiDeviceCard(
    p2pServerStatus: P2pServerStatus,
    p2pPairedDevices: List<PairedDevice>,
    onToggleP2pServer: (Boolean) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onStartPairingDialog: () -> Unit,
    onLaunchPcCompanionWizard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStufe4Tab by remember { mutableIntStateOf(0) }
    var showDualSyncInfoDialog by remember { mutableStateOf(false) }
    var showDesktopInfoDialog by remember { mutableStateOf(false) }

    val borderWidth by animateDpAsState(if (p2pServerStatus.isRunning) 1.5.dp else 1.dp, label = "s4_border")
    val borderColor by animateColorAsState(if (p2pServerStatus.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), label = "s4_color")
    val bgColor by animateColorAsState(if (p2pServerStatus.isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), label = "s4_bg")

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Checkbox(
                        checked = p2pServerStatus.isRunning,
                        onCheckedChange = onToggleP2pServer,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Stufe 4: Dual-Sync (Lokale Geräte & PC)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Details ein-/ausklappen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (selectedStufe4Tab == 0) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedStufe4Tab = 0 }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (selectedStufe4Tab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Android Geräte",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedStufe4Tab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedStufe4Tab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (selectedStufe4Tab == 1) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedStufe4Tab = 1 }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Computer,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (selectedStufe4Tab == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Windows, macOS, Linux",
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedStufe4Tab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedStufe4Tab == 1) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (selectedStufe4Tab == 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "Mehrere Android Geräte dauerhaft verbinden",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { showDualSyncInfoDialog = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Technische Details zu Dual-Sync anzeigen",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "dual-sync mittels myDocAnizer-Mobile auf zusätzlichen Gerät",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Verbundene Endgeräte: ${p2pPairedDevices.filter { it.deviceType != "DESKTOP_LIGHT_APP" }.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${p2pServerStatus.localIp}:${p2pServerStatus.port}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Text(
                                    text = "Verbindet ein zusätzliches Smartphone oder Tablet dauerhaft im lokalen WLAN. Synchronisiert alle Dokumente, Volltext-OCR und Aktenpläne direkt von Gerät zu Gerät ohne Internet.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = onStartPairingDialog,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Per QR-Code / PIN mit Zusatzgerät verbinden", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    if (selectedStufe4Tab == 1) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "Scanner, Drucker oder Tresor Zugriff auf Windows, macOS oder Linux konfigurieren",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    IconButton(
                                        onClick = { showDesktopInfoDialog = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Technische Details zu myDocAnizer-Desktop anzeigen",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Verbinde per QR-Code oder myDocAnizer-Desktop dein Windows, macOS oder Linux Desktop-PC um Dokumenten Scanner & Drucker zu verwenden oder für Datei Zugriff im Tresor.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = onLaunchPcCompanionWizard,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Per QR-Code / Assistent mit PC verbindenden", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDualSyncInfoDialog) {
        AlertDialog(
            onDismissRequest = { showDualSyncInfoDialog = false },
            icon = {
                Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            },
            title = {
                Text(
                    text = "Technische Details: Dual-Sync",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("🔐 Zero-Knowledge & Verschlüsselung", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("• Alle Dokumente und Metadaten werden auf Dateiebene per AES-256-GCM verschlüsselt übertragen.", style = MaterialTheme.typography.labelSmall)
                            Text("• Schlüsselaustausch erfolgt über einen 6-stelligen dynamischen Freigabe-PIN und gegenseitige Geräte-Autorisierung.", style = MaterialTheme.typography.labelSmall)
                            Text("• SHA-256 Integritäts-Prüfsumme für jedes einzelne PDF schließt Manipulation oder Datenverlust im WLAN aus.", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("⚡ Bidirektionaler Master-Master Delta-Sync", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("• Echte Peer-to-Peer Synchronisation: Beide Geräte sind gleichberechtigte Master ohne Cloud-Zwang.", style = MaterialTheme.typography.labelSmall)
                            Text("• Es werden nur neue oder geänderte Dokumente (Delta) übertragen, was Bandbreite und Akku schont.", style = MaterialTheme.typography.labelSmall)
                            Text("• Konflikt-Erkennung: Bei gleichzeitiger Bearbeitung entscheiden Nutzer per 1-Klick Dialog.", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("🌐 Lokales Netzwerk & Autarkie", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            Text("• Direkte TCP/HTTP-Verbindung auf Port 8765 im Heimnetzwerk.", style = MaterialTheme.typography.labelSmall)
                            Text("• Funktioniert auch bei komplettem Internetausfall oder im Offline-Hotspot.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDualSyncInfoDialog = false }) {
                    Text("Verstanden")
                }
            }
        )
    }

    if (showDesktopInfoDialog) {
        AlertDialog(
            onDismissRequest = { showDesktopInfoDialog = false },
            icon = {
                Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
            },
            title = {
                Text(
                    text = "Desktop-PC Anbindung",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Mit der Desktop-Erweiterung kannst du Dokumente von Flachbett-Scannern direkt in den Tresor übertragen oder Dokumente am großen Bildschirm sichten.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showDesktopInfoDialog = false }) {
                    Text("Schließen")
                }
            }
        )
    }
}
