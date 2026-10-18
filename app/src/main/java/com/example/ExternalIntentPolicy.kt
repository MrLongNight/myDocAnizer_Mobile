package com.example

/** Whitelist for widget/external navigation values. Unknown values are discarded. */
internal object ExternalIntentPolicy {
    private val allowedTabs = setOf("SCANNER", "DOCANIZER", "DASHBOARD", "BATCH_INBOX", "IMPORT", "SETTINGS")
    fun validTab(value: String?): String? = value?.takeIf { it in allowedTabs }
    fun isAllowedAction(value: String?): Boolean = value == "CASH_EXPENSE"
}
