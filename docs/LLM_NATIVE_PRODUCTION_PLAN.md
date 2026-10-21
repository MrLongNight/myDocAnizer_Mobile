# Produktionsreife lokale GGUF-LLM-Inferenz für Android

**Repository:** `MrLongNight/myDocAnizer_Mobile`  
**Zielbranch:** `main`  
**Stand:** 21.10.2026  
**Status:** Code-basierter Maßnahmenplan; kein Implementierungs- oder Laufzeitnachweis

## High-Level-Zusammenfassung

Die App besitzt bereits eine umfangreiche fachliche KI-Schicht: `LlmService`, `LlamaCppInferenceEngine`, Prompt-/JSON-Schemas, Fachabteilungs-Pipeline, Hardware-Erkennung, Modellkatalog, GGUF-Import/Download, Tool-Routing und Qualitätsprüfungen. Das Vorhandensein dieser Klassen beweist aber noch nicht, dass eine gültige native llama.cpp-Laufzeit samt JNI-Implementierung für alle ausgelieferten Android-ABIs im APK steckt und jedes Produktivfeature tatsächlich Modellinferenz verwendet.

Ein konkreter Risikopunkt ist `LlamaCppInferenceEngine.kt`: Die Initialisierung versucht `System.loadLibrary("llama_android")`; bei einem Fehler wird ein Fallback aktiviert. Der Logtext bezeichnet diesen Fallback als „On-Device Kotlin/JNI Fallback“, was nicht belegt, dass ein GGUF-Modell wirklich ausgeführt wird. Die vorhandenen Dokumente beschreiben teilweise einen vollständig produktiven Zustand, während die native Bibliothek, JNI-Symbole, Paketierung und Geräteausführung separat nachgewiesen werden müssen. Auch die aktuell sichtbare `app/build.gradle.kts` zeigt keine direkte llama.cpp-/CMake-Konfiguration; native Integration könnte an anderer Stelle liegen und muss inventarisiert werden.

**Zielzustand:** Jede als KI ausgewiesene Funktion läuft über eine echte, überprüfbare lokale GGUF-Inferenz oder wird klar als deterministische Regel-/OCR-Funktion gekennzeichnet. Fehlende native Bibliotheken, ungültige Modelle oder Inferenzfehler dürfen niemals stillschweigend als erfolgreiche KI-Antwort maskiert werden. Dokumentinhalte bleiben lokal; Netzwerkzugriff ist nur für explizite Modell-Downloads/Updates zulässig.

## Befundlage aus dem Repository

- `app/src/main/java/com/example/service/LlamaCppInferenceEngine.kt`: JNI-Engine, System-Prompts und GBNF-Schemas vorhanden; `System.loadLibrary("llama_android")` wird versucht und bei Fehlern auf einen Fallback umgeschaltet. Native Implementierungen der deklarierten `external`-Methoden und deren ABI-Abdeckung müssen nachgewiesen werden.
- `app/src/main/java/com/example/service/LlmService.kt`: Modellkatalog, Modellimport/-suche und Download-Logik vorhanden. Zu prüfen sind Modellpfad-Übergabe, echte Engine-Aufrufe, Abbruch-/Fehlerbehandlung, Modellwechsel und Verwechslungen zwischen „Datei vorhanden“ und „Modell ladbar“.
- `app/src/main/java/com/example/service/llm/LlmHardwareDetector.kt`: Hardware-Erkennung vorhanden. Ein synthetischer Benchmark/Heuristikwert darf nicht als echte Inferenzleistung oder Modellkompatibilitätsnachweis gelten.
- `app/src/main/java/com/example/service/LlmToolRouter.kt`, `LlmToolDefinitions.kt`, `LlmToolPromptBuilder.kt` sowie `app/src/main/java/com/example/service/llm/LlmToolExecutor.kt`: Tool-/Function-Calling-Schicht vorhanden. Lokale Modelle unterstützen nicht automatisch natives Tool-Calling; kontrollierte JSON-/Tool-Call-Schleife muss explizit implementiert und getestet sein.
- `app/build.gradle.kts`: Android/Kotlin-Konfiguration vorhanden; in der geprüften Datei ist keine direkte llama.cpp-/CMake-Integration sichtbar. Abhängigkeiten, NDK, CMake, `jniLibs`, ABI-Filter und tatsächlich erzeugte APK/AAB sind vollständig zu inventarisieren.
- `docs/LLM_INTEGRATION_DOCUMENTATION.md` und `docs/LLM_FUNKTIONEN_UND_PROZESSE_DOKUMENTATION.md`: fachliches Sollbild und Komponenten beschrieben. Aussagen wie „vollständig implementiert/verifiziert“, Modelllatenzen und Hardwarebeschleunigung erst nach reproduzierbaren Messungen als bestätigt bezeichnen.

## Notwendige Code-Maßnahmen

### P0 – Ist-Zustand und echte Inferenz beweisen

1. **Native Build-Artefakte inventarisieren**
   - Prüfen: `app/src/main/cpp/`, `CMakeLists.txt`, `jniLibs/`, Gradle-Versionenkatalog, NDK-/CMake-Version, Build-Tasks, CI-Workflows und alle `.so`-Dateien.
   - Für jede unterstützte ABI (`arm64-v8a`, gegebenenfalls `armeabi-v7a`, `x86_64` für Emulatoren) dokumentieren, ob `libllama_android.so` samt transitiven Abhängigkeiten tatsächlich im APK/AAB liegt.
   - JNI-Namen/Signaturen zwischen Kotlin-`external`-Methoden und C/C++-Exports abgleichen; Symbolauflösung und Ladefehler auf realen Geräten prüfen.
   - Prüfen, ob llama.cpp überhaupt gebaut und in die App gelinkt wird oder ob derzeit nur der Kotlin-Vertrag existiert.
2. **Alle Fallbacks klassifizieren**
   - Alle Zweige in `LlamaCppInferenceEngine.kt`, `LlmService.kt` und Aufrufern nach Heuristiken, Demo-/Platzhalterantworten, hartcodierten Ergebnissen und stillen Fehler-Fallbacks durchsuchen.
   - Fallbacks in getrennte Modi aufteilen: deterministische Regeln, echte Modellinferenz und expliziter Fehlerzustand. Kein heuristisches Ergebnis als „LLM-Ergebnis“ ausgeben.
   - Diagnostik muss Engine-Modus, Modell-ID, Lade-/Inferenzstatus und Fehlerursache nennen, ohne OCR-Inhalte oder vertrauliche Prompts zu protokollieren.
3. **End-to-End-Test mit echter GGUF-Datei**
   - Eine lizenzrechtlich freigegebene, kleine GGUF-Testdatei auf einem unterstützten physischen Android-Gerät laden.
   - Nachweisen: Datei geöffnet, Modellkontext erzeugt, Tokens generiert, Ergebnis aus dem echten Modell stammt; zusätzlich Negativtest mit ungültiger Datei und fehlender nativer Bibliothek.
   - Bis dahin native Produktionsfähigkeit als **nicht verifiziert** führen.

### P1 – Native llama.cpp-Laufzeit robust integrieren

4. **Versionierter nativer Build**
   - Eine konkrete llama.cpp-Version/Commit pinnen und Updates kontrolliert durchführen.
   - Android NDK und CMake über Gradle reproduzierbar konfigurieren; C++-Build, Linker-Flags, ABI-Liste und Bibliotheksnamen festlegen.
   - Nur tatsächlich unterstützte Backend-Optionen aktivieren. CPU/ARM NEON zuerst zuverlässig ausliefern; Vulkan/GPU separat und geräteabhängig nachweisen, nicht pauschal als GPU/NPU-Beschleunigung behaupten.
   - Lizenz- und NOTICE-Pflichten von llama.cpp, abhängigen Bibliotheken und jedem Modell dokumentieren.
5. **JNI-/C-API-Lebenszyklus**
   - Native API für Modell laden/entladen, Kontext erzeugen, Prompt ausführen, Token-Streaming, Abbruch und Fehlercodes vollständig implementieren.
   - Native Handles gegen Doppelfreigabe, parallele Nutzung, Activity-Neustart und Prozessende absichern. Speicherfreigabe auch bei Cancellation/Exception garantieren.
   - JNI-Methoden mit passenden Typen/Signaturen abgleichen; Exceptions kontrolliert an Kotlin zurückgeben. Keine C++-Exception über JNI-Grenzen laufen lassen.
   - Inferenz auf `Dispatchers.IO`/dediziertem Executor ausführen, UI-Thread blockieren vermeiden, Parallelität begrenzen und Cancel-Signale bis in den Decode-Loop weiterreichen.
6. **Modell- und Kontextmanagement**
   - Modellzustand explizit modellieren: `NotInstalled`, `Downloading`, `Validating`, `Ready`, `Loading`, `Loaded`, `Running`, `Failed`.
   - Nur ein validiertes, vollständig heruntergeladenes Modell freigeben. GGUF-Magic-Bytes sind lediglich ein erster Dateicheck, keine Integritäts- oder Kompatibilitätsgarantie.
   - Download atomar in temporäre Datei, Dateilänge/Hash wenn verfügbar prüfen, dann umbenennen. Resume, Abbruch, wenig Speicher, Netzwerkwechsel und beschädigte Dateien behandeln.
   - Import über Storage Access Framework sicher kopieren; Speicherplatz, Lesbarkeit, Berechtigungen und Dateigröße prüfen.
   - Kontextlänge, Batch-Größe, Threadzahl, GPU-Layer und Speicherbudget pro Gerät begrenzen. OOM-/Thermal-/Akku-Szenarien behandeln; Modell nach Nutzung kontrolliert entladen.
7. **ABI-/APK-Qualität**
   - `packaging` und ABI-Filter bewusst konfigurieren; 16-KB-Page-Size-Kompatibilität und native Abhängigkeiten gemäß aktueller Android-Anforderungen prüfen.
   - CI muss für jede ausgelieferte ABI APK/AAB-Inhalt, `System.loadLibrary`, JNI-Start und einen minimalen Inferenz-Smoke-Test validieren.

### P2 – Modellkatalog und echte Modellnutzung

8. **Katalogdaten validieren**
   - Für jedes Modell URL, tatsächlichen Dateinamen, GGUF-Variante, Lizenz, SHA-256/Release-Revision, Architektur/Tokenizer, benötigten RAM, Kontextlimit und unterstützte llama.cpp-Features prüfen.
   - Katalogmetadaten nicht als Tatsachen behandeln, bevor URL und Modellartefakt verifiziert sind. Fehlende oder veraltete URLs erkennbar als Fehler anzeigen.
   - Modelleinträge und physische Dateien über stabile IDs/Manifest verknüpfen; nicht nur per Dateinamen-Teilstring zuordnen.
9. **Einheitlicher Inferenzvertrag**
   - Eine testbare Schnittstelle (z. B. `LocalLlmEngine`) definieren mit `loadModel`, `generate`, `streamGenerate`, `cancel`, `unloadModel` und typisierten Fehlern.
   - `LlmService` soll orchestrieren; JNI-/llama.cpp-Details gehören hinter die Engine-Schnittstelle.
   - Konfiguration (Prompt, Sampling, Max-Tokens, Kontext, Timeout) zentral und pro Task steuerbar halten. Modellwechsel muss aktive Requests sicher beenden und Ressourcen freigeben.
10. **Structured Output robust parsen**
    - GBNF nur verwenden, wenn der native Build die passende Grammar-API tatsächlich enthält; ansonsten JSON-Ausgabe nicht als „erzwungen“ beschreiben.
    - JSON strikt gegen Schema validieren, unbekannte Felder/Typen behandeln, abgeschnittene Ausgaben erkennen und höchstens begrenzt erneut anfragen.
    - Null-/fehlende Werte, Datums-/Betragsnormalisierung, Confidence und OCR-Textanker getrennt prüfen. LLM-`reasoning` nicht ungefiltert in UI/Logs speichern.
11. **Echte Tool-Aufrufe statt Prompt-Versprechen**
    - `LlmToolRouter` und `LlmToolExecutor` an einen expliziten Ablauf koppeln: Modellantwort parsen → Toolname/Argumente validieren → Berechtigung prüfen → freigegebene lokale Funktion ausführen → Ergebnis zurück ins Modell → Abbruch-/Iterationslimit.
    - Niemals beliebige Modelltexte als Tool-Aufruf ausführen. Schreibende Aktionen (Regeln erstellen, Metadaten ändern, Dokument verschieben/löschen) benötigen Whitelist, Validierung und erforderlichenfalls Nutzerbestätigung.
    - Tool-Calling pro Modell/Prompt testen; bei kleinen Modellen lieber robuste JSON-Action-Schemas als nicht unterstützte native Tool-Calling-Fähigkeiten voraussetzen.

### P3 – Fachfunktionen vollständig auf echte Inferenz umstellen

12. **Aufruferinventar und Feature-Matrix**
    - Alle Aufrufer in `DocAnizerViewModel`, `DmsManager`, Import-/Scan-/Inbox-Flows, Dokumentdetail, Chat/Agent, Regelvorschlägen, Deadline-Erkennung, Custom-Fields und Dashboard erfassen.
    - Für jede Funktion dokumentieren: OCR-/Datenquelle, echte LLM-Anfrage ja/nein, JSON-Schema, Validierung, Fallback, Nutzerfreigabe und Test.
    - Regeln (`DocRuleRepository`) dürfen weiterhin deterministisch und bevorzugt ausgeführt werden. UI muss jedoch unterscheiden, ob Ergebnis aus Regel, OCR, LLM oder manuellem Nutzerentscheid stammt.
13. **Pipeline-Ausführung**
    - Triage, Fachabteilungs-Extraktion, Ordner-Routing und Quality Gatekeeper als getrennte, messbare Schritte umsetzen.
    - Jede Stufe muss einen echten Inferenzrequest auslösen, wenn sie als LLM-Schritt definiert ist; deterministische Stufen klar als solche markieren.
    - OCR-Textlänge auf Kontextlimit trimmen/chunken, Seiten-/Abschnittsbezug erhalten und Dokumente nicht blind vollständig in einen kleinen Kontext einfügen.
    - Quality Gatekeeper muss Modellvorschläge gegen OCR-Anker und Domänenregeln prüfen; kritische Finanz-/Rechtsdaten vor Übernahme bestätigen lassen.
14. **Lokaler Dokument-Chat / RAG**
    - Chat-Historie und ausgewählte Dokumente explizit in den Prompt-Kontext einbringen; nur aktuell autorisierte Dokumente berücksichtigen.
    - Für große Archive Room/FTS-Suche als Retrieval nutzen, relevante Auszüge begrenzen und Quellen/Dokumentseiten im Antwortmodell zurückgeben.
    - Modellantworten von Suchresultaten unterscheiden; bei fehlendem Kontext „nicht gefunden“ statt erfundener Aussagen zurückgeben.

### P4 – Datenschutz, UX, Stabilität und Messbarkeit

15. **Datenschutz**
    - OCR-Texte, Prompts, vollständige Modellantworten und Dokumentmetadaten aus Crashlytics/Analytics/Logs ausschließen. Nur minimale technische Fehlercodes und aggregierte Leistungsdaten erfassen.
    - Keine Cloud-Inferenz als stillen Fallback. Modell-Download ist vom Inferenzdatenfluss zu trennen.
    - Temporäre Prompt-/Tokenpuffer nach Möglichkeit freigeben; sensible Dateien im app-privaten Speicher ablegen und Backup-Regeln prüfen.
16. **UI-Status und Fehlermeldungen**
    - Modell nicht installiert, inkompatibel, lädt, wird geladen, generiert, abgebrochen und fehlgeschlagen getrennt darstellen.
    - Keine erfolgreiche Klassifikation suggerieren, wenn die native Engine nicht geladen ist. Alternativ darf ein klar gekennzeichneter Regelmodus weiterarbeiten.
    - Fortschritt und Abbruch beim Download sowie beim Tokenstream anzeigen; UI bei Rotation/Hintergrundwechsel stabil halten.
17. **Leistungs- und Hardwaretests**
    - Hardwaredetektor darf Empfehlungen liefern, aber kein synthetischer CPU-Loop ersetzt echten Modellbenchmark.
    - Auf realen Low-/Mid-/High-End-Geräten RAM-Spitze, Kaltstart-Ladezeit, Tokens/s, Latenz, Energieverbrauch, thermisches Verhalten und Abstürze messen.
    - Threadzahl und Kontext dynamisch an freien RAM anpassen; konservative Defaults und explizite Nutzeroptionen anbieten.

## Test- und Abnahmematrix

| Bereich | Mindestnachweis |
|---|---|
| Native Bibliothek | Für jede unterstützte ABI im APK/AAB vorhanden; `System.loadLibrary` erfolgreich |
| JNI | Alle Methoden korrekt gebunden; Laden, Generieren, Abbrechen, Entladen ohne Leak/Crash |
| GGUF | Gültiges Modell lädt; ungültige/abgeschnittene Datei wird abgelehnt |
| Offline | Nach Installation/Modellbereitstellung funktioniert Inferenz im Flugmodus |
| Download | Abbruch, Resume, Hash-/Größenfehler, Speicherknappheit, Netzwerkverlust |
| JSON | Valide Ausgabe, abgeschnittene Ausgabe, ungültige Typen und Retry-Limits |
| Tool-Routing | Nur Whitelist-Tools; ungültige Argumente und nicht autorisierte Aktionen abgelehnt |
| Feature-Flows | Scan, Import, Inbox, Chat, Extraktion und Regeln jeweils mit echter Modellinferenz oder korrekt gekennzeichnetem Regelmodus |
| Robustheit | Rotation, Hintergrund, Cancellation, parallele Requests, OOM und Modellwechsel |
| Datenschutz | Keine Dokumentinhalte in Logs/Analytics; kein Cloud-Inferenz-Fallback |
| CI/Release | Native Artefakte und Smoke-Test in Release-ähnlichem Build; ABI-/16-KB-Prüfung |

## Empfohlene Umsetzungsreihenfolge

1. **Audit:** native Build-/APK-/ABI-/JNI-Inventar erstellen und alle Fallback-Zweige klassifizieren.
2. **Native Minimalstrecke:** versioniertes llama.cpp mit CPU-Backend bauen, eine kleine lizenzkonforme GGUF-Datei laden und einen reproduzierbaren Geräte-Smoke-Test bestehen.
3. **Engine-Vertrag:** JNI-Lebenszyklus, Abbruch, Speicherlimits, Fehlerzustände und strukturierte Ergebnisse absichern.
4. **Modellmanagement:** Katalog, Integritätsprüfung, Download/Import, Kompatibilität und Modellwechsel produktionsreif machen.
5. **Fachpipeline:** alle tatsächlichen KI-Aufrufer migrieren und jeden Funktionspfad mit echter Inferenz testen.
6. **Tooling/RAG:** begrenzte Tool-Schleife, Dokument-Retrieval, Sicherheitsprüfung und Nutzerfreigabe vervollständigen.
7. **Release-Gate:** CI, Gerätebenchmarks, Datenschutzprüfung und nachvollziehbare Abnahmematrix verpflichtend machen.

## Verbindliche Definition von „produktiv“

Eine Funktion gilt erst als produktiv mit lokalem LLM, wenn ein reproduzierbarer Test belegt, dass ein echtes GGUF-Modell über die im Release enthaltene native llama.cpp-Bibliothek auf einem unterstützten Android-Gerät ausgeführt wird und das Ergebnis tatsächlich aus dieser Inferenz stammt. Quellcode-Klassen, Modellkatalogeinträge, GGUF-Magic-Bytes, erfolgreiche UI-Flows oder Heuristiktests allein sind kein ausreichender Nachweis.

**Wichtig:** Dieses Dokument ist ein Maßnahmenkonzept auf Basis der untersuchten Repository-Dateien. Es behauptet nicht, dass die native Bibliothek fehlt oder dass alle KI-Funktionen Dummy-Code sind; diese Aussagen müssen durch das P0-Audit und reale Geräte-/APK-Tests bestätigt werden.
