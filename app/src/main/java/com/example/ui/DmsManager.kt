package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.CustomFieldDao
import com.example.data.DocumentDao
import com.example.model.*
import com.example.service.DocRuleRepository
import com.example.service.DocumentStorageService
import com.example.service.LlmService
import com.example.service.OcrService
import com.example.service.SettingsRepository
import com.example.service.VaultSecurityManager
import com.example.service.llm.CustomFieldHarmonizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DmsManager(
    private val application: Application,
    private val documentDao: DocumentDao,
    private val customFieldDao: CustomFieldDao,
    private val ruleRepo: DocRuleRepository,
    private val settingsRepo: SettingsRepository,
    private val llmService: LlmService,
    private val scope: CoroutineScope,
    private val vaultSecurityManager: VaultSecurityManager = VaultSecurityManager.getInstance(application),
    private val onDocSaved: () -> Unit
) {
    // Documents
    val allDocuments: StateFlow<List<DocumentEntity>> = documentDao.getAllDocuments()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stapel-Puffer & Post-Processing (Review-Inbox)
    private val _batchQueue = MutableStateFlow<List<BatchDocumentItem>>(emptyList())
    val batchQueue: StateFlow<List<BatchDocumentItem>> = _batchQueue.asStateFlow()

    private val _isProcessingBatchQueue = MutableStateFlow(false)
    val isProcessingBatchQueue: StateFlow<Boolean> = _isProcessingBatchQueue.asStateFlow()

    // Dialog für neuen Regel-Vorschlag
    private val _pendingRuleSuggestion = MutableStateFlow<PendingRuleSuggestion?>(null)
    val pendingRuleSuggestion: StateFlow<PendingRuleSuggestion?> = _pendingRuleSuggestion.asStateFlow()

    fun setPendingRuleSuggestion(suggestion: PendingRuleSuggestion?) {
        _pendingRuleSuggestion.value = suggestion
    }

    fun clearPendingRuleSuggestion() { _pendingRuleSuggestion.value = null }

    private val _isBatchModeActive = MutableStateFlow(false)
    val isBatchModeActive: StateFlow<Boolean> = _isBatchModeActive.asStateFlow()

    fun setBatchModeActive(active: Boolean) { _isBatchModeActive.value = active }

    fun enqueueBatchDocument(bitmap: Bitmap, onEnqueued: () -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            val tempFile = File(application.cacheDir, "batch_${UUID.randomUUID()}.jpg")
            tempFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            val item = BatchDocumentItem(
                tempImagePaths = listOf(tempFile.absolutePath),
                status = BatchItemStatus.PENDING_PROCESS
            )
            _batchQueue.value = _batchQueue.value + item
            withContext(Dispatchers.Main) { onEnqueued() }
        }
    }

    fun processPendingBatchQueue(onComplete: (Int) -> Unit = {}) {
        scope.launch(Dispatchers.IO) {
            _isProcessingBatchQueue.value = true
            val itemsToProcess = _batchQueue.value.filter {
                it.status == BatchItemStatus.PENDING_PROCESS ||
                it.status == BatchItemStatus.WAITING_OCR
            }

            var processedCount = 0
            for (item in itemsToProcess) {
                updateBatchItem(item.copy(status = BatchItemStatus.WAITING_OCR))

                val bitmap = item.tempImagePaths.firstOrNull()?.let { path ->
                    try { BitmapFactory.decodeFile(path) } catch (_: Throwable) { null }
                }

                if (bitmap != null) {
                    try {
                        val ocrText = OcrService.recognizeText(bitmap, "", "")
                        val matchedRule = ruleRepo.matchRule(ocrText)

                        if (matchedRule != null) {
                            val updated = item.copy(
                                ocrText = ocrText,
                                status = BatchItemStatus.RULE_MATCHED,
                                suggestedTitle = matchedRule.name,
                                suggestedSender = matchedRule.detectedSender,
                                suggestedMainCategoryId = matchedRule.targetMainCategoryId,
                                suggestedSubCategoryId = matchedRule.targetSubCategoryId,
                                suggestedDocType = matchedRule.targetDocType,
                                suggestedTags = matchedRule.targetTags,
                                matchedRuleName = matchedRule.name,
                                aiReasoning = "Exakter Treffer durch Schlagwort-Regel '${matchedRule.name}'"
                            )
                            updateBatchItem(updated)
                        } else {
                            val aiResult = llmService.classifyDocumentText(ocrText)
                            val updated = item.copy(
                                ocrText = ocrText,
                                status = BatchItemStatus.AI_SUGGESTED,
                                suggestedTitle = aiResult.title,
                                suggestedSender = aiResult.sender,
                                suggestedMainCategoryId = aiResult.mainCategoryId,
                                suggestedSubCategoryId = aiResult.subCategoryId,
                                suggestedDocType = aiResult.docType,
                                suggestedTags = aiResult.tags,
                                aiReasoning = aiResult.explanation
                            )
                            updateBatchItem(updated)
                        }
                        processedCount++
                    } catch (t: Throwable) {
                        Log.e("DmsManager", "Fehler bei Stapelverarbeitung von Element ${item.id}: ${t.message}", t)
                        updateBatchItem(item.copy(status = BatchItemStatus.ERROR, aiReasoning = "Verarbeitung fehlgeschlagen: ${t.localizedMessage}"))
                    } finally {
                        bitmap.recycle()
                    }
                } else {
                    updateBatchItem(item.copy(status = BatchItemStatus.ERROR, aiReasoning = "Bilddatei konnte nicht geladen werden"))
                }
            }
            _isProcessingBatchQueue.value = false
            withContext(Dispatchers.Main) { onComplete(processedCount) }
        }
    }

    fun updateBatchItem(item: BatchDocumentItem) {
        _batchQueue.value = _batchQueue.value.map { if (it.id == item.id) item else it }
    }

    fun removeBatchItem(itemId: String) {
        val found = _batchQueue.value.find { it.id == itemId }
        found?.tempImagePaths?.forEach { File(it).delete() }
        _batchQueue.value = _batchQueue.value.filter { it.id != itemId }
    }

    fun clearBatchQueue() {
        _batchQueue.value.forEach { item ->
            item.tempImagePaths.forEach { File(it).delete() }
        }
        _batchQueue.value = emptyList()
    }

    suspend fun commitBatchItemInternal(
        item: BatchDocumentItem,
        pdfSettings: PdfSettings,
        isColorMode: Boolean,
        encryptionKey: ByteArray? = null,
        passwordKey: String? = null
    ): DocumentEntity? {
        val bitmaps = item.tempImagePaths.mapNotNull { path ->
            try { BitmapFactory.decodeFile(path) } catch (_: Throwable) { null }
        }
        if (bitmaps.isEmpty()) return null

        try {
            val template = templates.value.find {
                it.mainCategoryId == item.suggestedMainCategoryId && it.subCategoryId == item.suggestedSubCategoryId
            } ?: templates.value.firstOrNull() ?: TemplateItem(id = UUID.randomUUID().toString(), mainCategory = "Allgemein", subCategory = "Dokumenten", mainCategoryId = "A01", subCategoryId = "B1.01", defaultDocType = "Unbekannt")

            val sender = item.suggestedSender.ifBlank { "Dokument" }
            val title = item.suggestedTitle.ifBlank { template.subCategory }

            val targetDir = DocumentStorageService.getDocumentDirectory(
                context = application,
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                mainCategoryId = item.suggestedMainCategoryId.ifBlank { template.mainCategoryId },
                subCategoryId = item.suggestedSubCategoryId.ifBlank { template.subCategoryId },
                useIdPrefixes = pdfSettings.useIdPrefixes,
                folderPrefixStyle = pdfSettings.folderPrefixStyle,
                locationType = pdfSettings.baseStorageLocation,
                customPath = pdfSettings.customStoragePath
            )

            val displayName = DocumentStorageService.formatFileName(sender, title)
            val isEncrypted = true
            val internalName = DocumentStorageService.generateInternalFileName(isEncrypted = isEncrypted)
            val tagsStr = item.suggestedTags.joinToString(", ")

            val finalFile = DocumentStorageService.generatePdf(
                context = application,
                pageBitmaps = bitmaps,
                ocrText = item.ocrText,
                metadataTags = tagsStr,
                docType = item.suggestedDocType.ifBlank { template.defaultDocType },
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                targetDir = targetDir,
                internalName = internalName,
                isEncrypted = isEncrypted,
                encryptionKey = encryptionKey,
                passwordKey = passwordKey,
                settings = pdfSettings
            )

            val docEntity = DocumentEntity(
                title = title,
                sender = sender,
                fileName = displayName,
                filePath = finalFile.absolutePath,
                mainCategory = template.mainCategory,
                subCategory = template.subCategory,
                mainCategoryId = item.suggestedMainCategoryId.ifBlank { template.mainCategoryId },
                subCategoryId = item.suggestedSubCategoryId.ifBlank { template.subCategoryId },
                docType = item.suggestedDocType.ifBlank { template.defaultDocType },
                tags = tagsStr,
                ocrText = item.ocrText,
                colorMode = if (isColorMode) "COLOR" else "BW",
                pageCount = bitmaps.size,
                fileSizeFormatted = "${finalFile.length() / 1024} KB",
                isEncrypted = isEncrypted
            )
            documentDao.insertDocument(docEntity)
            com.example.service.AppAuditLogger.log(
                category = com.example.service.LogCategory.STORAGE_DMS,
                tag = "DmsManager",
                message = "Dokument '${docEntity.title}' (${docEntity.sender}) erfolgreich im Tresor abgelegt.",
                details = "Pfad: ${docEntity.filePath} | Typ: ${docEntity.docType} | Seiten: ${docEntity.pageCount}"
            )
            onDocSaved()
            removeBatchItem(item.id)
            return docEntity
        } finally {
            bitmaps.forEach { it.recycle() }
        }
    }

    fun commitBatchItem(
        item: BatchDocumentItem,
        pdfSettings: PdfSettings,
        isColorMode: Boolean,
        passwordKey: String? = null,
        onFinished: (DocumentEntity) -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            val docEntity = vaultSecurityManager.withUnlockedKeySuspend { lease ->
                commitBatchItemInternal(item, pdfSettings, isColorMode, lease, passwordKey)
            }
            if (docEntity != null) {
                withContext(Dispatchers.Main) { onFinished(docEntity) }
            } else {
                Log.w("DmsManager", "Batch item not committed: vault is locked or key lease unavailable")
            }
        }
    }

    fun commitAllBatchItems(
        pdfSettings: PdfSettings,
        isColorMode: Boolean,
        passwordKey: String? = null,
        onComplete: (Int) -> Unit = {}
    ) {
        scope.launch(Dispatchers.IO) {
            val list = _batchQueue.value.toList()
            var count = 0
            for (item in list) {
                val doc = vaultSecurityManager.withUnlockedKeySuspend { lease ->
                    commitBatchItemInternal(item, pdfSettings, isColorMode, lease, passwordKey)
                }
                if (doc == null) {
                    if (!vaultSecurityManager.isUnlocked()) {
                        Log.w("DmsManager", "Batch commit stopped: vault locked or key lease unavailable")
                        break
                    }
                    continue
                }
                count++
            }
            withContext(Dispatchers.Main) { onComplete(count) }
        }
    }

    // Doc Types
    val docTypes: StateFlow<List<DocTypeItem>> = settingsRepo.docTypes
    fun addDocType(docType: DocTypeItem) = settingsRepo.addDocType(docType)
    fun updateDocType(docType: DocTypeItem) = settingsRepo.updateDocType(docType)
    fun deleteDocType(docTypeId: String) = settingsRepo.deleteDocType(docTypeId)

    // Templates & Folders
    val templates: StateFlow<List<TemplateItem>> = settingsRepo.templates
    val folders: StateFlow<List<FolderCategoryItem>> = settingsRepo.folders
    val importTemplates: StateFlow<List<ImportTemplateItem>> = settingsRepo.importTemplates

    fun addImportTemplate(item: ImportTemplateItem) = settingsRepo.addImportTemplate(item)
    fun deleteImportTemplate(templateId: String) = settingsRepo.deleteImportTemplate(templateId)

    fun addMainFolder(name: String, prefixId: String = "") = settingsRepo.addMainFolder(name, prefixId)
    fun addSubFolder(mainFolderIdOrName: String, name: String, prefixId: String = "") = settingsRepo.addSubFolder(mainFolderIdOrName, name, prefixId)
    
    fun updateFolderIconAndLogo(
        mainCatNameOrId: String,
        subCatNameOrId: String? = null,
        iconName: String,
        companyLogo: String,
        customLogoUri: String = ""
    ) = settingsRepo.updateFolderIconAndLogo(mainCatNameOrId, subCatNameOrId, iconName, companyLogo, customLogoUri)

    fun renameMainFolder(oldName: String, newName: String) {
        val cleanNewName = newName.trim()
        if (cleanNewName.isBlank() || oldName == cleanNewName) return
        com.example.service.AppAuditLogger.log(
            category = com.example.service.LogCategory.STORAGE_DMS,
            tag = "DmsManager",
            message = "Hauptordner '$oldName' wurde in '$cleanNewName' umbenannt.",
            details = "Alle verknüpften Dokumente werden aktualisiert."
        )
        settingsRepo.renameMainFolder(oldName, cleanNewName)
        scope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            docs.filter { it.mainCategory.equals(oldName, ignoreCase = true) }.forEach { doc ->
                documentDao.updateDocument(doc.copy(mainCategory = cleanNewName))
            }
        }
    }

    fun renameSubFolder(mainCatName: String, oldSubName: String, newSubName: String) {
        val cleanNewSubName = newSubName.trim()
        if (cleanNewSubName.isBlank() || oldSubName == cleanNewSubName) return
        com.example.service.AppAuditLogger.log(
            category = com.example.service.LogCategory.STORAGE_DMS,
            tag = "DmsManager",
            message = "Unterordner '$oldSubName' in '$mainCatName' wurde in '$cleanNewSubName' umbenannt.",
            details = "Betroffene Dokumente werden im Hintergrund aktualisiert."
        )
        settingsRepo.renameSubFolder(mainCatName, oldSubName, cleanNewSubName)
        scope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            docs.filter {
                it.mainCategory.equals(mainCatName, ignoreCase = true) &&
                it.subCategory.equals(oldSubName, ignoreCase = true)
            }.forEach { doc ->
                documentDao.updateDocument(doc.copy(subCategory = cleanNewSubName))
            }
        }
    }

    fun deleteMainFolder(mainCatName: String, deleteDocuments: Boolean = false) {
        com.example.service.AppAuditLogger.log(
            category = com.example.service.LogCategory.STORAGE_DMS,
            tag = "DmsManager",
            message = "Hauptordner '$mainCatName' wird gelöscht (Inkl. Dokumente: $deleteDocuments).",
            details = "Aktion vom Benutzer ausgelöst."
        )
        scope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val docsToDelete = docs.filter { it.mainCategory.equals(mainCatName, ignoreCase = true) }
            if (docsToDelete.isNotEmpty() && deleteDocuments) {
                docsToDelete.forEach { doc ->
                    try {
                        val f = java.io.File(doc.filePath)
                        if (f.exists()) f.delete()
                    } catch (_: Exception) {}
                    documentDao.deleteDocument(doc)
                }
            }
            settingsRepo.deleteMainFolder(mainCatName)
        }
    }

    fun deleteSubFolder(mainCatName: String, subCatName: String, deleteDocuments: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            val docs = documentDao.getAllDocuments().first()
            val docsToDelete = docs.filter {
                it.mainCategory.equals(mainCatName, ignoreCase = true) &&
                it.subCategory.equals(subCatName, ignoreCase = true)
            }
            if (docsToDelete.isNotEmpty() && deleteDocuments) {
                docsToDelete.forEach { doc ->
                    try {
                        val f = java.io.File(doc.filePath)
                        if (f.exists()) f.delete()
                    } catch (_: Exception) {}
                    documentDao.deleteDocument(doc)
                }
            }
            settingsRepo.deleteSubFolder(mainCatName, subCatName)
        }
    }

    fun saveFolderStructure(mainCat: String, mainPrefix: String, subCat: String, subPrefix: String) {
        addMainFolder(mainCat, mainPrefix)
        if (subCat.isNotBlank()) {
            addSubFolder(mainCat, subCat, subPrefix)
        }
    }

    // Custom Fields
    val customFields: StateFlow<List<CustomFieldEntity>> = customFieldDao.getAllCustomFields()
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun addCustomFieldSync(field: CustomFieldEntity): Boolean {
        val existing = customFieldDao.getAllCustomFieldsList()
        if (existing.any { it.name.trim().equals(field.name.trim(), ignoreCase = true) }) {
            return false
        }
        customFieldDao.insertCustomField(field)
        return true
    }

    fun addCustomField(field: CustomFieldEntity) {
        scope.launch(Dispatchers.IO) {
            addCustomFieldSync(field)
        }
    }

    suspend fun updateCustomFieldSync(field: CustomFieldEntity): Boolean {
        val existing = customFieldDao.getAllCustomFieldsList()
        if (existing.any { it.id != field.id && it.name.trim().equals(field.name.trim(), ignoreCase = true) }) {
            return false
        }
        customFieldDao.updateCustomField(field)
        return true
    }

    fun updateCustomField(field: CustomFieldEntity) {
        scope.launch(Dispatchers.IO) {
            updateCustomFieldSync(field)
        }
    }

    fun deleteCustomField(fieldId: String) {
        scope.launch(Dispatchers.IO) {
            customFieldDao.deleteAllValuesForCustomField(fieldId)
            customFieldDao.deleteCustomFieldById(fieldId)
        }
    }

    fun setDocumentCustomFieldValue(documentId: Long, customFieldId: String, value: String) {
        scope.launch(Dispatchers.IO) {
            if (value.isBlank()) {
                customFieldDao.deleteFieldValue(documentId, customFieldId)
            } else {
                customFieldDao.insertOrUpdateFieldValue(
                    DocumentCustomFieldValueEntity(
                        documentId = documentId,
                        customFieldId = customFieldId,
                        fieldValue = value
                    )
                )
            }
        }
    }

    fun updateDocumentIconAndLogo(
        documentId: Long,
        customIcon: String,
        companyLogo: String
    ) {
        scope.launch(Dispatchers.IO) {
            val doc = documentDao.getDocumentById(documentId) ?: return@launch
            documentDao.updateDocument(doc.copy(customIcon = customIcon, companyLogo = companyLogo))
        }
    }

    // Rules mit automatischer Zusatzfeld-Harmonisierung & Registrierung im Tresor
    val docRules: StateFlow<List<DocRule>> = ruleRepo.rules

    fun addDocRule(rule: DocRule) {
        com.example.service.AppAuditLogger.log(
            category = com.example.service.LogCategory.RULE_ENGINE,
            tag = "DmsManager",
            message = "Neue Schlagwort-Regel '${rule.name}' wird erstellt.",
            details = "Ziel-Typ: ${rule.targetDocType} | Schlagwörter: ${rule.matchKeywords.joinToString()}"
        )
        scope.launch(Dispatchers.IO) {
            val existingFields = customFieldDao.getAllCustomFieldsList()
            val harmonization = CustomFieldHarmonizer.harmonizeFields(rule.targetCustomFields, existingFields)
            CustomFieldHarmonizer.ensureFieldsRegisteredInDatabase(harmonization, customFieldDao)
            val finalRule = rule.copy(targetCustomFields = harmonization.harmonizedFields)
            ruleRepo.addRule(finalRule)
        }
    }

    fun addDocRules(rules: List<DocRule>) {
        scope.launch(Dispatchers.IO) {
            val existingFields = customFieldDao.getAllCustomFieldsList()
            val finalRules = rules.map { r ->
                val harmonization = CustomFieldHarmonizer.harmonizeFields(r.targetCustomFields, existingFields)
                CustomFieldHarmonizer.ensureFieldsRegisteredInDatabase(harmonization, customFieldDao)
                r.copy(targetCustomFields = harmonization.harmonizedFields)
            }
            ruleRepo.addRules(finalRules)
        }
    }

    fun updateDocRule(rule: DocRule) {
        scope.launch(Dispatchers.IO) {
            val existingFields = customFieldDao.getAllCustomFieldsList()
            val harmonization = CustomFieldHarmonizer.harmonizeFields(rule.targetCustomFields, existingFields)
            CustomFieldHarmonizer.ensureFieldsRegisteredInDatabase(harmonization, customFieldDao)
            val finalRule = rule.copy(targetCustomFields = harmonization.harmonizedFields)
            ruleRepo.updateRule(finalRule)
        }
    }

    fun deleteDocRule(ruleId: String) = ruleRepo.deleteRule(ruleId)
    fun toggleDocRule(ruleId: String, enabled: Boolean) = ruleRepo.toggleRule(ruleId, enabled)
}
