# Changelog

All notable changes to **myDocAnizer-Mobile** are documented here.

## [Unreleased] - 2026-10-18 (Sicherheits-Härtung)
- Security: Entfernt pauschale Android-Geräteübertragungs-Includes für Dateien, Datenbanken und SharedPreferences; sensible Speicherbereiche werden explizit ausgeschlossen.
- Security tests: Erweitert `BackupRulesSecurityTest` um Prüfungen gegen pauschale Includes und für externe/temporäre Speicherbereiche.
- D14: Ergänzt einen PDFBox-basierten Instrumentationstest und dokumentiert, dass Scan-OCR aktuell nicht als Textlayer in die PDF eingebettet wird; Testlauf offen.

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
