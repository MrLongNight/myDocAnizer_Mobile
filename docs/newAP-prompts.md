@!@ # myDocAnizer-Mobile – granularisierte Coding-Prompts mit Sicherheits-Gates

**Projekt:** `MrLongNight/myDocAnizer_Mobile`  
**Ausgangsbasis:** Die acht erweiterten APs aus `myDocAnizer_Expanded_Prompts.md`. Die darin genannten Codebefunde sind Arbeitshypothesen, die im aktuellen Codebestand verifiziert werden müssen.  
**Ziel:** Große, sicherheitskritische Pakete in kleine, einzeln abnehmbare Änderungen (Micro-APs) zerlegen.

## Zentrale Benutzertest-Checkliste

Vor der manuellen Abnahme bitte den [Gesamt-Testplan aus Benutzersicht](security/benutzer-testplan-gesamt.md) verwenden. Er legt die abhängige Reihenfolge der Tests fest und verknüpft die Detailpläne V01–V10 und V11–S05. **V14 bleibt ausdrücklich blockiert** und darf nicht manuell getestet oder migriert werden, solange die v2-Laufzeitintegration fehlt.

## Workflow für Google AI Studio + manuelle Patch-Übernahme

1. **Ein Lauf = ein Micro-AP:** Sende den Masterprompt aus Abschnitt 1 zusammen mit genau einem Micro-AP. Keine Sammelaufträge und keine ungefragten Folgearbeiten.
2. **Vorher-Zustand wahren:** Vor Änderungen die betroffenen Dateien und deren Ausgangszustand erfassen. Keine vorhandenen Änderungen überschreiben und keine Datei komplett ersetzen, wenn ein gezielter Eingriff genügt.
3. **Änderung isolieren:** Nur die für den jeweiligen Micro-AP notwendigen Dateien anfassen. Keine Formatierungswellen, Umbenennungen, Dependency-Updates oder Nebenbei-Refactorings.
4. **Patch als Übergabeartefakt:** Änderungen werden als standardkonformer Unified Diff/Patch (inkl. neuer oder gelöschter Dateien) mit korrekten relativen Dateipfaden ausgegeben.
5. **Lokal prüfen vor dem nächsten Schritt:** Vor dem Anwenden des Patches lokal im eigenen Repo `git status` sichern, Patch einspielen, Diff kontrollieren und Tests ausführen. Bei Konflikten nicht blind mit `--reject`, `--ours` oder `--theirs` erzwingen.
6. **Kein Kettenlauf:** Erst nach lokaler Übernahme, erfolgreichem Test und Sichtprüfung den nächsten Micro-AP starten. Status in der Fortschrittsliste führen: `NICHT GESTARTET | BLOCKIERT | PATCH ERSTELLT | MANUELL ÜBERNOMMEN | GETESTET | ABGENOMMEN`.
7. **Gezielt vorgehen:** Erst `M00` ausführen, danach nur bestätigte Befunde abarbeiten. Architektur-Gates sind zwingende Stopppunkte.

---

## 1. Masterprompt – bei JEDEM Micro-AP voranstellen (oder als System Instruction nutzen)

> Du arbeitest am Android-Projekt `MrLongNight/myDocAnizer_Mobile` (Kotlin, Compose, Room, minSdk 24; maßgeblich ist die reale Konfiguration im Workspace). Setze ausschließlich den nachfolgenden Micro-AP im vorhandenen Codebestand um und liefere einen sauberen Unified Diff/Patch sowie einen kompakten Prüfbericht.
>
> **1. Reale Baseline & Ehrlichkeit:** Arbeite strikt mit den im Workspace tatsächlich vorhandenen Dateien und Werkzeugen. Erfinde niemals Befehlsausgaben, Builds oder Testergebnisse. Wenn Shell/Gradle verfügbar ist, führe `./gradlew assembleDebug testDebugUnitTest` sowie relevante Checks aus und dokumentiere das echte Resultat; andernfalls markiere sie explizit als `NICHT AUSFÜHRBAR`. Bewahre alle bereits bestehenden Änderungen im Workspace. Ist die Ausgangsbasis unklar, stoppe vor Codeänderungen.
>
> **2. Minimaler Scope:** Bearbeite ausschließlich den angegebenen Micro-AP. Prüfe relevante Implementierungen, Aufrufer, Tests, Gradle-/Version-Catalog-Dateien, Manifest, Migrationen und Backup-Regeln im aktuellen Code gegen die Annahmen im Prompt. Keine Vorgriffe auf spätere Micro-APs, keine Massenformatierungen, keine ungefragten Refactorings, Dependency-Upgrades oder API-/Dateiformatänderungen.
>
> **3. Sicherheitsregeln (Fail-Closed):**
> - Niemals Passwörter, TOTP-/Recovery-Secrets, DEK/KEK, Tokens, Pairing-Secrets, OCR-Texte oder Dokumentinhalte loggen oder in Berichte/Patches schreiben.
> - Keine unsicheren Fallbacks, kein `TODO` statt einer Sicherheitsprüfung, keine übersprungenen oder abgeschwächten Tests.
> - Keine eigenen kryptografischen Primitive entwerfen; Bibliotheks-API, Version und Referenzvektoren verifizieren.
> - Bei Schlüssel-, Vault-, Backup-, Datenbank- oder Dokumentmigrationen strikt fail-closed arbeiten, Nutzerdaten erhalten und Rollback-Fähigkeit sicherstellen.
>
> **4. Übergabe & Prüfung:** Kontrolliere den erzeugten Diff auf versehentliche Änderungen, Secrets, generierte Dateien und lokale Pfade. Gib einen standardkonformen Unified Diff (`micro-ap-<ID>.patch`) mit korrekten relativen Pfaden (inkl. neuer/gelöschter Dateien) aus. Markiere nicht ausführbare Geräte-, Instrumentierungs-, Migrations- oder Integrationstests explizit als offen.
>
> **5. STOPP-Regel:** Wenn Voraussetzungen oder Architekturfreigaben (Gates/ADRs) fehlen, das bestehende Datenformat unklar ist, ein Baseline-Fehler die isolierte Umsetzung verhindert oder kein sicherer Rollback gewährleistet werden kann, implementiere **nicht** auf Verdacht. Melde stattdessen Befund, konkrete Blockade und den kleinsten nötigen nächsten Schritt.
>
> **Erforderliches Abschlussformat:**
> - `STATUS`: `PATCH ERSTELLT` | `BLOCKIERT`
> - `AP-ID`: `<ID>`
> - `BASIS`: `<Verwendete Dateibasis / Commit falls bekannt>`
> - `DATEIEN`: `<Geänderte, neue, gelöschte Dateien + Kurzbeschreibung>`
> - `PATCH-DATEI`: `micro-ap-<ID>.patch` (als Unified Diff)
> - `TESTS`: `AUSGEFÜHRT` | `FEHLGESCHLAGEN` | `NICHT AUSFÜHRBAR` (mit echtem Grund/Ergebnis)
> - `RISIKEN / OFFENE PUNKTE`: `<Bekannte Risiken & offene Geräte-/Integrationstests>`
> - `MANUELLE ÜBERNAHME`: `<Kurze Hinweise zum Anwenden und lokalen Testen>`

---

## 2. Abhängigkeits- und Freigabeplan

> **Hinweis:** Abhängigkeiten definieren die fachliche Reihenfolge. Jeder Patch muss lokal übernommen und getestet sein, bevor ein abhängiger Micro-AP gestartet wird.

### Reihenfolge

1. **M00** – Projekt-/Buildbaseline und Befundinventar.
2. **V01–V15** – Vault-Kryptografie, Header-/Recovery-Formate und Migration. Zuerst Format- und Referenztests, danach Implementierungen, dann Migration.
3. **S01–S10** – Lockzustand und SecretStore. Der bisherige Schlüsselzugriff bleibt kompatibel, bis die abhängigen Konsumenten einzeln migriert und geprüft sind.
4. **D01–D14** – Dokumentverschlüsselung und Backup/Restore. **D02 (DB-Architektur-ADR)** muss vor DB12/DB13 und jeder Datenbankverschlüsselungs-Migration ausdrücklich freigegeben sein.
5. **DB01–DB14** – Room-Baseline, Migrationen, FTS, Geldbeträge, DB-Verschlüsselung und Performance. Datenbankversionen strikt sequenziell erhöhen; nie zwei DB-Migrationsaufträge parallel bearbeiten.
6. **P01–P13** – P2P. Benötigt die bestätigte Transport-/Pairing-ADR sowie kompatible Dokumentstreams und den SecretStore.
7. **C01–C09** – Cloud-Sync. Benötigt verschlüsselte Backupcontainer, SecretStore und eine ausdrücklich definierte Richtlinie für gesperrten Vault.
8. **L01–L11A bzw. L08B** – LLM. **L02 ist ein Freigabe-Gate**: Native Integration oder ehrlicher Rückbau; nicht beides implementieren.
9. **R01–R24** – Refactoring zuletzt, nach Stabilisierung der Sicherheitsgrenzen und Datenformate.

### Explizite Freigabepunkte – nicht selbst entscheiden

- **V05:** Soll TOTP als echte, kryptografisch wirksame zweite Schutzkomponente weitergeführt werden oder wird es entfernt? Keine bloße UI-Sperre als 2FA bezeichnen.
- **V08/V09:** Recovery-Dateiformat und Bedrohungsmodell müssen vor der Implementierung definiert und geprüft werden.
- **D02/DB11–DB12:** SQLCipher-/vollständige DB-Verschlüsselung oder bewusst begründete Feldverschlüsselung. FTS darf nicht unbemerkt sensible Klartextdaten offenlegen.
- **P01:** Browserportal nur behalten, wenn Zertifikatsvertrauen wirklich sicher und benutzbar verifiziert wird; ansonsten deaktivieren.
- **L02:** Pfad A (echte native Inferenz) oder Pfad B (Feature ehrlich deaktivieren/zurückbauen).
- **C04:** Google Drive mit sauberem OAuth und minimalen Scopes unterstützen oder den Provider zurückbauen.

---

## 3. Micro-APs – jeweils nur EINEN Auftrag ausführen

# M00 – Baseline und Bestandsinventar (AUDIT, keine Produktänderung) — **[ERLEDIGT]**

**Prompt:** Erfasse lesend die im Workspace vorhandene Dateibasis und führe – sofern in der Umgebung verfügbar – `./gradlew assembleDebug testDebugUnitTest` sowie vorhandene lokale Checks aus (andernfalls als nicht ausführbar kennzeichnen). Erstelle `docs/security/micro-ap-baseline.md` mit tatsächlichem Buildstatus, Grenzen der Umgebung, fehlenden/ungenutzten Tests, relevanten Modulen und offenen Befunden aus den ursprünglichen acht APs. Untersuche nur lesend und füge keine Produktlogik hinzu. Verifiziere insbesondere die aktuellen Implementierungen von VaultHeader/KDF/Recovery, `DocumentStorageService`, P2P-Server, CloudSync, Room-Version, LLM/JNI und Analytics. Markiere jeden Befund als bestätigt, widerlegt oder noch offen und nenne Pfad/Beleg. **Abnahme:** Auditdatei ist reproduzierbar und ehrlich; keine Anwendungslogik geändert. Fehlende Tool-Unterstützung oder Buildfehler werden dokumentiert, nicht kaschiert.

---

# V – Tresor, Schlüsselableitung und Recovery

## V01 – Vault-Krypto-Inventar und v1-Fixtures (AUDIT/TEST) — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Prompt:** Inventarisiere ausschließlich die aktuelle Vault-Krypto: `VaultHeader`, v1-Serialisierung, Salt/KDF-Parameter, KEK/DEK-Verpackung, Unlock-/Biometriepfade, Recovery, `activeDek` und alle Aufrufer. Lege – ohne produktive Logik zu ändern – synthetische Test-Fixtures für mindestens einen gültigen v1-Header, falsches Passwort und manipulierte Daten an. Dokumentiere das genaue Altformat, damit Kompatibilität später testbar ist. Gib keine echten Schlüssel aus. **Abnahme:** Tests/Fixtures reproduzieren das bestehende Format; keine Formatmigration und kein Entfernen von Altcode in diesem Schritt.

## V02 – Geprüfte Argon2id-Implementierung — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V01.  
**Prompt:** Ersetze ausschließlich die bisherige Argon2id-Engine durch eine gepflegte, versionsfixierte Bibliotheksimplementierung (z. B. geprüfte Bouncy-Castle-API, nach Kompatibilitätsprüfung). Behalte zunächst die exakt benötigte v1-Ableitung als klar getrennten Legacypfad, sofern sie für Entsperrung alter Vaults erforderlich ist. Für neue Parameter gilt mindestens `m=19 MiB`, `t=2`, `p=1`; Einheiten und API-Semantik verifizieren. Begrenze/validiere Parameter vor der speicherintensiven Ableitung und schütze vor parallelen RAM-Spitzen. Füge bekannte Referenzvektor-Tests hinzu. **Nicht tun:** Headerformat oder Migration ändern. **Abnahme:** Legacy-Fixture bleibt lesbar; korrekte Referenzvektoren; ungültige/überzogene Parameter werden früh abgelehnt.

## V03 – VaultHeader v2: Datenmodell und Parser — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V01, V02.  
**Prompt:** Entwirf und implementiere nur die typisierte v2-Headerstruktur samt strengem Parser/Validator. Speichere explizit Formatversion, KDF-/Algorithmus-ID, Parameter, Salt, IV/Nonce- und Taglängen sowie Vault-ID mit klar definierten Obergrenzen. Unbekannte Versionen, abgeschnittene Daten, unmögliche Längen und ungültige Parameter müssen abgewiesen werden. Bestehende v1-Daten weiter nur lesend unterstützen. Noch keine automatische v1→v2-Migration und keine Änderung an Biometrie oder TOTP. **Abnahme:** Roundtrip-Tests für v2, Negativtests für manipulierte/ungültige Header und Regressionstest für v1.

## V04 – Header-Authentifizierung und AAD — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V03.  
**Prompt:** Implementiere ausschließlich die AEAD-Operationen zum Verpacken/Öffnen des zufälligen 256-Bit-DEK im v2-Header. Nutze geprüfte AES-GCM/JCA- oder gleichwertige AEAD-Primitiven korrekt; pro Verschlüsselung neuen zufälligen Nonce/IV generieren und niemals wiederverwenden. Lege fest und teste, welche Vault-ID-, Versions- und KDF-Metadaten als AAD gebunden werden. Falscher Schlüssel, falscher AAD oder veränderter Tag müssen fehlschlagen, ohne Teilresultat auszugeben. Keine automatische Migration. **Abnahme:** gültiger Roundtrip; Manipulations- und Nonceformattests; keine geheime Materialausgabe in Logs.

## V05 – TOTP-Entscheidungsvorlage (AUDIT/GATE) — **[ADR ERSTELLT – ENTSCHEIDUNG OFFEN]**
**Prompt:** Untersuche die bestehende TOTP-/Base32-Implementierung, Speicherung des Secrets, UI-Gates und Beziehung zur DEK-Freigabe. Erstelle eine kurze ADR mit zwei Optionen: (A) TOTP entfernen und bestehende Klartext-Secrets geordnet migrieren/entfernen oder (B) überprüfbares Design, bei dem der zweite Faktor tatsächlich kryptografisch den DEK-Zugriff schützt. Berücksichtige RFC 6238, Rate-Limits, Replay, Gerätewechsel und Recovery. Nimm keine Codeänderung vor und entscheide nicht eigenmächtig zwischen den Produktoptionen. **Abnahme:** eindeutige Risiken, Aufwand und Nutzerfolgen; wartet auf Freigabe.

## V06 – TOTP nur nach freigegebenem Design testen/implementieren
**Abhängigkeit:** V05 und explizite Auswahl Option B; bei Option A diesen Auftrag überspringen.  
**Prompt:** Setze nur den in der freigegebenen ADR definierten TOTP-Kern um. Verwende RFC-6238-konforme HMAC-/Base32-Behandlung und offizielle Referenzvektoren einschließlich Zeitfenster. Vergleiche Codes konstantzeitig, begrenze Fehlversuche und verhindere Replay eines akzeptierten Codes im gleichen Zeitfenster. Speichere das Secret nicht im Klartext in Header/Prefs. Noch keine Änderung an UI oder Vault-Migration außer dem für den Kern zwingend nötigen, kompatiblen Adapter. **Abnahme:** Referenzvektoren und Negativtests grün; keine Behauptung echter 2FA, solange die Freigabearchitektur nicht vollständig integriert ist.

## V06A – TOTP entfernen (NUR nach Freigabe Option A) — **[BLOCKIERT – FREIGABE FEHLT]**
**Abhängigkeit:** V05 und explizite Freigabe zum Entfernen von TOTP; statt V06/V06B ausführen.  
**Prompt:** Entferne nur den freigegebenen TOTP-Pfad aus UI und Authentifizierungslogik. Vorhandene TOTP-Secrets müssen kontrolliert migriert/entfernt werden; lösche sie erst, wenn der neue Zustand nachweislich gespeichert wurde. Entferne keine anderen Authentifizierungs- oder Recovery-Wege. Aktualisiere Texte so, dass keine nicht mehr vorhandene 2FA behauptet wird. **Abnahme:** Tests für Altzustand, Migration, Neustart und TOTP-Deaktivierung; kein Klartext-Secret verbleibt.

## V06B – TOTP an die tatsächliche DEK-Freigabe binden (NUR Option B)
**Abhängigkeit:** V05/V06 und freigegebener Sicherheitsentwurf.  
**Prompt:** Integriere den bereits getesteten TOTP-Kern in den echten Vault-Unlock-/DEK-Freigabepfad der genehmigten ADR. Ein erfolgreiches Passwort allein darf den als 2FA geschützten Schlüssel nicht freigeben. Verhindere, dass andere öffentliche APIs oder ein UI-Callback diese Prüfung umgehen. Verknüpfe Replay-/Rate-Limit-Zustand mit S06, ohne parallele Backoff-Implementierung anzulegen. **Abnahme:** End-to-End-Negativtests für Passwort ohne TOTP, falschen/abgelaufenen/wiederverwendeten Code und korrekte Kombination; UI bezeichnet die Funktion erst danach als 2FA.

## V07 – BIP-39-Mnemonic-Validierung — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Prompt:** Isoliere die BIP-39-Funktionalität und prüfe Wortliste (genau 2048 Wörter), Entropie, Checksumme und Mnemonic-Validierung anhand offizieller Referenzvektoren. Korrigiere ausschließlich Fehler in diesem Modul; wenn Eigenbau-Logik die Referenzvektoren nicht zuverlässig erfüllt, ersetze sie durch eine geprüfte, kompatible Bibliothek. Erzeuge nur synthetische Testdaten. **Nicht tun:** Shamir, PDF/QR oder Headerformat verändern. **Abnahme:** gültige/ungültige Mnemonics, Grenzfälle und offizielle Vektoren sind getestet.

## V08 – Shamir-Share-Konsistenz — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Prompt:** Prüfe den vorhandenen Shamir-2-aus-3-Code und seine Hashspeicherung. Stelle durch Tests sicher, dass die drei gehashten Shares exakt dieselben Bytefolgen sind, die später an den Nutzer ausgegeben werden, und dass jedes zulässige Paar den ursprünglichen synthetischen DEK reproduziert. Verwende offizielle oder klar bezeichnete Referenzvektoren. Ersetze den Eigenbau, falls er nicht belastbar gegen Referenzen besteht. **Nicht tun:** Recovery-PDF oder Verschlüsselungsformat in diesem Schritt ändern. **Abnahme:** jedes gültige Paar rekonstruiert exakt denselben Schlüssel; vertauschte, gekürzte und manipulierte Shares scheitern.

## V09 – Recovery-Format als ADR (AUDIT/GATE) — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Prompt:** Entwirf ein versioniertes Recovery-Payload für ein Notfall-Kit, ohne es schon zu implementieren. Definiere Entropiequelle, KDF samt Parametern, Salt, AEAD, AAD, Nonce, Versionierung, Authentifizierungsfehler, Export-/Importworkflow und Schutz vor offline kopierten Kits. Ein sechsstelliger PIN reicht nicht als Schutz für ein offline kopierbares Kit. Begründe Passphrase-Anforderungen und Recovery bei Formatversionen. Beschreibe explizit, dass der rohe DEK niemals direkt in PDF, QR, UI, Clipboard oder Logs ausgegeben wird. **Abnahme:** ADR mit Angriffsmodell, Byteformat und Testfällen; Implementierung erst nach Freigabe.

## V10 – Recovery-Payload-Kern — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V09 und Freigabe der ADR.  
**Prompt:** Implementiere nur Encode/Decode des genehmigten Recovery-Payload-Formats als eigenständige, testbare Komponente. Schlüsselableitung und Authenticated Encryption ausschließlich über geprüfte Primitive. Versions-/Längenprüfung, falsche Passphrase, veränderte Ciphertextbytes und ungültiger Tag müssen kontrolliert scheitern. Nutze ausschließlich synthetische Schlüssel. Keine PDF-, QR- oder UI-Änderungen in diesem Micro-AP. **Abnahme:** bytegenaue Roundtrip-Tests, Negativtests und kein Roh-DEK in Fehlermeldungen/Logs.

## V11 – Standardkonforme QR-Kodierung — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V10.  
**Prompt:** Ersetze ausschließlich die derzeitige QR-Matrix-Erzeugung durch eine etablierte QR-Encoding-Bibliothek. Kodiert wird nur das freigegebene verschlüsselte Recovery-Payload, nie der Roh-DEK oder ein unverschlüsselter Schlüssel. Teste, dass ein unabhängiger QR-Decoder das Payload bytegenau zurückliest und beschädigte/zu große Inhalte kontrolliert behandelt werden. **Abnahme:** unabhängiger Decode-Roundtrip im Test; kein selbst gebautes Hash-Bit-Muster als QR-Code.

## V12 – Emergency-PDF sicherstellen — **[IMPLEMENTIERUNG ERLEDIGT – PDF-TEXTEXTRAKTION AM GERÄT OFFEN]**
**Abhängigkeit:** V10, V11.  
**Prompt:** Ändere ausschließlich die Emergency-Kit-PDF-Ausgabe: Entferne die bisherige Base64-Roh-DEK-Zeile und alle sonstigen direkten Schlüsselkopien. Das Dokument darf nur das genehmigte verschlüsselte Recovery-Payload/den passenden standardkonformen QR-Code und verständliche Anleitungen enthalten. Prüfe den erzeugten PDF-Textlayer und extrahierbaren Inhalt automatisiert auf Roh-DEK, Secret und unerwartete sensible Daten. UI muss Passphrase-/Recovery-Anforderungen klar erklären. **Abnahme:** PDF-Export- und Text-Extraktionstests; kein unverschlüsselter DEK in PDF-Bytes, OCR-Text, QR oder Logs.

## V13 – Biometrie und Keystore getrennt absichern — **[IMPLEMENTIERUNG ERLEDIGT – GERÄTETEST OFFEN]**
**Abhängigkeit:** V03/V04; gemeinsame Schlüsselzustandsintegration später mit S04/S05.  
**Prompt:** Prüfe und korrigiere nur die Keystore-Key-Erstellung und `BiometricPrompt`-Kryptooperation. Nutze `setUserAuthenticationRequired(true)` und passende Invalidierungsoptionen; StrongBox nur mit dokumentiertem, sicherem Fallback. API-abhängige Methoden nur ab den unterstützten API-Leveln aufrufen. Die Authentifizierung muss die tatsächliche kryptografische Entschlüsselungsoperation schützen, nicht nur eine UI-Abfrage bestätigen. **Nicht tun:** globalen LockManager implementieren. **Abnahme:** Tests/Mocks für Erfolg, Abbruch, Invalidierung und API-Fallback; echte Geräteprüfungen separat ausweisen.

## V14 – Atomare v1→v2-Headermigration — **[BLOCKIERT – V2-LAUFZEITINTEGRATION FEHLT]**
**Abhängigkeit:** V02–V04 und vorhandenes v1-Fixture aus V01.  
**Prompt:** Implementiere ausschließlich die Header-Migration. Lies v1 mit der alten, kompatiblen Ableitung, prüfe erfolgreichen Unlock, verpacke denselben DEK in v2 und schreibe v2 zunächst atomar in eine temporäre Datei/Transaktion. Lies den neuen Header erneut ein und prüfe ihn kryptografisch, bevor das Original ersetzt wird. Bei jedem Fehler bleibt v1 unverändert; keine automatische Neuinitialisierung und kein Datenverlust. Lege erzwungene Fehlerpfade im Test fest. **Abnahme:** Erfolgs- und Rollback-Test mit v1-Fixture, Idempotenz und Nachweis, dass falsches Passwort den Originalheader nicht ändert.

## V15 – Backup-Regeln für Vault-Secrets — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Prompt:** Prüfe `backup_rules.xml` und `data_extraction_rules.xml` für Cloud-Backup sowie Geräteübertragung. Schließe `mydocanizer_vault_security.xml` und andere Keystore-gebundene Geheimnisse ausdrücklich aus, sofern kein freigegebenes, geprüftes verschlüsseltes Exportformat existiert. Entferne keine legitimen Nutzerdateien pauschal; überprüfe jedes Include/Exclude samt tatsächlichem Speicherort. **Abnahme:** automatisierte/strukturelle Tests oder dokumentierte Regelprüfung decken beide Plattformmechanismen ab; keine geheime Vault-Pref wird versehentlich übertragen.

---

# S – LockManager, sichere Sitzung und SecretStore

## S01 – Lock- und Secret-Inventar (AUDIT) — **[ERLEDIGT – AUDIT DOKUMENTIERT]**
**Prompt:** Inventarisiere alle App-/Vault-Lock-Flags, `unlockApp`, `unlockVault`, `lockVault`, automatische Sperrung, Biometrie, `activeDek`-Zugriffe und geheime Werte in Prefs/DB/Dateien. Zeichne den bestehenden Zustandsfluss mit Aufrufstellen auf und schlage eine eindeutige Trennung bzw. Beziehung zwischen App-Lock und Vault-Lock vor. Noch keine produktive Änderung. **Abnahme:** Datei `docs/security/lock-secret-inventory.md`; jeder gefundene Geheimnisspeicher und jeder Unlock-Pfad ist referenziert.

## S02 – LockManager als Zustandsautomat — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** S01.  
**Prompt:** Implementiere einen testbaren `LockManager` mit typisiertem, zentralem Zustand und ausdrücklich erlaubten Übergängen. Beginne beim Prozessstart fail-closed, wenn ein Vault eingerichtet ist. Activity/ViewModel dürfen keine konkurrierenden Wahrheits-Flags mehr für denselben Vault-Lock erzeugen; halte vorhandene APIs über Adapter kompatibel. Noch keine automatische Hintergrundsperre und keine SecretStore-Migration. **Abnahme:** reine Unit-Tests der Zustandsübergänge; kein unkoordinierter zweiter Manager.

## S03 – Auto-Lock-Lifecycle — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** S02.  
**Prompt:** Verbinde den LockManager mit `ProcessLifecycleOwner` und der vorhandenen Auto-Lock-Einstellung. Messe Zeit mit `SystemClock.elapsedRealtime`; definiere und teste `0` als sofortige Sperre entsprechend der Produktsemantik. Bei Rückkehr aus dem Hintergrund muss vor Anzeige sensibler Inhalte der aktuelle Lockzustand abgefragt werden. Keine Speicherung von DEK oder Passphrase, um die Rückkehr zu beschleunigen. **Abnahme:** Tests für ON_STOP/ON_START, Timeoutgrenze und Prozessneustart; keine UI-Freigabe vor Lockprüfung.

## S04 – Unlock-Bypässe entfernen — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** S02 und V13.  
**Prompt:** Verfolge `AppLockScreen`, `unlockApp()` und alle Aufrufer. Entferne Pfade, die ohne erfolgreiches, überprüfbares Authentifizierungsresultat entsperren – insbesondere Null-Activity-/Boolean-Default-/UI-only-Pfade. Erfolg muss aus dem tatsächlich erfolgreichen Passwort-/Vault- oder `BiometricPrompt.CryptoObject`-Vorgang kommen. Ändere nur Authentifizierungsübergänge und ergänze Negativtests. **Abnahme:** kein erfolgreicher Unlock bei abgebrochener, fehlgeschlagener oder fehlender Authentifizierung.

## S05 – DEK-Leases an Lockstatus binden — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V04 und S02.  
**Prompt:** Ersetze unkontrollierte öffentliche DEK-Getter schrittweise durch eine begrenzte `withUnlockedKey`-/Key-Lease-Schnittstelle oder ein gleichwertig überprüftes Modell. Schlüsselmaterial darf nur während gültigem Unlock-Zustand genutzt werden; mutable Kopien soweit technisch möglich nach Gebrauch bereinigen. Synchronisiere zusammengesetzte Operationen mit `Mutex`/gleichwertiger Koordination. Definiere, was bei Lock während einer Dateioperation geschieht. Migriere nur die Aufrufstellen, die für den Build dieses Schritts nötig sind, und liste verbleibende Verbraucher auf. **Abnahme:** Tests, dass gesperrter Vault keine Entschlüsselung erlaubt und paralleler Lock/Unlock keinen alten Schlüsselzugriff hinterlässt.

## S06 – Fehlversuche und Backoff — **[IMPLEMENTIERUNG ERGÄNZT – TESTLAUF OFFEN]**
**Abhängigkeit:** S02.  
**Prompt:** Implementiere nur die gemeinsame Backoff-Logik für Passwort-/TOTP-/Recovery-Fehlversuche, wobei vorhandene Authentifizierungswege nicht umgangen werden dürfen. Schreibe persistent und atomar die notwendigen Zähler/Deadline-Informationen; nutze monotone Zeit während einer Sitzung und definiere konservatives Verhalten nach Prozess-/Geräteneustart. Teste die festgelegte Folge: nach fünf Fehlversuchen 30 Sekunden, danach 1, 5 und 15 Minuten. **Abnahme:** Grenzwerte, parallele Fehlversuche, Neustart und Restzeit getestet; Dokumentation, dass lokaler Backoff Defense-in-Depth ist.

## S07 – FLAG_SECURE und sensitive Screens — **[IMPLEMENTIERUNG VORHANDEN – GERÄTETEST OFFEN]**
**Prompt:** Setze `FLAG_SECURE` rechtzeitig vor dem Rendern sensibler Inhalte und steuere Debug-Ausnahmen ausschließlich über ein explizites Build-Flag bzw. `BuildConfig.DEBUG`, nicht über Emulatorerkennung. Sorge dafür, dass Recents/Screenshots und offene Detailansichten nach Sperrung keine sensiblen Inhalte mehr zeigen. Keine Navigation oder Sicherheitsarchitektur nebenbei refactoren. **Abnahme:** dokumentierter manueller Testplan für Screenshot, Recents, App-Hintergrund, Activity-Neustart und Lock auf einer offenen Detailseite.

## S08 – SecretStore-Grundkomponente — **[KERNKOMPONENTE ERSTELLT – INSTRUMENTATIONSTEST OFFEN]**
**Prompt:** Baue einen testbaren `SecretStore` für langlebige Geheimnisse. Verwende AES-GCM mit einem nicht exportierbaren Android-Keystore-Key, eindeutiger Nonce, Formatversion und Integritätsprüfung. Dokumentiere die Authentifizierungsbindung des Keys und API-Level-Fallbacks. Ungültiger Tag oder Keystore-Fehler darf niemals Klartextfallback auslösen. In diesem Schritt nur API und Kernkomponente samt synthetischen Tests erstellen; bestehende Prefs noch nicht migrieren. **Abnahme:** Roundtrip, falscher Tag, fehlender/invalidierter Key und Fehlerpfade getestet.

## S09 – Legacy-Secrets verlustfrei migrieren — **[BLOCKIERT – S08-TESTLAUF OFFEN]**
**Abhängigkeit:** S08.  
**Prompt:** Migriere nur die in S01 konkret aufgelisteten Klartext-Secrets in den SecretStore. Pro Wert: erkennen, verschlüsselt schreiben, entschlüsselten Roundtrip verifizieren und erst danach Original entfernen. Migration muss idempotent sein und bei Fehler den ursprünglichen Wert erhalten, ohne unkontrollierte doppelte Klartextkopien zu hinterlassen. `sync_master_password` erst entfernen, wenn die in D/P/C betroffenen Verbraucher nachweislich umgestellt sind; sonst nur dokumentierte Zwischenstufe mit klarer Restabhängigkeit. **Abnahme:** erster Start, zweiter Start, Prozessabbruch und Keyfehler getestet.

## S10 – Externe Navigationsextras validieren — **[WHITELIST UND POLICY-TESTS ERGÄNZT – INTEGRATIONSTEST OFFEN]**
**Prompt:** Prüfe die exportierte `MainActivity` sowie Widget-/Intent-Aufrufstellen. Validiere `EXTRA_START_TAB`, `EXTRA_OPEN_ACTION`, URIs und weitere externe Extras gegen feste Whitelists. Während der Vault gesperrt ist, dürfen externe Aktionen höchstens sicher zwischengespeichert und erst nach erfolgreichem Unlock verarbeitet werden. Ungültige Extras ignorieren und nicht später wiederverwenden. **Abnahme:** Tests für gültige, unbekannte, fehlerhafte und während Lock eintreffende Extras; kein Lock-Bypass.

---

# D – Dokumentdateien, Index, Backup und Restore

## D01 – Datenfluss-Inventar (AUDIT)
**Prompt:** Verfolge lesend sämtliche PDF-/Bild-/Scan-/OCR-/Viewer-/Share-/Export-/P2P-/Cloud-/Backup-/Restore-/Cachepfade sowie Speicherorte und Datenbankfelder mit sensiblen Metadaten. Markiere pro Pfad, ob Inhalt/Index/Metadaten derzeit in Klartext oder verschlüsselt vorliegen und welcher Aufrufer `isEncrypted`/`passwordKey` steuert. Keine Produktänderung. **Abnahme:** `docs/security/document-data-flow.md` mit Pfad- und Klassenbelegen, inklusive benutzerdefinierter Speicherorte.

## D02 – Datenbankverschlüsselung: Architekturentscheidung (AUDIT/GATE)
**Prompt:** Untersuche selbst `AppDatabase`, Room-Provider, FTS, WAL/SHM und alle DB-Öffnungspfade und erstelle darauf eine ADR, die vollständige Datenbankverschlüsselung (SQLCipher-kompatible, aktuell unterstützte Room-Integration) gegen Feldverschlüsselung abwägt. Bewerte OCR-FTS, WAL/SHM, Key-Lifecycle, Migration vorhandener DBs, Backup, Gerätekompatibilität und Wiederherstellung. Entscheide nicht stillschweigend; wenn Produktfreigabe oder Proof-of-Concept fehlt, liefere die Optionen und stoppe. AP-7 darf keine widersprüchliche Technik parallel auswählen. **Abnahme:** freigabefähige ADR mit Test-/Rollback-Plan, keine DB-Änderung.

## D03 – Zentrale KeyProvider-/CryptoService-Schnittstelle
**Abhängigkeit:** freigegebene Vault-v2-API aus V04 und D02-Entscheidung, soweit DB-Schlüssel betroffen sind.  
**Prompt:** Erstelle eine eng umrissene, testbare `CryptoService`-/`KeyProvider`-Schnittstelle für dokumentierte Zwecke. Nutze geprüfte HKDF-Implementierung, klare Domain-Separation für Datei, Index, Backup und gegebenenfalls DB; binde Vault-ID und Purpose ein. Ziehe keine Keys aus `userPasswordKey`-/`syncPasswordKey`-Strings. In diesem Schritt noch keine umfassende Umstellung aller Aufrufer; einen bestehenden Verbraucher als Kompatibilitätsnachweis migrieren und übrige Verbraucher inventarisieren. **Abnahme:** deterministische synthetische Tests, Purpose-Isolation und keine Schlüsselwerte in Logs.

## D04 – Streaming-Dateiformat für neue Dokumente
**Abhängigkeit:** D03.  
**Prompt:** Implementiere das versionierte Streaming-AEAD-Dateiformat als isolierte Komponente, bevorzugt auf geprüfter Bibliothek wie Tink, sofern sie mit der genehmigten Schlüsselarchitektur integrierbar ist. Definiere Datei-ID, Vault-ID, Formatversion und relevante Metadaten als authentifizierten Kontext; nutze eine dokumentierte Segmentgröße um 1 MiB. Erkenne beschädigten, abgeschnittenen oder mit falschem AAD versehenen Stream. Noch keine Bestandsmigration. **Abnahme:** Roundtrip auch für große synthetische Dateien und Negativtests; kein `ByteArray` für die Gesamtdatei.

## D05 – `secureSave` auf verschlüsselte Ablage umstellen
**Abhängigkeit:** D04.  
**Prompt:** Stelle ausschließlich die zentralen `secureSave`-/Import-Schreibpfade auf standardmäßige direkte Streaming-Verschlüsselung um. Entferne Aufrufoptionen, mit denen versehentlich Klartext dauerhaft abgelegt wird; falls eine Übergangs-API erforderlich ist, sperre sie explizit und dokumentiere sie. Schreibe in eine zufällige temporäre verschlüsselte Zieldatei, verifiziere Abschluss/Authentifizierung und committe erst danach atomar. **Abnahme:** Tests beweisen, dass normale Importpfade keine persistente Klartextkopie anlegen; vorhandene Ziele bleiben intakt.

## D06 – Viewer, OCR, Share und Vorschau anbinden
**Abhängigkeit:** D04, D05.  
**Prompt:** Migriere nur die Konsumenten Viewer, Vorschau und OCR-Re-Run auf kontrollierte entschlüsselnde Streams bzw. `ContentProvider`/Pipe. Wenn eine API eine Datei verlangt, erstelle eine kurzlebige app-private Arbeitskopie und räume sie nach Erfolg, Abbruch und Prozesswiederherstellung soweit möglich auf. Teilen an externe Apps muss korrekt darauf hinweisen, dass Empfängerkopien außerhalb der App liegen können. Export/P2P/Cloud noch nicht in diesem Schritt. **Abnahme:** Dateiinhalt stimmt; kein dauerhaftes Klartextcache nach regulärem Abschluss; Fehler-/Abbruchtests.

## D07 – Verschlüsselungsstatus aus Realität ableiten
**Abhängigkeit:** D04/D05.  
**Prompt:** Entferne in den betroffenen Dokumentpfaden die Semantik, nach der `isEncrypted` durch einen Bool-Parameter, Dateiendung oder Annahme des Aufrufers gesetzt wird. Der Status darf nur durch erfolgreiches Erkennen des unterstützten Formats und eine gültige Authentifizierung/Integritätsprüfung belegt werden. Fehlende/ungültige Header müssen als Fehler behandelt werden, nicht als Klartext oder verschlüsselte Datei erraten werden. **Abnahme:** Tests für falschen Status, Dateiendungs-Spoofing, abgeschnittenen Header und gültige Altdateien.

## D08 – Idempotente Bestandsmigration
**Abhängigkeit:** D04–D07.  
**Prompt:** Implementiere einen resumierbaren `WorkManager`-Migrationsjob für vorhandene Dokumentdateien. Pro Datei Original lesen, verschlüsselte temporäre Datei erstellen, diese durch Entschlüsseln verifizieren und Länge/Hash vergleichen, dann atomar committen und erst danach Quelle entfernen, sofern der dokumentierte Speicherplan das erlaubt. Speichere nur nötigen, nicht sensitiven Migrationsfortschritt; Prozessabbruch muss sichere Wiederholung erlauben. **Abnahme:** Tests für Abbruch an jeder Phase, Wiederholung und Fehler; Original bleibt bei jedem Verifikationsfehler erhalten.

## D09 – Kollisionssichere Ablage
**Prompt:** Stelle die interne Dateiablage auf kryptografisch zufällige IDs/UUIDs um, ohne Titel/Absender als Dateisystem-Sicherheitsgrenze zu nutzen. Zielanlagen müssen Create-new-/atomare Semantik verwenden und bestehende Zieldateien nie still überschreiben oder vorab löschen. Anzeigenamen bleiben Metadaten. Ändere nur die zentrale Ablagekomponente und die zwingend nötigen Aufrufer. **Abnahme:** Tests für gleiche Namen, parallele Imports, vorhandenes Ziel und Abbruch nach Teilwrite.

## D10 – Verschlüsselter Backupcontainer
**Abhängigkeit:** D03/D04.  
**Prompt:** Implementiere das genehmigte versionierte Backupcontainer-Format mit streamender AEAD-Verschlüsselung. Manifest und sensible Metadaten müssen geschützt sein; kein dauerhaftes Klartext-ZIP oder Klartext-Index als Zwischenschritt. Lege Größen-/Eintragsgrenzen und Formatkennung fest. Bestehende Restorelogik nur über klaren Adapter kompatibel halten; noch keine Massenmigration alter Backups. **Abnahme:** Roundtrip, große Datei ohne OOM, falscher Schlüssel, manipulierte Metadaten und abgeschnittener Container.

## D11 – Restore: Archive und Pfade validieren
**Abhängigkeit:** D10.  
**Prompt:** Implementiere ausschließlich die Vorabvalidierung beim Restore. Normalisiere Pfade und erzwinge, dass jeder Eintrag kanonisch im Zielverzeichnis verbleibt. Lehne absolute Pfade, `..`, Separatorvarianten, kodierte Traversalversuche, doppelte IDs, übergroße Einträge, zu viele Einträge und unzulässige Kompressionsverhältnisse ab. Daten nicht vor Abschluss der Validierung in die produktive Datenbank übernehmen. **Abnahme:** Zip-Slip-/Zip-Bomb-/Traversal- und Größenbegrenzungstests.

## D12 – Restore: atomarer Commit und verständlicher Report
**Abhängigkeit:** D11.  
**Prompt:** Sorge dafür, dass Restorevalidierung und Schreiben transaktional bzw. über einen Staging-Bereich erfolgen. Ein beschädigter AEAD-Tag, fehlendes Manifest oder fehlerhafte Metadaten darf keinen Teilimport in der Produktivdatenbank hinterlassen. Vermeide blindes `REPLACE`; definiere Konfliktregeln über neue IDs/Merge. UI erhält `wiederhergestellt / übersprungen / fehlgeschlagen` plus nicht sensitive Ursachen. **Abnahme:** erzwungene Fehler in jeder Phase hinterlassen keinen inkonsistenten Zwischenstand.

## D13 – Unverschlüsselten Mirror entfernen und Backupregeln prüfen — **[IMPLEMENTIERUNG ERGÄNZT – TESTLAUF OFFEN]**
**Prompt:** Prüfe den Auto-Mirror/Klartextbackup-Pfad und `backup_rules.xml`/`data_extraction_rules.xml`. Entferne den unverschlüsselten Mirror oder sperre ihn bis zu einer ausdrücklich bestätigten, sichtbaren Nutzerfreigabe und dokumentierten Warnung. Schließe Cache, temporäre Klartextdateien, geheime Prefs, Logs, lokale Backupcontainer und Modelle passend aus; exportiere keine sensiblen Speicherorte pauschal bei Geräteübertragung. **Abnahme:** alle Regeln einzeln begründet; keine unbeabsichtigte Klartextübertragung.

## D14 – PDF-Textlayer-Verhalten belegen — **[QUELLCODE AUDITIERT – EXTRAKTORTEST ERGÄNZT, TESTLAUF OFFEN]**
**Prompt:** Prüfe nur, ob von der App erzeugte Scan-PDFs einen durchsuchbaren Textlayer enthalten oder ausschließlich Bilder speichern. Erstelle einen Test mit einem PDF-Text-Extractor. Falls ein Textlayer bereits Produktanforderung ist und fehlt, lege zuerst einen separaten Minimalimplementierungsplan vor; implementiere ihn nicht nebenbei mit der Dokumentverschlüsselung. Dokumentiere andernfalls eindeutig, dass Suche über Room/FTS erfolgt. **Abnahme:** tatsächliches Verhalten ist automatisiert belegt und dokumentiert.

---

# P – P2P: TLS, Pairing, Server und Transfers

## P01 – Protokoll-Audit und Transport-ADR (AUDIT/GATE)
**Prompt:** Untersuche die vollständige P2P-Verbindung von Client bis Server inklusive direktem `ServerSocket`, HTTP-Parser, Authentifizierung, CA-/Portal-Endpunkten, CORS, Upload, Download und Sessionmanagement. Erstelle eine ADR für TLS mit pro Installation erzeugtem Zertifikat und überprüftem SHA-256-Fingerprint sowie für den Pairing-Handshake. Entscheide gesondert, ob das Browserportal sicher unterstützt werden kann. Noch keine Protokollimplementierung. **Abnahme:** Datenflussdiagramm, bestätigte Risiken, gewählte Architekturvorschläge und offene Freigaben.

## P02 – TLS nur auf Serverseite
**Abhängigkeit:** P01, freigegebene Transport-ADR.  
**Prompt:** Implementiere die TLS-Terminierung in der Serverkomponente gemäß ADR. Der Socket darf bei fehlender Zertifikats-/TLS-Konfiguration nicht auf Klartext zurückfallen. Noch nicht den Client umstellen: falls dadurch der Build oder Tests temporär nicht bestehen, halte einen klar isolierten, nicht produktiv erreichbaren Adapter nur so lange wie nötig und dokumentiere ihn; keine Release-Konfiguration darf Klartext akzeptieren. **Abnahme:** Integrationstest beweist TLS und Ablehnung von Klartextverbindungen.

## P03 – Client-TLS und Fingerprint-Pinning
**Abhängigkeit:** P02.  
**Prompt:** Stelle ausschließlich P2P-Clientverbindungen auf TLS gemäß ADR um. Der Fingerprint muss über einen zweiten, vertrauenswürdigen Pairingkanal verifiziert werden; unbekanntes oder gewechseltes Zertifikat darf nicht still akzeptiert werden. Teste gültigen Fingerprint, fremdes Zertifikat, Zertifikatswechsel und erneutes Pairing. Browser-Pinning nicht als durch App-Code automatisch gesichert darstellen. **Abnahme:** kein Klartext-Fallback und alle Zertifikatsnegativtests bestehen.

## P04 – Pairing-Handshake
**Abhängigkeit:** P01/P03 und S08, soweit gemeinsame Pairing-Secrets betroffen sind.  
**Prompt:** Implementiere nur das freigegebene Pairingverfahren mit gepflegter PAKE-Bibliothek oder korrekt verifiziertem ephemeral ECDH plus Short Authentication String. Keine Eigenbau-Kryptokonstruktion und keine PIN→PBKDF2-Lösung mit statischem Salt als alleinige gegenseitige Authentifizierung. Pairingcode einmalig und höchstens drei Minuten gültig. Geheimnisse in SecretStore, nicht in Logs. **Abnahme:** erfolgreicher Pairing-Roundtrip, falsche SAS/PIN, abgelaufener und wiederverwendeter Code.

## P05 – HTTP-Parsing und Ressourcenlimits
**Abhängigkeit:** P01.  
**Prompt:** Prüfe, ob eine gepflegte HTTP-Serverbibliothek mit minSdk und TLS-Architektur kompatibel ist. Nutze sie, wenn dadurch kein neues Sicherheitsproblem entsteht; andernfalls härte den bestehenden Parser eng begrenzt. Setze Limits für Request-Line, Headeranzahl/-größe, Body, Read-/Idle-Timeouts, Verbindungen und parallele Jobs. Vor Authentifizierung höchstens 4 KiB Body. Unerwartetes `Transfer-Encoding`, widersprüchliche Längen und abgeschnittene Requests ablehnen. **Abnahme:** Tests für oversized header/body, Slowloris-/Timeoutfall und ungültige Content-Length.

## P06 – Große Datei streamend übertragen
**Abhängigkeit:** D04, P03, P05.  
**Prompt:** Migriere ausschließlich Upload-/Download-Payloads auf Streaming; keine `ByteArray(contentLength)`-Allokation für große Dateien. Exakt erwartete Bytes lesen, frühen EOF als Fehler behandeln und nur vollständig authentifizierte/validierte Dateien in Datenbank/Manifest aufnehmen. Integriere das verschlüsselte AP-Dokumentformat ohne unverschlüsselte persistente Zwischenkopie. **Abnahme:** große synthetische Datei, Verbindungsabbruch, abgeschnittener Body und Integritätsfehler werden sicher behandelt.

## P07 – Replay-Schutz, Sessionablauf und Rate-Limits
**Prompt:** Ergänze den P2P-Authpfad um einen thread-sicheren Nonce-Replay-Cache pro gekoppeltem Gerät mit TTL. Wiederverwendete Nonces ablehnen; Zeitstempel allein zählen nicht als Replay-Schutz. Kopplungs-/Authfehler pro IP/Gerät begrenzen und Sessions nach 15 Minuten Inaktivität beenden. Bearer-Tokens ausschließlich im Authorization-Header. **Abnahme:** parallele Wiederholung, abgelaufene Session, viele falsche Codes und fehlende/abgelaufene Nonce getestet.

## P08 – IP-CIDR und Schnittstellenbindung
**Prompt:** Ersetze String-Präfixprüfungen durch eine echte IPv4-/IPv6-CIDR-Prüfung. Behandle IPv4 privat korrekt (`10/8`, `172.16/12`, `192.168/16`) sowie IPv6 ULA `fc00::/7`; Link-local `fe80::/10` nur wenn der konkrete Anwendungsfall es benötigt. Binde den Server an die ausgewählte WLAN-Schnittstelle; Loopback nur im Debug-Modus. **Abnahme:** Tests für Grenzen, fremde Netze, IPv6, WLAN-Wechsel und Interface-Verlust.

## P09 – Sichere Upload-Ziele
**Abhängigkeit:** P06.  
**Prompt:** Erzeuge Dateiziele ausschließlich intern aus zufälligen IDs. Originalnamen nur als validierte Metadaten speichern und `Content-Disposition` sicher verarbeiten. Schreibe in eine zufällige temporäre Datei, prüfe Größe und Integrität und committe atomar zu einem neuen Ziel. Niemals bestehende Dateien vorab löschen/überschreiben. **Abnahme:** Tests für `/`, `\\`, `..`, URL-Encoding, doppelte Namen, Kollision und Fehler während Teilwrite.

## P10 – Schwache Kryptofallbacks entfernen
**Prompt:** Suche ausschließlich in P2P-/Sync-Krypto nach Fallbacks wie `encryptString`, die aus ungültigen Schlüsselstrings durch SHA-256 oder ähnliche Heuristik einen Ersatzschlüssel erzeugen. Ungültige Key-/Nonce-/Payloadformate müssen Fehler auslösen, nicht auf schwächere Verschlüsselung wechseln. Migriere nur nachgewiesene Aufrufer auf die geprüften APIs; kein globales Refactoring. **Abnahme:** Negativtests für ungültigen Schlüssel und kein unsicherer Fallbackpfad.

## P11 – Serverlebenszyklus und Android-Berechtigungen
**Prompt:** Entferne `runBlocking` nur in den P2P-Request-/Serverpfaden durch strukturierten Coroutine-Lebenszyklus. Bei Serverstop müssen Jobs, Sockets und Streams zuverlässig beendet werden. Prüfe nötige Foreground-Service-Typen/Berechtigungen und aktuelle Laufzeitlimits der Ziel-API; Service nur während aktiver Aufgabe mit sichtbarer Benachrichtigung und Stoppmöglichkeit. Entferne WLAN-/Multicastberechtigungen nur nach Nutzungsprüfung. **Abnahme:** Stop/Cancel/Netzverlusttests; keine hängen gebliebenen Jobs oder unbegründeten Berechtigungen.

## P13 – Browserportal sicher deaktivieren oder sauber absichern
**Abhängigkeit:** P01–P03 und Entscheidung aus P01.  
**Prompt:** Setze ausschließlich die freigegebene Portalentscheidung um. Wenn ein Standardbrowser den Zertifikatsfingerprint nicht sicher und verständlich prüfen kann, deaktiviere das Browserportal standardmäßig und entferne die HTTP-/CA-Platzhalter-/Wildcard-CORS-Endpunkte. Falls das Portal behalten wird, muss seine komplette Verbindung tatsächlich über validiertes TLS laufen; keine stillen Ausnahme- oder Warnungsumgehungen. **Abnahme:** Tests belegen Deaktivierung oder nachvollziehbare TLS-Validierung; keine HTTP-Portal-URL und kein Wildcard-CORS.

## P12 – P2P-End-to-End-Sicherheitstests
**Abhängigkeit:** P02–P11.  
**Prompt:** Erstelle bzw. vervollständige echte In-Process-Client-/Servertests mit dem produktiven TLS-/Pairing-/Transferpfad. Teste Upload, Download, Manifest, Abbruch, Zertifikatswechsel, Replay, Ablauf, oversized Request, Pfad-Traversal, Zielkollision und 20 falsche Pairing-/Authversuche. Stelle Testergebnisse getrennt nach Unit-, Integration- und realem Gerätetest dar. Keine Behauptung, Android-Versionen seien kompatibel, wenn nicht geprüft. **Abnahme:** tatsächliche Testprotokolle; keine Klartext-HTTP-Ausnahme und kein Secret in Logs.

---

# L – Lokale KI und Modelldownloads

## L01 – Laufzeit- und APK-Inventur (AUDIT)
**Prompt:** Prüfe aktuell `LlamaCppInferenceEngine`, `LlmService`, JNI-Aufrufe, `System.loadLibrary`, CMake/NDK, `jniLibs`, Gradle-Artefakte, Modellkatalog, Downloads und tatsächlichen APK-Inhalt. Dokumentiere, was wirklich kompiliert und ladbar ist, und unterscheide echte Inferenz von regelbasierten Heuristiken. Noch keine Native-Bibliotheken hinzufügen und keine Funktion entfernen. **Abnahme:** reproduzierbares Inventar mit bestätigten Dateien/ABIs und offenen Punkten.

## L02 – Produktentscheidung Native Engine oder Rückbau (GATE)
**Prompt:** Liefere auf Basis von L01 eine Entscheidungsvorlage für (A) echte llama.cpp-Integration oder (B) standardmäßig deaktiviertes/entferntes LLM-Feature mit transparentem regelbasiertem Ersatz. Vergleiche Geräteabdeckung, APK-Größe, RAM, Lizenz/Notices, Buildkomplexität, 16-KB-Kompatibilität und Wartungsrisiken. Implementiere keinen der beiden Pfade und warte auf explizite Auswahl. Unabhängige Sicherheitsfehler im Download dürfen separat beschrieben werden. **Abnahme:** klare Optionsvorlage ohne Codepfad-Mischung.

## L03 – Modellkatalog und Quellenbelege
**Prompt:** Konsolidiere nur den Modellkatalog. Jeder Eintrag muss belastbare Quelle, exakte Dateigröße, SHA-256, Version, Lizenz, unterstützte Architektur und Dateiformat enthalten. Erfinde fehlende Werte nicht; unvollständige Einträge dürfen nicht aktiviert/heruntergeladen werden. Konsolidiere Duplikate ohne Modell-IDs/URLs unbemerkt zu verändern. **Abnahme:** Tests für Duplikate, fehlende Lizenz, fehlenden Hash und ungültige Größen.

## L04 – WorkManager-Downloader-Grundlage
**Abhängigkeit:** L03.  
**Prompt:** Stelle den Download in einen `CoroutineWorker` mit eindeutiger Arbeit pro Modell um, inklusive Fortschritt, Abbruch, begrenzten Retries, exponentiellem Backoff mit Jitter und sichtbarer Foreground-Info, soweit Laufzeit/Dateigröße dies erfordert. Prüfe vor Start den verfügbaren Speicher: exakte Modellgröße plus mindestens 20 % Reserve. Standardmäßig nur ungemessene/WLAN-Netze; Mobilfunk nur nach bewusstem Opt-in. Noch keine komplexe Resume-Logik. **Abnahme:** Worker-/TestDriver-Tests für Constraints, Abbruch, Retry, Platzmangel.

## L05 – Modellintegrität und private Ablage
**Abhängigkeit:** L03/L04.  
**Prompt:** Validiere nach Download exakte Länge, SHA-256 und GGUF-Magic/Grundstruktur gegen den Katalog. Bei jeder Abweichung Datei verwerfen und Modell nicht aktivieren. Redirects nur zu erlaubten HTTPS-Hosts. Speichere nur app-privat; entferne Kopien in öffentlichen `Downloads`-Ordnern und automatische Suchen dort. Für manuelle Modelle Android Storage Access Framework nutzen und nach Import prüfen. **Abnahme:** falscher Hash, Länge, Redirect, beschädigtes GGUF und öffentlicher Speicherpfad getestet.

## L06 – Download-Resume korrekt implementieren
**Abhängigkeit:** L04/L05.  
**Prompt:** Implementiere nur den Resume-Mechanismus mit `Range`, `ETag`/`If-Range`, `200`/`206`/`416`, `Content-Range` und erwarteter Partial-Dateigröße. Ungültiger Zustand muss sauber neu starten, ohne Bytes doppelt anzuhängen. Nach begrenzten Versuchen einen eindeutigen Fehler melden. **Abnahme:** MockWebServer-Tests für korrekten Resume, Server ignoriert Range, 416, ETag-Wechsel, Abbruch und beschädigte Partialdatei.

## L07 – Ehrliche Laufzeitmodi und Datenschutz
**Prompt:** Stelle sicher, dass UI, APIs, Logs und Telemetrie klar zwischen `Regelbasiert` und `Lokales Modell` unterscheiden. Keine Konfidenz wie `0.98`, Token-/Durchsatzmetriken oder „verifiziert“-Meldungen ohne echte Messung/Prüfung. Tokens/s nur aus real gezählten Tokens und gemessener Zeit. Keine Dokument-/OCR-Inhalte an entfernte Provider oder Telemetrie senden, ohne separat gekennzeichnete und ausdrückliche Nutzerfreigabe. **Abnahme:** Tests prüfen sichtbaren Modus, Heuristik-Fallback und fehlende/gescheiterte Engine; keine irreführende Inferenzbehauptung.

## L08A – Native Build reproduzierbar einbinden (NUR Pfad A)
**Abhängigkeit:** L02, explizit freigegebener Pfad A.  
**Prompt:** Binde ein geprüftes, fest versioniertes llama.cpp-Commit/Submodul mit dokumentierten Lizenzen, CMake/NDK-Konfiguration und nachvollziehbarem Build ein. Keine undokumentierten vorgefertigten `.so`-Dateien und keine gleichzeitige Downloader-/UI-Refaktorierung. Baue zuerst nur die freigegebenen ABIs und dokumentiere exakt die erzeugten Artefakte. **Abnahme:** reproduzierbarer Gradle-/Native-Build mit dokumentiertem Upstream-Commit und Lizenzen; noch keine Behauptung funktionierender Inferenz.

## L09A – JNI-Modelllebenszyklus (NUR Pfad A)
**Abhängigkeit:** L08A.  
**Prompt:** Implementiere den minimalen JNI-Lebenszyklus für Modell laden/entladen, Fehler zurückmelden und Ressourcen freigeben. Schütze vor parallelem Laden und ungültigen Modellpfaden; native Fehler dürfen nicht unkontrolliert den Prozess beenden. Tokenstreaming, Toolrouting und UI bleiben außerhalb dieses Micro-AP. **Abnahme:** instrumentierte Tests für erfolgreiches Laden/Entladen, ungültige Datei, doppeltes Laden und Cleanup.

## L10A – Echte Inferenz, Abbruch und Ressourcenlimits (NUR Pfad A)
**Abhängigkeit:** L09A.  
**Prompt:** Ergänze tatsächliche Token-Inferenz mit Callback/Streaming, Abbruch, Grenzen für Kontext, Tokenzahl, Threads und RAM sowie real gemessene Metriken. Keine Heuristik darf als Engineergebnis erscheinen. Native Ressourcen in allen Fehler-/Cancelpfaden freigeben. **Abnahme:** instrumentierter Test lädt ein kleines, klar lizenziertes und gehashtes Testmodell und erzeugt validiertes JSON; Metriken stammen von realem Decoder.

## L11A – ABI-/16-KB-/Geräteprüfung (NUR Pfad A)
**Abhängigkeit:** L08A–L10A.  
**Prompt:** Prüfe alle nativen Bibliotheken inklusive transitiver Abhängigkeiten auf ABI-Abdeckung sowie 16-KB-ELF- und APK/AAB-Alignment. Teste nur die zugesagten ABIs, einschließlich `arm64-v8a` und `x86_64` nur sofern tatsächlich paketiert/unterstützt, auf passenden Emulatoren oder Geräten. Dokumentiere Geräte, API-Level und Resultate. **Abnahme:** kein pauschales „kompatibel“ ohne Artefakt-/Gerätenachweis.

## L08B – Ehrlicher LLM-Rückbau (NUR Pfad B)
**Abhängigkeit:** L02, explizit freigegebener Pfad B; überspringe L08A–L11A.  
**Prompt:** Setze das LLM-Feature hinter ein Standard-`false`-Feature-Flag, entferne/unzugänglich mache Modellmenü, Chat-/Tool-Inferenz und Downloads, soweit sie nur zu diesem nicht verfügbaren Pfad gehören. Erhalte unabhängige regelbasierte Funktionen und kennzeichne sie korrekt. Keine Modellbytes im Hintergrund laden. **Abnahme:** Tests zeigen deaktivierten Standardzustand, keinen unnötigen Modell-Download und keine Inferenzbehauptungen.

---

# C – Cloud-Sync, OAuth und WebDAV

## C01 – Cloud-Verhalten und Schlüsselgrenze (AUDIT/GATE)
**Prompt:** Prüfe `CloudSyncWorkerService`, `GoogleDriveClient`, `WebDavClient`, Einstellungen, Token-/Passwortablage, Netzwerkstatus und Verhalten bei gesperrtem Vault. Erstelle eine knappe ADR, die entscheidet, ob Sync nur mit entsperrtem Vault läuft oder welche getrennt geprüfte Hardware-/Keystore-Freigabe zulässig wäre. Master-Passphrase darf nicht persistiert werden. Untersuche auch das aktuelle Fail-open-Verhalten bei unklarem WLAN-Status. Noch keine Provider-Implementierung. **Abnahme:** eindeutig definierter gesperrter Zustand und sichere Statusregeln.

## C02 – WorkManager-Grundmigration
**Abhängigkeit:** C01, D10 und S08 soweit Secrets benötigt werden.  
**Prompt:** Ersetze die direkte Singleton-Ausführung durch einen `CoroutineWorker` mit Unique Work pro Sync-Konfiguration, Netzwerk-Constraints, optionalem Akku-Constraint, Fortschritt und begrenztem Retry/Backoff. Netzstatus bei Fehler/Unklarheit muss fail-closed sein. Große Transfers erhalten nur bei Bedarf eine korrekte Foreground-Info mit Stoppmöglichkeit und API-konformem Serviceverhalten. Provider-Auth noch nicht neu entwerfen. **Abnahme:** WorkManager-TestDriver für Constraints, Unique Work, Retry und Abbruch.

## C03 – Vault-Lock und SecretStore anbinden
**Abhängigkeit:** C01, S05, S08.  
**Prompt:** Stelle sicher, dass Worker ausschließlich über die genehmigte KeyProvider-/SecretStore-Schnittstelle auf benötigte Schlüssel/Zugangsdaten zugreift. Definiere den Workerzustand bei gesperrtem Vault: kontrolliert warten/fehlschlagen oder nur den in C01 explizit genehmigten Weg verwenden. Kein `userPasswordKey: String`, keine gespeicherte Master-Passphrase, keine stillschweigende automatische Entsperrung. **Abnahme:** Tests für gesperrten Vault, fehlende Secrets, Prozessneustart, Lock während Sync und Widerruf.

## C04 – Google-Drive-OAuth-Entscheidung (GATE)
**Prompt:** Erstelle eine ADR zur echten Google-Identity-Autorisierung oder vollständigem Rückbau von Google Drive. Falls unterstützt: minimalen Scope vergleichen (`drive.file` versus `appDataFolder`), Consent/Neuautorisierung, Tokenlebenszyklus und Fehlerzustände beschreiben. Keine manuell einzutragenden Access-Tokens als Produktlösung. Noch keine OAuth-Implementierung; warte auf Auswahl. **Abnahme:** Produktoption samt Konsequenzen eindeutig dokumentiert.

## C04B – Google Drive zurückbauen (NUR wenn Option „entfernen“ freigegeben)
**Abhängigkeit:** C04 und ausdrückliche Freigabe zum Rückbau; statt C05 ausführen.  
**Prompt:** Entferne ausschließlich den nicht mehr unterstützten Google-Drive-Provider aus UI, Workerregistrierung und Konfiguration. Bestehende lokale/verschlüsselte Backups dürfen nicht gelöscht werden. Erkläre vorhandene Nutzerkonfiguration klar und ohne Secrets zu loggen; sichere bei Bedarf einen kompatiblen Fehlerzustand für alte Einstellungen. **Abnahme:** kein Drive-Upload und kein OAuth-Tokenabruf kann mehr gestartet werden; Build- und Regressionstests grün.

## C05 – Google-Drive-Client absichern (nur wenn genehmigt)
**Abhängigkeit:** C04, Freigabe für Drive.  
**Prompt:** Implementiere nur die Client-Zuverlässigkeit der genehmigten OAuth-Integration: URLs über `HttpUrl.Builder`, resumable Upload für große Dateien, begrenzte Retries mit `Retry-After` bei 429/geeigneten 5xx, getrennte Fehler für Auth/Quota/Netz/Format. Bei 401 kontrolliert stoppen und Neuautorisierung verlangen. Tokens und Dateiinhalt nie loggen. **Abnahme:** MockWebServer-Tests für Uploadfortsetzung, 401, 429, 5xx, Netzabbruch und Redirect.

## C06 – WebDAV-HTTPS und Zertifikatsvertrauen
**Prompt:** Erzwinge HTTPS und Hostnamenprüfung. Beziehe Anmeldedaten ausschließlich aus S08-SecretStore. Für bewusst unterstützte selbstsignierte NAS-Zertifikate nur explizites Trust-on-first-use mit SHA-256-Fingerprint pro Host; geändertes Zertifikat blockieren, bis der Nutzer bewusst erneut prüft. Keine globale Trust-all-Implementierung oder stille HTTP-Ausnahme. **Abnahme:** Tests für gültiges, falsches, abgelaufenes/geändertes Zertifikat und fehlende Zugangsdaten.

## C07 – Verschlüsseltes Sync-Manifest und Differenzabgleich
**Abhängigkeit:** D10/C03.  
**Prompt:** Implementiere nur ein versioniertes, authentifiziert verschlüsseltes Manifest mit Dokument-ID, Inhaltsprüfsumme, Änderungszeit und Backupgeneration. Provider dürfen keine Klartext-OCR-/Titel-/Absenderdaten erhalten. Übertrage nur neue/geänderte Daten. Lege Konflikt-/Rollbackregeln fest, damit ein veraltetes Manifest keinen neueren Vaultzustand überschreibt; Löschungen erst nach festgelegter Aufbewahrungsfrist spiegeln. **Abnahme:** Tests für identische/geänderte Dateien, altes Manifest, Konflikt und Manipulation.

## C08 – Status, Neuautorisierung und Abbruch
**Prompt:** Passe ausschließlich Status/UI an den echten Workerzustand an: letzter erfolgreicher Lauf, letzter Fehler, Fortschritt, nächste Planung soweit verfügbar und erforderliche Nutzeraktion. „Erfolg“ erst melden, wenn alle vorgesehenen Daten vollständig übertragen und verifiziert sind. Manueller Abbruch muss Netzwerk-/Dateistreams tatsächlich schließen. Fehlende Zustimmung/Config, fehlender Key oder gesperrter Vault dürfen keinen Upload starten. **Abnahme:** UI-/Worker-Tests für Erfolg, Teilfehler, Abbruch und Neuautorisierung.

## C09 – Cloud-End-to-End-Tests
**Abhängigkeit:** C02–C08, jeweils nur für ausgewählte Provider.  
**Prompt:** Ergänze echte Worker-/Provider-Integrationstests für Upload, Resume, 401, 429/Retry-After, 5xx, Offline/Netzwechsel, falschen Hash, gesperrten Vault, fehlende Konfiguration, Abbruch und Prozessneustart. Markiere nicht verfügbare Live-OAuth-/Providerprüfungen klar als offen. **Abnahme:** Testprotokoll trennt Mock-, WorkManager-, Integration- und reale Providerprüfung.

---

# DB – Room, FTS, Migrationen und Finanzbeträge

## DB01 – Room-v9-Baseline sichern
**Prompt:** Exportiere bzw. rekonstruiere das tatsächliche Room-Schema v9, ohne Schema- oder Datenmodelländerung. Prüfe `AppDatabase`, Entities, Indizes, Foreign Keys, FTS-Tabellen/Trigger und vorhandene Migrationen. Schalte noch nicht `exportSchema=true`, falls dadurch historische Versionen fälschlich erfunden würden. Erstelle ein Audit der tatsächlich vorhandenen Schemahistorie. **Abnahme:** v9-Baseline ist aus dem tatsächlichen Code/Build belegbar; keine destruktiven Änderungen.

## DB02 – Schemaexport und Migrationstest-Infrastruktur
**Abhängigkeit:** DB01.  
**Prompt:** Aktiviere `exportSchema=true`, konfiguriere KSP-`room.schemaLocation` und nimm die tatsächlich generierten Schema-JSONs in den Patch auf. Richte `MigrationTestHelper`-Infrastruktur ein, ohne gleich die fachlichen Migrationen umzusetzen. Historische fehlende Versionen nur aus echten veröffentlichten Schemata oder als klar bezeichnete Fixtures rekonstruieren. **Abnahme:** Build generiert erwartete Schemas reproduzierbar; Testinfrastruktur läuft, soweit die Umgebung dies ausführen kann; andernfalls verbleibt die Prüfung ausdrücklich offen.

## DB03 – Destruktiven Downgrade-Fallback entfernen
**Prompt:** Prüfe tatsächliche `fallbackToDestructiveMigrationOnDowngrade`-/Destructive-Migration-Nutzung. Entferne unsichere Löschpfade. Nicht unterstützter Downgrade muss in einen erklärbaren Fehler-/Recovery-Zustand führen, ohne eine leere Datenbank als Erfolg anzulegen. Keine Migration vorhandener Nutzerdaten in diesem Schritt. **Abnahme:** Test beweist, dass unbekannte Downgrades DB-Inhalt nicht löschen.

## DB04 – Migrationstests für bestehende Schemaübergänge
**Abhängigkeit:** DB01/DB02.  
**Prompt:** Erstelle `MigrationTestHelper`-Tests für jeden tatsächlich unterstützten historischen Übergang, mindestens 8→9, sofern diese Migration real vorliegt. Befülle jede Fixture mit repräsentativen synthetischen Dokumenten, Custom Fields und Finanzdatensätzen; vergleiche Daten vor/nach Migration und prüfe Indizes, Foreign Keys und FTS. Keine Migration ändern, nur um Tests „grün“ zu bekommen. **Abnahme:** Instrumentierte Tests tatsächlich ausführen bzw. Laufzeit/Umgebung nennen.

## DB05 – FTS-Trigger konsistent machen
**Prompt:** Isoliere `documents_fts` und zugehörige Trigger. Teste Einfügen, Titel-/OCR-Update, Delete, Soft-Delete und Migration; korrigiere nur nachgewiesene Inkonsistenzen. FTS-Dokumentzahl und Inhalt müssen der definierten Suchsemantik entsprechen. Bei genehmigter DB-Verschlüsselung darf kein zusätzlicher unverschlüsselter FTS-Nebenindex entstehen. **Abnahme:** Datenbanktests zeigen konsistenten Index nach jedem Ereignis.

## DB06 – Suchabfragen und Eingabevalidierung
**Abhängigkeit:** DB05.  
**Prompt:** Prüfe Prefix-Matching und FTS-Abfragen bei Umlauten, Sonderzeichen, Anführungszeichen, Operatoren und leeren Strings. Entferne den teuren/unsicheren `LIKE`-Fallback über `ocrText`, sofern FTS die definierte Suche übernehmen kann; begründe jede Abweichung. Parameterisieren statt SQL aus Nutzereingaben zusammenzusetzen. **Abnahme:** Tests für Syntax-/Injektionszeichen, keine Klartext-Nebenindizes, korrektes Suchverhalten.

## DB07 – Schlanke Dokumentlisten-Projektion
**Prompt:** Führe eine `DocumentListItem`-ähnliche Projektion nur mit tatsächlich für Listen benötigten Feldern ein. Listen-, Dashboard- und Explorerabfragen dürfen `ocrText` nicht laden; Detail-/Volltextansichten nutzen dedizierte Queries. Filter-/Sortiersemantik nicht verändern. **Abnahme:** DAO-/Mapper-Tests und Queryprüfung zeigen, dass OCR-Text nicht im Listenergebnis materialisiert wird.

## DB08 – Paging separat einführen
**Abhängigkeit:** DB07.  
**Prompt:** Integriere Paging 3 nur in die Dokumentlisten, die von der Projektion aus DB07 profitieren. Nutze Room-`PagingSource`, stabile Sortierung/Filter und sauberes ViewModel-Caching. Andere Screens und Filter nicht nebenbei umbauen. **Abnahme:** Tests für Pagingseiten, Sortierung, Filterwechsel und Aktualisierungen; vorhandenes Listenverhalten bleibt erhalten.

## DB09 – `REPLACE`-Semantik auditieren und korrigieren
**Prompt:** Suche gezielt nach `OnConflictStrategy.REPLACE` und SQL-`REPLACE` in Datenbankpfaden. Ersetze sie nur, wenn unbeabsichtigtes Delete+Insert bzw. Verlust von IDs/FKs/abhängigen Werten nachgewiesen ist, durch `@Upsert` oder gezielte Insert-/Update-Logik. Beabsichtigte Replace-Semantik dokumentieren und testen. **Abnahme:** gezielte Konflikttests für jede geänderte DAO-Methode.

## DB10 – Foreign-Key-Migration einzeln durchführen
**Abhängigkeit:** DB02/DB04; nur wenn Schema-Audit das bestätigt.  
**Prompt:** Erhöhe die Room-Version exakt um einen Schritt und setze nur die genehmigten Foreign Keys für `document_custom_field_values` um. Zähle vor Migration verwaiste Zeilen und behandle sie nach einer dokumentierten Regel; keine stillschweigende Löschung. Falls die reale aktuelle Version nicht 9 ist, ermittle zuerst den nächsten freien Versionsschritt statt die Zahl blind zu übernehmen. **Abnahme:** Migrationstest mit verwaisten/gültigen Zeilen, ON DELETE-Verhalten und unveränderten unabhängigen Datensätzen.

## DB11 – Geldbeträge als ganzzahlige Cent migrieren
**Abhängigkeit:** DB04; eigene, sequenzielle Schema-Migration nach allen vorangegangenen Migrationen.  
**Prompt:** Stelle Finanzbeträge im Storage-/Domain-Modell auf `Long`-Cent um. Definiere Rundungsregel fachlich (z. B. `BigDecimal`/HALF_UP) und migriere alte `REAL`/`Double`-Werte reproduzierbar; kein unkontrolliertes `Double * 100`. Ändere nur Betragsspeicherung und zwingend abhängige Mapper. Parser, UI, Bankabgleich und GiroCode schrittweise in Folgeaufträgen behandeln, sofern sie nicht in diesem eng begrenzten Schritt nötig sind. **Abnahme:** Migrationstests für positive/negative Werte, Null, Rundungsgrenzen und große Werte.

## DB12 – Datenbankverschlüsselung gemäß ADR umsetzen (GATE)
**Abhängigkeit:** D02, DB01–DB04 und explizit freigegebene ADR.  
**Prompt:** Implementiere nur die in D02 freigegebene Datenbanköffnung/Schlüsselbereitstellung, ohne gleichzeitig Dokumentverschlüsselung, Geldbeträge oder Refactoring zu ändern. Alle Öffnungspfade müssen denselben KeyProvider nutzen; kein passwortloser Notfallzugriff und kein Fallback auf unverschlüsseltes SQLite. Prüfe WAL/SHM, FTS und Migrationstest-Setup. Wenn SQLCipher-/Room-Kompatibilität nicht belegt ist, stoppe vor Datenmigration. **Abnahme:** instrumentierte Tests für Öffnen, falschen Schlüssel, Neustart und gesperrten Vault; offene Geräteprüfungen klar nennen.

## DB13 – Bestehende Datenbank sicher migrieren (NUR wenn SQLCipher/DB-Verschlüsselung freigegeben)
**Abhängigkeit:** DB12 ist vollständig getestet; D02 ist ausdrücklich freigegeben. Bei Feldverschlüsselungsentscheidung diesen Auftrag überspringen und den ADR-spezifischen Feldmigrationsplan verwenden.  
**Prompt:** Implementiere nur den sicheren Bestandstransfer von der bisherigen DB in die genehmigte verschlüsselte Datenbank. Originaldatenbank unverändert erhalten, bis Kopie/Export, Migration, FTS, Foreign Keys, Zeilenzahlen und repräsentative Datensummen verifiziert sind. Migration muss unter Prozessabbruch wiederaufnehmbar sein und darf nie in Klartext-SQLite zurückfallen. Teste fehlenden/invalidierten Key, wenig Speicher, Abbruch, beschädigte Quelle und Rollback. **Abnahme:** instrumentierte Migrationstests mit realer Room-/SQLCipher-Konfiguration; Quelle wird erst nach vollständiger Verifikation ersetzt bzw. gemäß genehmigtem Rollbackplan gesichert.

## DB14 – Listen-/Suchperformance messen
**Abhängigkeit:** DB05–DB08 und DB12/DB13, soweit die genehmigte DB-Verschlüsselung betroffen ist.  
**Prompt:** Erzeuge einen synthetischen Testbestand mit 10.000 Dokumenten und je etwa 10 KB OCR-Text. Miss vor/nach den Änderungen Listenaufbau, Paging, Suche, Heap und Datenbankgröße. Dokumentiere Gerät/Emulator, API-Level, Laufzahl und Messmethode. Keine Tuningänderung ohne Messbasis. Prüfe insbesondere, dass Listenabfragen `ocrText` nicht materialisieren und dass FTS unter der genehmigten DB-Architektur läuft. **Abnahme:** reproduzierbare Kennzahlen und Vergleichswerte; keine bloße Aussage „fühlt sich schneller an“.

---

# R – Architektur-Refactoring (erst nach Stabilisierung der Sicherheits-APs)

## R01 – Charakterisierungstests und Flow-Inventar (AUDIT)
**Prompt:** Inventarisiere die relevanten Nutzerflows und aktuellen sichtbaren Verhaltensweisen von Scanner/Import, DMS/Explorer, Finance, Sync/P2P, Security/Lock, LLM, Settings und Dashboard. Ergänze nur gezielte Charakterisierungstests für einen klar gewählten ersten Bereich; keine großen Refactorings. Lege eine Reihenfolge mit Abhängigkeiten und unverändert zu haltenden Verhaltensweisen an. **Abnahme:** Screen-/Flow-Liste und grüne Tests für den ausgewählten Bereich.

## R02 – DI-Technologie als ADR
**Prompt:** Prüfe den bestehenden Gradle-/Compose-Aufbau und entscheide in einer ADR zwischen Hilt, Koin oder einer bereits vorhandenen Lösung. Berücksichtige Testbarkeit, Buildkomplexität und Lebenszyklus. Noch keine Massenmigration und keine Entfernung bestehender Singletons. **Abnahme:** konkrete Wahl, schrittweiser Migrationsplan und Teststrategie.

## R03 – DI-Grundlage für Datenbank und Repositories
**Abhängigkeit:** R02.  
**Prompt:** Führe die freigegebene DI-Grundlage nur für `AppDatabase`, DAOs und einen abgegrenzten Repository-Bereich ein. Bestehende Verbraucher dürfen vorerst über schmale kompatible Adapter laufen. Vermeide doppelte DB-Instanzen. Keine Migration von Security-/Vault-Singletons in diesem Schritt. **Abnahme:** App buildbar, DI-Test und Repository-Faketest; kein breiter Call-Site-Umbau.

## R04 – Vault-/Lock-/Key-Singletons eindeutig verdrahten
**Abhängigkeit:** R02/R03, V/S-APs abgeschlossen.  
**Prompt:** Registriere `VaultSecurityManager`, `LockManager`, `KeyProvider` und `SecretStore` ausschließlich in den genehmigten DI-Scopes. Prüfe über Tests/Logs ohne Secrets, dass keine doppelte Instanz entsteht. Migriere nur direkte Aufrufer in einem ausgewählten Modul; keine Änderung des Sicherheitsverhaltens. **Abnahme:** Identitäts-/Scope-Test und Lock-/Key-Regressionstests grün.

## R05 – Scanner-/Import-ViewModel isolieren
**Abhängigkeit:** R01/R03.  
**Prompt:** Extrahiere ausschließlich Scanner-/Importverhalten aus `DocAnizerViewModel` in ein eigenes Feature-ViewModel mit typisiertem `UiState`/Events. Speicher-/Verschlüsselungsregeln bleiben unverändert und werden über bestehende Services/Repositories aufgerufen. Keine DMS-/Finance-/Sync-Aufteilung in diesem Auftrag. **Abnahme:** Fake-Repository-Tests, Charakterisierungstests und Build grün.

## R06 – DMS-/Explorer-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich DMS-/Explorerzustand und -aktionen in ein eigenes ViewModel. Erhalte Filter, Sortierung, Navigation, Lade-/Fehlerzustände und Liste/Detail-Semantik exakt. Verwende die bereits eingeführten Projektionen/Paging nur dort, wo die entsprechenden DB-APs abgeschlossen sind. **Abnahme:** Charakterisierungstests, Fake-Repository-Tests und Smoke-Test.

## R07 – Finance-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich Finanzzustand/-aktionen in ein eigenes Feature-ViewModel. Ändere keine Rechenregeln oder Währungsdarstellung; bei bereits migrierten Cent-Werten Mapper/Tests verwenden, sonst bestehendes Verhalten beibehalten. **Abnahme:** Tests für Summen, Filter, Monatswechsel und Fehlersituationen bleiben gleich.

## R08 – Sync-/P2P-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich UI-Zustand/-Events von Cloud-Sync und P2P in eigene ViewModels bzw. einen klar abgegrenzten Bereich. Netzwerkoperationen/Worker gehören weiterhin in Services; keine Sockets oder langlebigen Jobs im ViewModel halten. Status nicht als Erfolg darstellen, bevor der Worker vollständig erfolgreich ist. **Abnahme:** Fake-Service-Tests für Fortschritt, Fehler, Abbruch und Neuautorisierung.

## R09 – Security-/Lock-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich Security-/Lock-UI-Zustand in ein eigenes ViewModel. Authentifizierungsautorität bleibt beim LockManager/Vault-Service; das ViewModel darf den Vault nicht durch boolesche UI-Events entsperren. Keine Schlüsselkopien in UI-State. **Abnahme:** Tests für gesperrt/entsperrt, Authfehler, Timeout und Prozessneustart.

## R10 – LLM-/Chat-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich LLM-/Chat-UI-Zustand. Laufzeitmodus und Fallback müssen aus dem freigegebenen LLM-Pfad stammen; keine regelbasierte Antwort als Inferenz kennzeichnen. Keine Dokument-/OCR-Texte in Logs oder dauerhaftem UI-Debugzustand. **Abnahme:** Tests für Modellverfügbarkeit, regelbasierten Modus, Abbruch und Fehler.

## R11 – Settings-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich Einstellungen in ein eigenes ViewModel. Geheimnisse bleiben ausschließlich über SecretStore erreichbar; keine Passwörter/Tokens in `UiState`, DataStore oder UI-Debugausgaben. Bestehende Einstellungswerte und Defaults erhalten. **Abnahme:** Tests für Lesen/Ändern/Neustart und Secret-Redaktion.

## R12 – Dashboard-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich Dashboardzustand in ein eigenes ViewModel. Keine neuen fachlichen Regeln hinzufügen; Daten nur über existierende Repositories abrufen. Nichtkritische Ladeoperationen sauber abbrechen, kritische Security-Initialisierung nicht verzögern. **Abnahme:** Dashboard-Smoke-Test, Lade-/Fehler-/Empty-State-Tests.

## R13 – SettingsRepository zu DataStore migrieren
**Abhängigkeit:** R03/R11 und S09 für Secrets.  
**Prompt:** Migriere nur nicht-sensitive Einstellungen von SharedPreferences zu Preferences oder Proto DataStore. Geheimnisse dürfen nicht nach DataStore kopiert werden; dafür bleibt S08 SecretStore zuständig. Die Migration muss idempotent sein, Altwerte erst nach verifiziertem Schreiben entfernen und Prozessabbruch verkraften. **Abnahme:** Tests für Defaults, vorhandene Werte, zweimaligen Start, Crash während Migration und erhaltene Einstellungen.

## R14 – JSON-Strategie festlegen (AUDIT/GATE)
**Prompt:** Inventarisiere JSON-Verwendung und unterscheide internes/transientes JSON von persistierten oder externen Formaten (Room-Konverter, P2P-Protokoll, Backupmanifest, Einstellungen). Erstelle eine kurze ADR zur bevorzugten Bibliothek (z. B. `kotlinx.serialization`) und Kompatibilitätsregeln. Noch keine globale Ersetzung. **Abnahme:** Formatverzeichnis, Versionsregeln und priorisierte kleine Migrationsliste.

## R15 – Ein internes JSON-Format migrieren
**Abhängigkeit:** R14.  
**Prompt:** Migriere genau EIN in R14 benanntes, internes und nicht persistiertes JSON-Format zur genehmigten Bibliothek. Öffentliche APIs, Datenbankwerte, alte Backups, Manifest- und P2P-Wireformate bleiben unberührt. Füge Roundtrip-/Fehler-/Kompatibilitätstests hinzu. **Abnahme:** nur ein Format betroffen und bestehende Tests grün. Für jedes weitere Format einen neuen Durchlauf erstellen.

## R16 – Zentrales, redigierendes Logging
**Prompt:** Inventarisiere direkte `printStackTrace()`-/`android.util.Log`-Aufrufe und führe eine zentrale Logger-Abstraktion mit Release- und Debugverhalten ein. Redigiere Secrets, OCR-/Dokumenttexte, vertrauliche Dateinamen und URLs mit Zugangsdaten; Telemetrie darf nur nach der realen Einwilligungsentscheidung senden. Migriere nur einen abgegrenzten Bereich pro Lauf. **Abnahme:** Tests für Redaktionsregeln und keine sensiblen Beispieldaten in Testlogs.

## R17 – Optionale Firebase-/Analytics-Initialisierung
**Prompt:** Prüfe den Startpfad für `FirebaseAnalytics.getInstance` und die optionale Google-Services-Konfiguration. Falls Firebase optional bleiben soll, muss fehlende/ungültige Konfiguration Analytics deaktivieren, nicht den Appstart abstürzen lassen. Ereignisse erst nach entsprechender Einwilligung senden. OCR-/Dokumentdaten nie als Analytics-Parameter. **Abnahme:** Build-/Starttest ohne `google-services.json` bzw. ohne initialisierte Standard-App und Consent-Negativtest.

## R18 – `runBlocking` in UI-Pfaden entfernen
**Prompt:** Suche `runBlocking` ausschließlich in UI-/Main-Thread-Pfaden und behebe zuerst genau einen klar abgegrenzten Aufrufpfad. Verwende strukturierten Coroutine-Scope mit klarer Eigentümerschaft/Cancel-Verhalten. Keine großflächige API-Umstellung und keine Verschiebung kritischer Sicherheitsinitialisierung in den Hintergrund. **Abnahme:** Main-Thread-/Abbruchtests und Build grün; für jeden weiteren Pfad neuen Micro-AP-Lauf.

## R19 – `runBlocking` in Netzwerk-/Servicepfaden entfernen
**Prompt:** Suche `runBlocking` ausschließlich in Netzwerk-/Servicepfaden und migriere genau einen ausgewählten Pfad zu suspendierenden APIs/strukturierter Konkurrenz. Ressourcen müssen bei Cancel/Timeout in `finally` geschlossen werden. Ändere nicht gleichzeitig P2P-Protokoll oder Workerarchitektur, falls diese noch nicht stabil sind. **Abnahme:** Timeout-/Cancel-/Ressourcenfreigabetests und Build grün.

## R20 – Startarbeit gezielt aus `Application.onCreate` verschieben
**Prompt:** Prüfe den tatsächlichen Startpfad und verschiebe nur nachweislich nichtkritische Arbeiten wie `SplashSoundSynthesizer.precompute` in einen geeigneten Hintergrundpfad. Kritische Vault-/Lock-/Key-Initialisierung muss abgeschlossen sein, bevor sensible UI sichtbar wird. Keine spekulative Dependency-/Startup-Änderung. **Abnahme:** Start-Smoke-Test, Reihenfolge dokumentiert und Startzeit vorher/nachher gemessen.

## R21 – Eine große Compose-Datei gezielt teilen
**Prompt:** Teile ausschließlich `ui/views/DmsExplorerView.kt` (oder, falls diese bereits geteilt ist, genau eine im Audit ausgewählte Datei) entlang sinnvoller UI-/State-Grenzen in kleinere Composables. Verhalten, Filter, Sortierung, Navigation, Semantik und Berechtigungen bleiben unverändert. Keine rein mechanische Aufteilung auf eine Zielzeilenzahl erzwingen. **Abnahme:** Compose-Preview soweit vorhanden, Charakterisierungstest/Smoke-Test und unverändertes Verhalten.

## R22 – Performance-Baseline vor weiterem Refactoring
**Prompt:** Messe vor weiteren Refactorings Buildzeit, Startzeit und – falls relevant – Compose-Recomposition-/Listen-/Suchperformance. Erzeuge ein reproduzierbares Profil/Testsetup und dokumentiere Gerät/Emulator, API-Level, Datensatz und Messmethode. Keine Tuningänderungen in diesem Schritt. **Abnahme:** Zahlen und Reproduktionsschritte liegen vor; ohne Messbasis keine Behauptung einer Verbesserung.

## R23 – Veraltete Dateien und Dependencies einzeln bereinigen
**Abhängigkeit:** jeweiliger Featurebereich ist getestet und nicht mehr von den Dateien/Dependencies abhängig.  
**Prompt:** Prüfe eine einzelne vermutete tote Datei, Dependency, auskommentierte Gradle-Zeile oder ungenutzte Version-Catalog-Referenz per Code-/Buildsuche. Entferne nur diesen klar belegten Kandidaten pro Lauf, einschließlich `zwyxdl/MainActivity.kt` nur falls nachweislich nicht referenziert. Keine gleichzeitige Massenbereinigung. **Abnahme:** Suchbelege zeigen keine Nutzung mehr; Build und relevante Tests bestehen.

## R24 – Baseline Profile erstellen und Vorher/Nachher vergleichen
**Abhängigkeit:** R22 und abgeschlossene Refactoring-Schritte, deren Performance verglichen werden soll.  
**Prompt:** Erzeuge für die tatsächlich unterstützten kritischen Nutzerflows ein reproduzierbares Baseline Profile und messe Start-/UI-/Listenverhalten mit gleicher Testumgebung wie in R22. Ändere `largeHeap`, Startup-Work, Plugins oder Dependencies nicht ungeprüft. Berichte Verbesserung und Regressionen mit Messdaten; wenn eine Verbesserung nicht nachweisbar ist, sage das explizit. **Abnahme:** Profil, Reproduktionsschritte und Vorher/Nachher-Werte sind dokumentiert.

---

## 4. Gemeinsame Abnahmeliste für jeden Micro-AP

### Agenten-Gate – vor Ausgabe des Patches

- [ ] Nur der beauftragte Micro-AP wurde umgesetzt; keine unangekündigten Nebenänderungen.
- [ ] Ausgangszustand/Dateibasis wurde dokumentiert; bestehende Änderungen blieben erhalten.
- [ ] Patch enthält ausschließlich Änderungen dieses Micro-AP (inkl. neuer/gelöschter Dateien).
- [ ] Build und Unit-Tests wurden nur dann als bestanden angegeben, wenn sie tatsächlich liefen; Baseline-Fehler sind getrennt ausgewiesen.
- [ ] Relevante Instrumented-/Migration-/Netzwerk-/WorkManager-/Gerätetests wurden ausgeführt oder explizit als offen benannt.
- [ ] Keine Secrets, sensiblen Beispieldaten, lokalen absoluten Pfade oder unnötigen generierten Dateien in Patch oder Bericht.

### Nutzer-Gate – nach manueller Übernahme im lokalen Repository

- [ ] Vor Anwendung `git status --short` geprüft und eigene Änderungen gesichert.
- [ ] Patch sauber angewendet (bei Konflikten gestoppt, nicht blind erzwungen).
- [ ] `git diff --check` und vollständigen `git diff` geprüft.
- [ ] `./gradlew assembleDebug testDebugUnitTest` und passende zusätzliche Tests lokal ausgeführt.
- [ ] Status im Fortschrittsprotokoll aktualisiert; erst danach nächsten Micro-AP starten.

---

## 5. Wann sofort stoppen statt „irgendwie fertigstellen“

Sofort abbrechen und konkreten Blocker melden, wenn:
- die Baseline bereits fehlschlägt und die Ursache nicht zum Auftrag gehört,
- der Ausgangszustand im Workspace nicht eindeutig abgrenzbar ist,
- ein altes Dateiformat nicht eindeutig gelesen werden kann,
- eine Schlüssel-/Datenmigration keinen sicheren Rollback besitzt,
- eine Architekturfreigabe (Gate/ADR) fehlt,
- ein Test nur durch Überspringen/Abschwächen grün würde oder
- die Änderung den Schutz bestehender Dokumente gefährden könnte.

**Sicherer Stopp mit erhaltenen Daten ist ein korrektes Ergebnis; unsichere Fertigstellung ist es nicht.**

---

## 6. Kompaktes Fortschrittsprotokoll

| Micro-AP | Status | Patch-Datei | Lokal übernommen | Lokale Tests | Nächster Schritt |
|---|---|---|---|---|---|
| M00 | ABGENOMMEN | micro-ap-M00.patch | Ja | Bestanden | V01 |
| V01 | PATCH ERSTELLT | direkt in main | Ja | Build/Start vor aktuellem Batch gemeldet; Unit-/Funktionstests offen | V02 |
| V02 | PATCH ERSTELLT | direkt in main | Ja | Build/Start vor aktuellem Batch gemeldet; Unit-/Funktionstests offen | V03 |
| V03 | PATCH ERSTELLT | direkt in main | Ja | Build/Start vor aktuellem Batch gemeldet; Unit-/Funktionstests offen | V04 |
| V04 | PATCH ERSTELLT | direkt in main | Ja | Build/Start vor aktuellem Batch gemeldet; Unit-/Funktionstests offen | V05 |
| V05 | BLOCKIERT | – | Ja | Entscheidung offen | Freigabe Option A/B |
| V06A | BLOCKIERT | – | Nein | Freigabe fehlt | V07 (parallel möglich) |
| V07 | PATCH ERSTELLT | direkt in main | Ja | Build/Start vor aktuellem Batch gemeldet; Unit-/Funktionstests offen | V08 |
| V08 | PATCH ERSTELLT | direkt in main | Ja | Build/Start vor aktuellem Batch gemeldet; Unit-/Funktionstests offen | V09 |
| V09 | PATCH ERSTELLT | direkt in main | Ja | Build/Start vor aktuellem Batch gemeldet; Unit-/Funktionstests offen | V10 |
| V10 | PATCH ERSTELLT | direkt in main | Ja | Build/Start vor aktuellem Batch gemeldet; Unit-/Funktionstests offen | V11 |
| V11 | PATCH ERSTELLT | direkt in main | Ja | QR-Roundtrip-Test ergänzt; Lauf offen | V12 |
| V12 | PATCH ERSTELLT | direkt in main | Ja | PDF-Regressionstest ergänzt; Textlayer-/Gerätetest offen | V13 |
| V13 | PATCH ERSTELLT | direkt in main | Ja | CryptoObject-Instrumented-Test ergänzt; Gerätetest offen | V14 |
| V14 | BLOCKIERT | – | Nein | v2 nicht in Runtime integriert | V14-Laufzeitintegration |
| V15 | PATCH ERSTELLT | direkt in main | Ja | Backup-Regeltests ergänzt; Lauf offen | S01 |
| S01 | GETESTET | docs/security/lock-secret-inventory.md | Ja | Audit dokumentiert | S02 |
| S02 | PATCH ERSTELLT | direkt in main | Ja | LockManager-Unit-Tests ergänzt; Lauf offen | S03 |
| S03 | PATCH ERSTELLT | direkt in main | Ja | Auto-Lock-Tests ergänzt; Lauf offen | S04 |
| S04 | PATCH ERSTELLT | direkt in main | Ja | Bypass-Pfade entfernt; Negativtests/Build offen | S05 |
| S05 | PATCH ERSTELLT | direkt in main | Ja | Produktionsaufrufe auf Key-Leases migriert; Testlauf offen | S06 |
| S06 | PATCH ERSTELLT | direkt in main | Ja | Backoff-Tests ergänzt; Testlauf offen | S07 |
| S07 | PATCH ERSTELLT | direkt in main | Ja | Screenshot-/Recents-Gerätetest offen | S08 |
| S08 | PATCH ERSTELLT | direkt in main | Ja | SecretStore-Instrumentationstest ergänzt; Gerätetest offen | S09 |
| S09 | BLOCKIERT | – | Nein | S08-Testlauf und Verbraucher-Audit offen | S08 prüfen |
| S10 | PATCH ERSTELLT | direkt in main | Ja | Policy-Tests ergänzt; Widget-Cold-Start-Test offen | D01 |
| D13 | PATCH ERSTELLT | direkt in main | Ja | Backup-Regeltests erweitert; Build-/Testlauf offen | D14 |
| D14 | PATCH ERSTELLT | direkt in main | Ja | PDFBox-Textlayer-Instrumentationstest ergänzt; Lauf offen; Scan-OCR wird nicht in PDF eingebettet | P01 |
| P01 | PATCH ERSTELLT | direkt in main | Ja | P2P-Transport-Audit und TLS-ADR dokumentiert; Freigabe offen | ADR-Freigabe vor P02 |

Zulässige Statuswerte: `NICHT GESTARTET`, `BLOCKIERT`, `PATCH ERSTELLT`, `MANUELL ÜBERNOMMEN`, `GETESTET`, `ABGENOMMEN`.
