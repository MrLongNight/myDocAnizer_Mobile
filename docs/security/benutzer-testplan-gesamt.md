# Gesamt-Testplan aus Benutzersicht — myDocAnizer Mobile

Stand: 2026-10-18  
Zweck: **Eine zentrale, abhängigkeitssensible Reihenfolge für alle aktuell offenen Tests**. Dieser Plan verbindet die bestehenden Checklisten `manual-test-checklist-v01-v10.md` und `manual-test-checklist-v11-s05.md` zu einem durchgehenden Ablauf, damit Vorbedingungen nicht übersehen werden.

**Wichtig:** Die Änderungen V11–V13, V15 und S01–S05 wurden noch nicht durch einen vollständigen Build- und Gerätetestlauf bestätigt. Ein früherer erfolgreicher Build/Start belegt nicht, dass der aktuelle Stand funktioniert. Keine Testzeile als bestanden markieren, bevor sie tatsächlich durchgeführt wurde.

## Statuslegende

Für jeden Test genau einen Status verwenden:

- `OFFEN` – noch nicht ausgeführt
- `BESTANDEN` – Erwartung tatsächlich erfüllt
- `FEHLGESCHLAGEN` – Erwartung nicht erfüllt; Fehler-ID/Notiz ergänzen
- `BLOCKIERT` – Voraussetzung fehlt; Blocker angeben
- `NICHT ANWENDBAR` – nur mit Begründung

Dieses Dokument ist die **führende manuelle Ablauf-Checkliste**. Die beiden Detailchecklisten bleiben die technische Ergänzung für zusätzliche Negativ-, Referenzvektor- und Implementierungstests.

## 0. Sicherheitsregeln und Testmaterial

- [ ] Nur eine Debug-APK des zu prüfenden aktuellen Commits verwenden; Commit-SHA und APK-Version notieren.
- [ ] Nur einen eigens angelegten, löschbaren Test-Tresor verwenden.
- [ ] Ein synthetisches Master-Passwort und eine separate, mindestens 20 Zeichen lange Test-Recovery-Passphrase festlegen. Beide dürfen keine produktiven Passwörter sein.
- [ ] Ein bis drei harmlose Testdokumente verwenden, z. B. selbst erzeugte Dateien mit dem eindeutigen Inhalt `TESTDOKUMENT-001`; keine echten persönlichen Dokumente.
- [ ] Testgerät, Android-API-Level, verfügbare starke Biometrie und Gerätesperre notieren.
- [ ] Keine DEKs, Passphrasen, Recovery-Secrets, TOTP-Secrets oder Shamir-Shares in Screenshots, Logs, Tickets oder Chat kopieren.
- [ ] Vor destruktiven Tests sicherstellen, dass alle Testdaten gelöscht werden dürfen. Nicht mit einem echten oder wichtigen Tresor experimentieren.
- [ ] Nach jedem Fehler zuerst Ergebnis und Test-ID notieren. Nicht stillschweigend weitergehen, wenn spätere Tests dadurch ungültig werden.

**Testprotokoll oben ausfüllen:**  
Gerät: ______ · Android/API: ______ · App-Version: ______ · getesteter Commit-SHA: ______ · Datum: ______

## 1. Vorbedingungen: Build und automatische Tests

Diese Phase muss vor den UI-Tests stattfinden. Bei einem Build- oder Unit-Testfehler zunächst stoppen und den Fehler klären; andernfalls können Folgefehler irreführend sein.

- [ ] **PRE-01** Aktuellen Stand aus `main` synchronisieren und den Commit-SHA notieren.
- [ ] **PRE-02** `./gradlew assembleDebug` erfolgreich ausführen.
- [ ] **PRE-03** `./gradlew testDebugUnitTest` erfolgreich ausführen.
- [ ] **PRE-04** Kombiniert `./gradlew assembleDebug testDebugUnitTest` erfolgreich ausführen.
- [ ] **PRE-05** Relevante Instrumentationstests auf einem Gerät/Emulator ausführen, insbesondere `VaultBiometricCryptoTest`; nicht als bestanden werten, wenn sie nur kompiliert wurden.
- [ ] **PRE-06** Debug-APK installieren und die App starten. Starten allein ist nur ein Smoke-Test, keine Sicherheitsabnahme.

**Gate 1:** Wenn PRE-02, PRE-03 oder PRE-04 fehlschlägt, die folgenden manuellen Tests nicht als gültige Abnahme durchführen. Fehlerausgabe bereinigt dokumentieren.

## 2. Frische Installation und erster Tresor

Diese Schritte schaffen einen definierten Ausgangszustand für alle weiteren Tests.

- [ ] **BASE-01** App erstmals starten bzw. eine separate frische Testinstallation verwenden. Erwartung: kein Absturz.
- [ ] **BASE-02** Test-Tresor mit synthetischem Master-Passwort einrichten.
- [ ] **BASE-03** Ein eindeutiges Testdokument importieren/erstellen und prüfen, dass es nach erneutem Öffnen lesbar bleibt.
- [ ] **BASE-04** App-Prozess vollständig beenden und neu starten. Erwartung: der bestehende Tresor ist nicht ohne Authentifizierung entsperrt.
- [ ] **BASE-05** Falsches Master-Passwort eingeben. Erwartung: Ablehnung; Tresor und geschützte Dokumentinhalte bleiben gesperrt.
- [ ] **BASE-06** Mit korrektem Master-Passwort entsperren. Erwartung: Testdokument ist wieder lesbar.
- [ ] **BASE-07** Tresor explizit sperren und danach erneut versuchen, geschützte Inhalte zu öffnen. Erwartung: erneute Authentifizierung erforderlich.
- [ ] **BASE-08** Passwortwechsel durchführen, sofern der vorhandene UI-Workflow verfügbar ist. Neues Passwort muss funktionieren, altes Passwort muss scheitern.
- [ ] **BASE-09** App erneut beenden und starten; neues Passwort testen und verifizieren, dass der Tresorzustand weiterhin korrekt gesperrt ist.

**Gate 2:** BASE-02 bis BASE-07 müssen bestanden sein, bevor Recovery, Biometrie oder Key-Lease-abhängige Dokumentaktionen bewertet werden.

## 3. Schlüssel- und Recovery-Bausteine (über automatisierte Tests)

Diese Tests sind Voraussetzungen für die darauf aufbauenden QR-/PDF- und Recovery-Workflows. Sie sind nicht alle über die Benutzeroberfläche sichtbar.

- [ ] **CRYPTO-01** Argon2id-Referenzvektor und Parametergrenzen ausführen (Detailplan V01–V10, Abschnitt B).
- [ ] **CRYPTO-02** VaultHeader-v2 Roundtrip, falsches Passwort/KEK und Manipulationsfälle ausführen.
- [ ] **CRYPTO-03** Sicherstellen, dass v1-Fixture weiterhin lesbar ist und ungültige v1-Daten nicht automatisch einen neuen Tresor erzeugen.
- [ ] **CRYPTO-04** BIP-39-Referenzvektoren, ungültige Prüfsumme und Wortlistenprüfung ausführen.
- [ ] **CRYPTO-05** Alle drei Shamir-2-aus-3-Paare mit synthetischem Secret testen; fehlerhafte Shares müssen scheitern.
- [ ] **CRYPTO-06** Recovery-Payload: korrekter Roundtrip, falsche Passphrase, falsche Vault-ID, Manipulation, Trunkierung und Größenlimit testen.
- [ ] **CRYPTO-07** Log-/Fehlermeldungen auf ungewollte Ausgabe von Schlüsseln oder Recovery-Geheimnissen prüfen, ohne echte Secrets zu protokollieren.

**Gate 3:** Bei fehlgeschlagenem Recovery-Payload-Test die QR-/PDF-Recovery nicht als bestanden abnehmen.

## 4. Notfall-Kit: Passphrase → PDF → QR → Wiederherstellung

Dieser Abschnitt ist ein zusammenhängender Ablauf. Die Schritte müssen in dieser Reihenfolge getestet werden, da QR und PDF nur die Verpackung des Recovery-Payloads sind.

- [ ] **REC-01** In den Vault-Sicherheitseinstellungen den Notfall-Kit-/PDF-Export öffnen.
- [ ] **REC-02** Export ohne Passphrase versuchen. Erwartung: Export wird nicht durchgeführt.
- [ ] **REC-03** Eine Passphrase unter 20 Zeichen eingeben. Erwartung: klare Ablehnung.
- [ ] **REC-04** Zwei unterschiedliche Passphrasen in Eingabe und Bestätigung eingeben. Erwartung: klare Ablehnung.
- [ ] **REC-05** Eine gültige synthetische Passphrase ab mindestens 20 Zeichen zweimal eingeben und PDF erzeugen.
- [ ] **REC-06** PDF öffnen. Erwartung: Anweisungen sind verständlich und erklären, dass die Recovery-Passphrase separat aufbewahrt werden muss; die Passphrase selbst darf nicht im PDF stehen.
- [ ] **REC-07** QR im PDF mit einem unabhängigen Scanner lesen. Erwartung: lesbares Recovery-Payload; kein Roh-DEK.
- [ ] **REC-08** Wenn QR-Lesen nicht möglich ist, den dokumentierten Base64-Fallback prüfen. Der Fallback darf ausschließlich das verschlüsselte Recovery-Payload enthalten.
- [ ] **REC-09** PDF-Text extrahieren und auf synthetischen Roh-DEK (inklusive Base64-Darstellung), Testpassphrase und sonstige Test-Secrets prüfen. Erwartung: keine Treffer.
- [ ] **REC-10** Den QR-/Payload-Inhalt über den vorgesehenen Recovery-Dialog importieren und mit richtiger Passphrase sowie richtiger Vault-ID wiederherstellen. Erwartung: nur nach erfolgreicher kryptografischer Prüfung geht es weiter.
- [ ] **REC-11** Falsche Passphrase verwenden. Erwartung: kontrollierte Ablehnung; kein Unlock.
- [ ] **REC-12** Payload eines anderen Test-Vaults bzw. falsche Vault-ID verwenden. Erwartung: kontrollierte Ablehnung.
- [ ] **REC-13** Beschädigten QR bzw. manipuliertes Payload verwenden. Erwartung: kontrollierte Ablehnung ohne Absturz und ohne Teilentsperrung.
- [ ] **REC-14** Export-/Freigabedialog abbrechen. Erwartung: keine unerwartete Entsperrung und keine Passphrase in Logs.
- [ ] **REC-15** Nach erfolgreicher Wiederherstellung prüfen, dass der ursprüngliche Test-Tresor und das Testdokument mit dem wiederhergestellten Schlüsselzugriff verfügbar sind. Einen anschließenden App-Neustart ebenfalls testen.

**Gate 4:** Keine Recovery-Abnahme, solange REC-07, REC-09, REC-10 und die Negativtests REC-11 bis REC-13 nicht bestanden sind.

## 5. Biometrie – erst nach funktionierendem Passwort-Unlock

Voraussetzung: Gerät hat eine eingerichtete Gerätesperre und unterstützte starke Biometrie. Wenn das Gerät die Voraussetzungen nicht erfüllt, den Test als BLOCKIERT markieren, nicht als bestanden.

- [ ] **BIO-01** Biometrie zunächst deaktiviert lassen und den Tresor per Master-Passwort entsperren. Erwartung: normaler Passwortpfad funktioniert.
- [ ] **BIO-02** Biometrie in den Sicherheitseinstellungen einschalten. Erwartung: Android-Systemdialog für Biometrie/CryptoObject erscheint.
- [ ] **BIO-03** Systemdialog abbrechen. Erwartung: Biometrie wird nicht aktiviert; Vault bleibt im vorherigen sicheren Zustand.
- [ ] **BIO-04** Aktivierung erneut starten und Biometrie erfolgreich bestätigen. Erwartung: erst nach erfolgreicher kryptografischer Operation wird sie als aktiviert angezeigt.
- [ ] **BIO-05** Tresor sperren und biometrisches Entsperren durchführen. Erwartung: Entsperren nur nach erfolgreicher CryptoObject-Operation.
- [ ] **BIO-06** Biometrie abbrechen bzw. fehlgeschlagenen Versuch auslösen. Erwartung: kein Entsperren; Passwortweg bleibt verfügbar.
- [ ] **BIO-07** Biometrie deaktivieren. Erwartung: biometrischer Schlüssel/Status wird entfernt; Passwort-Unlock funktioniert weiterhin.
- [ ] **BIO-08** Falls ein zweites Testgerät verfügbar ist: Biometrie-Registrierung ändern/entfernen und erneut testen. Erwartung: invalidierter Keystore-Schlüssel führt nicht zu einem Bypass, sondern zu einem sicheren Fehler und Passwort-/Recovery-Weg.
- [ ] **BIO-09** Wenn verfügbar, API 24–29 und API 30+ getrennt testen. Fehlende Hardware oder nicht unterstützte Funktionen dürfen keinen unsicheren Fallback auslösen.

**Gate 5:** Wenn BIO-04 nicht bestanden ist, BIO-05 nicht als bestanden werten. Biometrie ist kein Ersatz für die erfolgreiche Master-Passwort-/Recovery-Baseline.

## 6. Sperrzustand, Bypass-Schutz und Auto-Lock

- [ ] **LOCK-01** App mit bestehendem Tresor kalt starten. Erwartung: Tresor startet gesperrt.
- [ ] **LOCK-02** Falsches Passwort, Abbruch und fehlgeschlagene Biometrie wiederholen. Erwartung: kein Pfad setzt den Tresor fälschlich auf entsperrt.
- [ ] **LOCK-03** App-Lock und Vault-Lock aus Benutzersicht unterscheiden: UI-Freigabe allein darf keine geschützten Dokumentdaten entschlüsseln.
- [ ] **LOCK-04** Debug-Bypass nur in einem Debug-Build prüfen. Er darf nicht den Tresor-DEK freigeben. In Release darf kein Debug-Bypass verfügbar sein.
- [ ] **LOCK-05** Auto-Lock auf **0 Minuten / Sofort** stellen, App in den Hintergrund schicken und zurückkehren. Erwartung: gesperrt, bevor sensible Inhalte wieder sichtbar sind.
- [ ] **LOCK-06** Auto-Lock auf **1 Minute** stellen, nach ca. 30 Sekunden zurückkehren. Erwartung: keine reine Timeout-Sperre vor Ablauf; anderer Lock-Auslöser kann natürlich weiterhin sperren.
- [ ] **LOCK-07** Auto-Lock auf **1 Minute** stellen, länger als eine Minute im Hintergrund bleiben und zurückkehren. Erwartung: Sperre vor Freigabe sensibler Inhalte.
- [ ] **LOCK-08** Mit geöffnetem Testdokument in den Hintergrund wechseln und zurückkehren. Erwartung: Inhalt wird nicht vor der Lockprüfung sichtbar.
- [ ] **LOCK-09** Geräteuhr vor- und zurückstellen. Erwartung: Auto-Lock-Zeitmessung bleibt korrekt.
- [ ] **LOCK-10** Prozess im Hintergrund beenden und neu starten. Erwartung: Tresor startet gesperrt.
- [ ] **LOCK-11** App sperren, dann eine geschützte Aktion starten. Erwartung: kein stiller Fallback und keine Ausgabe geschützter Daten.

## 6A. Authentifizierungs-Backoff und externe Intents (S06/S07/S10)

Diese Prüfungen bauen auf der erfolgreichen Basis aus Abschnitt 2 auf. Für Fehlversuche ausschließlich den entbehrlichen Test-Tresor verwenden.

- [ ] **SECRET-01** Instrumentationstests `SecretStoreInstrumentedTest` auf echtem Android-Gerät ausführen: verschlüsselter Roundtrip, manipulierte Daten, fehlender Keystore-Key und ungültiger Name. Erwartung: Fehler führen immer zu `null`/Ablehnung, niemals Klartextfallback.\n- [ ] **SECRET-02** Prüfen, dass `mydocanizer_secret_store` nur verschlüsselte Payloads enthält; keine synthetischen Test-Secrets als Klartextwert.\n- [ ] **BACKOFF-01** Automatisierte Tests für `AuthenticationBackoffManagerTest` ausführen. Erwartung: 5. Fehlversuch löst 30 Sekunden aus; danach steigert sich die Sperre auf 1, 5 und 15 Minuten.
- [ ] **BACKOFF-02** In der Debug-App fünfmal absichtlich falsches Test-Passwort eingeben. Erwartung: während der Sperrfrist werden weitere Unlock-Versuche abgewiesen; korrekte Zugangsdaten umgehen die aktive Sperre nicht.
- [ ] **BACKOFF-03** App-Prozess während einer aktiven Sperrfrist beenden und erneut starten. Erwartung: die persistierte Deadline bleibt wirksam; keine Abkürzung durch Neustart.
- [ ] **BACKOFF-04** Geräteuhr während der Sperre zurückstellen. Erwartung: die Sperre wird nicht vorzeitig aufgehoben. Eine konservativ längere Restzeit ist sicherer als ein frühzeitiger Unlock.
- [ ] **BACKOFF-05** Erfolgreich entsperren und prüfen, dass der Fehlversuchszähler zurückgesetzt wird.
- [ ] **SCREEN-01** Debug-Build: Screenshots sind gemäß der expliziten Debug-Ausnahme möglich; Release-Build: Screenshots/Screenrecording sensibler App-Inhalte müssen durch `FLAG_SECURE` blockiert sein.
- [ ] **SCREEN-02** In Release Recents öffnen, während ein Testdokument sichtbar war. Erwartung: keine sensiblen Inhalte in der Aufgabenübersicht.
- [ ] **SCREEN-03** Tresor auf einer geöffneten Detailansicht sperren und zurückkehren. Erwartung: sensible Inhalte sind nicht mehr sichtbar, bevor erneut entsperrt wurde.
- [ ] **INTENT-01** Gültiges Widget-Intent mit einem erlaubten Tab und erlaubter Aktion bei entsperrtem Test-Tresor auslösen. Erwartung: nur die freigegebene Navigation/Aktion wird ausgeführt.
- [ ] **INTENT-02** Unbekannten Tab, unbekannte Aktion und fehlerhafte Extras senden. Erwartung: werden ignoriert, keine Ausnahme und keine Navigation.
- [ ] **INTENT-03** Gültiges externes Intent bei gesperrtem Tresor auslösen, danach entsperren. Erwartung: die Aktion wird nicht nachträglich automatisch ausgeführt.
- [ ] **MIRROR-01** Auto-Mirror bleibt standardmäßig deaktiviert. Ohne verfügbaren Verschlüsselungsschlüssel darf auch nach explizitem Aktivieren keine unverschlüsselte Kopie im Backup-Verzeichnis entstehen.
- [ ] **MIRROR-02** Mit verfügbarem Schlüssel und aktiviertem Auto-Mirror eine Testdatei spiegeln. Erwartung: nur die verschlüsselte `.enc`-Kopie entsteht; keine parallele Klartextkopie.
- [ ] **MIRROR-03** Verschlüsselung absichtlich fehlschlagen lassen. Erwartung: keine Klartextkopie als Fallback; Fehler wird kontrolliert behandelt.
- [ ] **MIRROR-04** Auto-Mirror mit einem Dateinamen testen, der Verzeichnistrenner bzw. `..` enthält. Erwartung: Ziel bleibt im privaten `Backups`-Verzeichnis; kein Pfad außerhalb wird angelegt.
- [ ] **MIRROR-05** Während einer bestehenden verschlüsselten Spiegelung einen Fehler vor dem Commit provozieren. Erwartung: temporäre Datei wird entfernt; die vorherige vollständige `.enc`-Datei bleibt erhalten.

**Gate 6A:** Wenn BACKOFF-01 nicht bestanden ist, BACKOFF-02 bis BACKOFF-05 nicht als Abnahme werten. Wenn INTENT-03 fehlschlägt oder MIRROR-01 eine Klartextkopie erzeugt, Sicherheitsfehler dokumentieren und keine Release-Abnahme durchführen.

## 7. Dokumentaktionen und Key-Leases

Diese Tests prüfen, dass die App bei gesperrtem Tresor nicht weiter mit einem alten Schlüssel oder ungeschützten Fallback arbeitet.

- [ ] **LEASE-01** Testdokument bei entsperrtem Vault importieren und öffnen. Erwartung: Inhalt korrekt.
- [ ] **LEASE-02** Vault sperren und erneut Import, PDF-Erzeugung, E-Mail-/Teilen-Export und geschützte Dokumentaktionen versuchen. Erwartung: Aktion wird sicher abgelehnt oder verlangt zuerst Unlock.
- [ ] **LEASE-03** Mehrere Testdokumente in einem Batch verarbeiten. Erwartung: Dokumente werden korrekt verarbeitet; ein einzelner Fehler darf nicht unbemerkt als Erfolg gemeldet werden.
- [ ] **LEASE-04** Vault während einer längeren Dokumentaktion sperren, sofern über die UI zuverlässig auslösbar. Erwartung: kein neuer Schlüsselzugriff nach Sperre; Vorgang bricht sicher ab bzw. beendet die bereits laufende geschützte Operation kontrolliert.
- [ ] **LEASE-05** Nach fehlgeschlagener Aktion die App erneut entsperren und Testdokumente prüfen. Erwartung: keine beschädigten oder unvollständig geschriebenen Dokumente.
- [ ] **LEASE-06** Automatisierte `LockLeaseTest`-Tests ausführen: Lease-Kopie wird gelöscht, gesperrter Vault liefert keine Lease und Lock wartet auf laufende synchrone Lease.
- [ ] **LEASE-07** Nachsehen, ob Produktionsaufrufer von `getActiveDek()` verbleiben. Laut Implementierungsbericht sind verbliebene Nutzungen nur Legacy-/Testkompatibilität; bei Abweichung dokumentieren und nicht stillschweigend als bestanden markieren.

## 8. Android-Backup und Geräteübertragung

- [ ] **BACKUP-01** Unit-Test `BackupRulesSecurityTest` ausführen.
- [ ] **BACKUP-02** In der tatsächlich installierten APK prüfen, dass Manifest und Backup-Regeln zusammenpassen.
- [ ] **BACKUP-03** Prüfen, dass `mydocanizer_vault_security.xml` und `mydocanizer_prefs.xml` von Cloud-Backup ausgeschlossen sind.
- [ ] **BACKUP-04** Prüfen, dass sensible Preferences, Room-Datenbank, Dokument-/Scan-/Backup-Verzeichnisse und externe Dateien bei der automatischen Geräteübertragung ausgeschlossen sind. Der Gerätewechsel muss über den ausdrücklich gestarteten, verschlüsselten Export/Restore erfolgen.
- [ ] **BACKUP-05** Falls Testgerät/Android-Backup verfügbar: Backup/Restore oder Geräteübertragung durchführen. Erwartung: keine Übertragung von Vault-Headern oder Keystore-gebundenen Geheimnissen; nach Restore sicherer Zustand, keine unberechtigte Entsperrung.
- [ ] **BACKUP-06** Ergebnis der statischen Regeln und des echten Geräte-/Restore-Tests getrennt dokumentieren. Ein XML-Unit-Test allein beweist nicht den tatsächlichen System-Backupablauf.
- [ ] **BACKUP-07** Prüfen, dass Modell-/Temp-Verzeichnisse und der externe App-Dateibereich vom Cloud-Backup ausgeschlossen sind; Cache-Temporärdateien werden separat als Cache behandelt.

## 8A. Scan-PDF-Textlayer (D14)

- [ ] **PDF-01** Instrumentationstest `PdfTextLayerInstrumentedTest` ausführen.
- [ ] **PDF-02** Prüfen, dass PDFBox den sichtbaren Kopftext `myDocAnizer` extrahiert.
- [ ] **PDF-03** Prüfen, dass der eindeutige OCR-Sentinel nicht aus der erzeugten Scan-PDF extrahiert wird; das bestätigt den aktuellen fehlenden OCR-Textlayer.
- [ ] **PDF-04** Separat prüfen, dass derselbe OCR-Inhalt im Dokumentdatensatz in Room gespeichert und über die App-Suche gefunden wird.
- [ ] **PDF-05** Produktentscheidung dokumentieren: Reicht die Suche in Room/FTS innerhalb der App, oder wird ein eigenständig durchsuchbarer PDF-Textlayer verlangt? Bei Bedarf eines Textlayers zuerst einen eigenen Minimalplan freigeben, nicht im Rahmen der Verschlüsselungsarbeiten implementieren.
- [ ] **PDF-06** Instrumentationstest und Geräte-/Emulatorlauf getrennt protokollieren. Der Test wurde hinzugefügt, aber noch nicht ausgeführt.

## 9. Header v1→v2 Migration: bewusst nicht ausführen

- [ ] **V14-STOP** Nicht manuell Header, Preferences oder Vault-Dateien ändern. Die v2-Laufzeitintegration ist derzeit blockiert.
- [ ] **V14-STATUS** Als `BLOCKIERT` stehen lassen, bis Runtime-Integration, atomarer Schreib-/Readback-Pfad, Rollback und Idempotenztests umgesetzt und geprüft wurden.
- [ ] **V14-NACHFOLGE** Erst nach Freigabe testen: korrektes Passwort migriert denselben DEK; falsches Passwort lässt v1 unverändert; Schreib-/Readback-Fehler rollt sicher zurück; zweiter Lauf ist idempotent.

## 10. Abschluss und Regression

- [ ] **END-01** Alle vorherigen Testabschnitte durchgehen und offene abhängige Tests als BLOCKIERT statt BESTANDEN kennzeichnen.
- [ ] **END-02** App-Prozess beenden und erneut starten. Erwartung: bestehender Vault startet gesperrt.
- [ ] **END-03** Mit gültigem Passwort entsperren und alle angelegten Testdokumente öffnen.
- [ ] **END-04** Erneut sperren und falsches Passwort prüfen.
- [ ] **END-05** Sicherstellen, dass Recovery-PDF und Testdateien kontrolliert gelöscht werden können und keine Secrets in Screenshots/Logs verbleiben.
- [ ] **END-06** `./gradlew assembleDebug testDebugUnitTest` erneut ausführen, damit spätere Codeänderungen keine früher bestandenen Tests unbemerkt gebrochen haben.
- [ ] **END-07** Ergebnisprotokoll ausfüllen und Fehler/Blocker mit Test-ID, Commit-SHA, Gerät/API-Level und bereinigtem Fehlertext dokumentieren.

## 11. Ergebnisprotokoll

| Testbereich | Status | Fehler-ID / Notiz |
|---|---|---|
| PRE — Build und automatische Tests | OFFEN | |
| BASE — frische Installation und Passwort-Unlock | OFFEN | |
| CRYPTO — Schlüssel-/Recovery-Bausteine | OFFEN | |
| REC — Notfall-PDF/QR/Wiederherstellung | OFFEN | |
| BIO — Biometrie | OFFEN | |
| LOCK — Sperre und Auto-Lock | OFFEN |\n| SECRET — Keystore-SecretStore | OFFEN |\n| BACKOFF — Fehlversuche und Backoff | OFFEN |\n| SCREEN — Screenshot/Recents | OFFEN |\n| INTENT — externe Widgets/Navigation | OFFEN |\n| MIRROR — verschlüsselte lokale Spiegelung | OFFEN | |
| LEASE — Dokumentaktionen | OFFEN | |
| BACKUP — Backup/Restore | OFFEN | |
| V14 — Header-Migration | BLOCKIERT | Laufzeitintegration fehlt |
| END — Abschluss/Regression | OFFEN | |

## 12. Verknüpfte Detailpläne

- [Detailplan V01–V10: Vault, KDF, Header, BIP-39, Shamir und Recovery-Payload](manual-test-checklist-v01-v10.md)
- [Detailplan V11–S05: QR, Emergency-PDF, Biometrie, Backup und Lock](manual-test-checklist-v11-s05.md)
- [Implementierungsbericht V11–S05](v11-s05-implementation-report.md)
- [Implementierungsbericht S06–S10](s06-s10-implementation-report.md)
- [D14 PDF-Textlayer-Audit](d14-pdf-text-layer-audit.md)
- [V14-Migrationsblocker](v14-migration-blocker.md)

**Abnahmeregel:** Ein Abschnitt gilt erst als bestanden, wenn seine konkreten Tests durchgeführt wurden. Ein erfolgreiches Build, ein erfolgreicher App-Start oder ein grüner Einzeltest ersetzt keine End-to-End-Prüfung. Bei fehlgeschlagenen Voraussetzungen alle abhängigen Tests als BLOCKIERT markieren, bis der Fehler behoben und die betroffenen Tests wiederholt wurden.
