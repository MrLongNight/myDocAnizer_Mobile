package com.example

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RFC 9106 section 5.3 Argon2id test vector. This exercises the pinned
 * Bouncy Castle implementation and guards against accidental API/config changes.
 */
class Argon2idReferenceVectorTest {
    @Test
    fun `RFC 9106 Argon2id vector matches expected tag`() {
        val password = ByteArray(32) { 0x01 }
        val salt = ByteArray(16) { 0x02 }
        val secret = ByteArray(8) { 0x03 }
        val associatedData = ByteArray(12) { 0x04 }
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
        val output = ByteArray(32)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(password, output, 0, output.size)
        assertEquals(expected, output.joinToString("") { "%02x".format(it) })
        password.fill(0)
        secret.fill(0)
        associatedData.fill(0)
        output.fill(0)
    }
}
