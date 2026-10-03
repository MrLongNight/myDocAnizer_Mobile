# myDocAnizer-Mobile – Technische Dokumentation der On-Device LLM Integration

**Projekt:** myDocAnizer-Mobile  
**Autor:** Senior Android Software Engineering Team  
**Stand:** Oktober 2026  
**Status:** Produktiv / Vollständig implementiert & verifiziert  

---

## 1. Übersicht & Leitphilosophie

myDocAnizer-Mobile implementiert ein **100% lokales, datenschutzkonformes On-Device KI-Subsystem**.  
Im Gegensatz zu cloud-basierten Dokumenten-Management-Systemen verlassen sensible Dokumente, Verträge, Gehaltsabrechnungen, Steuerbelege und Rechnungen **niemals** das Smartphone des Nutzers.

### Kernprinzipien:
* **Zero-Cloud KI:** Alle Sprachmodelle laufen als quantisierte GGUF-Dateien direkt über die lokale CPU/GPU/NPU (via Vulkan, ARM NEON und Android NDK).
* **Deterministisch vor Stochastisch:** Ein deterministisches Schlagwort-Regelwerk (`DocRuleRepository`) hat immer Vorrang vor stochastischen LLM-Aufrufen, um maximale Geschwindigkeit (< 5 ms) und Null-Fehlerraten bei bekannten Belegen zu garantieren.
* **4-Stufen Pipeline (Multi-Agenten Fachabteilungs-Architektur):** Dokumente durchlaufen eine getaktete Kette aus Triage, Fachabteilungs-Tiefenanalyse, Ordner-Routing und abschließendem deterministischem Revisions-Check (Quality Gatekeeper).
* **Hardware-Adaptive Skalierung:** Live-Hardware-Telemetrie (RAM, CPU-Kerne, Performance-Tier) empfiehlt automatisch das ideale Modell für das jeweilige Endgerät.

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
|  - Exakter Regex- & Keyword-Match gegen Absender, IBAN, Belegarten                 |
|  - Latenz: < 5 ms | 0% Halluzination                                              |
+-----------------------------------------------------------------------------------+
                                         | (Falls keine Regel zutrifft)
                                         v
+-----------------------------------------------------------------------------------+
|  STUFE 1: Team 1 – Posteingangs-Triage (LlamaCppInferenceEngine)                   |
|  - Schnelle Dokumenten-Klassifikation (Rechnung, Vertrag, Bescheid, etc.)          |
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
|  - Text-Anchor-Cross-Check gegen OCR-Rohtext (Fängt Halluzinationen ab)           |
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
  * Verwaltet 12 kuratierte und verifizierte GGUF-Modelle.
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
  * `GENERAL` (Allgemeine Dokumente)
* **System-Prompts mit Schema-Enforcement:**
  * Dedizierte deutsche Prompts für jedes Fachabteilungs-Team (`buildDepartmentSystemPrompt`).
  * Erzwingt valides JSON-Output ohne Markdown-Umschweife (`DmsExtractionResponse`).
* **Robuster JSON-Parser (`parseDmsJson`):**
  * Bereinigt Markdown-Codeblöcke (` ```json ... ``` `).
  * Extrahiert Absender, Belegdatum, Betrag, Währung, IBAN, Fälligkeitsdatum, Kündigungsfrist und globale Zusatzfelder.
  * Wendet die gewählte `FolderStrategy` für den vorgeschlagenen Ablagepfad an.

---

### 3.3 `com.example.service.QualityGatekeeper.kt` (Team 4 – Revisionsprüfung)
Rein deterministischer Gatekeeper in Kotlin (ohne zusätzlichen LLM-Aufruf), der Halluzinationen und Extraktionsfehler zuverlässig abfängt.

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
  * Berechnet einen Qualitäts-Score von 0 bis 100 %.
  * Generiert klare Prüfhinweise für den Nutzer (`QualityWarning`), falls Felder manuell gegengeprüft werden sollten.
* **Audit-Logging:**
  * Protokolliert jede Revisionsentscheidung im `AppAuditLogger`.

---

### 3.4 `com.example.service.DocRuleRepository.kt` (Stufe 0)
Deterministisches Regelwerk für wiederkehrende Dokumente.

* Speichert benutzerdefinierte und automatisch erlernte Schlagwort-Regeln (`DocRule`).
* Sucht bei jedem Dokumenteneingang nach Übereinstimmungen von Absender, Schlagwörtern und Mustern.
* Erlaubt dem Nutzer die dauerhafte Übernahme von LLM-Vorschlägen als permanente Regel mit 1 Klick.

---

## 4. Der HuggingFace Modell-Katalog (12 On-Device GGUF-Modelle)

myDocAnizer-Mobile liefert einen Katalog aus 12 geprüften, quantisierten GGUF-Modellen (Q4_K_M):

| Modell-ID | Name | Größe | RAM-Bedarf | Ziel-Kategorie | Stärken |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `smollm2-135m-instruct` | **SmolLM2 135M** | 105 MB | 1.5–2 GB | DMS & Rechnungen | Ultraschnell (< 150 ms), minimaler Akkuverbrauch, ideal für Entry-Geräte |
| `smollm2-360m-instruct` | **SmolLM2 360M** | 270 MB | 2–3 GB | DMS & Rechnungen | Hohe Balance aus Geschwindigkeit (< 250 ms) und Beleg-Präzision |
| `distil-qwen-0.8b-invoice`| **Distil-Qwen 0.8B** | 580 MB | 2.5–4 GB | DMS & Rechnungen | Feingetunt für Belegtriage & Sortierung (< 180 ms) |
| `qwen2.5-0.5b-instruct` | **Qwen 2.5 0.5B** | 491 MB | 3–4 GB | DMS & Rechnungen | Exzellentes deutsches Sprachverständnis bei schrägem/unvollständigem OCR |
| `llama-3.2-1b-instruct` | **Llama 3.2 1B** | 807 MB | 4–6 GB | Reasoning & Logik | Metas On-Device Modell für Tagging & strukturierte Klassifizierung |
| `qwen2.5-1.5b-instruct` | **Qwen 2.5 1.5B** | 1117 MB | 4–6 GB | Verträge & Jura | Hohe semantische Zuverlässigkeit für Kündigungsfristen & Policen |
| `deepseek-r1-distill-1.5b`| **DeepSeek-R1 Distill** | 1117 MB | 4–6 GB | Reasoning & Logik | Kettendenken (Chain-of-Thought) für knifflige Steuer- & Gehaltsbelege |
| `qwen2.5-coder-1.5b` | **Qwen 2.5 Coder** | 1117 MB | 4–6 GB | Finanzen & Tabellen | Spezialisiert auf CSV, Betragstabellen und buchhalterische Saldenprüfung |
| `nuextract-2-2b-gguf` | **NuExtract 2.0 2B** | 1450 MB | 4–6 GB | DMS & Rechnungen | Hochspezialisiertes JSON-Schema-Extraktionsmodell ohne Halluzinationen |
| `ministral-3b-instruct` | **Ministral 3B** | 1950 MB | 6–8 GB | Mehrsprachig | Nativ mehrsprachig (DE, EN, FR, ES, IT) für Hotelbelege & internationale Rechnungen |
| `phi-3.5-mini-instruct` | **Phi-3.5 Mini** | 2393 MB | 6–8 GB | Verträge & Jura | Höchste logische Schärfe bei mehrseitigen AGBs und juristischen Klauseln |
| `lift-4b-structured-gguf` | **LIFT 4B PDF Extractor**| 2393 MB | 6–8 GB | Tabellen & PDF | Maximale Detailtreue für komplexe PDF-Berichte auf High-End-Smartphones |

---

## 5. Konfiguration & Inferenz-Parameter

Im **Experten-Modus** (`isExpertMode`) können Inferenz-Parameter über `LlmInferenceConfig` angepasst werden:

* `threadCount`: Anzahl der CPU-Inferenz-Threads (Standard: Automatisch nach CPU-Kernen, meist 4–6 Threads).
* `temperature`: Zufälligkeit der Generierung (Standard: `0.1` für deterministische, reproduzierbare Metadaten-Extraktion).
* `topP`: Top-P Nukleus-Sampling (Standard: `0.9`).
* `maxTokens`: Maximale Antwortlänge (Standard: `512` Tokens).
* `contextLength`: Kontext-Fenstergröße (Standard: `2048` Tokens).

---

## 6. UI-Integration & Revisions-Workflow

### 6.1 `PendingRuleSuggestionDialog.kt`
Wird nach einem Scan oder Import geöffnet, um dem Nutzer das KI-Ergebnis vorzuschlagen:
* **Vertrauens-Badge:** Zeigt den Confidence-Score aus Team 4 (z. B. *"98% Verifiziert"* oder *"75% Bitte prüfen"*).
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

## 8. Qualitätssicherung & Test-Matrix

Die gesamte LLM-Pipeline wird durch automatisierte Robolectric- und JVM-Tests abgesichert (`ExampleRobolectricTest.kt`):
* `verify AP-1 Two-Stage Pipeline and Fallback`: Testet Triage und Fallback-Ketten.
* `verify AP-2 Department Teams FolderStrategy and rule repository integration`: Validiert Fachabteilungs-Routing und dynamische Ordner-Strategien.
* `verify AP-3 Quality Gatekeeper text anchors, normalization and confidence audit`: Validiert Datums-/Betrags-Normalisierungen, Text-Anchor-Cross-Checks und Audit-Logs.

---
*myDocAnizer-Mobile – 100% On-Device KI für kompromisslose Privatsphäre.*
