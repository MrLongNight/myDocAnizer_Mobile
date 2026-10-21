package com.example.ui.views

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AppViewLevel
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.AppLogoBanner
import com.example.ui.views.wizard.*

/**
 * Strukturierter Ersteinrichtungs-Assistent für Dokumenten Tresor (Refactored with Wizard Modules & Sealed Class State).
 */
@Composable
fun SetupWizardDialog(
    viewModel: DocAnizerViewModel,
    isManualReconfig: Boolean = false,
    onDismissSession: () -> Unit = {},
    onFinish: () -> Unit
) {
    val appViewLevel by viewModel.appViewLevel.collectAsState()

    val activeSteps = remember(appViewLevel) {
        when (appViewLevel) {
            AppViewLevel.STANDARD -> listOf(1, 13, 2, 8, 11)
            AppViewLevel.ADVANCED -> listOf(1, 13, 2, 3, 4, 6, 8, 9, 11)
            AppViewLevel.EXPERT -> listOf(1, 13, 2, 12, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        }
    }

    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val safeStepIndex = stepIndex.coerceIn(0, activeSteps.size - 1)
    val currentStepId = activeSteps[safeStepIndex]
    val totalSteps = activeSteps.size
    val displayStep = safeStepIndex + 1
    val isLastStep = safeStepIndex == activeSteps.size - 1

    var showSkipDialog by remember { mutableStateOf(false) }
    var showSecurityRequiredDialog by remember { mutableStateOf(false) }
    var isRestoreMode by rememberSaveable { mutableStateOf(false) }

    val pdfSettings by viewModel.pdfSettings.collectAsState()
    val scannerSettings by viewModel.scannerSettings.collectAsState()
    val syncPasswordKey by viewModel.syncPasswordKey.collectAsState()
    val biometricAuthEnabled by viewModel.biometricAuthEnabled.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val cloudSyncConfig by viewModel.cloudSyncConfig.collectAsState()
    val autoCloudSyncEnabled by viewModel.autoCloudSyncEnabled.collectAsState()
    val isUnencryptedMode by viewModel.isUnencryptedModeConfirmed.collectAsState()

    var customPassword by rememberSaveable { mutableStateOf(syncPasswordKey) }
    var biometricEnabled by rememberSaveable { mutableStateOf(biometricAuthEnabled) }
    var enableDrive by rememberSaveable { mutableStateOf(cloudSyncConfig.enableGoogleDrive) }
    var enableNas by rememberSaveable { mutableStateOf(cloudSyncConfig.enableWebDavNas) }
    var enableManualUsb by rememberSaveable { mutableStateOf(cloudSyncConfig.enableManualUsbExport) }
    var autoMirror by rememberSaveable { mutableStateOf(cloudSyncConfig.autoMirrorToBackupDir) }
    var autoSyncEnabled by rememberSaveable { mutableStateOf(autoCloudSyncEnabled) }
    var webDavUrl by rememberSaveable { mutableStateOf(cloudSyncConfig.webDavUrl) }
    var webDavUser by rememberSaveable { mutableStateOf(cloudSyncConfig.webDavUsername) }
    var webDavPass by rememberSaveable { mutableStateOf(cloudSyncConfig.webDavPassword) }

    val context = LocalContext.current

    if (showSkipDialog) {
        AlertDialog(
            onDismissRequest = { showSkipDialog = false },
            icon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Einrichtung überspringen?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Möchtest du die Einrichtung überspringen? Das für deine Hardware empfohlene KI-Modell wird automatisch im Hintergrund heruntergeladen. In den Einstellungen kann jederzeit ein anderes Modell gewählt werden.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showSkipDialog = false
                    viewModel.setWizardCompleted(true)
                    val recModel = availableModels.find { it.isHardwareRecommended } ?: availableModels.firstOrNull()
                    if (recModel != null) {
                        viewModel.downloadModel(recModel.id)
                        Toast.makeText(context, "Hardware-empfohlenes Modell (${recModel.name}) wird im Hintergrund geladen.", Toast.LENGTH_LONG).show()
                    }
                    if (customPassword.isBlank() && !isUnencryptedMode) {
                        viewModel.openSecuritySettingsTab()
                    }
                    onFinish()
                }) {
                    Text("Überspringen & Modell laden")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSkipDialog = false
                    val recModel = availableModels.find { it.isHardwareRecommended } ?: availableModels.firstOrNull()
                    if (recModel != null) {
                        viewModel.downloadModel(recModel.id)
                        Toast.makeText(context, "Hardware-empfohlenes Modell (${recModel.name}) wird im Hintergrund geladen.", Toast.LENGTH_LONG).show()
                    }
                    if (customPassword.isBlank() && !isUnencryptedMode) {
                        viewModel.openSecuritySettingsTab()
                    }
                    onDismissSession()
                }) {
                    Text("Nur für jetzt")
                }
            }
        )
    }

    if (showSecurityRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityRequiredDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Sicherheitsfaktor erforderlich", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Die Vergabe eines Master-Passworts bzw. Zugangsfaktors ist im Assistenten Pflicht, es sei denn, die Stufe 'A) Keine Verschlüsselung (Unverschlüsselt)' wird aktiv gewählt.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { showSecurityRequiredDialog = false }) {
                    Text("Verstanden")
                }
            }
        )
    }

    BackHandler {
        if (isRestoreMode) {
            isRestoreMode = false
        } else if (safeStepIndex > 0) {
            stepIndex--
        } else if (isManualReconfig) {
            onFinish()
        } else {
            showSkipDialog = true
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("setup_wizard_dialog"),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isRestoreMode) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                RestoreWizardContent(
                    viewModel = viewModel,
                    onBackToSetup = { isRestoreMode = false },
                    onFinish = {
                        viewModel.setWizardCompleted(true)
                        onFinish()
                    }
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppLogoBanner(
                        iconHeight = 52.dp,
                        fontSize = 16f,
                        includeContainer = false,
                        showTagline = false
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$displayStep / $totalSteps",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { displayStep.toFloat() / totalSteps },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Crossfade(targetState = currentStepId, label = "wizard_step_crossfade") { stepId ->
                        when (WizardStep.fromId(stepId)) {
                            is WizardStep.Welcome -> StepWelcome(viewModel, isManualReconfig)
                            is WizardStep.PrivacyAndAnalytics -> StepPrivacyAndAnalytics(viewModel)
                            is WizardStep.ViewModeAndRestore -> StepViewModeAndRestore(viewModel, isManualReconfig, onStartRestore = { isRestoreMode = true })
                            is WizardStep.IncomeExpenseConfig -> StepIncomeExpenseConfig(viewModel)
                            is WizardStep.StorageLocation -> StepStorageLocation(viewModel, pdfSettings)
                            is WizardStep.ScanCaptureAndTrigger -> StepScanCaptureAndTrigger(viewModel, scannerSettings)
                            is WizardStep.QualityAndAutoColor -> StepQualityAndAutoColor(viewModel, pdfSettings)
                            is WizardStep.LlmModelSelection -> StepLlmModelSelection(viewModel, availableModels)
                            is WizardStep.LlmInferenceSettings -> StepLlmInferenceSettings(viewModel)
                            is WizardStep.SecurityAndBiometrics -> SecurityWizardStep(
                                viewModel = viewModel,
                                biometricEnabled = biometricEnabled,
                                onBiometricToggle = { biometricEnabled = it },
                                passwordValue = customPassword,
                                onPasswordChange = { customPassword = it },
                                onSecurityComplete = {
                                    stepIndex = (safeStepIndex + 1).coerceAtMost(activeSteps.size - 1)
                                }
                            )
                            is WizardStep.BackupAndRecoveryStrategy -> StepBackupAndRecoveryStrategy(
                                viewModel = viewModel,
                                enableDrive = enableDrive,
                                onToggleDrive = { enableDrive = it },
                                enableNas = enableNas,
                                onToggleNas = { enableNas = it },
                                enableManualUsb = enableManualUsb,
                                onToggleManualUsb = { enableManualUsb = it },
                                autoMirror = autoMirror,
                                onToggleAutoMirror = { autoMirror = it },
                                autoSync = autoSyncEnabled,
                                onToggleAutoSync = { autoSyncEnabled = it },
                                webDavUrl = webDavUrl,
                                onWebDavUrlChange = { webDavUrl = it },
                                webDavUser = webDavUser,
                                onWebDavUserChange = { webDavUser = it },
                                webDavPass = webDavPass,
                                onWebDavPassChange = { webDavPass = it }
                            )
                            is WizardStep.DatabaseAndMaintenance -> StepDatabaseAndMaintenance(viewModel)
                            is WizardStep.ReadySummary -> StepReadySummary(viewModel, scannerSettings, pdfSettings, availableModels, biometricEnabled, isManualReconfig)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (safeStepIndex > 0) {
                        TextButton(onClick = {
                            stepIndex = (safeStepIndex - 1).coerceAtLeast(0)
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Zurück")
                        }
                    } else {
                        TextButton(onClick = {
                            if (isManualReconfig) {
                                onDismissSession()
                            } else {
                                showSkipDialog = true
                            }
                        }) {
                            Text(if (isManualReconfig) "Schließen" else "Überspringen")
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (WizardStep.fromId(currentStepId) !is WizardStep.SecurityAndBiometrics && isLastStep && !isManualReconfig) {
                            OutlinedButton(
                                onClick = {
                                    if (customPassword.isBlank() && !isUnencryptedMode) {
                                        showSecurityRequiredDialog = true
                                        return@OutlinedButton
                                    }
                                    viewModel.setSyncPasswordKey(customPassword)
                                    viewModel.setBiometricAuthEnabled(biometricEnabled)
                                    viewModel.setAutoCloudSyncEnabled(autoCloudSyncEnabled)
                                    viewModel.updateCloudSyncConfig(
                                        cloudSyncConfig.copy(
                                            enableGoogleDrive = enableDrive,
                                            enableWebDavNas = enableNas,
                                            enableLocalVault = true,
                                            enableManualUsbExport = enableManualUsb,
                                            webDavUrl = webDavUrl,
                                            webDavUsername = webDavUser,
                                            webDavPassword = webDavPass,
                                            autoMirrorToBackupDir = autoMirror,
                                            isAirGappedStrict = false,
                                            syncTrigger = if (autoSyncEnabled) "AUTO_AFTER_SCAN" else "MANUAL",
                                            syncTarget = if (enableDrive && enableNas) "HYBRID_ALL" else if (enableNas) "WEBDAV_NAS" else if (enableDrive) "GOOGLE_DRIVE" else if (enableManualUsb) "MANUAL_USB" else "OFFLINE_ONLY"
                                        )
                                    )
                                    viewModel.setWizardCompleted(true)
                                    viewModel.dismissFirstStepsTour(markCompleted = true)
                                    onFinish()
                                },
                                modifier = Modifier.testTag("btn_wizard_skip_tour")
                            ) {
                                Text("Tour überspringen")
                            }
                        }

                        if (WizardStep.fromId(currentStepId) !is WizardStep.SecurityAndBiometrics) Button(
                            onClick = {
                                val currentStepItem = WizardStep.fromId(currentStepId)
                                if (currentStepItem is WizardStep.SecurityAndBiometrics) {
                                    if (customPassword.isBlank() && !isUnencryptedMode) {
                                        showSecurityRequiredDialog = true
                                        return@Button
                                    }
                                }
                                if (!isLastStep) {
                                    if (currentStepItem is WizardStep.LlmModelSelection) {
                                        val selectedModel = availableModels.find { it.isSelected }
                                        if (selectedModel != null && !selectedModel.isDownloaded && !selectedModel.isDownloading) {
                                            viewModel.startBackgroundDownload(selectedModel.id)
                                        }
                                    }
                                    stepIndex = (safeStepIndex + 1).coerceAtMost(activeSteps.size - 1)
                                } else {
                                    if (customPassword.isBlank() && !isUnencryptedMode) {
                                        showSecurityRequiredDialog = true
                                        return@Button
                                    }
                                    viewModel.setSyncPasswordKey(customPassword)
                                    viewModel.setBiometricAuthEnabled(biometricEnabled)
                                    viewModel.setAutoCloudSyncEnabled(autoCloudSyncEnabled)

                                    viewModel.updateCloudSyncConfig(
                                        cloudSyncConfig.copy(
                                            enableGoogleDrive = enableDrive,
                                            enableWebDavNas = enableNas,
                                            enableLocalVault = true,
                                            enableManualUsbExport = enableManualUsb,
                                            webDavUrl = webDavUrl,
                                            webDavUsername = webDavUser,
                                            webDavPassword = webDavPass,
                                            autoMirrorToBackupDir = autoMirror,
                                            isAirGappedStrict = false,
                                            syncTrigger = if (autoSyncEnabled) "AUTO_AFTER_SCAN" else "MANUAL",
                                            syncTarget = if (enableDrive && enableNas) "HYBRID_ALL" else if (enableNas) "WEBDAV_NAS" else if (enableDrive) "GOOGLE_DRIVE" else if (enableManualUsb) "MANUAL_USB" else "OFFLINE_ONLY"
                                        )
                                    )

                                    if (!isManualReconfig) {
                                        viewModel.setWizardCompleted(true)
                                    }
                                    onFinish()
                                }
                            },
                            modifier = Modifier.testTag("btn_wizard_next")
                        ) {
                            Text(
                                if (isLastStep) {
                                    if (isManualReconfig) "Speichern" else "Fertig & Tour starten"
                                } else if (safeStepIndex == 0) {
                                    "Einrichtung starten"
                                } else "Weiter"
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
}
