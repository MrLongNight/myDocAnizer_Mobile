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
    @Test
    fun `document index must only reference safe staged archive files`() {
        val valid = org.json.JSONArray()
            .put(org.json.JSONObject().put("id", 123L).put("fileName", "invoice.pdf.enc"))
        assertTrue(
            DocumentStorageService.validateBackupDocumentIndex(
                valid,
                setOf("123_invoice.pdf.enc")
            ).contains("123_invoice.pdf.enc")
        )

        val missing = org.json.JSONArray()
            .put(org.json.JSONObject().put("id", 123L).put("fileName", "missing.pdf.enc"))
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            DocumentStorageService.validateBackupDocumentIndex(missing, emptySet())
        }

        val externalPath = org.json.JSONArray()
            .put(org.json.JSONObject().put("id", 123L).put("fileName", "../outside.pdf"))
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            DocumentStorageService.validateBackupDocumentIndex(externalPath, setOf("123_outside.pdf"))
        }

        val duplicateIds = org.json.JSONArray()
            .put(org.json.JSONObject().put("id", 123L).put("fileName", "first.pdf"))
            .put(org.json.JSONObject().put("id", 123L).put("fileName", "second.pdf"))
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            DocumentStorageService.validateBackupDocumentIndex(
                duplicateIds,
                setOf("123_first.pdf", "123_second.pdf")
            )
        }

        val orphan = org.json.JSONArray()
            .put(org.json.JSONObject().put("id", 123L).put("fileName", "invoice.pdf.enc"))
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            DocumentStorageService.validateBackupDocumentIndex(
                orphan,
                setOf("123_invoice.pdf.enc", "999_unreferenced.pdf.enc")
            )
        }
    }

    @Test
    fun `user selected backup stream is bounded and copied completely`() {
        val payload = ByteArray(128 * 1024) { (it % 251).toByte() }
        val out = java.io.ByteArrayOutputStream()
        val count = DocumentStorageService.copyBackupInputBounded(
            java.io.ByteArrayInputStream(payload),
            out,
            maxBytes = payload.size.toLong()
        )
        org.junit.Assert.assertEquals(payload.size.toLong(), count)
        org.junit.Assert.assertArrayEquals(payload, out.toByteArray())
    }

    @Test
    fun `user selected backup stream aborts when limit is exceeded`() {
        val payload = ByteArray(1024) { 1 }
        val out = java.io.ByteArrayOutputStream()
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            DocumentStorageService.copyBackupInputBounded(
                java.io.ByteArrayInputStream(payload),
                out,
                maxBytes = 100
            )
        }
        org.junit.Assert.assertTrue(out.size() <= 100)
    }

}
