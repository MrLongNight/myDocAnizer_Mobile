package com.example.service

import android.util.Base64
import org.json.JSONObject
import java.util.Locale

/**
 * Typisierte v2-Headerstruktur für den Zero-Knowledge Tresor mit strengen Obergrenzen und Metadaten.
 */
data class VaultHeaderV2(
    val version: Int = 2,
    val algorithmId: String = "AES-256-GCM-Argon2id",
    val vaultId: String,
    val createdAt: Long,
    val argon2Iterations: Int,
    val argon2MemoryKb: Int,
    val saltBase64: String,
    val ivBase64: String,
    val tagLengthBits: Int = 128,
    val encryptedDekBase64: String,
    val securityTier: String = "ONE_FACTOR",
    val factorType: String = "PASSWORD",
    val autoLockMinutes: Int = 5,
    val domainSecurityTier: String = securityTier,
    val domainFactorType: String = factorType,
    val biometric2FaEnabled: Boolean = false,
    val encryptedDekByBiometricsBase64: String? = null,
    val biometricDekIvBase64: String? = null,
    val recoveryMethod: String = "NONE",
    val encryptedDekByRecoveryBase64: String? = null,
    val recoveryDekIvBase64: String? = null,
    val recoverySaltBase64: String? = null,
    val emergencyPdfChecksum: String? = null,
    val shamirShareHashes: List<String> = emptyList(),
    val hasEmergencyPin: Boolean = false,
    val emergencyPinSaltBase64: String? = null,
    val totpSecret: String? = null,
    val keyFileTokenHash: String? = null,
    val recoveryCodeHash: String? = null
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("version", version)
            put("algorithmId", algorithmId)
            put("vaultId", vaultId)
            put("createdAt", createdAt)
            put("argon2Iterations", argon2Iterations)
            put("argon2MemoryKb", argon2MemoryKb)
            put("saltBase64", saltBase64)
            put("ivBase64", ivBase64)
            put("tagLengthBits", tagLengthBits)
            put("encryptedDekBase64", encryptedDekBase64)
            put("securityTier", securityTier)
            put("factorType", factorType)
            put("autoLockMinutes", autoLockMinutes)
            put("domainSecurityTier", domainSecurityTier)
            put("domainFactorType", domainFactorType)
            put("biometric2FaEnabled", biometric2FaEnabled)
            encryptedDekByBiometricsBase64?.let { put("encryptedDekByBiometricsBase64", it) }
            biometricDekIvBase64?.let { put("biometricDekIvBase64", it) }
            put("recoveryMethod", recoveryMethod)
            encryptedDekByRecoveryBase64?.let { put("encryptedDekByRecoveryBase64", it) }
            recoveryDekIvBase64?.let { put("recoveryDekIvBase64", it) }
            recoverySaltBase64?.let { put("recoverySaltBase64", it) }
            emergencyPdfChecksum?.let { put("emergencyPdfChecksum", it) }
            put("shamirShareHashes", org.json.JSONArray().apply { shamirShareHashes.forEach { put(it) } })
            put("hasEmergencyPin", hasEmergencyPin)
            emergencyPinSaltBase64?.let { put("emergencyPinSaltBase64", it) }
            totpSecret?.let { put("totpSecret", it) }
            keyFileTokenHash?.let { put("keyFileTokenHash", it) }
            recoveryCodeHash?.let { put("recoveryCodeHash", it) }
        }.toString()
    }
}

/**
 * Strikter Parser und Validator für VaultHeader v2.
 * Fail-Closed: Unbekannte Versionen, abgeschnittene Daten, unmögliche Längen und ungültige Parameter werden strikt abgewiesen.
 */
object VaultHeaderParserV2 {
    private const val SUPPORTED_ALGORITHM_ID = "AES-256-GCM-Argon2id"

    fun parse(rawJson: String?): VaultHeaderV2? {
        if (rawJson.isNullOrBlank()) return null
        return try {
            val json = JSONObject(rawJson)

            // 1. Versionsprüfung (Muss exakt 2 sein)
            val version = json.optInt("version", -1)
            if (version != 2) {
                return null // Unbekannte oder nicht unterstützte Version abweisen
            }

            val algorithmId = json.optString("algorithmId", "")
            // This parser implements only this exact v2 algorithm suite. Unknown suites must fail closed.
            if (algorithmId != SUPPORTED_ALGORITHM_ID) return null

            val vaultId = json.optString("vaultId", "")
            if (vaultId.isBlank() || vaultId.length > 64 || !vaultId.startsWith("VAULT-")) return null

            val createdAt = json.optLong("createdAt", -1L)
            if (createdAt <= 0L) return null

            val iterations = json.optInt("argon2Iterations", -1)
            if (iterations < 2 || iterations > 10) return null // Obergrenze und Untergrenze prüfen

            val memoryKb = json.optInt("argon2MemoryKb", -1)
            if (memoryKb < 19456 || memoryKb > 262144) return null // Minimum 19 MiB, Obergrenze 256 MiB

            val saltBase64 = json.optString("saltBase64", "")
            if (saltBase64.isBlank()) return null
            val saltBytes = try {
                Base64.decode(saltBase64, Base64.NO_WRAP)
            } catch (e: Exception) {
                return null
            }
            if (saltBytes.size != 16) return null // Salt muss exakt 16 Byte sein

            val ivBase64 = json.optString("ivBase64", "")
            if (ivBase64.isBlank()) return null
            val ivBytes = try {
                Base64.decode(ivBase64, Base64.NO_WRAP)
            } catch (e: Exception) {
                return null
            }
            if (ivBytes.size != 12) return null // AES-GCM IV muss exakt 12 Byte (96 Bit) sein

            val tagLengthBits = json.optInt("tagLengthBits", -1)
            if (tagLengthBits != 128) return null // Tag muss exakt 128 Bit sein

            val encryptedDekBase64 = json.optString("encryptedDekBase64", "")
            if (encryptedDekBase64.isBlank()) return null
            val dekBytes = try {
                Base64.decode(encryptedDekBase64, Base64.NO_WRAP)
            } catch (e: Exception) {
                return null
            }
            if (dekBytes.size != 48) return null // 32-Byte-DEK plus 16-Byte-GCM-Tag

            val securityTier = json.optString("securityTier", "ONE_FACTOR")
            val factorType = json.optString("factorType", "PASSWORD")
            // The v2 crypto implementation must reject factor declarations it cannot enforce.
            val supportedFactorPolicy = when (securityTier) {
                "ONE_FACTOR" -> factorType == "PASSWORD"
                "TWO_FACTOR" -> factorType == "PASSWORD_AND_KEYFILE"
                else -> false
            }
            if (!supportedFactorPolicy) return null

            val autoLockMinutes = json.optInt("autoLockMinutes", 5)
            if (autoLockMinutes < 0 || autoLockMinutes > 1440) return null

            val domainSecurityTier = json.optString("domainSecurityTier", securityTier)
            if (domainSecurityTier !in setOf("UNENCRYPTED", "ONE_FACTOR", "TWO_FACTOR")) return null
            val domainFactorType = json.optString("domainFactorType", factorType)
            if (domainFactorType !in setOf("NONE", "PASSWORD", "KEYFILE", "PASSWORD_AND_KEYFILE", "PASSWORD_AND_TOTP", "PASSKEY")) return null
            val recoveryMethod = json.optString("recoveryMethod", "NONE")
            if (recoveryMethod !in setOf("NONE", "BIP39_WORDS", "EMERGENCY_PDF", "SHAMIR_2_OF_3", "RECOVERY_CODE")) return null
            if (domainSecurityTier == "TWO_FACTOR" && domainFactorType != "PASSWORD_AND_KEYFILE") return null
            val shares = json.optJSONArray("shamirShareHashes")
            val shareHashes = mutableListOf<String>()
            if (shares != null) {
                if (shares.length() > 3) return null
                for (index in 0 until shares.length()) {
                    val hash = shares.optString(index, "")
                    if (!hash.matches(Regex("[a-fA-F0-9]{64}"))) return null
                    shareHashes.add(hash)
                }
            }
            fun optionalString(name: String): String? =
                json.optString(name, "").takeIf { it.isNotBlank() && it != "null" }
            fun extensionHasSize(name: String, expected: Int): Boolean {
                val value = optionalString(name) ?: return true
                val decoded = runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull() ?: return false
                return decoded.size == expected
            }

            val encryptedBiometric = optionalString("encryptedDekByBiometricsBase64")
            val biometricIv = optionalString("biometricDekIvBase64")
            if ((encryptedBiometric == null) != (biometricIv == null)) return null
            if (!extensionHasSize("encryptedDekByBiometricsBase64", 48) ||
                !extensionHasSize("biometricDekIvBase64", 12)) return null
            val encryptedRecovery = optionalString("encryptedDekByRecoveryBase64")
            val recoveryIv = optionalString("recoveryDekIvBase64")
            if ((encryptedRecovery == null) != (recoveryIv == null)) return null
            if (!extensionHasSize("encryptedDekByRecoveryBase64", 48) ||
                !extensionHasSize("recoveryDekIvBase64", 12)) return null
            if (!extensionHasSize("emergencyPinSaltBase64", 16)) return null
            for (name in listOf("emergencyPdfChecksum", "keyFileTokenHash", "recoveryCodeHash")) {
                val value = optionalString(name)
                if (value != null && !value.matches(Regex("[a-fA-F0-9]{64}"))) return null
            }

            VaultHeaderV2(
                version = version,
                algorithmId = algorithmId,
                vaultId = vaultId,
                createdAt = createdAt,
                argon2Iterations = iterations,
                argon2MemoryKb = memoryKb,
                saltBase64 = saltBase64,
                ivBase64 = ivBase64,
                tagLengthBits = tagLengthBits,
                encryptedDekBase64 = encryptedDekBase64,
                securityTier = securityTier,
                factorType = factorType,
                autoLockMinutes = autoLockMinutes,
                domainSecurityTier = domainSecurityTier,
                domainFactorType = domainFactorType,
                biometric2FaEnabled = json.optBoolean("biometric2FaEnabled", encryptedBiometric != null),
                encryptedDekByBiometricsBase64 = encryptedBiometric,
                biometricDekIvBase64 = biometricIv,
                recoveryMethod = recoveryMethod,
                encryptedDekByRecoveryBase64 = encryptedRecovery,
                recoveryDekIvBase64 = recoveryIv,
                recoverySaltBase64 = optionalString("recoverySaltBase64"),
                emergencyPdfChecksum = optionalString("emergencyPdfChecksum"),
                shamirShareHashes = shareHashes,
                hasEmergencyPin = json.optBoolean("hasEmergencyPin", false),
                emergencyPinSaltBase64 = optionalString("emergencyPinSaltBase64"),
                totpSecret = optionalString("totpSecret"),
                keyFileTokenHash = optionalString("keyFileTokenHash"),
                recoveryCodeHash = optionalString("recoveryCodeHash")
            )
        } catch (e: Exception) {
            null
        }
    }
}
