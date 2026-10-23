package com.example.service

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Privacy-safe scanner diagnostics written both to Android Logcat and the
 * in-app AppAuditLogger export. High-frequency frame/readiness events are
 * sampled so the audit history remains useful instead of filling instantly.
 */
object DocumentScanTrace {
    private const val TAG = "DocScanTrace"
    private const val SAMPLE_INTERVAL_MS = 2_000L
    private val lastLoggedAt = ConcurrentHashMap<String, Long>()

    fun info(message: String) = write(Log.INFO, message)
    fun warn(message: String) = write(Log.WARN, message)
    fun error(message: String) = write(Log.ERROR, message)

    private fun write(priority: Int, message: String) {
        val event = EVENT_REGEX.find(message)?.groupValues?.getOrNull(1) ?: "unknown"
        val now = SystemClock.elapsedRealtime()
        val sampled = event == "frame" || event == "auto_readiness"
        if (sampled) {
            val previous = lastLoggedAt.put(event, now)
            if (previous != null && now - previous < SAMPLE_INTERVAL_MS) return
        }

        when (priority) {
            Log.ERROR -> Log.e(TAG, message)
            Log.WARN -> Log.w(TAG, message)
            else -> Log.i(TAG, message)
        }

        // Keep the in-app exported audit log in sync with scanner diagnostics.
        // Only camera state/quality metadata is passed here; no image or document text.
        AppAuditLogger.log(
            category = LogCategory.SYSTEM,
            tag = TAG,
            message = message,
            isError = priority == Log.ERROR
        )
    }

    private val EVENT_REGEX = Regex("""event=([a-z_]+)""")
}
