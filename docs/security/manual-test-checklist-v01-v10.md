# Manueller Testplan V01–V10 — myDocAnizer Mobile

Stand: 2026-10-18. Für eine frische Testinstallation und ausschließlich synthetische Testdaten. Keine echten Dokumente, produktiven Passwörter oder persönlichen Recovery-Secrets verwenden.

**Status:** Der Nutzer meldet einen erfolgreichen Build und problemlosen App-Start. Die folgenden Funktions- und Sicherheitstests sind dadurch nicht automatisch bestanden. Teststatus erst nach tatsächlicher Durchführung abhaken.

## Vorbereitung

- [ ] Debug-APK auf einem Testgerät installieren; App-Version/Commit notieren.
- [ ] Für destruktive Tests eine frische Testinstallation bzw. löschbare Testdaten verwenden.
- [ ] Einen Test-Tresor mit einem synthetischen Passwort und einem eindeutig erkennbaren Testdokument anlegen.
- [ ] Keine echten Tresor-DEKs, echten Recovery-Wörter oder persönlichen Passwörter in Screenshots, Logs oder Tickets aufnehmen.
- [ ] Wenn verfügbar, ein zweites Android-Gerät und ein QR-Scanner-/PDF-Text-Extraktionstool bereithalten.

## A. Tresor, Passwort und Schlüsselzustand

- [ ] **A1 Richtiger Master-Passwort-Unlock:** Tresor sperren, richtiges Passwort eingeben. Erwartung: Entsperren erfolgreich, Testdokument lesbar.
- [ ] **A2 Falsches Passwort:** Sperren, falsches Passwort eingeben. Erwartung: Unlock schlägt fehl; Tresor bleibt gesperrt.
- [ ] **A3 Lock räumt Schlüsselzustand:** Entsperren, explizit sperren und erneut auf geschützte Funktion zugreifen. Erwartung: keine Entschlüsselung ohne erneuten gültigen Unlock.
- [ ] **A4 Passwortwechsel:** Passwort ändern, mit neuem Passwort erfolgreich entsperren; altes Passwort muss scheitern.
- [ ] **A5 Neustart:** App-Prozess beenden und App neu starten. Erwartung: kein vorheriger DEK bleibt als entsperrter Sitzungszustand verfügbar.
- [ ] **A6 Legacy-v1:** Nur mit synthetischem v1-Testheader prüfen: korrekter Unlock erhält denselben DEK; falsches Passwort schlägt fehl; fehlerhafter Header führt nicht zu automatischer Neuinitialisierung.

## B. Header-v2 / AES-GCM

- [ ] **B1 Roundtrip:** Synthetischen 32-Byte-DEK mit v2 verpacken und wieder auspacken; Bytefolge muss exakt übereinstimmen.
- [ ] **B2 Falsches Passwort/KEK:** Entpacken muss fehlschlagen, ohne DEK zurückzugeben.
- [ ] **B3 Ciphertext-Manipulation:** Ein Ciphertextbyte verändern; Authentifizierung muss scheitern.
- [ ] **B4 Metadaten-Manipulation:** Vault-ID, Algorithmus-ID, Salt, IV oder authentifizierte Metadaten verändern. Erwartung: Parser lehnt ungültige Werte ab bzw. AEAD-Authentifizierung schlägt fehl.
- [ ] **B5 Nonce/Salt-Frische:** Zwei unabhängig erzeugte Header vergleichen. Erwartung: IV und Salt sind nicht identisch.
- [ ] **B6 Argon2id-Referenzvektor:** Den automatisierten RFC-9106-Test ausführen; nicht allein aus erfolgreichem App-Start auf KDF-Korrektheit schließen.

## C. BIP-39 und Shamir

- [ ] **C1 BIP-39-Referenzphrase:** Automatisierter Test für die offizielle Nullentropie-Phrase muss erfolgreich sein.
- [ ] **C2 Ungültige Prüfsumme:** Zwölfmal „abandon“ muss abgelehnt werden.
- [ ] **C3 Wortliste:** Automatisierter Vergleich muss genau 2048 Wörter in der offiziellen Reihenfolge bestätigen.
- [ ] **C4 Shamir-Paare:** Aus einem synthetischen 32-Byte-Secret drei Shares erzeugen. Alle drei unterschiedlichen 2-von-3-Paare müssen exakt dasselbe Secret rekonstruieren.
- [ ] **C5 Fehlerhafte Shares:** Doppelte, gekürzte, manipulierte und ungültig formatierte Shares müssen abgelehnt werden.
- [ ] **C6 Persistenz/Hash-Konsistenz:** Shares ausgeben, App neu starten und gespeicherte Share-Hashes gegen genau diese Shares verifizieren. Keine echten Shares teilen oder protokollieren.

## D. Recovery-Payload

- [ ] **D1 Bytegenauer Roundtrip:** Synthetischen DEK und mindestens 20 Zeichen lange, nicht produktive Test-Passphrase verwenden; Decode muss denselben DEK liefern.
- [ ] **D2 Falsche Passphrase:** Decode muss fehlschlagen und darf keinen Klartext-DEK liefern.
- [ ] **D3 Falsche Vault-ID:** Payload mit anderer Vault-ID entschlüsseln wollen. Erwartung: Ablehnung.
- [ ] **D4 Manipulation:** Version, Algorithmus, Salt, IV, Ciphertext und Tag jeweils einzeln verändern. Erwartung: kontrollierter Fehler.
- [ ] **D5 Trunkierung/Anhängsel/Größenlimit:** Abgeschnittene, zu große und mit zusätzlichen Bytes versehene Payloads müssen abgelehnt werden.
- [ ] **D6 Logs/Fehlermeldungen:** Prüfen, dass weder DEK noch Passphrase noch entschlüsseltes Recovery-Secret in Logs/Exception-Text erscheinen.

## E. Build und automatisierte Tests

- [ ] `./gradlew assembleDebug testDebugUnitTest`
- [ ] Bei Änderungen an Manifest, Biometrie oder Backupregeln zusätzlich die entsprechenden Instrumented-/Gerätetests ausführen.
- [ ] Prüfergebnis, Gerät/API-Level und Commit-SHA im Fortschrittsprotokoll festhalten.

## Ergebnisprotokoll

Für jeden Test `BESTANDEN`, `FEHLGESCHLAGEN` oder `NICHT AUSGEFÜHRT` eintragen. Fehler mit Test-ID, Gerät/API-Level, Commit-SHA und bereinigtem Fehlertext dokumentieren. Keine Secrets oder sensiblen Testwerte anhängen.
