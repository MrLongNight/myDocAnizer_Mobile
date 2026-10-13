package com.example.ui.views.wizard

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.service.ShamirShare
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import com.example.ui.DocAnizerViewModel
import java.io.File

/**
 * Erweiterter Sicherheits- & Wiederherstellungs-Schritt für den Ersteinrichtungs-Assistenten.
 *
 * Beinhaltet 4 geführte Teilschritte:
 * 1. Master-Passwort vergeben & bestätigen (mit Passwortstärke und Übereinstimmungsprüfung)
 * 2. Toggle "Biometrie als 2. Faktor aktivieren" (Fingerabdruck / Face Unlock)
 * 3. Auswahl der Notfall-Absicherung (12 Wörter / Notfall-PDF / Shamir 2-von-3 / Keine)
 * 4. Ausführung der gewählten Methode (Wörter anzeigen mit Bestätigungstest / PDF-Druck / Share-Übersicht)
 */
@Composable
fun SecurityWizardStep(
    viewModel: DocAnizerViewModel,
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    passwordValue: String,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val vaultSecManager = remember { VaultSecurityManager.getInstance(context) }

    var currentSubStep by remember { mutableIntStateOf(1) }

    // Substep 1: Master-Passwort State
    var password by remember { mutableStateOf(passwordValue) }
    var passwordConfirm by remember { mutableStateOf(passwordValue) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    // Substep 2: Biometrie
    var isBiometricActive by remember { mutableStateOf(biometricEnabled) }
    val bioStatusLabel = remember { viewModel.getBiometricStatusLabel() }

    // Substep 3: Recovery-Methode Auswahl
    var selectedRecovery by remember { mutableStateOf(VaultRecoveryMethod.EMERGENCY_PDF) }

    // Substep 4: Ausführungsdaten
    var generatedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var bip39Confirmed by remember { mutableStateOf(false) }
    var bip39TestWordIndex by remember { mutableIntStateOf(2) } // z.B. 3. Wort
    var bip39TestInput by remember { mutableStateOf("") }

    var generatedShares by remember { mutableStateOf<List<ShamirShare>>(emptyList()) }
    var emergencyPin by remember { mutableStateOf("") }
    var useEmergencyPin by remember { mutableStateOf(false) }
    var pdfGeneratedFile by remember { mutableStateOf<File?>(null) }
    var pdfStatusMessage by remember { mutableStateOf<String?>(null) }
    var noRecoveryAcknowledged by remember { mutableStateOf(false) }

    // Initialisierung von Beispieldaten für Vorschau
    LaunchedEffect(selectedRecovery) {
        when (selectedRecovery) {
            VaultRecoveryMethod.BIP39_WORDS -> {
                if (generatedWords.isEmpty()) {
                    generatedWords = vaultSecManager.generateBip39Mnemonic()
                }
            }
            VaultRecoveryMethod.SHAMIR_2_OF_3 -> {
                if (generatedShares.isEmpty()) {
                    generatedShares = vaultSecManager.splitSecretShamir2of3()
                }
            }
            else -> {}
        }
    }

    // Synchronisation mit übergeordnetem State
    LaunchedEffect(password) {
        onPasswordChange(password)
    }

    LaunchedEffect(isBiometricActive) {
        onBiometricToggle(isBiometricActive)
    }

    val isPasswordMatching = password.isNotBlank() && password == passwordConfirm
    val passwordStrength = remember(password) {
        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        score
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Kopfbereich mit Sub-Step Indikator
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sicherheit & Zero-Knowledge Tresor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Schritt $currentSubStep von 4",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = "Schütze deine Dokumente mit AES-256-GCM und wähle deinen Wiederherstellungsweg.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Substep Tabs / Fortschrittsleiste
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("1. Passwort", "2. Biometrie", "3. Notfall-Wahl", "4. Aktivierung").forEachIndexed { idx, label ->
                val stepNum = idx + 1
                val isSelected = currentSubStep == stepNum
                val isCompleted = currentSubStep > stepNum
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isCompleted -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { currentSubStep = stepNum }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                isCompleted -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // =====================================================================
        // SCHRITT 1: MASTER-PASSWORT VERGEBEN & BESTÄTIGEN
        // =====================================================================
        if (currentSubStep == 1) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(24.dp))
                        }
                        Column {
                            Text("1. Master-Passwort festlegen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Aus diesem Passwort wird per Argon2id der Hauptschlüssel abgeleitet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Master-Passwort") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_wizard_master_password")
                    )

                    OutlinedTextField(
                        value = passwordConfirm,
                        onValueChange = { passwordConfirm = it },
                        label = { Text("Passwort wiederholen") },
                        singleLine = true,
                        isError = password.isNotEmpty() && passwordConfirm.isNotEmpty() && !isPasswordMatching,
                        visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmVisible = !confirmVisible }) {
                                Icon(if (confirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("input_wizard_confirm_password")
                    )

                    if (password.isNotEmpty() && passwordConfirm.isNotEmpty() && !isPasswordMatching) {
                        Text("❌ Passwörter stimmen nicht überein", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    } else if (isPasswordMatching) {
                        Text("✅ Passwörter stimmen überein", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    }

                    // Passwort-Stärkeanzeige
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Passwortstärke:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                when (passwordStrength) {
                                    0, 1 -> "Schwach"
                                    2, 3 -> "Mittel"
                                    else -> "Sehr stark (Argon2id gehärtet)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when (passwordStrength) {
                                    0, 1 -> MaterialTheme.colorScheme.error
                                    2, 3 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (passwordStrength.toFloat() / 4f).coerceIn(0.1f, 1f) },
                            color = when (passwordStrength) {
                                0, 1 -> MaterialTheme.colorScheme.error
                                2, 3 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Button(
                        onClick = {
                            viewModel.setUnencryptedModeConfirmed(false)
                            onPasswordChange(password)
                            currentSubStep = 2
                        },
                        enabled = isPasswordMatching,
                        modifier = Modifier.fillMaxWidth().testTag("btn_wizard_security_next_step1")
                    ) {
                        Text("Passwort speichern & weiter")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            password = ""
                            passwordConfirm = ""
                            onPasswordChange("")
                            viewModel.setUnencryptedModeConfirmed(true)
                            currentSubStep = 2
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_wizard_security_unencrypted_mode")
                    ) {
                        Text("Ohne Master-Passwort nutzen (Unverschlüsselt)")
                    }
                }
            }
        }

        // =====================================================================
        // SCHRITT 2: TOGGLE BIOMETRIE ALS 2. FAKTOR
        // =====================================================================
        if (currentSubStep == 2) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp).size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("2. Biometrie als 2. Faktor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Android KeyStore StrongBox Verschlüsselung", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isBiometricActive,
                            onCheckedChange = { isBiometricActive = it },
                            modifier = Modifier.testTag("switch_wizard_biometrics")
                        )
                    }

                    Text(
                        text = "Mit biometrischer Authentifizierung kannst du den Tresor bequem per Fingerabdruck oder Face Unlock entsperren. Der Entschlüsselungs-Key wird in der hardware-gesicherten Android KeyStore Enklave geschützt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (isBiometricActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Hardware-Status: $bioStatusLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { currentSubStep = 1 }, modifier = Modifier.weight(1f)) {
                            Text("Zurück")
                        }
                        Button(
                            onClick = { currentSubStep = 3 },
                            modifier = Modifier.weight(1f).testTag("btn_wizard_security_next_step2")
                        ) {
                            Text("Notfall-Schutz wählen")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // =====================================================================
        // SCHRITT 3: AUSWAHL DER NOTFALL-ABSICHERUNG
        // =====================================================================
        if (currentSubStep == 3) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "3. Wähle deine Notfall-Wiederherstellungsmethode:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Falls du dein Master-Passwort vergisst, hast du mit der gewählten Methode weiterhin Zugriff auf deine verschlüsselten Daten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: 12 Wörter
                RecoveryOptionCard(
                    title = "12 Sicherheitswörter (BIP-39)",
                    subtitle = "Klassischer Mnemonic-Notizblock zum handschriftlichen Aufschreiben",
                    icon = Icons.Default.Description,
                    badge = "Beliebt",
                    isSelected = selectedRecovery == VaultRecoveryMethod.BIP39_WORDS,
                    onClick = { selectedRecovery = VaultRecoveryMethod.BIP39_WORDS },
                    tag = "card_recovery_bip39"
                )

                // Option 2: Notfall-PDF drucken
                RecoveryOptionCard(
                    title = "Notfall-PDF drucken (Emergency Kit)",
                    subtitle = "Druckfertiges Notfallblatt mit QR-Code zum sicheren physischen Abheften",
                    icon = Icons.Default.Print,
                    badge = "Empfohlen für Einsteiger",
                    isSelected = selectedRecovery == VaultRecoveryMethod.EMERGENCY_PDF,
                    onClick = { selectedRecovery = VaultRecoveryMethod.EMERGENCY_PDF },
                    tag = "card_recovery_emergency_pdf"
                )

                // Option 3: Shamir 2 von 3
                RecoveryOptionCard(
                    title = "Schlüssel-Teilung (Shamir 2 von 3)",
                    subtitle = "Mathematisch aufgeteilt auf Smartphone + Desktop Companion + Cloud/Notiz",
                    icon = Icons.Default.CallSplit,
                    badge = "Empfohlen für Multi-Device",
                    isSelected = selectedRecovery == VaultRecoveryMethod.SHAMIR_2_OF_3,
                    onClick = { selectedRecovery = VaultRecoveryMethod.SHAMIR_2_OF_3 },
                    tag = "card_recovery_shamir"
                )

                // Option 4: Keine Wiederherstellung
                RecoveryOptionCard(
                    title = "Keine Wiederherstellung",
                    subtitle = "Experten-Modus: Bei Passwortverlust gehen alle Daten unwiederbringlich verloren",
                    icon = Icons.Default.Warning,
                    badge = "Experte",
                    isSelected = selectedRecovery == VaultRecoveryMethod.NONE,
                    onClick = { selectedRecovery = VaultRecoveryMethod.NONE },
                    tag = "card_recovery_none",
                    isWarning = true
                )

                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { currentSubStep = 2 }, modifier = Modifier.weight(1f)) {
                        Text("Zurück")
                    }
                    Button(
                        onClick = { currentSubStep = 4 },
                        modifier = Modifier.weight(1f).testTag("btn_wizard_security_next_step3")
                    ) {
                        Text("Methode ausführen")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // =====================================================================
        // SCHRITT 4: AUSFÜHRUNG DER GEWÄHLTEN METHODE
        // =====================================================================
        if (currentSubStep == 4) {
            when (selectedRecovery) {
                VaultRecoveryMethod.BIP39_WORDS -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Deine 12 Sicherheitswörter", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Schreibe diese 12 Wörter in exakt dieser Reihenfolge auf ein Blatt Papier und verwahre es an einem sicheren Ort auf.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 12 Wörter Raster
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (row in 0 until 6) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val idx1 = row * 2
                                        val idx2 = row * 2 + 1
                                        if (idx1 < generatedWords.size) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text("${idx1 + 1}.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(generatedWords[idx1], style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        }
                                        if (idx2 < generatedWords.size) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text("${idx2 + 1}.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(generatedWords[idx2], style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedWords.joinToString(" ")))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Alle 12 Wörter in die Zwischenablage kopieren")
                            }

                            // Bestätigungstest
                            HorizontalDivider()
                            Text("Bestätigungstest: Gib das 3. Wort ein:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = bip39TestInput,
                                onValueChange = {
                                    bip39TestInput = it
                                    if (generatedWords.size >= 3 && it.trim().equals(generatedWords[2], ignoreCase = true)) {
                                        bip39Confirmed = true
                                    }
                                },
                                label = { Text("Wort #3 (${generatedWords.getOrNull(2) ?: ""})") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (bip39Confirmed) {
                                Text("✅ Perfekt! Die Phrase wurde bestätigt.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    vaultSecManager.initializeVault(
                                        masterPassword = password,
                                        enableBiometrics = isBiometricActive,
                                        recoveryMethod = VaultRecoveryMethod.BIP39_WORDS,
                                        bip39Words = generatedWords
                                    )
                                },
                                enabled = bip39Confirmed || bip39TestInput.isNotBlank(),
                                modifier = Modifier.fillMaxWidth().testTag("btn_wizard_save_bip39")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tresor mit 12 Wörtern absichern & fertigstellen")
                            }
                        }
                    }
                }

                VaultRecoveryMethod.EMERGENCY_PDF -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Notfall-Schlüsseldatei generieren", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Erstellt ein ansprechend formatiertes A4-PDF mit verschlüsseltem QR-Code zum Ausdrucken oder physischen Abheften.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Optionaler Notfall-PIN
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Zusätzlicher 6-stelliger Notfall-PIN", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Schützt den QR-Code zusätzlich vor unbefugtem Scannen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = useEmergencyPin, onCheckedChange = { useEmergencyPin = it })
                            }

                            if (useEmergencyPin) {
                                OutlinedTextField(
                                    value = emergencyPin,
                                    onValueChange = { if (it.length <= 6) emergencyPin = it },
                                    label = { Text("6-stelliger PIN (z. B. 482915)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Button(
                                onClick = {
                                    val targetFile = File(context.cacheDir, "myDocAnizer_Notfall_Emergency_Kit.pdf")
                                    val file = vaultSecManager.generateEmergencyPdf(targetFile, if (useEmergencyPin) emergencyPin else null)
                                    pdfGeneratedFile = file
                                    if (file != null) {
                                        pdfStatusMessage = "✅ Notfall-PDF erfolgreich generiert! (${file.length() / 1024} KB)"
                                        // Teilen / Drucken Dialog öffnen
                                        try {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Notfall-PDF drucken oder speichern"))
                                        } catch (e: Exception) {
                                            // Fallback
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("btn_wizard_generate_pdf")
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Notfall-PDF erstellen & drucken / teilen")
                            }

                            pdfStatusMessage?.let { msg ->
                                Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    vaultSecManager.initializeVault(
                                        masterPassword = password,
                                        enableBiometrics = isBiometricActive,
                                        recoveryMethod = VaultRecoveryMethod.EMERGENCY_PDF,
                                        emergencyPin = if (useEmergencyPin) emergencyPin else null
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("btn_wizard_save_pdf")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tresor mit Notfall-PDF fertigstellen")
                            }
                        }
                    }
                }

                VaultRecoveryMethod.SHAMIR_2_OF_3 -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CallSplit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text("Schlüssel-Teilung (2 von 3 Schwellenwert)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "Der Hauptschlüssel wurde mathematisch in 3 Teile zerlegt. Ein einzelner Teil ist nutzlos; erst 2 beliebige Teile stellen den Tresor wieder her.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            generatedShares.forEach { share ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(share.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            IconButton(
                                                onClick = { clipboardManager.setText(AnnotatedString(share.token)) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Kopieren", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Text(share.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = share.token.take(24) + "...",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    vaultSecManager.initializeVault(
                                        masterPassword = password,
                                        enableBiometrics = isBiometricActive,
                                        recoveryMethod = VaultRecoveryMethod.SHAMIR_2_OF_3
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("btn_wizard_save_shamir")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tresor mit Schlüssel-Teilung fertigstellen")
                            }
                        }
                    }
                }

                VaultRecoveryMethod.NONE -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text("Achtung: Keine Wiederherstellungsmethode!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            Text(
                                text = "Du hast den Experten-Modus ohne Notfall-Absicherung gewählt. Wenn du dein Master-Passwort vergisst, kann niemand – auch nicht der Entwickler oder der Support – deine verschlüsselten Daten wiederherstellen.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { noRecoveryAcknowledged = !noRecoveryAcknowledged },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(checked = noRecoveryAcknowledged, onCheckedChange = { noRecoveryAcknowledged = it })
                                Text(
                                    "Ich verstehe das Risiko des dauerhaften Datenverlusts.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    vaultSecManager.initializeVault(
                                        masterPassword = password,
                                        enableBiometrics = isBiometricActive,
                                        recoveryMethod = VaultRecoveryMethod.NONE
                                    )
                                },
                                enabled = noRecoveryAcknowledged,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().testTag("btn_wizard_save_no_recovery")
                            ) {
                                Text("Ohne Notfall-Absicherung fortfahren")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String,
    isWarning: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            if (isWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) {
                if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) {
                    if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
