# Entwickler- & Integrationsleitfaden: myDocAnizer-Desktop Client

**Projekt:** myDocAnizer-Desktop (Multi-OS: Windows, macOS, Linux)  
**Companion-App zu:** myDocAnizer-Mobile (Android)  
**Ziel:** 100% kompatibler, lokaler Desktop-Client mit bidirektionalem WLAN-Sync, nativer Dokumentenverwaltung und lokaler KI-Inferenz.

---

## 1. Architektur- & Technologie-Empfehlungen

Für die Entwicklung des Desktop-Clients stehen folgende moderne Stacks zur Auswahl:

1. **Option A (Empfohlen): Compose Multiplatform (Kotlin Desktop)**
   - **Vorteile:** Direkte Wiederverwendung von Kotlin-Datenmodellen (`DocumentEntity`, `DocRule`, `FolderStrategy`), gemeinsame Business-Logic und Coroutines.
   - **DB:** SQLite via Room (KMP) oder SQLDelight / Exposed.
   - **UI:** Jetpack Compose for Desktop (Material 3).
2. **Option B: Tauri v2 (Rust Backend + Vue/React/Svelte Frontend)**
   - **Vorteile:** Extrem geringer RAM-Verbrauch (< 30 MB), native Rust-Performance für OCR, PDF-Rendering und Krypto.
3. **Option C: Go + Wails v2 / v3**
   - **Vorteile:** Einfache P2P-Netzwerkprogrammierung, native System-Tray-Integration, ein kompaktes Binary.

---

## 2. Datenmodelle & SQLite-Datenbankschema

Die Tabellenstruktur des Desktop-Clients muss exakt mit der Android Room-Datenbank übereinstimmen:

### 2.1 Tabelle `documents`
```sql
CREATE TABLE IF NOT EXISTS documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    sender TEXT NOT NULL,
    fileName TEXT NOT NULL,
    filePath TEXT NOT NULL,
    fileSize INTEGER NOT NULL,
    mimeType TEXT NOT NULL,
    mainCategory TEXT NOT NULL,
    subCategory TEXT NOT NULL,
    docType TEXT NOT NULL,
    ocrText TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    modifiedAt INTEGER NOT NULL,
    cancellationDeadline INTEGER,
    contractEndDate INTEGER,
    reminderIntervalDays INTEGER NOT NULL DEFAULT 30,
    isCalendarSyncEnabled INTEGER NOT NULL DEFAULT 0,
    calendarEventId TEXT NOT NULL DEFAULT '',
    reminderNotes TEXT NOT NULL DEFAULT '',
    amount REAL,
    isDeleted INTEGER NOT NULL DEFAULT 0,
    customIcon TEXT NOT NULL DEFAULT '',
    companyLogo TEXT NOT NULL DEFAULT '',
    pageCount INTEGER NOT NULL DEFAULT 1,
    checksumSha256 TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS index_documents_isDeleted_createdAt ON documents(isDeleted, createdAt);
CREATE INDEX IF NOT EXISTS index_documents_docType ON documents(docType);
CREATE INDEX IF NOT EXISTS index_documents_mainCategory ON documents(mainCategory);
CREATE INDEX IF NOT EXISTS index_documents_cancellationDeadline ON documents(cancellationDeadline);
```

### 2.2 FTS4 Volltext-Tabelle `documents_fts`
```sql
CREATE VIRTUAL TABLE IF NOT EXISTS documents_fts USING fts4(
    content="documents",
    title,
    sender,
    docType,
    ocrText
);
```

### 2.3 Tabelle `custom_fields` & `document_custom_field_values`
```sql
CREATE TABLE IF NOT EXISTS custom_fields (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    type TEXT NOT NULL, -- 'TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECTION'
    scope TEXT NOT NULL, -- 'GLOBAL', 'FOLDER_SPECIFIC'
    folderId TEXT NOT NULL DEFAULT '',
    options TEXT NOT NULL DEFAULT '',
    defaultValue TEXT NOT NULL DEFAULT '',
    isMandatory INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS document_custom_field_values (
    documentId INTEGER NOT NULL,
    customFieldId TEXT NOT NULL,
    fieldValue TEXT NOT NULL,
    PRIMARY KEY(documentId, customFieldId),
    FOREIGN KEY(documentId) REFERENCES documents(id) ON DELETE CASCADE,
    FOREIGN KEY(customFieldId) REFERENCES custom_fields(id) ON DELETE CASCADE
);
```

### 2.4 Tabelle `doc_rules` (Regelwerk)
```sql
CREATE TABLE IF NOT EXISTS doc_rules (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    matchKeywordsJson TEXT NOT NULL, -- JSON Array: ["vodafone", "rechnungsnummer"]
    excludeKeywordsJson TEXT NOT NULL, -- JSON Array: ["mahnung"]
    targetMainCategoryId TEXT NOT NULL,
    targetSubCategoryId TEXT NOT NULL,
    targetDocType TEXT NOT NULL,
    detectedSender TEXT NOT NULL,
    targetFolderPath TEXT NOT NULL,
    targetTagsJson TEXT NOT NULL, -- JSON Array
    targetCustomFieldsJson TEXT NOT NULL, -- JSON Map: {"Kundennummer": "12345"}
    isEnabled INTEGER NOT NULL DEFAULT 1,
    isAiGenerated INTEGER NOT NULL DEFAULT 0,
    targetIcon TEXT NOT NULL DEFAULT '',
    targetLogo TEXT NOT NULL DEFAULT ''
);
```

---

## 3. P2P Local WiFi Sync: Schnittstellenspezifikation

Die Synchronisation erfolgt **direkt über das lokale WLAN** ohne externe Server.

### 3.1 mDNS / Zeroconf Service Discovery
- **Service Type:** `_mydocanizer._tcp`
- **Port:** `8765`
- **TXT Records:**
  - `devId`: UUID des Geräts
  - `devName`: z.B. "Desktop-PC-Büro"
  - `protoVer`: "2"
  - `fingerprint`: SHA-256 Hash des Zertifikats / Tokens

### 3.2 QR-Code Pairing Handshake
Der Desktop-Client erzeugt einen QR-Code für die Android-App mit folgendem JSON:
```json
{
  "version": 2,
  "id": "c7a8b9e0-1234-5678-90ab-cdef12345678",
  "name": "myDocAnizer-Desktop PC",
  "ip": "192.168.178.50",
  "port": 8765,
  "pubKey": "base64_encoded_public_key_or_token",
  "token": "a1b2c3d4e5f6...",
  "fingerprint": "8f:4a:2b:..."
}
```

### 3.3 Verschlüsselung & Authentifizierung (AES-256-GCM)
- **Verschlüsselung:** Alle Payloads werden mit **AES-256-GCM** verschlüsselt.
- **Session-Key:** Abgeleitet aus dem beim Pairing vereinbarten Pre-Shared Key (PSK) oder ECDH-Schlüsselaustausch.
- **Nonce (IV):** 12 Bytes zufällig pro Übertragung.
- **Auth Tag:** 128 Bit (16 Bytes).

### 3.4 REST & WebSocket Endpunkte (Port 8765)

| Methode | Endpunkt | Beschreibung |
| :--- | :--- | :--- |
| `POST` | `/api/v1/sync/pair` | Initiiert Pairing mit 6-stelligem Bestätigungs-PIN. |
| `GET` | `/api/v1/sync/status` | Liefert Gerätestatus, letzte Sync-Zeit und Snapshot-Checksumme. |
| `POST` | `/api/v1/sync/handshake` | Authentifiziert die Session via Pre-Shared Secret. |
| `POST` | `/api/v1/sync/delta` | Überträgt geänderte Datensätze seit Timestamp `lastSyncTimestamp`. |
| `GET` | `/api/v1/sync/document/{id}` | Lädt die Binärdatei (PDF / JPG) eines Dokuments herunter. |
| `POST` | `/api/v1/sync/document/upload` | Überträgt eine neue Dokumentdatei mit Metadaten. |
| `POST` | `/api/v1/sync/conflict/resolve` | Wendet eine manuelle Konfliktentscheidung (Lokal vs. Remote) an. |

### 3.5 Konfliktbehandlung (3-Way Merge & Snapshots)
Wenn ein Dokument auf Mobile und Desktop gleichzeitig modifiziert wurde:
1. Erzeuge einen `DocumentSummarySnapshot` beider Versionen:
   - `title`, `sender`, `mainCategory`, `subCategory`, `modifiedAt`, `fileSize`, `checksumSha256`.
2. Klassifiziere den Konfliktgrund (`ConflictCauseType`):
   - `CONCURRENT_EDIT`, `DELETE_VS_EDIT`, `HASH_MISMATCH`, `FILE_MISSING`.
3. Biete dem Nutzer eine visuelle Gegenüberstellung im UI zur Ein-Klick-Übernahme.

---

## 4. Ordner-Strategie & Deterministische Ablage

Der Desktop-Client muss die 4 Ebenen-1-Ablagestrategien (`FolderStrategy`) unterstützen:

```kotlin
enum class FolderStrategy(val id: String, val title: String, val examplePath: String) {
    BY_SENDER("BY_SENDER", "Nach Absender (Firmen)", "Vodafone/Rechnungen"),
    BY_CATEGORY("BY_CATEGORY", "Nach Lebensbereich (Kategorien)", "Finanzen/Versicherungen"),
    CHRONOLOGICAL("CHRONOLOGICAL", "Chronologisch (Jahr)", "2026/Rechnungen"),
    FLAT("FLAT", "Flach (Alles in einen Ordner)", "Dokumente")
}
```

### Automatische Pfad-Generierung:
```kotlin
fun computeTargetFolderPath(sender: String, docType: String, category: String, strategy: FolderStrategy): String {
    return when (strategy) {
        FolderStrategy.BY_SENDER -> "${sender.ifBlank { "Unbekannt" }}/${docType.ifBlank { "Dokumente" }}"
        FolderStrategy.BY_CATEGORY -> "${category.ifBlank { "Allgemein" }}/${docType.ifBlank { "Dokumente" }}"
        FolderStrategy.CHRONOLOGICAL -> "2026/${docType.ifBlank { "Dokumente" }}"
        FolderStrategy.FLAT -> "Dokumente"
    }
}
```

---

## 5. Desktop On-Device KI-Integration

- **Llama.cpp Desktop Bindings:** Verwendung von `llama.cpp` nativ auf Windows (Vulkan / DirectML), macOS (Metal) und Linux (Vulkan / CUDA).
- **GGUF-Modelle:** Gleiche quantized GGUF-Modelle (Q4_K_M / Q8_0) wie auf Android für 100% konsistente Extraktionsergebnisse.
- **Desktop OCR:**
  - Windows: Windows Media Ocr API oder Tesseract v5.
  - macOS: Apple Vision Framework OCR.
  - Linux: Tesseract v5 mit deutschem Sprachpaket (`tesseract-ocr-deu`).
- **Team 4 Gatekeeper:** Direkte Portierung von `QualityGatekeeper.kt` auf Desktop für identische Validierung von Beträgen, IBANs und Datumsangaben.
