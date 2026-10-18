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
        assertTrue(encoded != null)
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
    fun invalidNamesAreRejected() {
        assertFalse(store.put("../invalid", byteArrayOf(1, 2, 3)))
        assertNull(store.get("../invalid"))
    }
}
