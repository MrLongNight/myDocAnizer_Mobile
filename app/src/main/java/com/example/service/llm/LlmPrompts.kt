package com.example.service.llm

object LlmPrompts {

    fun extractAmountFromText(text: String): String {
        val highPriorityPattern = Regex(
            "(?:gesamtbetrag|rechnungsbetrag|endbetrag|zahlbetrag|zu zahlen|fälliger betrag|auszahlungsbetrag|monatsbeitrag|jahresbeitrag|summe|saldo|betrag)\\s*[:\\s]?\\s*(?:eur|€)?\\s*([0-9]{1,4}(?:[.,][0-9]{3})*[.,][0-9]{2})",
            RegexOption.IGNORE_CASE
        )
        val match = highPriorityPattern.find(text)
        if (match != null) {
            return "${match.groupValues[1]} €"
        }

        val suffixPattern = Regex("([0-9]{1,4}(?:[.,][0-9]{3})*[.,][0-9]{2})\\s*(?:€|eur|euro)", RegexOption.IGNORE_CASE)
        val allMatches = suffixPattern.findAll(text).toList()
        if (allMatches.isNotEmpty()) {
            val last = allMatches.last()
            return "${last.groupValues[1]} €"
        }

        return ""
    }

    fun extractDueDateFromText(text: String): String {
        val pattern = Regex(
            "(?:fällig bis|fällig am|zahlbar bis|fälligkeit|spätestens bis|frist bis|zahlungsziel|einreichungsfrist|einspruchsfrist)\\s*[:\\s]?\\s*([0-9]{1,2}[.][0-9]{1,2}[.](?:20)?[0-9]{2})",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    fun extractDocumentDateFromText(text: String): String {
        val pattern = Regex(
            "(?:rechnungsdatum|belegdatum|vertragsdatum|ausstellungsdatum|datum|vom)\\s*[:\\s]?\\s*([0-9]{1,2}[.][0-9]{1,2}[.](?:20)?[0-9]{2})",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(text)
        if (match != null) return match.groupValues[1]

        val standalone = Regex("\\b([0-9]{1,2}\\.[0-9]{1,2}\\.(?:20)[0-9]{2})\\b")
        val m2 = standalone.find(text)
        return m2?.groupValues?.get(1) ?: ""
    }

    fun extractCustomerNumber(text: String): String {
        val pattern = Regex(
            "(?:kundennummer|kunden-nr|kunden-id|kd-nr|customer\\s*id|mitgliedsnummer)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_/]{3,20})",
            RegexOption.IGNORE_CASE
        )
        val candidate = pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
        if (candidate.startsWith("DE", ignoreCase = true) && candidate.length > 15) return ""
        if (candidate.matches(Regex("[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{2,4}"))) return ""
        return candidate
    }

    fun extractInvoiceNumber(text: String): String {
        val pattern = Regex(
            "(?:rechnungsnummer|rechnungs-nr|rechnung-nr|rechnungs\\s*nr|belegnummer|beleg-nr|rg-nr|invoice\\s*no)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_/]{3,25})",
            RegexOption.IGNORE_CASE
        )
        val candidate = pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
        if (candidate.matches(Regex("[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{2,4}"))) return ""
        return candidate
    }

    fun extractPolicyNumber(text: String): String {
        val pattern = Regex(
            "(?:versicherungsschein-nr|versicherungsnummer|policennummer|police-nr|vertragsnummer|vertrags-nr)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_/]{4,25})",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractContractAccount(text: String): String {
        val pattern = Regex(
            "(?:vertragskonto|vertragskonto-nr|buchungskonto|vertrags-id)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_/]{4,20})",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractMeterNumber(text: String): String {
        val pattern = Regex(
            "(?:zählernummer|zähler-nr|zählerstand|zähler)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_]{4,16})",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractLicensePlate(text: String): String {
        val pattern = Regex("\\b([A-ZÄÖÜ]{1,3}[ -][A-Z]{1,2}[ -]?[0-9]{1,4})\\b")
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractTaxId(text: String): String {
        val pattern = Regex(
            "(?:steuernummer|steuer-id|steuer-identifikationsnummer|ust-idnr|ust-id|idnr|aktenzeichen)\\s*[:\\s]?\\s*([0-9/\\sA-Za-z\\-_]{8,20})",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    fun extractSenderFromHeader(text: String): String? {
        val lower = text.lowercase()
        when {
            lower.contains("deutsche post") || lower.contains("postbank") -> return "Deutsche Post"
            lower.contains("dhl") -> return "DHL Paket"
            lower.contains("vodafone") -> return "Vodafone GmbH"
            lower.contains("telekom") || lower.contains("magenta") -> return "Deutsche Telekom"
            lower.contains("o2") || lower.contains("telefonica") -> return "Telefónica O2"
            lower.contains("1&1") -> return "1&1 Telecom"
            lower.contains("allianz") -> return "Allianz"
            lower.contains("ergo") -> return "ERGO Versicherung"
            lower.contains("huk") -> return "HUK-COBURG"
            lower.contains("finanzamt") -> return "Finanzamt"
            lower.contains("stadtwerke") -> return "Stadtwerke"
            lower.contains("e.on") || lower.contains("eon") -> return "E.ON Energie"
            lower.contains("vattenfall") -> return "Vattenfall"
            lower.contains("barmer") -> return "BARMER"
            lower.contains("techniker krankenkasse") || lower.contains("tk ") -> return "Techniker Krankenkasse"
            lower.contains("aok") -> return "AOK"
            lower.contains("sparkasse") -> return "Sparkasse"
            lower.contains("volksbank") || lower.contains("raiffeisenbank") -> return "Volksbank"
            lower.contains("ing-diba") || lower.contains("ing diba") || Regex("(^|[^a-zäöüß])ing([^a-zäöüß]|$)", RegexOption.IGNORE_CASE).containsMatchIn(lower) -> return "ING"
            lower.contains("dkb") -> return "DKB"
            lower.contains("commerzbank") -> return "Commerzbank"
            lower.contains("deutsche bank") -> return "Deutsche Bank"
            lower.contains("adac") -> return "ADAC"
            lower.contains("amazon") -> return "Amazon"
            lower.contains("paypal") -> return "PayPal"
            lower.contains("klarna") -> return "Klarna"
        }

        val candidateLines = text.lines().take(10).map { it.trim() }
            .filter { line ->
                val lineLower = line.lowercase()
                line.length in 3..40 &&
                !lineLower.contains("rechnung") &&
                !lineLower.contains("datum") &&
                !lineLower.contains("seite") &&
                !lineLower.contains("sehr geehrte") &&
                !lineLower.contains("ihre kundennummer") &&
                !lineLower.contains("fälligkeit") &&
                !lineLower.contains("herzlich willkommen") &&
                !lineLower.contains("ftut") &&
                !lineLower.contains("dec1") &&
                !lineLower.contains("fe6d") &&
                !lineLower.contains("dv 09") &&
                !line.matches(Regex("^[0-9A-Fa-f\\s\\-_/]{8,}$"))
            }
        return candidateLines.firstOrNull()
    }
}
