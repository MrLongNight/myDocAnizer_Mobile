package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.service.VaultFactorType
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import com.example.service.VaultSecurityTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VaultTwoFactorSecurityTest {
    // Robolectric creates isolated application contexts per test class, while the manager is a process singleton.
    // Reset it so one test cannot retain another test's SharedPreferences/context.
    @Before
    fun resetVaultManagerBeforeTest() = resetVaultManagerSingleton()

    @After
    fun resetVaultManagerAfterTest() = resetVaultManagerSingleton()

    private fun resetVaultManagerSingleton() {
        val instanceField = VaultSecurityManager::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        instanceField.set(null, null)
    }

    @Test
    fun passwordAndKeyFileAreBothRequiredToUnlock() {
        val manager = VaultSecurityManager.getInstance(
            ApplicationProvider.getApplicationContext<Application>()
        )
        val password = "TwoFactorTestPassword2026!"
        val keyFileToken = manager.generateKeyFileToken()
        manager.lockVault()

        val setup = manager.initializeVault(
            masterPassword = password,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE,
            securityTier = VaultSecurityTier.TWO_FACTOR,
            factorType = VaultFactorType.PASSWORD_AND_KEYFILE,
            keyFileToken = keyFileToken
        )
        setup.dek.fill(0)

        assertTrue("Both correct factors must unlock", manager.unlockVault(password, keyFileToken))
        manager.lockVault()

        assertFalse("Password alone must not unlock", manager.unlockVault(password = password))
        assertTrue("Correct factors should still work after a failed attempt", manager.unlockVault(password, keyFileToken))
        manager.lockVault()

        assertFalse("Key file alone must not unlock", manager.unlockVault(keyFileToken = keyFileToken))
        assertTrue("Correct factors should still work after a failed attempt", manager.unlockVault(password, keyFileToken))
        manager.lockVault()

        val wrongToken = manager.generateKeyFileToken()
        assertFalse("Wrong key file must not unlock", manager.unlockVault(password, wrongToken))
        assertTrue("Correct factors should still work after a failed attempt", manager.unlockVault(password, keyFileToken))
        manager.lockVault()
    }

    @Test
    fun twoFactorSetupRejectsTotpAsCryptographicFactor() {
        val manager = VaultSecurityManager.getInstance(
            ApplicationProvider.getApplicationContext<Application>()
        )
        assertThrows(IllegalArgumentException::class.java) {
            manager.initializeVault(
                masterPassword = "TwoFactorTestPassword2026!",
                enableBiometrics = false,
                recoveryMethod = VaultRecoveryMethod.NONE,
                securityTier = VaultSecurityTier.TWO_FACTOR,
                factorType = VaultFactorType.PASSWORD_AND_TOTP,
                totpSecret = manager.generateTotpSecret()
            )
        }
    }

    @Test
    fun twoFactorSetupRejectsMissingOrMalformedKeyFile() {
        val manager = VaultSecurityManager.getInstance(
            ApplicationProvider.getApplicationContext<Application>()
        )
        assertThrows(IllegalArgumentException::class.java) {
            manager.initializeVault(
                masterPassword = "TwoFactorTestPassword2026!",
                enableBiometrics = false,
                recoveryMethod = VaultRecoveryMethod.NONE,
                securityTier = VaultSecurityTier.TWO_FACTOR,
                factorType = VaultFactorType.PASSWORD_AND_KEYFILE,
                keyFileToken = "not-a-256-bit-key"
            )
        }
    }
}
