package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppSessionLockState { LOCKED, UNLOCKED }
enum class VaultSessionLockState { NOT_CONFIGURED, LOCKED, UNLOCKED }

data class LockSnapshot(
    val app: AppSessionLockState,
    val vault: VaultSessionLockState
)

/**
 * Single in-process source of truth for app-session and vault-session lock state.
 * Process initialization is fail-closed for an existing vault.
 */
object LockManager {
    private val _snapshot = MutableStateFlow(
        LockSnapshot(AppSessionLockState.LOCKED, VaultSessionLockState.NOT_CONFIGURED)
    )
    val snapshot: StateFlow<LockSnapshot> = _snapshot.asStateFlow()

    private val _appUnlocked = MutableStateFlow(false)
    val appUnlocked: StateFlow<Boolean> = _appUnlocked.asStateFlow()

    @Volatile private var initialized = false

    @Synchronized
    fun initialize(vaultConfigured: Boolean, appLockRequired: Boolean, debugBuild: Boolean) {
        if (initialized) return
        val appState = if (!appLockRequired || debugBuild) {
            AppSessionLockState.UNLOCKED
        } else {
            AppSessionLockState.LOCKED
        }
        val vaultState = if (vaultConfigured) VaultSessionLockState.LOCKED else VaultSessionLockState.NOT_CONFIGURED
        _snapshot.value = LockSnapshot(appState, vaultState)
        _appUnlocked.value = appState == AppSessionLockState.UNLOCKED
        initialized = true
    }

    @Synchronized
    fun onVaultUnlocked() {
        _snapshot.value = _snapshot.value.copy(vault = VaultSessionLockState.UNLOCKED)
    }

    @Synchronized
    fun onVaultLocked() {
        val current = _snapshot.value
        if (current.vault != VaultSessionLockState.NOT_CONFIGURED) {
            _snapshot.value = current.copy(vault = VaultSessionLockState.LOCKED)
        }
    }

    @Synchronized
    fun onAppUnlockedAfterAuthentication() {
        _snapshot.value = _snapshot.value.copy(app = AppSessionLockState.UNLOCKED)
        _appUnlocked.value = true
    }

    @Synchronized
    fun onAppLocked() {
        _snapshot.value = _snapshot.value.copy(app = AppSessionLockState.LOCKED)
        _appUnlocked.value = false
    }

    fun isVaultUnlocked(): Boolean = _snapshot.value.vault == VaultSessionLockState.UNLOCKED

    @Synchronized
    internal fun resetForTests() {
        _snapshot.value = LockSnapshot(AppSessionLockState.LOCKED, VaultSessionLockState.NOT_CONFIGURED)
        _appUnlocked.value = false
        initialized = false
    }
}
