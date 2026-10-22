# Aktueller IST-/SOLL-Status – Stabilisierung (22.10.2026)

> Diese Liste ist die aktuelle Arbeitsliste für die laufende Fehlerbehebung. Codeänderung, grüner CI-Lauf und erfolgreicher Test auf einem physischen Gerät sind getrennte Nachweise.

## Letzte Änderungen auf `main`

| Commit | Änderung | Verifikation |
|---|---|---|
| `643c0be6` | Scan-Regelvorschlag auch bei einzelnem Treffer vor dem Speichern bestätigen lassen; Corner-Drag-Offset stabilisiert; schneller Chat-Smoke-Test ergänzt; CI lädt Testberichte bei Fehler hoch. | CI fehlgeschlagen; siehe unten. |
| `2eb2f62c` | OCR-Callback auf 12 s begrenzt, Bitmap-Lebensdauer bei Timeout abgesichert; APK-Build/-Upload vor Unit-Tests angeordnet. | APK wurde gebaut/verifiziert/hochgeladen; 187/188 Unit-Tests bestanden. |
| `635cad62` | Schräges Dokument-Testbild direkt in Pixeln erzeugen, damit der Test nicht von Robolectric-Canvas-Path-Rasterisierung abhängt. | Lauf durch nachfolgenden Push abgebrochen; neuer Lauf ausstehend. |
| `8d5ef909` | Auto-Scan verlangt zusätzlich geringe Bewegung und plausible Helligkeit; Stabilisierung von 250 auf 650 ms verlängert. | Lauf durch nachfolgenden Push abgebrochen; separat nicht verifiziert. |
| `e1448cab` | Bei weicher Vorschau wird gedrosselt AF/AE-Messung im Bildzentrum angefordert. | CI läuft/läuft erneut; noch kein grüner Nachweis. |

## Offene Fehler und konkrete Abnahmekriterien

| Priorität | Problem / IST | SOLL / nächster Schritt | Status |
|---|---|---|---|
| P0 | Letzter abgeschlossener CI-Lauf `39745388026`: APK-Build und Upload erfolgreich, Unit-Tests 187/188; `ScanDetectionRegressionTest` scheiterte bei der Erwartung schräger Eckpunkte (Zeile 69). | Deterministisches Testbild verifizieren; tatsächlichen Detektorfehler nicht durch Abschwächen der Assertions kaschieren. Neuesten CI-Lauf vollständig abwarten. | ⛔ Offen |
| P0 | Dokumenterkennung produziert laut Nutzer Fehlalarme (u. a. Pflanzen/Kerzen); Autofokus wird nicht zuverlässig berücksichtigt. | Erkennung gegen echte und synthetische positive/negative Beispiele testen; Schärfe, Kanten-/Viereck-Geometrie, Bewegung, Belichtung und Reflexionen prüfen. Keine Aufnahme bei unsicherer Erkennung. | ⛔ Offen |
| P0 | Auto-Scan muss nach stabiler Dokumenterkennung optimale Aufnahmeparameter sicherstellen. | AF/AE anfordern, Fokus-/Belichtungsstabilität und geringe Bewegung über mehrere Frames bestätigen; erst dann auslösen. Aktuelle Heuristiken sind noch nicht auf Gerät validiert. | 🟡 Teilweise umgesetzt |
| P0 | Automatische Perspektivkorrektur funktioniert nicht zuverlässig; manuelle Eckpunktkorrektur springt/ist ungenau. | Echte schiefe Seiten erkennen und entzerren; alle vier Eckpunkte exakt abbilden; Crop-Preview bleibt bis Bestätigung stabil. Tests für Hoch-/Querformat und Perspektive ergänzen. | ⛔ Offen |
| P0 | Nach Scan werden Speichern/PDF und Regelvorschlag nicht zuverlässig sequenziert; Warte-/Hängerproblem gemeldet. | Erst Scan-Vorschau und Regelvorschlag/Bestätigung zeigen, danach speichern; Coroutine-/OCR-Wartezeiten begrenzen; Bitmap-Ownership prüfen. Reproduktions- und Gerätetest offen. | 🟡 Teilweise umgesetzt |
| P0 | Chat-Agent hängt bei einfachem „Hi“; LLM-Benchmark meldet fehlerhafte Werte (Screenshot im früheren Chat). | Chat-Smoke-Test muss schnell und reproduzierbar enden; Benchmark muss echte native Metriken verwenden und Fallback/Schätzwerte klar kennzeichnen. Bestehende LLM-Todos im Produktionsplan bleiben offen. | 🟡 Hi-Smoke-Test bestanden, vollständiger Gerätetest offen |
| P1 | Bibliothekswahl für Scanner noch nicht entschieden. | Googles ML Kit Document Scanner als Referenz/Baseline evaluieren: Aufnahmequalität, automatische Kanten, Perspektivkorrektur, Belichtung/Fokus, Offline-/Datenschutzverhalten, API-/UI-Integration und Lizenz/Abhängigkeiten. Falls bestehender Live-Scanner die Qualitätsabnahme nicht erreicht, Scan-Logik austauschen statt endlos Heuristiken nachzurüsten. | ⛔ Entscheidung nach Vergleich |
| P1 | Echte Kameraqualität kann nicht allein mit Robolectric nachgewiesen werden. | Auf realem Android-Gerät testen: unscharfe Seite, schwaches/zu starkes Licht, Schatten/Reflexionen, schräges Blatt, Pflanzen/Kerzen, Bewegung, mehrere Seiten und Abbruch. | ⛔ Offen |

## Vorgehen / Regeln

- Änderungen direkt auf `main`, keine Pull Requests.
- Erst aktuelle Implementierung und Testresultat prüfen, dann kleine zusammenhängende Änderung.
- Nach jedem Commit CI prüfen; keine Build-/Test-Erfolge behaupten, bevor sie belegt sind.
- Fertige Bibliotheken bevorzugen, wenn sie die Qualitätsanforderung besser erfüllen; keine Abhängigkeit ohne Kompatibilitäts- und Integrationsprüfung hinzufügen.
- Dokumentation nach jedem Arbeitsblock aktualisieren; CI-Minuten sparen und nicht mehrere unnötige Pushes parallel starten.

---

# Offene Refactoring-Aufgaben & Code-Optimierung (myDocAnizer-Mobile)

Dieses Dokument enthält den aktuellen Status der Modularisierung und Dateigrößen in einer einzigen konsolidierten Liste sowie strukturierte Prompts für anstehende Refactoring-Pakete.

---

## 📋 Status der Refactoring-Pakete & Dateigrößen

- **Paket 1 (`SettingsView.kt`)**: ✅ **Erledigt** – Auf 81 Zeilen reduziert (`ui/views/settings/`).
- **Paket 2 (`DocAnizerViewModel.kt`)**: ✅ **Erledigt** – Modularisiert mit Fassaden-Pattern (`DmsManager.kt`, `FinanceManager.kt`, `SyncManager.kt`).
- **Paket 3 (`SetupWizardDialog.kt`)**: ✅ **Erledigt** – Auf 320 Zeilen reduziert (`ui/views/wizard/`).
- **Paket 4 (`LlmService.kt`)**: ✅ **Erledigt** – Modularisiert mit `LlmPrompts.kt`, `LlmToolExecutor.kt` & `LlmHardwareDetector.kt`.
- **Paket 5 (Audit-Logger & Diagnose)**: ✅ **Erledigt** – `AppAuditLogger.kt` mit Log-Leveln und Settings-Integration.
- **Paket 6 (`DmsExplorerView.kt`)**: ✅ **Erledigt** – Von 3.342 auf 1.977 Zeilen reduziert (`ui/views/dms/`: `DmsTrashPane`, `DmsDeadlinesPane`, `DmsCustomFieldsPane`, `DmsFolderNavPane`, `DmsDocumentListView`, `DmsFilterBar`, `DmsBatchActionToolbar`).
- **Paket 7 & 19 (`DashboardView.kt`)**: ✅ **Erledigt** – Von 1.793 auf 742 Zeilen reduziert (`ui/views/dashboard/`: `DashboardKpiWidget`, `DashboardQuickScanWidget`, `DashboardCashTrackerWidget`, `DashboardDeadlinesWidget`, `DashboardQuickNoteWidget`, `DashboardCategoryPieWidget`, `DashboardCustomFieldsWidget`).
- **Paket 8 (`DashboardCustomWidgetsUi.kt`)**: ✅ **Erledigt** – Auf 155 Zeilen reduziert (`ui/views/dashboard/custom/`: `CustomWidgetRenderer`, `CustomWidgetEditorDialog`, `CustomWidgetTypesUi`).
- **Paket 9 (`RulesAndAiSettingsView.kt`)**: ✅ **Erledigt** – Auf 434 Zeilen reduziert (`ui/views/rules/`: `RuleListAndEditorPane`, `AiProviderConfigPane`, `RuleTestingDialog`).
- **Paket 10 (`ScannerView.kt`)**: ✅ **Erledigt** – Auf 593 Zeilen reduziert (`ui/views/scanner/`: `ScannerCameraPreviewPane`, `ScannerCropOverlayPane`, `ScannerPageReviewPane`).
- **Paket 11 (`CloudStep.kt`)**: ✅ **Erledigt** – Von 1.195 auf 365 Zeilen reduziert (`ui/views/wizard/cloud/`: `BackupSafetyScoreBanner`, `BackupTierUsbCard`, `BackupTierDriveCard`, `BackupTierNasCard`, `BackupTierMultiDeviceCard`).
- **Paket 12 (`P2pSyncTab.kt`)**: ✅ **Erledigt** – Von 1.010 auf 139 Zeilen reduziert (`ui/views/p2p/`: `P2pInfoBanner`, `P2pServerStatusCard`, `P2pConflictAndProgressCard`, `P2pPairedDevicesList`, `PairingWizardDialog`).
- **Paket 13 (`MainScreen.kt`)**: ✅ **Erledigt** – Von 1.194 auf 610 Zeilen reduziert (`ui/navigation/`: `MainTopAppBar`, `MainBottomBar`, `FabSpeedDialOverlay`, `ScanImportBottomSheet`, `MainGestureZones`).
- **Paket 14 (`AiDocumentChatDrawer.kt`)**: ✅ **Erledigt** – Von 994 auf 566 Zeilen reduziert (`ui/chat/`: `ChatMessageListPane`, `ChatInputBarPane`, `ChatContextChipsPane`).
- **Paket 15 (`PcCompanionWizardDialog.kt`)**: ✅ **Erledigt** – Von 979 auf 254 Zeilen reduziert (`ui/views/p2p/companion/`: `Step1SelectConnectionType`, `Step2ConfigureConnection`, `Step3ConfigureDestination`, `Step4SummaryAndTest`).
- **Paket 16 (`ReconciliationDialog.kt`)**: ✅ **Erledigt** – Von 898 auf 574 Zeilen reduziert (`ui/views/reconciliation/`: `ReconciliationFilterBarPane`, `ReconciliationMatchCardPane`, `AddCashTransactionDialog`, `AddBankWithdrawalDialog`, `AddPrivateWithdrawalDialog`).
- **Paket 17 (`DocumentDetailModal.kt`)**: ✅ **Erledigt** – Von 896 auf 843 Zeilen reduziert (`ui/views/detail/`: `DocumentMetadataEditorPane`, `DocumentOcrPreviewPane`).
- **Paket 18 (`ImportView.kt`)**: ✅ **Erledigt** – Von 884 auf 558 Zeilen reduziert (`ui/views/import/`: `ImportFilePickerPane`, `ImportMetadataFormPane`, `ImportOcrAndFormatPane`).
- **Paket 20 (Erste-Schritte Tour UI & Skip-Option)**: ✅ **Erledigt** – Interaktive 11-Schritte Tour mit überarbeitetem Schritt-8 Tooltip auf dem AI-Agent Drawer, expliziten Skip-Buttons am oberen Bildschirmrand & im Tooltip-Header.
- **Paket 21 (Zero-Knowledge Security & Bottom Bar Layout)**: ✅ **Erledigt** – Kein Default-Passwort, automatisches Routing bei Wizard-Skip, Sicherheits-Warnbanner, Log-Datei in Entwickler-Einstellungen verschoben & vergrößerte Bottom-Bar Abstände für "Dokumente", "Dashboard" und "InBox".
- **Paket 22 (Hardware LLM Autodownload, Bottom-Sheet Chat, Tab-Crash Guard & Auto-Mirror Fixes)**: ✅ **Erledigt** – Automatischer Download des hardware-empfohlenen KI-Modells bei Wizard-Skip, Bottom-Sheet Chat mit Slide-In (~42% Default-Höhe, Vollbild-Toggle & maximiertem Text-Anzeigebereich), M3 TabRow IndexOutOfBounds-Schutz (`coerceIn` + safe indicator) und Auto-Mirror Fallback ohne Passwort.

---

## 🛠️ Status: Alle 22 geplanten Refactoring-Pakete, UI-Anpassungen & Stabilitäts-Härtungen erfolgreich abgeschlossen!
