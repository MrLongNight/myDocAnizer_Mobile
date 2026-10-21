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
    fun `stored Shamir hashes enforce the exact returned tokens and reject tampering`() {
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

        val pair12 = manager.recoverDekWithShamir(returnedShares[0].token, returnedShares[1].token)
        val pair13 = manager.recoverDekWithShamir(returnedShares[0].token, returnedShares[2].token)
        val pair23 = manager.recoverDekWithShamir(returnedShares[1].token, returnedShares[2].token)
        assertArrayEquals(setup.dek, pair12)
        assertArrayEquals(setup.dek, pair13)
        assertArrayEquals(setup.dek, pair23)
        pair12?.fill(0)
        pair13?.fill(0)
        pair23?.fill(0)

        val validShare = returnedShares[0].token
        val changedShare = validShare.dropLast(1) + if (validShare.last() == 'A') "B" else "A"
        assertNull(
            "A modified share must not pass the persisted hash check",
            manager.recoverDekWithShamir(changedShare, returnedShares[1].token)
        )
        assertNull(
            "Two copies of one share are not a valid pair",
            manager.recoverDekWithShamir(returnedShares[0].token, returnedShares[0].token)
        )
    }

    @Test
    fun `Shamir share rotation keeps the old set until a verified replacement is activated`() {
        val manager = VaultSecurityManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
        val setup = manager.initializeVault(
            masterPassword = "ShamirRotationTestPassword2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.SHAMIR_2_OF_3
        )
        val oldShares = setup.shamirShares ?: error("Expected original shares")
        val pendingShares = manager.splitSecretShamir2of3()

        assertNull(
            "Unregistered replacement shares must be rejected before activation",
            manager.recoverDekWithShamir(pendingShares[0].token, pendingShares[1].token)
        )
        val oldRecovered = manager.recoverDekWithShamir(oldShares[0].token, oldShares[1].token)
        assertArrayEquals(setup.dek, oldRecovered)
        oldRecovered?.fill(0)

        assertTrue("A complete replacement set for the current DEK should activate",
            manager.activateShamirRecoveryShares(pendingShares))
        assertNull("Old shares must stop working after successful activation",
            manager.recoverDekWithShamir(oldShares[0].token, oldShares[1].token))
        val newRecovered = manager.recoverDekWithShamir(pendingShares[0].token, pendingShares[1].token)
        assertArrayEquals(setup.dek, newRecovered)
        newRecovered?.fill(0)
    }
}
