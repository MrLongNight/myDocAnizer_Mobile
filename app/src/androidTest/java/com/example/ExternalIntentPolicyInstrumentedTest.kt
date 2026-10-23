package com.example

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalIntentPolicyInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun validWidgetRequestIsExtractedAndExternalPayloadIsConsumed() {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("EXTRA_START_TAB", "DASHBOARD")
            putExtra("EXTRA_OPEN_ACTION", "CASH_EXPENSE")
            putExtra("EXTRA_OPEN_RECONCILIATION", true)
            putExtra("EXTRA_UNRECOGNIZED", "must not survive")
            data = Uri.parse("mydocanizer://unsupported/deep-link")
            clipData = ClipData.newPlainText("external", "untrusted")
        }

        val request = ExternalIntentPolicy.consume(intent)

        assertEquals("DASHBOARD", request.tab)
        assertTrue(request.openCashExpense)
        assertTrue(request.openReconciliation)
        assertTrue(intent.extras == null || intent.extras?.isEmpty == true)
        assertNull(intent.data)
        assertNull(intent.clipData)
    }

    @Test
    fun quickScanIsAllowedOnlyForScannerTabAndPayloadIsConsumed() {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("EXTRA_START_TAB", "SCANNER")
            putExtra("EXTRA_QUICK_SCAN", true)
            putExtra("EXTRA_UNRECOGNIZED", "must not survive")
        }

        val request = ExternalIntentPolicy.consume(intent)

        assertEquals("SCANNER", request.tab)
        assertTrue(request.quickScan)
        assertTrue(intent.extras == null || intent.extras?.isEmpty == true)
        assertNull(intent.data)
        assertNull(intent.clipData)
    }

    @Test
    fun quickScanIsRejectedForOtherTabsAndMalformedBooleanExtras() {
        val dashboardIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("EXTRA_START_TAB", "DASHBOARD")
            putExtra("EXTRA_QUICK_SCAN", true)
        }
        assertFalse(ExternalIntentPolicy.consume(dashboardIntent).quickScan)

        val malformedIntent = Intent(context, MainActivity::class.java).apply {
            putExtra("EXTRA_START_TAB", "SCANNER")
            putExtra("EXTRA_QUICK_SCAN", "true")
        }
        assertFalse(ExternalIntentPolicy.consume(malformedIntent).quickScan)
        assertTrue(malformedIntent.extras == null || malformedIntent.extras?.isEmpty == true)
    }

    @Test
    fun invalidAndMalformedExternalValuesAreDiscarded() {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("EXTRA_START_TAB", "../SETTINGS")
            putExtra("EXTRA_OPEN_ACTION", "DELETE_ALL")
            putExtra("EXTRA_OPEN_RECONCILIATION", "not-a-boolean")
            putExtra("EXTRA_UNRECOGNIZED", true)
        }

        val request = ExternalIntentPolicy.consume(intent)

        assertNull(request.tab)
        assertFalse(request.openCashExpense)
        assertFalse(request.openReconciliation)
        assertTrue(intent.extras == null || intent.extras?.isEmpty == true)
    }
}
