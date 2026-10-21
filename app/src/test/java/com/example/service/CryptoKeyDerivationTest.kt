package com.example.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoKeyDerivationTest {
    @Test
    fun hkdfSha256MatchesRfc5869TestCaseOne() {
        val ikm = ByteArray(22) { 0x0b.toByte() }
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expected = hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865"
        )

        assertArrayEquals(expected, CryptoKeyDerivation.derive(ikm, salt, info, 42))
    }

    @Test
    fun vaultSubkeysAreStableButSeparatedByVaultAndPurpose() {
        val dek = ByteArray(32) { (it + 1).toByte() }
        val database = CryptoKeyDerivation.deriveVaultSubkey(
            dek, "vault-a", CryptoKeyDerivation.Purpose.DATABASE
        )

        assertArrayEquals(
            database,
            CryptoKeyDerivation.deriveVaultSubkey(dek, "vault-a", CryptoKeyDerivation.Purpose.DATABASE)
        )
        assertFalse(database.contentEquals(
            CryptoKeyDerivation.deriveVaultSubkey(dek, "vault-a", CryptoKeyDerivation.Purpose.DOCUMENT_STREAM)
        ))
        assertFalse(database.contentEquals(
            CryptoKeyDerivation.deriveVaultSubkey(dek, "vault-b", CryptoKeyDerivation.Purpose.DATABASE)
        ))
    }

    @Test
    fun invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CryptoKeyDerivation.derive(byteArrayOf(), byteArrayOf(1), byteArrayOf(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CryptoKeyDerivation.derive(byteArrayOf(1), byteArrayOf(), byteArrayOf(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CryptoKeyDerivation.deriveVaultSubkey(ByteArray(16), "vault-a", CryptoKeyDerivation.Purpose.DATABASE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CryptoKeyDerivation.deriveVaultSubkey(ByteArray(32), " ", CryptoKeyDerivation.Purpose.DATABASE)
        }
    }

    private fun hex(value: String): ByteArray {
        require(value.length % 2 == 0)
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
