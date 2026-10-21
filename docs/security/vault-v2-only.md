# Vault V2-only Architektur

Status: V2-only-Implementierung; keine V1-Migration.

## Verbindliche Laufzeitregeln

- VaultHeaderV2 ist das einzige persistierte Tresorformat.
- Neue Tresore werden direkt als V2 erzeugt.
- Entsperren, Passwortwechsel, Recovery, Biometrie, Keyfile/2FA und TOTP verwenden denselben V2-Datensatz.
- Es gibt keinen V1-Parser, keine V1-Entschlüsselung, keinen V1-Fallback, keine V1-Fixtures und keine Migration.
- Vorhandene V1-Daten werden nicht übernommen. Ein fehlender aktiver V2-Header bedeutet, dass kein nutzbarer Tresor initialisiert ist; ein ungültiger aktiver V2-Header schlägt geschlossen fehl.
- Ein bestehender oder beschädigter Header wird beim Start nicht automatisch ersetzt.

## Verifikation

Für den Abschluss muss `./gradlew assembleDebug testDebugUnitTest` erfolgreich laufen. Zusätzlich muss die Repository-Suche bestätigen, dass keine produktiven V1-Header-/Unlockpfade oder V1-Kompatibilitätstests verbleiben. Der Status wird erst nach dem tatsächlichen CI-Ergebnis auf erledigt gesetzt.
