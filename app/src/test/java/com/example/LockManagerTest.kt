package com.example

import com.example.service.AppSessionLockState
import com.example.service.LockManager
import com.example.service.VaultSessionLockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockManagerTest {
    @Test
    fun `configured vault starts locked even when debug app shell is unlocked`() {
        LockManager.resetForTests()
        LockManager.initialize(vaultConfigured = true, appLockRequired = false, debugBuild = true)
        assertEquals(VaultSessionLockState.LOCKED, LockManager.snapshot.value.vault)
        assertEquals(AppSessionLockState.UNLOCKED, LockManager.snapshot.value.app)
        assertFalse(LockManager.isVaultUnlocked())
    }

    @Test
    fun `vault state only becomes unlocked after explicit verified transition`() {
        LockManager.resetForTests()
        LockManager.initialize(vaultConfigured = true, appLockRequired = true, debugBuild = false)
        assertFalse(LockManager.appUnlocked.value)
        LockManager.onVaultUnlocked()
        LockManager.onAppUnlockedAfterAuthentication()
        assertTrue(LockManager.isVaultUnlocked())
        assertTrue(LockManager.appUnlocked.value)
        LockManager.onVaultLocked()
        LockManager.onAppLocked()
        assertFalse(LockManager.isVaultUnlocked())
        assertFalse(LockManager.appUnlocked.value)
        assertEquals(VaultSessionLockState.LOCKED, LockManager.snapshot.value.vault)
    }
}
