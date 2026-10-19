# D14 – PDF-Textlayer-Audit

**Stand:** 2026-10-19  
**Status:** Code-Audit durchgeführt; automatisierter Instrumentationstest hinzugefügt; Testlauf offen.  
**Scope:** Nur Verhalten feststellen. Keine PDF-Textlayer-Implementierung vorgenommen.

## Befund aus dem Quellcode

- `DocumentStorageService.generatePdf(...)` erhält `ocrText` als Parameter, verwendet diesen Wert beim Erstellen der PDF-Seiten aber nicht.
- Scan-Seiten werden als Bitmap über `PdfDocument` auf die Seite gezeichnet. Zusätzlich werden sichtbare Kopf-/Fußzeilen (Kategorie, Dokumenttyp und Seitenzahl) mit `Canvas.drawText` geschrieben.
- Damit enthält die erzeugte Scan-PDF maschinenlesbaren Text für die sichtbaren Kopf-/Fußzeilen, aber der OCR-Inhalt wird nicht als Textlayer in die PDF eingebettet.
- Der OCR-Text wird separat in `DocumentEntity.ocrText` gespeichert. `DocumentFtsEntity` definiert eine Room-FTS4-Tabelle; `DocumentDao` enthält sowohl eine FTS-Abfrage als auch einen LIKE-Suchpfad. Deshalb darf die Dokumentation nicht behaupten, dass die Suche ausschließlich über FTS läuft.

## Automatisierter Nachweis

`app/src/androidTest/java/com/example/service/PdfTextLayerInstrumentedTest.kt` erstellt eine echte Scan-PDF mit einem eindeutigen OCR-Sentinel und extrahiert den Text mit PDFBox-Android. Der Test erwartet:
1. Die PDF ist vorhanden und nicht leer.
2. Der sichtbare PDF-Header `myDocAnizer` ist durch Text-Extraktion auffindbar.
3. Der übergebene OCR-Sentinel ist **nicht** im extrahierten PDF-Text enthalten.

PDFBox-Android wird nur als `androidTestImplementation` eingebunden; die transitiven Bouncy-Castle-Artefakte werden ausgeschlossen, um eine Kollision mit der bereits verwendeten Bouncy-Castle-Version zu vermeiden.

## Produktentscheidung / offene Grenze

Der aktuelle Code dokumentiert die PDF-Erzeugung an mehreren Stellen als „durchsuchbar“, während der OCR-Inhalt nicht in die Scan-PDF geschrieben wird. Bevor ein Textlayer implementiert wird, muss geklärt werden, ob „durchsuchbar“ die PDF-Datei selbst oder die Suche innerhalb der App meint. Entsprechend D14 wird hier keine Textlayer-Implementierung nebenbei vorgenommen.

Wenn ein PDF-eigener OCR-Textlayer gefordert ist, ist ein separater Minimalplan nötig (Positionierung/Mapping von OCR-Wortboxen auf PDF-Koordinaten, unsichtbarer Text, Rotation/Skalierung, Mehrseiten-Zuordnung, Tests mit verschiedenen Scanauflösungen). Die aktuelle App-Suche ist davon unabhängig, aber der DAO enthält einen LIKE-Pfad zusätzlich zu FTS4; die Bereinigung dieses Pfads gehört in den separaten DB06-Arbeitsschritt.

## Verifikation

- [x] Quellcodepfad für PDF-Erzeugung geprüft.
- [x] Room-/FTS-Suchpfad anhand der Entities/DAO geprüft.
- [x] PDF-Text-Extractor-Test hinzugefügt.
- [ ] `./gradlew assembleDebug testDebugUnitTest` ausgeführt.
- [ ] Instrumentationstest auf Android-Gerät/Emulator ausgeführt.
