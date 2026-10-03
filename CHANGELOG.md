# Changelog

All notable changes to **myDocAnizer-Mobile** are documented here.

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
