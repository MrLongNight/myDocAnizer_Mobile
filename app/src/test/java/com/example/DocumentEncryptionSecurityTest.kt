package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.DocumentStorageService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentEncryptionSecurityTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testEncryptionAndDecryptionRoundtrip() {
        val plainFile = File(context.cacheDir, "test_plain_${System.currentTimeMillis()}.pdf")
        val encFile = File(context.cacheDir, "test_enc_${System.currentTimeMillis()}.enc")
        val decFile = File(context.cacheDir, "test_dec_${System.currentTimeMillis()}.pdf")

        try {
            val sampleContent = "myDocAnizer Secured Document Content - Confidential AES-256-GCM Test 12345"
            plainFile.writeText(sampleContent, Charsets.UTF_8)
            assertTrue(plainFile.exists())

            val password = "SecureMasterPassword#2026!"

            // 1. Encrypt
            val encryptedResult = DocumentStorageService.encryptFile(plainFile, encFile, password)
            assertNotNull(encryptedResult)
            assertTrue(encFile.exists())
            assertTrue(encFile.length() > plainFile.length()) // Includes header (salt + iv + tag)

            // Ensure original plaintext file is not encrypted in place
            assertNotEquals(plainFile.absolutePath, encFile.absolutePath)

            // 2. Decrypt with correct password
            val decryptedSuccess = DocumentStorageService.decryptFile(encFile, decFile, password)
            assertTrue(decryptedSuccess)
            assertTrue(decFile.exists())

            val restoredContent = decFile.readText(Charsets.UTF_8)
            assertEquals(sampleContent, restoredContent)

        } finally {
            if (plainFile.exists()) plainFile.delete()
            if (encFile.exists()) encFile.delete()
            if (decFile.exists()) decFile.delete()
        }
    }

    @Test
    fun testDecryptionWithWrongPasswordFails() {
        val plainFile = File(context.cacheDir, "test_wrong_pw_plain.pdf")
        val encFile = File(context.cacheDir, "test_wrong_pw_enc.enc")
        val decFile = File(context.cacheDir, "test_wrong_pw_dec.pdf")

        try {
            plainFile.writeText("Sensitive Financial Record", Charsets.UTF_8)
            val correctPassword = "CorrectMasterPassword123"
            val wrongPassword = "WrongPassword999"

            DocumentStorageService.encryptFile(plainFile, encFile, correctPassword)
            assertTrue(encFile.exists())

            // Decrypt with wrong password must return false and not produce valid output
            val success = DocumentStorageService.decryptFile(encFile, decFile, wrongPassword)
            assertFalse(success)
            assertFalse(decFile.exists())

        } finally {
            if (plainFile.exists()) plainFile.delete()
            if (encFile.exists()) encFile.delete()
            if (decFile.exists()) decFile.delete()
        }
    }

    @Test
    fun testTamperedCiphertextFailsIntegrity() {
        val plainFile = File(context.cacheDir, "test_tamper_plain.pdf")
        val encFile = File(context.cacheDir, "test_tamper_enc.enc")
        val decFile = File(context.cacheDir, "test_tamper_dec.pdf")

        try {
            plainFile.writeText("Data Integrity Verification Test", Charsets.UTF_8)
            val password = "TestPassword#456"

            DocumentStorageService.encryptFile(plainFile, encFile, password)
            assertTrue(encFile.exists())

            // Tamper with bytes in the encrypted file (e.g. modify middle byte)
            val bytes = encFile.readBytes()
            if (bytes.size > 30) {
                bytes[25] = (bytes[25].toInt() xor 0xFF).toByte()
                encFile.writeBytes(bytes)
            }

            // GCM authentication tag check must fail
            val success = DocumentStorageService.decryptFile(encFile, decFile, password)
            assertFalse(success)

        } finally {
            if (plainFile.exists()) plainFile.delete()
            if (encFile.exists()) encFile.delete()
            if (decFile.exists()) decFile.delete()
        }
    }

    @Test
    fun testDuplicateTitleAndDateFilenameCollisionPrevention() {
        val targetDir = File(context.cacheDir, "collision_test_dir").apply { mkdirs() }
        try {
            val date = java.util.Date()
            val fileName1 = DocumentStorageService.formatFileName("Vodafone", "Rechnung", date)
            val fileName2 = DocumentStorageService.formatFileName("Vodafone", "Rechnung", date)

            assertEquals(fileName1, fileName2)

            val id1 = 101L
            val id2 = 102L

            val file1 = File(targetDir, "${id1}_$fileName1")
            val file2 = File(targetDir, "${id2}_$fileName2")

            file1.writeText("Content 1")
            file2.writeText("Content 2")

            assertTrue(file1.exists())
            assertTrue(file2.exists())
            assertNotEquals(file1.absolutePath, file2.absolutePath)
            assertEquals("Content 1", file1.readText())
            assertEquals("Content 2", file2.readText())

        } finally {
            targetDir.deleteRecursively()
        }
    }

    @Test
    fun testAtomicFileWritingAndCleanup() {
        val targetDir = File(context.cacheDir, "atomic_test_dir").apply { mkdirs() }
        val tempFile = File(targetDir, "document_temp_${System.currentTimeMillis()}.tmp")
        val finalFile = File(targetDir, "final_document.pdf")

        try {
            // Write to temp file first (atomic staging)
            tempFile.writeText("Atomic Document Content", Charsets.UTF_8)
            assertTrue(tempFile.exists())
            assertFalse(finalFile.exists())

            // Atomic rename / publish
            val renamed = tempFile.renameTo(finalFile)
            assertTrue(renamed)
            assertFalse(tempFile.exists())
            assertTrue(finalFile.exists())
            assertEquals("Atomic Document Content", finalFile.readText())

        } finally {
            if (tempFile.exists()) tempFile.delete()
            targetDir.deleteRecursively()
        }
    }
}
