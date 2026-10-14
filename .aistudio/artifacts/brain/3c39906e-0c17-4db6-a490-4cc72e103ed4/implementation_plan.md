# Neugestaltung der Verschlüsselungs- & Sicherheits-Einrichtung (myDocAnizer-Mobile)

Intuitiver, transparenter und mehrstufiger Einrichtungsablauf für den Dokumenten-Tresor: Nutzer verstehen auf einen Blick, warum Verschlüsselung empfohlen wird, welche Konsequenzen die Stufen haben und wie 1-Faktor-, 2-Faktor-, Notfall- sowie Biometrie-Optionen konfiguriert werden.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> **Bestätigte Entscheidungen & Benutzer-Vorgaben**:
> - **Mehrstufiger geführter Ablauf**: Die Konfiguration wird in 4 klare Teilschritte unterteilt (1. Sicherheitsstufe wählen -> 2. Faktoren einrichten -> 3. Notfall-Absicherung -> 4. Biometrie & App-Schutz).
> - **Drei Kernstufen (A, B, C)**:
>   - **A) Keine Verschlüsselung (Unverschlüsselt)** mit deutlicher Aufklärung über Konsequenzen (Risiko bei Verlust/Diebstahl, keine Passwortpflicht).
>   - **B) Verschlüsselung mit einem Faktor (1-Faktor)** mit Wahl zwischen *Master-Passwort* oder *Schlüsseldatei (.key)*.
>   - **C) Verschlüsselung mit zwei Faktoren (2-Faktor / 2FA)** mit modernen, kostenfreien und sicheren Best Practices (*Master-Passwort + Schlüsseldatei* oder *Master-Passwort + Authenticator-App/TOTP*).
> - **Ausführliche Info-Popups**: Zu jeder Sicherheitsstufe, Notfall-Option und jedem Faktor steht ein modaler Info-Dialog bereit, der Zweck, Funktionsweise und Sicherheitsimplikationen verständlich erklärt.
> - **Notfall-Optionen**: Vollständiges Spektrum (BIP-39 Sicherheitswörter, Notfall-Kit PDF mit QR-Code, Shamir 2-von-3 Schlüsselteilung, Notfall-Wiederherstellungscode sowie bewusste Abwahl mit Warnung).
> - **App-Zugriff per Biometrie**: Hardware-gestützte Absicherung via Android KeyStore (StrongBox/TEE) mit Statusanzeige und konfigurierbarer automatischer Tresor-Sperre.

---

### 1. Overview & Core Concept

- **Was es tut**: Der bisherige, teilweise unübersichtliche Sicherheitsdialog wird durch eine didaktisch aufgebaute, intuitive Schritt-für-Schritt-Führung ersetzt. Nutzer erhalten vor der Entscheidung klare Antworten auf die Fragen: *Warum wird Verschlüsselung dringend empfohlen?*, *Was passiert, wenn ich mein Passwort vergesse?* und *Welche Sicherheitsstufe passt zu meinem Einsatzzweck?*.
- **Zielgruppe**: Privatanwender mit sensiblen Dokumenten (Gehaltsabrechnungen, Steuerdaten, Verträge, Ausweise) bis hin zu sicherheitsbewussten Nutzern, die professionellen Zero-Knowledge-Schutz ohne Cloud-Zwang verlangen.
- **Mehrwert**: Kein Datenverlust durch Missverständnisse, kein versehentlicher unverschlüsselter Betrieb ohne Risikoaufklärung, und flexible Wahl zwischen Bedienkomfort (z. B. Biometrie im Alltag) und maximaler Härtung (2FA & Shamir).

---

### 2. User Experience & Visual Design

#### A. Die 4 geführten Teilschritte im Detail

```
┌────────────────────────────────────────────────────────────────────────┐
│  SCHRITT 1: Grundstufe wählen (A: Keine | B: 1 Faktor | C: 2 Faktoren) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│  SCHRITT 2: Faktoren festlegen (Passwort, .key-Datei, TOTP-Authenticator)│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│  SCHRITT 3: Notfall-Absicherung (BIP-39, PDF-Kit, Shamir 2-von-3, Code) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│  SCHRITT 4: App-Zugriff & Biometrie (Fingerprint/Face, Auto-Lock, Info)│
└────────────────────────────────────────────────────────────────────────┘
```

1. **Teilschritt 1 – Grundlegende Sicherheitsstufe wählen**:
   - **Hero-Erklärung**: Prominenter Banner über die Vorteile von Zero-Knowledge (AES-256-GCM + Argon2id).
   - **3 interaktive Stufen-Karten**:
     - **Stufe A: Keine Verschlüsselung (Unverschlüsselt)**:
       - *Auswirkungen*: Sofortiger Schnellzugriff, kein Passwortverlust-Risiko. Aber: Dateien sind im Gerätespeicher ungeschützt lesbar.
       - *Badge*: `Ungeschützt` (Rot/Orange-Akzent).
     - **Stufe B: Verschlüsselung mit einem Faktor (1-Faktor)**:
       - *Auswirkungen*: AES-256-GCM geschützt. Entsperrung über 1 geheimes Element (z. B. Passwort). Ideal für Alltagssicherheit.
       - *Badge*: `Standard (Empfohlen)` (Primärfarbe).
     - **Stufe C: Verschlüsselung mit zwei Faktoren (2-Faktor / 2FA)**:
       - *Auswirkungen*: Banken- & Vault-Standard. Entsperrung verlangt zwingend zwei unabhängige Komponenten (Wissen + Besitz).
       - *Badge*: `Maximaler Schutz` (Grün/Schild-Icon).
   - **Info-Icon `(i)` an jeder Karte**: Öffnet ein modales Popup mit detaillierten Risiken, Vor- und Nachteilen.

2. **Teilschritt 2 – Faktoren einrichten**:
   - Bei **Stufe A**: Bestätigungskarte mit Sicherheitswarnung ("Dokumente bleiben im Klartext") und Direkt-Weiterleitung.
   - Bei **Stufe B (1 Faktor)**:
     - Auswahl zwischen:
       - *Option B1: Master-Passwort* (Eingabe, Bestätigung, Stärke-Score, Argon2id-Parameter).
       - *Option B2: Digitale Schlüsseldatei (.key)* (Erzeugung eines 256-Bit Schlüsselfiles zum lokalen Download/Speichern auf USB oder sicherem Ordner).
   - Bei **Stufe C (2 Faktoren)**:
     - Auswahl der Kombination:
       - *Option C1: Master-Passwort + Schlüsseldatei (.key)* ("Etwas, das du weißt" + "Etwas, das du besitzt").
       - *Option C2: Master-Passwort + Authenticator-App (TOTP)* (Passwort + zeitbasierter 6-stelliger Code per Google Authenticator, Aegis etc. mit QR-Code & Base32-Geheimnis).

3. **Teilschritt 3 – Notfall-Wiederherstellung (Safety Net)**:
   - Frage: *"Möchtest du eine Notfall-Wiederherstellung einrichten für den Fall, dass du deine Faktoren verlierst?"*
   - Auswählbare Methoden, jeweils mit eigenem **Info-Popup**:
     - **12 Sicherheitswörter (BIP-39)**: Klassische Mnemonic-Wörter zum Aufschreiben.
     - **Notfall-Schlüsseldatei / Emergency Kit (PDF)**: Druckfertiges A4-Blatt mit QR-Code zum physischen Abheften.
     - **Schlüssel-Teilung (Shamir 2 von 3)**: Mathematischer Split (Handy, Desktop, Vertrauensperson).
     - **Einmaliger Recovery-Code**: 32-stelliger Hex-Code zum Notieren.
     - **Keine Notfall-Option**: Expliziter Warnhinweis ("Bei Verlust der Faktoren ist kein Datenzugriff mehr möglich!").

4. **Teilschritt 4 – App-Zugriff & Biometrie absichern**:
   - **Biometrische Entsperrung**:
     - Toggle für Fingerabdruck / Face Unlock zur komfortablen Schnellentriegelung im Alltag.
     - Automatische Bindung an Android KeyStore StrongBox/TEE.
     - Hardware-Status-Anzeige (Sensor verfügbar, gesperrt, nicht eingerichtet).
   - **Automatische Tresor-Sperre (Auto-Lock)**:
     - Dropdown/Chips: Sofort beim Verlassen, nach 1 Minute, nach 5 Minuten, nach 15 Minuten.
   - **Sicherheits-Zusammenfassung**:
     - Kompakte Karte aller gewählten Einstellungen vor dem finalen Speichern.

---

### 3. Key Product Decisions & Trade-Offs

- **Warum TOTP und Schlüsseldatei als 2FA?**: Sie benötigen keine externen Server, verursachen keine laufenden Kosten (kein SMS-Gateway, kein Drittanbieter-Abo) und sind 100% lokal, Open-Source-kompatibel und Zero-Knowledge-konform.
- **Pflicht-Prüfung**: In Stufe B und C kann der Assistent nicht ohne vergebene Faktoren abgeschlossen werden. Nur bei expliziter Wahl von Stufe A (Unverschlüsselt) mit Bestätigungs-Dialog wird der Tresor unverschlüsselt betrieben.
- **Barrierefreiheit & Touch-Fläche**: Alle Radio-Karten, Info-Buttons und Toggles haben eine Mindest-Touch-Größe von 48dp x 48dp mit klarem Rippel-Feedback.

---

### 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             SetupWizardDialog.kt                                 │
│  - Hält globalen Wizard-Fortschritt & Dialog-Verwaltung                          │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │
┌────────────────────────────────────────▼─────────────────────────────────────────┐
│                           SecurityWizardStep.kt                                  │
│  ├── SubStep 1: Stufen-Wahl (Stufe A / B / C Cards + Info Dialogs)               │
│  ├── SubStep 2: Faktoren-Konfiguration (Passwort / .key-Datei / TOTP)           │
│  ├── SubStep 3: Notfall-Optionen (BIP-39 / Notfall-PDF / Shamir / Code)          │
│  └── SubStep 4: Biometrie-Toggle, Auto-Lock & Zusammenfassung                     │
└────────────────────────────────────────┬─────────────────────────────────────────┘
                                         │
┌────────────────────────────────────────▼─────────────────────────────────────────┐
│                  VaultSecurityManager.kt & DocAnizerViewModel                    │
│  - Argon2id Schlüsselableitung                                                   │
│  - AES-256-GCM Verschlüsselung & KeyStore StrongBox                              │
│  - TOTP Secret Generierung & QR-Code Erstellung                                  │
│  - Notfall-Ressourcen (BIP-39, Emergency PDF Kit, Shamir GF(256))                │
└──────────────────────────────────────────────────────────────────────────────────┘
```

#### Zustandsvariablen & Flow:
- `selectedSecurityTier`: Enum (`TIER_A_UNENCRYPTED`, `TIER_B_ONE_FACTOR`, `TIER_C_TWO_FACTOR`)
- `selectedFactorType`: Enum (`PASSWORD_ONLY`, `KEYFILE_ONLY`, `PASSWORD_AND_KEYFILE`, `PASSWORD_AND_TOTP`)
- `selectedRecoveryMethod`: `VaultRecoveryMethod` (`NONE`, `BIP39_WORDS`, `EMERGENCY_PDF`, `SHAMIR_2_OF_3`)
- `biometricEnabled`: `Boolean`
- `autoLockTimeout`: `Int` (Minuten)
- `activeInfoDialog`: Dialog-State für Erklärungstexte zu den Stufen und Optionen

---

### 5. Verifizierungs- und Test-Plan

1. **Kompilierung**: Ausführung von `compile_applet`.
2. **Robolectric JVM Integrationstests**: Ausführung von `gradle :app:testDebugUnitTest`.
   - Starten der `MainActivity` und Rendern von `SetupWizardDialog`.
   - Validierung der Teilschritt-Navigation 1 -> 2 -> 3 -> 4.
   - Prüfung der Passwort-Validierung und des Unencrypted-Pfads.
   - Prüfung der Notfall-Initialisierung im `VaultSecurityManager`.
