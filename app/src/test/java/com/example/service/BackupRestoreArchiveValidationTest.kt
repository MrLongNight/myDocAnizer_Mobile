package com.example.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class BackupRestoreArchiveValidationTest {

    @Test
    fun `single document entry under documents is accepted`() {
        assertTrue(DocumentStorageService.isSafeBackupDocumentEntryName("documents/123_invoice.pdf.enc"))
        assertTrue(DocumentStorageService.isSafeBackupDocumentEntryName("documents/a file #1.pdf.enc"))
    }

    @Test
    fun `zip slip and nested document paths are rejected`() {
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("documents/../outside.pdf"))
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("documents/../../outside.pdf"))
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("documents/folder/file.pdf"))
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("documents/"))
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("documents/."))
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("documents/.."))
    }

    @Test
    fun `backslashes controls and unexpected roots are rejected`() {
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("documents\\..\\outside.pdf"))
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("outside.pdf"))
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("vault_metadata.json"))
        assertFalse(DocumentStorageService.isSafeBackupDocumentEntryName("documents/bad\u0000name.pdf"))
    }
}
