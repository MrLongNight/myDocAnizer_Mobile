# Implementierungsbericht S06–S10

Stand: 2026-10-21 (S08-Teststatus aktualisiert; übrige offene Punkte unverändert). Änderungen aus dem ursprünglichen Arbeitsstand wurden direkt auf `main` vorgenommen. Dieser Bericht trennt Quellcodeänderungen von Testergebnissen.

## Ergebnisübersicht

| Micro-AP | Stand | Ergebnis / offene Abnahme |
|---|---|---|
| S06 | Implementierung ergänzt | Gemeinsamer persistenter Backoff ist in Passwort-/TOTP- und Recovery-Einstiegen integriert. Öffentliche Authentifizierungsversuche in `VaultSecurityManager` sind synchronisiert, damit parallele Aufrufe die Sperrprüfung nicht leicht überholen. Tests für Eskalation, Erfolg/Reset, Neustart und rückgestellte Geräteuhr sind vorhanden. Testlauf offen. |
| S07 | Implementierung vorhanden | `FLAG_SECURE` wird ohne Emulatorerkennung gesteuert; Debug-Ausnahme über `BuildConfig.DEBUG`. Screenshot-, Recents- und Geräteprüfung offen. |
| S08 | GETESTET (CI-Emulator) | `SecretStore` speichert kleine Secrets als versionierte AES-GCM-Payload mit 12-Byte-IV, AAD pro Secret-Namen und nicht exportierbarem Android-Keystore-AES-Schlüssel. Ungültiger Tag, fehlender Schlüssel und fehlerhaftes Format führen zu `null`/Fehler, nicht zu Klartextfallback. `connectedDebugAndroidTest` lief am 21.10.2026 auf dem API-35-x86_64-Emulator erfolgreich; physischer Gerätetest bleibt separat offen. |
| S09 | Blockiert: Verbraucher-Audit offen | Der S08-Build und die Android-Instrumentierung sind in CI erfolgreich. Der direkte `sync_master_password`-/`syncPasswordKey`-Datenfluss besteht weiterhin u. a. in `SettingsRepository`, `SyncManager`, `DocAnizerViewModel`, `AppLockScreen`, `SecuritySettingsTab`, `CloudSyncSettingsTab`, `VaultSettingsScreen` und `SetupWizardDialog`. Vollständige Consumer-Suche und sichere Umstellung bleiben offen; das Legacy-Preference darf nicht vor dem letzten Consumer entfernt werden. |
| S10 | Policy und Unit-Tests vorhanden | `ExternalIntentPolicy` erlaubt nur bekannte Tab-Werte und die explizite Aktion `CASH_EXPENSE`. `MainActivity` verwirft externe Aktionen während unvollständiger Authentifizierung und speichert sie nicht für spätere Ausführung. Cold-start-/Widget-Integrationstest offen. |

## Zusätzliche Sicherheitskorrektur: Auto-Mirror

- Der Default für `autoMirrorToBackupDir` ist `false`; `SettingsRepository` deaktiviert den alten Mirror-Standard einmalig.
- Der Auto-Mirror-Pfad erstellt keine unverschlüsselte Kopie mehr, wenn kein Verschlüsselungsschlüssel verfügbar ist. In diesem Fall wird kontrolliert abgebrochen.
- Dateinamen werden auf den Basename begrenzt. Verschlüsselung erfolgt in eine temporäre Datei; erst nach erfolgreicher Erstellung wird sie per Same-Directory-Rename übernommen. Fehlgeschlagene temporäre Dateien werden entfernt.
- Verschlüsselungsfehler, atomarer Austausch und bestehende Nutzerdateien müssen noch auf Gerät getestet werden.

## D13 – Backupregeln (zusätzliche Änderung am 2026-10-18)

- Die Android-Geräteübertragung hatte bisher pauschale `include`-Regeln für den gesamten Datei-, Datenbank- und SharedPreferences-Bereich. Diese wurden entfernt.
- Die Geräteübertragung schließt jetzt die Room-Datenbank, interne Dokument-/Scan-/Backup-/Modell-/Temp-Pfade, externe App-Dateien sowie sensible Preferences explizit aus. Ein Gerätewechsel muss über den explizit gestarteten verschlüsselten Export-/Restore-Weg erfolgen.
- Legacy- und moderne Cloud-Backup-Regeln schließen zusätzlich externe App-Dateien sowie Modell-/Temp-Pfade aus.
- `BackupRulesSecurityTest` prüft jetzt zusätzlich, dass keine pauschalen Includes für `.` auf Datei-, Datenbank- oder SharedPreferences-Domänen vorhanden sind und dass die neuen Ausschlüsse gesetzt sind.
- **Wichtig:** Diese XML-Regeln und Tests wurden committed, aber der Gradle-Testlauf und ein echter Android-Geräte-/Restore-Test wurden nicht ausgeführt. Die Regeln müssen vor Release anhand der realen Speicherpfade geprüft werden.

## Teststatus

- Vorhandene/ergänzte Unit-Tests: `AuthenticationBackoffManagerTest`, `ExternalIntentPolicyTest`.
- Neue Android-Instrumentationstests: `SecretStoreInstrumentedTest`.
- Für Commit `f6d00636bec784e598128c3b1e72a2dbd40cfe58` liegt der erfolgreiche [GitHub-Actions-Lauf 39558067219](https://github.com/MrLongNight/myDocAnizer_Mobile/actions/runs/39558067219) vor: `./gradlew assembleDebug testDebugUnitTest --stacktrace --no-daemon` und `./gradlew connectedDebugAndroidTest --stacktrace --no-daemon` endeten beide mit `BUILD SUCCESSFUL`; die Instrumentierung lief auf Android API 35, x86_64. Damit ist S08 per CI getestet, nicht jedoch ein separater Test auf physischer Hardware oder die weiteren S06/S07/S10-Abnahmen. Der separate `pre-commit.ci - push`-Status war `error`; die Ursache ist aus den verfügbaren Ergebnissen nicht belegt.
- Die integrierte Benutzertest-Checkliste wurde um SecretStore, Backoff, Screenshots/Recents, externe Intents und Auto-Mirror erweitert: `docs/security/benutzer-testplan-gesamt.md`.

## Architektur- und Nutzungsgrenze

Der SecretStore verschlüsselt Werte im Ruhezustand mit einem appgebundenen Keystore-Schlüssel. Der Schlüssel ist in dieser Grundkomponente **nicht** an eine Biometrie pro Nutzung gebunden; SecretStore ist daher keine eigenständige Vault-Entsperrung. Verbraucher müssen weiterhin den Vault-/Lockzustand prüfen. S09 darf diese Grenze nicht umgehen.

## Nächste sichere Reihenfolge

1. S08-CI-Nachweis für `assembleDebug`, `testDebugUnitTest` und `connectedDebugAndroidTest` liegt vor; bei nächster lokaler Synchronisierung denselben Gradle-Befehl auf der tatsächlich verwendeten Arbeitskopie wiederholen.
2. Optionalen physischen Gerätetest für SecretStore/Keystore-Keyverlust getrennt protokollieren; Emulator- und Hardwarestatus nicht vermischen.
3. Vor S09 den vollständigen Verbraucher-Audit für `sync_master_password`/`syncPasswordKey` abschließen und einen sicheren Umstellungsplan pro Aufrufer festlegen. Den Legacy-Wert nicht entfernen, solange ein aktiver Verbraucher ihn benötigt.
4. S09 anschließend wertweise mit SecretStore-Roundtrip-Verifikation, idempotentem Neustart-/Abbruchverhalten und Fehler-/Keyverlusttests durchführen.
5. Backoff-/ExternalIntent-Tests sowie Widget-Cold-Start, Lock-Fälle und D13-/Auto-Mirror-Klartext-/Fehlerfälle getrennt weiter abnehmen.

