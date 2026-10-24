# myDocAnizer-Mobile – Arbeitspakete

## Übersicht

Die Liste hatte viele Einträge **„In Arbeit“**, weil dieser Status bisher zwei verschiedene Dinge zusammenfasste: teilweise vorhandene Implementierung **und** fehlenden Abschlussnachweis. Das bedeutete nicht, dass 60 Punkte gleichzeitig aktiv bearbeitet wurden. Die Zahl allein war deshalb kein brauchbarer Fortschrittsmesser.

**Für den Release-Fortschritt zuerst verwenden:** [Praktische Beta-Freigabecheckliste](release-beta-checklist.md). Sie bündelt die App-Abnahme in neun Gates; du musst nicht 123 Prompt-Abschnitte einzeln als Release-Checkliste abarbeiten.

**Aktuelle Release-Zuordnung:** 46 APs sind Beta-Blocker (11 davon derzeit als „Umgesetzt / geprüft“ markiert; 35 sind noch nicht abgenommen), 51 betreffen Funktionen, die erst nach bestandenem Nachweis aktiviert werden sollen, und 26 sind bewusst auf später verschoben. Diese Zahlen zählen Micro-APs, nicht 46 separate manuelle Testläufe.

**Status – ab jetzt eindeutig:**
- **Offen** – die eigentliche Umsetzung ist noch nicht begonnen oder es gibt bisher nur eine Idee/Anforderung.
- **In Arbeit** – Teile sind im Code vorhanden, aber die definierte Funktion oder ein notwendiger Test/Nachweis fehlt noch. Der konkrete Rest steht in der AP-Abnahme.
- **Umgesetzt / geprüft** – die für genau diesen AP festgelegte Abnahme ist durch Code-/Test-/Dokumentationsnachweise belegt. Ein alter grüner Build allein genügt nicht.

**Release-Stufe:**
- **Beta-Blocker** – vor dem Google-Play-Betatest erledigen. Bei optionalen Verbindungen gilt: entweder nachweisen oder die Funktion in der Beta zuverlässig deaktivieren/verbergen.
- **Vor Aktivierung** – die App kann mit dieser Funktion zunächst getestet werden, aber der entsprechende Funktionsumfang darf erst eingeschaltet/als sicher beworben werden, wenn der AP abgeschlossen ist.
- **Später** – Refactoring/Optimierung, die den ersten sicheren Betatest nicht blockieren soll, sofern keine konkreten Fehler oder Abstürze dagegen sprechen.

**„Was konkret abnehmen?“** sagt dir, ob du die Funktion direkt in der App prüfen kannst oder ob ein technischer Test nötig ist. Bei Technik-/ADR-APs gibt es nicht zwangsläufig einen sichtbaren Knopf in der App. Die granularen Bezeichnungen sind Filter-/Zuordnungslabels, keine zusätzlichen Aufgaben.

| AP | Bereich | Funktion / Teilbereich | Arbeitspaket | Was konkret abnehmen? | Release-Stufe | Status |
|---|---|---|---|---|---|---|
| V01 | Tresor & Wiederherstellung | `Tresor_V2Negativtests` | V2-only Vault-Krypto-Inventar und Negativtests | Technik: gültiger V2-Tresor funktioniert; falsches Passwort, manipulierte Daten und alte V1-Formate scheitern geschlossen. | Beta-Blocker | Umgesetzt / geprüft |
| V02 | Tresor & Wiederherstellung | `Tresor_PasswortKDF` | Geprüfte Argon2id-Implementierung | App + Technik: Tresor mit Passwort erstellen/öffnen; falsches Passwort und unzulässige KDF-Parameter müssen scheitern. | Beta-Blocker | In Arbeit |
| V03 | Tresor & Wiederherstellung | `Tresor_VaultHeader` | VaultHeader v2: Datenmodell und Parser | Technik: V2-Header speichern/laden; abgeschnittene Felder, falsche Version und ungültige Längen ablehnen. | Beta-Blocker | Umgesetzt / geprüft |
| V04 | Tresor & Wiederherstellung | `Tresor_HeaderAuthentifizierung` | Header-Authentifizierung und AAD | Technik: Header/AAD manipulieren; Öffnen muss scheitern und darf keinen DEK freigeben. | Beta-Blocker | Umgesetzt / geprüft |
| V05 | Tresor & Wiederherstellung | `Tresor_2FAArchitektur` | Optionale echte Zwei-Faktor-Absicherung | Architektur-Nachweis: Passwort-only und Passwort+Schlüsseldatei unterscheiden; TOTP nicht als Tresor-Faktor zulassen. | Vor Aktivierung | Umgesetzt / geprüft |
| V06 | Tresor & Wiederherstellung | `Tresor_2FAKeyfile` | Zweiten Faktor technisch absichern und testen | App + Technik: Schlüsseldatei einrichten; Passwort allein, Datei allein und falsche Datei ablehnen; richtige Kombination öffnet. | Vor Aktivierung | Umgesetzt / geprüft |
| V06A | Tresor & Wiederherstellung | `Tresor_2FAEinrichtung` | Optionalen Zwei-Faktor-Modus sicher konfigurierbar machen | App: 2FA ein-/ausschalten, App neu starten; Modus und Tresoröffnung müssen übereinstimmen. | Vor Aktivierung | In Arbeit |
| V06B | Tresor & Wiederherstellung | `Tresor_2FA_DEKFreigabe` | Optionalen zweiten Faktor an die echte DEK-Freigabe binden | App + Technik: Freigabe ohne beide verlangten Faktoren muss scheitern, auch über Biometrie/Service-Aufruf. | Vor Aktivierung | In Arbeit |
| V09 | Tresor & Wiederherstellung | `Recovery_Format` | Recovery-Format und Wiederherstellungsoptionen festlegen (ADR) | Technik: Recovery-Format mit falschem Geheimnis und veränderten Bytes testen; keinen Tresor freigeben. | Vor Aktivierung | In Arbeit |
| V07 | Tresor & Wiederherstellung | `Recovery_BIP39` | BIP-39-Mnemonic-Validierung | Technik: offizielle BIP-39-Testvektoren für gültige/ungültige Wörter, Checksumme und Grenzfälle bestehen. | Vor Aktivierung | In Arbeit |
| V08 | Tresor & Wiederherstellung | `Recovery_Shamir` | Shamir-Share-Konsistenz | Technik: Shamir-Shares unter/bei/über der Schwelle und beschädigte Shares testen. | Vor Aktivierung | In Arbeit |
| V10 | Tresor & Wiederherstellung | `Recovery_Payload` | Versionierten Recovery-Payload-Kern umsetzen | App + Technik: Recovery-Payload erstellen und auf frischer Testinstallation wiederherstellen; falsches Geheimnis/manipulierte Bytes ablehnen. | Vor Aktivierung | In Arbeit |
| V11 | Tresor & Wiederherstellung | `Recovery_QR` | Standardkonforme QR-Kodierung | App: QR exportieren, wieder einlesen; abgeschnittener oder fremder QR muss verständlich abgewiesen werden. | Vor Aktivierung | In Arbeit |
| V12 | Tresor & Wiederherstellung | `Recovery_Notfalldokument` | Eindeutig gekennzeichnetes Notfalldokument | App: Notfalldokument exportieren und Testtresor wiederherstellen; PDF/QR enthalten niemals den rohen DEK. | Vor Aktivierung | In Arbeit |
| V12B | Tresor & Wiederherstellung | `Recovery_UnauffaelligesDokument` | Unauffälliges Notfalldokument | Vor Freigabe: nur synthetischen Testtresor tarnen/exportieren/importieren; Tarnung nicht als Kryptosicherheit darstellen. | Später | Offen |
| V13 | Tresor & Wiederherstellung | `Tresor_BiometrieKeystore` | Biometrie und Keystore getrennt absichern | App: Biometrie akzeptieren/abbrechen/ablehnen, App neu starten und Keystore-Fehler simulieren; unberechtigter Unlock bleibt gesperrt. | Beta-Blocker | In Arbeit |
| V14 | Tresor & Wiederherstellung | `Tresor_V2Only` | V2-only Laufzeit und vollständige Entfernung von V1 | Technik: Tests und Codeprüfung bestätigen keine V1-Lese-, Fallback- oder Migrationspfade; V2 bleibt nach Neustart nutzbar. | Beta-Blocker | Umgesetzt / geprüft |
| V15 | Tresor & Wiederherstellung | `Datenschutz_Systembackup` | Backup-Regeln für Vault-Secrets | Technik: Android-Backup/Device-Transfer prüfen; kein DEK, Token, Klartextdokument oder Recovery-Secret enthalten. | Beta-Blocker | Umgesetzt / geprüft |
| V16 | Tresor & Wiederherstellung | `Recovery_Passkey` | Wiederherstellung mit Passkey | Vor Freigabe: Passkey auf Gerät A einrichten, Restore auf Gerät B testen; inkompatibler Authenticator muss klar scheitern. | Später | Offen |
| V17 | Tresor & Wiederherstellung | `Recovery_Sicherheitsfragen` | Zusätzliche Sicherheitsfragen für Passwort-/Passkey-Restore | Vor Freigabe: richtige/falsche Antworten, Unicode-Normalisierung und Restore ohne Klartextantworten prüfen. | Später | Offen |
| V18 | Tresor & Wiederherstellung | `Recovery_Auswahl` | Wiederherstellungsoptionen verständlich auswählbar machen | App: Recovery-Option wählen und absichtlich falsche Option versuchen; Hinweise müssen verständlich sein. | Später | Offen |
| S01 | Sperre & Geheimnisse | `Sicherheit_LockSecretAudit` | Lock- und Secret-Inventar | Technik: Lock-/Unlock- und Secret-Pfade mit Codefundstellen dokumentieren; offene Lücken benennen. | Beta-Blocker | Umgesetzt / geprüft |
| S02 | Sperre & Geheimnisse | `Sicherheit_LockManager` | LockManager als Zustandsautomat | App + Technik: Prozessstart, Sperren/Entsperren und parallele Übergänge; kein widersprüchlicher Lockstatus. | Beta-Blocker | Umgesetzt / geprüft |
| S03 | Sperre & Geheimnisse | `Sicherheit_AutoLock` | Auto-Lock-Lifecycle | App: App in Hintergrund legen, Timeout abwarten und zurückkehren; vor Anzeige sensibler Daten muss Sperre greifen. | Beta-Blocker | Umgesetzt / geprüft |
| S04 | Sperre & Geheimnisse | `Sicherheit_UnlockBypass` | Unlock-Bypässe entfernen | App + Technik: Passwort falsch, Biometrie abbrechen und externen/UI-Bypass versuchen; kein Pfad darf entsperren. | Beta-Blocker | In Arbeit |
| S05 | Sperre & Geheimnisse | `Tresor_DEKLeases` | DEK-Leases an Lockstatus binden | Technik: gesperrten Schlüsselzugriff und Lock während Dateioperation prüfen; alte Schlüsselkopie darf nicht weiter nutzbar sein. | Beta-Blocker | In Arbeit |
| S06 | Sperre & Geheimnisse | `Tresor_FehlversuchsBackoff` | Fehlversuche und Backoff | App + Technik: Fehlversuche auslösen und Wartezeit prüfen; Neustart/Uhränderung testen; TOTP darf Vault nicht beeinflussen. | Beta-Blocker | In Arbeit |
| S07 | Sperre & Geheimnisse | `Datenschutz_ScreenshotRecents` | FLAG_SECURE und sensitive Screens | App: sensible Ansicht öffnen, Screenshot/Recents testen und sperren; Daten dürfen dann nicht sichtbar sein. | Vor Aktivierung | In Arbeit |
| S08 | Sperre & Geheimnisse | `Sicherheit_SecretStore` | SecretStore-Grundkomponente | Technik: Secret speichern/lesen; falscher Tag, fehlender Keystore-Key und Fehler dürfen nie Klartext-Fallback auslösen. | Beta-Blocker | Umgesetzt / geprüft |
| S09 | Sperre & Geheimnisse | `Sicherheit_SecretMigration` | Legacy-Secrets verlustfrei migrieren | App + Technik: erster und zweiter Start mit Legacy-Secret; Klartext erst nach erfolgreichem verschlüsseltem Roundtrip entfernen. | Beta-Blocker | In Arbeit |
| S10 | Sperre & Geheimnisse | `Sicherheit_ExterneIntents` | Externe Navigationsextras validieren | App: Widget/externe Aktion im gesperrten und entsperrten Zustand testen; gesperrte Aktion darf später nicht nachträglich ausgeführt werden. | Beta-Blocker | In Arbeit |
| D01 | Dokumente & Datensicherung | `Dokumente_DatenflussAudit` | Datenfluss-Inventar | Technik: Dokument-, OCR-, DB-, Cache- und Backup-Datenflüsse mit Codefundstellen/Risiken belegen. | Beta-Blocker | Umgesetzt / geprüft |
| D02 | Dokumente & Datensicherung | `Datenbank_SQLCipherPlan` | Vollständige Datenbankverschlüsselung (SQLCipher) technisch festlegen | Architektur: SQLCipher-Entscheidung mit Schlüsselbereitstellung, FTS, WAL/SHM und Migration festhalten; keine Runtime-Integration behaupten. | Beta-Blocker | In Arbeit |
| D03 | Dokumente & Datensicherung | `Sicherheit_ZweckgebundeneSchluessel` | Gemeinsame Schlüsselarchitektur für DB, Dokumente, Settings und Backups | Technik: Schlüsselzwecke für DB/Dokumente/Settings/Backup getrennt prüfen; gesperrter Tresor gibt keine Schlüssel frei. | Beta-Blocker | In Arbeit |
| D04 | Dokumente & Datensicherung | `Dokumente_VerschluesseltesDateiformat` | Streaming-Dateiformat für neue Dokumente | Technik: Datei verschlüsselt speichern/öffnen; falscher Tag, abgeschnittene Segmente und falscher Schlüssel scheitern. | Beta-Blocker | Offen |
| D05 | Dokumente & Datensicherung | `Dokumente_SicherSpeichern` | secureSave auf verschlüsselte Ablage umstellen | App + Technik: Dokument scannen/importieren, speichern, schließen und erneut öffnen; keine falsche Erfolgsmeldung. | Beta-Blocker | In Arbeit |
| D06 | Dokumente & Datensicherung | `Dokumente_OCRViewerExport` | Viewer, OCR, Share und Vorschau an Verschlüsselung anbinden | App: Dokument ansehen, OCR/Suche und Vorschau benutzen; Share/Export nur nach bewusster Nutzeraktion. | Beta-Blocker | In Arbeit |
| D07 | Dokumente & Datensicherung | `Dokumente_Verschluesselungsstatus` | Verschlüsselungsstatus aus tatsächlicher Speicherung ableiten | App + Technik: Statusanzeige mit tatsächlich gespeicherten Dateien vergleichen; Endung/Flag allein darf keine Verschlüsselung vortäuschen. | Beta-Blocker | Offen |
| D08 | Dokumente & Datensicherung | `Dokumente_Migration` | Resumierbare Migration vorhandener Dokumente | Technik: Migration abbrechen und wiederholen; Original bleibt bei Fehler lesbar, kein Duplikat/Verlust. | Vor Aktivierung | Offen |
| D09 | Dokumente & Datensicherung | `Dokumente_Kollisionsschutz` | Kollisionssichere Ablage | App: zwei gleichnamige Dokumente und parallele Importe speichern; nichts wird still überschrieben. | Beta-Blocker | In Arbeit |
| D10 | Dokumente & Datensicherung | `Datensicherung_VerschluesselterContainer` | Vollständig verschlüsselter Backupcontainer für alle Nutzdaten | App + Technik: Backup erstellen und auf frischer Installation wiederherstellen; falscher Schlüssel/kaputter Container dürfen keinen Datenverlust verursachen. | Beta-Blocker | In Arbeit |
| D11 | Dokumente & Datensicherung | `Datensicherung_RestoreValidierung` | Restore: Archive und Pfade validieren | Technik: Zip-Slip, absolute/verschachtelte Pfade, doppelte Einträge und zu große Archive ablehnen. | Beta-Blocker | Umgesetzt / geprüft |
| D12 | Dokumente & Datensicherung | `Datensicherung_RestoreCommit` | Restore: atomarer Commit und verständlicher Report | App + Technik: Restore absichtlich fehlschlagen lassen; Produktivbestand bleibt unverändert und Fehler wird verständlich gemeldet. | Beta-Blocker | Offen |
| D13 | Dokumente & Datensicherung | `Datenschutz_KlartextMirrors` | Unverschlüsselte Mirrors entfernen und Backupregeln prüfen | Technik: Dateisystem, Cache und Android-Backupregeln prüfen; keine unbeabsichtigte Klartextkopie bleibt zurück. | Beta-Blocker | In Arbeit |
| D14 | Dokumente & Datensicherung | `Dokumente_PDFTextlayerOCR` | PDF-Textlayer und OCR-Ausgaben vollständig prüfen | App + Technik: synthetisches PDF/OCR speichern; extrahierter Text liegt nicht in unverschlüsselter Datei/Indexkopie. | Beta-Blocker | In Arbeit |
| D15 | Dokumente & Datensicherung | `Einstellungen_Verschluesselung` | Alle App-Einstellungen verschlüsselt speichern | App + Technik: Einstellungen ändern und App neu starten; Werte sind verschlüsselt und wiederherstellbar. | Beta-Blocker | Offen |
| P01 | App-/PC-Kopplung (P2P) | `P2P_Transportaudit` | Sicherheits-Audit aller Verbindungsarten und Transport-ADR | Technik: jeden App2App-/PC2App-/Browser2App-Pfad als HTTP/TLS, Identität, Pairing und Negativtest inventarisieren. | Vor Aktivierung | Umgesetzt / geprüft |
| P02 | App-/PC-Kopplung (P2P) | `P2P_TLS_Server` | TLS für alle Server-Endpunkte verbindlich machen | App + Technik: jede angebotene Verbindung mit TLS testen; Klartextverbindung/Zertifikatsfehler muss abgewiesen werden. | Vor Aktivierung | Offen |
| P03 | App-/PC-Kopplung (P2P) | `P2P_NativeClientsTLS` | Native App- und PC-Clients: TLS und Identitätsprüfung | App + PC: koppeln und transferieren; unbekanntes/geändertes Zertifikat darf nicht still akzeptiert werden. | Vor Aktivierung | Offen |
| P04 | App-/PC-Kopplung (P2P) | `P2P_Pairing` | Sicherer Pairing-Handshake für alle Verbindungstypen | App + PC: koppeln, falschen/abgelaufenen Code und Wiederverwendung testen; nur richtige Gegenstelle wird vertraut. | Vor Aktivierung | In Arbeit |
| P05 | App-/PC-Kopplung (P2P) | `P2P_HTTPParser` | HTTP-Parsing und Ressourcenlimits | Technik: übergroße Header/Body, langsame/unvollständige Requests und widersprüchliche Längen ablehnen. | Vor Aktivierung | In Arbeit |
| P06 | App-/PC-Kopplung (P2P) | `P2P_Dateitransfer` | Dokumente über alle Verbindungstypen streamend übertragen | App + PC: große Datei senden, Transfer abbrechen/wiederholen; Hash stimmt und keine Klartext-Zwischenkopie. | Vor Aktivierung | In Arbeit |
| P07 | App-/PC-Kopplung (P2P) | `P2P_ReplayRateLimits` | Replay-Schutz, Sessionablauf und Rate-Limits | Technik: Request/Nonce wiederholen, falschen Code mehrfach senden und abgelaufene Session nutzen; alles ablehnen. | Vor Aktivierung | In Arbeit |
| P08 | App-/PC-Kopplung (P2P) | `P2P_Netzwerkbindung` | IP-CIDR und Schnittstellenbindung | App + Technik: WLAN wechseln, fremdes Netz und IPv6 testen; Server bleibt an erlaubte Schnittstelle gebunden. | Vor Aktivierung | In Arbeit |
| P09 | App-/PC-Kopplung (P2P) | `P2P_UploadZiele` | Sichere Upload-Ziele | App + PC: gleiche Dateinamen und fehlerhaften Upload senden; keine fremde Datei überschreiben. | Vor Aktivierung | In Arbeit |
| P10 | App-/PC-Kopplung (P2P) | `P2P_Kryptofallbacks` | Schwache Kryptofallbacks entfernen | Technik: ungültigen Schlüssel/beschädigte Payload testen; kein schwächerer Fallback. | Vor Aktivierung | Umgesetzt / geprüft |
| P11 | App-/PC-Kopplung (P2P) | `P2P_ServerLebenszyklus` | Serverlebenszyklus und Android-Berechtigungen | App: Server starten/stoppen, App verlassen, Berechtigungen verweigern und Gerät sperren; keine verwaisten Listener. | Vor Aktivierung | In Arbeit |
| P12 | App-/PC-Kopplung (P2P) | `P2P_EndToEndTests` | End-to-End-Sicherheitstests für App2App, PC2App und Browser2App | App + PC: alle aktivierten Transportwege mit falschem Zertifikat, Abbruch und beschädigter Datei Ende-zu-Ende prüfen. | Vor Aktivierung | Offen |
| P13 | App-/PC-Kopplung (P2P) | `P2P_Browser2App` | Browser2App sicher und ohne Warnungsumgehung unterstützen | Browser: Verbindung ohne Zertifikatswarnungsumgehung testen; bis zum Nachweis Browser2App deaktivieren/ausblenden. | Vor Aktivierung | Offen |
| L01 | Lokale KI (LLM) | `LLM_RuntimeAPKInventur` | Laufzeit- und APK-Inventur | Technik: APK prüfen und pro ABI nachweisen, welche native Bibliothek enthalten ist; Modelllauf separat ausweisen. | Vor Aktivierung | In Arbeit |
| L02 | Lokale KI (LLM) | `LLM_Produktionsplan` | Native-Produktionsplan mit dem tatsächlichen Bestand abgleichen | Dokumentation: Produktionsplan mit Quellstand und aktuellem CI verknüpfen; alte Befunde kennzeichnen. | Vor Aktivierung | In Arbeit |
| L03 | Lokale KI (LLM) | `LLM_Modellkatalog` | Modellkatalog und Quellenbelege | App + Technik: Modellkatalog auf Quelle/Lizenz/Hash/Dateigröße prüfen; unvollständige Modelle nicht aktivieren. | Vor Aktivierung | In Arbeit |
| L04 | Lokale KI (LLM) | `LLM_ModelldownloadWorker` | WorkManager-Downloader-Grundlage | App: Download starten, abbrechen und erneut starten; Status/Fortschritt/Fehler korrekt, keine doppelten Jobs. | Vor Aktivierung | In Arbeit |
| L05 | Lokale KI (LLM) | `LLM_Modellintegritaet` | Modellintegrität und private Ablage | Technik: falschen Hash, Größe, GGUF-Kopf und Redirect simulieren; Datei wird verworfen und bleibt inaktiv. | Vor Aktivierung | In Arbeit |
| L06 | Lokale KI (LLM) | `LLM_DownloadResume` | Download-Resume korrekt implementieren | App + Technik: Download unterbrechen/fortsetzen; keine doppelten Bytes; ungültiger Range startet sauber neu. | Vor Aktivierung | Offen |
| L07 | Lokale KI (LLM) | `LLM_LaufzeitmodusDatenschutz` | Ehrliche Laufzeitmodi und Datenschutz | App: Modell verfügbar/nicht verfügbar und Offline-Modus testen; Regel-Fallback ist klar als regelbasiert gekennzeichnet. | Beta-Blocker | In Arbeit |
| L08A | Lokale KI (LLM) | `LLM_NativeBuildABIs` | Vorhandene Native-Buildintegration reproduzierbar verifizieren | Technik: CI baut native Ziel-ABIs; APK-/ELF-/16-KB-Prüfung und gepinnte Quelle dokumentiert. | Vor Aktivierung | In Arbeit |
| L09A | Lokale KI (LLM) | `LLM_JNI_Modelllebenszyklus` | JNI-Modelllebenszyklus | Technik: Modell wiederverwenden/wechseln, ungültige Datei und parallele Aufrufe testen; kein Absturz/Leak. | Vor Aktivierung | In Arbeit |
| L10A | Lokale KI (LLM) | `LLM_NativeInferenz` | Vorhandene echte Inferenz verifizieren und sicher begrenzen | App + Technik: gehashtes GGUF wirklich laden und Modellantwort erzeugen; ohne echten Lauf nicht als Inferenz bestanden markieren. | Vor Aktivierung | In Arbeit |
| L11A | Lokale KI (LLM) | `LLM_ABI_16KB_Geraete` | ABI-/16-KB-/Geräteprüfung | Technik: Paketierung/Installation jeder zugesagten ABI samt 16-KB-Prüfung dokumentieren; 32-Bit-Fallback testen. | Vor Aktivierung | In Arbeit |
| L12 | Lokale KI (LLM) | `LLM_InferenzGrammarCancel` | Native Inferenz absichern: Grammar, Cancel, Kontext und echte Metriken | App + Technik: Modelllauf abbrechen/Timeout auslösen; Sperre wird frei, Grammarfehler sichtbar, Metriken stammen aus diesem Lauf. | Vor Aktivierung | In Arbeit |
| L13 | Lokale KI (LLM) | `LLM_Regelerstellung_Kandidatenanalyse` | Kandidatenbasierte Hybridanalyse statt frei erfundener Feldwerte | App: synthetische Rechnung analysieren und Felder mit OCR-Beleg prüfen; Betrag/Datum/Absender/Kategorie ohne Beleg darf nicht automatisch gespeichert werden. | Beta-Blocker | Offen |
| L14 | Lokale KI (LLM) | `LLM_AuditDokumentenprozess` | Durchgängiges, datensparsames Audit der Dokumentenverarbeitung | App + Technik: Scan bis Saved/Failed verfolgen; Audit enthält Prozessphasen, aber keinen OCR-Text/PII. | Beta-Blocker | In Arbeit |
| L15 | Lokale KI (LLM) | `LLM_ScanPipelineChatAktionen` | Einheitliche Scan-Pipeline, Nutzerprüfung und sichere Chat-Aktionen | App: Scan/importieren, Vorschlag prüfen, Regel anwenden und Chat-Aktion abbrechen/bestätigen; kein Speichern vor Bestätigung. | Beta-Blocker | In Arbeit |
| L16 | Lokale KI (LLM) | `LLM_ModellEmpfehlungen` | Modellkatalog, Integrität und Empfehlungen belastbar machen | App + Technik: Empfehlung gegen Lizenz/Hash/Gerätemessung prüfen; ungemessene Modelle heißen experimentell. | Vor Aktivierung | In Arbeit |
| L17 | Lokale KI (LLM) | `LLM_QualitaetGoldenSet` | Golden Set, Kandidatenabdeckung und echte Auswahlqualität nachweisen | Technik: Golden Set reproduzierbar ausführen; Kandidatenabdeckung und Auswahlgenauigkeit getrennt berichten. | Vor Aktivierung | Offen |
| C01 | Cloud-Datensicherung | `Datensicherung_CloudSyncSicherheit` | Cloud-Sync, Verschlüsselungsgrenze und Sperrverhalten auditieren | App + Technik: Cloud-Sync ohne Konfiguration und bei gesperrtem Tresor testen; nichts automatisch aktivieren oder im Klartext senden. | Vor Aktivierung | In Arbeit |
| C02 | Cloud-Datensicherung | `Datensicherung_WorkManager` | WorkManager-Grundmigration | App + Technik: Sync starten/abbrechen, offline gehen und erneut versuchen; nur ein kontrollierter Worker läuft. | Vor Aktivierung | Offen |
| C03 | Cloud-Datensicherung | `Datensicherung_VaultSecretStore` | Vault-Lock und SecretStore anbinden | App: Sync bei gesperrtem Vault/fehlendem Secret starten; kein automatisches Entsperren oder Passwort-Fallback. | Vor Aktivierung | In Arbeit |
| C04 | Cloud-Datensicherung | `Datensicherung_GoogleDrive_OAuth` | Google-Drive-OAuth sicher integrieren | App: Google-Konto autorisieren, widerrufen und neu autorisieren; keine manuellen Access-Tokens. | Vor Aktivierung | In Arbeit |
| C05 | Cloud-Datensicherung | `Datensicherung_GoogleDrive_Transfer` | Google-Drive-Client und Transfers absichern | App + Technik: großen Drive-Container übertragen, 401/429/Netzabbruch testen; Integrität vor Restore prüfen. | Vor Aktivierung | In Arbeit |
| C06 | Cloud-Datensicherung | `Datensicherung_WebDAV_TLS` | WebDAV-HTTPS und Zertifikatsvertrauen | App: WebDAV mit gültigem HTTPS und falschem/geändertem Zertifikat prüfen; unsichere Verbindung ablehnen. | Vor Aktivierung | In Arbeit |
| C07 | Cloud-Datensicherung | `Datensicherung_SyncManifest` | Verschlüsseltes Sync-Manifest und Differenzabgleich | App + Technik: zwei Geräte synchronisieren, Änderung/Löschung/Konflikt simulieren; Manifest bleibt authentifiziert. | Vor Aktivierung | In Arbeit |
| C08 | Cloud-Datensicherung | `Datensicherung_SyncModus` | Manuellen/automatischen Sync konfigurierbar machen | App: manuellen/automatischen Sync einstellen und neu starten; Auto-Sync nur nach Opt-in. | Vor Aktivierung | In Arbeit |
| C09 | Cloud-Datensicherung | `Datensicherung_CloudEndToEnd` | Cloud-End-to-End-Tests | App + Technik: Cloud-Backup erstellen/wiederherstellen; falsche Credentials/kaputte Datei scheitern ohne Datenverlust. | Vor Aktivierung | Offen |
| DB01 | Datenbank & Suche | `Datenbank_SchemaBaseline` | Room-v9-Baseline sichern | Technik: Schema v9 aus dem tatsächlichen Code reproduzieren; keine historischen Schemata erfinden. | Beta-Blocker | In Arbeit |
| DB02 | Datenbank & Suche | `Datenbank_Schemaexport` | Schemaexport und Migrationstest-Infrastruktur | Technik: Schemaexport reproduzieren und MigrationTestHelper ausführen; Schema-Dateien im Repository nachweisen. | Beta-Blocker | In Arbeit |
| DB03 | Datenbank & Suche | `Datenbank_KeinDestruktiverDowngrade` | Destruktiven Downgrade-Fallback entfernen | Technik: Downgrade/unbekannte Version testen; Tabellen dürfen nicht still gelöscht werden. | Beta-Blocker | In Arbeit |
| DB04 | Datenbank & Suche | `Datenbank_Migrationstests` | Migrationstests für bestehende Schemaübergänge | Technik: jede unterstützte Room-Migration mit Testdaten ausführen; Tabellen, FTS, Foreign Keys und Zeilenanzahl prüfen. | Beta-Blocker | Offen |
| DB05 | Datenbank & Suche | `Datenbank_FTS` | FTS-Trigger konsistent machen | App + Technik: Dokument erstellen/ändern/löschen und danach suchen; FTS-Index stimmt mit Daten überein. | Beta-Blocker | In Arbeit |
| DB06 | Datenbank & Suche | `Datenbank_Suchvalidierung` | Suchabfragen und Eingabevalidierung | App: leere, lange, ungültige und SQL-artige Suchbegriffe testen; keine Abfragefehler. | Beta-Blocker | In Arbeit |
| DB07 | Datenbank & Suche | `Datenbank_Dokumentlisten` | Schlanke Dokumentlisten-Projektion | App + Technik: Liste öffnen/filtern; nur benötigte Felder laden, Details bleiben korrekt. | Später | Offen |
| DB08 | Datenbank & Suche | `Datenbank_Paging` | Paging separat einführen | App: lange Liste scrollen, filtern und sortieren; keine Duplikate/fehlenden Einträge. | Später | Offen |
| DB09 | Datenbank & Suche | `Datenbank_REPLACESemantik` | `REPLACE`-Semantik auditieren und korrigieren | Technik: Update/Import auf bestehenden Primärschlüssel testen; abhängige Daten werden nicht ungewollt ersetzt. | Beta-Blocker | Offen |
| DB10 | Datenbank & Suche | `Datenbank_ForeignKeys` | Foreign-Key-Migration einzeln durchführen | Technik: Migration mit Foreign Keys ausführen; Integritätsprüfung danach erfolgreich. | Beta-Blocker | Offen |
| DB11 | Datenbank & Suche | `Finanzen_CentBetrag` | Geldbeträge als ganzzahlige Cent migrieren | App + Technik: Beträge addieren und über Migration wieder öffnen; Centwerte bleiben exakt. | Vor Aktivierung | Offen |
| DB12 | Datenbank & Suche | `Datenbank_SQLCipher` | SQLCipher für die vollständige Room-Datenbank umsetzen | App + Technik: DB als SQLCipher validieren; ohne Schlüssel nicht lesbar, kein Klartext-Fallback. | Beta-Blocker | Offen |
| DB13 | Datenbank & Suche | `Datenbank_SQLCipherMigration` | Bestehende Datenbank in SQLCipher migrieren | App + Technik: DB-Migration bei Abbruch/falschem Key testen; Original bleibt vollständig erhalten. | Beta-Blocker | Offen |
| DB14 | Datenbank & Suche | `Datenbank_Performance` | Listen-/Suchperformance messen | Technik: Listen-/Suchzeiten auf festem Datensatz messen; Optimierung, kein Beta-Blocker. | Später | Offen |
| R01 | Stabilität & Architektur | `Architektur_Charakterisierungstests` | Charakterisierungstests und Flow-Inventar | Technik: Scan-, Tresor- und Backup-Flows als Regressionstests dokumentieren; keine Produktänderung. | Später | In Arbeit |
| R02 | Stabilität & Architektur | `Architektur_DIEntscheidung` | DI-Technologie als ADR | Architektur: kurze DI-ADR mit Entscheidung und Konsequenzen; kein direkter UI-Test. | Später | Offen |
| R03 | Stabilität & Architektur | `Architektur_DIGrundlage` | DI-Grundlage für Datenbank und Repositories | Technik: DI-Starttest und Repository-Faketest; keine doppelte Datenbankinstanz. | Später | Offen |
| R04 | Stabilität & Architektur | `Sicherheit_DIKeySingletons` | Vault-/Lock-/Key-Singletons eindeutig verdrahten | App + Technik: Vault sperren/entsperren und Key-Leases testen; nur eine autoritative Instanz. | Beta-Blocker | In Arbeit |
| R05 | Stabilität & Architektur | `Architektur_ScannerImportViewModel` | Scanner-/Import-ViewModel isolieren | App: Scan-/Import-Ablauf vor/nach Extraktion gleich; Speichern, Abbrechen und Fehler prüfen. | Später | Offen |
| R06 | Stabilität & Architektur | `Architektur_DMSViewModel` | DMS-/Explorer-ViewModel isolieren | App: Dokumentliste/Filter/Sortierung/Navi prüfen; kein Feature-Verlust. | Später | Offen |
| R07 | Stabilität & Architektur | `Architektur_FinanceViewModel` | Finance-ViewModel isolieren | App: Finanzsummen und Monatswechsel vergleichen; keine Rechenänderung. | Später | Offen |
| R08 | Stabilität & Architektur | `Architektur_SyncP2PViewModel` | Sync-/P2P-ViewModel isolieren | App: Sync/P2P-Fortschritt, Abbruch und Fehler anzeigen; kein Erfolg vor Abschluss. | Später | Offen |
| R09 | Stabilität & Architektur | `Sicherheit_SecurityViewModel` | Security-/Lock-ViewModel isolieren | App: Sicherheitsaktionen nur nach erfolgreicher Authentifizierung; keine Schlüssel im UI-State. | Später | Offen |
| R10 | Stabilität & Architektur | `Architektur_LLMChatViewModel` | LLM-/Chat-ViewModel isolieren | App: Chat lädt, antwortet, bricht ab und zeigt Regel-Fallback korrekt. | Später | Offen |
| R11 | Stabilität & Architektur | `Sicherheit_SettingsViewModel` | Settings-ViewModel isolieren | App: Einstellungen ändern und App neu starten; Secrets erscheinen nicht in UI-State/Logs. | Später | Offen |
| R12 | Stabilität & Architektur | `Architektur_DashboardViewModel` | Dashboard-ViewModel isolieren | App: Dashboard mit Daten/leerem Zustand/Fehler laden; bestehende Widgets bleiben. | Später | In Arbeit |
| R13 | Stabilität & Architektur | `Einstellungen_DataStore` | SettingsRepository zu DataStore migrieren | App + Technik: alte Einstellungen migrieren; zweiter Start liefert dieselben Werte. | Später | Offen |
| R14 | Stabilität & Architektur | `Architektur_JSONStrategie` | JSON-Strategie festlegen | Architektur: JSON-Formate und Kompatibilitätsregeln dokumentieren; kein globaler Umbau. | Später | Offen |
| R15 | Stabilität & Architektur | `Architektur_JSONMigration` | Ein internes JSON-Format migrieren | Technik: genau ein internes JSON-Format roundtrip-/fehler-/kompatibilitätsgetestet. | Später | Offen |
| R16 | Stabilität & Architektur | `Datenschutz_LogRedaktion` | Zentrales, redigierendes Logging | Technik: synthetische Tokens/OCR-Texte/Dateinamen in Logs auslösen; sensible Werte sind redigiert. | Beta-Blocker | In Arbeit |
| R17 | Stabilität & Architektur | `Datenschutz_AnalyticsConsent` | Optionale Firebase-/Analytics-Initialisierung | App: ohne Google-Services-Konfiguration starten; Analytics erst nach Opt-in. | Vor Aktivierung | In Arbeit |
| R18 | Stabilität & Architektur | `Stabilitaet_UIBlockaden` | `runBlocking` in UI-Pfaden entfernen | App: Navigation/UI-Aktionen bei langsamen Operationen prüfen; Oberfläche bleibt bedienbar. | Vor Aktivierung | Offen |
| R19 | Stabilität & Architektur | `Stabilitaet_ServiceBlockaden` | `runBlocking` in Netzwerk-/Servicepfaden entfernen | Technik: Netzwerk-/Servicefehler und Abbruch simulieren; kein blockierter Thread/Job. | Vor Aktivierung | Offen |
| R20 | Stabilität & Architektur | `Stabilitaet_AppStart` | Startarbeit gezielt aus `Application.onCreate` verschieben | App: Kaltstart messen und Security-Initialisierung prüfen; keine unnötige Verzögerung. | Später | Offen |
| R21 | Stabilität & Architektur | `Architektur_ComposeAufteilung` | Eine große Compose-Datei gezielt teilen | App: betroffene Compose-Ansicht und alle Zustände durchgehen; keine visuelle Regression. | Später | Offen |
| R22 | Stabilität & Architektur | `Stabilitaet_PerformanceBaseline` | Performance-Baseline vor weiterem Refactoring | Technik: feste Start-/Scroll-/Such-Baseline reproduzierbar messen; kein Beta-Blocker. | Später | Offen |
| R23 | Stabilität & Architektur | `Wartung_Abhaengigkeiten` | Veraltete Dateien und Dependencies einzeln bereinigen | Technik: Build/Tests nach Entfernung einer einzelnen veralteten Datei/Dependency bestehen. | Später | Offen |
| R24 | Stabilität & Architektur | `Stabilitaet_BaselineProfile` | Baseline Profile erstellen und Vorher/Nachher vergleichen | Technik: Baseline Profile erzeugen und Vorher/Nachher auf gleichem Gerät vergleichen. | Später | Offen |

## Verbindliche Abschlussregel (praktisch, ohne Bürokratie)

Ein AP darf erst auf **„Umgesetzt / geprüft“** wechseln, wenn alle Punkte für seinen Umfang erfüllt sind:

1. **Änderung auf main:** Code oder Entscheidungstext ist tatsächlich vorhanden; der Commit ist notiert.
2. **Passender Test bestanden:** `./gradlew assembleDebug testDebugUnitTest` läuft auf genau diesem Stand. Für Schlüssel, Datenbank, Backup, Sync und native LLM-Funktionen zusätzlich die AP-spezifischen Negativ-/Integrationstests; ein erfolgreicher Build allein reicht nicht.
3. **App-Funktion manuell geprüft, wenn sie sichtbar/bedienbar ist:** Verwende die kurze Prüfung aus der Übersicht und dokumentiere Pass/Fail. Bei rein technischen APs ersetzt der reproduzierbare automatisierte Test den UI-Test.
4. **Fehler behoben und Nachweis auffindbar:** Der Commit und der passende CI-/Testnachweis müssen benannt sein. Ergebnis eines beta-relevanten End-to-End-Flows wird einmal pro Gate in `docs/release-beta-checklist.md` festgehalten – nicht als zusätzliche Zeile für jeden Micro-AP. Rein technische APs verweisen auf ihren konkreten Testlauf bzw. das bereits zuständige Technikdokument. Fehler oder nicht durchführbare Gerätetests bleiben offen; sie werden nicht als bestanden markiert.

Das kann keine mathematische Fehlerfreiheit garantieren. Es verhindert aber, dass ein Punkt nur wegen vorhandenen Codes, einer alten CI-Ausführung oder eines gelungenen Builds als abgeschlossen gilt. Die Release-Checkliste ist der kurze Nachweis für den Betatest; die 123 Prompts bleiben Arbeitsanweisungen, nicht 123 gleichzeitige Release-Blocker.


**Aktueller Prüfhinweis (25.10.2026):** Der LLM-Auditpfad speichert keine OCR-/Reasoning-Auszüge, Absender-/Dokumenttypwerte oder vollständigen Chatfragen. Nicht kalibrierte Confidence wird nicht mehr als Prozentwert an Firebase Analytics übertragen. Das Klassifikationsergebnis führt intern weiterhin `confidence = 0f` als „nicht verfügbar“-Sentinel; UI- und API-Anzeigen bleiben zu prüfen. CI für die letzten Änderungen: [Run 40137078831 erfolgreich](https://github.com/MrLongNight/myDocAnizer_Mobile/actions/runs/40137078831); der Folge-Commit ist noch nicht verifiziert.

## Arbeitspakete

# V – Tresor, Schlüsselableitung und Recovery

## V01 – V2-only Vault-Krypto-Inventar und Negativtests
**Prompt:** Inventarisiere ausschließlich den V2-Header, die V2-Argon2id-/AEAD-Ableitung, Unlock-/Recovery-Pfade, Biometrie, Keyfile/2FA, TOTP, `activeDek` und alle Aufrufer. Keine V1-Fixtures, V1-Deserialisierung, V1-Rückfalllogik oder Migration. Lege synthetische V2-Tests für falsches Passwort, ungültige Header und manipulierte verschlüsselte DEK-Daten an. **Abnahme:** nur V2 wird gelesen und geschrieben; ungültige/alte Formate scheitern geschlossen.

## V02 – Geprüfte Argon2id-Implementierung
**Abhängigkeit:** V01.  
**Prompt:** Verwende ausschließlich die versionsfixierte Argon2id-Implementierung im V2-Pfad. Für V2 gelten mindestens `m=19 MiB`, `t=2`, `p=1`; Einheiten und API-Semantik verifizieren. Begrenze/validiere Parameter vor der speicherintensiven Ableitung und schütze vor parallelen RAM-Spitzen. Füge bekannte Referenzvektoren und Negativtests hinzu. Entferne Alt-Ableitungspfade, die nur dem alten Header dienten. **Abnahme:** korrekte Referenzvektoren; ungültige/überzogene Parameter werden früh abgelehnt; kein V1-KDF-Pfad bleibt in der Tresorlogik.

## V03 – VaultHeader v2: Datenmodell und Parser
**Abhängigkeit:** V01, V02.  
**Prompt:** V2 ist das einzige unterstützte persistierte Vault-Format. Der typisierte Header samt strengem Parser/Validator muss Formatversion, KDF-/Algorithmus-ID, Parameter, Salt, IV/Nonce- und Taglängen, Vault-ID sowie Recovery-/Biometrie-/Faktor-Metadaten validieren. Unbekannte Versionen, abgeschnittene Daten, unmögliche Längen und ungültige Parameter müssen abgewiesen werden. Der Parser darf keine V1-Daten lesen oder rückwärtskompatible Defaults dafür einsetzen. **Abnahme:** V2-Roundtrips und Negativtests; keine V1-Referenztests oder V1-Lesepfade.

## V04 – Header-Authentifizierung und AAD
**Abhängigkeit:** V03.  
**Prompt:** Implementiere ausschließlich die AEAD-Operationen zum Verpacken/Öffnen des zufälligen 256-Bit-DEK im v2-Header. Nutze geprüfte AES-GCM/JCA- oder gleichwertige AEAD-Primitiven korrekt; pro Verschlüsselung neuen zufälligen Nonce/IV generieren und niemals wiederverwenden. Lege fest und teste, welche Vault-ID-, Versions- und KDF-Metadaten als AAD gebunden werden. Falscher Schlüssel, falscher AAD oder veränderter Tag müssen fehlschlagen, ohne Teilresultat auszugeben. Keine automatische Migration. **Abnahme:** gültiger Roundtrip; Manipulations- und Nonceformattests; keine geheime Materialausgabe in Logs.

## V05 – Optionale echte Zwei-Faktor-Absicherung (ARCHITEKTUR)
**Status:** Abschließend entschieden in `docs/security/adr-002-totp-decision.md`. Keine erneute Grundsatzprüfung. Für echten Zwei-Faktor-Modus gilt ausschließlich Passwort + zufällige 256-Bit-Schlüsseldatei; der Passwort-only-Modus bleibt separat verfügbar. TOTP ist kein kryptografischer Tresor-Faktor und darf den DEK-Unlock nicht freigeben oder blockieren.
## V06 – Zweiten Faktor technisch absichern und testen
**Abhängigkeit:** V05.  
**Prompt:** Implementiere ausschließlich den freigegebenen Passwort+Schlüsseldatei-Pfad mit etablierter Kryptografie. Die Schlüsseldatei muss zufällig und hochentropisch sein; Passwort und Datei müssen beide in den echten DEK-Wrapper eingehen. TOTP darf weder als Vault-Faktor ausgewertet noch mit dem Wrapper verbunden werden. Speichere keine TOTP-Secrets im V2-Header oder in SharedPreferences. Verbleibende TOTP-Hilfsfunktionen dürfen nur isolierte RFC-6238-Utilitytests bedienen, nicht die Tresorfreigabe. **Abnahme:** Negativtests für fehlenden/falschen Faktor; kein TOTP-Secret in neu geschriebenem Header; bestehende Klartextfelder werden beim Laden entfernt; Build und Tests müssen tatsächlich laufen.
## V06A – Optionalen Zwei-Faktor-Modus sicher konfigurierbar machen
**Abhängigkeit:** V05 und V06.  
**Prompt:** Implementiere die Konfiguration für den optionalen Zwei-Faktor-Modus. Standardmäßig bleibt er deaktiviert, bis der Nutzer ihn ausdrücklich einrichtet. Eine Aktivierung darf erst erfolgreich sein, wenn der zweite Faktor eingerichtet, ein Wiederherstellungsweg bestätigt und ein Test-Unlock erfolgreich war. Deaktivierung erfordert bestätigte Tresorentsperrung und muss die Schlüsselverpackung atomar aktualisieren. Kein Zwischenzustand darf den Nutzer aussperren oder den DEK unverschlüsselt speichern. **Abnahme:** Tests für Einrichtung, Abbruch, Neustart, ungültigen Faktor, Deaktivierung und Wiederherstellung.
## V06B – Optionalen zweiten Faktor an die echte DEK-Freigabe binden
**Abhängigkeit:** V05, V06 und V06A.  
**Prompt:** Binde den freigegebenen zweiten Faktor in den echten Vault-Unlock-/DEK-Freigabepfad ein. Ist die Funktion aktiviert, darf Passwort/erster Faktor allein den geschützten DEK nicht freigeben. Alle öffentlichen APIs, Hintergrunddienste und UI-Callbacks müssen denselben Gatekeeper verwenden. Der Recovery-Weg muss funktionieren, ohne heimlich den zweiten Faktor zu umgehen. **Abnahme:** End-to-End-Negativtests für fehlenden/falschen/abgelaufenen Faktor, korrekte Kombination, Lock/Unlock, Neustart und Recovery; die UI behauptet erst danach echte Zwei-Faktor-Absicherung.
## V09 – Recovery-Format und Wiederherstellungsoptionen festlegen (ADR)
**Prompt:** Definiere ein versioniertes Wiederherstellungskonzept für drei getrennte Wege: (1) Tresorpasswort **oder** Passkey; (2) optional zusätzlich konfigurierte Sicherheitsfragen für den Passwort-/Passkey-Pfad; (3) ein optionales, eigenständig nutzbares Notfalldokument mit eigenem hochentropischem Recovery-Geheimnis. Das Notfalldokument muss den Tresor ohne das ursprüngliche Passwort/Passkey wiederherstellen können; Sicherheitsfragen dürfen diesen unabhängigen Notfallweg nicht versehentlich blockieren. Erstelle klar erkennbare und unauffällige Druckvarianten. Definiere KDF, AEAD, AAD, Nonce, Salt, Version, Prüfsumme, Normalisierung, Fehlerbehandlung und Kompatibilität. Beurteile die Passkey-PRF-/Schlüsselverfügbarkeit bei Gerätewechseln; ein bloßer Passkey-Login ohne nutzbares Schlüsselmaterial reicht nicht. Sicherheitsfragen dürfen keine alleinige starke Verschlüsselung versprechen; geringe Entropie und Offline-Risiken müssen in UI und Tests berücksichtigt werden. Tarnung ist keine kryptografische Sicherheit. **Abnahme:** ADR deckt alle drei Wege, Wiederherstellung auf neuem Gerät, Verlustszenarien und Grenzen ab.

## V07 – BIP-39-Mnemonic-Validierung
**Abhängigkeit:** V09.
**Prompt:** Isoliere die BIP-39-Funktionalität und prüfe Wortliste (genau 2048 Wörter), Entropie, Checksumme und Mnemonic-Validierung anhand offizieller Referenzvektoren. Korrigiere ausschließlich Fehler in diesem Modul; wenn Eigenbau-Logik die Referenzvektoren nicht zuverlässig erfüllt, ersetze sie durch eine geprüfte, kompatible Bibliothek. Erzeuge nur synthetische Testdaten. **Nicht tun:** Shamir, PDF/QR oder Headerformat verändern. **Abnahme:** gültige/ungültige Mnemonics, Grenzfälle und offizielle Vektoren sind getestet.

## V08 – Shamir-Share-Konsistenz
**Abhängigkeit:** V09; die Nutzung von Shamir muss zum freigegebenen Recovery-Format passen.
**Prompt:** Prüfe den vorhandenen Shamir-2-aus-3-Code und seine Hashspeicherung. Stelle durch Tests sicher, dass die drei gehashten Shares exakt dieselben Bytefolgen sind, die später an den Nutzer ausgegeben werden, und dass jedes zulässige Paar den ursprünglichen synthetischen DEK reproduziert. Verwende offizielle oder klar bezeichnete Referenzvektoren. Ersetze den Eigenbau, falls er nicht belastbar gegen Referenzen besteht. **Nicht tun:** Recovery-PDF oder Verschlüsselungsformat in diesem Schritt ändern. **Abnahme:** jedes gültige Paar rekonstruiert exakt denselben Schlüssel; vertauschte, gekürzte und manipulierte Shares scheitern.

## V10 – Versionierten Recovery-Payload-Kern umsetzen
**Abhängigkeit:** V09.  
**Prompt:** Implementiere Encode/Decode für das freigegebene Recovery-Payload als isolierte Komponente. Erzeuge ein zufälliges, ausreichend hochentropisches Recovery-Geheimnis, das einen verpackten Tresorschlüssel freigeben kann; exportiere niemals den rohen DEK. Nutze etablierte KDF-/AEAD-Bibliotheken, versionsgebundene AAD, sichere Nonces, Salt, Längenlimits und Integritätsprüfung. Unterstütze zuerst den passwortbasierten Payload-Pfad; Passkey-PRF und Sicherheitsfragen werden separat in V16/V17 integriert. Falsches Geheimnis, manipulierte Bytes, ungültiger Tag, unbekannte Version oder abgeschnittener Payload müssen sicher scheitern. **Abnahme:** bytegenaue Roundtrips, Negativtests und keine Secrets in Meldungen/Logs.
## V11 – Standardkonforme QR-Kodierung
**Abhängigkeit:** V10.  
**Prompt:** Ersetze ausschließlich die derzeitige QR-Matrix-Erzeugung durch eine etablierte QR-Encoding-Bibliothek. Kodiert wird nur das freigegebene verschlüsselte Recovery-Payload, nie der Roh-DEK oder ein unverschlüsselter Schlüssel. Teste, dass ein unabhängiger QR-Decoder das Payload bytegenau zurückliest und beschädigte/zu große Inhalte kontrolliert behandelt werden. **Abnahme:** unabhängiger Decode-Roundtrip im Test; kein selbst gebautes Hash-Bit-Muster als QR-Code.

## V12 – Eindeutig gekennzeichnetes Notfalldokument
**Abhängigkeit:** V09–V11.  
**Prompt:** Erstelle die klar erkennbare Druckvariante mit Kennzeichnung als myDocAnizer-Mobile-Notfall-/Wiederherstellungsdokument. Sie enthält notwendige versionierte Daten inklusive des ausdrücklich erzeugten Recovery-Geheimnisses, Prüfsumme, Formatversion und verständlicher Anleitung. Der rohe DEK, Tresorpasswort und Sicherheitsfragen-Antworten dürfen nicht hinein. Sind Fragen eingerichtet, zeige deren Fragen und genaue Antwortregeln, nicht die Antworten. Warne beim Export, dass Personen mit Zugriff auf das Recovery-Geheimnis den Tresor möglicherweise wiederherstellen können. **Abnahme:** PDF-/QR-Roundtrip auf einem zweiten Gerät, Testdruck/Lesbarkeit, PDF-Text-/QR-Analyse und keine unbeabsichtigten Daten in Metadaten oder Logs.
## V12B – Unauffälliges Notfalldokument
**Abhängigkeit:** V09–V12.  
**Prompt:** Erzeuge dieselben kryptografischen Recovery-Daten wie V12, aber in neutraler Gestaltung ohne offensichtliche myDocAnizer-/Tresor-Bezeichnung. Die App muss das enthaltene, versionsgebundene Payload zuverlässig über Import/Scan erkennen; verwende keine schwer merkbaren Geheimregeln und keine externen Links. Die kryptografischen Daten und Zufallsentropie bleiben gleich sicher zur klaren Variante. Erkläre vor Export, dass Tarnung keine kryptografische Sicherheit bietet. Antworten auf Sicherheitsfragen und das Tresorpasswort werden nicht abgedruckt. **Abnahme:** Beide Varianten stellen denselben synthetischen Tresor wieder her; QR-/PDF-Parser, Export, Druck, Textlayer und Metadaten geprüft.

## V13 – Biometrie und Keystore getrennt absichern
**Abhängigkeit:** V04 und optionaler Faktorpfad V05–V06B.  
**Prompt:** Prüfe und sichere den Biometrie-/Keystore-Pfad: Biometrie ist eine lokale Freigabemethode, nicht automatisch ein portabler Wiederherstellungsschlüssel. Der DEK darf nur über den dokumentierten Gatekeeper freigegeben werden; aktivierte Zwei-Faktor-Regeln dürfen nicht durch Biometrie, Service/API-Aufruf oder UI-Callback umgangen werden. Gerätewechsel und Keystore-Invalidierung müssen einen getesteten Recovery-Pfad verwenden. **Abnahme:** Instrumentationstests für Biometrie, Ablehnung, Key-Invalidierung, Neustart und optional aktivierte 2FA.
## V14 – V2-only Laufzeit und vollständige Entfernung von V1
**Abhängigkeit:** V02–V04 und V07–V13.
**Prompt:** V2 ist das einzige Persistenz- und Laufzeitformat. Ersteinrichtung, Entsperren, Passwortwechsel, Biometrie, Keyfile/2FA und Recovery müssen V2 verwenden. Verbleibende TOTP-Hilfsfunktionen dürfen keinen Vault-Unlock beeinflussen und sind nicht Teil des V2-Faktorpfads. Entferne vollständig V1-Header, -Serialisierung, -Parser, -KDF-/Unlockpfade, Rückfalllogik, V1-Fixtures/-Tests und jede Staging-/Promotionslogik für eine Migration. Keine V1-Migration durchführen, vorbereiten oder als Fallback beibehalten. Alte V1-Daten werden nicht übernommen und dürfen vom Code nicht als Tresor erkannt werden. Beschädigte V2-Header müssen geschlossen fehlschlagen und dürfen nicht stillschweigend überschrieben oder als entsperrt behandelt werden. **Abnahme:** keine V1-Codepfade; V2-Funktionstests und vollständiger Build/Testlauf bestehen.

## V15 – Backup-Regeln für Vault-Secrets
**Prompt:** Prüfe Manifest-Backupregeln, Auto Backup, Device Transfer und alle Android-Backup-/Exportpfade. Schlüssel, Einstellungen im Klartext, TOTP-Secrets, Recovery-Secrets, Access-/Refresh-Tokens und Klartextdokumente dürfen nicht unbeabsichtigt in Systembackups gelangen. Nutzerinitiierte verschlüsselte Backupcontainer bleiben unterstützt. **Abnahme:** XML-/Manifest- und Backup-Regressionsprüfungen belegen, dass nur freigegebene verschlüsselte Artefakte aufgenommen werden.
## V16 – Wiederherstellung mit Passkey
**Abhängigkeit:** V09/V10 und das in V05 freigegebene Schlüsselkonzept.  
**Prompt:** Implementiere einen Passkey-basierten Restore-Pfad nur, wenn die echte Plattformunterstützung genügend kryptografisches Schlüsselmaterial für die Entschlüsselung liefert (z. B. unterstützte PRF-Funktion) und die Wiederherstellung auf einem Ersatzgerät praktisch nachgewiesen wurde. Ein gewöhnlicher Authentifizierungsnachweis oder ein lokaler, nicht exportierbarer Android-Keystore-Schlüssel ist kein portabler Entschlüsselungsschlüssel. Passwort-Fallback bleibt verfügbar; ungeeignete Geräte müssen vor Einrichtung verständlich erklärt werden. **Abnahme:** Enrollment, Restore auf anderem unterstützten Gerät/Authenticator, nicht unterstützte Geräte, verlorener Passkey und Passwort-Fallback getestet.

## V17 – Zusätzliche Sicherheitsfragen für Passwort-/Passkey-Restore
**Abhängigkeit:** V09/V10; Passkey-Kombination erst nach V16.  
**Prompt:** Ermögliche Nutzern optional eigene Sicherheitsfragen samt Antworten als zusätzliche Pflichtangabe beim Passwort- oder Passkey-basierten Restore. Antworten niemals im Klartext persistieren, loggen oder exportieren. Normalisierung (Unicode, Groß-/Kleinschreibung, Leerzeichen) muss stabil, versioniert und vor dem Einrichten erklärt werden. Die Antworten werden kryptografisch mit dem gewählten Passwort-/Passkey-Pfad kombiniert; Fragen allein dürfen keinen Tresor öffnen. Erkläre geringe Entropie und dass lokale Rate-Limits keine Offline-Versuche stoppen. Das eigenständige Notfalldokument aus V12/V12B bleibt ein separater Recovery-Weg und darf nicht unbemerkt zusätzlich die Sicherheitsfragen voraussetzen. Druckdokumente enthalten höchstens Fragen und Antwortregeln, niemals die Antworten. **Abnahme:** richtige/falsche Antworten, Normalisierung, Unicode, vergessene Fragen, manipuliertes Payload, Passwort-/Passkeypfad und keine Klartextantworten getestet.
## V18 – Wiederherstellungsoptionen verständlich auswählbar machen
**Abhängigkeit:** V10, V12, V12B, V16 und V17.  
**Prompt:** Baue die Einstellungs- und Restore-Oberfläche für die gewählten Optionen: Tresorpasswort oder Passkey; optionale Sicherheitsfragen als Zusatz für diesen Pfad; optionales, eigenständig nutzbares Notfalldokument in klarer oder unauffälliger Gestaltung. Zeige Voraussetzungen, unterstützte Geräte, Folgen bei Verlust und verlange einen erfolgreichen Test-Restore, bevor die Konfiguration aktiv gilt. Erkläre in einfacher Sprache, dass das gedruckte Notfalldokument selbst ein hochsensibles Wiederherstellungsgeheimnis enthält und die unauffällige Gestaltung keine Verschlüsselung ersetzt. Keine Option darf einen unsicheren Bypass oder falsche Erfolgsmeldung ermöglichen. **Abnahme:** UI-/Robolectric-Tests für Auswahlkombinationen, Geräte ohne Passkey-Unterstützung, Abbruch, Fehlkonfiguration und Restore.
# S – LockManager, sichere Sitzung und SecretStore

## S01 – Lock- und Secret-Inventar (AUDIT)
**Prompt:** Inventarisiere alle App-/Vault-Lock-Flags, `unlockApp`, `unlockVault`, `lockVault`, automatische Sperrung, Biometrie, `activeDek`-Zugriffe und geheime Werte in Prefs/DB/Dateien. Zeichne den bestehenden Zustandsfluss mit Aufrufstellen auf und schlage eine eindeutige Trennung bzw. Beziehung zwischen App-Lock und Vault-Lock vor. Noch keine produktive Änderung. **Abnahme:** Datei `docs/security/lock-secret-inventory.md`; jeder gefundene Geheimnisspeicher und jeder Unlock-Pfad ist referenziert.

## S02 – LockManager als Zustandsautomat
**Abhängigkeit:** S01.  
**Prompt:** Implementiere einen testbaren `LockManager` mit typisiertem, zentralem Zustand und ausdrücklich erlaubten Übergängen. Beginne beim Prozessstart fail-closed, wenn ein Vault eingerichtet ist. Activity/ViewModel dürfen keine konkurrierenden Wahrheits-Flags mehr für denselben Vault-Lock erzeugen; halte vorhandene APIs über Adapter kompatibel. Noch keine automatische Hintergrundsperre und keine SecretStore-Migration. **Abnahme:** reine Unit-Tests der Zustandsübergänge; kein unkoordinierter zweiter Manager.

## S03 – Auto-Lock-Lifecycle
**Abhängigkeit:** S02.  
**Prompt:** Verbinde den LockManager mit `ProcessLifecycleOwner` und der vorhandenen Auto-Lock-Einstellung. Messe Zeit mit `SystemClock.elapsedRealtime`; definiere und teste `0` als sofortige Sperre entsprechend der Produktsemantik. Bei Rückkehr aus dem Hintergrund muss vor Anzeige sensibler Inhalte der aktuelle Lockzustand abgefragt werden. Keine Speicherung von DEK oder Passphrase, um die Rückkehr zu beschleunigen. **Abnahme:** Tests für ON_STOP/ON_START, Timeoutgrenze und Prozessneustart; keine UI-Freigabe vor Lockprüfung.

## S04 – Unlock-Bypässe entfernen
**Abhängigkeit:** S02 und V13.  
**Prompt:** Verfolge `AppLockScreen`, `unlockApp()` und alle Aufrufer. Entferne Pfade, die ohne erfolgreiches, überprüfbares Authentifizierungsresultat entsperren – insbesondere Null-Activity-/Boolean-Default-/UI-only-Pfade. Erfolg muss aus dem tatsächlich erfolgreichen Passwort-/Vault- oder `BiometricPrompt.CryptoObject`-Vorgang kommen. Ändere nur Authentifizierungsübergänge und ergänze Negativtests. **Abnahme:** kein erfolgreicher Unlock bei abgebrochener, fehlgeschlagener oder fehlender Authentifizierung.

## S05 – DEK-Leases an Lockstatus binden
**Abhängigkeit:** V04 und S02.  
**Prompt:** Ersetze unkontrollierte öffentliche DEK-Getter schrittweise durch eine begrenzte `withUnlockedKey`-/Key-Lease-Schnittstelle oder ein gleichwertig überprüftes Modell. Schlüsselmaterial darf nur während gültigem Unlock-Zustand genutzt werden; mutable Kopien soweit technisch möglich nach Gebrauch bereinigen. Synchronisiere zusammengesetzte Operationen mit `Mutex`/gleichwertiger Koordination. Definiere, was bei Lock während einer Dateioperation geschieht. Migriere nur die Aufrufstellen, die für den Build dieses Schritts nötig sind, und liste verbleibende Verbraucher auf. **Abnahme:** Tests, dass gesperrter Vault keine Entschlüsselung erlaubt und paralleler Lock/Unlock keinen alten Schlüsselzugriff hinterlässt.

## S06 – Fehlversuche und Backoff
**Abhängigkeit:** S02.  
**Prompt:** Prüfe die vorhandene Backoff-Implementierung und entferne TOTP aus aktiven Vault-Unlock-/Backoff-Pfaden: TOTP ist kein Vault-Faktor. Der Backoff muss nur die tatsächlich aktiven Passwort-/Passwort+Schlüsseldatei- und freigegebenen Recovery-Pfade absichern, ohne andere Authentifizierungswege zu umgehen. Schreibe persistent und atomar die notwendigen Zähler/Deadline-Informationen; nutze monotone Zeit während einer Sitzung und definiere konservatives Verhalten nach Prozess-/Geräteneustart. Teste die festgelegte Folge: nach fünf Fehlversuchen 30 Sekunden, danach 1, 5 und 15 Minuten. **Abnahme:** Grenzwerte, parallele Fehlversuche, Neustart und Restzeit getestet; Dokumentation, dass lokaler Backoff Defense-in-Depth ist.

## S07 – FLAG_SECURE und sensitive Screens
**Prompt:** Setze `FLAG_SECURE` rechtzeitig vor dem Rendern sensibler Inhalte und steuere Debug-Ausnahmen ausschließlich über ein explizites Build-Flag bzw. `BuildConfig.DEBUG`, nicht über Emulatorerkennung. Sorge dafür, dass Recents/Screenshots und offene Detailansichten nach Sperrung keine sensiblen Inhalte mehr zeigen. Keine Navigation oder Sicherheitsarchitektur nebenbei refactoren. **Abnahme:** dokumentierter manueller Testplan für Screenshot, Recents, App-Hintergrund, Activity-Neustart und Lock auf einer offenen Detailseite.

## S08 – SecretStore-Grundkomponente
**Prompt:** Baue einen testbaren `SecretStore` für langlebige Geheimnisse. Verwende AES-GCM mit einem nicht exportierbaren Android-Keystore-Key, eindeutiger Nonce, Formatversion und Integritätsprüfung. Dokumentiere die Authentifizierungsbindung des Keys und API-Level-Fallbacks. Ungültiger Tag oder Keystore-Fehler darf niemals Klartextfallback auslösen. In diesem Schritt nur API und Kernkomponente samt synthetischen Tests erstellen; bestehende Prefs noch nicht migrieren. **Abnahme:** Roundtrip, falscher Tag, fehlender/invalidierter Key und Fehlerpfade getestet.

## S09 – Legacy-Secrets verlustfrei migrieren
**Abhängigkeit:** S08.  
**Prompt:** Migriere alle in S01 konkret aufgelisteten und durch den Code-Audit gefundenen Klartext-Secrets in den SecretStore. Der Audit hat zusätzlich `cloud_sync_webdav_pass` als Klartextpräferenz identifiziert. Pro Wert: erkennen, verschlüsselt schreiben, entschlüsselten Roundtrip verifizieren und erst danach Original entfernen. Migration muss idempotent sein und bei Fehler den ursprünglichen Wert erhalten, ohne unkontrollierte doppelte Klartextkopien zu hinterlassen. `sync_master_password` erst entfernen, wenn die in D/P/C betroffenen Verbraucher nachweislich umgestellt sind; sonst nur dokumentierte Zwischenstufe mit klarer Restabhängigkeit. **Abnahme:** erster Start, zweiter Start, Prozessabbruch und Keyfehler getestet. Instrumentationstests müssen insbesondere idempotente Wiederinitialisierung und den Erhalt des Legacy-Werts bei fehlgeschlagener SecretStore-Schreiboperation nachweisen.

## S10 – Externe Navigationsextras validieren
**Prompt:** Prüfe die exportierte `MainActivity` sowie Widget-/Intent-Aufrufstellen. Validiere `EXTRA_START_TAB`, `EXTRA_OPEN_ACTION`, URIs und weitere externe Extras gegen feste Whitelists. Während der Vault gesperrt ist, dürfen externe Aktionen höchstens sicher zwischengespeichert und erst nach erfolgreichem Unlock verarbeitet werden. Ungültige Extras ignorieren und nicht später wiederverwenden. **Abnahme:** Tests für gültige, unbekannte, fehlerhafte und während Lock eintreffende Extras; kein Lock-Bypass.

**Umsetzung direkt auf `main`:** `ExternalIntentPolicy.consume()` liest nur unterstützte Navigationswerte aus, verwirft unbekannte Extras und entfernt anschließend alle Extras, `data` und `clipData` aus dem Intent. `MainActivity` konsumiert den Payload **vor** der Lock-/Unlock-Prüfung; bei gesperrtem Zustand bleibt daher kein externer Befehl zur späteren Wiederholung an der Activity hängen. Ein Deep-Link-/URI-Schema wird derzeit nicht unterstützt und wird verworfen. `ExternalIntentPolicyInstrumentedTest` deckt gültige Werte, unbekannte Extras, ungültige Werte, falsch typisierte Boolean-Extras und das Entfernen von URI-/ClipData ab. Der Nutzer bestätigt erfolgreichen Build, `:app:testDebugUnitTest` und App-Start im Streaming-Emulator. Ein separater `connectedDebugAndroidTest`-Lauf sowie ein gezielter Widget-Cold-Start-/Lock-Replay-Test wurden in der Rückmeldung nicht ausdrücklich benannt und bleiben als spezifische Abnahme offen.
---
# D – Dokumentdateien, Index, Backup und Restore

## D01 – Datenfluss-Inventar (AUDIT)
**Prompt:** Verfolge lesend sämtliche PDF-/Bild-/Scan-/OCR-/Viewer-/Share-/Export-/P2P-/Cloud-/Backup-/Restore-/Cachepfade sowie Speicherorte und Datenbankfelder mit sensiblen Metadaten. Markiere pro Pfad, ob Inhalt/Index/Metadaten derzeit in Klartext oder verschlüsselt vorliegen und welcher Aufrufer `isEncrypted`/`passwordKey` steuert. Keine Produktänderung. **Abnahme:** `docs/security/document-data-flow.md` mit Pfad- und Klassenbelegen, inklusive benutzerdefinierter Speicherorte.

## D02 – Vollständige Datenbankverschlüsselung (SQLCipher) technisch festlegen
**Produktentscheidung:** vollständige Datenbankverschlüsselung ist gewählt.  
**Prompt:** Prüfe den tatsächlichen AppDatabase-/Room-2.7-Aufbau, Öffnungspfade, FTS, WAL/SHM, Export/Restore und Versionskatalog. Prüfe aktuelle SQLCipher-for-Android-Integration auf Room-Kompatibilität, minSdk 24, native ABIs, 16-KB-Kompatibilität, Wartung und Lizenz. Erstelle eine technische ADR mit konkreter Bibliotheksversion, Schlüsselbereitstellung, FTS-/WAL-Verhalten, Migration, Tests und Rollback. Feldverschlüsselung ist nicht der gewählte Ersatz. **Abnahme:** nachweislich kompatibler Weg für vollständig verschlüsselte Room-Datenbank; kein Rückfall auf unverschlüsseltes SQLite.
## D03 – Gemeinsame Schlüsselarchitektur für DB, Dokumente, Settings und Backups
**Abhängigkeit:** V04 und D02.  
**Bisher umgesetzt:** `CryptoKeyDerivation` stellt HKDF-SHA-256 und zweckgebundene Ableitungskontexte für Datenbank, Dokument-Streams, Einstellungen und Backupcontainer bereit. RFC-5869-Referenztest sowie Tests auf Vault-/Purpose-Isolation und ungültige Eingaben wurden ergänzt.  
**Zusätzlich umgesetzt (Testlauf offen):** `VaultKeyProvider` leitet einen zweckgebundenen Schlüssel ausschließlich innerhalb eines `VaultSecurityManager.withUnlockedKey`-Leases ab und überschreibt den abgeleiteten Schlüssel auch bei Callback-Fehlern. Isolierte Tests decken Purpose-/Vault-Isolation, gesperrten Tresor und Zeroing ab. Noch keine Verbraucher umgestellt.  
**Noch offen; D03 ist ausdrücklich nicht abgeschlossen:** zufälligen DB-Schlüssel sicher verpacken/entpacken; portable Recovery und Schlüssel-Lebenszyklus über Sperren/Entsperren hinweg; vollständige Integrationstests ohne Schlüsselwerte in Logs. Bestehende `DocumentStorageService`-PBKDF2-String-Overloads und Dateiformate bleiben vorerst unverändert, damit keine vorhandenen Dateien unlesbar werden. Keine Verbraucher auf die neue Ableitung umstellen, bevor Formatversionierung und Migration freigegeben sind.  
**Prompt:** Implementiere eine testbare CryptoService-/KeyProvider-Schnittstelle mit klarer Schlüsselhierarchie und Domain-Separation für SQLCipher, Dokument-Streams, Einstellungen und Backupcontainer. Verwende geprüfte HKDF-/AEAD-APIs; Schlüssel an Vault-ID und Zweck binden. Die DB erhält einen zufälligen Schlüssel, geschützt über die Vault-Schlüsselarchitektur; niemals das Nutzerpasswort direkt als SQLCipher-Key verwenden. Definiere Sperrverhalten, flüchtige Schlüssel und portable Wiederherstellung. Keine neuen userPasswordKey-/syncPasswordKey-String-Fallbacks. **Abnahme:** RFC-5869-Referenzvektoren, Purpose-Isolation, Recovery-Verhalten und keine Schlüsselwerte in Logs; DB-/Dokumentformate werden erst in nachgelagerten APs migriert.
## D04 – Streaming-Dateiformat für neue Dokumente
**Abhängigkeit:** D03.  
**Prompt:** Implementiere versioniertes Streaming-AEAD-Dateiformat für neue Dokumente. Authentifiziere Dokument-ID, Vault-ID, Formatversion und notwendige Metadaten als AAD; verwende eine geprüfte Bibliothek und dokumentierte Segmentierung um 1 MiB. Kein vollständiger Dateiinhalt im RAM. Jede persistente Vorschau/OCR-Ausgabe, die Dokumentinhalt enthält, muss verschlüsselt abgelegt oder als kurzlebige geschützte Arbeitskopie behandelt werden. **Abnahme:** große Dateien, Abbruch, falscher Kontext, Beschädigung und abgeschnittener Stream.
## D05 – secureSave auf verschlüsselte Ablage umstellen
**Abhängigkeit:** D03/D04.  
**Prompt:** Stelle alle relevanten secureSave-Pfade auf das verschlüsselte Dokumentformat um. Vor erfolgreichem AEAD-Abschluss darf keine Zieldatei als gültig sichtbar werden. Kein Schreiben in öffentliche Ordner oder persistente unverschlüsselte Zweitkopie ohne ausdrücklichen Nutzerexport. Leser werden nur über einen klaren Adapter versorgt. **Abnahme:** Save/overwrite/abgebrochener Save, Prozessabbruch, falscher Schlüssel und Wiederöffnen getestet.
## D06 – Viewer, OCR, Share und Vorschau an Verschlüsselung anbinden
**Abhängigkeit:** D04/D05.  
**Prompt:** Prüfe Datenfluss durch Viewer, OCR, Vorschau/Thumbnail, Suche, Teilen/Export und temporäre Dateien. Persistente Inhalte, OCR-Text, Suchindizes und Vorschaubilder müssen verschlüsselt sein; notwendige Klartextarbeitskopien nur kurzlebig, app-privat und mit verlässlichem Cleanup. Teilen/Export ist ein ausdrücklicher Nutzerexport mit verständlicher Warnung, kein stiller Klartext-Mirror. **Abnahme:** Datenfluss-/Regressionstests für jeden Pfad, Prozessabbruch und Cleanup.
## D07 – Verschlüsselungsstatus aus tatsächlicher Speicherung ableiten
**Abhängigkeit:** D04–D06.  
**Prompt:** UI und Modelle leiten den Status aus Dateiformat, Authentifizierungsprüfung und Speicherort ab, nicht aus einem frei gesetzten Flag. Fehlende Schlüssel, unbekannte Formate und beschädigte Dateien gelten nicht als frei lesbare Dokumente. **Abnahme:** Tests für verschlüsselt, Legacy, fehlenden Schlüssel, manipulierten Header und widersprüchliche Metadaten.
## D08 – Resumierbare Migration vorhandener Dokumente
**Abhängigkeit:** D04–D07.  
**Prompt:** Implementiere wiederaufnehmbaren WorkManager-Migrationsjob. Pro Datei Original lesen, verschlüsselte temporäre Datei erstellen, durch Entschlüsseln und Hash-/Längenvergleich verifizieren und erst dann atomar committen. Original vor erfolgreicher Verifikation erhalten. Abbruch und Wiederholung dürfen keine unverschlüsselten Duplikate oder inkonsistente Status erzeugen. Während der Entwicklung ist ein dokumentierter Datenreset zulässig; vor Produktivfreigabe ist ein nachvollziehbarer Upgrade-/Rollback-Pfad erforderlich. **Abnahme:** Abbruch in jeder Phase, Wiederholung, wenig Speicher und Fehler ohne Datenverlust.
## D09 – Kollisionssichere Ablage
**Prompt:** Stelle die interne Dateiablage auf kryptografisch zufällige IDs/UUIDs um, ohne Titel/Absender als Dateisystem-Sicherheitsgrenze zu nutzen. Zielanlagen müssen Create-new-/atomare Semantik verwenden und bestehende Zieldateien nie still überschreiben oder vorab löschen. Anzeigenamen bleiben Metadaten. Ändere nur die zentrale Ablagekomponente und die zwingend nötigen Aufrufer. **Abnahme:** Tests für gleiche Namen, parallele Imports, vorhandenes Ziel und Abbruch nach Teilwrite.

## D10 – Vollständig verschlüsselter Backupcontainer für alle Nutzdaten
**Abhängigkeit:** D03/D04 und SQLCipher-Aufbau aus DB12.  
**Prompt:** Implementiere versionierten streamenden AEAD-Backupcontainer mit verschlüsselter Datenbank, allen wiederherzustellenden App-Einstellungen, Dokumenten, OCR-/Suchdaten und nötigen nicht gerätegebundenen Recovery-Metadaten. Manifest, Dateinamen und sensible Metadaten innerhalb des Containers verschlüsseln. Keine Klartext-ZIP-/JSON-/Indexzwischenablage. Android-Keystore- und Google-OAuth-Zugangsdaten bleiben gerätegebunden bzw. werden beim Restore neu autorisiert; nicht in den Backupcontainer aufnehmen. **Abnahme:** kompletter Restore in frischer Installation, große Dateien ohne OOM, Integritätsfehler, falscher Schlüssel, abgeschnittener Container und unbekannte Version.
## D11 – Restore: Archive und Pfade validieren
**Abhängigkeit:** D10.  
**Prompt:** Implementiere ausschließlich die Vorabvalidierung beim Restore. Normalisiere Pfade und erzwinge, dass jeder Eintrag kanonisch im Zielverzeichnis verbleibt. Lehne absolute Pfade, `..`, Separatorvarianten, kodierte Traversalversuche, doppelte IDs, übergroße Einträge, zu viele Einträge und unzulässige Kompressionsverhältnisse ab. Daten nicht vor Abschluss der Validierung in die produktive Datenbank übernehmen. **Abnahme:** Zip-Slip-/Zip-Bomb-/Traversal- und Größenbegrenzungstests.

## D12 – Restore: atomarer Commit und verständlicher Report
**Abhängigkeit:** D11.  
**Prompt:** Sorge dafür, dass Restorevalidierung und Schreiben transaktional bzw. über einen Staging-Bereich erfolgen. Ein beschädigter AEAD-Tag, fehlendes Manifest oder fehlerhafte Metadaten darf keinen Teilimport in der Produktivdatenbank hinterlassen. Vermeide blindes `REPLACE`; definiere Konfliktregeln über neue IDs/Merge. UI erhält `wiederhergestellt / übersprungen / fehlgeschlagen` plus nicht sensitive Ursachen. **Abnahme:** erzwungene Fehler in jeder Phase hinterlassen keinen inkonsistenten Zwischenstand.

## D13 – Unverschlüsselte Mirrors entfernen und Backupregeln prüfen
**Abhängigkeit:** D05–D12.  
**Prompt:** Suche und entferne produktive Klartext-Mirrors, Fallbacks, Cachekopien und Backup-Pfade, die Verschlüsselung umgehen. Prüfe backup_rules, data_extraction_rules, Manifest, Auto Backup und Device Transfer. Nur ausdrücklich vom Nutzer exportierte Dateien dürfen als Klartext außerhalb des Tresors landen. **Abnahme:** Code-/Dateisuche und Backup-/Restore-Regressionstests weisen keine unbeabsichtigte dauerhafte Klartextkopie nach.
## D14 – PDF-Textlayer und OCR-Ausgaben vollständig prüfen
**Abhängigkeit:** D06/D13.  
**Prompt:** Belege anhand synthetischer Dokumente, dass PDF-Textlayer, OCR-Ausgaben und Suchindizes nur innerhalb der verschlüsselten Persistenz verbleiben. Ein PDF darf einen Textlayer haben, solange die PDF-Datei selbst verschlüsselt gespeichert wird; bei explizitem Nutzerexport ist das Verhalten klar. Keine Recovery-Secrets oder Tresor-Schlüssel in Textlayer einbetten. **Abnahme:** PDF-/OCR-Tests, Text-Extraktion und Dateisystemkontrollen; Geräte-/Instrumentationstests getrennt dokumentieren.
## D15 – Alle App-Einstellungen verschlüsselt speichern
**Abhängigkeit:** D03 und S08; vor vollständigem Backup-Restore mit D10 koordinieren.  
**Prompt:** Inventarisiere DataStore, SharedPreferences, Settings-Repositories, lokale JSON-Dateien und weitere Persistenz von App-Einstellungen. Verschlüssele sämtliche dauerhaft gespeicherten Einstellungen app-seitig über einen zentralen getesteten Pfad; Klartextdateien dürfen nicht neben der verschlüsselten Ablage entstehen. Secrets wie OAuth-Tokens gehören in den SecretStore. Lokale Entschlüsselung darf bequem sein, aber Backup-Wiederherstellung muss ohne alten, nicht portablen Keystore-Schlüssel möglich sein; deshalb Einstellungen im D10-Backup zusätzlich mit dem tragbaren Recovery-/Backup-Schlüssel verschlüsseln. **Abnahme:** Persistenzinventur, Klartextsuche in Dateien/Backups, falscher/fehlender Schlüssel, Neustart und vollständiger Restore.

# P – P2P: TLS, Pairing, Server und Transfers

## P01 – Sicherheits-Audit aller Verbindungsarten und Transport-ADR
**Produktanforderung:** App2App, PC2App und Browser2App sollen sicher unterstützt werden.  
**Prompt:** Prüfe im echten Code jeden End-to-End-Pfad für App2App, PC2App und Browser2App: Client, ServerSocket/HTTP-Parser, Ports, WLAN/LAN, TLS, Zertifikats-/Fingerprintprüfung, Pairing, Authentifizierung, Upload/Download, Sessionverwaltung, CORS/Origin, Browserzugriff und Fallbacks. Erstelle eine Matrix pro Verbindung mit vorhandener Verschlüsselung, Identitätsprüfung, Replay-Schutz und Negativtests. Entwirf einen gepflegten Transport-/Pairing-Ansatz. Browser-Zertifikatsvalidierung muss ohne Warnungsumgehung funktionieren; plane keine Lösung, die Nutzer zum Ignorieren einer Zertifikatswarnung zwingt. Noch keine Protokollimplementierung. **Abnahme:** belegte Ist-Matrix, Risiken und umsetzbarer Zielentwurf; kein „sicher“ allein aufgrund vorhandener TLS-Klassen.
## P02 – TLS für alle Server-Endpunkte verbindlich machen
**Abhängigkeit:** P01, geprüfter Transportentwurf.  
**Prompt:** Implementiere TLS auf allen Serverendpunkten für App2App, PC2App und Browser2App. Kein produktiver Socket darf auf Klartext zurückfallen, wenn TLS oder Zertifikatsprüfung fehlt/fehlschlägt. Dokumentiere Zertifikatsbereitstellung und Erneuerung ohne Warnungsumgehung. **Abnahme:** Integrationstests beweisen TLS auf jedem Endpunkt und Ablehnung unverschlüsselter Verbindungen; Release-Build enthält keinen Klartext-Fallback.
## P03 – Native App- und PC-Clients: TLS und Identitätsprüfung
**Abhängigkeit:** P02.  
**Prompt:** Stelle App2App- und PC2App-Clients auf TLS um. Die Gegenstelle muss beim Pairing über einen vertrauenswürdigen Kanal authentifiziert werden; unbekannte/gewechselte Zertifikate dürfen nicht still akzeptiert werden. Prüfe gültiges/fremdes Zertifikat, Zertifikatswechsel, Re-Pairing und fehlende TLS-Unterstützung. Browser-Sicherheit nicht durch native App-Pinning-Tests ersetzen. **Abnahme:** kein Klartext-Fallback und Negativtests für alle unterstützten nativen Clientpfade.
## P04 – Sicherer Pairing-Handshake für alle Verbindungstypen
**Abhängigkeit:** P01/P03 und S08 soweit gemeinsame Pairing-Secrets betroffen sind.  
**Prompt:** Implementiere ein geprüftes Pairingverfahren für App2App, PC2App und Browser2App. Verwende gepflegte PAKE-Bibliothek oder korrekt verifiziertes ephemeres ECDH mit authentifiziertem Short Authentication String, abhängig vom Zielentwurf. Keine selbst erfundene Kryptografie und keine statische PIN als alleinige Authentifizierung. Pairingcode einmalig und kurzlebig; Rate-Limits und SecretStore verwenden. Browser muss mit echten Browser-Sicherheitsgrenzen funktionieren. **Abnahme:** erfolgreicher Pairing-Roundtrip sowie falsche, abgelaufene und wiederverwendete Codes und Identitätsverwechslung getestet.
## P05 – HTTP-Parsing und Ressourcenlimits
**Abhängigkeit:** P01.  
**Prompt:** Prüfe, ob eine gepflegte HTTP-Serverbibliothek mit minSdk und TLS-Architektur kompatibel ist. Nutze sie, wenn dadurch kein neues Sicherheitsproblem entsteht; andernfalls härte den bestehenden Parser eng begrenzt. Setze Limits für Request-Line, Headeranzahl/-größe, Body, Read-/Idle-Timeouts, Verbindungen und parallele Jobs. Vor Authentifizierung höchstens 4 KiB Body. Unerwartetes `Transfer-Encoding`, widersprüchliche Längen und abgeschnittene Requests ablehnen. **Abnahme:** Tests für oversized header/body, Slowloris-/Timeoutfall und ungültige Content-Length.

## P06 – Dokumente über alle Verbindungstypen streamend übertragen
**Abhängigkeit:** D04, P03–P05.  
**Prompt:** Migriere Upload-/Download-Payloads für App2App, PC2App und Browser2App auf Streaming. Keine Gesamtdatei als ByteArray, keine dauerhafte unverschlüsselte Zwischenkopie. Nur vollständig authentifizierte und validierte Dateien dürfen in Datenbank/Manifest aufgenommen werden. **Abnahme:** große synthetische Dateien, Abbruch, gekürzter Body, falscher Hash/AEAD-Tag und alle drei Transportpfade getestet.
## P07 – Replay-Schutz, Sessionablauf und Rate-Limits
**Prompt:** Ergänze den P2P-Authpfad um einen thread-sicheren Nonce-Replay-Cache pro gekoppeltem Gerät mit TTL. Wiederverwendete Nonces ablehnen; Zeitstempel allein zählen nicht als Replay-Schutz. Kopplungs-/Authfehler pro IP/Gerät begrenzen und Sessions nach 15 Minuten Inaktivität beenden. Bearer-Tokens ausschließlich im Authorization-Header. **Abnahme:** parallele Wiederholung, abgelaufene Session, viele falsche Codes und fehlende/abgelaufene Nonce getestet.

## P08 – IP-CIDR und Schnittstellenbindung
**Prompt:** Ersetze String-Präfixprüfungen durch eine echte IPv4-/IPv6-CIDR-Prüfung. Behandle IPv4 privat korrekt (`10/8`, `172.16/12`, `192.168/16`) sowie IPv6 ULA `fc00::/7`; Link-local `fe80::/10` nur wenn der konkrete Anwendungsfall es benötigt. Binde den Server an die ausgewählte WLAN-Schnittstelle; Loopback nur im Debug-Modus. **Abnahme:** Tests für Grenzen, fremde Netze, IPv6, WLAN-Wechsel und Interface-Verlust.

## P09 – Sichere Upload-Ziele
**Abhängigkeit:** P06.  
**Prompt:** Erzeuge Dateiziele ausschließlich intern aus zufälligen IDs. Originalnamen nur als validierte Metadaten speichern und `Content-Disposition` sicher verarbeiten. Schreibe in eine zufällige temporäre Datei, prüfe Größe und Integrität und committe atomar zu einem neuen Ziel. Niemals bestehende Dateien vorab löschen/überschreiben. **Abnahme:** Tests für `/`, `\\`, `..`, URL-Encoding, doppelte Namen, Kollision und Fehler während Teilwrite.

## P10 – Schwache Kryptofallbacks entfernen
**Prompt:** Suche ausschließlich in P2P-/Sync-Krypto nach Fallbacks wie `encryptString`, die aus ungültigen Schlüsselstrings durch SHA-256 oder ähnliche Heuristik einen Ersatzschlüssel erzeugen. Ungültige Key-/Nonce-/Payloadformate müssen Fehler auslösen, nicht auf schwächere Verschlüsselung wechseln. Migriere nur nachgewiesene Aufrufer auf die geprüften APIs; kein globales Refactoring. **Abnahme:** Negativtests für ungültigen Schlüssel und kein unsicherer Fallbackpfad.

## P11 – Serverlebenszyklus und Android-Berechtigungen
**Prompt:** Entferne `runBlocking` nur in den P2P-Request-/Serverpfaden durch strukturierten Coroutine-Lebenszyklus. Bei Serverstop müssen Jobs, Sockets und Streams zuverlässig beendet werden. Prüfe nötige Foreground-Service-Typen/Berechtigungen und aktuelle Laufzeitlimits der Ziel-API; Service nur während aktiver Aufgabe mit sichtbarer Benachrichtigung und Stoppmöglichkeit. Entferne WLAN-/Multicastberechtigungen nur nach Nutzungsprüfung. **Abnahme:** Stop/Cancel/Netzverlusttests; keine hängen gebliebenen Jobs oder unbegründeten Berechtigungen.

## P12 – End-to-End-Sicherheitstests für App2App, PC2App und Browser2App
**Abhängigkeit:** P02–P11.  
**Prompt:** Erstelle echte Client-/Server-Integrationstests für jeden der drei Verbindungstypen. Teste Upload/Download, Pairing, Zertifikatswechsel, Replay, Sessionablauf, falsche Zugangsdaten, Rate-Limits, oversized Requests, Pfad-Traversal, Zielkollision, Verbindungsabbruch und keinen Klartext-Fallback. Browser2App auf realen unterstützten Browsern prüfen, insbesondere Zertifikatsvertrauen, Origin/CSRF und CORS/Private-Network-Regeln. Trenne Unit-, Integration-, Emulator- und reale Gerätetests. **Abnahme:** Testmatrix pro Verbindungstyp; keine pauschale Sicherheits-/Kompatibilitätsbehauptung ohne Befund.

## P13 – Browser2App sicher und ohne Warnungsumgehung unterstützen
**Abhängigkeit:** P01–P03 und P12.  
**Prompt:** Implementiere das Browserportal so, dass aktuelle Standardbrowser die Verbindung ohne deaktivierte Zertifikatsprüfung oder Ignorieren einer Sicherheitswarnung nutzen können. Sichere Origin-/CSRF-/CORS-/Private-Network-Access-Regeln, Pairing-Token, Sitzung und jede Upload-/Download-Route. Kein HTTP-Fallback, keine Wildcard-CORS-Öffnung, keine Platzhalter-CA und keine unsichere Ausnahme. Wenn eine Browserfunktion technisch nicht sicher bereitgestellt werden kann, stoppe vor Release und dokumentiere die Blockade statt sie unsicher zu belassen. **Abnahme:** Tests auf unterstützten Browsern belegen TLS/Authentifizierung/Origin-Schutz; kein Klartextpfad.


# L – Lokale KI und Modelldownloads

## L01 – Laufzeit- und APK-Inventur (AUDIT)
**Prompt:** Prüfe aktuell `LlamaCppInferenceEngine`, `LlmService`, JNI-Aufrufe, `System.loadLibrary`, CMake/NDK, `jniLibs`, Gradle-Artefakte, Modellkatalog, Downloads und tatsächlichen APK-Inhalt. Dokumentiere, was wirklich kompiliert und ladbar ist, und unterscheide echte Inferenz von regelbasierten Heuristiken. Noch keine Native-Bibliotheken hinzufügen und keine Funktion entfernen. **Abnahme:** reproduzierbares Inventar mit bestätigten Dateien/ABIs und offenen Punkten.

## L02 – Native-Produktionsplan mit dem tatsächlichen Bestand abgleichen
**Abhängigkeit:** L01.  
**Prompt:** Aktualisiere docs/LLM_NATIVE_PRODUCTION_PLAN.md anhand der aktuellen Native-/JNI-/Gradle-Dateien und des neuesten verifizierten CI-Laufs. Entferne veraltete Aussagen, wonach CMake, llama.cpp, JNI oder die native Implementierung fehlten. Trenne exakt: (a) Quell-/Buildintegration vorhanden, (b) durch CI kompiliert, (c) auf ABI installiert, (d) echtes GGUF geladen und Inferenzen erzeugt, (e) Qualität auf realem Gerät gemessen. Vergib für jede Ebene getrennte Nachweise. Verknüpfe verbleibende Punkte mit L08A–L12, ohne deren Arbeit doppelt zu formulieren. **Abnahme:** Der Produktionsplan stimmt mit tatsächlichen Dateien und Tests überein; kein fehlender Datei- oder Gerätetest wird als bestanden gemeldet.

## L03 – Modellkatalog und Quellenbelege
**Prompt:** Konsolidiere nur den Modellkatalog. Jeder Eintrag muss belastbare Quelle, exakte Dateigröße, SHA-256, Version, Lizenz, unterstützte Architektur und Dateiformat enthalten. Erfinde fehlende Werte nicht; unvollständige Einträge dürfen nicht aktiviert/heruntergeladen werden. Konsolidiere Duplikate ohne Modell-IDs/URLs unbemerkt zu verändern. **Abnahme:** Tests für Duplikate, fehlende Lizenz, fehlenden Hash und ungültige Größen.

## L04 – WorkManager-Downloader-Grundlage
**Abhängigkeit:** L03.  
**Prompt:** Stelle den Download in einen `CoroutineWorker` mit eindeutiger Arbeit pro Modell um, inklusive Fortschritt, Abbruch, begrenzten Retries, exponentiellem Backoff mit Jitter und sichtbarer Foreground-Info, soweit Laufzeit/Dateigröße dies erfordert. Prüfe vor Start den verfügbaren Speicher: exakte Modellgröße plus mindestens 20 % Reserve. Standardmäßig nur ungemessene/WLAN-Netze; Mobilfunk nur nach bewusstem Opt-in. Noch keine komplexe Resume-Logik. **Abnahme:** Worker-/TestDriver-Tests für Constraints, Abbruch, Retry, Platzmangel.

## L05 – Modellintegrität und private Ablage
**Abhängigkeit:** L03/L04.  
**Prompt:** Validiere nach Download exakte Länge, SHA-256 und GGUF-Magic/Grundstruktur gegen den Katalog. Bei jeder Abweichung Datei verwerfen und Modell nicht aktivieren. Redirects nur zu erlaubten HTTPS-Hosts. Speichere nur app-privat; entferne Kopien in öffentlichen `Downloads`-Ordnern und automatische Suchen dort. Für manuelle Modelle Android Storage Access Framework nutzen und nach Import prüfen. **Abnahme:** falscher Hash, Länge, Redirect, beschädigtes GGUF und öffentlicher Speicherpfad getestet.

## L06 – Download-Resume korrekt implementieren
**Abhängigkeit:** L04/L05.  
**Prompt:** Implementiere nur den Resume-Mechanismus mit `Range`, `ETag`/`If-Range`, `200`/`206`/`416`, `Content-Range` und erwarteter Partial-Dateigröße. Ungültiger Zustand muss sauber neu starten, ohne Bytes doppelt anzuhängen. Nach begrenzten Versuchen einen eindeutigen Fehler melden. **Abnahme:** MockWebServer-Tests für korrekten Resume, Server ignoriert Range, 416, ETag-Wechsel, Abbruch und beschädigte Partialdatei.

## L07 – Ehrliche Laufzeitmodi und Datenschutz
**Prompt:** Stelle sicher, dass UI, APIs, Logs und Telemetrie klar zwischen `Regelbasiert` und `Lokales Modell` unterscheiden. Keine Konfidenz wie `0.98`, Token-/Durchsatzmetriken oder „verifiziert“-Meldungen ohne echte Messung/Prüfung. Tokens/s nur aus real gezählten Tokens und gemessener Zeit. Keine Dokument-/OCR-Inhalte an entfernte Provider oder Telemetrie senden, ohne separat gekennzeichnete und ausdrückliche Nutzerfreigabe. **Abnahme:** Tests prüfen sichtbaren Modus, Heuristik-Fallback und fehlende/gescheiterte Engine; keine irreführende Inferenzbehauptung.

## L08A – Vorhandene Native-Buildintegration reproduzierbar verifizieren
**Abhängigkeit:** L01/L02.  
**Prompt:** Der native Build ist im aktuellen Code bereits vorhanden: gepinntes llama.cpp-Submodul, CMakeLists.txt, llama_android.cpp, 32-Bit-Stub, Gradle-NDK-Konfiguration und vier ABI-Filter. Füge diese Dateien nicht nochmals hinzu. Prüfe das exakte Upstream-Commit, Lizenzdateien, den reproduzierbaren CI-Build, gelinkte Bibliotheken pro ABI, transitive Artefakte und aktuelle 16-KB-Packaging-/ELF-Anforderungen. Korrigiere ausschließlich nachgewiesene Lücken. **Abnahme:** exakt benannter Workflow/Commit, angeforderte ABIs und Paketprüfungen belegt; „Build erfolgreich“ ist kein Modelllauf.

## L09A – JNI-Modelllebenszyklus
**Abhängigkeit:** L08A.  
**Prompt:** Prüfe den bereits vorhandenen nativen Modelllebenszyklus in `llama_android.cpp` (`ensure_engine`, `release_engine`, globaler Engine-Zustand) und den Kotlin-Aufrufpfad. Implementiere keine zweite Lade-/Inferenz-Engine. Ergänze nur nachgewiesene Lücken: sichere Serialisierung von Laden/Wechseln, explizite Freigabe nur falls ein realer App-Lifecycle-Aufrufer sie benötigt, robuste Fehlerweitergabe und Cleanup bei Modellwechsel sowie Fehlern. Prüfe ungültige Modellpfade und konkurrierende Aufrufe; native Fehler dürfen den Prozess nicht unkontrolliert beenden. Tokenstreaming, Grammar, Cancel/Timeout und UI bleiben außerhalb dieses Micro-AP. **Abnahme:** Tests für Wiederverwendung desselben Modells, Modellwechsel, ungültige Datei, parallele Aufrufe und Cleanup; keine Behauptung über einen physischen GGUF-Lauf ohne dessen Nachweis.

## L10A – Vorhandene echte Inferenz verifizieren und sicher begrenzen
**Abhängigkeit:** L09A.  
**Prompt:** Der vorhandene llama_android.cpp-Code enthält bereits Modellladen, Chat-Template-Verarbeitung, Token-Generierung sowie native Token-/Zeitmetriken. Baue keine zweite Inferenzengine. Prüfe Modell-/Tokenizer-/Chat-Template-Kompatibilität, tatsächliche Output-Tokenzählung, Fehlerbehandlung, Max-Token-Grenze, Thread- und Kontextgrenzen sowie Freigabe von Modell-/Sampler-/Context-Ressourcen bei Erfolg und Fehler. Implementiere hier nur Lücken, die nicht in L12 als Cancel-/Timeout-/Ergebnisvertrag behandelt werden. Regelbasierte Fallbacks müssen außerhalb des Ergebnisses echter nativer Inferenz bleiben. **Abnahme:** kleines, lizenziertes und gehashtes GGUF wird auf unterstützter ABI geladen und erzeugt überprüfbare Modellantwort; tatsächliche Decoder-Messwerte liegen vor oder der Geräteschritt wird als nicht ausgeführt dokumentiert.

## L11A – ABI-/16-KB-/Geräteprüfung
**Abhängigkeit:** L08A–L10A.  
**Prompt:** Prüfe alle nativen Bibliotheken inklusive transitiver Abhängigkeiten auf ABI-Abdeckung sowie 16-KB-ELF- und APK/AAB-Alignment. Teste nur die zugesagten ABIs, einschließlich `arm64-v8a` und `x86_64` nur sofern tatsächlich paketiert/unterstützt, auf passenden Emulatoren oder Geräten. Dokumentiere Geräte, API-Level und Resultate. **Abnahme:** kein pauschales „kompatibel“ ohne Artefakt-/Gerätenachweis.

### L08B – LLM-Rückbau (nicht ausgewählte Alternative; nicht automatisch ausführen)
**Abhängigkeit:** Nur nach dokumentiertem schwerwiegendem technischem Blocker und erneuter ausdrücklicher Nutzerentscheidung.  
**Prompt:** Diesen Auftrag nicht automatisch ausführen. Erst nach konkretem Befund und Nutzerfreigabe einen Rückbau umsetzen. Regelbasierte Funktionen transparent kennzeichnen; keine Modellbytes ohne Freigabe laden. **Abnahme:** Entscheidung und Auswirkungen dokumentiert; kein stillschweigender Austausch echter KI gegen Heuristiken.

## Verbindliche Neufassung: LLM-Dokumentenverarbeitung als Hybrid (L12–L17)

**Einordnung:** L12–L17 konkretisieren die Qualitäts- und Verarbeitungsschicht nach der nativen Laufzeitintegration L08A–L11A. Sie ersetzen die Annahme, das kleine Modell solle alle Feldwerte frei als JSON extrahieren. Das LLM wählt primär aus einer von Kotlin erzeugten, belegbaren Kandidatenliste. Deterministische Erkennung liefert Kandidaten, keine vermeintlich unfehlbare Gesamtklassifikation.

**Verbindliche Qualitätsregel:** Eine syntaktisch gültige JSON-Antwort ist kein Beleg für sachliche Richtigkeit. Grammar darf nur das Antwortformat und zulässige Kandidaten-IDs begrenzen. Nicht vorhandene, ungültige oder unpassende Kandidaten werden verworfen und dürfen niemals stillschweigend in Dokumentmetadaten gespeichert werden.

## L12 – Native Inferenz absichern: Grammar, Cancel, Kontext und echte Metriken

**Abhängigkeit:** L08A–L11A müssen vor produktiver Umsetzung dieses AP abgeschlossen oder mit einem konkreten, dokumentierten Blocker versehen sein. CMake/JNI und der native Modelllebenszyklus sind im Repository vorhanden; der aktuelle reproduzierbare CI-/NDK-Build, tatsächliche ABI-Paketierung/Installierbarkeit und echte GGUF-Inferenz auf einem unterstützten Zielgerät sind jedoch noch nicht abschließend nachgewiesen. Grammar, Cancel/Timeout und native Ressourcenfreigabe müssen anhand der fest gepinnten llama.cpp-Version geprüft werden. Vor Änderungen aktuellen CI- und Gerätestand prüfen. Keine nicht vorhandene Datei oder JNI-Funktion als implementiert darstellen.

**Projektregeln-Vorspann (bei jeder Ausführung verbindlich):**
Du arbeitest ausschließlich im tatsächlich vorhandenen lokalen Workspace von myDocAnizer-Mobile. Lies zuerst docs/coding-agent_documentation.md und die unmittelbar betroffenen Implementierungen und Tests. Der Code ist maßgeblich; Dokumentation, Kommentare und frühere Statusmeldungen sind zu verifizieren. Bearbeite nur diese Micro-AP, führe kein unabhängiges Groß-Refactoring durch und ändere keine fachfremden Datenmodelle. Sende OCR, Dokumentinhalte, Chatfragen oder Feldwerte nicht an externe KI-Dienste. Erfinde keine Funktionen, Messwerte, Tests oder Erfolgsmeldungen. Der Coding-Agent in AI Studio arbeitet mit der bereitgestellten Workspace-Kopie; behaupte weder GitHub-Zugriff noch einen Commit, wenn du ihn nicht tatsächlich ausgeführt hast. Liefere einen vollständigen, übernehmbaren Patch/Diff mit den geänderten Dateien. Führe nach der Änderung ./gradlew assembleDebug testDebugUnitTest aus, sofern die Umgebung es unterstützt; sonst dokumentiere konkret, weshalb nicht. Bei Fehlern bleib innerhalb dieser AP, behebe sie oder melde sie offen. Aktualisiere docs/open_tasks.md für nicht ausführbare Geräte-/Native-Tests.

**Prompt:**
1. Prüfe zuerst den echten nativen Versionsstand und die tatsächlich vorhandene Signatur jeder JNI-Methode. Implementiere ein Cancel-Flag mit thread-sicherem Lebenszyklus und eine native Abbruchfunktion. Nutze eine Abbruch-Callback-API, die im fest gepinnten llama.cpp-Stand tatsächlich verfügbar ist; nicht ungeprüft eine API eines anderen Versionsstands voraussetzen. Prüfe das Flag regelmäßig während der Token-Generierung. Kotlin-Coroutine-Abbruch muss den nativen Abbruch auslösen und Mutex/Semaphore/Engine-Sperren in jedem Exit-Pfad freigeben.
2. Ersetze gemeinsam veränderliche Zustandsfelder wie lastInferenceMode/lastInferenceMetrics durch einen Rückgabewert InferenceResult mit Text/Antwort, tatsächlichem Modus, finishReason (stop, length, abort, error), gemessenen Prompt- und Generierungs-Tokens, Kürzungsstatus, Modell-Ladezeit und optionalen Fehlerdetails. Fehlt eine Messgröße im Native-API-Stand, liefere sie nicht als erfundenen Wert. _isGenerating muss parallele Aufrufe korrekt abbilden (Zähler oder echte Job-Identität statt ein einzelnes Boolean).
3. Setze konfigurierbare Watchdogs: Triage bis 20 s, Tiefenanalyse bis 45 s, Chat bis 30 s. Timeout und Nutzerabbruch müssen denselben sicheren Cleanup-Pfad verwenden; ein blockierter nativer Aufruf darf den Mutex nicht dauerhaft festhalten. Baue einen reproduzierbaren Test für den bisherigen Hänger bei einer simplen Begrüßung ein. Entferne etwaige Begrüßungs-Sonderfälle erst, wenn Timeout/Abbruch samt Logs belegt funktionieren.
4. Extraktion und Kandidaten-ID-Auswahl verwenden Temperatur 0 und einen festen Seed, soweit die gepinnte native API das garantiert. Chat darf eine getrennt konfigurierte Temperatur verwenden. Berichte deterministisches Verhalten nicht, solange gleiche Eingabe/Modellversion nicht ausreichend reproduzierbar getestet wurde.
5. Härte die native Grammar-Schnittstelle zunächst mit einer isolierten Testgrammatik und JSON-Fixtures. Prüfe Grammar-Konstruktion, Fehlerweitergabe und Sampler-Reihenfolge gegen den gepinnten llama.cpp-Code. Ändere die Reihenfolge nicht nur aufgrund einer Hypothese: vergleiche in einem gezielten Test die zulässigen Outputs vor/nach der Änderung. Die dynamische Grammatik für tatsächlich erlaubte Kandidaten-IDs gehört zu L13, nachdem dort das Kandidatenschema feststeht. Bis Grammatik und Parser im echten Native-Pfad getestet sind, grammar=null in produktiven Klassifikationsaufrufen nicht blind entfernen.
6. Berechne das Kontextbudget mit dem tatsächlichen Tokenizer. System-Prompt, Chat-Template und Antwortschema dürfen nicht abgeschnitten werden. Kürze ausschließlich den OCR-Teil vor dem Chat-Template, erhalte einen definierten Anteil vom Anfang und Ende und kennzeichne die Kürzung. Begrenze n_ctx nach geprüftem Hardware-Tier und nie oberhalb des Modellkontexts. Threadzahl, Modellfreigabe bei onTrimMemory/Modellwechsel und Freigabe bei längerem Hintergrundzustand nur mit belegtem Ressourcenpfad implementieren.
7. Metriken dürfen nur echte Zähler und Zeitmessungen abbilden: keine geschätzten Tokens/s, fest eingetragenen RAM-Werte oder erfundenen Denkprotokolle. Modus, Metriken und Fehler gehören in das einzelne InferenceResult statt in konkurrierende globale Seiteneffekte.
8. Tests: (a) 50 reale Durchläufe mit einem kleinen, gehashten GGUF und ausschließlich gültigen, zur Laufzeit erlaubten Kandidaten-IDs, (b) Abbruch unter 1 s soweit das Referenzgerät dies zulässt, (c) Timeout gibt Engine-Sperre frei, (d) langer OCR-Text kürzt ausschließlich OCR, (e) native Fehler führen nicht zu einem als LLM ausgewiesenen Heuristikergebnis. Kann ein Lauf nicht in CI stattfinden, als offenen Geräte-/Instrumentierungstest in docs/open_tasks.md dokumentieren; nicht als bestanden markieren.

**Abnahme:** Native Laufzeit und JNI sind im Workspace vorhanden; echter Inferenzlauf oder präzise dokumentierter Geräteblocker; Cancel-/Timeout-/Kontext-/Grammar-Tests; keine gefälschten Metriken. Grammar-Tests sind getrennt von der fachlichen Auswahlgenauigkeit auszuweisen.

## L13 – Kandidatenbasierte Hybridanalyse statt frei erfundener Feldwerte

**Abhängigkeit:** L12. Dieses AP ist die fachliche Kernänderung der Dokumentenverarbeitung.

**Projektregeln-Vorspann (bei jeder Ausführung verbindlich):**
Du arbeitest ausschließlich im tatsächlich vorhandenen lokalen Workspace von myDocAnizer-Mobile. Lies zuerst docs/coding-agent_documentation.md sowie LlmService, LlmPrompts, Qualitäts-/OCR-Helfer, bestehende Kategorien, Dokumenttypen, Custom Fields und die Aufrufstellen. Prüfe den echten Ist-Zustand, ändere nur diese Micro-AP und erhalte bestehende Ablage- und Nutzerbestätigungsabläufe. Keine externe KI-API, keine privaten Beispieldokumente in Tests und keine OCR-/Feldwerte in Logs. Keine fingierte Konfidenz und keine freie LLM-Ausgabe ungeprüft speichern. Liefere vollständige, übernehmbare Änderungen statt unvollständiger Ausschnitte. Führe ./gradlew assembleDebug testDebugUnitTest aus oder dokumentiere den exakten Grund, wenn das nicht möglich ist. Unausführbare Gerätetests gehören als offen nach docs/open_tasks.md.

**Prompt:**
1. Führe eine gemeinsame kandidatenbasierte Analysekomponente ein. Ihr Eingang ist OCR-Text samt Zeilen-/Positionsinformationen; ihr Ausgang ist eine typisierte Candidate-Liste plus Analysemetadaten. Jeder Kandidat hat eine nur für diesen Lauf gültige ID, Feldtyp, Originalausschnitt, normalisierten Wert, Quellart, Position und Validierungsstatus. Gleiche normalisierte Kandidaten werden dedupliziert, ohne ihren Textbeleg zu verlieren.
2. Verwende als generische Kandidatenquellen erstens ML Kit Entity Extraction für Deutsch (mindestens Geld/Währung, Datum/Uhrzeit und IBAN, soweit die API dies tatsächlich zurückgibt) und zweitens ein eng begrenztes, dokumenttypunabhängiges Musterwerk für Beträge, Daten und IBAN. IBANs müssen die MOD-97-Prüfung bestehen. Beträge sind verlustfrei normalisiert, ohne Netto/Brutto gleichzusetzen; mehrere Beträge bleiben als separate Kandidaten erhalten. Datumsangaben werden erst nach Syntax-/Kalenderprüfung akzeptiert. Keine Sammlung dokumenttypspezifischer Regex-Regeln aufbauen. Falls ML Kit, das Sprachmodell oder die passende API-Version nicht verfügbar ist, soll die generische Kandidatenerkennung weiterlaufen.
3. Vor einer ML-Kit-Abhängigkeit prüfe aktuelle Library-Version, minSdk 24, tatsächliche deutsche Modellunterstützung, Download-/Updateverhalten und das Verhalten ohne Netz bzw. ohne installiertes Modell. Die Erkennung muss lokal auf dem Gerät ausgeführt werden; OCR-Text wird nicht an einen Dienst gesendet. Weil das deutsche ML-Kit-Modell bei Bedarf heruntergeladen werden kann und die API Beta ist, dokumentiere die Auswirkung auf Erststart, Offline-Nutzung und Datenschutztext. Niemals pauschal „immer offline, ohne Modelldownload“ behaupten.
4. Stelle für Absender und Referenznummern ebenfalls Belegkandidaten bereit: insbesondere OCR-Zeilen und generische Kennungs-Kandidaten mit Originaltext. Das LLM darf einen Kandidaten auswählen, aber keinen beliebigen Absender oder eine frei erfundene Nummer zurückschreiben. Fehlt ein geeigneter Kandidat, muss null gewählt werden und needsReview gesetzt werden.
5. Übergib dem LLM nur kompakten, datensparsamen Kontext, die relevanten Kandidaten samt IDs und die tatsächlich in der App vorhandenen Dokumenttyp-/Kategorieoptionen. Die strukturierte Antwort enthält ausschließlich Kandidaten-IDs oder null für Werte, einen Dokumenttyp aus einer geschlossenen Enum-Liste und eine existierende mainCategoryId. Die IDs und Kategorieoptionen werden pro Lauf aus den echten App-Daten generiert; keine frei formulierten Kategorien wie „Finanzen“, falls die Persistenz eine ID wie A02 erwartet. Nutze dieselbe kleine Antwortstruktur für Triage und Tiefenanalyse; der zweite Lauf darf nur Zusatzentscheidungen ergänzen, nicht unkontrolliert alle Werte neu überschreiben.
6. Implementiere einen zentralen Validator/Merger. Priorität: explizite Nutzerkorrektur, eine bereits bestätigte und exakt passende DocRule, danach validierte LLM-Auswahl eines vorhandenen Kandidaten; bei widersprüchlichen belegten Quellen needsReview statt stiller Überschreibung. Verwerfe unbekannte IDs, falsche Feldtypen, doppelte Synonyme und Werte ohne OCR-Beleg. Für Betrag/Datum/IBAN ist die validierte Kandidaten-Normalisierung maßgeblich; das LLM darf keine neuen Rohwerte erzeugen. Wende pro Analyse einen zentralen Mappingpfad für Dokumenttypen und Kategorien an.
7. Verwerfe Platzhalter, leere Strings, „null“ als Klartext, Schema-Beispielwerte und nicht existente Kategorien/Feldnamen. Entferne das fest kodierte Absender-Fallback „Deutsche Post“, das Jahres-Fallback 2026 und jede automatische Zuordnung eines unbekannten Dokumenttyps zu A01. Unbekannt muss unbekannt bleiben und bei Bedarf überprüft werden.
8. Führe einen AnalysisResult-Typ ein, der pro Feld mindestens Wert/Kandidatenbezug, Quelle (RULE, LLM, USER), Grounding-/Validierungsstatus und Review-Flag ausweist. Zahlenwerte für Confidence nur dann ausgeben, wenn sie aus dokumentierten, kalibrierten Tests abgeleitet werden; sonst kein Prozentwert. analysisSource muss echte native LLM-Auswahl, reparierte Auswahl und reinen Regel-/Kandidaten-Fallback sauber unterscheiden. Modellfehler, kaputtes JSON und Timeout dürfen nie als erfolgreiche LLM-Klassifikation erscheinen.
9. Entferne buildModelReasoning und alle erfundenen Denkprotokolle. refineRuleSuggestion darf keine Textsuche-Regeln anwenden, die nach der Modellantwort Typ oder Kategorie überschreiben. generateRulesFromPrompt darf nicht als echte Modellgenerierung erscheinen, wenn es nur Vorlagen oder feste Themenzweige ausführt: entweder dieselbe validierte lokale LLM-Pipeline verwenden oder die UI ausdrücklich „Vorlagen“ nennen.
10. Tests mit Fake-Engine und synthetischen deutschen OCR-Zeilen: fehlender/mehrfacher Kandidat, mehrere Beträge mit Netto/Brutto, Geburtsdatum neben Rechnungsdatum, IBAN mit gültiger/ungültiger Prüfsumme, OCR-Verwechslungen, normalisierte Datumsformate, unbekannte Kategorie-ID, Platzhalter-Filter, Duplikate, User-vs-Rule-vs-LLM-Konflikt und kaputtes JSON. Ein ungültiges Resultat darf nie ungeprüft persistiert werden.

**Abnahme:** Das LLM kann nur eine existierende Option bzw. einen Kandidaten wählen. Ungültige/fehlende Werte führen kontrolliert zu null/needsReview. Deterministische Extraktion und LLM-Auswahl sind einzeln testbar. Keine frei erfundenen Beträge, Daten, IBANs, Nummern oder Absender im finalen Ergebnis.

## L14 – Durchgängiges, datensparsames Audit der Dokumentenverarbeitung

**Abhängigkeit:** L13.

**Projektregeln-Vorspann (bei jeder Ausführung verbindlich):**
Arbeite nur im vorhandenen Repository und lies zuerst docs/coding-agent_documentation.md, AppAuditLogger und die realen Scan-/OCR-/Storage-Aufrufstellen. Begrenze Änderungen auf Korrelation, Status und Messbarkeit. Logge nie OCR-Text, Chatfrage, Refine-Prompt, Rohantwort, Absender, IBAN, Betrag, Dokumenttitel oder andere Feldwerte. Externe Telemetrie ist kein Ersatz für das lokale Audit. Erfinde keine Metriken. Liefere vollständige Diffs und Tests. Führe ./gradlew assembleDebug testDebugUnitTest aus oder dokumentiere die Einschränkung präzise; nicht ausführbare Export-/Gerätetests nach docs/open_tasks.md.

**Prompt:**
1. Erzeuge pro Scan eine zufällige scanId nur zur Korrelation. Reiche diese durch Erfassung, Zuschnitt, OCR, Kandidatenerkennung, Regelabgleich, LLM-Inferenz, Vorschlagsanzeige, Nutzerentscheidung, PDF-Erzeugung und Persistenz durch. Sie ist keine Nutzer- oder Dokument-ID und darf keine geheimen Inhalte kodieren.
2. Erfasse Ereignisse mit Zeitstempel, Dauer, Ergebnis und Phase: capture, crop, ocr, candidate_generation, rule_match, analysis_start, analysis_result, suggestion_shown, user_decision, pdf_generate, db_write und saved/failed. Notiere nur Zeichen-/Kandidatenanzahl, IDs aus kontrollierten Enum-Werten, Modell-/Prompt-Version, tatsächlichen finish_reason, gemessene Tokenzahlen und Zeiten, Anzahl verworfener Kandidaten sowie needsReview. OCR-Inhalt, Ausschnitte und Feldwerte bleiben ausgeschlossen.
3. Jeder Timeout, Cancel, Parserfehler, Reparaturversuch, Fallback, fehlendes Modell, ABI-/JNI-Fehler und Speicherfehler muss auch im AppAuditLogger als strukturierter Fehler erscheinen und eine kontrollierte Fehlerklasse erhalten; Logcat allein reicht nicht. Reine Regeln/Kandidaten ohne Modellinferenz als solchen Modus kennzeichnen.
4. Speichere pro Dokument bei erfolgreicher Nutzerbestätigung analysisSource, modelId soweit verwendet, promptVersion, needsReview und relevante begrenzte Qualitätsmetadaten mit einer migrationssicheren Room-Änderung. Bestehende Dokumente müssen sinnvoll null/unknown erhalten, nicht nachträglich als LLM-Analysen ausgegeben werden. Erhöhe die Room-Version nur um den notwendigen nächsten Schritt und ergänze Migrationstest ohne Datenverlust.
5. Prüfe Logrotation, Größenlimit und Exportpfad von AppAuditLogger und dokumentiere tatsächliche Grenzen. Füge Tests hinzu, die Test-PII (z. B. synthetische IBAN, Mail, Betrag und einmalige Markerzeichenfolge) in Eingaben verwenden und verifizieren, dass diese Werte weder im Audit noch im Fehlertext erscheinen. Rohprompts und rohe JSON-Antworten dürfen auch im Debug-Modus nicht versehentlich im persistenten Audit auftauchen.
6. Ergänze im Diagnoseexport nur aggregierte und begrenzte Metadaten: Modell/Version, Appversion, Gerätetier, Regel-/LLM-/Fallback-Anteile, p50/p95-Latenz, Timeout-/Abbruch-/Parsefehler und letzte 20 Analysen ohne Inhalte oder Feldwerte. Downloadereignisse enthalten Soll-/Ist-Größe und SHA-256, nicht Authentifizierungsdaten.
7. Teste Audit-Ereignisfolgen für erfolgreich gespeicherten Scan, verworfenen Scan, OCR ohne Text, Timeout, Abbruch, Native-Fehler, JSON-Fehler und Regel-Fallback. Jeder Zustand muss eine korrelierbare, verständliche und PII-freie Ereignisfolge besitzen.

**Abnahme:** Nachvollziehbare Scan-ID und Prozessereignisse; Audit bleibt inhaltlich datensparsam; keine Klartextinhalte oder erfundenen Leistungswerte; Migrations- und PII-Tests vorhanden.

## L15 – Einheitliche Scan-Pipeline, Nutzerprüfung und sichere Chat-Aktionen

**Abhängigkeit:** L12–L14.

**Projektregeln-Vorspann (bei jeder Ausführung verbindlich):**
Lies docs/coding-agent_documentation.md und alle tatsächlichen ViewModel-Aufrufstellen vor Änderungen. Nutze eine einzige validierte Analyse-Pipeline; baue keinen parallelen Sonderweg. Scan, Bulk-Speicherung und erneuter Lauf im Dokumentdetail dürfen keine unterschiedlichen Regeln für Quellen/Validierung anwenden. Kein Feature darf LLM-Fallback als echte KI ausgeben. Schreibende Chat-Tools benötigen ausdrückliche Nutzerbestätigung. Keine fremden Module nebenbei umbauen. Stelle Diffs und Testergebnisse ehrlich bereit und führe ./gradlew assembleDebug testDebugUnitTest aus oder dokumentiere, warum es nicht möglich war. Offene UI-/Gerätetests in docs/open_tasks.md festhalten.

**Prompt:**
1. Binde die Pipeline aus L13 an alle realen Scanpfade an: insbesondere requestScanRuleConfirmation, saveSinglePageBulk und Re-Run im Dokumentdetail. Entferne doppelte Klassifikationslogik, ohne den bestehenden Scan- und Speicherablauf unnötig umzustellen. Der Haupt-Scanpfad darf nicht stillschweigend nur buildFastRuleSuggestion ausführen und dies als KI-Vorschlag anzeigen.
2. Zeige einen sichtbaren, abbrechbaren Zustand „Analysiere …“ und verwende die konfigurierten harten Zeitlimits aus L12. Bei Timeout, Fehler, Abbruch oder fehlendem Modell muss unmittelbar ein ehrlicher kandidaten-/regelbasierter Fallback verfügbar sein. UI-Beschriftung, Audit und Ergebnisobjekt müssen dieselbe tatsächliche Herkunft zeigen. needsReview-Felder sind sichtbar markiert; fehlende Werte werden nicht durch Standard-Absender oder Jahreswerte ersetzt.
3. Bei null oder mehreren passenden DocRules die Analyse nicht stillschweigend überspringen. Zeige bei Mehrfachtreffern Auswahl/Begründung und erlaube die Kandidatenanzeige auch ohne Modell. Bereits bestätigte Nutzerregeln behalten Vorrang; nicht bestätigte, widersprüchliche Regeln führen zur Nutzerprüfung.
4. Der erneute Lauf muss einen eigenen Lade-/Abbruchzustand besitzen. Fehler werden im Dokumentdetail verständlich angezeigt, inklusive Herkunft des alten und neuen Vorschlags, ohne Rohprompt, OCR oder private Rohantworten offenzulegen. Der Lauf darf vorhandene validierte Werte nur überschreiben, wenn neue Daten gültig sind und die Prioritätsregeln aus L13 eingehalten werden.
5. Chat-Aktionsintents in Kotlin sicher erkennen und Schreibaktionen wie updateDocumentStatus, createFolder und createCustomDashboardWidget vor Ausführung als sichtbare, konkrete Vorschau darstellen. Führe die Aktion erst nach bewusster Bestätigung aus. Validiere jede Dokument-ID und jedes Argument gegen das Schema, die bestehenden Berechtigungen und tatsächlich vorhandene Entitäten. Bei ungültigem Tool-JSON folgt eine verständliche Rückfrage, nie Rohtextausführung.
6. Dokumentbezogene Chatantworten nennen den tatsächlich gefundenen Dokumenttitel/Bezeichner als Quelle. Wenn kein Dokument mit passender Evidenz gefunden wird, sage das explizit, statt eine Antwort zu erfinden. Keine stillen Schreibaktionen und kein Ausführen von Tool-Aufrufen allein deshalb, weil das Modell sie als Text ausgegeben hat.
7. generateRulesFromPrompt und andere angebliche „KI“-Aktionen müssen entweder wirklich die geprüfte lokale Inferenz nutzen oder in UI und Dokumentation ausdrücklich als Regelvorlagen/Vorlagen bezeichnet werden. Fallbacks dürfen Modellnamen, KI-Badges oder Modell-Konfidenz nicht übernehmen.
8. ViewModel-/UI-Tests mit Fake-Pipeline: Erfolg, Timeout, Cancel, Modell fehlt, kaputtes JSON, Mehrfachregeln, Nutzeränderung, Re-Run-Fehler, nicht bestätigter Tool-Call und bestätigter Tool-Call. Beweise, dass kein schreibendes Tool vor Bestätigung ausgeführt wird.

**Abnahme:** Alle Analyseaufrufe nutzen dieselbe Validierung. Herkunft und Fehler sind sichtbar und wahrheitsgemäß. Kein stilles Überschreiben, keine unbestätigte Schreibaktion, kein KI-Etikett für Regeln.

## L16 – Modellkatalog, Integrität und Empfehlungen belastbar machen

**Abhängigkeit:** L03–L07 und L12.

**Projektregeln-Vorspann (bei jeder Ausführung verbindlich):**
Arbeite nur am lokalen Modellkatalog, Downloader, Import und der dazugehörigen UI. Lies vor Änderungen den bestehenden Katalog und prüfe jeden Eintrag anhand der tatsächlichen Primärquelle. Erfinde keine Modellnamen, Größen, SHA-256-Werte, Lizenzen oder Performancewerte. Ein Modell darf bei unklarer Integrität oder Lizenz nicht als empfohlen/verifiziert erscheinen. Große Änderungen an Downloadarchitektur nur, wenn für die geforderte Prüfung notwendig. Vollständige Diffs liefern, ./gradlew assembleDebug testDebugUnitTest ausführen oder den nicht möglichen Lauf klar dokumentieren; offene Netz-/Gerätetests in docs/open_tasks.md.

**Prompt:**
1. Verifiziere jeden Eintrag gegen das tatsächliche Repository des Herstellers/Modellinhabers und die konkrete heruntergeladene Datei: ID, Name, Organisation, URL, Dateiname, exakte Größe, SHA-256, GGUF-Format/Architektur, quantisierte Variante, Lizenz/Version, Mindest-RAM und nachgewiesene Fähigkeiten. Nutze soweit möglich die offizielle Hugging-Face-API bzw. die ursprüngliche Downloadquelle; dokumentiere Quelle und Abrufdatum. Ein Modellname allein ist kein Beweis, dass die Datei dahinter dieses Modell ist.
2. Gleiche frühere Warnungen zuerst mit dem heutigen Katalog ab. „LIFT 4B Structured PDF Extractor“ steht aktuell weiterhin im Code und verweist auf dieselbe Phi-3.5-mini-instruct-Q4_K_M.gguf-URL/Datei wie der Phi-3.5-Eintrag; korrigiere oder entferne diese irreführende Modellidentität. „Distil-Qwen 0.8B Invoice Triage“ und „Danube3 100M“ sind nicht als aktuelle Einträge bestätigt; nicht blind wieder hinzufügen. Der aktuelle Katalog enthält u. a. Ministral-3B und NuExtract-2-2B-Einträge; verifiziere deren konkrete Repository-, Format-, Lizenz- und Lade-Kompatibilität statt sie nur aufgrund des Namens zu akzeptieren. Für kommerzielle Nutzbarkeit/Redistribution muss die tatsächliche Lizenz belegt sein.
3. Speichere Katalogeinträge als versionierte Daten mit Quelle, erwarteter exakter Dateigröße und SHA-256. Unvollständige Werte bedeuten nicht „unbekannt akzeptieren“, sondern nicht verifiziert/nicht aktivierbar. Keine zwei unterschiedlichen Modell-IDs dürfen dieselbe Datei mit widersprüchlicher Beschreibung als verschiedene Modelle ausgeben.
4. Vor Aktivierung eines Downloads müssen exakte Größe, SHA-256 und überprüfte GGUF-Magic/Grundstruktur stimmen. Falsche Signatur, Hash, Länge, Redirect oder unzureichender Speicher führen zu eindeutigem Fehler und Entfernen einer unvollständigen Datei; eine Warnung mit trotzdem aktiviertem Modell ist nicht zulässig. Ein vorhandenes Modell darf nur per exaktem Hash und passender Größe automatisch verknüpft werden, nicht über Teilstrings im Dateinamen.
5. Die Standardempfehlung darf nur Modelle berücksichtigen, deren Integrität, Lizenzmetadaten, RAM-Tauglichkeit und tatsächliches Benchmarkprofil bekannt sind. Modelle ohne vollständig gemessenen Qualitäts-/Gerätenachweis werden in der UI als „Experimentell“ bezeichnet. Kein Modell wird allein nach Parameterzahl oder geschätzten Tokens/s empfohlen. Vorhandene Kandidaten wie Qwen2.5-1.5B können Testkandidaten sein, sind aber ohne eigene Geräteergebnisse nicht automatisch für den Produktivbetrieb freigegeben.
6. Integriere die Ergebnisse von L17 in die tatsächlichen Empfehlungsbedingungen. Ein Lauf auf einem Emulator oder ein Fake-Engine-Test darf nicht als echte Gerätefreigabe zählen. Dokumentiere außerdem, ob das optionale deutsche ML-Kit-Modell für die Kandidatenerkennung installiert ist, wie es bezogen wird und wie die App ohne dieses Modell funktioniert.
7. Tests mit Mock-Downloadserver: falsche Signatur, falsche Größe, falscher Hash, Abbruch, ungültiger HTTP-Range/416, unzulässiger Redirect, wenig freier Speicher und doppelter Dateieintrag. Alle Katalogeinträge müssen schema-validiert sein; keine unbekannte Lizenz wird stillschweigend als erlaubt behandelt.

**Abnahme:** Modellidentität und Integrität sind prüfbar; ungültige Dateien werden abgewiesen; Lizenz- und Geräteclaims sind belegt oder als offen gekennzeichnet; nicht gemessene Modelle heißen Experimentell.

## L17 – Golden Set, Kandidatenabdeckung und echte Auswahlqualität nachweisen

**Abhängigkeit:** L12–L16.

**Projektregeln-Vorspann (bei jeder Ausführung verbindlich):**
Nutze ausschließlich synthetische, frei erfundene deutsche OCR-Texte für automatisierte Tests; keine privaten Dokumente oder echte personenbezogene Daten. Prüfe vor jedem Benchmark, ob die Ausgabe echte Engine-Messwerte oder eine Fake-Engine ist. Verwechsle syntaktische JSON-Gültigkeit nicht mit fachlicher Richtigkeit. Verifiziere Testbefehle und Modelllizenzen, dokumentiere nicht ausführbare Gerätetests offen und ändere keine Produktionsschwellen nur, um ein Modell bestehen zu lassen. Liefere Patch/Diff und tatsächliche Ergebnisse. Führe ./gradlew assembleDebug testDebugUnitTest aus oder beschreibe die konkrete technische Grenze; Gerätebenchmark nicht als bestanden markieren, wenn er nicht lief.

**Prompt:**
1. Erstelle mindestens 40 synthetische deutsche OCR-Testfälle und versionierte Ground-Truth-JSON-Dateien. Decke Rechnung, Mobilfunk, Energie, Versicherung, Vertrag, Gehalt, Steuerbescheid, Kassenbon, Kontoauszug, Mahnung und Behördenbrief ab. Variiere OCR-Fehler, lange Texte, fehlende Felder, Mehrdeutigkeit, mehrere Beträge, Netto/Brutto, Rechnungsdatum neben Geburts-/Fälligkeitsdatum, ungültige/gültige IBAN sowie ähnliche Absendernamen. Kein persönliches Dokument verwenden.
2. Miss als zentrale Qualitätskennzahlen genau zwei getrennt ausgewiesene Stufen:
   - **Kandidatenabdeckung (Recall):** Anteil der erwarteten Feldwerte, die bereits vor der LLM-Auswahl als gültiger, korrekt normalisierter Kandidat vorhanden sind. Berichte die Werte feldweise für Betrag, Datum, IBAN und Referenz-/Absenderkandidaten; dokumenttypspezifische Enum-Fälle separat auswerten.
   - **Auswahlgenauigkeit:** Anteil korrekter LLM-Auswahlen unter den Fällen, in denen der richtige Kandidat tatsächlich vorhanden war. Falsche IDs, ungültige IDs, null trotz klar vorhandenem richtigen Kandidaten und unzulässige Enum-Werte gelten nach dokumentierter Regel als Fehlentscheidung. Werte, deren korrekter Kandidat fehlt, zählen nicht fälschlich als Auswahlfehler; sie erscheinen in der Kandidatenabdeckung.
3. Bewerte Dokumenttyp und Kategorie zusätzlich anhand der geschlossenen, realen App-Enums und der tatsächlichen Kategorie-IDs. Miss die Senderauswahl nur dann als korrekt, wenn ein erlaubter Textkandidat im OCR belegt ist. Werte nach Reparatur getrennt markieren; eine mit Reparatur gültige JSON-Antwort verbessert nicht rückwirkend die Erstversuchsqualität. Da das Testset mindestens 40 Fälle umfasst, sind die Kennzahlen ein reproduzierbarer Entwicklungs-Gate und keine statistische Garantie für alle Dokumente.
4. Als anfängliche, konfigurierbare Entwicklungsziele dokumentieren: Kandidatenabdeckung mindestens 95 % für Betrag/Datum/IBAN und Auswahlgenauigkeit mindestens 90 % auf Fällen mit vorhandenem richtigen Kandidaten. Diese Schwellen gelten nicht automatisch als Produktionsfreigabe. Verschärfe oder ändere sie nur mit dokumentierter Begründung und ohne Tests künstlich leichter zu machen.
5. Trenne deterministische Unit-Tests mit Fake-Engine, JSON-/Grammar-Tests, echte native Modelltests und reale Gerätebenchmarks. Berichte zusätzlich technische Diagnostik wie Erstversuchs-JSON-Gültigkeit, Reparatur-/Fallback-Anteil, Timeouts, gemessene p50/p95-Latenz sowie Modell-/Prompt-Version. Erfinde keine Tokens/s, RAM-Spitzen oder Modell-Ladezeiten; nicht verfügbare Messungen als „nicht gemessen“ ausweisen.
6. Erweitere runStandardizedModelBenchmark oder ergänze einen dedizierten Runner, der das Golden Set reproduzierbar ausführt und JSON-Berichte pro Modell-/Promptversion erzeugt. Der Report muss die Kandidatenliste vor Auswahl und das gewählte Kandidaten-IDschema vergleichbar machen, ohne OCR-Rohtexte in normalen Auditlogs zu speichern. Synthetische Testtexte dürfen in speziell versionierten Testartefakten liegen.
7. Nutze das Golden Set als Regressionstest für Candidate Builder, Normalisierung, IBAN-Mod-97, Enum-Mapping, Auswahlparser, Deduplizierung, Merge-Konflikte und Fallback. Echte Modelle werden nur auf geeigneten Geräten getestet. Wenn Instrumentierung/CI kein GGUF laden kann, kennzeichne das Ergebnis als offen und schreibe die manuelle Schrittfolge nach docs/open_tasks.md.
8. Aktualisiere den Modelkatalog erst dann auf verified=true, wenn alle verpflichtenden Qualitätsziele für das konkrete Modell und die Promptversion erfüllt sind und ein reproduzierbarer Gerätetest dokumentiert wurde. Bis dahin bleibt das Modell Experimentell. Im UI und in Exporten keine Behauptung „fehlerfrei“ oder „produktiv“, die nicht durch die dokumentierten Messungen gedeckt ist.

**Abnahme:** Die zwei Kernzahlen sind reproduzierbar und aufgeschlüsselt: (1) Kandidatenabdeckung und (2) bedingte Auswahlgenauigkeit. Fake-Engine-, CI- und echte Gerätetestergebnisse bleiben klar getrennt. Kein Modell wird ohne gemessenen Nachweis als produktiv/verifiziert bezeichnet.

---

# C – Cloud-Sync, OAuth und WebDAV

## C01 – Cloud-Sync, Verschlüsselungsgrenze und Sperrverhalten auditieren
**Produktentscheidung:** vollständige Google-Drive-Integration; Nutzdaten ausschließlich clientseitig verschlüsselt.  
**Prompt:** Prüfe CloudSyncWorkerService, GoogleDriveClient, WebDavClient, Einstellungen, Token-/Passwortablage, Netzwerkstatus und Vault-Lock. Definiere manuelle und automatische Synchronisation als auswählbare Modi; automatische Synchronisation erst nach ausdrücklicher Aktivierung. Niemals Klartextdaten hochladen. Bei gesperrtem Vault darf ein Worker höchstens bereits vollständig erstellte und authentifiziert verschlüsselte Container übertragen; neue Container benötigen Schlüssel gemäß Vault-Regeln. Erstelle technische Richtlinie für diesen Zustand, Konflikte, Netzwechsel und Abbruch. **Abnahme:** Datenfluss-/Zustandsmatrix ohne gespeicherte Master-Passphrase und ohne unsicheres Fail-open.
## C02 – WorkManager-Grundmigration
**Abhängigkeit:** C01, D10 und S08 soweit Secrets benötigt werden.  
**Prompt:** Ersetze die direkte Singleton-Ausführung durch einen `CoroutineWorker` mit Unique Work pro Sync-Konfiguration, Netzwerk-Constraints, optionalem Akku-Constraint, Fortschritt und begrenztem Retry/Backoff. Netzstatus bei Fehler/Unklarheit muss fail-closed sein. Große Transfers erhalten nur bei Bedarf eine korrekte Foreground-Info mit Stoppmöglichkeit und API-konformem Serviceverhalten. Provider-Auth noch nicht neu entwerfen. **Abnahme:** WorkManager-TestDriver für Constraints, Unique Work, Retry und Abbruch.

## C03 – Vault-Lock und SecretStore anbinden
**Abhängigkeit:** C01, S05, S08.  
**Prompt:** Stelle sicher, dass Worker ausschließlich über die genehmigte KeyProvider-/SecretStore-Schnittstelle auf benötigte Schlüssel/Zugangsdaten zugreift. Definiere den Workerzustand bei gesperrtem Vault: kontrolliert warten/fehlschlagen oder nur den in C01 explizit genehmigten Weg verwenden. Kein `userPasswordKey: String`, keine gespeicherte Master-Passphrase, keine stillschweigende automatische Entsperrung. **Abnahme:** Tests für gesperrten Vault, fehlende Secrets, Prozessneustart, Lock während Sync und Widerruf.

## C04 – Google-Drive-OAuth sicher integrieren
**Produktentscheidung:** Google Drive unterstützen; kein manuell eingetragener Access-Token.  
**Prompt:** Implementiere echten Android-OAuth-/Authorization-Flow mit gepflegter Google-Autorisierungsbibliothek. Nutze die engste Scope-Kombination, die den Backupablauf unterstützt. Für vom Nutzer sichtbare Backupdateien den drive.file-Pfad und passenden Datei-/Ordnerfluss prüfen; appDataFolder nur nutzen, wenn ausdrücklich versteckte App-Daten gewünscht sind. Access-/Refresh-Tokens sicher lokal im SecretStore/Keystore speichern, niemals loggen oder in Backups aufnehmen. Consent, Neuautorisierung, Widerruf und Konto-/Netzfehler sauber behandeln. **Abnahme:** echte Autorisierung, minimale Berechtigung, Revocation-/401-Tests und verständliche Statusmeldungen.
### C04B – Google Drive entfernen (nicht ausgewählte Alternative; nicht automatisch ausführen)
**Abhängigkeit:** Nur falls die aktuelle Produktentscheidung nach einem belegten schwerwiegenden technischen Blocker erneut geändert wird.  
**Prompt:** Diesen Auftrag nicht automatisch ausführen. Erst konkreten Befund, Alternativen und Empfehlung vorlegen und erneute ausdrückliche Entscheidung abwarten. Kein Provider darf stillschweigend entfernt werden.
## C05 – Google-Drive-Client und Transfers absichern
**Abhängigkeit:** C04; Uploads ausschließlich aus D10-verschlüsselten Containern.  
**Prompt:** Implementiere zuverlässige Drive-Uploads/Downloads großer Container mit Resumable Upload, begrenzten Retries und Retry-After bei geeigneten 429/5xx, getrennten Auth-/Quota-/Netz-/Formatfehlern und sicherem Abbruch. Prüfe Containerintegrität vor dem Anwenden eines Downloads. Bei 401 kontrolliert stoppen und Neuautorisierung anfordern. Logs dürfen keine Tokens, Inhalte, Klartextnamen oder Recovery-Secrets enthalten. **Abnahme:** Tests für Resume, 401, 403, 429, 5xx, Netzabbruch, ungültigen Container und Abbruch.
## C06 – WebDAV-HTTPS und Zertifikatsvertrauen
**Prompt:** Erzwinge HTTPS und Hostnamenprüfung. Beziehe Anmeldedaten ausschließlich aus S08-SecretStore. Für bewusst unterstützte selbstsignierte NAS-Zertifikate nur explizites Trust-on-first-use mit SHA-256-Fingerprint pro Host; geändertes Zertifikat blockieren, bis der Nutzer bewusst erneut prüft. Keine globale Trust-all-Implementierung oder stille HTTP-Ausnahme. **Abnahme:** Tests für gültiges, falsches, abgelaufenes/geändertes Zertifikat und fehlende Zugangsdaten.

## C07 – Verschlüsseltes Sync-Manifest und Differenzabgleich
**Abhängigkeit:** D10/C03.  
**Prompt:** Implementiere ein versioniertes, authentifiziert verschlüsseltes Sync-Manifest mit Dokument-ID, Inhaltsprüfsumme, Änderungszeit und Backupgeneration. Sensible Titel, OCR, Absender, Dateinamen und Ordnerstruktur bleiben im verschlüsselten Container; im Drive bleiben nur unvermeidbare Transportmetadaten. Übertrage nur neue/geänderte Daten. Alte Manifeste dürfen keinen neueren Vaultzustand überschreiben; Konflikt- und Löschregeln müssen idempotent sein. **Abnahme:** identische/geänderte Dateien, altes/manipuliertes Manifest, Konflikte und Integritätsprüfung getestet.
## C08 – Manuellen/automatischen Sync konfigurierbar machen
**Abhängigkeit:** C01–C07.  
**Prompt:** Ergänze die Auswahl **Manuell** oder **Automatisch**. Manuell synchronisiert nur nach Nutzeraktion. Automatisch wird erst nach Opt-in eingerichtet, nutzt eindeutige WorkManager-Arbeit, Netzwerkbedingungen und begrenzte Wiederholungen und lässt sich deaktivieren. Status muss den echten Workerzustand wiedergeben: letzter Erfolg/Fehler, Fortschritt und nötige Neuautorisierung. Kein Upload bei fehlender Konfiguration oder unverschlüsseltem Payload. **Abnahme:** UI-/Worker-Tests für beide Modi, Opt-in/Opt-out, Vault-Lock, Abbruch, Neuautorisierung und Netzwechsel.
## C09 – Cloud-End-to-End-Tests
**Abhängigkeit:** C02–C08, jeweils nur für ausgewählte Provider.  
**Prompt:** Ergänze echte Worker-/Provider-Integrationstests für Upload, Resume, 401, 429/Retry-After, 5xx, Offline/Netzwechsel, falschen Hash, gesperrten Vault, fehlende Konfiguration, Abbruch und Prozessneustart. Markiere nicht verfügbare Live-OAuth-/Providerprüfungen klar als offen. **Abnahme:** Testprotokoll trennt Mock-, WorkManager-, Integration- und reale Providerprüfung.
---
# DB – Room, FTS, Migrationen und Finanzbeträge

## DB01 – Room-v9-Baseline sichern
**Prompt:** Prüfe das tatsächlich aus `AppDatabase`, Entities, Indizes, Foreign Keys, FTS-Tabellen/Triggern und den Migrationen 1→9 generierte Room-Schema v9. Exportiere nur das Schema, das der aktuelle Code tatsächlich erzeugt; fehlende historische Versionen niemals erfinden. Vergleiche die generierte JSON-Datei mit den tatsächlichen Tabellen/Indizes/Foreign Keys/FTS-Objekten und dokumentiere Abweichungen. **Abnahme:** die tatsächliche v9-Baseline wurde aus dem Build gewonnen, manuell geprüft und als Schemaartefakt versioniert; keine Produktdaten oder Migrationen verändert.

## DB02 – Schemaexport und Migrationstest-Infrastruktur
**Abhängigkeit:** DB01.  
**Abhängigkeit:** DB01 und die tatsächlich geprüfte v9-JSON.  
**Prompt:** Stelle sicher, dass `exportSchema=true` und KSP-`room.schemaLocation` reproduzierbar konfiguriert bleiben. Füge ausschließlich die tatsächlich durch den Build erzeugte und geprüfte v9-JSON hinzu. Richte danach `MigrationTestHelper`-Infrastruktur ein, ohne fachliche Migrationen umzusetzen. Historische fehlende Versionen dürfen nur aus echten veröffentlichten Schemata oder klar bezeichneten Fixtures stammen. **Abnahme:** Build generiert die erwartete v9-Datei reproduzierbar, die Datei ist im Repository versioniert und ein MigrationTestHelper-Smoke-Test läuft; nicht ausgeführte Checks bleiben ausdrücklich offen.

## DB03 – Destruktiven Downgrade-Fallback entfernen
**Prompt:** Prüfe tatsächliche `fallbackToDestructiveMigrationOnDowngrade`-/Destructive-Migration-Nutzung. Entferne unsichere Löschpfade. Nicht unterstützter Downgrade muss in einen erklärbaren Fehler-/Recovery-Zustand führen, ohne eine leere Datenbank als Erfolg anzulegen. Keine Migration vorhandener Nutzerdaten in diesem Schritt. **Abnahme:** Test beweist, dass unbekannte Downgrades DB-Inhalt nicht löschen.

## DB04 – Migrationstests für bestehende Schemaübergänge
**Abhängigkeit:** DB01/DB02.  
**Prompt:** Erstelle `MigrationTestHelper`-Tests für jeden tatsächlich unterstützten historischen Übergang, mindestens 8→9, sofern diese Migration real vorliegt. Befülle jede Fixture mit repräsentativen synthetischen Dokumenten, Custom Fields und Finanzdatensätzen; vergleiche Daten vor/nach Migration und prüfe Indizes, Foreign Keys und FTS. Keine Migration ändern, nur um Tests „grün“ zu bekommen. **Abnahme:** Instrumentierte Tests tatsächlich ausführen bzw. Laufzeit/Umgebung nennen.

## DB05 – FTS-Trigger konsistent machen
**Prompt:** Isoliere `documents_fts` und zugehörige Trigger. Teste Einfügen, Titel-/OCR-Update, Delete, Soft-Delete und Migration; korrigiere nur nachgewiesene Inkonsistenzen. FTS-Dokumentzahl und Inhalt müssen der definierten Suchsemantik entsprechen. Bei genehmigter DB-Verschlüsselung darf kein zusätzlicher unverschlüsselter FTS-Nebenindex entstehen. **Abnahme:** Datenbanktests zeigen konsistenten Index nach jedem Ereignis.

## DB06 – Suchabfragen und Eingabevalidierung
**Abhängigkeit:** DB05.  
**Prompt:** Prüfe Prefix-Matching und FTS-Abfragen bei Umlauten, Sonderzeichen, Anführungszeichen, Operatoren und leeren Strings. Entferne den teuren/unsicheren `LIKE`-Fallback über `ocrText`, sofern FTS die definierte Suche übernehmen kann; begründe jede Abweichung. Parameterisieren statt SQL aus Nutzereingaben zusammenzusetzen. **Abnahme:** Tests für Syntax-/Injektionszeichen, keine Klartext-Nebenindizes, korrektes Suchverhalten.

## DB07 – Schlanke Dokumentlisten-Projektion
**Prompt:** Führe eine `DocumentListItem`-ähnliche Projektion nur mit tatsächlich für Listen benötigten Feldern ein. Listen-, Dashboard- und Explorerabfragen dürfen `ocrText` nicht laden; Detail-/Volltextansichten nutzen dedizierte Queries. Filter-/Sortiersemantik nicht verändern. **Abnahme:** DAO-/Mapper-Tests und Queryprüfung zeigen, dass OCR-Text nicht im Listenergebnis materialisiert wird.

## DB08 – Paging separat einführen
**Abhängigkeit:** DB07.  
**Prompt:** Integriere Paging 3 nur in die Dokumentlisten, die von der Projektion aus DB07 profitieren. Nutze Room-`PagingSource`, stabile Sortierung/Filter und sauberes ViewModel-Caching. Andere Screens und Filter nicht nebenbei umbauen. **Abnahme:** Tests für Pagingseiten, Sortierung, Filterwechsel und Aktualisierungen; vorhandenes Listenverhalten bleibt erhalten.

## DB09 – `REPLACE`-Semantik auditieren und korrigieren
**Prompt:** Suche gezielt nach `OnConflictStrategy.REPLACE` und SQL-`REPLACE` in Datenbankpfaden. Ersetze sie nur, wenn unbeabsichtigtes Delete+Insert bzw. Verlust von IDs/FKs/abhängigen Werten nachgewiesen ist, durch `@Upsert` oder gezielte Insert-/Update-Logik. Beabsichtigte Replace-Semantik dokumentieren und testen. **Abnahme:** gezielte Konflikttests für jede geänderte DAO-Methode.

## DB10 – Foreign-Key-Migration einzeln durchführen
**Abhängigkeit:** DB02/DB04; nur wenn Schema-Audit das bestätigt.  
**Prompt:** Erhöhe die Room-Version exakt um einen Schritt und setze nur die genehmigten Foreign Keys für `document_custom_field_values` um. Zähle vor Migration verwaiste Zeilen und behandle sie nach einer dokumentierten Regel; keine stillschweigende Löschung. Falls die reale aktuelle Version nicht 9 ist, ermittle zuerst den nächsten freien Versionsschritt statt die Zahl blind zu übernehmen. **Abnahme:** Migrationstest mit verwaisten/gültigen Zeilen, ON DELETE-Verhalten und unveränderten unabhängigen Datensätzen.

## DB11 – Geldbeträge als ganzzahlige Cent migrieren
**Abhängigkeit:** DB04; eigene, sequenzielle Schema-Migration nach allen vorangegangenen Migrationen.  
**Prompt:** Stelle Finanzbeträge im Storage-/Domain-Modell auf `Long`-Cent um. Definiere Rundungsregel fachlich (z. B. `BigDecimal`/HALF_UP) und migriere alte `REAL`/`Double`-Werte reproduzierbar; kein unkontrolliertes `Double * 100`. Ändere nur Betragsspeicherung und zwingend abhängige Mapper. Parser, UI, Bankabgleich und GiroCode schrittweise in Folgeaufträgen behandeln, sofern sie nicht in diesem eng begrenzten Schritt nötig sind. **Abnahme:** Migrationstests für positive/negative Werte, Null, Rundungsgrenzen und große Werte.

## DB12 – SQLCipher für die vollständige Room-Datenbank umsetzen
**Abhängigkeit:** D02, DB01–DB04 und D03.  
**Prompt:** Implementiere den in D02 festgelegten, aktuell unterstützten SQLCipher-/Room-Aufbau. Alle Datenbanköffnungen müssen denselben sicheren KeyProvider verwenden; kein passwortloser Notfallzugriff und kein Fallback auf unverschlüsseltes SQLite. Prüfe WAL/SHM, FTS, Migrationen, native ABIs und Start-/Lock-Lifecycle. Bei Sperrung DB-Verbindungen schließen und flüchtige Schlüssel so weit wie möglich entfernen. **Abnahme:** echte Room-/SQLCipher-Integrationstests für Öffnen, falschen Schlüssel, Neustart, Sperren/Entsperren und FTS; Geräte-/ABI-Prüfungen getrennt dokumentieren.
## DB13 – Bestehende Datenbank in SQLCipher migrieren
**Abhängigkeit:** DB12 vollständig getestet.  
**Prompt:** Implementiere sicheren Bestandstransfer der bisherigen Datenbank in SQLCipher. Original bis zur Verifikation erhalten; Migration prüft Schema, Zeilenzahlen, Foreign Keys, FTS und repräsentative Inhalte. Während der Entwicklung darf ein dokumentierter Clean-Install/Datenreset verwendet werden, da keine produktiven Nutzerdaten erhalten werden müssen; vor Release muss ein echter Upgrade-/Rollback-Pfad getestet sein. Nie auf Klartext-SQLite zurückfallen. **Abnahme:** Tests für Abbruch, wenig Speicher, falschen Schlüssel, beschädigte Quelle, Rollback und vollständiges Wiederöffnen nach Neustart.
## DB14 – Listen-/Suchperformance messen
**Abhängigkeit:** DB05–DB08 und DB12/DB13, soweit die genehmigte DB-Verschlüsselung betroffen ist.  
**Prompt:** Erzeuge einen synthetischen Testbestand mit 10.000 Dokumenten und je etwa 10 KB OCR-Text. Miss vor/nach den Änderungen Listenaufbau, Paging, Suche, Heap und Datenbankgröße. Dokumentiere Gerät/Emulator, API-Level, Laufzahl und Messmethode. Keine Tuningänderung ohne Messbasis. Prüfe insbesondere, dass Listenabfragen `ocrText` nicht materialisieren und dass FTS unter der genehmigten DB-Architektur läuft. **Abnahme:** reproduzierbare Kennzahlen und Vergleichswerte; keine bloße Aussage „fühlt sich schneller an“.

---

# R – Architektur-Refactoring (erst nach Stabilisierung der Sicherheits-APs)

## R01 – Charakterisierungstests und Flow-Inventar (AUDIT)
**Prompt:** Inventarisiere die relevanten Nutzerflows und aktuellen sichtbaren Verhaltensweisen von Scanner/Import, DMS/Explorer, Finance, Sync/P2P, Security/Lock, LLM, Settings und Dashboard. Ergänze nur gezielte Charakterisierungstests für einen klar gewählten ersten Bereich; keine großen Refactorings. Lege eine Reihenfolge mit Abhängigkeiten und unverändert zu haltenden Verhaltensweisen an. **Abnahme:** Screen-/Flow-Liste und grüne Tests für den ausgewählten Bereich.

## R02 – DI-Technologie als ADR
**Prompt:** Prüfe den bestehenden Gradle-/Compose-Aufbau und entscheide in einer ADR zwischen Hilt, Koin oder einer bereits vorhandenen Lösung. Berücksichtige Testbarkeit, Buildkomplexität und Lebenszyklus. Noch keine Massenmigration und keine Entfernung bestehender Singletons. **Abnahme:** konkrete Wahl, schrittweiser Migrationsplan und Teststrategie.

## R03 – DI-Grundlage für Datenbank und Repositories
**Abhängigkeit:** R02.  
**Prompt:** Führe die freigegebene DI-Grundlage nur für `AppDatabase`, DAOs und einen abgegrenzten Repository-Bereich ein. Bestehende Verbraucher dürfen vorerst über schmale kompatible Adapter laufen. Vermeide doppelte DB-Instanzen. Keine Migration von Security-/Vault-Singletons in diesem Schritt. **Abnahme:** App buildbar, DI-Test und Repository-Faketest; kein breiter Call-Site-Umbau.

## R04 – Vault-/Lock-/Key-Singletons eindeutig verdrahten
**Abhängigkeit:** R02/R03, V/S-APs abgeschlossen.  
**Prompt:** Registriere `VaultSecurityManager`, `LockManager`, `KeyProvider` und `SecretStore` ausschließlich in den genehmigten DI-Scopes. Prüfe über Tests/Logs ohne Secrets, dass keine doppelte Instanz entsteht. Migriere nur direkte Aufrufer in einem ausgewählten Modul; keine Änderung des Sicherheitsverhaltens. **Abnahme:** Identitäts-/Scope-Test und Lock-/Key-Regressionstests grün.

## R05 – Scanner-/Import-ViewModel isolieren
**Abhängigkeit:** R01/R03.  
**Prompt:** Extrahiere ausschließlich Scanner-/Importverhalten aus `DocAnizerViewModel` in ein eigenes Feature-ViewModel mit typisiertem `UiState`/Events. Speicher-/Verschlüsselungsregeln bleiben unverändert und werden über bestehende Services/Repositories aufgerufen. Keine DMS-/Finance-/Sync-Aufteilung in diesem Auftrag. **Abnahme:** Fake-Repository-Tests, Charakterisierungstests und Build grün.

## R06 – DMS-/Explorer-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich DMS-/Explorerzustand und -aktionen in ein eigenes ViewModel. Erhalte Filter, Sortierung, Navigation, Lade-/Fehlerzustände und Liste/Detail-Semantik exakt. Verwende die bereits eingeführten Projektionen/Paging nur dort, wo die entsprechenden DB-APs abgeschlossen sind. **Abnahme:** Charakterisierungstests, Fake-Repository-Tests und Smoke-Test.

## R07 – Finance-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich Finanzzustand/-aktionen in ein eigenes Feature-ViewModel. Ändere keine Rechenregeln oder Währungsdarstellung; bei bereits migrierten Cent-Werten Mapper/Tests verwenden, sonst bestehendes Verhalten beibehalten. **Abnahme:** Tests für Summen, Filter, Monatswechsel und Fehlersituationen bleiben gleich.

## R08 – Sync-/P2P-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich UI-Zustand/-Events von Cloud-Sync und P2P in eigene ViewModels bzw. einen klar abgegrenzten Bereich. Netzwerkoperationen/Worker gehören weiterhin in Services; keine Sockets oder langlebigen Jobs im ViewModel halten. Status nicht als Erfolg darstellen, bevor der Worker vollständig erfolgreich ist. **Abnahme:** Fake-Service-Tests für Fortschritt, Fehler, Abbruch und Neuautorisierung.

## R09 – Security-/Lock-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich Security-/Lock-UI-Zustand in ein eigenes ViewModel. Authentifizierungsautorität bleibt beim LockManager/Vault-Service; das ViewModel darf den Vault nicht durch boolesche UI-Events entsperren. Keine Schlüsselkopien in UI-State. **Abnahme:** Tests für gesperrt/entsperrt, Authfehler, Timeout und Prozessneustart.

## R10 – LLM-/Chat-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich LLM-/Chat-UI-Zustand. Laufzeitmodus und Fallback müssen aus dem freigegebenen LLM-Pfad stammen; keine regelbasierte Antwort als Inferenz kennzeichnen. Keine Dokument-/OCR-Texte in Logs oder dauerhaftem UI-Debugzustand. **Abnahme:** Tests für Modellverfügbarkeit, regelbasierten Modus, Abbruch und Fehler.

## R11 – Settings-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich Einstellungen in ein eigenes ViewModel. Geheimnisse bleiben ausschließlich über SecretStore erreichbar; keine Passwörter/Tokens in `UiState`, DataStore oder UI-Debugausgaben. Bestehende Einstellungswerte und Defaults erhalten. **Abnahme:** Tests für Lesen/Ändern/Neustart und Secret-Redaktion.

## R12 – Dashboard-ViewModel isolieren
**Prompt:** Extrahiere ausschließlich Dashboardzustand in ein eigenes ViewModel. Keine neuen fachlichen Regeln hinzufügen; Daten nur über existierende Repositories abrufen. Nichtkritische Ladeoperationen sauber abbrechen, kritische Security-Initialisierung nicht verzögern. **Abnahme:** Dashboard-Smoke-Test, Lade-/Fehler-/Empty-State-Tests.

## R13 – SettingsRepository zu DataStore migrieren
**Abhängigkeit:** R03/R11 und S09 für Secrets.  
**Prompt:** Migriere nur nicht-sensitive Einstellungen von SharedPreferences zu Preferences oder Proto DataStore. Geheimnisse dürfen nicht nach DataStore kopiert werden; dafür bleibt S08 SecretStore zuständig. Die Migration muss idempotent sein, Altwerte erst nach verifiziertem Schreiben entfernen und Prozessabbruch verkraften. **Abnahme:** Tests für Defaults, vorhandene Werte, zweimaligen Start, Crash während Migration und erhaltene Einstellungen.

## R14 – JSON-Strategie festlegen (AUDIT/GATE)
**Prompt:** Inventarisiere JSON-Verwendung und unterscheide internes/transientes JSON von persistierten oder externen Formaten (Room-Konverter, P2P-Protokoll, Backupmanifest, Einstellungen). Erstelle eine kurze ADR zur bevorzugten Bibliothek (z. B. `kotlinx.serialization`) und Kompatibilitätsregeln. Noch keine globale Ersetzung. **Abnahme:** Formatverzeichnis, Versionsregeln und priorisierte kleine Migrationsliste.

## R15 – Ein internes JSON-Format migrieren
**Abhängigkeit:** R14.  
**Prompt:** Migriere genau EIN in R14 benanntes, internes und nicht persistiertes JSON-Format zur genehmigten Bibliothek. Öffentliche APIs, Datenbankwerte, alte Backups, Manifest- und P2P-Wireformate bleiben unberührt. Füge Roundtrip-/Fehler-/Kompatibilitätstests hinzu. **Abnahme:** nur ein Format betroffen und bestehende Tests grün. Für jedes weitere Format einen neuen Durchlauf erstellen.

## R16 – Zentrales, redigierendes Logging
**Prompt:** Inventarisiere direkte `printStackTrace()`-/`android.util.Log`-Aufrufe und führe eine zentrale Logger-Abstraktion mit Release- und Debugverhalten ein. Redigiere Secrets, OCR-/Dokumenttexte, vertrauliche Dateinamen und URLs mit Zugangsdaten; Telemetrie darf nur nach der realen Einwilligungsentscheidung senden. Migriere nur einen abgegrenzten Bereich pro Lauf. **Abnahme:** Tests für Redaktionsregeln und keine sensiblen Beispieldaten in Testlogs.

## R17 – Optionale Firebase-/Analytics-Initialisierung
**Prompt:** Prüfe den Startpfad für `FirebaseAnalytics.getInstance` und die optionale Google-Services-Konfiguration. Falls Firebase optional bleiben soll, muss fehlende/ungültige Konfiguration Analytics deaktivieren, nicht den Appstart abstürzen lassen. Ereignisse erst nach entsprechender Einwilligung senden. OCR-/Dokumentdaten nie als Analytics-Parameter. **Abnahme:** Build-/Starttest ohne `google-services.json` bzw. ohne initialisierte Standard-App und Consent-Negativtest.

## R18 – `runBlocking` in UI-Pfaden entfernen
**Prompt:** Suche `runBlocking` ausschließlich in UI-/Main-Thread-Pfaden und behebe zuerst genau einen klar abgegrenzten Aufrufpfad. Verwende strukturierten Coroutine-Scope mit klarer Eigentümerschaft/Cancel-Verhalten. Keine großflächige API-Umstellung und keine Verschiebung kritischer Sicherheitsinitialisierung in den Hintergrund. **Abnahme:** Main-Thread-/Abbruchtests und Build grün; für jeden weiteren Pfad neuen Micro-AP-Lauf.

## R19 – `runBlocking` in Netzwerk-/Servicepfaden entfernen
**Prompt:** Suche `runBlocking` ausschließlich in Netzwerk-/Servicepfaden und migriere genau einen ausgewählten Pfad zu suspendierenden APIs/strukturierter Konkurrenz. Ressourcen müssen bei Cancel/Timeout in `finally` geschlossen werden. Ändere nicht gleichzeitig P2P-Protokoll oder Workerarchitektur, falls diese noch nicht stabil sind. **Abnahme:** Timeout-/Cancel-/Ressourcenfreigabetests und Build grün.

## R20 – Startarbeit gezielt aus `Application.onCreate` verschieben
**Prompt:** Prüfe den tatsächlichen Startpfad und verschiebe nur nachweislich nichtkritische Arbeiten wie `SplashSoundSynthesizer.precompute` in einen geeigneten Hintergrundpfad. Kritische Vault-/Lock-/Key-Initialisierung muss abgeschlossen sein, bevor sensible UI sichtbar wird. Keine spekulative Dependency-/Startup-Änderung. **Abnahme:** Start-Smoke-Test, Reihenfolge dokumentiert und Startzeit vorher/nachher gemessen.

## R21 – Eine große Compose-Datei gezielt teilen
**Prompt:** Teile ausschließlich `ui/views/DmsExplorerView.kt` (oder, falls diese bereits geteilt ist, genau eine im Audit ausgewählte Datei) entlang sinnvoller UI-/State-Grenzen in kleinere Composables. Verhalten, Filter, Sortierung, Navigation, Semantik und Berechtigungen bleiben unverändert. Keine rein mechanische Aufteilung auf eine Zielzeilenzahl erzwingen. **Abnahme:** Compose-Preview soweit vorhanden, Charakterisierungstest/Smoke-Test und unverändertes Verhalten.

## R22 – Performance-Baseline vor weiterem Refactoring
**Prompt:** Messe vor weiteren Refactorings Buildzeit, Startzeit und – falls relevant – Compose-Recomposition-/Listen-/Suchperformance. Erzeuge ein reproduzierbares Profil/Testsetup und dokumentiere Gerät/Emulator, API-Level, Datensatz und Messmethode. Keine Tuningänderungen in diesem Schritt. **Abnahme:** Zahlen und Reproduktionsschritte liegen vor; ohne Messbasis keine Behauptung einer Verbesserung.

## R23 – Veraltete Dateien und Dependencies einzeln bereinigen
**Abhängigkeit:** jeweiliger Featurebereich ist getestet und nicht mehr von den Dateien/Dependencies abhängig.  
**Prompt:** Prüfe eine einzelne vermutete tote Datei, Dependency, auskommentierte Gradle-Zeile oder ungenutzte Version-Catalog-Referenz per Code-/Buildsuche. Entferne nur diesen klar belegten Kandidaten pro Lauf, einschließlich `zwyxdl/MainActivity.kt` nur falls nachweislich nicht referenziert. Keine gleichzeitige Massenbereinigung. **Abnahme:** Suchbelege zeigen keine Nutzung mehr; Build und relevante Tests bestehen.

## R24 – Baseline Profile erstellen und Vorher/Nachher vergleichen
**Abhängigkeit:** R22 und abgeschlossene Refactoring-Schritte, deren Performance verglichen werden soll.  
**Prompt:** Erzeuge für die tatsächlich unterstützten kritischen Nutzerflows ein reproduzierbares Baseline Profile und messe Start-/UI-/Listenverhalten mit gleicher Testumgebung wie in R22. Ändere `largeHeap`, Startup-Work, Plugins oder Dependencies nicht ungeprüft. Berichte Verbesserung und Regressionen mit Messdaten; wenn eine Verbesserung nicht nachweisbar ist, sage das explizit. **Abnahme:** Profil, Reproduktionsschritte und Vorher/Nachher-Werte sind dokumentiert.

