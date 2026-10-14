package com.example.service.llm

import com.example.model.DocumentEntity
import com.example.service.LocalAiChatResult

class LlmToolExecutor {

    companion object {
        private val STOP_WORDS = setOf("wie", "viele", "habe", "ich", "was", "und", "der", "die", "das", "ein", "eine", "den", "dem", "des", "zu", "von", "im", "in", "mit", "für")
    }

    fun executeDocumentAssistant(question: String, rawDocuments: List<DocumentEntity>): String {
        val documents = rawDocuments.sortedByDescending { it.createdAt }.take(30)
        val qLower = question.lowercase().trim()
        val now = System.currentTimeMillis()

        // 1. Spezifische Frage nach Verträgen / Vertragslaufzeiten
        val isContractQuery = qLower.contains("vertrag") || qLower.contains("verträge") || 
                              qLower.contains("laufzeit") || qLower.contains("abos") || qLower.contains("kündig")
        if (isContractQuery) {
            val contracts = documents.filter { doc ->
                doc.docType.contains("vertrag", ignoreCase = true) ||
                doc.mainCategory.contains("vertrag", ignoreCase = true) ||
                doc.title.contains("vertrag", ignoreCase = true) ||
                doc.contractEndDate != null ||
                doc.cancellationDeadline != null ||
                doc.tags.contains("vertrag", ignoreCase = true)
            }

            if (contracts.isEmpty()) {
                return "📊 Vertragslage:\nDu hast aktuell noch keine als Vertrag markierten Dokumente im Dokumenten-Tresor hinterlegt.\n\nTipp: Du kannst beim Scannen oder im Explorer bei jedem Dokument einen Vertragstyp, das Vertragsende und eine Kündigungsfrist festlegen!"
            }

            val sb = StringBuilder()
            sb.append("📋 Du hast aktuell **${contracts.size} laufende(n) Vertrag/Verträge** erfasst:\n\n")
            
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
            contracts.forEachIndexed { index, doc ->
                sb.append("**${index + 1}. ${doc.title}**\n")
                sb.append("   • Vertragspartner/Absender: ${doc.sender.ifBlank { "Nicht angegeben" }}\n")
                sb.append("   • Kategorie: ${doc.mainCategory} ➔ ${doc.subCategory}\n")
                
                if (doc.contractEndDate != null) {
                    val endStr = sdf.format(java.util.Date(doc.contractEndDate))
                    val daysLeft = ((doc.contractEndDate - now) / (1000 * 60 * 60 * 24)).toInt()
                    sb.append("   • Vertragsende: $endStr (${if (daysLeft > 0) "noch $daysLeft Tage" else "bereits abgelaufen"})\n")
                }
                if (doc.cancellationDeadline != null) {
                    val cancelStr = sdf.format(java.util.Date(doc.cancellationDeadline))
                    val daysCancel = ((doc.cancellationDeadline - now) / (1000 * 60 * 60 * 24)).toInt()
                    sb.append("   • ⚠️ Kündigungsfrist: $cancelStr (${if (daysCancel > 0) "noch $daysCancel Tage Frist" else "Frist verstrichen"})\n")
                }
                if (doc.amount != null && doc.amount > 0.0) {
                    sb.append("   • Betrag: ${String.format(java.util.Locale.GERMAN, "%.2f €", doc.amount)}\n")
                }
                sb.append("\n")
            }
            
            val upcomingCancellations = contracts.filter { 
                it.cancellationDeadline != null && it.cancellationDeadline > now && 
                ((it.cancellationDeadline - now) / (1000 * 60 * 60 * 24)) <= 60 
            }
            if (upcomingCancellations.isNotEmpty()) {
                sb.append("🔔 **Wichtiger Hinweis:** Bei ${upcomingCancellations.size} Vertrag/Verträgen läuft in den nächsten 60 Tagen die Kündigungsfrist ab!")
            }
            return sb.toString()
        }

        // 2. Frage nach bevorstehenden Fristen / Terminen
        val isDeadlineQuery = qLower.contains("frist") || qLower.contains("termin") || 
                              qLower.contains("ablauf") || qLower.contains("wann") || qLower.contains("fällig")
        if (isDeadlineQuery) {
            val withDeadlines = documents.filter { it.cancellationDeadline != null || it.contractEndDate != null }
                .sortedBy { it.cancellationDeadline ?: it.contractEndDate ?: Long.MAX_VALUE }

            if (withDeadlines.isEmpty()) {
                return "📅 Keine Fristen gefunden: Es sind aktuell keine Dokumente mit hinterlegten Kündigungs- oder Vertragsfristen vorhanden."
            }

            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
            val sb = StringBuilder()
            sb.append("📅 **Übersicht der anstehenden Termine & Fristen:**\n\n")
            withDeadlines.forEachIndexed { i, doc ->
                val targetDate = doc.cancellationDeadline ?: doc.contractEndDate ?: return@forEachIndexed
                val days = ((targetDate - now) / (1000 * 60 * 60 * 24)).toInt()
                val icon = if (days < 14) "🚨" else if (days < 45) "⚠️" else "🗓️"
                val label = if (doc.cancellationDeadline != null) "Kündigungsfrist" else "Laufzeitende"
                sb.append("$icon **${doc.title}** (${doc.sender})\n")
                sb.append("   • $label: ${sdf.format(java.util.Date(targetDate))} (${if (days > 0) "in $days Tagen" else "überfällig"})\n\n")
            }
            return sb.toString()
        }

        // 3. Frage nach Finanzen / Rechnungen / Beträgen
        val isFinanceQuery = qLower.contains("rechnung") || qLower.contains("ausgabe") || 
                             qLower.contains("kosten") || qLower.contains("geld") || qLower.contains("euro") || qLower.contains("bezahl")
        if (isFinanceQuery) {
            val invoices = documents.filter { 
                it.docType.contains("rechnung", ignoreCase = true) ||
                it.mainCategory.contains("finanz", ignoreCase = true) ||
                it.title.contains("rechnung", ignoreCase = true) ||
                (it.amount != null && it.amount > 0.0)
            }

            val totalAmount = invoices.mapNotNull { it.amount }.sum()
            val sb = StringBuilder()
            sb.append("💶 **Finanzen & Rechnungs-Übersicht:**\n")
            sb.append("Es wurden **${invoices.size} Rechnungs-/Finanzdokumente** gefunden.\n")
            if (totalAmount > 0.0) {
                sb.append("Erfasste Gesamtsumme: **${String.format(java.util.Locale.GERMAN, "%.2f €", totalAmount)}**\n\n")
            } else {
                sb.append("\n")
            }

            invoices.take(5).forEach { doc ->
                val amtStr = if (doc.amount != null && doc.amount > 0.0) " (${String.format(java.util.Locale.GERMAN, "%.2f €", doc.amount)})" else ""
                sb.append("• **${doc.title}** von ${doc.sender}$amtStr\n")
            }
            return sb.toString()
        }

        // 4. Allgemeine Textsuche & Zusammenfassung über OCR-Text und Metadaten
        val searchTokens = qLower.split(" ").filter { it.length > 2 && it !in STOP_WORDS }
        val matchedDocs = if (searchTokens.isNotEmpty()) {
            documents.filter { doc ->
                searchTokens.any { t ->
                    doc.title.contains(t, ignoreCase = true) ||
                    doc.sender.contains(t, ignoreCase = true) ||
                    doc.mainCategory.contains(t, ignoreCase = true) ||
                    doc.subCategory.contains(t, ignoreCase = true) ||
                    doc.ocrText.contains(t, ignoreCase = true) ||
                    doc.tags.contains(t, ignoreCase = true)
                }
            }
        } else {
            documents.take(3)
        }

        if (matchedDocs.isNotEmpty()) {
            val sb = StringBuilder()
            sb.append("🔍 **Gefundene Treffer zu deiner Frage:**\n\n")
            matchedDocs.take(5).forEach { doc ->
                sb.append("• **${doc.title}** (${doc.mainCategory} ➔ ${doc.subCategory})\n")
                sb.append("  Absender: ${doc.sender.ifBlank { "Unbekannt" }} | Erstellt: ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN).format(java.util.Date(doc.createdAt))}\n")
                if (doc.ocrText.isNotBlank()) {
                    val snippet = doc.ocrText.replace("\n", " ").take(140)
                    sb.append("  *Auszug: „$snippet...“*\n")
                }
                sb.append("\n")
            }
            sb.append("💡 *Alle Auswertungen erfolgten 100% lokal auf deinem Smartphone ohne Cloud-Zugriff.*")
            return sb.toString()
        }

        return "ℹ️ Zu deiner Anfrage „$question“ konnten keine direkten Treffer im Dokumenten-Tresor gefunden werden.\n\nDu hast aktuell ${documents.size} Dokumente archiviert. Versuche Stichworte wie z.B. „Verträge“, „Fristen“, „Rechnungen“ oder Namen von Absendern."
    }

    fun executeAssistantWithFollowUps(
        question: String,
        rawDocuments: List<DocumentEntity>,
        attachedDoc: DocumentEntity? = null
    ): LocalAiChatResult {
        val documents = rawDocuments.sortedByDescending { it.createdAt }.take(30)
        val q = question.trim()
        val qLower = q.lowercase()
        val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
        val now = System.currentTimeMillis()

        // 0. APP-STEUERUNG & FUNKTIONSBEFEHLE (TOOL CALLING)
        val statusMatch = Regex("(?:status|dokument|beleg)\\s+(?:von\\s+)?(?:id\\s+|#)?([0-9]+|[a-zA-Z0-9_-]+)\\s+(?:auf|als)\\s+([a-zA-ZäöüÄÖÜ\\s]+)", RegexOption.IGNORE_CASE).find(q)
        if (statusMatch != null && (qLower.contains("status") || qLower.contains("setz") || qLower.contains("änder") || qLower.contains("markier"))) {
            val docId = statusMatch.groupValues[1].trim()
            val rawStatus = statusMatch.groupValues[2].trim().replace(".", "")
            val cleanStatus = when {
                rawStatus.contains("bezahlt", ignoreCase = true) -> "Bezahlt"
                rawStatus.contains("offen", ignoreCase = true) -> "Offen"
                rawStatus.contains("gekündigt", ignoreCase = true) -> "Gekündigt"
                rawStatus.contains("prüfung", ignoreCase = true) || rawStatus.contains("prüfen", ignoreCase = true) -> "Prüfen"
                else -> rawStatus.replaceFirstChar { it.uppercase() }
            }
            val toolJson = """
                ```json
                {
                  "name": "updateDocumentStatus",
                  "arguments": {
                    "docId": "$docId",
                    "newStatus": "$cleanStatus"
                  }
                }
                ```
            """.trimIndent()
            return LocalAiChatResult(replyText = toolJson)
        }

        if (qLower.contains("filter") || (qLower.contains("zeige") && (qLower.contains("offen") || qLower.contains("bezahlt") || qLower.contains("gekündigt")))) {
            var sender: String? = null
            var status: String? = null
            var category: String? = null

            when {
                qLower.contains("telekom") -> sender = "Telekom"
                qLower.contains("vodafone") -> sender = "Vodafone"
                qLower.contains("allianz") -> sender = "Allianz"
                qLower.contains("finanzamt") -> sender = "Finanzamt"
                qLower.contains("stadtwerke") -> sender = "Stadtwerke"
                qLower.contains("post") || qLower.contains("dhl") -> sender = "Deutsche Post"
                qLower.contains("nach ") -> {
                    val extracted = q.substringAfter("nach ").trim().split(" ", ",", ".").firstOrNull()?.replaceFirstChar { it.uppercase() }
                    if (extracted !in listOf("Status", "Kategorie", "Offen", "Bezahlt", "Gekündigt")) {
                        sender = extracted
                    }
                }
                qLower.contains("von ") -> {
                    sender = q.substringAfter("von ").trim().split(" ", ",", ".").firstOrNull()?.replaceFirstChar { it.uppercase() }
                }
            }

            when {
                qLower.contains("offen") -> status = "Offen"
                qLower.contains("bezahlt") -> status = "Bezahlt"
                qLower.contains("gekündigt") -> status = "Gekündigt"
                qLower.contains("prüfen") -> status = "Prüfen"
            }

            when {
                qLower.contains("rechnung") || qLower.contains("finanz") -> category = "Finanzen"
                qLower.contains("vertrag") || qLower.contains("verträge") -> category = "Verträge"
                qLower.contains("versicherung") -> category = "Versicherungen"
                qLower.contains("steuer") -> category = "Steuern"
            }

            val argsList = mutableListOf<String>()
            sender?.let { argsList.add("\"sender\": \"$it\"") }
            status?.let { argsList.add("\"status\": \"$it\"") }
            category?.let { argsList.add("\"category\": \"$it\"") }

            val toolJson = """
                ```json
                {
                  "name": "filterDocuments",
                  "arguments": {
                    ${argsList.joinToString(",\n                    ")}
                  }
                }
                ```
            """.trimIndent()
            return LocalAiChatResult(replyText = toolJson)
        }

        if (qLower.contains("scan") || qLower.contains("kamera") || qLower.contains("abfotografieren") || qLower.contains("foto erfassen") || qLower.contains("dokument einscannen")) {
            return LocalAiChatResult(
                replyText = "Ich öffne direkt den Dokumenten-Scanner für dich! 📷\n\nDort kannst du deine Belege oder Verträge mit automatischer Kantenerkennung erfassen und als durchsuchbares PDF archivieren.",
                actionType = "OPEN_SCANNER",
                actionLabel = "📷 Scanner jetzt öffnen"
            )
        }

        if ((qLower.contains("öffne") || qLower.contains("gehe") || qLower.contains("zeig")) && (qLower.contains("dashboard") || qLower.contains("übersicht") || qLower.contains("ausgabenverlauf") || qLower.contains("finanzüber"))) {
            return LocalAiChatResult(
                replyText = "Hier geht es direkt zu deinem persönlichen Dashboard! 📊\n\nIm Dashboard hast du den monatlichen Ausgabenverlauf, das Fristen-Radar und den Schnellzugriff auf wichtige Ordner im Blick.",
                actionType = "OPEN_DASHBOARD",
                actionLabel = "📊 Zum Dashboard"
            )
        }

        if (qLower.contains("einstellung") || qLower.contains("settings") || qLower.contains("design") || qLower.contains("theme") || qLower.contains("farb") || qLower.contains("skin")) {
            return LocalAiChatResult(
                replyText = "In den Einstellungen kannst du das Farbschema anpassen, Speicherpfade konfigurieren, automatische Erkennungsregeln pflegen und lokale Backups anlegen.",
                actionType = "OPEN_SETTINGS",
                actionLabel = "⚙️ Einstellungen öffnen"
            )
        }

        // 0.3 ORDNER ERSTELLEN (TOOL CALLING)
        if ((qLower.contains("neu") || qLower.contains("erstell") || qLower.contains("anleg") || qLower.contains("mach")) && (qLower.contains("ordner") || qLower.contains("kategorie") || qLower.contains("verzeichnis"))) {
            var folderName: String? = null
            var parentCategory: String? = null

            val quoteMatch = Regex("[\"']([^\"']+)[\"']").find(question)
            if (quoteMatch != null) {
                folderName = quoteMatch.groupValues[1].trim()
            }

            if (folderName == null) {
                val nameMatch = Regex("(?:namens|mit dem namen|mit namen|heißt|bezeichnung)\\s+([a-zA-Z0-9äöüÄÖÜ_\\-\\s]+)", RegexOption.IGNORE_CASE).find(question)
                if (nameMatch != null) {
                    folderName = nameMatch.groupValues[1].trim().split(Regex("[,.;]")).firstOrNull()?.trim()
                }
            }

            if (folderName == null) {
                val ordnerMatch = Regex("(?:erstelle|anlegen|neuen?)\\s+(?:einen?\\s+)?(?:neuen?\\s+)?ordner\\s+(?:für\\s+)?([a-zA-Z0-9äöüÄÖÜ_\\-\\s]+)", RegexOption.IGNORE_CASE).find(question)
                if (ordnerMatch != null) {
                    val candidate = ordnerMatch.groupValues[1].trim()
                        .replace(Regex("(?:erstellen|anlegen|bitte|jetzt|im tresor|unter|in).*$"), "")
                        .trim()
                    if (candidate.isNotBlank() && !candidate.equals("erstellen", ignoreCase = true) && !candidate.equals("anlegen", ignoreCase = true)) {
                        folderName = candidate
                    }
                }
            }

            val parentMatch = Regex("(?:unter|in der kategorie|im ordner|in)\\s+([a-zA-Z0-9äöüÄÖÜ_\\-]+)", RegexOption.IGNORE_CASE).find(question)
            if (parentMatch != null) {
                val p = parentMatch.groupValues[1].trim()
                if (!p.equals("ordner", ignoreCase = true) && !p.equals("tresor", ignoreCase = true) && !p.equals("app", ignoreCase = true)) {
                    parentCategory = p.replaceFirstChar { it.uppercase() }
                }
            }

            val finalFolderName = folderName?.takeIf { it.isNotBlank() } ?: "Neuer Ordner"

            val argsContent = if (parentCategory != null) {
                """"folderName": "$finalFolderName",
                "parentCategory": "$parentCategory""""
            } else {
                """"folderName": "$finalFolderName""""
            }

            return LocalAiChatResult(
                replyText = "```json\n{\n  \"name\": \"createFolder\",\n  \"arguments\": {\n    $argsContent\n  }\n}\n```",
                actionType = null,
                actionLabel = null
            )
        }

        if (qLower.contains("papierkorb") || qLower.contains("gelöscht") || qLower.contains("mülleimer")) {
            return LocalAiChatResult(
                replyText = "Gelöschte Dokumente werden 30 Tage lang im Papierkorb aufbewahrt und können dort jederzeit mit einem Klick wiederhergestellt werden.",
                actionType = "OPEN_TRASH",
                actionLabel = "🗑️ Zum Papierkorb"
            )
        }

        if ((qLower.contains("zeig") || qLower.contains("öffne") || qLower.contains("gehe")) && (qLower.contains("frist") || qLower.contains("radar") || qLower.contains("termine"))) {
            return LocalAiChatResult(
                replyText = "Ich öffne das Fristen-Radar für dich! ⏰\n\nHier siehst du alle kommenden Kündigungsstichtage, Ablaufdaten und Zahlungsziele auf einen Blick.",
                actionType = "OPEN_DEADLINES",
                actionLabel = "⏰ Fristen-Radar aufrufen"
            )
        }

        if (qLower.contains("import") || qLower.contains("pdf import") || qLower.contains("hochladen")) {
            return LocalAiChatResult(
                replyText = "Über den Datei-Import kannst du bestehende PDFs und Bilder direkt in den Tresor laden.",
                actionType = "OPEN_IMPORT",
                actionLabel = "📥 Datei-Import öffnen"
            )
        }

        // HILFE & FUNKTIONSÜBERSICHT
        if (qLower.contains("hilfe") || qLower.contains("was kannst du") || qLower.contains("wie funktion") || qLower.contains("anleitung") || qLower == "?") {
            val helpText = """
Hier ist ein Überblick, wie ich dir helfen kann:

📂 **Dokumenten-Tresor:**
• Fragen zu Akten: „Welche Rechnungen habe ich?“
• Suche: „Finde Dokumente von Telekom“
• Dokumenten-Fokus: Hefte eine Akte über 📎 an für Detailfragen

✍️ **Verträge & Rechtliches:**
• „Welche Verträge habe ich und wann enden sie?“
• „Erstelle mir ein Kündigungsschreiben“
• „Welche Aufbewahrungsfristen gelten gesetzlich?“

⚡ **App-Steuerung (direkt per Text):**
• „Öffne den Scanner“
• „Gehe zum Dashboard“
• „Neuen Ordner erstellen“
• „Einstellungen öffnen“
• „Papierkorb anzeigen“

Alle Auswertungen erfolgen zu 100% lokal auf deinem Smartphone ohne Internet.
""".trimIndent()
            return LocalAiChatResult(replyText = helpText)
        }

        // 1. Kündigungsschreiben verfassen
        if (qLower.contains("kündigung") && (qLower.contains("schreiben") || qLower.contains("muster") || qLower.contains("erstell") || qLower.contains("verfass") || qLower.contains("brief") || qLower.contains("vorlage"))) {
            val targetDoc = attachedDoc ?: documents.firstOrNull { doc ->
                val title = doc.title.lowercase()
                val sender = doc.sender.lowercase()
                qLower.contains(sender) || qLower.contains(title) || doc.contractEndDate != null
            }

            val recipient = targetDoc?.sender?.ifBlank { "[Name des Anbieters / Vertragspartners]" } ?: "[Name des Anbieters, z.B. Vodafone / Telekom / Versicherung]"
            val contractSubject = targetDoc?.title ?: "[Vertragsbezeichnung / Tarif]"
            val todayStr = sdf.format(java.util.Date(now))

            val letter = """
Hier ist dein rechtssicheres Kündigungsschreiben (nach BGB § 309 Nr. 9 in Textform):

```text
[Dein Vor- und Nachname]
[Deine Straße und Hausnummer]
[Deine PLZ und Ort]
[Deine E-Mail-Adresse]

An:
$recipient
[Kundenservice / Vertragsabteilung]
[Straße und Hausnummer oder E-Mail-Adresse]
[PLZ und Ort]

Datum: $todayStr

Betreff: Kündigung des Vertrags „$contractSubject“
Vertragsnummer / Kundennummer: [Deine Kunden- oder Vertragsnummer]

Sehr geehrte Damen und Herren,

hiermit kündige ich meinen oben genannten Vertrag fristgerecht zum nächstmöglichen Zeitpunkt.

Hilfsweise kündige ich zum nächstzulässigen Termin.

Gleichzeitig widerrufe ich die Ihnen erteilte Einzugsermächtigung zum Zeitpunkt des Wirksamwerdens der Kündigung.

Bitte senden Sie mir eine schriftliche Bestätigung der Kündigung unter Angabe des Beendigungszeitpunkts innerhalb von 14 Tagen per Brief oder E-Mail zu.

Mit freundlichen Grüßen

[Dein Vor- und Nachname]
```

💡 *Tipp: Du kannst diesen Text direkt über den Button kopieren und per E-Mail oder Brief versenden.*
""".trimIndent()

            return LocalAiChatResult(replyText = letter)
        }

        // 2. Frage zu einem konkret angehängten Dokument
        if (attachedDoc != null && (qLower.contains("dies") || qLower.contains("dokument") || qLower.contains("inhalt") || qLower.contains("zusammenfass") || qLower.contains("erklär"))) {
            val sb = StringBuilder()
            sb.append("Hier ist die Detail-Analyse für das Dokument **„${attachedDoc.title}“**:\n\n")
            sb.append("• **Absender:** ${attachedDoc.sender.ifBlank { "Kein Absender hinterlegt" }}\n")
            sb.append("• **Ablageort:** ${attachedDoc.mainCategory} ➔ ${attachedDoc.subCategory}\n")
            sb.append("• **Dokumentendatum:** ${sdf.format(java.util.Date(attachedDoc.createdAt))}\n")
            if (attachedDoc.contractEndDate != null) {
                sb.append("• **Vertragsende:** ${sdf.format(java.util.Date(attachedDoc.contractEndDate))}\n")
            }
            if (attachedDoc.cancellationDeadline != null) {
                sb.append("• **Kündigungsfrist:** ${sdf.format(java.util.Date(attachedDoc.cancellationDeadline))}\n")
            }
            if (attachedDoc.reminderNotes.isNotBlank()) {
                sb.append("• **Notizen:** ${attachedDoc.reminderNotes}\n")
            }
            if (attachedDoc.ocrText.isNotBlank()) {
                val preview = attachedDoc.ocrText.replace("\n", " ").take(350).trim()
                sb.append("\n**Erkannter OCR-Textauszug:**\n„$preview...“\n")
            }
            sb.append("\nWas möchtest du mit diesem Dokument tun?")

            return LocalAiChatResult(replyText = sb.toString())
        }

        // 3. Laufende Verträge & Abonnements
        if (qLower.contains("vertrag") || qLower.contains("verträge") || qLower.contains("abos") || qLower.contains("laufzeit")) {
            val contracts = documents.filter { doc ->
                doc.docType.contains("vertrag", ignoreCase = true) ||
                doc.mainCategory.contains("vertrag", ignoreCase = true) ||
                doc.title.contains("vertrag", ignoreCase = true) ||
                doc.contractEndDate != null ||
                doc.cancellationDeadline != null
            }

            if (contracts.isEmpty()) {
                val reply = "Ich habe aktuell noch keine expliziten Verträge in deinem Tresor gefunden.\n\n" +
                        "💡 Tipp: Du kannst Verträge im Dokumenten-Tresor unter der Kategorie „Verträge“ ablegen oder beim Bearbeiten eines Dokuments das Feld „Vertragsende“ ausfüllen, damit ich sie automatisch tracke."
                return LocalAiChatResult(replyText = reply)
            }

            val sb = StringBuilder()
            sb.append("Du hast aktuell **${contracts.size} laufende(n) Vertrag / Verträge** in deinem Tresor:\n\n")
            contracts.forEachIndexed { i, c ->
                sb.append("${i + 1}. **${c.title}** (${c.sender})\n")
                if (c.contractEndDate != null) sb.append("   • Vertragsende: ${sdf.format(java.util.Date(c.contractEndDate))}\n")
                if (c.cancellationDeadline != null) sb.append("   • Kündigungsfrist: ${sdf.format(java.util.Date(c.cancellationDeadline))}\n")
                sb.append("   • Ordner: ${c.mainCategory} ➔ ${c.subCategory}\n\n")
            }
            sb.append("Möchtest du für einen dieser Verträge ein Kündigungsschreiben erstellen?")

            return LocalAiChatResult(replyText = sb.toString())
        }

        // 4. Fristen & Kündigungsfristen
        if (qLower.contains("frist") || qLower.contains("fällig") || qLower.contains("termin") || qLower.contains("ablauf")) {
            val withDeadlines = documents.filter { it.cancellationDeadline != null || it.contractEndDate != null }
            if (withDeadlines.isEmpty()) {
                val reply = "Aktuell sind in deinem Tresor keine Dokumente mit aktivem Kündigungs- oder Ablaufdatum hinterlegt.\n\n" +
                        "Alle Fristen, die du beim Scannen oder Bearbeiten im Feld „Kündigungsfrist“ einträgst, liste ich dir hier sekundenschnell auf."
                return LocalAiChatResult(replyText = reply)
            }

            val sb = StringBuilder()
            sb.append("Hier ist deine Übersicht aller anstehenden Fristen (**${withDeadlines.size} Einträge**):\n\n")
            withDeadlines.sortedBy { it.cancellationDeadline ?: it.contractEndDate }.forEachIndexed { idx, d ->
                val targetDate = d.cancellationDeadline ?: d.contractEndDate ?: return@forEachIndexed
                val days = ((targetDate - now) / (1000 * 60 * 60 * 24)).toInt()
                val icon = if (days < 14) "🚨" else if (days < 45) "⚠️" else "🗓️"
                sb.append("${idx + 1}. $icon **${sdf.format(java.util.Date(targetDate))}** – „${d.title}“ (${d.sender})\n")
                sb.append("   Ordner: ${d.mainCategory} (${if (days > 0) "in $days Tagen" else "überfällig"})\n")
            }
            sb.append("\nSoll ich ein Musterschreiben für eine dieser Fristen aufsetzen?")
            return LocalAiChatResult(
                replyText = sb.toString(),
                actionType = "OPEN_DEADLINES",
                actionLabel = "⏰ Fristen-Radar aufrufen"
            )
        }

        // 5. Ausgaben, Finanzen & Rechnungen
        if (qLower.contains("ausgabe") || qLower.contains("kosten") || qLower.contains("rechnung") || qLower.contains("geld") || qLower.contains("euro") || qLower.contains("finanz")) {
            val invoices = documents.filter { doc ->
                doc.docType.contains("rechnung", ignoreCase = true) ||
                doc.mainCategory.contains("finanz", ignoreCase = true) ||
                doc.tags.contains("rechnung", ignoreCase = true) ||
                doc.title.contains("rechnung", ignoreCase = true) ||
                (doc.amount != null && doc.amount > 0.0)
            }

            val totalAmount = invoices.mapNotNull { it.amount }.sum()
            val sb = StringBuilder()
            sb.append("💶 **Finanzen & Rechnungs-Übersicht:**\n")
            sb.append("Es wurden **${invoices.size} Rechnungs-/Finanzdokumente** im Tresor gefunden.\n")
            if (totalAmount > 0.0) {
                sb.append("Erfasste Gesamtsumme: **${String.format(java.util.Locale.GERMAN, "%.2f €", totalAmount)}**\n\n")
            } else {
                sb.append("\n")
            }
            if (invoices.isNotEmpty()) {
                sb.append("Zuletzt erfasste Belege:\n")
                invoices.take(5).forEach { inv ->
                    val amtStr = if (inv.amount != null && inv.amount > 0.0) " (${String.format(java.util.Locale.GERMAN, "%.2f €", inv.amount)})" else ""
                    sb.append("• **${inv.title}** von ${inv.sender}$amtStr (${sdf.format(java.util.Date(inv.createdAt))})\n")
                }
                sb.append("\n💡 Im **Dashboard** findest du zudem den automatischen monatlichen Ausgabenverlauf.")
            }
            return LocalAiChatResult(
                replyText = sb.toString(),
                actionType = "OPEN_DASHBOARD",
                actionLabel = "📊 Zum Dashboard"
            )
        }

        // 6. Gesetzliche Aufbewahrungsfristen
        if (qLower.contains("aufbewahr") || qLower.contains("aufheben") || qLower.contains("entsorgen") || qLower.contains("wegwerfen") || qLower.contains("wie lange")) {
            val reply = """
Hier sind die gesetzlichen Aufbewahrungsfristen in Deutschland im Überblick:

• **10 Jahre (Handels- & Steuerrecht § 147 AO):**
  Bücher, Jahresabschlüsse, Inventare, Buchungsbelege und Rechnungen.
• **6 Jahre (§ 257 HGB):**
  Empfangene Handels- und Geschäftsbriefe, sonstige steuerrelevante Unterlagen.
• **2–3 Jahre (Verjährung BGB § 195):**
  Kaufbelege & Quittungen für Gewährleistung, Handwerkerrechnungen, alltägliche Verträge.
• **Lebenslang aufbewahren:**
  Geburts-, Heirats- und Sterbeurkunden, Zeugnisse, Rentenversicherungsunterlagen, ärztliche Gutachten.

💡 *Alle Auswertungen und Speicherung erfolgen 100% lokal und verschlüsselt auf deinem Smartphone.*
""".trimIndent()
            return LocalAiChatResult(replyText = reply)
        }

        // 7. Freie Suche / Treffer im Tresor
        val foundDocs = documents.filter { doc ->
            doc.title.contains(q, ignoreCase = true) ||
            doc.sender.contains(q, ignoreCase = true) ||
            doc.reminderNotes.contains(q, ignoreCase = true) ||
            doc.ocrText.contains(q, ignoreCase = true) ||
            doc.mainCategory.contains(q, ignoreCase = true) ||
            doc.subCategory.contains(q, ignoreCase = true)
        }

        if (foundDocs.isNotEmpty() && q.length >= 3) {
            val sb = StringBuilder()
            sb.append("Ich habe **${foundDocs.size} passende Dokument(e)** zu deiner Anfrage „$q“ gefunden:\n\n")
            foundDocs.take(6).forEachIndexed { i, d ->
                sb.append("${i + 1}. **${d.title}** (${d.sender})\n")
                sb.append("   Ablage: ${d.mainCategory} ➔ ${d.subCategory} | Erstellt: ${sdf.format(java.util.Date(d.createdAt))}\n")
            }
            if (foundDocs.size > 6) {
                sb.append("\n... und ${foundDocs.size - 6} weitere Dokumente.")
            }
            return LocalAiChatResult(replyText = sb.toString())
        }

        // 8. Allgemeine Hilfe
        val fallbackReply = """
Ich habe deine Anfrage verstanden: **„$q“**

Dein Dokumenten-Tresor enthält aktuell **${documents.size} archivierte Dokumente**.

Ich laufe zu **100% On-Device und offline** auf deinem Smartphone. Womit kann ich dir helfen?
• **Dokumente & Rechnungen:** Frage nach Ausgaben, Absendern oder Inhalten
• **Verträge & Kündigungen:** Musterschreiben oder Laufzeiten anzeigen
• **App-Steuerung:** Sag z.B. „Öffne den Scanner“ oder „Gehe zum Dashboard“
• **Hilfe:** Tippe „Hilfe“ für eine vollständige Befehlsliste
""".trimIndent()

        return LocalAiChatResult(replyText = fallbackReply)
    }
}
