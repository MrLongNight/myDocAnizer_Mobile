package com.example

import android.graphics.PointF
import com.example.service.DocumentDetection
import com.example.service.DocumentTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentTrackerTest {
    private val page = listOf(
        PointF(0.20f, 0.15f),
        PointF(0.80f, 0.15f),
        PointF(0.80f, 0.85f),
        PointF(0.20f, 0.85f)
    )

    private fun detection(quad: List<PointF>? = page, confidence: Float = 0.82f) =
        DocumentDetection(quad?.map { PointF(it.x, it.y) }, confidence, if (quad == null) "kein Viereck" else "erkannt")

    @Test
    fun `stable high confidence frames acquire lock and become stable`() {
        val tracker = DocumentTracker()
        var result = tracker.update(detection(), 0L)
        for (i in 1..9) result = tracker.update(detection(), i * 100L)

        assertTrue("Five qualifying frames should acquire lock", result.isLocked)
        assertTrue("Corners should settle after roughly 500ms", result.isStable)
        assertNotNull(result.quadSmoothed)
    }

    @Test
    fun `flashing detections do not acquire lock`() {
        val tracker = DocumentTracker()
        var result = tracker.update(detection(), 0L)
        for (i in 1..13) {
            val sample = if (i % 2 == 0) detection() else detection(confidence = 0.30f)
            result = tracker.update(sample, i * 100L)
        }
        assertFalse("Fewer than five of seven frames should never lock", result.isLocked)
    }

    @Test
    fun `two or three missing frames do not release an acquired lock`() {
        val tracker = DocumentTracker()
        for (i in 0..6) tracker.update(detection(), i * 100L)
        var result = tracker.update(detection(quad = null), 700L)
        result = tracker.update(detection(quad = null), 800L)
        result = tracker.update(detection(quad = null), 900L)
        assertTrue(result.isLocked)
        result = tracker.update(detection(), 1000L)
        assertTrue(result.isLocked)
    }

    @Test
    fun `six consecutive missing frames release lock`() {
        val tracker = DocumentTracker()
        for (i in 0..6) tracker.update(detection(), i * 100L)
        var result = tracker.update(detection(quad = null), 700L)
        for (i in 1..5) result = tracker.update(detection(quad = null), 700L + i * 100L)
        assertFalse(result.isLocked)
        assertTrue(result.quadSmoothed == null)
    }

    @Test
    fun `jumping corners are not considered stable`() {
        val tracker = DocumentTracker()
        var result = tracker.update(detection(), 0L)
        for (i in 1..9) result = tracker.update(detection(), i * 100L)
        assertTrue(result.isLocked)
        assertTrue(result.isStable)

        val jumped = page.map { PointF(it.x + 0.08f, it.y) }
        result = tracker.update(detection(jumped), 1000L)
        assertFalse("A sudden corner jump invalidates stability", result.isStable)
        result = tracker.update(detection(jumped), 1100L)
        assertFalse("EMA should not immediately report a moved page as stable", result.isStable)
    }
}
