package com.example

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.service.VaultFactorType
import com.example.service.VaultHeaderCryptoV2
import com.example.service.VaultHeaderParserV2
import com.example.service.VaultHeaderV2
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import com.example.service.VaultSecurityTier
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VaultV2OnlyTest {
    @Before fun resetBefore() = resetSingleton()
    @After fun resetAfter() = resetSingleton()

    private fun resetSingleton() {
        val field = VaultSecurityManager::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `initialization persists valid V2 and supports restart unlock`() {
        val context = context()
        val password = "V2OnlyFixturePassword2026!"
        val manager = VaultSecurityManager.getInstance(context)
        val setup = manager.initializeVault(password, false, VaultRecoveryMethod.NONE)

        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val parsed = VaultHeaderParserV2.parse(prefs.getString("vault_header_json_v2_active", null))
        assertNotNull(parsed)
        assertEquals(2, parsed!!.version)
        assertEquals(setup.vaultId, parsed.vaultId)
        assertEquals("ONE_FACTOR", parsed.securityTier)
        assertEquals("PASSWORD", parsed.factorType)
        manager.lockVault()
        assertFalse(manager.unlockWithPassword("IncorrectV2Password2026!"))
        assertTrue(manager.unlockWithPassword(password))
        assertArrayEquals(setup.dek, manager.getActiveDek())

        manager.lockVault()
        resetSingleton()
        val reloaded = VaultSecurityManager.getInstance(context)
        assertEquals(2, reloaded.vaultHeader.value?.version)
        assertTrue(reloaded.unlockWithPassword(password))
        assertArrayEquals(setup.dek, reloaded.getActiveDek())
    }

    @Test
    fun `two factor vault requires correct password and keyfile token`() {
        val context = context()
        val password = "V2TwoFactorPassword2026!"
        val token = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val manager = VaultSecurityManager.getInstance(context)
        val setup = manager.initializeVault(
            masterPassword = password,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE,
            securityTier = VaultSecurityTier.TWO_FACTOR,
            factorType = VaultFactorType.PASSWORD_AND_KEYFILE,
            keyFileToken = token
        )
        val parsed = VaultHeaderParserV2.parse(
            context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
                .getString("vault_header_json_v2_active", null)
        )
        assertNotNull(parsed)
        assertEquals("TWO_FACTOR", parsed!!.securityTier)
        assertEquals("PASSWORD_AND_KEYFILE", parsed.factorType)
        manager.lockVault()
        assertFalse(manager.unlockVault(password = password))
        assertFalse(manager.unlockVault(password = "", keyFileToken = token))
        assertFalse(manager.unlockVault(password = password, keyFileToken = token.dropLast(2)))
        assertFalse(manager.unlockVault(password = password, keyFileToken = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"))
        assertTrue(manager.unlockVault(password = password, keyFileToken = token))
        assertArrayEquals(setup.dek, manager.getActiveDek())
    }

    @Test
    fun `persisted two factor header without token hash fails closed`() {
        val context = context()
        val password = "V2MissingTokenHashPassword2026!"
        val token = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val manager = VaultSecurityManager.getInstance(context)
        manager.initializeVault(
            masterPassword = password,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE,
            securityTier = VaultSecurityTier.TWO_FACTOR,
            factorType = VaultFactorType.PASSWORD_AND_KEYFILE,
            keyFileToken = token
        )
        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val json = org.json.JSONObject(prefs.getString("vault_header_json_v2_active", null)!!)
        json.remove("keyFileTokenHash")
        assertTrue(prefs.edit().putString("vault_header_json_v2_active", json.toString()).commit())

        resetSingleton()
        val reloaded = VaultSecurityManager.getInstance(context)
        assertNull("Persisted two-factor header without verifier must not load", reloaded.vaultHeader.value)
        assertFalse(reloaded.unlockVault(password = password, keyFileToken = token))
    }

    @Test
    fun `legacy unsigned V2 password unlock rewraps and remains unlockable after restart`() {
        val context = context()
        val password = "LegacyUnsignedV2Password2026!"
        val manager = VaultSecurityManager.getInstance(context)
        val setup = manager.initializeVault(password, false, VaultRecoveryMethod.NONE)
        val expectedDek = setup.dek.copyOf()
        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)

        try {
            val signed = VaultHeaderParserV2.parse(
                prefs.getString("vault_header_json_v2_active", null)
            ) ?: error("Initialized V2 header expected")
            val legacy = createLegacyUnsignedHeader(signed, password, expectedDek)
            assertEquals(0, legacy.metadataAuthVersion)
            assertTrue(prefs.edit().putString("vault_header_json_v2_active", legacy.toJson()).commit())

            resetSingleton()
            val firstReload = VaultSecurityManager.getInstance(context)
            assertTrue("Correct password should migrate a genuine unsigned V2 wrapper",
                firstReload.unlockVault(password = password))

            val migrated = VaultHeaderParserV2.parse(
                prefs.getString("vault_header_json_v2_active", null)
            ) ?: error("Migrated V2 header expected")
            assertEquals("Migration must install authenticated metadata", 1, migrated.metadataAuthVersion)
            assertFalse("Migration must replace the old-AAD DEK wrapper",
                legacy.encryptedDekBase64 == migrated.encryptedDekBase64)
            val unwrapped = VaultHeaderCryptoV2.unwrapDek(migrated, password.toCharArray())
            try {
                assertArrayEquals(expectedDek, unwrapped)
            } finally {
                unwrapped.fill(0)
            }

            firstReload.lockVault()
            resetSingleton()
            val secondReload = VaultSecurityManager.getInstance(context)
            assertTrue("The upgraded header must also unlock after a process restart",
                secondReload.unlockVault(password = password))
            secondReload.lockVault()
        } finally {
            expectedDek.fill(0)
            setup.dek.fill(0)
        }
    }

    @Test
    fun `legacy recovery fails closed until password unlock rewraps its header`() {
        val context = context()
        val password = "LegacyRecoveryV2Password2026!"
        val manager = VaultSecurityManager.getInstance(context)
        val setup = manager.initializeVault(password, false, VaultRecoveryMethod.BIP39_WORDS)
        val expectedDek = setup.dek.copyOf()
        val words = setup.bip39Words ?: error("Generated BIP-39 phrase expected")
        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)

        try {
            val signed = VaultHeaderParserV2.parse(
                prefs.getString("vault_header_json_v2_active", null)
            ) ?: error("Initialized V2 header expected")
            val legacy = createLegacyUnsignedHeader(signed, password, expectedDek)
            assertTrue(prefs.edit().putString("vault_header_json_v2_active", legacy.toJson()).commit())

            resetSingleton()
            val reloaded = VaultSecurityManager.getInstance(context)
            val recovered = reloaded.recoverDekWithBip39(words)
            assertNull(
                "Unsigned metadata must not be accepted or signed through recovery; migrate using the password first",
                recovered
            )
            recovered?.fill(0)

            val afterRecovery = VaultHeaderParserV2.parse(
                prefs.getString("vault_header_json_v2_active", null)
            ) ?: error("Legacy V2 header should remain valid")
            assertEquals("Recovery alone must not falsely mark the old-AAD wrapper as upgraded",
                0, afterRecovery.metadataAuthVersion)

            assertTrue("Password unlock performs the actual wrapper rewrap",
                reloaded.unlockVault(password = password))
            val upgraded = VaultHeaderParserV2.parse(
                prefs.getString("vault_header_json_v2_active", null)
            ) ?: error("Migrated V2 header expected")
            assertEquals(1, upgraded.metadataAuthVersion)
            reloaded.lockVault()
        } finally {
            expectedDek.fill(0)
            setup.dek.fill(0)
        }
    }

    @Test
    fun `recovery metadata remains in V2 across process reload`() {
        val context = context()
        val password = "V2MetadataFixturePassword2026!"
        val manager = VaultSecurityManager.getInstance(context)
        manager.initializeVault(
            masterPassword = password,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.RECOVERY_CODE,
            recoveryCode = "TEST-RECOVERY-CODE-2026"
        )
        val before = manager.vaultHeader.value ?: error("Header expected")
        assertEquals(VaultRecoveryMethod.RECOVERY_CODE, before.recoveryMethod)
        assertNotNull(before.encryptedDekByRecoveryBase64)
        manager.lockVault()
        resetSingleton()
        val reloaded = VaultSecurityManager.getInstance(context)
        val after = reloaded.vaultHeader.value ?: error("V2 header expected")
        assertEquals(2, after.version)
        assertEquals(VaultRecoveryMethod.RECOVERY_CODE, after.recoveryMethod)
        assertNotNull(after.encryptedDekByRecoveryBase64)
        assertNotNull(after.recoveryCodeHash)
        assertTrue(reloaded.unlockWithPassword(password))
    }

    @Test
    fun `generated recovery credentials recover the exact original DEK`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)

        val bip39Setup = manager.initializeVault(
            masterPassword = "V2GeneratedBip39Password2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.BIP39_WORDS
        )
        val words = bip39Setup.bip39Words ?: error("Generated BIP-39 words expected")
        assertTrue(manager.validateBip39Mnemonic(words))
        val bip39RecoveredDek = manager.recoverDekWithBip39(words)
        assertNotNull("Returned generated mnemonic must unlock the persisted recovery wrapper", bip39RecoveredDek)
        assertArrayEquals(bip39Setup.dek, bip39RecoveredDek)
        bip39RecoveredDek?.fill(0)

        val codeSetup = manager.initializeVault(
            masterPassword = "V2GeneratedRecoveryCodePassword2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.RECOVERY_CODE
        )
        val code = codeSetup.recoveryCode ?: error("Generated recovery code expected")
        val codeRecoveredDek = manager.recoverDekWithRecoveryCode(code)
        assertNotNull("Returned generated recovery code must unlock the persisted recovery wrapper", codeRecoveredDek)
        assertArrayEquals(codeSetup.dek, codeRecoveredDek)
        codeRecoveredDek?.fill(0)
    }

    @Test
    fun `BIP-39 recovery rejects metadata tampering before returning the DEK`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        val setup = manager.initializeVault(
            masterPassword = "V2Bip39TamperPassword2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.BIP39_WORDS
        )
        val words = setup.bip39Words ?: error("Generated BIP-39 words expected")
        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val raw = prefs.getString("vault_header_json_v2_active", null)
            ?: error("Persisted V2 header expected")
        val tampered = org.json.JSONObject(raw).put("autoLockMinutes", 6)
        assertTrue(prefs.edit().putString("vault_header_json_v2_active", tampered.toString()).commit())

        resetSingleton()
        val reloaded = VaultSecurityManager.getInstance(context)
        assertNull(
            "Recovery must reject a validly shaped but unauthenticated header",
            reloaded.recoverDekWithBip39(words)
        )
        assertNull("Rejected recovery must not install an active DEK", reloaded.getActiveDek())
    }

    @Test
    fun `all software recovery paths reject unauthenticated vault metadata`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        val bip39 = manager.initializeVault(
            masterPassword = "V2Bip39MetadataPassword2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.BIP39_WORDS
        )
        val words = bip39.bip39Words ?: error("Generated BIP-39 words expected")
        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val rawBip39 = prefs.getString("vault_header_json_v2_active", null)
            ?: error("Persisted V2 header expected")
        assertTrue(
            prefs.edit().putString(
                "vault_header_json_v2_active",
                org.json.JSONObject(rawBip39).put("autoLockMinutes", 6).toString()
            ).commit()
        )
        resetSingleton()
        assertNull(
            "BIP-39 must not bypass the DEK-derived metadata authenticator",
            VaultSecurityManager.getInstance(context).recoverDekWithBip39(words)
        )

        val shamirManager = VaultSecurityManager.getInstance(context)
        val shamir = shamirManager.initializeVault(
            masterPassword = "V2ShamirMetadataPassword2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.SHAMIR_2_OF_3
        )
        val shares = shamir.shamirShares ?: error("Generated Shamir shares expected")
        val rawShamir = prefs.getString("vault_header_json_v2_active", null)
            ?: error("Persisted Shamir V2 header expected")
        assertTrue(
            prefs.edit().putString(
                "vault_header_json_v2_active",
                org.json.JSONObject(rawShamir).put("autoLockMinutes", 7).toString()
            ).commit()
        )
        resetSingleton()
        assertNull(
            "Shamir reconstruction must not bypass the DEK-derived metadata authenticator",
            VaultSecurityManager.getInstance(context)
                .recoverDekWithShamir(shares[0].token, shares[1].token)
        )
    }

    @Test
    fun `recovery can rotate password without changing the original DEK`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        val oldPassword = "V2RecoveryRotateOldPassword2026!"
        val newPassword = "V2RecoveryRotateNewPassword2026!"
        val recoveryCode = "ROTATE-RECOVERY-CODE-2026"
        val setup = manager.initializeVault(
            masterPassword = oldPassword,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.RECOVERY_CODE,
            recoveryCode = recoveryCode
        )

        manager.lockVault()
        val recoveredDek = manager.recoverDekWithRecoveryCode(recoveryCode)
            ?: error("Valid recovery code must recover the DEK")
        assertArrayEquals(setup.dek, recoveredDek)
        assertTrue(manager.resetPasswordWithRecoveredDek(newPassword, recoveredDek))
        recoveredDek.fill(0)

        manager.lockVault()
        assertFalse(manager.unlockWithPassword(oldPassword))
        assertTrue(manager.unlockWithPassword(newPassword))
        assertArrayEquals(setup.dek, manager.getActiveDek())
    }


    @Test
    fun `legacy TOTP secret is scrubbed from persisted V2 header and does not gate unlock`() {
        val context = context()
        val password = "V2LegacyTotpCleanupPassword2026!"
        val manager = VaultSecurityManager.getInstance(context)
        manager.initializeVault(password, false, VaultRecoveryMethod.NONE)
        manager.lockVault()

        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val raw = prefs.getString("vault_header_json_v2_active", null)
            ?: error("V2 header expected")
        val legacyHeader = org.json.JSONObject(raw)
            .put("totpSecret", "JBSWY3DPEHPK3PXP")
        assertTrue(prefs.edit().putString("vault_header_json_v2_active", legacyHeader.toString()).commit())

        resetSingleton()
        val reloaded = VaultSecurityManager.getInstance(context)
        assertNull(reloaded.vaultHeader.value?.totpSecret)

        val sanitizedRaw = prefs.getString("vault_header_json_v2_active", null)
            ?: error("Sanitized V2 header expected")
        assertFalse("Legacy TOTP secret must be removed from persistent JSON",
            org.json.JSONObject(sanitizedRaw).has("totpSecret"))
        assertTrue("Password remains the valid cryptographic unlock factor",
            reloaded.unlockVault(password = password, totpCode = null))
    }

    @Test
    fun `new vault setup rejects TOTP secret persistence`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        prefs.edit().remove("vault_header_json_v2_active").commit()

        assertThrows(IllegalArgumentException::class.java) {
            manager.initializeVault(
                masterPassword = "V2NoTotpPersistencePassword2026!",
                enableBiometrics = false,
                recoveryMethod = VaultRecoveryMethod.NONE,
                totpSecret = "JBSWY3DPEHPK3PXP"
            )
        }
        assertNull(prefs.getString("vault_header_json_v2_active", null))
    }

    @Test
    fun `failed vault reinitialization preserves previous security mode and header`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        manager.initializeVault(
            masterPassword = "",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE,
            securityTier = VaultSecurityTier.UNENCRYPTED,
            factorType = VaultFactorType.NONE
        )
        assertTrue(manager.isUnencryptedModeEnabled())

        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val originalHeader = prefs.getString("vault_header_json_v2_active", null)
            ?: error("Existing V2 header expected")

        assertThrows(IllegalArgumentException::class.java) {
            manager.initializeVault(
                masterPassword = "ReplacementPassword2026!",
                enableBiometrics = false,
                recoveryMethod = VaultRecoveryMethod.NONE,
                securityTier = VaultSecurityTier.ONE_FACTOR,
                factorType = VaultFactorType.PASSWORD,
                autoLockMinutes = -1
            )
        }

        assertTrue("A failed replacement must not change the previous security mode", manager.isUnencryptedModeEnabled())
        assertEquals("A failed replacement must not overwrite the previous V2 header",
            originalHeader, prefs.getString("vault_header_json_v2_active", null))
    }

    @Test
    fun `legacy header keys are ignored and never migrated into V2`() {
        val context = context()
        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val legacyMarker = """{"version":1,"vaultId":"LEGACY-MUST-NOT-LOAD"}"""
        assertTrue(
            prefs.edit()
                .putString("vault_header_json", legacyMarker)
                .putString("vault_header_json_v1", legacyMarker)
                .remove("vault_header_json_v2_active")
                .commit()
        )

        val manager = VaultSecurityManager.getInstance(context)
        assertNull("Legacy-only data must not be interpreted as a V2 vault", manager.vaultHeader.value)
        assertFalse("A legacy-only vault must not unlock through the V2 path", manager.unlockWithPassword("LegacyPassword2026!"))
        assertEquals(legacyMarker, prefs.getString("vault_header_json", null))
        assertEquals(legacyMarker, prefs.getString("vault_header_json_v1", null))
        assertNull("Startup must not create or promote a V2 header", prefs.getString("vault_header_json_v2_active", null))
    }

    /**
     * Creates a synthetic pre-metadata-HMAC V2 header for migration regression tests.
     * The legacy GCM AAD deliberately omits metadataAuthVersion=1.
     */
    private fun createLegacyUnsignedHeader(
        authenticatedHeader: VaultHeaderV2,
        password: String,
        dek: ByteArray
    ): VaultHeaderV2 {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val legacyBase = authenticatedHeader.copy(
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            encryptedDekBase64 = "",
            metadataAuthVersion = 0,
            metadataAuthBase64 = null
        )
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val kek = ByteArray(32)
        var ciphertext: ByteArray? = null
        try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(legacyBase.argon2Iterations)
                .withMemoryAsKB(legacyBase.argon2MemoryKb)
                .withParallelism(1)
                .withSalt(salt)
                .build()
            Argon2BytesGenerator().apply { init(params) }
                .generateBytes(passwordBytes, kek, 0, kek.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(kek, "AES"),
                GCMParameterSpec(legacyBase.tagLengthBits, iv)
            )
            val aad = listOf(
                "myDocAnizer-VaultHeader-v2",
                "version=${legacyBase.version}",
                "algorithmId=${legacyBase.algorithmId}",
                "vaultId=${legacyBase.vaultId}",
                "createdAt=${legacyBase.createdAt}",
                "argon2Iterations=${legacyBase.argon2Iterations}",
                "argon2MemoryKb=${legacyBase.argon2MemoryKb}",
                "saltBase64=${legacyBase.saltBase64}",
                "tagLengthBits=${legacyBase.tagLengthBits}",
                "securityTier=${legacyBase.securityTier}",
                "factorType=${legacyBase.factorType}",
                "autoLockMinutes=${legacyBase.autoLockMinutes}"
            ).joinToString("|").toByteArray(Charsets.UTF_8)
            try {
                cipher.updateAAD(aad)
                val encrypted = cipher.doFinal(dek)
                ciphertext = encrypted
                return legacyBase.copy(
                    encryptedDekBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
                )
            } finally {
                aad.fill(0)
            }
        } finally {
            passwordBytes.fill(0)
            kek.fill(0)
            salt.fill(0)
            iv.fill(0)
            ciphertext?.fill(0)
        }
    }

    @Test
    fun `malformed active V2 header fails closed`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        manager.initializeVault("V2CorruptionFixturePassword2026!", false, VaultRecoveryMethod.NONE)
        manager.lockVault()
        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        assertTrue(prefs.edit().putString("vault_header_json_v2_active", "{not-json").commit())
        resetSingleton()
        val reloaded = VaultSecurityManager.getInstance(context)
        assertNull(reloaded.vaultHeader.value)
        assertFalse(reloaded.unlockWithPassword("V2CorruptionFixturePassword2026!"))
    }
}