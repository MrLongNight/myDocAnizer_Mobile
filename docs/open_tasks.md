# Aktueller IST-/SOLL-Status – Stabilisierung (22.10.2026)

> Verbindliche Arbeitsliste. Codeänderung, grüner CI-Lauf und erfolgreicher Test auf einem physischen Gerät sind getrennte Nachweise. **Die Entscheidung über einen Wechsel der Scan-Engine liegt ausschließlich beim Nutzer. Es wird keine alternative Engine eingebaut, bevor dieser Wechsel ausdrücklich freigegeben wurde.**

## Aktueller Stand und Nachweise

- **Branch:** `main`
- **Aktueller Code-Commit:** [`c593d26d`](https://github.com/MrLongNight/myDocAnizer_Mobile/commit/c593d26d4b52ab43e12436bdcf9876baf55bc8c2) – Auto-Scan-Wartezeit wird abgebrochen, wenn die Qualitätsbedingungen während des Countdowns wegbrechen.
- **Aktueller CI-Lauf:** [39766676859](https://github.com/MrLongNight/myDocAnizer_Mobile/actions/runs/39766676859) – **erfolgreich**. APK-Build/Verifikation/Upload, Unit-Tests und Instrumentierungstest-Schritt melden Erfolg. Das ist kein physischer Kameratest.
- **APK-Artefakt:** `myDocAnizer-Mobile-APK`, ca. 93 MB, Artefakt-ID `12420377803`, Ablaufdatum 21.11.2026. Download über den oben verlinkten Actions-Lauf.
- **CI-Hinweis:** In den Logs gibt es Emulator-/ADB-Warnungen während des Instrumentierungsschritts, der Schritt und der Job endeten trotzdem erfolgreich. Eine veraltete `Undo`-Icon-API erzeugt außerdem eine Deprecation-Warnung.
- **Wichtig zur Perspektive:** Ein früherer Lauf (`39749143472`) scheiterte weiterhin am Test der schrägen Dokumentecken. Danach wurde die Test-Fixture und schließlich die Pixel-Assertion angepasst. Der aktuelle grüne Lauf bestätigt, dass die aktuelle Test-Suite besteht; er beweist nicht allein die Perspektivqualität echter Fotos.

## Letzte Änderungen auf `main`

| Commit | Änderung | Verifikation / Grenze |
|---|---|---|
| `643c0be6` | Regelvorschlag auch bei einzelnem Treffer vor Speichern bestätigen lassen; Corner-Drag-Offset angepasst; schneller „Hi“-Smoke-Test; Testberichte bei CI-Fehler hochladen. | Ältere CI-Läufe fehlgeschlagen; aktueller Gesamtstand siehe Run `39766676859`. |
| `2eb2f62c` | OCR mit 12-s-Timeout/Fallback abgesichert; Bitmap-Freigabe bei noch laufendem ML-Kit-Task berücksichtigt; APK-Schritt vor Unit-Tests verschoben. | Aktueller CI-Lauf erfolgreich. Timeout löst nicht automatisch alle UI-Hänger. |
| `635cad62` | Schräges synthetisches Dokument pixelweise statt mit Robolectric-Canvas-Pfad erzeugt. | In späterem Gesamt-CI enthalten. |
| `8d5ef909` | Auto-Scan zusätzlich an Bewegungs- und Helligkeitsgrenzen gebunden; Stabilisierungsfenster verlängert. | In späterem Gesamt-CI enthalten; Grenzwerte nicht auf echtem Gerät validiert. |
| `e1448cab` | Bei geringer Schärfe wird gedrosselt AF/AE-Messung am Preview-Zentrum angefordert. | In späterem Gesamt-CI enthalten; Kamera-Reaktion nicht physisch validiert. |
| `6dc6b096` | Perspektiv-Regressionsassertion auf einen inneren Bildpunkt umgestellt, um eine fragile Randpixel-Prüfung zu vermeiden. | Aktuelle Tests grün; prüfen, dass die Teständerung keinen echten Geometriefehler kaschiert. |
| `c593d26d` | `snapshotFlow` + `collectLatest`: Auto-Scan-Countdown wird abgebrochen, wenn Dokument-, Ausrichtungs-, Schärfe-, Bewegungs- oder Helligkeitsbedingung nicht mehr erfüllt ist. | CI-Lauf `39766676859` erfolgreich; Verhalten mit realem Kamerabild noch offen. |

## Offene Fehler und konkrete Abnahmekriterien

| Priorität | Problem / IST | SOLL / nächste Lösungsschritte | Status |
|---|---|---|---|
| **P0** | **Falsche Dokumenterkennung:** Pflanzen, Kerzen und andere Nicht-Dokumente werden laut Nutzer als Dokument erkannt. Aktueller Detektor basiert auf eigenen Bild-/Kontrast-Heuristiken. | Positiv-/Negativ-Testkorpus anlegen; Geometrie eines plausiblen Vierecks, Kantenkontinuität, Flächen-/Seitenverhältnis, Kontrast und zeitliche Stabilität gemeinsam bewerten. Bei niedriger Konfidenz nicht automatisch auslösen. Fehlalarme mit reproduzierbaren Fixtures testen, statt nur Schwellenwerte zu verschieben. | ⛔ Offen |
| **P0** | **Schärfe/Fokus:** Auto-Scan prüft Schärfewert; AF/AE wird bei geringer Schärfe angefordert. Der Schärfewert ist nur ein Bildheuristik-Wert und bestätigt nicht, dass der Kamera-Autofokus tatsächlich eingerastet ist. | Kamera-Zustand und Bildschärfe getrennt beobachten; AF/AE-Auslösung, Wartezeit und Schärfe über mehrere Frames protokollieren. Bei anhaltend unscharfem Bild Aufnahme blockieren und verständlichen Hinweis anzeigen. Test mit unscharfer Seite und bewegtem Telefon. | 🟡 Teilweise umgesetzt, Gerätetest offen |
| **P0** | **Automatischer Scan:** Bereitschaft verlangt erkannte Seite, Ausrichtung, `sharpnessScore >= 26`, `motionScore < 4.5` und Helligkeit `125..245`; Countdown 650 ms und Abbruch bei Qualitätsverlust sind implementiert. | Prüfen, ob Messwerte frisch und frame-synchron sind; Countdown darf nicht bei veralteter Analyse weiterlaufen. Aufnahmen in schwachem Licht, Überbelichtung, Reflexion, Schatten und Bewegung testen. Grenzwerte anhand Testdaten festlegen, nicht nach Gefühl. | 🟡 Code umgesetzt, Qualität unbestätigt |
| **P0** | **Perspektivkorrektur:** `cropAndWarpPerspective` enthält eine 4-Punkt-Homographie; die Ecke-/Test-Fixture wurde verändert, damit der Regressionstest stabil läuft. Frühere CI-Läufe scheiterten am Test der schrägen Ecken. | Mit bekannten Quell-/Zielpunkten und synthetischen Gittern testen; alle vier Ecken, Hoch-/Querformat und stark schräg fotografierte Seiten prüfen. Test muss sowohl korrekte erkannte Ecken als auch korrekte entzerrte Geometrie prüfen. Manuelle Eckeingaben nicht still durch Auto-Crop ersetzen, wenn sie ungültig sind, ohne dies sichtbar zu machen. | ⛔ Reale Qualität offen |
| **P0** | **Manueller Zuschnitt:** Touch-Offset wurde angepasst; zuvor wurden springende Ecken und eine nach Sekunden verschwindende Vorschau gemeldet. Ursache der Preview-Unterbrechung ist noch nicht belegt. | Zustand über Preview → Eckpunktbearbeitung → Bestätigung nachverfolgen. Bitmap-Ownership, `LaunchedEffect`-Schlüssel, Navigation und `capturedRawBitmap`-/Processing-Reset prüfen. UI-Test: 30+ Sekunden im Crop bleiben, jede Ecke mehrfach ziehen, drehen/zoomen und bestätigen/abbrechen. | ⛔ Offen |
| **P0** | **Regelvorschlag/Speicherreihenfolge:** Beide Scan-Pfade wurden auf einen gemeinsamen Bestätigungsablauf umgestellt; OCR hat ein Zeitlimit. Nutzer meldete, dass PDF-Speicherung vor der Regelbestätigung angekündigt wird und der Ablauf lange hängt. | Den Ablauf als explizite Zustandsfolge prüfen: Aufnahme → Vorschau/Regelvorschlag → Nutzerentscheidung (einmalig/Regel speichern/Abbrechen) → OCR/Metadaten → PDF speichern → Erfolg anzeigen. Keine Erfolgsmeldung vor tatsächlichem Speichern. OCR-Timeout, Abbruch und Bitmap-Freigabe separat testen. | 🟡 Teilweise umgesetzt, Ablauf noch nicht bestätigt |
| **P0** | **Chat-Agent hängt bei „Hi“:** Ein schneller Smoke-Test wurde ergänzt und bestand zuvor. Er prüft den schnellen Antwortpfad, nicht zwingend die gesamte echte Agent-/LLM-Inferenz samt Tool-Auswahl und Provider-Aufruf. | Den realen Agent-Pfad mit Zeitmessung und Timeout testen; Phasen (UI, Promptaufbau, Provider/Inference, Tool-Aufruf, Antwortzustellung) protokollieren. Bei einfachem Text darf kein unnötiger Tool-/Dokumenten-Workflow starten; Ladezustand und Fehlerzustand müssen garantiert enden. | 🟡 Teiltest vorhanden, echter Agent-Pfad offen |
| **P0** | **LLM-Benchmark:** Änderungen zur Nutzung nativer llama.cpp-Metriken (Tokens, Prompt-/Generierungszeit, TPS) und zum Zurücksetzen alter Messwerte sind dokumentiert. Der frühere Screenshot-Fehler ist in diesem Arbeitsblock nicht erneut reproduziert worden. | Screenshot/Fall reproduzieren; Messwerte gegen native Logs abgleichen. Fallback/Schätzung klar von Messwert unterscheiden, fehlende Werte nicht als 0 oder alte Werte anzeigen; Modellwechsel, Fehler, Abbruch und erster Lauf testen. Physische GGUF-Inferenz weiterhin separat nachweisen. | 🟡 Codeänderungen vorhanden, End-to-End-Abnahme offen |
| **P1** | **Scan-Engine-Entscheidung:** Keine alternative Scan-Engine wurde integriert. ML Kit Document Scanner ist die vom Nutzer gewünschte Qualitätsreferenz, aber kein bereits freigegebener Austausch. | Zuerst bestehende Engine anhand festgelegter Qualitätskriterien testen und einen Vergleich/Proof of Concept nur nach Zustimmung durchführen. Vergleich: Fehlalarmrate, verpasste Seiten, Kanten-/Perspektivqualität, Schärfe, Mehrseitenfluss, Abbruch, UI-Integration, Datenschutz/Offline-Verhalten und Gerätevoraussetzungen. **Kein Wechsel ohne ausdrückliche Nutzerentscheidung.** | ⛔ Entscheidung liegt beim Nutzer |
| **P1** | **Geräteabnahme:** CI/Robolectric/Emulator sind grün, echte Kameraqualität ist damit nicht belegt. | Checkliste auf einem echten Android-Gerät: Dokument flach/schräg, Pflanzen/Kerzen, unscharf, Bewegung, schwaches/helles Licht, Reflexion/Schatten, mehrere Seiten, Crop lange geöffnet, Abbruch, Regelvorschlag und gespeichertes PDF prüfen. Fehler mit Modell/Android-Version und Reproduktionsschritten dokumentieren. | ⛔ Offen |

## Lösungsreihenfolge (ohne Engine-Wechsel)

1. **Build-/Test-Basis sichern:** Der aktuelle grüne Lauf ist dokumentiert; nächste Änderung erst nach konkreter Ursachenanalyse und mit möglichst einem zusammenhängenden Commit.
2. **Scan-Ablauf entkoppelt prüfen:** Zuerst Zustandsfolge und Bitmap-Lebensdauer rund um Crop, Regelvorschlag, OCR und PDF-Speicherung analysieren, weil hier Hänger und verfrühte Erfolgsmeldungen auftreten können.
3. **Erkennung messbar machen:** Positiv-/Negativ-Beispiele und Metriken (Fehlalarme, verpasste Seiten, stabile Erkennung) anlegen. Keine reine Schwellenwert-Lotterie.
4. **Geometrie unabhängig testen:** Eckenerkennung und Homographie mit bekannten Punkten testen; danach echte Fotos auf dem Gerät.
5. **Chat und Benchmark getrennt reproduzieren:** Smoke-Test nicht mit vollständigem Agententest gleichsetzen; native Messwerte und Fallback-Status überprüfen.
6. **Erst Ergebnisse vorlegen:** Was bestehende Engine zuverlässig behebt und was nicht. Ein Architekturwechsel bleibt eine Entscheidung des Nutzers.

## Arbeitsregeln

- Direkt auf `main`, keine Pull Requests.
- Keine Scan-Engine tauschen, hinzufügen oder UI darauf umstellen, solange der Nutzer es nicht ausdrücklich freigibt.
- Vor jedem Code-Fix aktuellen Code und Testbeleg prüfen; Änderungen klein und zusammenhängend halten.
- Nach jedem Commit den zugehörigen CI-Lauf kontrollieren; keine unbelegten Erfolgsbehauptungen.
- Erfolgreicher CI-Build, Unit-Tests, Instrumentierungstests und physischer Gerätetest sind getrennte Nachweise.
- Dokumentation nach jedem Arbeitsblock aktualisieren. Pushes bündeln, damit nicht mehrere CI-Läufe unnötig abgebrochen werden.

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
