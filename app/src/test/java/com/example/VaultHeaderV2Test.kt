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
    fun `v2 parser rejects missing required security metadata`() {
        val required = listOf("securityTier", "factorType", "autoLockMinutes", "domainSecurityTier", "domainFactorType", "recoveryMethod", "biometric2FaEnabled", "shamirShareHashes", "hasEmergencyPin")
        required.forEach { field ->
            val json = org.json.JSONObject(createValidV2Header().toJson())
            json.remove(field)
            assertNull("Missing required field $field must fail closed", VaultHeaderParserV2.parse(json.toString()))
        }
    }

    @Test
    fun `v2 parser rejects wrong JSON types instead of coercing defaults`() {
        val wrongTypes = listOf(
            "securityTier" to 1, "factorType" to true, "autoLockMinutes" to "5",
            "domainSecurityTier" to 1, "domainFactorType" to false, "recoveryMethod" to 0,
            "biometric2FaEnabled" to "false", "shamirShareHashes" to "[]", "hasEmergencyPin" to "false"
        )
        wrongTypes.forEach { (field, value) ->
            val json = org.json.JSONObject(createValidV2Header().toJson())
            json.put(field, value)
            assertNull("Wrong JSON type for $field must fail closed", VaultHeaderParserV2.parse(json.toString()))
        }
    }

    @Test
    fun `v2 parser rejects fractional numbers and invalid optional field types`() {
        val fractionalVersion = org.json.JSONObject(createValidV2Header().toJson()).put("version", 2.5)
        assertNull("Fractional version must not be truncated to v2", VaultHeaderParserV2.parse(fractionalVersion.toString()))

        val fractionalIterations = org.json.JSONObject(createValidV2Header().toJson()).put("argon2Iterations", 2.5)
        assertNull("Fractional KDF iterations must be rejected", VaultHeaderParserV2.parse(fractionalIterations.toString()))

        val wrongOptionalType = org.json.JSONObject(createValidV2Header().toJson()).put("totpSecret", 123456)
        assertNull("Optional secret fields must not be coerced from numbers", VaultHeaderParserV2.parse(wrongOptionalType.toString()))

        val wrongShareType = org.json.JSONObject(createValidV2Header().toJson())
            .put("shamirShareHashes", org.json.JSONArray().put(123))
        assertNull("Share hashes must be strings", VaultHeaderParserV2.parse(wrongShareType.toString()))
    }

    @Test
    fun `v2 parser rejects integer fields that overflow Int`() {
        // These values wrap to otherwise valid Int values if optInt() runs before range validation.
        val overflowingValues = listOf(
            "version" to 4_294_967_298L,
            "argon2Iterations" to 4_294_967_298L,
            "argon2MemoryKb" to 4_294_986_752L,
            "tagLengthBits" to 4_294_967_424L,
            "autoLockMinutes" to 4_294_967_301L
        )

        overflowingValues.forEach { (field, value) ->
            val json = org.json.JSONObject(createValidV2Header().toJson()).put(field, value)
            assertNull("Overflowing integer $field must fail closed", VaultHeaderParserV2.parse(json.toString()))
        }
    }

    @Test
    fun `v2 parser rejects inconsistent recovery and factor metadata`() {
        val biometricWithoutSlot = org.json.JSONObject(createValidV2Header().toJson())
            .put("biometric2FaEnabled", true)
        assertNull("Enabled biometrics require an encrypted DEK slot", VaultHeaderParserV2.parse(biometricWithoutSlot.toString()))

        val bip39WithoutRecoverySlot = org.json.JSONObject(createValidV2Header().toJson())
            .put("recoveryMethod", "BIP39_WORDS")
        assertNull("BIP-39 requires an encrypted recovery slot and salt", VaultHeaderParserV2.parse(bip39WithoutRecoverySlot.toString()))

        val shamirWithNoShares = org.json.JSONObject(createValidV2Header().toJson())
            .put("recoveryMethod", "SHAMIR_2_OF_3")
        assertNull("Shamir recovery requires exactly three share hashes", VaultHeaderParserV2.parse(shamirWithNoShares.toString()))

        // The parser is also used while a new crypto header is staged, before
        // VaultSecurityManager adds the persisted token-hash verifier.
        val stagedTwoFactorHeader = createValidV2Header().copy(
            securityTier = "TWO_FACTOR",
            factorType = "PASSWORD_AND_KEYFILE",
            domainSecurityTier = "TWO_FACTOR",
            domainFactorType = "PASSWORD_AND_KEYFILE"
        )
        assertNotNull("A structurally valid staged two-factor crypto header must parse", VaultHeaderParserV2.parse(stagedTwoFactorHeader.toJson()))
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
