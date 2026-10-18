package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.model.DocTypeItem
import com.example.model.DocumentEntity
import com.example.model.PendingRuleSuggestion
import com.example.model.ChatMessage
import com.example.model.SavedChatSession
import com.example.model.LlmUndoAction
import com.example.model.ExplorerFilter
import com.example.model.ImportTemplateItem
import com.example.model.PdfSettings
import com.example.model.ScannerSettings
import com.example.model.TemplateItem
import com.example.model.DocRule
import com.example.model.BatchDocumentItem
import com.example.model.BatchItemStatus
import com.example.model.*
import com.example.service.*
import com.example.BuildConfig
import com.example.util.DebugDataSeeder
import com.example.util.UserProfileSimulation
import com.example.util.UserPersonaFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DocAnizerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val documentDao = db.documentDao()
    val customFieldDao = db.customFieldDao()
    val financeDao = db.financeDao()
    private val settingsRepo = SettingsRepository(application)
    val ruleRepo = DocRuleRepository(application)
    val ruleRepository: DocRuleRepository get() = ruleRepo
    val llmService = LlmService(application)
    val biometricAuthManager = BiometricAuthManager(application)
    val passkeyAuthManager = PasskeyAuthManager(application)
    val p2pSyncManager = P2pSyncManager(application, db)
    val vaultSecurityManager = VaultSecurityManager.getInstance(application)
    val vaultStatus: StateFlow<VaultStatus> = vaultSecurityManager.vaultStatus
    val vaultHeader: StateFlow<VaultHeader?> = vaultSecurityManager.vaultHeader

    // Domain Managers
    val dmsManager = DmsManager(application, documentDao, customFieldDao, ruleRepo, settingsRepo, llmService, viewModelScope) {
        incrementScannedDocumentCount()
    }
    val financeManager = FinanceManager(financeDao, settingsRepo, viewModelScope) {
        triggerRatingPromptOnSuccess()
    }
    val syncManager = SyncManager(p2pSyncManager, settingsRepo, viewModelScope)
    val analyticsManager = AnalyticsManager(application, settingsRepo.analyticsEnabled)
    private val migrationService = DocumentMigrationService(application, documentDao, vaultSecurityManager)
    
    private val _migrationResult = MutableStateFlow<DocumentMigrationService.MigrationResult?>(null)
    val migrationResult = _migrationResult.asStateFlow()

    fun runDocumentMigration() {
        viewModelScope.launch {
            val result = migrationService.migrateAllDocuments(syncPasswordKey.value)
            _migrationResult.value = result
            AppAuditLogger.log(
                category = LogCategory.STORAGE_DMS,
                tag = "Migration",
                message = "Dokumenten-Migration abgeschlossen: ${result.migrated} migriert, ${result.skipped} übersprungen, ${result.failed} fehlgeschlagen.",
                details = "Gesamt: ${result.total}"
            )
        }
    }

    init {
        AppAuditLogger.init(application)
        settingsRepo.incrementAppLaunchCount()
        llmService.analyticsManager = analyticsManager
        llmService.updatePreferenceMode(settingsRepo.llmPreferenceMode.value)

        // Automatische Verknüpfung bestehender Modelle & Katalog-Check nach Intervall
        viewModelScope.launch(Dispatchers.IO) {
            try {
                llmService.scanAndLinkExistingModels()
                val autoCheckEnabled = settingsRepo.llmCatalogAutoCheckEnabled.value
                val interval = settingsRepo.llmCatalogCheckInterval.value
                val lastSync = llmService.lastCatalogSync.value
                val now = System.currentTimeMillis()

                val shouldCheck = autoCheckEnabled && when (interval) {
                    "EVERY_START" -> true
                    "DAILY" -> (now - lastSync) > 24 * 60 * 60 * 1000L
                    "WEEKLY" -> (now - lastSync) > 7 * 24 * 60 * 60 * 1000L
                    "DISABLED" -> false
                    else -> true
                }
                if (shouldCheck) {
                    llmService.syncModelCatalogFromRemote(forceCheck = false)
                }
            } catch (_: Throwable) {}
        }

        // Analytics Launch Event
        analyticsManager.logEvent("app_launch")

        // Listen for analytics consent changes
        viewModelScope.launch {
            settingsRepo.analyticsEnabled.collect { enabled ->
                analyticsManager.updateConsent(enabled)
            }
        }
    }

    val auditLogs: StateFlow<List<AuditLogEntry>> = AppAuditLogger.logs

    val analyticsEnabled: StateFlow<Boolean> = settingsRepo.analyticsEnabled
    fun setAnalyticsEnabled(enabled: Boolean) = settingsRepo.setAnalyticsEnabled(enabled)

    fun exportAndShareAuditLog(context: android.content.Context) {
        try {
            val file = AppAuditLogger.exportLogFile(context)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "myDocAnizer Diagnose- & Audit-Protokoll")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = android.content.Intent.createChooser(intent, "Diagnose-Log teilen / exportieren").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e("DocAnizerViewModel", "Fehler beim Exportieren des Audit-Logs: ${e.message}", e)
        }
    }

    fun clearAuditLogs(context: android.content.Context) {
        AppAuditLogger.clearLogs(context)
    }

    val appLaunchCount: StateFlow<Int> = settingsRepo.appLaunchCount
    val firstLaunchTime: StateFlow<Long> = settingsRepo.firstLaunchTime
    val lastRatingDismissedTime: StateFlow<Long> = settingsRepo.lastRatingDismissedTime
    val launchesSinceDismissal: StateFlow<Int> = settingsRepo.launchesSinceDismissal

    private val _showSuccessRatingPrompt = MutableStateFlow(false)
    val showSuccessRatingPrompt: StateFlow<Boolean> = _showSuccessRatingPrompt.asStateFlow()

    fun snoozeRatingPrompt() {
        settingsRepo.snoozeRatingPrompt()
        _showSuccessRatingPrompt.value = false
    }

    fun dismissSuccessRatingPrompt() {
        _showSuccessRatingPrompt.value = false
    }

    fun triggerRatingPromptOnSuccess() {
        val hasRated = hasRatedOrSkipped.value
        val launches = appLaunchCount.value
        val scans = scannedDocumentCount.value
        val firstLaunch = firstLaunchTime.value
        val dismissedTime = lastRatingDismissedTime.value
        val snoozeLaunches = launchesSinceDismissal.value

        val now = System.currentTimeMillis()
        val installedAtLeast3Days = (now - firstLaunch) >= 3 * 24 * 60 * 60 * 1000L
        val snoozeDaysPassed = (now - dismissedTime) >= 14 * 24 * 60 * 60 * 1000L
        val snoozeLaunchesPassed = snoozeLaunches >= 10

        // Verschärfte, professionelle Kriterien für absolute Unaufdringlichkeit:
        // 1. Hat noch nicht bewertet oder dauerhaft abgelehnt.
        // 2. Mindestens 10 App-Launches (etablierter Nutzer).
        // 3. Mindestens 5 erfolgreiche Dokumentenscans (kennt den Mehrwert der App).
        // 4. App muss seit mindestens 3 Tagen installiert sein (kein nerviges Pop-up am 1. Tag).
        // 5. Falls zuvor weggeschoben (snoozed), müssen 14 Tage vergangen UND 10 weitere App-Starts erfolgt sein.
        val isEligible = !hasRated &&
                launches >= 10 &&
                scans >= 5 &&
                installedAtLeast3Days &&
                (dismissedTime == 0L || (snoozeDaysPassed && snoozeLaunchesPassed))

        if (isEligible) {
            _showSuccessRatingPrompt.value = true
        }
    }
    val scannedDocumentCount: StateFlow<Int> = settingsRepo.scannedDocumentCount
    val hasRatedOrSkipped: StateFlow<Boolean> = settingsRepo.hasRatedOrSkipped

    fun incrementScannedDocumentCount() {
        settingsRepo.incrementScannedDocumentCount()
        triggerRatingPromptOnSuccess()
    }

    fun setHasRatedOrSkipped(value: Boolean) {
        settingsRepo.setHasRatedOrSkipped(value)
        _showSuccessRatingPrompt.value = false
    }

    // Vordefinierte & Benutzerdefinierte Zusatzfelder (Custom Fields)
    val customFields: StateFlow<List<com.example.model.CustomFieldEntity>> = customFieldDao.getAllCustomFields()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDocumentCustomFieldValues: StateFlow<List<com.example.model.DocumentCustomFieldValueEntity>> = customFieldDao.getAllDocumentCustomFieldValues()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enableIncomeExpenseTracking: StateFlow<Boolean> = settingsRepo.enableIncomeExpenseTracking
    val enableHouseholdBook: StateFlow<Boolean> = settingsRepo.enableHouseholdBook
    fun setEnableIncomeExpenseTracking(enabled: Boolean) = settingsRepo.setEnableIncomeExpenseTracking(enabled)
    fun setEnableHouseholdBook(enabled: Boolean) = settingsRepo.setEnableHouseholdBook(enabled)

    val enableCashTracker: StateFlow<Boolean> = settingsRepo.enableCashTracker
    fun setEnableCashTracker(enabled: Boolean) = settingsRepo.setEnableCashTracker(enabled)

    val enableReceiptExpenses: StateFlow<Boolean> = settingsRepo.enableReceiptExpenses
    fun setEnableReceiptExpenses(enabled: Boolean) = settingsRepo.setEnableReceiptExpenses(enabled)

    val enableBankStatementImport: StateFlow<Boolean> = settingsRepo.enableBankStatementImport
    fun setEnableBankStatementImport(enabled: Boolean) = settingsRepo.setEnableBankStatementImport(enabled)

    val enableMonthlyReconciliation: StateFlow<Boolean> = settingsRepo.enableMonthlyReconciliation
    fun setEnableMonthlyReconciliation(enabled: Boolean) = settingsRepo.setEnableMonthlyReconciliation(enabled)

    val notifyReconciliationDiscrepancies: StateFlow<Boolean> = settingsRepo.notifyReconciliationDiscrepancies
    fun setNotifyReconciliationDiscrepancies(enabled: Boolean) = settingsRepo.setNotifyReconciliationDiscrepancies(enabled)

    // Dashboard anpassbare Widgets
    val showBelegQuickScanWidget: StateFlow<Boolean> = settingsRepo.showBelegQuickScanWidget
    fun setShowBelegQuickScanWidget(show: Boolean) = settingsRepo.setShowBelegQuickScanWidget(show)

    val showCashTrackerWidget: StateFlow<Boolean> = settingsRepo.showCashTrackerWidget
    fun setShowCashTrackerWidget(show: Boolean) = settingsRepo.setShowCashTrackerWidget(show)

    val showKpiWidgets: StateFlow<Boolean> = settingsRepo.showKpiWidgets
    fun setShowKpiWidgets(show: Boolean) = settingsRepo.setShowKpiWidgets(show)

    val showDeadlinesWidget: StateFlow<Boolean> = settingsRepo.showDeadlinesWidget
    fun setShowDeadlinesWidget(show: Boolean) = settingsRepo.setShowDeadlinesWidget(show)

    val showFinanceWidget: StateFlow<Boolean> = settingsRepo.showFinanceWidget
    fun setShowFinanceWidget(show: Boolean) = settingsRepo.setShowFinanceWidget(show)

    val showReconciliationWidget: StateFlow<Boolean> = settingsRepo.showReconciliationWidget
    fun setShowReconciliationWidget(show: Boolean) = settingsRepo.setShowReconciliationWidget(show)

    val showCategoryDistributionWidget: StateFlow<Boolean> = settingsRepo.showCategoryDistributionWidget
    fun setShowCategoryDistributionWidget(show: Boolean) = settingsRepo.setShowCategoryDistributionWidget(show)

    val showSecurityScoreWidget: StateFlow<Boolean> = settingsRepo.showSecurityScoreWidget
    fun setShowSecurityScoreWidget(show: Boolean) = settingsRepo.setShowSecurityScoreWidget(show)

    val showCustomFieldsWidget: StateFlow<Boolean> = settingsRepo.showCustomFieldsWidget
    fun setShowCustomFieldsWidget(show: Boolean) = settingsRepo.setShowCustomFieldsWidget(show)

    val showRecentDocsWidget: StateFlow<Boolean> = settingsRepo.showRecentDocsWidget
    fun setShowRecentDocsWidget(show: Boolean) = settingsRepo.setShowRecentDocsWidget(show)

    val showFolderShortcutsWidget: StateFlow<Boolean> = settingsRepo.showFolderShortcutsWidget
    fun setShowFolderShortcutsWidget(show: Boolean) = settingsRepo.setShowFolderShortcutsWidget(show)

    val showInboxDocsWidget: StateFlow<Boolean> = settingsRepo.showInboxDocsWidget
    fun setShowInboxDocsWidget(show: Boolean) = settingsRepo.setShowInboxDocsWidget(show)

    val showBudgetWatchWidget: StateFlow<Boolean> = settingsRepo.showBudgetWatchWidget
    fun setShowBudgetWatchWidget(show: Boolean) = settingsRepo.setShowBudgetWatchWidget(show)

    val showQuickActionsWidget: StateFlow<Boolean> = settingsRepo.showQuickActionsWidget
    fun setShowQuickActionsWidget(show: Boolean) = settingsRepo.setShowQuickActionsWidget(show)

    val showQuickNoteWidget: StateFlow<Boolean> = settingsRepo.showQuickNoteWidget
    fun setShowQuickNoteWidget(show: Boolean) = settingsRepo.setShowQuickNoteWidget(show)

    val quickNoteText: StateFlow<String> = settingsRepo.quickNoteText
    fun setQuickNoteText(text: String) = settingsRepo.setQuickNoteText(text)

    // Individuelle Dashboard-Elemente & Sortierung
    val customDashboardWidgets: StateFlow<List<com.example.model.CustomDashboardWidget>> = settingsRepo.customDashboardWidgets
    fun addCustomDashboardWidget(widget: com.example.model.CustomDashboardWidget) = settingsRepo.addCustomWidget(widget)
    fun updateCustomDashboardWidget(widget: com.example.model.CustomDashboardWidget) = settingsRepo.updateCustomWidget(widget)
    fun deleteCustomDashboardWidget(id: String) = settingsRepo.deleteCustomWidget(id)
    fun toggleCustomDashboardWidget(id: String, enabled: Boolean) = settingsRepo.toggleCustomWidget(id, enabled)
    fun updateCustomWidgetChecklist(id: String, items: List<com.example.model.ChecklistItem>) = settingsRepo.updateCustomWidgetChecklist(id, items)
    fun updateCustomWidgetNote(id: String, text: String) = settingsRepo.updateCustomWidgetNote(id, text)

    val dashboardWidgetOrder: StateFlow<List<String>> = settingsRepo.dashboardWidgetOrder
    fun setDashboardWidgetOrder(order: List<String>) = settingsRepo.setDashboardWidgetOrder(order)
    fun moveDashboardWidgetUp(widgetId: String) = settingsRepo.moveWidgetUp(widgetId)
    fun moveDashboardWidgetDown(widgetId: String) = settingsRepo.moveWidgetDown(widgetId)
    fun resetDashboardWidgetOrder() = settingsRepo.resetWidgetOrder()

    // Element-spezifische Zeitraum-Scopes
    val elementPeriodScopes: StateFlow<Map<String, com.example.model.ElementPeriodScope>> = settingsRepo.elementPeriodScopes
    fun getElementPeriodScope(elementId: String, default: com.example.model.ElementPeriodScope = com.example.model.ElementPeriodScope.ALL): com.example.model.ElementPeriodScope = settingsRepo.getElementPeriodScope(elementId, default)
    fun setElementPeriodScope(elementId: String, scope: com.example.model.ElementPeriodScope) = settingsRepo.setElementPeriodScope(elementId, scope)

    // Homescreen Widget Intent Triggers
    private val _targetNavigationTab = MutableStateFlow<String?>(null)
    val targetNavigationTab: StateFlow<String?> = _targetNavigationTab.asStateFlow()
    fun setTargetNavigationTab(tab: String?) {
        _targetNavigationTab.value = tab
    }

    private val _targetSettingsSubTab = MutableStateFlow<Int?>(null)
    val targetSettingsSubTab: StateFlow<Int?> = _targetSettingsSubTab.asStateFlow()
    fun setTargetSettingsSubTab(subTab: Int?) {
        _targetSettingsSubTab.value = subTab
    }

    private val _directOpenCashTracker = MutableStateFlow(false)
    val directOpenCashTracker: StateFlow<Boolean> = _directOpenCashTracker.asStateFlow()
    fun setDirectOpenCashTracker(open: Boolean) {
        _directOpenCashTracker.value = open
    }

    private val _directOpenReconciliation = MutableStateFlow(false)
    val directOpenReconciliation: StateFlow<Boolean> = _directOpenReconciliation.asStateFlow()
    fun setDirectOpenReconciliation(open: Boolean) {
        _directOpenReconciliation.value = open
    }

    // Delegated to FinanceManager
    val cashTransactions: StateFlow<List<com.example.model.CashTransactionEntity>> = financeManager.cashTransactions
    val bankStatementEntries: StateFlow<List<com.example.model.BankStatementEntryEntity>> = financeManager.bankStatementEntries
    fun addCashTransaction(tx: com.example.model.CashTransactionEntity) = financeManager.addCashTransaction(tx)
    fun updateCashTransaction(tx: com.example.model.CashTransactionEntity) = financeManager.updateCashTransaction(tx)
    fun deleteCashTransaction(id: String) = financeManager.deleteCashTransaction(id)
    fun importBankStatementEntries(entries: List<com.example.model.BankStatementEntryEntity>, onComplete: ((Int) -> Unit)? = null) = financeManager.importBankStatementEntries(entries, onComplete)
    fun importDemoBankStatement(monthKey: String, onComplete: ((Int) -> Unit)? = null) = financeManager.importDemoBankStatement(monthKey, onComplete)
    fun deleteBankStatementEntry(id: String) = financeManager.deleteBankStatementEntry(id)
    fun clearBankStatementEntries() = financeManager.clearBankStatementEntries()
    suspend fun importBankStatementEntriesSync(entries: List<com.example.model.BankStatementEntryEntity>): Int = financeManager.importBankStatementEntriesSync(entries)

    // Delegated to DmsManager (Custom Fields)
    fun addCustomField(field: com.example.model.CustomFieldEntity) = dmsManager.addCustomField(field)
    suspend fun addCustomFieldSync(field: com.example.model.CustomFieldEntity): Boolean = dmsManager.addCustomFieldSync(field)
    fun updateCustomField(field: com.example.model.CustomFieldEntity) = dmsManager.updateCustomField(field)
    suspend fun updateCustomFieldSync(field: com.example.model.CustomFieldEntity): Boolean = dmsManager.updateCustomFieldSync(field)
    fun deleteCustomField(fieldId: String) = dmsManager.deleteCustomField(fieldId)
    fun setDocumentCustomFieldValue(documentId: Long, customFieldId: String, value: String) = dmsManager.setDocumentCustomFieldValue(documentId, customFieldId, value)

    // Delegated to SyncManager (P2P Sync)
    val p2pPairedDevices: StateFlow<List<com.example.model.PairedDevice>> = syncManager.p2pPairedDevices
    val p2pServerStatus: StateFlow<com.example.model.P2pServerStatus> = syncManager.p2pServerStatus
    val p2pHostPairingPin: StateFlow<String?> = syncManager.p2pHostPairingPin
    val p2pPendingPairingRequests: StateFlow<List<com.example.model.IncomingPairingRequest>> = syncManager.p2pPendingPairingRequests
    val p2pSyncProgress: StateFlow<com.example.model.P2pSyncProgressState> = syncManager.p2pSyncProgress
    val p2pActiveConflicts: StateFlow<List<com.example.model.SyncConflict>> = syncManager.p2pActiveConflicts
    val p2pSyncHistory: StateFlow<List<com.example.model.P2pSyncLog>> = syncManager.p2pSyncHistory
    val p2pLocalDeviceName: StateFlow<String> = syncManager.p2pLocalDeviceName

    fun setP2pDeviceName(name: String) = syncManager.setP2pDeviceName(name)
    fun startP2pServer() = syncManager.startP2pServer()
    fun stopP2pServer() = syncManager.stopP2pServer()
    fun startP2pHostPairingMode(): String = syncManager.startP2pHostPairingMode()
    fun stopP2pHostPairingMode() = syncManager.stopP2pHostPairingMode()
    fun authorizeP2pPairingRequest(requestId: String, allow: Boolean) = syncManager.authorizeP2pPairingRequest(requestId, allow)
    fun pairWithP2pDevice(ip: String, port: Int = 8765, pin: String, onResult: (Result<PairingResult>) -> Unit) = syncManager.pairWithP2pDevice(ip, port, pin, onResult)
    fun syncWithP2pDevice(device: com.example.model.PairedDevice, onResult: (Result<com.example.model.P2pSyncLog>) -> Unit = {}) = syncManager.syncWithP2pDevice(device, onResult)
    fun syncAllP2pDevices(onComplete: (Int, Int) -> Unit = { _, _ -> }) = syncManager.syncAllP2pDevices(onComplete)
    fun resolveP2pConflict(conflict: com.example.model.SyncConflict, action: com.example.model.ConflictResolutionAction) = syncManager.resolveP2pConflict(conflict, action)
    fun removeP2pPairedDevice(deviceId: String) = syncManager.removeP2pPairedDevice(deviceId)
    fun pairWithDesktopLightApp(qrJson: String, onResult: (Boolean, String) -> Unit) = syncManager.pairWithDesktopLightApp(qrJson, onResult)

    // Biometrische Autorisierung (Fingerabdruck, Face Unlock, PIN)
    val biometricAuthEnabled: StateFlow<Boolean> = settingsRepo.biometricAuthEnabled
    private val lockManagerInitialized = LockManager.initialize(
        vaultConfigured = vaultSecurityManager.vaultHeader.value != null,
        appLockRequired = settingsRepo.biometricAuthEnabled.value,
        debugBuild = BuildConfig.DEBUG
    )
    val isAppUnlocked: StateFlow<Boolean> = LockManager.appUnlocked

    fun setBiometricAuthEnabled(enabled: Boolean) {
        settingsRepo.setBiometricAuthEnabled(enabled)
        if (!enabled) LockManager.onAppUnlockedAfterAuthentication()
    }

    /** Marks the app session unlocked only after the caller completed a real vault unlock. */
    fun completeVaultUnlock(): Boolean {
        val status = vaultSecurityManager.vaultStatus.value
        if (!status.isUnencryptedMode && !vaultSecurityManager.isUnlocked()) return false
        LockManager.onAppUnlockedAfterAuthentication()
        return true
    }

    /** Deliberately isolated debug-only escape hatch; never available in release builds. */
    fun debugUnlockAppForDevelopment() {
        if (BuildConfig.DEBUG) LockManager.onAppUnlockedAfterAuthentication()
    }

    fun lockApp() {
        vaultSecurityManager.lockVault()
        LockManager.onAppLocked()
    }

    fun getBiometricStatusLabel(): String = biometricAuthManager.getHardwareStatusLabel()

    fun authenticateBiometric(
        activity: androidx.fragment.app.FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val header = vaultSecurityManager.vaultHeader.value
        if (header?.biometric2FaEnabled == true) {
            val cipher = vaultSecurityManager.createBiometricUnlockCipher()
            if (cipher == null) {
                onError("Biometrischer Schlüssel fehlt, wurde invalidiert oder ist nicht verfügbar. Entsperre mit dem Master-Passwort.")
                return
            }
            biometricAuthManager.showBiometricPromptForCipher(
                activity = activity,
                cipher = cipher,
                title = "myDocAnizer-Tresor entsperren",
                subtitle = "Biometrische Authentifizierung schützt die tatsächliche Schlüsselentschlüsselung",
                onSuccess = { authenticatedCipher ->
                    if (vaultSecurityManager.completeBiometricUnlock(authenticatedCipher)) {
                        LockManager.onAppUnlockedAfterAuthentication()
                        onSuccess()
                    } else {
                        onError("Tresor konnte mit dem authentifizierten Schlüssel nicht entsperrt werden.")
                    }
                },
                onError = onError
            )
        } else {
            onError("Biometrisches Entsperren ist erst nach kryptografischer Tresor-Bindung verfügbar. Nutze das Master-Passwort.")
        }
    }

    // Delegated to DmsManager (Rules)
    val docRules: StateFlow<List<com.example.model.DocRule>> = dmsManager.docRules
    fun addDocRule(rule: com.example.model.DocRule) = dmsManager.addDocRule(rule)
    fun addDocRules(rules: List<com.example.model.DocRule>) = dmsManager.addDocRules(rules)
    fun updateDocRule(rule: com.example.model.DocRule) = dmsManager.updateDocRule(rule)
    fun deleteDocRule(ruleId: String) = dmsManager.deleteDocRule(ruleId)
    fun toggleDocRule(ruleId: String, enabled: Boolean) = dmsManager.toggleDocRule(ruleId, enabled)

    // On-Device LLM Modelle & Hardware-Telemetrie
    val availableModels: StateFlow<List<HuggingFaceModelInfo>> = llmService.availableModels
    val activeDownloadingModel: StateFlow<HuggingFaceModelInfo?> = llmService.activeDownloadingModel
    val isGeneratingAi: StateFlow<Boolean> = llmService.isGenerating
    val deviceHardwareInfo: StateFlow<com.example.model.DeviceHardwareInfo> = llmService.deviceHardware
    val llmInferenceConfig: StateFlow<com.example.model.LlmInferenceConfig> = settingsRepo.llmInferenceConfig
    val llmPreferenceMode: StateFlow<com.example.model.LlmPreferenceMode> = settingsRepo.llmPreferenceMode
    val isCheckingNewModels: StateFlow<Boolean> = llmService.isCheckingNewModels
    val newModelsNotification: StateFlow<String?> = llmService.newModelsNotification
    val lastCatalogSync: StateFlow<Long> = llmService.lastCatalogSync

    fun setLlmPreferenceMode(mode: com.example.model.LlmPreferenceMode) {
        settingsRepo.setLlmPreferenceMode(mode)
        llmService.updatePreferenceMode(mode)
    }

    suspend fun runModelBenchmark(modelId: String): com.example.model.ModelBenchmarkResult {
        return llmService.runStandardizedModelBenchmark(modelId)
    }

    fun selectModel(modelId: String) = llmService.selectModel(modelId)
    fun selectHuggingFaceModel(modelId: String) = llmService.selectModel(modelId)
    fun startBackgroundDownload(modelId: String, onProgress: (Float) -> Unit = {}) = llmService.startBackgroundDownload(modelId, onProgress)
    fun cancelDownload(modelId: String, deletePartial: Boolean = false) = llmService.cancelDownload(modelId, deletePartial)
    fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}) {
        llmService.startBackgroundDownload(modelId, onProgress)
    }
    fun deleteModel(modelId: String) {
        llmService.deleteModel(modelId)
    }
    fun refreshHardwareInfo() = llmService.refreshHardwareInfo()
    fun updateLlmInferenceConfig(config: com.example.model.LlmInferenceConfig) = settingsRepo.updateLlmInferenceConfig(config)
    fun setLlmTemperature(temp: Float) = updateLlmInferenceConfig(llmInferenceConfig.value.copy(temperature = temp))
    fun setSystemPromptPreset(preset: String) = updateLlmInferenceConfig(llmInferenceConfig.value.copy(systemPromptFocus = preset))
    fun setLlmCpuThreads(threads: Int) = updateLlmInferenceConfig(llmInferenceConfig.value.copy(threadCount = threads))
    fun dismissNewModelsNotification() = llmService.dismissNewModelsNotification()
    val llmCatalogAutoCheckEnabled: StateFlow<Boolean> = settingsRepo.llmCatalogAutoCheckEnabled
    fun setLlmCatalogAutoCheckEnabled(enabled: Boolean) = settingsRepo.setLlmCatalogAutoCheckEnabled(enabled)

    val llmCatalogCheckInterval: StateFlow<String> = settingsRepo.llmCatalogCheckInterval
    fun setLlmCatalogCheckInterval(interval: String) = settingsRepo.setLlmCatalogCheckInterval(interval)

    fun relinkDownloadedModels(onResult: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = llmService.scanAndLinkExistingModels()
            withContext(Dispatchers.Main) {
                onResult(count)
            }
        }
    }

    fun importCustomModelFromUri(uri: android.net.Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = llmService.importModelFromUri(getApplication(), uri)
            res.onSuccess { msg -> onResult(true, msg) }
                .onFailure { err -> onResult(false, err.message ?: "Import-Fehler") }
        }
    }

    fun rescanPersistedModels(): Int = llmService.scanAndLinkExistingModels()
    fun syncModelCatalogFromRemote(forceCheck: Boolean = true) {
        viewModelScope.launch {
            llmService.syncModelCatalogFromRemote(forceCheck)
        }
    }

    // Two-Stage LLM Pipeline (AP-1: First-Run Triage & Second-Run Tiefenanalyse)
    private val _isSecondRunGenerating = MutableStateFlow(false)
    val isSecondRunGenerating: StateFlow<Boolean> = _isSecondRunGenerating.asStateFlow()

    private val _lastAiAnalysisResult = MutableStateFlow<Map<Long, LlmClassificationResult>>(emptyMap())
    val lastAiAnalysisResult: StateFlow<Map<Long, LlmClassificationResult>> = _lastAiAnalysisResult.asStateFlow()

    fun runSecondRunAnalysis(
        documentId: Long,
        userCorrection: String? = null,
        onComplete: ((LlmClassificationResult) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSecondRunGenerating.value = true
            try {
                val doc = allDocuments.value.find { it.id == documentId }
                    ?: documentDao.getDocumentById(documentId)
                if (doc != null && doc.ocrText.isNotBlank()) {
                    // Team 3 Stammdaten-Verwalter: Alle aktiven globalen Zusatzfelder aus Room DB holen
                    val existingFields = customFieldDao.getAllCustomFieldsList()
                    val whitelistedNames = existingFields.map { it.name }.distinct()

                    val result = llmService.classifyDocumentSecondRun(
                        ocrText = doc.ocrText,
                        whitelistedCustomFields = whitelistedNames,
                        userCorrection = userCorrection,
                        folderStrategy = settingsRepo.folderStrategy.value,
                        config = llmInferenceConfig.value
                    )

                    // Dokument-Metadaten aktualisieren falls präzisiert
                    val updatedDoc = doc.copy(
                        sender = if (result.sender.isNotBlank() && result.sender != "Dokument") result.sender else doc.sender,
                        docType = if (result.docType.isNotBlank() && result.docType != "Dokument") result.docType else doc.docType
                    )
                    documentDao.updateDocument(updatedDoc)

                    // Extrahierte Zusatzfelder in Room DB einpflegen
                    result.customFields.forEach { (fieldName, fieldValue) ->
                        if (fieldName.isNotBlank() && fieldValue.isNotBlank()) {
                            val matchingField = existingFields.find { it.name.equals(fieldName, ignoreCase = true) }
                            val fieldId = matchingField?.id ?: run {
                                val newField = com.example.model.CustomFieldEntity(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = fieldName,
                                    description = "Von Team 2 Tiefenanalyse erfasst",
                                    type = com.example.model.CustomFieldType.TEXT,
                                    scope = com.example.model.CustomFieldScope.GLOBAL
                                )
                                customFieldDao.insertCustomField(newField)
                                newField.id
                            }
                            customFieldDao.insertOrUpdateFieldValue(
                                com.example.model.DocumentCustomFieldValueEntity(
                                    documentId = documentId,
                                    customFieldId = fieldId,
                                    fieldValue = fieldValue
                                )
                            )
                        }
                    }

                    _lastAiAnalysisResult.value = _lastAiAnalysisResult.value + (documentId to result)
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(result)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DocAnizerViewModel", "Fehler in runSecondRunAnalysis: ${e.message}", e)
            } finally {
                _isSecondRunGenerating.value = false
            }
        }
    }

    fun reRunFirstRunTriage(
        documentId: Long,
        onComplete: ((LlmClassificationResult) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSecondRunGenerating.value = true
            try {
                val doc = allDocuments.value.find { it.id == documentId }
                    ?: documentDao.getDocumentById(documentId)
                if (doc != null && doc.ocrText.isNotBlank()) {
                    val result = llmService.classifyDocumentFirstRun(
                        ocrText = doc.ocrText,
                        config = llmInferenceConfig.value
                    )
                    _lastAiAnalysisResult.value = _lastAiAnalysisResult.value + (documentId to result)
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(result)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DocAnizerViewModel", "Fehler in reRunFirstRunTriage: ${e.message}", e)
            } finally {
                _isSecondRunGenerating.value = false
            }
        }
    }

    // First-Run Wizard & Manueller Start
    val isWizardCompleted: StateFlow<Boolean> = settingsRepo.isWizardCompleted
    fun setWizardCompleted(completed: Boolean) = settingsRepo.setWizardCompleted(completed)
    fun resetWizard() = settingsRepo.resetWizard()

    // Ordner-Strategie (Ebene 1): Nach Absender, Lebensbereich, Chronologisch oder Flach
    val folderStrategy: StateFlow<com.example.model.FolderStrategy> = settingsRepo.folderStrategy
    fun setFolderStrategy(strategy: com.example.model.FolderStrategy) = settingsRepo.setFolderStrategy(strategy)

    private val _showManualConfigWizard = MutableStateFlow(false)
    val showManualConfigWizard: StateFlow<Boolean> = _showManualConfigWizard.asStateFlow()

    fun launchSetupWizardManually() {
        _showManualConfigWizard.value = true
    }

    fun dismissManualConfigWizard() {
        _showManualConfigWizard.value = false
    }

    // Virtuelle Erste-Schritte Tour (Simpel & effektiv für jeden neuen Benutzer)
    val isFirstStepsTourCompleted: StateFlow<Boolean> = settingsRepo.isFirstStepsTourCompleted
    private val _showFirstStepsTour = MutableStateFlow(false)
    val showFirstStepsTour: StateFlow<Boolean> = _showFirstStepsTour.asStateFlow()
    private val _tourStep = MutableStateFlow(1)
    val tourStep: StateFlow<Int> = _tourStep.asStateFlow()

    fun setTourStep(step: Int) {
        _tourStep.value = step.coerceIn(1, 11)
    }

    fun triggerSampleScanForTour() {
        if (pendingRuleSuggestion.value == null) {
            val sampleBitmap = com.example.service.ImageProcessingService.createSampleDocumentBitmap(
                title = "Rechnung Büromaterial",
                sender = "Büro Express",
                modeBw = false
            )
            val sampleRule = com.example.model.DocRule(
                id = "tour_rule_sample",
                name = "Büro Express Rechnung",
                targetDocType = "Rechnung",
                detectedSender = "Büro Express",
                targetMainCategoryId = "A02",
                targetSubCategoryId = "B2.01",
                targetFolderPath = "Finanzen/Belege",
                matchKeywords = listOf("büro express", "rechnung", "ust", "beleg"),
                confidenceScore = 0.96f,
                targetCustomFields = mapOf(
                    "Rechnungsnummer" to "INV-2026-9912",
                    "Rechnungsbetrag" to "122,93 €",
                    "Belegdatum" to "12.10.2026"
                ),
                targetIcon = "receipt_long",
                targetLogo = "shop"
            )
            dmsManager.setPendingRuleSuggestion(
                com.example.model.PendingRuleSuggestion(
                    bitmap = sampleBitmap,
                    ocrText = "Büro Express GmbH\nRechnungsnummer: INV-2026-9912\nKundennummer: KD-48201\nBelegdatum: 12.10.2026\nRechnungsbetrag: 122,93 EUR\nUmsatzsteuer (19%): 19,63 EUR\nFälligkeit: 26.10.2026\nZahlungsziel: 14 Tage\nIBAN: DE89 3704 0044 0532 0130 00",
                    suggestedRule = sampleRule,
                    onSingleUseOnly = {
                        dmsManager.clearPendingRuleSuggestion()
                        setTourStep(6)
                    },
                    onSaveAsPermanentRule = { rule ->
                        addDocRule(rule)
                        dmsManager.clearPendingRuleSuggestion()
                        setTourStep(6)
                    },
                    onDismiss = {
                        dmsManager.clearPendingRuleSuggestion()
                        setTourStep(6)
                    }
                )
            )
        }
    }

    fun startFirstStepsTour() {
        _tourStep.value = 1
        _showFirstStepsTour.value = true
    }

    fun dismissFirstStepsTour(markCompleted: Boolean = true) {
        _showFirstStepsTour.value = false
        if (markCompleted) {
            settingsRepo.setFirstStepsTourCompleted(true)
        }
    }

    fun resetFirstStepsTour() {
        settingsRepo.resetFirstStepsTour()
        _tourStep.value = 1
        _showFirstStepsTour.value = true
    }

    // Optionaler PC- & Desktop-Scanner Assistent
    private val _showPcCompanionWizard = MutableStateFlow(false)
    val showPcCompanionWizard: StateFlow<Boolean> = _showPcCompanionWizard.asStateFlow()

    fun launchPcCompanionWizard() {
        _showPcCompanionWizard.value = true
    }

    fun dismissPcCompanionWizard() {
        _showPcCompanionWizard.value = false
    }

    // Delegated to SyncManager (Cloud Sync)
    val syncState: StateFlow<CloudSyncWorkerService.SyncState> = CloudSyncWorkerService.syncState
    val syncPasswordKey: StateFlow<String> = syncManager.syncPasswordKey
    val autoCloudSyncEnabled: StateFlow<Boolean> = syncManager.autoCloudSyncEnabled
    val lastSyncTimestamp: StateFlow<Long> = syncManager.lastSyncTimestamp
    val cloudSyncConfig: StateFlow<com.example.model.CloudSyncConfig> = syncManager.cloudSyncConfig

    fun setSyncPasswordKey(key: String) {
        syncManager.setSyncPasswordKey(key)
        if (key.isNotBlank()) {
            setUnencryptedModeConfirmed(false)
        }
    }
    fun setAutoCloudSyncEnabled(enabled: Boolean) = syncManager.setAutoCloudSyncEnabled(enabled)
    fun updateCloudSyncConfig(config: com.example.model.CloudSyncConfig) = syncManager.updateCloudSyncConfig(config)

    val isUnencryptedModeConfirmed: StateFlow<Boolean> = settingsRepo.isUnencryptedModeConfirmed
    fun setUnencryptedModeConfirmed(confirmed: Boolean) {
        settingsRepo.setUnencryptedModeConfirmed(confirmed)
        vaultSecurityManager.setUnencryptedModeEnabled(confirmed)
    }

    fun openSecuritySettingsTab() {
        setTargetSettingsSubTab(4)
        setTargetNavigationTab("SETTINGS")
    }

    // Passkey & Tresor-Sicherheitsverfahren
    val securityMethod: StateFlow<String> = settingsRepo.securityMethod
    val passkeyName: StateFlow<String> = settingsRepo.passkeyName
    val passkeyCreatedAt: StateFlow<Long> = settingsRepo.passkeyCreatedAt
    fun setSecurityMethod(method: String) = settingsRepo.setSecurityMethod(method)
    fun registerPasskey(name: String) = settingsRepo.registerPasskey(name)

    // Delegated to SyncManager (USB Backup)
    val usbBackupDrives: StateFlow<List<com.example.model.UsbBackupDrive>> = syncManager.usbBackupDrives
    val usbDrives: StateFlow<List<com.example.model.UsbBackupDrive>> get() = usbBackupDrives
    fun addUsbBackupDrive(name: String, location: String) = syncManager.addUsbBackupDrive(name, location)
    fun addUsbDrive(name: String, locationNote: String = "Zuhause") = syncManager.addUsbBackupDrive(name, locationNote)
    fun deleteUsbBackupDrive(id: String) = syncManager.deleteUsbBackupDrive(id)
    fun removeUsbDrive(id: String) = syncManager.deleteUsbBackupDrive(id)
    fun syncUsbDrive(id: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        val count = allDocuments.value.size
        syncManager.syncUsbDrive(id, count)
        _pendingUsbSyncWarning.value = null
        onComplete?.invoke(true, "USB-Stick erfolgreich synchronisiert ($count Dokumente gesichert)")
    }

    suspend fun testCloudSync(target: String, onComplete: (Boolean, String) -> Unit) {
        val config = cloudSyncConfig.value
        if (target == "WEBDAV" || target == "NAS" || config.enableWebDavNas) {
            if (config.webDavUrl.isBlank()) {
                onComplete(false, "WebDAV/NAS-URL darf nicht leer sein.")
                return
            }
            val res = WebDavClient.testConnection(config.webDavUrl, config.webDavUsername, config.webDavPassword)
            res.fold(
                onSuccess = { msg ->
                    connectNas(config.nasProtocol, config.nasShareName)
                    onComplete(true, msg)
                },
                onFailure = { err ->
                    onComplete(false, err.localizedMessage ?: "Verbindung zum WebDAV/NAS fehlgeschlagen.")
                }
            )
        } else if (target == "GOOGLE_DRIVE") {
            if (config.googleDriveAccount.isBlank()) {
                onComplete(false, "Google-Konto-E-Mail darf nicht leer sein.")
            } else {
                connectGoogleDrive(config.googleDriveAccount)
                onComplete(true, "Google Drive Ziel-Konto '${config.googleDriveAccount}' konfiguriert. Vor Upload wird Zero-Knowledge AES-256-GCM Verschlüsselung angewendet.")
            }
        } else {
            onComplete(true, "Lokaler Speicher bereit.")
        }
    }

    // Google Drive & NAS Anbindung
    fun connectGoogleDrive(account: String) = settingsRepo.connectGoogleDrive(account)
    fun connectNas(protocol: String, share: String) = settingsRepo.connectNas(protocol, share)

    // Ausstehender USB-Sync Warnhinweis
    private val _pendingUsbSyncWarning = MutableStateFlow<String?>(null)
    val pendingUsbSyncWarning: StateFlow<String?> = _pendingUsbSyncWarning.asStateFlow()
    fun dismissUsbSyncWarning() { _pendingUsbSyncWarning.value = null }
    fun triggerUsbSyncReminderIfNeeded() {
        if (cloudSyncConfig.value.enableManualUsbExport && cloudSyncConfig.value.usbSyncWarningOnScan && usbBackupDrives.value.isNotEmpty()) {
            _pendingUsbSyncWarning.value = "⚠️ USB-Backup ausstehend: Bitte schließe deine Backup-USB-Sticks per OTG-Adapter an, um die neuen Dokumente zu synchronisieren!"
        }
    }

    fun triggerCloudSync() {
        viewModelScope.launch {
            val docs = allDocuments.value
            CloudSyncWorkerService.performBackgroundSync(
                context = getApplication(),
                documentDao = documentDao,
                allDocuments = docs,
                userPasswordKey = syncPasswordKey.value,
                config = cloudSyncConfig.value
            )
            settingsRepo.setLastSyncTimestamp(System.currentTimeMillis())
        }
    }

    suspend fun testWebDavConnection(url: String, user: String, pass: String): Result<String> {
        return CloudSyncWorkerService.testWebDavConnection(url, user, pass)
    }

    /**
     * 1-Klick-Sicherung & Export: Erzeugt ein verschlüsseltes Archiv (.enc)
     * Packt alle PDF-Dokumente und die Metadaten-Indexdatei zusammen und
     * verschlüsselt das Gesamtarchiv mit AES-256-GCM (PBKDF2 100.000 Iterationen).
     */
    fun exportEncryptedBackup(onComplete: (java.io.File?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val docs = allDocuments.value
                val backupDir = java.io.File(getApplication<Application>().filesDir, "Backups").apply { mkdirs() }
                val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val targetEncFile = java.io.File(backupDir, "MyDocAnizer_Backup_$dateStr.enc")

                val exported = com.example.service.DocumentStorageService.createEncryptedBackupArchive(
                    context = getApplication(),
                    docs = docs,
                    outputEncFile = targetEncFile,
                    passwordKey = syncPasswordKey.value
                )
                onComplete(exported, null)
            } catch (e: Exception) {
                onComplete(null, e.localizedMessage ?: "Fehler beim Exportieren")
            }
        }
    }

    /**
     * Wiederherstellung (Disaster Recovery):
     * Liest eine verschlüsselte .enc Archivdatei (z.B. nach Handyverlust auf neuem Gerät)
     * entschlüsselt diese mit dem Master-Passwort und fügt alle Dokumente und Kategorien
     * wieder in die Room-Datenbank ein.
     */
    fun restoreEncryptedBackup(
        sourceUri: android.net.Uri,
        passwordOverride: String? = null,
        onComplete: (Int, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val password = passwordOverride?.ifBlank { null } ?: syncPasswordKey.value
                val tempEncFile = java.io.File(context.cacheDir, "restore_temp_${UUID.randomUUID()}.enc")
                
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    tempEncFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw Exception("Konnte Backup-Datei nicht lesen")

                val result = com.example.service.DocumentStorageService.restoreEncryptedBackupArchive(
                    context = context,
                    inputEncFile = tempEncFile,
                    passwordKey = password
                )

                tempEncFile.delete()

                result.onSuccess { restoredList ->
                    for (doc in restoredList) {
                        documentDao.insertDocument(doc)
                    }
                    if (passwordOverride != null && passwordOverride.isNotBlank()) {
                        setSyncPasswordKey(passwordOverride)
                    }
                    onComplete(restoredList.size, null)
                }.onFailure { err ->
                    onComplete(0, err.localizedMessage ?: "Fehler bei der Entschlüsselung")
                }
            } catch (e: Exception) {
                onComplete(0, e.localizedMessage ?: "Fehler bei der Wiederherstellung")
            }
        }
    }

    // Delegated to DmsManager (Batch Queue)
    val batchQueue: StateFlow<List<com.example.model.BatchDocumentItem>> = dmsManager.batchQueue
    val isProcessingBatchQueue: StateFlow<Boolean> = dmsManager.isProcessingBatchQueue
    val pendingRuleSuggestion: StateFlow<com.example.model.PendingRuleSuggestion?> = dmsManager.pendingRuleSuggestion
    val isBatchModeActive: StateFlow<Boolean> = dmsManager.isBatchModeActive

    fun setBatchModeActive(active: Boolean) = dmsManager.setBatchModeActive(active)
    fun clearPendingRuleSuggestion() = dmsManager.clearPendingRuleSuggestion()

    fun enqueueBatchDocument(bitmap: Bitmap, onEnqueued: () -> Unit = {}) = dmsManager.enqueueBatchDocument(bitmap, onEnqueued)
    fun processPendingBatchQueue(onComplete: (Int) -> Unit = {}) = dmsManager.processPendingBatchQueue(onComplete)
    fun updateBatchItem(item: com.example.model.BatchDocumentItem) = dmsManager.updateBatchItem(item)
    fun removeBatchItem(itemId: String) = dmsManager.removeBatchItem(itemId)
    fun clearBatchQueue() = dmsManager.clearBatchQueue()
    fun commitBatchItem(item: com.example.model.BatchDocumentItem, onFinished: (DocumentEntity) -> Unit = {}) = 
        dmsManager.commitBatchItem(item, pdfSettings.value, _isColorMode.value, vaultSecurityManager.getActiveDek(), syncPasswordKey.value, onFinished)
    fun commitAllBatchItems(onComplete: (Int) -> Unit = {}) = 
        dmsManager.commitAllBatchItems(pdfSettings.value, _isColorMode.value, vaultSecurityManager.getActiveDek(), syncPasswordKey.value, onComplete)

    // KI-Regelgenerator: Erstellt DocRule-Vorschläge anhand von Benutzer-Eingaben
    suspend fun generateRulesWithAi(userDescription: String): List<com.example.model.DocRule> {
        return llmService.generateRulesFromPrompt(userDescription)
    }

    suspend fun refineRuleSuggestion(currentRule: com.example.model.DocRule, userFeedbackPrompt: String, ocrText: String): com.example.model.DocRule {
        return llmService.refineRuleSuggestion(currentRule, userFeedbackPrompt, ocrText)
    }

    val modelBenchmarks = com.example.service.AppAuditLogger.modelBenchmarks

    // Dark/Light Theme: "SYSTEM", "DARK", "LIGHT"
    val themeMode: StateFlow<String> = settingsRepo.themeMode

    fun setThemeMode(mode: String) {
        settingsRepo.setThemeMode(mode)
    }

    // Color Skin Farbschema
    val colorSkin: StateFlow<String> = settingsRepo.colorSkin

    fun setColorSkin(skin: String) {
        settingsRepo.setColorSkin(skin)
    }

    // Barrierefreiheit & Sehhilfe (Hoher Kontrast & Große Touch-Targets)
    val highContrastMode: StateFlow<Boolean> = settingsRepo.highContrastMode
    fun setHighContrastMode(enabled: Boolean) = settingsRepo.setHighContrastMode(enabled)

    val largeTouchTargets: StateFlow<Boolean> = settingsRepo.largeTouchTargets
    fun setLargeTouchTargets(enabled: Boolean) = settingsRepo.setLargeTouchTargets(enabled)

    // Delegated to DmsManager (Documents)
    val allDocuments: StateFlow<List<DocumentEntity>> = dmsManager.allDocuments

    // Papierkorb / Mülleimer
    val trashDocuments: StateFlow<List<DocumentEntity>> = documentDao.getTrashDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashCount: StateFlow<Int> = documentDao.getTrashCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Verträge & Fristen (Laufzeiten, Kündigungstermine)
    val contractDeadlines: StateFlow<List<DocumentEntity>> = documentDao.getContractDeadlineDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Papierkorb Retention & Kalender-Einstellungen
    val trashRetentionDays: StateFlow<Int> = settingsRepo.trashRetentionDays
    fun setTrashRetentionDays(days: Int) = settingsRepo.setTrashRetentionDays(days)

    val calendarIntegrationMode: StateFlow<String> = settingsRepo.calendarIntegrationMode
    fun setCalendarIntegrationMode(mode: String) = settingsRepo.setCalendarIntegrationMode(mode)

    val defaultReminderDaysBefore: StateFlow<Int> = settingsRepo.defaultReminderDaysBefore
    fun setDefaultReminderDaysBefore(days: Int) = settingsRepo.setDefaultReminderDaysBefore(days)

    // 3-stufige App-Ansicht: Standard, Erweitert, Experte
    val appViewLevel: StateFlow<com.example.model.AppViewLevel> = settingsRepo.appViewLevel
    val isAdvancedOrExpert: StateFlow<Boolean> = settingsRepo.isAdvancedOrExpert
    val isExpertMode: StateFlow<Boolean> = settingsRepo.isExpertMode
    fun setAppViewLevel(level: com.example.model.AppViewLevel) = settingsRepo.setAppViewLevel(level)
    fun setExpertMode(enabled: Boolean) = settingsRepo.setExpertMode(enabled)

    // Automatische Prüfung des Backup-Synchronisations-Status aller definierten Speicherorte
    private val _isSyncHealthWarningDismissed = MutableStateFlow(false)
    val isSyncHealthWarningDismissed: StateFlow<Boolean> = _isSyncHealthWarningDismissed.asStateFlow()
    fun dismissSyncHealthWarning() { _isSyncHealthWarningDismissed.value = true }
    fun resetSyncHealthWarningDismissal() { _isSyncHealthWarningDismissed.value = false }

    val backupSyncHealth: StateFlow<com.example.model.OverallBackupSyncHealth> = kotlinx.coroutines.flow.combine(
        allDocuments,
        cloudSyncConfig,
        usbBackupDrives,
        lastSyncTimestamp
    ) { docs, config, usbs, lastSync ->
        com.example.model.calculateBackupSyncHealth(
            totalDocumentsCount = docs.size,
            config = config,
            usbDrives = usbs,
            lastSyncTimestamp = lastSync
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.model.calculateBackupSyncHealth(
            totalDocumentsCount = 0,
            config = cloudSyncConfig.value,
            usbDrives = usbBackupDrives.value,
            lastSyncTimestamp = lastSyncTimestamp.value
        )
    )

    // Speicherplatz-Ausnutzung / Speicher-Verwaltung (Gesamter App-Speicher & Ordner-Größen)
    val storageUsage: StateFlow<com.example.model.StorageUsageInfo> = allDocuments.map { docs ->
        val context = getApplication<Application>()
        var docBytes = 0L
        var totalPages = 0

        val folderMap = mutableMapOf<String, Pair<String, Long>>() // mainCategory -> (mainCategoryId, bytes)
        val folderDocCount = mutableMapOf<String, Int>()

        docs.forEach { doc ->
            totalPages += doc.pageCount
            val f = File(doc.filePath)
            val size = if (f.exists()) f.length() else 0L
            docBytes += size

            val current = folderMap[doc.mainCategory] ?: Pair(doc.mainCategoryId, 0L)
            folderMap[doc.mainCategory] = Pair(current.first.ifBlank { doc.mainCategoryId }, current.second + size)
            folderDocCount[doc.mainCategory] = (folderDocCount[doc.mainCategory] ?: 0) + 1
        }

        // App-Gesamtspeicher (Dateien + Cache + Datenbanken)
        fun calculateDirSize(dir: File): Long {
            var total = 0L
            dir.listFiles()?.forEach { file ->
                total += if (file.isDirectory) calculateDirSize(file) else file.length()
            }
            return total
        }

        val appDirSize = calculateDirSize(context.filesDir) +
                calculateDirSize(context.cacheDir) +
                (context.getExternalFilesDir(null)?.let { calculateDirSize(it) } ?: 0L)

        fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
                bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
                bytes >= 1024 -> "${bytes / 1024} KB"
                else -> "$bytes B"
            }
        }

        val folderUsageList = folderMap.map { (cat, pair) ->
            com.example.model.FolderStorageUsage(
                mainCategory = cat,
                mainCategoryId = pair.first,
                documentCount = folderDocCount[cat] ?: 0,
                totalBytes = pair.second,
                formattedSize = formatBytes(pair.second)
            )
        }.sortedByDescending { it.totalBytes }

        com.example.model.StorageUsageInfo(
            totalAppBytes = appDirSize.coerceAtLeast(docBytes),
            totalDocumentBytes = docBytes,
            totalDocumentCount = docs.size,
            totalPagesCount = totalPages,
            totalAppFormatted = formatBytes(appDirSize.coerceAtLeast(docBytes)),
            folderUsageList = folderUsageList
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.model.StorageUsageInfo(0L, 0L, 0, 0, "0 KB", emptyList())
    )

    val pdfSettings: StateFlow<PdfSettings> = settingsRepo.pdfSettings
    val scannerSettings: StateFlow<ScannerSettings> = settingsRepo.scannerSettings

    // Diagnose-Logging
    val logLevel = settingsRepo.logLevel
    fun setLogLevel(level: LogLevel) = settingsRepo.setLogLevel(level)

    // Integriertes Test- & Diagnose-System
    private val diagnosticService = DiagnosticService(getApplication(), db)
    val diagnosticSteps = diagnosticService.steps
    val isStressTesting = diagnosticService.isStressTesting
    val lastAnalysisReport = diagnosticService.lastAnalysisReport
    val lastSimulationResult = diagnosticService.lastSimulationResult

    fun runIntegrityCheck() = diagnosticService.runIntegrityCheck()

    fun copyDiagnosticReport(context: android.content.Context) {
        val report = diagnosticService.generateDiagnosticReport()
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("myDocAnizer Diagnostic", report)
        clipboard.setPrimaryClip(clip)
    }

    suspend fun startStressSimulation(profile: UserProfileSimulation): SimulationResult {
        return diagnosticService.startStressSimulation(profile)
    }

    fun copySimulationResult(context: android.content.Context, result: SimulationResult) {
        val report = diagnosticService.formatSimulationReport(result)
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("myDocAnizer Simulation", report)
        clipboard.setPrimaryClip(clip)
    }

    suspend fun runFinanceWorkflowCheck(): String = diagnosticService.runFinanceWorkflowCheck()

    val availableOptimizationTemplates: List<com.example.model.OptimizationTemplate> = com.example.model.PRESET_OPTIMIZATION_TEMPLATES

    val activeOptimizationTemplate: StateFlow<com.example.model.OptimizationTemplate> = pdfSettings.map { settings ->
        com.example.model.PRESET_OPTIMIZATION_TEMPLATES.find { it.id == settings.activeOptimizationTemplateId }
            ?: com.example.model.PRESET_OPTIMIZATION_TEMPLATES.first()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.example.model.PRESET_OPTIMIZATION_TEMPLATES.first()
    )

    fun setOptimizationEnabled(enabled: Boolean) {
        settingsRepo.updatePdfSettings(pdfSettings.value.copy(isOptimizationEnabled = enabled))
    }

    fun setOptimizationTemplate(templateId: String) {
        settingsRepo.updatePdfSettings(pdfSettings.value.copy(activeOptimizationTemplateId = templateId))
    }

    fun updateScannerSettings(settings: ScannerSettings) {
        settingsRepo.updateScannerSettings(settings)
    }

    fun setScanMode(mode: String) {
        updateScannerSettings(scannerSettings.value.copy(scanMode = mode))
    }

    fun setTriggerMode(mode: String) {
        updateScannerSettings(scannerSettings.value.copy(triggerMode = mode))
    }

    // Delegated to DmsManager (DMS & Folders)
    val templates: StateFlow<List<TemplateItem>> = dmsManager.templates
    val docTypes: StateFlow<List<DocTypeItem>> = dmsManager.docTypes
    val importTemplates: StateFlow<List<ImportTemplateItem>> = dmsManager.importTemplates
    val folders: StateFlow<List<com.example.model.FolderCategoryItem>> = dmsManager.folders

    fun addDocType(docType: DocTypeItem) = dmsManager.addDocType(docType)
    fun updateDocType(docType: DocTypeItem) = dmsManager.updateDocType(docType)
    fun deleteDocType(docTypeId: String) = dmsManager.deleteDocType(docTypeId)

    fun addImportTemplate(item: ImportTemplateItem) = dmsManager.addImportTemplate(item)
    fun deleteImportTemplate(templateId: String) = dmsManager.deleteImportTemplate(templateId)

    fun addMainFolder(name: String, prefixId: String = "") = dmsManager.addMainFolder(name, prefixId)
    fun addSubFolder(mainFolderIdOrName: String, name: String, prefixId: String = "") = dmsManager.addSubFolder(mainFolderIdOrName, name, prefixId)
    fun updateFolderIconAndLogo(mainCatNameOrId: String, subCatNameOrId: String? = null, iconName: String, companyLogo: String, customLogoUri: String = "") = dmsManager.updateFolderIconAndLogo(mainCatNameOrId, subCatNameOrId, iconName, companyLogo, customLogoUri)

    fun renameMainFolder(oldName: String, newName: String) = dmsManager.renameMainFolder(oldName, newName)
    fun renameSubFolder(mainCatName: String, oldSubName: String, newSubName: String) = dmsManager.renameSubFolder(mainCatName, oldSubName, newSubName)
    fun deleteMainFolder(mainCatName: String, deleteDocuments: Boolean = false) = dmsManager.deleteMainFolder(mainCatName, deleteDocuments)
    fun deleteSubFolder(mainCatName: String, subCatName: String, deleteDocuments: Boolean = false) = dmsManager.deleteSubFolder(mainCatName, subCatName, deleteDocuments)
    fun saveFolderStructure(mainCat: String, mainPrefix: String, subCat: String, subPrefix: String) = dmsManager.saveFolderStructure(mainCat, mainPrefix, subCat, subPrefix)

    fun updateDocumentIconAndLogo(documentId: Long, customIcon: String, companyLogo: String) = dmsManager.updateDocumentIconAndLogo(documentId, customIcon, companyLogo)

    // Hardware-Taste Auslöser Event (Lautstärketasten)
    private val _hardwareTriggerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val hardwareTriggerEvent: SharedFlow<Unit> = _hardwareTriggerEvent.asSharedFlow()

    fun triggerHardwareCapture() {
        _hardwareTriggerEvent.tryEmit(Unit)
    }

    // Letztes im Bulk-Scan Modus gespeichertes Dokument (für 1.5s Bestätigungs-Overlay)
    private val _lastBulkSavedDoc = MutableStateFlow<DocumentEntity?>(null)
    val lastBulkSavedDoc: StateFlow<DocumentEntity?> = _lastBulkSavedDoc.asStateFlow()

    fun clearLastBulkSavedDoc() {
        _lastBulkSavedDoc.value = null
    }

    // UI-Zustand für den Scan-Vorgang
    private val _selectedTemplate = MutableStateFlow<TemplateItem?>(null)
    val selectedTemplate: StateFlow<TemplateItem?> = _selectedTemplate.asStateFlow()

    // Schiebeschalter für Scan-Modus: true = Farbe, false = S/W
    private val _isColorMode = MutableStateFlow(false)
    val isColorMode: StateFlow<Boolean> = _isColorMode.asStateFlow()

    // Multi-Page Scan: Liste der erfassten Seiten
    private val _capturedPages = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedPages: StateFlow<List<Bitmap>> = _capturedPages.asStateFlow()

    private val _isProcessingScan = MutableStateFlow(false)
    val isProcessingScan: StateFlow<Boolean> = _isProcessingScan.asStateFlow()

    private val _filter = MutableStateFlow(ExplorerFilter())
    val filter: StateFlow<ExplorerFilter> = _filter.asStateFlow()

    private val _isAdvancedSearchVisible = MutableStateFlow(false)
    val isAdvancedSearchVisible: StateFlow<Boolean> = _isAdvancedSearchVisible.asStateFlow()

    val searchQuery: StateFlow<String> = _filter.map { it.query }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val filterSender: StateFlow<String> = _filter.map { it.sender }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val filterDocType: StateFlow<String?> = _filter.map { it.docType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterTag: StateFlow<String?> = _filter.map { it.tag }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterMainCategory: StateFlow<String?> = _filter.map { it.mainCategory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterSubCategory: StateFlow<String?> = _filter.map { it.subCategory }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterDateRange: StateFlow<String> = _filter.map { it.dateRange }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ALL")
    val filterPageFilter: StateFlow<String> = _filter.map { it.pageFilter }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ALL")
    val filterSortBy: StateFlow<String> = _filter.map { it.sortBy }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DATE_DESC")
    val filterCustomFieldId: StateFlow<String?> = _filter.map { it.customFieldId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val filterCustomFieldValue: StateFlow<String?> = _filter.map { it.customFieldValue }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Zähler aktiver Filter für Badge auf dem Filter-Button
    val activeFilterCount: StateFlow<Int> = _filter.map { f ->
        var count = 0
        if (f.docType != null) count++
        if (f.mainCategory != null) count++
        if (f.subCategory != null) count++
        if (f.dateRange != "ALL") count++
        if (f.pageFilter != "ALL") count++
        if (f.tag != null) count++
        if (f.sender.isNotBlank()) count++
        if (f.customFieldId != null) count++
        count
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Gefilterte Dokumente für DocAnizer
    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        allDocuments,
        allDocumentCustomFieldValues,
        _filter
    ) { docs, customVals, f ->
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()

        docs.filter { doc ->
            // 1. Echte Volltextsuche: Mehrwort-Suche über Titel, OCR-Volltext, Dateiname, Tags, Ordner, Doc-Typ & Absender
            val qTrimmed = f.query.trim()
            val matchesQuery = if (qTrimmed.isBlank()) {
                true
            } else {
                val searchTokens = qTrimmed.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
                val searchableHaystack = buildString {
                    append(doc.title)
                    append(" ")
                    append(doc.ocrText)
                    append(" ")
                    append(doc.fileName)
                    append(" ")
                    append(doc.tags)
                    append(" ")
                    append(doc.docType)
                    append(" ")
                    append(doc.mainCategory)
                    append(" ")
                    append(doc.subCategory)
                    append(" ")
                    append(doc.sender)
                }.lowercase()
                searchTokens.all { token -> searchableHaystack.contains(token) }
            }

            // 2. Absender (optional)
            val matchesSender = f.sender.isBlank() || doc.sender.contains(f.sender, ignoreCase = true)

            // 3. Doc-Type (z.B. "laufender Vertrag")
            val matchesDocType = when {
                f.docType == null -> true
                f.docType == "__NONE__" -> doc.docType.isBlank()
                else -> doc.docType.equals(f.docType, ignoreCase = true) || doc.docType.contains(f.docType, ignoreCase = true)
            }

            // 4. Tags
            val matchesTag = f.tag == null || doc.tags.contains(f.tag, ignoreCase = true)

            // 5. Ordner Ebene 1 (Hauptkategorie)
            val matchesMainCat = f.mainCategory == null || doc.mainCategory.equals(f.mainCategory, ignoreCase = true)

            // 6. Ordner Ebene 2 (Unterordner)
            val matchesSubCat = f.subCategory == null || doc.subCategory.equals(f.subCategory, ignoreCase = true)

            // 7. Zeitraum
            val matchesDate = when (f.dateRange) {
                "TODAY" -> {
                    cal.timeInMillis = now
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    doc.createdAt >= cal.timeInMillis
                }
                "WEEK" -> {
                    doc.createdAt >= (now - 7L * 24 * 60 * 60 * 1000)
                }
                "MONTH" -> {
                    doc.createdAt >= (now - 30L * 24 * 60 * 60 * 1000)
                }
                "YEAR" -> {
                    doc.createdAt >= (now - 365L * 24 * 60 * 60 * 1000)
                }
                else -> true
            }

            // 8. Seitenzahl (Einzelseiter vs Mehrfachseiten)
            val matchesPageCount = when (f.pageFilter) {
                "SINGLE" -> doc.pageCount == 1
                "MULTI" -> doc.pageCount >= 2
                "3_PLUS" -> doc.pageCount >= 3
                "5_PLUS" -> doc.pageCount >= 5
                else -> true
            }

            // 9. Zusatzfelder Filter
            val matchesCustomField = when {
                f.customFieldId == null -> true
                else -> {
                    val matchingVals = customVals.filter { it.documentId == doc.id && it.customFieldId == f.customFieldId }
                    if (f.customFieldValue.isNullOrBlank()) {
                        matchingVals.isNotEmpty() && matchingVals.any { it.fieldValue.isNotBlank() }
                    } else {
                        matchingVals.any { it.fieldValue.contains(f.customFieldValue, ignoreCase = true) }
                    }
                }
            }

            matchesQuery && matchesSender && matchesDocType && matchesTag && matchesMainCat && matchesSubCat && matchesDate && matchesPageCount && matchesCustomField
        }.let { list ->
            when (f.sortBy) {
                "DATE_ASC" -> list.sortedBy { it.createdAt }
                "TITLE_ASC" -> list.sortedBy { it.title.lowercase() }
                "TITLE_DESC" -> list.sortedByDescending { it.title.lowercase() }
                "PAGES_DESC" -> list.sortedByDescending { it.pageCount }
                else -> list.sortedByDescending { it.createdAt }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Preset Zusatzfelder (Custom Fields) initialisieren falls DB leer ist
        viewModelScope.launch(Dispatchers.IO) {
            val existingFields = customFieldDao.getAllCustomFieldsList()
            if (existingFields.isEmpty()) {
                val presets = listOf(
                    com.example.model.CustomFieldEntity(
                        id = "cf_monthly_amount",
                        name = "Monatliche Kosten",
                        description = "Regelmäßige Fixkosten / Abo-Betrag",
                        type = com.example.model.CustomFieldType.AMOUNT,
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_contract_partner",
                        name = "Vertragspartner / Firma",
                        description = "Absender oder Anbieter",
                        type = com.example.model.CustomFieldType.TEXT,
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_customer_number",
                        name = "Kundennummer / Vertrags-ID",
                        description = "Eindeutiges Aktenzeichen",
                        type = com.example.model.CustomFieldType.TEXT,
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_status",
                        name = "Bearbeitungsstatus",
                        description = "Status im Tresor",
                        type = com.example.model.CustomFieldType.SELECTION,
                        options = "Offen,Bezahlt,Gekündigt,Prüfen,In Bearbeitung",
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_cancellation_notice",
                        name = "Kündigungsfrist",
                        description = "Datum der Kündigungsfrist",
                        type = com.example.model.CustomFieldType.DATE,
                        scope = com.example.model.CustomFieldScope.FOLDER_SPECIFIC,
                        targetMainCategory = "Verträge"
                    ),
                    com.example.model.CustomFieldEntity(
                        id = "cf_tax_relevant",
                        name = "Steuerrelevant",
                        description = "Für Steuererklärung vormerken",
                        type = com.example.model.CustomFieldType.BOOLEAN,
                        scope = com.example.model.CustomFieldScope.GLOBAL
                    )
                )
                presets.forEach { customFieldDao.insertCustomField(it) }
            } else {
                // Sicherstellen, dass nur eindeutige Feldbezeichnungen in der DB verbleiben
                val seenNames = mutableSetOf<String>()
                existingFields.forEach { f ->
                    val key = f.name.trim().lowercase()
                    if (seenNames.contains(key)) {
                        customFieldDao.deleteCustomFieldById(f.id)
                    } else {
                        seenNames.add(key)
                    }
                }
            }
        }

        viewModelScope.launch {
            templates.collect { list ->
                if (_selectedTemplate.value == null) {
                    list.firstOrNull()?.let { first ->
                        _selectedTemplate.value = first
                        _isColorMode.value = (first.defaultColorMode == "COLOR")
                    }
                }
            }
        }

        // Sofortige Bereinigung sämtlicher Dummy-Dokumente und Test-PDFs aus der Datenbank
        viewModelScope.launch(Dispatchers.IO) {
            val existing = documentDao.getAllDocuments().first()
            val dummySendersOrFiles = setOf(
                "Hausverwaltung Schmidt & Partner",
                "Vodafone Deutschland",
                "Stadtwerke München",
                "HUK-COBURG",
                "Muster Arbeitgeber GmbH",
                "2024-01-15_Schmidt_Mietvertrag.pdf",
                "2024-02-10_Vodafone_Vertrag.pdf",
                "2024-03-01_Stadtwerke_Abrechnung.pdf",
                "2023-11-20_HUK_KfzPolice.pdf",
                "2024-11-28_Gehaltsabrechnung.pdf"
            )
            existing.filter { doc ->
                doc.filePath.isBlank() ||
                doc.sender in dummySendersOrFiles ||
                doc.fileName in dummySendersOrFiles ||
                doc.tags.contains("Kaution") ||
                doc.tags.contains("Vodafone") ||
                doc.tags.contains("Stadtwerke") ||
                doc.tags.contains("Vollkasko")
            }.forEach {
                documentDao.deleteDocument(it)
            }
        }

        // Automatische Papierkorb-Bereinigung gemäß Aufbewahrungsdauer
        viewModelScope.launch(Dispatchers.IO) {
            cleanupExpiredTrash()
        }
    }

    fun selectTemplate(template: TemplateItem) {
        _selectedTemplate.value = template
        _isColorMode.value = (template.defaultColorMode == "COLOR")
    }

    fun setColorMode(isColor: Boolean) {
        _isColorMode.value = isColor
    }

    // Multi-Page Scan Aktionen
    fun addPage(bitmap: Bitmap) {
        _capturedPages.value = _capturedPages.value + bitmap
    }

    fun removePage(index: Int) {
        val current = _capturedPages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _capturedPages.value = current
        }
    }

    fun clearPages() {
        _capturedPages.value = emptyList()
    }

    fun setSearchQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

    fun toggleAdvancedSearch() {
        _isAdvancedSearchVisible.value = !_isAdvancedSearchVisible.value
    }

    fun setFilterSender(sender: String) {
        _filter.value = _filter.value.copy(sender = sender)
    }

    fun setFilterDocType(type: String?) {
        _filter.value = _filter.value.copy(docType = type)
    }

    fun setFilterTag(tag: String?) {
        _filter.value = _filter.value.copy(tag = tag)
    }

    fun setFilterMainCategory(mainCat: String?) {
        _filter.value = _filter.value.copy(mainCategory = mainCat, subCategory = null)
    }

    fun setFilterSubCategory(subCat: String?) {
        _filter.value = _filter.value.copy(subCategory = subCat)
    }

    fun setFilterDateRange(dateRange: String) {
        _filter.value = _filter.value.copy(dateRange = dateRange)
    }

    fun setFilterPageFilter(pageFilter: String) {
        _filter.value = _filter.value.copy(pageFilter = pageFilter)
    }

    fun setFilterCustomField(fieldId: String?, value: String? = null) {
        _filter.value = _filter.value.copy(customFieldId = fieldId, customFieldValue = value)
    }

    fun setSortBy(sortBy: String) {
        _filter.value = _filter.value.copy(sortBy = sortBy)
    }

    fun resetAllFilters() {
        _filter.value = ExplorerFilter()
    }

    fun resetAdvancedFilters() {
        _filter.value = _filter.value.copy(
            sender = "",
            docType = null,
            tag = null,
            mainCategory = null,
            subCategory = null,
            dateRange = "ALL",
            pageFilter = "ALL",
            customFieldId = null,
            customFieldValue = null
        )
    }

    // Template & Folder Management
    fun createFolderAndTemplate(
        mainCategory: String,
        subCategory: String,
        mainCategoryId: String = "",
        subCategoryId: String = "",
        defaultDocType: String = "",
        defaultSender: String = "",
        defaultTags: List<String> = emptyList(),
        defaultColorMode: String = "BW"
    ): TemplateItem {
        val context = getApplication<Application>()
        // 1. Reale Ordnerstruktur physisch auf der Festplatte / Dateisystem erstellen
        val folder = DocumentStorageService.getDocumentDirectory(
            context = context,
            mainCategory = mainCategory,
            subCategory = subCategory,
            mainCategoryId = mainCategoryId,
            subCategoryId = subCategoryId,
            useIdPrefixes = pdfSettings.value.useIdPrefixes,
            folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
            locationType = pdfSettings.value.baseStorageLocation,
            customPath = pdfSettings.value.customStoragePath
        )

        // 2. Template für diesen realen Ordner erstellen
        val template = TemplateItem(
            id = UUID.randomUUID().toString(),
            mainCategory = mainCategory,
            subCategory = subCategory,
            mainCategoryId = mainCategoryId,
            subCategoryId = subCategoryId,
            defaultDocType = defaultDocType,
            defaultSender = defaultSender,
            namingPattern = "YYYY-MM-DD_[Sender]_[Title]",
            defaultTags = defaultTags,
            defaultColorMode = defaultColorMode
        )
        addTemplate(template)
        _selectedTemplate.value = template
        return template
    }

    /**
     * Benennt einen Hauptordner um, aktualisiert alle zugehörigen Dokumente und Vorlagen
     */
    fun renameMainCategory(oldName: String, newName: String) {
        if (oldName == newName || newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val matchingDocs = docs.filter { it.mainCategory == oldName }
            for (doc in matchingDocs) {
                documentDao.updateDocument(doc.copy(mainCategory = newName.trim()))
            }
            val currentTemplates = settingsRepo.templates.value
            currentTemplates.filter { it.mainCategory == oldName }.forEach { tmpl ->
                settingsRepo.updateTemplate(tmpl.copy(mainCategory = newName.trim()))
            }
        }
    }

    /**
     * Benennt einen Unterordner um, aktualisiert alle zugehörigen Dokumente und Vorlagen
     */
    fun renameSubCategory(mainCategory: String, oldSubName: String, newSubName: String) {
        if (oldSubName == newSubName || newSubName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val matchingDocs = docs.filter { it.mainCategory == mainCategory && it.subCategory == oldSubName }
            for (doc in matchingDocs) {
                documentDao.updateDocument(doc.copy(subCategory = newSubName.trim()))
            }
            val currentTemplates = settingsRepo.templates.value
            currentTemplates.filter { it.mainCategory == mainCategory && it.subCategory == oldSubName }.forEach { tmpl ->
                settingsRepo.updateTemplate(tmpl.copy(subCategory = newSubName.trim()))
            }
        }
    }

    /**
     * Löscht einen Ordner oder Unterordner.
     * Falls deleteContainedDocuments = true, werden auch alle darin liegenden Dokumente aus der DB und dem Speicher gelöscht.
     */
    fun deleteFolderStructure(mainCategory: String, subCategory: String? = null, deleteContainedDocuments: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val docsToDelete = if (subCategory != null && subCategory.isNotBlank()) {
                docs.filter { it.mainCategory == mainCategory && it.subCategory == subCategory }
            } else {
                docs.filter { it.mainCategory == mainCategory }
            }

            if (deleteContainedDocuments) {
                for (doc in docsToDelete) {
                    try {
                        val f = File(doc.filePath)
                        if (f.exists()) f.delete()
                    } catch (ignored: Exception) {}
                    documentDao.deleteDocument(doc)
                }
            }

            // Zugehörige Vorlagen/Templates entfernen
            val currentTemplates = settingsRepo.templates.value
            val templatesToDelete = if (subCategory != null && subCategory.isNotBlank()) {
                currentTemplates.filter { it.mainCategory == mainCategory && it.subCategory == subCategory }
            } else {
                currentTemplates.filter { it.mainCategory == mainCategory }
            }
            for (t in templatesToDelete) {
                settingsRepo.deleteTemplate(t.id)
            }
        }
    }

    fun addTemplate(template: TemplateItem) {
        val context = getApplication<Application>()
        // Stellt sicher, dass der reale Ordner für das Template existiert
        DocumentStorageService.getDocumentDirectory(
            context = context,
            mainCategory = template.mainCategory,
            subCategory = template.subCategory,
            mainCategoryId = template.mainCategoryId,
            subCategoryId = template.subCategoryId,
            useIdPrefixes = pdfSettings.value.useIdPrefixes,
            folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
            locationType = pdfSettings.value.baseStorageLocation,
            customPath = pdfSettings.value.customStoragePath
        )
        settingsRepo.addTemplate(template)
        if (_selectedTemplate.value == null) {
            _selectedTemplate.value = template
        }
    }

    fun updateTemplate(template: TemplateItem) {
        val context = getApplication<Application>()
        DocumentStorageService.getDocumentDirectory(
            context = context,
            mainCategory = template.mainCategory,
            subCategory = template.subCategory,
            mainCategoryId = template.mainCategoryId,
            subCategoryId = template.subCategoryId,
            useIdPrefixes = pdfSettings.value.useIdPrefixes,
            folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
            locationType = pdfSettings.value.baseStorageLocation,
            customPath = pdfSettings.value.customStoragePath
        )
        settingsRepo.updateTemplate(template)
        if (_selectedTemplate.value?.id == template.id) {
            _selectedTemplate.value = template
        }
    }

    fun deleteTemplate(templateId: String) {
        settingsRepo.deleteTemplate(templateId)
        if (_selectedTemplate.value?.id == templateId) {
            _selectedTemplate.value = templates.value.firstOrNull { it.id != templateId }
        }
    }

    fun updatePdfSettings(settings: PdfSettings) {
        settingsRepo.updatePdfSettings(settings)
    }

    /**
     * Schließt den Multi-Seiten-Scan ab und erstellt das gemeinsame PDF
     */
    fun finishAndSaveMultiPageScan(
        onComplete: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessingScan.value = true
            var processedPages: List<Bitmap> = emptyList()
            val rawPages = _capturedPages.value
            try {
                val context = getApplication<Application>()
                val template = _selectedTemplate.value 
                    ?: settingsRepo.templates.value.firstOrNull()
                    ?: TemplateItem(
                        id = "default_general",
                        mainCategory = "Allgemein",
                        subCategory = "Dokumente",
                        mainCategoryId = "A01",
                        subCategoryId = "B1.01",
                        defaultDocType = "",
                        defaultSender = "",
                        namingPattern = "YYYY-MM-DD_[Sender]_[Title]",
                        defaultTags = emptyList(),
                        defaultColorMode = "BW"
                    )
                val sender = template.defaultSender.ifBlank { "Unbekannt" }
                val title = template.subCategory.ifBlank { "Dokument" }

                if (rawPages.isEmpty()) {
                    return@launch
                }

                val preferredMode = pdfSettings.value.defaultColorMode
                var anyPageHasColor = false

                processedPages = rawPages.map { bmp ->
                    val (procBmp, isPageColor) = ImageProcessingService.processDocumentAdaptive(bmp, preferredMode)
                    if (isPageColor) anyPageHasColor = true
                    procBmp
                }
                val isColor = anyPageHasColor

                // 2. OCR auf erster Seite (falls vorhanden)
                val ocrText = processedPages.firstOrNull()?.let { firstBmp ->
                    OcrService.recognizeText(firstBmp, sender, title)
                } ?: ""

                // 3. Ordnerstruktur mit optionalem ID-Präfix und gewählter Speicher-Basis
                val targetDir = DocumentStorageService.getDocumentDirectory(
                    context = context,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = template.mainCategoryId,
                    subCategoryId = template.subCategoryId,
                    useIdPrefixes = pdfSettings.value.useIdPrefixes,
                    folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                    locationType = pdfSettings.value.baseStorageLocation,
                    customPath = pdfSettings.value.customStoragePath
                )

                // 4. Dateinamen erstellen (Anzeige-Name & Interner Name)
                val displayName = DocumentStorageService.formatFileName(sender, title)
                val isEncrypted = true // Standardmäßig verschlüsselt
                val internalName = DocumentStorageService.generateInternalFileName(isEncrypted = isEncrypted)
                
                // 5. Mehrseitiges PDF erstellen
                val tagsStr = template.defaultTags.joinToString(", ")
                val encryptionKey = vaultSecurityManager.getActiveDek()
                
                val finalFile = DocumentStorageService.generatePdf(
                    context = context,
                    pageBitmaps = processedPages,
                    ocrText = ocrText,
                    metadataTags = tagsStr,
                    docType = template.defaultDocType,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    targetDir = targetDir,
                    internalName = internalName,
                    isEncrypted = isEncrypted,
                    encryptionKey = encryptionKey,
                    passwordKey = syncPasswordKey.value,
                    settings = pdfSettings.value
                )

                // Fristen-Erkennung ausführen
                val (detectedCancellation, detectedContractEnd) = com.example.service.DeadlineDetectionService.extractPrimaryDeadlines(ocrText)

                // 6. In Room-Datenbank speichern
                val entity = DocumentEntity(
                    title = title,
                    sender = sender,
                    fileName = displayName,
                    filePath = finalFile.absolutePath,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = template.mainCategoryId,
                    subCategoryId = template.subCategoryId,
                    docType = template.defaultDocType,
                    tags = tagsStr,
                    ocrText = ocrText,
                    colorMode = if (isColor) "COLOR" else "BW",
                    pageCount = processedPages.size,
                    fileSizeFormatted = "${(finalFile.length() / 1024).coerceAtLeast(35)} KB",
                    isEncrypted = isEncrypted,
                    isSynced = false,
                    cancellationDeadline = detectedCancellation,
                    contractEndDate = detectedContractEnd
                )

                val newId = documentDao.insertDocument(entity)
                val created = entity.copy(id = newId)

                triggerUsbSyncReminderIfNeeded()
                _capturedPages.value = emptyList()
                withContext(Dispatchers.Main) {
                    onComplete(created)
                }
            } catch (e: Exception) {
                Log.e("DocAnizerViewModel", "Fehler bei finishAndSaveMultiPageScan: ${e.message}", e)
            } finally {
                processedPages.forEach { runCatching { it.recycle() } }
                rawPages.forEach { runCatching { it.recycle() } }
                _isProcessingScan.value = false
            }
        }
    }

    /**
     * Sofortiges Speichern im Bulk / Auto-Scan Modus:
     * Erstellt sofort das PDF in der gewählten Vorlage und meldet Erfolg zurück,
     * damit der Nutzer ohne Zwischenschritte direkt das nächste Dokument scannen kann.
     */
    /**
     * Sofortiges Speichern im Einzel-/Bulk-Scan Modus mit intelligentem Regel-Workflow:
     * 1. OCR ausführen
     * 2. Wenn eine Schlagwort-Regel matcht -> sofortige automatische Zuordnung und Speicherung
     * 3. Wenn keine Regel matcht -> KI-Regelvorschlag anzeigen (Dauerhaft speichern vs. Einmalig anwenden)
     */
    fun saveSinglePageBulk(
        bitmap: Bitmap,
        onComplete: ((DocumentEntity) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessingScan.value = true
            try {
                val context = getApplication<Application>()

                val preferredMode = if (scannerSettings.value.scanMode == "AUTO") {
                    pdfSettings.value.defaultColorMode
                } else {
                    if (_isColorMode.value) "COLOR" else "BW"
                }

                val (processed, isColor) = ImageProcessingService.processDocumentAdaptive(
                    src = bitmap,
                    preferredMode = preferredMode
                )
                if (processed != bitmap) {
                    runCatching { bitmap.recycle() }
                }

                // 1. Echte Offline ML-Kit OCR
                val ocrText = OcrService.recognizeText(processed, "", "")

                // Integrierte Diagnose für Routine 2
                val analysisReport = StringBuilder()
                analysisReport.append("OCR-Länge: ${ocrText.length} Zeichen\n")
                analysisReport.append("Vorschau: ${ocrText.take(100).replace("\n", " ")}...\n")

                // 2. Regelwerk VOR KI abgleichen
                val matchedRule = ruleRepo.matchRule(ocrText)
                
                if (matchedRule != null) {
                    analysisReport.append("REGEL-TREFFER: '${matchedRule.name}'\n")
                    analysisReport.append("Zuweisung: ${matchedRule.targetMainCategoryId} -> ${matchedRule.targetSubCategoryId}")
                    diagnosticService.updateLastAnalysisReport(analysisReport.toString())
                    
                    // Fall A: Regel matcht! Vollautomatische Zuordnung und Speicherung
                    saveProcessedBitmapWithRule(
                        bitmap = processed,
                        ocrText = ocrText,
                        rule = matchedRule,
                        isColor = isColor,
                        onComplete = onComplete
                    )
                } else {
                    analysisReport.append("KEIN REGEL-TREFFER. Starte KI-Klassifizierung...\n")
                    
                    // Fall B: Keine passende Regel -> Lokale On-Device KI erzeugt Regel-Vorschlag
                    val aiClass = llmService.classifyDocumentText(ocrText)
                    
                    analysisReport.append("KI-ERGEBNIS:\n")
                    analysisReport.append("- Sender: ${aiClass.sender}\n")
                    analysisReport.append("- Typ: ${aiClass.docType}\n")
                    analysisReport.append("- Konfidenz: ${(aiClass.confidence * 100).toInt()}%\n")
                    analysisReport.append("- Begründung: ${aiClass.explanation.take(100)}...")
                    
                    diagnosticService.updateLastAnalysisReport(analysisReport.toString())

                    val smartKeywords = if (aiClass.smartKeywords.isNotEmpty()) {
                        aiClass.smartKeywords
                    } else {
                        ocrText.lowercase()
                            .replace(Regex("[^a-zäöüß0-9\\s]"), " ")
                            .split(Regex("\\s+"))
                            .filter { it.length in 5..20 }
                            .distinct()
                            .take(4)
                            .ifEmpty { listOf(aiClass.sender.lowercase()) }
                    }

                    val (suggestedIcon, suggestedLogo) = com.example.ui.components.detectSuggestedLogoAndIcon(aiClass.sender, ocrText, aiClass.mainCategoryId)

                    // Maßnahme 2: Kotlin-Logik für Regel-Benennung (Entlastung des LLMs)
                    val cleanSender = aiClass.sender.trim().takeIf { it.isNotBlank() && it != "null" }
                    val cleanType = aiClass.docType.trim().takeIf { it.isNotBlank() && it != "null" }
                    val ruleName = when {
                        cleanSender != null && cleanType != null -> "$cleanSender $cleanType"
                        cleanSender != null -> "$cleanSender Dokument"
                        cleanType != null -> "$cleanType Ablage"
                        aiClass.title.isNotBlank() -> aiClass.title
                        else -> "Neues Dokument"
                    }

                    val targetPath = aiClass.suggestedFolderPath.ifBlank {
                        val snd = cleanSender ?: "Dokumente"
                        val typ = cleanType ?: "Allgemein"
                        when (settingsRepo.folderStrategy.value) {
                            com.example.model.FolderStrategy.BY_SENDER -> "$snd/$typ"
                            com.example.model.FolderStrategy.BY_CATEGORY -> "${aiClass.mainCategoryId}/$typ"
                            com.example.model.FolderStrategy.CHRONOLOGICAL -> "2026/$typ"
                            com.example.model.FolderStrategy.FLAT -> "Dokumente"
                        }
                    }

                    val evaluatedCustomFields = if (aiClass.customFields.isNotEmpty()) {
                        aiClass.customFields
                    } else {
                        val cf = mutableMapOf<String, String>()
                        val amt = com.example.service.llm.LlmPrompts.extractAmountFromText(ocrText)
                        val invNo = com.example.service.llm.LlmPrompts.extractInvoiceNumber(ocrText)
                        val dt = com.example.service.llm.LlmPrompts.extractDocumentDateFromText(ocrText)
                        if (amt.isNotBlank()) cf["Rechnungsbetrag"] = amt
                        if (invNo.isNotBlank()) cf["Rechnungsnummer"] = invNo
                        if (dt.isNotBlank()) cf["Belegdatum"] = dt
                        cf
                    }

                    val suggestedRule = com.example.model.DocRule(
                        name = ruleName,
                        matchKeywords = smartKeywords,
                        targetMainCategoryId = aiClass.mainCategoryId,
                        targetSubCategoryId = aiClass.subCategoryId,
                        targetDocType = aiClass.docType,
                        detectedSender = aiClass.sender,
                        targetFolderPath = targetPath,
                        targetTags = aiClass.tags,
                        isEnabled = true,
                        isAiGenerated = true,
                        targetIcon = suggestedIcon,
                        targetLogo = suggestedLogo,
                        targetCustomFields = evaluatedCustomFields
                    )

                    _isProcessingScan.value = false

                    // Dialog für Nutzer-Entscheidung anzeigen
                    dmsManager.setPendingRuleSuggestion(com.example.model.PendingRuleSuggestion(
                        bitmap = processed,
                        ocrText = ocrText,
                        suggestedRule = suggestedRule,
                        onSingleUseOnly = { finalRule ->
                            dmsManager.clearPendingRuleSuggestion()
                            saveProcessedBitmapWithRule(
                                bitmap = processed,
                                ocrText = ocrText,
                                rule = finalRule,
                                isColor = isColor,
                                onComplete = onComplete
                            )
                        },
                        onSaveAsPermanentRule = { finalRule ->
                            dmsManager.clearPendingRuleSuggestion()
                            // Regel dauerhaft im Repository speichern
                            addDocRule(finalRule)
                            saveProcessedBitmapWithRule(
                                bitmap = processed,
                                ocrText = ocrText,
                                rule = finalRule,
                                isColor = isColor,
                                onComplete = onComplete
                            )
                        },
                        onDismiss = {
                            dmsManager.clearPendingRuleSuggestion()
                            runCatching { processed.recycle() }
                        }
                    ))
                }
            } catch (e: Exception) {
                Log.e("DocAnizerViewModel", "Fehler in saveSinglePageBulk: ${e.message}", e)
                _isProcessingScan.value = false
            }
        }
    }

    data class BackgroundTriageResult(
        val ocrText: String,
        val matchedRule: com.example.model.DocRule?,
        val suggestedRule: com.example.model.DocRule?,
        val isColor: Boolean
    )

    private var backgroundTriageJob: kotlinx.coroutines.Job? = null
    private val _backgroundTriageResult = MutableStateFlow<BackgroundTriageResult?>(null)
    val backgroundTriageResult: StateFlow<BackgroundTriageResult?> = _backgroundTriageResult.asStateFlow()

    /**
     * Startet sofortige OCR- und Regel-/KI-Analyse im Hintergrund direkt bei Fotoaufnahme,
     * damit der Nutzer während der Zuschnitt- und Optimierungsvorschau keinerlei Wartezeit hat.
     */
    fun startBackgroundScanTriage(bitmap: Bitmap) {
        backgroundTriageJob?.cancel()
        _backgroundTriageResult.value = null
        val snapshotBmp = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        backgroundTriageJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val preferredMode = if (scannerSettings.value.scanMode == "AUTO") {
                    pdfSettings.value.defaultColorMode
                } else {
                    if (_isColorMode.value) "COLOR" else "BW"
                }
                val (processed, isColor) = ImageProcessingService.processDocumentAdaptive(
                    src = snapshotBmp,
                    preferredMode = preferredMode
                )
                val ocrText = OcrService.recognizeText(processed, "", "")
                val matchedRule = ruleRepo.matchRule(ocrText)
                val suggestedRule: com.example.model.DocRule? = if (matchedRule == null) {
                    val aiClass = llmService.classifyDocumentText(ocrText)
                    val smartKeywords = if (aiClass.smartKeywords.isNotEmpty()) {
                        aiClass.smartKeywords
                    } else {
                        ocrText.lowercase()
                            .replace(Regex("[^a-zäöüß0-9\\s]"), " ")
                            .split(Regex("\\s+"))
                            .filter { it.length in 5..20 }
                            .distinct()
                            .take(4)
                            .ifEmpty { listOf(aiClass.sender.lowercase()) }
                    }
                    val (suggestedIcon, suggestedLogo) = com.example.ui.components.detectSuggestedLogoAndIcon(aiClass.sender, ocrText, aiClass.mainCategoryId)
                    val cleanSender = aiClass.sender.trim().takeIf { it.isNotBlank() && it != "null" }
                    val cleanType = aiClass.docType.trim().takeIf { it.isNotBlank() && it != "null" }
                    val ruleName = when {
                        cleanSender != null && cleanType != null -> "$cleanSender $cleanType"
                        cleanSender != null -> "$cleanSender Dokument"
                        cleanType != null -> "$cleanType Ablage"
                        aiClass.title.isNotBlank() -> aiClass.title
                        else -> "Neues Dokument"
                    }
                    val targetPath = aiClass.suggestedFolderPath.ifBlank {
                        val snd = cleanSender ?: "Dokumente"
                        val typ = cleanType ?: "Allgemein"
                        when (settingsRepo.folderStrategy.value) {
                            com.example.model.FolderStrategy.BY_SENDER -> "$snd/$typ"
                            com.example.model.FolderStrategy.BY_CATEGORY -> "${aiClass.mainCategoryId}/$typ"
                            com.example.model.FolderStrategy.CHRONOLOGICAL -> "2026/$typ"
                            com.example.model.FolderStrategy.FLAT -> "Dokumente"
                        }
                    }
                    val evaluatedCustomFields = if (aiClass.customFields.isNotEmpty()) {
                        aiClass.customFields
                    } else {
                        val cf = mutableMapOf<String, String>()
                        val amt = com.example.service.llm.LlmPrompts.extractAmountFromText(ocrText)
                        val invNo = com.example.service.llm.LlmPrompts.extractInvoiceNumber(ocrText)
                        val dt = com.example.service.llm.LlmPrompts.extractDocumentDateFromText(ocrText)
                        if (amt.isNotBlank()) cf["Rechnungsbetrag"] = amt
                        if (invNo.isNotBlank()) cf["Rechnungsnummer"] = invNo
                        if (dt.isNotBlank()) cf["Belegdatum"] = dt
                        cf
                    }
                    com.example.model.DocRule(
                        name = ruleName,
                        matchKeywords = smartKeywords,
                        targetMainCategoryId = aiClass.mainCategoryId,
                        targetSubCategoryId = aiClass.subCategoryId,
                        targetDocType = aiClass.docType,
                        detectedSender = aiClass.sender,
                        targetFolderPath = targetPath,
                        targetTags = aiClass.tags,
                        isEnabled = true,
                        isAiGenerated = true,
                        targetIcon = suggestedIcon,
                        targetLogo = suggestedLogo,
                        targetCustomFields = evaluatedCustomFields
                    )
                } else null

                _backgroundTriageResult.value = BackgroundTriageResult(
                    ocrText = ocrText,
                    matchedRule = matchedRule,
                    suggestedRule = suggestedRule,
                    isColor = isColor
                )
            } catch (e: Exception) {
                Log.e("DocAnizerViewModel", "Hintergrund-Triage Ausnahme: ${e.message}", e)
            }
        }
    }

    /**
     * Finalisiert das Dokument nach Zuschnitt und Optimierung:
     * Greift auf die bereits im Hintergrund berechnete KI-Triage zu (Zero Latency!).
     */
    fun finalizeScanWithOptimizedBitmap(
        finalBitmap: Bitmap,
        onComplete: ((DocumentEntity) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessingScan.value = true
            try {
                backgroundTriageJob?.join()
                val triage = _backgroundTriageResult.value

                val ocrText = triage?.ocrText ?: OcrService.recognizeText(finalBitmap, "", "")
                val isColor = triage?.isColor ?: true
                val matchedRule = triage?.matchedRule ?: ruleRepo.matchRule(ocrText)

                if (matchedRule != null) {
                    saveProcessedBitmapWithRule(
                        bitmap = finalBitmap,
                        ocrText = ocrText,
                        rule = matchedRule,
                        isColor = isColor,
                        onComplete = onComplete
                    )
                } else {
                    val suggestedRule = triage?.suggestedRule ?: run {
                        val aiClass = llmService.classifyDocumentText(ocrText)
                        val cleanSender = aiClass.sender.trim().takeIf { it.isNotBlank() && it != "null" }
                        val cleanType = aiClass.docType.trim().takeIf { it.isNotBlank() && it != "null" }
                        val ruleName = when {
                            cleanSender != null && cleanType != null -> "$cleanSender $cleanType"
                            cleanSender != null -> "$cleanSender Dokument"
                            cleanType != null -> "$cleanType Ablage"
                            else -> "Neues Dokument"
                        }
                        val (suggestedIcon, suggestedLogo) = com.example.ui.components.detectSuggestedLogoAndIcon(aiClass.sender, ocrText, aiClass.mainCategoryId)
                        com.example.model.DocRule(
                            name = ruleName,
                            matchKeywords = listOf(cleanSender?.lowercase() ?: "dokument"),
                            targetMainCategoryId = aiClass.mainCategoryId,
                            targetSubCategoryId = aiClass.subCategoryId,
                            targetDocType = aiClass.docType,
                            detectedSender = aiClass.sender,
                            targetFolderPath = "Dokumente",
                            targetTags = aiClass.tags,
                            isEnabled = true,
                            isAiGenerated = true,
                            targetIcon = suggestedIcon,
                            targetLogo = suggestedLogo,
                            targetCustomFields = emptyMap()
                        )
                    }

                    _isProcessingScan.value = false
                    dmsManager.setPendingRuleSuggestion(com.example.model.PendingRuleSuggestion(
                        bitmap = finalBitmap,
                        ocrText = ocrText,
                        suggestedRule = suggestedRule,
                        onSingleUseOnly = { finalRule ->
                            dmsManager.clearPendingRuleSuggestion()
                            saveProcessedBitmapWithRule(
                                bitmap = finalBitmap,
                                ocrText = ocrText,
                                rule = finalRule,
                                isColor = isColor,
                                onComplete = onComplete
                            )
                        },
                        onSaveAsPermanentRule = { permanentRule ->
                            addDocRule(permanentRule)
                            dmsManager.clearPendingRuleSuggestion()
                            saveProcessedBitmapWithRule(
                                bitmap = finalBitmap,
                                ocrText = ocrText,
                                rule = permanentRule,
                                isColor = isColor,
                                onComplete = onComplete
                            )
                        },
                        onDismiss = {
                            dmsManager.clearPendingRuleSuggestion()
                        }
                    ))
                }
            } catch (e: Exception) {
                Log.e("DocAnizerViewModel", "Fehler bei finalizeScanWithOptimizedBitmap: ${e.message}", e)
            } finally {
                _isProcessingScan.value = false
            }
        }
    }

    private fun saveProcessedBitmapWithRule(
        bitmap: Bitmap,
        ocrText: String,
        rule: com.example.model.DocRule,
        isColor: Boolean,
        onComplete: ((DocumentEntity) -> Unit)? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isProcessingScan.value = true
            try {
                val context = getApplication<Application>()
                val template = templates.value.find {
                    it.mainCategoryId == rule.targetMainCategoryId && it.subCategoryId == rule.targetSubCategoryId
                } ?: templates.value.firstOrNull() ?: createFolderAndTemplate("Allgemein", "Dokumente", "A01", "B1.01")

                val sender = rule.detectedSender.ifBlank { template.defaultSender.ifBlank { "Dokument" } }
                val title = rule.name.ifBlank { template.subCategory.ifBlank { "Dokument" } }

                val targetDir = DocumentStorageService.getDocumentDirectory(
                    context = context,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = rule.targetMainCategoryId.ifBlank { template.mainCategoryId },
                    subCategoryId = rule.targetSubCategoryId.ifBlank { template.subCategoryId },
                    useIdPrefixes = pdfSettings.value.useIdPrefixes,
                    folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                    locationType = pdfSettings.value.baseStorageLocation,
                    customPath = pdfSettings.value.customStoragePath
                )

                val displayName = DocumentStorageService.formatFileName(sender, title)
                val isEncrypted = true
                val internalName = DocumentStorageService.generateInternalFileName(isEncrypted = isEncrypted)
                val tagsStr = if (rule.targetTags.isNotEmpty()) rule.targetTags.joinToString(", ") else template.defaultTags.joinToString(", ")
                val encryptionKey = vaultSecurityManager.getActiveDek()

                val finalFile = DocumentStorageService.generatePdf(
                    context = context,
                    pageBitmaps = listOf(bitmap),
                    ocrText = ocrText,
                    metadataTags = tagsStr,
                    docType = rule.targetDocType.ifBlank { template.defaultDocType },
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    targetDir = targetDir,
                    internalName = internalName,
                    isEncrypted = isEncrypted,
                    encryptionKey = encryptionKey,
                    passwordKey = syncPasswordKey.value,
                    settings = pdfSettings.value
                )

                val (detectedCancellation, detectedContractEnd) = com.example.service.DeadlineDetectionService.extractPrimaryDeadlines(ocrText)

                val (autoIcon, autoLogo) = com.example.ui.components.detectSuggestedLogoAndIcon(sender, ocrText, template.mainCategory)
                val finalIcon = rule.targetIcon.ifBlank { autoIcon }
                val finalLogo = rule.targetLogo.ifBlank { autoLogo }

                val entity = DocumentEntity(
                    title = title,
                    sender = sender,
                    fileName = displayName,
                    filePath = finalFile.absolutePath,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = rule.targetMainCategoryId.ifBlank { template.mainCategoryId },
                    subCategoryId = rule.targetSubCategoryId.ifBlank { template.subCategoryId },
                    docType = rule.targetDocType.ifBlank { template.defaultDocType },
                    tags = tagsStr,
                    ocrText = ocrText,
                    colorMode = if (isColor) "COLOR" else "BW",
                    pageCount = 1,
                    fileSizeFormatted = "${(finalFile.length() / 1024).coerceAtLeast(15)} KB",
                    isEncrypted = isEncrypted,
                    isSynced = false,
                    cancellationDeadline = detectedCancellation,
                    contractEndDate = detectedContractEnd,
                    customIcon = finalIcon,
                    companyLogo = finalLogo
                )

                val newId = documentDao.insertDocument(entity)
                val created = entity.copy(id = newId)

                // Strukturierte Zusatzfelder in Room Datenbank persistieren
                if (rule.targetCustomFields.isNotEmpty()) {
                    val existingFields = customFieldDao.getAllCustomFieldsList()
                    rule.targetCustomFields.forEach { (fieldName, fieldValue) ->
                        if (fieldName.isNotBlank() && fieldValue.isNotBlank()) {
                            val matchingField = existingFields.find { it.name.equals(fieldName, ignoreCase = true) }
                            val fieldId = matchingField?.id ?: run {
                                val newField = com.example.model.CustomFieldEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = fieldName,
                                    description = "Automatisch von lokaler KI erfasst",
                                    type = when {
                                        fieldValue.contains("€") || fieldValue.matches(Regex(".*[0-9]+[.,][0-9]{2}.*")) -> com.example.model.CustomFieldType.AMOUNT
                                        fieldValue.matches(Regex(".*[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{2,4}.*")) -> com.example.model.CustomFieldType.DATE
                                        else -> com.example.model.CustomFieldType.TEXT
                                    },
                                    scope = com.example.model.CustomFieldScope.GLOBAL
                                )
                                customFieldDao.insertCustomField(newField)
                                newField.id
                            }
                            customFieldDao.insertOrUpdateFieldValue(
                                com.example.model.DocumentCustomFieldValueEntity(
                                    documentId = newId,
                                    customFieldId = fieldId,
                                    fieldValue = fieldValue
                                )
                            )
                        }
                    }
                }

                AppAuditLogger.log(
                    category = LogCategory.STORAGE_DMS,
                    tag = "DocumentStorage",
                    message = "Dokument '${created.title}' (${created.sender}) erfolgreich im Tresor abgelegt",
                    details = "ID: $newId | Pfad: ${created.filePath} | Regel: '${rule.name}' | Zusatzfelder: ${rule.targetCustomFields.size}"
                )

                _lastBulkSavedDoc.value = created

                // Automatischer Cloud-/Off-Site-Sync nach jedem Scan (sofern aktiviert und Ziel vorhanden)
                if (autoCloudSyncEnabled.value && (cloudSyncConfig.value.enableGoogleDrive || cloudSyncConfig.value.enableWebDavNas) && cloudSyncConfig.value.syncTrigger == "AUTO_AFTER_SCAN") {
                    triggerCloudSync()
                }

                // Automatisches lokales Spiegeln in das Backup-Verzeichnis
                if (cloudSyncConfig.value.autoMirrorToBackupDir) {
                    launch(Dispatchers.IO) {
                        try {
                            val backupDir = File(context.filesDir, "Backups").apply { mkdirs() }
                            val pw = syncPasswordKey.value
                            if (!pw.isNullOrBlank()) {
                                val encTarget = File(backupDir, "${entity.fileName}.enc")
                                DocumentStorageService.encryptFile(File(entity.filePath), encTarget, pw)
                            } else {
                                // Bei unverschlüsseltem Modus direkt eine lokale Sicherheitskopie anlegen
                                val rawTarget = File(backupDir, entity.fileName)
                                File(entity.filePath).copyTo(rawTarget, overwrite = true)
                            }
                        } catch (e: Exception) {
                            Log.e("DocAnizerViewModel", "Auto-Mirror fehlgeschlagen: ${e.message}")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(created)
                }
            } catch (e: Exception) {
                Log.e("DocAnizerViewModel", "Fehler in saveProcessedBitmapWithRule: ${e.message}", e)
            } finally {
                runCatching { bitmap.recycle() }
                _isProcessingScan.value = false
            }
        }
    }

    fun updateDocumentDocType(documentId: Long, newDocType: String) {
        viewModelScope.launch {
            val doc = documentDao.getDocumentById(documentId)
            if (doc != null) {
                documentDao.updateDocument(doc.copy(docType = newDocType))
            }
        }
    }

    /**
     * Verschiebt ein Dokument in den Mülleimer / Papierkorb (Soft-Delete).
     * Datei bleibt vorerst auf dem Gerät erhalten und kann wiederhergestellt werden.
     */
    fun deleteDocument(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            documentDao.softDeleteDocument(document.id, System.currentTimeMillis())
        }
    }

    /**
     * Stellt ein zuvor gelöschtes Dokument aus dem Papierkorb wieder her.
     */
    fun restoreDocument(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            documentDao.restoreDocument(document.id)
        }
    }

    /**
     * Endgültiges Löschen: Löscht die PDF-Datei unwiderruflich von der Festplatte
     * und entfernt den Datensatz aus der Datenbank.
     */
    fun permanentlyDeleteDocument(document: DocumentEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (document.filePath.isNotBlank()) {
                    val file = File(document.filePath)
                    if (file.exists()) file.delete()
                }
            } catch (_: Exception) {}
            customFieldDao.deleteAllValuesForDocument(document.id)
            documentDao.deleteDocument(document)
        }
    }

    /**
     * Leert den gesamten Papierkorb unwiderruflich.
     */
    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            val trashed = documentDao.getTrashDocumentsList()
            for (doc in trashed) {
                try {
                    if (doc.filePath.isNotBlank()) {
                        val file = File(doc.filePath)
                        if (file.exists()) file.delete()
                    }
                } catch (_: Exception) {}
                customFieldDao.deleteAllValuesForDocument(doc.id)
            }
            documentDao.emptyTrash()
            customFieldDao.deleteOrphanedFieldValues()
        }
    }

    /**
     * Bereinigt Dokumente im Papierkorb, deren Löschdatum älter als trashRetentionDays ist.
     * 0 = Nie automatisch löschen (nur manuell leeren).
     */
    fun cleanupExpiredTrash() {
        val days = trashRetentionDays.value
        if (days <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val threshold = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L)
            val oldTrashDocs = documentDao.getTrashOlderThanList(threshold)
            for (doc in oldTrashDocs) {
                try {
                    if (doc.filePath.isNotBlank()) {
                        val file = File(doc.filePath)
                        if (file.exists()) file.delete()
                    }
                } catch (_: Exception) {}
                customFieldDao.deleteAllValuesForDocument(doc.id)
            }
            documentDao.deleteTrashOlderThan(threshold)
            customFieldDao.deleteOrphanedFieldValues()
        }
    }

    /**
     * Speichert oder aktualisiert Fristen, Vertragsdaten und Kalender-Erinnerungen.
     */
    fun updateContractReminders(
        documentId: Long,
        contractEndDate: Long?,
        cancellationDeadline: Long?,
        reminderDays: Int,
        hasReminder: Boolean,
        eventType: String,
        reminderNotes: String,
        amount: Double?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = documentDao.getDocumentById(documentId) ?: return@launch
            val updated = doc.copy(
                contractEndDate = contractEndDate,
                cancellationDeadline = cancellationDeadline,
                reminderDaysBefore = reminderDays,
                hasCalendarReminder = hasReminder,
                calendarEventType = eventType,
                reminderNotes = reminderNotes,
                amount = amount
            )
            documentDao.updateDocument(updated)
        }
    }

    // 100% LOKALER ON-DEVICE KI-DOKUMENTEN-ASSISTENT & APP-STEUERUNG
    val chatbotPureSwipeGestures: StateFlow<Boolean> = settingsRepo.chatbotPureSwipeGestures

    fun setChatbotPureSwipeGestures(enabled: Boolean) {
        settingsRepo.setChatbotPureSwipeGestures(enabled)
    }
    
    val enableAiAgentFab: StateFlow<Boolean> = settingsRepo.enableAiAgentFab
    fun setEnableAiAgentFab(enabled: Boolean) = settingsRepo.setEnableAiAgentFab(enabled)

    val enableAiAgentAnimation: StateFlow<Boolean> = settingsRepo.enableAiAgentAnimation
    fun setEnableAiAgentAnimation(enabled: Boolean) = settingsRepo.setEnableAiAgentAnimation(enabled)

    // Reiner Gesten-Modus (Immersive Fullscreen: Bottom-Bar & symmetrische FABs ausblenden)
    val isGestureModeEnabled: StateFlow<Boolean> = settingsRepo.isGestureModeEnabled

    fun setGestureModeEnabled(enabled: Boolean) {
        settingsRepo.setGestureModeEnabled(enabled)
    }

    private val _isAiChatDrawerOpen = MutableStateFlow(false)
    val isAiChatDrawerOpen: StateFlow<Boolean> = _isAiChatDrawerOpen.asStateFlow()

    fun openAiChatDrawer() {
        _isAiChatDrawerOpen.value = true
    }

    fun closeAiChatDrawer() {
        _isAiChatDrawerOpen.value = false
    }

    fun toggleAiChatDrawer() {
        _isAiChatDrawerOpen.value = !_isAiChatDrawerOpen.value
    }

    // CHAT-SESSION MANAGEMENT (Standard: Temporärer Chat)
    val savedChatSessions: StateFlow<List<SavedChatSession>> = settingsRepo.savedChatSessions

    private val _currentChatSessionId = MutableStateFlow<String?>(null)
    val currentChatSessionId: StateFlow<String?> = _currentChatSessionId.asStateFlow()

    private val _currentChatTitle = MutableStateFlow<String>("Temporärer Chat")
    val currentChatTitle: StateFlow<String> = _currentChatTitle.asStateFlow()

    // RÜCKGÄNGIG-MACHEN VON LLM-AKTIONEN
    private val _lastUndoAction = MutableStateFlow<LlmUndoAction?>(null)
    val lastUndoAction: StateFlow<LlmUndoAction?> = _lastUndoAction.asStateFlow()

    private val defaultInitialMessage = ChatMessage(
        text = "Hallo! Ich bin dein lokaler myDocAnizer Assistent. 🤖\n\nIch laufe zu 100% offline direkt auf deinem Smartphone und kenne mich mit deinen Dokumenten, deinen Verträgen und allen Funktionen der App aus.\n\nDu kannst mich alles zu deinen Akten fragen, Musterschreiben erstellen lassen oder App-Funktionen steuern (z.B. „Öffne den Scanner“ oder „Neuen Ordner erstellen“).\n\n💡 Wenn du wissen möchtest, wie und bei was ich dir genau helfen kann, tippe einfach **„Hilfe“**!",
        isUser = false
    )

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(listOf(defaultInitialMessage))
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatGenerating = MutableStateFlow(false)
    val isChatGenerating: StateFlow<Boolean> = _isChatGenerating.asStateFlow()

    private val _selectedChatDocument = MutableStateFlow<DocumentEntity?>(null)
    val selectedChatDocument: StateFlow<DocumentEntity?> = _selectedChatDocument.asStateFlow()

    fun selectChatDocument(doc: DocumentEntity?) {
        _selectedChatDocument.value = doc
    }

    val isLocalLlmActive: Boolean
        get() = true

    fun startNewTemporaryChat() {
        _currentChatSessionId.value = null
        _currentChatTitle.value = "Temporärer Chat"
        _selectedChatDocument.value = null
        _lastUndoAction.value = null
        _chatMessages.value = listOf(defaultInitialMessage)
    }

    fun saveCurrentChat(customTitle: String? = null) {
        val messages = _chatMessages.value
        val defaultTitle = messages.firstOrNull { it.isUser }?.text?.take(28)
            ?: ("Chat vom " + java.text.SimpleDateFormat("dd.MM. HH:mm", java.util.Locale.GERMANY).format(java.util.Date()))
        val title = customTitle?.trim()?.takeIf { it.isNotBlank() } ?: defaultTitle
        val existingId = _currentChatSessionId.value
        val sessionId = existingId ?: UUID.randomUUID().toString()

        val session = SavedChatSession(
            id = sessionId,
            title = title,
            messages = messages,
            updatedAt = System.currentTimeMillis()
        )
        _currentChatSessionId.value = sessionId
        _currentChatTitle.value = title
        settingsRepo.addOrUpdateChatSession(session)
    }

    fun loadSavedChatSession(session: SavedChatSession) {
        _currentChatSessionId.value = session.id
        _currentChatTitle.value = session.title
        _selectedChatDocument.value = null
        _lastUndoAction.value = null
        _chatMessages.value = session.messages.ifEmpty {
            listOf(
                ChatMessage(
                    text = "Hallo! Ich bin dein lokaler myDocAnizer Assistent. 🤖\n\nDieser Chatverlauf ist dauerhaft gespeichert.",
                    isUser = false
                )
            )
        }
    }

    fun deleteSavedChatSession(sessionId: String) {
        settingsRepo.deleteChatSession(sessionId)
        if (_currentChatSessionId.value == sessionId) {
            startNewTemporaryChat()
        }
    }

    fun renameSavedChatSession(sessionId: String, newTitle: String) {
        settingsRepo.renameChatSession(sessionId, newTitle)
        if (_currentChatSessionId.value == sessionId) {
            _currentChatTitle.value = newTitle.trim()
        }
    }

    private fun autoSaveIfPersistent() {
        val sessionId = _currentChatSessionId.value ?: return
        val title = _currentChatTitle.value
        settingsRepo.addOrUpdateChatSession(
            SavedChatSession(
                id = sessionId,
                title = title,
                messages = _chatMessages.value,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun getLastUserPrompt(): String? {
        return _chatMessages.value.lastOrNull { it.isUser }?.text
    }

    fun undoLastLlmAction() {
        val action = _lastUndoAction.value ?: return
        viewModelScope.launch {
            when (action) {
                is LlmUndoAction.FolderCreated -> {
                    settingsRepo.deleteTemplate(action.templateId)
                    _lastUndoAction.value = null
                    val undoMsg = ChatMessage(
                        text = "↩️ **Aktion rückgängig gemacht:** Der Ordner **„${action.folderName}“** wurde wieder aus dem Dokumenten-Tresor entfernt.",
                        isUser = false
                    )
                    _chatMessages.value = _chatMessages.value + undoMsg
                    autoSaveIfPersistent()
                }
                is LlmUndoAction.DocumentStatusUpdated -> {
                    if (action.previousStatus != null) {
                        customFieldDao.insertOrUpdateFieldValue(
                            com.example.model.DocumentCustomFieldValueEntity(
                                documentId = action.docId,
                                customFieldId = "cf_status",
                                fieldValue = action.previousStatus
                            )
                        )
                    } else {
                        customFieldDao.deleteFieldValue(action.docId, "cf_status")
                    }
                    _lastUndoAction.value = null
                    val undoMsg = ChatMessage(
                        text = "↩️ **Aktion rückgängig gemacht:** Der Bearbeitungsstatus für **„${action.docTitle}“** wurde auf **„${action.previousStatus ?: "Unbearbeitet"}“** zurückgesetzt.",
                        isUser = false
                    )
                    _chatMessages.value = _chatMessages.value + undoMsg
                    autoSaveIfPersistent()
                }
                is LlmUndoAction.FilterApplied -> {
                    _filter.value = action.previousFilter
                    _lastUndoAction.value = null
                    val undoMsg = ChatMessage(
                        text = "↩️ **Aktion rückgängig gemacht:** Der vorherige Filterzustand im Dokumenten-Tresor wurde wiederhergestellt.",
                        isUser = false
                    )
                    _chatMessages.value = _chatMessages.value + undoMsg
                    autoSaveIfPersistent()
                }
                is LlmUndoAction.DashboardWidgetCreated -> {
                    db.customDashboardWidgetDao().deleteWidgetById(action.widgetId)
                    settingsRepo.deleteCustomWidget(action.widgetId)
                    _lastUndoAction.value = null
                    val undoMsg = ChatMessage(
                        text = "↩️ **Aktion rückgängig gemacht:** Das Dashboard-Widget **„${action.widgetTitle}“** wurde wieder entfernt.",
                        isUser = false
                    )
                    _chatMessages.value = _chatMessages.value + undoMsg
                    autoSaveIfPersistent()
                }
            }
        }
    }

    val toolActionHandler = object : ToolActionHandler {
        override suspend fun onFilterDocuments(sender: String?, status: String?, category: String?): String {
            return applyFilterFromTool(sender, status, category)
        }

        override suspend fun onUpdateDocumentStatus(docId: String, newStatus: String): String {
            return updateDocumentStatusFromTool(docId, newStatus)
        }

        override suspend fun onOpenAppSection(section: String): String {
            return when (section.lowercase()) {
                "scanner" -> "Ich öffne den Scanner für dich! 📷"
                "dashboard" -> "Hier geht es direkt zu deinem Dashboard! 📊"
                "trash" -> "Ich öffne den Papierkorb für dich. 🗑️"
                "settings" -> "Ich öffne die Einstellungen für dich. ⚙️"
                "deadlines" -> "Ich rufe das Fristen-Radar auf. ⏰"
                "import" -> "Ich öffne den Datei-Import. 📥"
                else -> "Ich navigiere zum Bereich '$section'."
            }
        }

        override suspend fun onCreateFolder(folderName: String, parentCategory: String?): String {
            return createFolderFromTool(folderName, parentCategory)
        }

        override suspend fun onCreateCustomDashboardWidget(
            title: String,
            subtitle: String,
            widgetType: String,
            targetCategory: String,
            targetTag: String,
            numericLimit: Double,
            colorHex: Long,
            targetSubCategory: String,
            targetCustomFieldName: String,
            targetCustomFieldValue: String,
            noteContent: String,
            iconName: String
        ): String {
            return createCustomDashboardWidgetFromTool(
                title, subtitle, widgetType, targetCategory, targetTag, numericLimit, colorHex,
                targetSubCategory, targetCustomFieldName, targetCustomFieldValue, noteContent, iconName
            )
        }
    }

    val llmToolRouter = LlmToolRouter(toolActionHandler)

    fun applyFilterFromTool(sender: String?, status: String?, category: String?): String {
        val previousFilter = _filter.value
        _lastUndoAction.value = LlmUndoAction.FilterApplied(previousFilter)

        var current = _filter.value
        val summaryParts = mutableListOf<String>()

        if (!sender.isNullOrBlank()) {
            current = current.copy(sender = sender)
            summaryParts.add("Absender: **$sender**")
        }
        if (!category.isNullOrBlank()) {
            current = current.copy(mainCategory = category, subCategory = null)
            summaryParts.add("Kategorie: **$category**")
        }
        if (!status.isNullOrBlank()) {
            current = current.copy(customFieldId = "cf_status", customFieldValue = status)
            summaryParts.add("Status: **$status**")
        }

        _filter.value = current

        AppAuditLogger.log(
            category = LogCategory.STORAGE_DMS,
            tag = "LlmToolRouter",
            message = "Dokumenten-Filter via KI-Tool 'filterDocuments' angewendet",
            details = "Absender: $sender | Status: $status | Kategorie: $category"
        )

        return if (summaryParts.isNotEmpty()) {
            "🔍 **Dokumenten-Filter angewendet:**\n${summaryParts.joinToString(" • ")}\n\nIm Dokumenten-Tresor werden jetzt die gefilterten Dokumente angezeigt."
        } else {
            "🔍 Alle Dokumenten-Filter im Tresor wurden zurückgesetzt."
        }
    }

    suspend fun updateDocumentStatusFromTool(docId: String, newStatus: String): String {
        val idLong = docId.trim().toLongOrNull()
        val allDocs = documentDao.getAllDocuments().first()
        val targetDoc = if (idLong != null) {
            allDocs.find { it.id == idLong }
        } else {
            allDocs.find { it.title.contains(docId, ignoreCase = true) || it.fileName.contains(docId, ignoreCase = true) }
        }

        if (targetDoc == null) {
            return "⚠️ Dokument mit ID oder Titel „$docId“ konnte im Dokumenten-Tresor nicht gefunden werden."
        }

        val currentValues = customFieldDao.getCustomFieldValuesForDocumentList(targetDoc.id)
        val existingStatus = currentValues.find { it.customFieldId == "cf_status" }?.fieldValue
        _lastUndoAction.value = LlmUndoAction.DocumentStatusUpdated(
            docId = targetDoc.id,
            docTitle = targetDoc.title,
            previousStatus = existingStatus,
            newStatus = newStatus
        )

        // Sicherstellen, dass das Feld cf_status existiert
        val existingFields = customFieldDao.getAllCustomFieldsList()
        val statusField = existingFields.find { it.id == "cf_status" }
        if (statusField == null) {
            customFieldDao.insertCustomField(
                com.example.model.CustomFieldEntity(
                    id = "cf_status",
                    name = "Bearbeitungsstatus",
                    description = "Status im Tresor",
                    type = com.example.model.CustomFieldType.SELECTION,
                    options = "Offen,Bezahlt,Gekündigt,Prüfen,In Bearbeitung",
                    scope = com.example.model.CustomFieldScope.GLOBAL
                )
            )
        }

        customFieldDao.insertOrUpdateFieldValue(
            com.example.model.DocumentCustomFieldValueEntity(
                documentId = targetDoc.id,
                customFieldId = "cf_status",
                fieldValue = newStatus
            )
        )

        if (newStatus.equals("Gekündigt", ignoreCase = true)) {
            val updatedTags = if (!targetDoc.tags.contains("gekündigt", ignoreCase = true)) {
                if (targetDoc.tags.isBlank()) "#gekündigt" else "${targetDoc.tags}, #gekündigt"
            } else targetDoc.tags
            documentDao.updateDocument(targetDoc.copy(tags = updatedTags))
        }

        AppAuditLogger.log(
            category = LogCategory.STORAGE_DMS,
            tag = "LlmToolRouter",
            message = "Dokument-Status via KI-Tool 'updateDocumentStatus' geändert",
            details = "Dokument: '${targetDoc.title}' (ID: ${targetDoc.id}) -> Neuer Status: '$newStatus'"
        )

        return "✅ Der Bearbeitungsstatus für **„${targetDoc.title}“** (ID: #${targetDoc.id}) wurde auf **„$newStatus“** gesetzt."
    }

    fun createFolderFromTool(folderName: String, parentCategory: String?): String {
        val cleanName = folderName.trim().replace("\"", "").replace("'", "").trim()
        if (cleanName.isBlank()) return "⚠️ Der Ordnername darf nicht leer sein."

        val mainCat = parentCategory?.trim()?.takeIf { it.isNotBlank() } ?: cleanName
        val subCat = if (parentCategory != null && parentCategory.isNotBlank()) cleanName else "Allgemein"

        val template = createFolderAndTemplate(
            mainCategory = mainCat,
            subCategory = subCat
        )

        _lastUndoAction.value = LlmUndoAction.FolderCreated(
            folderName = cleanName,
            parentCategory = mainCat,
            templateId = template.id
        )

        // DMS-Ordner-Fokus sofort auf diesen neu erstellten Ordner setzen
        _selectedTemplate.value = template
        _filter.value = _filter.value.copy(mainCategory = mainCat, subCategory = subCat)

        AppAuditLogger.log(
            category = LogCategory.STORAGE_DMS,
            tag = "LlmToolRouter",
            message = "Ordner via KI-Tool 'createFolder' erstellt",
            details = "Ordner: '$cleanName' (Pfad: $mainCat / $subCat, Template-ID: ${template.id})"
        )

        return "📁 Der Ordner **„$cleanName“** wurde erfolgreich im Dokumenten-Tresor angelegt!\n\nDu findest ihn jetzt direkt in deiner Ordnerstruktur ($mainCat ➔ $subCat)."
    }

    suspend fun createCustomDashboardWidgetFromTool(
        title: String,
        subtitle: String,
        widgetType: String,
        targetCategory: String,
        targetTag: String,
        numericLimit: Double,
        colorHex: Long,
        targetSubCategory: String = "",
        targetCustomFieldName: String = "",
        targetCustomFieldValue: String = "",
        noteContent: String = "",
        iconName: String = "analytics"
    ): String {
        val cleanTitle = title.trim().replace("\"", "").replace("'", "").trim()
        if (cleanTitle.isBlank()) return "⚠️ Der Titel des Widgets darf nicht leer sein."

        val parsedType = try {
            com.example.model.CustomWidgetType.valueOf(widgetType.uppercase())
        } catch (_: Exception) {
            com.example.model.CustomWidgetType.FOLDER_MONITOR
        }

        val widgetId = UUID.randomUUID().toString()
        val widgetEntity = CustomDashboardWidgetEntity(
            id = widgetId,
            title = cleanTitle,
            subtitle = subtitle.trim(),
            widgetType = parsedType,
            targetMainCategory = targetCategory.trim(),
            targetSubCategory = targetSubCategory.trim(),
            targetCustomFieldId = targetCustomFieldName.trim(),
            targetCustomFieldValue = targetCustomFieldValue.trim(),
            numericLimit = numericLimit,
            noteContent = noteContent.trim(),
            colorHex = colorHex,
            iconName = iconName.trim().ifBlank {
                when(parsedType) {
                    com.example.model.CustomWidgetType.FOLDER_MONITOR -> "folder_open"
                    com.example.model.CustomWidgetType.CUSTOM_FIELD_MONITOR -> "label"
                    com.example.model.CustomWidgetType.DEADLINE_MONITOR -> "event"
                    com.example.model.CustomWidgetType.BUDGET_LIMIT -> "savings"
                    com.example.model.CustomWidgetType.QUICK_SHORTCUT -> "bolt"
                    com.example.model.CustomWidgetType.STICKY_NOTE -> "edit_note"
                }
            },
            isEnabled = true,
            position = 0
        )

        db.customDashboardWidgetDao().insertOrUpdateWidget(widgetEntity)

        val customWidget = com.example.model.CustomDashboardWidget(
            id = widgetEntity.id,
            title = cleanTitle,
            subtitle = subtitle.trim(),
            type = when(parsedType) {
                com.example.model.CustomWidgetType.FOLDER_MONITOR -> com.example.model.CustomDashboardWidgetType.FILTER_DOCUMENTS
                com.example.model.CustomWidgetType.CUSTOM_FIELD_MONITOR -> com.example.model.CustomDashboardWidgetType.TAG_FILTER
                com.example.model.CustomWidgetType.DEADLINE_MONITOR -> com.example.model.CustomDashboardWidgetType.TAG_FILTER
                com.example.model.CustomWidgetType.BUDGET_LIMIT -> com.example.model.CustomDashboardWidgetType.BUDGET_GOAL
                com.example.model.CustomWidgetType.QUICK_SHORTCUT -> com.example.model.CustomDashboardWidgetType.FILTER_DOCUMENTS
                com.example.model.CustomWidgetType.STICKY_NOTE -> com.example.model.CustomDashboardWidgetType.NOTE_MEMO
            },
            iconName = widgetEntity.iconName,
            colorSkin = "MYDOCANIZER",
            periodScope = ElementPeriodScope.ALL,
            targetCategory = targetCategory.trim(),
            targetSubCategory = targetSubCategory.trim(),
            targetTag = targetTag.trim(),
            targetCustomFieldId = targetCustomFieldName.trim(),
            targetCustomFieldValue = targetCustomFieldValue.trim(),
            noteContent = noteContent.trim(),
            targetAmount = numericLimit,
            isEnabled = true
        )
        settingsRepo.addCustomWidget(customWidget)
        
        _lastUndoAction.value = LlmUndoAction.DashboardWidgetCreated(
            widgetId = widgetId,
            widgetTitle = cleanTitle
        )

        AppAuditLogger.log(
            category = LogCategory.STORAGE_DMS,
            tag = "LlmToolRouter",
            message = "Smartes Dashboard-Element via KI-Tool 'createCustomDashboardWidget' erstellt",
            details = "Titel: '$cleanTitle' (Typ: $widgetType)"
        )

        return "Smartes Dashboard-Element ‚$cleanTitle‘ wurde erfolgreich erstellt!"
    }

    suspend fun sendChatMessageSync(userText: String, attachedDoc: DocumentEntity? = _selectedChatDocument.value): LlmRouterResult {
        val trimmed = userText.trim()
        val userMsg = ChatMessage(
            text = trimmed,
            isUser = true,
            attachedDocTitle = attachedDoc?.title
        )
        _chatMessages.value = _chatMessages.value + userMsg
        autoSaveIfPersistent()

        val docs = allDocuments.value
        val contextSummary = buildDocumentContextSummary(docs)
        val rawResult = llmService.queryDocumentAssistantWithFollowUps(
            question = trimmed,
            documents = docs,
            attachedDoc = attachedDoc,
            documentContextSummary = contextSummary
        )

        val routerResult = llmToolRouter.routeResponse(rawResult.replyText)
        val assistantMsg = when (routerResult) {
            is LlmRouterResult.ToolCallExecuted -> {
                ChatMessage(
                    text = routerResult.confirmationMessage,
                    isUser = false,
                    actionType = routerResult.actionType ?: rawResult.actionType,
                    actionLabel = routerResult.actionLabel ?: rawResult.actionLabel
                )
            }
            is LlmRouterResult.Text -> {
                ChatMessage(
                    text = routerResult.replyText,
                    isUser = false,
                    actionType = rawResult.actionType,
                    actionLabel = rawResult.actionLabel
                )
            }
        }
        _chatMessages.value = _chatMessages.value + assistantMsg
        autoSaveIfPersistent()
        return routerResult
    }

    private fun buildDocumentContextSummary(docs: List<DocumentEntity>): String {
        if (docs.isEmpty()) return "Keine Dokumente im Tresor."
        
        val latest = docs.take(30)
        val categories = docs.map { it.mainCategory }.distinct()
        val totalAmount = docs.mapNotNull { it.amount }.sum()
        
        return buildString {
            appendLine("Gesamtbestand: ${docs.size} Dokumente")
            appendLine("Hauptkategorien: ${categories.joinToString(", ")}")
            appendLine("Gesamtsumme (Beträge): ${String.format("%.2f €", totalAmount)}")
            appendLine("Zuletzt hinzugefügt (Top 30):")
            latest.forEach { doc ->
                appendLine("- ${doc.title} (${doc.sender}, ${doc.mainCategory}, ${doc.amount ?: 0.0} €)")
            }
        }
    }

    fun sendChatMessage(userText: String, attachedDoc: DocumentEntity? = _selectedChatDocument.value) {
        val trimmed = userText.trim()
        if (trimmed.isBlank()) return
        _isChatGenerating.value = true

        viewModelScope.launch {
            try {
                sendChatMessageSync(trimmed, attachedDoc)
            } catch (e: Exception) {
                Log.e("DocAnizerViewModel", "Chat assistant failed: ${e.message}", e)
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    text = "Lokaler KI-Fehler: ${e.message}",
                    isUser = false
                )
                autoSaveIfPersistent()
            } finally {
                _isChatGenerating.value = false
            }
        }
    }

    fun clearChat() {
        _selectedChatDocument.value = null
        _lastUndoAction.value = null
        _chatMessages.value = listOf(defaultInitialMessage)
        autoSaveIfPersistent()
    }

    /**
     * Importiert ein Bild unabhängig vom Scan-Modus und archiviert es als durchsuchbares PDF
     */
    fun importImageDocument(
        bitmap: Bitmap,
        title: String,
        sender: String,
        template: TemplateItem,
        isColor: Boolean = true,
        onComplete: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val safeTitle = title.ifBlank { "Bild-Import" }
            val safeSender = sender.trim()
            val processed = if (!isColor) {
                ImageProcessingService.convertToOptimizedBw(bitmap)
            } else {
                ImageProcessingService.enhanceColor(bitmap)
            }
            try {
                val ocrText = OcrService.recognizeText(processed, safeSender, safeTitle)

                val targetDir = DocumentStorageService.getDocumentDirectory(
                    context = context,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = template.mainCategoryId,
                    subCategoryId = template.subCategoryId,
                    useIdPrefixes = pdfSettings.value.useIdPrefixes,
                    folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                    locationType = pdfSettings.value.baseStorageLocation,
                    customPath = pdfSettings.value.customStoragePath
                )

                val displayName = DocumentStorageService.formatFileName(safeSender, safeTitle)
                val isEncrypted = true
                val internalName = DocumentStorageService.generateInternalFileName(isEncrypted = isEncrypted)
                val tagsStr = template.defaultTags.joinToString(", ")
                val encryptionKey = vaultSecurityManager.getActiveDek()

                val finalFile = DocumentStorageService.generatePdf(
                    context = context,
                    pageBitmaps = listOf(processed),
                    ocrText = ocrText,
                    metadataTags = tagsStr,
                    docType = template.defaultDocType,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    targetDir = targetDir,
                    internalName = internalName,
                    isEncrypted = isEncrypted,
                    encryptionKey = encryptionKey,
                    passwordKey = syncPasswordKey.value,
                    settings = pdfSettings.value
                )

                val (detectedCancellation, detectedContractEnd) = com.example.service.DeadlineDetectionService.extractPrimaryDeadlines(ocrText)

                val entity = DocumentEntity(
                    title = safeTitle,
                    sender = safeSender,
                    fileName = displayName,
                    filePath = finalFile.absolutePath,
                    mainCategory = template.mainCategory,
                    subCategory = template.subCategory,
                    mainCategoryId = template.mainCategoryId,
                    subCategoryId = template.subCategoryId,
                    docType = template.defaultDocType,
                    tags = tagsStr,
                    ocrText = ocrText,
                    colorMode = if (isColor) "COLOR" else "BW",
                    pageCount = 1,
                    fileSizeFormatted = "${(finalFile.length() / 1024).coerceAtLeast(35)} KB",
                    isEncrypted = isEncrypted,
                    isSynced = false,
                    cancellationDeadline = detectedCancellation,
                    contractEndDate = detectedContractEnd
                )

                val newId = documentDao.insertDocument(entity)
                withContext(Dispatchers.Main) {
                    onComplete(entity.copy(id = newId))
                }
            } finally {
                runCatching { processed.recycle() }
                if (bitmap != processed) {
                    runCatching { bitmap.recycle() }
                }
            }
        }
    }

    /**
     * Importiert eine E-Mail (aus .eml Datei oder manueller Eingabe), erzeugt ein formatiertes PDF
     * und indexiert den gesamten E-Mail-Text in der Volltextsuche
     */
    fun importEmailDocument(
        sender: String,
        recipient: String,
        subject: String,
        body: String,
        dateStr: String,
        template: TemplateItem,
        onComplete: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val safeTitle = subject.ifBlank { "E-Mail Beleg" }
            val safeSender = sender.trim()
            val ocrText = "Betreff: $safeTitle\nVon: $safeSender\nAn: $recipient\nDatum: $dateStr\n\n$body"

            val targetDir = DocumentStorageService.getDocumentDirectory(
                context = context,
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                mainCategoryId = template.mainCategoryId,
                subCategoryId = template.subCategoryId,
                useIdPrefixes = pdfSettings.value.useIdPrefixes,
                folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                locationType = pdfSettings.value.baseStorageLocation,
                customPath = pdfSettings.value.customStoragePath
            )

            val displayName = DocumentStorageService.formatFileName(safeSender, safeTitle)
            val isEncrypted = true
            val internalName = DocumentStorageService.generateInternalFileName(isEncrypted = isEncrypted)
            val encryptionKey = vaultSecurityManager.getActiveDek()
            val tagsList = (template.defaultTags + listOf("#email", "#korrespondenz")).distinct()
            val tagsStr = tagsList.joinToString(", ")

            val finalFile = DocumentStorageService.generateEmailPdf(
                context = context,
                sender = safeSender,
                recipient = recipient,
                dateStr = dateStr,
                subject = safeTitle,
                bodyText = body,
                targetDir = targetDir,
                internalName = internalName,
                isEncrypted = isEncrypted,
                encryptionKey = encryptionKey,
                passwordKey = syncPasswordKey.value,
                settings = pdfSettings.value
            )

            val entity = DocumentEntity(
                title = safeTitle,
                sender = safeSender,
                fileName = displayName,
                filePath = finalFile.absolutePath,
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                mainCategoryId = template.mainCategoryId,
                subCategoryId = template.subCategoryId,
                docType = if (template.defaultDocType.isNotBlank()) template.defaultDocType else "E-Mail / Schriftverkehr",
                tags = tagsStr,
                ocrText = ocrText,
                colorMode = "COLOR",
                pageCount = 1,
                fileSizeFormatted = "${(finalFile.length() / 1024).coerceAtLeast(20)} KB",
                isEncrypted = isEncrypted,
                isSynced = false
            )

            val newId = documentDao.insertDocument(entity)
            withContext(Dispatchers.Main) {
                onComplete(entity.copy(id = newId))
            }
        }
    }

    /**
     * Führt eine On-Demand OCR-Analyse auf einem Bild durch und liefert den erkannten Text
     */
    suspend fun performOcrAnalysis(bitmap: Bitmap, sender: String = "", title: String = ""): String {
        return withContext(Dispatchers.IO) {
            val processed = ImageProcessingService.convertToOptimizedBw(bitmap)
            try {
                OcrService.recognizeText(processed, sender, title)
            } finally {
                runCatching { processed.recycle() }
            }
        }
    }

    /**
     * Universelle Importfunktion:
     * - Unterstützt Zielformat PDF oder Original-Bild (JPEG/PNG)
     * - Bei Bedarf mit OCR-Text
     * - Speichert im DMS-Ordnersystem und Room DB
     */
    fun importDocumentExtended(
        bitmap: Bitmap?,
        pdfBytes: ByteArray? = null,
        title: String,
        sender: String,
        mainCategory: String,
        subCategory: String,
        mainCategoryId: String = "",
        subCategoryId: String = "",
        docType: String = "",
        tags: List<String> = emptyList(),
        targetFormat: String = "PDF", // "PDF" oder "IMAGE"
        ocrTextProvided: String = "",
        runOcr: Boolean = true,
        isColor: Boolean = true,
        onComplete: (DocumentEntity) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val safeTitle = title.trim().ifBlank { "Import_Dokument" }
            val safeSender = sender.trim()
            val targetDir = DocumentStorageService.getDocumentDirectory(
                context = context,
                mainCategory = mainCategory,
                subCategory = subCategory,
                mainCategoryId = mainCategoryId,
                subCategoryId = subCategoryId,
                useIdPrefixes = pdfSettings.value.useIdPrefixes,
                folderPrefixStyle = pdfSettings.value.folderPrefixStyle,
                locationType = pdfSettings.value.baseStorageLocation,
                customPath = pdfSettings.value.customStoragePath
            )

            var finalOcrText = ocrTextProvided
            val extension = if (targetFormat == "IMAGE") "jpg" else "pdf"
            val displayName = DocumentStorageService.formatFileName(safeSender, safeTitle, extension = extension)
            val isEncrypted = true
            val internalName = DocumentStorageService.generateInternalFileName(extension = extension, isEncrypted = isEncrypted)
            val encryptionKey = vaultSecurityManager.getActiveDek()
            var finalFile: File? = null

            var pageCount = 1
            var processedBmpToRecycle: Bitmap? = null

            try {
                if (targetFormat == "IMAGE" && bitmap != null) {
                    // Als Original-Bilddatei im DMS archivieren
                    val processed = if (!isColor) {
                        ImageProcessingService.convertToOptimizedBw(bitmap)
                    } else {
                        bitmap
                    }
                    if (processed != bitmap) processedBmpToRecycle = processed
                    if (finalOcrText.isBlank() && runOcr) {
                        finalOcrText = OcrService.recognizeText(processed, safeSender, safeTitle)
                    }
                    finalFile = DocumentStorageService.saveImageFile(
                        context = context,
                        bitmap = processed,
                        targetDir = targetDir,
                        internalName = internalName,
                        isEncrypted = isEncrypted,
                        encryptionKey = encryptionKey,
                        passwordKey = syncPasswordKey.value
                    )
                    pageCount = 1
                } else if (targetFormat == "PDF" && bitmap != null) {
                    // Bild als durchsuchbares PDF archivieren
                    val processed = if (!isColor) {
                        ImageProcessingService.convertToOptimizedBw(bitmap)
                    } else {
                        ImageProcessingService.enhanceColor(bitmap)
                    }
                    processedBmpToRecycle = processed
                    if (finalOcrText.isBlank() && runOcr) {
                        finalOcrText = OcrService.recognizeText(processed, safeSender, safeTitle)
                    }
                    val tagsStr = tags.joinToString(", ")
                    finalFile = DocumentStorageService.generatePdf(
                        context = context,
                        pageBitmaps = listOf(processed),
                        ocrText = finalOcrText,
                        metadataTags = tagsStr,
                        docType = docType,
                        mainCategory = mainCategory,
                        subCategory = subCategory,
                        targetDir = targetDir,
                        internalName = internalName,
                        isEncrypted = isEncrypted,
                        encryptionKey = encryptionKey,
                        passwordKey = syncPasswordKey.value,
                        settings = pdfSettings.value
                    )
                    pageCount = 1
                } else if (pdfBytes != null) {
                    // Reines PDF direkt wegschreiben via secureSave
                    finalFile = DocumentStorageService.secureSave(
                        context = context,
                        targetDir = targetDir,
                        internalFileName = internalName,
                        isEncrypted = isEncrypted,
                        encryptionKey = encryptionKey,
                        passwordKey = syncPasswordKey.value,
                        inputAction = { tempFile ->
                            tempFile.writeBytes(pdfBytes)
                        }
                    )
                    pageCount = 1
                }

                if (finalFile == null) throw java.io.IOException("Datei konnte nicht gespeichert werden.")

                val tagsStr = tags.joinToString(", ")
                val fileSizeKb = (finalFile.length() / 1024).coerceAtLeast(15)

                val entity = DocumentEntity(
                    title = safeTitle,
                    sender = safeSender,
                    fileName = displayName,
                    filePath = finalFile.absolutePath,
                    mainCategory = mainCategory,
                    subCategory = subCategory,
                    mainCategoryId = mainCategoryId,
                    subCategoryId = subCategoryId,
                    docType = docType,
                    tags = tagsStr,
                    ocrText = finalOcrText,
                    colorMode = if (isColor) "COLOR" else "BW",
                    pageCount = pageCount,
                    fileSizeFormatted = "$fileSizeKb KB",
                    isEncrypted = isEncrypted,
                    isSynced = false
                )

                val newId = documentDao.insertDocument(entity)
                withContext(Dispatchers.Main) {
                    onComplete(entity.copy(id = newId))
                }
            } finally {
                processedBmpToRecycle?.let { runCatching { it.recycle() } }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // DEBUG-DATEN-SEEDER & TESTDATEN-GENERIERUNG (NUR FÜR DEBUG-BUILDS!)
    // ---------------------------------------------------------------------------------------------
    fun seedDebugData(onComplete: (Int) -> Unit = {}) {
        if (!BuildConfig.DEBUG) {
            onComplete(0)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val count = DebugDataSeeder.seedDatabaseAndFiles(
                context = getApplication(),
                database = db,
                settingsRepo = settingsRepo
            )
            withContext(Dispatchers.Main) {
                onComplete(count)
            }
        }
    }
}
