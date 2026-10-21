# myDocAnizer-Mobile – Micro-AP-Arbeitsplan

## Statusübersicht

| Kennzahl | Aktueller Stand |
|---|---|
| Micro-APs insgesamt | **119** |
| Einzelstatus dokumentiert | **44** |
| Einzelstatus noch abzugleichen | **75** |
| Dokumentierte Statusverteilung | 1 abgenommen · 7 getestet · 28 Patch erstellt · 5 nicht gestartet · 1 ADR freigegeben · 1 Audit dokumentiert · 1 ADR erstellt |
| Tests | 23 Testklassen inkl. `VaultTwoFactorSecurityTest`; vorhanden heißt nicht automatisch ausgeführt oder bestanden |
| Build/Tests | GitHub Actions Run `39633700992` auf Commit `d509ebb` erfolgreich: `assembleDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest`, APK-Metadatenprüfung und Artefakt-Upload |

Fehlende Einzelstatus sind eine Dokumentationslücke, kein Beweis dafür, dass ein Arbeitspaket nicht umgesetzt wurde.

## Verbindliche Produktvorgaben

| Thema | Festgelegte Anforderung |
|---|---|
| **Optionale Zwei-Faktor-Absicherung** | Nutzer kann sie bei Bedarf aktivieren. Nur als echte kryptografische Absicherung ausliefern; falls sie nicht mit vertretbarem Aufwand sicher umsetzbar ist, zuerst technische Optionen mit Empfehlung vorlegen. |
| **Tresor-Wiederherstellung** | Passwort **oder** Passkey; optional zusätzliche Sicherheitsfragen; optional druckbares Notfalldokument in eindeutig erkennbarer oder unauffälliger Gestaltung. |
| **Vollständige lokale Verschlüsselung** | Gesamte Room-Datenbank, dauerhaft gespeicherte App-Einstellungen und Dokumente verschlüsseln. Suchindizes, Metadaten, Vorschauen, temporäre Dateien und Backups mitprüfen; keine unverschlüsselten dauerhaften Spiegel. |
| **Verbindungen** | App2App, PC2App und Browser2App sollen unterstützt und jeweils auf Transportverschlüsselung, Identitätsprüfung, Kopplung und Angriffsflächen geprüft werden. Kein Klartext-Fallback. |
| **Lokale KI** | Echte native Modellinferenz ist das gewählte Ziel. Regelbasierte Heuristiken dürfen nicht als KI-Inferenz dargestellt werden. |
| **Google Drive** | Vollständige OAuth-Integration für verschlüsselte Backups/Sync. Synchronisationsmodus in der App wählbar: manuell oder automatisch; automatischer Sync ist explizit aktivierbar. Hochgeladen werden ausschließlich clientseitig verschlüsselte Daten. |

## Technische Leitplanken

- Architektur-, Bibliotheks- und Implementierungsentscheidungen treffe ich nach Codeprüfung und dokumentiere sie knapp. Produktentscheidungen werden nicht erneut abgefragt.
- Wenn eine gewählte Funktion mit den erforderlichen Sicherheitsgarantien technisch nicht machbar erscheint, wird sie nicht stillschweigend unsicher umgesetzt oder entfernt. Dann folgt eine verständliche Entscheidungsfrage mit Empfehlung.
- Kein roher DEK in PDFs, QR-Codes, UI oder Logs. Ein bewusst erzeugter, zufälliger Recovery-Schlüssel darf als explizites Notfallgeheimnis exportiert werden; das Dokument ist dann hochsensibel.
- Eine unauffällige Gestaltung eines Notfalldokuments ist nur Tarnung, keine kryptografische Sicherheit. Wer das Geheimnis findet, kann es möglicherweise benutzen.
- Die Testbasis umfasst laut Bericht 22 Testklassen. Der Status einzelner Micro-APs muss zusätzlich anhand echter Implementierung, passender Tests und erforderlicher Geräte-/Integrationstests bewertet werden.

---

## 1. Abhängigkeits- und Freigabeplan

Ein Micro-AP ist erst abgeschlossen, wenn der Patch übernommen, der passende Testlauf ausgeführt und der tatsächliche Status dokumentiert wurde.

### Reihenfolge

1. **M00** – Projekt-/Buildbaseline und Befundinventar.
2. **V01–V18** – Tresor, Schlüssel, optionale Zwei-Faktor-Absicherung und Wiederherstellung.
3. **S01–S10** – Lockzustand und SecretStore. Legacy-Schlüsselzugriff bleibt kompatibel, bis alle Verbraucher einzeln migriert und geprüft sind.
4. **D01–D15** – Datenfluss, Schlüsselarchitektur, Dokumentverschlüsselung, Backup/Restore und Verschlüsselung aller App-Einstellungen.
5. **DB01–DB14** – vollständige SQLCipher-Datenbankverschlüsselung, Migrationen, FTS, Geldbeträge und Performance. Datenbankversionen strikt sequenziell erhöhen.
6. **P01–P13** – sichere App2App-, PC2App- und Browser2App-Verbindungen; alle drei anhand einer gemeinsamen Sicherheitsmatrix prüfen.
7. **C01–C09** – Google-Drive-/WebDAV-Sync. Backupcontainer und sensible Metadaten vor dem Upload clientseitig verschlüsseln; manueller/automatischer Sync ist eine Nutzeroption.
8. **L01–L11A** – echte native lokale Inferenz ist gewählt. L08B ist kein regulärer Pfad und darf nicht ohne technischen Befund und erneute ausdrückliche Produktfreigabe anstelle der nativen Engine ausgeführt werden.
9. **R01–R24** – Refactoring nach Stabilisierung der Sicherheitsgrenzen, Persistenzformate und Integrationspfade.

### Technische Freigabe- und Stopp-Punkte

- **V05/V06B – optionale Zwei-Faktor-Absicherung:** Das Ziel ist entschieden; offen ist nur die technische Machbarkeit eines echten kryptografischen zweiten Faktors. Eine sechsstellige TOTP-Prüfung darf nicht als Schutz gelten, wenn ein Offline-Angreifer dadurch den DEK durch Ausprobieren freischalten kann.
- **V09/V16–V18 – Wiederherstellung:** Passwort- und Passkey-Pfade müssen auf unterstützten Geräten/Authenticators tatsächlich wiederherstellbar sein. Ein Passkey, der nur die Identität bestätigt, aber keinen geeigneten Schlüssel für die Entschlüsselung liefert, ist kein eigenständiger Verschlüsselungsschlüssel.
- **D02/DB12–DB13 – Datenbank:** vollständige SQLCipher-Verschlüsselung ist festgelegt. Vor Migration müssen Room-Kompatibilität, Schlüsselbereitstellung, native ABIs, WAL/SHM, FTS, Backups und Rückkehrpfad technisch belegt sein.
- **P01/P12 – Verbindungen:** kein Kanal gilt als sicher, nur weil TLS-Klassen oder Auditdokumente existieren. Die echten Produktpfade sind für App2App, PC2App und Browser2App mit Negativtests zu belegen.
- **L01/L02 – lokale KI:** erst Bestandsaudit, dann nativen Engine-Pfad umsetzen. Bei einem konkreten Build-/Lizenz-/Geräteblocker Befund samt Alternativen vorlegen, statt kommentarlos auf Heuristiken zurückzufallen.
- **C01/C04–C09 – Google Drive:** echte Autorisierung, minimale passende OAuth-Berechtigungen, verschlüsselte Nutzdaten und wählbarer manueller/automatischer Sync. Erfolg erst nach vollständiger Übertragung und Integritätsprüfung melden.

---

## 2. Micro-APs

# M00 – Baseline und Bestandsinventar (AUDIT, keine Produktänderung) — **[ERLEDIGT]**

**Prompt:** Erfasse lesend die im Workspace vorhandene Dateibasis und führe – sofern in der Umgebung verfügbar – `./gradlew assembleDebug testDebugUnitTest` sowie vorhandene lokale Checks aus (andernfalls als nicht ausführbar kennzeichnen). Erstelle `docs/security/micro-ap-baseline.md` mit tatsächlichem Buildstatus, Grenzen der Umgebung, fehlenden/ungenutzten Tests, relevanten Modulen und offenen Befunden aus den ursprünglichen acht APs. Untersuche nur lesend und füge keine Produktlogik hinzu. Verifiziere insbesondere die aktuellen Implementierungen von VaultHeader/KDF/Recovery, `DocumentStorageService`, P2P-Server, CloudSync, Room-Version, LLM/JNI und Analytics. Markiere jeden Befund als bestätigt, widerlegt oder noch offen und nenne Pfad/Beleg. **Abnahme:** Auditdatei ist reproduzierbar und ehrlich; keine Anwendungslogik geändert. Fehlende Tool-Unterstützung oder Buildfehler werden dokumentiert, nicht kaschiert.

---

# V – Tresor, Schlüsselableitung und Recovery

## V01 – V2-only Vault-Krypto-Inventar und Negativtests
**Prompt:** Inventarisiere ausschließlich den V2-Header, die V2-Argon2id-/AEAD-Ableitung, Unlock-/Recovery-Pfade, Biometrie, Keyfile/2FA, TOTP, `activeDek` und alle Aufrufer. Keine V1-Fixtures, V1-Deserialisierung, V1-Rückfalllogik oder Migration. Lege synthetische V2-Tests für falsches Passwort, ungültige Header und manipulierte verschlüsselte DEK-Daten an. **Abnahme:** nur V2 wird gelesen und geschrieben; ungültige/alte Formate scheitern geschlossen.

## V02 – Geprüfte Argon2id-Implementierung — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V01.  
**Prompt:** Verwende ausschließlich die versionsfixierte Argon2id-Implementierung im V2-Pfad. Für V2 gelten mindestens `m=19 MiB`, `t=2`, `p=1`; Einheiten und API-Semantik verifizieren. Begrenze/validiere Parameter vor der speicherintensiven Ableitung und schütze vor parallelen RAM-Spitzen. Füge bekannte Referenzvektoren und Negativtests hinzu. Entferne Alt-Ableitungspfade, die nur dem alten Header dienten. **Abnahme:** korrekte Referenzvektoren; ungültige/überzogene Parameter werden früh abgelehnt; kein V1-KDF-Pfad bleibt in der Tresorlogik.

## V03 – VaultHeader v2: Datenmodell und Parser — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V01, V02.  
**Prompt:** V2 ist das einzige unterstützte persistierte Vault-Format. Der typisierte Header samt strengem Parser/Validator muss Formatversion, KDF-/Algorithmus-ID, Parameter, Salt, IV/Nonce- und Taglängen, Vault-ID sowie Recovery-/Biometrie-/Faktor-Metadaten validieren. Unbekannte Versionen, abgeschnittene Daten, unmögliche Längen und ungültige Parameter müssen abgewiesen werden. Der Parser darf keine V1-Daten lesen oder rückwärtskompatible Defaults dafür einsetzen. **Abnahme:** V2-Roundtrips und Negativtests; keine V1-Referenztests oder V1-Lesepfade.

## V04 – Header-Authentifizierung und AAD — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V03.  
**Prompt:** Implementiere ausschließlich die AEAD-Operationen zum Verpacken/Öffnen des zufälligen 256-Bit-DEK im v2-Header. Nutze geprüfte AES-GCM/JCA- oder gleichwertige AEAD-Primitiven korrekt; pro Verschlüsselung neuen zufälligen Nonce/IV generieren und niemals wiederverwenden. Lege fest und teste, welche Vault-ID-, Versions- und KDF-Metadaten als AAD gebunden werden. Falscher Schlüssel, falscher AAD oder veränderter Tag müssen fehlschlagen, ohne Teilresultat auszugeben. Keine automatische Migration. **Abnahme:** gültiger Roundtrip; Manipulations- und Nonceformattests; keine geheime Materialausgabe in Logs.

## V05 – Optionale echte Zwei-Faktor-Absicherung (ARCHITEKTUR) — **[ADR FREIGEGEBEN]**
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
## V07 – BIP-39-Mnemonic-Validierung — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Prompt:** Isoliere die BIP-39-Funktionalität und prüfe Wortliste (genau 2048 Wörter), Entropie, Checksumme und Mnemonic-Validierung anhand offizieller Referenzvektoren. Korrigiere ausschließlich Fehler in diesem Modul; wenn Eigenbau-Logik die Referenzvektoren nicht zuverlässig erfüllt, ersetze sie durch eine geprüfte, kompatible Bibliothek. Erzeuge nur synthetische Testdaten. **Nicht tun:** Shamir, PDF/QR oder Headerformat verändern. **Abnahme:** gültige/ungültige Mnemonics, Grenzfälle und offizielle Vektoren sind getestet.

## V08 – Shamir-Share-Konsistenz — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Prompt:** Prüfe den vorhandenen Shamir-2-aus-3-Code und seine Hashspeicherung. Stelle durch Tests sicher, dass die drei gehashten Shares exakt dieselben Bytefolgen sind, die später an den Nutzer ausgegeben werden, und dass jedes zulässige Paar den ursprünglichen synthetischen DEK reproduziert. Verwende offizielle oder klar bezeichnete Referenzvektoren. Ersetze den Eigenbau, falls er nicht belastbar gegen Referenzen besteht. **Nicht tun:** Recovery-PDF oder Verschlüsselungsformat in diesem Schritt ändern. **Abnahme:** jedes gültige Paar rekonstruiert exakt denselben Schlüssel; vertauschte, gekürzte und manipulierte Shares scheitern.

## V09 – Recovery-Format und Wiederherstellungsoptionen festlegen (ADR)
**Prompt:** Definiere ein versioniertes Wiederherstellungskonzept für drei getrennte Wege: (1) Tresorpasswort **oder** Passkey; (2) optional zusätzlich konfigurierte Sicherheitsfragen für den Passwort-/Passkey-Pfad; (3) ein optionales, eigenständig nutzbares Notfalldokument mit eigenem hochentropischem Recovery-Geheimnis. Das Notfalldokument muss den Tresor ohne das ursprüngliche Passwort/Passkey wiederherstellen können; Sicherheitsfragen dürfen diesen unabhängigen Notfallweg nicht versehentlich blockieren. Erstelle klar erkennbare und unauffällige Druckvarianten. Definiere KDF, AEAD, AAD, Nonce, Salt, Version, Prüfsumme, Normalisierung, Fehlerbehandlung und Kompatibilität. Beurteile die Passkey-PRF-/Schlüsselverfügbarkeit bei Gerätewechseln; ein bloßer Passkey-Login ohne nutzbares Schlüsselmaterial reicht nicht. Sicherheitsfragen dürfen keine alleinige starke Verschlüsselung versprechen; geringe Entropie und Offline-Risiken müssen in UI und Tests berücksichtigt werden. Tarnung ist keine kryptografische Sicherheit. **Abnahme:** ADR deckt alle drei Wege, Wiederherstellung auf neuem Gerät, Verlustszenarien und Grenzen ab.
## V10 – Versionierten Recovery-Payload-Kern umsetzen
**Abhängigkeit:** V09.  
**Prompt:** Implementiere Encode/Decode für das freigegebene Recovery-Payload als isolierte Komponente. Erzeuge ein zufälliges, ausreichend hochentropisches Recovery-Geheimnis, das einen verpackten Tresorschlüssel freigeben kann; exportiere niemals den rohen DEK. Nutze etablierte KDF-/AEAD-Bibliotheken, versionsgebundene AAD, sichere Nonces, Salt, Längenlimits und Integritätsprüfung. Unterstütze zuerst den passwortbasierten Payload-Pfad; Passkey-PRF und Sicherheitsfragen werden separat in V16/V17 integriert. Falsches Geheimnis, manipulierte Bytes, ungültiger Tag, unbekannte Version oder abgeschnittener Payload müssen sicher scheitern. **Abnahme:** bytegenaue Roundtrips, Negativtests und keine Secrets in Meldungen/Logs.
## V11 – Standardkonforme QR-Kodierung — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
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
**Prompt:** V2 ist das einzige Persistenz- und Laufzeitformat. Ersteinrichtung, Entsperren, Passwortwechsel, Biometrie, Keyfile/2FA, TOTP und Recovery müssen V2 verwenden. Entferne vollständig V1-Header, -Serialisierung, -Parser, -KDF-/Unlockpfade, Rückfalllogik, V1-Fixtures/-Tests und jede Staging-/Promotionslogik für eine Migration. Keine V1-Migration durchführen, vorbereiten oder als Fallback beibehalten. Alte V1-Daten werden nicht übernommen und dürfen vom Code nicht als Tresor erkannt werden. Beschädigte V2-Header müssen geschlossen fehlschlagen und dürfen nicht stillschweigend überschrieben oder als entsperrt behandelt werden. **Abnahme:** keine V1-Codepfade; V2-Funktionstests und vollständiger Build/Testlauf bestehen.

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

## S01 – Lock- und Secret-Inventar (AUDIT) — **[ERLEDIGT – AUDIT DOKUMENTIERT]**
**Prompt:** Inventarisiere alle App-/Vault-Lock-Flags, `unlockApp`, `unlockVault`, `lockVault`, automatische Sperrung, Biometrie, `activeDek`-Zugriffe und geheime Werte in Prefs/DB/Dateien. Zeichne den bestehenden Zustandsfluss mit Aufrufstellen auf und schlage eine eindeutige Trennung bzw. Beziehung zwischen App-Lock und Vault-Lock vor. Noch keine produktive Änderung. **Abnahme:** Datei `docs/security/lock-secret-inventory.md`; jeder gefundene Geheimnisspeicher und jeder Unlock-Pfad ist referenziert.

## S02 – LockManager als Zustandsautomat — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** S01.  
**Prompt:** Implementiere einen testbaren `LockManager` mit typisiertem, zentralem Zustand und ausdrücklich erlaubten Übergängen. Beginne beim Prozessstart fail-closed, wenn ein Vault eingerichtet ist. Activity/ViewModel dürfen keine konkurrierenden Wahrheits-Flags mehr für denselben Vault-Lock erzeugen; halte vorhandene APIs über Adapter kompatibel. Noch keine automatische Hintergrundsperre und keine SecretStore-Migration. **Abnahme:** reine Unit-Tests der Zustandsübergänge; kein unkoordinierter zweiter Manager.

## S03 – Auto-Lock-Lifecycle — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** S02.  
**Prompt:** Verbinde den LockManager mit `ProcessLifecycleOwner` und der vorhandenen Auto-Lock-Einstellung. Messe Zeit mit `SystemClock.elapsedRealtime`; definiere und teste `0` als sofortige Sperre entsprechend der Produktsemantik. Bei Rückkehr aus dem Hintergrund muss vor Anzeige sensibler Inhalte der aktuelle Lockzustand abgefragt werden. Keine Speicherung von DEK oder Passphrase, um die Rückkehr zu beschleunigen. **Abnahme:** Tests für ON_STOP/ON_START, Timeoutgrenze und Prozessneustart; keine UI-Freigabe vor Lockprüfung.

## S04 – Unlock-Bypässe entfernen — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** S02 und V13.  
**Prompt:** Verfolge `AppLockScreen`, `unlockApp()` und alle Aufrufer. Entferne Pfade, die ohne erfolgreiches, überprüfbares Authentifizierungsresultat entsperren – insbesondere Null-Activity-/Boolean-Default-/UI-only-Pfade. Erfolg muss aus dem tatsächlich erfolgreichen Passwort-/Vault- oder `BiometricPrompt.CryptoObject`-Vorgang kommen. Ändere nur Authentifizierungsübergänge und ergänze Negativtests. **Abnahme:** kein erfolgreicher Unlock bei abgebrochener, fehlgeschlagener oder fehlender Authentifizierung.

## S05 – DEK-Leases an Lockstatus binden — **[IMPLEMENTIERUNG ERLEDIGT – TESTLAUF OFFEN]**
**Abhängigkeit:** V04 und S02.  
**Prompt:** Ersetze unkontrollierte öffentliche DEK-Getter schrittweise durch eine begrenzte `withUnlockedKey`-/Key-Lease-Schnittstelle oder ein gleichwertig überprüftes Modell. Schlüsselmaterial darf nur während gültigem Unlock-Zustand genutzt werden; mutable Kopien soweit technisch möglich nach Gebrauch bereinigen. Synchronisiere zusammengesetzte Operationen mit `Mutex`/gleichwertiger Koordination. Definiere, was bei Lock während einer Dateioperation geschieht. Migriere nur die Aufrufstellen, die für den Build dieses Schritts nötig sind, und liste verbleibende Verbraucher auf. **Abnahme:** Tests, dass gesperrter Vault keine Entschlüsselung erlaubt und paralleler Lock/Unlock keinen alten Schlüsselzugriff hinterlässt.

## S06 – Fehlversuche und Backoff — **[IMPLEMENTIERUNG ERGÄNZT – TESTLAUF OFFEN]**
**Abhängigkeit:** S02.  
**Prompt:** Implementiere nur die gemeinsame Backoff-Logik für Passwort-/TOTP-/Recovery-Fehlversuche, wobei vorhandene Authentifizierungswege nicht umgangen werden dürfen. Schreibe persistent und atomar die notwendigen Zähler/Deadline-Informationen; nutze monotone Zeit während einer Sitzung und definiere konservatives Verhalten nach Prozess-/Geräteneustart. Teste die festgelegte Folge: nach fünf Fehlversuchen 30 Sekunden, danach 1, 5 und 15 Minuten. **Abnahme:** Grenzwerte, parallele Fehlversuche, Neustart und Restzeit getestet; Dokumentation, dass lokaler Backoff Defense-in-Depth ist.

## S07 – FLAG_SECURE und sensitive Screens — **[IMPLEMENTIERUNG VORHANDEN – GERÄTETEST OFFEN]**
**Prompt:** Setze `FLAG_SECURE` rechtzeitig vor dem Rendern sensibler Inhalte und steuere Debug-Ausnahmen ausschließlich über ein explizites Build-Flag bzw. `BuildConfig.DEBUG`, nicht über Emulatorerkennung. Sorge dafür, dass Recents/Screenshots und offene Detailansichten nach Sperrung keine sensiblen Inhalte mehr zeigen. Keine Navigation oder Sicherheitsarchitektur nebenbei refactoren. **Abnahme:** dokumentierter manueller Testplan für Screenshot, Recents, App-Hintergrund, Activity-Neustart und Lock auf einer offenen Detailseite.

## S08 – SecretStore-Grundkomponente — **[CI-TESTS BESTANDEN – PHYSISCHER GERÄTETEST OFFEN]**
**Prompt:** Baue einen testbaren `SecretStore` für langlebige Geheimnisse. Verwende AES-GCM mit einem nicht exportierbaren Android-Keystore-Key, eindeutiger Nonce, Formatversion und Integritätsprüfung. Dokumentiere die Authentifizierungsbindung des Keys und API-Level-Fallbacks. Ungültiger Tag oder Keystore-Fehler darf niemals Klartextfallback auslösen. In diesem Schritt nur API und Kernkomponente samt synthetischen Tests erstellen; bestehende Prefs noch nicht migrieren. **Abnahme:** Roundtrip, falscher Tag, fehlender/invalidierter Key und Fehlerpfade getestet.

## S09 – Legacy-Secrets verlustfrei migrieren — **[IN ARBEIT – sync_master_password und WebDAV-Passwort verschlüsselt; CI erfolgreich]**
**Abhängigkeit:** S08.  
**Prompt:** Migriere alle in S01 konkret aufgelisteten und durch den Code-Audit gefundenen Klartext-Secrets in den SecretStore. Der Audit hat zusätzlich `cloud_sync_webdav_pass` als Klartextpräferenz identifiziert. Pro Wert: erkennen, verschlüsselt schreiben, entschlüsselten Roundtrip verifizieren und erst danach Original entfernen. Migration muss idempotent sein und bei Fehler den ursprünglichen Wert erhalten, ohne unkontrollierte doppelte Klartextkopien zu hinterlassen. `sync_master_password` erst entfernen, wenn die in D/P/C betroffenen Verbraucher nachweislich umgestellt sind; sonst nur dokumentierte Zwischenstufe mit klarer Restabhängigkeit. **Abnahme:** erster Start, zweiter Start, Prozessabbruch und Keyfehler getestet. Instrumentationstests müssen insbesondere idempotente Wiederinitialisierung und den Erhalt des Legacy-Werts bei fehlgeschlagener SecretStore-Schreiboperation nachweisen.

## S10 – Externe Navigationsextras validieren — **[IMPLEMENTIERT – BUILD/UNIT-TESTS VOM NUTZER BESTÄTIGT]**
**Prompt:** Prüfe die exportierte `MainActivity` sowie Widget-/Intent-Aufrufstellen. Validiere `EXTRA_START_TAB`, `EXTRA_OPEN_ACTION`, URIs und weitere externe Extras gegen feste Whitelists. Während der Vault gesperrt ist, dürfen externe Aktionen höchstens sicher zwischengespeichert und erst nach erfolgreichem Unlock verarbeitet werden. Ungültige Extras ignorieren und nicht später wiederverwenden. **Abnahme:** Tests für gültige, unbekannte, fehlerhafte und während Lock eintreffende Extras; kein Lock-Bypass.

**Umsetzung direkt auf `main`:** `ExternalIntentPolicy.consume()` liest nur unterstützte Navigationswerte aus, verwirft unbekannte Extras und entfernt anschließend alle Extras, `data` und `clipData` aus dem Intent. `MainActivity` konsumiert den Payload **vor** der Lock-/Unlock-Prüfung; bei gesperrtem Zustand bleibt daher kein externer Befehl zur späteren Wiederholung an der Activity hängen. Ein Deep-Link-/URI-Schema wird derzeit nicht unterstützt und wird verworfen. `ExternalIntentPolicyInstrumentedTest` deckt gültige Werte, unbekannte Extras, ungültige Werte, falsch typisierte Boolean-Extras und das Entfernen von URI-/ClipData ab. Der Nutzer bestätigt erfolgreichen Build, `:app:testDebugUnitTest` und App-Start im Streaming-Emulator. Ein separater `connectedDebugAndroidTest`-Lauf sowie ein gezielter Widget-Cold-Start-/Lock-Replay-Test wurden in der Rückmeldung nicht ausdrücklich benannt und bleiben als spezifische Abnahme offen.
---
# D – Dokumentdateien, Index, Backup und Restore

## D01 – Datenfluss-Inventar (AUDIT)
**Prompt:** Verfolge lesend sämtliche PDF-/Bild-/Scan-/OCR-/Viewer-/Share-/Export-/P2P-/Cloud-/Backup-/Restore-/Cachepfade sowie Speicherorte und Datenbankfelder mit sensiblen Metadaten. Markiere pro Pfad, ob Inhalt/Index/Metadaten derzeit in Klartext oder verschlüsselt vorliegen und welcher Aufrufer `isEncrypted`/`passwordKey` steuert. Keine Produktänderung. **Abnahme:** `docs/security/document-data-flow.md` mit Pfad- und Klassenbelegen, inklusive benutzerdefinierter Speicherorte.

## D02 – Vollständige Datenbankverschlüsselung (SQLCipher) technisch festlegen
**Produktentscheidung:** vollständige Datenbankverschlüsselung ist gewählt.  
**Prompt:** Prüfe den tatsächlichen AppDatabase-/Room-2.7-Aufbau, Öffnungspfade, FTS, WAL/SHM, Export/Restore und Versionskatalog. Prüfe aktuelle SQLCipher-for-Android-Integration auf Room-Kompatibilität, minSdk 24, native ABIs, 16-KB-Kompatibilität, Wartung und Lizenz. Erstelle eine technische ADR mit konkreter Bibliotheksversion, Schlüsselbereitstellung, FTS-/WAL-Verhalten, Migration, Tests und Rollback. Feldverschlüsselung ist nicht der gewählte Ersatz. **Abnahme:** nachweislich kompatibler Weg für vollständig verschlüsselte Room-Datenbank; kein Rückfall auf unverschlüsseltes SQLite.
## D03 – Gemeinsame Schlüsselarchitektur für DB, Dokumente, Settings und Backups — **[TEILIMPLEMENTIERT – TESTLAUF OFFEN]**
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
## D11 – Restore: Archive und Pfade validieren — **[PATCH ERSTELLT – CI OFFEN; INDEX-PREFLIGHT UND IMPORTLIMIT ERGÄNZT]**
**Abhängigkeit:** D10.  
**Prompt:** Implementiere ausschließlich die Vorabvalidierung beim Restore. Normalisiere Pfade und erzwinge, dass jeder Eintrag kanonisch im Zielverzeichnis verbleibt. Lehne absolute Pfade, `..`, Separatorvarianten, kodierte Traversalversuche, doppelte IDs, übergroße Einträge, zu viele Einträge und unzulässige Kompressionsverhältnisse ab. Daten nicht vor Abschluss der Validierung in die produktive Datenbank übernehmen. **Abnahme:** Zip-Slip-/Zip-Bomb-/Traversal- und Größenbegrenzungstests.

## D12 – Restore: atomarer Commit und verständlicher Report
**Abhängigkeit:** D11.  
**Prompt:** Sorge dafür, dass Restorevalidierung und Schreiben transaktional bzw. über einen Staging-Bereich erfolgen. Ein beschädigter AEAD-Tag, fehlendes Manifest oder fehlerhafte Metadaten darf keinen Teilimport in der Produktivdatenbank hinterlassen. Vermeide blindes `REPLACE`; definiere Konfliktregeln über neue IDs/Merge. UI erhält `wiederhergestellt / übersprungen / fehlgeschlagen` plus nicht sensitive Ursachen. **Abnahme:** erzwungene Fehler in jeder Phase hinterlassen keinen inkonsistenten Zwischenstand.

## D13 – Unverschlüsselte Mirrors entfernen und Backupregeln prüfen — **[IMPLEMENTIERUNG ERGÄNZT – TESTLAUF OFFEN]**
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

## L02 – Native lokale KI-Integration verbindlich vorbereiten
**Produktentscheidung:** echte native Modellinferenz lokal auf dem Gerät.  
**Prompt:** Verwende das L01-Audit, um den nativen Integrationspfad für llama.cpp zu bestimmen: fest versionierter Upstream, passende NDK/CMake-Version, unterstützte ABIs, Modellformat/Lizenz, RAM-/Kontextgrenzen, APK-Größe und 16-KB-Alignment. Erstelle den Implementierungsplan für L08A–L11A. Die Wahl echter nativer Inferenz ist getroffen; nicht auf einen weiteren Nutzerentscheid warten und regelbasierte Heuristiken nicht als Ersatz-KI deklarieren. Bei belegtem technischen Blocker Befund und Alternativen vorlegen, bevor ein Rückbau erwogen wird. **Abnahme:** reproduzierbarer Native-Integrationsplan und klarer Build-/Geräteumfang.
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

## L08A – Native Build reproduzierbar einbinden
**Abhängigkeit:** L01/L02.  
**Prompt:** Binde ein geprüftes, fest versioniertes llama.cpp-Commit/Submodul mit dokumentierten Lizenzen, CMake/NDK-Konfiguration und reproduzierbarem Build ein. Keine undokumentierten vorgefertigten .so-Dateien. Beginne mit den nötigen ABIs und prüfe minSdk 24 sowie aktuelle Packaging-/16-KB-Anforderungen. Dokumentiere Upstream-Commit, Lizenznotices, Buildbefehle und Artefakte. **Abnahme:** reproduzierbarer Gradle-/Native-Build und belegte Paketierung; noch keine Behauptung funktionierender Inferenz.
## L09A – JNI-Modelllebenszyklus
**Abhängigkeit:** L08A.  
**Prompt:** Implementiere den minimalen JNI-Lebenszyklus für Modell laden/entladen, Fehler zurückmelden und Ressourcen freigeben. Schütze vor parallelem Laden und ungültigen Modellpfaden; native Fehler dürfen nicht unkontrolliert den Prozess beenden. Tokenstreaming, Toolrouting und UI bleiben außerhalb dieses Micro-AP. **Abnahme:** instrumentierte Tests für erfolgreiches Laden/Entladen, ungültige Datei, doppeltes Laden und Cleanup.

## L10A – Echte Inferenz, Abbruch und Ressourcenlimits
**Abhängigkeit:** L09A.  
**Prompt:** Ergänze tatsächliche Token-Inferenz mit Callback/Streaming, Abbruch, Grenzen für Kontext, Tokenzahl, Threads und RAM sowie real gemessene Metriken. Keine Heuristik darf als Engineergebnis erscheinen. Native Ressourcen in allen Fehler-/Cancelpfaden freigeben. **Abnahme:** instrumentierter Test lädt ein kleines, klar lizenziertes und gehashtes Testmodell und erzeugt validiertes JSON; Metriken stammen von realem Decoder.

## L11A – ABI-/16-KB-/Geräteprüfung
**Abhängigkeit:** L08A–L10A.  
**Prompt:** Prüfe alle nativen Bibliotheken inklusive transitiver Abhängigkeiten auf ABI-Abdeckung sowie 16-KB-ELF- und APK/AAB-Alignment. Teste nur die zugesagten ABIs, einschließlich `arm64-v8a` und `x86_64` nur sofern tatsächlich paketiert/unterstützt, auf passenden Emulatoren oder Geräten. Dokumentiere Geräte, API-Level und Resultate. **Abnahme:** kein pauschales „kompatibel“ ohne Artefakt-/Gerätenachweis.

## L08B – LLM-Rückbau (nicht ausgewählt; nur Notfall-Alternative)
**Abhängigkeit:** Nur nach dokumentiertem schwerwiegendem technischem Blocker und erneuter ausdrücklicher Nutzerentscheidung.  
**Prompt:** Diesen Auftrag nicht automatisch ausführen. Erst nach konkretem Befund und Nutzerfreigabe einen Rückbau umsetzen. Regelbasierte Funktionen transparent kennzeichnen; keine Modellbytes ohne Freigabe laden. **Abnahme:** Entscheidung und Auswirkungen dokumentiert; kein stillschweigender Austausch echter KI gegen Heuristiken.
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
## C04B – Google Drive entfernen (nicht ausgewählt)
**Abhängigkeit:** Nur falls die aktuelle Produktentscheidung nach einem belegten schwerwiegenden technischen Blocker erneut geändert wird.  
**Prompt:** Diesen Auftrag nicht automatisch ausführen. Erst konkreten Befund, Alternativen und Empfehlung vorlegen und erneute ausdrückliche Entscheidung abwarten. Kein Provider darf stillschweigend entfernt werden.
## C05 – Google-Drive-Client und Transfers absichern
**Abhängigkeit:** C04; Uploads ausschließlich aus D10-verschlüsselten Containern.  
**Prompt:** Implementiere zuverlässige Drive-Uploads/Downloads großer Container mit Resumable Upload, begrenzten Retries und Retry-After bei geeigneten 429/5xx, getrennten Auth-/Quota-/Netz-/Formatfehlern und sicherem Abbruch. Prüfe Containerintegrität vor dem Anwenden eines Downloads. Bei 401 kontrolliert stoppen und Neuautorisierung anfordern. Logs dürfen keine Tokens, Inhalte, Klartextnamen oder Recovery-Secrets enthalten. **Abnahme:** Tests für Resume, 401, 403, 429, 5xx, Netzabbruch, ungültigen Container und Abbruch.
## C06 – WebDAV-HTTPS und Zertifikatsvertrauen — **[PATCH ERSTELLT – CI OFFEN; TOFU OFFEN]**
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
**Prompt:** Exportiere bzw. rekonstruiere das tatsächliche Room-Schema v9, ohne Schema- oder Datenmodelländerung. Prüfe `AppDatabase`, Entities, Indizes, Foreign Keys, FTS-Tabellen/Trigger und vorhandene Migrationen. Schalte noch nicht `exportSchema=true`, falls dadurch historische Versionen fälschlich erfunden würden. Erstelle ein Audit der tatsächlich vorhandenen Schemahistorie. **Abnahme:** v9-Baseline ist aus dem tatsächlichen Code/Build belegbar; keine destruktiven Änderungen.

## DB02 – Schemaexport und Migrationstest-Infrastruktur
**Abhängigkeit:** DB01.  
**Prompt:** Aktiviere `exportSchema=true`, konfiguriere KSP-`room.schemaLocation` und nimm die tatsächlich generierten Schema-JSONs in den Patch auf. Richte `MigrationTestHelper`-Infrastruktur ein, ohne gleich die fachlichen Migrationen umzusetzen. Historische fehlende Versionen nur aus echten veröffentlichten Schemata oder als klar bezeichnete Fixtures rekonstruieren. **Abnahme:** Build generiert erwartete Schemas reproduzierbar; Testinfrastruktur läuft, soweit die Umgebung dies ausführen kann; andernfalls verbleibt die Prüfung ausdrücklich offen.

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

---

## Einzelstatus – 40 erfasste von 119 Micro-APs

**Hinweis zur Vollständigkeit:** Die Tabelle bildet 40 protokollierte Einzelstände ab. Weitere 79 im Dokument definierte Micro-APs haben hier noch keinen expliziten Status. Das ist eine Dokumentationslücke, keine Aussage darüber, ob sie bereits umgesetzt wurden.



| Micro-AP | Status | Patch-Datei | Lokal übernommen | Lokale Tests | Nächster Schritt |
|---|---|---|---|---|---|
| M00 | ABGENOMMEN | micro-ap-M00.patch | Ja | Bestanden | V01 |
| V01 | GETESTET (CI) | VaultSecurityManager.kt, VaultV2OnlyTest.kt | Ja | GitHub Actions Run [39644923474](https://github.com/MrLongNight/myDocAnizer_Mobile/actions/runs/39644923474) erfolgreich: Debug-APK-Build, Android-Instrumentierungstests und Artefaktprüfung; Regressionstest für fehlgeschlagene Neuinitialisierung enthalten. `pre-commit.ci` meldet separat einen Fehler; dessen Ursache ist nicht durch den erfolgreichen Android-Workflow abgedeckt. | V01–V04 als gemeinsame Sicherheitsbasis abgleichen; pre-commit-Befund separat klären |
| V02 | PATCH ERSTELLT | direkt in main | Ja | Relevante Testklassen laut Statusbericht verifiziert; AP-Abnahme separat prüfen | V03 |
| V03 | GETESTET (CI) | VaultHeaderV2.kt, VaultHeaderV2Test.kt, VaultV2OnlyTest.kt | Ja | Parser-, Recovery- und Faktor-Negativtests einschließlich Pflichtfeld-/Typvalidierung liefen in GitHub Actions Run `39633700992` erfolgreich; staged crypto headers bleiben parsebar. Gesamt-Abnahme V01–V04 weiterhin separat abgleichen | V04 / Gesamt-Abnahme V01–V04 |
| V04 | PATCH ERSTELLT – SICHERHEITSLÜCKE IM METADATEN-AAD-UMFANG OFFEN | VaultHeaderCryptoV2.kt, VaultHeaderCryptoV2Test.kt | Ja | AES-GCM authentifiziert derzeit nur Kernfelder (`version`, Algorithmus, Vault-ID, Zeitstempel, KDF-Parameter, Salt, `tagLengthBits`, Sicherheitsstufe/Faktortyp und Auto-Lock). Zusätzliche persistierte Felder wie `domainSecurityTier`, `domainFactorType`, Biometrie-/Recovery-Metadaten und Recovery-Ciphertext sind nicht vollständig durch diese AAD gebunden. Das beweist keinen direkten DEK-Diebstahl, erlaubt aber Manipulation/DoS bzw. falsche Zustandsmetadaten und verhindert die vollständige V04-Abnahme. | V04-AAD-Konzept erweitern und alle sicherheitsrelevanten Metadaten vor Nutzung authentifizieren; Roundtrip-, Manipulations- und Bestandsheader-Tests ergänzen |
| V05 | ADR FREIGEGEBEN | docs/security/adr-002-totp-decision.md | Ja | Optionale 2FA: Passwort + zufällige Schlüsseldatei; TOTP ist kein kryptografischer Faktor | V06 |
| V06 | GETESTET (CI) | main, Commit `d509ebb` (PR #3) | Ja | Legacy-TOTP wird nicht mehr als Vault-Faktor akzeptiert; neue Header serialisieren kein TOTP-Secret, bestehende Klartextfelder werden beim Laden entfernt. GitHub Actions Run `39633700992`: Build, Unit-, Instrumentierungs- und APK-Prüfschritte erfolgreich | V06A/V06B separat abnehmen; manuelle Geräteprüfung bleibt offen |
| V06A | PATCH ERSTELLT | direkt in main | Ja | Wizard bietet nur Passwort+Keyfile als neue kryptografische 2FA; `.key`-Export ergänzt; Gesamt-CI grün, gezielte UI-/Geräteabnahme des Exports noch offen | V06B |
| V06B | PATCH ERSTELLT | direkt in main | Ja | Manager verlangt beide Faktoren und prüft Keyfile-Hash; `.key`-Import und Negativtests ergänzt; Gesamt-CI grün, gezielte Import-/Geräteabnahme bleibt offen | V06A/V06B gezielt auf Gerät prüfen |
| V07 | PATCH ERSTELLT | direkt in main | Ja | Relevante Testklassen laut Statusbericht verifiziert; AP-Abnahme separat prüfen | V08 |
| V08 | PATCH ERSTELLT | direkt in main | Ja | Relevante Testklassen laut Statusbericht verifiziert; AP-Abnahme separat prüfen | V09 |
| V09 | PATCH ERSTELLT | direkt in main | Ja | Relevante Testklassen laut Statusbericht verifiziert; AP-Abnahme separat prüfen | V10 |
| V10 | PATCH ERSTELLT | direkt in main | Ja | Relevante Testklassen laut Statusbericht verifiziert; AP-Abnahme separat prüfen | V11 |
| V11 | PATCH ERSTELLT | direkt in main | Ja | QrEncodingTest laut Statusbericht verifiziert; AP-Abnahme separat abgleichen | V12 |
| V12 | PATCH ERSTELLT | direkt in main | Ja | PDF-Regressionstest ergänzt; explizites Notfalldokument und Geräterestore abzunehmen | V12B/V13 |
| V12B | NICHT GESTARTET | – | Nein | Unauffällige Druckvariante erstellen | V12 |
| V13 | PATCH ERSTELLT | direkt in main | Ja | CryptoObject-Instrumented-Test ergänzt; Gerätetest offen | V14 |
| V14 | GETESTET (CI) | VaultSecurityManager.kt, VaultHeaderV2.kt, VaultV2OnlyTest.kt | Ja | V2-only-Headerpfad und Negativ-Regressionstest für alte Header-Schlüssel liefen in GitHub Actions Run `39633700992` erfolgreich. Keine Migration/Fallbacks | Gesamtstatus V01–V04 separat abnehmen |
| V15 | PATCH ERSTELLT | direkt in main | Ja | BackupRulesSecurityTest laut Statusbericht verifiziert; weitere AP-Abnahme separat prüfen | S01 |
| V16 | NICHT GESTARTET | – | Nein | Passkey muss echte Wiederherstellung ermöglichen | V09/V10 |
| V17 | NICHT GESTARTET | – | Nein | Sicherheitsfragen nur zusätzlicher, begrenzt starker Faktor | V10/V16 |
| V18 | NICHT GESTARTET | – | Nein | Restore-Auswahl/Einrichtung in der App | V12/V12B/V16/V17 |
| S01 | GETESTET | docs/security/lock-secret-inventory.md | Ja | Audit dokumentiert | S02 |
| S02 | PATCH ERSTELLT | direkt in main | Ja | LockManagerTest laut Statusbericht verifiziert | S03 |
| S03 | PATCH ERSTELLT | direkt in main | Ja | VaultAutoLockObserverTest laut Statusbericht verifiziert | S04 |
| S04 | PATCH ERSTELLT | direkt in main | Ja | Bypass-Pfade entfernt; Negativtests/Build offen | S05 |
| S05 | PATCH ERSTELLT | direkt in main | Ja | Produktionsaufrufe auf Key-Leases migriert; Testlauf offen | S06 |
| S06 | PATCH ERSTELLT | direkt in main | Ja | AuthenticationBackoffManagerTest laut Statusbericht verifiziert | S07 |
| S07 | PATCH ERSTELLT | direkt in main | Ja | Screenshot-/Recents-Gerätetest offen | S08 |
| S08 | GETESTET | direkt in main | Ja | GitHub Actions am 21.10.2026: assembleDebug, testDebugUnitTest und connectedDebugAndroidTest erfolgreich; Emulator API 35/x86_64. Separater Test auf physischem Gerät offen. | S09 |
| S09 | IN ARBEIT | direkt in main | Ja | GitHub Actions für Commit `1d593056` erfolgreich: Build, Unit-Tests und connectedDebugAndroidTest. Secret-/Token-Abgleich, physischer Gerätetest und Migration-Fehlerpfade bleiben offen. | S09-Instrumentation und verbleibende S01-Secrets abgleichen |
| S10 | GETESTET (Nutzerbestätigung) | direkt in main | Ja | Nutzer bestätigt erfolgreichen Build, `:app:testDebugUnitTest` und App-Start im Streaming-Emulator. Separater `connectedDebugAndroidTest`-Lauf und gezielter Widget-Cold-Start-/Lock-Replay-Test sind nicht ausdrücklich ausgewiesen. | S09-Secret-Inventar abschließen, anschließend D01/D02-Abhängigkeiten prüfen |
| D01 | AUDIT DOKUMENTIERT | docs/security/document-data-flow.md | Ja | Codepfad-Inventar erstellt; dynamische Geräte-/Share-/Restore-Abnahme bleibt für Folge-APs offen | D02 |
| D02 | ADR ERSTELLT | docs/security/adr-003-sqlcipher-room-database.md | Ja | SQLCipher for Android 4.19.1 / Room 2.7.0 als technischer Vorschlag; Runtime bleibt bis D03 und DB01–DB04 blockiert | D03 || DB03 | PATCH ERSTELLT | AppDatabase.kt / DatabaseDowngradeSafetyTest.kt | Ja | Destruktiven Downgrade-Fallback entfernt; Emulator-Abnahme dieses Commits steht noch aus | DB04 |
| D13 | PATCH ERSTELLT | direkt in main | Ja | BackupRulesSecurityTest laut Statusbericht verifiziert; Restore-/Geräteabnahme separat prüfen | D14 |
| D11 | PATCH ERSTELLT | DocumentStorageService.kt / BackupRestoreArchiveValidationTest.kt | Ja | Größenlimits, Duplikat-/Unexpected-Entry-Prüfung, Pflichtindex, Pfadsegmentprüfung, Index-Vorprüfung und Größenlimit schon beim Import aus einer Content-URI; keine Restore-Fallbacks auf im Backup gespeicherte Gerätepfade. Backup-Erstellung bricht bei fehlenden Dokumenten oder nicht lesbaren Vault-Metadaten ab statt ein unvollständiges Archiv zu markieren. CI offen; atomare DB-/Datei-Wiederherstellung bleibt D12 | D12 |
| D14 | PATCH ERSTELLT | direkt in main | Ja | PDFBox-Textlayer-Instrumentationstest ergänzt; Lauf offen; Scan-OCR wird nicht in PDF eingebettet | D15 || D15 | NICHT GESTARTET | – | Nein | Alle dauerhaft gespeicherten Einstellungen verschlüsseln | DB/Persistenztests |
| P01 | PATCH ERSTELLT | direkt in main | Ja | Bestehende Audit-/TLS-ADR dokumentiert; alle drei Verbindungstypen müssen im Code bestätigt werden | Sicherheitsmatrix vor P02 |
| P10 | PATCH ERSTELLT | direkt in main | Ja | SHA-256-Ersatzschlüssel-Fallback entfernt; P2pSyncSecurityTest laut Statusbericht verifiziert; Aufrufer-Audit separat prüfen | Testlauf und Aufrufer-Audit prüfen |
| C06 | PATCH ERSTELLT | WebDavClient.kt / WebDavTransportSecurityTest.kt | Ja | HTTP explizit abgewiesen; Bare-Host wird HTTPS; Redirects deaktiviert; URL-Zugangsdaten, Query/Fragment und Path-Traversal-Namen abgewiesen. CI/Unit-Tests sowie Zertifikats-TOFU-/Fingerprint-Pinning stehen noch aus | CI prüfen, dann TOFU pro Host mit SHA-256-Fingerprint implementieren und testen |