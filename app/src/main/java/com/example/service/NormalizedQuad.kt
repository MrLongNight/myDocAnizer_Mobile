package com.example.service

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A document quadrilateral expressed entirely in normalized image coordinates.
 *
 * Corner order is clockwise in image coordinates: TL, TR, BR, BL.
 */
data class NormalizedQuad(
    val tl: PointF,
    val tr: PointF,
    val br: PointF,
    val bl: PointF,
    val confidence: Float
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        tl.x, tl.y,
        tr.x, tr.y,
        br.x, br.y,
        bl.x, bl.y
    )

    fun orderClockwise(): NormalizedQuad {
        val points = listOf(tl, tr, br, bl)
        val cx = points.sumOf { it.x.toDouble() }.toFloat() / 4f
        val cy = points.sumOf { it.y.toDouble() }.toFloat() / 4f
        val sorted = points.sortedBy { point ->
            atan2(point.y - cy, point.x - cx)
        }
        val startIndex = sorted.indices.minByOrNull { index ->
            sorted[index].x + sorted[index].y
        } ?: 0
        val ordered = List(4) { offset -> sorted[(startIndex + offset) % 4] }
        return NormalizedQuad(
            tl = ordered[0],
            tr = ordered[1],
            br = ordered[2],
            bl = ordered[3],
            confidence = confidence
        )
    }

    fun isConvex(): Boolean {
        val points = listOf(tl, tr, br, bl)
        val crosses = FloatArray(4) { index ->
            val p0 = points[index]
            val p1 = points[(index + 1) % 4]
            val p2 = points[(index + 2) % 4]
            val edge1X = p1.x - p0.x
            val edge1Y = p1.y - p0.y
            val edge2X = p2.x - p1.x
            val edge2Y = p2.y - p1.y
            edge1X * edge2Y - edge1Y * edge2X
        }
        val allPositive = crosses.all { it > 0.0001f }
        val allNegative = crosses.all { it < -0.0001f }
        if (!allPositive && !allNegative) return false

        return points.indices.all { index ->
            val first = points[index]
            val second = points[(index + 1) % 4]
            hypot(second.x - first.x, second.y - first.y) >= 0.10f
        }
    }

    fun area(): Float = 0.5f * abs(
        (tl.x * tr.y - tr.x * tl.y) +
            (tr.x * br.y - br.x * tr.y) +
            (br.x * bl.y - bl.x * br.y) +
            (bl.x * tl.y - tl.x * bl.y)
    )

    fun maxCornerDistanceTo(other: NormalizedQuad): Float {
        val first = listOf(tl, tr, br, bl)
        val second = listOf(other.tl, other.tr, other.br, other.bl)
        return first.indices.maxOf { index ->
            hypot(
                first[index].x - second[index].x,
                first[index].y - second[index].y
            )
        }
    }

    companion object {
        fun fromFloatArray(arr: FloatArray, confidence: Float): NormalizedQuad {
            require(arr.size >= 8) { "A quadrilateral requires at least 8 coordinate values" }
            return NormalizedQuad(
                tl = PointF(arr[0], arr[1]),
                tr = PointF(arr[2], arr[3]),
                br = PointF(arr[4], arr[5]),
                bl = PointF(arr[6], arr[7]),
                confidence = confidence
            )
        }
    }
}
