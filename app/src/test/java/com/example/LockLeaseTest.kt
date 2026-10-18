package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.service.LockManager
import com.example.service.VaultRecoveryMethod
import com.example.service.VaultSecurityManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LockLeaseTest {
    @Test
    fun `lease is wiped after callback and no lease is issued while locked`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = VaultSecurityManager.getInstance(context)
        val setup = manager.initializeVault(
            masterPassword = "SyntheticLeasePassword2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE
        )
        val leaseAfterCallback = manager.withUnlockedKey { lease ->
            assertArrayEquals(setup.dek, lease)
            lease
        }
        assertTrue(leaseAfterCallback!!.all { it == 0.toByte() })
        manager.lockVault()
        assertNull(manager.withUnlockedKey { it.copyOf() })
        setup.dek.fill(0)
    }

    @Test
    fun `lock waits for active lease callback to finish`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = VaultSecurityManager.getInstance(context)
        manager.initializeVault(
            masterPassword = "SyntheticLeasePassword2026!",
            enableBiometrics = false,
            recoveryMethod = VaultRecoveryMethod.NONE
        )
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val lockFinished = AtomicBoolean(false)
        val worker = Thread {
            manager.withUnlockedKey {
                entered.countDown()
                release.await(3, TimeUnit.SECONDS)
                Unit
            }
        }
        worker.start()
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        val locker = Thread {
            manager.lockVault()
            lockFinished.set(true)
        }
        locker.start()
        Thread.sleep(100)
        assertTrue("Lock must wait while a bounded lease callback is active", !lockFinished.get())
        release.countDown()
        worker.join(3000)
        locker.join(3000)
        assertTrue(lockFinished.get())
        assertNull(manager.withUnlockedKey { it.copyOf() })
    }
}
