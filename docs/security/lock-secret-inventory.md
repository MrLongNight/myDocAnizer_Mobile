# Lock- und Secret-Inventar

Stand: 2026-10-18 — Repository `main`. Dieses Dokument ist das Audit-Ergebnis für S01; es beschreibt den vor der LockManager-Integration gefundenen Zustand.

## 1. Zustandsquellen

| Bereich | Quelle | Befund / Risiko |
|---|---|---|
| Vault-Lock | `VaultSecurityManager.activeDek` | `null` bedeutet gesperrt; nicht persistiert. Das ByteArray wird bei `lockVault()` überschrieben. |
| Öffentlicher DEK-Zugriff | `VaultSecurityManager.getActiveDek()` | Liefert eine mutable Kopie, die unabhängig vom späteren Lock weiter existieren kann, wenn ein Aufrufer sie nicht löscht. |
| App-Lock | `LockManager.appUnlocked` | Zentraler StateFlow; das frühere ViewModel-eigene `_isAppUnlocked` wurde entfernt. App- und Vault-Lock bleiben getrennte Zustände. |
| App-Lock-Konfiguration | `SettingsRepository.biometricAuthEnabled` | Ist ein App-Setting und allein kein Nachweis einer erfolgreichen Vault-Kryptografieoperation. |
| App-Start | `LockManager.initialize` über `DocAnizerViewModel` | Ein vorhandener Vault startet fail-closed gesperrt. Debug kann nur den App-Shell-Lock umgehen, nicht den Vault-Lock. Der Null-Activity-Unlock-Bypass wurde entfernt. |
| Auto-Lock | `VaultAutoLockObserver` + `VaultHeader.autoLockMinutes` | Prozessweiter Observer nutzt `ProcessLifecycleOwner` und `SystemClock.elapsedRealtime`; 0 Minuten sperrt sofort. |

## 2. Persistierte Geheimnisse und Schlüsselmaterial

- `mydocanizer_vault_security.xml` (SharedPreferences-Datei aus `getSharedPreferences("mydocanizer_vault_security", ...)`): enthält den aktiven V2-Header `vault_header_json_v2_active` mit verschlüsseltem DEK, Salt/IV und Recovery-/Faktor-Metadaten. Der frühere V1-Befund zu einem Klartext-TOTP-Secret ist historisch; der aktive V2-Serializer schreibt das Feld nicht mehr und der Loader entfernt ein solches Legacy-Feld aus einem ansonsten gültigen V2-Header. Das ist keine pauschale Bestätigung, dass alle SharedPreferences verschlüsselt sind.
- `mydocanizer_prefs.xml` (SharedPreferences-Datei aus `getSharedPreferences("mydocanizer_prefs", ...)`): enthält das Legacy-Feld `sync_master_password`, die WebDAV-Zugangsdaten `cloud_sync_webdav_user`/`cloud_sync_webdav_pass` und `biometric_auth_enabled`. `sync_master_password` wird in `SecretStore` migriert. Die WebDAV-Passphrase wird jetzt ebenfalls per `SecretStore` gespeichert; die Migration aus `cloud_sync_webdav_pass` entfernt Klartext erst nach erfolgreichem Roundtrip. `cloud_sync_webdav_user` ist ein Benutzername und kein Authentifizierungsgeheimnis. Der Google-Drive-Access-Token wird in `CloudSyncConfig` geführt, aber von `SettingsRepository` nicht persistent geladen/gespeichert; Token-Lifecycle und echte OAuth-Erneuerung bleiben separat zu prüfen.
- Android Keystore: Biometrie-DEK-Wrapping-Key unter Alias `myDocAnizer_vault_biometric_key_v2` (neuer auth-gebundener Schlüsselpfad). Schlüsselmaterial sollte nicht exportiert werden; Verlust/Invalidierung muss auf Passwort-Recovery zurückfallen.
- Room-Datenbank `mydocanizer_database` und zugehörige WAL/SHM-Dateien: enthalten App-Daten. Die Backupregeln schließen sie vom Cloud-Backup aus; Geräteübertragungsregeln sind separat zu prüfen.
- Cache-/Exportdateien: Emergency-PDF wird unter `cacheDir/myDocAnizer_Notfall_Emergency_Kit.pdf` erzeugt und über FileProvider geteilt. Der neue Export darf nur verschlüsseltes Recovery-Payload, keinen Roh-DEK, enthalten.
- RAM: temporäre DEK-/KEK-/Passwort-/Recovery-Arrays in `VaultSecurityManager`, Dokumentenoperationen und ViewModel-Aufrufstellen. Kopien müssen möglichst in `finally` überschrieben werden.

## 3. Unlock- und Lock-Pfade vor S02–S04

- `VaultSecurityManager.unlockVault(password, keyFileToken, totpCode)`: gibt den DEK nach erfolgreicher Passwort- bzw. Passwort+Keyfile-Prüfung frei. `totpCode` bleibt nur als quellkompatibler Parameter, wird aber ignoriert; Legacy-TOTP wird nicht geprüft oder als Vault-Faktor verwendet.
- `VaultSecurityManager.unlockWithPassword(password)`: Wrapper für den obigen Pfad.
- `VaultSecurityManager.completeBiometricUnlock(Cipher)`: neuer Pfad, der eine vom erfolgreichen `BiometricPrompt.CryptoObject` zurückgegebene Cipher benötigt.
- `DocAnizerViewModel.authenticateBiometric(...)`: muss bei Vault-Biometrie den CryptoObject-Pfad verwenden; reine UI-Erfolgsmeldung darf keinen Vault-DEK freigeben.
- `AppLockScreen`: Passwort-Dialog ruft `unlockVault` auf und markiert anschließend die App als entsperrt. Der frühere allgemeine `unlockApp()`-Aufruf war auch ohne Prüfung auf erfolgreiches Vault-Unlock erreichbar.
- `AppLockScreen`: Null-Activity-Biometrie-Fallback zeigt jetzt einen Fehler und entsperrt nicht.
- `AppLockScreen`: Wiederherstellungs-Callback setzt nach erfolgreicher Recovery ein neues Passwort und markiert anschließend die App als entsperrt.
- `DocAnizerViewModel.lockApp()`: sperrt Vault und App-Session über den zentralen LockManager.
- `VaultSecurityManager.lockVault()`: überschreibt den intern gehaltenen DEK und setzt ihn auf `null`.

## 4. DEK-Verbraucher / öffentliche Kopien

Die Produktionsaufrufstellen im `DocAnizerViewModel`, `DmsManager` und `DocumentMigrationService` verwenden für suspendierbare Datei-/Batchoperationen `withUnlockedKeySuspend`; rein synchrone Schlüsseloperationen verwenden `withUnlockedKey`. Beide Varianten geben eine Kopie aus und überschreiben sie in `finally`. Bei `withUnlockedKey` wartet ein paralleler Lock auf das Ende des synchronen Callbacks. Bei `withUnlockedKeySuspend` verhindert ein Lock sofort neue Leases; eine bereits gestartete Operation darf ihren begrenzten Schreibvorgang beenden und überschreibt ihre Kopie unmittelbar bei Rückkehr/Abbruch. `getActiveDek()` bleibt vorerst als veralteter Kompatibilitäts-Getter erhalten und wird noch in bestehenden Tests verwendet; neue Produktionsaufrufe dürfen ihn nicht verwenden.

## 5. Empfohlene Zustandsdefinition

- **App-Lock** steuert Navigation/Anzeige und ist kein Beweis, dass ein DEK verfügbar ist.
- **Vault-Lock** steuert, ob kryptografische Operationen einen DEK nutzen dürfen.
- Ein Vault gilt nach Prozessstart, explizitem Sperren, Authentifizierungsabbruch und ungültigem/invalidiertem Keystore-Schlüssel als gesperrt.
- Entsperren ist nur nach erfolgreicher, überprüfbarer Passwort-/Recovery-Operation oder einer vom Android Keystore authentifizierten Cipher-Operation erlaubt.
- Bei Lock während einer zusammengesetzten Dateioperation: keine neue Lease ausgeben. Eine bereits erteilte suspendierbare Lease kann eine laufende Speicherung noch abschließen; ihre Kopie wird danach in `finally` gelöscht. Die UI darf nach Lock keine weiteren sensiblen Aktionen starten. Falls ein zukünftiges Produktdesign auch den Abbruch laufender Schreibvorgänge erfordert, braucht es einen eigenen Cancellation-/Rollback-Entwurf.
- Debug-Verhalten darf keinen Release- oder produktionsähnlichen Sicherheitsstatus vortäuschen.

## 6. Offene Risiken nach diesem Audit

1. Der veraltete öffentliche `getActiveDek()`-Kompatibilitäts-Getter bleibt vorhanden; Folgearbeiten sollen ihn nach Umstellung der Tests entfernen.
2. Die neuen Lease-/Lock-/Biometriepfade müssen lokal getestet und auf echten Geräten validiert werden.
3. Die atomare v1→v2-Laufzeitmigration ist separat in `docs/security/v14-migration-blocker.md` blockiert.
5. Klartext-Legacy-Secret-Migration in einen Keystore-basierten SecretStore: S08/S09.
6. Die TOTP-Entscheidung ist in `docs/security/adr-002-totp-decision.md` abgeschlossen: TOTP ist kein kryptografischer Tresor-Faktor; die Laufzeitbereinigung ist in Commit `d509ebb` umgesetzt.
