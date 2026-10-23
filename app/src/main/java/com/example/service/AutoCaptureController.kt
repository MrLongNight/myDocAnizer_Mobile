package com.example.service

import java.util.ArrayDeque

const val WINDOW_SIZE = 15
const val MIN_CONFIDENCE = 0.82f
const val MIN_AREA = 0.12f
const val MAX_JITTER_NORM = 0.012f
const val COOLDOWN_MS = 2500L

data class TrackerStatus(
    val smoothedQuad: NormalizedQuad?,
    val stabilityProgress: Float,
    val shouldTriggerCapture: Boolean
)

class AutoCaptureController {
    private val quadFilter = OneEuroQuadFilter()
    private val stableWindow = ArrayDeque<NormalizedQuad>(WINDOW_SIZE)

    private var lastCaptureTimeMs: Long = 0L
    private var missingFramesCount: Int = 0

    fun processFrame(rawQuad: NormalizedQuad?, timestampNs: Long): TrackerStatus {
        if (rawQuad == null || rawQuad.confidence < MIN_CONFIDENCE) {
            missingFramesCount++
            if (missingFramesCount > 5) {
                stableWindow.clear()
                quadFilter.reset()
            }
            return TrackerStatus(null, 0f, false)
        }

        missingFramesCount = 0
        val smoothed = quadFilter.filter(rawQuad, timestampNs)
        if (!smoothed.isConvex() || smoothed.area() < MIN_AREA) {
            stableWindow.clear()
            return TrackerStatus(smoothed, 0f, false)
        }

        if (stableWindow.size == WINDOW_SIZE) {
            stableWindow.removeFirst()
        }
        stableWindow.addLast(smoothed)

        while (stableWindow.size > 1 &&
            smoothed.maxCornerDistanceTo(stableWindow.first()) > MAX_JITTER_NORM
        ) {
            stableWindow.removeFirst()
        }

        val progress = (stableWindow.size.toFloat() / WINDOW_SIZE.toFloat())
            .coerceIn(0f, 1f)
        val nowMs = System.currentTimeMillis()
        val trigger = stableWindow.size == WINDOW_SIZE &&
            nowMs - lastCaptureTimeMs > COOLDOWN_MS

        if (trigger) {
            lastCaptureTimeMs = nowMs
            stableWindow.clear()
        }

        return TrackerStatus(smoothed, progress, trigger)
    }
}
