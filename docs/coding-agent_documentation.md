# myDocAnizer-Mobile: Coding-Agent & Technische Entwickler-Dokumentation

**Projekt:** myDocAnizer-Mobile (Android)  
**Paketname (Namespace):** `com.example`  
**Architektur:** MVVM + Clean Architecture / Jetpack Compose / Android Room (SQLite + FTS4) / Offline ML Kit OCR / On-Device GGUF Nano-LLMs / AES-256-GCM / P2P Local WiFi Sync  
**Plattform:** Google AI Studio Build / Android Studio  

---

## 1. Systemübersicht & Philosophie

**myDocAnizer-Mobile** ist ein 100% datenschutzkonformes, cloudfreies Dokumenten-Management-System (DMS) und intelligenter Dokumenten-Scanner für Android.

### Kernprinzipien für Entwickler & Coding-Agents:
1. **Lokaler KI-Pfad mit klaren Grenzen:** OCR läuft mit Google ML Kit; native llama.cpp/GGUF-Inferenz ist CPU-only und für `arm64-v8a`/`x86_64` gebaut. Auf anderen ABIs läuft ein Stub-/Fallback-Pfad. Vor jeder Behauptung prüfen, ob ein konkreter Feature-Aufrufer wirklich `LOCAL_LLM` erreicht oder heuristisch zurückfällt.
2. **0 € Tokenkosten:** Keine externen API-Abhängigkeiten (OpenAI, Gemini REST, Anthropic) im Produktivbetrieb.
3. **Deterministische Sicherheit vor KI:** Die KI liefert Vorschläge und Vorfilterungen; die finale Namensgebung, Regelzuweisung, Dateiablage und Revisionsprüfung erfolgen durch deterministischen Kotlin-Code.
4. **Zwingendes Test-Gate:** Nach jedem Commit den passenden GitHub-Actions-Run für genau diesen Commit prüfen. Lokale Gradle-Tests ausführen, wenn die Umgebung sie unterstützt; keine nicht vorhandenen Jobs/Tasks erfinden. Ein grüner CI-Lauf ist kein physischer Gerätetest.

---

## 2. On-Device 4-Team KI-Pipeline

Die folgende Darstellung beschreibt die vorgesehenen Komponenten und deren Zusammenarbeit. Sie ist kein Beweis, dass jeder Aufrufer aktuell eine vollständig modellgetriebene End-to-End-Pipeline ausführt. Der deterministische Gatekeeper prüft definierte Plausibilitäts- und OCR-Anker, kann aber nicht sämtliche Halluzinationen ausschließen.

```
+---------------------------------------------------------------------------------------+
|                   4-TEAM ON-DEVICE PIPELINE (Ziel-/Komponentenübersicht)               |
+---------------------------------------------------------------------------------------+
| 1. Team 1: Poststelle & Eingangstriage                                                |
|    • OcrPreFilterMatrix: Schneidet Volltext auf Kopfzeilen & Signalzeilen (max 900ch) |
|    • First Run: Schnelle Vorhersage von Absender, Dokumententyp & Hauptkategorie     |
|    • GBNF Grammar: TRIAGE_JSON_GBNF erzwingt striktes JSON ohne Overhead              |
+---------------------------------------------------------------------------------------+
                                           │
                                           ▼ (Automatischer Kotlin-Dispatcher)
+---------------------------------------------------------------------------------------+
| 2. Team 2: Fachabteilungs-Teams (Second Run Tiefenscan)                               |
|    • DepartmentTeam.FINANCE: Rechnungen, Belege, IBAN, Betrag, Skonto, Steuern        |
|    • DepartmentTeam.CONTRACTS: Verträge, Policen, Fristen, Mindestlaufzeit, Raten     |
|    • DepartmentTeam.ADMINISTRATION: Behörden, Bescheide, Aktenzeichen, Betreff        |
|    • Dynamische Injektion der 'FolderStrategy' (Ebene 1: Absender/Kategorie/Jahr/Flat)|
+---------------------------------------------------------------------------------------+
                                           │
                                           ▼
+---------------------------------------------------------------------------------------+
| 3. Team 3: Stammdaten & Whitelist-Manager                                             |
|    • Liest existierende globale Zusatzfelder aus Room DB (CustomFieldDao)             |
|    • Injiziert Whitelist in den Prompt: "Verwende ZWINGEND bestehende Feldnamen"      |
|    • Verhindert Synonym-Wildwuchs (z.B. 'Zählernr' vs 'Zählernummer')                 |
+---------------------------------------------------------------------------------------+
                                           │
                                           ▼
+---------------------------------------------------------------------------------------+
| 4. Team 4: Revision & Quality Gatekeeper (Deterministischer Cross-Check)              |
|    • Reines Kotlin, 0 LLM-Aufrufe                                                     |
|    • Text-Anchor-Cross-Check: Prüft, ob Betrag, IBAN, Datum wirklich im OCR vorkommen |
|    • Normalisierung: Währung ("1.000,00 €" -> 1000.00) & Datum ("12. März" -> 12.03)  |
|    • Plausibilitätsfilter: Fängt Geburtsdatum-Verwechslung neben 'geb.' ab            |
|    • Confidence-Score & 1-Klick-Korrekturvorschläge im UI                             |
+---------------------------------------------------------------------------------------+
```

### Technische Implementierung der Teams:
- `OcrPreFilterMatrix.kt` / `LlamaCppInferenceEngine.kt`: Vorfilterung und GBNF-Grammatiken (`TRIAGE_JSON_GBNF`, `DMS_JSON_GBNF`).
- `DepartmentTeam.kt`: Dispatcher via `DepartmentTeam.dispatch(docTypeOrText)`.
- `QualityGatekeeper.kt`: Revisions-Prüfung, Regex-Normalisierung und Kontextprüfung.
- `LlmService.kt`: Zentrale Schnittstelle für `classifyDocumentFirstRun`, `classifyDocumentSecondRun` und Fallback-Heuristiken.

---

## 3. Datenbank- & Datenmodell-Architektur (Room)

Die Datenbank (`AppDatabase.kt`) läuft auf SQLite mit Room und FTS4-Volltextindizierung.

### Zentrale Entities:
1. **`DocumentEntity`:**
   - Primärschlüssel: `id: Long` (autoGenerate = true)
   - Felder: `title`, `sender`, `fileName`, `filePath`, `fileSize`, `mimeType`, `mainCategory`, `subCategory`, `docType`, `ocrText`, `createdAt`, `modifiedAt`, `cancellationDeadline`, `contractEndDate`, `amount`, `isDeleted`, `customIcon`, `companyLogo`.
   - Indizes auf: `isDeleted`, `createdAt`, `docType`, `mainCategory`, `cancellationDeadline`.
2. **`DocumentFtsEntity` (FTS4):**
   - Unterstützt blitzschnelle Tokensuche über den gesamten indexierten OCR-Volltext.
3. **`CustomFieldEntity` & `DocumentCustomFieldValueEntity`:**
   - Erlaubt beliebig viele dynamische Metadatenfelder (Text, Zahl, Datum, Boolean, Auswahl).
   - Scope: `GLOBAL` (für alle Dokumente sichtbar) oder `FOLDER_SPECIFIC`.
4. **`FinanceTransactionEntity` & `CashBookEntryEntity`:**
   - Speichert Banktransaktionen aus CSV-Kontoauszügen und manuelle Bar-Ausgaben.
   - Ermöglicht Belegabgleich (Matching von gescannten Rechnungen mit Bankumsätzen).

---

## 4. Deterministische Regel-Engine (`DocRule`)

Die App verwendet ein automatisiertes Regelwerk (`DocRuleRepository.kt`), um bekannte Absender ohne KI-Wartezeit sofort einzuordnen:

### Regel-Struktur (`DocRule`):
- `name`: Deterministisch generiert im Format `"$Absender $Dokumententyp"`.
- `matchKeywords`: Liste von Pflicht-Schlagwörtern (z.B. `["vodafone", "mobilfunkrechnung"]`).
- `excludeKeywords`: Ausschlussbegriffe.
- `targetMainCategoryId`, `targetSubCategoryId`, `targetDocType`.
- `targetFolderPath`: Ziel-Ordnerpfad basierend auf der aktiven `FolderStrategy`.
- `targetCustomFields`: Vorbelegte Metadaten.

### Ordner-Strategie (`FolderStrategy`):
1. `BY_SENDER`: Ebene 1 nach Firma/Aussteller (z.B. `Vodafone/Rechnungen`).
2. `BY_CATEGORY`: Ebene 1 nach Lebensbereich (z.B. `Finanzen/Versicherungen`).
3. `CHRONOLOGICAL`: Ebene 1 nach Jahr (z.B. `2026/Rechnungen`).
4. `FLAT`: Zentraler Hauptordner (`Dokumente`).

---

## 5. Sicherheit, Biometrie & Verschlüsselung

- **Biometrie & Passkey:** `BiometricAuthManager.kt` und `PasskeyAuthManager.kt` schützen den Tresor-Zugriff über die Android BiometricPrompt API.
- **Zero-Knowledge Backup-Verschlüsselung:** Exportierte Backups werden lokal clientseitig per **AES-256-GCM** mit PBKDF2-Key-Derivation (100.000 Runden + Salt) verschlüsselt.
- **Audit-Logging:** `AppAuditLogger.kt` protokolliert alle KI-Inferenzen, OCR-Läufe und Sync-Vorgänge in einem 100% lokalen Ringspeicher (`logs.txt`).

---

## 6. P2P Local WiFi Synchronisation

- **Protokoll:** Direkte Peer-to-Peer Kommunikation im lokalen WLAN (Port 8765) ohne Cloud-Server.
- **Pairing:** Sicherer Handshake via Desktop-QR-Code oder 6-stelligem PIN.
- **Verschlüsselung:** AES-256-GCM mit Pre-Shared Key.
- **Konfliktbehandlung:** 3-Way Snapshot-Vergleich mit optischer Gegenüberstellung im UI.

---
## Dokumentationspflicht für Coding-Agents

Nach Änderungen die folgenden Dateien synchronisieren, sobald der jeweilige Inhalt betroffen ist:

- `docs/LLM_NATIVE_PRODUCTION_PLAN.md`: immer nach jedem Arbeitsblock; erledigte Aufgaben, genaue Commit-SHA, CI-Status/Run-Link und offene Folgearbeiten.
- `README.md`: öffentliche Architektur, Build-/Nutzerhinweise oder reale Funktionsgrenzen ändern.
- `docs/LLM_FUNKTIONEN_UND_PROZESSE_DOKUMENTATION.md` und `docs/LLM_INTEGRATION_DOCUMENTATION.md`: LLM-Prompts, Inferenzpfad, Modellkatalog, Benchmarks, Fallbacks oder Tool-Calling ändern.
- `docs/coding-agent_documentation.md`: Build-/Agent-Prozess, Architektur oder verbindliche Entwicklerregeln ändern.
- `docs/README.md`: Dokumente hinzufügen, umbenennen oder ihre Rolle ändern.

**Arbeitsregeln:**
1. Vor Änderungen den Code und die vorhandenen Dokumente lesen; Dokumente nie aus Annahmen aktualisieren.
2. Keine Geschwindigkeit, Genauigkeit, GPU-Unterstützung, Tool-Funktion oder End-to-End-Lokalinferenz als verifiziert bezeichnen, wenn es dafür keinen Nachweis gibt.
3. Einen Fallback nie als Modellantwort kennzeichnen. Einen Benchmark nur als gemessen markieren, wenn `lastInferenceMode == LOCAL_LLM` und echte native Messwerte vorliegen.
4. Nach jedem Commit die Actions für genau dessen SHA prüfen. Keinen zweiten Commit auf `main` pushen, während der zugehörige Build läuft, wenn dadurch der Run abgebrochen würde; erst bei Abschluss Status und Doku aktualisieren.
5. Ein Gerätetest ist erst erledigt, wenn eine tatsächliche GGUF-Inferenz auf dem Zielgerät belegt ist. CI-Kompilierung allein genügt nicht.


## 7. Mandatory Test Gate & Entwickler-Richtlinien

Jeder Entwickler und Coding-Agent muss vor Abschluss jeder Aufgabe folgenden Zyklus einhalten:

1. **GitHub Actions zuerst prüfen:** Öffne den neuesten Run in `.github/workflows/build-apk.yml`, prüfe `head_sha`, Jobstatus und Fazit. Nur `completed/success` gilt als grüner Lauf. Bei `in_progress` oder `queued` ist das Ergebnis offen; bei `cancelled`/failure darf Erfolg nicht behauptet werden.
2. **Lokale Gradle-Tests, sofern ausführbar:**
   ```bash
   gradle :app:testDebugUnitTest
   ```
   Simuliert den kompletten App-Lifecycle auf der JVM:
   - `MainActivity.onCreate()`
   - Room DB Tabellen, DAOs und Migrationen
   - `DocAnizerViewModel` StateFlows & Coroutines
   - 4-Team KI-Pipeline, QualityGatekeeper & Normalisierung
   - DocRule Matching & P2P Sync Datenmodelle
3. **App-Identität:**
   - Der offizielle Name lautet stets **myDocAnizer-Mobile** (synchron in `metadata.json`, `strings.xml` und `settings.gradle.kts`).
