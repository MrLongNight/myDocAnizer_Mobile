package com.example

import android.graphics.Bitmap
import android.graphics.Color
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

        val shoeGrid = IntArray(8 * 8) { 30 }
        for (row in 2..5) {
            for (column in 1..6) {
                val roundedEnd = (row == 2 || row == 5) && (column == 1 || column == 6)
                if (!roundedEnd) shoeGrid[row * 8 + column] = 225
            }
        }
        assertFalse("A bright elongated shoe-like silhouette is not a document", hasCoherentPaperGrid(shoeGrid))
    }

    @Test
    fun `automatic crop corners follow a skewed page instead of its axis aligned bounds`() {
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        try {
            // Fill the synthetic page directly in pixels. This avoids relying on
            // Robolectric's Canvas Path rasterizer, while keeping the skewed geometry real.
            val page = listOf(
                android.graphics.PointF(140f, 90f),
                android.graphics.PointF(460f, 150f),
                android.graphics.PointF(390f, 510f),
                android.graphics.PointF(80f, 430f)
            )
            val pixels = IntArray(600 * 600) { Color.rgb(25, 25, 25) }
            for (y in 0 until 600) {
                for (x in 0 until 600) {
                    val px = x + 0.5f
                    val py = y + 0.5f
                    var inside = false
                    var previous = page.last()
                    for (current in page) {
                        if ((current.y > py) != (previous.y > py) &&
                            px < (previous.x - current.x) * (py - current.y) /
                                (previous.y - current.y) + current.x
                        ) {
                            inside = !inside
                        }
                        previous = current
                    }
                    if (inside) pixels[y * 600 + x] = Color.WHITE
                }
            }
            bitmap.setPixels(pixels, 0, 600, 0, 0, 600, 600)

            val corners = ImageProcessingService.detectDocumentCorners(bitmap)
            assertEquals(4, corners.size)
            assertTrue("Top edge should retain perspective; detected corners=$corners", abs(corners[0].y - corners[1].y) > 20f)
            assertTrue("Left edge should retain perspective", abs(corners[0].x - corners[3].x) > 20f)

            val warped = ImageProcessingService.cropAndWarpPerspective(bitmap, corners)
            try {
                assertTrue(warped.width > 250)
                assertTrue(warped.height > 250)
                // Check the warped page interior, not the first few edge pixels:
                // the detector intentionally expands corners slightly, so interpolation
                // and edge coverage can legitimately leave the extreme output corner dark.
                val interiorPixel = warped.getPixel(warped.width / 2, warped.height / 2)
                assertTrue(
                    "Perspective warp should map the page interior to white; pixel=$interiorPixel",
                    Color.red(interiorPixel) > 200 &&
                        Color.green(interiorPixel) > 200 &&
                        Color.blue(interiorPixel) > 200
                )
            } finally {
                if (warped !== bitmap && !warped.isRecycled) warped.recycle()
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
