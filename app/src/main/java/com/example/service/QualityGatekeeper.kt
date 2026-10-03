package com.example.service

import com.example.model.DocRule
import com.example.model.DocumentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Ergebnis der Qualitätsprüfung für ein einzelnes Feld.
 */
data class FieldQualityCheck(
    val fieldName: String,
    val extractedValue: String,
    val normalizedValue: String,
    val isFoundInText: Boolean,
    val isPlausible: Boolean,
    val warningMessage: String? = null,
    val suggestedCorrection: String? = null,
    val confidence: Float = 1.0f
)

/**
 * Gesamter Revisions-Bericht von Team 4 (Quality Gatekeeper).
 */
data class QualityAuditReport(
    val overallConfidence: Float,
    val isApproved: Boolean,
    val fieldChecks: List<FieldQualityCheck>,
    val warnings: List<String>,
    val auditSummary: String
) {
    val hasWarnings: Boolean
        get() = warnings.isNotEmpty() || fieldChecks.any { !it.isFoundInText || !it.isPlausible }

    fun getCheckForField(name: String): FieldQualityCheck? =
        fieldChecks.find { it.fieldName.equals(name, ignoreCase = true) }
}

/**
 * Normalisierter Geldbetrag mit verschiedenen Text-Repräsentationen für den OCR-Abgleich.
 */
data class NormalizedAmount(
    val numericValue: Double,
    val standardFormatted: String, // z.B. "1000.00"
    val germanFormatted: String,   // z.B. "1.000,00"
    val simpleGerman: String,      // z.B. "1000,00"
    val searchVariants: List<String>
)

/**
 * Normalisiertes Datum mit verschiedenen Text-Repräsentationen für den OCR-Abgleich.
 */
data class NormalizedDate(
    val standardFormatted: String, // z.B. "12.03.2024"
    val isoFormatted: String,      // z.B. "2024-03-12"
    val searchVariants: List<String>
)

/**
 * Team 4: Deterministischer Quality Gatekeeper & Revisions-Prüfer.
 * Führt einen 100% deterministischen Cross-Check in reinem Kotlin ohne LLM-Aufruf durch:
 * 1. Text-Anchor-Cross-Check: Prüft, ob extrahierte Beträge, IBANs und Datumsangaben im OCR-Rohtext vorkommen.
 * 2. Normalisierungs-Logik: Wandelt Datumsangaben ("12. März 2024" -> "12.03.2024") und Beträge ("1.000,00" -> "1000.00").
 * 3. Logik-Plausibilität: Fängt Fehlzuordnungen ab (z.B. Fälligkeitsdatum neben 'geb.' / Geburtsdatum-Verwechslung).
 * 4. Berechnet einen Confidence-Score und protokolliert das Ergebnis im AppAuditLogger.
 */
object QualityGatekeeper {

    private val GERMAN_MONTH_MAP = mapOf(
        "januar" to "01", "jan" to "01",
        "februar" to "02", "feb" to "02",
        "märz" to "03", "maerz" to "03", "mrz" to "03", "mar" to "03",
        "april" to "04", "apr" to "04",
        "mai" to "05",
        "juni" to "06", "jun" to "06",
        "juli" to "07", "jul" to "07",
        "august" to "08", "aug" to "08",
        "september" to "09", "sep" to "09", "sept" to "09",
        "oktober" to "10", "okt" to "10",
        "november" to "11", "nov" to "11",
        "dezember" to "12", "dez" to "12"
    )

    /**
     * Normalisiert Betragsangaben ("1.000,00 €", "49,99 EUR", "1,250.00")
     * und generiert Varianten für die Suche im OCR-Rohtext.
     */
    fun normalizeAmount(raw: String?): NormalizedAmount? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace("€", "")
            .replace("EUR", "", ignoreCase = true)
            .replace("Euro", "", ignoreCase = true)
            .trim()

        val matcher = Pattern.compile("([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)").matcher(cleaned)
        if (!matcher.find()) return null
        val numStr = matcher.group(1) ?: return null

        val numericVal: Double
        val dotFormatted: String
        val commaFormatted: String
        val simpleComma: String

        if (numStr.contains(".") && numStr.contains(",")) {
            val lastDot = numStr.lastIndexOf('.')
            val lastComma = numStr.lastIndexOf(',')
            if (lastComma > lastDot) {
                // Deutsch: 1.000,50
                val cleanDigits = numStr.replace(".", "").replace(",", ".")
                numericVal = cleanDigits.toDoubleOrNull() ?: return null
            } else {
                // US: 1,000.50
                val cleanDigits = numStr.replace(",", "")
                numericVal = cleanDigits.toDoubleOrNull() ?: return null
            }
        } else if (numStr.contains(",")) {
            // Nur Komma: z.B. "49,99" oder "1000,00"
            val cleanDigits = numStr.replace(",", ".")
            numericVal = cleanDigits.toDoubleOrNull() ?: return null
        } else if (numStr.contains(".")) {
            // Nur Punkt: z.B. "49.99" oder "1.000"
            val parts = numStr.split(".")
            if (parts.size == 2 && parts[1].length == 3 && parts[0].length <= 3) {
                // Wahrscheinlich Tausender-Trennzeichen: 1.000
                numericVal = (parts[0] + parts[1]).toDoubleOrNull() ?: return null
            } else {
                numericVal = numStr.toDoubleOrNull() ?: return null
            }
        } else {
            numericVal = numStr.toDoubleOrNull() ?: return null
        }

        dotFormatted = String.format(Locale.US, "%.2f", numericVal)
        val intPart = numericVal.toLong()
        val decPart = String.format(Locale.US, "%.2f", numericVal).substringAfter(".")
        val formattedIntPart = String.format(Locale.GERMAN, "%,d", intPart).replace("\u00A0", ".")
        commaFormatted = "$formattedIntPart,$decPart"
        simpleComma = "$intPart,$decPart"

        val variants = mutableListOf<String>()
        variants.add(commaFormatted)
        variants.add(simpleComma)
        variants.add(dotFormatted)
        variants.add(numStr)
        if (numStr.contains(",")) {
            variants.add(numStr.replace(",", "."))
        }
        if (numStr.contains(".")) {
            variants.add(numStr.replace(".", ","))
        }
        if (intPart > 0) {
            variants.add(intPart.toString())
        }

        return NormalizedAmount(
            numericValue = numericVal,
            standardFormatted = dotFormatted,
            germanFormatted = commaFormatted,
            simpleGerman = simpleComma,
            searchVariants = variants.distinct()
        )
    }

    /**
     * Normalisiert Datumsangaben ("12. März 2024", "12.03.2024", "2024-03-12") zu "12.03.2024"
     * und generiert Varianten für die Textsuche.
     */
    fun normalizeDate(raw: String?): NormalizedDate? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // 1. Textuelle deutsche Monatsnamen: "12. März 2024" oder "12. Mär. 2024"
        val textMonthPattern = Pattern.compile("(\\d{1,2})\\.?\\s*([A-Za-zäöüÄÖÜß]+)\\.?\\s*(\\d{2,4})")
        val tmMatch = textMonthPattern.matcher(trimmed)
        if (tmMatch.find()) {
            val day = tmMatch.group(1)!!.padStart(2, '0')
            val monthWord = tmMatch.group(2)!!.lowercase()
            var year = tmMatch.group(3)!!
            if (year.length == 2) year = "20$year"

            val monthDigits = GERMAN_MONTH_MAP[monthWord] ?: GERMAN_MONTH_MAP.entries.find { monthWord.startsWith(it.key) }?.value
            if (monthDigits != null) {
                val standard = "$day.$monthDigits.$year"
                val iso = "$year-$monthDigits-$day"
                val variants = listOf(
                    standard,
                    "${day.toInt()}.${monthDigits.toInt()}.$year",
                    "${day}.${monthDigits}.${year.takeLast(2)}",
                    iso,
                    "$day. $monthWord $year",
                    trimmed
                ).distinct()
                return NormalizedDate(standard, iso, variants)
            }
        }

        // 2. ISO-Format: "2024-03-12" oder "2024.03.12"
        val isoPattern = Pattern.compile("(\\d{4})[./\\-](\\d{1,2})[./\\-](\\d{1,2})")
        val isoMatch = isoPattern.matcher(trimmed)
        if (isoMatch.find()) {
            val year = isoMatch.group(1)!!
            val month = isoMatch.group(2)!!.padStart(2, '0')
            val day = isoMatch.group(3)!!.padStart(2, '0')

            val standard = "$day.$month.$year"
            val iso = "$year-$month-$day"
            val variants = listOf(
                standard,
                "${day.toInt()}.${month.toInt()}.$year",
                "${day}.${month}.${year.takeLast(2)}",
                iso,
                trimmed
            ).distinct()
            return NormalizedDate(standard, iso, variants)
        }

        // 3. Numerisches deutsches Format: "12.03.2024" oder "12.3.24"
        val germanNumPattern = Pattern.compile("(?:^|\\b)(\\d{1,2})[./\\-](\\d{1,2})[./\\-](\\d{2,4})")
        val gnMatch = germanNumPattern.matcher(trimmed)
        if (gnMatch.find()) {
            val day = gnMatch.group(1)!!.padStart(2, '0')
            val month = gnMatch.group(2)!!.padStart(2, '0')
            var year = gnMatch.group(3)!!
            if (year.length == 2) year = "20$year"

            val standard = "$day.$month.$year"
            val iso = "$year-$month-$day"
            val variants = listOf(
                standard,
                "${day.toInt()}.${month.toInt()}.$year",
                "${day}.${month}.${year.takeLast(2)}",
                iso,
                trimmed
            ).distinct()
            return NormalizedDate(standard, iso, variants)
        }

        return null
    }

    /**
     * Normalisiert eine IBAN (Entfernt Leerzeichen, prüft DE-Format).
     */
    fun normalizeIban(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.replace("\\s".toRegex(), "").uppercase(Locale.ROOT)
        return if (clean.startsWith("DE") && clean.length in 20..24) clean else null
    }

    /**
     * Sucht im OCR-Text nach einem passenden Fälligkeitsdatum, falls eine Verwechslung vorliegt.
     */
    private fun findAlternativeDueDate(ocrText: String, invalidDate: String): String? {
        val lower = ocrText.lowercase()
        val keywords = listOf("fällig am", "fälligkeit", "zahlbar bis", "zahlungsziel", "bis zum", "frist bis")
        for (kw in keywords) {
            val idx = lower.indexOf(kw)
            if (idx != -1) {
                val snippet = ocrText.substring(idx, (idx + 60).coerceAtMost(ocrText.length))
                val datePattern = Pattern.compile("(\\d{1,2}\\.\\d{1,2}\\.\\d{2,4})")
                val m = datePattern.matcher(snippet)
                if (m.find()) {
                    val candidate = m.group(1)
                    if (candidate != null && candidate != invalidDate) {
                        return candidate
                    }
                }
            }
        }
        return null
    }

    /**
     * Führt den deterministischen Text-Anchor-Cross-Check und die Plausibilitätsprüfung durch.
     */
    fun auditExtraction(
        ocrText: String,
        sender: String? = null,
        docType: String? = null,
        amountStr: String? = null,
        dueDateStr: String? = null,
        docDateStr: String? = null,
        ibanStr: String? = null,
        customFields: Map<String, String> = emptyMap()
    ): QualityAuditReport {
        val checks = mutableListOf<FieldQualityCheck>()
        val warnings = mutableListOf<String>()
        var confidencePenalty = 0.0f
        val lowerOcr = ocrText.lowercase()

        // 1. Cross-Check: Betrag
        val effectiveAmount = amountStr ?: customFields.entries.find {
            it.key.contains("betrag", ignoreCase = true) || it.key.contains("summe", ignoreCase = true)
        }?.value

        if (!effectiveAmount.isNullOrBlank()) {
            val normAmount = normalizeAmount(effectiveAmount)
            if (normAmount != null) {
                val foundInOcr = normAmount.searchVariants.any { lowerOcr.contains(it.lowercase()) }
                var isPlausible = true
                var warning: String? = null

                if (normAmount.numericValue <= 0.0) {
                    isPlausible = false
                    warning = "Betrag ist 0,00 € oder negativ (${effectiveAmount})"
                    warnings.add("Betrag unplausibel: $warning")
                    confidencePenalty += 0.25f
                } else if (!foundInOcr) {
                    warning = "Betrag '${effectiveAmount}' (${normAmount.germanFormatted} €) nicht im OCR-Text gefunden – mögliche Halluzination"
                    warnings.add("Betrag bitte kurz prüfen")
                    confidencePenalty += 0.35f
                }

                checks.add(
                    FieldQualityCheck(
                        fieldName = "Betrag",
                        extractedValue = effectiveAmount,
                        normalizedValue = "${normAmount.germanFormatted} €",
                        isFoundInText = foundInOcr,
                        isPlausible = isPlausible,
                        warningMessage = warning,
                        suggestedCorrection = if (!foundInOcr) null else "${normAmount.germanFormatted} €",
                        confidence = if (foundInOcr && isPlausible) 1.0f else 0.4f
                    )
                )
            } else {
                warnings.add("Betrag '${effectiveAmount}' konnte nicht als Währungswert normalisiert werden")
                confidencePenalty += 0.20f
                checks.add(
                    FieldQualityCheck(
                        fieldName = "Betrag",
                        extractedValue = effectiveAmount,
                        normalizedValue = effectiveAmount,
                        isFoundInText = false,
                        isPlausible = false,
                        warningMessage = "Ungültiges Betragsformat",
                        confidence = 0.3f
                    )
                )
            }
        }

        // 2. Cross-Check: Fälligkeitsdatum & Logik-Plausibilität (z.B. Fälligkeit neben 'geb.')
        val effectiveDueDate = dueDateStr ?: customFields.entries.find {
            it.key.contains("fällig", ignoreCase = true) || it.key.contains("faellig", ignoreCase = true) || it.key.contains("frist", ignoreCase = true)
        }?.value

        if (!effectiveDueDate.isNullOrBlank()) {
            val normDate = normalizeDate(effectiveDueDate)
            if (normDate != null) {
                var matchIndex = -1
                for (v in normDate.searchVariants) {
                    val idx = lowerOcr.indexOf(v.lowercase())
                    if (idx != -1) {
                        matchIndex = idx
                        break
                    }
                }
                val foundInOcr = matchIndex != -1

                var isPlausible = true
                var warning: String? = null
                var suggestedCorr: String? = null

                if (foundInOcr) {
                    // Kontext-Check: Steht das Datum neben "geb." / "geboren" / "geburtsdatum"?
                    val start = (matchIndex - 45).coerceAtLeast(0)
                    val end = (matchIndex + 45).coerceAtMost(lowerOcr.length)
                    val contextSnippet = lowerOcr.substring(start, end)

                    val isBirthContext = contextSnippet.contains("geb.") ||
                            contextSnippet.contains("geboren") ||
                            contextSnippet.contains("geburtsdatum") ||
                            contextSnippet.contains("geburtstag") ||
                            contextSnippet.contains("birth")

                    if (isBirthContext) {
                        isPlausible = false
                        warning = "Fälligkeitsdatum '${normDate.standardFormatted}' steht neben 'geb.' (Geburtsdatum-Verwechslung!)"
                        warnings.add(warning)
                        confidencePenalty += 0.40f

                        // Suche ein alternatives echtes Fälligkeitsdatum
                        suggestedCorr = findAlternativeDueDate(ocrText, normDate.standardFormatted)
                    }
                } else {
                    warning = "Fälligkeitsdatum '${effectiveDueDate}' nicht im OCR-Text gefunden"
                    warnings.add("Fälligkeit bitte kurz prüfen")
                    confidencePenalty += 0.30f
                }

                checks.add(
                    FieldQualityCheck(
                        fieldName = "Fälligkeit",
                        extractedValue = effectiveDueDate,
                        normalizedValue = normDate.standardFormatted,
                        isFoundInText = foundInOcr,
                        isPlausible = isPlausible,
                        warningMessage = warning,
                        suggestedCorrection = suggestedCorr ?: if (foundInOcr && isPlausible) normDate.standardFormatted else null,
                        confidence = if (foundInOcr && isPlausible) 1.0f else 0.35f
                    )
                )
            } else {
                warnings.add("Fälligkeit '${effectiveDueDate}' konnte nicht normalisiert werden")
                confidencePenalty += 0.20f
                checks.add(
                    FieldQualityCheck(
                        fieldName = "Fälligkeit",
                        extractedValue = effectiveDueDate,
                        normalizedValue = effectiveDueDate,
                        isFoundInText = false,
                        isPlausible = false,
                        warningMessage = "Ungültiges Datumsformat",
                        confidence = 0.3f
                    )
                )
            }
        }

        // 3. Cross-Check: Belegdatum
        val effectiveDocDate = docDateStr ?: customFields.entries.find {
            it.key.equals("datum", ignoreCase = true) || it.key.equals("rechnungsdatum", ignoreCase = true)
        }?.value

        if (!effectiveDocDate.isNullOrBlank()) {
            val normDate = normalizeDate(effectiveDocDate)
            if (normDate != null) {
                val foundInOcr = normDate.searchVariants.any { lowerOcr.contains(it.lowercase()) }
                checks.add(
                    FieldQualityCheck(
                        fieldName = "Belegdatum",
                        extractedValue = effectiveDocDate,
                        normalizedValue = normDate.standardFormatted,
                        isFoundInText = foundInOcr,
                        isPlausible = true,
                        warningMessage = if (!foundInOcr) "Belegdatum nicht im Text verifiziert" else null,
                        confidence = if (foundInOcr) 1.0f else 0.7f
                    )
                )
                if (!foundInOcr) {
                    confidencePenalty += 0.10f
                }
            }
        }

        // 4. Cross-Check: IBAN
        val effectiveIban = ibanStr ?: customFields.entries.find {
            it.key.contains("iban", ignoreCase = true)
        }?.value

        if (!effectiveIban.isNullOrBlank()) {
            val normIban = normalizeIban(effectiveIban)
            if (normIban != null) {
                val formattedIban = normIban.chunked(4).joinToString(" ")
                val cleanOcr = lowerOcr.replace(" ", "")
                val foundInOcr = cleanOcr.contains(normIban.lowercase()) || lowerOcr.contains(formattedIban.lowercase())

                var isPlausible = true
                var warning: String? = null
                if (normIban.length != 22) {
                    isPlausible = false
                    warning = "IBAN Länge unplausibel (${normIban.length} statt 22 Zeichen für DE)"
                    warnings.add("IBAN unvollständig")
                    confidencePenalty += 0.25f
                } else if (!foundInOcr) {
                    warning = "IBAN nicht im OCR-Text gefunden – mögliche Halluzination"
                    warnings.add("IBAN bitte kurz prüfen")
                    confidencePenalty += 0.30f
                }

                checks.add(
                    FieldQualityCheck(
                        fieldName = "IBAN",
                        extractedValue = effectiveIban,
                        normalizedValue = formattedIban,
                        isFoundInText = foundInOcr,
                        isPlausible = isPlausible,
                        warningMessage = warning,
                        suggestedCorrection = formattedIban,
                        confidence = if (foundInOcr && isPlausible) 1.0f else 0.4f
                    )
                )
            } else {
                warnings.add("IBAN Format ungültig: '$effectiveIban'")
                confidencePenalty += 0.25f
                checks.add(
                    FieldQualityCheck(
                        fieldName = "IBAN",
                        extractedValue = effectiveIban,
                        normalizedValue = effectiveIban,
                        isFoundInText = false,
                        isPlausible = false,
                        warningMessage = "Ungültige IBAN",
                        confidence = 0.2f
                    )
                )
            }
        }

        // 5. Cross-Check: Kundennummer & Rechnungsnummer aus customFields
        customFields.forEach { (key, value) ->
            if (key.equals("kundennummer", ignoreCase = true) || key.equals("rechnungsnummer", ignoreCase = true) || key.equals("vertragsnummer", ignoreCase = true)) {
                if (value.isNotBlank()) {
                    val cleanVal = value.trim().lowercase()
                    val foundInOcr = lowerOcr.contains(cleanVal) || lowerOcr.replace("-", "").contains(cleanVal.replace("-", ""))
                    if (!foundInOcr) {
                        warnings.add("$key '$value' nicht im Text verifiziert")
                        confidencePenalty += 0.10f
                    }
                    checks.add(
                        FieldQualityCheck(
                            fieldName = key,
                            extractedValue = value,
                            normalizedValue = value.trim(),
                            isFoundInText = foundInOcr,
                            isPlausible = true,
                            warningMessage = if (!foundInOcr) "$key nicht im Text gefunden" else null,
                            confidence = if (foundInOcr) 1.0f else 0.6f
                        )
                    )
                }
            }
        }

        // Gesamt-Score berechnen
        val calculatedConfidence = (1.0f - confidencePenalty).coerceIn(0.10f, 1.0f)
        val isApproved = calculatedConfidence >= 0.70f && checks.none { !it.isPlausible }

        val summary = buildString {
            append("Revision Team 4: ")
            append(if (isApproved) "✅ FREIGEGEBEN" else "⚠️ PRÜFHINWEISE")
            append(" (Score: ${(calculatedConfidence * 100).toInt()}%) | ")
            append("Geprüfte Felder: ${checks.size}")
            if (warnings.isNotEmpty()) {
                append(" | Warnungen: ${warnings.joinToString(" • ")}")
            }
        }

        val report = QualityAuditReport(
            overallConfidence = calculatedConfidence,
            isApproved = isApproved,
            fieldChecks = checks,
            warnings = warnings,
            auditSummary = summary
        )

        // Protokollierung im 100% lokalen AppAuditLogger
        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "QualityGatekeeper",
            message = "Team 4 Revision: Score ${(calculatedConfidence * 100).toInt()}% (${if (isApproved) "Freigabe" else "Prüfhinweis"})",
            details = summary
        )

        return report
    }

    /**
     * Audit für eine [DocRule] im Regel-Vorschlagsdialog.
     */
    fun auditRule(ocrText: String, rule: DocRule): QualityAuditReport {
        return auditExtraction(
            ocrText = ocrText,
            sender = rule.detectedSender,
            docType = rule.targetDocType,
            customFields = rule.targetCustomFields
        )
    }

    /**
     * Audit für ein [DocumentEntity] im Dokumenten-Detailmodal.
     */
    fun auditDocument(
        document: DocumentEntity,
        customFields: Map<String, String> = emptyMap()
    ): QualityAuditReport {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
        return auditExtraction(
            ocrText = document.ocrText,
            sender = document.sender,
            docType = document.docType,
            amountStr = if (document.amount != null && document.amount > 0.0) String.format(Locale.GERMAN, "%.2f", document.amount) else null,
            dueDateStr = document.cancellationDeadline?.let { sdf.format(Date(it)) },
            customFields = customFields
        )
    }
}
