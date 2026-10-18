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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VaultV1CompatibilityTest {
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
    }
}
