# Ganzheitliches Code- & App-Audit für myDocAnizer-Mobile

## Zusammenfassung
Dieses Dokument beschreibt den umfassenden Audit- und Optimierungsplan für **myDocAnizer-Mobile**. Das Audit deckt alle Schichten der Anwendung ab: Performance & Speicherverwaltung (LLM, OCR, Bitmap-Recycling, Room DB), Sicherheit & Datenschutz (AES-256-GCM, Biometrie, Audit-Logs), UI/UX-Recompositionen (Jetpack Compose, Touch-Targets, Insets) sowie allgemeine Code-Qualität & Architektur.

---

## 1. Audit-Säulen & Prüfbereiche

### A. Performance & Speicherverwaltung (Memory & Resource Audit)
1. **LLM & GGUF Modell-Lebenszyklus (`LlmService.kt`)**:
   - Prüfung der Entladung von inaktiven GGUF-Modellen aus dem Arbeitsspeicher (RAM/VRAM).
   - Validierung der CoroutineScopes und Verhinderung von Memory Leaks bei abgebrochenen Modell-Downloads oder Inferenzläufen.
2. **Bitmap & Bild-Cache Management (`ScannerView.kt`, `DmsExplorerView.kt`)**:
   - Überprüfung des Recyclings von hochauflösenden Bitmap-Objekten nach dem OCR-Scan und der Dokumentdarstellung.
   - Optimierung von Coil ImageLoader Caching-Strategien für Thumbnail-Generierung.
3. **Room-Datenbank & SQLite-Indizes (`AppDatabase.kt`, `DocumentDao.kt`)**:
   - Analyse der Query-Ausführungszeiten und FTS4-Volltextindizierung.
   - Sicherstellung, dass alle gefilterten Spalten (`isDeleted`, `createdAt`, `docType`, `mainCategory`, `cancellationDeadline`) durch zusammengesetzte Indizes abgedeckt sind.

---

### B. Sicherheit, Datenschutz & Zero-Knowledge (Security Audit)
1. **AES-256-GCM Backup- & Tresor-Verschlüsselung (`SyncManager.kt`, `BackupRestoreManager.kt`)**:
   - Prüfung der PBKDF2-Schlüsselableitung (Salt, Iterationen) und IV-Generierung für Backups.
   - Verifikation, dass keine unverschlüsselten Hauptschlüssel im Arbeitsspeicher verbleiben.
2. **Biometrie- & Passkey-Entsperrung (`BiometricAuthManager.kt`, `PasskeyAuthManager.kt`)**:
   - Absicherung von Prompt-Abbrüchen, Fallback-Prozessen bei Sensorfehlern und Screen-Orientation-Changes.
3. **Audit-Logger & Ringpuffer (`AppAuditLogger.kt`)**:
   - Überprüfung der Maskierung sensibler Inhalte in Log-Einträgen.
   - Validierung der automatischen Begrenzung der Log-Dateigröße (Ringspeicher max. 2 MB).

---

### C. UI/UX, Material 3 & Jetpack Compose (UI Audit)
1. **Recompositionen & State Stability**:
   - Prüfung aller Composable-Funktionen auf unberechtigte Recompositionen (Nutzung von `derivedStateOf`, `remember` und unmodifiable Collections).
2. **Barrierefreiheit & Touch Target Compliance**:
   - Sicherstellung, dass alle interaktiven Icons und Buttons eine Mindest-Touch-Größe von **48dp x 48dp** einhalten.
3. **WindowInsets & Edge-to-Edge**:
   - Nahtlose Handhabung von Status- und Navigationsleisten bei unterschiedlichen Bildschirmauflösungen und Foldables.

---

### D. Code-Qualität & Robustheit (Code Integrity Audit)
1. **Fehlerbehandlung & Exception Handling**:
   - Prüfung aller Coroutine Exception Handler und DAO-Transaktionen auf unbewusste Silent Failures.
2. **Mandatory Test Gate**:
   - Vollständiger Durchlauf von `compile_applet` und `gradle :app:testDebugUnitTest`.

---

## 2. Empfohlene Optimierungspakete

| Paket | Bereich | Ziel & Optimierung |
| :--- | :--- | :--- |
| **Paket A** | **LLM & Memory Optimization** | Explizite Entladung inaktiver Modell-Instanzen & Caching im `LlmService.kt` |
| **Paket B** | **Bitmap Caching & Page Review** | Garbage-Collection-Erleichterung bei Mehrseiten-Scans |
| **Paket C** | **Room DB Query Indexing** | Feinjustierung zusammengesetzter Indizes für `DocumentDao` & FTS4 |
| **Paket D** | **Compose Touch Targets & Insets** | Durchgängige 48dp Touch Target Mindestgröße in BottomBar & TopBar |

---

## 3. Verifizierungs- und Testplan

Nach Genehmigung des Plans wird der ZWINGENDE Test-Gate Ablauf ausgeführt:
1. **Kompilierungsprüfung**: `compile_applet` (muss fehlerfrei kompilieren).
2. **Robolectric JVM Test-Suite**: `gradle :app:testDebugUnitTest` zur Absicherung des Starts, aller ViewModels, DAOs, Security-Klassen und UI-Komponenten.
3. **Ergebnisbericht**: Detaillierte Zusammenfassung aller gefundenen und behobenen Punkte.
