# Offene Refactoring-Aufgaben & Code-Optimierung (myDocAnizer-Mobile)

Dieses Dokument enthält den tatsächlichen, realen Status der Modularisierung, die vollständige Analyse aller Dateien > 500 Zeilen sowie strukturierte, praxiserprobte und strikt abgesicherte Prompts für anstehende Refactoring-Aufgaben (speziell optimiert für KI-Modelle / Gemini, um visuelle Regressionen und Logikfehler auszuschließen).

---

## 🛡️ ZWINGENDE GOLDENE REGELN FÜR JEDES REFACTORING (KEINE VISUELLEN REGRESSIONEN!)

Für alle ausführenden KI-Agenten und Entwickler gelten bei jedem Refactoring-Schritt folgende unumstößliche Prinzipien:

1. **Reines Code-Splitting (Separation of Concerns):**
   - Es wird **ausschließlich** bestehender Code 1:1 in neue Dateien verschoben.
   - **Absolutes Verbot von Redesigns, visuellen Neuerungen oder Umgestaltungen!** Kein "Aufräumen" von Paddings, keine Farbänderungen, keine Schriftgrößenanpassungen, keine neuen Komponenten oder veränderten Layout-Hierarchien.
2. **1:1 Code-Transplantation (Kein Neuschreiben aus dem Gedächtnis):**
   - Bestehende Composables, Hilfsfunktionen, Modifier, TestTags (`Modifier.testTag(...)`), Keys und Animationen müssen unverändert kopiert werden.
   - Niemals UI-Elemente "vereinfachen" oder "zusammenfassen".
3. **Schrittweises Vorgehen (Inkrementell statt Mega-Chunks):**
   - Niemals 1.500+ Zeilen Compose-Code in einem einzigen Schritt refaktorisieren.
   - Immer Teilkomponente für Teilkomponente extrahieren und sofort kompilieren.
4. **Zwingendes Test Gate:**
   - Nach jedem Refactoring-Schritt müssen sowohl `compile_applet` als auch `gradle :app:testDebugUnitTest` fehlerfrei durchlaufen.

---

## 📊 Tatsächliche Dateigrößen-Analyse (Alle Dateien > 500 Zeilen)

| Datei | Zeilen | Realkontext & Aktueller Status || :--- | :--- | :--- |
| `DmsExplorerView.kt` | 3.342 | **Sehr groß / Hauptansicht Dokumenten-Tresor**: Früherer Auslagerungsversuch nach `ui/views/dms/` wurde bei Rollback nicht aktiv genutzt. Sollte vorsichtig und modulweise modularisiert werden. |
| `DocAnizerViewModel.kt` | 2.868 | **ViewModel-Zentrale**: Verwaltet alle StateFlows. Teilweise mit Managern gekoppelt (`DmsManager`, `FinanceManager`, `SyncManager`), aber noch stark wachsend. |
| `LlmService.kt` | 1.947 | **LLM-Engine**: Enthält On-Device Llama-Logik, Prompt-Templates, Tool-Calling und OCR-Parsing. |
| `DashboardView.kt` | 1.791 | **Dashboard-Hauptansicht**: Einige Widgets bereits in `ui/views/dashboard/` angelegt, Hauptdatei enthält noch Grid-, Filter- und Statistik-Code. |
| `RulesAndAiSettingsView.kt` | 1.632 | **Regelwerk & KI-Settings**: Bereits nach `ui/views/rules/` extrahiert (`AiProviderConfigPane`, `RuleListAndEditorPane`, `RuleTestingDialog`). `SettingsView` greift bereits direkt auf die neuen Klassen zu. Diese Alt-Datei kann nun sicher bereinigt werden. |
| `ScannerView.kt` | 1.456 | **Dokumentenscanner**: Kamera-Lifecycle, Kantenerkennung, manueller Zuschnitt, Bildfilter und Mehrseiten-Scan in einer Datei. |
| `SettingsRepository.kt` | 1.416 | **Einstellungs-Repository**: DataStore/Room-Persistenz für alle Module (Stabil). |
| `Models.kt` | 1.345 | **Domänenmodelle**: Datenklassen, Enums, Room-Entities und Type-Converter (Stabil). |
| `P2pSyncServer.kt` | 1.310 | **P2P Sync HTTP-Server**: Lokaler TLS-Server für Datenaustausch mit Desktop/Mobile (Stabil). |
| `CloudStep.kt` | 1.195 | **Setup-Wizard Cloud**: Enthält WebDAV-, Google Drive-, Nextcloud-Konfiguration. |
| `MainScreen.kt` | 1.194 | **App-Scaffold**: NavigationDrawer, TopBar, BottomBar, Floating Action Buttons & Screen-Routing. |
| `UserPersonaFactory.kt` | 1.150 | **Benutzer-Personas**: Vordefinierte Kategorien, Ordnerstrukturen und Vorlagen (Stabil). |
| `IconAndLogoCatalog.kt` | 1.091 | **Design-Katalog**: Vektor-Icons und Sender-Logos (Stabil). |
| `P2pSyncTab.kt` | 1.010 | **P2P-Sync UI**: UI für Device-Discovery, Pairing-PIN, QR-Code und Übertragungs-Log. |
| `AiDocumentChatDrawer.kt` | 994 | **KI-Chat-Drawer**: Chat-Historie, Quick-Prompts, Kontext-Vorschau und Streaming-UI. |
| `PcCompanionWizardDialog.kt` | 979 | **Desktop-Kopplungs-Dialog**: QR-Code Pairing und Verbindungsassistent. |
| `CustomWidgetEditorDialog.kt` | 931 | **Dashboard Widget-Editor**: Dialog zum Erstellen/Bearbeiten von Dashboard-Elementen. |
| `ReconciliationDialog.kt` | 898 | **Bankabgleich-Dialog**: Matching von Kontoauszugspositionen mit Belegen. |
| `DocumentDetailModal.kt` | 896 | **Dokument-Detailansicht**: Metadaten-Editor, OCR-Text-Inspektor, Tag-Zuweisung. |
| `ImportView.kt` | 884 | **Import-Zentrale**: Dateiauswahl, Batch-Verarbeitung und Vorschau. |
| `LlamaCppInferenceEngine.kt` | 858 | **Lokale Inferenz-Engine**: JNI/C++ Wrapper & Prozesssteuerung. |
| `PendingRuleSuggestionDialog.kt` | 822 | **Regelvorschlags-Dialog**: KI-basierte Regelableitung mit Quality-Gatekeeper-Abgleich. |
| `DocumentStorageService.kt` | 783 | **Storage-Service**: Lokale Dateioperationen, PDF-Erzeugung und Verschlüsselung. |
| `DebugDataSeeder.kt` | 773 | **Testdaten-Generator**: Generiert Beispieldokumente für Debug-Zwecke. |
| `FirstStepsTourModal.kt` | 734 | **Onboarding-Tour**: Interaktiver Einführungsdialog. |
| `AppStartSplashAnimation.kt` | 696 | **Splashscreen**: Startanimation und Initialisierungsfortschritt. |
| `AiProviderConfigPane.kt` | 682 | **KI-Provider Konfiguration**: In `ui/views/rules/` bereits modularisiert. |
| `DeveloperSettingsTab.kt` | 676 | **Entwickler-Optionen**: Diagnostics, DB-Export, Log-Viewer. |
| `LlmToolExecutor.kt` | 600 | **Tool-Calling Executor**: Ausführung von Werkzeugen durch das LLM. |
| `P2pSyncEngine.kt` | 592 | **Sync-Protokoll**: P2P Synchronisationslogik. |
| `BankStatementImportDialog.kt` | 590 | **Kontoauszug-Import**: CAMT/CSV Parser und Mapping. |
| `QualityGatekeeper.kt` | 590 | **Quality Gatekeeper**: Deterministische Verifikation von KI-Metadaten. |
| `BatchInboxView.kt` | 577 | **Posteingang Stapelverarbeitung**: Inbox für unkategorisierte Dokumente. |
| `StorageStep.kt` | 575 | **Setup-Wizard Speicherort**: Auswahl Ordnerstrategie & Speicherpfad. |
| `RuleListAndEditorPane.kt` | 562 | **Regelliste & Editor**: In `ui/views/rules/` bereits modularisiert. |
| `TemplateSelectModal.kt` | 532 | **Vorlagenauswahl**: Modal für Dokumentvorlagen. |
| `ImportDialogs.kt` | 521 | **Import-Dialoge**: Detaildialoge für Dateiimporte. |

---

## 🔍 Reale Bestandsaufnahme der bisherigen Refactorings

1. **Paket 1 (`SettingsView.kt`)**: ✅ Erfolgreich auf 81 Zeilen reduziert; Sub-Tabs liegen sauber in `ui/views/settings/`.
2. **Paket 2 (`DocAnizerViewModel.kt`)**: 🟡 Teilweise ausgelagert (`DmsManager`, `FinanceManager`, `SyncManager`), aber mit 2.868 Zeilen weiterhin sehr groß.
3. **Paket 3 (`SetupWizardDialog.kt`)**: ✅ Auf 320 Zeilen geschrumpft; Schritte in `ui/views/wizard/` ausgelagert.
4. **Paket 4 (`LlmService.kt`)**: 🟡 Tools und Prompts wurden ausgelagert, Service umfasst dennoch 1.947 Zeilen.
5. **Paket 5 (`Audit-Logger`)**: ✅ `AppAuditLogger.kt` aktiv integriert.
6. **Paket 6 (`DmsExplorerView.kt`)**: ⚠️ **Achtung**: `DmsExplorerView.kt` ist nach dem Versions-Rollback wieder bei 3.342 Zeilen. Die Dateien in `ui/views/dms/` existieren zwar parallel, sind aber in der Hauptansicht noch nicht eingebunden.
7. **Paket 7 (`DashboardView.kt`)**: 🟡 Standard-Widgets existieren in `ui/views/dashboard/`, Hauptansicht ist aber noch 1.791 Zeilen groß.
8. **Paket 8 (`DashboardCustomWidgetsUi.kt`)**: ✅ Wurde bereits erfolgreich auf 154 Zeilen reduziert; Sub-Komponenten liegen in `ui/views/dashboard/custom/`.
9. **Paket 9 (`RulesAndAiSettingsView.kt`)**: 🟡 Die Sub-Dateien in `ui/views/rules/` (`AiProviderConfigPane.kt`, `RuleListAndEditorPane.kt`, `RuleTestingDialog.kt`) existieren bereits und sind voll funktionsfähig. Die alte Ursprungsdatei `RulesAndAiSettingsView.kt` (1.632 Zeilen) existiert noch parallel als Relikt.

---

## 🛠️ Optimierte, absolut regressionssichere Prompts für anstehende Refactorings

### 📋 Prompt für Paket A: Bereinigung & Verschlankung `RulesAndAiSettingsView.kt`
```text
Analysiere die Datei 'app/src/main/java/com/example/ui/views/RulesAndAiSettingsView.kt' (1.632 Zeilen) und den Ordner 'app/src/main/java/com/example/ui/views/rules/'.

Hintergrund:
Die Modularisierung in 'RuleListAndEditorPane.kt', 'AiProviderConfigPane.kt' und 'RuleTestingDialog.kt' wurde bereits durchgeführt. 'SettingsView.kt' referenziert die Komponenten bereits direkt aus dem 'rules'-Package.

Aufgabe (STRIKT 1:1, KEINE DESIGN-ÄNDERUNGEN):
1. Öffne 'app/src/main/java/com/example/ui/views/RulesAndAiSettingsView.kt'.
2. Entferne die redundanten, duplizierten Implementierungen, die bereits in 'com.example.ui.views.rules.*' existieren.
3. Reduziere 'RulesAndAiSettingsView.kt' auf eine minimale Schnittstelle / Facade, die bei Bedarf auf die ausgelagerten Klassen weiterleitet, oder aktualisiere die einzige verbliebene Referenz in 'ModuleSettingsTab.kt'.
4. Prüfe, dass kein einziger Aufruf verloren geht und keine UI-Unterschiede entstehen.
5. Führe 'compile_applet' und 'gradle :app:testDebugUnitTest' aus.
```

---

### 📋 Prompt für Paket B: Schutzkontrolliertes Refactoring `ScannerView.kt` (Teil 1: Kamera-Preview & Shutter)
```text
Analysiere 'app/src/main/java/com/example/ui/views/ScannerView.kt' (1.456 Zeilen).

WICHTIGE REGELN:
- ABSOLUTES DESIGN-STILLSTAND-GEBOT: Übertrage Code 1:1 via Cut & Paste.
- Keine Farb- oder Padding-Korrekturen, keine "Optimierungen" des UI-Trees!
- Behalte alle Modifier.testTag(...) exakt bei.

Aufgabe:
1. Erstelle den Ordner 'app/src/main/java/com/example/ui/views/scanner/'.
2. Schneide ausschließlich den Live-Kamerabereich und Shutter-Button (CameraX Preview, Flash-Toggle, Shutter-Auslöser, Bildformat-Wahl) aus 'ScannerView.kt' heraus und platziere ihn 1:1 in:
   'app/src/main/java/com/example/ui/views/scanner/ScannerCameraPreviewPane.kt'.
3. Übergebe alle benötigten Lambdas und Parameter unverändert als Argumente an das neue Composable.
4. Binde 'ScannerCameraPreviewPane' in 'ScannerView.kt' ein.
5. Führe 'compile_applet' und 'gradle :app:testDebugUnitTest' aus.
```

---

### 📋 Prompt für Paket C: Schutzkontrolliertes Refactoring `ScannerView.kt` (Teil 2: Kantenerkennung & Zuschnitt)
```text
Analysiere 'app/src/main/java/com/example/ui/views/ScannerView.kt'.

WICHTIGE REGELN:
- ABSOLUTES DESIGN-STILLSTAND-GEBOT: 1:1 Übertragung aller Canvas-Operationen, Lupenfunktionen und Touch-Listener.
- Ändere weder Farben, Transparenzen noch Touch-Slop-Werte.

Aufgabe:
1. Schneide den manuellen Zuschnitt- und Edge-Detection-Dialog bzw. Overlay (Magnifier-Lupe, Eckpunkte-Verschiebung, Perspektiv-Korrektur) aus 'ScannerView.kt' heraus.
2. Platziere ihn 1:1 in:
   'app/src/main/java/com/example/ui/views/scanner/ScannerCropOverlayPane.kt'.
3. Binde 'ScannerCropOverlayPane' in 'ScannerView.kt' ein.
4. Führe 'compile_applet' und 'gradle :app:testDebugUnitTest' aus.
```

---

### 📋 Prompt für Paket D: Schutzkontrolliertes Refactoring `DmsExplorerView.kt` (Modulweise Anbindung)
```text
Analysiere 'app/src/main/java/com/example/ui/views/DmsExplorerView.kt' (3.342 Zeilen) und die bereits vorhandenen Teil-Dateien in 'app/src/main/java/com/example/ui/views/dms/'.

WICHTIGE REGELN:
- Die Hauptansicht des "Dokumenten Tresor" ist der sensibelste Bereich der App.
- ABSOLUTES DESIGN-STILLSTAND-GEBOT: Keine visuellen Änderungen, keine veränderten Listen-Elemente, keine neuen Icons!

Aufgabe:
1. Vergleiche die bestehende Implementierung von 'DmsFolderNavPane.kt' (Ordner-Navigationsleiste / Baum) mit dem entsprechenden Block in 'DmsExplorerView.kt'.
2. Ersetze diesen Block in 'DmsExplorerView.kt' schrittweise durch 'DmsFolderNavPane', ohne dass optisch auch nur 1 Pixel Differenz entsteht.
3. Wiederhole das gleiche Vorgehen für 'DmsFilterBar.kt' (Filter- & Suchleiste).
4. Verifiziere das Ergebnis unmittelbar mit 'compile_applet' und 'gradle :app:testDebugUnitTest'.
```

---

### 📋 Prompt für Paket E: Refactoring `MainScreen.kt` (Navigation & Drawer-Auslagerung)
```text
Analysiere 'app/src/main/java/com/example/ui/views/MainScreen.kt' (1.194 Zeilen).

WICHTIGE REGELN:
- ABSOLUTES DESIGN-STILLSTAND-GEBOT: Alle Icons, Lila-Buttons, Farben und Navigations-Routen müssen exakt erhalten bleiben.
- Das Symbol für den Local AI Agent muss unangetastet bleiben.

Aufgabe:
1. Erstelle den Ordner 'app/src/main/java/com/example/ui/views/navigation/'.
2. Schneide den 'ModalNavigationDrawer'-Inhalt (Seitenmenü mit Menüpunkten, Nutzerprofil, Schnellzugriffen) 1:1 heraus nach:
   'app/src/main/java/com/example/ui/views/navigation/AppNavigationDrawerContent.kt'.
3. Schneide die 'NavigationBar' (untere Menüleiste inkl. Floating Action Buttons) 1:1 heraus nach:
   'app/src/main/java/com/example/ui/views/navigation/AppBottomBarAndFab.kt'.
4. Binde beide Komponenten in 'MainScreen.kt' ein.
5. Führe 'compile_applet' und 'gradle :app:testDebugUnitTest' aus.
```

---

### 📋 Prompt für Paket F: Refactoring `AiDocumentChatDrawer.kt`
```text
Analysiere 'app/src/main/java/com/example/ui/components/AiDocumentChatDrawer.kt' (994 Zeilen).

WICHTIGE REGELN:
- Chat-Bubbles, Farben, Markdown-Formatierungen und Eingabefeld 1:1 übernehmen.

Aufgabe:
1. Erstelle den Ordner 'app/src/main/java/com/example/ui/components/chat/'.
2. Extrahiere die Komponenten in dedizierte Dateien:
   - 'ChatMessageBubble.kt': Rendering einzelner User- und KI-Nachrichten inkl. Kopier-Button und Quellen-Chips.
   - 'ChatInputBar.kt': Texteingabezeile, Attachment-Button, Audio-Recorder-Button und Sende-Button.
   - 'ChatContextPreviewHeader.kt': Vorschau des aktuell referenzierten Dokuments / Kontextes.
3. Behalte 'AiDocumentChatDrawer.kt' als Haupt-Drawer-Container bei.
4. Führe 'compile_applet' und 'gradle :app:testDebugUnitTest' aus.
```
