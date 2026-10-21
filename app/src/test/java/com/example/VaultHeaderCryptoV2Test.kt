package com.example

import com.example.service.VaultHeaderCryptoV2
import com.example.service.VaultHeaderParserV2
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VaultHeaderCryptoV2Test {
    private val vaultId = "VAULT-TEST1234"
    private val password = "Vault header test password".toCharArray()
    private val dek = ByteArray(32) { (it * 7).toByte() }

    private fun newHeader() = VaultHeaderCryptoV2.createHeader(
        vaultId = vaultId,
        createdAt = 1729177200000L,
        password = password,
        dek = dek
    )

    @Test
    fun `v2 header wraps and unwraps DEK`() {
        val header = newHeader()
        assertNotNull(VaultHeaderParserV2.parse(header.toJson()))
        assertEquals(48, android.util.Base64.decode(header.encryptedDekBase64, android.util.Base64.NO_WRAP).size)
        assertArrayEquals(dek, VaultHeaderCryptoV2.unwrapDek(header, password))
    }

    @Test
    fun `two factor v2 requires the correct password and key file`() {
        val token = "a".repeat(64).toCharArray()
        val header = VaultHeaderCryptoV2.createHeader(
            vaultId = vaultId,
            createdAt = 1729177200000L,
            password = password,
            dek = dek,
            securityTier = "TWO_FACTOR",
            factorType = "PASSWORD_AND_KEYFILE",
            keyFileToken = token
        )

        assertArrayEquals(dek, VaultHeaderCryptoV2.unwrapDek(header, password, token))
        assertArrayEquals(
            "Hex key-file tokens must be case-insensitive",
            dek,
            VaultHeaderCryptoV2.unwrapDek(header, password, "A".repeat(64).toCharArray())
        )

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(header, password)
        }
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(header, password, "b".repeat(64).toCharArray())
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            VaultHeaderCryptoV2.createHeader(
                vaultId = vaultId,
                createdAt = 1729177200000L,
                password = password,
                dek = dek,
                securityTier = "TWO_FACTOR",
                factorType = "PASSWORD_AND_KEYFILE"
            )
        }
    }

    @Test
    fun `downgrading v2 two factor metadata does not enable password only unlock`() {
        val token = "d".repeat(64).toCharArray()
        val header = VaultHeaderCryptoV2.createHeader(
            vaultId = vaultId,
            createdAt = 1729177200000L,
            password = password,
            dek = dek,
            securityTier = "TWO_FACTOR",
            factorType = "PASSWORD_AND_KEYFILE",
            keyFileToken = token
        )
        val downgraded = header.copy(
            securityTier = "ONE_FACTOR",
            factorType = "PASSWORD"
        )

        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(downgraded, password)
        }
    }

    @Test
    fun `one factor v2 cannot silently ignore a supplied key file`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            VaultHeaderCryptoV2.createHeader(
                vaultId = vaultId,
                createdAt = 1729177200000L,
                password = password,
                dek = dek,
                keyFileToken = "c".repeat(64).toCharArray()
            )
        }
    }

    @Test
    fun `each header gets a fresh random IV`() {
        val first = newHeader()
        val second = newHeader()
        assertFalse(first.ivBase64 == second.ivBase64)
        assertFalse(first.saltBase64 == second.saltBase64)
    }

    @Test
    fun `wrong password is rejected`() {
        val header = newHeader()
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(header, "wrong password".toCharArray())
        }
    }

    @Test
    fun `authenticated metadata tampering is rejected`() {
        val header = newHeader()
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(header.copy(vaultId = "VAULT-OTHER123"), password)
        }
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(header.copy(autoLockMinutes = 10), password)
        }
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(header.copy(argon2MemoryKb = 32768), password)
        }
    }

    @Test
    fun `tampering with security metadata is rejected by the DEK-derived authenticator`() {
        val header = newHeader()
        assertEquals(1, header.metadataAuthVersion)
        assertTrue(!header.metadataAuthBase64.isNullOrBlank())

        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(header.copy(domainSecurityTier = "UNENCRYPTED"), password)
        }
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(header.copy(emergencyPdfChecksum = "a".repeat(64)), password)
        }
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(header.copy(keyFileTokenHash = "b".repeat(64)), password)
        }

        val alteredMac = android.util.Base64.decode(requireNotNull(header.metadataAuthBase64), android.util.Base64.NO_WRAP)
        alteredMac[0] = (alteredMac[0].toInt() xor 1).toByte()
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(
                header.copy(
                    metadataAuthBase64 = android.util.Base64.encodeToString(alteredMac, android.util.Base64.NO_WRAP)
                ),
                password
            )
        }
        alteredMac.fill(0)
    }

    @Test
    fun `removing the metadata authenticator cannot downgrade a signed header`() {
        val signed = newHeader()
        val stripped = signed.copy(metadataAuthVersion = 0, metadataAuthBase64 = null)
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(stripped, password)
        }
        // Even the legacy migration path must still authenticate the original DEK wrapper;
        // this header was created with the new AAD marker and is not a genuine legacy header.
        org.junit.Assert.assertThrows(SecurityException::class.java) {
            VaultHeaderCryptoV2.unwrapDek(stripped, password, allowLegacyMetadataUpgrade = true)
        }
    }

    @Test
    fun `unsupported weak KDF parameters are rejected before derivation`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            VaultHeaderCryptoV2.createHeader(
                vaultId = vaultId,
                createdAt = 1729177200000L,
                password = password,
                dek = dek,
                memoryKb = 16384
            )
        }
        val header = newHeader().copy(argon2Iterations = 1)
        assertNull(VaultHeaderParserV2.parse(header.toJson()))
    }

    @Test
    fun `encrypted DEK length must be exact`() {
        val header = newHeader()
        val shortened = header.copy(
            encryptedDekBase64 = android.util.Base64.encodeToString(
                ByteArray(32), android.util.Base64.NO_WRAP
            )
        )
        assertNull(VaultHeaderParserV2.parse(shortened.toJson()))
    }
}
