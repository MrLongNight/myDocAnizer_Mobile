# D02 – ADR: Vollständige Room-Datenbankverschlüsselung mit SQLCipher for Android

**Datum der Prüfung:** 2026-10-21  
**Status:** Technischer Vorschlag auf Basis der aktuellen offiziellen Projektunterlagen; noch keine Runtime-Integration.  
**Produktentscheidung:** Die gesamte lokale Room-Datenbank wird verschlüsselt. Feldverschlüsselung oder ein zusätzlicher unverschlüsselter Suchindex sind kein Ersatz.

## 1. Ist-Zustand und Anforderungen

- Projekt: Room `2.7.0`, `AppDatabase` Schema-Version `9`, minSdk `24`, targetSdk `36`.
- `AppDatabase.getDatabase()` verwendet heute den normalen Room-Builder ohne Key-/SQLCipher-Factory; die Datei `mydocanizer_database` ist somit nicht vollständig verschlüsselt.
- Die Datenbank enthält Dokumentmetadaten und OCR; FTS4 speichert Titel, Absender, OCR-Text und Tags. Weitere Tabellen enthalten Finanz- und benutzerdefinierte Daten.
- Die App muss nach Lock ohne verfügbaren DB-Schlüssel keine Datenbankabfragen ausführen. Es darf keinen automatischen oder stillen Fallback auf die bisherige Klartextdatenbank geben.
- Bestehende Daten und Schema-Versionen müssen überprüfbar migriert und bei Fehlern wiederherstellbar bleiben.

## 2. Entscheidungsvorschlag

Verwende **`net.zetetic:sqlcipher-android:4.19.1@aar`** mit der vorhandenen Room-2-Integration über `SupportOpenHelperFactory` und `androidx.sqlite:sqlite:2.7.0`. Der offizielle SQLCipher-for-Android-Release `v4.19.1` wurde am 2026-09-29 veröffentlicht. Das offizielle README beschreibt die Integration für Room 2 und Room 3; für das vorhandene Projekt bleibt Room 2.7.0 unverändert.

`sqlcipher-android` ist der offizielle Nachfolger des alten `android-database-sqlcipher`-Projekts. Das alte Projekt ist als deprecated ausgewiesen und erhält keine Community-Updates mehr. Der neue Client unterstützt laut README Android API 23 und neuer sowie `armeabi-v7a`, `x86`, `x86_64` und `arm64-v8a`; das deckt minSdk 24 und die bekannten üblichen Android-ABIs ab. Die Release-Hinweise zu v4.19.1 nennen außerdem einen Common-Page-Size-Linker-Flag. Trotzdem ist die tatsächliche APK-/AAB-Verifikation für 16-KB-Seiten und jede unterstützte ABI eine Abnahmebedingung, kein Ersatz für die Prüfung.

**Native Initialisierung:** `System.loadLibrary("sqlcipher")` muss abgeschlossen sein, bevor Room die verschlüsselte Datenbank öffnet. Die sichere Factory ist bei jeder DB-Erstellung derselben geschützten Schlüsselquelle zuzuordnen. Eine zweite Factory oder ein zweiter unverschlüsselter Singleton ist unzulässig.

## 3. Schlüsselbereitstellung (Abhängigkeit D03)

1. Erzeuge einen zufälligen, 256-Bit-Datenbankschlüssel. Verwende niemals das Nutzerpasswort, `syncPasswordKey` oder einen statischen String direkt als SQLCipher-Key.
2. Schütze den DB-Schlüssel durch den zentralen `CryptoService/KeyProvider` und eine vom Vault-DEK abgeleitete, zweckgebundene Schlüsselhierarchie. Der Zweck muss von Dokumentdateien, Einstellungen und Backup-Containern getrennt sein.
3. Der Schlüssel darf nicht als Klartext in SharedPreferences, DataStore, Room, Logs oder Audit-Ereignissen persistiert werden. Temporäre Byte-Arrays sind nach Verwendung zu überschreiben, soweit APIs das erlauben.
4. Ein nur an den lokalen Android-Keystore gebundener DB-Schlüssel reicht für portable Wiederherstellung nicht aus. Backup/Recovery muss die Wiederherstellbarkeit des zufälligen DB-Schlüssels über den geschützten Vault-/Recovery-Pfad ausdrücklich behandeln.
5. Ohne verfügbaren oder validen DB-Schlüssel: geschlossen fehlschlagen, verständliche Recovery-Fehleroberfläche anzeigen und niemals die Datei als leere/unkomprimierte SQLite-Datenbank neu öffnen.

Die konkrete KeyProvider-Implementierung gehört in D03 und muss vor DB12 freigegeben werden; diese ADR speichert noch keinen Schlüssel und implementiert noch keine Datenbanköffnung.

## 4. Room-, FTS- und WAL/SHM-Verhalten

- Room 2 verwendet die vom Projekt bereitgestellte `SupportSQLiteOpenHelper.Factory` über `openHelperFactory(SupportOpenHelperFactory(...))`. Nicht gleichzeitig auf Room-3-`setDriver` umstellen.
- Haupttabellen, Indizes, Trigger, `documents_fts` und sämtliche Room-Migrationen müssen innerhalb derselben SQLCipher-Datenbank liegen. Es darf kein unverschlüsselter FTS-/OCR-Nebenindex entstehen.
- WAL/SHM sind zusammen mit der DB-Öffnungs-, Checkpoint- und Schließungssequenz zu testen. Bei Migration: Schreibzugriffe stoppen, `RoomDatabase.close()`, Checkpoint/sauberes Schließen durchführen und nicht blind eine DB ohne zugehörige Sidecars kopieren. Nach erfolgreicher Verschlüsselung dürfen keine alten plaintext-WAL-Dateien verbleiben.
- `fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)` in `AppDatabase` ist mit der geforderten Datenerhaltung nicht vereinbar. DB03 muss diesen Löschpfad entfernen; ein nicht unterstützter Downgrade muss kontrolliert scheitern und Recovery erklären, nicht eine leere Datenbank öffnen.
- `exportSchema = false` ist aktuell gesetzt. DB01/DB02 müssen zuerst eine belegte Schema-v9-Baseline bzw. reproduzierbare Room-Schemaexporte sichern, bevor Migrationen umgestellt werden.

## 5. Migration von SQLite-Klartext zur verschlüsselten Datenbank

Die Migration darf nicht beim bloßen Wechsel des Builders aktiviert werden. Vorgeschlagene sichere Reihenfolge:

1. Vorab vollständige Prüfungen und einen app-internen, geschützten Rollback-Snapshot bereitstellen; Sync, Dokumentmigration und DB-Schreibzugriffe anhalten.
2. Room schließen, offene Transaktionen beenden und WAL sauber checkpointen. Original-DB und nötige Sidecars erst entfernen, wenn das neue verschlüsselte DB-File erfolgreich verifiziert wurde.
3. Eine temporäre SQLCipher-Datenbank am app-internen Zielpfad über den offiziellen Export-/Migrationmechanismus erzeugen. Schlüssel und temporäre Kopien nicht loggen oder exportieren; Prozessabbruch muss den Originalbestand unverändert lassen.
4. Die neue Datei mit einem frischen Room-SQLCipher-Open verifizieren: Schlüsselkorrektheit, SQLCipher-/SQLite-Integritätsprüfung soweit unterstützt, Room-Version/Schema, Rowcounts aller Tabellen, Indizes/Trigger, FTS-Suche einschließlich OCR-Testdatensatz und synthetische Custom-Field-/Finanzdaten.
5. Erst nach erfolgreicher Prüfung den Zielpfad atomar umschalten. Den Original-Snapshot nicht vorzeitig löschen; bei jedem Fehler die alte DB vollständig behalten und niemals automatisch eine Klartext-Neuerstellung anbieten.
6. Nach Umschaltung testen: App-Prozessneustart, Lock/Unlock, falscher Schlüssel, fehlender/invalidierter Schlüssel, Backup/Restore und erneute Migration. Die Migration muss idempotent sein.

Historische Schema-Versionen dürfen nicht erfunden werden. DB01/DB02 sichern die tatsächliche Version 9, DB03 entfernt den destruktiven Downgrade, DB04 prüft die tatsächlich unterstützten Migrationen. Erst danach folgt DB12 mit SQLCipher-Runtime und echten Integrations-/Migrationstests.

## 6. Abnahmekriterien

- [ ] Dependency wird ausschließlich als geprüfter, konkreter Versionsstand eingebunden; Lizenz-/Attributionshinweise werden anhand der ausgelieferten Artefakte geprüft.
- [ ] API 24+ und die unterstützten ABIs bauen und öffnen dieselbe verschlüsselte Room-Datenbank.
- [ ] Die auf dem Datenträger liegende DB beginnt nicht mit SQLite-Klartextsignatur und enthält keine auffindbaren synthetischen OCR-/Dokumentsentinels im DB-File oder alten WAL.
- [ ] Falscher/fehlender Schlüssel öffnet niemals eine unverschlüsselte Datenbank und verändert keinen existierenden Datenbestand.
- [ ] FTS, Inserts/Updates/Deletes, Migration v9, WAL/SHM, Lock/Unlock, Prozessneustart und Restore sind instrumentiert getestet.
- [ ] Fehler bei jeder Migrationsstufe halten die Originaldaten zurück und erlauben einen klaren Rollback.
- [ ] 16-KB-Seiten-Kompatibilität für die gebaute Native-Library wird in der tatsächlichen APK/AAB geprüft; README-/Release-Aussage allein gilt nicht als Geräteabnahme.

## 7. Referenzen (abgerufen/geprüft am 2026-10-21)

- Offizieller Release v4.19.1 (29.09.2026): https://github.com/sqlcipher/sqlcipher-android/releases/tag/v4.19.1
- Dokumentation und Room-2-/Room-3-Integration, API-Level, ABIs: https://github.com/sqlcipher/sqlcipher-android/blob/v4.19.1/README.md
- Legacy-Bibliothek als deprecated und Hinweise auf 16-KB-Kompatibilität: https://github.com/sqlcipher/android-database-sqlcipher/blob/master/README.md
- Herstellerhinweis zu 16-KB-Seitengrößen: https://www.zetetic.net/blog/2025/06/26/sqlcipher-for-android-16kb-page-size-support/

**Geltungsgrenze:** Dies ist eine technische ADR für D02 und noch keine Genehmigung zur Aktivierung der Runtime-Migration. D03, DB01–DB04, die konkreten Rollback-Tests und die Freigabe der Schlüssel-/Recovery-Architektur bleiben vorgeschaltet.
