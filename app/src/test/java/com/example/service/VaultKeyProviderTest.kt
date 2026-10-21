package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultKeyProviderTest {
    @Test
    fun derivesPurposeScopedKeyOnlyInsideLeaseAndWipesDerivedKey() {
        val source = FakeLeaseSource("VAULT-TEST-01", ByteArray(32) { (it + 1).toByte() })
        val provider = VaultKeyProvider(source)
        var leasedKey: ByteArray? = null

        val result = provider.withKey(CryptoKeyDerivation.Purpose.DATABASE) { key ->
            leasedKey = key
            key.copyOf()
        }

        assertEquals(32, result?.size)
        assertTrue(source.leaseIssued)
        assertTrue(leasedKey!!.all { it == 0.toByte() })
        result!!.fill(0)
    }

    @Test
    fun separatesPurposesAndVaults() {
        val dek = ByteArray(32) { (it + 1).toByte() }
        val db = VaultKeyProvider(FakeLeaseSource("VAULT-TEST-01", dek))
            .withKey(CryptoKeyDerivation.Purpose.DATABASE) { it.copyOf() }!!
        val settings = VaultKeyProvider(FakeLeaseSource("VAULT-TEST-01", dek))
            .withKey(CryptoKeyDerivation.Purpose.SETTINGS) { it.copyOf() }!!
        val otherVault = VaultKeyProvider(FakeLeaseSource("VAULT-TEST-02", dek))
            .withKey(CryptoKeyDerivation.Purpose.DATABASE) { it.copyOf() }!!

        assertFalse(db.contentEquals(settings))
        assertFalse(db.contentEquals(otherVault))
        db.fill(0)
        settings.fill(0)
        otherVault.fill(0)
        dek.fill(0)
    }

    @Test
    fun missingVaultOrLockedLeaseFailsClosed() {
        val dek = ByteArray(32) { 7 }
        assertNull(
            VaultKeyProvider(FakeLeaseSource(null, dek))
                .withKey(CryptoKeyDerivation.Purpose.DATABASE) { it.size }
        )
        val locked = FakeLeaseSource("VAULT-TEST-01", dek).apply { locked = true }
        assertNull(
            VaultKeyProvider(locked)
                .withKey(CryptoKeyDerivation.Purpose.DATABASE) { it.size }
        )
        assertFalse(locked.leaseIssued)
        dek.fill(0)
    }

    @Test
    fun callbackExceptionStillWipesDerivedKey() {
        val source = FakeLeaseSource("VAULT-TEST-01", ByteArray(32) { 9 })
        var leasedKey: ByteArray? = null

        try {
            VaultKeyProvider(source).withKey(CryptoKeyDerivation.Purpose.BACKUP_CONTAINER) {
                leasedKey = it
                error("synthetic failure")
            }
        } catch (_: IllegalStateException) {
            // Expected synthetic callback failure.
        }

        assertTrue(leasedKey!!.all { it == 0.toByte() })
    }

    private class FakeLeaseSource(
        private val vaultId: String?,
        private val dek: ByteArray
    ) : VaultDekLeaseSource {
        var locked: Boolean = false
        var leaseIssued: Boolean = false

        override fun currentVaultId(): String? = vaultId

        override fun <T> withUnlockedKey(operation: (ByteArray) -> T): T? {
            if (locked || dek.size != 32) return null
            leaseIssued = true
            val lease = dek.copyOf()
            return try {
                operation(lease)
            } finally {
                lease.fill(0)
            }
        }
    }
}
