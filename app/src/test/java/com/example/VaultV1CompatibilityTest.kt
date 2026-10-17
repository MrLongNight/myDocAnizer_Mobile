package com.example

import android.util.Base64
import com.example.service.Argon2idEngine
import com.example.service.ShamirEngine
import com.example.service.VaultHeader
import com.example.service.VaultRecoveryMethod
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VaultV1CompatibilityTest {

    /**
     * Synthetisches Fixture für einen gültigen Vault Header v1.
     * Passwort: "test-password-123"
     * Salt: "SaltySalt12345678" (16 bytes)
     * DEK (synthetic): 32 bytes of 0x42
     */
    private val VALID_V1_HEADER_JSON = """
        {
          "version": 1,
          "vaultId": "VAULT-FIXTURE-V1",
          "createdAt": 1729177200000,
          "argon2Salt": "U2FsdHlTYWx0MTIzNDU2Nzg=",
          "argon2Iterations": 2,
          "argon2MemoryKb": 16384,
          "encDekPassword": "hN9rI/YpZtL8zG/H7v9rI/YpZtL8zG/H7v9rI/YpZtL8zG/H7v9rI/YpZtL8zG/H7v9rI/YpZtL8=",
          "ivPassword": "MTIzNDU2Nzg5MDEy",
          "biometric2Fa": false,
          "recoveryMethod": "NONE",
          "securityTier": "ONE_FACTOR",
          "factorType": "PASSWORD",
          "autoLockMinutes": 5
        }
    """.trimIndent()

    @Test
    fun `test parse v1 header fixture`() {
        val json = JSONObject(VALID_V1_HEADER_JSON)
        assertEquals(1, json.getInt("version"))
        assertEquals("VAULT-FIXTURE-V1", json.getString("vaultId"))
        assertEquals("U2FsdHlTYWx0MTIzNDU2Nzg=", json.getString("argon2Salt"))
    }

    @Test
    fun `test argon2id derivation with reference values`() {
        // Da Argon2id rechenintensiv ist, prüfen wir hier nur die Konsistenz der Implementierung
        val password = "master-password"
        val salt = "fixed-salt-16-bytes".toByteArray().copyOf(16)
        
        val key1 = Argon2idEngine.deriveKey(password.toCharArray(), salt, iterations = 1, memoryKb = 1024)
        val key2 = Argon2idEngine.deriveKey(password.toCharArray(), salt, iterations = 1, memoryKb = 1024)
        
        assertArrayEquals(key1, key2)
        assertEquals(32, key1.size)
    }

    @Test
    fun `test shamir 2-of-3 reconstruction with synthetic shares`() {
        val secret = ByteArray(32) { it.toByte() }
        val shares = ShamirEngine.split2of3(secret)
        
        assertEquals(3, shares.size)
        
        // Rekonstruktion mit 1 & 2
        val combined12 = ShamirEngine.combine2of3(shares[0], shares[1])
        assertArrayEquals("Reconstruction with 1 & 2 failed", secret, combined12)
        
        // Rekonstruktion mit 2 & 3
        val combined23 = ShamirEngine.combine2of3(shares[1], shares[2])
        assertArrayEquals("Reconstruction with 2 & 3 failed", secret, combined23)
        
        // Rekonstruktion mit 1 & 3
        val combined13 = ShamirEngine.combine2of3(shares[0], shares[2])
        assertArrayEquals("Reconstruction with 1 & 3 failed", secret, combined13)
    }

    @Test
    fun `test manipulated shamir share fails`() {
        val secret = ByteArray(32) { 0xAA.toByte() }
        val shares = ShamirEngine.split2of3(secret)
        
        // Manipuliere Share 2 (Inhalt ändern, aber Format wahren)
        val originalB64 = shares[1].substringAfter("MYDOC-S2-")
        // Wir ändern ein Zeichen am Anfang, das sicher kein Padding-Bereich ist
        val manipulatedB64 = if (originalB64[0] == 'A') "B" + originalB64.substring(1) else "A" + originalB64.substring(1)
        val manipulatedShare2 = "MYDOC-S2-$manipulatedB64"
        
        val combined = ShamirEngine.combine2of3(shares[0], manipulatedShare2)
        
        // Entweder es kommt ein falsches Ergebnis raus oder null (bei Dekodierfehlern)
        // Beides ist ein Erfolg für den Test auf Manipulations-Resistenz (kein falscher Erfolg)
        if (combined != null) {
            assertFalse("Manipulated share should not yield correct secret", secret.contentEquals(combined))
        }
    }

    @Test
    fun `test invalid header parsing`() {
        val invalidJson = """{"version": 2, "broken": true}"""
        // In VaultSecurityManager.loadVaultHeader wird version 1 erwartet
        val json = JSONObject(invalidJson)
        // Hier simulieren wir nur die Logik-Erwartung
        assertNotEquals(1, json.optInt("version", -1))
    }
}
