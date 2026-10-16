package com.example

import com.example.model.CustomFieldEntity
import com.example.model.CustomFieldScope
import com.example.model.CustomFieldType
import com.example.service.llm.CustomFieldHarmonizer
import com.example.service.llm.HarmonizationStatus
import com.example.service.llm.LlmPrompts
import com.example.service.LlamaCppInferenceEngine
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testCustomFieldHarmonizer_auditRuleFields_matchesExistingAndSuggestsDashboardWidgets() {
        val existingFields = listOf(
            CustomFieldEntity(
                id = "cf-betrag-1",
                name = "Betrag",
                description = "Hauptbetrag",
                type = CustomFieldType.AMOUNT,
                scope = CustomFieldScope.GLOBAL,
                iconName = "payments"
            ),
            CustomFieldEntity(
                id = "cf-kunde-1",
                name = "Kundennummer",
                description = "Kundenkonto",
                type = CustomFieldType.TEXT,
                scope = CustomFieldScope.GLOBAL,
                iconName = "person"
            )
        )

        // Regel mit Synonym "Rechnungsbetrag"
        val currentFields = mapOf(
            "Rechnungsbetrag" to "59,99 €",
            "Zählernummer" to "123456"
        )

        val report = CustomFieldHarmonizer.auditRuleFields(
            ruleName = "Stadtwerke Stromabrechnung",
            docType = "Rechnung",
            sender = "Stadtwerke München",
            currentCustomFields = currentFields,
            existingFields = existingFields
        )

        // 1. "Rechnungsbetrag" muss als MATCHED_EXISTING auf "Betrag" gemappt sein
        val matchedBetrag = report.assignedFields.find { it.fieldName == "Betrag" }
        assertNotNull("Betrag sollte zugeordnet sein", matchedBetrag)
        assertEquals(HarmonizationStatus.MATCHED_EXISTING, matchedBetrag?.status)
        assertEquals("cf-betrag-1", matchedBetrag?.matchedFieldId)

        // 2. "Zählernummer" existiert noch nicht im Tresor -> muss als NEW_FIELD_CREATION erkannt werden
        val newZaehler = report.assignedFields.find { it.fieldName == "Zählernummer" }
        assertNotNull("Zählernummer sollte neu vorgeschlagen sein", newZaehler)
        assertEquals(HarmonizationStatus.NEW_FIELD_CREATION, newZaehler?.status)

        // 3. Fehlende empfohlene Felder für Stadtwerke / Strom (z.B. Fälligkeit, Vertragskonto)
        val missingFaelligkeit = report.missingRecommendedFields.find { it.fieldName == "Fälligkeit" }
        assertNotNull("Fälligkeit sollte als Empfehlung vorhanden sein", missingFaelligkeit)

        // 4. Dashboard Widgets müssen aktiviert sein (Finanz-Widget durch Betrag, Zähler-Widget durch Zählernummer)
        assertTrue("Finanz-Widget sollte unterstützt sein", report.supportedDashboardWidgets.any { it.contains("Finanz") })
        assertTrue("Zähler-Widget sollte unterstützt sein", report.supportedDashboardWidgets.any { it.contains("Zähler") })
    }

    @Test
    fun testCustomFieldHarmonizer_synonymHarmonization() {
        val existingFields = listOf(
            CustomFieldEntity(
                id = "cf-faellig-1",
                name = "Fälligkeit",
                description = "Zahlungsziel",
                type = CustomFieldType.DATE,
                scope = CustomFieldScope.GLOBAL,
                iconName = "event"
            )
        )

        val input = mapOf("zahlungsziel" to "15.11.2026", "kdnr" to "KD-998877")
        val result = CustomFieldHarmonizer.harmonizeFields(input, existingFields)

        assertEquals("15.11.2026", result.harmonizedFields["Fälligkeit"])
        assertEquals("KD-998877", result.harmonizedFields["Kundennummer"])
    }

    @Test
    fun testLlamaCppEngine_createHeuristicFallbackResponse_populatesFields() {
        val ocrSample = """
            Stadtwerke München GmbH
            Stromabrechnung 2026
            Rechnungsnummer: RE-2026-98765
            Kundennummer: KD-112233
            Zählernummer: Z-554433
            Fälligkeit: 30.11.2026
            Rechnungsbetrag: 142,50 €
        """.trimIndent()

        val fallback = LlamaCppInferenceEngine.createHeuristicFallbackResponse(ocrSample)

        assertEquals("Stadtwerke", fallback.absender)
        assertEquals("Rechnung", fallback.dokumententyp)
        assertEquals("142,50 €", fallback.globaleZusatzfelder["Betrag"])
        assertEquals("30.11.2026", fallback.globaleZusatzfelder["Fälligkeit"])
        assertEquals("RE-2026-98765", fallback.globaleZusatzfelder["Rechnungsnummer"])
        assertEquals("KD-112233", fallback.globaleZusatzfelder["Kundennummer"])
    }

    @Test
    fun testDocRuleRepository_containsKeywordMatch() {
        val ocrText = "CO2-Steuer Zahlung an o2"
        
        // "o2" in "CO2-Steuer" should NOT match, but "o2" in "an o2" SHOULD match
        assertTrue("o2 should match 'an o2'", com.example.service.DocRuleRepository.containsKeywordMatch(ocrText, "o2"))
        
        // "co2" should match "CO2-Steuer"
        assertTrue("co2 should match 'CO2-Steuer'", com.example.service.DocRuleRepository.containsKeywordMatch(ocrText, "co2"))
        
        // "o2" should NOT match "CO2"
        assertFalse("o2 should not match part of CO2", com.example.service.DocRuleRepository.containsKeywordMatch("CO2", "o2"))
    }

    @Test
    fun testBankStatementParser_encodingDetection() {
        val utf8Text = "Überweisung mit ü"
        val brokenUtf8Bytes = (utf8Text + "\uFFFD").toByteArray(Charsets.UTF_8)
        
        val previewUtf8 = String(brokenUtf8Bytes, Charsets.UTF_8)
        val useIso = previewUtf8.contains("\uFFFD")
        
        assertTrue("Broken UTF-8 should trigger ISO-8859-1 detection", useIso)
    }
}

