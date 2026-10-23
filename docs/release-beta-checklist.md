# myDocAnizer-Mobile – praktische Beta-Freigabecheckliste

**Zweck:** Mit wenigen großen Funktionsprüfungen entscheiden, ob eine konkrete APK/AAB für den geschlossenen Google-Play-Betatest geeignet ist. Diese Checkliste ersetzt nicht die 123 Micro-AP-Prompts; sie fasst deren Freigabekriterien in **neun überprüfbare Gates** zusammen.

**Grundregel:** Ein Gate bekommt erst den Status **Bestanden**, wenn die Prüfung mit einem benannten Build/Commit durchgeführt und das Ergebnis notiert wurde. „Noch nicht getestet“ ist nicht „Bestanden“. Bei optionalen Funktionen ist eine sichere Alternative zulässig: Funktion in der Beta wirklich deaktivieren/verbergen und nicht als verfügbar bewerben.

## Ergebnisse eintragen

Pro Gate reicht ein kurzer Nachweis: **Commit/Build + bestanden/fehlgeschlagen + Gerät/Android-Version + Datum**. Bei Fehlern Reproduktionsschritte und betroffene AP-IDs ergänzen. Keine privaten Dokumente, OCR-Texte, Passwörter, Tokens oder Schlüssel in Logs/Testberichten ablegen.

| Gate | Konkrete Prüfung | Erwartetes Ergebnis | Ergebnis / Nachweis |
|---|---|---|---|
| **G1 – Build und Start** | Auf aktuellem `main`: `./gradlew assembleDebug testDebugUnitTest`; verfügbaren aktuellen CI-Lauf kontrollieren; APK installieren, starten, beenden und erneut starten. | Build und Tests bestehen auf demselben Commit; keine Startabstürze. Alte grüne CI-Läufe zählen nicht als Nachweis für einen neueren Commit. | ☐ Nicht getestet · ☐ Bestanden · ☐ Fehler |
| **G2 – Tresor, Entsperren und Sperre** | Neuinstallation: Tresor mit Passwort einrichten, korrekt/falsch entsperren, App neu starten, im Hintergrund warten und zurückkehren, sperren/entsperren. Biometrie und optionale Schlüsseldatei nur testen, wenn sie angeboten werden. Gesperrt auch Widget/Deep-Link-Aufrufe versuchen. | Falsches Passwort/Faktor entsperrt nie; nach Sperre oder Neustart erscheinen keine sensiblen Inhalte vor erfolgreicher Freigabe. Optional nicht geprüfte Entsperrwege sind deaktiviert. | ☐ Nicht getestet · ☐ Bestanden · ☐ Fehler |
| **G3 – Scannen/Importieren bis zur gespeicherten Datei** | Synthetische Seite scannen und eine Datei importieren. Zuschnitt bestätigen/abbrechen, OCR abwarten, Regelvorschlag wählen, speichern, App neu starten, Dokument öffnen und suchen. OCR-/Speicherfehler sowie „Erneut versuchen“ testen; bei Mehrseiten-PDF den kompletten Ablauf testen. | „Gespeichert“ erscheint erst nach erfolgreicher PDF-Erzeugung und Datenbankpersistenz. Fehler verlieren weder Scan noch vorhandene Dokumente. Mehrseitenverhalten ist entschieden und getestet oder die Funktion wird in der Beta deaktiviert. | ☐ Nicht getestet · ☐ Bestanden · ☐ Fehler |
| **G4 – Tatsächliche Verschlüsselung im Ruhezustand** | Mit synthetischem Testdokument Datenbank-, Dokument-, Cache-, Export- und Backupdateien nach dem Speichern prüfen. SQLCipher-/Dateiformat- und Negativtests mit falschem Schlüssel/Manipulation ausführen. | OCR/Text/Metadaten und Dokumentinhalt sind nicht unbeabsichtigt aus Klartextdateien oder unverschlüsselten DB-/FTS-/WAL-/Cachekopien lesbar. Keine stillschweigende Klartext-Fallback-Ablage. | ☐ Nicht getestet · ☐ Bestanden · ☐ Fehler |
| **G5 – Backup und Wiederherstellung** | Verschlüsseltes lokales Backup mit synthetischen Daten erstellen und in einer frischen Testinstallation wiederherstellen. Falschen Schlüssel, beschädigten/abgeschnittenen Container und Abbruch simulieren. Anschließend Dokumentanzahl, Inhalte, OCR/Suche und Einstellungen vergleichen. | Erfolgreicher Roundtrip ohne Datenverlust; beschädigtes Backup verändert den vorhandenen Datenbestand nicht. Google Drive/WebDAV bleiben ausgeblendet/deaktiviert, bis G8 für den jeweiligen Provider bestanden ist. | ☐ Nicht getestet · ☐ Bestanden · ☐ Fehler |
| **G6 – Datenbank, Suche und Datenintegrität** | Dokument anlegen, ändern und löschen; OCR-Volltextsuche ausführen; App neu starten; unterstützte Migration/Downgrade-Fehler testen. Finanzfunktionen nur dann aktiv testen, wenn sie im Beta-Umfang enthalten sind. | Suchindex und Hauptdaten stimmen überein; Migration und Fehler führen weder zu stiller Löschung noch zu beschädigten/vertauschten Werten. | ☐ Nicht getestet · ☐ Bestanden · ☐ Fehler |
| **G7 – LLM, Regelerstellung und sichere Vorschläge** | Wenn LLM aktiv ist: tatsächliches lokales Modell laden und echte Inferenz belegen; zusätzlich Offline-/Modellfehler-Fallback testen. Mit synthetischen OCR-Daten Beträge, Daten, Absender und Kategorien prüfen. Vorschlag ablehnen und korrigieren. | Regel-Fallback wird nicht als echte LLM-Inferenz ausgegeben. Keine erfundenen Messwerte oder OCR-Felder werden ungeprüft gespeichert; nutzerbestätigungspflichtige Aktionen lassen sich abbrechen. Ist das nicht erfüllt, LLM-Schreibaktionen/LLM-Funktion für den Beta-Build deaktivieren oder nur klar als experimentell mit manueller Prüfung bereitstellen. | ☐ Nicht getestet · ☐ Bestanden · ☐ Fehler |
| **G8 – Cloud und Geräteverbindungen (nur falls in Beta aktiv)** | Je aktivierter Funktion einzeln: Google Drive Autorisierung/Entzug/Backup/Restore; WebDAV mit gültigem und falschem/geändertem TLS-Zertifikat; App2App/PC2App/Browser2App Pairing, Transfer, Abbruch und falsche Gegenstelle. | Nur verschlüsselte Nutzdaten übertragen; ungültige Identität/Zertifikat, fehlende Konfiguration oder beschädigte Übertragung wird abgelehnt. Jede nicht bestandene Verbindung ist für die Beta tatsächlich deaktiviert/versteckt. | ☐ Nicht getestet · ☐ Bestanden · ☐ Fehler |
| **G9 – Datenschutz und Play-Console-Freigabe** | Logs und Diagnoseexport mit synthetischen Daten auf OCR-/Dokumentinhalte, Passwörter, Tokens und Schlüssel prüfen. Release-App-Bundle, Signierung, Version, Ziel-API, Datenschutzerklärung und Play-Console-Datensicherheitsangaben prüfen. | Keine sensiblen Inhalte in Logs/Telemetrie; tatsächliches Verhalten, Datenschutzerklärung und Datensicherheitsformular stimmen überein. Abhängige optionale Provider/Analytics sind entweder korrekt dokumentiert oder deaktiviert. | ☐ Nicht getestet · ☐ Bestanden · ☐ Fehler |

## Bekannte offene Freigabepunkte (Quell-/Dokumentationsstand 23.10.2026)

Diese Punkte sind **nicht als bestanden anzusehen**, solange neue Nachweise nichts anderes belegen:

- **G4 – Room-Datenbank:** `app/src/main/java/com/example/data/AppDatabase.kt` verwendet weiter den normalen Room-Builder, Schema-Version 9 und `exportSchema = false`; die SQLCipher-ADR weist ausdrücklich darauf hin, dass noch keine Runtime-Integration vorliegt. G4 kann deshalb aktuell nicht als bestanden gelten. Erst DB12/DB13 plus passende Tests können diesen Status ändern.
- **G5 – Backup/Restore:** Ein vollständiger erfolgreicher Roundtrip des neuen verschlüsselten Containers in einer frischen Installation ist in der Freigabecheckliste noch nicht nachgewiesen.
- **Scanner:** `docs/open_tasks.md` führt den physischen Kameratest, ViewModel-Integrationstests, die Entscheidung zum Mehrseiten-Regelvorschlag und die Prüfung der Bitmap-Lebensdauer weiterhin als offen. Der manuelle Scanablauf aus G3 ist deshalb noch tatsächlich durchzuführen.
- **Lokales LLM:** Quellcode für native llama.cpp/JNI ist vorhanden; echter GGUF-Lauf auf einem physischen Gerät sowie wirksame Grammar-/Cancel-/Timeout-Abnahme sind in den aktuellen Nachweisen nicht bestätigt. Modellantworten nicht als verifizierte Inferenz ausgeben, bevor das belegt ist.
- **Google Drive, WebDAV und P2P:** Bis die jeweilige Verbindung G8 besteht, muss der betreffende Einstieg in der Beta deaktiviert/versteckt sein. Vorhandener Code oder ein erfolgreiches Build ist kein Ende-zu-Ende-Nachweis.

## AP-Zuordnung zu den Gates

- **G1:** alle Änderungen des zu veröffentlichenden Commits; aktuelle Build-/CI-Nachweise.
- **G2:** V01–V06B, V13–V15, S02–S07, S10.
- **G3:** D05–D07, D09, D14 sowie offene Scan-Punkte in `docs/open_tasks.md`.
- **G4:** D02–D07, D13–D15, DB12–DB13, S05, R16 und zugehörige Verschlüsselungs-/Negativtests.
- **G5:** D10–D12, D13, D08; C04–C09 nur, wenn der jeweilige Cloud-Provider aktiviert wird.
- **G6:** DB01–DB06, DB09–DB11 und alle Migrationen, die tatsächlich unterstützt werden.
- **G7:** L01–L17; unerledigte LLM-Funktionen nicht über die Oberfläche als produktiv anbieten.
- **G8:** P01–P13 und C01–C09, jeweils nur für aktivierte Verbindungen.
- **G9:** R16, R17 sowie tatsächliche Play-Console- und Datenschutzangaben.

## Was vor dem ersten geschlossenen Beta-Test wirklich nötig ist

1. **G1–G7 und G9 müssen bestanden sein**, sofern die entsprechenden Funktionen in diesem Beta-Build aktiv sind. Ein unsicheres optionales Feature darf nicht allein wegen einer To-do-Liste sichtbar bleiben.
2. **G8 ist ein bedingtes Gate:** Google Drive, WebDAV und P2P müssen entweder für den Test tatsächlich deaktiviert sein oder ihre jeweilige Prüfung bestanden haben.
3. Vor dem Betatest mit echten Daten muss insbesondere **G4 (Verschlüsselung der Datenbank und Dokumente)** und **G5 (sicherer Backup-/Restore-Roundtrip)** belegt sein. Ein erfolgreicher Build beweist diese Eigenschaften nicht.
4. Nach dem Test alle als „Fehler“ markierten Gates korrigieren und auf dem neuen Commit erneut prüfen. Nicht den Status des alten Builds wiederverwenden.

## Aktuelle Google-Play-Hinweise

- Für **neue Apps und App-Updates** gilt seit **31. August 2026** grundsätzlich `targetSdk` **36 (Android 16) oder höher**. Im aktuellen Repository steht `targetSdk = 36`; vor Upload trotzdem das tatsächlich gebaute App-Bundle kontrollieren. [Offizielle Google-Play-Anforderungen zum Ziel-API-Level](https://support.google.com/googleplay/android-developer/answer/11926878?hl=de)
- Für Apps, die in einem **geschlossenen oder offenen Test-Track** veröffentlicht werden, ist der Abschnitt „Datensicherheit“ auszufüllen; auch Apps, die keine Nutzerdaten erheben, benötigen diesen Abschnitt und eine Datenschutzerklärung. [Offizielle Play-Console-Hilfe zur Datensicherheit](https://support.google.com/googleplay/android-developer/answer/10787469?hl=de)
- Falls das Entwicklerkonto ein **persönliches Konto ist, das nach dem 13. November 2023 erstellt wurde**, sind für den Antrag auf Produktionszugriff mindestens **12 Testpersonen über 14 aufeinanderfolgende Tage** in einem geschlossenen Test erforderlich. Das ist eine Voraussetzung für den späteren Produktionszugriff, nicht die Voraussetzung, um den geschlossenen Test überhaupt zu starten. [Offizielle Anforderungen an Tests für neue private Entwicklerkonten](https://support.google.com/googleplay/android-developer/answer/14151465?hl=de)

**Wichtig:** Diese Datei ist eine Abnahmevorlage, keine Behauptung, dass die Gates bereits bestanden sind. Die Testresultate werden erst nach tatsächlicher Prüfung eingetragen.
