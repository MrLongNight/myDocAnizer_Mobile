package com.example.ui.views

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.ByteArrayOutputStream
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.AppLogoBanner

@Composable
fun AppLockScreen(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var enteredPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val syncPasswordKey by viewModel.syncPasswordKey.collectAsState()
    val vaultStatus by viewModel.vaultStatus.collectAsState()
    val vaultSecManager = remember { com.example.service.VaultSecurityManager.getInstance(context) }
    
    var enteredTotpCode by remember { mutableStateOf("") }
    var enteredKeyFileToken by remember { mutableStateOf("") }
    var keyFileImportMessage by remember { mutableStateOf<String?>(null) }
    val keyFileImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            keyFileImportMessage = "Schlüsseldatei-Auswahl abgebrochen."
        } else {
            val importedToken = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(512)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 4096) { "Datei ist zu groß" }
                        output.write(buffer, 0, count)
                    }
                    String(output.toByteArray(), Charsets.UTF_8).trim()
                } ?: error("Datei kann nicht gelesen werden")
            }.getOrNull()
            if (importedToken != null && importedToken.matches(Regex("[A-Fa-f0-9]{64}"))) {
                enteredKeyFileToken = importedToken
                keyFileImportMessage = "Schlüsseldatei geladen."
            } else {
                enteredKeyFileToken = ""
                keyFileImportMessage = "Ungültige Schlüsseldatei. Bitte die originale .key-Datei auswählen."
            }
        }
    }

    val isBiometricAvailable = remember {
        viewModel.biometricAuthManager.checkBiometricAvailability() == com.example.service.BiometricAuthManager.BiometricHardwareStatus.AVAILABLE
    }

    // Automatische Auslösung der Biometrie beim Start nur wenn Hardware verfügbar ist
    LaunchedEffect(Unit) {
        if (activity != null && isBiometricAvailable) {
            viewModel.authenticateBiometric(
                activity = activity,
                onSuccess = {
                    errorMessage = null
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false 
                passwordError = null
            },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Mit Passwort / PIN entsperren", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (syncPasswordKey.isBlank()) 
                            "Es wurde kein separates Passwort vergeben. Du kannst den Tresor direkt entsperren oder ein Passwort eingeben." 
                        else 
                            "Gib dein Tresor-Passwort bzw. deine PIN ein:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = enteredPassword,
                        onValueChange = { 
                            enteredPassword = it 
                            passwordError = null
                        },
                        label = { Text("Passwort / PIN") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val success = if (vaultStatus.isUnencryptedMode) {
                                vaultSecManager.unlockUnencryptedVault()
                            } else {
                                vaultSecManager.unlockVault(
                                    password = enteredPassword,
                                    keyFileToken = if (vaultStatus.keyFileEnabled) enteredKeyFileToken else null,
                                    totpCode = if (vaultStatus.totpEnabled) enteredTotpCode else null
                                )
                            }

                            if (success) {
                                viewModel.completeVaultUnlock()
                                showPasswordDialog = false
                            } else {
                                passwordError = "Falsches Passwort oder ungültiger 2. Faktor"
                            }
                        }),
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth().testTag("input_lock_password")
                    )

                    // TOTP Feld falls aktiviert
                    if (vaultStatus.totpEnabled) {
                        OutlinedTextField(
                            value = enteredTotpCode,
                            onValueChange = { enteredTotpCode = it.take(6) },
                            label = { Text("Authenticator-Code (6-stellig)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth().testTag("input_lock_totp")
                        )
                    }

                    // Keyfile-Token Feld falls aktiviert
                    if (vaultStatus.keyFileEnabled) {
                        OutlinedTextField(
                            value = enteredKeyFileToken,
                            onValueChange = { enteredKeyFileToken = it },
                            label = { Text("Schlüsseldatei-Token (64 Hex-Zeichen)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("input_lock_keyfile")
                        )
                        OutlinedButton(
                            onClick = { keyFileImportLauncher.launch(arrayOf("application/octet-stream", "text/plain", "application/*")) },
                            modifier = Modifier.fillMaxWidth().testTag("btn_import_vault_keyfile")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Schlüsseldatei (.key) laden")
                        }
                        keyFileImportMessage?.let { message ->
                            Text(
                                text = message,
                                color = if (message == "Schlüsseldatei geladen.") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    passwordError?.let { err ->
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }

                    TextButton(
                        onClick = {
                            showPasswordDialog = false
                            showRecoveryDialog = true
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("btn_forgot_password_in_dialog")
                    ) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Passwort vergessen? Tresor wiederherstellen", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = if (vaultStatus.isUnencryptedMode) {
                            vaultSecManager.unlockUnencryptedVault()
                        } else {
                            vaultSecManager.unlockVault(
                                password = enteredPassword,
                                keyFileToken = if (vaultStatus.keyFileEnabled) enteredKeyFileToken else null,
                                totpCode = if (vaultStatus.totpEnabled) enteredTotpCode else null
                            )
                        }

                        if (success) {
                            viewModel.completeVaultUnlock()
                            showPasswordDialog = false
                        } else {
                            passwordError = "Falsches Passwort oder ungültiger 2. Faktor"
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_password_unlock")
                ) {
                    Text("Tresor entsperren")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showRecoveryDialog) {
        VaultRecoveryModal(
            vaultSecManager = vaultSecManager,
            onRecoverySuccess = { newPassword ->
                viewModel.setSyncPasswordKey(newPassword)
                viewModel.completeVaultUnlock()
                showRecoveryDialog = false
            },
            onDismiss = { showRecoveryDialog = false }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_lock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Offizielles myDocAnizer Logo-Banner
                AppLogoBanner(
                    iconHeight = 110.dp,
                    fontSize = 24f,
                    includeContainer = true,
                    showTagline = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Geschützter Dokumenten-Tresor",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Deine Dokumente und Belege sind gesichert. Bitte bestätige deine Identität per Fingerabdruck, Gesicht oder Passwort.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }

                errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        if (activity != null) {
                            viewModel.authenticateBiometric(
                                activity = activity,
                                onSuccess = {
                                    errorMessage = null
                                },
                                onError = { err ->
                                    errorMessage = err
                                }
                            )
                        } else {
                            errorMessage = "Biometrische Authentifizierung erfordert eine aktive Activity. Nutze das Master-Passwort."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_unlock_biometric"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Biometrisch entsperren",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { 
                        if (vaultStatus.isUnencryptedMode) {
                            if (!viewModel.unlockUnencryptedVault()) {
                                errorMessage = "Unverschlüsselter Tresor konnte nicht sicher geladen werden."
                            }
                        } else {
                            showPasswordDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_unlock_with_password"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (vaultStatus.isUnencryptedMode) "Direkt entsperren (Kein Passwort gesetzt)" else "Mit Passwort / PIN entsperren",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (com.example.BuildConfig.DEBUG) {
                    OutlinedButton(
                        onClick = { viewModel.debugUnlockAppForDevelopment() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("btn_debug_instant_unlock"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⚡ Sofort-Entsperren (Test-Modus)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = { showRecoveryDialog = true },
                    modifier = Modifier.testTag("btn_lock_screen_recovery")
                ) {
                    Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Passwort vergessen? Tresor wiederherstellen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Modaler Wiederherstellungs-Dialog ("Passwort vergessen" Flow)
 */
@Composable
fun VaultRecoveryModal(
    vaultSecManager: com.example.service.VaultSecurityManager,
    onRecoverySuccess: (newPassword: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf(com.example.service.VaultRecoveryMethod.BIP39_WORDS) }
    var recoveredDek by remember { mutableStateOf<ByteArray?>(null) }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    // Inputs
    var bip39Input by remember { mutableStateOf("") }
    var pdfQrInput by remember { mutableStateOf("") }
    var pdfRecoveryPassphrase by remember { mutableStateOf("") }
    var shamirShare1 by remember { mutableStateOf("") }
    var shamirShare2 by remember { mutableStateOf("") }

    // Neues Passwort nach erfolgreicher Rekonstruktion
    var newMasterPass by remember { mutableStateOf("") }
    var confirmMasterPass by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (recoveredDek == null) Icons.Default.SettingsBackupRestore else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = if (recoveredDek == null) "Tresor wiederherstellen" else "Neues Master-Passwort setzen",
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
                if (recoveredDek == null) {
                    Text(
                        text = "Wähle deine eingerichtete Wiederherstellungsmethode:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedMethod == com.example.service.VaultRecoveryMethod.BIP39_WORDS,
                            onClick = { selectedMethod = com.example.service.VaultRecoveryMethod.BIP39_WORDS; recoveryError = null },
                            label = { Text("12 Wörter", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedMethod == com.example.service.VaultRecoveryMethod.EMERGENCY_PDF,
                            onClick = { selectedMethod = com.example.service.VaultRecoveryMethod.EMERGENCY_PDF; recoveryError = null },
                            label = { Text("Notfall-PDF", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedMethod == com.example.service.VaultRecoveryMethod.SHAMIR_2_OF_3,
                            onClick = { selectedMethod = com.example.service.VaultRecoveryMethod.SHAMIR_2_OF_3; recoveryError = null },
                            label = { Text("Shamir 2/3", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    when (selectedMethod) {
                        com.example.service.VaultRecoveryMethod.BIP39_WORDS -> {
                            Text("Gib deine 12 Sicherheitswörter mit Leerzeichen getrennt ein:", style = MaterialTheme.typography.labelSmall)
                            OutlinedTextField(
                                value = bip39Input,
                                onValueChange = { bip39Input = it; recoveryError = null },
                                label = { Text("12 Wörter Phrase") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth().testTag("input_recovery_bip39")
                            )
                        }

                        com.example.service.VaultRecoveryMethod.EMERGENCY_PDF -> {
                            Text("Scanne den QR-Code des Notfallblatts oder füge das verschlüsselte Recovery-Payload (JSON/Base64) hier ein:", style = MaterialTheme.typography.labelSmall)
                            OutlinedTextField(
                                value = pdfQrInput,
                                onValueChange = { pdfQrInput = it; recoveryError = null },
                                label = { Text("QR-Code-Inhalt / verschlüsseltes Payload") },
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth().testTag("input_recovery_pdf_qr")
                            )
                            Text("Gib die separate Recovery-Passphrase ein, die beim PDF-Export festgelegt wurde. Sie steht nicht im PDF und muss mindestens 20 Zeichen lang sein.", style = MaterialTheme.typography.labelSmall)
                            OutlinedTextField(
                                value = pdfRecoveryPassphrase,
                                onValueChange = { pdfRecoveryPassphrase = it; recoveryError = null },
                                label = { Text("Recovery-Passphrase (mind. 20 Zeichen)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("input_recovery_pdf_passphrase")
                            )
                        }

                        com.example.service.VaultRecoveryMethod.SHAMIR_2_OF_3 -> {
                            Text("Gib zwei beliebige deiner 3 Teilschlüssel (Shares) ein:", style = MaterialTheme.typography.labelSmall)
                            OutlinedTextField(
                                value = shamirShare1,
                                onValueChange = { shamirShare1 = it; recoveryError = null },
                                label = { Text("Erster Teilschlüssel (MYDOC-S...)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_recovery_shamir_1")
                            )
                            OutlinedTextField(
                                value = shamirShare2,
                                onValueChange = { shamirShare2 = it; recoveryError = null },
                                label = { Text("Zweiter Teilschlüssel (MYDOC-S...)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("input_recovery_shamir_2")
                            )
                        }

                        else -> {}
                    }

                    recoveryError?.let { err ->
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    // Schritt 2: Neues Master-Passwort vergeben
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("DEK erfolgreich rekonstruiert! Bitte vergebe jetzt direkt ein neues Master-Passwort.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedTextField(
                        value = newMasterPass,
                        onValueChange = { newMasterPass = it; passError = null },
                        label = { Text("Neues Master-Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("input_recovery_new_password")
                    )

                    OutlinedTextField(
                        value = confirmMasterPass,
                        onValueChange = { confirmMasterPass = it; passError = null },
                        label = { Text("Neues Passwort bestätigen") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("input_recovery_confirm_password")
                    )

                    passError?.let { err ->
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            if (recoveredDek == null) {
                Button(
                    onClick = {
                        recoveryError = null
                        when (selectedMethod) {
                            com.example.service.VaultRecoveryMethod.BIP39_WORDS -> {
                                val words = bip39Input.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
                                if (words.size != 12) {
                                    recoveryError = "Bitte genau 12 Wörter eingeben (aktuell: ${words.size})."
                                    return@Button
                                }
                                val dek = vaultSecManager.recoverDekWithBip39(words)
                                if (dek != null) {
                                    recoveredDek = dek
                                } else {
                                    recoveryError = "Wiederherstellung fehlgeschlagen: Ungültige Phrase oder Checksumme."
                                }
                            }

                            com.example.service.VaultRecoveryMethod.EMERGENCY_PDF -> {
                                if (pdfQrInput.isBlank()) {
                                    recoveryError = "Bitte den Code oder QR-Inhalt eingeben."
                                    return@Button
                                }
                                if (pdfRecoveryPassphrase.length < 20) {
                                    recoveryError = "Die Recovery-Passphrase muss mindestens 20 Zeichen lang sein."
                                    return@Button
                                }
                                val expectedVaultId = vaultSecManager.vaultHeader.value?.vaultId
                                if (expectedVaultId.isNullOrBlank()) {
                                    recoveryError = "Kein bestehender Tresorheader gefunden; Recovery wurde abgebrochen."
                                    return@Button
                                }
                                val dek = vaultSecManager.recoverDekFromEmergencyKit(
                                    pdfQrInput.trim(),
                                    pdfRecoveryPassphrase.toCharArray(),
                                    expectedVaultId
                                )
                                pdfRecoveryPassphrase = ""
                                if (dek != null) {
                                    recoveredDek = dek
                                } else {
                                    recoveryError = "Wiederherstellung fehlgeschlagen: ungültiges Payload, falsche Passphrase oder falsche Tresor-ID."
                                }
                            }

                            com.example.service.VaultRecoveryMethod.SHAMIR_2_OF_3 -> {
                                if (shamirShare1.isBlank() || shamirShare2.isBlank()) {
                                    recoveryError = "Bitte beide Teilschlüssel eingeben."
                                    return@Button
                                }
                                val dek = vaultSecManager.recoverDekWithShamir(shamirShare1.trim(), shamirShare2.trim())
                                if (dek != null) {
                                    recoveredDek = dek
                                } else {
                                    recoveryError = "Wiederherstellung fehlgeschlagen: Die Teilschlüssel passen nicht zusammen."
                                }
                            }

                            else -> {}
                        }
                    },
                    modifier = Modifier.testTag("btn_submit_recovery")
                ) {
                    Text("Schlüssel prüfen")
                }
            } else {
                Button(
                    onClick = {
                        if (newMasterPass.length < 8) {
                            passError = "Das Passwort muss mindestens 8 Zeichen lang sein."
                            return@Button
                        }
                        if (newMasterPass != confirmMasterPass) {
                            passError = "Passwörter stimmen nicht überein."
                            return@Button
                        }
                        val dek = recoveredDek ?: return@Button
                        val success = vaultSecManager.resetPasswordWithRecoveredDek(newMasterPass, dek)
                        if (success) {
                            onRecoverySuccess(newMasterPass)
                        } else {
                            passError = "Fehler beim Setzen des neuen Master-Passworts."
                        }
                    },
                    modifier = Modifier.testTag("btn_save_recovered_password")
                ) {
                    Text("Passwort speichern & entsperren")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
