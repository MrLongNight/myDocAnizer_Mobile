# myDocAnizer-Mobile – Technische Dokumentation der On-Device LLM Integration

**Projekt:** myDocAnizer-Mobile  
**Autor:** Senior Android Software Engineering Team  
**Stand:** Oktober 2026  
**Status:** Teilweise implementiert; CI- und Geräte-Nachweise sind getrennt auszuweisen  

> **Aktueller Stand:** Komponenten im Repository sind nicht automatisch ein Nachweis für Ende-zu-Ende-Modellbetrieb. Der CI-Lauf baut/testet nur die in diesem Lauf definierten Schritte; echte GGUF-Inferenz auf Hardware steht noch aus. Native llama.cpp-Konfiguration ist CPU-only (Vulkan deaktiviert), mit Builds für `arm64-v8a` und `x86_64`; andere ABIs gehen auf Stub/Fallback. TPS- und Modellqualitätswerte sind nur dann Messwerte, wenn sie aus einem tatsächlichen nativen Modelllauf stammen. Regeln-/Heuristik-Fallbacks sind keine LLM-Ausgabe. Benchmark-Tokens, Prompt-/Generierungszeiten und Tokens/s werden nur aus den nativen llama.cpp-Zählern übernommen; fehlen valide Zähler, wird das Ergebnis ausdrücklich als nicht gemessen markiert. Tokens werden nicht aus der Zeichenlänge geschätzt. Offene Punkte siehe [Live-Plan](LLM_NATIVE_PRODUCTION_PLAN.md).

---

## 1. Übersicht & Leitphilosophie

myDocAnizer-Mobile verfolgt einen **lokalen, datenschutzorientierten On-Device-KI-Ansatz**. Die native Modellinferenz ist ABI-abhängig; einzelne Funktionen verwenden weiterhin deterministische Fallbacks.  
Im Gegensatz zu cloud-basierten Dokumenten-Management-Systemen verlassen sensible Dokumente, Verträge, Gehaltsabrechnungen, Steuerbelege und Rechnungen **niemals** das Smartphone des Nutzers.

### Kernprinzipien:
* **Lokaler Ausführungspfad:** Native llama.cpp/GGUF-Inferenz ist derzeit CPU-only; Vulkan- und GPU-Inferenz werden nicht aktiviert oder behauptet. Native Build-Ziele sind `arm64-v8a` und `x86_64`.
* **Deterministischer Backup-Pfad:** `DocRuleRepository` kann bekannte Dokumente ohne LLM einordnen; Laufzeiten und Fehlerraten sind nicht pauschal gemessen. Mehrdeutige Regel-Treffer müssen sicher abgewiesen oder zur Auswahl vorgelegt werden.
* **Pipeline-Komponenten:** Triage, Fachabteilungslogik, Ordner-Routing und Quality-Gatekeeper sind vorhanden; nicht jeder Feature-Aufrufer ist als echter, end-to-end modellgetriebener Ablauf nachgewiesen. Regelgenerierung und Tool-Calling enthalten noch heuristische Pfade.
* **Hardware-Empfehlungen:** RAM-/CPU-Erkennung und Kompatibilitätshinweise unterstützen die Auswahl, ersetzen aber keinen Benchmark. Unbekannte Hardware-Kapazität bleibt unbekannt; Empfehlungen sind keine Messwerte.

---

## 2. Architektur & Komponenten-Übersicht

```
+-----------------------------------------------------------------------------------+
|                              myDocAnizer-Mobile App                               |
+-----------------------------------------------------------------------------------+
                                         |
                       [ Scan / Import / Batch-Inbox ]
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|  STUFE 0: Deterministische Schlagwort-Regeln (DocRuleRepository)                   |
|  - Deterministischer Keyword-/Regex-Match nach den konfigurierten Regelbedingungen |
|  - Regelbasierte Ergebnisse, keine LLM-Inferenz; tatsächliche Laufzeit nicht pauschal benchmark-geprüft |
+-----------------------------------------------------------------------------------+
                                         | (Falls keine Regel zutrifft)
                                         v
+-----------------------------------------------------------------------------------+
|  STUFE 1: Team 1 – Posteingangs-Triage (LlamaCppInferenceEngine)                   |
|  - Strukturierte Dokumenten-Klassifikation (Rechnung, Vertrag, Bescheid, etc.)     |
|  - Ermittlung von Absender & Fachabteilungs-Routing                               |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|  STUFE 2: Team 2 – Fachabteilungs-Tiefenanalyse (Department Teams)                |
|  - FINANZEN: Beträge, Netto/MwSt, IBAN, Rechnungsnummer, Zahlungsziel             |
|  - VERTRÄGE: Vertragspartner, Beginn, Laufzeit, Kündigungsfrist                   |
|  - VERWALTUNG: Aktenzeichen, Behörde, Bescheiddatum, Einspruchsfrist              |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|  STUFE 3: Team 3 – Dynamisches Ordner-Routing & Regelvorschlag                     |
|  - Zuordnung nach DIN-Aktenplan & gewählter FolderStrategy (z.B. Absender/Jahr)   |
|  - Generierung deterministischer Regelnamen ("$Absender $Dokumententyp")          |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|  STUFE 4: Team 4 – Quality Gatekeeper (QualityGatekeeper.kt)                       |
|  - Prüft definierte OCR-Textanker; kann nicht alle Halluzinationen erkennen oder ausschließen           |
|  - Normalisierung von Datumsangaben ("12. März 2024" -> "12.03.2024")             |
|  - Normalisierung von Beträgen ("1.250,50 €" -> 1250.50)                          |
|  - Plausibilitätsprüfungen (Fälligkeitsdatum != Geburtsdatum, IBAN-Prüfung)       |
|  - Confidence-Score Berechnung & Logging im AppAuditLogger                        |
+-----------------------------------------------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|  UI & Benutzer-Freigabe (PendingRuleSuggestionDialog / DocumentDetailModal)       |
|  - Transparente Anzeige des Revisionsberichts (Vertrauens-Badge)                  |
|  - 1-Klick-Korrektur oder Bestätigung                                             |
+-----------------------------------------------------------------------------------+
```

---

## 3. Die Komponenten im Detail

### 3.1 `com.example.service.LlmService.kt`
Zentrale Schnittstelle für die Verwaltung der lokalen On-Device LLMs.

* **Hardware-Erkennung (`detectDeviceHardware`):**
  * Ermittelt Gesamtspeicher (`totalRamGb`), verfügbaren Speicher (`availableRamGb`), CPU-Kerne und stuft das Gerät in Performance-Tiers ein (`HIGH_END`, `BALANCED`, `ENTRY`).
* **Modellkatalog-Verwaltung (`availableModels`):**
  * Verwaltet konfigurierte GGUF-Modell-Einträge; Katalogeinträge allein bestätigen keine Quelle, Lizenz, Laufzeit oder Qualität.
  * Unterstützt Modellwechsel zur Laufzeit, Dateiprüfung und Speicherplatzauswertung.
* **Download-Manager (`downloadModel`):**
  * Asynchroner Download mit Fortschritts-Stream (`downloadProgress`, `downloadedBytes`).
  * **GGUF-Signatur-Validierung:** Überprüft die Magic Bytes `0x47, 0x47, 0x55, 0x46` vor Freigabe der Modelldatei.
  * Automatische redundante Sicherung im privaten und öffentlichen App-Verzeichnis.
* **Inferenz-Dispatching (`classifyDocument`, `classifyDocumentSecondRun`):**
  * Führt die zweistufige Pipeline aus und erzeugt strukturierte `LlmClassificationResult`-Objekte.

---

### 3.2 `com.example.service.LlamaCppInferenceEngine.kt`
Die Inferenz-Engine für die lokale Ausführung und JSON-Strukturierung.

* **Fachabteilungs-Definition (`enum class DepartmentTeam`):**
  * `FINANCE` (Finanzen, Rechnungen & Steuern)
  * `CONTRACTS` (Verträge, Kündigungen & Versicherungen)
  * `ADMINISTRATION` (Verwaltung, Behörden & Bescheide)
* **System-Prompts mit Schema-Enforcement:**
  * Dedizierte deutsche Prompts für jedes Fachabteilungs-Team (`buildDepartmentSystemPrompt`).
  * Erzwingt valides JSON-Output ohne Markdown-Umschweife (`DmsExtractionResponse`).
* **Robuster JSON-Parser (`parseDmsJson`):**
  * Bereinigt Markdown-Codeblöcke (` ```json ... ``` `).
  * Extrahiert Absender, Belegdatum, Betrag, Währung, IBAN, Fälligkeitsdatum, Kündigungsfrist und globale Zusatzfelder.
  * Wendet die gewählte `FolderStrategy` für den vorgeschlagenen Ablagepfad an.

---

### 3.3 `com.example.service.QualityGatekeeper.kt` (Team 4 – Revisionsprüfung)
Rein deterministischer Gatekeeper in Kotlin (ohne zusätzlichen LLM-Aufruf), der bekannte Plausibilitäts- und Textankerprüfungen ausführt. Er kann nicht jede Halluzination erkennen oder ausschließen.

* **Text-Anchor-Cross-Check (`checkTextAnchors`):**
  * Prüft, ob extrahierte Beträge, Datumsangaben, IBANs, Rechnungsnummern und Kundennummern tatsächlich im OCR-Rohtext vorkommen.
* **Normalisierungs-Logik:**
  * **Datum-Normalisierung (`normalizeDateVariants`):** Erkennt und vereinheitlicht Schreibweisen wie:
    * `"12. März 2024"`, `"12.03.2024"`, `"2024-03-12"`, `"12/03/24"`.
  * **Betrags-Normalisierung (`normalizeAmountVariants`):** Erkennt und vereinheitlicht:
    * `"1.250,50 €"`, `"1250.50"`, `"1,250.50"`, `"EUR 1250,50"`.
  * **IBAN-Bereinigung (`normalizeIban`):** Entfernt Leerzeichen und prüft auf gültige deutsche/europäische Formate.
* **Logik- & Plausibilitätsprüfungen (`checkLogicalPlausibility`):**
  * Verhindert Verwechslungen (z. B. wenn ein Fälligkeitsdatum neben dem Wort *"geb."* oder *"Geburtsdatum"* steht).
  * Prüft, ob Rechnungsbeträge positiv sind.
  * Validiert Fälligkeitsdaten darauf, dass sie zeitlich nach dem Belegdatum liegen.
* **Confidence-Score Berechnung:**
  * Berechnet einen heuristischen Prüfscore von 0 bis 100; dies ist keine kalibrierte Wahrscheinlichkeit und keine alleinige Bestätigung der Korrektheit.
  * Generiert klare Prüfhinweise für den Nutzer (`QualityWarning`), falls Felder manuell gegengeprüft werden sollten.
* **Audit-Logging:**
  * Protokolliert jede Revisionsentscheidung im `AppAuditLogger`.

---

### 3.4 `com.example.service.DocRuleRepository.kt` (Stufe 0)
Deterministisches Regelwerk für wiederkehrende Dokumente.

* Speichert benutzerdefinierte Regeln und kann vom Nutzer bestätigte Vorschläge als `DocRule` ablegen. Ein vollständig modellgetriebener Regelgenerator/-verfeinerer ist noch offen.
* Sucht bei jedem Dokumenteneingang nach Übereinstimmungen von Absender, Schlagwörtern und Mustern.
* Erlaubt dem Nutzer die dauerhafte Übernahme von LLM-Vorschlägen als permanente Regel mit 1 Klick.

---

## 4. Modellkatalog (12 konfigurierte GGUF-Einträge)

> Die unten aufgeführten Größen-/RAM-Angaben und Anwendungsbereiche sind Katalogangaben. Sie sind kein Nachweis über gemessene Geschwindigkeit, Genauigkeit oder Gerätekompatibilität.

Der App-Katalog enthält 12 konfigurierte GGUF-Modell-Einträge; Quelle, tatsächliche Quantisierung, Laufzeit und Extraktionsqualität müssen jeweils separat verifiziert werden.

| Modell-ID | Name | Größe | RAM-Bedarf | Ziel-Kategorie | Stärken |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `smollm2-135m-instruct` | **SmolLM2 135M** | 105 MB | 1.5–2 GB | DMS & Rechnungen | Für kleine Aufgaben vorgesehen; Laufzeit und Qualität nicht gemessen |
| `smollm2-360m-instruct` | **SmolLM2 360M** | 270 MB | 2–3 GB | DMS & Rechnungen | Kompaktes Modell; Laufzeit und Belegqualität nicht benchmark-geprüft |
| `distil-qwen-0.8b-invoice`| **Distil-Qwen 0.8B** | 580 MB | 2.5–4 GB | DMS & Rechnungen | Für Belegtriage vorgesehen; Laufzeit und Qualität nicht verifiziert |
| `qwen2.5-0.5b-instruct` | **Qwen 2.5 0.5B** | 491 MB | 3–4 GB | DMS & Rechnungen | Für deutsche Klassifikation vorgesehen; Robustheit nicht verifiziert |
| `llama-3.2-1b-instruct` | **Llama 3.2 1B** | 807 MB | 4–6 GB | Reasoning & Logik | Für Tagging/Klassifikation vorgesehen; Qualität nicht verifiziert |
| `qwen2.5-1.5b-instruct` | **Qwen 2.5 1.5B** | 1117 MB | 4–6 GB | Verträge & Jura | Für Vertragsdokumente vorgesehen; Fristenerkennung nicht gemessen |
| `deepseek-r1-distill-1.5b`| **DeepSeek-R1 Distill** | 1117 MB | 4–6 GB | Reasoning & Logik | Für strukturierte Auswertung vorgesehen; ausführliche Chain-of-Thought-Ausgabe wird nicht angefordert |
| `qwen2.5-coder-1.5b` | **Qwen 2.5 Coder** | 1117 MB | 4–6 GB | Finanzen & Tabellen | Für Tabellenaufgaben vorgesehen; Qualität nicht verifiziert |
| `nuextract-2-2b-gguf` | **NuExtract 2.0 2B** | 1450 MB | 4–6 GB | DMS & Rechnungen | Für JSON-Extraktion vorgesehen; Halluzinationsfreiheit ist nicht belegt |
| `ministral-3b-instruct` | **Ministral 3B** | 1950 MB | 6–8 GB | Mehrsprachig | Für mehrsprachige Dokumente vorgesehen; Qualität pro Sprache nicht gemessen |
| `phi-3.5-mini-instruct` | **Phi-3.5 Mini** | 2393 MB | 6–8 GB | Verträge & Jura | Für Vertrags-/Behördentexte vorgesehen; Qualität nicht verifiziert |
| `lift-4b-structured-gguf` | **LIFT 4B PDF Extractor**| 2393 MB | 6–8 GB | Tabellen & PDF | Für strukturierte Extraktion vorgesehen; Detailtreue und RAM-Bedarf nicht gemessen |

---

## 5. Konfiguration & Inferenz-Parameter

Im **Experten-Modus** (`isExpertMode`) können Inferenz-Parameter über `LlmInferenceConfig` angepasst werden:

* `threadCount`: Anzahl der CPU-Inferenz-Threads (Standard: 2; bei Bedarf im Experten-Modus erhöhen).
* `temperature`: Zufälligkeit der Generierung (Standard: `0.1` für deterministische, reproduzierbare Metadaten-Extraktion).
* `topP`: Top-P Nukleus-Sampling (Standard: `0.9`).
* `maxTokens`: Maximale Antwortlänge (Standard: `512` Tokens).
* `contextLength`: Kontext-Fenstergröße (Standard: `2048` Tokens).

---

## 6. UI-Integration & Revisions-Workflow

### 6.1 `PendingRuleSuggestionDialog.kt`
Wird nach einem Scan oder Import geöffnet, um dem Nutzer das KI-Ergebnis vorzuschlagen:
* **Vertrauens-Badge:** Zeigt den heuristischen Prüfscore und zugehörige Hinweise. Der Prozentwert ist keine kalibrierte Wahrscheinlichkeit und allein kein Verifikationsbeleg.
* **Revisions-Hinweise:** Farbige Hinweis-Karten heben Diskrepanzen hervor (z. B. *"Betrag 149,99 € weicht leicht von OCR ab"*).
* **1-Klick-Korrektur:** Schaltflächen zur direkten Übernahme korrigierter Vorschlagswerte.
* **Permanente Übernahme:** Klick auf *"Als Dauer-Regel speichern"* trägt die Regel deterministisch in `DocRuleRepository` ein.

### 6.2 `RulesAndAiSettingsView.kt`
Verwaltungsansicht für KI-Modelle und Regeln:
* **Live-Hardware-Diagnose-Karte:** Zeigt RAM-Auslastung, CPU-Kerne und die passende Empfehlung.
* **Kategorie-Filter:** `Alle (12)`, `⭐ Empfohlen`, `DMS & Rechnungen`, `Reasoning & Logik`, `Verträge & Jura`, `Finanzen & Tabellen`, `Mehrsprachig`.
* **Katalog-Sync:** Aktualisiert Metadaten und prüft lokale Modelldateien auf dem Gerätespeicher.

---

## 7. Entwickler-Leitfaden: Neue Fachabteilung hinzufügen

Um ein neues Fachabteilungs-Team zu integrieren:

1. **Enum erweitern** in `LlamaCppInferenceEngine.kt`:
   ```kotlin
   enum class DepartmentTeam(val displayName: String, val systemPromptName: String) {
       FINANCE("Finanzen & Steuern", "FINANCE_PROMPT"),
       CONTRACTS("Verträge & Jura", "CONTRACTS_PROMPT"),
       ADMINISTRATION("Verwaltung & Behörden", "ADMIN_PROMPT"),
       HEALTH("Gesundheit & Rezepte", "HEALTH_PROMPT"), // Neu
       GENERAL("Allgemein", "GENERAL_PROMPT")
   }
   ```
2. **System-Prompt definieren** in `buildDepartmentSystemPrompt`:
   Füge die schemaspezifischen Extraktionsfelder (z. B. Arztname, Rezeptgebühr, PZN) im Prompt-Template hinzu.
3. **Plausibilitätsregeln ergänzen** in `QualityGatekeeper.kt`:
   Erweitere `checkLogicalPlausibility` um fachspezifische Prüfregeln (z. B. PZN-Format oder Ausstellungsdatum).

---

## 8. Qualitätssicherung & Testgrenzen

GitHub Actions führt die in `.github/workflows/build-apk.yml` definierten Build- und Testschritte aus. Der jeweils konkrete Run ist maßgeblich; ein erfolgreicher APK-Build oder Unit-/Instrumentierungstest beweist für sich genommen nicht, dass auf einem physischen Gerät eine GGUF-Datei geladen und tatsächlich ausgeführt wurde. Tests mit Heuristik-/Regel-Fallback gelten nicht als Nachweis für echte Modellinferenz. Den aktuellen CI-Stand, bestätigte Commits und offene Abnahmekriterien führt die [Live-Aufgabenliste](LLM_NATIVE_PRODUCTION_PLAN.md).
---
*myDocAnizer-Mobile – 100% On-Device KI für kompromisslose Privatsphäre.*
