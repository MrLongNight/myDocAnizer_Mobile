package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.VaultFactorType
import com.example.service.VaultHeaderParserV2
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import com.example.service.VaultSecurityTier
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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