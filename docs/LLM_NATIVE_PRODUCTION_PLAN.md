# Lokale LLM-Funktionen – Live-Todo

**Stand:** 22.10.2026 · **Branch:** `main` · **Pflegepflicht:** Nach jedem Arbeitsblock Aufgabenstatus, Commit und CI-Nachweis hier aktualisieren. Ein grüner CI-Build belegt keinen realen Gerätetest.
**Statusbegriffe:** ✅ belegt/erledigt · 🟡 teilweise belegt · ⛔ blockiert/offen

## Arbeitsprotokoll – erledigte Änderungen und Nachweise

| Datum (UTC) | Commit | Erledigt | CI-Nachweis |
|---|---|---|---|
| 21.10.2026 | `cc372dc3` | Mehrdeutige Regel-Treffer werden an die Auswahlentscheidung weitergereicht, statt still eine Regel zu bevorzugen. | Dieser Lauf wurde durch einen nachfolgenden Commit abgebrochen; kein eigener grüner Nachweis. |
| 21.10.2026 | `c0f0376d` | Warnhinweise vor dem Speichern überlappender Regeldefinitionen ergänzt. | GitHub Actions erfolgreich. |
| 22.10.2026 | `c0aa4553` | Hardware-Fallbacks erfinden keine Geräteleistung mehr; unbekannte Kapazität bleibt unbekannt. | GitHub Actions erfolgreich; Debug-APK gebaut, Instrumentierungstests bestanden, APK hochgeladen. |
| 22.10.2026 | `f23786b2` | Heuristischer Fallback wird nicht als gemessener Modellbenchmark ausgegeben; Benchmark-Ergebnis kennzeichnet Messstatus; unbekannte Hardware wird als unbekannt/limitiert behandelt; UI bezeichnet Schätzwerte als Schätzung. | GitHub Actions erfolgreich (Run [39699404634](https://github.com/MrLongNight/myDocAnizer_Mobile/actions/runs/39699404634)). |
| 22.10.2026 | `8ee30cdb` | Extraktionsprompts für kleine Modelle verschlankt: keine ausführliche Chain-of-Thought-Ausgabe; Zusatzfelder nur mit OCR-belegten Werten; keine erfundenen Werte; JSON-Ausgabe klar vorgegeben. | **CI dieses Commits wurde durch den nachfolgenden Push abgebrochen**; der Prompt-Quellstand ist im Dokumentations-Sync-Branch enthalten und dort erneut zu prüfen. |
| 22.10.2026 | `7fb2776e` / `55a961af` / `8c2cd9a9` | Benchmark übernimmt erzeugte Tokens, Promptzeit, Generierungszeit und TPS aus nativen llama.cpp-Metriken in `ModelBenchmarkResult`; die UI zeigt die gemessene Zeitaufteilung an. Textlängen-Schätzung entfernt; fehlende Messwerte ergeben `isMeasured = false`. Status/Metriken werden vor Inferenz und auf frühen Fallback-Pfaden zurückgesetzt. | CI noch ausstehend; nicht als Build-verifiziert markieren. |
| 22.10.2026 | `468654cc` | Fehlende Pre-Commit-Konfiguration ergänzt (YAML/JSON-Validierung und Merge-Conflict-Prüfung), nachdem `pre-commit.ci` mit `error during ci config` abbrach. | Erneuter Pre-Commit-Lauf noch ausstehend. |

**Wichtig:** „Erledigt“ bedeutet hier Codeänderung committed. Ein CI-Erfolg ist separat vermerkt; echte GGUF-Inferenz auf physischen Geräten ist weiterhin nicht belegt.


## Scan-/Chat-Stabilisierung – Arbeitsprotokoll (22.10.2026)

Diese Scan- und Chat-Aufgaben sind im selben Repository offen, auch wenn der Dateiname dieses Dokuments auf LLM verweist. Die verbindliche kompakte IST/SOLL-Liste liegt in [open_tasks.md](open_tasks.md).

| Commit | Änderung | CI-Status / Grenze |
|---|---|---|
| `643c0be6` | Einzelne Scan-Regeltreffer müssen bestätigt werden; manueller Eckpunkt-Drag stabilisiert; schneller „Hi“-Smoke-Test; Testbericht-Upload bei CI-Fehler. | Gesamt-CI fehlgeschlagen. |
| `2eb2f62c` | OCR-Callback auf 12 s begrenzt und APK vor Unit-Tests veröffentlicht. | APK gebaut/verifiziert/hochgeladen; 187/188 Tests bestanden, Perspektivtest fehlgeschlagen. |
| `635cad62` | Synthetische schräge Dokumentseite pixelweise erzeugt, um Canvas-Rasterisierungsabhängigkeit zu vermeiden. | CI durch nachfolgenden Push abgebrochen; Verifikation offen. |
| `8d5ef909` | Auto-Scan prüft zusätzlich Bewegung und plausible Helligkeit und stabilisiert länger. | Separater Lauf abgebrochen; nicht verifiziert. |
| `e1448cab` | Drosselte AF/AE-Messung am Bildzentrum bei unscharfer Vorschau. | CI-Verifikation offen. |

**Scanner-Architekturentscheidung:** Googles ML Kit Document Scanner ist die Qualitätsreferenz, weil der Nutzer damit zuverlässige Massendigitalisierung erlebt hat. Zuerst Vergleich/Proof of Concept mit realen Seiten und Negativbeispielen; die API bringt einen eigenen Scanner-Flow mit und ist nicht automatisch ein Drop-in-Ersatz für die bestehende Live-Kamera. Erreicht der vorhandene Live-Scanner die Abnahmekriterien nicht, soll die Scan-Logik ausgetauscht werden, statt fragile Heuristiken unbegrenzt zu erweitern. Keine Qualitätsbehauptung ohne physischen Gerätetest.

## Dokumentations-Synchronisierung

README, LLM-Prozessdokumentation, technische LLM-Kurzdokumentation, Coding-Agent-Regeln und Dokumentationsindex müssen bei relevanten Änderungen gemeinsam aktualisiert werden. Veraltete Aussagen zu „vollständig verifiziert“, Vulkan/GPU, Modellqualität, Laufzeit und nativem Function-Calling wurden korrigiert. Diese Änderungen beschreiben die bestätigten Grenzen und verweisen auf offene Abnahmen. Den CI-Status dieser Synchronisierung erst nach Abschluss des zugehörigen Runs als erfolgreich markieren.

## Nächste konkrete Aufgaben

- [x] Native Benchmark-Ergebnisse aus den echten llama.cpp-Metriken (Promptzeit, Generierungszeit, erzeugte Tokens, TPS) direkt in `ModelBenchmarkResult` übernehmen; keine Zeichenlängen-Näherung als Messwert verwenden.
- [x] Vor jedem Inferenzaufruf Messwerte und `lastInferenceMode` zurücksetzen, damit ein vorheriger erfolgreicher Aufruf nicht den Status eines späteren Fallbacks verfälscht.
- [ ] Modellqualität, Hardware-Empfehlung und „Quality vs Quantity“-Einstellung sauber trennen: gemessene Werte vs. Schätzungen sichtbar machen und die Einstellung wirksam erklären/anwenden.
- [ ] Nach jedem Commit unmittelbar den zugehörigen GitHub-Actions-Lauf prüfen und diesen Abschnitt aktualisieren; Fehler vor dem nächsten Code-Commit beheben.

## Zielzustand

Alle vorgesehenen KI-Funktionen laufen wirklich lokal über das ausgewählte `.gguf`-Modell und llama.cpp. Das LLM soll Dokumente semantisch analysieren, Felder/Kategorien/Ablagewege vorschlagen, neue Regeln aus freier Sprache entwerfen und bestehende Regeln verstehen bzw. verbessern. Vorhandene freigegebene Regeln dürfen automatisch greifen; mehrdeutige oder neue Regeländerungen werden validiert. OCR, Regex und deterministische Regeln bleiben ein eigenständiger, funktionierender Backup-Pfad – dürfen aber nie als LLM-Ergebnis ausgegeben werden. **Premium-/Lizenz-Gating ist nicht Teil dieser Aufgabe.**

## IST → SOLL

| Bereich | Bestätigter IST-Zustand | Nächster Soll-Schritt | Status |
|---|---|---|---|
| Native GGUF-Laufzeit | Der AAR-Ansatz wurde verworfen, weil er in der frei verfügbaren Variante nur `arm64-v8a` enthält. Stattdessen ist `llama.cpp` als Git-Submodul auf Commit `2da668617612d2df773f966e3b0ee22dc2beef7b` gepinnt; CMake baut eine eigene JNI-Bibliothek für `arm64-v8a` und `x86_64`. 32-Bit-ABIs erhalten einen kleinen JNI-Stub, damit die App installierbar bleibt; Kotlin wählt dort bewusst den Fallback. | CI/NDK-Build prüfen; GGUF auf arm64-Gerät und x86_64-Emulator ausführen; 32-Bit-Fallback und JNI-Fehlerpfad testen. | 🟡 Code integriert; Build/Gerätetest offen |
| Dokument-Triage / Tiefenanalyse | First-/Second-Run rufen die native JNI-Engine auf; `lastInferenceMode` unterscheidet `LOCAL_LLM` und `RULE_FALLBACK`. GBNF wird an den llama.cpp-Sampler übergeben. Tokenzahl, Promptzeit, Generierungszeit und TPS kommen aus dem nativen Resultat. | Build und echte Inferenz verifizieren; UI/Diagnostik muss den Modus sichtbar machen; Modell-/Kontext-RAM-Verhalten auf Geräten testen. | 🟡 Code integriert; Build/Gerätetest offen |
| Regelgenerator / Regelpflege | `generateRulesFromPrompt()` enthält feste Themenzweige und einen generischen Wort-Fallback, aber keinen Modellaufruf. `matchRule()` verlangt aktuell alle Keywords und prüft Ausschlussbegriffe; semantischer Abgleich fehlt. `refineRuleSuggestion()` ruft die Engine auf, erbt aber deren Fallback. | LLM soll freie Beschreibungen in strukturierte Regeln übersetzen, bestehende Regeln vergleichen/verbessern und Konflikte erkennen. Deterministische Validierung und sicherer Regel-Commit bleiben Kotlin-Aufgabe. | ⛔ Offen |
| Dokumenten-Assistent / Tools | `queryDocumentAssistant()` nutzt direkt `LlmToolExecutor` (Kotlin-Heuristiken). Der Follow-up-Pfad kann die Engine aufrufen, aber auch heuristisch zurückfallen. Tool-Schemas, Router und ViewModel-Aktionen existieren; deren Existenz beweist noch keinen echten Modell-Tool-Workflow. | Chat/RAG und Tool-Aufrufe bei vorhandenem Modell tatsächlich über GGUF führen; Argumente prüfen, reale Ausführung bestätigen und Fallback sichtbar kennzeichnen. | 🟡 Teilweise |
| Modelle / Hardware | Katalog, Import und Download vorhanden; Downloadprüfung umfasst u. a. Mindestgröße und GGUF-Magic-Bytes. Hardware-TPS sind Schätzungen/Formeln, kein echter GGUF-Benchmark. | Quellen/Lizenzen/Hashes und Modellkompatibilität verifizieren; echten Laufzeit-Benchmark von Schätzwerten trennen. | 🟡 Teilweise |
| Regel-/OCR-Backup | Scan- und Batch-Pfade prüfen zuerst `DocRuleRepository`. | Neue/aktualisierte/aktivierte Regeldefinitionen werden abgewiesen, wenn ihre Pflicht- und Ausschlusskeywords eine Überschneidung mit einer anderen aktivierten Regel zulassen; Runtime-Matching bleibt zusätzlich fail-closed. Tests decken Definition-Konflikte und Laufzeit-Mehrdeutigkeit ab. Bestehende gespeicherte Konflikte müssen separat bereinigt werden. | 🟡 Code geändert; Tests noch auszuführen |

## Priorisierte To-dos

- [x] **L01 – Laufzeit-/Repository-Audit:** Kotlin/JNI-Aufrufpfad und Feature-Aufrufer geprüft; keine native Implementierung/Builddefinition im getrackten Repository gefunden. APK bleibt ungeprüft.
- [ ] **P0 – Native Wahrheit herstellen:** Native CMake/JNI-Integration mit gepinntem llama.cpp-Submodul angelegt. Abnahme offen: CI/Build für alle vier ABIs, echte GGUF-Inferenz auf `arm64-v8a` und `x86_64`, Grammatik-Negativtest, wiederholte Modellaufrufe sowie Installations-/Fallback-Test auf 32-Bit-ABI.
- [ ] **P1 – Ehrliche Laufzeit:** `LOCAL_LLM` und `RULE_FALLBACK` sind im Engine-Status getrennt; noch offen: UI/Diagnostik anbinden und verbleibende feste Confidence-/TPS-Angaben außerhalb dieser Engine prüfen.
- [x] **Sicherer Regel-Treffer:** mehrere gleichzeitig passende Regeln führen nicht mehr zur willkürlichen Auswahl der zuerst gespeicherten Regel.
- [x] **Konflikte vorbeugen:** neue, geänderte oder erneut aktivierte Regeln werden nicht gespeichert/aktiviert, wenn die Definitionen auf demselben OCR-Text gleichzeitig greifen könnten. Bereits vorhandene widersprüchliche Regeln werden beim Runtime-Matching sicher abgewiesen, müssen aber noch bereinigt werden.
- [ ] **P2 – Smarte Regeln:** echten LLM-Regelentwurf und -verfeinerung implementieren; vorhandene Regeln semantisch berücksichtigen; freigegebene Regeln automatisch, neue/konfliktbehaftete Änderungen kontrolliert anwenden.
- [ ] **P3 – Alle Aufrufer verbinden:** Scan/Import/Inbox, First-/Second-Run, Dokumenten-Chat/RAG und Tool-Calling mit echtem Inferenzpfad durchtesten; Backup-Pfad separat erhalten.
- [ ] **P4 – Nachweis statt Behauptung:** Modellkatalog/Integrität, echte Performance, Engine-Fehler, Offline-Betrieb und Kernfunktionen in automatisierten sowie Geräte-Tests nachweisen; widersprüchliche Dokumentaussagen erst nach Tests aktualisieren.

## Verbindliche Abnahme

Eine Funktion gilt erst als echte LLM-Funktion, wenn der Test belegt, dass das gewählte GGUF-Modell über die paketierte native llama.cpp-Laufzeit die Antwort erzeugt hat. Tests, die nur JSON, Regex, Schlüsselwörter oder Fallback-Ergebnisse prüfen, zählen dafür nicht. Der deterministische Backup-Pfad muss auch ohne Modell weiterarbeiten und darf nie als KI-Inferenz erscheinen.

**Aktueller Nachweis:** Native CMake/JNI-Quellen und ein auf Commit `2da668617612d2df773f966e3b0ee22dc2beef7b` gepinntes llama.cpp-Submodul sind eingecheckt. GitHub Actions bestätigte für Commit `f23786b2` einen erfolgreichen Debug-APK-Build samt Instrumentierungstests und APK-Upload. Der Prompt-Commit `8ee30cdb` wurde durch einen Folge-Push abgebrochen und muss im Dokumentations-Sync-Branch erneut geprüft werden. Diese CI-Ergebnisse belegen keine echte GGUF-Inferenz auf einem physischen Gerät; ABI-Installierbarkeit und Gerätelaufzeit bleiben offen. Die native Konfiguration ist CPU-only; GPU/Vulkan wird nicht behauptet.
