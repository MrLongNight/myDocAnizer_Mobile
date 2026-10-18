package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class BackupRulesSecurityTest {
    private fun exclusions(resourceId: Int): Set<Pair<String, String>> {
        val parser = ApplicationProvider.getApplicationContext<Application>().resources.getXml(resourceId)
        val result = mutableSetOf<Pair<String, String>>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "exclude") {
                result += parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path")
            }
            event = parser.next()
        }
        parser.close()
        return result
    }

    @Test
    fun `cloud backup excludes vault security preferences`() {
        val excluded = exclusions(R.xml.backup_rules) + exclusions(R.xml.data_extraction_rules)
        assertTrue(("sharedpref" to "mydocanizer_vault_security.xml") in excluded)
        assertTrue(("sharedpref" to "mydocanizer_prefs.xml") in excluded)
    }

    @Test
    fun `device transfer excludes keystore bound vault preferences`() {
        val parser = ApplicationProvider.getApplicationContext<Application>().resources.getXml(R.xml.data_extraction_rules)
        var inDeviceTransfer = false
        var sawVaultExclusion = false
        var sawLegacyPrefsExclusion = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "device-transfer") inDeviceTransfer = true
            if (event == XmlPullParser.END_TAG && parser.name == "device-transfer") inDeviceTransfer = false
            if (inDeviceTransfer && event == XmlPullParser.START_TAG && parser.name == "exclude" &&
                parser.getAttributeValue(null, "domain") == "sharedpref"
            ) {
                when (parser.getAttributeValue(null, "path")) {
                    "mydocanizer_vault_security.xml" -> sawVaultExclusion = true
                    "mydocanizer_prefs.xml" -> sawLegacyPrefsExclusion = true
                }
            }
            event = parser.next()
        }
        parser.close()
        assertTrue(sawVaultExclusion)
        assertTrue(sawLegacyPrefsExclusion)
    }
}
