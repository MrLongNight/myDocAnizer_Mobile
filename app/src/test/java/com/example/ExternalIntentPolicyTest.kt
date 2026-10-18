package com.example
import org.junit.Assert.*
import org.junit.Test
class ExternalIntentPolicyTest{
 @Test fun knownTabsOnly(){listOf("SCANNER","DOCANIZER","DASHBOARD","BATCH_INBOX","IMPORT","SETTINGS").forEach{assertEquals(it,ExternalIntentPolicy.validTab(it))};listOf(null,"","admin","../SETTINGS","settings","P2P").forEach{assertNull(ExternalIntentPolicy.validTab(it))}}
 @Test fun knownActionOnly(){assertTrue(ExternalIntentPolicy.isAllowedAction("CASH_EXPENSE"));listOf(null,"","DELETE_ALL","RECONCILE","../CASH_EXPENSE").forEach{assertFalse(ExternalIntentPolicy.isAllowedAction(it))}}
}
