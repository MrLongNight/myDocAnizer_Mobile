package com.example.ui.views.wizard.cloud

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.UsbBackupDrive

@Composable
fun BackupTierUsbCard(
    enableManualUsb: Boolean,
    onToggleManualUsb: (Boolean) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    usbSyncWarningOnScan: Boolean,
    onToggleSyncWarning: (Boolean) -> Unit,
    usbDrives: List<UsbBackupDrive>,
    onSyncUsbDrive: (String) -> Unit,
    onRemoveUsbDrive: (String) -> Unit,
    onShowAddUsbDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showUsbTips by remember { mutableStateOf(false) }

    val borderWidth by animateDpAsState(if (enableManualUsb) 1.5.dp else 1.dp, label = "s1_border")
    val borderColor by animateColorAsState(if (enableManualUsb) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), label = "s1_color")
    val bgColor by animateColorAsState(if (enableManualUsb) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), label = "s1_bg")

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
                        checked = enableManualUsb,
                        onCheckedChange = onToggleManualUsb,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(Icons.Default.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Stufe 1: USB-Stick Backup (Offline)",
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showUsbTips = !showUsbTips },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💡 Formatierung & Voraussetzungen:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                Text(
                                    text = if (showUsbTips) "▲ Ausblenden" else "▼ Details anzeigen",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            AnimatedVisibility(visible = showUsbTips) {
                                Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("• Formatierung: Handelsübliche USB-Sticks mit exFAT oder FAT32 (von Android nativ unterstützt).", style = MaterialTheme.typography.labelSmall)
                                    Text("• Anschluss: USB-C Stick oder USB-A Stick mit kleinem USB-OTG-Adapter.", style = MaterialTheme.typography.labelSmall)
                                    Text("• Weiternutzung als normaler Speicher: Dein USB-Stick wird NICHT formatiert! DocAnizer erstellt lediglich den Ordner '/DocAnizer_Backup/'. Alle bestehenden Dateien und Fotos bleiben vollständig erhalten.", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                                    Text("• Räumliche Trennung: Lagere mindestens 1 Stick z.B. bei Angehörigen oder im Schließfach.", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Warnhinweis bei neuem Scan/Import", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("Erinnert automatisch daran, die USB-Sticks zu synchronisieren.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = usbSyncWarningOnScan,
                                onCheckedChange = onToggleSyncWarning
                            )
                        }
                    }

                    Text("Eingerichtete USB-Sticks (${usbDrives.size}):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    usbDrives.forEach { drive ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(drive.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (drive.lastBackupDate.isNullOrBlank()) "Noch nie gesichert" else "Letzte Sicherung: ${drive.lastBackupDate}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    FilledTonalButton(
                                        onClick = { onSyncUsbDrive(drive.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Sichern", fontSize = 11.sp)
                                    }
                                    IconButton(
                                        onClick = { onRemoveUsbDrive(drive.id) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Löschen", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onShowAddUsbDialog,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Weiteren USB-Stick einrichten / hinzufügen", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
