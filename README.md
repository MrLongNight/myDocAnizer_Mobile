<div align="center">
  <a href="https://github.com/MrLongNight/myDocAnizer_Mobile">
    <img src="app/src/main/res/drawable/mydocanizer_mobile.png" alt="myDocAnizer Mobile logo" width="1000" />
  </a>
</div>

The Privacy-First, 100% On-Device Document Management & Smart Scanner App

**Version:** 2.4.0 (Build 2026.09)  
**License:** GNU Affero General Public License v3.0 (AGPLv3) for source code; All branding, logos, trademarks & store assets copyright mr.locke84.  
**Repository:** https://github.com/MrLongNight/myDocAnizer_Mobile  
**Desktop:** https://github.com/MrLongNight/myDocAnizer-Desktop  
**Architecture:** Kotlin / Jetpack Compose / Android Room (SQLite + FTS4) / Google ML Kit Offline OCR / Local HuggingFace GGUF Nano-LLMs / AES-256-GCM Zero-Knowledge Client-Side Encryption / P2P Direct WiFi Sync

## Related project

<div align="center">
  <a href="https://github.com/MrLongNight/myDocAnizer-Desktop">
    <img src="app/src/main/res/drawable/mydocanizer_desktop.png" alt="myDocAnizer Desktop logo" width="900" />
  </a>
</div>

**🖥️ myDocAnizer Desktop** is the cross-platform companion for Windows, macOS, and Linux. It brings the same smart document workflow to the desktop with advanced management features, helping you organize, manage and securely access your scanned documents.  
→ https://github.com/MrLongNight/myDocAnizer-Desktop

---

## 1. Mission & Philosophy

myDocAnizer-Mobile is the counter-design to commercial scanner apps with opaque cloud uploads, expensive subscription traps, and tracking.

### Core Promises

- **100% PRIVACY & GDPR:** No scanned document and no OCR text ever leaves your smartphone unencrypted.
- **ZERO-CLOUD AI:** The app uses exclusively on-device AI models (quantized GGUF weights via HuggingFace) and offline OCR via Google ML Kit.
- **0 € TOKEN COSTS:** Neither users nor developers incur ongoing API costs from OpenAI, Google Cloud, or Anthropic.
- **ZERO-KNOWLEDGE ENCRYPTION:** Backups and optional cloud backups (WebDAV, Nextcloud, Google Drive) are encrypted locally on the device via AES-256-GCM and PBKDF2 (100,000 iterations). Only the owner can read them.
- **TRANSPARENT MONETIZATION:** An honest hybrid model of free community access, modular lifetime one-time purchases, and fair monthly/project passes without lock-in.

---

## 2. System Architecture & Modules

```
+------------------------------------------------------------------------------+
|                             myDocAnizer-Mobile                               |
+------------------------------------------------------------------------------+
| 1. Dokumenten-Tresor (DMS)  | 2. Scanner & OCR        | 3. On-Device LLM     |
|   • DIN-Ordner (A01 - A09)  |   • Offline ML Kit OCR  |   • GGUF Inferenz    |
|   • Batch-Inbox für Scans   |   • Auto-B/W Threshold  |   • Hardware-Check   |
|   • Custom Fields (Zusatz)  |   • Multi-Page Capture  |   • Lokales RAG Q&A  |
|   • FTS4 Volltext-Suche     |   • Akustische Profile  |   • Regelgenerator   |
+-----------------------------+-------------------------+----------------------+
| 4. Fristen & Regeln         | 5. Finanzen & Bank      | 6. Sync & Sicherheit |
|   • Kündigungsfrist-Regex   |   • CSV Kontoauszüge    |   • P2P WLAN-Sync    |
|   • Schlagwort-Regelwerk    |   • Belegabgleich       |   • AES-256-GCM Enc  |
|   • Auto-Logo-Zuweisung     |   • Bar-Ausgabenbuch    |   • Biometrie/Passkey|
|   • 100% deterministisch    |   • Budget & KPIs       |   • Disaster Restore |
+------------------------------------------------------------------------------+
```

### Module Details

**Module 1: Document Vault (DMS Explorer)**
- Structured two-level folder hierarchy (main categories A01–A09: e.g., Housing, Finance, Contracts, Taxes, Authorities; subcategories B1.01, etc.).
- Batch Inbox for fast scanning in batches with later review and assignment.
- Custom additional fields (e.g., customer number, license plate, tax ID, case number) with free type definition (text, amount, date).
- Integrated PDF viewer with sharing, export, and print functions.

**Module 2: Smart Camera Scanner & Preprocessing**
- 100% offline text recognition with Google ML Kit.
- Automatic lighting check, skew detection, and straightening.
- Dynamic B/W threshold filter for high-contrast, crystal-clear text.
- Multi-page document capture with sorting and page deletion.
- Acoustic sound profiles (click, beep, chime, silent) and haptic feedback.

**Module 3: Local HuggingFace LLM Suite**
- Complete on-device inference without internet.
- Local models include SmolLM2, Qwen 2.5, DeepSeek-R1 Distill Qwen, Phi-3.5 Mini, Ministral, Gemma 2, Llama 3.2, and Qwen 2.5 Coder.
- Automatic hardware barometer and remote catalog sync.
- Local AI rule generator and on-device RAG document chat.

**Module 4: Deadline & Termination Detection**
- Automatic analysis of OCR text for termination periods, minimum contract durations, payment terms, and objection deadlines.
- Pre-warning times with calendar integration and dashboard alerts.

**Module 5: Finance, Household Budget & Receipt Reconciliation**
- CSV import for common German banks.
- Automatic receipt reconciliation with account entries.
- Integrated household budget with cash tracker.

**Module 6: Synchronization & Data Security**
- Local P2P master-master WiFi sync between phone and desktop.
- Client-side zero-knowledge encryption with AES-256-GCM and PBKDF2.
- Disaster recovery via encrypted .enc archives.
- App lock via biometrics and FIDO2 passkey.

---

## 3. Pricing Model & Monetization Strategy

### A) LIFETIME (One-time purchase)
- **Community / Free:** 0,00 €
- **Smart Vault:** 14,99 €
- **Local AI & Rules:** 29,99 €
- **Ultimate Suite:** 44,99 €

### B) PROJECT-PASS (Flexible subscription)
- **Monthly Pass:** 3,49 € / month
- **3-Month Pass:** 7,99 € one-time

### C) TRANSPARENT EXAMPLES
- Move & apartment change: 2 months = 6,98 €
- Tax return: 1 month = 3,49 €
- Long-term users: lifetime becomes more economical from month 13 onward

### D) FAIR-USE & NO DATA LOCK-IN
- When a monthly pass expires, all previous documents and data remain readable, searchable, and exportable.
- Gating affects only ongoing new scans, LLM re-evaluations, and P2P sync.

---

## 4. Build & Development Instructions

### Requirements
- Android SDK Version 34
- Java JDK 17 or 21
- Gradle 8.5+ (Kotlin DSL)

### Build Project
```bash
gradle assembleDebug
```

### Run Local JVM & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Important Guidelines
- Never use `gradle clean` routinely.
- Run `gradle :app:testDebugUnitTest` after each change.

---

## 5. License, Trademarks & Open Source

The source code of myDocAnizer-Mobile is licensed under the GNU Affero General Public License v3.0 (AGPLv3).

The name "myDocAnizer", "myDocAnizer-Mobile", logos, icons, and Play Store graphic assets are copyrighted property of the author (mr.locke84). Everyone is entitled to view, audit, and create their own builds for personal use. Official Play Store distribution and official trademark branding remain with the project owner.
