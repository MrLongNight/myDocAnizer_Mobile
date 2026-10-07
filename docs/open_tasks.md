# Offene Refactoring-Aufgaben & Code-Optimierung (myDocAnizer-Mobile)

Dieses Dokument enthält eine detaillierte Analyse der Codebasis und strukturierte Arbeitspakete (Prompts) für das Refactoring von zu groß gewordenen Dateien. Ziel ist es, die Wartbarkeit zu erhöhen, die Build-Zeiten zu optimieren und die Einhaltung von Best Practices (SOLID, Clean Architecture) sicherzustellen.

---

## 📊 Analyse der aktuellen Dateigrößen (Auszug)

| Datei | Zeilen | Empfohlene Maßnahme |
| :--- | :--- | :--- |
| `SetupWizardDialog.kt` | ~4.900 | Aufteilen in einzelne Step-Composables pro Datei. |
| `SettingsView.kt` | ~4.750 | Modularisierung in Sub-Screens (Appearance, Security, Cloud, etc.). |
| `DmsExplorerView.kt` | ~3.300 | Extraktion von Komponenten (FolderList, DocList, FilterBar). |
| `DocAnizerViewModel.kt`| ~3.300 | Aufteilung in Module-spezifische ViewModels oder Delegates. |
| `LlmService.kt` | ~2.800 | Trennung von Inferenz-Logik und Tool-Handling. |
| `DashboardView.kt` | ~2.100 | Auslagern der Widget-Composables in eigene Dateien. |

---

## 🧪 Arbeitspaket 5: Integriertes Test- & Diagnose-System (Geführte Routinen)
**Hintergrund:** Um die App stabil zu halten und Fehlerberichte für die KI zu optimieren, bauen wir ein vierstufiges Test-System ein.

### Ziel-Routinen im Entwickler-Menü:
1.  **Routine 1: Basis-Integrität (Auto-Run)**
    *   Prüft automatisch beim App-Start oder Menü-Aufruf: DB-Verbindung, Schreibrechte, Speicherplatz, Kamerazugriff.
2.  **Routine 2: KI & Regelerkennung (Geführt)**
    *   Manueller Scan eines Testdokuments -> Detaillierte Anzeige der Entscheidungsmatrix (warum welche Regel gegriffen hat).
3.  **Routine 4: Realistische Benutzer- & Stress-Simulation (Vollautomatisch)**
    *   Wahl aus 5 lebensnahen, detaillierten Benutzerprofilen (Personas):
        - **Familie Weber (Angestellter):** Gehaltsabrechnung, Betriebs-/Heizkosten, Kfz-Versicherung, SWM Strom/Gas, Telekom DSL, Allianz Hausrat/Haftpflicht, Kita-Bescheid, Einkommensteuer, MediaMarkt Garantie.
        - **Marco Rossi (Freiberufler/IT):** Ausgangsrechnung BMW, MacBook Pro Rechnung, Hetzner Server, USt-Voranmeldung, Bewirtungsbeleg, DB Reisekosten, Hiscox IT-Haftpflicht, Coworking.
        - **Margarete Wagner (Rentnerin):** Rentenanpassungsmitteilung, Handwerkerrechnung §35a Rohrbruch, Apotheken-Zuzahlungsbeleg, Fielmann Gleitsichtbrille, TK Zahnersatz, Rundfunkbeitrag.
        - **Jonas Bergmann (Student/WG):** BAföG-Bescheid, TUM Immatrikulationsbescheinigung, WG-Untermietvertrag, Rewe Minijob, MVG Deutschlandticket, Hugendubel Fachliteratur.
        - **Dr. Thomas Neumann (Immobilien/Vermieter):** Wohnraum-Mietvertrag Maxvorstadt, WEG Hausgeld-Abrechnung, Grundsteuer B Bescheid, Dachsanierung, Sparkasse Darlehenszins, Gebäudeversicherung.
    *   Vollautomatische Generierung authentischer Dokumente mit DIN-A4 PDF, echten OCR-Volltexten, Beträgen, Steueranteilen, Kündigungsfristen und Metadaten.
    *   Strukturierte Auswertung mit Kennzahlen (Dokumente, Finanzvolumen, Fristen, Verarbeitungsdauer) und 1-Klick-Report-Export für KI-Support.

### Diagnose-Report (Copy-Paste für die KI):
Ein zentraler Button "Diagnose-Report für Support kopieren" erstellt eine Zusammenfassung der letzten OCR-Ergebnisse, Regeltreffer und Systemstatus in Textform.

---

## 🛠️ Arbeitspakete & Prompts für Gemini Flash-Lite

Die folgenden Prompts sind so konzipiert, dass sie vom **Gemini Flash-Lite** Modell effizient verarbeitet werden können.

### Paket 1: Refactoring `SettingsView.kt` (Priorität: Hoch)
**Hintergrund:** Die Datei ist mit fast 5.000 Zeilen kaum noch wartbar.
**Ziel:** Aufteilung in funktionale Unter-Dateien im Ordner `app/src/main/java/com/example/ui/views/settings/`.

**Prompt für Gemini Flash-Lite:**
```text
Analysiere die Datei 'app/src/main/java/com/example/ui/views/SettingsView.kt'.
Extrahiere die folgenden Tabs/Sektionen in eigene Dateien im neuen Verzeichnis 'app/src/main/java/com/example/ui/views/settings/':
1. AppearanceSettingsTab.kt (Themen, Farben, Barrierefreiheit)
2. SecuritySettingsTab.kt (Passkey, Biometrie, App-Sperre)
3. CloudSyncSettingsTab.kt (Backup, Google Drive, WebDAV)
4. ModuleSettingsTab.kt (Aktivierung von Bargeld-Tracker, Finanzen etc.)
5. DeveloperSettingsTab.kt (Debug-Tools, Log-Level, Seeder)

Stelle sicher, dass alle notwendigen Imports und das DocAnizerViewModel korrekt übergeben werden. Die Hauptdatei 'SettingsView.kt' soll danach nur noch das Scaffold und die Tab-Navigation enthalten.
```

### Paket 2: Refactoring `DocAnizerViewModel.kt` (Priorität: Hoch)
**Hintergrund:** Das ViewModel enthält die Logik für alle App-Bereiche.
**Ziel:** Einführung von Domain-spezifischen Managern oder Delegates.

**Prompt für Gemini Flash-Lite:**
```text
Die Datei 'app/src/main/java/com/example/ui/DocAnizerViewModel.kt' ist zu groß. 
Erstelle ein Refactoring-Konzept, um die Verantwortlichkeiten aufzuteilen.
1. Erstelle 'DmsManager.kt' für die Dokumenten- und Ordnerverwaltung.
2. Erstelle 'FinanceManager.kt' für Bargeld-Tracker und Haushaltsbuch-Logik.
3. Erstelle 'SyncManager.kt' für Cloud- und P2P-Synchronisation.

Verschiebe die entsprechende Logik und StateFlows aus dem ViewModel in diese neuen Klassen. Das ViewModel soll diese Manager injiziert bekommen und nur noch als zentrale Anlaufstelle für die UI dienen (Fassade-Pattern).
```

### Paket 3: Refactoring `SetupWizardDialog.kt` (Priorität: Mittel)
**Hintergrund:** Enthält alle 12+ Schritte des Onboarding-Assistenten.
**Ziel:** Ein Composable pro Datei.

**Prompt für Gemini Flash-Lite:**
```text
Refactore 'app/src/main/java/com/example/ui/views/SetupWizardDialog.kt'.
Erstelle einen neuen Ordner 'app/src/main/java/com/example/ui/views/wizard/'.
Extrahiere jeden Wizard-Schritt (z.B. WelcomeStep, ThemeStep, SecurityStep, CloudStep) in eine eigene Kotlin-Datei.
Verwende ein gemeinsames Interface oder eine versiegelte Klasse (sealed class) für die Wizard-States.
```

### Paket 4: Refactoring `LlmService.kt`
**Hintergrund:** Vermischung von LLM-Inferenz und Tool-Logik.
**Ziel:** Saubere Trennung der KI-Infrastruktur.

**Prompt für Gemini Flash-Lite:**
```text
Analysiere 'app/src/main/java/com/example/service/LlmService.kt'.
Lagere die Tool-Ausführungslogik (Tool Calling / Function Calling) in eine eigene Klasse 'LlmToolExecutor.kt' aus.
Verschiebe die Prompt-Templates in eine dedizierte Resource-Klasse oder Konstanten-Datei 'LlmPrompts.kt'.
Der LlmService soll sich nur noch um die Kommunikation mit der LlamaCpp-Engine oder der Gemini API kümmern.
```

---

## 📝 Implementierte Änderungen (Status: Erledigt)

### 1. Erweitertes Logging-System (Diagnose-Modus)
- **Datei:** `app/src/main/java/com/example/service/AppAuditLogger.kt`
- **Änderung:** Einführung von `LogLevel` (NONE, STANDARD, DEVELOP).
- **Logik:** 
  - `NONE`: Kein Logging.
  - `STANDARD`: Volles Logging, aber ohne OCR-Texte (Datenschutz).
  - `DEVELOP`: Volles Logging inklusive OCR-Rohdaten für Debugging.

### 2. Integration in die Einstellungen
- **Datei:** `app/src/main/java/com/example/service/SettingsRepository.kt` & `SettingsView.kt`
- **Änderung:** Neue Einstellung "Diagnose-Logging" im Entwickler-Tab hinzugefügt. Der Status wird persistent gespeichert und steuert den `AppAuditLogger` global.

---

## 🚀 Nächste Schritte für den Entwickler

1. Führe die oben genannten Prompts nacheinander mit einem KI-Modell aus.
2. Verifiziere nach jedem Schritt die Kompilierung mit `compile_applet`.
3. Führe die Unit-Tests aus: `gradle :app:testDebugUnitTest`.
