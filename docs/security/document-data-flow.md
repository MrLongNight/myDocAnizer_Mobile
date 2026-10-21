# D01 – Datenfluss-Inventar für Dokumente, OCR, Backups und Sync

**Audit-Datum:** 2026-10-21  
**Status:** Quellcode-Inventar erstellt; keine Produktlogik geändert.  
**Zweck:** Ist-Zustand festhalten, bevor Datenbank- und Dateiformatverschlüsselung umgestellt werden. Aussagen beziehen sich auf den geprüften Code, nicht auf einen vollständigen Geräte-/Restore-Test.

## 1. Kurzbefund

Die App verschlüsselt bestimmte Dokumentdateien bereits mit AES-256-GCM. Das bedeutet jedoch noch nicht, dass der gesamte Dokumentdatenfluss verschlüsselt ist: die Room-Datenbank und ihr FTS-Index, ausgewählte Speicherorte, temporäre Dateien, nicht verschlüsselte Dokumente und Restore-Zwischenstände können weiterhin Klartext enthalten.

Besonders wichtig sind drei verschiedene Dinge, die derzeit teils über getrennte Zustände gesteuert werden:

- `DocumentEntity.isEncrypted` ist ein Datenbankfeld und nicht der Nachweis, dass die Datei korrekt verschlüsselt ist.
- `generateInternalFileName(..., isEncrypted = true)` hängt `.enc` an den Dateinamen. Die Dateiendung allein authentifiziert den Inhalt nicht.
- `DocumentStorageService.encryptFile` hat zwei Schlüsselpfade: direkte Schlüsselbytes (z. B. DEK) sowie passwortbasierte Schlüsselableitung. Die Dateistruktur enthält Salt, IV und Ciphertext, aber in den geprüften Routinen keinen Magic-Wert, keine Formatversion und keine AAD-Bindung an Vault-/Dokument-ID.

## 2. Pfade und Speicherorte

| Bereich | Implementierung / Daten | Ist-Zustand und Risiko |
|---|---|---|
| Room-Datenbank | `AppDatabase.kt`, Datenbankname `mydocanizer_database`, Schema-Version 9 | Standard-`Room.databaseBuilder`, kein SQLCipher-`openHelperFactory`. Titel, Absender, Pfad, Tags, OCR-Text, benutzerdefinierte Felder und Finanzdaten werden deshalb nicht durch eine Datenbankverschlüsselung geschützt. `exportSchema = false`; der Downgrade-Fallback kann Tabellen zerstören. |
| FTS-Suche | `DocumentDao.kt`, `documents_fts` aus der Migration 8→9 | FTS4 enthält Titel, Absender, OCR-Text und Tags. Der Index ist Teil derselben unverschlüsselten SQLite-Datenbank. Es gibt sowohl eine FTS-Abfrage als auch einen `LIKE`-Fallback über `ocrText`. |
| Standardablage | `DocumentStorageService.getBaseDocumentsDirectory` | `APP_STORAGE` fällt auf `context.filesDir/Documents` zurück. App-interner Speicher ist die bevorzugte Basis, wird aber erst durch ein korrektes Dateiformat und einen sicheren Schlüssel geschützt. |
| App-spezifischer externer Speicher | `locationType = DOCUMENTS` oder `EXTERNAL_STORAGE` | Verwendet `getExternalFilesDir(...)`, also app-spezifische externe Verzeichnisse. Diese Pfade sind nicht mit öffentlichem Shared Storage gleichzusetzen, sollten aber nicht als Ersatz für Inhaltsverschlüsselung betrachtet werden. |
| Benutzerdefinierter Pfad | `locationType = CUSTOM`, `customPath` | Kann außerhalb der App-Sandbox liegen. Es gibt hier keine generelle Garantie, dass das Ziel nur app-intern oder gegen andere lokale Zugriffe geschützt ist. Vor einer Freigabe muss die UI den Sicherheitsstatus des gewählten Speicherorts klarstellen. |
| Sicheres Speichern | `DocumentStorageService.secureSave` | Schreibt die Eingabe zunächst in eine temporäre Datei unter `cacheDir`. Danach wird abhängig von `isEncrypted` verschlüsselt oder Klartext verschoben/kopiert. Die temporäre Datei wird in `finally` gelöscht, aber nicht sicher überschrieben. Das ist auf Flash-Speicher keine verlässliche physische Löschung. |
| Dateiverschlüsselung | `DocumentStorageService.encryptFile` / `decryptFile` | AES-GCM mit 64-KiB-Puffer; einmal mit Schlüsselbytes und einmal mit PBKDF2-aus-Passwort. Der Output wird direkt am übergebenen Zielpfad geschrieben. Eine spätere AP muss das Commit-Verhalten bei Abbruch sowie die Löschung partieller Ausgaben testen und absichern. |
| Scan/PDF und OCR | `DocumentStorageService.generatePdf`, `DocumentEntity.ocrText` | Scan-Seiten werden als Bilder in ein PDF gezeichnet. Der übergebene OCR-Text wird derzeit nicht als PDF-Textlayer geschrieben (separate Bestätigung: `docs/security/d14-pdf-text-layer-audit.md` und `PdfTextLayerInstrumentedTest`). OCR-Text wird in Room gespeichert und ist über FTS/LIKE suchbar. |
| Dokumentmigration | `DocumentMigrationService.migrateAllDocuments` | Liest die registrierten Dokumente, erstellt eine neue Datei per `secureSave`, aktualisiert danach den DB-Pfad und löscht erst anschließend die alte Datei. Fehler müssen darauf geprüft werden, ob alte Dateien erhalten bleiben und keine verwaisten Zieldateien zurückbleiben. Der Aufrufer übergibt weiterhin einen Passwort-String; sichere Schlüssel-Leases werden nur für den Pfad verwendet, der den aktiven DEK nutzt. |
| Cloud-Sync | `CloudSyncWorkerService.performBackgroundSync` | Prüft Konfiguration und Air-Gap-Schalter, verpackt ausstehende Datei-Payloads vor dem Upload erneut mit `encryptFile` und legt verschlüsselte Zwischenblobs unter `cacheDir` an. Das Passwort liegt als String im Speicher und wird durch Aufrufschichten gereicht. Metadata-/Restore-Verhalten muss zusammen mit der Backup-ADR geprüft werden. |
| Backup-Erstellung | `DocumentStorageService.createEncryptedBackupArchive` | Baut zunächst einen ZIP-Container unter `cacheDir`; darin liegen `doc_index.json`, optional `vault_metadata.json` und die ausgewählten Dokumentbytes. Anschließend wird der ZIP-Container verschlüsselt. Während der Erstellung können deshalb Klartext-Metadaten und alle unverschlüsselt gespeicherten Dokumentbytes im Cache liegen; die Zwischen-Datei wird gelöscht, aber nicht sicher überschrieben. |
| Backup-Restore | `DocumentStorageService.restoreEncryptedBackupArchive` | Entschlüsselt zunächst den äußeren Container in eine temporäre ZIP-Datei und extrahiert Einträge in ein temporäres Verzeichnis. Vor produktiver Freigabe sind Größen-/Anzahlgrenzen für ZIP-Einträge, Abbruchbereinigung, Integritätsverifikation und atomarer Commit mit separaten Tests nachzuweisen. |
| P2P-/Browser-Upload | `P2pSyncServer` | Der Listener verwendet im geprüften Code `ServerSocket`; Upload-Metadaten können ein `x-encrypted`-Flag liefern. Ein clientseitiges Flag beweist keine Verschlüsselung. Transportbefunde sind separat in `docs/security/p01-p2p-transport-adr.md` dokumentiert: der Ist-Pfad ist HTTP, nicht eine verifizierte TLS-Verbindung. |
| Diagnose/temporäre Dateien | `cacheDir`, PDF-/Restore-/Sync-Zwischenstände | Mehrere Flows löschen temporäre Dateien nur regulär. Es gibt keine allgemeine Garantie, dass deren Inhalt nach Prozessabbruch sofort entfernt oder physisch überschrieben ist. Sensible Klartext-Zwischenstände müssen auf Minimum reduziert und durch Tests für Fehler/Abbruch geprüft werden. |

## 3. Sensible persistierte Felder

`DocumentEntity` enthält unter anderem Titel, Absender, Dateiname und -pfad, Kategorie, Tags, OCR-Text, erkannte Beträge/Fristen sowie Sync- und Verschlüsselungsflags. Weitere Room-Tabellen speichern Custom Fields, Finanztransaktionen, Bankauszüge und Dashboard-Widgets. Die konkreten Entities sind in `app/src/main/java/com/example/model/Models.kt` dokumentiert; ihre Felder liegen aktuell in derselben unverschlüsselten Datenbank.

Der FTS-Index ist kein unabhängiger verschlüsselter Dienst: Er liegt als Tabelle in der SQLite-Datei und enthält abgeleitete OCR-/Metadaten erneut. Die vollständige DB-Verschlüsselung muss daher die Haupttabellen, FTS4, Trigger und WAL/SHM-Lifecycle zusammen erfassen.

## 4. Sicherheitsrelevante Abhängigkeiten / Reihenfolge

1. **D02 + DB01/DB02:** SQLCipher-/Room-Aufbau und echte Schema-Baseline festlegen.
2. **D03:** zufälligen DB-Schlüssel, Vault-KeyProvider, Domain-Separation, Lock-/Unlock-Lifecycle und portables Recovery definieren.
3. **D04/D05:** versioniertes Streaming-Format und atomaren Save-Commit bauen; bestehende Datei-Formate weiter lesbar halten.
4. **D06/D07/DB05/DB06:** Viewer, OCR, Vorschaubilder, Share/Export, Suchindex und Listenergebnisse abdecken.
5. **D10 und Restore-APs:** Backup-Limits, Integritätsprüfung, Restore-Transaktion, Recovery und Rollback testen.
6. **P02/P03:** P2P nur gemäß separater Transport-ADR ändern; Dateipayload-Verschlüsselung ersetzt kein TLS.

## 5. Geprüfte Quellstellen

- `app/src/main/java/com/example/data/AppDatabase.kt:17-29, 180-194` – Room-Schema, Version und Öffnung.
- `app/src/main/java/com/example/data/DocumentDao.kt:65-85` – LIKE-/FTS-Pfade.
- `app/src/main/java/com/example/model/Models.kt:22` und die weiteren Entity-Deklarationen – persistierte Datenfelder.
- `app/src/main/java/com/example/service/DocumentStorageService.kt:32-58` – Speicherorte; `291-330` – secureSave; `365-450` – PDF; `653-831` – Datei-Krypto; `1172-1439` – Backup/Restore.
- `app/src/main/java/com/example/service/DocumentMigrationService.kt:33-105` – Dateimigration.
- `app/src/main/java/com/example/service/CloudSyncWorkerService.kt:65-180` – Sync-/Verschlüsselungspfad.
- `app/src/main/java/com/example/service/P2pSyncServer.kt:82, 286, 462-600` – Listener und Uploadpfad.

**Abgrenzung:** Dies ist ein Quellcode-Audit, kein vollständiger dynamischer Nachweis aller UI-/Share-/Viewer-/Gerätepfade. Diese bleiben in D04–D15 und den manuellen Geräte-Checks offen.
