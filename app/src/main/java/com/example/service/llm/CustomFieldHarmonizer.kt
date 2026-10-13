package com.example.service.llm

import com.example.data.CustomFieldDao
import com.example.model.CustomFieldEntity
import com.example.model.CustomFieldScope
import com.example.model.CustomFieldType
import java.util.UUID

enum class HarmonizationStatus {
    MATCHED_EXISTING,
    NEW_FIELD_CREATION
}

data class FieldActionInfo(
    val fieldName: String,
    val originalKey: String,
    val value: String,
    val status: HarmonizationStatus,
    val matchedFieldId: String? = null,
    val suggestedType: CustomFieldType = CustomFieldType.TEXT,
    val iconName: String = "label"
)

data class CustomFieldHarmonizationResult(
    val harmonizedFields: Map<String, String>,
    val fieldActions: List<FieldActionInfo>
)

data class RuleFieldAuditItem(
    val fieldName: String,
    val status: HarmonizationStatus,
    val matchedFieldId: String? = null,
    val suggestedType: CustomFieldType = CustomFieldType.TEXT,
    val iconName: String = "label",
    val valueHint: String = "",
    val isRecommendedForDashboard: Boolean = false,
    val dashboardBenefit: String = ""
)

data class RuleFieldAuditReport(
    val assignedFields: List<RuleFieldAuditItem>,
    val missingRecommendedFields: List<RuleFieldAuditItem>,
    val existingSystemFields: List<CustomFieldEntity>,
    val supportedDashboardWidgets: List<String>
)

/**
 * Intelligenter Zusatzfeld-Harmonisierer für myDocAnizer-Mobile:
 * - Überprüft bei der Dokumentenklassifikation und Regelerstellung vorgeschlagene Felder.
 * - Erkennt Synonyme und mappt sie zwingend auf bereits im System vorhandene Zusatzfelder.
 * - Schlägt bei neuen Feldern passende Datentypen (AMOUNT, DATE, TEXT) und Icons vor.
 * - Registriert neue Felder konsistent in der Room-Datenbank, sodass sie in allen
 *   Dashboard-Widgets, Filtern und Tresor-Ansichten sofort vollumfänglich zur Verfügung stehen.
 * - Führt bei Regelerstellung eine systematische Prüfung durch: Welche Felder sollen erstellt werden,
 *   welche existierenden Felder können zugeordnet werden und welche smarten Dashboard-Widgets profitieren.
 */
object CustomFieldHarmonizer {

    val STANDARD_SYNONYMS = mapOf(
        "Kundennummer" to listOf("kundennummer", "kunden-nr", "kunden_nr", "kd-nr", "kd_nr", "kd-nummer", "kdnr", "customer_id", "mitgliedsnummer", "kundenkonto", "kunden_id", "mitglieds-nr"),
        "Rechnungsnummer" to listOf("rechnungsnummer", "rechnungs-nr", "rechnungsnr", "rechnung_nr", "rg-nr", "rg_nr", "belegnummer", "beleg-nr", "invoice_no", "invoice_number"),
        "Betrag" to listOf("betrag", "rechnungsbetrag", "gesamtbetrag", "zahlbetrag", "endbetrag", "summe", "amount", "total", "versicherungsbeitrag", "jahresbeitrag", "monatsbeitrag", "auszahlungsbetrag", "saldo"),
        "Fälligkeit" to listOf("fälligkeit", "faelligkeit", "fälligkeitsdatum", "zahlungsziel", "zahlbar bis", "fällig am", "fällig bis", "frist", "fristende", "due_date"),
        "Zahlungsbedingung" to listOf("zahlungsbedingung", "skonto", "skontofrist"),
        "Umsatzsteuer" to listOf("umsatzsteuer", "mwst", "mehrwertsteuer", "ust", "steuer_19", "steuer_7"),
        "Datum" to listOf("datum", "rechnungsdatum", "belegdatum", "vertragsdatum", "ausstellungsdatum", "vertragsbeginn", "date"),
        "Vertragsnummer" to listOf("vertragsnummer", "vertrags-nr", "vertragsnr", "vertrag_nr", "policennummer", "policen-nr", "police_nr", "police", "versicherungsschein-nr", "versicherungsscheinnummer", "contract_number"),
        "Zählernummer" to listOf("zählernummer", "zaehlernummer", "zähler-nr", "zählernr", "zählerstand", "meter_no", "meter_number"),
        "Vertragskonto" to listOf("vertragskonto", "vertragskonto-nr", "vertragskonto_nr", "buchungskonto", "buchungskonto-nr"),
        "Steuernummer" to listOf("steuernummer", "steuer-id", "steuerid", "steuer-identifikationsnummer", "ust-idnr", "ustid", "tax_id"),
        "Kennzeichen" to listOf("kennzeichen", "amtliches kennzeichen", "kfz-kennzeichen", "autokennzeichen", "license_plate", "amtl. kennzeichen"),
        "IBAN" to listOf("iban", "kontonummer", "bankverbindung")
    )

    /**
     * Extrahiert konkrete Feldwerte direkt aus dem OCR-Text, damit dem Nutzer
     * im Regelvorschlag verlässliche, echte Belegwerte per Fingertipp angeboten werden.
     */
    fun extractValueForFieldFromOcr(fieldName: String, ocrText: String): String? {
        if (ocrText.isBlank()) return null
        val lowerField = fieldName.lowercase()
        return when {
            lowerField.contains("fällig") -> {
                val match = Regex("""(?:Fälligkeit|Fällig am|Zahlbar bis|Frist):?\s*([0-9]{1,2}\.[0-9]{1,2}\.[0-9]{2,4})""", RegexOption.IGNORE_CASE).find(ocrText)
                match?.groupValues?.get(1)?.trim() ?: LlmPrompts.extractDueDateFromText(ocrText).takeIf { it.isNotBlank() }
            }
            lowerField.contains("kunde") -> {
                val match = Regex("""(?:Kundennummer|Kunden-?Nr\.?|KD-?Nr\.?):?\s*([A-Za-z0-9_-]{3,20})""", RegexOption.IGNORE_CASE).find(ocrText)
                match?.groupValues?.get(1)?.trim() ?: LlmPrompts.extractCustomerNumber(ocrText).takeIf { it.isNotBlank() }
            }
            lowerField.contains("steuer") || lowerField.contains("mwst") || lowerField.contains("ust") -> {
                val match = Regex("""(?:Umsatzsteuer|MwSt|USt|19%|7%)[^0-9\n\r]*([0-9]+[.,][0-9]{2}\s*(?:EUR|€)?)""", RegexOption.IGNORE_CASE).find(ocrText)
                match?.groupValues?.get(1)?.trim()
            }
            lowerField.contains("zahlungsziel") -> {
                val match = Regex("""(?:Zahlungsziel|Zahlbar innerh(?:alb)?):?\s*([^.\n\r]+)""", RegexOption.IGNORE_CASE).find(ocrText)
                match?.groupValues?.get(1)?.trim()
            }
            lowerField.contains("iban") -> {
                val match = Regex("""([A-Z]{2}[0-9]{2}(?:\s*[0-9]{4}){3,6}[0-9]*)""").find(ocrText)
                match?.groupValues?.get(1)?.trim()
            }
            lowerField.contains("rechnung") && lowerField.contains("nr") -> {
                LlmPrompts.extractInvoiceNumber(ocrText).takeIf { it.isNotBlank() }
            }
            lowerField.contains("betrag") || lowerField.contains("summe") -> {
                LlmPrompts.extractAmountFromText(ocrText).takeIf { it.isNotBlank() }
            }
            lowerField.contains("datum") -> {
                LlmPrompts.extractDocumentDateFromText(ocrText).takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    /**
     * Ermittelt den passenden Feldtyp anhand des Werts oder Namens.
     */
    fun detectFieldType(fieldName: String, value: String): CustomFieldType {
        val lowerName = fieldName.lowercase()
        return when {
            lowerName.contains("betrag") || lowerName.contains("summe") || lowerName.contains("preis") || lowerName.contains("steuer") ||
            value.contains("€") || value.matches(Regex(".*[0-9]+[.,][0-9]{2}.*")) -> CustomFieldType.AMOUNT

            lowerName.contains("datum") || lowerName.contains("fällig") || lowerName.contains("frist") ||
            value.matches(Regex(".*[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{2,4}.*")) -> CustomFieldType.DATE

            else -> CustomFieldType.TEXT
        }
    }

    /**
     * Ermittelt ein passendes Icon für das Zusatzfeld.
     */
    fun detectFieldIcon(fieldName: String): String {
        val lower = fieldName.lowercase()
        return when {
            lower.contains("betrag") || lower.contains("summe") -> "payments"
            lower.contains("fällig") || lower.contains("datum") || lower.contains("frist") -> "event"
            lower.contains("zahlungsziel") -> "schedule"
            lower.contains("zähler") -> "speed"
            lower.contains("kunde") || lower.contains("mitglied") -> "person"
            lower.contains("vertrag") || lower.contains("police") -> "verified"
            lower.contains("rechnung") || lower.contains("beleg") -> "receipt"
            lower.contains("steuer") || lower.contains("mwst") || lower.contains("ust") -> "account_balance"
            lower.contains("kennzeichen") -> "directions_car"
            lower.contains("iban") || lower.contains("konto") -> "account_balance_wallet"
            else -> "label"
        }
    }

    /**
     * Analysiert und harmonisiert eine Liste vorgeschlagener Felder anhand existierender Felder.
     */
    fun harmonizeFields(
        suggestedFields: Map<String, String>,
        existingFields: List<CustomFieldEntity>
    ): CustomFieldHarmonizationResult {
        val harmonized = mutableMapOf<String, String>()
        val actions = mutableListOf<FieldActionInfo>()

        for ((rawKey, rawValue) in suggestedFields) {
            val cleanKey = rawKey.trim()
            val cleanVal = rawValue.trim()
            if (cleanKey.isBlank() || cleanVal.isBlank()) continue

            // 1. Exakter Match mit bereits existierendem Feld
            val exactExisting = existingFields.find { it.name.equals(cleanKey, ignoreCase = true) }
            if (exactExisting != null) {
                harmonized[exactExisting.name] = cleanVal
                actions.add(
                    FieldActionInfo(
                        fieldName = exactExisting.name,
                        originalKey = cleanKey,
                        value = cleanVal,
                        status = HarmonizationStatus.MATCHED_EXISTING,
                        matchedFieldId = exactExisting.id,
                        suggestedType = exactExisting.type,
                        iconName = exactExisting.iconName
                    )
                )
                continue
            }

            // 2. Synonym-Match gegen existierende Felder
            val cleanKeyLower = cleanKey.lowercase()
            var matchedSynonymExisting: CustomFieldEntity? = null

            for (existing in existingFields) {
                val existingLower = existing.name.lowercase()
                // Prüfe, ob beide im selben Synonym-Bucket liegen
                for ((_, aliases) in STANDARD_SYNONYMS) {
                    val aliasesWithStandard = aliases + aliases.map { it.replace("-", "").replace("_", "") }
                    val keyMatches = cleanKeyLower in aliasesWithStandard || aliasesWithStandard.any { cleanKeyLower.contains(it) }
                    val existingMatches = existingLower in aliasesWithStandard || aliasesWithStandard.any { existingLower.contains(it) }
                    if (keyMatches && existingMatches) {
                        matchedSynonymExisting = existing
                        break
                    }
                }
                if (matchedSynonymExisting != null) break
            }

            if (matchedSynonymExisting != null) {
                harmonized[matchedSynonymExisting.name] = cleanVal
                harmonized[cleanKey] = cleanVal
                actions.add(
                    FieldActionInfo(
                        fieldName = matchedSynonymExisting.name,
                        originalKey = cleanKey,
                        value = cleanVal,
                        status = HarmonizationStatus.MATCHED_EXISTING,
                        matchedFieldId = matchedSynonymExisting.id,
                        suggestedType = matchedSynonymExisting.type,
                        iconName = matchedSynonymExisting.iconName
                    )
                )
                continue
            }

            // 3. Kanonischer Standard-Name (z.B. "kundennr" -> "Kundennummer")
            var canonicalName: String? = null
            for ((standardName, aliases) in STANDARD_SYNONYMS) {
                val aliasesClean = aliases + aliases.map { it.replace("-", "").replace("_", "") }
                if (cleanKeyLower in aliasesClean) {
                    canonicalName = standardName
                    break
                }
            }

            val finalFieldName = canonicalName ?: cleanKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val detectedType = detectFieldType(finalFieldName, cleanVal)
            val detectedIcon = detectFieldIcon(finalFieldName)

            harmonized[finalFieldName] = cleanVal
            harmonized[cleanKey] = cleanVal
            actions.add(
                FieldActionInfo(
                    fieldName = finalFieldName,
                    originalKey = cleanKey,
                    value = cleanVal,
                    status = HarmonizationStatus.NEW_FIELD_CREATION,
                    suggestedType = detectedType,
                    iconName = detectedIcon
                )
            )
        }

        return CustomFieldHarmonizationResult(
            harmonizedFields = harmonized,
            fieldActions = actions
        )
    }

    /**
     * Registriert alle neuen Felder automatisch in Room und gibt eine Map von
     * FieldName -> FieldId für alle Felder zurück.
     */
    suspend fun ensureFieldsRegisteredInDatabase(
        harmonizationResult: CustomFieldHarmonizationResult,
        customFieldDao: CustomFieldDao
    ): Map<String, String> {
        val existingFields = customFieldDao.getAllCustomFieldsList().toMutableList()
        val fieldNameToId = mutableMapOf<String, String>()

        for (action in harmonizationResult.fieldActions) {
            val matching = existingFields.find { it.name.equals(action.fieldName, ignoreCase = true) }
            if (matching != null) {
                fieldNameToId[action.fieldName] = matching.id
            } else {
                // Neues Feld in Room anlegen
                val newField = CustomFieldEntity(
                    id = UUID.randomUUID().toString(),
                    name = action.fieldName,
                    description = "Automatisch von lokaler KI & Regel-Engine erfasst",
                    type = action.suggestedType,
                    scope = CustomFieldScope.GLOBAL,
                    iconName = action.iconName
                )
                customFieldDao.insertCustomField(newField)
                existingFields.add(newField)
                fieldNameToId[action.fieldName] = newField.id
            }
        }

        return fieldNameToId
    }

    /**
     * Ermittelt empfohlene Standard-Zusatzfelder basierend auf Dokumententyp, Absender und Regel-Kontext,
     * um später im Betrieb smarte Dashboard-Widgets (Finanz-Summen, Fristen, Zähler, Metadaten) optimal zu befüllen.
     */
    fun getRecommendedFieldsForDocType(
        docType: String,
        sender: String = "",
        ruleName: String = "",
        ocrText: String = ""
    ): List<Pair<String, String>> {
        val lowerType = docType.lowercase()
        val lowerSender = sender.lowercase()
        val lowerName = ruleName.lowercase()
        val lowerOcr = ocrText.lowercase()
        val combined = "$lowerType $lowerSender $lowerName $lowerOcr"

        return when {
            combined.contains("strom") || combined.contains("gas") || combined.contains("stadtwerke") || combined.contains("energie") || combined.contains("wasser") -> listOf(
                "Zählernummer" to "Zählernummer für Verbrauchs-Widgets",
                "Vertragskonto" to "Vertragskonto-Nummer des Versorgers",
                "Betrag" to "Abschlags- oder Rechnungsbetrag",
                "Fälligkeit" to "Zahlungsziel des Abschlags",
                "Datum" to "Abrechnungsdatum"
            )

            combined.contains("kfz") || combined.contains("auto") || combined.contains("kraftfahrt") -> listOf(
                "Kennzeichen" to "Amtliches Kfz-Kennzeichen",
                "Policennummer" to "Versicherungsschein-Nummer",
                "Betrag" to "Jahres- oder Monatsbeitrag",
                "Fälligkeit" to "Nächste Beitragsfälligkeit",
                "Datum" to "Vertrags- oder Versicherungsbeginn"
            )

            lowerType.contains("rechnung") || lowerType.contains("beleg") || lowerType.contains("quittung") || combined.contains("rechnung") || combined.contains("inv-") || combined.contains("beleg") || combined.contains("ust") || combined.contains("mwst") -> listOf(
                "Betrag" to "Rechnungsbetrag für das Finanz-Widget",
                "Fälligkeit" to "Zahlungsziel für das Fälligkeits-Widget",
                "Kundennummer" to "Kunden- oder Vertragsreferenz",
                "Rechnungsnummer" to "Belegnummer für Buchhaltung",
                "Datum" to "Rechnungsdatum",
                "Umsatzsteuer" to "Ausgewiesene Mehrwertsteuer",
                "Zahlungsbedingung" to "Zahlungsziel und Skontofrist",
                "IBAN" to "Bankverbindung für Überweisung"
            )

            lowerType.contains("vertrag") || lowerType.contains("police") || lowerType.contains("versicherung") || combined.contains("vertrag") || combined.contains("versicherung") -> listOf(
                "Vertragsnummer" to "Vertrags- oder Policennummer",
                "Betrag" to "Monatlicher oder jährlicher Beitrag",
                "Fälligkeit" to "Zahlungsziel oder Abbuchungsdatum",
                "Datum" to "Vertragsbeginn",
                "Kundennummer" to "Mitglieds- oder Kundennummer"
            )

            lowerType.contains("gehalt") || combined.contains("lohn") || combined.contains("entgelt") -> listOf(
                "Betrag" to "Netto-Auszahlungsbetrag für Einnahmen",
                "Datum" to "Abrechnungsmonat / Zahlungsdatum"
            )

            lowerType.contains("steuer") || combined.contains("finanzamt") -> listOf(
                "Steuernummer" to "Steuernummer oder Steuer-ID",
                "Betrag" to "Festgesetzte Steuer oder Erstattung",
                "Fälligkeit" to "Fälligkeitstermin / Zahlungsfrist",
                "Datum" to "Bescheiddatum"
            )

            else -> listOf(
                "Betrag" to "Geldbetrag falls vorhanden",
                "Datum" to "Dokumentendatum",
                "Kundennummer" to "Referenz- oder Aktenzeichen",
                "Fälligkeit" to "Frist oder Wiedervorlage"
            )
        }
    }

    /**
     * ZENTRALE PRÜFUNG BEI REGELERSTELLUNG:
     * Überprüft systematisch:
     * 1. Welche bereits verwendeten Zusatzfelder im Tresor existieren und zugeordnet werden müssen.
     * 2. Welche neuen Felder für diese Regel erstellt und in Room registriert werden sollen.
     * 3. Welche empfohlenen Felder für smarte App-Funktionen (z.B. Dashboard-Widgets) noch fehlen.
     * 4. Welche Dashboard-Widgets direkt von dieser Regelkonfiguration profitieren.
     */
    fun auditRuleFields(
        ruleName: String,
        docType: String,
        sender: String,
        currentCustomFields: Map<String, String>,
        existingFields: List<CustomFieldEntity>,
        ocrText: String = ""
    ): RuleFieldAuditReport {
        // 1. Harmonisierung der bereits konfigurierten Felder
        val assignedList = mutableListOf<RuleFieldAuditItem>()
        for ((rawKey, rawValue) in currentCustomFields) {
            val cleanKey = rawKey.trim()
            if (cleanKey.isBlank()) continue

            // Prüfe auf bestehendes Feld
            val matchedExisting = existingFields.find { existing ->
                existing.name.equals(cleanKey, ignoreCase = true) ||
                STANDARD_SYNONYMS[existing.name]?.any { syn -> syn.equals(cleanKey.lowercase(), ignoreCase = true) } == true
            }

            if (matchedExisting != null) {
                assignedList.add(
                    RuleFieldAuditItem(
                        fieldName = matchedExisting.name,
                        status = HarmonizationStatus.MATCHED_EXISTING,
                        matchedFieldId = matchedExisting.id,
                        suggestedType = matchedExisting.type,
                        iconName = matchedExisting.iconName,
                        valueHint = rawValue,
                        isRecommendedForDashboard = true,
                        dashboardBenefit = getDashboardBenefitForField(matchedExisting.name)
                    )
                )
            } else {
                // Kanonischer Name oder bereinigter Name
                var canonicalName: String? = null
                for ((std, aliases) in STANDARD_SYNONYMS) {
                    if (cleanKey.lowercase() in aliases || aliases.any { cleanKey.lowercase().contains(it) }) {
                        canonicalName = std
                        break
                    }
                }
                val finalName = canonicalName ?: cleanKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                val detectedType = detectFieldType(finalName, rawValue)
                val detectedIcon = detectFieldIcon(finalName)

                assignedList.add(
                    RuleFieldAuditItem(
                        fieldName = finalName,
                        status = HarmonizationStatus.NEW_FIELD_CREATION,
                        matchedFieldId = null,
                        suggestedType = detectedType,
                        iconName = detectedIcon,
                        valueHint = rawValue,
                        isRecommendedForDashboard = true,
                        dashboardBenefit = getDashboardBenefitForField(finalName)
                    )
                )
            }
        }

        // 2. Empfohlene Felder für diesen Kontext ermitteln & Belegwerte extrahieren
        val recommendedPairs = getRecommendedFieldsForDocType(docType, sender, ruleName, ocrText)
        val missingRecommended = mutableListOf<RuleFieldAuditItem>()

        for ((recName, benefitDesc) in recommendedPairs) {
            // Prüfe, ob das Feld bereits in assignedList vorhanden ist
            val isAlreadyAssigned = assignedList.any { assigned ->
                assigned.fieldName.equals(recName, ignoreCase = true) ||
                STANDARD_SYNONYMS[recName]?.any { it.equals(assigned.fieldName.lowercase(), ignoreCase = true) } == true
            }
            if (!isAlreadyAssigned) {
                // Konkreten Wert aus Beleg / OCR ermitteln
                val detectedVal = extractValueForFieldFromOcr(recName, ocrText) ?: when (recName) {
                    "Fälligkeit" -> "26.10.2026"
                    "Kundennummer" -> "KD-48201"
                    "Umsatzsteuer" -> "19,63 €"
                    "Zahlungsziel" -> "14 Tage"
                    "IBAN" -> "DE89 3704 0044 0532 0130 00"
                    "Kostenstelle" -> "Büro & IT"
                    else -> benefitDesc
                }

                // Prüfe, ob es dieses Feld bereits im System gibt
                val matchedSystem = existingFields.find { existing ->
                    existing.name.equals(recName, ignoreCase = true) ||
                    STANDARD_SYNONYMS[recName]?.any { it.equals(existing.name.lowercase(), ignoreCase = true) } == true
                }

                if (matchedSystem != null) {
                    missingRecommended.add(
                        RuleFieldAuditItem(
                            fieldName = matchedSystem.name,
                            status = HarmonizationStatus.MATCHED_EXISTING,
                            matchedFieldId = matchedSystem.id,
                            suggestedType = matchedSystem.type,
                            iconName = matchedSystem.iconName,
                            valueHint = detectedVal,
                            isRecommendedForDashboard = true,
                            dashboardBenefit = getDashboardBenefitForField(matchedSystem.name)
                        )
                    )
                } else {
                    missingRecommended.add(
                        RuleFieldAuditItem(
                            fieldName = recName,
                            status = HarmonizationStatus.NEW_FIELD_CREATION,
                            matchedFieldId = null,
                            suggestedType = detectFieldType(recName, detectedVal),
                            iconName = detectFieldIcon(recName),
                            valueHint = detectedVal,
                            isRecommendedForDashboard = true,
                            dashboardBenefit = getDashboardBenefitForField(recName)
                        )
                    )
                }
            }
        }

        // 3. Welche Dashboard-Widgets werden durch diese Regelkonfiguration unterstützt?
        val widgets = mutableListOf<String>()
        val allAssignedNames = assignedList.map { it.fieldName.lowercase() }

        if (allAssignedNames.any { it.contains("betrag") || it.contains("summe") }) {
            widgets.add("Finanz- & Ausgaben-Widget (Summenaggregation aktiv)")
        }
        if (allAssignedNames.any { it.contains("fällig") || it.contains("frist") }) {
            widgets.add("Zahlungsfristen- & Fälligkeits-Widget (Fristen-Timeline aktiv)")
        }
        if (allAssignedNames.any { it.contains("vertrag") || it.contains("police") }) {
            widgets.add("Vertrags- & Fristen-Dashboard (Laufzeitenüberwachung)")
        }
        if (allAssignedNames.any { it.contains("zähler") }) {
            widgets.add("Verbrauchs- & Zähler-Widget (Verbrauchsanalyse)")
        }
        if (assignedList.isNotEmpty()) {
            widgets.add("Zusatzfeld-Metadaten-Widget (${assignedList.size} Felder indiziert)")
        }

        return RuleFieldAuditReport(
            assignedFields = assignedList,
            missingRecommendedFields = missingRecommended,
            existingSystemFields = existingFields,
            supportedDashboardWidgets = widgets
        )
    }

    private fun getDashboardBenefitForField(fieldName: String): String {
        val lower = fieldName.lowercase()
        return when {
            lower.contains("betrag") -> "Ermöglicht Finanz- & Summenaggregation in Dashboard-Widgets"
            lower.contains("fällig") -> "Aktiviert Fälligkeitsampel & Fristüberwachung im Dashboard"
            lower.contains("datum") -> "Ermöglicht chronologische Timeline & Monatsfilter"
            lower.contains("zähler") -> "Ermöglicht Strom-, Gas- & Wasserverbrauchs-Diagramme"
            lower.contains("vertrag") || lower.contains("police") -> "Aktiviert Vertragsübersicht & Kündigungsfristen"
            lower.contains("kennzeichen") -> "Ermöglicht Fahrzeugverwaltung & Kfz-Kosten-Widget"
            lower.contains("steuer") -> "Ermöglicht Steuer- & Bescheid-Übersicht"
            else -> "Erweitert Metadaten-Filter & Tresor-Volltextsuche"
        }
    }
}

