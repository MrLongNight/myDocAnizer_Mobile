# ADR 001: Versioniertes Recovery-Payload für Notfall-Kits

**Status:** FREIGEGEBEN (Micro-AP V09)  
**Datum:** 2026-10-17  
**Betrifft:** myDocAnizer-Mobile – Notfall-Kit & Wiederherstellungs-Payload (`Emergency PDF` & `BIP-39 / Recovery-Payload`)

---

## 1. Kontext & Problemstellung
Bislang wurde in Notfall-Kits (z.B. Emergency-PDF) unter Umständen der rohe 256-Bit-DEK (Data Encryption Key) als Base64-String ausgegeben. Dies verstößt gegen strenge Zero-Knowledge-Sicherheitsprinzipien, da ein kompromittiertes PDF oder ein Einblick in den Druckauftrag/Textlayer sofort den vollständigen Zugriff auf den gesamten Tresor ermöglicht.

Zudem muss das Recovery-Format versioniert, kryptografisch authentifiziert (AES-GCM mit AAD) und gegen Brute-Force-Angriffe bei Offline-Exfiltration (z. B. durch zu schwache PINs) geschützt werden.

---

## 2. Architekturentscheidung (Design)

### 2.1 Entropiequelle & KDF (Key Derivation Function)
- **Entropiequelle:** Der DEK ist ein kryptografisch sicherer, zufälliger 256-Bit-Schlüssel (`SecureRandom`), der beim Einrichten des Tresors generiert wird.
- **KDF für Recovery-Passphrase / Master-Passwort:** 
  - **Argon2id** (v1.3) mit mindestens `m = 19456 KB` (19 MiB), `t = 2`, `p = 1` und einem zufälligen 16-Byte Salt.
  - Alternativ für strukturierte Passphrasen (z. B. BIP-39 Mnemonic): `PBKDF2WithHmacSHA512` mit mindestens 2048 Iterationen zur Seed-Ableitung.
- **Wichtig:** Ein einfacher 6-stelliger PIN reicht als *alleiniger* Schutz für ein offline kopierbares Notfall-Kit **nicht** aus, da dieser per Brute-Force in Sekunden angreifbar wäre. Notfall-Kits verlangen daher eine starke Passphrase (mindestens 20 Zeichen; Länge allein ersetzt keine Unvorhersagbarkeit) oder eine 12-Wörter-BIP-39-Mnemonic bzw. einen hoch-entropischen Recovery-Code (mind. 32 Zeichen). Der Recovery-Payload-Kern akzeptiert Passphrasen ab 20 Zeichen.

### 2.2 Byteformat & Versioniertes Payload (v2)
Das Recovery-Payload wird als JSON-Struktur (und binär repräsentiert im QR-Code) kodiert:
```json
{
  "version": 2,
  "alg": "AES-256-GCM-Argon2id",
  "vaultId": "VAULT-ABC12345",
  "salt": "<Base64 16 Bytes>",
  "iv": "<Base64 12 Bytes>",
  "ciphertext": "<Base64 verschlüsselter 32-Byte DEK>",
  "tagBits": 128
}
```

### 2.3 Authenticated Encryption mit AAD (Associated Data)
- **Verschlüsselung:** `AES/GCM/NoPadding`
- **AAD (Associated Data):** Um Angriffe durch Verschieben von Payloads zwischen verschiedenen Vaults oder Versionen zu verhindern, wird die AAD fest an den Header gebunden:
  `AAD = "myDocAnizer-Recovery-v2:VAULT-ABC12345"`
- **Nonce/IV:** Pro Verschlüsselung ein neuer, zufälliger 12-Byte-IV (niemals wiederverwenden).

### 2.4 Workflow & Schutz vor Offline-Exfiltration
1. **Export:** Der DEK wird niemals im Klartext im PDF, QR-Code oder UI angezeigt. Verschlüsselt wird ausschließlich der DEK mittels des aus der Recovery-Passphrase abgeleiteten KEK.
2. **Import/Recovery:** Der Nutzer scannt den QR-Code oder gibt das Payload ein, tippt seine Recovery-Passphrase ein, leitet den KEK ab und entschlüsselt den DEK im RAM (`activeDek`).
3. **Säuberung:** Sensible Byte-Arrays (KEK, temporäre Passwörter) werden nach Verwendung sofort mit `fill(0)` überschrieben.

---

## 3. Testfälle & Abnahme (V10 / V12)
- **Roundtrip-Test:** Verschlüsselung eines synthetischen DEK mit gültiger Passphrase -> erfolgreiche Entschlüsselung.
- **Manipulations-Test:** Verändern eines Ciphertext-Bytes oder des AAD führt zu einem `AEADBadTagException` / `SecurityException`.
- **PDF-Textlayer-Prüfung:** Automatisierter Test stellt sicher, dass kein Roh-DEK im Textlayer des Emergency-PDFs enthalten ist.
