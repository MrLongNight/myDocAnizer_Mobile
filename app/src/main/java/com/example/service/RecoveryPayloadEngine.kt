package com.example.service

import android.util.Base64
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned recovery payload defined by docs/security/adr-001-recovery-format.md.
 *
 * The payload contains only an AES-GCM-encrypted 256-bit DEK. The vault ID is
 * authenticated as AAD and must also match the vault selected by the caller.
 */
object RecoveryPayloadEngine {
    private const val CURRENT_VERSION = 2
    private const val ALGORITHM = "AES-256-GCM-Argon2id"
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val DEK_LENGTH_BYTES = 32
    private const val CIPHERTEXT_LENGTH_BYTES = DEK_LENGTH_BYTES + TAG_LENGTH_BITS / 8
    private const val KEY_LENGTH_BYTES = 32
    private const val ARGON2_ITERATIONS = 2
    private const val ARGON2_MEMORY_KB = 19456
    private const val MAX_SERIALIZED_BYTES = 16 * 1024
    private const val MIN_PASSPHRASE_CHARS = 20

    data class RecoveryPayload(
        val version: Int,
        val algorithm: String,
        val vaultId: String,
        val salt: ByteArray,
        val iv: ByteArray,
        val ciphertext: ByteArray,
        val tagBits: Int
    ) {
        fun serialize(): ByteArray {
            validateFields(this)
            val json = JSONObject().apply {
                put("version", version)
                put("alg", algorithm)
                put("vaultId", vaultId)
                put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                put("tagBits", tagBits)
            }
            return json.toString().toByteArray(StandardCharsets.UTF_8).also {
                require(it.size <= MAX_SERIALIZED_BYTES) { "Recovery payload too large" }
            }
        }

        companion object {
            fun deserialize(data: ByteArray): RecoveryPayload {
                require(data.isNotEmpty() && data.size <= MAX_SERIALIZED_BYTES) {
                    "Invalid recovery payload size"
                }
                val json = try {
                    val tokener = JSONTokener(String(data, StandardCharsets.UTF_8))
                    val value = tokener.nextValue()
                    require(value is JSONObject) { "Recovery payload must be a JSON object" }
                    require(tokener.nextClean() == '\u0000') { "Trailing data after recovery payload" }
                    value
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid recovery payload encoding", e)
                }
                val version = json.optInt("version", -1)
                require(version == CURRENT_VERSION) { "Unsupported recovery payload version" }
                val algorithm = json.optString("alg", "")
                require(algorithm == ALGORITHM) { "Unsupported recovery algorithm" }
                val vaultId = json.optString("vaultId", "")
                require(isValidVaultId(vaultId)) { "Invalid recovery vault ID" }
                val salt = decodeBase64(json.optString("salt", ""), "salt")
                val iv = decodeBase64(json.optString("iv", ""), "IV")
                val ciphertext = decodeBase64(json.optString("ciphertext", ""), "ciphertext")
                val tagBits = json.optInt("tagBits", -1)
                val payload = RecoveryPayload(version, algorithm, vaultId, salt, iv, ciphertext, tagBits)
                validateFields(payload)
                return payload
            }
        }
    }

    private fun validateFields(payload: RecoveryPayload) {
        require(payload.version == CURRENT_VERSION) { "Unsupported recovery payload version" }
        require(payload.algorithm == ALGORITHM) { "Unsupported recovery algorithm" }
        require(isValidVaultId(payload.vaultId)) { "Invalid recovery vault ID" }
        require(payload.salt.size == SALT_LENGTH) { "Invalid recovery salt length" }
        require(payload.iv.size == NONCE_LENGTH) { "Invalid recovery IV length" }
        require(payload.ciphertext.size == CIPHERTEXT_LENGTH_BYTES) { "Invalid recovery ciphertext length" }
        require(payload.tagBits == TAG_LENGTH_BITS) { "Unsupported recovery tag length" }
    }

    private fun isValidVaultId(vaultId: String): Boolean =
        vaultId.length in 8..64 &&
            vaultId.startsWith("VAULT-") &&
            vaultId.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun decodeBase64(value: String, field: String): ByteArray {
        require(value.isNotBlank()) { "Missing recovery $field" }
        return try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid recovery $field encoding", e)
        }
    }

    private fun aad(vaultId: String): ByteArray =
        "myDocAnizer-Recovery-v2:$vaultId".toByteArray(StandardCharsets.UTF_8)

    /**
     * Derives a KEK with the exact Argon2id parameters approved in ADR 001.
     * No alternate KDF is used: failure must fail closed rather than create an
     * incompatible payload with an implicit algorithm change.
     */
    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) {
            "Recovery passphrase must contain at least $MIN_PASSPHRASE_CHARS characters"
        }
        return Argon2idEngine.deriveKey(
            password = passphrase,
            salt = salt,
            iterations = ARGON2_ITERATIONS,
            memoryKb = ARGON2_MEMORY_KB,
            tagLength = KEY_LENGTH_BYTES
        )
    }

    /** Encrypts exactly one 256-bit DEK using the ADR-approved v2 format. */
    fun encodeRecoveryPayload(dek: ByteArray, passphrase: CharArray, vaultId: String): ByteArray {
        require(dek.size == DEK_LENGTH_BYTES) { "DEK must be exactly 256 bits" }
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }
        require(isValidVaultId(vaultId)) { "Invalid recovery vault ID" }

        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(NONCE_LENGTH)
        SecureRandom().apply {
            nextBytes(salt)
            nextBytes(iv)
        }

        val kek = deriveKey(passphrase, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.updateAAD(aad(vaultId))
            val ciphertext = cipher.doFinal(dek)
            return RecoveryPayload(
                CURRENT_VERSION, ALGORITHM, vaultId, salt, iv, ciphertext, TAG_LENGTH_BITS
            ).serialize()
        } finally {
            kek.fill(0)
        }
    }

    /**
     * Decrypts a v2 payload only for the expected vault. Wrong passphrase,
     * wrong vault ID, malformed input, or authentication failure never returns
     * a partial DEK.
     */
    fun decodeRecoveryPayload(
        serializedPayload: ByteArray,
        passphrase: CharArray,
        expectedVaultId: String
    ): ByteArray {
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) {
            "Recovery passphrase must contain at least $MIN_PASSPHRASE_CHARS characters"
        }
        require(isValidVaultId(expectedVaultId)) { "Invalid expected vault ID" }
        val payload = RecoveryPayload.deserialize(serializedPayload)
        if (payload.vaultId != expectedVaultId) {
            throw SecurityException("Recovery payload belongs to a different vault")
        }

        val kek = deriveKey(passphrase, payload.salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_LENGTH_BITS, payload.iv))
            cipher.updateAAD(aad(payload.vaultId))
            val dek = cipher.doFinal(payload.ciphertext)
            if (dek.size != DEK_LENGTH_BYTES) {
                dek.fill(0)
                throw SecurityException("Invalid recovered DEK length")
            }
            return dek
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw SecurityException("Failed to decrypt recovery payload: invalid passphrase or tampered data", e)
        } finally {
            kek.fill(0)
        }
    }
}
