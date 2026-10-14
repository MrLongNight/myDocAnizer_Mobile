package com.example.ui.views.settings

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.service.ShamirShare
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import com.example.service.VaultStatus
import com.example.ui.DocAnizerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedizierter Einstellungs-Bildschirm für Tresor-Sicherheit, Master-Passwort-Verwaltung,
 * 2FA-Biometrie und Zero-Knowledge Recovery-Methoden.
 */
@Composable
fun VaultSettingsScreen(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val vaultSecManager = remember { VaultSecurityManager.getInstance(context) }
    val vaultStatus by vaultSecManager.vaultStatus.collectAsState()
    val vaultHeader by vaultSecManager.vaultHeader.collectAsState()

    val syncPasswordKey by viewModel.syncPasswordKey.collectAsState()
    val isUnencryptedModeConfirmed by viewModel.isUnencryptedModeConfirmed.collectAsState()
    val hasCustomPassword = syncPasswordKey.isNotBlank()

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showAuthForRecoveryDialog by remember { mutableStateOf(false) }
    var isRecoveryUnlocked by remember { mutableStateOf(false) }

    var actionFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var actionIsError by remember { mutableStateOf(false) }

    // Dialog Master-Passwort ändern
    if (showChangePasswordDialog) {
        ChangeMasterPasswordDialog(
            vaultSecManager = vaultSecManager,
            onDismiss = { showChangePasswordDialog = false },
            onSuccess = {
                showChangePasswordDialog = false
                actionFeedbackMessage = "✅ Master-Passwort erfolgreich geändert! DEK wurde neu verschlüsselt."
                actionIsError = false
            }
        )
    }

    // Authentifizierung zur Einsicht der Wiederherstellungsdaten
    if (showAuthForRecoveryDialog) {
        AuthenticateVaultPasswordDialog(
            vaultSecManager = vaultSecManager,
            title = "Wiederherstellungsdaten freischalten",
            description = "Gib dein Master-Passwort ein, um deine vertraulichen Notfall-Schlüssel einzusehen:",
            onDismiss = { showAuthForRecoveryDialog = false },
            onSuccess = {
                showAuthForRecoveryDialog = false
                isRecoveryUnlocked = true
                actionFeedbackMessage = "🔓 Wiederherstellungsbereich für diese Sitzung freigeschaltet."
                actionIsError = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // WARNHINWEIS BEI FEHLENDEM PASSWORT
        if (!hasCustomPassword && !isUnencryptedModeConfirmed) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                ),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().testTag("card_no_password_warning")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(8.dp).size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚠️ Kein Master-Passwort eingerichtet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Kein sicherer Zero-Knowledge Betrieb gewährleistet!",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Text(
                        text = "Du hast die Ersteinrichtung übersprungen oder kein eigenes Master-Passwort vergeben. Ohne ein eigenes Passwort sind deine Dokumente ungeschützt und kein sicherer Betrieb möglich.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showChangePasswordDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).testTag("btn_set_custom_master_password")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Passwort vergeben")
                        }

                        OutlinedButton(
                            onClick = { viewModel.setUnencryptedModeConfirmed(true) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.testTag("btn_accept_unencrypted_mode")
                        ) {
                            Text("Ohne Schutz nutzen")
                        }
                    }
                }
            }
        } else if (!hasCustomPassword && isUnencryptedModeConfirmed) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("card_unencrypted_mode_info")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Unverschlüsselter Modus aktiv",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Du nutzt die App bewusst ohne Master-Passwort. Deine Dokumente werden lokal ohne Tresor-Passwort geöffnet. Du kannst jederzeit ein Master-Passwort einrichten, um Zero-Knowledge-Schutz zu aktivieren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showChangePasswordDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("btn_enable_encryption_now")
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Jetzt Master-Passwort einrichten")
                    }
                }
            }
        }
        // Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().testTag("card_vault_status")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(8.dp).size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Zero-Knowledge Tresor-Sicherheit",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "AES-256-GCM • Argon2id KEK • KeyStore Enklave",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "AKTIV",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Tresor-Kennung:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(vaultStatus.vaultId.ifBlank { "VAULT-LOKAL" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Recovery-Status:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(vaultStatus.recoveryStatusLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        actionFeedbackMessage?.let { msg ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (actionIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (actionIsError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (actionIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                    Text(text = msg, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // =====================================================================
        // SEKTION 1: MASTER-PASSWORT ÄNDERN
        // =====================================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Master-Passwort ändern", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Verschlüsselt den bestehenden DEK mit einem neuen Argon2id-Key neu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(
                    onClick = { showChangePasswordDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("btn_change_master_password")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Master-Passwort jetzt ändern")
                }
            }
        }

        // =====================================================================
        // SEKTION 2: 2FA BIOMETRIE
        // =====================================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp).size(22.dp))
                        }
                        Column {
                            Text("2. Faktor via Biometrie", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Fingerabdruck / Face Unlock im Android KeyStore", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = vaultStatus.biometric2FaEnabled,
                        onCheckedChange = { enable ->
                            val success = vaultSecManager.setBiometricsEnabled(enable)
                            if (success) {
                                viewModel.setBiometricAuthEnabled(enable)
                                actionFeedbackMessage = if (enable) "✅ Biometrie (2FA) aktiviert." else "Biometrie deaktiviert."
                                actionIsError = false
                            } else {
                                actionFeedbackMessage = "❌ Biometrie konnte nicht umgeschaltet werden."
                                actionIsError = true
                            }
                        },
                        modifier = Modifier.testTag("switch_vault_biometrics")
                    )
                }
                Text(
                    text = "Hardware-Status: ${viewModel.getBiometricStatusLabel()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // =====================================================================
        // SEKTION 3: WIEDERHERSTELLUNGSMETHODE VERWALTEN
        // =====================================================================
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(8.dp).size(22.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wiederherstellungsmethode verwalten", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(vaultStatus.recoveryStatusLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (!isRecoveryUnlocked) {
                    Text(
                        text = "Um Notfall-Schlüssel einzusehen, das Notfall-PDF neu zu drucken oder Shamir-Shares zu exportieren, ist die Eingabe deines Master-Passworts erforderlich.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showAuthForRecoveryDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("btn_unlock_recovery_management")
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mit Master-Passwort freischalten")
                    }
                } else {
                    // Freigeschalteter Bereich
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("Bereich freigeschaltet", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }

                            when (vaultStatus.recoveryMethod) {
                                VaultRecoveryMethod.BIP39_WORDS -> {
                                    Text("Aktive 12-Wörter Phrase (BIP-39):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    val words = remember { vaultSecManager.generateBip39Mnemonic() }
                                    Text(
                                        text = words.mapIndexed { i, w -> "${i + 1}. $w" }.chunked(3).joinToString("\n") { it.joinToString("   ") },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(words.joinToString(" ")))
                                            actionFeedbackMessage = "Wörter in die Zwischenablage kopiert."
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("12 Wörter kopieren")
                                    }
                                }

                                VaultRecoveryMethod.EMERGENCY_PDF -> {
                                    Text("Notfall-Schlüsseldatei (Emergency Kit):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Drucke das Notfallblatt erneut aus oder sichere es an einem sicheren physischen Ort.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(
                                        onClick = {
                                            val target = File(context.cacheDir, "myDocAnizer_Notfall_Emergency_Kit.pdf")
                                            val file = vaultSecManager.generateEmergencyPdf(target)
                                            if (file != null) {
                                                try {
                                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/pdf"
                                                        putExtra(Intent.EXTRA_STREAM, uri)
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(Intent.createChooser(intent, "Notfall-PDF drucken / exportieren"))
                                                } catch (e: Exception) {
                                                    actionFeedbackMessage = "PDF erzeugt: ${file.name}"
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("btn_reexport_emergency_pdf")
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Emergency Kit PDF erneut exportieren / drucken")
                                    }
                                }

                                VaultRecoveryMethod.SHAMIR_2_OF_3 -> {
                                    Text("Aktive Shamir-Shares (2 von 3 Schwellenwert):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    val shares = remember { vaultSecManager.splitSecretShamir2of3() }
                                    shares.forEach { share ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(share.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            IconButton(
                                                onClick = { clipboardManager.setText(AnnotatedString(share.token)) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                VaultRecoveryMethod.RECOVERY_CODE -> {
                                    Text("Aktiver Einmal-Notfall-Schlüssel (32 Zeichen):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("Ein sicherer 32-stelliger Wiederherstellungsschlüssel ist hinterlegt. Verwende diesen im Notfall bei Passwortverlust.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                VaultRecoveryMethod.NONE -> {
                                    Text("Aktuell ist keine Notfall-Methode eingerichtet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Lokales Diagnose- & Audit-Protokoll Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_audit_log_security")
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text("Diagnose- & Inferenz-Logdatei", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("100% lokales Audit aller KI-, OCR- & App-Aktionen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(
                    text = "Protokolliert alle Aktivitäten, Inferenzschritte der lokalen LLMs und OCR-Treffer direkt im internen Speicher. Kann jederzeit zur Fehleranalyse exportiert oder geteilt werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportAndShareAuditLog(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_export_audit_log_security"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log exportieren / teilen")
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearAuditLogs(context) },
                        modifier = Modifier.testTag("btn_clear_audit_log_security")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Leeren")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangeMasterPasswordDialog(
    vaultSecManager: VaultSecurityManager,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showPass by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Master-Passwort ändern", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Gib dein bisheriges Passwort und das neue Master-Passwort ein:", style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = oldPass,
                    onValueChange = { oldPass = it; errorMsg = null },
                    label = { Text("Bisheriges Passwort") },
                    singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("input_old_password")
                )

                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it; errorMsg = null },
                    label = { Text("Neues Passwort") },
                    singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("input_new_password")
                )

                OutlinedTextField(
                    value = confirmPass,
                    onValueChange = { confirmPass = it; errorMsg = null },
                    label = { Text("Neues Passwort bestätigen") },
                    singleLine = true,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("input_confirm_new_password")
                )

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPass.length < 8) {
                        errorMsg = "Das neue Passwort muss mindestens 8 Zeichen lang sein."
                        return@Button
                    }
                    if (newPass != confirmPass) {
                        errorMsg = "Die neuen Passwörter stimmen nicht überein."
                        return@Button
                    }
                    val changed = vaultSecManager.changeMasterPassword(oldPass, newPass)
                    if (changed) {
                        onSuccess()
                    } else {
                        errorMsg = "Bisheriges Passwort ist nicht korrekt."
                    }
                },
                modifier = Modifier.testTag("btn_confirm_change_password")
            ) {
                Text("Passwort ändern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun AuthenticateVaultPasswordDialog(
    vaultSecManager: VaultSecurityManager,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = null },
                    label = { Text("Master-Passwort") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("input_auth_vault_password")
                )
                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (vaultSecManager.unlockWithPassword(password)) {
                        onSuccess()
                    } else {
                        errorMsg = "Falsches Master-Passwort."
                    }
                },
                modifier = Modifier.testTag("btn_confirm_auth_vault")
            ) {
                Text("Freischalten")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
