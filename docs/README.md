# Dokumentations-Übersicht: myDocAnizer-Mobile

In diesem Verzeichnis befinden sich alle technischen und konzeptionellen Dokumentationen für das Projekt **myDocAnizer-Mobile** sowie den Companion-Client **myDocAnizer-Desktop**:

1. **[LLM Funktionen- & Prozessdokumentation](LLM_FUNKTIONEN_UND_PROZESSE_DOKUMENTATION.md)**
   - Dokumentation der lokalen LLM-Architektur, des bestätigten Ist-Zustands und offener Verifikationspunkte
   - Modellkatalog und Hardware-Empfehlungen (Schätzwerte sind keine gemessenen Benchmarks)
   - GGUF-Magic-Header-Validierung & Download-Lifecycle
   - 4-Stufen Pipeline (Stufe 0: Regeln, Stufe 1: Triage, Stufe 2: Fachabteilungen, Stufe 3: Ordner-Routing, Stufe 4: Quality Gatekeeper)
   - Tool-Router, Tool-Schemas und Native App Tools; modellgetriebenes End-to-End-Function-Calling ist noch nicht nachgewiesen
   - Fristen- & Termin-Erkennung (DeadlineDetectionService)
   - API-Referenz, Fallback-Mechanismen und Test-Matrix

2. **[LLM Integration Technische Kurz-Dokumentation](LLM_INTEGRATION_DOCUMENTATION.md)**
   - Übersicht der Komponenten und des Inferenzpfads; echte End-to-End-Geräte-Inferenz ist separat nachzuweisen

3. **[Coding-Agent Technische Entwickler-Dokumentation](coding-agent_documentation.md)**
   - Umfassende Beschreibung der Systemarchitektur, MVVM & Clean Architecture
   - 4-Team On-Device KI-Pipeline (Triage, Fachabteilungen, Stammdaten, Gatekeeper)
   - Room-Datenbank, FTS4 Volltextsuche, DAOs und Custom Fields
   - Deterministische Regel-Engine & Ordner-Strategie Wizard
   - Sicherheit, Biometrie, Zero-Knowledge AES-256-GCM Verschlüsselung
   - Mandatory Test Gate & Robolectric Unit-Testing

3a. **[Live-Aufgabenliste und CI-Nachweise](LLM_NATIVE_PRODUCTION_PLAN.md)**
   - Erledigte Änderungen mit Commit-/CI-Verweisen
   - Offene Aufgaben und Grenzen zwischen lokalem LLM und regelbasiertem Fallback
   - Abnahmekriterien für native Inferenz und echte Gerätetests

4. **[Entwickler- & Integrationsleitfaden myDocAnizer-Desktop](DEV_GUIDE_myDocAnizer-Desktop.md)**
   - Architektur- und Tech-Stack Empfehlungen für den Desktop-Client (Windows, macOS, Linux)
   - Vollständiges SQLite-Datenbankschema (100% kompatibel zu Android Room)
   - P2P Local WiFi Synchronisation: mDNS Discovery, QR-Code Pairing Handshake, AES-256-GCM Verschlüsselung, REST/WebSocket API Endpunkte (Port 8765)
   - 3-Way Snapshot Konfliktlösung und Delta-Sync
   - Desktop OCR & Llama.cpp GGUF Integration

5. **[Changelog & Release Notes](../CHANGELOG.md)**
   - Vollständige Historie aller Versionen (inkl. Release 2.5.0 mit 4-Team Pipeline)

6. **[Haupt-README](../README.md)**
   - Projektübersicht, Modulübersicht, Preismodell und Build-Anleitung
