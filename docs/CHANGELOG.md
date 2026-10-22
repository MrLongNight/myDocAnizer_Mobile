# Changelog

All notable changes to **myDocAnizer-Mobile** are documented here.

## [Unreleased]

### CI-Korrektur: flakey Laufzeitassertion im Detektor-Test
- Nach dem Kotlin-Compilerfix wurde der Build erfolgreich bis zu den JVM-Tests ausgeführt; 229 von 230 Tests bestanden.
- Der einzige Fehler war die harte 15-ms-Wall-Clock-Assertion für `DocumentQuadDetector` unter Robolectric/Shared CI. Der Test verwendet nun Aufwärm-Runden, Medianmessung und eine großzügige 100-ms-Grenze als Schutz vor groben Performance-Regressionen. 15 ms bleibt Optimierungsziel, aber kein deterministischer Unit-Test-SLA.
- Commit `40cb1f1f`; CI-Ergebnis für diesen Commit steht noch aus.


### CI-Korrektur: Kotlin-Compilerfehler im Mehrseiten-Scan
- Der leere-Seiten-Guard in `finishAndSaveMultiPageScan` lag innerhalb des `withTimeout`-Lambdas und verwendete dort ein unzulässiges `return@launch`. Der Guard steht jetzt vor dem Timeout-Block.
- GitHub Actions Run [39855858835](https://github.com/MrLongNight/myDocAnizer_Mobile/actions/runs/39855858835) zeigte den konkreten Compilerfehler in `DocAnizerViewModel.kt:1803`.
- Der Fix wurde direkt auf `main` committed. Der anschließende CI-Lauf ist noch abzuwarten.


### Scanner: Regelvorschlag, Speicherstatus und Fehlerpfad
- Regelvorschlag-Dialog ist nicht mehr durch Außenklick oder Zurück verwerfbar; „Verwerfen“ verlangt eine explizite Bestätigung.
- Der Scanner unterscheidet Aufnahme, Analyse, Regelentscheidung, Speichern und gespeichertes PDF. Der Erfolgshinweis wird erst über den Marker nach PDF-Erzeugung und Datenbankeintrag angezeigt; beim Start eines neuen Scans wird ein alter Erfolgshinweis zurückgesetzt.
- OCR im Einzel-/Bulk-Pfad und nach bestätigtem Zuschnitt hat ein 12-s-Timeout. Speicheroperationen im Regelpfad haben ein 60-s-Timeout. Fehler (einschließlich Vault gesperrt und OutOfMemoryError) werden als Failed angezeigt und halten den Scan für Retry bzw. explizites Verwerfen vor.
- Beim Schließen der Zuschnittansicht werden die Vorschau-Bitmaps freigegeben; das an den ViewModel-Speicherpfad übergebene Bitmap bleibt erhalten.
- JVM-Tests für die gemeinsame ScanWorkflowStateMachine decken Zustandsfolge, Vault-Lock, OCR-Timeout, OOM-Meldung und Reset/Verwerfen ab. **Noch offen:** echte ViewModel-Integrationstests der Speicherpfade, Prüfung aller Scanner-Erfolgstexte, Heap-/10-Scans-Test und Entscheidung zum Regelvorschlag im Mehrseitenmodus. Der Mehrseitenpfad umgeht den Regelvorschlag weiterhin.
- **Nicht am Gerät validiert:** Detektor-Schwellen, Kamera-/Koordinatenverhalten und die tatsächliche Bitmap-Freigabe unter realer Speichernutzung.


### Auto-Scan-Bereitschaft und Live-Quad-Overlay
- Auto-Scan erfordert weiterhin einen gelockten, stabilen Dokument-Quad; Wasserwaage ist kein allgemeiner Blocker mehr. Im Stativmodus gilt nur eine großzügigere Neigungsschwelle von 8°.
- Starres Luminanzfenster 125–245 entfernt; Mindesthelligkeit im Dokumentbereich ist 50. Schärfe blockiert nicht mehr dauerhaft: bei mindestens 1,5 s stabiler, aber unscharfer Seite wird einmalig AF/AE am Quad-Mittelpunkt angefordert und nach etwa 1 s auch bei weiterhin niedriger Schärfe ausgelöst.
- Die 650-ms-Beruhigungszeit mit `collectLatest` bleibt erhalten; der Ablauf wird bei verlorenem Lock oder instabiler Erkennung abgebrochen.
- Nach Auto-Aufnahme bleibt dasselbe Blatt gesperrt. Wiederfreigabe erfolgt erst nach Lock-Verlust oder einer mittleren Eckverschiebung von mehr als ca. 15 % der normalisierten Bilddiagonale; die eingestellte Nachlaufpause bleibt davor bestehen.
- Scanner-Overlay zeichnet das erkannte Quad statt eines festen Rahmens. Das Quad wird anhand Rotation und FILL_CENTER-Seitenverhältnis in Preview-Koordinaten abgebildet; ohne Quad bleibt nur eine gestrichelte Platzierungshilfe sichtbar.
- Debug-Anzeige für Confidence, Ablehnungsgrund, Schärfe und Bewegung ist über Scanner-Einstellungen zuschaltbar und standardmäßig aus.
- **Die Schwellen und die Koordinatenabbildung sind noch nicht auf realen Geräten validiert.** Reale Kamera-/AF-Tests bleiben offen.


### Live-Dokumenterkennung: Quad-Detektor integriert
- CameraX-Analyzer nutzt jetzt `DocumentQuadDetector` statt 12×12-Helligkeitsraster, `hasCoherentPaperGrid`, Luma-Fenster und 30-Frame-Zähler.
- Analyse auf etwa 9 fps gedrosselt; verkleinerte YUV-Luma/Chroma-Frames werden mit wiederverwendbaren Puffern an den Detektor übergeben.
- Neuer `DocumentTracker`: Lock bei mindestens 5 von 7 Frames mit confidence ≥ 0,55 und Eckbewegung < 3 % der Bilddiagonale; Lock-Verlust erst nach 6 Fehlframes; EMA-Eckenglättung und Stabilitätsprüfung über ca. 500 ms.
- `CameraFrameAnalysis` liefert Quad, Confidence, Stabilität, Ablehnungsgrund sowie Rotation, Quellabmessungen und CropRect für spätere Overlay-Ausrichtung. Schärfe (Laplace) und mittlere Helligkeit werden im Quad-Inneren gemessen, mit Bildmitte als Fallback.
- Autofokus wird bei neuem Lock auf den Quad-Mittelpunkt gelegt und auf maximal einen Versuch je 1,5 Sekunden begrenzt. Tap-to-focus bleibt unverändert.
- Hautähnliche U/V-Werte sind nur ein weicher Confidence-Abzug (Faktor 0,72), kein eigenständiger Hard-Reject; synthetischer Regressionstest für hautähnliche Chroma-Werte auf einer geometrisch klaren Seite ergänzt.
- JVM-Tests decken Lock, Flackern, kurze Aussetzer, Unlock nach 6 Fehlframes und springende Ecken ab.
- **Wichtig: Detektor- und Tracker-Schwellen sind vorläufig und ausdrücklich nicht auf realen Geräten validiert.** FPS und <15-ms-Zielwert müssen auf realer Hardware gemessen werden.


### Automatische Dokumentenerkennung (Detektor-Prototyp)
- Eigenständiger, JVM-testbarer Kotlin-Detektor ohne OpenCV oder neue Runtime-Abhängigkeiten: geglättete Luma, Otsu mit beiden Polaritäten, gefüllte Komponenten, morphologisches Öffnen, konvexe Hülle, Quad-Vereinfachung, Geometrie-/Randkontrastprüfung und optionaler weicher Haut-Chroma-Abzug.
- Neue synthetische `DocumentQuadDetectorTest`-Suite für helle/dunkle/cremefarbene Dokumente, verschiedene Hintergründe, Rotation, Trapez/Perspektive, Textzeilen sowie Kreis, Hand, Fuß, Pflanze, Wand, Rauschen, Randbeschnitt und übergroße Flächen.
- JVM-Laufzeittest misst den aufgewärmten Durchschnitt für 160×120; das 15-ms-Ziel ist ein Richtwert und kann je CI-Host variieren.
- Neue YUV_420_888-Konvertierung berücksichtigt Y/U/V rowStride und pixelStride, reduziert auf maximal 160 px lange Kante und kann Zielpuffer wiederverwenden.
- Dieser Test-/Detektor-Arbeitsschritt ändert keine UI-Dateien. Die Live-Kamera-Integration ist separat zu bewerten und darf nicht als Beleg für zuverlässige Erkennung auf realen Geräten gelten.
- **Wichtig: Alle Schwellen sind vorläufig und ausdrücklich nicht auf realen Geräten validiert.** Die synthetischen JVM-Tests und der Laufzeit-Richtwert werden erst durch den zugehörigen CI-Lauf bestätigt.
 - 2026-10-22 (Scan-/Chat-Stabilisierung – noch nicht vollständig verifiziert)

- Added native inference phase/progress reporting to the chat and benchmark, plus keyboard-aware chat expansion.
- Benchmark token cap reduced from 350 to 160 for the fixed extraction case; visible chat output strips internal `<think>` blocks.
- Live scanner now evaluates connected bright components, inferred quadrilateral containment, and frame-aware aspect ratio instead of only the 8×8 brightness pattern; added a shoe-like negative test.
- Scan processing preserves color source pixels and avoids a second crop after explicit corner warping. Physical-device validation remains pending.
- Chat: zeitbegrenzter Smoke-Test für die einfache Begrüßung ergänzt; der Test bestand im letzten protokollierten Lauf in 0,1 s. Das beweist noch keine zuverlässige physische Geräte-/LLM-Inferenz.
- Scan-Regeln: Vorschlag/Bestätigung wird auch bei einem einzelnen bestehenden Treffer angefordert; endgültiger Speichervorgang soll erst nach Nutzerentscheidung erfolgen. Sequenz und UI auf Gerät noch nachzuprüfen.
- OCR: ML-Kit-Callback mit 12-s-Timeout abgesichert; Timeout-Fallback und Bitmap-Lebensdauer angepasst.
- Auto-Scan: Aufnahmebedingungen zusätzlich um Bewegungs- und Helligkeitsprüfung erweitert; Stabilisierung auf 650 ms verlängert; bei weicher Vorschau wird gedrosselt AF/AE-Messung am Bildzentrum angefordert. Die Schwellenwerte und Kamera-Wirkung sind noch nicht auf realen Geräten validiert.
- Regressionstest: schräges synthetisches Dokument wird direkt als Pixelbild gerastert, um Robolectric-Canvas-Abhängigkeit zu vermeiden.
- CI-Nachweis: Run [39745388026](https://github.com/MrLongNight/myDocAnizer_Mobile/actions/runs/39745388026) baute und veröffentlichte eine Debug-APK, aber nur 187/188 Unit-Tests bestanden; der Perspektivtest scheiterte. Neuere Läufe wurden durch weitere Pushes abgebrochen/neu gestartet; kein grüner Gesamtstatus ist bestätigt.
- Offen: Fehlalarme auf Nicht-Dokumente, automatische Perspektivkorrektur, präziser/manuell stabiler Crop, korrekte Scan-/Regelvorschlag-/Speicherreihenfolge, vollständiger Chat-/Benchmark-Test und physische Gerätetests. Googles ML Kit Document Scanner wird als Qualitäts-Baseline evaluiert; falls der Live-Scanner nicht gleichwertig wird, ist ein Austausch der Scan-Logik ausdrücklich vorgesehen.

## [Unreleased] - 2026-10-18 (Sicherheits-Härtung)
- Security: Entfernt pauschale Android-Geräteübertragungs-Includes für Dateien, Datenbanken und SharedPreferences; sensible Speicherbereiche werden explizit ausgeschlossen.
- Security tests: Erweitert `BackupRulesSecurityTest` um Prüfungen gegen pauschale Includes und für externe/temporäre Speicherbereiche.
- D14: Ergänzt einen PDFBox-basierten Instrumentationstest und dokumentiert, dass Scan-OCR aktuell nicht als Textlayer in die PDF eingebettet wird; Testlauf offen.
- P01: Dokumentiert den Ist-Zustand des P2P-Transports, bestätigt Klartext-HTTP statt TLS und erstellt eine TLS-/Fingerprint-Pinning-ADR; Freigabe vor P02/P03 erforderlich.
- P10: Entfernt den SHA-256-Ersatzschlüssel-Fallback bei ungültigen P2P-AES-Schlüsselstrings; falsche Base64-/Schlüssellängen schlagen geschlossen fehl, Tests ergänzt.

### Tresor-Authentifizierung und externe Intents
- Gemeinsamer persistenter Backoff für Passwort-/TOTP-/Recovery-Versuche; Authentifizierungsaufrufe gegen parallele Versuche serialisiert.
- Allowlist für externe Widget-Navigation und Aktionen; externe Aktionen während unvollständiger Authentifizierung werden verworfen.
- Auto-Mirror ist standardmäßig deaktiviert und erstellt ohne verfügbaren Verschlüsselungsschlüssel keine Klartextkopie.

### Keystore-SecretStore
- Neue versionierte AES-GCM-Grundkomponente für langlebige Secrets mit Android-Keystore-Schlüssel, AAD pro Secret-Namen und fail-closed Verhalten.
- Unit-/Instrumentationstests ergänzt. Build-, Test- und Gerätelauf für diese Änderungen sind noch offen; siehe `docs/security/s06-s10-implementation-report.md`.

## [2.8.0] - 2026-10-15 (UI-Optimierung, Hardware-LLM Autodownload & Stabilität)

### 🤖 Hardware-Empfohlenes LLM Autodownload beim Überspringen des Einrichtungsassistenten
- **Automatischer Modell-Download bei Wizard-Skip:**
  - Wird der Ersteinrichtungs-Assistent (`SetupWizardDialog.kt`) vorzeitig beendet oder übersprungen, ermittelt die App automatisch das über die On-Device Hardware-Erkennung (`detectDeviceHardware()`) empfohlene Modell für das Endgerät (z. B. SmolLM2 360M, Qwen2.5 0.5B / 1.5B oder NuExtract 2B) und startet den Download im Hintergrund.
  - Informativer Toast-Hinweis informiert den Nutzer, dass das empfohlene Modell geladen wird und jederzeit in den Einstellungen ein anderes Modell gewählt werden kann.

### 💬 Schlanker KI-Chat-Bereich & Textanzeige-Maximierung (`AiDocumentChatDrawer.kt`)
- **Bottom-Sheet Design mit Slide-In:**
  - Der KI-Agenten-Chat öffnet sich nun als elegantes Bottom-Sheet vom unteren Bildschirmrand statt als sperriges Seiten-Panel.
  - Standard-Höhe auf ca. 42% der Bildschirmhöhe optimiert (ausreichend Platz für Dialoge, ohne den Dokumenten-Kontext völlig zu verdecken).
  - Neuer Vollbild-Maximierungs-Button (`btn_drawer_fullscreen`) in der Kopfzeile für 100% Bildschirmabdeckung bei langen Konversationen.
- **Kompakte Kopfleiste:**
  - Minimierte vertikale Abstände und Icons in der Header-Leiste, wodurch maximaler vertikaler Raum für den Textanzeigebereich und Chat-Verlauf freigegeben wird.

### 🔍 Dokumenten-Prüfung & Schärfe-Check (`DocumentReviewAndCropScreen.kt`)
- **Interaktiver Schärfe-Check (Lupe):**
  - Implementierung einer Lupenfunktion zur Prüfung feiner Textdetails und Schärfe vor dem finalen Zuschneiden und Speichern im Dokumenten-Tresor.

### 📱 UI-Feinschliff & Navigation
- **Korrektur der Scan-Eingang Beschriftung (`MainBottomBar.kt`):**
  - Umbenennung in „Posteingang“ mit 10.sp Schriftgröße, um unschöne Textabschneidungen auf schmaleren Displays zu verhindern.
- **KI-Agent Icon im Settings-Menü (`SettingsView.kt`):**
  - Der Reiter „Lokaler KI Agent“ verwendet nun das offizielle Roboter-KI-Icon (`LocalAiAgentIcon`).
- **Verfeinerte Animation:**
  - Sanfte Pulsier- und Neige-Animation des schwebenden KI-Agenten-Buttons in der Navigationsleiste.

### 🛡️ Stabilität, Crash-Prävention & Audit-Fixes
- **TabRow IndexOutOfBoundsException Schutz:**
  - Absicherung aller `ScrollableTabRow`- und `TabRow`-Komponenten (`SettingsView.kt`, `ModuleSettingsTab.kt`) mit Index-Coercion (`coerceIn`) und geschützten Indikatoren (`safeSelectedTabIndex in tabPositions.indices`). Verhindert Abstürze beim dynamischen Umschalten zwischen Standard-, Advanced- und Expert-Modus.
- **Auto-Mirror & Cloud-Sync Fallback bei unverschlüsseltem Modus:**
  - `DocAnizerViewModel.kt` und `CloudSyncWorkerService.kt` werfen keine Exceptions mehr bei leerem Master-Passwort, sondern führen eine sichere lokale Spiegelung durch bzw. informieren verständlich über den Status.

## [2.7.0] - 2026-10-13 (Zero-Knowledge Security & Bottom Navigation Margin Optimization)

### 🔐 Erweitertes Zero-Knowledge Sicherheitskonzept & Recovery-System
- **Entfernung harter Default-Passwörter:**
  - Komplette Deaktivierung automatischer Fallback-Masterpasswörter.
  - Das Master-Passwort muss nun stets bewusst vom Nutzer vergeben oder der unverschlüsselte Modus explizit bestätigt werden.
- **Wizard-Kopplung & Automatisches Security-Routing:**
  - Wird der Ersteinrichtungs-Assistent ohne Vergabe eines Master-Passworts übersprungen, navigiert die App automatisch in die Sicherheits-Einstellungen (`SecuritySettingsTab.kt`).
- **Sicherheits-Warnbanner & Unverschlüsselter Modus:**
  - Neuer sichtbarer Warnhinweis-Banner (`card_no_password_warning`) in den Tresor-Einstellungen bei ungeschütztem Betrieb.
  - Direkte Auswahl-Aktionen: *"Passwort vergeben"* oder *"Ohne Schutz nutzen (Unverschlüsselt)"*.
  - Transparente Statusanzeige für den unverschlüsselten Modus mit jederzeitiger Option zur nachträglichen Verschlüsselung.
- **Verschiebung der Audit-Log-Funktion:**
  - Die Karte *"Diagnose- & Inferenz-Logdatei"* (`card_audit_log_developer`) inklusive Log-Export, Teilen und Leeren wurde aus den Sicherheits-Einstellungen in die **Entwickler-Einstellungen** (`DeveloperSettingsTab.kt`) verschoben.

### 📐 Navigation & UI-Layout Optimierung
- **Garantierter vertikaler Sicherheitsabstand für Bottom-Bar Icons:**
  - Anpassung der `NavigationBar` Container-Höhe auf `82.dp` und Injektion von oberer Polsterung (`padding(top = 4.dp)`).
  - Verhindert, dass die Icons der Haupt-Tabs **"Dokumente"**, **"Dashboard"** und **"Scan Eingang" (InBox)** zu nah am oberen Rand des Rahmens / Containers anliegen.

## [2.6.0] - 2026-10-12 (Interaktive Erste-Schritte Tour & AI-Agent Alignment)

### 🚀 Interaktive 11-Schritte Erste-Schritte Tour & Skip-Option
- **Explizite "Tour überspringen" Optionen:**
  - Neuer dauerhafter "Tour überspringen" Button am oberen rechten Bildschirmrand (`testTag("btn_screen_skip_tour")`).
  - Überarbeiteter Tour-Karten-Header mit "Überspringen" Text und Schließen-Icon (`testTag("btn_close_tour")`).
  - Sauberes Zurücksetzen aller temporären UI-Zustände (geöffnete Speed-Dial-FABs, AI-Chat-Drawer, Pending Rule Dialoge) beim Überspringen.
- **Perfekte Tooltip-Positionierung für den AI-Agenten (Schritt 8):**
  - Fokussierte Ziel-Bounds im oberen Konversationsbereich des `AiDocumentChatDrawer.kt`, damit die Erklärungskarte 100% vollständig im sichtbaren Bildschirmbereich gerendert wird.
  - Dynamische min/max Y-Grenzen (`64.dp` Sicherheitsabstand) unterhalb von Statusleisten und Notches.
- **Vollständige Prozess-Führung & Wizard-Abschluss-Info:**
  - Chronologische Führung von FAB (+) über Speed-Dial, Kamera-Berechtigung, Scanner, KI-Regelvorschlag, InBox, Dokumenten Tresor, KI-Agent, Fragen & Befehle bis zum Dashboard.
  - Neues Info-Banner auf der Zusammenfassungs-Seite am Ende des Einrichtungswizards (`SummaryStep.kt`) mit Vorab-Erklärung zum Tourstart und der direkten Option „Tour überspringen“ in den Wizard-Aktionsschaltflächen.

## [2.5.0] - 2026-10-03 (4-Team On-Device KI-Pipeline & Quality Gatekeeper Release)

### 🤖 4-Team On-Device KI-Pipeline (100% Offline & Datenschutzkonform)
- **Team 1 (Poststelle & Eingangstriage):**
  - Implementierung von `OcrPreFilterMatrix.kt`: Intelligentes Schneiden langer OCR-Texte auf Kopfzeilen und Signalbereiche (max. 900 Zeichen).
  - Blitzschneller First Run mit GBNF-Grammar (`TRIAGE_JSON_GBNF`) für Absender-, Typ- und Hauptkategorie-Erkennung ohne Metadaten-Overhead.
- **Team 2 (Fachabteilungs-Teams & Second Run):**
  - Spezialisierte deutsche Fachabteilungen via Kotlin-Dispatcher (`DepartmentTeam`):
    - *Team Finanzen (Abt. A):* Rechnungen, Kassenbelege, IBAN, Skonto, Steuernummer, Betrag.
    - *Team Verträge (Abt. B):* Verträge, Policen, Kündigungsfristen, Mindestlaufzeiten, monatliche Raten.
    - *Team Verwaltung (Abt. C):* Bescheide, Behördenschreiben, Aktenzeichen, Fristen.
  - Dynamische Injektion der Benutzer-Ordnerstrategie (`FolderStrategy`) in alle Prompts.
- **Team 3 (Stammdaten & Whitelist-Manager):**
  - Auslesen aller aktiven globalen Zusatzfelder aus der Room-Datenbank (`CustomFieldDao`).
  - Whitelist-Injektion in den Systemprompt zur Vermeidung von Synonym-Chaos (z. B. Vereinheitlichung von `Kundennummer` statt `Kd-Nr`).
- **Team 4 (Revision & Quality Gatekeeper):**
  - 100% deterministischer Cross-Check in reinem Kotlin (`QualityGatekeeper.kt`) ohne LLM-Aufruf.
  - **Text-Anchor-Cross-Check:** Verifiziert, ob extrahierte Beträge, IBANs und Daten tatsächlich im OCR-Rohtext vorkommen (Halluzinations-Schutz).
  - **Normalisierungs-Logik:** Deutsche Datumsangaben (`12. März 2024` -> `12.03.2024`, ISO `2024-11-20`) und Währungsbeträge (`1.000,00 €` -> `1000.00`).
  - **Kontext-Plausibilitätsfilter:** Fängt Geburtsdatum-Verwechslungen ab (wenn ein Datum neben `geb.` steht) und schlägt echte Zahlungsziele vor.

### 📁 Dynamischer Ordner-Strategie-Wizard
- Neues Enum `FolderStrategy` mit den Optionen:
  - *Nach Absender (Firmen):* `Vodafone/Rechnungen`
  - *Nach Lebensbereich (Kategorien):* `Finanzen/Versicherungen`
  - *Chronologisch (Jahr):* `2026/Rechnungen`
  - *Flach (Alles in einen Ordner):* `Dokumente`
- Integration in `SetupWizardDialog.kt` (Schritt 2: Speicherort) und `SettingsView.kt`.

### ⚡ Deterministische Regel-Engine (`DocRule`)
- Deterministische Regelbenennung nach dem Muster `val ruleName = "$Absender $Dokumententyp"`.
- Übernahme des vorgeschlagenen Zielpfads (`targetFolderPath`) aus der Fachabteilungs-Inferenz.
- Transparenter Abgleich mit bestehenden Regeln im `PendingRuleSuggestionDialog`.

### 🛡️ UI-Revision & Schnellkorrekturen
- Neuer Revisions-Warnbanner bei Diskrepanzen oder geringem Vertrauenswert in `PendingRuleSuggestionDialog.kt` und `DocumentDetailModal.kt`.
- Ein-Klick-Korrektur (`AssistChip`) und Ein-Klick-Bestätigung für geprüfte Werte.
- Farbliche Inline-Markierungen (`⚠️ Bitte prüfen`) in den Zusatzfeld-Listen.

### 🧪 Qualitätssicherung & Test Gate
- 35 automatisierte Robolectric- und Integrationstests auf der JVM (100% grün).
- Verifikation des gesamten App-Lifecycles, Room-Datenbank, DAOs, ViewModel StateFlows, Gatekeeper und Normalisierungs-Logik.

---

## [2.4.0] - 2026-09-15
- Initialer Release von myDocAnizer-Mobile mit Dokumenten-Tresor, Offline OCR, P2P WLAN-Sync, Zero-Knowledge AES-256-GCM Verschlüsselung und Multi-OS Desktop Companion.
