package com.example

import android.app.Application
import android.util.Log
import com.example.service.AppAuditLogger
import com.example.service.LogCategory
import com.example.service.VaultAutoLockObserver
import com.example.service.VaultSecurityManager
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Anwendungs-Klasse für myDocAnizer-Mobile:
 * Initialisiert zentrale Audit-Dienste und sichert die App gegen ungefangene Abstürze ab.
 */
class DocAnizerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            AppAuditLogger.init(this)
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                VaultAutoLockObserver(VaultSecurityManager.getInstance(this))
            )
            try {
                com.example.ui.components.SplashSoundSynthesizer.precompute(this)
            } catch (_: Throwable) {}
            
            // Globaler Crash-Guard & Logging für maximale Stabilität
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Log.e("DocAnizerApp", "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
                try {
                    AppAuditLogger.log(
                        category = LogCategory.ERROR,
                        tag = "CrashGuard",
                        message = "Unerwarteter Fehler abgefangen: ${throwable.localizedMessage}",
                        details = Log.getStackTraceString(throwable)
                    )
                } catch (_: Throwable) {}
                defaultHandler?.uncaughtException(thread, throwable)
            }
        } catch (t: Throwable) {
            Log.e("DocAnizerApp", "Fehler bei App-Initialisierung: ${t.message}", t)
        }
    }
}
