package com.example.service

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

/**
 * Schnittstelle zur Ausführung nativer App-Aktionen, die vom LLM per Function Calling ausgelöst werden.
 */
interface ToolActionHandler {
    suspend fun onFilterDocuments(sender: String?, status: String?, category: String?): String
    suspend fun onUpdateDocumentStatus(docId: String, newStatus: String): String
    suspend fun onOpenAppSection(section: String): String
    suspend fun onCreateFolder(folderName: String, parentCategory: String?): String
    suspend fun onCreateCustomDashboardWidget(
        title: String,
        subtitle: String,
        widgetType: String,
        targetCategory: String,
        targetTag: String,
        numericLimit: Double,
        colorHex: Long,
        targetSubCategory: String = "",
        targetCustomFieldName: String = "",
        targetCustomFieldValue: String = "",
        noteContent: String = "",
        iconName: String = "analytics"
    ): String
}

/**
 * Der Function-Router für On-Device LLM Function Calling in myDocAnizer-Mobile.
 *
 * Verantwortlichkeiten:
 * 1. Sicheres und fehlertolerantes Parsen des LLM-Outputs via kotlinx.serialization und org.json Fallback.
 * 2. Erkennen von Tool-Calls im OpenAI-Standardformat:
 *    { "name": "filterDocuments", "arguments": { ... } }
 * 3. Ausführung der nativen Kotlin-App-Logik (z.B. StateFlow-Updates, Room-Datenbank-Operationen).
 * 4. Rückgabe von formatierten Bestätigungsmeldungen für das Chat-UI oder nahtlose Weitergabe von normalem Text.
 */
class LlmToolRouter(
    private val actionHandler: ToolActionHandler? = null
) {
    companion object {
        private const val TAG = "LlmToolRouter"

        private val jsonParser = Json {
            isLenient = true
            ignoreUnknownKeys = true
            allowSpecialFloatingPointValues = true
        }
    }

    /**
     * Parst eine rohe Antwort des LLMs fehlertolerant.
     * Erkennt JSON-Tool-Calls selbst wenn sie in Markdown-Codeblöcke eingebettet sind
     * oder geringfügige Syntaxabweichungen aufweisen.
     */
    fun parseToolCall(rawResponse: String): AppToolCall? {
        val trimmed = rawResponse.trim()
        if (trimmed.isBlank()) return null

        val jsonCandidate = extractJsonString(trimmed) ?: return null

        // 1. Primäres Parsen via kotlinx.serialization
        try {
            val element = jsonParser.parseToJsonElement(jsonCandidate)
            if (element is JsonObject) {
                val call = parseFromKotlinxJsonObject(element)
                if (call != null) return call
            }
        } catch (e: Exception) {
            Log.d(TAG, "kotlinx.serialization Parsing fehlgeschlagen (${e.message}), starte org.json Fallback")
        }

        // 2. Sekundäres fehlertolerantes Parsen via org.json
        try {
            val jsonObj = JSONObject(jsonCandidate)
            return parseFromOrgJsonObject(jsonObj)
        } catch (e: Exception) {
            Log.d(TAG, "org.json Parsing fehlgeschlagen: ${e.message}")
        }

        return null
    }

    private fun parseFromKotlinxJsonObject(json: JsonObject): AppToolCall? {
        // Mögliche Namensfelder: "name", "tool", "function", oder verschachtelt in "tool_call"
        var toolName = json["name"]?.jsonPrimitive?.contentOrNull
            ?: json["tool"]?.jsonPrimitive?.contentOrNull
            ?: json["function"]?.jsonPrimitive?.contentOrNull

        var argsObj: JsonObject? = json["arguments"]?.let {
            if (it is JsonObject) it
            else {
                // Manche Modelle liefern "arguments" als serialisierten String
                try {
                    val parsed = jsonParser.parseToJsonElement(it.jsonPrimitive.content)
                    if (parsed is JsonObject) parsed else null
                } catch (_: Exception) {
                    null
                }
            }
        } ?: json["parameters"]?.let { if (it is JsonObject) it else null }

        // Verschachteltes Format: { "tool_call": { "name": ..., "arguments": ... } }
        if (toolName == null && json.containsKey("tool_call")) {
            val nested = json["tool_call"]
            if (nested is JsonObject) {
                toolName = nested["name"]?.jsonPrimitive?.contentOrNull
                argsObj = nested["arguments"] as? JsonObject
            }
        }

        if (toolName.isNullOrBlank()) {
            return null
        }

        return when (toolName.trim()) {
            AppTool.FilterDocuments.name -> {
                val sender = argsObj?.get("sender")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                val status = argsObj?.get("status")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                val category = argsObj?.get("category")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                AppToolCall.FilterDocuments(sender = sender, status = status, category = category)
            }
            AppTool.UpdateDocumentStatus.name -> {
                val docId = argsObj?.get("docId")?.jsonPrimitive?.contentOrNull
                    ?: argsObj?.get("id")?.jsonPrimitive?.contentOrNull
                    ?: argsObj?.get("documentId")?.jsonPrimitive?.contentOrNull
                    ?: ""
                val newStatus = argsObj?.get("newStatus")?.jsonPrimitive?.contentOrNull
                    ?: argsObj?.get("status")?.jsonPrimitive?.contentOrNull
                    ?: ""
                if (docId.isNotBlank() && newStatus.isNotBlank()) {
                    AppToolCall.UpdateDocumentStatus(docId = docId.trim(), newStatus = newStatus.trim())
                } else {
                    AppToolCall.Unknown(toolName, argsObj.toString())
                }
            }
            AppTool.OpenAppSection.name -> {
                val section = argsObj?.get("section")?.jsonPrimitive?.contentOrNull
                    ?: argsObj?.get("target")?.jsonPrimitive?.contentOrNull
                    ?: "dashboard"
                AppToolCall.OpenAppSection(section = section)
            }
            AppTool.CreateFolder.name -> {
                val folderName = argsObj?.get("folderName")?.jsonPrimitive?.contentOrNull
                    ?: argsObj?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: argsObj?.get("folder")?.jsonPrimitive?.contentOrNull
                    ?: ""
                val parentCategory = argsObj?.get("parentCategory")?.jsonPrimitive?.contentOrNull
                    ?: argsObj?.get("parent")?.jsonPrimitive?.contentOrNull
                    ?: argsObj?.get("category")?.jsonPrimitive?.contentOrNull
                if (folderName.isNotBlank()) {
                    AppToolCall.CreateFolder(folderName = folderName.trim(), parentCategory = parentCategory?.trim())
                } else {
                    AppToolCall.Unknown(toolName, argsObj?.toString() ?: "{}")
                }
            }
            AppTool.CreateCustomDashboardWidget.name -> {
                val title = argsObj?.get("title")?.jsonPrimitive?.contentOrNull ?: ""
                val subtitle = argsObj?.get("subtitle")?.jsonPrimitive?.contentOrNull ?: ""
                val widgetType = argsObj?.get("widgetType")?.jsonPrimitive?.contentOrNull ?: "FOLDER_MONITOR"
                val targetCategory = argsObj?.get("targetCategory")?.jsonPrimitive?.contentOrNull ?: ""
                val targetTag = argsObj?.get("targetTag")?.jsonPrimitive?.contentOrNull ?: ""
                val numericLimit = argsObj?.get("numericLimit")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
                val colorHex = argsObj?.get("colorHex")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0xFF2563EB
                val targetSubCategory = argsObj?.get("targetSubCategory")?.jsonPrimitive?.contentOrNull ?: ""
                val targetCustomFieldName = argsObj?.get("targetCustomFieldName")?.jsonPrimitive?.contentOrNull ?: ""
                val targetCustomFieldValue = argsObj?.get("targetCustomFieldValue")?.jsonPrimitive?.contentOrNull ?: ""
                val noteContent = argsObj?.get("noteContent")?.jsonPrimitive?.contentOrNull ?: ""
                val iconName = argsObj?.get("iconName")?.jsonPrimitive?.contentOrNull ?: "analytics"

                if (title.isNotBlank()) {
                    AppToolCall.CreateCustomDashboardWidget(
                        title = title.trim(),
                        subtitle = subtitle.trim(),
                        widgetType = widgetType.trim(),
                        targetCategory = targetCategory.trim(),
                        targetTag = targetTag.trim(),
                        numericLimit = numericLimit,
                        colorHex = colorHex,
                        targetSubCategory = targetSubCategory.trim(),
                        targetCustomFieldName = targetCustomFieldName.trim(),
                        targetCustomFieldValue = targetCustomFieldValue.trim(),
                        noteContent = noteContent.trim(),
                        iconName = iconName.trim()
                    )
                } else {
                    AppToolCall.Unknown(toolName, argsObj.toString())
                }
            }
            else -> {
                AppToolCall.Unknown(toolName = toolName, argumentsJson = argsObj?.toString() ?: "{}")
            }
        }
    }

    private fun parseFromOrgJsonObject(json: JSONObject): AppToolCall? {
        var toolName = json.optString("name", "").takeIf { it.isNotBlank() }
            ?: json.optString("tool", "").takeIf { it.isNotBlank() }
            ?: json.optString("function", "").takeIf { it.isNotBlank() }

        var argsObj: JSONObject? = json.optJSONObject("arguments")
            ?: json.optJSONObject("parameters")

        if (argsObj == null && json.has("arguments")) {
            val argsStr = json.optString("arguments", "")
            if (argsStr.startsWith("{")) {
                try {
                    argsObj = JSONObject(argsStr)
                } catch (_: Exception) {}
            }
        }

        if (toolName == null && json.has("tool_call")) {
            val nested = json.optJSONObject("tool_call")
            if (nested != null) {
                toolName = nested.optString("name", "").takeIf { it.isNotBlank() }
                argsObj = nested.optJSONObject("arguments")
            }
        }

        if (toolName.isNullOrBlank()) return null

        return when (toolName.trim()) {
            AppTool.FilterDocuments.name -> {
                val sender = argsObj?.optString("sender", "")?.takeIf { it.isNotBlank() && it != "null" }
                val status = argsObj?.optString("status", "")?.takeIf { it.isNotBlank() && it != "null" }
                val category = argsObj?.optString("category", "")?.takeIf { it.isNotBlank() && it != "null" }
                AppToolCall.FilterDocuments(sender = sender, status = status, category = category)
            }
            AppTool.UpdateDocumentStatus.name -> {
                val docId = argsObj?.optString("docId", "")?.takeIf { it.isNotBlank() }
                    ?: argsObj?.optString("id", "")?.takeIf { it.isNotBlank() }
                    ?: argsObj?.optString("documentId", "")?.takeIf { it.isNotBlank() }
                    ?: ""
                val newStatus = argsObj?.optString("newStatus", "")?.takeIf { it.isNotBlank() }
                    ?: argsObj?.optString("status", "")?.takeIf { it.isNotBlank() }
                    ?: ""
                if (docId.isNotBlank() && newStatus.isNotBlank()) {
                    AppToolCall.UpdateDocumentStatus(docId = docId.trim(), newStatus = newStatus.trim())
                } else {
                    AppToolCall.Unknown(toolName, argsObj?.toString() ?: "{}")
                }
            }
            AppTool.OpenAppSection.name -> {
                val section = argsObj?.optString("section", "dashboard") ?: "dashboard"
                AppToolCall.OpenAppSection(section = section)
            }
            AppTool.CreateFolder.name -> {
                val folderName = argsObj?.optString("folderName", "")?.takeIf { it.isNotBlank() }
                    ?: argsObj?.optString("name", "")?.takeIf { it.isNotBlank() }
                    ?: argsObj?.optString("folder", "")?.takeIf { it.isNotBlank() }
                    ?: ""
                val parentCategory = argsObj?.optString("parentCategory", "")?.takeIf { it.isNotBlank() }
                    ?: argsObj?.optString("parent", "")?.takeIf { it.isNotBlank() }
                    ?: argsObj?.optString("category", "")?.takeIf { it.isNotBlank() }
                if (folderName.isNotBlank()) {
                    AppToolCall.CreateFolder(folderName = folderName.trim(), parentCategory = parentCategory?.trim())
                } else {
                    AppToolCall.Unknown(toolName, argsObj?.toString() ?: "{}")
                }
            }
            AppTool.CreateCustomDashboardWidget.name -> {
                val title = argsObj?.optString("title", "") ?: ""
                val subtitle = argsObj?.optString("subtitle", "") ?: ""
                val widgetType = argsObj?.optString("widgetType", "FOLDER_MONITOR") ?: "FOLDER_MONITOR"
                val targetCategory = argsObj?.optString("targetCategory", "") ?: ""
                val targetTag = argsObj?.optString("targetTag", "") ?: ""
                val numericLimit = argsObj?.optDouble("numericLimit", 0.0) ?: 0.0
                val colorHex = argsObj?.optLong("colorHex", 0xFF2563EBL) ?: 0xFF2563EBL
                val targetSubCategory = argsObj?.optString("targetSubCategory", "") ?: ""
                val targetCustomFieldName = argsObj?.optString("targetCustomFieldName", "") ?: ""
                val targetCustomFieldValue = argsObj?.optString("targetCustomFieldValue", "") ?: ""
                val noteContent = argsObj?.optString("noteContent", "") ?: ""
                val iconName = argsObj?.optString("iconName", "analytics") ?: "analytics"

                if (title.isNotBlank()) {
                    AppToolCall.CreateCustomDashboardWidget(
                        title = title.trim(),
                        subtitle = subtitle.trim(),
                        widgetType = widgetType.trim(),
                        targetCategory = targetCategory.trim(),
                        targetTag = targetTag.trim(),
                        numericLimit = numericLimit,
                        colorHex = colorHex,
                        targetSubCategory = targetSubCategory.trim(),
                        targetCustomFieldName = targetCustomFieldName.trim(),
                        targetCustomFieldValue = targetCustomFieldValue.trim(),
                        noteContent = noteContent.trim(),
                        iconName = iconName.trim()
                    )
                } else {
                    AppToolCall.Unknown(toolName, argsObj?.toString() ?: "{}")
                }
            }
            else -> {
                AppToolCall.Unknown(toolName = toolName, argumentsJson = argsObj?.toString() ?: "{}")
            }
        }
    }

    /**
     * Extrahiert den JSON-String aus Markdown-Codeblöcken oder findet die äußeren geschweiften Klammern.
     */
    fun extractJsonString(raw: String): String? {
        var str = raw.trim()

        // Filter out <think>...</think> blocks if present (for DeepSeek-R1 Distill)
        if (str.contains("<think>") && str.contains("</think>")) {
            str = str.substringAfter("</think>").trim()
        }

        if (str.contains("```json")) {
            str = str.substringAfter("```json").substringBefore("```").trim()
        } else if (str.contains("```")) {
            val block = str.substringAfter("```").substringBefore("```").trim()
            if (block.startsWith("{") && block.endsWith("}")) {
                str = block
            }
        }

        val firstBrace = str.indexOf('{')
        val lastBrace = str.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            val candidate = str.substring(firstBrace, lastBrace + 1).trim()
            // Prüfe rudimentär, ob es nach Tool-Call aussieht
            if (candidate.contains("\"name\"") || candidate.contains("\"tool\"") || 
                candidate.contains("\"filterDocuments\"") || candidate.contains("\"updateDocumentStatus\"") ||
                candidate.contains("\"createCustomDashboardWidget\"") || candidate.contains("\"createFolder\"") || 
                candidate.contains("\"openAppSection\"")) {
                return candidate
            }
        }

        return if (str.startsWith("{") && str.endsWith("}")) str else null
    }

    /**
     * Hauptmethode des Routers:
     * Parst die Antwort des LLMs.
     * - Wenn ein valider Tool-Call erkannt wird: Führt die native App-Aktion aus und liefert das Ergebnis zurück.
     * - Wenn normaler Text vorliegt: Gibt den Text direkt aus.
     */
    suspend fun routeResponse(rawResponse: String): LlmRouterResult {
        return try {
            val toolCall = parseToolCall(rawResponse)
            if (toolCall != null) {
                executeToolCall(toolCall)
            } else {
                LlmRouterResult.Text(replyText = rawResponse.trim())
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Unerwarteter Fehler im Function-Router: ${e.message}", e)
            // 100% absturzsicher: Im Fehlerfall niemals die App crashen lassen!
            LlmRouterResult.Text(replyText = rawResponse.trim())
        }
    }

    /**
     * Führt einen konkreten [AppToolCall] auf der nativen App-Ebene aus.
     */
    suspend fun executeToolCall(toolCall: AppToolCall): LlmRouterResult {
        return when (toolCall) {
            is AppToolCall.FilterDocuments -> {
                val confirmation = actionHandler?.onFilterDocuments(
                    sender = toolCall.sender,
                    status = toolCall.status,
                    category = toolCall.category
                ) ?: defaultFilterConfirmation(toolCall)

                LlmRouterResult.ToolCallExecuted(
                    toolCall = toolCall,
                    confirmationMessage = confirmation,
                    actionType = "OPEN_EXPLORER",
                    actionLabel = "📂 Gefilterten Tresor anzeigen",
                    success = true
                )
            }

            is AppToolCall.UpdateDocumentStatus -> {
                val confirmation = actionHandler?.onUpdateDocumentStatus(
                    docId = toolCall.docId,
                    newStatus = toolCall.newStatus
                ) ?: defaultUpdateStatusConfirmation(toolCall)

                LlmRouterResult.ToolCallExecuted(
                    toolCall = toolCall,
                    confirmationMessage = confirmation,
                    actionType = "OPEN_EXPLORER",
                    actionLabel = "📂 Zum Dokument im Tresor",
                    success = true
                )
            }

            is AppToolCall.OpenAppSection -> {
                val confirmation = actionHandler?.onOpenAppSection(toolCall.section)
                    ?: "Ich öffne den Bereich '${toolCall.section}' für dich."

                val (actionType, actionLabel) = when (toolCall.section.lowercase()) {
                    "scanner" -> Pair("OPEN_SCANNER", "📷 Scanner öffnen")
                    "dashboard" -> Pair("OPEN_DASHBOARD", "📊 Zum Dashboard")
                    "trash" -> Pair("OPEN_TRASH", "🗑️ Zum Papierkorb")
                    "settings" -> Pair("OPEN_SETTINGS", "⚙️ Zu den Einstellungen")
                    "deadlines" -> Pair("OPEN_DEADLINES", "⏰ Fristen-Radar aufrufen")
                    "import" -> Pair("OPEN_IMPORT", "📥 Datei-Import öffnen")
                    else -> Pair("OPEN_DASHBOARD", "📊 Zum Dashboard")
                }

                LlmRouterResult.ToolCallExecuted(
                    toolCall = toolCall,
                    confirmationMessage = confirmation,
                    actionType = actionType,
                    actionLabel = actionLabel,
                    success = true
                )
            }

            is AppToolCall.CreateFolder -> {
                val confirmation = actionHandler?.onCreateFolder(
                    folderName = toolCall.folderName,
                    parentCategory = toolCall.parentCategory
                ) ?: "📁 Der Ordner **„${toolCall.folderName}“** wurde erfolgreich im Dokumenten-Tresor erstellt!"

                LlmRouterResult.ToolCallExecuted(
                    toolCall = toolCall,
                    confirmationMessage = confirmation,
                    actionType = "OPEN_EXPLORER",
                    actionLabel = "📂 Neuen Ordner im Tresor anzeigen",
                    success = true
                )
            }

            is AppToolCall.CreateCustomDashboardWidget -> {
                val confirmation = actionHandler?.onCreateCustomDashboardWidget(
                    title = toolCall.title,
                    subtitle = toolCall.subtitle,
                    widgetType = toolCall.widgetType,
                    targetCategory = toolCall.targetCategory,
                    targetTag = toolCall.targetTag,
                    numericLimit = toolCall.numericLimit,
                    colorHex = toolCall.colorHex,
                    targetSubCategory = toolCall.targetSubCategory,
                    targetCustomFieldName = toolCall.targetCustomFieldName,
                    targetCustomFieldValue = toolCall.targetCustomFieldValue,
                    noteContent = toolCall.noteContent,
                    iconName = toolCall.iconName
                ) ?: "Smartes Dashboard-Element ‚${toolCall.title}‘ wurde erfolgreich erstellt!"

                LlmRouterResult.ToolCallExecuted(
                    toolCall = toolCall,
                    confirmationMessage = confirmation,
                    actionType = "OPEN_DASHBOARD",
                    actionLabel = "📊 Zum Dashboard",
                    success = true
                )
            }

            is AppToolCall.Unknown -> {
                val msg = "ℹ️ Der Befehl '${toolCall.toolName}' ist nicht registriert."
                LlmRouterResult.ToolCallExecuted(
                    toolCall = toolCall,
                    confirmationMessage = msg,
                    success = false
                )
            }
        }
    }

    private fun defaultFilterConfirmation(call: AppToolCall.FilterDocuments): String {
        val criteria = mutableListOf<String>()
        call.sender?.let { criteria.add("Absender: **$it**") }
        call.status?.let { criteria.add("Status: **$it**") }
        call.category?.let { criteria.add("Kategorie: **$it**") }

        return if (criteria.isNotEmpty()) {
            "🔍 **Dokumenten-Filter angewendet:**\n${criteria.joinToString(" • ")}\n\nDie Liste im Dokumenten-Tresor wurde entsprechend aktualisiert."
        } else {
            "🔍 Alle Filter im Dokumenten-Tresor wurden zurückgesetzt."
        }
    }

    private fun defaultUpdateStatusConfirmation(call: AppToolCall.UpdateDocumentStatus): String {
        return "✅ Der Bearbeitungsstatus für Dokument-ID **#${call.docId}** wurde erfolgreich auf **„${call.newStatus}“** gesetzt."
    }
}
