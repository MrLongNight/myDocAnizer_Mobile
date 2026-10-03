<div align="center">
  <a href="https://github.com/MrLongNight/myDocAnizer-Desktop">
    <img src="app/src/main/res/drawable/mydocanizer_mobile.png" alt="myDocAnizer Mobile logo" width="1000" />
  </a>
</div>

The Privacy-First, 100% On-Device Document Management & Smart Scanner App

**Version:** 2.4.0 (Build 2026.09)  
**License:** GNU Affero General Public License v3.0 (AGPLv3) for source code; All branding, logos, trademarks & store assets copyright mr.locke84.  
**Repository:** https://github.com/MrLongNight/myDocAnizer-Mobile  
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

myDocAnizer-Mobile ist der Gegenentwurf zu kommerziellen Scanner-Apps mit undurchsichtigen Cloud-Uploads, teuren Abofallen und Tracking.

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
- Batch Inbox (Posteingang) for fast scanning in batches with later review and assignment.
- Custom additional fields (Custom Fields: e.g., customer number, license plate, tax ID, case number) with free type definition (text, amount, date).
- Integrated PDF viewer with sharing, export, and print functions.

**Module 2: Smart Camera Scanner & Preprocessing**
- 100% offline text recognition with Google ML Kit.
- Automatic lighting check (isWellLit), skew detection & straightening.
- Dynamic B/W threshold filter for high-contrast, crystal-clear text.
- Multi-page document capture with sorting and page deletion.
- Acoustic sound profiles (click, beep, chime, silent) and haptic feedback.

**Module 3: Local HuggingFace LLM Suite**
- Complete on-device inference without internet:
  - SmolLM2 135M / 360M Instruct (ultra-fast, minimal RAM requirements)
  - Qwen 2.5 0.5B / 1.5B Instruct (strong German language understanding)
  - DeepSeek-R1 Distill Qwen 1.5B (Chain-of-Thought reasoning for tax/finance)
  - Phi-3.5 Mini Instruct (superior logic for multi-page contracts & clauses)
  - Ministral 3B Instruct (native multilingual: DE, EN, FR, ES, IT)
  - Gemma 2 2B Instruct (DeepMind architecture with high factual accuracy for amounts)
  - Llama 3.2 1B / 3B Instruct (high-end on-device language models)
  - Qwen 2.5 Coder 1.5B (specialist for tables, CSV, and amount verification)
- Automatic hardware barometer: reads RAM and cores, marks models as Optimal, High Load, or Not Recommended.
- Remote catalog sync: checks for newly released, verified models from mr.locke84 with notification function.
- Local AI rule generator: creates deterministic keyword rules from natural language ("invoices from Telekom and Stadtwerke go here").
- On-device RAG & document chat: searches local database and answers questions about contracts, upcoming deadlines, costs, and senders.

**Module 4: Deadline & Termination Detection**
- Automatic analysis of OCR text for termination periods, minimum contract terms, payment terms, and objection deadlines.
- Pre-warning times with calendar integration and dashboard alerts.

**Module 5: Finance, Household Budget & Receipt Reconciliation**
- CSV import for all common German banks (Sparkasse, VR-Banks, ING, DKB, Postbank, Commerzbank, N26).
- Automatic receipt reconciliation: links scanned invoices and cash receipts with account entries and identifies gaps.
- Integrated household budget with cash tracker.

**Module 6: Synchronization & Data Security**
- Local P2P master-master WiFi sync: direct exchange between smartphone and myDocAnizer-Desktop PC in your home network via 6-digit PIN or QR code scan. Completely serverless, no third party involved.
- Client-side zero-knowledge encryption: all backups and cloud exports are encrypted with AES-256-GCM and PBKDF2.
- Disaster recovery: 1-click export/restore of encrypted .enc archives (e.g., for USB OTG sticks).
- App lock via biometrics (fingerprint, face unlock) and FIDO2 passkey.

---

## 3. Pricing Model & Monetization Strategy

The model deliberately distinguishes between two user types:

### A) LIFETIME (One-time purchase) – For sustainable, long-term organization
- **Community / Free:** 0,00 € (Vault, basic scan & OCR, full-text search)
- **Smart Vault:** 14,99 € (custom fields, AES-256 backups, biometrics, batch inbox)
- **Local AI & Rules:** 29,99 € (all HuggingFace LLMs, AI rules, RAG chat, deadlines)
- **Ultimate Suite:** 44,99 € (incl. P2P WiFi sync to PC, bank reconciliation & household budget)

### B) PROJECT-PASS (Flexible subscription) – For temporary digitization projects
- **Monthly Pass:** 3,49 € / month (monthly cancellable)
- **3-Month Pass:** 7,99 € one-time (ends automatically after 90 days)

### C) TRANSPARENT EXAMPLES
- **Move & apartment change:** 2 months pass = 6,98 € (saves 38,01 € vs. Ultimate)
- **Tax return:** 1 month pass = 3,49 € for complete receipt reconciliation and PDF export
- **Long-term users:** From month 13 onwards, the Ultimate lifetime purchase is the more economical choice!

### D) FAIR-USE & NO DATA LOCK-IN ("Read-Only Archive")
- When a monthly pass expires, ALL previously recorded documents, metadata, categories, and backups remain permanently readable, searchable, and exportable.
- Gating after expiration affects only ongoing new scans in batch, ongoing on-device LLM re-evaluations, and continuous P2P desktop synchronization.

---

## 4. Build & Development Instructions

### Requirements
- Android SDK Version 34 (Upside Down Cake)
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
- Never use `gradle clean` routinely (slows down subsequent builds).
- Run the test command `gradle :app:testDebugUnitTest` after each change.

---

## 5. License, Trademarks & Open Source

The source code of myDocAnizer-Mobile is licensed under the GNU Affero General Public License v3.0 (AGPLv3).

The name "myDocAnizer", "myDocAnizer-Mobile", logos, icons, and Play Store graphic assets are copyrighted property of the author (mr.locke84). Everyone is entitled to view, audit, and create their own builds for personal use. Official Play Store distribution and official trademark branding remain with the project owner.
