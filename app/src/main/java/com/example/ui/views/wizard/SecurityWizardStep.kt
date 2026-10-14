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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.service.VaultFactorType
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import com.example.service.VaultSecurityTier
import com.example.ui.DocAnizerViewModel
import java.io.File

/**
 * Informationsinhalt für modale Erklärungs-Popups zu Sicherheitsstufen und Methoden.
 */
data class SecurityInfoContent(
    val title: String,
    val badge: String,
    val whyRecommended: String,
    val consequences: String,
    val details: String
)

/**
 * Intuitive, didaktisch geführte Neugestaltung des Sicherheits- und Verschlüsselungs-Schritts.
 *
 * 4 Phasen:
 * 1. Grundlegende Stufe wählen (A: Keine | B: 1-Faktor | C: 2-Faktoren) mit Auswirkung & Info-Popups
 * 2. Faktoren festlegen (Master-Passwort, Schlüsseldatei .key, TOTP-Authenticator)
 * 3. Notfall-Absicherung (BIP-39, Notfall-Kit PDF, Shamir 2-von-3, Notfall-Code, Keine) mit Info-Popups
 * 4. App-Zugriff & Biometrie absichern (Fingerabdruck / Face Unlock, Auto-Lock, Zusammenfassung)
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

    // Teilschritt 1..4
    var currentSubStep by remember { mutableIntStateOf(1) }

    // Substep 1: Grundstufe
    var selectedTier by remember {
        mutableStateOf(
            if (viewModel.isUnencryptedModeConfirmed.value) VaultSecurityTier.UNENCRYPTED
            else VaultSecurityTier.ONE_FACTOR
        )
    }

    // Substep 2: Faktoren-Wahl
    var selectedFactorType by remember {
        mutableStateOf(
            if (selectedTier == VaultSecurityTier.TWO_FACTOR) VaultFactorType.PASSWORD_AND_KEYFILE
            else if (selectedTier == VaultSecurityTier.UNENCRYPTED) VaultFactorType.NONE
            else VaultFactorType.PASSWORD
        )
    }

    // Passwort State
    var password by remember { mutableStateOf(passwordValue) }
    var passwordConfirm by remember { mutableStateOf(passwordValue) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    // Schlüsseldatei State
    var keyFileToken by remember { mutableStateOf(vaultSecManager.generateKeyFileToken()) }
    var keyFileExported by remember { mutableStateOf(false) }

    // TOTP State
    var totpSecret by remember { mutableStateOf(vaultSecManager.generateTotpSecret()) }
    var totpInputCode by remember { mutableStateOf("") }
    var totpVerified by remember { mutableStateOf(false) }

    // Substep 3: Notfall-Methode
    var selectedRecovery by remember { mutableStateOf(VaultRecoveryMethod.BIP39_WORDS) }
    var generatedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var bip39Confirmed by remember { mutableStateOf(false) }
    var bip39TestInput by remember { mutableStateOf("") }
    var generatedShares by remember { mutableStateOf<List<ShamirShare>>(emptyList()) }
    var recoveryCode by remember { mutableStateOf(vaultSecManager.generateRecoveryCode()) }
    var recoveryCodeCopied by remember { mutableStateOf(false) }
    var pdfGeneratedFile by remember { mutableStateOf<File?>(null) }
    var pdfStatusMessage by remember { mutableStateOf<String?>(null) }
    var noRecoveryAcknowledged by remember { mutableStateOf(false) }

    // Substep 4: Biometrie & Alltagsschutz
    var isBiometricActive by remember { mutableStateOf(biometricEnabled) }
    val bioStatusLabel = remember { viewModel.getBiometricStatusLabel() }
    var autoLockMinutes by remember { mutableIntStateOf(5) }
    var vaultSavedSuccessfully by remember { mutableStateOf(false) }

    // Modaler Info-Dialog State
    var activeInfoDialog by remember { mutableStateOf<SecurityInfoContent?>(null) }
    var showUnencryptedWarningDialog by remember { mutableStateOf(false) }

    // Initialisierung von Beispieldaten für Notfall-Vorschau
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
    LaunchedEffect(password, selectedTier, selectedFactorType, keyFileToken) {
        if (selectedTier == VaultSecurityTier.UNENCRYPTED) {
            onPasswordChange("")
            viewModel.setUnencryptedModeConfirmed(true)
        } else {
            viewModel.setUnencryptedModeConfirmed(false)
            if (selectedFactorType == VaultFactorType.KEYFILE) {
                onPasswordChange(keyFileToken)
            } else {
                onPasswordChange(password)
            }
        }
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

    // Kann Substep 2 fortfahren?
    val canProceedStep2 = when (selectedTier) {
        VaultSecurityTier.UNENCRYPTED -> true
        VaultSecurityTier.ONE_FACTOR -> {
            // Master-Passwort ist IMMER Pflicht bei Verschlüsselung
            isPasswordMatching
        }
        VaultSecurityTier.TWO_FACTOR -> {
            if (selectedFactorType == VaultFactorType.PASSWORD_AND_KEYFILE) {
                isPasswordMatching && keyFileToken.isNotBlank()
            } else if (selectedFactorType == VaultFactorType.PASSWORD_AND_TOTP) {
                isPasswordMatching && (totpVerified || totpInputCode.length == 6)
            } else isPasswordMatching
        }
    }

    // Kann Substep 3 fortfahren?
    val canProceedStep3 = when (selectedRecovery) {
        VaultRecoveryMethod.NONE -> noRecoveryAcknowledged
        VaultRecoveryMethod.BIP39_WORDS -> bip39Confirmed || bip39TestInput.isNotBlank()
        VaultRecoveryMethod.EMERGENCY_PDF -> true
        VaultRecoveryMethod.SHAMIR_2_OF_3 -> true
        VaultRecoveryMethod.RECOVERY_CODE -> true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Kopfbereich
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dokumenten-Tresor & Sicherheit",
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
                text = when (currentSubStep) {
                    1 -> "Wähle deine grundlegende Sicherheitsstufe für myDocAnizer."
                    2 -> "Lege deine Zugangsfaktoren für den Tresor fest."
                    3 -> "Wähle dein Sicherheitsnetz für den Notfall."
                    else -> "Schütze den App-Zugriff im Alltag per Biometrie."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Substep Indikator-Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("1. Stufe", "2. Faktoren", "3. Notfall", "4. Biometrie").forEachIndexed { idx, label ->
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
                        .clickable {
                            if (stepNum < currentSubStep || (currentSubStep == 1 && selectedTier == VaultSecurityTier.UNENCRYPTED)) {
                                currentSubStep = stepNum
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
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
        // TEILSCHRITT 1: GRUNDLEGENDE SICHERHEITSSTUFE WÄHLEN
        // =====================================================================
        if (currentSubStep == 1) {
            // Didaktischer Info-Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Warum wird Verschlüsselung empfohlen?",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Ihre Dokumente enthalten sensible Finanz-, Steuer- und Identitätsdaten. Mit AES-256-GCM bleiben diese selbst bei Geräteverlust oder unberechtigten Lesezugriffen absolut geschützt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Text(
                text = "Wähle das passende Schutz-Niveau für deinen Tresor:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // OPTION A: Keine Verschlüsselung
            SecurityTierCard(
                title = "A) Keine Verschlüsselung",
                subtitle = "Dateien im Klartext auf dem Speicher. Kein Passwort nötig, aber null Schutz bei Fremdzugriff.",
                icon = Icons.Default.LockOpen,
                badge = "Ungeschützt",
                isSelected = selectedTier == VaultSecurityTier.UNENCRYPTED,
                isWarning = true,
                onClick = { selectedTier = VaultSecurityTier.UNENCRYPTED },
                onInfoClick = {
                    activeInfoDialog = SecurityInfoContent(
                        title = "Stufe A: Unverschlüsselter Betrieb",
                        badge = "Ungeschützt",
                        whyRecommended = "Nur für Testzwecke oder reine Beispieldaten empfohlen.",
                        consequences = "Alle gescannten Dokumente, Rechnungen und Notizen werden ohne Verschlüsselung auf dem Gerät abgelegt. Jede App mit Dateirechten oder jeder Finder eines verlorenen Telefons kann alle Daten lesen.",
                        details = "Sie müssen sich kein Master-Passwort merken. Das Schutzniveau entspricht dem gewöhnlicher Dateiordner auf dem Telefon."
                    )
                },
                tag = "card_tier_unencrypted"
            )

            // OPTION B: Verschlüsselung mit einem Faktor
            SecurityTierCard(
                title = "B) Verschlüsselung mit einem Faktor",
                subtitle = "AES-256-GCM Zero-Knowledge Schutz. Du benötigst 1 Element (Passwort oder Schlüsseldatei).",
                icon = Icons.Default.Lock,
                badge = "Empfohlen (Standard)",
                isSelected = selectedTier == VaultSecurityTier.ONE_FACTOR,
                onClick = { selectedTier = VaultSecurityTier.ONE_FACTOR },
                onInfoClick = {
                    activeInfoDialog = SecurityInfoContent(
                        title = "Stufe B: 1-Faktor-Verschlüsselung",
                        badge = "Empfohlen (Standard)",
                        whyRecommended = "Optimales Verhältnis zwischen starkem Schutz und Alltagstauglichkeit für 95% aller Nutzer.",
                        consequences = "Ihre Dokumente werden mit AES-256-GCM und Argon2id gehärtet. Zum Entsperren benötigen Sie genau ein Geheimnis (z.B. Ihr Master-Passwort).",
                        details = "Niemand ohne diesen Faktor kann die Dokumente lesen. Falls Sie das Passwort vergessen, sichert Sie in Schritt 3 eine Notfallmethode ab."
                    )
                },
                tag = "card_tier_one_factor"
            )

            // OPTION C: Verschlüsselung mit zwei Faktoren
            SecurityTierCard(
                title = "C) Verschlüsselung mit zwei Faktoren (2FA)",
                subtitle = "Höchste Sicherheit nach Banken-Standard. Es werden zwingend ZWEI Faktoren zum Entsperren benötigt.",
                icon = Icons.Default.VpnKey,
                badge = "Maximaler Schutz",
                isSelected = selectedTier == VaultSecurityTier.TWO_FACTOR,
                onClick = { selectedTier = VaultSecurityTier.TWO_FACTOR },
                onInfoClick = {
                    activeInfoDialog = SecurityInfoContent(
                        title = "Stufe C: Zwei-Faktor-Authentifizierung (2FA)",
                        badge = "Maximaler Schutz",
                        whyRecommended = "Für hochsensible Geschäfts- oder Vertraulichkeitsdaten.",
                        consequences = "Kombiniert zwei unabhängige Faktoren: 'Etwas, das du weißt' (Passwort) + 'Etwas, das du besitzt' (Schlüsseldatei oder Authenticator-App).",
                        details = "Selbst wenn ein Angreifer Ihr Passwort erfährt, bleibt der Tresor ohne den zweiten Faktor versiegelt. Komplett lokal ohne Cloud-Kosten!"
                    )
                },
                tag = "card_tier_two_factor"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    if (selectedTier == VaultSecurityTier.UNENCRYPTED) {
                        showUnencryptedWarningDialog = true
                    } else {
                        // Passende Faktorenart für nächsten Schritt vorwählen
                        if (selectedTier == VaultSecurityTier.TWO_FACTOR) {
                            if (selectedFactorType != VaultFactorType.PASSWORD_AND_KEYFILE && selectedFactorType != VaultFactorType.PASSWORD_AND_TOTP) {
                                selectedFactorType = VaultFactorType.PASSWORD_AND_KEYFILE
                            }
                        } else {
                            if (selectedFactorType != VaultFactorType.PASSWORD && selectedFactorType != VaultFactorType.KEYFILE) {
                                selectedFactorType = VaultFactorType.PASSWORD
                            }
                        }
                        currentSubStep = 2
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_wizard_tier_continue")
            ) {
                Text(
                    if (selectedTier == VaultSecurityTier.UNENCRYPTED) "Unverschlüsselt fortfahren"
                    else "Auswahl bestätigen & Faktoren einrichten"
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }

        // =====================================================================
        // TEILSCHRITT 2: FAKTOREN FESTLEGEN
        // =====================================================================
        if (currentSubStep == 2) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = if (selectedTier == VaultSecurityTier.TWO_FACTOR) "2. Zwei-Faktor-Methoden konfigurieren"
                        else "2. Zugangsfaktor konfigurieren",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Wenn Stufe B: Master-Passwort ist der Standard-Faktor
                    if (selectedTier == VaultSecurityTier.ONE_FACTOR) {
                        Text(
                            text = "Der gewählte Schutzfaktor ist dein Master-Passwort.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        selectedFactorType = VaultFactorType.PASSWORD
                    }

                    // Wenn Stufe C: Wahl der 2FA-Kombination
                    if (selectedTier == VaultSecurityTier.TWO_FACTOR) {
                        Text(
                            text = "Faktor 1 ist dein Master-Passwort. Wähle deinen 2. Faktor:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedFactorType == VaultFactorType.PASSWORD_AND_KEYFILE,
                                onClick = { selectedFactorType = VaultFactorType.PASSWORD_AND_KEYFILE },
                                label = { Text("Passwort + Schlüsseldatei") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedFactorType == VaultFactorType.PASSWORD_AND_TOTP,
                                onClick = { selectedFactorType = VaultFactorType.PASSWORD_AND_TOTP },
                                label = { Text("Passwort + Authenticator (TOTP)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // EINGABE: MASTER-PASSWORT (falls relevant)
                    if (selectedFactorType == VaultFactorType.PASSWORD ||
                        selectedFactorType == VaultFactorType.PASSWORD_AND_KEYFILE ||
                        selectedFactorType == VaultFactorType.PASSWORD_AND_TOTP
                    ) {
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (selectedTier == VaultSecurityTier.TWO_FACTOR) "Faktor 1: Master-Passwort" else "Master-Passwort festlegen",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                activeInfoDialog = SecurityInfoContent(
                                    title = "Master-Passwort (Argon2id)",
                                    badge = "Wissensfaktor",
                                    whyRecommended = "Aus diesem Passwort wird per Argon2id der kryptografische Schlüssel berechnet.",
                                    consequences = "Wähle mindestens 8-12 Zeichen. Ein gutes Passwort schützt gegen unbefugte Entschlüsselung.",
                                    details = "Die Argon2id-Ableitung benötigt 16 MB Speicherhärte auf dem Gerät und wehrt Brute-Force-Angriffe zuverlässig ab."
                                )
                            }) {
                                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
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

                        // Passwort-Stärke
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
                    }

                    // EINGABE: SCHLÜSSELDATEI (.key)
                    if (selectedFactorType == VaultFactorType.KEYFILE || selectedFactorType == VaultFactorType.PASSWORD_AND_KEYFILE) {
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (selectedTier == VaultSecurityTier.TWO_FACTOR) "Faktor 2: Schlüsseldatei (.key)" else "Digitale Schlüsseldatei",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                activeInfoDialog = SecurityInfoContent(
                                    title = "Digitale Schlüsseldatei (.key)",
                                    badge = "Besitzfaktor",
                                    whyRecommended = "Funktioniert wie ein physischer Haustürschlüssel.",
                                    consequences = "Diese 256-Bit Datei muss zum Entsperren ausgewählt oder vorgelegt werden. Bewahre sie separat auf (z. B. auf USB-Stick).",
                                    details = "Absolut resistent gegen Passwort-Erraten oder Tastatur-Spionage."
                                )
                            }) {
                                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Schlüssel-Token (256-Bit):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    keyFileToken.take(16) + "..." + keyFileToken.takeLast(8),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(keyFileToken))
                                    keyFileExported = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Token kopieren")
                            }
                            Button(
                                onClick = {
                                    keyFileToken = vaultSecManager.generateKeyFileToken()
                                    keyFileExported = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Neu generieren")
                            }
                        }
                    }

                    // EINGABE: TOTP AUTHENTICATOR
                    if (selectedFactorType == VaultFactorType.PASSWORD_AND_TOTP) {
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Faktor 2: Authenticator-App (TOTP)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                activeInfoDialog = SecurityInfoContent(
                                    title = "Authenticator-App (TOTP)",
                                    badge = "Besitzfaktor / 2FA",
                                    whyRecommended = "State of the Art für sichere Zwei-Faktor-Authentifizierung ohne SMS-Kosten oder Internetzwang.",
                                    consequences = "Erzeuge alle 30 Sekunden einen neuen 6-stelligen Code in Apps wie Google Authenticator, Aegis oder Bitwarden.",
                                    details = "100% offline, kein externer Server erforderlich, offener RFC 6238 Standard."
                                )
                            }) {
                                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Geheimer Einrichtungs-Schlüssel (Base32):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    totpSecret.chunked(4).joinToString(" "),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(totpSecret))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Schlüssel in Authenticator-App einfügen")
                        }

                        // Code-Testfeld
                        OutlinedTextField(
                            value = totpInputCode,
                            onValueChange = {
                                totpInputCode = it.take(6)
                                if (it.length == 6) {
                                    totpVerified = vaultSecManager.verifyTotpCode(totpSecret, it)
                                }
                            },
                            label = { Text("6-stelligen Test-Code eingeben") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (totpVerified) {
                            Text("✅ Code erfolgreich bestätigt! 2FA ist einsatzbereit.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Navigation Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { currentSubStep = 1 }, modifier = Modifier.weight(1f)) {
                            Text("Zurück")
                        }
                        Button(
                            onClick = { currentSubStep = 3 },
                            enabled = canProceedStep2,
                            modifier = Modifier.weight(1f).testTag("btn_wizard_factors_continue")
                        ) {
                            Text("Weiter zu Notfall")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // =====================================================================
        // TEILSCHRITT 3: NOTFALL-ABSICHERUNG FESTLEGEN
        // =====================================================================
        if (currentSubStep == 3) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "3. Notfall-Wiederherstellung festlegen",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Falls du dein Passwort oder deine Faktoren verlierst: Wähle eine Methode, um nicht dauerhaft ausgesperrt zu werden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: 12 Wörter (BIP-39)
                RecoveryCardWithInfo(
                    title = "12 Sicherheitswörter (BIP-39)",
                    subtitle = "Klassische Mnemonic-Phrase zum handschriftlichen Aufschreiben.",
                    icon = Icons.Default.Description,
                    badge = "Beliebt",
                    isSelected = selectedRecovery == VaultRecoveryMethod.BIP39_WORDS,
                    onClick = { selectedRecovery = VaultRecoveryMethod.BIP39_WORDS },
                    onInfoClick = {
                        activeInfoDialog = SecurityInfoContent(
                            title = "12 Sicherheitswörter (BIP-39)",
                            badge = "Universell",
                            whyRecommended = "Bewährter Weltstandard aus der modernen Kryptografie.",
                            consequences = "Erlaubt jederzeitige Wiederherstellung aller Dokumente ohne Passwort.",
                            details = "Schreibe die Wörter handschriftlich auf ein Blatt Papier und verwahre es getrennt vom Smartphone an einem sicheren Ort auf."
                        )
                    },
                    tag = "card_recovery_bip39"
                )

                // Option 2: Notfall-PDF drucken
                RecoveryCardWithInfo(
                    title = "Notfall-Kit PDF (Druckblatt)",
                    subtitle = "Druckfertiges A4-Notfallblatt mit QR-Code zum physischen Abheften.",
                    icon = Icons.Default.Print,
                    badge = "Für Einsteiger",
                    isSelected = selectedRecovery == VaultRecoveryMethod.EMERGENCY_PDF,
                    onClick = { selectedRecovery = VaultRecoveryMethod.EMERGENCY_PDF },
                    onInfoClick = {
                        activeInfoDialog = SecurityInfoContent(
                            title = "Notfall-Kit PDF",
                            badge = "Druckfertig",
                            whyRecommended = "Ideal für Nutzer, die wichtige Dokumente in Ordnern abheften.",
                            consequences = "Erzeugt ein A4-Blatt mit verschlüsseltem QR-Code. Kann ausgedruckt und im Tresor oder Ordner verwahrt werden.",
                            details = "Kann später im Notfall einfach per Kamera gescannt werden, um den Tresor wiederherzustellen."
                        )
                    },
                    tag = "card_recovery_emergency_pdf"
                )

                // Option 3: Shamir 2 von 3
                RecoveryCardWithInfo(
                    title = "Schlüssel-Teilung (Shamir 2 von 3)",
                    subtitle = "Mathematisch aufgeteilt auf 3 Teile. Beliebige 2 reichen zur Entschlüsselung.",
                    icon = Icons.Default.CallSplit,
                    badge = "Multi-Device",
                    isSelected = selectedRecovery == VaultRecoveryMethod.SHAMIR_2_OF_3,
                    onClick = { selectedRecovery = VaultRecoveryMethod.SHAMIR_2_OF_3 },
                    onInfoClick = {
                        activeInfoDialog = SecurityInfoContent(
                            title = "Shamir's Secret Sharing (2 von 3)",
                            badge = "Mathematisch sicher",
                            whyRecommended = "Kein einzelner Punkt des Scheiterns (Single Point of Failure).",
                            consequences = "Drei Schlüssel-Teile werden erzeugt. Sie können einen auf dem Handy lassen, einen auf den PC senden und einen einer Vertrauensperson geben.",
                            details = "Jeder einzelne Teil allein verrät mathematisch exakt 0% des Schlüssels. Erst 2 Teile zusammen rekonstruieren den Tresor."
                        )
                    },
                    tag = "card_recovery_shamir"
                )

                // Option 4: Einmaliger Rettungscode
                RecoveryCardWithInfo(
                    title = "Einmaliger Notfall-Schlüssel (32 Zeichen)",
                    subtitle = "Kompakter alphanumerischer Rettungsschlüssel zum Notieren im Passwort-Manager.",
                    icon = Icons.Default.VpnKey,
                    badge = "Kompakt",
                    isSelected = selectedRecovery == VaultRecoveryMethod.RECOVERY_CODE,
                    onClick = { selectedRecovery = VaultRecoveryMethod.RECOVERY_CODE },
                    onInfoClick = {
                        activeInfoDialog = SecurityInfoContent(
                            title = "Einmaliger Notfall-Schlüssel",
                            badge = "Kompakt",
                            whyRecommended = "Ideal, wenn du bereits einen Passwort-Manager wie 1Password, Bitwarden oder Keepass nutzt.",
                            consequences = "Ein 32-stelliger Code im Format XXXX-XXXX-XXXX-... kann dort als sichere Notiz hinterlegt werden.",
                            details = "PBKDF2-gehärteter Wiederherstellungs-Schlüssel."
                        )
                    },
                    tag = "card_recovery_code"
                )

                // Option 5: Keine Wiederherstellung
                RecoveryCardWithInfo(
                    title = "Keine Wiederherstellung (Reines Zero-Knowledge)",
                    subtitle = "Experten-Modus: Bei Passwortverlust gehen alle Daten unwiederbringlich verloren.",
                    icon = Icons.Default.Warning,
                    badge = "Experte (Riskant)",
                    isSelected = selectedRecovery == VaultRecoveryMethod.NONE,
                    isWarning = true,
                    onClick = { selectedRecovery = VaultRecoveryMethod.NONE },
                    onInfoClick = {
                        activeInfoDialog = SecurityInfoContent(
                            title = "Keine Notfall-Wiederherstellung",
                            badge = "Warnung",
                            whyRecommended = "Nur für Hochsicherheits-Experten.",
                            consequences = "Wenn du dein Passwort oder deine Faktoren vergisst, kann absolut NIEMAND deine Daten retten.",
                            details = "Selbst der Support und die Entwickler haben keinerlei Hintertür."
                        )
                    },
                    tag = "card_recovery_none"
                )

                // Details zur aktuell gewählten Notfallmethode
                if (selectedRecovery == VaultRecoveryMethod.BIP39_WORDS) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Deine 12 Sicherheitswörter:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            // Raster 3 Spalten
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (row in 0 until 4) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        for (col in 0 until 3) {
                                            val idx = row * 3 + col
                                            if (idx < generatedWords.size) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = "${idx + 1}. ${generatedWords[idx]}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(generatedWords.joinToString(" ")))
                                    bip39Confirmed = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Alle 12 Wörter kopieren & bestätigen")
                            }
                        }
                    }
                } else if (selectedRecovery == VaultRecoveryMethod.RECOVERY_CODE) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dein Notfall-Schlüssel:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = recoveryCode,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(recoveryCode))
                                    recoveryCodeCopied = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Code in Zwischenablage kopieren")
                            }
                        }
                    }
                } else if (selectedRecovery == VaultRecoveryMethod.NONE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { noRecoveryAcknowledged = !noRecoveryAcknowledged },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = noRecoveryAcknowledged, onCheckedChange = { noRecoveryAcknowledged = it })
                        Text(
                            "Ich verstehe das Risiko des dauerhaften Datenverlusts ohne Notfall-Option.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { currentSubStep = 2 }, modifier = Modifier.weight(1f)) {
                        Text("Zurück")
                    }
                    Button(
                        onClick = { currentSubStep = 4 },
                        enabled = canProceedStep3,
                        modifier = Modifier.weight(1f).testTag("btn_wizard_recovery_continue")
                    ) {
                        Text("Weiter zu Biometrie")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // =====================================================================
        // TEILSCHRITT 4: APP-ZUGRIFF & BIOMETRIE ABSICHERN
        // =====================================================================
        if (currentSubStep == 4) {
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
                            Text("4. Biometrie & Alltagsschutz", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Android KeyStore StrongBox Verschlüsselung", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isBiometricActive,
                            onCheckedChange = { isBiometricActive = it },
                            modifier = Modifier.testTag("switch_wizard_biometrics")
                        )
                    }

                    Text(
                        text = "Mit biometrischer Authentifizierung kannst du den Tresor im Alltag blitzschnell per Fingerabdruck oder Face Unlock öffnen, ohne jedes Mal das lange Master-Passwort einzugeben.",
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
                                text = "Sensor-Status: $bioStatusLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Auto-Lock Zeit
                    HorizontalDivider()
                    Text("Automatische Tresor-Sperre (Auto-Lock):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "1 Min", 5 to "5 Min", 15 to "15 Min", 30 to "30 Min").forEach { (mins, lbl) ->
                            FilterChip(
                                selected = autoLockMinutes == mins,
                                onClick = { autoLockMinutes = mins },
                                label = { Text(lbl, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // ZUSAMMENFASSUNG VOR ABSCHLUSS
                    HorizontalDivider()
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Übersicht deiner Konfiguration:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("• Stufe: ${selectedTier.getDisplayName()}", style = MaterialTheme.typography.bodySmall)
                            Text("• Faktoren: ${selectedFactorType.getDisplayName()}", style = MaterialTheme.typography.bodySmall)
                            Text("• Notfall-Schutz: ${selectedRecovery.getDisplayName()}", style = MaterialTheme.typography.bodySmall)
                            Text("• Biometrie: ${if (isBiometricActive) "Aktiviert (Fingerabdruck/Face)" else "Deaktiviert"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (vaultSavedSuccessfully) {
                        Text("✅ Sicherheitskonfiguration erfolgreich angewendet!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { currentSubStep = 3 }, modifier = Modifier.weight(1f)) {
                            Text("Zurück")
                        }
                        Button(
                            onClick = {
                                if (selectedTier == VaultSecurityTier.UNENCRYPTED) {
                                    viewModel.setUnencryptedModeConfirmed(true)
                                    onPasswordChange("")
                                    vaultSecManager.setUnencryptedModeEnabled(true)
                                } else {
                                    viewModel.setUnencryptedModeConfirmed(false)
                                    val effPass = if (selectedFactorType == VaultFactorType.KEYFILE) keyFileToken else password
                                    onPasswordChange(effPass)
                                    vaultSecManager.initializeVault(
                                        masterPassword = effPass,
                                        enableBiometrics = isBiometricActive,
                                        recoveryMethod = selectedRecovery,
                                        bip39Words = if (selectedRecovery == VaultRecoveryMethod.BIP39_WORDS) generatedWords else null,
                                        securityTier = selectedTier,
                                        factorType = selectedFactorType,
                                        totpSecret = if (selectedFactorType == VaultFactorType.PASSWORD_AND_TOTP) totpSecret else null,
                                        keyFileToken = if (selectedFactorType == VaultFactorType.KEYFILE || selectedFactorType == VaultFactorType.PASSWORD_AND_KEYFILE) keyFileToken else null,
                                        recoveryCode = if (selectedRecovery == VaultRecoveryMethod.RECOVERY_CODE) recoveryCode else null,
                                        autoLockMinutes = autoLockMinutes
                                    )
                                }
                                vaultSavedSuccessfully = true
                            },
                            modifier = Modifier.weight(1f).testTag("btn_wizard_security_finish")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abschließen")
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODALE INFO-POPUPS & BESTÄTIGUNGSDIALOGE
    // =========================================================================

    // Info-Dialog für Optionen & Stufen
    activeInfoDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { activeInfoDialog = null },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(info.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                        Text(info.badge, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    Text("💡 Warum empfohlen?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(info.whyRecommended, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Text("⚠️ Konsequenzen:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(info.consequences, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Text("🔍 Details:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(info.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { activeInfoDialog = null }) {
                    Text("Verstanden")
                }
            }
        )
    }

    // Bestätigungs-Dialog für Stufe A (Unverschlüsselt)
    if (showUnencryptedWarningDialog) {
        AlertDialog(
            onDismissRequest = { showUnencryptedWarningDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Wirklich unverschlüsselt fortfahren?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Im unverschlüsselten Modus werden alle Dokumente im Klartext gespeichert.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Jede App mit Speicherzugriff oder jeder, der Zugriff auf Ihr Telefon hat, kann alle gespeicherten Dokumente öffnen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnencryptedWarningDialog = false
                        viewModel.setUnencryptedModeConfirmed(true)
                        onPasswordChange("")
                        currentSubStep = 4
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Trotzdem unverschlüsselt nutzen")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUnencryptedWarningDialog = false }) {
                    Text("Abbrechen (Verschlüsselung wählen)")
                }
            }
        )
    }
}

/**
 * Wiederverwendbare Karte zur Auswahl einer Sicherheitsstufe mit direktem Info-Popup Button.
 */
@Composable
private fun SecurityTierCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    isSelected: Boolean,
    isWarning: Boolean = false,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            if (isWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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

            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, contentDescription = "Information zu $title", tint = MaterialTheme.colorScheme.primary)
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

/**
 * Wiederverwendbare Karte zur Auswahl einer Notfall-Option mit Info-Popup Button.
 */
@Composable
private fun RecoveryCardWithInfo(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    isSelected: Boolean,
    isWarning: Boolean = false,
    onClick: () -> Unit,
    onInfoClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            if (isWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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

            IconButton(onClick = onInfoClick) {
                Icon(Icons.Default.Info, contentDescription = "Information zu $title", tint = MaterialTheme.colorScheme.primary)
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
