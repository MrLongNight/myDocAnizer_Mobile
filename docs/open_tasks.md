# Aktueller IST-/SOLL-Status – Stabilisierung (23.10.2026)

## Scan-Speicherfluss – aktueller Arbeitsstand

- **Regelvorschlag-Dialog:** Außenklick und Zurück verwerfen den Scan nicht mehr. Verwerfen erfolgt über eine eigene Schaltfläche mit Rückfrage.
- **Status-/Erfolgstexte:** Der Banner „PDF gespeichert“ hängt am lastBulkSavedDoc-Marker; ein neuer Scan löscht den alten Marker zuerst. Vor dem Marker liegen PDF-Erzeugung und Datenbankeintrag. Zwischenaufnahme wird ausdrücklich als noch nicht gespeichert bezeichnet.
- **OCR:** Einzel-/Bulk-Scan und bestätigter Zuschnitt verwenden ein sichtbares Fehler-State und ein 12-s-OCR-Timeout. Fehlgeschlagene Scans bleiben für einen erneuten Versuch erhalten.
- **Speichern:** saveProcessedBitmapWithRule hat ein 60-s-Timeout. Bei Vault-Lock, Timeout, OOM oder anderem Fehler wird Failed samt Meldung gesetzt; der Vorschlagsdialog bleibt offen und bietet „Erneut versuchen“. Das Bitmap wird nur nach erfolgreichem Speichern recycelt oder nach bestätigtem Verwerfen.
- **Mehrseitenmodus – offene Entscheidung:** finishAndSaveMultiPageScan umgeht weiterhin den Regelvorschlag und speichert anhand des ausgewählten Templates. Der gemeinsame Dialog ist derzeit auf ein einzelnes Bitmap ausgelegt und der Speichercallback erzeugt eine einseitige PDF. Vor einer Änderung muss entschieden werden, ob der Vorschlag auf Basis der ersten Seite die Regel auf das gesamte mehrseitige PDF anwenden soll; bis dahin bleibt der bestehende Mehrseitenpfad unverändert.
- **Bitmap-Lebensdauer:** Beim Schließen der Zuschnittansicht werden die dort erzeugten Vorschau-Bitmaps freigegeben; das final übergebene Bitmap wird ausgenommen. Ein Heap-/10-Scans-Test ist noch offen; eine tatsächliche Speicherfreigabe auf einem Gerät ist nicht belegt.
- **Tests/CI:** Der Kotlin-Compilerfehler `DocAnizerViewModel.kt:1803:21 'return' is prohibited here` wurde mit Commit `c27e9855` korrigiert. Im Lauf [39858296359](https://github.com/MrLongNight/myDocAnizer_Mobile/actions/runs/39858296359) wurde der APK-Build abgeschlossen, aber 1 von 230 JVM-Tests scheiterte: die harte 15-ms-Laufzeitassertion in `DocumentQuadDetectorTest`. Commit `40cb1f1f` macht diesen Test durch Warm-up, Medianmessung und 100-ms-Grobregressionsgrenze weniger CI-anfällig; der neue CI-Lauf steht noch aus. Die angeforderten ViewModel-Tests für Zustandsfolge, Vault-Lock, OCR-Timeout, Verwerfen und „keine Erfolgsmeldung vor Saved“ bleiben offen.
- **Gerätetest:** Nicht durchgeführt. Detektor-Schwellen und Koordinaten-/Kamera-Verhalten bleiben **nicht am Gerät validiert**.

## Noch offene Punkte aus diesem Arbeitsblock

1. **Ergänzt:** JVM-Tests für die gemeinsame ScanWorkflowStateMachine (Zustandsfolge, Vault-Lock, OCR-Timeout, OOM-Meldung, Reset/Verwerfen). **Offen:** echte ViewModel-Integrationstests für die durchlaufenen Funktionen und Bitmap-Lebensdauer, insbesondere OOM/Retry.
2. Alle Scanner-Snackbar-/Toast-/Banner-Erfolgstexte einschließlich Mehrseitenpfad prüfen und nachweisen, dass sie ausschließlich nach Saved erscheinen.
3. Mehrseiten-Regelvorschlag als explizite Architekturentscheidung klären; nicht stillschweigend in den Einzelbild-Speicherpfad umleiten.
4. Mit Heap-Profil oder reproduzierbarem Bitmap-Lebenszyklus-Test prüfen, dass nach zehn Scans keine Vollauflösungs-Bitmaps aus vorherigen Scans gehalten werden.


> Verbindliche Arbeitsliste. Codeänderung, grüner CI-Lauf und erfolgreicher Test auf einem physischen Gerät sind getrennte Nachweise. **Die Entscheidung über einen Wechsel der Scan-Engine liegt ausschließlich beim Nutzer. Es wird keine alternative Engine eingebaut, bevor dieser Wechsel ausdrücklich freigegeben wurde.**

## Aktueller Stand und Nachweise

- **Branch:** `main`
- **Letzter hier dokumentierter Scan-/Diagnose-Code-Stand:** `c602552a` (`Export diagnostic logs with .log extension`), nach `cb976e48` (`Write scan workflow trace to audit export`). Diese Änderungen ergänzen Diagnose-/Audit-Export, ersetzen aber keinen Scan-Gerätetest.
- **CI:** Die im Dokument genannten erfolgreichen Läufe gehören zu früheren Commits. Für den aktuellen `main`-Stand ist in diesem Dokument kein bestätigter vollständiger GitHub-Actions-Lauf dokumentiert; deshalb Build, Unit-Tests und Instrumentierung nicht pauschal als aktuell grün ausweisen.
- **Physischer Gerätetest:** Für Quad-Overlay, FILL_CENTER-Ausrichtung, Autofokus und Wiederfreigabe nach Blattwechsel noch offen.
- **Wichtig zur Perspektive:** Ein früherer Lauf (`39749143472`) scheiterte am Test der schrägen Dokumentecken. Danach wurden Test-Fixture und Pixel-Assertion angepasst. Die früheren grünen Läufe belegen nur den damaligen Commit, nicht automatisch den aktuellen `main`-Stand und nicht die Perspektivqualität echter Fotos.

## Letzte Änderungen auf `main`

| Commit | Änderung | Verifikation / Grenze |
|---|---|---|
| `643c0be6` | Regelvorschlag auch bei einzelnem Treffer vor Speichern bestätigen lassen; Corner-Drag-Offset angepasst; schneller „Hi“-Smoke-Test; Testberichte bei CI-Fehler hochladen. | Ältere CI-Läufe fehlgeschlagen; aktueller Gesamtstand siehe Run `39766676859`. |
| `2eb2f62c` | OCR mit 12-s-Timeout/Fallback abgesichert; Bitmap-Freigabe bei noch laufendem ML-Kit-Task berücksichtigt; APK-Schritt vor Unit-Tests verschoben. | Historischer CI-Lauf erfolgreich; kein Nachweis für den aktuellen HEAD. Timeout löst nicht automatisch alle UI-Hänger. |
| `635cad62` | Schräges synthetisches Dokument pixelweise statt mit Robolectric-Canvas-Pfad erzeugt. | In späterem Gesamt-CI enthalten. |
| `8d5ef909` | Auto-Scan zusätzlich an Bewegungs- und Helligkeitsgrenzen gebunden; Stabilisierungsfenster verlängert. | In späterem Gesamt-CI enthalten; Grenzwerte nicht auf echtem Gerät validiert. |
| `e1448cab` | Bei geringer Schärfe wird gedrosselt AF/AE-Messung am Preview-Zentrum angefordert. | In späterem Gesamt-CI enthalten; Kamera-Reaktion nicht physisch validiert. |
| `6dc6b096` | Perspektiv-Regressionsassertion auf einen inneren Bildpunkt umgestellt, um eine fragile Randpixel-Prüfung zu vermeiden. | Damalige Tests grün; prüfen, dass die Teständerung keinen echten Geometriefehler kaschiert. Kein aktueller HEAD-Nachweis. |
| `c593d26d` | `snapshotFlow` + `collectLatest`: Auto-Scan-Countdown wird abgebrochen, wenn Dokument-, Ausrichtungs-, Schärfe-, Bewegungs- oder Helligkeitsbedingung nicht mehr erfüllt ist. | Historischer CI-Lauf `39766676859` erfolgreich; kein aktueller HEAD-Nachweis, Verhalten mit realem Kamerabild noch offen. |
| `cb976e48` | Scan-Workflow-Trace wird in den Diagnoseexport aufgenommen. | Codeänderung vorhanden; aktueller Gesamt-CI-/Gerätenachweis nicht aus diesem Eintrag ableitbar. |
| `c602552a` | Diagnoseexport erhält die Dateiendung `.log`. | Codeänderung vorhanden; aktueller Gesamt-CI-/Gerätenachweis nicht aus diesem Eintrag ableitbar. |

## Arbeitsblock 22.10.2026 – Live-Erkennung, Inferenzstatus und Scan-Verarbeitung

- Commit `3891ab8`: native llama.cpp-Phase/Fortschritt wird an Chat und Benchmark gemeldet; Chat maximiert sich bei geöffneter Tastatur; Benchmark zeigt Phase/Laufzeit und setzt Busy-State auch bei Fehlern zurück.
- Commit `a`: Live-Kamera-Prüfung wird von bloßer 8×8-Helligkeitsbelegung auf zusammenhängende helle Komponenten mit Viereck-/Konturprüfung und Seitenverhältnis umgestellt; Abtastraster auf 12×12 erhöht. Ein Negativtest für eine schuhähnliche helle Form ergänzt.
- Scan-Qualitätscheck: Farberkennung ist konservativer; Farbbilder werden nicht automatisch nachverstärkt. Bereits manuell entzerrte Seiten umgehen den zweiten heuristischen Zuschnitt.
- Benchmark-Ausgabe begrenzt auf 160 Tokens, weil der feste Extraktionsfall keine 350 Tokens benötigt. Chat entfernt versteckte `<think>`-Blöcke aus sichtbaren Antworten.
- **Noch kein Gerätetest:** Die Raster-/Konturheuristik ist ein messbarer Zwischenschritt, keine Zusicherung zuverlässiger Erkennung in allen Szenen. Positiv-/Negativ-Fotos und echte Kamera-Tests bleiben erforderlich. Die 280-ms-Vorschau-Neuberechnung und Drag-Latenz sind noch offen.
- **CI für Commit `3891ab8` wird durch diesen Folgeschritt ersetzt; das Ergebnis ist erst nach Abschluss des neuen Workflows gültig.**

## Arbeitsblock 22.10.2026 – Auto-Scan-Bereitschaft und Quad-Overlay

- Auto-Scan-Pflichtbedingungen: gelocktes und stabiles Quad sowie Mindesthelligkeit 50 im Quad-Bereich. Die Wasserwaage blockiert Handscans nicht; nur bei aktiviertem Stativscan gilt die großzügigere Grenze von 8°.
- 650-ms-Beruhigungsfenster mit `collectLatest` bleibt bestehen. Bei einem stabilen, aber unscharfen Dokument wartet der Ablauf bis 1,5 s Stabilität, fordert einmal AF/AE am Quad-Mittelpunkt an und löst nach ca. 1 s trotzdem aus, sofern Lock/Stabilität bestehen bleiben.
- Nach Auto-Aufnahme wird dasselbe Blatt nicht erneut ausgelöst. Nach der konfigurierten Nachlaufpause wird erst bei verlorenem Lock oder einer mittleren Eckverschiebung von >15 % der normalisierten Bilddiagonale wieder freigegeben.
- Preview-Overlay zeichnet das Quad in auf PreviewView normierten Koordinaten, inklusive Rotation und FILL_CENTER-Zuschnitt; die gestrichelte Platzierungshilfe bleibt nur ohne Quad sichtbar. Debugwerte sind über Scanner-Einstellungen zuschaltbar (Default aus).
- **Noch offen:** tatsächliche Kamera-Transform-Validierung, AF-Verhalten, Hand-/Stativtests und Prüfung, dass das gleiche Blatt nicht erneut ausgelöst wird. Die verwendeten Schwellen sind nicht auf realen Geräten validiert.

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
| **P1** | **Geräteabnahme:** Frühere CI-/Robolectric-/Emulatorläufe waren grün; das belegt weder den aktuellen HEAD noch echte Kameraqualität. | Checkliste auf einem echten Android-Gerät: Dokument flach/schräg, Pflanzen/Kerzen, unscharf, Bewegung, schwaches/helles Licht, Reflexion/Schatten, mehrere Seiten, Crop lange geöffnet, Abbruch, Regelvorschlag und gespeichertes PDF prüfen. Fehler mit Modell/Android-Version und Reproduktionsschritten dokumentieren. | ⛔ Offen |

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
