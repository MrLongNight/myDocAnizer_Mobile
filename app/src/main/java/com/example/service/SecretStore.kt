package com.example.service

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

/**
 * Encrypted storage for small, long-lived application secrets.
 *
 * Values are encrypted with an app-private, non-exportable Android Keystore AES key.
 * A missing/invalidated key or malformed/authentication-failing value returns null;
 * this class never falls back to storing or returning plaintext.
 */
class SecretStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun put(name: String, secret: ByteArray): Boolean {
        if (!isValidName(name) || secret.isEmpty() || secret.size > MAX_SECRET_BYTES) return false
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(aad(name))
            val encrypted = cipher.doFinal(secret)
            val iv = cipher.iv
            if (iv.size != GCM_IV_BYTES) return false
            val payload = ByteArray(2 + iv.size + encrypted.size)
            payload[0] = FORMAT_VERSION
            payload[1] = iv.size.toByte()
            System.arraycopy(iv, 0, payload, 2, iv.size)
            System.arraycopy(encrypted, 0, payload, 2 + iv.size, encrypted.size)
            try {
                prefs.edit().putString(storageKey(name), Base64.encodeToString(payload, Base64.NO_WRAP)).commit()
            } finally {
                payload.fill(0)
                encrypted.fill(0)
                iv.fill(0)
            }
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun get(name: String): ByteArray? {
        if (!isValidName(name)) return null
        val encoded = prefs.getString(storageKey(name), null) ?: return null
        // Bound attacker-controlled SharedPreferences data before Base64 allocation.
        if (encoded.length > MAX_ENCODED_PAYLOAD_CHARS) return null
        var payload: ByteArray? = null
        var ciphertext: ByteArray? = null
        return try {
            val decoded = Base64.decode(encoded, Base64.NO_WRAP)
            payload = decoded
            if (decoded.size < 2 + GCM_IV_BYTES + GCM_TAG_BYTES ||
                decoded[0] != FORMAT_VERSION ||
                (decoded[1].toInt() and 0xff) != GCM_IV_BYTES
            ) return null
            val iv = decoded.copyOfRange(2, 2 + GCM_IV_BYTES)
            val encryptedBytes = decoded.copyOfRange(2 + GCM_IV_BYTES, decoded.size)
            ciphertext = encryptedBytes
            try {
                Cipher.getInstance(TRANSFORMATION).run {
                    init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
                    updateAAD(aad(name))
                    doFinal(encryptedBytes)
                }
            } finally {
                iv.fill(0)
            }
        } catch (_: Exception) {
            null
        } finally {
            payload?.fill(0)
            ciphertext?.fill(0)
        }
    }

    @Synchronized
    fun remove(name: String): Boolean {
        if (!isValidName(name)) return false
        return prefs.edit().remove(storageKey(name)).commit()
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw IllegalStateException("SecretStore key is missing")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun isValidName(name: String): Boolean =
        name.length in 1..120 && name.matches(Regex("[A-Za-z0-9_.-]+"))

    private fun storageKey(name: String) = "secret_v1_$name"
    private fun aad(name: String) = "myDocAnizer.SecretStore.v1:$name".toByteArray(Charsets.UTF_8)

    companion object {
        private const val PREFERENCES_NAME = "mydocanizer_secret_store"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mydocanizer_secret_store_aes_gcm_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        private const val MAX_SECRET_BYTES = 64 * 1024
        private const val MAX_ENCODED_PAYLOAD_CHARS = 90_000
    }
}
