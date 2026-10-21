# Lokale LLM-Funktionen – Live-Todo

**Stand:** 21.10.2026 · **Branch:** `main` · **Prüfbasis:** Repository-Code und Dokumentation; kein APK-/Gerätetest in dieser Prüfung.
**Statusbegriffe:** ✅ belegt/erledigt · 🟡 teilweise belegt · ⛔ blockiert/offen

## Zielzustand

Alle vorgesehenen KI-Funktionen laufen wirklich lokal über das ausgewählte `.gguf`-Modell und llama.cpp. Das LLM soll Dokumente semantisch analysieren, Felder/Kategorien/Ablagewege vorschlagen, neue Regeln aus freier Sprache entwerfen und bestehende Regeln verstehen bzw. verbessern. Vorhandene freigegebene Regeln dürfen automatisch greifen; mehrdeutige oder neue Regeländerungen werden validiert. OCR, Regex und deterministische Regeln bleiben ein eigenständiger, funktionierender Backup-Pfad – dürfen aber nie als LLM-Ergebnis ausgegeben werden. **Premium-/Lizenz-Gating ist nicht Teil dieser Aufgabe.**

## IST → SOLL

| Bereich | Bestätigter IST-Zustand | Nächster Soll-Schritt | Status |
|---|---|---|---|
| Native GGUF-Laufzeit | Kotlin ruft `System.loadLibrary("llama_android")` auf und deklariert eine JNI-Methode. Im gesamten getrackten Repository wurden keine C/C++-Implementierung, `CMakeLists.txt`, `jniLibs/`, `.so`-Dateien oder Gradle-Native-Buildkonfiguration gefunden. Bei fehlender Bibliothek/Fehlern fällt die Engine auf Heuristiken zurück. | Native llama.cpp reproduzierbar integrieren; APK/ABI und JNI auf Gerät verifizieren. Detaillierte Arbeitsschritte: `docs/newAP-prompts.md`, L01–L02 und L08A–L11A. | ⛔ Blocker |
| Dokument-Triage / Tiefenanalyse | First-/Second-Run rufen die Engine nur bei ausreichend großer Modelldatei auf; die Engine kann intern trotzdem heuristisch antworten. Ohne Datei greift zusätzlich feste OCR-/Keyword-Klassifikation. Konfidenz und TPS werden teilweise fest gesetzt bzw. aus Antwortlänge geschätzt. | Echten Inferenzmodus von Regel-/Fehlermodus unterscheiden; keine erfundenen Konfidenz- oder Leistungswerte. Beide Analyse-Stufen mit echtem GGUF testen. | ⛔ Offen |
| Regelgenerator / Regelpflege | `generateRulesFromPrompt()` enthält feste Themenzweige und einen generischen Wort-Fallback, aber keinen Modellaufruf. `matchRule()` verlangt aktuell alle Keywords und prüft Ausschlussbegriffe; semantischer Abgleich fehlt. `refineRuleSuggestion()` ruft die Engine auf, erbt aber deren Fallback. | LLM soll freie Beschreibungen in strukturierte Regeln übersetzen, bestehende Regeln vergleichen/verbessern und Konflikte erkennen. Deterministische Validierung und sicherer Regel-Commit bleiben Kotlin-Aufgabe. | ⛔ Offen |
| Dokumenten-Assistent / Tools | `queryDocumentAssistant()` nutzt direkt `LlmToolExecutor` (Kotlin-Heuristiken). Der Follow-up-Pfad kann die Engine aufrufen, aber auch heuristisch zurückfallen. Tool-Schemas, Router und ViewModel-Aktionen existieren; deren Existenz beweist noch keinen echten Modell-Tool-Workflow. | Chat/RAG und Tool-Aufrufe bei vorhandenem Modell tatsächlich über GGUF führen; Argumente prüfen, reale Ausführung bestätigen und Fallback sichtbar kennzeichnen. | 🟡 Teilweise |
| Modelle / Hardware | Katalog, Import und Download vorhanden; Downloadprüfung umfasst u. a. Mindestgröße und GGUF-Magic-Bytes. Hardware-TPS sind Schätzungen/Formeln, kein echter GGUF-Benchmark. | Quellen/Lizenzen/Hashes und Modellkompatibilität verifizieren; echten Laufzeit-Benchmark von Schätzwerten trennen. | 🟡 Teilweise |
| Regel-/OCR-Backup | Scan- und Batch-Pfade prüfen zuerst `DocRuleRepository`; bei Treffer ist automatische Zuordnung vorhanden. Extraktion, Feldharmonisierung und Quality Gatekeeper enthalten absichtlich deterministische Logik. | Diesen Pfad erhalten und separat testen; Quelle eines Ergebnisses (Regel, OCR/Heuristik, LLM, Nutzer) eindeutig kennzeichnen. | ✅ Belegt; Regressionstests offen |

## Priorisierte To-dos

- [x] **Quellcode-Inventur:** zentrale LLM-, Regel-, Chat-, Tool- und Dokumenten-Aufrufpfade sowie vorhandene LLM-Dokumentation geprüft; keine native Implementierung im getrackten Repository gefunden.
- [ ] **P0 – Native Wahrheit herstellen:** L01-L02 abschließen, dann L08A-L11A umsetzen. Abnahme: ausgewähltes GGUF generiert auf unterstütztem Android-Gerät echte Tokens; APK enthält passende native Bibliothek/ABI; JNI- und Negativtests bestehen.
- [ ] **P1 – Ehrliche Laufzeit:** explizite Zustände `LOCAL_LLM`, `RULE_FALLBACK`, `ERROR`; stille Heuristik-Fallbacks und fest behauptete Confidence/TPS beseitigen.
- [ ] **P2 – Smarte Regeln:** echten LLM-Regelentwurf und -verfeinerung implementieren; vorhandene Regeln semantisch berücksichtigen; freigegebene Regeln automatisch, neue/konfliktbehaftete Änderungen kontrolliert anwenden.
- [ ] **P3 – Alle Aufrufer verbinden:** Scan/Import/Inbox, First-/Second-Run, Dokumenten-Chat/RAG und Tool-Calling mit echtem Inferenzpfad durchtesten; Backup-Pfad separat erhalten.
- [ ] **P4 – Nachweis statt Behauptung:** Modellkatalog/Integrität, echte Performance, Engine-Fehler, Offline-Betrieb und Kernfunktionen in automatisierten sowie Geräte-Tests nachweisen; widersprüchliche Dokumentaussagen erst nach Tests aktualisieren.

## Verbindliche Abnahme

Eine Funktion gilt erst als echte LLM-Funktion, wenn der Test belegt, dass das gewählte GGUF-Modell über die paketierte native llama.cpp-Laufzeit die Antwort erzeugt hat. Tests, die nur JSON, Regex, Schlüsselwörter oder Fallback-Ergebnisse prüfen, zählen dafür nicht. Der deterministische Backup-Pfad muss auch ohne Modell weiterarbeiten und darf nie als KI-Inferenz erscheinen.

**Grenze des aktuellen Befunds:** Repository-Prüfung ist abgeschlossen; tatsächlicher APK-Inhalt, ABI-/JNI-Lauffähigkeit, Build und echte Geräte-Inferenz sind noch nicht verifiziert.
