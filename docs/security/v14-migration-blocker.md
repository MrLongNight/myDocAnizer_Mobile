# V14 — v1→v2-Headermigration: Blocker

Status: IN ARBEIT — begrenzter V2-Runtimepfad für kompatible Passwort-only-Vaults; V1 bleibt als Rückfall erhalten und ist noch nicht vollständig abgelöst.

## Befund

- VaultSecurityManager liest und schreibt weiterhin den V1-Datensatz unter vault_header_json_v1. Für strikt begrenzte Passwort-only-Vaults ohne Recovery, Biometrie, Keyfile oder TOTP kann er inzwischen einen geprüften V2-Header aktivieren und bei späteren Starts über V2 entsperren.
- V1 bleibt bewusst erhalten. Der V2-Header wird aus dem erfolgreich entsperrten DEK erzeugt, zurückgelesen, streng geparst und kryptografisch geprüft, bevor er atomar vom Staging- zum Active-Key befördert wird.
- Wenn der V2-Header nicht nutzbar ist, bleibt V1 als Rückfall erhalten. Ein gültiger V2-Header kann für diese schmale Konfiguration allein geladen werden, falls der V1-Datensatz fehlt. Ein gespeicherter, aber unlesbarer Header verhindert die Legacy-Auto-Neuinitialisierung.
- Komplexe V1-Konfigurationen (Recovery, Biometrie, Keyfile/2FA und TOTP) sind noch nicht vollständig auf V2 abgebildet. Deshalb ist V14 weiterhin offen.

## Was vor dem Abschluss von V14 nötig ist

1. Die Lade-/Speicherschnittstelle V1/V2 weiter konsolidieren; ein gültiger V2-Header darf nicht zu einer automatischen Neuinitialisierung eines Ersatz-Tresors führen.
2. Den ergänzten V2-Unlockpfad für Passwort-only-Konfigurationen mit Recovery-/Lock-/Prozessneustart-Tests abdecken.
3. Recovery, Biometrie, Keyfile/2FA und TOTP samt Schlüssel-/Faktor-Metadaten verlustfrei in V2 überführen, bevor diese Konfigurationen migriert werden dürfen.
4. Den Readback-/Staging-/Promotionpfad für alle unterstützten Formate mit Schreibfehler-, Parse-, Authentifizierungs-, Prozessabbruch- und Idempotenztests absichern.
5. Vor der späteren Abschaltung von V1 eine atomare Commit-/Rollbackstrategie liefern, die den DEK nach Neustart weiterhin entsperrt und bei jedem Fehler den vorherigen Header bytegenau erhält.
6. Erfolgreichen und falschen Passwort-Unlock, defekten V2-Header mit V1-Fallback, fehlenden V1-Header bei gültigem V2, Schreibfehler und Idempotenz testen.
7. Erst nach bestandenem Testlauf und echter Wiederherstellungsabnahme darf V1 entfernt werden; beschädigte Header dürfen niemals eine automatische Neuinitialisierung auslösen.

## Entscheidung für diesen Arbeitslauf

V14 wird nicht als erledigt markiert, bis alle bestehenden Tresorvarianten migriert oder nachweislich von einer Migration ausgeschlossen sind und die Rollbacktests vorliegen. Der aktuelle Runtimepfad nutzt V2 nur für kompatible Passwort-only-Vaults; V1 bleibt als Rückfall erhalten, damit noch nicht migrierte Recovery-/Biometriepfade nicht beschädigt werden.


## Aktualisierung 2026-10-21 – v2-Faktorprüfung unabhängig gehärtet

Die eigenständige v2-Kryptokomponente `VaultHeaderCryptoV2` wurde so eingeschränkt, dass sie nur tatsächlich implementierte Faktoren akzeptiert:

- `ONE_FACTOR / PASSWORD`
- `TWO_FACTOR / PASSWORD_AND_KEYFILE` mit genau 64 hexadezimalen Zeichen als 256-Bit-Keyfile-Token.

TOTP, Passkey, unverschlüsselter Modus oder widersprüchliche Faktor-Metadaten werden vom v2-Parser abgelehnt, solange dieser Kryptopfad sie nicht vollständig umsetzt. Password+Keyfile gehen gemeinsam in die Argon2id-Keyableitung ein; Keyfile-Token werden vor der Ableitung hex-normalisiert. AAD bindet die Faktor-Metadaten, sodass ein Downgrade der gespeicherten Faktor-Erklärung nicht als Passwort-only-Unlock funktionieren darf. Neue Unit-Tests decken fehlende/falsche Keyfile, widersprüchliche Faktor-Metadaten und einen Downgrade-Versuch ab.

**Das hebt den V14-Blocker nicht auf.** `VaultSecurityManager` persistiert den kanonischen v1-Header weiterhin unter `vault_header_json_v1`; `VaultHeaderV2` ist noch nicht der Laufzeit-Header. Vor der Umschaltung müssen v1-Recovery-, Shamir-, Keyfile-Hash- und Biometrie-Metadaten verlustfrei modelliert, die Staging-/Readback-/Rollback-Sequenz implementiert und alle Fehlerpfade getestet werden. Automatische Neuinitialisierung oder destruktives Ersetzen des v1-Headers bleibt unzulässig.


## Aktualisierung 2026-10-21 – V2-Runtime für Passwort-only-Vaults

- Ein verifizierter V2-Header wird nach erfolgreichem V1-Unlock atomar vom Staging- zum Active-Key befördert; der V1-Datensatz bleibt bytegenau erhalten.
- Bei geeigneten Vaults wird der Active-V2-Header beim Laufzeit-Unlock verwendet. Ist V2 beschädigt oder nicht authentifizierbar, kann der unveränderte V1-Header den Unlock übernehmen und der V2-Shadow wird neu verifiziert.
- Wenn der V1-Datensatz fehlt, kann ein gültiger V2-Header für diese begrenzte Konfiguration allein geladen werden. Ein gespeicherter, aber unlesbarer Header verhindert die automatische Legacy-Neuinitialisierung.
- Passwortänderungen invalidieren veraltete V2-Shadow-Daten; bei vorhandenem V1 wird V2 mit dem neuen Passwort neu aufgebaut. Im V2-only-Modus wird nur der V2-Header neu verpackt.
- Die vollständige V1-Ablösung für Recovery-/Biometrie-/Keyfile-/TOTP-Konfigurationen sowie die Test- und Restore-Abnahme sind weiterhin offen.
