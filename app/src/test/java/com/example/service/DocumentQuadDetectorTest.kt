package com.example.service

import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class DocumentQuadDetectorTest {
    private val detector = DocumentQuadDetector()
    private val w = 160
    private val h = 120

    @Test fun recognizesWhitePaperOnDarkBrownAndGrayBackgrounds() {
        for (background in listOf(25, 75, 130)) {
            val pixels = solid(background)
            rect(pixels, 160, 120, 35, 15, 125, 105, 235)
            assertDetected(pixels)
        }
    }

    @Test fun recognizesCreamPaper() {
        val pixels = solid(65)
        rect(pixels, w, h, 30, 12, 130, 108, 218)
        assertDetected(pixels)
    }

    @Test fun recognizesDarkPaperOnBrightTable() {
        val pixels = solid(232)
        rect(pixels, w, h, 30, 12, 130, 108, 45)
        assertDetected(pixels)
    }

    @Test fun recognizesSlightlyRotatedRectangle() {
        val pixels = solid(55)
        polygon(pixels, w, h, listOf(45f to 12f, 135f to 30f, 115f to 108f, 25f to 90f), 230)
        assertDetected(pixels)
    }

    @Test fun recognizesPerspectiveTrapezoid() {
        val pixels = solid(45)
        polygon(pixels, w, h, listOf(48f to 10f, 123f to 18f, 142f to 108f, 22f to 96f), 232)
        assertDetected(pixels)
    }

    @Test fun recognizesPaperWithTextLines() {
        val pixels = solid(50)
        rect(pixels, w, h, 30, 10, 130, 110, 235)
        for (y in 28..82 step 9) rect(pixels, w, h, 43, y, if (y % 2 == 0) 112 else 119, y + 2, 35)
        assertDetected(pixels)
    }

    @Test fun rejectsCircle() {
        val p = solid(40)
        ellipse(p, w, h, 80, 60, 45, 45, 230)
        assertNull(detector.detect(frame(p)).quad)
    }

    @Test fun rejectsHandLikeSilhouette() {
        val p = solid(45)
        ellipse(p, w, h, 80, 77, 27, 25, 205)
        val fingers = listOf(55 to 43, 68 to 31, 81 to 26, 94 to 33, 106 to 44)
        for ((x, y) in fingers) rect(p, w, h, x, y, x + 8, y + 35, 205)
        assertNull(detector.detect(frame(p)).quad)
    }

    @Test fun rejectsFootLikeShape() {
        val p = solid(45)
        ellipse(p, w, h, 82, 70, 25, 42, 205)
        ellipse(p, w, h, 82, 32, 18, 19, 205)
        assertNull(detector.detect(frame(p)).quad)
    }

    @Test fun rejectsPlantMadeOfOverlappingEllipses() {
        val p = solid(35)
        for (i in 0 until 9) ellipse(p, w, h, 35 + (i * 17) % 90, 20 + (i * 23) % 75, 16, 10, 210)
        assertNull(detector.detect(frame(p)).quad)
    }

    @Test fun rejectsUniformBrightWallAndNoise() {
        assertNull(detector.detect(frame(solid(235))).quad)
        val random = java.util.Random(17)
        val noisy = IntArray(w * h) { 90 + random.nextInt(80) }
        assertNull(detector.detect(frame(noisy)).quad)
    }

    @Test fun rejectsClippedAndOversizedRectangles() {
        val clipped = solid(40)
        rect(clipped, w, h, 0, 10, 110, 110, 230)
        assertNull(detector.detect(frame(clipped)).quad)
        val huge = solid(35)
        rect(huge, w, h, 4, 3, 156, 117, 230)
        assertNull(detector.detect(frame(huge)).quad)
    }

    @Test fun quadIsNormalizedAndOrderedTopLeftClockwise() {
        val p = solid(45)
        rect(p, w, h, 30, 12, 130, 108, 230)
        val q = detector.detect(frame(p)).quad
        assertNotNull(q)
        assertEquals(4, q!!.size)
        assertTrue(q.all { it.x in 0f..1f && it.y in 0f..1f })
        assertTrue(q[0].x < q[1].x && q[0].y < q[2].y)
    }

    @Test fun typicalFrameDetectionTargetUnder15ms() {
        val p = solid(55)
        rect(p, w, h, 30, 10, 130, 110, 230)
        val f = frame(p)
        repeat(3) { detector.detect(f) }
        val start = System.nanoTime()
        repeat(10) { detector.detect(f) }
        val averageMs = (System.nanoTime() - start) / 10.0 / 1_000_000.0
        assertTrue("average was $averageMs ms", averageMs < 15.0)
    }

    private fun assertDetected(p: IntArray) {
        val result = detector.detect(frame(p))
        assertNotNull("rejected: ${result.rejectReason}", result.quad)
        assertTrue("confidence ${result.confidence}", result.confidence >= DocumentDetectionConfig.MIN_ACCEPT_CONFIDENCE)
        assertEquals(4, result.quad!!.size)
    }

    private fun frame(pixels: IntArray): LumaChromaFrame =
        LumaChromaFrame(w, h, ByteArray(w * h) { pixels[it].toByte() })

    private fun solid(value: Int) = IntArray(w * h) { value }

    private fun rect(p: IntArray, width: Int, height: Int, x0: Int, y0: Int, x1: Int, y1: Int, value: Int) {
        for (y in y0.coerceAtLeast(0)..y1.coerceAtMost(height - 1)) for (x in x0.coerceAtLeast(0)..x1.coerceAtMost(width - 1)) p[y * width + x] = value
    }

    private fun ellipse(p: IntArray, width: Int, height: Int, cx: Int, cy: Int, rx: Int, ry: Int, value: Int) {
        for (y in 0 until height) for (x in 0 until width) {
            val dx = (x - cx).toFloat() / rx
            val dy = (y - cy).toFloat() / ry
            if (dx * dx + dy * dy <= 1f) p[y * width + x] = value
        }
    }

    private fun polygon(p: IntArray, width: Int, height: Int, points: List<Pair<Float, Float>>, value: Int) {
        for (y in 0 until height) for (x in 0 until width) {
            var inside = false
            var j = points.lastIndex
            for (i in points.indices) {
                val (xi, yi) = points[i]; val (xj, yj) = points[j]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = i
            }
            if (inside) p[y * width + x] = value
        }
    }
}
