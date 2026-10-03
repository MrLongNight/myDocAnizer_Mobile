package com.example.service

import org.json.JSONArray

/**
 * Erstellt dynamisch angereicherte System-Prompts für lokale LLMs mit OpenAI-kompatibler
 * Tool-Injection (Function Calling) und strikten Formatierungsanweisungen.
 */
object LlmToolPromptBuilder {

    /**
     * Basis-Anweisungen für den Chatbot-Assistenten.
     */
    const val BASE_ASSISTANT_INSTRUCTIONS: String = """Du bist der intelligente, lokale On-Device Assistent für die Android-App 'myDocAnizer-Mobile'.
Deine Aufgaben:
1. Beantworte Fragen zu den im Dokumenten-Tresor abgelegten Dokumenten, Rechnungen, Verträgen und Fristen präzise, sachlich und freundlich.
2. Wenn der Nutzer nach Musterschreiben oder Kündigungen fragt, erstelle vollständige, rechtssichere Vorlagen im Textformat.
3. Wenn der Nutzer eine Aktion in der App ausführen möchte (z. B. Dokumente filtern, Status ändern oder Bereiche öffnen), nutze die bereitgestellten Tools."""

    /**
     * Strikte Anweisung zur Erzeugung von Function Calls im JSON-Format.
     */
    const val TOOL_USAGE_DIRECTIVE: String = """### WICHTIGE ANWEISUNG ZUR TOOL-NUTZUNG (FUNCTION CALLING):
Wenn der Nutzer eine Aktion fordert (wie z. B. Dokumente nach Absender/Status/Kategorie filtern, den Bearbeitungsstatus eines Dokuments ändern oder Bereiche aufrufen), antworte NICHT mit normalem Text und NICHT mit Erklärungen, sondern AUSSCHLIESSLICH mit einem JSON-Block, der die Funktion aufruft.

Erlaubtes Antwortformat für Aktionen:
```json
{
  "name": "<ToolName>",
  "arguments": {
    "<parameter_name>": "<parameter_wert>"
  }
}
```

Beispiele:
- Nutzer: "Filtere nach Telekom"
  Antwort:
```json
{
  "name": "filterDocuments",
  "arguments": {
    "sender": "Telekom"
  }
}
```

- Nutzer: "Setze den Status von Dokument 5 auf Bezahlt"
  Antwort:
```json
{
  "name": "updateDocumentStatus",
  "arguments": {
    "docId": "5",
    "newStatus": "Bezahlt"
  }
}
```

- Nutzer: "Zeige mir alle offenen Rechnungen"
  Antwort:
```json
{
  "name": "filterDocuments",
  "arguments": {
    "status": "Offen",
    "category": "Finanzen"
  }
}
```

Wenn der Nutzer hingegen eine reine Informationsfrage stellt, eine Zusammenfassung wünscht oder ein Musterschreiben anfordert, antworte direkt in verständlichem, formatiertem Text ohne JSON-Block."""

    /**
     * Baut das OpenAI-kompatible JSON-Array aller registrierten Tools.
     */
    fun buildToolsJsonArray(tools: List<AppTool> = AppTool.DEFAULT_TOOLS): String {
        val jsonArray = JSONArray()
        tools.forEach { tool ->
            jsonArray.put(tool.toOpenAiToolSchema())
        }
        return jsonArray.toString(2)
    }

    /**
     * Erzeugt den vollständigen, mit Tools angereicherten System-Prompt zur Laufzeit.
     */
    fun buildInjectedSystemPrompt(
        basePrompt: String = BASE_ASSISTANT_INSTRUCTIONS,
        tools: List<AppTool> = AppTool.DEFAULT_TOOLS
    ): String {
        val toolsJson = buildToolsJsonArray(tools)

        return buildString {
            appendLine(basePrompt.trim())
            appendLine()
            appendLine("### VERFÜGBARE NATIVE APP-TOOLS:")
            appendLine("Folgende Funktionen stehen dir zur direkten Interaktion mit der App zur Verfügung:")
            appendLine("```json")
            appendLine(toolsJson)
            appendLine("```")
            appendLine()
            append(TOOL_USAGE_DIRECTIVE.trim())
        }
    }
}
