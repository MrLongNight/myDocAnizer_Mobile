package com.example.service

import android.content.Context
import android.util.Log
import android.os.Build
import com.example.model.FolderStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Fachabteilungs-Teams für den Second Run (Team 2: Spezialisten-Tiefenanalyse).
 */
enum class DepartmentTeam(val id: String, val displayName: String, val shortName: String) {
    FINANCE("FINANCE", "Abt. A: Finanzen & Buchhaltung", "Team Finanzen"),
    CONTRACTS("CONTRACTS", "Abt. B: Vertragswesen & Recht", "Team Verträge"),
    ADMINISTRATION("ADMINISTRATION", "Abt. C: Postverkehr & Verwaltung", "Team Verwaltung");

    companion object {
        fun dispatch(docTypeOrText: String?): DepartmentTeam {
            val t = (docTypeOrText ?: "").lowercase().trim()
            val isExplicitInvoiceOrTax = t.contains("rechnung") || t.contains("quittung") ||
                t.contains("kassenbon") || t.contains("steuerbescheid") ||
                t.contains("gehaltsabrechnung") || t.contains("lohnabrechnung") || t.contains("kontoauszug")
            val isExplicitContract = t.contains("vertrag") || t.contains("police") ||
                t.contains("versicherungsschein") || t.contains("kündigung") ||
                t.contains("mitgliedschaft") || t.contains("leasing") || t.contains("miet") ||
                t.contains("vereinbarung") || t.contains("haftpflicht")
            return when {
                isExplicitContract && !isExplicitInvoiceOrTax -> CONTRACTS
                isExplicitInvoiceOrTax || t.contains("beleg") || t.contains("gehalt") ||
                t.contains("entgelt") || t.contains("lohn") || t.contains("steuer") ||
                t.contains("finanz") || t.contains("auszug") || t.contains("gutschrift") ||
                t.contains("abschlag") || t.contains("strom") || t.contains("gas") ||
                t.contains("energie") -> FINANCE
                isExplicitContract || t.contains("versicherung") || t.contains("abo") || t.contains("kfz") -> CONTRACTS
                else -> ADMINISTRATION
            }
        }
    }
}

/**
 * Strukturierte JSON-Antwort des LLMs gemäß dem vorgegebenen Chain-of-Thought DMS-Schema.
 */
data class DmsExtractionResponse(
    val reasoning: String = "",
    val absender: String? = null,
    val dokumententyp: String? = null,
    val matchKeywords: List<String> = emptyList(),
    val kategorieVorschlag: String? = null,
    val vorgeschlagenerPfad: String? = null,
    val globaleZusatzfelder: Map<String, String> = emptyMap(),
    val rawJson: String = ""
) {
    /**
     * Erzeugt den finalen Regelnamen via deterministischer Kotlin-Logik (Entlastung des LLMs).
     */
    fun computeRuleName(): String {
        val cleanSender = absender?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        val cleanType = dokumententyp?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        return when {
            cleanSender != null && cleanType != null -> "$cleanSender $cleanType"
            cleanSender != null -> "$cleanSender Dokument"
            cleanType != null -> "$cleanType Ablage"
            else -> "Neues Dokument"
        }
    }

    /**
     * Wandelt die Antwort wieder in ein sauberes JSON-Objekt um (z. B. für Re-Prompting/Refining).
     */
    fun toJsonString(): String {
        val json = JSONObject()
        json.put("_reasoning", reasoning)
        json.put("absender", absender ?: JSONObject.NULL)
        json.put("dokumententyp", dokumententyp ?: JSONObject.NULL)
        json.put("match_keywords", JSONArray(matchKeywords))
        json.put("kategorie_vorschlag", kategorieVorschlag ?: JSONObject.NULL)
        json.put("vorgeschlagener_pfad", vorgeschlagenerPfad ?: JSONObject.NULL)
        val fieldsObj = JSONObject()
        globaleZusatzfelder.forEach { (k, v) -> fieldsObj.put(k, v) }
        json.put("globale_zusatzfelder", fieldsObj)
        return json.toString(2)
    }
}

/**
 * Llama.cpp Native Inferenz-Engine für GGUF-Modelle auf Android.
 * Bietet native JNI-Bindungen an GGML / llama.cpp mit automatischer
 * Hardware-Beschleunigung (GPU via Vulkan/OpenCL & CPU via ARM NEON)
 * sowie GBNF-Grammar-Unterstützung für hardwareseitig erzwungene JSON-Outputs.
 */
class LlamaCppInferenceEngine(private val context: Context) {

    enum class InferenceMode { NOT_RUN, LOCAL_LLM, RULE_FALLBACK }

    @Volatile
    var lastInferenceMode: InferenceMode = InferenceMode.NOT_RUN
        private set

    data class InferenceMetrics(
        val generatedTokens: Int,
        val promptEvalTimeMs: Long,
        val generationTimeMs: Long,
        val tokensPerSecond: Float
    )

    /** Last call's native metrics only; null means no trustworthy native measurements. */
    @Volatile
    var lastInferenceMetrics: InferenceMetrics? = null
        private set

    companion object {
        private const val TAG = "LlamaCppEngine"
        private val inferenceMutex = kotlinx.coroutines.sync.Mutex()

        private fun safeLogI(tag: String, msg: String) {
            try { Log.i(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
        }
        private fun safeLogW(tag: String, msg: String) {
            try { Log.w(tag, msg) } catch (_: Throwable) { println("[$tag] $msg") }
        }
        private fun safeLogE(tag: String, msg: String, tr: Throwable? = null) {
            try { Log.e(tag, msg, tr) } catch (_: Throwable) { println("[$tag] $msg ${tr?.message ?: ""}") }
        }

        /**
         * Kompakter Extraktionsprompt: keine Chain-of-Thought-Ausgabe und keine erfundenen Feldwerte.
         */
        const val BASE_SYSTEM_PROMPT: String = """Du extrahierst Dokumentdaten für myDocAnizer. Nutze ausschließlich Informationen, die im OCR-Text eindeutig stehen.
Regeln:
1. Erfinde niemals Werte. Fehlende oder unsichere Werte sind null bzw. werden ausgelassen.
2. Unterscheide Datumsarten anhand ihrer Beschriftung. Ein Geburtsdatum ist keine Fälligkeit.
3. 'globale_zusatzfelder' darf NUR Felder mit einem im OCR-Text belegten Wert enthalten. Erfinde keine Schlüssel-Wert-Paare. Schlüssel kurz und stabil benennen.
4. '_reasoning' ist eine knappe Ergebnisbegründung mit höchstens einem Satz, keine ausführliche Gedankenkette.
5. Antworte ausschließlich mit gültigem JSON, ohne Markdown oder Text davor/danach.

JSON-Schema:
{
  "_reasoning": "Kurze Begründung",
  "absender": "Firma oder Aussteller oder null",
  "dokumententyp": "Dokumenttyp oder null",
  "match_keywords": ["bis zu drei OCR-belegte Schlüsselwörter"],
  "kategorie_vorschlag": "Hauptkategorie oder null",
  "globale_zusatzfelder": {
    "feldname": "nur ein tatsächlich erkannter Wert"
  }
}"""

        /**
         * System-Prompt für den schnellen First Run. Kompakt und ohne ausführliche Reasoning-Ausgabe.
         */
        const val TRIAGE_SYSTEM_PROMPT: String = """Du bist der Leiter der Poststelle & Eingangstriage (Team 1) für die App myDocAnizer.
Deine Aufgabe: Blitzschnelle Ersterfassung des Dokuments anhand der Kopfzeilen und Schlüsselbereiche.
Regeln:
1. Nutze nur eindeutig im OCR-Text belegte Informationen. Erfinde keine Werte; fehlende Werte sind null.
2. Identifiziere Absender, Dokumententyp und Hauptkategorie.
3. Extrahiere bis zu 3 Schlüsselwörter, die tatsächlich im OCR-Text vorkommen.
4. '_reasoning' ist höchstens ein kurzer Satz, keine Gedankenkette.
5. Antworte nur mit gültigem JSON.

Antworte AUSSCHLIESSLICH mit diesem exakten JSON-Schema:
{
  "_reasoning": "Kurzer Satz zur Begründung",
  "absender": "Firma oder Aussteller",
  "dokumententyp": "z.B. Rechnung, Vertrag, Überweisung",
  "match_keywords": ["keyword1", "keyword2"],
  "kategorie_vorschlag": "Hauptkategorie"
}"""

        /**
         * GBNF Grammatik für den schnellen First Run (Team 1 Triage ohne Zusatzfelder-Overhead).
         */
        const val TRIAGE_JSON_GBNF: String = """
root ::= "{" ws "\"_reasoning\"" ws ":" ws string "," ws "\"absender\"" ws ":" ws opt_string "," ws "\"dokumententyp\"" ws ":" ws opt_string "," ws "\"match_keywords\"" ws ":" ws string_array "," ws "\"kategorie_vorschlag\"" ws ":" ws opt_string "}" ws
opt_string ::= string | "null"
string_array ::= "[" ws (string ("," ws string)*)? ws "]"
string ::= "\"" ([^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4}))* "\""
ws ::= [ \t\n\r]*
"""

        /**
         * Hochgradig optimierter, schlanker System-Prompt speziell für kleine Vor-Ort-Modelle (0.5B - 3B),
         * wie Qwen 2.5 0.5B/1.5B/3B, Llama 3.2 1B/3B, SmolLM2 und Phi-3.5.
         * Minimiert Attention-Drift und Token-Overhead, erzwingt standardisierte Feldnamen für smarte Dashboard-Widgets.
         */
        fun buildSmallModelOptimizedPrompt(whitelistedCustomFields: List<String> = emptyList()): String {
            val availableFields = if (whitelistedCustomFields.isNotEmpty()) {
                whitelistedCustomFields.take(15).joinToString(", ")
            } else {
                "Betrag, Fälligkeit, Rechnungsnummer, Kundennummer, Vertragsnummer, Zählernummer, Datum, Steuernummer, Kennzeichen, IBAN"
            }

            return """DMS-Assistent für mobile On-Device Extraktion.
Analysiere den OCR-Text und extrahiere Schlüsseldaten strikt als kompaktes JSON.

REGELN FÜR KLEINE MODELLE:
1. '_reasoning': Maximal 1 kurzer Ergebnissatz, keine Gedankenkette.
2. Erfinde niemals Werte. Nutze nur Werte, die eindeutig im OCR-Text stehen; sonst lasse das Feld weg oder nutze null.
3. 'globale_zusatzfelder': Nutze AUSSCHLIESSLICH passende Schlüssel aus dieser Liste und fülle sie nur mit belegten OCR-Werten:
   [ $availableFields ]
3. Formate: Beträge mit Währung (z.B. "49,90 €"), Datumsangaben als "DD.MM.YYYY".
4. Erfinde keine Daten; wenn ein Wert nicht im Text steht, lasse das Feld weg.

SCHEMA:
{
  "_reasoning": "Rechnung von Vodafone mit Monatsbetrag.",
  "absender": "Firmenname oder Aussteller",
  "dokumententyp": "Rechnung, Vertrag, Brief, Beleg oder Gehaltsabrechnung",
  "match_keywords": ["keyword1", "keyword2"],
  "kategorie_vorschlag": "Finanzen, Verträge, Steuern oder Post & Dokumente",
  "globale_zusatzfelder": {
    "Betrag": "49,90 €",
    "Fälligkeit": "15.11.2026"
  }
}"""
        }

        /**
         * Baut den spezialisierten Fachabteilungs-System-Prompt für den Second Run (Team 2).
         * Unterstützt die drei Fachabteilungen (Finanzen, Verträge, Verwaltung),
         * injiziert die Benutzer-Ordnerstrategie ('FolderStrategy') und globale Zusatzfelder-Whitelists (Team 3).
         */
        fun buildDepartmentSystemPrompt(
            department: DepartmentTeam,
            folderStrategy: FolderStrategy = FolderStrategy.BY_CATEGORY,
            whitelistedCustomFields: List<String> = emptyList(),
            userCorrectionPrompt: String? = null
        ): String {
            val roleIntro = when (department) {
                DepartmentTeam.FINANCE -> """Du bist der leitende Buchhalter in Abteilung A (Finanzen & Buchhaltung) für myDocAnizer.
Deine Kernkompetenz: Rechnungen, Kassenbelege, Quittungen, Gehaltsabrechnungen, Steuerbescheide, Bankauszüge.
Extrahiere präzise alle kaufmännischen Daten: Rechnungsbetrag, Fälligkeit, Skonto, Rechnungsnummer, IBAN, Steuernummer, Leistungszeitraum."""

                DepartmentTeam.CONTRACTS -> """Du bist der Justiziar & Vertragsprüfer in Abteilung B (Vertragswesen & Recht) für myDocAnizer.
Deine Kernkompetenz: Verträge, Versicherungspolicen, Mitgliedschaften, Kündigungen, Leasing, Mietverträge, Abonnements.
Extrahiere präzise alle rechtlichen Vertragsdaten: Vertragspartner, Vertragsnummer, Policennummer, Vertragsbeginn, Mindestlaufzeit, Kündigungsfrist, monatliche Rate/Beitrag."""

                DepartmentTeam.ADMINISTRATION -> """Du bist der Verwaltungsleiter in Abteilung C (Postverkehr & Verwaltung) für myDocAnizer.
Deine Kernkompetenz: Amtliche Bescheide, Behördenschreiben, Meldebescheinigungen, Zeugnisse, Garantiescheine, allgemeiner Schriftverkehr.
Extrahiere präzise alle administrativen Daten: Behörde/Aussteller, Aktenzeichen, Bescheiddatum, Fristen, Betreff, Bearbeiter."""
            }

            val strategyDirective = """### ORDNERSTRATEGIE-VORGABE (EBENE 1):
Der Nutzer bevorzugt Ebene 1 nach ${folderStrategy.title}.
Schlage im JSON unter 'vorgeschlagener_pfad' einen passenden, flexiblen Ordnerpfad vor (z.B. '${folderStrategy.examplePath}' oder 'Kategorie/Unterkategorie' bzw. 'Absender/Dokumententyp'), der exakt zu diesem Dokument passt. Keine starren Muster erzwingen!"""

            val fieldsPart = if (whitelistedCustomFields.isNotEmpty()) {
                """### TEAM 3 STAMMDATEN-REGEL FÜR GLOBALE ZUSATZFELDER:
Folgende globale Zusatzfelder existieren bereits im Tresor:
[ ${whitelistedCustomFields.joinToString(", ")} ]
REGELN:
1. Ordne gefundene Werte (z.B. Rechnungsnummer, Kundennummer, Vertragsnummer, Fälligkeit) ZWINGEND einem dieser existierenden Felder zu, sofern passend!
2. Erfinde nur dann ein neues Feld in 'globale_zusatzfelder', wenn absolut kein existierendes Feld passt.
3. Erfinde niemals Daten. Wenn ein Wert fehlt, nutze null."""
            } else {
                """3. Erfinde unter 'globale_zusatzfelder' sinnvolle, ordnerübergreifende Schlüssel-Wert-Paare (wie 'vertragsnummer', 'zaehlernummer', 'faelligkeit'), um das Dokument maximal filterbar zu machen."""
            }

            val correctionPart = if (!userCorrectionPrompt.isNullOrBlank()) {
                """

### BENUTZER-KORREKTURANWEISUNG (HÖCHSTE PRIORITÄT):
"$userCorrectionPrompt"
Berücksichtige diese Anweisung des Nutzers zwingend vorrangig bei der Extraktion!"""
            } else ""

            return """$roleIntro
Analysiere den VOLLSTÄNDIGEN OCR-Text und extrahiere alle relevanten Daten präzise.
Regeln:
1. Erfinde keine Daten. Wenn ein Wert fehlt, nutze null.
2. Achte extrem genau auf den Kontext von Daten (z. B. ein Geburtsdatum neben 'geb.' ist niemals eine Fälligkeit!).
$strategyDirective
$fieldsPart$correctionPart

Antworte AUSSCHLIESSLICH mit diesem exakten JSON-Schema:
{
  "_reasoning": "Ausführlicher Satz: Warum wurden diese Entitäten und dieser Ordnerpfad gewählt?",
  "absender": "Firma oder Aussteller",
  "dokumententyp": "z.B. Rechnung, Vertrag, Überweisung",
  "match_keywords": ["keyword1", "keyword2", "keyword3"],
  "kategorie_vorschlag": "Hauptkategorie",
  "vorgeschlagener_pfad": "z.B. Vodafone/Rechnungen oder Finanzen/Energie",
  "globale_zusatzfelder": {
    "dynamisches_feld_1": "wert",
    "dynamisches_feld_2": "wert"
  }
}"""
        }

        fun buildSecondRunSystemPrompt(
            whitelistedCustomFields: List<String> = emptyList(),
            userCorrectionPrompt: String? = null
        ): String {
            return buildDepartmentSystemPrompt(
                department = DepartmentTeam.ADMINISTRATION,
                folderStrategy = FolderStrategy.BY_CATEGORY,
                whitelistedCustomFields = whitelistedCustomFields,
                userCorrectionPrompt = userCorrectionPrompt
            )
        }

        /**
         * GBNF-Grammatik für die native llama.cpp-Schnittstelle. Sie wird an den Sampler übergeben,
         * damit die JSON-Struktur während der Generierung eingeschränkt wird.
         */
        const val DMS_JSON_GBNF: String = """
root ::= "{" ws "\"_reasoning\"" ws ":" ws string "," ws "\"absender\"" ws ":" ws opt_string "," ws "\"dokumententyp\"" ws ":" ws opt_string "," ws "\"match_keywords\"" ws ":" ws string_array "," ws "\"kategorie_vorschlag\"" ws ":" ws opt_string "," ws "\"vorgeschlagener_pfad\"" ws ":" ws opt_string "," ws "\"globale_zusatzfelder\"" ws ":" ws object "}" ws
opt_string ::= string | "null"
string_array ::= "[" ws (string ("," ws string)*)? ws "]"
object ::= "{" ws (pair ("," ws pair)*)? ws "}"
pair ::= string ws ":" ws (string | number | "true" | "false" | "null")
number ::= "-"? [0-9]+ ("." [0-9]+)?
string ::= "\"" ([^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4}))* "\""
ws ::= [ \t\n\r]*
"""

        fun createHeuristicFallbackResponse(
            ocrText: String,
            folderStrategy: FolderStrategy = FolderStrategy.BY_CATEGORY
        ): DmsExtractionResponse {
            val lower = ocrText.lowercase()
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val detectedSender = when {
                DocRuleRepository.containsKeywordMatch(lower, "congstar") -> "congstar"
                DocRuleRepository.containsKeywordMatch(lower, "deutsche post") || DocRuleRepository.containsKeywordMatch(lower, "postbank") -> "Deutsche Post"
                DocRuleRepository.containsKeywordMatch(lower, "dhl") -> "DHL Paket"
                DocRuleRepository.containsKeywordMatch(lower, "vodafone") -> "Vodafone GmbH"
                DocRuleRepository.containsKeywordMatch(lower, "telekom") || DocRuleRepository.containsKeywordMatch(lower, "magenta") -> "Deutsche Telekom"
                DocRuleRepository.containsKeywordMatch(lower, "o2") || DocRuleRepository.containsKeywordMatch(lower, "telefonica") -> "Telefónica O2"
                DocRuleRepository.containsKeywordMatch(lower, "1&1") -> "1&1 Telecom"
                DocRuleRepository.containsKeywordMatch(lower, "allianz") -> "Allianz"
                DocRuleRepository.containsKeywordMatch(lower, "ergo") -> "ERGO Versicherung"
                DocRuleRepository.containsKeywordMatch(lower, "huk") -> "HUK-COBURG"
                DocRuleRepository.containsKeywordMatch(lower, "finanzamt") -> "Finanzamt"
                DocRuleRepository.containsKeywordMatch(lower, "stadtwerke") -> "Stadtwerke"
                DocRuleRepository.containsKeywordMatch(lower, "e.on") || DocRuleRepository.containsKeywordMatch(lower, "eon") -> "E.ON Energie"
                DocRuleRepository.containsKeywordMatch(lower, "vattenfall") -> "Vattenfall"
                DocRuleRepository.containsKeywordMatch(lower, "barmer") -> "BARMER"
                DocRuleRepository.containsKeywordMatch(lower, "techniker") || DocRuleRepository.containsKeywordMatch(lower, "tk ") -> "Techniker Krankenkasse"
                DocRuleRepository.containsKeywordMatch(lower, "aok") -> "AOK"
                DocRuleRepository.containsKeywordMatch(lower, "sparkasse") -> "Sparkasse"
                DocRuleRepository.containsKeywordMatch(lower, "volksbank") || DocRuleRepository.containsKeywordMatch(lower, "raiffeisenbank") -> "Volksbank Raiffeisenbank"
                DocRuleRepository.containsKeywordMatch(lower, "ing-diba") || DocRuleRepository.containsKeywordMatch(lower, "ing diba") -> "ING"
                DocRuleRepository.containsKeywordMatch(lower, "dkb") -> "DKB"
                DocRuleRepository.containsKeywordMatch(lower, "commerzbank") -> "Commerzbank"
                DocRuleRepository.containsKeywordMatch(lower, "deutsche bank") -> "Deutsche Bank"
                DocRuleRepository.containsKeywordMatch(lower, "adac") -> "ADAC"
                DocRuleRepository.containsKeywordMatch(lower, "amazon") -> "Amazon"
                DocRuleRepository.containsKeywordMatch(lower, "paypal") -> "PayPal"
                DocRuleRepository.containsKeywordMatch(lower, "klarna") -> "Klarna"
                else -> null
            }
            val detectedType = when {
                lower.contains("rechnung") || lower.contains("rechnungsbetrag") || lower.contains("invoice") -> "Rechnung"
                lower.contains("vertrag") || lower.contains("versicherungsschein") || lower.contains("police") -> "Vertrag"
                lower.contains("bescheid") || lower.contains("steuerbescheid") -> "Bescheid"
                lower.contains("gehalt") || lower.contains("entgelt") || lower.contains("lohnabrechnung") -> "Gehaltsabrechnung"
                lower.contains("quittung") || lower.contains("kassenbon") || lower.contains("beleg") -> "Kassenbeleg"
                lower.contains("brief") || lower.contains("schreiben") || lower.contains("mitteilung") || lower.contains("deutsche post") -> "Brief"
                else -> "Dokument"
            }

            val keywords = listOfNotNull(detectedSender?.lowercase()?.split(" ")?.firstOrNull(), detectedType.lowercase())

            val catSuggestion = when (detectedType) {
                "Rechnung", "Gehaltsabrechnung", "Kassenbeleg" -> "Finanzen"
                "Vertrag", "Bescheid" -> "Verträge"
                else -> "Post & Dokumente"
            }

            val suggestedPath = when (folderStrategy) {
                FolderStrategy.BY_SENDER -> "${detectedSender ?: "Dokumente"}/$detectedType"
                FolderStrategy.BY_CATEGORY -> "$catSuggestion/$detectedType"
                FolderStrategy.CHRONOLOGICAL -> "$currentYear/$detectedType"
                FolderStrategy.FLAT -> "Dokumente"
            }

            val fallbackFields = mutableMapOf<String, String>()
            val amt = com.example.service.llm.LlmPrompts.extractAmountFromText(ocrText)
            if (amt.isNotBlank()) fallbackFields["Betrag"] = amt
            val due = com.example.service.llm.LlmPrompts.extractDueDateFromText(ocrText)
            if (due.isNotBlank()) fallbackFields["Fälligkeit"] = due
            val docDate = com.example.service.llm.LlmPrompts.extractDocumentDateFromText(ocrText)
            if (docDate.isNotBlank()) fallbackFields["Datum"] = docDate
            val custNo = com.example.service.llm.LlmPrompts.extractCustomerNumber(ocrText)
            if (custNo.isNotBlank()) fallbackFields["Kundennummer"] = custNo
            val invNo = com.example.service.llm.LlmPrompts.extractInvoiceNumber(ocrText)
            if (invNo.isNotBlank()) fallbackFields["Rechnungsnummer"] = invNo
            val polNo = com.example.service.llm.LlmPrompts.extractPolicyNumber(ocrText)
            if (polNo.isNotBlank()) fallbackFields["Policennummer"] = polNo
            val meterNo = com.example.service.llm.LlmPrompts.extractMeterNumber(ocrText)
            if (meterNo.isNotBlank()) fallbackFields["Zählernummer"] = meterNo
            val plate = com.example.service.llm.LlmPrompts.extractLicensePlate(ocrText)
            if (plate.isNotBlank()) fallbackFields["Kennzeichen"] = plate
            val taxId = com.example.service.llm.LlmPrompts.extractTaxId(ocrText)
            if (taxId.isNotBlank()) fallbackFields["Steuernummer"] = taxId

            return DmsExtractionResponse(
                reasoning = "Regelbasierter Fallback (kein LLM): ${detectedSender ?: "Allgemeines Dokument"} ($detectedType) anhand von OCR-Schlüsselwörtern erkannt.",
                absender = detectedSender,
                dokumententyp = detectedType,
                matchKeywords = keywords,
                kategorieVorschlag = catSuggestion,
                vorgeschlagenerPfad = suggestedPath,
                globaleZusatzfelder = fallbackFields
            )
        }
    }

    data class InferenceParams(
        val temperature: Float = 0.1f,
        val topP: Float = 0.9f,
        val maxTokens: Int = 512,
        val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
        val gpuLayers: Int = 0,
        /**
         * Optional grammar. Keep disabled until the grammar is validated against the
         * pinned llama.cpp parser on the target Android ABI.
         */
        val grammar: String? = null
    )

    /**
     * Prüft, ob GPU-Hardwarebeschleunigung (Vulkan) auf diesem Android-Gerät verfügbar ist.
     */
    fun checkGpuAccelerationSupport(): Boolean {
        // This pinned AAR ships CPU/NEON only; chipset names do not prove GPU support.
        return false
    }

    /**
     * Führt den schnellen First Run (Team 1: Poststelle & Eingangstriage) aus.
     * Übergibt den per OCR-Vorfilter-Matrix zugeschnittenen Kopfbereich und injiziert vorhandene Zusatzfelder,
     * optimiert für schlanke lokale LLMs.
     */
    suspend fun executeFirstRunExtraction(
        modelFile: File,
        ocrText: String,
        whitelistedCustomFields: List<String> = emptyList(),
        useStrictTriage: Boolean = true,
        params: InferenceParams = InferenceParams(maxTokens = 350, grammar = null)
    ): DmsExtractionResponse = withContext(Dispatchers.Default) {
        lastInferenceMode = InferenceMode.NOT_RUN
        lastInferenceMetrics = null
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            lastInferenceMode = InferenceMode.RULE_FALLBACK
            return@withContext createHeuristicFallbackResponse(ocrText)
        }
        val filteredSlice = OcrPreFilterMatrix.extractFirstRunSlice(ocrText)
        val useTriage = useStrictTriage || whitelistedCustomFields.isEmpty()
        val systemPrompt = if (useTriage) TRIAGE_SYSTEM_PROMPT else buildSmallModelOptimizedPrompt(whitelistedCustomFields)
        // The active TRIAGE/DMS grammars are rejected by llama.cpp on the target device.
        // Do not let grammar construction abort structured extraction; parse the model JSON afterward.
        val targetParams = params.copy(
            maxTokens = if (useTriage) 200 else params.maxTokens,
            grammar = null
        )

        val rawOutput = executeInference(
            modelFile = modelFile,
            prompt = "OCR-KOPFZEILEN (TRIAGE):\n$filteredSlice",
            systemPrompt = systemPrompt,
            params = targetParams
        )
        parseDmsJson(rawOutput, filteredSlice)
    }

    /**
     * Führt den erweiterten Second Run (Team 2: Fachabteilungs-Tiefenanalyse) aus.
     * Übergibt den VOLLSTÄNDIGEN OCR-Text und injiziert Whitelist-Zusatzfelder aus Team 3
     * sowie optionale gezielte Benutzer-Korrekturanweisungen.
     */
    suspend fun executeSecondRunExtraction(
        modelFile: File,
        ocrText: String,
        whitelistedCustomFields: List<String> = emptyList(),
        userCorrectionPrompt: String? = null,
        folderStrategy: FolderStrategy = FolderStrategy.BY_CATEGORY,
        department: DepartmentTeam? = null,
        params: InferenceParams = InferenceParams(maxTokens = 600, grammar = null)
    ): DmsExtractionResponse = withContext(Dispatchers.Default) {
        lastInferenceMode = InferenceMode.NOT_RUN
        lastInferenceMetrics = null
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            lastInferenceMode = InferenceMode.RULE_FALLBACK
            return@withContext createHeuristicFallbackResponse(ocrText, folderStrategy)
        }
        val targetDept = department ?: DepartmentTeam.dispatch(ocrText)
        val systemPrompt = buildDepartmentSystemPrompt(
            department = targetDept,
            folderStrategy = folderStrategy,
            whitelistedCustomFields = whitelistedCustomFields,
            userCorrectionPrompt = userCorrectionPrompt
        )
        val promptContent = buildString {
            append("OCR-VOLLTEXT:\n")
            append(ocrText)
            if (!userCorrectionPrompt.isNullOrBlank()) {
                append("\n\nHINWEIS ZUR BENUTZER-KORREKTUR:\n")
                append(userCorrectionPrompt)
            }
        }
        val rawOutput = executeInference(
            modelFile = modelFile,
            prompt = promptContent,
            systemPrompt = systemPrompt,
            params = params
        )
        parseDmsJson(rawOutput, ocrText, folderStrategy)
    }

    /**
     * Führt eine strukturierte GGUF-Inferenz aus und parst das Ergebnis fehlertolerant in [DmsExtractionResponse].
     */
    suspend fun executeDmsExtraction(
        modelFile: File,
        ocrText: String,
        systemPrompt: String = BASE_SYSTEM_PROMPT,
        params: InferenceParams = InferenceParams()
    ): DmsExtractionResponse = withContext(Dispatchers.Default) {
        lastInferenceMode = InferenceMode.NOT_RUN
        lastInferenceMetrics = null
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            lastInferenceMode = InferenceMode.RULE_FALLBACK
            return@withContext createHeuristicFallbackResponse(ocrText)
        }
        val rawOutput = executeInference(
            modelFile = modelFile,
            prompt = "OCR-VOLLTEXT:\n$ocrText",
            systemPrompt = systemPrompt,
            params = params
        )
        parseDmsJson(rawOutput, ocrText)
    }

    /**
     * Führt die Roh-Inferenz über llama.cpp oder die optimierte Fallback-Engine aus.
     */
    suspend fun executeInference(
        modelFile: File,
        prompt: String,
        systemPrompt: String = BASE_SYSTEM_PROMPT,
        params: InferenceParams = InferenceParams()
    ): String = withContext(Dispatchers.Default) {
        // Clear previous-call state first: a fallback must never inherit stale LLM status/metrics.
        lastInferenceMode = InferenceMode.NOT_RUN
        lastInferenceMetrics = null
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            lastInferenceMode = InferenceMode.RULE_FALLBACK
            return@withContext executeFallbackInference(prompt, systemPrompt)
        }
        if (!supportsNativeInference()) {
            lastInferenceMode = InferenceMode.RULE_FALLBACK
            safeLogW(TAG, "Native llama.cpp wird auf dieser 32-Bit-ABI nicht gestartet; OCR-/Regel-Fallback aktiv.")
            return@withContext executeFallbackInference(prompt, systemPrompt)
        }

        try {
            val resultJson = inferenceMutex.withLock {
                System.loadLibrary("llama_android")
                nativeExecuteLlamaWithGrammar(
                    modelPath = modelFile.absolutePath,
                    prompt = prompt,
                    systemPrompt = systemPrompt,
                    threads = params.threads,
                    gpuLayers = 0,
                    temp = params.temperature,
                    topP = params.topP,
                    maxTokens = params.maxTokens,
                    grammar = params.grammar ?: ""
                )
            }
            val result = JSONObject(resultJson)
            if (result.has("error")) {
                throw IllegalStateException(result.optString("error", "Native inference failed"))
            }
            val output = result.optString("text", "")
            if (output.isBlank()) throw IllegalStateException("Native inference returned an empty response")

            lastInferenceMode = InferenceMode.LOCAL_LLM
            val generatedTokens = result.optInt("tokensGenerated", 0)
            val promptMs = result.optLong("promptEvalTimeMs", 0L)
            val generationMs = result.optLong("generateTimeMs", 0L)
            val tokensPerSecond = result.optDouble("tokensPerSecond", 0.0)
            lastInferenceMetrics = if (
                generatedTokens > 0 && generationMs > 0L && tokensPerSecond > 0.0
            ) {
                InferenceMetrics(
                    generatedTokens = generatedTokens,
                    promptEvalTimeMs = promptMs,
                    generationTimeMs = generationMs,
                    tokensPerSecond = tokensPerSecond.toFloat()
                )
            } else {
                null
            }
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = TAG,
                message = "Echte lokale llama.cpp-Inferenz abgeschlossen",
                details = "Modell=${modelFile.name}; Tokens=${generatedTokens}; Prompt=${promptMs} ms; " +
                    "Generierung=${generationMs} ms; gemessene Geschwindigkeit=${tokensPerSecond} Tokens/s; " +
                    "ABI=${Build.SUPPORTED_ABIS.firstOrNull() ?: "unbekannt"}; Backend=CPU",
                isError = false
            )
            output
        } catch (e: LinkageError) {
            lastInferenceMetrics = null
            lastInferenceMode = InferenceMode.RULE_FALLBACK
            safeLogW(TAG, "Native llama.cpp-Laufzeit nicht verfügbar: ${e.message}")
            executeFallbackInference(prompt, systemPrompt)
        } catch (e: Exception) {
            lastInferenceMetrics = null
            lastInferenceMode = InferenceMode.RULE_FALLBACK
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = TAG,
                message = "Lokale Modellinferenz fehlgeschlagen; deterministischer Fallback verwendet",
                details = "Modell=${modelFile.name}; Fehler=${e.message}",
                isError = true
            )
            executeFallbackInference(prompt, systemPrompt)
        }
    }

    private fun supportsNativeInference(): Boolean =
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    fun executeFallbackInference(ocrText: String, systemPrompt: String): String {
        lastInferenceMetrics = null
        lastInferenceMode = InferenceMode.RULE_FALLBACK
        return createHeuristicFallbackResponse(ocrText).toJsonString()
    }

    /**
     * Parst den rohen Modell-Output fehlertolerant in ein [DmsExtractionResponse] Objekt.
     * Filtert Markdown-Codeblöcke heraus und repariert gängige JSON-Ungenauigkeiten.
     */
    fun parseDmsJson(
        rawOutput: String,
        fallbackOcrText: String = "",
        folderStrategy: FolderStrategy = FolderStrategy.BY_CATEGORY
    ): DmsExtractionResponse {
        try {
            var jsonStr = rawOutput.trim()

            // Filter out <think>...</think> blocks if present (for DeepSeek-R1 Distill)
            if (jsonStr.contains("<think>") && jsonStr.contains("</think>")) {
                jsonStr = jsonStr.substringAfter("</think>").trim()
            }

            // Extrahiere JSON aus ```json ... ``` Blöcken falls vorhanden
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substringAfter("```json").substringBefore("```").trim()
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substringAfter("```").substringBefore("```").trim()
            }

            // Finde erstes { und letztes }
            val firstBrace = jsonStr.indexOf('{')
            val lastBrace = jsonStr.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                jsonStr = jsonStr.substring(firstBrace, lastBrace + 1)
            }

            val json = JSONObject(jsonStr)

            val reasoning = json.optString("_reasoning", "")
            val absender = json.optString("absender", "").takeIf { it.isNotBlank() && it != "null" }
            val docType = json.optString("dokumententyp", "").takeIf { it.isNotBlank() && it != "null" }
            val catSuggestion = json.optString("kategorie_vorschlag", "").takeIf { it.isNotBlank() && it != "null" }
            val pathSuggestion = json.optString("vorgeschlagener_pfad", "").takeIf { it.isNotBlank() && it != "null" }

            val matchKeywords = mutableListOf<String>()
            val kwArray = json.optJSONArray("match_keywords")
            if (kwArray != null) {
                for (i in 0 until kwArray.length()) {
                    val kw = kwArray.optString(i, "").trim().lowercase()
                    if (kw.isNotBlank() && kw != "null") {
                        matchKeywords.add(kw)
                    }
                }
            }

            val customFields = mutableMapOf<String, String>()
            val fieldsObj = json.optJSONObject("globale_zusatzfelder")
            if (fieldsObj != null) {
                val keys = fieldsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = fieldsObj.optString(k, "").trim()
                    if (v.isNotBlank() && v != "null") {
                        customFields[k] = v
                    }
                }
            }

            return DmsExtractionResponse(
                reasoning = reasoning,
                absender = absender,
                dokumententyp = docType,
                matchKeywords = matchKeywords,
                kategorieVorschlag = catSuggestion,
                vorgeschlagenerPfad = pathSuggestion ?: run {
                    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val snd = absender ?: "Dokumente"
                    val typ = docType ?: "Allgemein"
                    val cat = catSuggestion ?: "Finanzen"
                    when (folderStrategy) {
                        FolderStrategy.BY_SENDER -> "$snd/$typ"
                        FolderStrategy.BY_CATEGORY -> "$cat/$typ"
                        FolderStrategy.CHRONOLOGICAL -> "$currentYear/$typ"
                        FolderStrategy.FLAT -> "Dokumente"
                    }
                },
                globaleZusatzfelder = customFields,
                rawJson = jsonStr
            )
        } catch (e: Exception) {
            safeLogW(TAG, "JSON-Parsing fehlgeschlagen: ${e.message}, erzeuge Heuristik-Antwort")
            lastInferenceMetrics = null
            lastInferenceMode = InferenceMode.RULE_FALLBACK
            return createHeuristicFallbackResponse(fallbackOcrText, folderStrategy)
        }
    }


    /**
     * Führt eine Chatbot-Inferenz mit dynamisch injiziertem System-Prompt und Function-Calling Tools aus.
     */
    suspend fun executeChatInferenceWithTools(
        modelFile: File,
        userMessage: String,
        tools: List<AppTool> = AppTool.DEFAULT_TOOLS,
        documentContextSummary: String = "",
        systemPrompt: String = LlmToolPromptBuilder.buildInjectedSystemPrompt(tools = tools, documentContextSummary = documentContextSummary),
        params: InferenceParams = InferenceParams(temperature = 0.2f, maxTokens = 192, grammar = null)
    ): String = withContext(Dispatchers.Default) {
        lastInferenceMode = InferenceMode.NOT_RUN
        lastInferenceMetrics = null
        if (!modelFile.exists() || modelFile.length() < 1024 * 1024) {
            lastInferenceMode = InferenceMode.RULE_FALLBACK
            return@withContext executeFallbackToolInference(userMessage)
        }
        executeInference(
            modelFile = modelFile,
            prompt = userMessage,
            systemPrompt = systemPrompt,
            params = params
        )
    }

    /**
     * Fallback-Logik wenn GGUF-Modell noch nicht heruntergeladen ist oder in Testumgebungen.
     * Erzeugt OpenAI-kompatible JSON Tool-Calls bei Handlungsanweisungen oder Text bei Fragen.
     */
    fun executeFallbackToolInference(userMessage: String): String {
        val qLower = userMessage.trim().lowercase()

        // 1. Status ändern: "setze status von dokument 5 auf bezahlt" oder "dokument 1 als bezahlt markieren"
        val statusMatch = Regex("(?:status|dokument|beleg)\\s+(?:von\\s+)?(?:id\\s+|#)?([0-9]+|[a-zA-Z0-9_-]+)\\s+(?:auf|als)\\s+([a-zA-ZäöüÄÖÜ\\s]+)", RegexOption.IGNORE_CASE).find(userMessage)
        if (statusMatch != null && (qLower.contains("status") || qLower.contains("setz") || qLower.contains("änder") || qLower.contains("markier"))) {
            val id = statusMatch.groupValues[1].trim()
            val rawStatus = statusMatch.groupValues[2].trim().replace(".", "")
            val cleanStatus = when {
                rawStatus.contains("bezahlt", ignoreCase = true) -> "Bezahlt"
                rawStatus.contains("offen", ignoreCase = true) -> "Offen"
                rawStatus.contains("gekündigt", ignoreCase = true) -> "Gekündigt"
                rawStatus.contains("prüfung", ignoreCase = true) || rawStatus.contains("prüfen", ignoreCase = true) -> "Prüfen"
                else -> rawStatus.replaceFirstChar { it.uppercase() }
            }
            return """
                ```json
                {
                  "name": "updateDocumentStatus",
                  "arguments": {
                    "docId": "$id",
                    "newStatus": "$cleanStatus"
                  }
                }
                ```
            """.trimIndent()
        }

        // 2. Dokumente filtern: "filtere nach Telekom", "zeige offene rechnungen", "dokumente von Allianz"
        if (qLower.contains("filter") || (qLower.contains("zeig") && (qLower.contains("dokument") || qLower.contains("rechnung")))) {
            var sender: String? = null
            var status: String? = null
            var category: String? = null

            // Sender ermitteln
            when {
                qLower.contains("telekom") -> sender = "Telekom"
                qLower.contains("vodafone") -> sender = "Vodafone"
                qLower.contains("allianz") -> sender = "Allianz"
                qLower.contains("finanzamt") -> sender = "Finanzamt"
                qLower.contains("stadtwerke") -> sender = "Stadtwerke"
                qLower.contains("post") || qLower.contains("dhl") -> sender = "Deutsche Post"
                qLower.contains("nach ") -> {
                    sender = userMessage.substringAfter("nach ").trim().split(" ", ",", ".").firstOrNull()?.replaceFirstChar { it.uppercase() }
                }
                qLower.contains("von ") -> {
                    sender = userMessage.substringAfter("von ").trim().split(" ", ",", ".").firstOrNull()?.replaceFirstChar { it.uppercase() }
                }
            }

            // Status ermitteln
            when {
                qLower.contains("offen") -> status = "Offen"
                qLower.contains("bezahlt") -> status = "Bezahlt"
                qLower.contains("gekündigt") -> status = "Gekündigt"
                qLower.contains("prüfen") -> status = "Prüfen"
            }

            // Kategorie ermitteln
            when {
                qLower.contains("rechnung") || qLower.contains("finanz") -> category = "Finanzen"
                qLower.contains("vertrag") || qLower.contains("verträge") -> category = "Verträge"
                qLower.contains("versicherung") -> category = "Versicherungen"
                qLower.contains("steuer") -> category = "Steuern"
            }

            val args = mutableListOf<String>()
            sender?.let { args.add("\"sender\": \"$it\"") }
            status?.let { args.add("\"status\": \"$it\"") }
            category?.let { args.add("\"category\": \"$it\"") }

            return """
                ```json
                {
                  "name": "filterDocuments",
                  "arguments": {
                    ${args.joinToString(",\n                    ")}
                  }
                }
                ```
            """.trimIndent()
        }

        // 3. Neuen Ordner erstellen (Tool-Calling)
        if ((qLower.contains("erstell") || qLower.contains("neu") || qLower.contains("anleg") || qLower.contains("mach")) && (qLower.contains("ordner") || qLower.contains("kategorie") || qLower.contains("verzeichnis"))) {
            var folderName: String? = null
            var parentCategory: String? = null

            val quoteMatch = Regex("[\"']([^\"']+)[\"']").find(userMessage)
            if (quoteMatch != null) {
                folderName = quoteMatch.groupValues[1].trim()
            }

            if (folderName == null) {
                val nameMatch = Regex("(?:namens|mit dem namen|mit namen|heißt|bezeichnung)\\s+([a-zA-Z0-9äöüÄÖÜ_\\-\\s]+)", RegexOption.IGNORE_CASE).find(userMessage)
                if (nameMatch != null) {
                    folderName = nameMatch.groupValues[1].trim().split(Regex("[,.;]")).firstOrNull()?.trim()
                }
            }

            if (folderName == null) {
                val ordnerMatch = Regex("(?:erstelle|anlegen|neuen?)\\s+(?:einen?\\s+)?(?:neuen?\\s+)?ordner\\s+(?:für\\s+)?([a-zA-Z0-9äöüÄÖÜ_\\-\\s]+)", RegexOption.IGNORE_CASE).find(userMessage)
                if (ordnerMatch != null) {
                    val candidate = ordnerMatch.groupValues[1].trim()
                        .replace(Regex("(?:erstellen|anlegen|bitte|jetzt|im tresor|unter|in).*$"), "")
                        .trim()
                    if (candidate.isNotBlank() && !candidate.equals("erstellen", ignoreCase = true) && !candidate.equals("anlegen", ignoreCase = true)) {
                        folderName = candidate
                    }
                }
            }

            val parentMatch = Regex("(?:unter|in der kategorie|im ordner|in)\\s+([a-zA-Z0-9äöüÄÖÜ_\\-]+)", RegexOption.IGNORE_CASE).find(userMessage)
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

            return """
                ```json
                {
                  "name": "createFolder",
                  "arguments": {
                    $argsContent
                  }
                }
                ```
            """.trimIndent()
        }

        // 4. Dashboard Widgets erstellen: "widget", "dashboard", "kachel", "budget-wächter", "monitor" + "erstell", "anleg", "neu", "mach"
        if ((qLower.contains("widget") || qLower.contains("dashboard") || qLower.contains("kachel") || qLower.contains("budget-wächter") || qLower.contains("monitor")) &&
            (qLower.contains("erstell") || qLower.contains("anleg") || qLower.contains("neu") || qLower.contains("mach"))) {
            
            var title = "Neues Dashboard-Widget"
            val quoteMatch = Regex("[\"']([^\"']+)[\"']").find(userMessage)
            if (quoteMatch != null) {
                title = quoteMatch.groupValues[1].trim()
            }

            var type = "FOLDER_MONITOR"
            when {
                qLower.contains("budget") || qLower.contains("ausgabe") -> type = "BUDGET_LIMIT"
                qLower.contains("frist") || qLower.contains("termin") || qLower.contains("radar") -> type = "DEADLINE_MONITOR"
                qLower.contains("notiz") || qLower.contains("merkzettel") -> type = "STICKY_NOTE"
                qLower.contains("zusatzfeld") || qLower.contains("filter") -> type = "CUSTOM_FIELD_MONITOR"
                qLower.contains("schnell") || qLower.contains("shortcut") -> type = "QUICK_SHORTCUT"
            }

            var cat = ""
            val catMatch = Regex("(?:für|in|ordner|kategorie)\\s+([a-zA-Z0-9äöüÄÖÜ_\\-]+)", RegexOption.IGNORE_CASE).find(userMessage)
            if (catMatch != null) {
                cat = catMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            }

            return """
                ```json
                {
                  "name": "createCustomDashboardWidget",
                  "arguments": {
                    "title": "$title",
                    "widgetType": "$type",
                    "targetCategory": "$cat",
                    "colorHex": 4278190080
                  }
                }
                ```
            """.trimIndent()
        }

        // 5. App-Bereiche öffnen
        if (qLower.contains("scanner") || qLower.contains("kamera")) {
            return """
                ```json
                {
                  "name": "openAppSection",
                  "arguments": {
                    "section": "scanner"
                  }
                }
                ```
            """.trimIndent()
        }

        // Default normale Antwort
        return "Ich bin dein persönlicher myDocAnizer Assistent. Womit kann ich dir behilflich sein?"
    }

    private external fun nativeExecuteLlamaWithGrammar(
        modelPath: String,
        prompt: String,
        systemPrompt: String,
        threads: Int,
        gpuLayers: Int,
        temp: Float,
        topP: Float,
        maxTokens: Int,
        grammar: String
    ): String

}

/**
 * OCR-Vorfilter-Matrix für die schnelle Triage (Team 1: Poststelle & Eingangstriage).
 * Schneidet den Text auf die oberen Zeilen zu (max. ca. 30% bzw. max. 800-1000 Zeichen),
 * und sammelt Schlüsselzeilen für Dokumententyp, Absender und Beträge.
 */
object OcrPreFilterMatrix {
    private val KEYWORD_PATTERNS = listOf(
        Regex("(?i)\\b(rechnung|rechnungs-?nr|invoice|beleg|quittung|kassenbon|gutschrift)\\b"),
        Regex("(?i)\\b(vertrag|versicherung|police|kundennummer|kunden-?nr|vertrags-?nr)\\b"),
        Regex("(?i)\\b(kündigung|laufzeit|vertragsende|kündigungsfrist|mindestlaufzeit)\\b"),
        Regex("(?i)\\b(gesamtbetrag|endbetrag|rechnungsbetrag|zu zahlen|fällig|zahlungsziel|iban|bic)\\b"),
        Regex("(?i)\\b(finanzamt|bescheid|steuer-?nr|steuererklärung|aktenzeichen|gehalt|entgelt|lohn)\\b"),
        Regex("(?i)\\b(stadtwerke|energie|wasser|strom|mobilfunk|telekom|vodafone|o2|allianz|post)\\b")
    )

    fun extractFirstRunSlice(ocrText: String, maxChars: Int = 900): String {
        if (ocrText.length <= maxChars) return ocrText

        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ocrText.take(maxChars)

        val headerBuilder = StringBuilder()
        val keyzoneBuilder = StringBuilder()

        var headerChars = 0
        val maxHeaderChars = 550
        val headerIndices = mutableSetOf<Int>()

        for ((index, line) in lines.withIndex()) {
            if (headerChars + line.length > maxHeaderChars) break
            headerBuilder.appendLine(line)
            headerChars += line.length + 1
            headerIndices.add(index)
        }

        var keyChars = 0
        val maxKeyChars = maxChars - headerChars

        for ((index, line) in lines.withIndex()) {
            if (index in headerIndices) continue
            if (keyChars + line.length > maxKeyChars) break

            val matches = KEYWORD_PATTERNS.any { it.containsMatchIn(line) }
            if (matches) {
                keyzoneBuilder.appendLine(line)
                keyChars += line.length + 1
            }
        }

        val result = buildString {
            append("--- KOPFZEILEN (TRIAGE) ---\n")
            append(headerBuilder.toString())
            if (keyzoneBuilder.isNotBlank()) {
                append("\n--- SCHLÜSSELZEILEN ---\n")
                append(keyzoneBuilder.toString())
            }
        }

        return if (result.length > maxChars) result.take(maxChars) else result
    }
}
