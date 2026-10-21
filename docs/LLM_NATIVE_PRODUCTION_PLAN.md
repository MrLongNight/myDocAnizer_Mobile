# Lokale LLM-Funktionen – Live-Todo

**Stand:** 21.10.2026 · **Branch:** `main` · **Prüfbasis:** vollständige Repository-Dateiinventur plus gezielter Code-/Dokumenten-Audit; kein APK-/Gerätetest.
**Statusbegriffe:** ✅ belegt/erledigt · 🟡 teilweise belegt · ⛔ blockiert/offen

## Zielzustand

Alle vorgesehenen KI-Funktionen laufen wirklich lokal über das ausgewählte `.gguf`-Modell und llama.cpp. Das LLM soll Dokumente semantisch analysieren, Felder/Kategorien/Ablagewege vorschlagen, neue Regeln aus freier Sprache entwerfen und bestehende Regeln verstehen bzw. verbessern. Vorhandene freigegebene Regeln dürfen automatisch greifen; mehrdeutige oder neue Regeländerungen werden validiert. OCR, Regex und deterministische Regeln bleiben ein eigenständiger, funktionierender Backup-Pfad – dürfen aber nie als LLM-Ergebnis ausgegeben werden. **Premium-/Lizenz-Gating ist nicht Teil dieser Aufgabe.**

## IST → SOLL

| Bereich | Bestätigter IST-Zustand | Nächster Soll-Schritt | Status |
|---|---|---|---|
| Native GGUF-Laufzeit | `dev.ffmpegkit-maintained:llama-android:0.1.1` ist als native llama.cpp-AAR eingebunden und ersetzt den bisherigen JNI-Aufruf ohne Implementierung durch echte `Llama.loadModel`/`Llama.complete`-Aufrufe. Laut Anbieter: CPU/NEON, `arm64-v8a`, API 24+; kein GBNF und kein x86_64 in der frei verfügbaren Variante. | Build/CI prüfen; auf arm64-Gerät echte GGUF-Inferenz und wiederholte Modellaufrufe testen. Nicht unterstützte ABIs müssen nachvollziehbar in den Fallback wechseln. | 🟡 Code integriert; Build/Gerätetest offen |
| Dokument-Triage / Tiefenanalyse | First-/Second-Run verwenden jetzt den nativen AAR-Pfad. `lastInferenceMode` unterscheidet `LOCAL_LLM` von `RULE_FALLBACK`; TPS/Zeit werden aus dem nativen Resultat übernommen, nicht aus der Antwortlänge geschätzt. Ungültiges JSON wechselt in den Fallback. | UI/Diagnostik muss den Modus sichtbar machen. GBNF-Grammatik wird von dieser AAR nicht unterstützt und darf nicht als aktiv behauptet werden. | 🟡 Code integriert; Build/Gerätetest offen |
| Regelgenerator / Regelpflege | `generateRulesFromPrompt()` enthält feste Themenzweige und einen generischen Wort-Fallback, aber keinen Modellaufruf. `matchRule()` verlangt aktuell alle Keywords und prüft Ausschlussbegriffe; semantischer Abgleich fehlt. `refineRuleSuggestion()` ruft die Engine auf, erbt aber deren Fallback. | LLM soll freie Beschreibungen in strukturierte Regeln übersetzen, bestehende Regeln vergleichen/verbessern und Konflikte erkennen. Deterministische Validierung und sicherer Regel-Commit bleiben Kotlin-Aufgabe. | ⛔ Offen |
| Dokumenten-Assistent / Tools | `queryDocumentAssistant()` nutzt direkt `LlmToolExecutor` (Kotlin-Heuristiken). Der Follow-up-Pfad kann die Engine aufrufen, aber auch heuristisch zurückfallen. Tool-Schemas, Router und ViewModel-Aktionen existieren; deren Existenz beweist noch keinen echten Modell-Tool-Workflow. | Chat/RAG und Tool-Aufrufe bei vorhandenem Modell tatsächlich über GGUF führen; Argumente prüfen, reale Ausführung bestätigen und Fallback sichtbar kennzeichnen. | 🟡 Teilweise |
| Modelle / Hardware | Katalog, Import und Download vorhanden; Downloadprüfung umfasst u. a. Mindestgröße und GGUF-Magic-Bytes. Hardware-TPS sind Schätzungen/Formeln, kein echter GGUF-Benchmark. | Quellen/Lizenzen/Hashes und Modellkompatibilität verifizieren; echten Laufzeit-Benchmark von Schätzwerten trennen. | 🟡 Teilweise |
| Regel-/OCR-Backup | Scan- und Batch-Pfade prüfen zuerst `DocRuleRepository`. | Neue/aktualisierte/aktivierte Regeldefinitionen werden abgewiesen, wenn ihre Pflicht- und Ausschlusskeywords eine Überschneidung mit einer anderen aktivierten Regel zulassen; Runtime-Matching bleibt zusätzlich fail-closed. Tests decken Definition-Konflikte und Laufzeit-Mehrdeutigkeit ab. Bestehende gespeicherte Konflikte müssen separat bereinigt werden. | 🟡 Code geändert; Tests noch auszuführen |

## Priorisierte To-dos

- [x] **L01 – Laufzeit-/Repository-Audit:** Kotlin/JNI-Aufrufpfad und Feature-Aufrufer geprüft; keine native Implementierung/Builddefinition im getrackten Repository gefunden. APK bleibt ungeprüft.
- [ ] **P0 – Native Wahrheit herstellen:** Erster nativer Adapter eingebunden (Maven AAR `0.1.1`, CPU/NEON, `arm64-v8a`, API 24+). Abnahme offen: CI/Build, echtes GGUF auf arm64-Gerät, wiederholte Aufrufe, Speicherfreigabe und Fallback auf nicht unterstützter ABI. Diese Variante bietet keine GBNF-Grammatik und kein x86_64.
- [ ] **P1 – Ehrliche Laufzeit:** `LOCAL_LLM` und `RULE_FALLBACK` sind im Engine-Status getrennt; noch offen: UI/Diagnostik anbinden und verbleibende feste Confidence-/TPS-Angaben außerhalb dieser Engine prüfen.
- [x] **Sicherer Regel-Treffer:** mehrere gleichzeitig passende Regeln führen nicht mehr zur willkürlichen Auswahl der zuerst gespeicherten Regel.
- [x] **Konflikte vorbeugen:** neue, geänderte oder erneut aktivierte Regeln werden nicht gespeichert/aktiviert, wenn die Definitionen auf demselben OCR-Text gleichzeitig greifen könnten. Bereits vorhandene widersprüchliche Regeln werden beim Runtime-Matching sicher abgewiesen, müssen aber noch bereinigt werden.
- [ ] **P2 – Smarte Regeln:** echten LLM-Regelentwurf und -verfeinerung implementieren; vorhandene Regeln semantisch berücksichtigen; freigegebene Regeln automatisch, neue/konfliktbehaftete Änderungen kontrolliert anwenden.
- [ ] **P3 – Alle Aufrufer verbinden:** Scan/Import/Inbox, First-/Second-Run, Dokumenten-Chat/RAG und Tool-Calling mit echtem Inferenzpfad durchtesten; Backup-Pfad separat erhalten.
- [ ] **P4 – Nachweis statt Behauptung:** Modellkatalog/Integrität, echte Performance, Engine-Fehler, Offline-Betrieb und Kernfunktionen in automatisierten sowie Geräte-Tests nachweisen; widersprüchliche Dokumentaussagen erst nach Tests aktualisieren.

## Verbindliche Abnahme

Eine Funktion gilt erst als echte LLM-Funktion, wenn der Test belegt, dass das gewählte GGUF-Modell über die paketierte native llama.cpp-Laufzeit die Antwort erzeugt hat. Tests, die nur JSON, Regex, Schlüsselwörter oder Fallback-Ergebnisse prüfen, zählen dafür nicht. Der deterministische Backup-Pfad muss auch ohne Modell weiterarbeiten und darf nie als KI-Inferenz erscheinen.

**Aktueller Nachweis:** Repository-Audit abgeschlossen; der bisher fehlende JNI-Pfad wurde durch eine gepinnte native AAR-API ersetzt. GitHub Actions und echte Geräte-Inferenz sind noch nicht verifiziert. Laut Anbieter unterstützt die frei verfügbare AAR nur `arm64-v8a`, CPU/NEON und keine GBNF-Grammatik; x86_64-Emulatoren bleiben im Fallback.
