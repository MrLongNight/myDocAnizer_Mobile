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
    val autoLockMinutes: Int = 5
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
            if (iterations < 1 || iterations > 10) return null // Obergrenze und Untergrenze prüfen

            val memoryKb = json.optInt("argon2MemoryKb", -1)
            if (memoryKb < 1024 || memoryKb > 262144) return null // 1 MB bis 256 MB Obergrenze

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
            if (dekBytes.size < 32) return null // Mindestens AES-256 Ciphertext-Länge inklusive Tag

            val securityTier = json.optString("securityTier", "ONE_FACTOR")
            if (securityTier !in listOf("UNENCRYPTED", "ONE_FACTOR", "TWO_FACTOR")) return null

            val factorType = json.optString("factorType", "PASSWORD")
            if (factorType !in listOf("NONE", "PASSWORD", "KEYFILE", "PASSWORD_AND_KEYFILE", "PASSWORD_AND_TOTP", "PASSKEY")) return null

            val autoLockMinutes = json.optInt("autoLockMinutes", 5)
            if (autoLockMinutes < 0 || autoLockMinutes > 1440) return null

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
                autoLockMinutes = autoLockMinutes
            )
        } catch (e: Exception) {
            null
        }
    }
}
