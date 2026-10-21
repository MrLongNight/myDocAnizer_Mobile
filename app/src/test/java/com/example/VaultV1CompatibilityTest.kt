package com.example

import android.app.Application
import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.service.Argon2idEngine
import com.example.service.Bip39Engine
import com.example.service.ShamirEngine
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VaultV1CompatibilityTest {
    // Robolectric creates isolated application contexts per test class, while the manager is a process singleton.
    // Reset it so one test cannot retain another test's SharedPreferences/context.
    @Before
    fun resetVaultManagerBeforeTest() = resetVaultManagerSingleton()

    @After
    fun resetVaultManagerAfterTest() = resetVaultManagerSingleton()

    private fun resetVaultManagerSingleton() {
        val instanceField = VaultSecurityManager::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, null)
    }

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `generated v1 header matches persisted legacy schema and unlocks`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        val password = "LegacyV1FixturePassword2026!"
        val setup = manager.initializeVault(
            masterPassword = password,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE
        )

        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val raw = prefs.getString("vault_header_json_v1", null)
        assertNotNull(raw)
        val json = JSONObject(raw!!)
        assertEquals(1, json.getInt("version"))
        assertEquals(setup.vaultId, json.getString("vaultId"))
        assertEquals(19456, json.getInt("argon2MemoryKb"))
        assertEquals(16, Base64.decode(json.getString("argon2Salt"), Base64.NO_WRAP).size)
        assertEquals(12, Base64.decode(json.getString("ivPassword"), Base64.NO_WRAP).size)
        val encryptedDek = Base64.decode(json.getString("encDekPassword"), Base64.NO_WRAP)
        assertEquals(48, encryptedDek.size)

        val header = manager.vaultHeader.value ?: error("Expected generated vault header")
        val salt = Base64.decode(header.argon2SaltBase64, Base64.NO_WRAP)
        val iv = Base64.decode(header.passwordDekIvBase64, Base64.NO_WRAP)
        val kek = manager.deriveKekArgon2id(
            password, salt, iterations = header.argon2Iterations, memoryKb = header.argon2MemoryKb
        )
        try {
            val tampered = encryptedDek.clone().apply {
                this[0] = (this[0].toInt() xor 1).toByte()
            }
            assertThrows(Exception::class.java) {
                manager.decryptAesGcm(tampered, iv, kek)
            }
        } finally {
            kek.fill(0)
        }

        manager.lockVault()
        assertFalse(manager.unlockWithPassword("DefinitelyWrongLegacyPassword2026!"))
        assertTrue(manager.unlockWithPassword(password))
        assertArrayEquals(setup.dek, manager.getActiveDek())
    }

    @Test
    fun `successful password only v1 unlock promotes verified v2 without changing canonical v1 header`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        val password = "StageV2FixturePassword2026!"
        val setup = manager.initializeVault(
            masterPassword = password,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE
        )
        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val originalV1 = prefs.getString("vault_header_json_v1", null)
            ?: error("Expected canonical v1 header")

        manager.lockVault()
        assertTrue(manager.unlockWithPassword(password))

        // The existing v1 header remains byte-for-byte intact until all v2 unlock/recovery
        // paths can be switched together.
        assertEquals(originalV1, prefs.getString("vault_header_json_v1", null))

        val activeRaw = prefs.getString("vault_header_json_v2_active", null)
        assertNotNull("Expected verified active v2 header", activeRaw)
        assertNull("Verified staging header should be atomically promoted", prefs.getString("vault_header_json_v2_staging", null))
        val staged = com.example.service.VaultHeaderParserV2.parse(activeRaw)
            ?: error("Active v2 header must pass strict validation")
        assertEquals(2, staged.version)
        assertEquals(setup.vaultId, staged.vaultId)
        val reopenedDek = com.example.service.VaultHeaderCryptoV2.unwrapDek(staged, password.toCharArray())
        try {
            assertArrayEquals(setup.dek, reopenedDek)
        } finally {
            reopenedDek.fill(0)
        }
    }

    @Test
    fun `active v2 header can unlock and change password when legacy v1 header is absent`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        val oldPassword = "RuntimeV2FixturePassword2026!"
        val newPassword = "RuntimeV2ChangedPassword2026!"
        val setup = manager.initializeVault(
            masterPassword = oldPassword,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE
        )
        manager.lockVault()
        assertTrue(manager.unlockWithPassword(oldPassword))

        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        assertNotNull("Expected active v2 header",
            prefs.getString("vault_header_json_v2_active", null))
        assertTrue(prefs.edit().remove("vault_header_json_v1").commit())

        resetVaultManagerSingleton()
        val reloaded = VaultSecurityManager.getInstance(context)
        assertEquals(2, reloaded.vaultHeader.value?.version)
        assertTrue("A valid v2 header must independently unlock the existing vault",
            reloaded.unlockWithPassword(oldPassword))
        assertArrayEquals(setup.dek, reloaded.getActiveDek())
        assertTrue("Password rotation must keep a v2-only vault usable",
            reloaded.changeMasterPassword(oldPassword, newPassword))
        reloaded.lockVault()
        resetVaultManagerSingleton()

        val afterRotation = VaultSecurityManager.getInstance(context)
        assertTrue(afterRotation.unlockWithPassword(newPassword))
        assertArrayEquals(setup.dek, afterRotation.getActiveDek())
        afterRotation.setAutoLockMinutes(12)
        assertEquals(12, afterRotation.vaultHeader.value?.autoLockMinutes)
        afterRotation.lockVault()
        assertFalse("The previous password must no longer unlock v2",
            afterRotation.unlockWithPassword(oldPassword))
    }

    @Test
    fun `corrupt active v2 header falls back to valid v1 and is repaired`() {
        val context = context()
        val manager = VaultSecurityManager.getInstance(context)
        val password = "FallbackV2FixturePassword2026!"
        val setup = manager.initializeVault(
            masterPassword = password,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE
        )
        manager.lockVault()
        assertTrue(manager.unlockWithPassword(password))

        val prefs = context.getSharedPreferences("mydocanizer_vault_security", Context.MODE_PRIVATE)
        val originalV1 = prefs.getString("vault_header_json_v1", null)
            ?: error("Expected v1 rollback header")
        val activeJson = JSONObject(prefs.getString("vault_header_json_v2_active", null)!!)
        val encryptedDek = Base64.decode(activeJson.getString("encryptedDekBase64"), Base64.NO_WRAP)
        encryptedDek[0] = (encryptedDek[0].toInt() xor 1).toByte()
        activeJson.put("encryptedDekBase64", Base64.encodeToString(encryptedDek, Base64.NO_WRAP))
        assertTrue(prefs.edit().putString("vault_header_json_v2_active", activeJson.toString()).commit())

        resetVaultManagerSingleton()
        val reloaded = VaultSecurityManager.getInstance(context)
        assertTrue("Valid legacy header must remain a rollback path",
            reloaded.unlockWithPassword(password))
        assertArrayEquals(setup.dek, reloaded.getActiveDek())
        assertEquals("Legacy v1 record must remain unchanged", originalV1,
            prefs.getString("vault_header_json_v1", null))
        val repaired = com.example.service.VaultHeaderParserV2.parse(
            prefs.getString("vault_header_json_v2_active", null))
        assertNotNull("A verified v2 header should be regenerated", repaired)
        val reopenedDek = com.example.service.VaultHeaderCryptoV2.unwrapDek(repaired!!, password.toCharArray())
        try {
            assertArrayEquals(setup.dek, reopenedDek)
        } finally {
            reopenedDek.fill(0)
        }
    }

    @Test
    fun `legacy Argon2id parameters remain deterministic and weak values are rejected`() {
        val password = "master-password".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        val key1 = Argon2idEngine.deriveKey(password, salt, iterations = 2, memoryKb = 16384)
        val key2 = Argon2idEngine.deriveKey(password, salt, iterations = 2, memoryKb = 16384)
        assertArrayEquals(key1, key2)
        assertEquals(32, key1.size)

        assertThrows(IllegalArgumentException::class.java) {
            Argon2idEngine.deriveKey(password, salt, iterations = 1, memoryKb = 1024)
        }
    }

    @Test
    fun `BIP39 official zero entropy mnemonic validates checksum`() {
        assertEquals(2048, Bip39Engine.BIP39_WORD_LIST.size)
        val valid = List(11) { "abandon" } + "about"
        assertTrue(Bip39Engine.validateMnemonic(valid))
        assertFalse(Bip39Engine.validateMnemonic(List(12) { "abandon" }))
    }

    @Test
    fun `all Shamir pairs reconstruct the same synthetic secret`() {
        val secret = ByteArray(32) { it.toByte() }
        val shares = ShamirEngine.split2of3(secret)
        assertEquals(3, shares.size)
        assertArrayEquals(secret, ShamirEngine.combine2of3(shares[0], shares[1]))
        assertArrayEquals(secret, ShamirEngine.combine2of3(shares[0], shares[2]))
        assertArrayEquals(secret, ShamirEngine.combine2of3(shares[1], shares[2]))
        assertNull(ShamirEngine.combine2of3(shares[0], shares[0]))
        assertNull(ShamirEngine.combine2of3("prefix" + shares[0], shares[1]))
        assertNull(ShamirEngine.combine2of3(shares[0].dropLast(4), shares[1]))
    }
}
