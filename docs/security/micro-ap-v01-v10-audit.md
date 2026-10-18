# V01–V10 Codeabgleich — 2026-10-18

**Branch:** `main`  
**Geprüfte Anforderungsbasis:** `docs/newAP-prompts.md`, insbesondere V01–V10, sowie `docs/security/adr-001-recovery-format.md`.  
**Wichtig:** Der Code wurde direkt in `main` geändert. In dieser Umgebung konnte ich weder Gradle noch Android-Tests ausführen. Die neuen und angepassten Tests sind daher **nicht ausgeführt** und noch nicht abgenommen.

## Ergebnis nach Arbeitspaket

| AP | Ergebnis | Änderung / Restpunkt |
|---|---|---|
| V01 | Verbessert | Die bisherigen v1-Tests prüften überwiegend nur JSON-Felder und verwendeten ein nicht wirklich durch den Vault entsperrtes Fixture. Sie prüfen jetzt den tatsächlich persistierten v1-Header, falsches Passwort, erfolgreichen Unlock und manipulierten AES-GCM-Ciphertext. |
| V02 | Behoben, Test offen | Argon2id-Parameter werden nicht mehr stillschweigend auf schwache Werte geklemmt. Neue Vaults verwenden 19 MiB / t=2 / p=1; der exakte Legacy-Tupel 16 MiB / t=2 bleibt für v1-Lesbarkeit zugelassen. RFC-9106-Referenzvektor-Test ergänzt. |
| V03 | Behoben, Test offen | Der v2-Parser akzeptiert nur die implementierte Algorithmus-ID, mindestens 19 MiB und t≥2 sowie exakt 48 Byte verschlüsselten DEK einschließlich GCM-Tag. Negativtests ergänzt. |
| V04 | Implementiert, Test offen | Neue `VaultHeaderCryptoV2`-Komponente verpackt/öffnet einen 32-Byte-DEK mit AES-256-GCM. AAD bindet Version, Algorithmus, Vault-ID, Zeitstempel, KDF-Parameter, Salt und sicherheitsrelevante Header-Metadaten. IV und Salt werden zufällig erzeugt; falscher Schlüssel oder veränderte Metadaten schlagen fehl. Keine v1→v2-Migration vorgenommen. |
| V05 | ADR erstellt | `docs/security/adr-002-totp-decision.md` dokumentiert beide Optionen und den Istzustand. Die Produktentscheidung ist noch offen. |
| V06A | **Blockiert** | Die Arbeitspaketliste markierte das Entfernen von TOTP als erledigt, aber der aktuelle Code enthält weiterhin TOTP-Secret und Unlock-Prüfung. Ein expliziter Freigabenachweis für Option A fehlt. Ich habe diese Produktentscheidung nicht eigenmächtig getroffen und die Statusangabe korrigiert. |
| V07 | Verbessert, Test offen | Test für den offiziellen BIP-39-Nullentropie-Vektor (`abandon` × 11 + `about`), Checksum-Fehler und Wortlistenlänge 2048 ergänzt. Temporäre Seed-/Passwortpuffer werden nach PBKDF2 gelöscht. |
| V08 | Behoben, Test offen | Der Vault erzeugte vorher einen Share-Satz zum Hashen und einen zweiten zufälligen Share-Satz zur Rückgabe. Gespeicherte Hashes konnten daher nicht zu den ausgegebenen Shares passen. Hashes und zurückgegebene Shares stammen jetzt aus exakt demselben Satz; Regressionstest ergänzt. |
| V09 | Ergänzt | Recovery-ADR bleibt die Formatgrundlage. Sie definiert jetzt explizit mindestens 20 Zeichen für die Passphrase; das ist eine Mindestlänge, kein Ersatz für Unvorhersagbarkeit. |
| V10 | Neu umgesetzt, Test offen | Recovery-Payload entspricht nun dem genehmigten JSON-v2-Format. Keine PBKDF2-Ausweichableitung mehr; Version, Algorithmus, Vault-ID, Salt, IV, Ciphertext- und Taglänge sowie maximale Payloadgröße werden geprüft. Vault-ID wird als AAD gebunden; nur ein exakt 32 Byte langer DEK wird akzeptiert. Tests für Roundtrip, falsche Passphrase, falsche Vault-ID, Manipulation und nachgestellte Daten ergänzt. |

## Weitere Sicherheitskorrekturen

- KEK wird im Unlock-Pfad auch bei Fehlern gelöscht; der vorherige aktive DEK wird vor dem Ersetzen überschrieben.
- Beim Ändern des Master-Passworts wird der temporäre KEK auch dann gelöscht, wenn AES-GCM fehlschlägt.
- Neue Vaults verwenden die stärkeren Argon2id-Parameter; bestehende v1-Header mit dem exakt dokumentierten Legacy-Tupel bleiben ableitbar.

## Dateien

- `app/src/main/java/com/example/service/RecoveryPayloadEngine.kt`
- `app/src/main/java/com/example/service/VaultHeaderCryptoV2.kt` (neu)
- `app/src/main/java/com/example/service/VaultHeaderV2.kt`
- `app/src/main/java/com/example/service/VaultSecurityManager.kt`
- `app/src/test/java/com/example/RecoveryPayloadEngineTest.kt` (neu)
- `app/src/test/java/com/example/VaultHeaderCryptoV2Test.kt` (neu)
- `app/src/test/java/com/example/Argon2idReferenceVectorTest.kt` (neu)
- `app/src/test/java/com/example/VaultRecoverySecurityTest.kt` (neu)
- `app/src/test/java/com/example/VaultV1CompatibilityTest.kt`
- `app/src/test/java/com/example/VaultHeaderV2Test.kt`
- `app/src/test/java/com/example/ExampleRobolectricTest.kt`
- `docs/security/adr-001-recovery-format.md`
- `docs/security/adr-002-totp-decision.md` (neu)
- `docs/newAP-prompts.md`

## Offene Abnahme

1. Lokal ausführen: `./gradlew assembleDebug testDebugUnitTest`.
2. Bei Fehlern zuerst Kotlin-/API-Kompatibilität und die neuen Referenzvektortests korrigieren.
3. V05/V06A erst abschließen, wenn Option A oder B ausdrücklich freigegeben ist.
4. PDF-/QR-Export und Entfernung des Klartext-DEK sind späteren APs zugeordnet und wurden in diesem V01–V10-Durchlauf nicht verändert.
