# myDocAnizer-Mobile: Coding-Agent & Technische Entwickler-Dokumentation

**Projekt:** myDocAnizer-Mobile (Android)  
**Paketname (Namespace):** `com.example`  
**Architektur:** MVVM + Clean Architecture / Jetpack Compose / Android Room (SQLite + FTS4) / Offline ML Kit OCR / On-Device GGUF Nano-LLMs / AES-256-GCM / P2P Local WiFi Sync  
**Plattform:** Google AI Studio Build / Android Studio  

---

## 1. Systemübersicht & Philosophie

**myDocAnizer-Mobile** ist ein 100% datenschutzkonformes, cloudfreies Dokumenten-Management-System (DMS) und intelligenter Dokumenten-Scanner für Android.

### Kernprinzipien für Entwickler & Coding-Agents:
1. **100% On-Device & Zero-Cloud-AI:** Sämtliche OCR- und LLM-Inferenzprozesse laufen lokal auf dem Endgerät (Google ML Kit + Llama.cpp / GGUF). Keine Daten verlassen das Smartphone unverschlüsselt.
2. **0 € Tokenkosten:** Keine externen API-Abhängigkeiten (OpenAI, Gemini REST, Anthropic) im Produktivbetrieb.
3. **Deterministische Sicherheit vor KI:** Die KI liefert Vorschläge und Vorfilterungen; die finale Namensgebung, Regelzuweisung, Dateiablage und Revisionsprüfung erfolgen durch deterministischen Kotlin-Code.
4. **Zwingendes Mandatory Test Gate:** Nach jeder Code-Änderung müssen `compile_applet` und `gradle :app:testDebugUnitTest` fehlerfrei durchlaufen.

---

## 2. On-Device 4-Team KI-Pipeline

Die KI-Architektur ist in 4 spezialisierte "Teams" unterteilt, die sich gegenseitig entlasten und Halluzinationen deterministisch abfangen:

```
+---------------------------------------------------------------------------------------+
|                                4-TEAM ON-DEVICE PIPELINE                              |
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

## 7. Mandatory Test Gate & Entwickler-Richtlinien

Jeder Entwickler und Coding-Agent muss vor Abschluss jeder Aufgabe folgenden Zyklus einhalten:

1. **Kompilierung:** `compile_applet` (muss fehlerfrei sein).
2. **Robolectric JVM Test Suite:**
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
