package com.example.service

import android.content.Context
import android.util.Log
import com.example.data.DocumentDao
import com.example.model.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Service zur Migration bestehender Dokumente auf das neue sichere Speicherformat (Interne IDs & Enforced Encryption).
 */
class DocumentMigrationService(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val vaultSecurityManager: VaultSecurityManager
) {

    data class MigrationResult(
        val total: Int,
        val migrated: Int,
        val skipped: Int,
        val failed: Int,
        val details: List<String> = emptyList()
    )

    /**
     * Führt die Migration für alle in der Datenbank registrierten Dokumente durch.
     */
    suspend fun migrateAllDocuments(passwordKey: String?): MigrationResult = withContext(Dispatchers.IO) {
        val allDocs = documentDao.getAllDocuments().first()
        var migrated = 0
        var skipped = 0
        var failed = 0
        val details = mutableListOf<String>()

        val encryptionKey = vaultSecurityManager.getActiveDek()
        val pw = passwordKey ?: ""

        for (doc in allDocs) {
            try {
                val oldFile = File(doc.filePath)
                val fileName = oldFile.name
                
                // Prüfen ob bereits migriert (Interne ID Struktur: doc_uuid...)
                val isAlreadyMigrated = fileName.startsWith("doc_") && 
                                        (fileName.endsWith(".enc") || !doc.isEncrypted)
                
                if (isAlreadyMigrated) {
                    skipped++
                    continue
                }

                if (!oldFile.exists()) {
                    details.add("FEHLT: ${doc.title} (${doc.filePath})")
                    failed++
                    continue
                }

                // Migration durchführen
                val isEncrypted = doc.isEncrypted
                val extension = oldFile.extension.ifBlank { "pdf" }.replace("enc", "pdf")
                val internalName = DocumentStorageService.generateInternalFileName(extension, isEncrypted)
                val targetDir = oldFile.parentFile ?: DocumentStorageService.getBaseDocumentsDirectory(context)
                
                val finalFile = DocumentStorageService.secureSave(
                    context = context,
                    targetDir = targetDir,
                    internalFileName = internalName,
                    isEncrypted = isEncrypted,
                    encryptionKey = encryptionKey,
                    passwordKey = pw,
                    inputAction = { tempFile ->
                        oldFile.copyTo(tempFile, overwrite = true)
                    }
                )

                // Datenbank aktualisieren
                val updatedDoc = doc.copy(
                    filePath = finalFile.absolutePath,
                    fileSizeFormatted = "${(finalFile.length() / 1024).coerceAtLeast(1)} KB"
                )
                documentDao.updateDocument(updatedDoc)

                // Altes File löschen
                if (oldFile.absolutePath != finalFile.absolutePath) {
                    oldFile.delete()
                }

                migrated++
                details.add("OK: ${doc.title} -> $internalName")

            } catch (e: Exception) {
                Log.e("DocumentMigration", "Fehler bei Migration von ${doc.title}: ${e.message}", e)
                failed++
                details.add("ERROR: ${doc.title} (${e.message})")
            }
        }

        MigrationResult(allDocs.size, migrated, skipped, failed, details)
    }
}
