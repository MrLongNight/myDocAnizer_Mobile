================================================================================
                              myDocAnizer-Mobile
      The Privacy-First, 100% On-Device Document Management & Smart Scanner App
================================================================================

Version:       2.4.0 (Build 2026.09)
License:       GNU Affero General Public License v3.0 (AGPLv3) for source code;
               All branding, logos, trademarks & store assets copyright mr.locke84.
Repository:    https://github.com/MrLongNight/myDocAnizer_Mobile
Desktop:       https://github.com/MrLongNight/myDocAnizer-Desktop
Architecture:  Kotlin / Jetpack Compose / Android Room (SQLite + FTS4) /
               Google ML Kit Offline OCR / Local HuggingFace GGUF Nano-LLMs /
               AES-256-GCM Zero-Knowledge Client-Side Encryption / P2P Direct WiFi Sync

📱 myDocAnizer Mobile
🖥️  myDocAnizer Desktop is the cross-platform companion for Windows, macOS, and Linux.
    It brings the same smart document workflow to the desktop with advanced management
    features, helping you organize, manage and securely access your scanned documents.
    → https://github.com/MrLongNight/myDocAnizer-Desktop

--------------------------------------------------------------------------------
1. MISSION & PHILOSOPHIE
--------------------------------------------------------------------------------
myDocAnizer-Mobile is the counter-design to commercial scanner apps with opaque cloud uploads,
expensive subscription traps, and tracking.

Core promises:
  * 100% PRIVACY & GDPR: No scanned document and no OCR text ever leaves the smartphone
    unencrypted.
  * ZERO-CLOUD AI: The app uses exclusively on-device AI models (quantized GGUF weights via
    HuggingFace) and offline OCR via Google ML Kit.
  * 0 € TOKEN COSTS: Neither the user nor the developer incurs recurring API costs from
    OpenAI, Google Cloud, or Anthropic.
  * ZERO-KNOWLEDGE ENCRYPTION: Backups and optional cloud storage (WebDAV, Nextcloud,
    Google Drive) are encrypted locally on the device via AES-256-GCM and PBKDF2 with
    100,000 iterations.
  * TRANSPARENT MONETIZATION: An honest hybrid model of Free Community, modular lifetime
    one-time purchases, and fair monthly/project passes without lock-in.

--------------------------------------------------------------------------------
2. SYSTEM ARCHITECTURE & MODULES
--------------------------------------------------------------------------------

+------------------------------------------------------------------------------+
|                             myDocAnizer-Mobile                               |
+------------------------------------------------------------------------------+
| 1. Document Vault (DMS)     | 2. Scanner & OCR        | 3. On-Device LLM     |
|   • DIN folders (A01 - A09)  |   • Offline ML Kit OCR  |   • GGUF inference    |
|   • Batch inbox for scans    |   • Auto-B/W Threshold  |   • Hardware check    |
|   • Custom fields            |   • Multi-page capture  |   • Local RAG Q&A     |
|   • FTS4 full-text search    |   • Acoustic profiles    |   • Rule generator    |
+-----------------------------+-------------------------+----------------------+
| 4. Deadlines & Rules        | 5. Finance & Banking    | 6. Sync & Security   |
|   • Deadline regex          |   • CSV statements      |   • P2P WiFi sync     |
|   • Keyword rule engine     |   • Reconciliation      |   • AES-256-GCM Enc   |
|   • Auto-logo assignment     |   • Cash book          |   • Biometrics/Passkey|
|   • 100% deterministic      |   • Budget & KPIs      |   • Disaster restore  |
+------------------------------------------------------------------------------+

Module details:

* Module 1: Document Vault (DMS Explorer)
  - Structured two-level folder hierarchy.
  - Batch inbox for fast scanning and later review.
  - Custom metadata fields and PDF viewer.

* Module 2: Smart Camera Scanner & Preprocessing
  - 100% offline OCR with Google ML Kit.
  - Lighting, skew correction, and dynamic B/W thresholding.
  - Multi-page scanning and haptic/acoustic feedback.

* Module 3: Local HuggingFace LLM Suite
  - On-device inference with compact quantized models.
  - Automatic hardware barometer and remote catalog sync.
  - AI rule generation and local RAG document chat.

* Module 4: Deadline & Termination Detection
  - OCR-driven detection of deadlines, minimum terms, and objection periods.

* Module 5: Finance, Household Book & Reconciliation
  - CSV import for common German banks.
  - Automatic invoice and receipt matching.
  - Integrated cash tracker.

* Module 6: Synchronization & Data Security
  - Local P2P master-master WiFi sync.
  - Client-side zero-knowledge encryption.
  - Disaster recovery with encrypted .enc archives.
  - App lock with biometrics and FIDO2 passkey.

--------------------------------------------------------------------------------
3. PRICING MODEL & MONETIZATION STRATEGY
--------------------------------------------------------------------------------
The model deliberately distinguishes between two user types:

A) LIFETIME (one-time purchase)
   - Community / Free:  0,00 €
   - Smart Vault:      14,99 €
   - Local AI & Rules: 29,99 €
   - Ultimate Suite:   44,99 €

B) PROJECT PASS (flexible subscription)
   - Monthly Pass:       3,49 € / month
   - 3-Month Pass:       7,99 € one-time

C) TRANSPARENT EXAMPLES:
   - Move & apartment change: 2 months = 6,98 €
   - Tax return: 1 month = 3,49 €
   - Long-term users: from month 13 onwards, the lifetime purchase is cheaper

D) FAIR-USE & NO DATA LOCK-IN ("Read-Only Archive"):
   - When the subscription ends, all existing documents remain readable and exportable.
   - Gating affects only ongoing scans and sync operations.

--------------------------------------------------------------------------------
4. BUILD & DEVELOPMENT INSTRUCTIONS
--------------------------------------------------------------------------------

Requirements:
  * Android SDK Version 34 (Upside Down Cake)
  * Java JDK 17 or 21
  * Gradle 8.5+ (Kotlin DSL)

Build project:
  gradle assembleDebug

Run local JVM & Robolectric tests:
  gradle :app:testDebugUnitTest

Important guidelines:
  - Do not use 'gradle clean' routinely.
  - Run 'gradle :app:testDebugUnitTest' after each change.

--------------------------------------------------------------------------------
5. LICENSE, TRADEMARKS & OPEN SOURCE
--------------------------------------------------------------------------------
The source code of myDocAnizer-Mobile is licensed under the GNU Affero General
Public License v3.0 (AGPLv3).

The name "myDocAnizer", "myDocAnizer-Mobile", logos, icons and Play Store graphic assets
are copyrighted property of the author (mr.locke84).
Everyone is entitled to view, audit, and create their own builds for personal use.
The official Play Store distribution and official branding remain with the project owner.

================================================================================
