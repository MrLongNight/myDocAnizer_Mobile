package com.example

import com.example.service.RecoveryPayloadEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class RecoveryPayloadEngineTest {
    private val vaultId = "VAULT-TEST1234"

    @Test
    fun `v2 payload roundtrips exact 256 bit DEK`() {
        val dek = ByteArray(32) { it.toByte() }
        val passphrase = "A long recovery passphrase with sufficient entropy".toCharArray()
        val encoded = RecoveryPayloadEngine.encodeRecoveryPayload(dek, passphrase, vaultId)
        val payload = RecoveryPayloadEngine.RecoveryPayload.deserialize(encoded)

        assertEquals(2, payload.version)
        assertEquals("AES-256-GCM-Argon2id", payload.algorithm)
        assertEquals(vaultId, payload.vaultId)
        assertEquals(128, payload.tagBits)
        assertEquals(16, payload.salt.size)
        assertEquals(12, payload.iv.size)
        assertEquals(48, payload.ciphertext.size)
        assertArrayEquals(dek, RecoveryPayloadEngine.decodeRecoveryPayload(encoded, passphrase, vaultId))
    }

    @Test
    fun `wrong passphrase fails closed`() {
        val encoded = RecoveryPayloadEngine.encodeRecoveryPayload(
            ByteArray(32) { 7 }, "Valid recovery passphrase".toCharArray(), vaultId
        )
        assertThrows(SecurityException::class.java) {
            RecoveryPayloadEngine.decodeRecoveryPayload(encoded, "wrong passphrase".toCharArray(), vaultId)
        }
    }

    @Test
    fun `wrong vault ID fails closed`() {
        val encoded = RecoveryPayloadEngine.encodeRecoveryPayload(
            ByteArray(32) { 8 }, "Valid recovery passphrase".toCharArray(), vaultId
        )
        assertThrows(SecurityException::class.java) {
            RecoveryPayloadEngine.decodeRecoveryPayload(
                encoded, "Valid recovery passphrase".toCharArray(), "VAULT-OTHER123"
            )
        }
    }

    @Test
    fun `modified ciphertext fails authentication`() {
        val encoded = RecoveryPayloadEngine.encodeRecoveryPayload(
            ByteArray(32) { 9 }, "Valid recovery passphrase".toCharArray(), vaultId
        )
        val payload = RecoveryPayloadEngine.RecoveryPayload.deserialize(encoded)
        val changed = payload.copy(ciphertext = payload.ciphertext.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() })
        assertThrows(SecurityException::class.java) {
            RecoveryPayloadEngine.decodeRecoveryPayload(changed.serialize(), "Valid recovery passphrase".toCharArray(), vaultId)
        }
    }

    @Test
    fun `rejects trailing bytes and unsupported versions`() {
        val encoded = RecoveryPayloadEngine.encodeRecoveryPayload(
            ByteArray(32) { 10 }, "Valid recovery passphrase".toCharArray(), vaultId
        )
        // JSON parsers must not silently accept concatenated/trailing payload bytes.
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPayloadEngine.RecoveryPayload.deserialize(encoded + byteArrayOf(0x41))
        }
        val payload = RecoveryPayloadEngine.RecoveryPayload.deserialize(encoded)
        assertThrows(IllegalArgumentException::class.java) {
            payload.copy(version = 1).serialize()
        }
    }

    @Test
    fun `rejects non 256 bit DEKs and invalid vault IDs`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPayloadEngine.encodeRecoveryPayload(ByteArray(16), "passphrase".toCharArray(), vaultId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPayloadEngine.encodeRecoveryPayload(ByteArray(32), "passphrase".toCharArray(), "invalid")
        }
    }
}
