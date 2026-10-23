package com.example

import com.example.service.Argon2idEngine
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The first test preserves RFC 9106 section 5.3 as an upstream-library vector.
 * The second independently generated known-answer vector exercises the actual
 * Argon2idEngine configuration used by the persisted V2 vault.
 */
class Argon2idReferenceVectorTest {
    @Test
    fun `RFC 9106 Argon2id vector matches expected tag`() {
        val password = ByteArray(32) { 0x01 }
        val salt = ByteArray(16) { 0x02 }
        val secret = ByteArray(8) { 0x03 }
        val associatedData = ByteArray(12) { 0x04 }
        val output = ByteArray(32)
        try {
            val expected = "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(3)
                .withMemoryAsKB(32)
                .withParallelism(4)
                .withSalt(salt)
                .withSecret(secret)
                .withAdditional(associatedData)
                .build()
            Argon2BytesGenerator().apply { init(params) }
                .generateBytes(password, output, 0, output.size)
            assertEquals(expected, output.toHex())
        } finally {
            password.fill(0)
            salt.fill(0)
            secret.fill(0)
            associatedData.fill(0)
            output.fill(0)
        }
    }

    @Test
    fun `V2 vault Argon2id engine matches independent known answer vector`() {
        // Independently generated with libargon2 Argon2id v=19, t=2, m=19456 KiB,
        // p=1, output=32 bytes. Salt is 00..0f and password is UTF-8 below.
        val password = "V2-Argon2id-test-password".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        val expected = "41b940572ef8de133a01ac7b3863ee334bdd5073c608ac080c0fe8aa2ea0a365"
        var actual: ByteArray? = null
        try {
            actual = Argon2idEngine.deriveKey(
                password = password,
                salt = salt,
                iterations = 2,
                memoryKb = 19456,
                tagLength = 32
            )
            assertEquals(32, actual.size)
            assertEquals(expected, actual.toHex())
        } finally {
            password.fill('\u0000')
            salt.fill(0)
            actual?.fill(0)
        }
    }

    @Test
    fun `V2 vault Argon2id engine rejects weak and malformed parameters before work`() {
        val password = "Synthetic-vault-password".toCharArray()
        val salt = ByteArray(16) { it.toByte() }
        try {
            assertThrows(IllegalArgumentException::class.java) {
                Argon2idEngine.deriveKey(password, salt, iterations = 1, memoryKb = 19456)
            }
            assertThrows(IllegalArgumentException::class.java) {
                Argon2idEngine.deriveKey(password, salt, iterations = 2, memoryKb = 16384)
            }
            assertThrows(IllegalArgumentException::class.java) {
                Argon2idEngine.deriveKey(password, ByteArray(15), iterations = 2, memoryKb = 19456)
            }
            assertThrows(IllegalArgumentException::class.java) {
                Argon2idEngine.deriveKey(password, salt, iterations = 2, memoryKb = 19456, tagLength = 64)
            }
            assertThrows(IllegalArgumentException::class.java) {
                Argon2idEngine.deriveKey(charArrayOf(), salt, iterations = 2, memoryKb = 19456)
            }
        } finally {
            password.fill('\u0000')
            salt.fill(0)
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}
