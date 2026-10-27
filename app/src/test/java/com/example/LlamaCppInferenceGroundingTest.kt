package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.FolderStrategy
import com.example.service.LlamaCppInferenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LlamaCppInferenceGroundingTest {
    private fun engine(): LlamaCppInferenceEngine =
        LlamaCppInferenceEngine(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun rejectsSenderAndTypeNotPresentInOcrAndIgnoresModelPath() {
        val result = engine().parseDmsJson(
            """{"absender":"Musterbank AG","dokumententyp":"Steuerbescheid","kategorie_vorschlag":"Finanzen","vorgeschlagener_pfad":"2026/Musterbank AG/Geheim","match_keywords":["Musterbank","geheime Klausel"],"globale_zusatzfelder":{"Referenz":"ABC-999"}}""",
            "Stadtwerke Leipzig Rechnung vom 12.03.2025, Betrag 89,50 Euro",
            FolderStrategy.BY_CATEGORY
        )

        assertNull(result.absender)
        assertNull(result.dokumententyp)
        assertEquals("Finanzen", result.kategorieVorschlag)
        assertEquals("Finanzen/Dokument", result.vorgeschlagenerPfad)
        assertTrue(result.matchKeywords.isEmpty())
        assertTrue(result.globaleZusatzfelder.isEmpty())
    }

    @Test
    fun acceptsOcrGroundedSenderTypeAndYearOnly() {
        val result = engine().parseDmsJson(
            """{"absender":"Stadtwerke Leipzig","dokumententyp":"Rechnung","kategorie_vorschlag":"Finanzen","vorgeschlagener_pfad":"2030/Invented","match_keywords":["Rechnung","nicht vorhanden"]}""",
            "Stadtwerke Leipzig Rechnung vom 12.03.2025, Betrag 89,50 Euro",
            FolderStrategy.CHRONOLOGICAL
        )

        assertEquals("Stadtwerke Leipzig", result.absender)
        assertEquals("Rechnung", result.dokumententyp)
        assertEquals("2025/Rechnung", result.vorgeschlagenerPfad)
        assertEquals(listOf("rechnung"), result.matchKeywords)
    }

    @Test
    fun doesNotInventChronologicalYearWhenOcrHasNoYear() {
        val result = engine().parseDmsJson(
            """{"absender":"Stadtwerke Leipzig","dokumententyp":"Rechnung","kategorie_vorschlag":"Finanzen","vorgeschlagener_pfad":"2026/Rechnung"}""",
            "Stadtwerke Leipzig Rechnung, Betrag 89,50 Euro",
            FolderStrategy.CHRONOLOGICAL
        )

        assertEquals("Rechnung", result.vorgeschlagenerPfad)
    }
}
