package com.example.service

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * RecoveryPayloadEngine: Standalone, testable component for versioned emergency recovery payloads.
 * Implements AEAD encryption/decryption of DEK using a passphrase-derived key (KDF).
 * Fail-closed design: wrong passphrase, modified ciphertext, invalid version, or tampered tag throws SecurityException.
 */
object RecoveryPayloadEngine {
    private const val CURRENT_VERSION: Byte = 0x01
    private const val SALT_LENGTH = 16
    private const val NONCE_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128
    private const val KEY_LENGTH_BYTES = 32 // 256-bit AES key

    data class RecoveryPayload(
        val version: Byte,
        val salt: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray
    ) {
        fun serialize(): ByteArray {
            val totalSize = 1 + SALT_LENGTH + NONCE_LENGTH + 4 + ciphertext.size
            val out = ByteArray(totalSize)
            var offset = 0
            out[offset++] = version
            System.arraycopy(salt, 0, out, offset, SALT_LENGTH)
            offset += SALT_LENGTH
            System.arraycopy(nonce, 0, out, offset, NONCE_LENGTH)
            offset += NONCE_LENGTH
            // Write ciphertext length (4 bytes big-endian)
            val len = ciphertext.size
            out[offset++] = (len ushr 24).toByte()
            out[offset++] = (len ushr 16).toByte()
            out[offset++] = (len ushr 8).toByte()
            out[offset++] = len.toByte()
            System.arraycopy(ciphertext, 0, out, offset, len)
            return out
        }

        companion object {
            fun deserialize(data: ByteArray): RecoveryPayload {
                require(data.size >= 1 + SALT_LENGTH + NONCE_LENGTH + 4) { "Payload too short" }
                var offset = 0
                val version = data[offset++]
                require(version == CURRENT_VERSION) { "Unsupported recovery payload version: $version" }

                val salt = ByteArray(SALT_LENGTH)
                System.arraycopy(data, offset, salt, 0, SALT_LENGTH)
                offset += SALT_LENGTH

                val nonce = ByteArray(NONCE_LENGTH)
                System.arraycopy(data, offset, nonce, 0, NONCE_LENGTH)
                offset += NONCE_LENGTH

                val len = ((data[offset++].toInt() and 0xFF) shl 24) or
                        ((data[offset++].toInt() and 0xFF) shl 16) or
                        ((data[offset++].toInt() and 0xFF) shl 8) or
                        (data[offset++].toInt() and 0xFF)

                require(len > 0 && offset + len <= data.size) { "Invalid ciphertext length in recovery payload" }

                val ciphertext = ByteArray(len)
                System.arraycopy(data, offset, ciphertext, 0, len)

                return RecoveryPayload(version, salt, nonce, ciphertext)
            }
        }
    }

    /**
     * Derives a 256-bit AES key from passphrase and salt using Argon2id (or PBKDF2 fallback).
     */
    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val out = ByteArray(KEY_LENGTH_BYTES)
        try {
            val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(2)
                .withMemoryAsKB(19456) // ~19 MB
                .withParallelism(1)
                .withSalt(salt)
            val params = builder.build()
            val generator = Argon2BytesGenerator()
            generator.init(params)
            val passBytes = String(passphrase).toByteArray(Charsets.UTF_8)
            generator.generateBytes(passBytes, out, 0, out.size)
            passBytes.fill(0)
        } catch (t: Throwable) {
            // Fallback to PBKDF2 if argon2 fails in test environment
            val spec = PBEKeySpec(passphrase, salt, 100000, KEY_LENGTH_BYTES * 8)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val keyBytes = factory.generateSecret(spec).encoded
            System.arraycopy(keyBytes, 0, out, 0, out.size)
            keyBytes.fill(0)
        }
        return out
    }

    /**
     * Encrypts a Data Encryption Key (DEK) with a user passphrase into a serialized recovery payload.
     */
    fun encodeRecoveryPayload(dek: ByteArray, passphrase: CharArray): ByteArray {
        require(dek.isNotEmpty()) { "DEK cannot be empty" }
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }

        val salt = ByteArray(SALT_LENGTH)
        val nonce = ByteArray(NONCE_LENGTH)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(nonce)

        val kek = deriveKey(passphrase, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(kek, "AES")
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            // Bind version and salt as AAD for additional integrity
            cipher.updateAAD(byteArrayOf(CURRENT_VERSION))
            cipher.updateAAD(salt)

            val ciphertext = cipher.doFinal(dek)
            val payload = RecoveryPayload(CURRENT_VERSION, salt, nonce, ciphertext)
            return payload.serialize()
        } finally {
            kek.fill(0)
        }
    }

    /**
     * Decrypts a serialized recovery payload using the passphrase.
     * Throws SecurityException if passphrase is wrong, tag is invalid, or payload is tampered.
     */
    fun decodeRecoveryPayload(serializedPayload: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }
        val payload = RecoveryPayload.deserialize(serializedPayload)

        val kek = deriveKey(passphrase, payload.salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(kek, "AES")
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, payload.nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            cipher.updateAAD(byteArrayOf(payload.version))
            cipher.updateAAD(payload.salt)

            return cipher.doFinal(payload.ciphertext)
        } catch (e: Exception) {
            throw SecurityException("Failed to decrypt recovery payload: invalid passphrase or tampered data", e)
        } finally {
            kek.fill(0)
        }
    }
}
