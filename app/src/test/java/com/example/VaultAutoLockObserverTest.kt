package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.service.VaultAutoLockObserver
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import com.example.service.LockManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VaultAutoLockObserverTest {
    @After
    fun cleanupVaultSession() {
        VaultSecurityManager.getInstance(ApplicationProvider.getApplicationContext<Application>()).lockVault()
        LockManager.resetForTests()
    }
    @Test
    fun `zero timeout locks immediately on background`() {
        val manager = VaultSecurityManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
        manager.initializeVault("SyntheticAutoLockPassword2026!", false, VaultRecoveryMethod.NONE)
        manager.setAutoLockMinutes(0)
        var now = 100L
        var locks = 0
        val observer = VaultAutoLockObserver(manager, { now }, { locks++ })
        observer.onBackground()
        assertEquals(1, locks)
        observer.onForeground()
        assertEquals("No duplicate lock after immediate timeout", 1, locks)
    }

    @Test
    fun `positive timeout locks only at or after monotonic boundary`() {
        val manager = VaultSecurityManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
        manager.initializeVault("SyntheticAutoLockPassword2026!", false, VaultRecoveryMethod.NONE)
        manager.setAutoLockMinutes(1)
        var now = 1_000L
        var locks = 0
        val observer = VaultAutoLockObserver(manager, { now }, { locks++ })
        observer.onBackground()
        now += 59_999L
        observer.onForeground()
        assertEquals(0, locks)

        observer.onBackground()
        now += 60_000L
        observer.onForeground()
        assertEquals(1, locks)
    }

    @Test
    fun `auto lock duration rejects out of range values`() {
        val manager = VaultSecurityManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
        assertThrows(IllegalArgumentException::class.java) { manager.setAutoLockMinutes(-1) }
        assertThrows(IllegalArgumentException::class.java) { manager.setAutoLockMinutes(1441) }
        assertTrue(manager.getAutoLockMinutes() in 0..1440)
    }
}
