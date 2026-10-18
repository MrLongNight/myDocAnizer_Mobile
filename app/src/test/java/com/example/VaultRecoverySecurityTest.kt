package com.example

import android.app.Application
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import java.security.MessageDigest
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VaultRecoverySecurityTest {
    @Test
    fun `BIP39 official zero entropy mnemonic validates and checksum corruption fails`() {
        val manager = VaultSecurityManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
        assertEquals(2048, com.example.service.Bip39Engine.BIP39_WORD_LIST.size)
        val officialZeroEntropy = List(11) { "abandon" } + "about"
        assertTrue(manager.validateBip39Mnemonic(officialZeroEntropy))
        assertFalse(manager.validateBip39Mnemonic(List(12) { "abandon" }))
    }

    @Test
    fun `stored Shamir share hashes match the exact shares returned to caller`() {
        val manager = VaultSecurityManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
        val setup = manager.initializeVault(
            masterPassword = "ShamirRecoveryTestPassword2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.SHAMIR_2_OF_3
        )
        val returnedShares = setup.shamirShares ?: error("Expected generated Shamir shares")
        val storedHashes = manager.vaultHeader.value?.shamirShareHashes ?: error("Expected persisted share hashes")
        val expectedHashes = returnedShares.map { share ->
            MessageDigest.getInstance("SHA-256")
                .digest(share.token.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
        assertEquals(3, returnedShares.size)
        assertEquals(expectedHashes, storedHashes)
        assertTrue(returnedShares.all { it.token.startsWith("MYDOC-S") })
    }
}
