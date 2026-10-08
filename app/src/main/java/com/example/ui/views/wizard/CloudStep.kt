package com.example.ui.views.wizard

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CloudSyncConfig
import com.example.model.UsbBackupDrive
import com.example.model.calculateBackupSafetyScore
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.PairingWizardDialog
import kotlinx.coroutines.launch

@Composable
fun StepBackupAndRecoveryStrategy(
    viewModel: DocAnizerViewModel,
    enableDrive: Boolean,
    onToggleDrive: (Boolean) -> Unit,
    enableNas: Boolean,
    onToggleNas: (Boolean) -> Unit,
    enableManualUsb: Boolean,
    onToggleManualUsb: (Boolean) -> Unit,
    autoMirror: Boolean,
    onToggleAutoMirror: (Boolean) -> Unit,
    autoSync: Boolean,
    onToggleAutoSync: (Boolean) -> Unit,
    webDavUrl: String,
    onWebDavUrlChange: (String) -> Unit,
    webDavUser: String,
    onWebDavUserChange: (String) -> Unit,
    webDavPass: String,
    onWebDavPassChange: (String) -> Unit
) {
    val cloudSyncConfig by viewModel.cloudSyncConfig.collectAsState()
    val usbDrives by viewModel.usbDrives.collectAsState()
    val allDocs by viewModel.allDocuments.collectAsState()
    val p2pServerStatus by viewModel.p2pServerStatus.collectAsState()
    val p2pPairedDevices by viewModel.p2pPairedDevices.collectAsState()
    var showWizardPairingDialog by remember { mutableStateOf(false) }

    var showEducationDialog by remember { mutableStateOf(false) }
    var showAddUsbDialog by remember { mutableStateOf(false) }
    var newUsbName by remember { mutableStateOf("") }
    var driveStatusMessage by remember { mutableStateOf<String?>(null) }
    var nasStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingDrive by remember { mutableStateOf(false) }
    var isTestingNas by remember { mutableStateOf(false) }
    var showAllDetails by rememberSaveable { mutableStateOf(false) }
    var expandedTier by rememberSaveable { mutableStateOf<Int?>(null) }
    var showUsbTips by rememberSaveable { mutableStateOf(false) }
    var showNasSafetyNotice by rememberSaveable { mutableStateOf(false) }
    var showDriveTips by rememberSaveable { mutableStateOf(false) }
    var showDualSyncInfoDialog by rememberSaveable { mutableStateOf(false) }
    var showDesktopInfoDialog by rememberSaveable { mutableStateOf(false) }
    var selectedStufe4Tab by rememberSaveable { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val currentConfig = cloudSyncConfig.copy(
        enableGoogleDrive = enableDrive,
        enableWebDavNas = enableNas,
        enableLocalVault = true,
        enableManualUsbExport = enableManualUsb,
        autoMirrorToBackupDir = autoMirror,
        isAirGappedStrict = false
    )
    val safetyScore = calculateBackupSafetyScore(currentConfig)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(
                text = "Backup & Ausfallsicherheit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Schütze deine Unterlagen vor Geräteverlust, Brand und Katastrophen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(safetyScore.colorHex).copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color(safetyScore.colorHex).copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sicherheits-Status: ${safetyScore.scorePercent}% (${safetyScore.badgeText})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(safetyScore.colorHex)
                    )
                }

                LinearProgressIndicator(
                    progress = { safetyScore.scorePercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(safetyScore.colorHex)
                )

                Text(
                    text = safetyScore.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text(
                        text = "Praxis-Hinweis: Risikovorsorge bei Backups",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Warum ist ein einzelner Speicherort ein Risiko? Bei unerwarteten Zwischenfällen wie einem schweren Wasserschaden, Gerätedefekt, Einbruch oder Brand können Geräte im selben Haushalt gleichzeitig Schaden nehmen. Deine digitalen Dokumente sind am verlässlichsten geschützt, wenn Sicherheitskopien an mindestens zwei räumlich getrennten Orten aufbewahrt werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = { showEducationDialog = true },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verständliche Erklärung: Die 3-2-1 Regel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Basis: Lokaler Smartphone-Speicher (100% Offline)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Speicher-Stufen & Synchronisation",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clickable { showAllDetails = !showAllDetails }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (showAllDetails) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (showAllDetails) "Kompaktansicht" else "Alle Details ausklappen",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        val isStufe1Expanded = showAllDetails || enableManualUsb || expandedTier == 1
        val stufe1BorderWidth by animateDpAsState(if (enableManualUsb) 1.5.dp else 1.dp, label = "s1_border")
        val stufe1BorderColor by animateColorAsState(if (enableManualUsb) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), label = "s1_color")
        val stufe1BgColor by animateColorAsState(if (enableManualUsb) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), label = "s1_bg")

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = stufe1BgColor,
            border = BorderStroke(stufe1BorderWidth, stufe1BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedTier = if (expandedTier == 1) null else 1 },
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
                        imageVector = if (isStufe1Expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Details ein-/ausklappen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isStufe1Expanded,
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
                                    checked = cloudSyncConfig.usbSyncWarningOnScan,
                                    onCheckedChange = { checked ->
                                        viewModel.updateCloudSyncConfig(cloudSyncConfig.copy(usbSyncWarningOnScan = checked))
                                    }
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
                                            onClick = {
                                                viewModel.syncUsbDrive(drive.id) { _, _ -> }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Sichern", fontSize = 11.sp)
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeUsbDrive(drive.id) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Löschen", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddUsbDialog = true },
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

        val isStufe2Expanded = showAllDetails || enableDrive || expandedTier == 2
        val stufe2BorderWidth by animateDpAsState(if (enableDrive) 1.5.dp else 1.dp, label = "s2_border")
        val stufe2BorderColor by animateColorAsState(if (enableDrive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), label = "s2_color")
        val stufe2BgColor by animateColorAsState(if (enableDrive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), label = "s2_bg")

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = stufe2BgColor,
            border = BorderStroke(stufe2BorderWidth, stufe2BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedTier = if (expandedTier == 2) null else 2 },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Checkbox(
                            checked = enableDrive,
                            onCheckedChange = onToggleDrive,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Stufe 2: Google Drive Cloud-Tresor",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isStufe2Expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Details ein-/ausklappen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isStufe2Expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showDriveTips = !showDriveTips },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("🔒 Zero-Knowledge Verschlüsselungs-Garantie:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (showDriveTips) "▲ Ausblenden" else "▼ Details",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                AnimatedVisibility(visible = showDriveTips) {
                                    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("• Gesicherter Google Drive Ordner: 'myDocAnizer_Vault'", style = MaterialTheme.typography.labelSmall)
                                        Text("• Alle Dokumente werden vor dem Upload mit deinem Master-Schlüssel verschlüsselt. Google hat keinen Klartext-Zugriff.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                isTestingDrive = true
                                driveStatusMessage = null
                                coroutineScope.launch {
                                    viewModel.testCloudSync("GOOGLE_DRIVE") { success, msg ->
                                        isTestingDrive = false
                                        driveStatusMessage = if (success) "✅ Google Drive Tresor erfolgreich verbunden & einsatzbereit!" else "Fehler: $msg"
                                    }
                                }
                            },
                            enabled = !isTestingDrive,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestingDrive) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verbindung wird hergestellt...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google Drive verbinden & testen", fontSize = 12.sp)
                            }
                        }

                        driveStatusMessage?.let { msg ->
                            Text(text = msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        val isStufe3Expanded = showAllDetails || enableNas || expandedTier == 3
        val stufe3BorderWidth by animateDpAsState(if (enableNas) 1.5.dp else 1.dp, label = "s3_border")
        val stufe3BorderColor by animateColorAsState(if (enableNas) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), label = "s3_color")
        val stufe3BgColor by animateColorAsState(if (enableNas) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), label = "s3_bg")

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = stufe3BgColor,
            border = BorderStroke(stufe3BorderWidth, stufe3BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedTier = if (expandedTier == 3) null else 3 },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Checkbox(
                            checked = enableNas,
                            onCheckedChange = onToggleNas,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Stufe 3: Lokales Heim-NAS / WebDAV",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isStufe3Expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Details ein-/ausklappen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isStufe3Expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showNasSafetyNotice = !showNasSafetyNotice },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        Text("Brandschutz-Warnung zum Heim-NAS:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    }
                                    Text(
                                        text = if (showNasSafetyNotice) "▲ Ausblenden" else "▼ Details",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                AnimatedVisibility(visible = showNasSafetyNotice) {
                                    Text(
                                        text = "Dein NAS und dein Smartphone befinden sich üblicherweise in derselben Wohnung. Bei Feuer, Hochwasser oder Blitzeinschlag sind BEIDE Medien auf einen Schlag zerstört! Nutze NAS daher nur in Kombination mit extern gelagerten USB-Sticks oder Cloud.",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = webDavUrl,
                            onValueChange = onWebDavUrlChange,
                            label = { Text("Server WebDAV-URL") },
                            placeholder = { Text("https://192.168.1.100/remote.php/dav/files/...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = webDavUser,
                                onValueChange = onWebDavUserChange,
                                label = { Text("Benutzer") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = webDavPass,
                                onValueChange = onWebDavPassChange,
                                label = { Text("Passwort") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Button(
                            onClick = {
                                isTestingNas = true
                                nasStatusMessage = null
                                coroutineScope.launch {
                                    viewModel.testCloudSync("WEBDAV_NAS") { success, msg ->
                                        isTestingNas = false
                                        nasStatusMessage = if (success) "✅ Heim-NAS erfolgreich erreichbar & eingerichtet!" else "Fehler: $msg"
                                    }
                                }
                            },
                            enabled = !isTestingNas,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestingNas) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("NAS wird kontaktiert...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("NAS-Verbindung einrichten & testen", fontSize = 12.sp)
                            }
                        }

                        nasStatusMessage?.let { msg ->
                            Text(text = msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        val isStufe4Expanded = showAllDetails || p2pServerStatus.isRunning || expandedTier == 4
        val stufe4BorderWidth by animateDpAsState(if (p2pServerStatus.isRunning) 1.5.dp else 1.dp, label = "s4_border")
        val stufe4BorderColor by animateColorAsState(if (p2pServerStatus.isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), label = "s4_color")
        val stufe4BgColor by animateColorAsState(if (p2pServerStatus.isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), label = "s4_bg")

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = stufe4BgColor,
            border = BorderStroke(stufe4BorderWidth, stufe4BorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedTier = if (expandedTier == 4) null else 4 },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Checkbox(
                            checked = p2pServerStatus.isRunning,
                            onCheckedChange = { start ->
                                if (start) viewModel.startP2pServer() else viewModel.stopP2pServer()
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Stufe 4: Lokale Geräte-Verbindung & Multi-Device Sync",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (isStufe4Expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Details ein-/ausklappen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isStufe4Expanded,
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
                                        onClick = {
                                            if (!p2pServerStatus.isRunning) viewModel.startP2pServer()
                                            showWizardPairingDialog = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Zusätzliches Gerät jetzt verbinden", fontSize = 12.sp)
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
                                        text = "Verbinde per myDocAnizer-Desktop dein Windows, macOS oder Linux Desktop-PC um Dokumenten Scanner & Drucker zu verwenden oder für Datei Zugriff im Tresor.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Button(
                                        onClick = {
                                            if (!p2pServerStatus.isRunning) viewModel.startP2pServer()
                                            viewModel.launchPcCompanionWizard()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("PC-Verbindungs-Assistent starten", fontSize = 12.sp)
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
                        text = "Technische Details: myDocAnizer-Desktop",
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
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("🖨️ Scanner- & Drucker-Integration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                                Text("• Scan-to-Android: Native Steuerung von USB- & WLAN-Dokumentenscannern (WIA, TWAIN, SANE).", style = MaterialTheme.typography.labelSmall)
                                Text("• Eingescannte Dokumente werden direkt an die Smartphone-KI zur automatischen OCR und Kategorisierung übertragen.", style = MaterialTheme.typography.labelSmall)
                                Text("• Lokaler Dokumentendruck über den PC-Druckerspooler ohne Google Cloud Print oder Treiber-Apps auf dem Smartphone.", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("🔒 Lokaler SQLCipher Voll-Spiegel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text("• Der PC hält eine verschlüsselte 1:1 Kopie deiner Dokumente und der Datenbank auf der Festplatte vor.", style = MaterialTheme.typography.labelSmall)
                                Text("• Volltextsuche (FTS5) und Navigation in der Ordnerstruktur blitzschnell am großen Monitor.", style = MaterialTheme.typography.labelSmall)
                                Text("• Verbindung erfolgt berührungslos und sekundenschnell per QR-Code Scan.", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("💻 Plattform-Unterstützung", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                Text("• Unterstützt Windows 10/11, macOS (Apple Silicon & Intel) sowie Linux (Debian, Ubuntu, Fedora, Arch).", style = MaterialTheme.typography.labelSmall)
                                Text("• Alternativ: Zero-Knowledge Web-Portal für jeden Standard-Browser (Chrome, Firefox, Edge, Safari).", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDesktopInfoDialog = false }) {
                        Text("Verstanden")
                    }
                }
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Sync nach jedem Scan", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Sichert neue Belege direkt im Hintergrund", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoSync,
                    onCheckedChange = onToggleAutoSync,
                    enabled = enableDrive || enableNas
                )
            }
        }
    }

    if (showWizardPairingDialog) {
        PairingWizardDialog(
            viewModel = viewModel,
            serverIp = p2pServerStatus.localIp,
            serverPort = p2pServerStatus.port,
            onDismiss = {
                viewModel.stopP2pHostPairingMode()
                showWizardPairingDialog = false
            }
        )
    }

    if (showEducationDialog) {
        AlertDialog(
            onDismissRequest = { showEducationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Datensicherheit: Die 3-2-1 Strategie")
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Warum ist eine Verteilung auf mehrere Speicherorte sinnvoll?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1. Elementarschäden & Haushalts-Unfälle:\nBei unglücklichen Zwischenfällen wie einem schweren Leitungswasserschaden oder Brand können Geräte im selben Zimmer oder Haushalt gleichzeitig unbrauchbar werden. Ein räumlich getrennter USB-Stick (z.B. bei Angehörigen oder im Schließfach) oder ein verschlüsselter Speicher stellt sicher, dass Dokumente auch in solchen Fällen erhalten bleiben.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Verlust, Diebstahl oder Gerätedefekt:\nSmartphones können verloren gehen, einen Platinenschaden erleiden oder gestohlen werden. Mit einer externen Sicherheitskopie stellst du deine Unterlagen auf einem neuen Gerät innerhalb weniger Minuten wieder her.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Die bewährte 3-2-1 Backup-Regel:\n• 3 Kopien aller wichtigen Dateien anlegen\n• 2 verschiedene Speichermedien nutzen (z.B. Smartphone-Speicher + USB-Stick)\n• 1 Kopie an einem getrennten Ort aufbewahren (Offsite-Cloud oder USB-Stick außerhalb des Haushalts)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showEducationDialog = false }) {
                    Text("Verstanden")
                }
            }
        )
    }

    if (showAddUsbDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddUsbDialog = false
                newUsbName = ""
            },
            title = { Text("USB-Stick registrieren") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Gib dem USB-Stick einen sprechenden Namen (z.B. nach Aufbewahrungsort):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newUsbName,
                        onValueChange = { newUsbName = it },
                        label = { Text("Stick-Name") },
                        placeholder = { Text("z.B. USB #2 (Eltern / Extern)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newUsbName.isNotBlank()) {
                            viewModel.addUsbDrive(newUsbName.trim())
                            showAddUsbDialog = false
                            newUsbName = ""
                        }
                    },
                    enabled = newUsbName.isNotBlank()
                ) {
                    Text("Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddUsbDialog = false
                    newUsbName = ""
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
