# Lock- und Secret-Inventar

Stand: 2026-10-18 — Repository `main`. Dieses Dokument ist das Audit-Ergebnis für S01; es beschreibt den vor der LockManager-Integration gefundenen Zustand.

## 1. Zustandsquellen

| Bereich | Quelle | Befund / Risiko |
|---|---|---|
| Vault-Lock | `VaultSecurityManager.activeDek` | `null` bedeutet gesperrt; nicht persistiert. Das ByteArray wird bei `lockVault()` überschrieben. |
| Öffentlicher DEK-Zugriff | `VaultSecurityManager.getActiveDek()` | Liefert eine mutable Kopie, die unabhängig vom späteren Lock weiter existieren kann, wenn ein Aufrufer sie nicht löscht. |
| App-Lock | `DocAnizerViewModel._isAppUnlocked` | Separater MutableStateFlow; kann vom Vault-Zustand abweichen. |
| App-Lock-Konfiguration | `SettingsRepository.biometricAuthEnabled` | Ist ein App-Setting und allein kein Nachweis einer erfolgreichen Vault-Kryptografieoperation. |
| App-Start | `DocAnizerViewModel`-Initialisierung | DEBUG startet standardmäßig entsperrt. Außerdem gab es einen Fallback, der ohne verfügbare Activity direkt `unlockApp()` aufruft. |
| Auto-Lock | `VaultHeader.autoLockMinutes` | Einstellung wird im Header persistiert; im untersuchten Pfad war keine zentrale, monotone Lifecycle-Sperrsteuerung über einen LockManager vorhanden. |

## 2. Persistierte Geheimnisse und Schlüsselmaterial

- `mydocanizer_vault_security.xml` (SharedPreferences-Datei aus `getSharedPreferences("mydocanizer_vault_security", ...)`): enthält `vault_header_json_v1`, inklusive verschlüsseltem DEK, Salt/IV und weiteren Recovery-/Faktor-Metadaten. Im Header werden auch TOTP-Secret und weitere Authentifizierungsmetadaten serialisiert; das TOTP-Secret ist kein verschlüsselter SecretStore-Wert.
- `mydocanizer_prefs.xml` (SharedPreferences-Datei aus `getSharedPreferences("mydocanizer_prefs", ...)`): enthält mindestens das Legacy-Feld `sync_master_password` und `biometric_auth_enabled`. Verbraucher in Sync-/Dokumentenpfaden lesen das Sync-Passwort.
- Android Keystore: Biometrie-DEK-Wrapping-Key unter Alias `myDocAnizer_vault_biometric_key_v2` (neuer auth-gebundener Schlüsselpfad). Schlüsselmaterial sollte nicht exportiert werden; Verlust/Invalidierung muss auf Passwort-Recovery zurückfallen.
- Room-Datenbank `mydocanizer_database` und zugehörige WAL/SHM-Dateien: enthalten App-Daten. Die Backupregeln schließen sie vom Cloud-Backup aus; Geräteübertragungsregeln sind separat zu prüfen.
- Cache-/Exportdateien: Emergency-PDF wird unter `cacheDir/myDocAnizer_Notfall_Emergency_Kit.pdf` erzeugt und über FileProvider geteilt. Der neue Export darf nur verschlüsseltes Recovery-Payload, keinen Roh-DEK, enthalten.
- RAM: temporäre DEK-/KEK-/Passwort-/Recovery-Arrays in `VaultSecurityManager`, Dokumentenoperationen und ViewModel-Aufrufstellen. Kopien müssen möglichst in `finally` überschrieben werden.

## 3. Unlock- und Lock-Pfade vor S02–S04

- `VaultSecurityManager.unlockVault(password, keyFileToken, totpCode)`: Passwort-/Keyfile-Ableitung und optional TOTP-Prüfung; bei Erfolg setzt sie `activeDek`.
- `VaultSecurityManager.unlockWithPassword(password)`: Wrapper für den obigen Pfad.
- `VaultSecurityManager.completeBiometricUnlock(Cipher)`: neuer Pfad, der eine vom erfolgreichen `BiometricPrompt.CryptoObject` zurückgegebene Cipher benötigt.
- `DocAnizerViewModel.authenticateBiometric(...)`: muss bei Vault-Biometrie den CryptoObject-Pfad verwenden; reine UI-Erfolgsmeldung darf keinen Vault-DEK freigeben.
- `AppLockScreen`: Passwort-Dialog ruft `unlockVault` auf und markiert anschließend die App als entsperrt. Der frühere allgemeine `unlockApp()`-Aufruf war auch ohne Prüfung auf erfolgreiches Vault-Unlock erreichbar.
- `AppLockScreen`: früherer Null-Activity-Biometrie-Fallback rief `unlockApp()` auf; dieser Pfad muss fail-closed sein.
- `AppLockScreen`: Wiederherstellungs-Callback setzt nach erfolgreicher Recovery ein neues Passwort und markiert anschließend die App als entsperrt.
- `DocAnizerViewModel.lockApp()`: ruft `VaultSecurityManager.lockVault()`; der App-Lock-Status wurde nur abhängig von einer Setting-Flag geändert. App- und Vault-Lock konnten daher auseinanderlaufen.
- `VaultSecurityManager.lockVault()`: überschreibt den intern gehaltenen DEK und setzt ihn auf `null`.

## 4. DEK-Verbraucher / öffentliche Kopien

Die Suche nach `getActiveDek()` ergab Aufrufstellen im `DocAnizerViewModel`, unter anderem in Batch-Import/Commit-, Dokument-/Dateioperationen und weiteren DMS-Flows. Jede Rückgabe ist eine separate ByteArray-Kopie und wird nicht automatisch durch `lockVault()` gelöscht. Die schrittweise Migration auf eine Lease-/Callback-API ist S05; nach dem Schritt verbleibende Verbraucher müssen separat aufgelistet bleiben.

## 5. Empfohlene Zustandsdefinition

- **App-Lock** steuert Navigation/Anzeige und ist kein Beweis, dass ein DEK verfügbar ist.
- **Vault-Lock** steuert, ob kryptografische Operationen einen DEK nutzen dürfen.
- Ein Vault gilt nach Prozessstart, explizitem Sperren, Authentifizierungsabbruch und ungültigem/invalidiertem Keystore-Schlüssel als gesperrt.
- Entsperren ist nur nach erfolgreicher, überprüfbarer Passwort-/Recovery-Operation oder einer vom Android Keystore authentifizierten Cipher-Operation erlaubt.
- Bei Lock während einer zusammengesetzten Dateioperation: keine neue Lease ausgeben; laufende Operationen müssen kooperativ abbrechen oder ihre bereits erteilte Lease innerhalb eines begrenzten, dokumentierten Abschnitts beenden. Keine neue DEK-Kopie nach Lock.
- Debug-Verhalten darf keinen Release- oder produktionsähnlichen Sicherheitsstatus vortäuschen.

## 6. Offene Risiken nach diesem Audit

1. Öffentliche DEK-Kopien und ihre Lebensdauer: S05.
2. Ein zentraler, typisierter Zustand und fail-closed Prozessstart: S02.
3. Lifecycle-Auto-Lock mit monotoner Zeit: S03.
4. Alle Unlock-Bypässe und Null-Activity-Pfade: S04.
5. Klartext-Legacy-Secret-Migration in einen Keystore-basierten SecretStore: S08/S09.
6. TOTP bleibt eine separate offene Produktentscheidung in `docs/security/adr-002-totp-decision.md`; dieses Inventar nimmt keine Entscheidung vorweg.
