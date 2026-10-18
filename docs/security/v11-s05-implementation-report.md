# Implementierungsbericht V11–S05

Stand: 2026-10-18. Alle Änderungen in diesem Bericht wurden direkt in main vorgenommen. Der Nutzer hatte vor diesen Änderungen einen erfolgreichen Build und App-Start gemeldet; dieser Build deckt die nachfolgenden Commits nicht ab. Am 18.10.2026 meldete der Nutzer Compilerfehler an suspendierbaren Speicheroperationen und einen fehlenden Legacy-PIN-KDF. Die Korrekturen wurden auf `main` committed. Anschließend lief GitHub Actions `Build APK` für Commit `2fbafaffab982e33074a10522112a46a133d6fb2` durch: `Build Debug APK`, `Find and Verify APK` und `Upload APK Artifact` meldeten Erfolg. Das bestätigt den Debug-APK-Build des Produktionscodes. `testDebugUnitTest` und die Geräte-/Integrationstests wurden durch diesen Workflow nicht ausgeführt und bleiben offen.

| Arbeitspaket | Ergebnis | Offene Abnahme |
|---|---|---|
| V11 QR | Eigenbau-Matrix durch ZXing-QR-Code ersetzt; UTF-8, Fehlerkorrektur M, Margin 4 und 2.000-Byte-Grenze. Unabhängiger Decoder-Roundtrip-Test ergänzt. | Unit-Test ausführen und QR aus einem echten PDF mit Scanner-App testen. |
| V12 Emergency-PDF | Kein Roh-DEK-Base64 mehr. PDF/QR verwendet verschlüsseltes Recovery-Payload; getrennte Recovery-Passphrase mit mindestens 20 Zeichen, Bestätigungsfeld und Anleitung ergänzt. Recovery akzeptiert JSON-QR-Inhalt oder Base64 des verschlüsselten Payload-Fallbacks. | PDF-Textlayer auf einem Gerät extrahieren und nach Test-DEK/Passphrase durchsuchen; Export und Wiederherstellung Ende-zu-Ende testen. |
| V13 Biometrie | Android-Keystore-Key verlangt Authentifizierung pro Nutzung und wird bei Biometrieänderung invalidiert. Enrollment/Unlock verwenden BiometricPrompt-CryptoObject; der alte parameterlose Biometrie-Unlock schlägt fail-closed fehl. Instrumentierter Test für nicht authentifizierte Cipher-Nutzung ergänzt. | Erfolgreiche Authentifizierung, Abbruch, falsche Biometrie und Key-Invalidierung auf echten Geräten testen. |
| V14 Header-Migration | **Nicht implementiert und bewusst blockiert.** Der Laufzeitpfad verwendet weiterhin v1; V2-Parser/AEAD sind nicht als kanonisches Laufzeitformat integriert. Eine einfache Ersetzung würde den aktuellen Unlock-Pfad brechen. | Erst V2-Laufzeit-Lade-/Speicheradapter, Staging, kryptografischer Readback, Rollback und Idempotenztests umsetzen. |
| V15 Backup | Vault-Sicherheits- und allgemeine Prefs werden aus Cloud-Backup und Geräteübertragung ausgeschlossen; strukturelle XML-Tests ergänzt. | Unit-Tests ausführen; wenn möglich Backup/Restore bzw. Device Transfer auf Gerät prüfen. |
| S01 Audit | Lock-/Secret-Inventar und offene Speicherstellen dokumentiert. | Keine weitere Code-Abnahme für den Audit selbst erforderlich. |
| S02 LockManager | Zentraler typisierter App-/Vault-Lock-State; vorhandener Vault startet fail-closed; ViewModel-eigenes Wahrheits-Flag entfernt. | Unit-Tests ausführen und Start/Unlock/Lock auf Gerät prüfen. |
| S03 Auto-Lock | ProcessLifecycleOwner-Observer mit SystemClock.elapsedRealtime; 0 Minuten sperrt sofort; Timer wird beim Zurückkehren ausgewertet. | Unit-Tests und Hintergrund-/Vordergrund-Gerätetests ausführen. |
| S04 Bypässe | Allgemeiner unlockApp()-Pfad, Null-Activity-Entsperrung und UI-only Biometrie als Vault-Authentifizierung entfernt. Debug-Freigabe bleibt auf Debug-Build beschränkt und gibt keinen Vault-DEK frei. | Negative Tests für falsches Passwort, Abbruch, fehlende Activity, Recovery und Release-Build ausführen. |
| S05 Key-Leases | `withUnlockedKey` schützt synchrone Operationen; `withUnlockedKeySuspend` stellt für suspendierbare Datei-/Batchoperationen eine Kopie bereit, die nach Ende/Abbruch gelöscht wird. Lock verhindert sofort neue suspendierbare Leases; ein bereits laufender Schreibvorgang kann noch abschließen. Fehlender `deriveKekFromPin`-Helper wurde mit den dokumentierten v1-Parametern (PBKDF2-HMAC-SHA256, 50.000 Iterationen, 256 Bit) wiederhergestellt. | Debug-APK-Build durch GitHub Actions bestanden; `testDebugUnitTest` und manuelle/Device-Tests bleiben offen. Legacy-/Testverwendungen von `getActiveDek()` später entfernen. |

## Geänderte bzw. neue Hauptdateien

- app/src/main/java/com/example/service/VaultSecurityManager.kt
- app/src/main/java/com/example/service/BiometricAuthManager.kt
- app/src/main/java/com/example/service/LockManager.kt
- app/src/main/java/com/example/service/VaultAutoLockObserver.kt
- app/src/main/java/com/example/ui/DocAnizerViewModel.kt
- app/src/main/java/com/example/ui/DmsManager.kt
- app/src/main/java/com/example/ui/views/AppLockScreen.kt
- app/src/main/java/com/example/ui/views/settings/VaultSettingsScreen.kt
- app/src/main/java/com/example/service/DocumentMigrationService.kt
- app/src/main/res/xml/backup_rules.xml
- app/src/main/res/xml/data_extraction_rules.xml
- app/src/test/java/com/example/QrEncodingTest.kt
- app/src/test/java/com/example/BackupRulesSecurityTest.kt
- app/src/test/java/com/example/LockManagerTest.kt
- app/src/test/java/com/example/VaultAutoLockObserverTest.kt
- app/src/test/java/com/example/LockLeaseTest.kt
- app/src/androidTest/java/com/example/VaultBiometricCryptoTest.kt

## Testdokumente

- docs/security/manual-test-checklist-v01-v10.md
- docs/security/manual-test-checklist-v11-s05.md
- docs/security/v14-migration-blocker.md
- docs/security/lock-secret-inventory.md

## Abnahmegrenze

Der Quellcode ist geändert, aber nicht durch einen neuen Build bestätigt. Bitte zuerst den aktuellen main synchronisieren, dann `./gradlew assembleDebug testDebugUnitTest` ausführen. Danach die V11–S05-Gerätetests aus der Checkliste durchführen. V14 bleibt blockiert und ist nicht als erledigt markiert.
