package com.example

import android.util.Base64
import com.example.service.VaultHeaderV2
import com.example.service.VaultHeaderParserV2
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VaultHeaderV2Test {

    private fun createValidV2Header(): VaultHeaderV2 {
        val salt16 = ByteArray(16) { 0x01.toByte() }
        val iv12 = ByteArray(12) { 0x02.toByte() }
        val dekCipher = ByteArray(48) { 0x03.toByte() }

        return VaultHeaderV2(
            version = 2,
            algorithmId = "AES-256-GCM-Argon2id",
            vaultId = "VAULT-TEST1234",
            createdAt = 1729177200000L,
            argon2Iterations = 2,
            argon2MemoryKb = 19456,
            saltBase64 = Base64.encodeToString(salt16, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv12, Base64.NO_WRAP),
            tagLengthBits = 128,
            encryptedDekBase64 = Base64.encodeToString(dekCipher, Base64.NO_WRAP),
            securityTier = "ONE_FACTOR",
            factorType = "PASSWORD",
            autoLockMinutes = 5
        )
    }

    @Test
    fun `test v2 roundtrip serialization and parsing`() {
        val original = createValidV2Header()
        val jsonStr = original.toJson()

        val parsed = VaultHeaderParserV2.parse(jsonStr)
        assertNotNull("Parsed v2 header should not be null", parsed)
        assertEquals(original.version, parsed?.version)
        assertEquals(original.vaultId, parsed?.vaultId)
        assertEquals(original.argon2MemoryKb, parsed?.argon2MemoryKb)
        assertEquals(original.saltBase64, parsed?.saltBase64)
        assertEquals(original.ivBase64, parsed?.ivBase64)
    }

    @Test
    fun `test v2 parser rejects invalid version`() {
        val header = createValidV2Header().copy(version = 1)
        val jsonStr = header.toJson()
        assertNull("Parser must reject version != 2", VaultHeaderParserV2.parse(jsonStr))

        val headerV3 = createValidV2Header().copy(version = 3)
        assertNull("Parser must reject version 3", VaultHeaderParserV2.parse(headerV3.toJson()))
    }

    @Test
    fun `test v2 parser rejects unsupported algorithm and weak parameters`() {
        assertNull(
            VaultHeaderParserV2.parse(
                createValidV2Header().copy(algorithmId = "AES-128-CBC").toJson()
            )
        )
        assertNull(
            VaultHeaderParserV2.parse(
                createValidV2Header().copy(argon2Iterations = 1).toJson()
            )
        )
        assertNull(
            VaultHeaderParserV2.parse(
                createValidV2Header().copy(argon2MemoryKb = 16384).toJson()
            )
        )
    }

    @Test
    fun `v2 parser rejects factor declarations not enforced by its crypto path`() {
        assertNull(
            VaultHeaderParserV2.parse(
                createValidV2Header().copy(securityTier = "TWO_FACTOR").toJson()
            )
        )
        assertNull(
            VaultHeaderParserV2.parse(
                createValidV2Header().copy(
                    securityTier = "TWO_FACTOR",
                    factorType = "PASSWORD_AND_TOTP"
                ).toJson()
            )
        )
        assertNull(
            VaultHeaderParserV2.parse(
                createValidV2Header().copy(
                    securityTier = "UNENCRYPTED",
                    factorType = "NONE"
                ).toJson()
            )
        )
    }

    @Test
    fun `test v2 parser rejects invalid salt length`() {
        // Salt with 8 bytes instead of 16
        val invalidSalt = ByteArray(8) { 0x01.toByte() }
        val header = createValidV2Header().copy(
            saltBase64 = Base64.encodeToString(invalidSalt, Base64.NO_WRAP)
        )
        assertNull("Parser must reject invalid salt length", VaultHeaderParserV2.parse(header.toJson()))
    }

    @Test
    fun `test v2 parser rejects invalid iv length`() {
        // IV with 16 bytes instead of 12
        val invalidIv = ByteArray(16) { 0x02.toByte() }
        val header = createValidV2Header().copy(
            ivBase64 = Base64.encodeToString(invalidIv, Base64.NO_WRAP)
        )
        assertNull("Parser must reject invalid IV length", VaultHeaderParserV2.parse(header.toJson()))
    }

    @Test
    fun `test v2 parser rejects out of bounds memory`() {
        // Memory 512 KB (too low)
        val headerLow = createValidV2Header().copy(argon2MemoryKb = 512)
        assertNull("Parser must reject too low memory", VaultHeaderParserV2.parse(headerLow.toJson()))

        // Memory 1 GB (too high)
        val headerHigh = createValidV2Header().copy(argon2MemoryKb = 1048576)
        assertNull("Parser must reject too high memory", VaultHeaderParserV2.parse(headerHigh.toJson()))
    }

    @Test
    fun `test v2 parser rejects invalid vaultId format`() {
        val header = createValidV2Header().copy(vaultId = "INVALID-ID")
        assertNull("Parser must reject vaultId not starting with VAULT-", VaultHeaderParserV2.parse(header.toJson()))
    }
}
