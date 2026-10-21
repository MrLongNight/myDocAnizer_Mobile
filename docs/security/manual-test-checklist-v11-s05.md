# Manueller Testplan V11–S05 — QR, Emergency-PDF, Biometrie, Backup und Lock

Stand: 2026-10-18. **Diese Checkliste ist noch nicht ausgeführt.** Bitte die Tests auf einem echten Android-Gerät durchführen, soweit angegeben. Ausschließlich einen synthetischen Test-Tresor und Testpassphrasen verwenden.

## Vor dem Test

- [ ] Aktuellen main-Commit-SHA notieren und die neueste Fassung lokal synchronisieren.
- [ ] ./gradlew assembleDebug testDebugUnitTest ausführen.
- [ ] App auf einem Gerät mit registrierter starker Biometrie und Geräte-PIN testen; API-Level notieren.
- [ ] Für Recovery einen Test-Tresor und eine unproduktive Recovery-Passphrase mit mindestens 20 Zeichen verwenden.
- [ ] Keine echten DEKs, Passphrasen, TOTP-Secrets oder Shamir-Shares protokollieren.

## V11 — Standard-QR

- [ ] **QR-1:** Im automatisierten Test muss QrEncodingTest den QR mit einem unabhängigen ZXing-Reader decodieren und exakt dieselbe UTF-8-Payload zurückliefern.
- [ ] **QR-2:** Emergency-PDF auf dem Gerät erzeugen, QR aus dem PDF mit einer unabhängigen Scanner-App lesen und die dekodierte JSON-Payload mit dem erwarteten Format vergleichen.
- [ ] **QR-3:** Ein übergroßes Payload testen. Erwartung: kontrollierter Fehler, kein selbstgebautes Muster und kein Absturz.
- [ ] **QR-4:** Den gedruckten QR mit üblicher Druckgröße und normalem Kamerafokus scannen. Erwartung: zuverlässig lesbar.

## V12 — Emergency-PDF

- [ ] **PDF-1:** Export ohne Passphrase ablehnen bzw. keinen PDF-Export erlauben.
- [ ] **PDF-2:** Passphrase unter 20 Zeichen ablehnen; ab 20 Zeichen nur bei identischer Wiederholung fortfahren.
- [ ] **PDF-3:** PDF erzeugen und öffnen. Es darf keinen Roh-DEK, keine Base64-DEK-Zeile und keine Klartext-Recovery-Passphrase enthalten.
- [ ] **PDF-4:** PDF-Text extrahieren und nach dem synthetischen DEK in Base64, Passphrase und anderen Test-Secrets durchsuchen. Alle Treffer müssen ausbleiben.
- [ ] **PDF-5:** Den QR mit der App bzw. dem vorgesehenen Recovery-Pfad lesen und mit richtiger Passphrase sowie richtiger Vault-ID wiederherstellen.
- [ ] **PDF-6:** Falsche Passphrase, andere Vault-ID, manipulierten Ciphertext und beschädigten QR testen. Erwartung: kontrollierte Ablehnung; kein Teil-DEK und kein Unlock.
- [ ] **PDF-7:** Export abbrechen, Dateifreigabe abbrechen und App-Prozess beenden. Erwartung: kein ungewollter Unlock und keine Passphrase in Logs.

## V13 — Biometrie und Keystore

- [ ] **BIO-1:** Biometrie im Vault-Sicherheitsbereich einschalten. Android muss den echten BiometricPrompt für eine CryptoObject-Cipher anzeigen.
- [ ] **BIO-2:** Authentifizierung abbrechen. Erwartung: Biometrie bleibt aus; Vault bleibt gesperrt.
- [ ] **BIO-3:** Authentifizierung erfolgreich abschließen. Erwartung: erst nach erfolgreicher doFinal-Operation wird die Biometrie als aktiviert angezeigt.
- [ ] **BIO-4:** Vault sperren und biometrisch entsperren. Erwartung: DEK wird nur nach erfolgreichem CryptoObject-Vorgang freigegeben.
- [ ] **BIO-5:** Fehlversuch und Abbruch wiederholen. Erwartung: kein Unlock.
- [ ] **BIO-6:** Biometrische Registrierung im Android-System ändern/entfernen und erneut entsperren. Erwartung: invalidierter Schlüssel führt fail-closed zum Passwort-Recovery.
- [ ] **BIO-7:** Mindestens ein Gerät mit API 24–29 und eines mit API 30+ prüfen, wenn verfügbar. StrongBox ist nicht Voraussetzung; fehlende Hardware darf keinen unsicheren Fallback auslösen.

## V14 — V2-only-Laufzeit und Behandlung alter Header

- [x] Codeprüfung: `VaultSecurityManager` liest/schreibt den aktiven Header ausschließlich über `vault_header_json_v2_active` und `VaultHeaderParserV2`; kein Laufzeit-Migrations-/Fallbackpfad für V1 gefunden.
- [ ] Regressionstest: alte Header-Schlüssel werden weder als Vault geladen noch automatisch migriert oder überschrieben; der Test muss in CI bestehen.
- [ ] Keine V1-Migration: vorhandene Altbestände werden nicht automatisch übernommen. Vor einer späteren Produktfreigabe muss die UX für solche Altbestände ausdrücklich geklärt sein; nicht manuell an SharedPreferences manipulieren.

## V15 — Android-Backup und Geräteübertragung

- [ ] **BACKUP-1:** Automatisierte Tests für backup_rules.xml und data_extraction_rules.xml ausführen.
- [ ] **BACKUP-2:** In der installierten APK prüfen, dass die Manifest-Verweise auf beide Regeldateien korrekt sind.
- [ ] **BACKUP-3:** Cloud-Backup-Regeln schließen mydocanizer_vault_security.xml und mydocanizer_prefs.xml aus.
- [ ] **BACKUP-4:** Geräteübertragungsregeln schließen dieselben SharedPreferences-Dateien aus; legitime Dokument-/Dateipfade werden nicht pauschal ausgeschlossen.
- [ ] **BACKUP-5:** Bei verfügbarer Testumgebung Backup/Restore bzw. Device Transfer testen und prüfen, dass Vault-Header und Keystore-gebundene Geheimnisse nicht übertragen werden.

## S01–S02 — Lock-Inventar und zentraler LockManager

- [ ] **LOCK-1:** Automatisierte Tests für den LockManager ausführen. Ein vorhandener Vault muss beim Prozessstart gesperrt sein.
- [ ] **LOCK-2:** App-Lock und Vault-Lock getrennt beobachten: App-Oberfläche allein darf den DEK nicht freigeben.
- [ ] **LOCK-3:** Falsches Passwort, Abbruch und ungültiger Keystore-Schlüssel dürfen den Lockzustand nicht auf entsperrt setzen.
- [ ] **LOCK-4:** Debug-Instant-Unlock darf nur im Debug-Build existieren; Release-Build darf keinen solchen Button oder Pfad enthalten.
- [ ] **LOCK-5:** Prüfen, dass lockVault() den aktiven DEK überschreibt und danach keine neue Key-Lease ausgegeben wird.

## S03 — Auto-Lock

- [ ] **AUTO-1:** Auto-Lock auf 0 Minuten setzen, App in den Hintergrund schicken. Erwartung: sofortige Sperre.
- [ ] **AUTO-2:** Auf 1 Minute setzen, nach 30 Sekunden zurückkehren. Erwartung: keine Timeout-Sperre allein aufgrund der Zeitgrenze.
- [ ] **AUTO-3:** Auf 1 Minute setzen, länger als 1 Minute im Hintergrund bleiben. Erwartung: Sperre spätestens bei Ablauf bzw. vor Freigabe sensibler Inhalte beim Zurückkehren.
- [ ] **AUTO-4:** Während ein Dokument geöffnet ist in den Hintergrund wechseln und zurückkehren. Erwartung: keine sensiblen Inhalte vor der Lockprüfung.
- [ ] **AUTO-5:** Geräteuhr vor/zurück stellen. Erwartung: Timeout bleibt korrekt, da monotone Zeit verwendet wird.
- [ ] **AUTO-6:** Prozess während des Hintergrundaufenthalts beenden und App neu starten. Erwartung: fail-closed, keine gespeicherte DEK-/Passphrase-Abkürzung.

## S04 — Authentifizierungs-Bypässe

- [ ] **AUTH-1:** Passwort falsch eingeben und bestätigen. Erwartung: App und Tresor bleiben gesperrt.
- [ ] **AUTH-2:** Passwortdialog abbrechen. Erwartung: gesperrt.
- [ ] **AUTH-3:** Biometrie abbrechen oder einen Fehler provozieren. Erwartung: gesperrt.
- [ ] **AUTH-4:** Activity nicht verfügbar machen bzw. Rotation/Navigation während Auth testen. Erwartung: kein automatisches Entsperren.
- [ ] **AUTH-5:** Recovery erfolgreich abschließen und anschließend neues Master-Passwort setzen. Erwartung: Unlock nur nach erfolgreicher Recovery-/DEK-Operation.
- [ ] **AUTH-6:** Release-Build installieren und nach Debug-Bypass-Schaltflächen suchen.

## S05 — Key-Leases

- [ ] **LEASE-1:** Automatisierte Tests ausführen: Lease-Array muss nach Callback auf null Bytes stehen.
- [ ] **LEASE-2:** Gesperrter Tresor darf keine Lease liefern.
- [ ] **LEASE-3:** Synchrone Lease-Operation parallel starten und sperren. Erwartung: `lockVault()` wartet, bis der synchrone Callback beendet ist; danach ist keine neue Lease verfügbar.
- [ ] **LEASE-3a:** Suspendierbare Datei-/Batchoperation starten, bis sie eine Key-Lease besitzt, und dann sperren. Erwartung: Lock verhindert sofort neue Leases; die bereits gestartete Operation darf den begrenzten Schreibvorgang abschließen oder kontrolliert abbrechen. Nach Ende/Abbruch muss die Lease-Kopie in `finally` auf null Bytes stehen. Dokumentieren, ob eine Dateioperation nach dem Sperrbefehl noch abgeschlossen wurde.
- [ ] **LEASE-4:** Dokument-Import, PDF-Erzeugung, Batch-Commit und E-Mail-Import mit gesperrtem Tresor ausprobieren. Erwartung: sichere Ablehnung statt Klartext-/Fallback-Schreiben.
- [ ] **LEASE-5:** Die noch verbliebenen Aufrufstellen von `getActiveDek()` sind im Prüfbericht dokumentiert und müssen in Folgearbeiten migriert werden.
- [ ] **LEASE-6:** Automatisierten Suspend-Lease-Test ausführen: Operation darf suspendieren, nach Lock werden keine neuen Leases ausgegeben und die bereits erteilte Lease-Kopie wird bei Rückkehr/Abbruch gelöscht.

## Abnahme

- [ ] Jede Zeile als BESTANDEN, FEHLGESCHLAGEN oder NICHT AUSGEFÜHRT markieren.
- [ ] Build-/Testausgabe und Gerät/API-Level notieren; keine Secrets anhängen.
- [ ] V14-Abnahme: V2-only-Laufzeit plus Negativtest für ignorierte Legacy-Header muss CI bestehen. Keine Runtime-Migration ist Teil der beschlossenen V2-only-Strategie; alte Daten dürfen nicht stillschweigend migriert oder überschrieben werden.
