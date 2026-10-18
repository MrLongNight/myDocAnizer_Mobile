package com.example.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Applies the configured vault timeout to the process lifecycle.
 * Time is measured only with monotonic elapsedRealtime, never wall-clock time.
 */
class VaultAutoLockObserver(
    private val vaultSecurityManager: VaultSecurityManager,
    private val nowElapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
    private val lockAction: () -> Unit = {
        vaultSecurityManager.lockVault()
        LockManager.onAppLocked()
    }
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private var backgroundStartedAt: Long? = null
    private var pendingLock: Runnable? = null

    override fun onStop(owner: LifecycleOwner) = onBackground()

    override fun onStart(owner: LifecycleOwner) = onForeground()

    internal fun onBackground() {
        cancelPending()
        val minutes = configuredMinutes()
        backgroundStartedAt = nowElapsedRealtime()
        if (minutes <= 0) {
            lockAction()
            return
        }
        val timeoutMs = minutes.toLong() * MILLIS_PER_MINUTE
        val runnable = object : Runnable {
            override fun run() {
                val startedAt = backgroundStartedAt ?: return
                val elapsed = (nowElapsedRealtime() - startedAt).coerceAtLeast(0L)
                if (elapsed >= timeoutMs) {
                    lockAction()
                    pendingLock = null
                } else {
                    handler.postDelayed(this, timeoutMs - elapsed)
                }
            }
        }
        pendingLock = runnable
        handler.postDelayed(runnable, timeoutMs)
    }

    internal fun onForeground() {
        val startedAt = backgroundStartedAt ?: return
        val minutes = configuredMinutes()
        val elapsed = (nowElapsedRealtime() - startedAt).coerceAtLeast(0L)
        val timeoutMs = minutes.toLong().coerceAtLeast(0L) * MILLIS_PER_MINUTE
        cancelPending()
        backgroundStartedAt = null
        if (minutes <= 0 || elapsed >= timeoutMs) lockAction()
    }

    private fun configuredMinutes(): Int =
        vaultSecurityManager.vaultHeader.value?.autoLockMinutes ?: DEFAULT_TIMEOUT_MINUTES

    private fun cancelPending() {
        pendingLock?.let(handler::removeCallbacks)
        pendingLock = null
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val DEFAULT_TIMEOUT_MINUTES = 5
    }
}
