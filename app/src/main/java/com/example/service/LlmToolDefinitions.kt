package com.example.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONArray
import org.json.JSONObject

/**
 * Definition eines Parameters für ein LLM Function-Calling Tool im JSON-Schema Standard.
 */
data class ToolParameter(
    val name: String,
    val type: String, // "string", "number", "boolean", "array"
    val description: String,
    val isRequired: Boolean = false,
    val enumValues: List<String>? = null
)

/**
 * Repräsentiert ein registriertes natives App-Tool mit OpenAI-kompatibler Funktionsdefinition.
 */
sealed class AppTool(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>
) {
    /**
     * Erstellt ein standardisiertes OpenAI-kompatibles JSON-Objekt für dieses Tool.
     */
    fun toOpenAiToolSchema(): JSONObject {
        val toolObj = JSONObject()
        toolObj.put("type", "function")

        val funcObj = JSONObject()
        funcObj.put("name", name)
        funcObj.put("description", description)

        val paramsObj = JSONObject()
        paramsObj.put("type", "object")

        val propertiesObj = JSONObject()
        val requiredList = JSONArray()

        for (param in parameters) {
            val propObj = JSONObject()
            propObj.put("type", param.type)
            propObj.put("description", param.description)
            if (!param.enumValues.isNullOrEmpty()) {
                propObj.put("enum", JSONArray(param.enumValues))
            }
            propertiesObj.put(param.name, propObj)
            if (param.isRequired) {
                requiredList.put(param.name)
            }
        }

        paramsObj.put("properties", propertiesObj)
        paramsObj.put("required", requiredList)
        funcObj.put("parameters", paramsObj)
        toolObj.put("function", funcObj)

        return toolObj
    }

    /**
     * Konvertiert das Schema zu einem kotlinx.serialization JsonObject für typsichere Weiterverarbeitung.
     */
    fun toKotlinxJsonObject(): JsonObject {
        return buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", name)
                put("description", description)
                putJsonObject("parameters") {
                    put("type", "object")
                    putJsonObject("properties") {
                        parameters.forEach { param ->
                            putJsonObject(param.name) {
                                put("type", param.type)
                                put("description", param.description)
                                param.enumValues?.let { enums ->
                                    putJsonArray("enum") {
                                        enums.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                                    }
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        parameters.filter { it.isRequired }.forEach { param ->
                            add(kotlinx.serialization.json.JsonPrimitive(param.name))
                        }
                    }
                }
            }
        }
    }

    // 1. Tool: Dokumente filtern
    object FilterDocuments : AppTool(
        name = "filterDocuments",
        description = "Filtert die Dokumentenliste im Dokumenten-Tresor der App nach Absender/Firma, Bearbeitungsstatus oder Hauptkategorie.",
        parameters = listOf(
            ToolParameter(
                name = "sender",
                type = "string",
                description = "Name des Absenders oder der Firma (z.B. 'Telekom', 'Vodafone', 'Allianz', 'Finanzamt').",
                isRequired = false
            ),
            ToolParameter(
                name = "status",
                type = "string",
                description = "Bearbeitungsstatus des Dokuments (z.B. 'Offen', 'Bezahlt', 'Gekündigt', 'In Bearbeitung', 'Prüfen').",
                isRequired = false,
                enumValues = listOf("Offen", "Bezahlt", "Gekündigt", "In Bearbeitung", "Prüfen")
            ),
            ToolParameter(
                name = "category",
                type = "string",
                description = "Hauptkategorie oder Ordner im Tresor (z.B. 'Finanzen', 'Verträge', 'Versicherungen', 'Steuern', 'Allgemein').",
                isRequired = false
            )
        )
    )

    // 2. Tool: Dokument-Status ändern
    object UpdateDocumentStatus : AppTool(
        name = "updateDocumentStatus",
        description = "Aktualisiert den Bearbeitungsstatus eines Dokuments im Dokumenten-Tresor anhand seiner eindeutigen Dokumenten-ID.",
        parameters = listOf(
            ToolParameter(
                name = "docId",
                type = "string",
                description = "Die eindeutige numerische ID des Dokuments (z.B. '1', '12', '42').",
                isRequired = true
            ),
            ToolParameter(
                name = "newStatus",
                type = "string",
                description = "Der neue Status, der dem Dokument zugewiesen werden soll.",
                isRequired = true,
                enumValues = listOf("Offen", "Bezahlt", "Gekündigt", "In Bearbeitung", "Prüfen")
            )
        )
    )

    // 3. Optionales Zusatz-Tool: App-Bereich öffnen
    object OpenAppSection : AppTool(
        name = "openAppSection",
        description = "Öffnet direkt einen nativen Bereich oder Screen innerhalb der App.",
        parameters = listOf(
            ToolParameter(
                name = "section",
                type = "string",
                description = "Der Zielbereich in der App.",
                isRequired = true,
                enumValues = listOf("scanner", "dashboard", "trash", "settings", "deadlines", "import")
            )
        )
    )

    // 4. Tool: Neuen Ordner / Kategorie im Dokumenten-Tresor anlegen
    object CreateFolder : AppTool(
        name = "createFolder",
        description = "Erstellt direkt und physisch einen neuen Ordner bzw. eine neue Kategorie im Dokumenten-Tresor der App.",
        parameters = listOf(
            ToolParameter(
                name = "folderName",
                type = "string",
                description = "Der Name des anzulegenden Ordners (z.B. 'Steuern 2024', 'Versicherungen', 'KfZ', 'Reisebelege').",
                isRequired = true
            ),
            ToolParameter(
                name = "parentCategory",
                type = "string",
                description = "Optionale übergeordnete Kategorie (z.B. 'Finanzen', 'Verträge', 'Privat').",
                isRequired = false
            )
        )
    )

    // 5. Tool: Smartes Dashboard-Element erstellen
    object CreateCustomDashboardWidget : AppTool(
        name = "createCustomDashboardWidget",
        description = "Erstellt ein neues smartes Dashboard-Element bzw. Widget (z.B. Ordner-Monitor, Fristen-Radar, Budget-Limit) im Dashboard der App.",
        parameters = listOf(
            ToolParameter(
                name = "title",
                type = "string",
                description = "Titel des Dashboard-Elements (z.B. 'Steuern 2024', 'Monatsbudget')",
                isRequired = true
            ),
            ToolParameter(
                name = "subtitle",
                type = "string",
                description = "Untertitel oder Beschreibung des Widgets",
                isRequired = false
            ),
            ToolParameter(
                name = "widgetType",
                type = "string",
                description = "Typ des Widgets",
                isRequired = true,
                enumValues = listOf("FOLDER_MONITOR", "CUSTOM_FIELD_MONITOR", "DEADLINE_MONITOR", "BUDGET_LIMIT", "QUICK_SHORTCUT", "STICKY_NOTE")
            ),
            ToolParameter(
                name = "targetCategory",
                type = "string",
                description = "Zielkategorie im Tresor",
                isRequired = false
            ),
            ToolParameter(
                name = "targetTag",
                type = "string",
                description = "Ziel-Tag (z.B. #garantie)",
                isRequired = false
            ),
            ToolParameter(
                name = "numericLimit",
                type = "number",
                description = "Numerisches Limit oder Budget-Betrag",
                isRequired = false
            ),
            ToolParameter(
                name = "colorHex",
                type = "number",
                description = "Farb-Hex-Wert als Long",
                isRequired = false
            )
        )
    )

    companion object {
        /**
         * Standard-Liste aller im Chatbot registrierten nativen Tools.
         */
        val DEFAULT_TOOLS: List<AppTool>
            get() = listOf(
                FilterDocuments,
                UpdateDocumentStatus,
                CreateFolder,
                OpenAppSection,
                CreateCustomDashboardWidget
            )
    }
}

/**
 * Typisierte Darstellung eines vom LLM angeforderten Tool-Aufrufs.
 */
sealed class AppToolCall {
    data class FilterDocuments(
        val sender: String? = null,
        val status: String? = null,
        val category: String? = null
    ) : AppToolCall()

    data class UpdateDocumentStatus(
        val docId: String,
        val newStatus: String
    ) : AppToolCall()

    data class OpenAppSection(
        val section: String
    ) : AppToolCall()

    data class CreateFolder(
        val folderName: String,
        val parentCategory: String? = null
    ) : AppToolCall()

    data class CreateCustomDashboardWidget(
        val title: String,
        val subtitle: String = "",
        val widgetType: String = "FOLDER_MONITOR",
        val targetCategory: String = "",
        val targetTag: String = "",
        val numericLimit: Double = 0.0,
        val colorHex: Long = 0xFF2563EB
    ) : AppToolCall()

    data class Unknown(
        val toolName: String,
        val argumentsJson: String
    ) : AppToolCall()
}

/**
 * Ergebnis einer LLM-Antwort nach der Router-Prüfung:
 * Entweder ein normaler Textbeitrag oder ein ausgeführter nativer Tool-Call.
 */
sealed class LlmRouterResult {
    data class Text(
        val replyText: String
    ) : LlmRouterResult()

    data class ToolCallExecuted(
        val toolCall: AppToolCall,
        val confirmationMessage: String,
        val actionType: String? = null,
        val actionLabel: String? = null,
        val success: Boolean = true
    ) : LlmRouterResult()
}
