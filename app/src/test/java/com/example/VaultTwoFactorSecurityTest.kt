package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.AuthenticationBackoffManager
import com.example.service.VaultFactorType
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import com.example.service.VaultSecurityTier
import org.junit.Assert.assertEquals
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
        val previous = instanceField.get(null) as? VaultSecurityManager
        runCatching { previous?.lockVault() }
        instanceField.set(null, null)
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.getSharedPreferences(
            AuthenticationBackoffManager.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit().clear().commit()
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
    fun vaultUnlockFailureThresholdBlocksEvenAValidPasswordUntilBackoffExpires() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = VaultSecurityManager.getInstance(context)
        val password = "VaultBackoffIntegrationPassword2026!"
        val setup = manager.initializeVault(
            masterPassword = password,
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE,
            securityTier = VaultSecurityTier.ONE_FACTOR,
            factorType = VaultFactorType.PASSWORD
        )
        val backoffPrefs = context.getSharedPreferences(
            AuthenticationBackoffManager.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        try {
            setup.dek.fill(0)
            manager.lockVault()

            repeat(5) { attempt ->
                assertFalse(
                    "Wrong password attempt ${attempt + 1} must fail",
                    manager.unlockVault(password = "IncorrectIntegrationPassword$attempt")
                )
            }
            assertEquals(5, backoffPrefs.getInt("failure_count", 0))

            assertFalse(
                "A correct password must still be rejected while the real vault backoff is active",
                manager.unlockVault(password = password)
            )
            assertEquals(
                "A blocked request must not increment the failure count",
                5,
                backoffPrefs.getInt("failure_count", 0)
            )
            assertFalse("Failed/blocked authentication must not leave the Vault unlocked", manager.isUnlocked())
        } finally {
            setup.dek.fill(0)
            manager.lockVault()
        }
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
