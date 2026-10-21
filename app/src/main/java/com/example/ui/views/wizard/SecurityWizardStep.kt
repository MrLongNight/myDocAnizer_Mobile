package com.example.ui.views.wizard

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.credentials.CreatePasswordRequest
import androidx.fragment.app.FragmentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialCancellationException
import kotlinx.coroutines.launch
import com.example.service.BiometricAuthManager
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
 * 2. Faktoren festlegen (Master-Passwort und Schlüsseldatei .key)
 * 3. Notfall-Absicherung (BIP-39, Notfall-Kit PDF, Shamir 2-von-3, Notfall-Code, Keine) mit Info-Popups
 * 4. App-Zugriff & Biometrie absichern (Fingerabdruck / Face Unlock, Auto-Lock, Zusammenfassung)
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SecurityWizardStep(
    viewModel: DocAnizerViewModel,
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    passwordValue: String,
    onPasswordChange: (String) -> Unit,
    onSecurityComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val vaultSecManager = remember { VaultSecurityManager.getInstance(context) }

    // Teilschritt 1..4
    var currentSubStep by remember { mutableIntStateOf(1) }

    // Substep 1: Grundstufe - Keine Default-Vorauswahl bei den 3 Betriebsarten
    var selectedTier by remember { mutableStateOf<VaultSecurityTier?>(null) }

    // Substep 2: Faktoren-Wahl
    var selectedFactorType by remember {
        mutableStateOf(VaultFactorType.PASSWORD)
    }

    // Passwort State
    var password by remember { mutableStateOf(passwordValue) }
    var passwordConfirm by remember { mutableStateOf(passwordValue) }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    @Suppress("DEPRECATION")
    val autofill = LocalAutofill.current
    @Suppress("DEPRECATION")
    val autofillTree = LocalAutofillTree.current
    val masterPasswordAutofillNode = remember {
        AutofillNode(
            autofillTypes = listOf(AutofillType.NewPassword),
            onFill = { filledPassword ->
                password = filledPassword
                passwordConfirm = filledPassword
            }
        )
    }
    DisposableEffect(autofillTree, masterPasswordAutofillNode) {
        autofillTree += masterPasswordAutofillNode
        onDispose { autofillTree.children.remove(masterPasswordAutofillNode.id) }
    }

    // Schlüsseldatei State
    var keyFileToken by remember { mutableStateOf(vaultSecManager.generateKeyFileToken()) }
    var keyFileExported by remember { mutableStateOf(false) }
    var keyFileExportMessage by remember { mutableStateOf<String?>(null) }
    val keyFileExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) {
            keyFileExportMessage = "Export abgebrochen."
        } else {
            keyFileExportMessage = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(keyFileToken.toByteArray(Charsets.UTF_8))
                } ?: error("Datei konnte nicht geschrieben werden")
                keyFileExported = true
                "Schlüsseldatei gespeichert. Bewahre sie getrennt vom Smartphone auf."
            }.getOrElse { "Schlüsseldatei konnte nicht gespeichert werden." }
        }
    }

    // Google Password Manager & Passkey State
    val coroutineScope = rememberCoroutineScope()
    var isSavingToGoogleManager by remember { mutableStateOf(false) }
    var googleManagerStatusMsg by remember { mutableStateOf<String?>(null) }
    var isRegisteringPasskey by remember { mutableStateOf(false) }
    var passkeyRegisterStatus by remember { mutableStateOf<String?>(null) }

    // Substep 3: Notfall-Methode
    var selectedRecovery by remember { mutableStateOf(VaultRecoveryMethod.BIP39_WORDS) }
    var generatedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var bip39Confirmed by remember { mutableStateOf(false) }
    var bip39TestInput by remember { mutableStateOf("") }
    var generatedShares by remember { mutableStateOf<List<ShamirShare>>(emptyList()) }
    var shamirSharesConfirmed by remember { mutableStateOf(false) }
    var recoveryCode by remember { mutableStateOf(vaultSecManager.generateRecoveryCode()) }
    var recoveryCodeCopied by remember { mutableStateOf(false) }
    var pdfGeneratedFile by remember { mutableStateOf<File?>(null) }
    var pdfStatusMessage by remember { mutableStateOf<String?>(null) }
    var showEmergencyPdfPassphraseDialog by remember { mutableStateOf(false) }
    var emergencyPdfPassphrase by remember { mutableStateOf("") }
    var emergencyPdfPassphraseConfirm by remember { mutableStateOf("") }
    var noRecoveryAcknowledged by remember { mutableStateOf(false) }

    // Substep 4: Biometrie & Alltagsschutz
    var isBiometricActive by remember { mutableStateOf(biometricEnabled) }
    val bioStatusLabel = remember { viewModel.getBiometricStatusLabel() }
    var autoLockMinutes by remember { mutableIntStateOf(5) }
    var vaultSavedSuccessfully by remember { mutableStateOf(false) }
    var securitySaveError by remember { mutableStateOf<String?>(null) }

    fun finishSecurityStep() {
        if (selectedTier != VaultSecurityTier.UNENCRYPTED &&
            selectedRecovery == VaultRecoveryMethod.SHAMIR_2_OF_3 &&
            (!shamirSharesConfirmed || generatedShares.size != 3)
        ) {
            securitySaveError = "Speichere alle drei echten Shamir-Shares sicher getrennt und bestätige das, bevor du fortfährst."
            return
        }
        vaultSavedSuccessfully = true
        if (selectedTier != VaultSecurityTier.UNENCRYPTED &&
            selectedRecovery == VaultRecoveryMethod.EMERGENCY_PDF
        ) {
            pdfStatusMessage = null
            showEmergencyPdfPassphraseDialog = true
        } else {
            onSecurityComplete()
        }
    }

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
            // Shamir shares must be generated from the DEK created during setup. Do not
            // generate an unregistered set before the vault exists.
            else -> {}
        }
    }

    // Synchronisation mit übergeordnetem State
    LaunchedEffect(password, selectedTier, selectedFactorType, keyFileToken) {
        if (selectedTier == VaultSecurityTier.UNENCRYPTED) {
            onPasswordChange("")
            viewModel.setUnencryptedModeConfirmed(true)
            viewModel.setSecurityMethod("BIOMETRIC")
        } else if (selectedTier != null) {
            viewModel.setUnencryptedModeConfirmed(false)
            if (selectedFactorType == VaultFactorType.PASSKEY) {
                // A passkey is not yet cryptographically bound to the vault DEK.
                // Never substitute a fixed, public placeholder as the vault password.
                onPasswordChange(password)
                viewModel.setSecurityMethod("PASSWORD")
            } else if (selectedFactorType == VaultFactorType.KEYFILE) {
                onPasswordChange(keyFileToken)
                viewModel.setSecurityMethod("PASSWORD")
            } else {
                onPasswordChange(password)
                viewModel.setSecurityMethod("PASSWORD")
            }
        }
    }

    val isPasswordMatching = password.isNotBlank() && password == passwordConfirm
    val passwordStrength = remember(password) {
        com.example.util.PasswordStrengthEstimator.score(password)
    }

    // Kann Substep 2 fortfahren?
    val canProceedStep2 = when (selectedTier) {
        VaultSecurityTier.UNENCRYPTED -> true
        VaultSecurityTier.ONE_FACTOR -> {
            when (selectedFactorType) {
                VaultFactorType.PASSKEY -> false
                VaultFactorType.KEYFILE -> keyFileToken.isNotBlank()
                else -> isPasswordMatching
            }
        }
        VaultSecurityTier.TWO_FACTOR -> {
            selectedFactorType == VaultFactorType.PASSWORD_AND_KEYFILE &&
                isPasswordMatching &&
                keyFileToken.matches(Regex("[A-Fa-f0-9]{64}")) &&
                keyFileExported
        }
        null -> false
    }

    // Kann Substep 3 fortfahren?
    val canProceedStep3 = when (selectedRecovery) {
        VaultRecoveryMethod.NONE -> noRecoveryAcknowledged
        VaultRecoveryMethod.BIP39_WORDS -> bip39Confirmed || bip39TestInput.isNotBlank()
        VaultRecoveryMethod.EMERGENCY_PDF -> true
        VaultRecoveryMethod.SHAMIR_2_OF_3 -> selectedTier != VaultSecurityTier.UNENCRYPTED
        VaultRecoveryMethod.RECOVERY_CODE -> true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
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
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Warum wird Verschlüsselung empfohlen?",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Ihre Dokumente enthalten Finanz- & Steuerdaten. AES-256-GCM schützt sie vor unberechtigtem Zugriff.",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
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
                subtitle = "Dateien im Klartext auf dem Speicher. Kein Passwort nötig, null Fremdzugriffsschutz.",
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
                title = "B) 1-Faktor-Verschlüsselung",
                subtitle = "AES-256-GCM Schutz via Master-Passwort, Google Passwort-Manager, Passkey oder Datei.",
                icon = Icons.Default.Lock,
                badge = "Empfohlen",
                isSelected = selectedTier == VaultSecurityTier.ONE_FACTOR,
                onClick = { selectedTier = VaultSecurityTier.ONE_FACTOR },
                onInfoClick = {
                    activeInfoDialog = SecurityInfoContent(
                        title = "Stufe B: 1-Faktor-Verschlüsselung (Passwort, Passkey oder Schlüsseldatei)",
                        badge = "Empfohlen (Standard)",
                        whyRecommended = "Optimales Verhältnis zwischen starkem Schutz und Alltagstauglichkeit für 95% aller Nutzer.",
                        consequences = "Ihre Dokumente werden mit AES-256-GCM und Argon2id gehärtet. Sie können ein Master-Passwort (inkl. Google Passwort-Manager Auto-Sync) oder einen modernen biometrischen Passkey (FIDO2) nutzen.",
                        details = "Niemand ohne diesen Faktor kann die Dokumente lesen. Falls Sie das Passwort vergessen, sichert Sie in Schritt 3 eine Notfallmethode ab."
                    )
                },
                tag = "card_tier_one_factor"
            )

            // OPTION C: Verschlüsselung mit zwei Faktoren
            SecurityTierCard(
                title = "C) 2-Faktor Schutz (2FA)",
                subtitle = "Zwei Faktoren zwingend: Master-Passwort UND separate zufällige Schlüsseldatei (.key).",
                icon = Icons.Default.VpnKey,
                badge = "2FA Höchste Stufe",
                isSelected = selectedTier == VaultSecurityTier.TWO_FACTOR,
                onClick = { selectedTier = VaultSecurityTier.TWO_FACTOR },
                onInfoClick = {
                    activeInfoDialog = SecurityInfoContent(
                        title = "Stufe C: Zwei-Faktor-Authentifizierung (2FA)",
                        badge = "Maximaler Schutz",
                        whyRecommended = "Für hochsensible Geschäfts- oder Vertraulichkeitsdaten.",
                        consequences = "Kombiniert Wissen (Master-Passwort) und Besitz (separate zufällige Schlüsseldatei). TOTP wird nicht als kryptografischer Faktor angeboten.",
                        details = "Selbst wenn ein Angreifer Ihr Passwort erfährt, bleibt der Tresor ohne die separate Schlüsseldatei versiegelt. Die Schlüsseldatei muss sicher und getrennt aufbewahrt werden."
                    )
                },
                tag = "card_tier_two_factor"
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    val tier = selectedTier ?: return@Button
                    if (tier == VaultSecurityTier.UNENCRYPTED) {
                        showUnencryptedWarningDialog = true
                    } else {
                        // Passende Faktorenart für nächsten Schritt vorwählen
                        if (tier == VaultSecurityTier.TWO_FACTOR) {
                            if (selectedFactorType != VaultFactorType.PASSWORD_AND_KEYFILE) {
                                selectedFactorType = VaultFactorType.PASSWORD_AND_KEYFILE
                            }
                        } else {
                            if (selectedFactorType != VaultFactorType.PASSWORD && 
                                selectedFactorType != VaultFactorType.PASSKEY && 
                                selectedFactorType != VaultFactorType.KEYFILE
                            ) {
                                selectedFactorType = VaultFactorType.PASSWORD
                            }
                        }
                        currentSubStep = 2
                    }
                },
                enabled = selectedTier != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_wizard_tier_continue")
            ) {
                Text(
                    when (selectedTier) {
                        null -> "Bitte eine Betriebsart auswählen"
                        VaultSecurityTier.UNENCRYPTED -> "Unverschlüsselt fortfahren"
                        else -> "Auswahl bestätigen & Faktoren einrichten"
                    }
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
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (selectedTier == VaultSecurityTier.TWO_FACTOR) "2. Zwei-Faktor-Methoden konfigurieren"
                        else "2. Zugangsfaktor konfigurieren",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Wenn Stufe B: Wahl des Faktors (Passwort, Passkey oder Schlüsseldatei)
                    if (selectedTier == VaultSecurityTier.ONE_FACTOR) {
                        Text(
                            text = "Wähle deinen primären Zugangs-Faktor:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FactorOptionCard(
                                title = "Master-Passwort",
                                subtitle = "Argon2id gehärteter Wissensfaktor mit Google Passwort-Manager Auto-Fill & Sync",
                                icon = Icons.Default.Password,
                                badge = "Klassisch",
                                isSelected = selectedFactorType == VaultFactorType.PASSWORD,
                                onClick = { selectedFactorType = VaultFactorType.PASSWORD },
                                tag = "factor_card_password"
                            )

                            FactorOptionCard(
                                title = "Passkey (FIDO2)",
                                subtitle = "Registrierung möglich; Tresor-Verschlüsselungsbindung noch nicht fertig",
                                icon = Icons.Default.Fingerprint,
                                badge = "Noch nicht integriert",
                                isSelected = selectedFactorType == VaultFactorType.PASSKEY,
                                onClick = { selectedFactorType = VaultFactorType.PASSKEY },
                                tag = "factor_card_passkey"
                            )

                            FactorOptionCard(
                                title = "Digitale Schlüsseldatei (.key)",
                                subtitle = "Kryptografischer Besitzfaktor als lokale Datei auf USB/Gerätespeicher",
                                icon = Icons.Default.Key,
                                badge = "Offline Datei",
                                isSelected = selectedFactorType == VaultFactorType.KEYFILE,
                                onClick = { selectedFactorType = VaultFactorType.KEYFILE },
                                tag = "factor_card_keyfile"
                            )
                        }
                    }

                    // Wenn Stufe C: Wahl der 2FA-Kombination
                    if (selectedTier == VaultSecurityTier.TWO_FACTOR) {
                        Text(
                            text = "Faktor 1 ist dein Master-Passwort. Wähle deinen 2. Faktor:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FactorOptionCard(
                                title = "Passwort + Schlüsseldatei (.key)",
                                subtitle = "Sicherheit durch Wissen (Master-Passwort) UND Besitz (digitale Schlüsseldatei)",
                                icon = Icons.Default.Key,
                                badge = "2-Faktor Besitz",
                                isSelected = selectedFactorType == VaultFactorType.PASSWORD_AND_KEYFILE,
                                onClick = { selectedFactorType = VaultFactorType.PASSWORD_AND_KEYFILE },
                                tag = "factor_card_pass_keyfile"
                            )

                        }
                    }

                    // EINGABE: PASSKEY (FIDO2 / Hardware Keystore)
                    if (selectedFactorType == VaultFactorType.PASSKEY) {
                        HorizontalDivider()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Passkey (Google Password Manager & FIDO2)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "WebAuthn-Passkey-Registrierung; noch nicht zur Tresorverschlüsselung nutzbar.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = "Wichtig: Ein Passkey ist kein direkt nutzbarer Verschlüsselungsschlüssel. Die sichere Bindung an den Tresor-Schlüssel ist in dieser Version noch nicht implementiert. Bis sie fertig und getestet ist, wird der Tresor nicht allein durch einen Passkey geschützt.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Button(
                                    onClick = {
                                        isRegisteringPasskey = true
                                        passkeyRegisterStatus = null
                                        coroutineScope.launch {
                                            try {
                                                val res = viewModel.passkeyAuthManager.registerPasskey(context, "myDocAnizer-User")
                                                if (res.isSuccess) {
                                                    // Registration alone does not prove or derive the vault encryption key.
                                                    // Keep the vault protected by a real user password, never a fixed placeholder.
                                                    selectedFactorType = VaultFactorType.PASSWORD
                                                    passkeyRegisterStatus = "Passkey registriert. Er ist noch nicht als Tresor-Verschlüsselungsfaktor eingebunden; Master-Passwort bleibt ausgewählt."
                                                } else {
                                                    passkeyRegisterStatus = res.exceptionOrNull()?.message ?: "Registrierung abgebrochen"
                                                }
                                            } finally {
                                                isRegisteringPasskey = false
                                            }
                                        }
                                    },
                                    enabled = !isRegisteringPasskey,
                                    modifier = Modifier.fillMaxWidth().testTag("btn_register_passkey")
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isRegisteringPasskey) "Registriere Passkey..." else "Passkey auf Gerät einrichten")
                                }

                                passkeyRegisterStatus?.let { msg ->
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    // EINGABE: MASTER-PASSWORT (falls relevant)
                    if (selectedFactorType == VaultFactorType.PASSWORD ||
                        selectedFactorType == VaultFactorType.PASSWORD_AND_KEYFILE
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                                .onGloballyPositioned { masterPasswordAutofillNode.boundingBox = it.boundsInWindow() }
                                .onFocusChanged { focusState ->
                                    @Suppress("DEPRECATION")
                                    if (focusState.isFocused) autofill?.requestAutofillForNode(masterPasswordAutofillNode)
                                    else autofill?.cancelAutofillForNode(masterPasswordAutofillNode)
                                }
                                .testTag("input_wizard_master_password")
                        )

                        OutlinedTextField(
                            value = passwordConfirm,
                            onValueChange = { passwordConfirm = it },
                            label = { Text("Passwort wiederholen") },
                            singleLine = true,
                            isError = password.isNotEmpty() && passwordConfirm.isNotEmpty() && !isPasswordMatching,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                                        4 -> "Stark"
                                        else -> "Sehr stark"
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
                                progress = { (passwordStrength.toFloat() / 5f).coerceIn(0.05f, 1f) },
                                color = when (passwordStrength) {
                                    0, 1 -> MaterialTheme.colorScheme.error
                                    2, 3 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Google Passwort-Manager Integration Card
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        Icon(Icons.Default.Password, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text(
                                                text = "Google Passwort-Manager",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Master-Passwort optional speichern",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Optional",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = "Du kannst das gerade bestätigte Master-Passwort über den Android Credential Manager an deinen eingerichteten Passwort-Manager übergeben. Der Passwort-Manager zeigt seinen eigenen Speicherdialog; erst nach deiner Bestätigung wird der Eintrag gespeichert. Autofill-Vorschläge beim Passwortfeld sind eine separate Android-Funktion.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedButton(
                                    onClick = {
                                        if (password.isBlank()) return@OutlinedButton
                                        isSavingToGoogleManager = true
                                        googleManagerStatusMsg = null
                                        coroutineScope.launch {
                                            try {
                                                val credManager = runCatching { CredentialManager.create(context) }.getOrNull()
                                                if (credManager != null) {
                                                    val req = CreatePasswordRequest("myDocAnizer-Master-Key", password)
                                                    credManager.createCredential(context, req)
                                                    googleManagerStatusMsg = "✅ Im Google Passwort-Manager hinterlegt!"
                                                } else {
                                                    googleManagerStatusMsg = "Android Credential Manager ist auf diesem Gerät nicht verfügbar."
                                                }
                                            } catch (e: CreateCredentialCancellationException) {
                                                googleManagerStatusMsg = "Speichern im Passwort-Manager abgebrochen."
                                            } catch (e: Throwable) {
                                                googleManagerStatusMsg = "Google Passwort-Manager konnte nicht speichern: ${e.localizedMessage ?: "unbekannter Fehler"}"
                                            } finally {
                                                isSavingToGoogleManager = false
                                            }
                                        }
                                    },
                                    enabled = password.isNotBlank() && isPasswordMatching && !isSavingToGoogleManager,
                                    modifier = Modifier.fillMaxWidth().testTag("btn_save_google_password_manager")
                                ) {
                                    Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isSavingToGoogleManager) "Öffne Speicherdialog..." else "Master-Passwort speichern")
                                }

                                googleManagerStatusMsg?.let { msg ->
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (msg.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
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
                                    keyFileExportMessage = "Token kopiert. Speichere ihn sicher und getrennt vom Gerät."
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Token kopieren")
                            }
                            Button(
                                onClick = { keyFileExportLauncher.launch("myDocAnizer-vault-key.key") },
                                modifier = Modifier.weight(1f).testTag("btn_export_vault_keyfile")
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Als .key speichern")
                            }
                            Button(
                                onClick = {
                                    keyFileToken = vaultSecManager.generateKeyFileToken()
                                    keyFileExported = false
                                    keyFileExportMessage = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Neu generieren")
                            }
                        }
                        keyFileExportMessage?.let { message ->
                            Text(message, style = MaterialTheme.typography.labelSmall, color = if (keyFileExported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        }
                        if (selectedTier == VaultSecurityTier.TWO_FACTOR && !keyFileExported) {
                            Text(
                                "Bitte kopiere den Schlüssel oder speichere die .key-Datei, bevor du fortfährst. Ohne sie lässt sich der Tresor nicht entsperren.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Navigation Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { currentSubStep = 1 }, modifier = Modifier.weight(1f)) {
                            Text("Zurück")
                        }
                        Button(
                            onClick = {
                                currentSubStep = 3
                            },
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
                    onClick = {
                        selectedRecovery = VaultRecoveryMethod.BIP39_WORDS
                        pdfStatusMessage = null
                    },
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
                    onClick = {
                        selectedRecovery = VaultRecoveryMethod.EMERGENCY_PDF
                        pdfStatusMessage = null
                    },
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
                    onClick = {
                        selectedRecovery = VaultRecoveryMethod.SHAMIR_2_OF_3
                        pdfStatusMessage = null
                    },
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
                    onClick = {
                        selectedRecovery = VaultRecoveryMethod.RECOVERY_CODE
                        pdfStatusMessage = null
                    },
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
                    onClick = {
                        selectedRecovery = VaultRecoveryMethod.NONE
                        pdfStatusMessage = null
                    },
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
                if (selectedRecovery == VaultRecoveryMethod.EMERGENCY_PDF) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Notfall-Kit PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Das verschlüsselte Recovery-PDF wird nach dem Speichern der Tresor-Sicherheit erzeugt. Dafür legst du eine separate Passphrase fest, die nicht im PDF enthalten ist.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            pdfStatusMessage?.let { message ->
                                Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                } else if (selectedRecovery == VaultRecoveryMethod.BIP39_WORDS) {
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
                        listOf(0 to "Sofort", 1 to "1 Min", 5 to "5 Min", 15 to "15 Min", 30 to "30 Min").forEach { (mins, lbl) ->
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
                            Text("• Stufe: ${selectedTier?.getDisplayName() ?: "Nicht gewählt"}", style = MaterialTheme.typography.bodySmall)
                            Text("• Faktoren: ${selectedFactorType.getDisplayName()}", style = MaterialTheme.typography.bodySmall)
                            Text("• Notfall-Schutz: ${selectedRecovery.getDisplayName()}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "• Biometrie: " + when {
                                    !isBiometricActive -> "Deaktiviert"
                                    vaultSecManager.vaultHeader.value?.biometric2FaEnabled == true -> "Kryptografisch eingerichtet"
                                    else -> "Nach dem Speichern noch zu bestätigen"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (vaultSavedSuccessfully) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Sicherheitseinstellungen erfolgreich gespeichert.",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (vaultSavedSuccessfully &&
                        selectedTier != VaultSecurityTier.UNENCRYPTED &&
                        selectedRecovery == VaultRecoveryMethod.SHAMIR_2_OF_3 &&
                        generatedShares.size == 3
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("card_shamir_recovery_shares"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Deine echten Shamir-Shares", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Diese drei Teile gehören zum gerade eingerichteten Tresor. Bewahre sie getrennt auf; beliebige zwei werden zur Wiederherstellung benötigt. Erzeuge oder teile keine gemeinsame Kopie aller drei Teile.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                generatedShares.sortedBy { it.id }.forEach { share ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(share.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            share.token,
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        TextButton(
                                            onClick = { clipboardManager.setText(AnnotatedString(share.token)) },
                                            modifier = Modifier.align(Alignment.End)
                                        ) { Text("Share ${share.id} kopieren") }
                                    }
                                    if (share.id < 3) HorizontalDivider()
                                }
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Checkbox(
                                        checked = shamirSharesConfirmed,
                                        onCheckedChange = { shamirSharesConfirmed = it },
                                        modifier = Modifier.testTag("checkbox_shamir_shares_saved")
                                    )
                                    Text(
                                        "Ich habe alle drei Shares sicher und getrennt aufbewahrt und weiß, dass zwei davon zur Wiederherstellung nötig sind.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    securitySaveError?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { currentSubStep = 3 }, modifier = Modifier.weight(1f)) {
                            Text("Zurück")
                        }
                        Button(
                            enabled = (selectedTier == VaultSecurityTier.UNENCRYPTED || selectedFactorType != VaultFactorType.PASSKEY) &&
                                (selectedRecovery != VaultRecoveryMethod.SHAMIR_2_OF_3 || !vaultSavedSuccessfully || shamirSharesConfirmed),
                            onClick = {
                                securitySaveError = null
                                if (selectedTier == VaultSecurityTier.UNENCRYPTED) {
                                    if (!vaultSavedSuccessfully) {
                                        viewModel.setUnencryptedModeConfirmed(true)
                                        onPasswordChange("")
                                        vaultSecManager.setUnencryptedModeEnabled(true)
                                        vaultSavedSuccessfully = true
                                    }
                                    onBiometricToggle(false)
                                    isBiometricActive = false
                                    finishSecurityStep()
                                    return@Button
                                }

                                if (!vaultSavedSuccessfully) {
                                    try {
                                        viewModel.setUnencryptedModeConfirmed(false)
                                        val effPass = if (selectedFactorType == VaultFactorType.KEYFILE) keyFileToken else password
                                        onPasswordChange(effPass)
                                        // Enrollment must happen through BiometricPrompt.CryptoObject;
                                        // initializeVault cannot safely enroll an auth-per-use Keystore key itself.
                                        val setupResult = vaultSecManager.initializeVault(
                                            masterPassword = effPass,
                                            enableBiometrics = false,
                                            recoveryMethod = selectedRecovery,
                                            bip39Words = if (selectedRecovery == VaultRecoveryMethod.BIP39_WORDS) generatedWords else null,
                                            securityTier = selectedTier ?: VaultSecurityTier.ONE_FACTOR,
                                            factorType = selectedFactorType,
                                            totpSecret = null,
                                            keyFileToken = if (selectedFactorType == VaultFactorType.KEYFILE || selectedFactorType == VaultFactorType.PASSWORD_AND_KEYFILE) keyFileToken else null,
                                            recoveryCode = if (selectedRecovery == VaultRecoveryMethod.RECOVERY_CODE) recoveryCode else null,
                                            autoLockMinutes = autoLockMinutes
                                        )
                                        generatedShares = setupResult.shamirShares.orEmpty()
                                        vaultSavedSuccessfully = true
                                        if (selectedRecovery == VaultRecoveryMethod.SHAMIR_2_OF_3) {
                                            securitySaveError = "Tresor ist eingerichtet. Speichere jetzt alle drei ausgegebenen Shares; Biometrie und Abschluss folgen nach deiner Bestätigung."
                                            return@Button
                                        }
                                    } catch (e: Exception) {
                                        securitySaveError = "Tresor-Sicherheit konnte nicht gespeichert werden: ${e.message ?: "unbekannter Fehler"}"
                                        return@Button
                                    }
                                }

                                if (isBiometricActive && vaultSecManager.vaultHeader.value?.biometric2FaEnabled != true) {
                                    val activity = context as? FragmentActivity
                                    val cipher = vaultSecManager.createBiometricEnrollmentCipher()
                                    if (activity == null || cipher == null) {
                                        securitySaveError = "Biometrie konnte nicht vorbereitet werden. Prüfe eine aktive Android-Activity und die Gerätesperre. Du kannst es erneut versuchen oder die Biometrie ausschalten und mit dem Master-Passwort fortfahren."
                                        return@Button
                                    }

                                    securitySaveError = "Bitte bestätige jetzt die biometrische Einrichtung im Android-Dialog."
                                    BiometricAuthManager(context).showBiometricPromptForCipher(
                                        activity = activity,
                                        cipher = cipher,
                                        title = "Tresor-Biometrie aktivieren",
                                        subtitle = "Bestätige die Verschlüsselung des Tresor-Schlüssels",
                                        onSuccess = { authenticatedCipher ->
                                            if (vaultSecManager.completeBiometricEnrollment(authenticatedCipher)) {
                                                onBiometricToggle(true)
                                                securitySaveError = null
                                                finishSecurityStep()
                                            } else {
                                                securitySaveError = "Biometrie konnte nicht kryptografisch eingerichtet werden. Der Tresor ist weiterhin mit dem Master-Passwort geschützt. Versuche es erneut oder schalte Biometrie aus."
                                            }
                                        },
                                        onError = { error ->
                                            securitySaveError = "Biometrische Einrichtung abgebrochen oder fehlgeschlagen: $error. Versuche es erneut oder schalte Biometrie aus, um mit dem Master-Passwort fortzufahren."
                                        }
                                    )
                                    return@Button
                                }

                                val biometricReady = vaultSecManager.vaultHeader.value?.biometric2FaEnabled == true
                                onBiometricToggle(isBiometricActive && biometricReady)
                                finishSecurityStep()
                            },
                            modifier = Modifier.weight(1f).testTag("btn_wizard_security_finish")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (vaultSavedSuccessfully && isBiometricActive &&
                                    vaultSecManager.vaultHeader.value?.biometric2FaEnabled != true
                                ) "Biometrie bestätigen" else "Sicherheit speichern"
                            )
                        }
                    }
                }
            }
        }
    }

    // Das Emergency Kit benötigt den initialisierten Vault-DEK und eine getrennte Passphrase.
    if (showEmergencyPdfPassphraseDialog) {
        AlertDialog(
            onDismissRequest = {
                showEmergencyPdfPassphraseDialog = false
                emergencyPdfPassphrase = ""
                emergencyPdfPassphraseConfirm = ""
                currentSubStep = 3
                vaultSavedSuccessfully = false
                pdfStatusMessage = "PDF-Erstellung abgebrochen. Wähle eine andere Recovery-Methode oder starte die PDF-Erstellung erneut."
            },
            title = { Text("Notfall-Kit PDF erstellen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Lege eine eigene Recovery-Passphrase mit mindestens 20 Zeichen fest. Sie wird nicht im PDF gespeichert und muss getrennt vom Ausdruck aufbewahrt werden."
                    )
                    OutlinedTextField(
                        value = emergencyPdfPassphrase,
                        onValueChange = { emergencyPdfPassphrase = it },
                        label = { Text("Recovery-Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = emergencyPdfPassphraseConfirm,
                        onValueChange = { emergencyPdfPassphraseConfirm = it },
                        label = { Text("Passphrase wiederholen") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    if (emergencyPdfPassphrase.isNotEmpty() && emergencyPdfPassphrase.length < 20) {
                        Text("Mindestens 20 Zeichen erforderlich.", color = MaterialTheme.colorScheme.error)
                    }
                    if (emergencyPdfPassphraseConfirm.isNotEmpty() &&
                        emergencyPdfPassphrase != emergencyPdfPassphraseConfirm
                    ) {
                        Text("Die Passphrasen stimmen nicht überein.", color = MaterialTheme.colorScheme.error)
                    }
                    pdfStatusMessage?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = emergencyPdfPassphrase.length >= 20 &&
                        emergencyPdfPassphrase == emergencyPdfPassphraseConfirm,
                    onClick = {
                        val passphrase = emergencyPdfPassphrase.toCharArray()
                        emergencyPdfPassphrase = ""
                        emergencyPdfPassphraseConfirm = ""
                        val target = File(context.cacheDir, "myDocAnizer_Notfall_Emergency_Kit.pdf")
                        val file = vaultSecManager.generateEmergencyPdf(target, passphrase)
                        if (file == null || !file.exists() || file.length() <= 0L) {
                            pdfStatusMessage = "Notfall-PDF konnte nicht erzeugt werden. Prüfe, ob der Tresor entsperrt ist, und versuche es erneut."
                        } else {
                            pdfGeneratedFile = file
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(Intent.createChooser(viewIntent, "Notfall-Kit PDF anzeigen"))
                                } catch (_: Exception) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Notfall-Kit PDF öffnen oder exportieren"))
                                }
                                showEmergencyPdfPassphraseDialog = false
                                pdfStatusMessage = "Notfall-Kit PDF wurde erzeugt und zum Öffnen bereitgestellt."
                                onSecurityComplete()
                            } catch (e: Exception) {
                                pdfStatusMessage = "PDF wurde erzeugt, konnte aber nicht geöffnet werden. Datei: ${file.name}"
                            }
                        }
                    }
                ) { Text("PDF erzeugen und öffnen") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEmergencyPdfPassphraseDialog = false
                    emergencyPdfPassphrase = ""
                    emergencyPdfPassphraseConfirm = ""
                    currentSubStep = 3
                    vaultSavedSuccessfully = false
                    pdfStatusMessage = "PDF-Erstellung abgebrochen. Wähle eine andere Recovery-Methode oder starte die PDF-Erstellung erneut."
                }) { Text("Zurück zur Recovery-Auswahl") }
            }
        )
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
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            if (isWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (isSelected) 1.dp else 0.5.dp,
            if (isSelected) {
                if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) {
                    if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (badge.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(24.dp)
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
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            if (isWarning) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            if (isSelected) 1.dp else 0.5.dp,
            if (isSelected) {
                if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) {
                    if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (badge.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onInfoClick, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Wiederverwendbare Karte zur Auswahl eines Zugangs- oder Sicherheitsfaktors.
 */
@Composable
private fun FactorOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 1.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    badge?.let { b ->
                        if (b.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = b,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp)
                                )
                            }
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

