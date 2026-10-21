# Implementierungsbericht S06–S10

Stand: 2026-10-21 (S08-CI-Nachweis und S09-SecretStore-Migration aktualisiert). Dieser Bericht trennt Quellcodeänderungen von Testergebnissen.

## Ergebnisübersicht

| Micro-AP | Stand | Ergebnis / offene Abnahme |
|---|---|---|
| S06 | Implementierung ergänzt | Gemeinsamer persistenter Backoff ist in Passwort-/TOTP- und Recovery-Einstiegen integriert. Öffentliche Authentifizierungsversuche in `VaultSecurityManager` sind synchronisiert, damit parallele Aufrufe die Sperrprüfung nicht leicht überholen. Tests für Eskalation, Erfolg/Reset, Neustart und rückgestellte Geräteuhr sind vorhanden. Testlauf offen. |
| S07 | Implementierung vorhanden | `FLAG_SECURE` wird ohne Emulatorerkennung gesteuert; Debug-Ausnahme über `BuildConfig.DEBUG`. Screenshot-, Recents- und Geräteprüfung offen. |
| S08 | GETESTET (CI-Emulator) | `SecretStore` speichert kleine Secrets als versionierte AES-GCM-Payload mit 12-Byte-IV, AAD pro Secret-Namen und nicht exportierbarem Android-Keystore-AES-Schlüssel. Ungültiger Tag, fehlender Schlüssel und fehlerhaftes Format führen zu `null`/Fehler, nicht zu Klartextfallback. `connectedDebugAndroidTest` lief am 21.10.2026 auf dem API-35-x86_64-Emulator erfolgreich; physischer Gerätetest bleibt separat offen. |
| S09 | In Arbeit: zentrale Persistenz umgestellt | `SettingsRepository` lädt `sync_master_password` über `SecretStore`; beim ersten Start wird ein vorhandener Legacy-Wert verschlüsselt geschrieben, anschließend per entschlüsseltem Roundtrip verglichen und erst danach aus `mydocanizer_prefs` entfernt. Neue Änderungen schreiben ebenfalls in `SecretStore` und aktualisieren den Flow erst nach verifiziertem Roundtrip. Die öffentliche `StateFlow<String>`-API bleibt vorerst als Kompatibilitätsgrenze bestehen; Verbraucher in ViewModel/UI halten den Wert weiterhin zeitweise im Speicher. Instrumentationstests für Migration und Update ergänzt. Zusätzlich decken neue Tests den zweiten Repository-Start (idempotente Migration) und die Ablehnung eines übergroßen Legacy-Werts ab; dabei muss der Klartext bei fehlgeschlagener Verschlüsselung erhalten bleiben. Der Lauf dieser erweiterten Tests ist noch offen. Weitere S01-Secrets/Token und Fehler-/Gerätetests bleiben offen. |
| S10 | Implementiert; Build/Unit-Tests vom Nutzer bestätigt | `ExternalIntentPolicy.consume()` extrahiert nur die erlaubten Navigationswerte und entfernt danach alle Extras, `Intent.data` und `clipData`. `MainActivity` führt diese Bereinigung vor der Lock-Prüfung aus; gesperrte oder nicht authentifizierte externe Befehle bleiben nicht für einen späteren Activity-Neustart erhalten. Nicht unterstützte URI-Deep-Links werden verworfen. Der Nutzer bestätigt erfolgreichen Build, `:app:testDebugUnitTest` und App-Start im Streaming-Emulator. Ein separater `connectedDebugAndroidTest`-Lauf und gezielter Widget-Cold-Start-/Lock-Replay-Test sind nicht ausdrücklich ausgewiesen. |

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
- Neue Android-Instrumentationstests: `SecretStoreInstrumentedTest`, `ExternalIntentPolicyInstrumentedTest`.
- Für Commit `f6d00636bec784e598128c3b1e72a2dbd40cfe58` liegt der erfolgreiche [GitHub-Actions-Lauf 39558067219](https://github.com/MrLongNight/myDocAnizer_Mobile/actions/runs/39558067219) vor: `./gradlew assembleDebug testDebugUnitTest --stacktrace --no-daemon` und `./gradlew connectedDebugAndroidTest --stacktrace --no-daemon` endeten beide mit `BUILD SUCCESSFUL`; die Instrumentierung lief auf Android API 35, x86_64. Das bestätigt S08 für diesen Baseline-Commit, nicht die neu hinzugefügte S09-Migration. Der Nutzer bestätigt, dass der Build nach den S09-Änderungen fehlerfrei war; ein erfolgreicher Instrumentationstest für die S09-Migration ist damit nicht belegt. `pre-commit.ci - push` meldete für den damaligen Commit `error`; Ursache nicht belegt. Für S10 bestätigt der Nutzer inzwischen erfolgreichen Build, `:app:testDebugUnitTest` und App-Start im Streaming-Emulator. Das ist als Nutzerbestätigung dokumentiert; ein separater `connectedDebugAndroidTest`-Lauf wurde nicht ausdrücklich genannt.
- Die integrierte Benutzertest-Checkliste wurde um SecretStore, Backoff, Screenshots/Recents, externe Intents und Auto-Mirror erweitert: `docs/security/benutzer-testplan-gesamt.md`.

## Architektur- und Nutzungsgrenze

Der SecretStore verschlüsselt Werte im Ruhezustand mit einem appgebundenen Keystore-Schlüssel. Der Schlüssel ist in dieser Grundkomponente **nicht** an eine Biometrie pro Nutzung gebunden; SecretStore ist daher keine eigenständige Vault-Entsperrung. Verbraucher müssen weiterhin den Vault-/Lockzustand prüfen. S09 darf diese Grenze nicht umgehen.

## Nächste sichere Reihenfolge

1. S08-CI-Nachweis für `assembleDebug`, `testDebugUnitTest` und `connectedDebugAndroidTest` liegt vor; bei nächster lokaler Synchronisierung denselben Gradle-Befehl auf der tatsächlich verwendeten Arbeitskopie wiederholen.
2. Optionalen physischen Gerätetest für SecretStore/Keystore-Keyverlust getrennt protokollieren; Emulator- und Hardwarestatus nicht vermischen.
3. S10 gezielt auf Widget-Cold-Start und Intent-Replay nach einem gesperrten Start prüfen; zusätzlich `connectedDebugAndroidTest` ausführen, falls noch nicht Teil des bestätigten Testlaufs.
4. S09-Migration für `sync_master_password` mit Instrumentationstests prüfen: erster Start, zweiter Repository-Start, Update sowie Ablehnung eines übergroßen Legacy-Werts mit unverändert erhaltener Legacy-Pref. Die erweiterten Tests sind ergänzt; ihr CI-Lauf steht noch aus.
5. Die übrigen S01-Einträge auf weitere Passwörter/Tokens abgleichen; pro Secret nur nach verifiziertem Roundtrip den Klartext entfernen. Keine Klartext-Fallbacks hinzufügen.
6. Backoff-/ExternalIntent-Tests sowie D13-/Auto-Mirror-Klartext-/Fehlerfälle getrennt weiter abnehmen.

