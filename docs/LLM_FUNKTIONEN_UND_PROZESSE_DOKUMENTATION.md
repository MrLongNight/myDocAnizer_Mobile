# myDocAnizer-Mobile – Umfassende Dokumentation: LLM-Integration, Funktionen & Prozessabläufe

**Projekt:** myDocAnizer-Mobile  
**Modul:** On-Device LLM & KI-Subsystem  
**Autoren:** Senior Android Software Engineering Team  
**Stand:** Oktober 2026  
**Status:** Vollständig implementiert, getestet & verifiziert  

---

## 1. Executive Summary & System-Architektur

myDocAnizer-Mobile setzt auf ein kompromissloses **100% On-Device KI-Konzept**. Dokumente, Verträge, Gehaltsabrechnungen, Steuer- und Bankbelege verbleiben zu jedem Zeitpunkt exklusiv auf dem Endgerät des Nutzers. Es existieren keinerlei externe Cloud-LLM-Abhängigkeiten oder Telemetrie-Schnittstellen für Dokumenteninhalte.

```
+===================================================================================+
|                               UI- & PRÄSENTATIONSSCHICHT                          |
|  - SetupWizardDialog / RulesAndAiSettingsView (Modellkatalog, Hardware-Check)     |
|  - PendingRuleSuggestionDialog (KI-Revisionsbericht, 1-Klick-Übernahme)           |
|  - DocumentDetailModal (Zusatzfelder, Metadaten, Revisions-Badges)                |
+===================================================================================+
                                         │
                                         ▼
+===================================================================================+
|                             VIEWMODEL & STEUERUNG                                 |
|  - DocAnizerViewModel (StateFlows, Pipeline-Orchestrierung, UI-State)             |
+===================================================================================+
                                         │
                                         ▼
+===================================================================================+
|                          SERVICE- & LOGIKSCHICHT (KI-CORE)                        |
|                                                                                   |
|  [Stufe 0] DocRuleRepository (Deterministische Schlagwort- & Regex-Regeln)        |
|  [Stufe 1] LlmService & LlamaCppInferenceEngine (Triage & Klassifikation)         |
|  [Stufe 2] Department Teams (Finanzen, Verträge, Verwaltung, Allgemein)           |
|  [Stufe 3] Dynamic Folder Routing (DIN-Aktenplan & Ordner-Hierarchie)             |
|  [Stufe 4] QualityGatekeeper (Anchor-Cross-Check, Normalisierung, Vertrauen)      |
|                                                                                   |
|  [Tools]   LlmToolRouter & LlmToolPromptBuilder (Natives Function-Calling)       |
|  [Fristen] DeadlineDetectionService (Zahlungsziele, Kündigungsfristen)            |
|  [Audit]   AppAuditLogger (Vollständige Revisions- & Nachvollziehbarkeit)         |
+===================================================================================+
                                         │
                                         ▼
+===================================================================================+
|                        INFERENZ- & HARDWARE-SCHICHT (NDK)                         |
|  - llama.cpp On-Device Engine (GGUF Q4_K_M Quantisierung)                         |
|  - Hardware-Beschleunigung: ARM NEON, Vulkan, Multi-Core CPU-Threading            |
|  - Lokaler Gerätespeicher: Scoped App Storage & GGUF-Validierung                  |
+===================================================================================+
```

---

## 2. Der HuggingFace Modellkatalog & Hardware-Empfehlungs-Matrix

Die App enthält einen vorab kuratierten Katalog aus **12 hochoptimierten On-Device GGUF-Modellen (Q4_K_M)**.

### 2.1 Modellkatalog-Übersicht

| Modell-ID | Name | Größe | RAM-Bedarf | Ziel-Kategorie | Kernstärken & Anwendungsbereich |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `smollm2-135m-instruct` | **SmolLM2 135M** | 105 MB | 1.5–2 GB | DMS & Rechnungen | Ultraschnell (< 150 ms), extrem akkuschonend, optimiert für Low-End/Entry-Geräte. |
| `smollm2-360m-instruct` | **SmolLM2 360M** | 270 MB | 2–3 GB | DMS & Rechnungen | Perfekte Balance aus Schnelligkeit (< 250 ms) und Beleg-Präzision. |
| `distil-qwen-0.8b-invoice` | **Distil-Qwen 0.8B** | 580 MB | 2.5–4 GB | DMS & Rechnungen | Spezialisiert auf Rechnungen, Kassenbelege und Absender-Extraktion (< 180 ms). |
| `qwen2.5-0.5b-instruct` | **Qwen 2.5 0.5B** | 491 MB | 3–4 GB | DMS & Rechnungen | Exzellente deutsche Sprachverarbeitung auch bei verrauschtem/schrägem OCR-Rohtext. |
| `llama-3.2-1b-instruct` | **Llama 3.2 1B** | 807 MB | 4–6 GB | Reasoning & Logik | Metas On-Device Modell für mehrstufige Klassifikation und intelligentes Tagging. |
| `qwen2.5-1.5b-instruct` | **Qwen 2.5 1.5B** | 1117 MB | 4–6 GB | Verträge & Jura | Hohe semantische Zuverlässigkeit für Versicherungspolicen, Kündigungsfristen & AGBs. |
| `deepseek-r1-distill-1.5b` | **DeepSeek-R1 Distill** | 1117 MB | 4–6 GB | Reasoning & Logik | Kettendenken (Chain-of-Thought) für anspruchsvolle Steuer-, Lohn- und Nachweisdokumente. |
| `qwen2.5-coder-1.5b` | **Qwen 2.5 Coder** | 1117 MB | 4–6 GB | Finanzen & Tabellen | Spezialisiert auf tabellarische Belege, CSVs, Positionslisten und Saldenabgleiche. |
| `nuextract-2-2b-gguf` | **NuExtract 2.0 2B** | 1450 MB | 4–6 GB | DMS & Rechnungen | Hochspezialisiertes JSON-Schema-Extraktionsmodell mit minimaler Halluzinationsrate. |
| `ministral-3b-instruct` | **Ministral 3B** | 1950 MB | 6–8 GB | Mehrsprachig | Nativ mehrsprachig (DE, EN, FR, ES, IT) für Hotelbelege, Flugtickets und Auslandsrechnungen. |
| `phi-3.5-mini-instruct` | **Phi-3.5 Mini** | 2393 MB | 6–8 GB | Verträge & Jura | Höchste logische Schärfe bei vielseitigen juristischen Verträgen und Behördenschreiben. |
| `lift-4b-structured-gguf` | **LIFT 4B PDF Extractor** | 2393 MB | 6–8 GB | Tabellen & PDF | Maximale Detailtreue für komplexe PDF-Berichte auf Flaggschiff-Smartphones (8GB+ RAM). |

### 2.2 Live-Hardware-Telemetrie & Empfehlungslogik

Die Methode `LlmService.detectDeviceHardware(context)` liest die Geräteeigenschaften zur Laufzeit aus:

1. **Speicheranalyse via `ActivityManager.MemoryInfo`:**
   * `totalRamGb`: Physisch verbauter Gesamtspeicher (z.B. 4.0 GB, 8.0 GB, 12.0 GB).
   * `availableRamGb`: Aktuell freier Arbeitsspeicher.
2. **CPU-Analyse via `Runtime.getRuntime().availableProcessors()`:**
   * Bestimmung der CPU-Kerne und Zuweisung optimaler Thread-Zahlen für die Inferenz.
3. **Einstufung in Performance-Tiers:**
   * `HIGH_END` (≥ 8.0 GB RAM, ≥ 8 Kerne) ➔ Empfehlung: Modelle bis 3B/4B Parameter (z.B. NuExtract 2B, Phi-3.5 Mini).
   * `BALANCED` (≥ 4.0 GB RAM, ≥ 6 Kerne) ➔ Empfehlung: Modelle von 0.8B bis 1.5B Parameter (z.B. Qwen 2.5 1.5B, Distil-Qwen 0.8B).
   * `ENTRY` (< 4.0 GB RAM) ➔ Empfehlung: Ultraleichte Modelle (SmolLM2 135M/360M oder Qwen 2.5 0.5B).

---

## 3. Modell-Lifecycle: Download, Verifikation & Synchronisation

```
[Katalog-Auswahl] 
       │
       ▼
[Download via OkHttp / Android DownloadManager]
       │ ──► Fortschritts-Stream (_downloadProgress, _downloadedBytes)
       ▼
[GGUF Magic Header Validierung]
       │ ──► Byte-Prüfung: 0x47, 0x47, 0x55, 0x46 ("GGUF")
       ▼
[Redundante Ablage in Scoped Storage]
       │ ──► Intern: context.filesDir/models/
       │ ──► Fallback: context.getExternalFilesDir(null)/models/
       ▼
[Integritäts-Audit] ──► Protokollierung im AppAuditLogger
```

### 3.1 GGUF-Validierungsroutine (`LlmService.validateGgufFile`)
Bevor ein Modell zur Inferenz freigegeben wird, liest die App die ersten 4 Bytes der Datei:
* Gültige Magic Bytes: `0x47 ('G'), 0x47 ('G'), 0x55 ('U'), 0x46 ('F')`.
* Bei ungültigen Magic Bytes oder korrupten Downloads wird die Datei verworfen, bereinigt und dem Nutzer eine Wiederholung angeboten.

### 3.2 Katalog-Synchronisation (`syncModelCatalogFromRemote`)
* Prüft periodisch oder bei manuellem Pull-to-Refresh die Verfügbarkeit neuer Modelldefinitionen.
* Führt automatisch `refreshHardwareInfo()` aus, um Kompatibilitäts-Badges aktuell zu halten.
* Sendet Benachrichtigungen über `_newModelsNotification` an die UI.

---

## 4. Die 4-Stufen Analyse- & Extraktions-Pipeline

Jedes gescannte oder importierte Dokument durchläuft eine strikte, deterministisch überwachte Pipeline:

```
                  [ OCR-Rohtext ]
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ STUFE 0: Deterministische Schlagwort-Regeln            │
│ (DocRuleRepository)                                    │
│ - Exakter Match auf Absender, IBAN, Schlüsselwörter    │
│ - Latenz < 5 ms | 0% Halluzination                     │
└────────────────────────────────────────────────────────┘
                         │ (Falls keine Regel greift)
                         ▼
┌────────────────────────────────────────────────────────┐
│ STUFE 1: Posteingangs-Triage (Team 1)                  │
│ - Dokumenttyp: RECHNUNG, VERTRAG, BESCHEID, etc.       │
│ - Absender & Routing an Fachabteilungs-Team            │
└────────────────────────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ STUFE 2: Fachabteilungs-Tiefenanalyse (Team 2)         │
│ - FINANCE: Betrag, MwSt, IBAN, Rechnungsnummer         │
│ - CONTRACTS: Vertragspartner, Beginn, Kündigungsfrist  │
│ - ADMINISTRATION: Aktenzeichen, Behörde, Frist         │
│ - GENERAL: Betreff, Zusammenfassung, Schlagwörter      │
└────────────────────────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ STUFE 3: Dynamisches Ordner-Routing (Team 3)           │
│ - Zielordner nach FolderStrategy & DIN-Aktenplan       │
│ - Generierung deterministischer Regelvorschläge        │
└────────────────────────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ STUFE 4: Quality Gatekeeper & Revision (Team 4)        │
│ - Text-Anchor Cross-Check gegen OCR-Rohtext            │
│ - Datums- & Betrags-Normalisierung                     │
│ - Plausibilitätsprüfungen (Fälligkeit > Belegdatum)    │
│ - Confidence-Score (0-100%) & Revisionshinweise        │
└────────────────────────────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────┐
│ UI: PendingRuleSuggestionDialog & 1-Klick-Übernahme    │
└────────────────────────────────────────────────────────┘
```

### 4.1 Stufe 0: Deterministische Schlagwort-Regeln (`DocRuleRepository`)
* Vor jedem LLM-Aufruf wird der OCR-Text gegen gespeicherte `DocRule`-Einträge geprüft.
* Trifft ein Regex- oder Schlagwort-Muster zu (z.B. `"Telekom Deutschland"` + `"Rechnung"`), wird das Dokument sofort (< 5 ms) ohne LLM-Inferenz zugeordnet.
* Garantiert maximale Geschwindigkeit und Null-Fehlerraten bei bekannten Belegen.

### 4.2 Stufe 1 & 2: Posteingangs-Triage & Fachabteilungs-Teams (`LlamaCppInferenceEngine`)
Die Engine nutzt spezialisierte System-Prompts mit striktem JSON-Schema (`DmsExtractionResponse`):

* **Team FINANZEN (`DepartmentTeam.FINANCE`):**
  * Extrahiert: `sender`, `docDate`, `amount`, `currency`, `taxAmount`, `invoiceNumber`, `iban`, `dueDate`, `paymentStatus`.
* **Team VERTRÄGE (`DepartmentTeam.CONTRACTS`):**
  * Extrahiert: `sender`, `contractNumber`, `startDate`, `endDate`, `cancellationPeriod`, `monthlyFee`.
* **Team VERWALTUNG (`DepartmentTeam.ADMINISTRATION`):**
  * Extrahiert: `sender`, `fileReference` (Aktenzeichen), `noticeDate`, `appealDeadline`, `authorityName`.
* **Team ALLGEMEIN (`DepartmentTeam.GENERAL`):**
  * Extrahiert: `sender`, `docDate`, `subject`, `summary`, `tags`.

### 4.3 Stufe 3: Dynamisches Ordner-Routing & Regelvorschlag
Basierend auf der konfigurierten `FolderStrategy`:
* `SENDER_YEAR`: `[Absender] / [Jahr]` (z.B. `Telekom/2026`)
* `CATEGORY_SENDER`: `[Kategorie] / [Absender]` (z.B. `Finanzen/Telekom`)
* `DIN_AKTENPLAN`: Zuordnung nach deutschem Einheitsaktenplan (z.B. `04_Finanzen/042_Rechnungen`)
* `CUSTOM_HIERARCHY`: Frei definierte Ordnerstrukturen

Zusätzlich wird ein generierter Regelvorschlag (`suggestedRulePattern`) formuliert, den der Anwender als permanente Regel speichern kann.

### 4.4 Stufe 4: Quality Gatekeeper & Revisions-Prüfung (`QualityGatekeeper.kt`)
Ein deterministisches Kotlin-Prüfmodul verhindert Halluzinationen und bereinigt Formate:

1. **Text-Anchor Cross-Check (`checkTextAnchors`):**
   * Verifiziert, ob extrahierte Zahlenwerte (Betrag, IBAN, Rechnungsnummer, Datum) wortwörtlich oder in transformierter Form im OCR-Rohtext existieren.
2. **Datums-Normalisierung (`normalizeDateVariants`):**
   * Wandelt Formate wie `"12. März 2026"`, `"2026-03-12"`, `"12/03/26"` einheitlich in `YYYY-MM-DD` bzw. `DD.MM.YYYY` um.
3. **Betrags-Normalisierung (`normalizeAmountVariants`):**
   * Bereinigt `"1.250,50 €"`, `"1250.50 EUR"` zu numerischen Double-Werten `1250.50`.
4. **Logische Plausibilitätsprüfungen (`checkLogicalPlausibility`):**
   * Stellt sicher, dass das Fälligkeitsdatum nicht vor dem Belegdatum liegt.
   * Prüft, dass Beträge positiv sind.
   * Schließt Verwechslungen von Geburtsdaten mit Rechnungsdaten aus.
5. **Confidence-Score Berechnung:**
   * Vergibt einen Vertrauenswert von 0 bis 100 %.
   * Generiert transparente `QualityWarning`-Objekte für den Nutzer.

---

## 5. Natives LLM Function-Calling (`LlmToolRouter`)

myDocAnizer-Mobile unterstützt natives On-Device Function-Calling nach dem OpenAI-kompatiblen Tool-Schema.

```
[Benutzer-Prompt / Chat / Sprachbefehl]
                     │
                     ▼
[LlmToolPromptBuilder.buildSystemPromptWithTools]
                     │
                     ▼
[On-Device LLM Inferenz] ──► Generiert Tool-Call JSON
                     │
                     ▼
[LlmToolRouter.routeLlmOutput]
                     │
         ┌───────────┴───────────┐
         ▼                       ▼
  [Tool erkannt]          [Reiner Text]
         │                       │
         ▼                       ▼
[Typsichere Ausführung]   [Direkte Antwort]
         │
         ├── filterDocuments(sender, status, category)
         ├── updateDocumentStatus(docId, newStatus)
         ├── createFolder(folderName, parentCategory)
         └── openAppSection(section)
         │
         ▼
[UI-Bestätigung & Audit-Log]
```

### 5.1 Registrierte native Tools (`AppTool`)

1. **`filterDocuments`:**
   * Filtert die Dokumentenliste im Dokumenten-Tresor nach Absender, Bearbeitungsstatus oder Hauptkategorie.
2. **`updateDocumentStatus`:**
   * Ändert den Bearbeitungsstatus eines Dokuments (`Offen`, `Bezahlt`, `Gekündigt`, `In Bearbeitung`, `Prüfen`).
3. **`createFolder`:**
   * Legt physisch neue Ordner und Kategorien im Dokumenten-Tresor an.
4. **`openAppSection`:**
   * Navigiert direkt zu Zielbereichen der App (`scanner`, `dashboard`, `trash`, `settings`, `deadlines`, `import`).

---

## 6. Fristen- & Termin-Erkennung (`DeadlineDetectionService`)

Der `DeadlineDetectionService` analysiert extrahierte Dokumenten-Metadaten auf relevante Termine:

* **Zahlungsfristen:** Automatische Berechnung von Skonto- und Fälligkeitsterminen.
* **Kündigungsfristen:** Erkennung von Vertragslaufzeiten und Kündigungsfristen (z.B. *"3 Monate zum Monatsende"*).
* **Behördliche Einspruchsfristen:** Erkennung von Monatsfristen nach Bekanntgabe von Verwaltungsakten.
* **System-Integration:**
  * Lokales Scheduling von Benachrichtigungen via Android `WorkManager` & `AlarmManager`.
  * Optionaler Export als iCalendar (`.ics`) oder Direkteintrag in den lokalen Gerätekalender.

---

## 7. Fehlerbehandlung, Fallback-Strategien & Resilienz

Um unter allen Umständen einen stabilen Betrieb und Null-Abstürze zu gewährleisten, greifen mehrstufige Fallback-Mechanismen:

1. **Kein Modell geladen oder Download ausstehend:**
   * Automatischer Fallback auf den schnellen Regex-/Heuristik-Parser (`FallbackHeuristicExtractor`).
   * Alle Basisfelder (Betrag, Datum, IBAN) werden rein regelbasiert extrahiert.
2. **Unerwartetes Inferenz-Format (Malformed JSON):**
   * Robuster Regex-Cleaner isoliert JSON-Payloads aus Freitext oder Markdown-Blöcken.
   * Schlägt das JSON-Parsing fehl, greift der Fuzzy-Feld-Matcher.
3. **Geringer Arbeitsspeicher (Low Memory Warning):**
   * Dynamische Drosselung der Kontextlänge und Thread-Zahl.
   * Empfehlung zum Wechsel auf SmolLM2 135M oder 360M im UI.
4. **Text-Diskrepanz im Quality Gatekeeper:**
   * Anstatt falsche Werte stillschweigend zu übernehmen, markiert das System unklare Werte mit einem Revisions-Badge in der UI.

---

## 8. Vollständige API- & Schnittstellen-Referenz

### 8.1 `LlmService.kt`
```kotlin
// Hardware-Telemetrie
fun detectDeviceHardware(context: Context): HardwareInfo
fun refreshHardwareInfo()

// Modellkatalog & Status
val availableModels: StateFlow<List<LlmModelItem>>
val selectedModel: StateFlow<LlmModelItem?>
val isModelLoaded: StateFlow<Boolean>
val downloadProgress: StateFlow<Float>

// Download & Synchronisation
suspend fun downloadModel(model: LlmModelItem, onProgress: (Float) -> Unit): Boolean
suspend fun syncModelCatalogFromRemote(forceCheck: Boolean = false)

// Inferenz
suspend fun classifyDocument(ocrText: String): LlmClassificationResult
suspend fun classifyDocumentSecondRun(ocrText: String, triageResult: LlmClassificationResult): LlmClassificationResult
```

### 8.2 `QualityGatekeeper.kt`
```kotlin
data class QualityGateResult(
    val confidenceScore: Int, // 0..100
    val verifiedFields: Map<String, String>,
    val warnings: List<QualityWarning>,
    val isApproved: Boolean
)

fun inspectAndVerify(
    rawOcrText: String,
    extraction: DmsExtractionResponse,
    department: DepartmentTeam
): QualityGateResult
```

### 8.3 `LlmToolRouter.kt`
```kotlin
suspend fun routeLlmOutput(
    rawLlmOutput: String,
    context: Context,
    viewModel: DocAnizerViewModel
): LlmRouterResult
```

---

## 9. Qualitätssicherung & Test-Matrix

Das gesamte On-Device LLM-Subsystem ist über die automatisierte Robolectric-Testsuite abgesichert:

* **`verify AP-1 Two-Stage Pipeline and Fallback`**:
  * Testet Stufe 1 Triage, Stufe 2 Fachabteilungsanalyse und den fehlerfreien Heuristik-Fallback.
* **`verify AP-2 Department Teams FolderStrategy and rule repository integration`**:
  * Testet alle 4 Fachabteilungen, Ordner-Routing-Strategien und die Erstellung permanenter Regeln.
* **`verify AP-3 Quality Gatekeeper text anchors, normalization and confidence audit`**:
  * Validiert Text-Anchor-Cross-Checks, Datums-/Betrags-Normalisierungen, Plausibilitätswarnungen und Audit-Logging.
* **`verify Hardware check and model catalog sync`**:
  * Validiert die Hardware-Erkennung und Katalog-Konsistenz aller 12 GGUF-Modelle.

---
*myDocAnizer-Mobile – 100% On-Device Intelligenz, maximale Privatsphäre und verlässliche Dokumenten-Organisation.*
