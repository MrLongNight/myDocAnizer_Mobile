package com.example

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.example.service.VaultSecurityManager
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class QrEncodingTest {
    private val manager by lazy {
        VaultSecurityManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    fun `standard QR decodes exact UTF8 payload with independent ZXing reader`() {
        val payload = """{"v":2,"vaultId":"VAULT-TEST123","encryptedPayload":"synthesized-test-only-1234567890"}"""
        val bitmap = manager.generateQrCodeBitmap(payload, 500, 500)
        assertNotNull(bitmap)
        bitmap!!
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(payload, decoded.text)
        bitmap.recycle()
    }

    @Test
    fun `QR generator rejects oversized payload without fallback pattern`() {
        assertNull(manager.generateQrCodeBitmap("x".repeat(VaultSecurityManager.MAX_QR_PAYLOAD_BYTES + 1), 500, 500))
    }
}
