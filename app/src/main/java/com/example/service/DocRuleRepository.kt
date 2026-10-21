package com.example.service

import android.content.Context
import com.example.model.DocRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repository zur Speicherung und Auswertung von deterministischen Schlagwort-Regeln.
 * Auswertung erfolgt in Millisekunden noch VOR dem Aufruf eines LLMs.
 */
class DocRuleRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("doc_rules_storage", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow<List<DocRule>>(loadRules())
    val rules: StateFlow<List<DocRule>> = _rules.asStateFlow()

    private fun loadRules(): List<DocRule> {
        val json = prefs.getString("saved_doc_rules_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<DocRule>()
            for (i in 0 until arr.length()) {
                list.add(jsonToRule(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRules(list: List<DocRule>) {
        try {
            val arr = JSONArray()
            list.forEach { r ->
                arr.put(ruleToJson(r))
            }
            prefs.edit().putString("saved_doc_rules_json", arr.toString()).apply()
            _rules.value = list
        } catch (e: Exception) {
            // Logging
        }
    }

    companion object {
        /**
         * Prüft, ob ein Keyword mittels Wortgrenzen-Analyse im Text vorkommt.
         */
        fun containsKeywordMatch(text: String, keyword: String): Boolean {
            val kw = keyword.trim()
            if (kw.length <= 4 || kw.matches(Regex("^[a-zA-Z0-9]+$"))) {
                return try {
                    Regex("\\b${Regex.escape(kw)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
                } catch (e: Exception) {
                    text.contains(kw, ignoreCase = true)
                }
            }
            return text.contains(kw, ignoreCase = true)
        }

        /**
         * Sucht eine andere aktivierte Regel, die mit der Kandidatenregel auf mindestens
         * einem möglichen OCR-Text gleichzeitig matchen könnte. Konservativ wird ein
         * Konflikt angenommen, sofern keine Pflicht-Keywords der jeweils anderen Regel
         * den Treffer durch ein Ausschluss-Keyword sicher verhindern.
         */
        fun findPotentialRuleConflict(
            existingRules: List<DocRule>,
            candidate: DocRule
        ): DocRule? {
            if (!candidate.isEnabled || candidate.matchKeywords.none { it.isNotBlank() }) return null
            return existingRules.firstOrNull { existing ->
                existing.id != candidate.id &&
                    existing.isEnabled &&
                    existing.matchKeywords.any { it.isNotBlank() } &&
                    rulesCouldMatchSameDocument(existing, candidate)
            }
        }

        fun hasPotentialRuleConflict(rules: List<DocRule>): Boolean {
            val enabled = rules.filter { it.isEnabled && it.matchKeywords.any(String::isNotBlank) }
            return enabled.indices.any { i ->
                (i + 1 until enabled.size).any { j ->
                    rulesCouldMatchSameDocument(enabled[i], enabled[j])
                }
            }
        }

        /** Only reject conflicts introduced by the proposed rules; legacy conflicts elsewhere must not block unrelated edits. */
        fun hasPotentialConflictForNewRules(
            existingRules: List<DocRule>,
            proposedRules: List<DocRule>
        ): Boolean {
            if (hasPotentialRuleConflict(proposedRules)) return true
            val replacedIds = proposedRules.map { it.id }.toSet()
            val base = existingRules.filterNot { it.id in replacedIds }
            return proposedRules.any { candidate ->
                findPotentialRuleConflict(base, candidate) != null
            }
        }

        private fun rulesCouldMatchSameDocument(left: DocRule, right: DocRule): Boolean {
            val leftRequired = left.matchKeywords.filter { it.isNotBlank() }
            val rightRequired = right.matchKeywords.filter { it.isNotBlank() }
            if (leftRequired.isEmpty() || rightRequired.isEmpty()) return false

            val leftExcludes = left.excludeKeywords.filter { it.isNotBlank() }
            val rightExcludes = right.excludeKeywords.filter { it.isNotBlank() }
            val leftAlwaysExcludedByRight = leftExcludes.any { excluded ->
                rightRequired.any { required -> containsKeywordMatch(required.lowercase(), excluded.lowercase()) }
            }
            val rightAlwaysExcludedByLeft = rightExcludes.any { excluded ->
                leftRequired.any { required -> containsKeywordMatch(required.lowercase(), excluded.lowercase()) }
            }
            return !leftAlwaysExcludedByRight && !rightAlwaysExcludedByLeft
        }

        /**
         * Liefert nur dann eine Regel, wenn genau eine aktivierte Regel alle Pflicht-
         * Keywords erfüllt und kein Ausschluss-Keyword findet. Bei Mehrdeutigkeit
         * wird null geliefert, damit der Aufrufer prüfen oder die LLM-Stufe verwenden kann.
         */
        fun findUniqueMatchingRule(rules: List<DocRule>, ocrText: String): DocRule? {
            if (ocrText.isBlank()) return null
            val normalizedText = ocrText.lowercase()
            val matches = rules.filter { rule ->
                if (!rule.isEnabled || rule.matchKeywords.isEmpty()) return@filter false
                val excluded = rule.excludeKeywords.any { keyword ->
                    keyword.isNotBlank() && containsKeywordMatch(normalizedText, keyword)
                }
                if (excluded) return@filter false
                rule.matchKeywords.all { keyword ->
                    keyword.isNotBlank() && containsKeywordMatch(normalizedText, keyword)
                }
            }
            return matches.singleOrNull()
        }

        fun ruleToJson(r: DocRule): JSONObject {
            // (existing ruleToJson content)
            return JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("matchKeywords", JSONArray(r.matchKeywords))
                put("excludeKeywords", JSONArray(r.excludeKeywords))
                put("targetMainCategoryId", r.targetMainCategoryId)
                put("targetSubCategoryId", r.targetSubCategoryId)
                put("targetDocType", r.targetDocType)
                put("detectedSender", r.detectedSender)
                put("targetFolderPath", r.targetFolderPath)
                put("targetTags", JSONArray(r.targetTags))
                put("isEnabled", r.isEnabled)
                put("confidenceScore", r.confidenceScore.toDouble())
                put("isAiGenerated", r.isAiGenerated)
                val cfObj = JSONObject()
                r.targetCustomFields.forEach { (k, v) -> cfObj.put(k, v) }
                put("targetCustomFields", cfObj)
                put("targetIcon", r.targetIcon)
                put("targetLogo", r.targetLogo)
            }
        }
        
        fun jsonToRule(obj: JSONObject): DocRule {
            // (existing jsonToRule content)
            val matchArr = obj.optJSONArray("matchKeywords") ?: JSONArray()
            val matchWords = (0 until matchArr.length()).map { matchArr.getString(it) }

            val exclArr = obj.optJSONArray("excludeKeywords") ?: JSONArray()
            val exclWords = (0 until exclArr.length()).map { exclArr.getString(it) }

            val tagsArr = obj.optJSONArray("targetTags") ?: JSONArray()
            val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }

            val cfObj = obj.optJSONObject("targetCustomFields") ?: JSONObject()
            val customFieldsMap = mutableMapOf<String, String>()
            val cfKeys = cfObj.keys()
            while (cfKeys.hasNext()) {
                val key = cfKeys.next()
                customFieldsMap[key] = cfObj.getString(key)
            }

            return DocRule(
                id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                name = obj.optString("name", "Regel"),
                matchKeywords = matchWords,
                excludeKeywords = exclWords,
                targetMainCategoryId = obj.optString("targetMainCategoryId", "A01"),
                targetSubCategoryId = obj.optString("targetSubCategoryId", "B1.01"),
                targetDocType = obj.optString("targetDocType", "Dokument"),
                detectedSender = obj.optString("detectedSender", ""),
                targetFolderPath = obj.optString("targetFolderPath", ""),
                targetTags = tags,
                isEnabled = obj.optBoolean("isEnabled", true),
                confidenceScore = obj.optDouble("confidenceScore", 1.0).toFloat(),
                isAiGenerated = obj.optBoolean("isAiGenerated", false),
                targetCustomFields = customFieldsMap,
                targetIcon = obj.optString("targetIcon", ""),
                targetLogo = obj.optString("targetLogo", "")
            )
        }
    }

    /**
     * Neue Regeln werden nur gespeichert, wenn sie keine aktivierte Regeldefinition
     * erzeugen, die mit einer anderen Definition gleichzeitig matchen könnte.
     * Runtime-Matching bleibt zusätzlich fail-closed.
     */
    fun addRule(rule: DocRule): Boolean {
        val current = _rules.value.filterNot { it.id == rule.id }
        if (findPotentialRuleConflict(current, rule) != null) return false
        saveRules(listOf(rule) + current)
        return true
    }

    fun addRules(newRules: List<DocRule>): Boolean {
        if (hasPotentialConflictForNewRules(_rules.value, newRules)) return false
        val replacedIds = newRules.map { it.id }.toSet()
        val base = _rules.value.filterNot { it.id in replacedIds }
        saveRules(newRules + base)
        return true
    }

    fun updateRule(rule: DocRule): Boolean {
        val current = _rules.value.filterNot { it.id == rule.id }
        if (findPotentialRuleConflict(current, rule) != null) return false
        saveRules(listOf(rule) + current)
        return true
    }

    fun deleteRule(ruleId: String) {
        val current = _rules.value.filter { it.id != ruleId }
        saveRules(current)
    }

    fun toggleRule(ruleId: String, enabled: Boolean): Boolean {
        val current = _rules.value.map {
            if (it.id == ruleId) it.copy(isEnabled = enabled) else it
        }
        val candidate = current.firstOrNull { it.id == ruleId }
        if (enabled && candidate != null &&
            findPotentialRuleConflict(current.filterNot { it.id == ruleId }, candidate) != null
        ) return false
        saveRules(current)
        return true
    }

    /**
     * Schneller Regel-Check des OCR-Textes (Schritt 1 VOR dem LLM)
     * Gibt nur bei genau einem passenden Treffer eine Regel zurück, sonst null
     */
    fun matchRule(ocrText: String): DocRule? =
        findUniqueMatchingRule(_rules.value, ocrText)

    /**
     * Prüft, ob bereits eine existierende Regel für diesen Absender oder Dokumenttyp vorliegt.
     * Ermöglicht transparente Hinweise im Vorschlags-Dialog.
     */
    fun findMatchingOrSimilarRule(sender: String, docType: String): DocRule? {
        val s = sender.trim().lowercase()
        val dt = docType.trim().lowercase()
        return _rules.value.firstOrNull { rule ->
            (s.isNotBlank() && rule.detectedSender.isNotBlank() && (rule.detectedSender.lowercase() == s || s.contains(rule.detectedSender.lowercase()))) ||
            (s.isNotBlank() && dt.isNotBlank() && rule.name.lowercase().contains(s) && rule.name.lowercase().contains(dt))
        }
    }
}
