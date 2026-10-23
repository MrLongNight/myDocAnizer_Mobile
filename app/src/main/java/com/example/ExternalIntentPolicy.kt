package com.example

import android.content.Intent
import android.os.Bundle

/** Validates and consumes the only external navigation commands supported by the app. */
internal object ExternalIntentPolicy {
    private val allowedTabs = setOf("SCANNER", "DOCANIZER", "DASHBOARD", "BATCH_INBOX", "IMPORT", "SETTINGS")

    data class Request(
        val tab: String?,
        val openCashExpense: Boolean,
        val openReconciliation: Boolean,
        val quickScan: Boolean
    )

    fun validTab(value: String?): String? = value?.takeIf { it in allowedTabs }

    fun isAllowedAction(value: String?): Boolean = value == "CASH_EXPENSE"

    /**
     * Reads only explicitly supported values, then strips the complete external payload.
     * Stripping happens before authentication gating so a locked launch cannot replay a
     * previously rejected command after Activity recreation or a later onNewIntent call.
     * No URI/deep-link format is currently supported; Intent.data is therefore discarded.
     */
    fun consume(intent: Intent): Request {
        val tab = validTab(runCatching { intent.getStringExtra("EXTRA_START_TAB") }.getOrNull())
        val openCashExpense = isAllowedAction(
            runCatching { intent.getStringExtra("EXTRA_OPEN_ACTION") }.getOrNull()
        )
        val openReconciliation = runCatching {
            intent.getBooleanExtra("EXTRA_OPEN_RECONCILIATION", false)
        }.getOrDefault(false)
        // Quick scan is a separate explicit command and is only valid with the scanner tab.
        // Malformed extras are treated as false; the entire Intent payload is still consumed.
        val quickScan = tab == "SCANNER" && runCatching {
            intent.getBooleanExtra("EXTRA_QUICK_SCAN", false)
        }.getOrDefault(false)

        // Replace without enumerating/unparceling unknown attacker-controlled extras.
        intent.replaceExtras(null as Bundle?)
        intent.data = null
        intent.clipData = null

        return Request(
            tab = tab,
            openCashExpense = openCashExpense,
            openReconciliation = openReconciliation,
            quickScan = quickScan
        )
    }
}
