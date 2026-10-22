package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.example.service.ImageProcessingService
import com.example.ui.components.hasCoherentPaperGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanDetectionRegressionTest {

    @Test
    fun `rectangular bright area passes the live paper-shape gate but a candle-like circle does not`() {
        val paperGrid = IntArray(8 * 8) { 30 }
        for (row in 1..6) {
            for (column in 1..6) {
                paperGrid[row * 8 + column] = 225
            }
        }
        assertTrue(hasCoherentPaperGrid(paperGrid))

        val candleGrid = IntArray(8 * 8) { 30 }
        for (row in 1..6) {
            for (column in 1..6) {
                val dx = column - 3.5f
                val dy = row - 3.5f
                if (dx * dx + dy * dy <= 2.8f * 2.8f) {
                    candleGrid[row * 8 + column] = 225
                }
            }
        }
        assertFalse(hasCoherentPaperGrid(candleGrid))
    }

    @Test
    fun `automatic crop corners follow a skewed page instead of its axis aligned bounds`() {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(25, 25, 25))
            val page = Path().apply {
                moveTo(140f, 90f)
                lineTo(460f, 150f)
                lineTo(390f, 510f)
                lineTo(80f, 430f)
                close()
            }
            canvas.drawPath(
                page,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.FILL
                }
            )

            val corners = ImageProcessingService.detectDocumentCorners(bitmap)
            assertEquals(4, corners.size)
            assertTrue("Top edge should retain perspective", abs(corners[0].y - corners[1].y) > 20f)
            assertTrue("Left edge should retain perspective", abs(corners[0].x - corners[3].x) > 20f)

            val warped = ImageProcessingService.cropAndWarpPerspective(bitmap, corners)
            try {
                assertTrue(warped.width > 250)
                assertTrue(warped.height > 250)
            } finally {
                if (warped !== bitmap && !warped.isRecycled) warped.recycle()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
