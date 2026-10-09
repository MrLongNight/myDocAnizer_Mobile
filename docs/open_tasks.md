# Offene Refactoring-Aufgaben & Code-Optimierung (myDocAnizer-Mobile)

Dieses Dokument enthält den aktuellen Status der Modularisierung, die Analyse der Dateigrößen sowie strukturierte, direkt ausführbare Prompts für anstehende Refactoring-Aufgaben (optimiert für KI-Modelle / Gemini).

---

## 📊 Aktuelle Dateigrößen-Analyse (Größte Dateien)

| Datei | Zeilen | Aktueller Status / Empfohlene Maßnahme |
| :--- | :--- | :--- |
| `DmsExplorerView.kt` | 3.342 | **Offen**: Hauptansicht für Dokumenten-Tresor; Teilkomponenten in `dms/` vorhanden, Delegierung ausstehend. |
| `DocAnizerViewModel.kt` | 2.868 | **Teilweise refaktoriert**: `DmsManager`, `FinanceManager`, `SyncManager` ausgelagert. |
| `LlmService.kt` | 2.195 | **Teilweise refaktoriert**: Prompts in `LlmPrompts.kt` (208 Z.) & Tools in `LlmToolExecutor.kt` (600 Z.) ausgelagert. |
| `DashboardView.kt` | 1.791 | **Teilweise refaktoriert**: Standard-Widgets nach `dashboard/` ausgelagert (Finance, RecentDocs, Shortcuts, Sync). |
| `SettingsRepository.kt` | 1.416 | **Stabil**: Daten-Repository für App-Einstellungen. |
| `Models.kt` | 1.345 | **Stabil**: Domänenmodelle und Enums. |
| `P2pSyncServer.kt` | 1.310 | **Stabil**: P2P HTTP/TLS Server-Implementierung. |
| `CloudStep.kt` | 1.195 | **Offen**: Modularisierung von WebDAV- & Google Drive Sub-Komponenten. |
| `MainScreen.kt` | 1.194 | **Offen**: Modularisierung von Drawer, Navigation und Screen-Routing. |
| `UserPersonaFactory.kt` | 1.150 | **Stabil**: Persona-Vorlagen und Template-Definitionen. |
| `IconAndLogoCatalog.kt` | 1.091 | **Stabil**: Icon- und Logokatalog. |
| `P2pSyncTab.kt` | 1.010 | **Offen**: Aufteilung in Device-Pairing, QR-Scanner & Transfer-Monitor. |
| `AiDocumentChatDrawer.kt` | 994 | **Offen**: Chat-Message-List, Input-Bar & Context-Chips auslagern. |
| `PcCompanionWizardDialog.kt` | 979 | **Offen**: Pairing-, Verbindungs- & Sync-Schritte extrahieren. |
| `CustomWidgetEditorDialog.kt` | 931 | **Erledigt**: Aus `DashboardCustomWidgetsUi.kt` nach `dashboard/custom/` ausgelagert. |
| `ReconciliationDialog.kt` | 898 | **Offen**: Bankabgleich-Filter & Match-Karten auslagern. |
| `DocumentDetailModal.kt` | 896 | **Offen**: Metadaten-Formular, OCR-Vorschau, Versionierung. |
| `ImportView.kt` | 884 | **Offen**: Datei-Picker, Batch-Queue & Preview. |
| `AiProviderConfigPane.kt` | 829 | **Erledigt**: Aus `RulesAndAiSettingsView.kt` nach `rules/` ausgelagert. |
| `ScannerView.kt` | 593 | **Erledigt**: Aufgeteilt in `scanner/` (`ScannerCameraPreviewPane`, `ScannerCropOverlayPane`, `ScannerPageReviewPane`). |
| `RulesAndAiSettingsView.kt` | 434 | **Erledigt**: Aufgeteilt in `rules/` (`RuleListAndEditorPane`, `AiProviderConfigPane`, `RuleTestingDialog`). |
| `SetupWizardDialog.kt` | 320 | **Erledigt**: Auf 320 Zeilen geschrumpft (Step-Composables in `wizard/` ausgelagert). |
| `DashboardCustomWidgetsUi.kt` | 155 | **Erledigt**: Auf 155 Zeilen geschrumpft (`dashboard/custom/`). |
| `SettingsView.kt` | 81 | **Erledigt**: Auf 81 Zeilen geschrumpft (`settings/`). |

---

## ✅ Status der Refactoring-Pakete (Kurzübersicht)

- **Paket 1 (`SettingsView.kt`)**: ✅ **Erledigt** – Auf 81 Zeilen reduziert (`ui/views/settings/`).
- **Paket 2 (`DocAnizerViewModel.kt`)**: 🟡 **Teilweise refaktoriert** – 2.868 Zeilen; Fassaden-Pattern (`DmsManager.kt`, `FinanceManager.kt`, `SyncManager.kt`).
- **Paket 3 (`SetupWizardDialog.kt`)**: ✅ **Erledigt** – Auf 320 Zeilen reduziert (`ui/views/wizard/`).
- **Paket 4 (`LlmService.kt`)**: 🟡 **Teilweise refaktoriert** – 2.195 Zeilen; `LlmPrompts.kt` (208 Z.) & `LlmToolExecutor.kt` (600 Z.) ausgelagert.
- **Paket 5 (Audit-Logger & Diagnose)**: ✅ **Erledigt** – `AppAuditLogger.kt` mit Log-Leveln und Settings-Integration.
- **Paket 6 (`DmsExplorerView.kt`)**: 🟡 **Offen / Nacharbeit** – 3.342 Zeilen; Teilkomponenten in `ui/views/dms/` angelegt (`DmsFolderNavPane`, `DmsDocumentListView`, `DmsFilterBar`, `DmsBatchActionToolbar`), vollständige Entflechtung der Hauptansicht ausstehend.
- **Paket 7 (`DashboardView.kt` Widgets)**: 🟡 **Teilweise refaktoriert** – 1.791 Zeilen; Standard-Widgets ausgelagert (`ui/views/dashboard/`).
- **Paket 8 (`DashboardCustomWidgetsUi.kt`)**: ✅ **Erledigt** – Auf 155 Zeilen reduziert (`ui/views/dashboard/custom/`: `CustomWidgetRenderer`, `CustomWidgetEditorDialog`, `CustomWidgetTypesUi`).
- **Paket 9 (`RulesAndAiSettingsView.kt`)**: ✅ **Erledigt** – Auf 434 Zeilen reduziert (`ui/views/rules/`: `RuleListAndEditorPane`, `AiProviderConfigPane`, `RuleTestingDialog`).
- **Paket 10 (`ScannerView.kt`)**: ✅ **Erledigt** – Auf 593 Zeilen reduziert (`ui/views/scanner/`: `ScannerCameraPreviewPane`, `ScannerCropOverlayPane`, `ScannerPageReviewPane`).

---

## 🛠️ Ausführbare Prompts für anstehende Refactoring-Pakete

### 📋 Prompt für Paket 8: Refactoring `DashboardCustomWidgetsUi.kt` (Priorität: Hoch)
```text
Analysiere 'app/src/main/java/com/example/ui/views/DashboardCustomWidgetsUi.kt' (~1.537 Zeilen).
Die Datei enthält Rendering, Bearbeitungsdialoge und Konfiguration für benutzerdefinierte Dashboard-Widgets (z.B. Quick Links, Notizen, KPI-Karten, Diagramme).

Führe folgende Schritte aus:
1. Erstelle den Ordner 'app/src/main/java/com/example/ui/views/dashboard/custom/'.
2. Extrahiere die Komponenten in dedizierte Dateien:
   - 'CustomWidgetRenderer.kt': Dispatcher und Basiskarten für Custom Widgets.
   - 'CustomWidgetEditorDialog.kt': Dialog zum Erstellen/Bearbeiten von Widgets (Typ, Farben, Icons, Filter).
   - 'CustomWidgetTypesUi.kt': Typ-spezifische Views (Notizen, Zähler, Schnellverknüpfungen, KPIs).
3. Bereinige 'DashboardCustomWidgetsUi.kt' als schlanke Schnittstelle.
4. Verifiziere das Refactoring mit 'compile_applet' und 'gradle :app:testDebugUnitTest'.
```

### 📋 Prompt für Paket 9: Refactoring `RulesAndAiSettingsView.kt` (Priorität: Hoch)
```text
Analysiere die Datei 'app/src/main/java/com/example/ui/views/RulesAndAiSettingsView.kt' (~1.633 Zeilen).
Die Datei vermischt Regel-Verwaltung (Auto-Tagging, Ordnerzuweisung, Regex) mit KI-Provider-Einstellungen (Gemini, LlamaCpp, On-Device).

Führe folgende Schritte aus:
1. Erstelle den Ordner 'app/src/main/java/com/example/ui/views/rules/'.
2. Extrahiere folgende Teil-Composables:
   - 'RuleListAndEditorPane.kt': Anzeige bestehender Regeln, Regel-Editor-Dialog, Bedingungen & Aktionen.
   - 'AiProviderConfigPane.kt': Konfiguration lokaler/Cloud-Modelle, API-Keys, Prompt-Feintuning.
   - 'RuleTestingDialog.kt': Interaktives Testen von Regeln gegen Beispieldokumente.
3. Bereinige 'RulesAndAiSettingsView.kt' als koordinierende Tab-View.
4. Verifiziere das Refactoring mit 'compile_applet' und 'gradle :app:testDebugUnitTest'.
```

### 📋 Prompt für Paket 10: Refactoring `ScannerView.kt` (Priorität: Mittel)
```text
Analysiere die Datei 'app/src/main/java/com/example/ui/views/ScannerView.kt' (~1.456 Zeilen).
Die Datei steuert Kamera, Dokumentenkantenerkennung, manuelles Zuschneiden, Farbfilter und Mehrseiten-Scan.

Führe folgende Schritte aus:
1. Erstelle den Ordner 'app/src/main/java/com/example/ui/views/scanner/'.
2. Extrahiere Teil-Composables:
   - 'ScannerCameraPreviewPane.kt': CameraX-Vorschau, Taschenlampe, Shutter-Button.
   - 'ScannerCropOverlayPane.kt': Kantenanzeige, Lupen-Zoom, Eckpunkt-Verschiebung.
   - 'ScannerPageReviewPane.kt': Multi-Page Thumbnail-Liste, Sortierung, Drehung & Filterwahl.
3. Bereinige 'ScannerView.kt' als Haupt-Controller.
4. Verifiziere das Refactoring mit 'compile_applet' und 'gradle :app:testDebugUnitTest'.
```
