# V14 — v1→v2-Headermigration: Blocker

Status: BLOCKIERT — keine automatische Migration aktiviert.

## Befund

- Die aktuelle Laufzeitkomponente VaultSecurityManager liest und schreibt weiterhin den Schlüssel vault_header_json_v1 in SharedPreferences.
- VaultHeaderV2 und VaultHeaderCryptoV2 sind bislang ein getrenntes Datenmodell bzw. eine Kryptokomponente. Der aktuelle Laufzeit-Unlock liest nicht automatisch den v2-Header und kann dessen AAD-gebundene DEK-Verpackung nicht über den bestehenden v1-Entschlüsselungspfad öffnen.
- Ein bloßes Schreiben eines v2-Headers und anschließendes Löschen des v1-Headers würde die laufende App beim nächsten Start am Entsperren hindern. Das wäre keine sichere Migration.
- Die neue v1-Fixture beweist v1-Kompatibilität, aber noch keine erfolgreiche Laufzeitmigration auf v2.

## Was vor dem Abschluss von V14 nötig ist

1. Eine kanonische Header-Lade-/Speicherschnittstelle einführen, die v1 und v2 explizit unterscheiden kann.
2. Unlock muss v1 mit der alten KDF und v2 über VaultHeaderCryptoV2 öffnen.
3. Bei erfolgreichem v1-Unlock den identischen 32-Byte-DEK mit dem freigegebenen v2-Format neu verpacken.
4. v2 in einen Staging-Key schreiben, aus dem Staging-Key erneut lesen, streng parsen und kryptografisch mit dem Passwort verifizieren.
5. Erst nach erfolgreichem Readback den kanonischen Header atomar auf v2 umstellen. v1 muss bei jedem vorherigen Fehler bytegenau erhalten bleiben.
6. Erfolg, falsches Passwort, Schreibfehler, Parsefehler, Authentifizierungsfehler, Rollback und Idempotenz testen.
7. Legacy-Recovery-Metadaten und vorhandene Unlock-/Recovery-Aufrufstellen explizit behandeln; keine automatische Neuinitialisierung bei beschädigtem Header.

## Entscheidung für diesen Arbeitslauf

V14 wird nicht als erledigt markiert, bis die Laufzeitintegration und die Rollbacktests vorliegen. Die App bleibt auf dem bisherigen v1-Laufzeitformat, damit die vorhandenen Unlock-Pfade nicht durch einen unvollständigen Formatwechsel beschädigt werden.


## Aktualisierung 2026-10-21 – v2-Faktorprüfung unabhängig gehärtet

Die eigenständige v2-Kryptokomponente `VaultHeaderCryptoV2` wurde so eingeschränkt, dass sie nur tatsächlich implementierte Faktoren akzeptiert:

- `ONE_FACTOR / PASSWORD`
- `TWO_FACTOR / PASSWORD_AND_KEYFILE` mit genau 64 hexadezimalen Zeichen als 256-Bit-Keyfile-Token.

TOTP, Passkey, unverschlüsselter Modus oder widersprüchliche Faktor-Metadaten werden vom v2-Parser abgelehnt, solange dieser Kryptopfad sie nicht vollständig umsetzt. Password+Keyfile gehen gemeinsam in die Argon2id-Keyableitung ein; Keyfile-Token werden vor der Ableitung hex-normalisiert. AAD bindet die Faktor-Metadaten, sodass ein Downgrade der gespeicherten Faktor-Erklärung nicht als Passwort-only-Unlock funktionieren darf. Neue Unit-Tests decken fehlende/falsche Keyfile, widersprüchliche Faktor-Metadaten und einen Downgrade-Versuch ab.

**Das hebt den V14-Blocker nicht auf.** `VaultSecurityManager` persistiert den kanonischen v1-Header weiterhin unter `vault_header_json_v1`; `VaultHeaderV2` ist noch nicht der Laufzeit-Header. Vor der Umschaltung müssen v1-Recovery-, Shamir-, Keyfile-Hash- und Biometrie-Metadaten verlustfrei modelliert, die Staging-/Readback-/Rollback-Sequenz implementiert und alle Fehlerpfade getestet werden. Automatische Neuinitialisierung oder destruktives Ersetzen des v1-Headers bleibt unzulässig.
