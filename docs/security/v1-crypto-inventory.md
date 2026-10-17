# Vault Kryptografie Inventar (v1) - myDocAnizer-Mobile

Dieses Dokument beschreibt die exakte Implementierung der Vault-Sicherheit in Version 1 zur Sicherstellung der Abwärtskompatibilität.

## 1. Vault Header v1 (Serialisierung)
Der Header wird als JSON-String in den SharedPreferences `mydocanizer_vault_security` unter dem Schlüssel `vault_header_json` gespeichert.

### Felder:
| Feld | Typ | Beschreibung |
| :--- | :--- | :--- |
| `version` | Int | Aktuell immer 1 |
| `vaultId` | String | Eindeutige Kennung (z.B. `VAULT-ABC12345`) |
| `createdAt` | Long | Zeitstempel der Erstellung |
| `argon2Salt` | Base64 | 16 Byte Salt für KDF |
| `argon2Iterations` | Int | Standard: 2 |
| `argon2MemoryKb` | Int | Standard: 16384 (16 MB) |
| `encDekPassword` | Base64 | Mit KEK_Password verschlüsselter DEK |
| `ivPassword` | Base64 | 12 Byte IV für Password-Verschlüsselung |
| `biometric2Fa` | Boolean | Status der Biometrie-Bindung |
| `encDekBio` | Base64? | Mit Keystore-Schlüssel verschlüsselter DEK |
| `ivBio` | Base64? | 12 Byte IV für Biometrie-Verschlüsselung |
| `recoveryMethod` | Enum | `NONE`, `BIP39_WORDS`, `EMERGENCY_PDF`, `SHAMIR_2_OF_3`, `RECOVERY_CODE` |
| `encDekRecovery` | Base64? | Mit Recovery-Schlüssel verschlüsselter DEK |
| `ivRecovery` | Base64? | IV für Recovery |
| `saltRecovery` | Base64? | Salt für Recovery-KDF |
| `pdfChecksum` | String? | SHA-256 des DEK (für Validierung nach PDF-Import) |
| `shamirHashes` | Array | Liste von SHA-256 Hashes der Shamir-Shares |
| `hasEmergencyPin` | Boolean | Ob die Recovery durch PIN geschützt ist |
| `emergencyPinSalt` | Base64? | Salt für PIN-Ableitung |
| `securityTier` | Enum | `UNENCRYPTED`, `ONE_FACTOR`, `TWO_FACTOR` |
| `factorType` | Enum | `NONE`, `PASSWORD`, `KEYFILE`, ... |
| `totpSecret` | String? | Base32 TOTP Secret (Klartext in v1!) |
| `keyFileTokenHash`| String? | SHA-256 des Keyfile-Tokens |
| `recoveryCodeHash`| String? | SHA-256 des Notfall-Codes |
| `autoLockMinutes` | Int | Timeout für automatische Sperre |

## 2. Algorithmen & Parameter

### 2.1 Master-Passwort Ableitung (Argon2id)
- **Implementierung:** Eigenbau in Kotlin (`Argon2idEngine`)
- **Parameter:** 
    - `lanes`: 1
    - `version`: 0x13
    - `tagLength`: 32 Byte
    - `salt`: 16 Byte (Zufall)
- **Kombinierte Faktoren:** Falls Passwort und Keyfile genutzt werden, erfolgt die Ableitung über den String `"$password:$keyFileToken"`.

### 2.2 Daten-Verschlüsselung (AES-GCM)
- **Algorithmus:** `AES/GCM/NoPadding`
- **Schlüssellänge:** 256 Bit (DEK)
- **IV Länge:** 12 Byte (Zufall)
- **Tag Länge:** 128 Bit
- **Integrität:** Wird durch GCM sichergestellt.

### 2.3 Recovery-Mechanismen
- **BIP-39:** 12 Wörter, PBKDF2WithHmacSHA512 (2048 Iterationen) zur Seed-Ableitung (die ersten 32 Byte werden als KEK genutzt).
- **Emergency PIN:** PBKDF2WithHmacSHA256 (50.000 Iterationen).
- **Shamir's Secret Sharing:** 2-aus-3 über GF(256).
- **Recovery Code:** 32 Zeichen (A-Z, 2-9), PBKDF2WithHmacSHA256 (20.000 Iterationen).

## 3. Bekannte Schwachstellen (Audit-Befunde)
1. **TOTP-Secret:** Wird im Klartext (Base32) im JSON-Header gespeichert.
2. **Keine Header-Authentifizierung:** Das JSON-Objekt selbst ist nicht signiert oder authentifiziert (AAD fehlt).
3. **DEK-Anzeige:** Der DEK wird als Base64-String im PDF und im UI ausgegeben.
