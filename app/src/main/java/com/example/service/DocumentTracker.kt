package com.example.service

import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Stateful temporal filter for normalized document corners.
 *
 * cornerMotion is the average corner displacement divided by the normalized image
 * diagonal (sqrt(2)); 0.03 therefore means about 3% of the frame diagonal.
 */
class DocumentTracker(
    private val confidenceThreshold: Float = LOCK_CONFIDENCE,
    private val maxFrameCornerMotion: Float = MAX_FRAME_CORNER_MOTION,
    private val smoothingAlpha: Float = SMOOTHING_ALPHA,
    private val lockWindowSize: Int = LOCK_WINDOW_SIZE,
    private val lockRequiredFrames: Int = LOCK_REQUIRED_FRAMES,
    private val unlockFailureFrames: Int = UNLOCK_FAILURE_FRAMES,
    private val stableWindowMs: Long = STABLE_WINDOW_MS,
    private val stableMotionThreshold: Float = STABLE_CORNER_MOTION
) {
    data class Result(
        val quadSmoothed: List<PointF>?,
        val isLocked: Boolean,
        val isStable: Boolean,
        val cornerMotion: Float,
        val lockJustAcquired: Boolean = false
    )

    private data class Observation(val qualifies: Boolean, val quad: List<PointF>?)
    private data class TimedQuad(val timestampMs: Long, val quad: List<PointF>)

    private val observations = ArrayDeque<Observation>()
    private val stabilityHistory = ArrayDeque<TimedQuad>()
    private var lastCandidate: List<PointF>? = null
    private var smoothed: List<PointF>? = null
    private var locked = false
    private var consecutiveFailures = 0
    private var lastTimestampMs = Long.MIN_VALUE

    @Synchronized
    fun update(detection: DocumentDetection, timestampMs: Long): Result {
        require(timestampMs >= lastTimestampMs) { "timestamps must be monotonic" }
        lastTimestampMs = timestampMs

        val rawQuad = detection.quad?.takeIf {
            it.size == 4 && detection.confidence >= confidenceThreshold
        }?.map { PointF(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }

        var cornerMotion = 0f
        var qualifies = rawQuad != null
        if (rawQuad != null) {
            val previousCandidate = lastCandidate
            if (previousCandidate != null) {
                cornerMotion = normalizedCornerMotion(previousCandidate, rawQuad)
                if (cornerMotion >= maxFrameCornerMotion) qualifies = false
            }
            // Rebase after a jump so a genuinely new, stable page can be acquired.
            lastCandidate = rawQuad
        }

        observations.addLast(Observation(qualifies, if (qualifies) rawQuad else null))
        while (observations.size > lockWindowSize) observations.removeFirst()

        val lockJustAcquired: Boolean
        if (qualifies) {
            consecutiveFailures = 0
            if (smoothed == null) smoothed = rawQuad!!.map { PointF(it.x, it.y) }
            else smoothed = smoothQuad(smoothed!!, rawQuad!!, smoothingAlpha)
        } else {
            consecutiveFailures++
        }

        if (!locked && observations.count { it.qualifies } >= lockRequiredFrames) {
            locked = true
            consecutiveFailures = 0
            stabilityHistory.clear()
            lockJustAcquired = true
        } else {
            lockJustAcquired = false
        }

        if (locked && consecutiveFailures >= unlockFailureFrames) {
            locked = false
            stabilityHistory.clear()
            smoothed = null
            lastCandidate = null
            observations.clear()
        }

        val currentSmoothed = smoothed
        if (locked && qualifies && currentSmoothed != null) {
            stabilityHistory.addLast(TimedQuad(timestampMs, currentSmoothed.map { PointF(it.x, it.y) }))
        }
        while (stabilityHistory.isNotEmpty() &&
            timestampMs - stabilityHistory.first().timestampMs > stableWindowMs
        ) {
            stabilityHistory.removeFirst()
        }

        val stable = locked && qualifies && isStableHistory(timestampMs)
        return Result(
            quadSmoothed = currentSmoothed?.map { PointF(it.x, it.y) },
            isLocked = locked,
            isStable = stable,
            cornerMotion = cornerMotion,
            lockJustAcquired = lockJustAcquired
        )
    }

    @Synchronized
    fun reset() {
        observations.clear()
        stabilityHistory.clear()
        lastCandidate = null
        smoothed = null
        locked = false
        consecutiveFailures = 0
        lastTimestampMs = Long.MIN_VALUE
    }

    private fun isStableHistory(nowMs: Long): Boolean {
        if (stabilityHistory.size < 2) return false
        if (nowMs - stabilityHistory.first().timestampMs < stableWindowMs * 4 / 5) return false
        val latest = stabilityHistory.last().quad
        return stabilityHistory.all { normalizedCornerMotion(it.quad, latest) < stableMotionThreshold }
    }

    private fun smoothQuad(old: List<PointF>, next: List<PointF>, alpha: Float): List<PointF> =
        old.indices.map { i ->
            PointF(
                old[i].x + (next[i].x - old[i].x) * alpha,
                old[i].y + (next[i].y - old[i].y) * alpha
            )
        }

    private fun normalizedCornerMotion(a: List<PointF>, b: List<PointF>): Float {
        if (a.size != 4 || b.size != 4) return 1f
        var sum = 0.0
        for (i in 0..3) {
            val dx = (a[i].x - b[i].x).toDouble()
            val dy = (a[i].y - b[i].y).toDouble()
            sum += sqrt(dx * dx + dy * dy)
        }
        return (sum / 4.0 / sqrt(2.0)).toFloat()
    }

    companion object {
        const val LOCK_CONFIDENCE = 0.55f
        const val MAX_FRAME_CORNER_MOTION = 0.03f
        const val SMOOTHING_ALPHA = 0.4f
        const val LOCK_WINDOW_SIZE = 7
        const val LOCK_REQUIRED_FRAMES = 5
        const val UNLOCK_FAILURE_FRAMES = 6
        const val STABLE_WINDOW_MS = 500L
        const val STABLE_CORNER_MOTION = 0.015f
    }
}
