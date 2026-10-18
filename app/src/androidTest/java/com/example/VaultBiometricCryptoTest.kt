package com.example

import android.app.Application
import androidx.biometric.BiometricManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.Cipher
import javax.crypto.UserNotAuthenticatedException

@RunWith(AndroidJUnit4::class)
class VaultBiometricCryptoTest {
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
