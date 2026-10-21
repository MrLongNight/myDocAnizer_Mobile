package com.example.service

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: SecretStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("mydocanizer_secret_store", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = SecretStore(context)
    }

    @Test
    fun encryptedSecretRoundTrips() {
        val secret = "synthetic-secret-for-test-only".toByteArray(Charsets.UTF_8)
        assertTrue(store.put("instrumentation_test", secret))
        val recovered = store.get("instrumentation_test")
        assertArrayEquals(secret, recovered)
        recovered?.fill(0)
        secret.fill(0)
    }

    @Test
    fun modifiedCiphertextFailsClosed() {
        val secret = "another-synthetic-test-secret".toByteArray(Charsets.UTF_8)
        assertTrue(store.put("tamper_test", secret))
        val prefs = context.getSharedPreferences("mydocanizer_secret_store", Context.MODE_PRIVATE)
        val encoded = prefs.getString("secret_v1_tamper_test", null)
            ?: throw AssertionError("encrypted test payload missing")
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
        prefs.edit().putString("secret_v1_tamper_test", Base64.encodeToString(payload, Base64.NO_WRAP)).commit()
        assertNull(store.get("tamper_test"))
        payload.fill(0)
        secret.fill(0)
    }

    @Test
    fun malformedOrMissingSecretReturnsNull() {
        assertNull(store.get("not_present"))
        context.getSharedPreferences("mydocanizer_secret_store", Context.MODE_PRIVATE)
            .edit().putString("secret_v1_corrupt_test", "not-valid-ciphertext").commit()
        assertNull(store.get("corrupt_test"))
    }

    @Test
    fun ciphertextCannotBeMovedBetweenSecretNames() {
        val secret = "synthetic-aad-test".toByteArray(Charsets.UTF_8)
        assertTrue(store.put("source_name", secret))
        val prefs = context.getSharedPreferences("mydocanizer_secret_store", Context.MODE_PRIVATE)
        val encoded = prefs.getString("secret_v1_source_name", null)
            ?: throw AssertionError("encrypted test payload missing")
        prefs.edit().putString("secret_v1_destination_name", encoded).commit()
        assertNull(store.get("destination_name"))
        secret.fill(0)
    }

    @Test
    fun removeDeletesStoredCiphertext() {
        val secret = "synthetic-remove-test".toByteArray(Charsets.UTF_8)
        assertTrue(store.put("remove_test", secret))
        assertTrue(store.remove("remove_test"))
        assertNull(store.get("remove_test"))
        secret.fill(0)
    }

    @Test
    fun missingKeystoreKeyFailsClosed() {
        val secret = "synthetic-key-loss-test".toByteArray(Charsets.UTF_8)
        assertTrue(store.put("key_loss_test", secret))
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.deleteEntry("mydocanizer_secret_store_aes_gcm_v1")
        assertNull(store.get("key_loss_test"))
        secret.fill(0)
    }

    @Test
    fun oversizedSecretAndStoredPayloadAreRejected() {
        val oversizedSecret = ByteArray(64 * 1024 + 1) { 0x5a.toByte() }
        assertFalse(store.put("oversized_test", oversizedSecret))
        assertNull(store.get("oversized_test"))
        oversizedSecret.fill(0)

        context.getSharedPreferences("mydocanizer_secret_store", Context.MODE_PRIVATE)
            .edit().putString("secret_v1_oversized_payload", "A".repeat(90_001)).commit()
        assertNull(store.get("oversized_payload"))
    }

    @Test
    fun invalidNamesAreRejected() {
        assertFalse(store.put("../invalid", byteArrayOf(1, 2, 3)))
        assertNull(store.get("../invalid"))
    }
}
