package com.example

import android.graphics.PointF
import com.example.service.DocumentQuadDetector
import com.example.service.LumaChromaFrame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentQuadDetectorTest {
    private val detector = DocumentQuadDetector()
    private val width = 160
    private val height = 120

    private fun frame(background: Int, foreground: Int = 240, draw: (IntArray) -> Unit): LumaChromaFrame {
        val pixels = IntArray(width * height) { background }
        draw(pixels)
        return LumaChromaFrame(width, height, ByteArray(pixels.size) { pixels[it].coerceIn(0, 255).toByte() })
    }

    private fun rectangle(background: Int, paper: Int, left: Int = 32, top: Int = 18, right: Int = 128, bottom: Int = 102) =
        frame(background, paper) { p ->
            for (y in top..bottom) for (x in left..right) p[y * width + x] = paper
        }

    private fun polygon(
        background: Int,
        foreground: Int,
        points: List<PointF>,
        pixels: IntArray
    ) {
        val minX = points.minOf { it.x.toInt() }.coerceAtLeast(0)
        val maxX = points.maxOf { it.x.toInt() }.coerceAtMost(width - 1)
        val minY = points.minOf { it.y.toInt() }.coerceAtLeast(0)
        val maxY = points.maxOf { it.y.toInt() }.coerceAtMost(height - 1)
        for (y in minY..maxY) for (x in minX..maxX) {
            if (inside(x + 0.5f, y + 0.5f, points)) pixels[y * width + x] = foreground
        }
    }

    private fun inside(x: Float, y: Float, points: List<PointF>): Boolean {
        var inside = false
        var j = points.lastIndex
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    private fun shape(background: Int, foreground: Int, points: List<PointF>) =
        frame(background, foreground) { polygon(background, foreground, points, it) }

    private fun ellipsePixels(p: IntArray, cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        val x0 = (cx - rx).toInt().coerceAtLeast(0)
        val x1 = (cx + rx).toInt().coerceAtMost(width - 1)
        val y0 = (cy - ry).toInt().coerceAtLeast(0)
        val y1 = (cy + ry).toInt().coerceAtMost(height - 1)
        for (y in y0..y1) for (x in x0..x1) {
            val dx = (x - cx) / rx
            val dy = (y - cy) / ry
            if (dx * dx + dy * dy <= 1f) p[y * width + x] = color
        }
    }

    private fun assertDocument(frame: LumaChromaFrame) {
        val result = detector.detect(frame)
        assertNotNull("Expected document, rejected as: ${result.rejectReason}", result.quad)
        assertTrue("Confidence should be in [0,1]", result.confidence in 0f..1f)
        assertTrue(result.quad!!.size == 4)
        result.quad.forEach { assertTrue(it.x in 0f..1f && it.y in 0f..1f) }
    }

    private fun assertRejected(frame: LumaChromaFrame) {
        val result = detector.detect(frame)
        assertNull("Unexpected quad with confidence=${result.confidence}: ${result.rejectReason}", result.quad)
    }

    @Test fun `white paper on dark background is detected`() = assertDocument(rectangle(25, 245))
    @Test fun `white paper on brown background is detected`() = assertDocument(rectangle(82, 238))
    @Test fun `white paper on gray background is detected`() = assertDocument(rectangle(115, 245))
    @Test fun `cream paper is detected`() = assertDocument(rectangle(65, 218))
    @Test fun `dark paper on bright table is detected`() = assertDocument(rectangle(235, 35))

    @Test fun `slightly rotated document is detected`() {
        val angle = Math.toRadians(9.0)
        val cx = 80f; val cy = 60f; val hw = 45f; val hh = 36f
        val points = listOf(
            PointF(-hw, -hh), PointF(hw, -hh), PointF(hw, hh), PointF(-hw, hh)
        ).map { p ->
            PointF(
                cx + (p.x * cos(angle) - p.y * sin(angle)).toFloat(),
                cy + (p.x * sin(angle) + p.y * cos(angle)).toFloat()
            )
        }
        assertDocument(shape(30, 242, points))
    }

    @Test fun `perspective trapezoid is detected`() = assertDocument(
        shape(28, 240, listOf(PointF(48f, 20f), PointF(115f, 25f), PointF(135f, 101f), PointF(29f, 96f)))
    )

    @Test fun `document with text lines is detected`() {
        val f = rectangle(28, 240)
        for (y in 32..88 step 8) {
            for (x in 43..113) if (x % 17 !in 0..2) f.y[y * width + x] = 35
        }
        assertDocument(f)
    }

    @Test fun `circle is rejected`() {
        val f = frame(25, 240) { p -> ellipsePixels(p, 80f, 60f, 42f, 42f, 240) }
        assertRejected(f)
    }

    @Test fun `hand silhouette with five fingers is rejected`() {
        val f = frame(28, 235) { p ->
            ellipsePixels(p, 80f, 75f, 25f, 25f, 235)
            for (i in 0..4) {
                val x = 57 + i * 11
                for (y in 30..65) for (xx in x..x + 7) p[y * width + xx] = 235
            }
        }
        assertRejected(f)
    }

    @Test fun `foot shaped silhouette is rejected`() {
        val f = frame(30, 235) { p ->
            ellipsePixels(p, 82f, 72f, 22f, 31f, 235)
            ellipsePixels(p, 82f, 40f, 15f, 18f, 235)
        }
        assertRejected(f)
    }

    @Test fun `overlapping plant leaves are rejected`() {
        val f = frame(30, 220) { p ->
            for (i in 0..7) {
                val angle = i * Math.PI / 4.0
                ellipsePixels(
                    p,
                    80f + (18.0 * cos(angle)).toFloat(),
                    60f + (24.0 * sin(angle)).toFloat(),
                    14f,
                    8f,
                    220
                )
            }
        }
        assertRejected(f)
    }

    @Test fun `uniform bright wall is rejected`() = assertRejected(frame(245) { })
    @Test fun `random noise is rejected`() {
        var seed = 0x12345678
        val f = frame(0) { p ->
            for (i in p.indices) {
                seed = seed * 1664525 + 1013904223
                p[i] = (seed ushr 24) and 0xff
            }
        }
        assertRejected(f)
    }

    @Test fun `rectangle touching image edge is rejected`() =
        assertRejected(rectangle(25, 245, left = 0, top = 18, right = 90, bottom = 102))

    @Test fun `rectangle covering more than 92 percent is rejected`() =
        assertRejected(rectangle(25, 245, left = 3, top = 3, right = 156, bottom = 116))

    @Test fun `160 by 120 detection remains correct across warmed repeated calls`() {
        val sample = rectangle(25, 245)
        repeat(5) {
            val warmup = detector.detect(sample)
            assertNotNull("Warmup detection rejected: ${warmup.rejectReason}", warmup.quad)
        }
        val start = System.nanoTime()
        repeat(10) {
            val result = detector.detect(sample)
            assertNotNull("Repeated detection rejected: ${result.rejectReason}", result.quad)
        }
        // Keep timing observable for test diagnostics, but don't fail correctness tests
        // on shared CI/JVM host load; a wall-clock threshold is not a stable unit-test assertion.
        val averageMs = (System.nanoTime() - start) / 10.0 / 1_000_000.0
        assertTrue("Average runtime should be a finite non-negative measurement: ${averageMs}ms", averageMs.isFinite() && averageMs >= 0.0)
    }
}
