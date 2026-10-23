package com.example.service

import kotlin.math.PI
import kotlin.math.abs

class OneEuroScalarFilter(
    private val minCutoff: Float = 0.8f,
    private val beta: Float = 15.0f,
    private val dCutoff: Float = 1.0f
) {
    private var xPrev: Float = Float.NaN
    private var dxPrev: Float = 0f
    private var tPrevNs: Long = -1L

    private fun alpha(cutoff: Float, dtSec: Float): Float {
        val tau = 1.0f / (2.0f * PI.toFloat() * cutoff)
        return 1.0f / (1.0f + tau / dtSec)
    }

    fun filter(x: Float, timestampNs: Long): Float {
        if (tPrevNs == -1L || xPrev.isNaN()) {
            xPrev = x
            dxPrev = 0f
            tPrevNs = timestampNs
            return x
        }

        val dtSec = ((timestampNs - tPrevNs) * 1e-9f).coerceIn(0.001f, 0.5f)
        val dx = (x - xPrev) / dtSec
        val edx = dxPrev + alpha(dCutoff, dtSec) * (dx - dxPrev)
        val cutoff = minCutoff + beta * abs(edx)
        val filteredX = xPrev + alpha(cutoff, dtSec) * (x - xPrev)

        xPrev = filteredX
        dxPrev = edx
        tPrevNs = timestampNs
        return filteredX
    }

    fun reset() {
        tPrevNs = -1L
        xPrev = Float.NaN
    }
}

class OneEuroQuadFilter {
    private val filters = Array(8) { OneEuroScalarFilter() }

    fun filter(raw: NormalizedQuad, timestampNs: Long): NormalizedQuad {
        val ordered = raw.orderClockwise()
        val coordinates = ordered.toFloatArray()
        val filtered = FloatArray(8) { index ->
            filters[index].filter(coordinates[index], timestampNs)
        }
        return NormalizedQuad.fromFloatArray(filtered, ordered.confidence)
    }

    fun reset() {
        filters.forEach { it.reset() }
    }
}
