# ADR 002: Optionale Zwei-Faktor-Absicherung des Tresors

**Status:** ENTSCHEIDUNG GETROFFEN; TOTP-Klartextbereinigung umgesetzt. GitHub Actions Run `39633700992` (Build, Unit-, Instrumentierungs- und APK-Prüfung) erfolgreich; manuelle Geräteabnahme bleibt offen.  
**Datum:** 2026-10-20

## Entscheidung

Die optionale echte Zwei-Faktor-Verschlüsselung soll zunächst **Tresorpasswort + separate, zufällig generierte Schlüsseldatei** verwenden. Passwort allein bleibt als separater Ein-Faktor-Modus verfügbar. Die Schlüsseldatei enthält niemals den rohen DEK.

TOTP aus einer Authenticator-App wird **nicht** als kryptografischer zweiter Faktor für den lokalen Tresor gewertet. Eine zusätzliche UI-Abfrage kann den Offline-Angriff auf eine kopierte Vault-Datei nicht verhindern.

## Ausgangsbefund vor der Umsetzung (2026-10-20)

- `VaultHeader.totpSecret` wird als Klartext im JSON-Header in SharedPreferences gespeichert.
- `unlockVault` prüft TOTP vor der Passwort-KDF, aber der DEK-Wrapper wird weiterhin nur mit Passwort bzw. Passwort+Keyfile abgeleitetem KEK geöffnet.
- Der Code kombiniert bereits `password` und `keyFileToken` zu einem Argon2id-Eingang. Die tatsächliche Einrichtung, UI, Schlüsseldatei-Generierung, Recovery und Tests müssen noch belegen, dass beide Faktoren erforderlich sind.
- Bestehende `PASSWORD_AND_TOTP`-Header dürfen nicht stillschweigend als sichere 2FA gelten und nicht durch eine ungetestete Migration unbrauchbar werden.

## Begründung

Ein sechsstelliger TOTP-Code hat nur etwa eine Million mögliche Werte. Bei einem lokalen Offline-Tresor gibt es keinen vertrauenswürdigen Server, der Angriffsversuche zentral begrenzt. Ein lokal gespeichertes TOTP-Secret kann zudem aus einer Header-Kopie entnommen werden. Daher ist TOTP allein keine geeignete kryptografische Ergänzung für den DEK-Wrapper.

## Umsetzungsvorgaben für V06/V06A/V06B

1. Schlüsseldatei kryptografisch zufällig und hochentropisch erzeugen; keine PIN oder manuell gewählte Zeichenfolge als Ersatz.
2. Zwei-Faktor-Modus erst nach erfolgreichem Test-Unlock mit Passwort **und** Schlüsseldatei aktivieren.
3. Negativtests müssen zeigen, dass Passwort allein, Schlüsseldatei allein, falsche/gekürzte Datei und manipulierte Eingaben scheitern.
4. Passwort-/Keyfile-Wechsel atomar ausführen; bei Fehler bleibt der vorherige Wrapper gültig.
5. Kein Schlüsseldatei-Inhalt in Logs, Crashmeldungen, Backups oder Einstellungen im Klartext.
6. Umsetzung: Der aktive V2-Unlock wertet TOTP nicht aus. Historische `totpSecret`-Felder werden beim Laden aus dem persistierten Header entfernt; neue Header serialisieren sie nicht. Neue Vault-Einrichtung weist ein nicht-leeres TOTP-Secret zurück. RFC-6238-Hilfsfunktionen dürfen isoliert getestet werden, sind aber keine Tresor-Absicherung.
7. Recovery bleibt ein eigenständiger, bewusst eingerichteter Pfad. Ein gerätegebundener Keystore-Schlüssel ist nicht automatisch portabel.
8. Abnahme erfordert Unit-/Robolectric- und Android-Instrumentationstests; Gerätetests separat dokumentieren.

## Umsetzungsstand (2026-10-21)

- Der V2-Serializer schreibt kein `totpSecret` mehr. Ein historisches `totpSecret`-Feld wird beim Laden eines ansonsten gültigen aktiven V2-Headers entfernt.
- `unlockVault` behält den Parameter `totpCode` aus Quellkompatibilitätsgründen, wertet ihn aber nicht aus. Die DEK-Freigabe erfolgt ausschließlich über Passwort oder Passwort plus Schlüsseldatei.
- TOTP-Hilfsfunktionen bleiben von der Tresorfreigabe getrennte Utility-Funktionen; sie sind keine Sicherheitsstufe des Tresors.
- GitHub Actions Run `39633700992` auf Commit `d509ebb` bestand `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` sowie APK-Prüfung/Artefakt-Upload. Eine separate physische Geräteabnahme bleibt offen.

## Referenzen

- Android Keystore: https://developer.android.com/privacy-and-security/keystore
- WebAuthn PRF (Authenticator-Kompatibilität ist nicht universell): https://github.com/w3c/webauthn/blob/main/explainers/prf-extension.md
