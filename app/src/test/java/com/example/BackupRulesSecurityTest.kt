package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class BackupRulesSecurityTest {
    private fun exclusions(resourceId: Int, section: String? = null): Set<Pair<String, String>> {
        val parser = ApplicationProvider.getApplicationContext<Application>().resources.getXml(resourceId)
        val result = mutableSetOf<Pair<String, String>>()
        var currentSection: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if (parser.name == "cloud-backup" || parser.name == "device-transfer") {
                    currentSection = parser.name
                } else if (parser.name == "exclude" && (section == null || section == currentSection)) {
                    result += parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path")
                }
            }
            if (event == XmlPullParser.END_TAG &&
                (parser.name == "cloud-backup" || parser.name == "device-transfer")
            ) {
                currentSection = null
            }
            event = parser.next()
        }
        parser.close()
        return result
    }

    @Test
    fun `legacy and modern cloud backup rules exclude vault security preferences`() {
        val legacyBackup = exclusions(R.xml.backup_rules)
        val extractionRules = exclusions(R.xml.data_extraction_rules, "cloud-backup")
        assertTrue(("sharedpref" to "mydocanizer_vault_security.xml") in legacyBackup)
        assertTrue(("sharedpref" to "mydocanizer_prefs.xml") in legacyBackup)
        assertTrue(("sharedpref" to "mydocanizer_vault_security.xml") in extractionRules)
        assertTrue(("sharedpref" to "mydocanizer_prefs.xml") in extractionRules)
        assertTrue(("external" to ".") in extractionRules)
        assertTrue(("file" to "Models") in extractionRules)
        assertTrue(("file" to "Temp") in extractionRules)
        assertTrue(("file" to "tmp") in extractionRules)
    }

    @Test
    fun `device transfer excludes keystore bound vault preferences`() {
        val transfer = exclusions(R.xml.data_extraction_rules, "device-transfer")
        assertTrue(("sharedpref" to "mydocanizer_vault_security.xml") in transfer)
        assertTrue(("sharedpref" to "mydocanizer_prefs.xml") in transfer)
        assertTrue(("sharedpref" to "doc_rules_storage.xml") in transfer)
    }

    @Test
    fun `device transfer does not include every file and database wholesale`() {
        val parser = ApplicationProvider.getApplicationContext<Application>().resources.getXml(R.xml.data_extraction_rules)
        var inDeviceTransfer = false
        var hasBroadInclude = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "device-transfer") inDeviceTransfer = true
            if (inDeviceTransfer && event == XmlPullParser.START_TAG && parser.name == "include") {
                val domain = parser.getAttributeValue(null, "domain")
                val path = parser.getAttributeValue(null, "path")
                if (path == "." && domain in setOf("file", "database", "sharedpref")) hasBroadInclude = true
            }
            if (event == XmlPullParser.END_TAG && parser.name == "device-transfer") inDeviceTransfer = false
            event = parser.next()
        }
        parser.close()
        assertFalse("Device transfer must not blanket-include sensitive stores", hasBroadInclude)

        val transfer = exclusions(R.xml.data_extraction_rules, "device-transfer")
        assertTrue(("database" to ".") in transfer)
        assertTrue(("external" to ".") in transfer)
        assertTrue(("file" to "Documents") in transfer)
        assertTrue(("file" to "Scans") in transfer)
        assertTrue(("file" to "Backups") in transfer)
    }
}
