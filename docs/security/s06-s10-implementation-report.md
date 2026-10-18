# Implementierungsbericht S06–S10

Stand: 2026-10-18. Änderungen wurden direkt auf `main` vorgenommen. Dieser Bericht trennt Quellcodeänderungen von noch nicht ausgeführten Tests.

## Ergebnisübersicht

| Micro-AP | Stand | Ergebnis / offene Abnahme |
|---|---|---|
| S06 | Implementierung ergänzt | Gemeinsamer persistenter Backoff ist in Passwort-/TOTP- und Recovery-Einstiegen integriert. Öffentliche Authentifizierungsversuche in `VaultSecurityManager` sind synchronisiert, damit parallele Aufrufe die Sperrprüfung nicht leicht überholen. Tests für Eskalation, Erfolg/Reset, Neustart und rückgestellte Geräteuhr sind vorhanden. Testlauf offen. |
| S07 | Implementierung vorhanden | `FLAG_SECURE` wird ohne Emulatorerkennung gesteuert; Debug-Ausnahme über `BuildConfig.DEBUG`. Screenshot-, Recents- und Geräteprüfung offen. |
| S08 | Kernkomponente erstellt | `SecretStore` speichert kleine Secrets als versionierte AES-GCM-Payload mit 12-Byte-IV, AAD pro Secret-Namen und nicht exportierbarem Android-Keystore-AES-Schlüssel. Ungültiger Tag, fehlender Schlüssel und fehlerhaftes Format führen zu `null`/Fehler, nicht zu Klartextfallback. Instrumentationstests erstellt, aber nicht ausgeführt. |
| S09 | Nicht begonnen | Bewusst nicht gestartet, bis S08 auf Gerät/Build geprüft und alle Verbraucher der in S01 inventarisierten Secrets einzeln migriert sind. Insbesondere `sync_master_password` darf nicht entfernt werden, solange D/P/C-Verbraucher es noch lesen. |
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
- **Kein neuer Gradle-Build oder Testlauf wurde durch diese Änderungen bestätigt.** Ein Statuscheck für einen der letzten Commits zeigte `pre-commit.ci - push: error`; die genaue Ursache war über die verfügbare Schnittstelle nicht lesbar. Deshalb sind Build- und Teststatus als offen zu behandeln.
- Die integrierte Benutzertest-Checkliste wurde um SecretStore, Backoff, Screenshots/Recents, externe Intents und Auto-Mirror erweitert: `docs/security/benutzer-testplan-gesamt.md`.

## Architektur- und Nutzungsgrenze

Der SecretStore verschlüsselt Werte im Ruhezustand mit einem appgebundenen Keystore-Schlüssel. Der Schlüssel ist in dieser Grundkomponente **nicht** an eine Biometrie pro Nutzung gebunden; SecretStore ist daher keine eigenständige Vault-Entsperrung. Verbraucher müssen weiterhin den Vault-/Lockzustand prüfen. S09 darf diese Grenze nicht umgehen.

## Nächste sichere Reihenfolge

1. Aktuellen `main` synchronisieren und `./gradlew assembleDebug testDebugUnitTest` ausführen.
2. SecretStore-Instrumentationstests auf einem echten Gerät ausführen; Fehler beheben.
3. Backoff- und ExternalIntent-Unit-Tests ausführen; Widget-Cold-Start und Lock-Fälle manuell prüfen.
4. Erst danach S09 wertweise mit Roundtrip-Verifikation und rollbackfähigem Entfernen der Legacy-Werte beginnen.
5. D13-/Auto-Mirror-Tests auf Klartextkopien und Verschlüsselungsfehler durchführen.

