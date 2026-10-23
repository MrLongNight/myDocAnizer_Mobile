package com.example

import android.app.Application
import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultFactorType
import com.example.service.VaultSecurityTier
import com.example.service.isBiometricUnlockAllowed
import com.example.service.VaultSecurityManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.security.keystore.UserNotAuthenticatedException

@RunWith(AndroidJUnit4::class)
class VaultBiometricCryptoTest {
    @Test
    fun twoFactorVaultCannotEnrollOrUnlockViaBiometrics() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = VaultSecurityManager.getInstance(context)
        val password = "SyntheticTwoFactorBioPassword2026!"
        val keyFileToken = manager.generateKeyFileToken()
        val setup = manager.initializeVault(
            masterPassword = password,
            enableBiometrics = true,
            recoveryMethod = VaultRecoveryMethod.NONE,
            securityTier = VaultSecurityTier.TWO_FACTOR,
            factorType = VaultFactorType.PASSWORD_AND_KEYFILE,
            keyFileToken = keyFileToken
        )
        try {
            assertFalse(isBiometricUnlockAllowed(VaultSecurityTier.TWO_FACTOR))
            assertFalse(
                "Biometric slot must not be initialized in two-factor mode",
                manager.vaultHeader.value?.biometric2FaEnabled == true
            )
            assertNull("Enrollment cipher must be unavailable in two-factor mode",
                manager.createBiometricEnrollmentCipher())
            assertNull("Unlock cipher must be unavailable in two-factor mode",
                manager.createBiometricUnlockCipher())

            val softwareCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(ByteArray(32) { 0x11 }, "AES"),
                    GCMParameterSpec(128, ByteArray(12) { 0x22 })
                )
            }
            assertFalse("Service-level enrollment must also fail closed",
                manager.completeBiometricEnrollment(softwareCipher))
            assertFalse("Service-level biometric unlock must also fail closed",
                manager.completeBiometricUnlock(softwareCipher))

            manager.lockVault()
            assertFalse("Password alone must not open two-factor vault",
                manager.unlockVault(password = password))
            assertTrue("Both required factors must still open the vault",
                manager.unlockVault(password = password, keyFileToken = keyFileToken))
        } finally {
            setup.dek.fill(0)
            manager.lockVault()
        }
    }

    @Test
    fun biometricConveniencePolicyIsSingleFactorOnly() {
        assertTrue(isBiometricUnlockAllowed(VaultSecurityTier.ONE_FACTOR))
        assertFalse(isBiometricUnlockAllowed(VaultSecurityTier.TWO_FACTOR))
        assertFalse(isBiometricUnlockAllowed(VaultSecurityTier.UNENCRYPTED))
    }

    @Test
    fun keystoreCipherCannotEncryptDekBeforeBiometricAuthentication() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assumeTrue(
            "Requires enrolled strong biometric authentication",
            BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
        )
        val manager = VaultSecurityManager.getInstance(context)
        val setup = manager.initializeVault(
            masterPassword = "SyntheticBiometricPassword2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE
        )
        val cipher = manager.createBiometricEnrollmentCipher()
        assertNotNull("Keystore must create an auth-bound Cipher", cipher)
        try {
            cipher!!.doFinal(setup.dek)
            throw AssertionError("Expected Keystore to reject unauthenticated key use")
        } catch (_: UserNotAuthenticatedException) {
            // Expected: the key is bound to the actual BiometricPrompt CryptoObject.
        } finally {
            setup.dek.fill(0)
        }
    }
}
