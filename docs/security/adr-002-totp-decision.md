# ADR 002: TOTP als zweiter Tresor-Faktor

**Status:** ENTSCHEIDUNG AUSSTEHEND — keine Produktoption wurde in den geprüften Repository-Artefakten ausdrücklich freigegeben.  
**Datum:** 2026-10-18  
**Betrifft:** `VaultSecurityManager`, `VaultHeader.totpSecret`, `unlockVault`, Sicherheitsstufen und Authentifizierungs-UI.

## Ausgangsbefund

- `VaultHeader` enthält `totpSecret`; `saveVaultHeader` serialisiert das Secret in den JSON-Header innerhalb der SharedPreferences.
- `unlockVault` prüft einen TOTP-Code vor der Passwort-KDF.
- Der DEK wird jedoch weiterhin allein mit dem aus Passwort/Keyfile abgeleiteten KEK verpackt. Der TOTP-Code ist nicht Bestandteil einer kryptografischen Schlüsselableitung oder eines zweiten DEK-Wrapping-Schritts.
- Damit ist der aktuelle TOTP-Pfad eine zusätzliche Software-Prüfung, aber keine kryptografisch unabhängige zweite Schutzkomponente gegen direkte Manipulation bzw. Umgehung des Aufrufpfads.
- Die Arbeitspaketliste markiert V06A als erledigt, aber der aktuelle Quellcode enthält weiterhin TOTP-Secret, Codeprüfung und TOTP-bezogene Faktorwerte. Ein dokumentierter Freigabenachweis für Option A wurde nicht gefunden.

## Option A — TOTP entfernen

- TOTP aus Einrichtung, Unlock, UI, Header-Modell und zugehörigen Status-/Faktorwerten entfernen.
- Bestehende gespeicherte TOTP-Secrets kontrolliert aus dem Header entfernen; das Entfernen muss vor dem Speichern geprüft werden.
- Keine Passwort-, Keyfile-, Biometrie- oder Recovery-Wege entfernen.
- Tests für Header mit/ohne Alt-Secret, Unlock nach Bereinigung und Neustart ergänzen.

**Vorteil:** keine irreführende 2FA-Behauptung und kein dauerhaft gespeichertes Klartext-TOTP-Secret.  
**Nachteil:** TOTP als Produktfunktion entfällt.

## Option B — echte kryptografische zweite Schutzkomponente

- Vor Umsetzung einen überprüfbaren Entwurf für die DEK-Freigabe genehmigen.
- Der zweite Faktor muss kryptografisch notwendig sein; Passwort allein darf den als 2FA markierten DEK nicht freigeben.
- TOTP-Secret geschützt speichern; Rate-Limit, Replay-Schutz, Gerätewechsel, Recovery und Wiederherstellung berücksichtigen.
- End-to-End-Negativtests müssen zeigen, dass Passwort allein, falscher/abgelaufener/wiederverwendeter Code und alternative öffentliche Unlock-Pfade den DEK nicht freigeben.

**Vorteil:** TOTP bleibt als echte zweite Komponente erhalten.  
**Nachteil:** erheblicherer Eingriff in Schlüsselverpackung, Unlock, Recovery und Tests.

## Freigabe

**Noch keine Option ausgewählt.** Die Arbeitspakete V06A und V06B dürfen nicht als umgesetzt gelten, bis die Produktentscheidung ausdrücklich feststeht und der entsprechende Pfad im Code nachweislich implementiert wurde.
