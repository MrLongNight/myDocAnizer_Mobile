package com.example.service

import android.graphics.PointF
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Small, reusable luminance/chroma frame for CPU-only document detection.
 * Pixel arrays are row-major, unsigned 8-bit samples stored in ByteArrays.
 */
data class LumaChromaFrame(
    val width: Int,
    val height: Int,
    val y: ByteArray,
    val u: ByteArray? = null,
    val v: ByteArray? = null
) {
    init {
        require(width > 0 && height > 0)
        require(y.size >= width * height)
        require(u == null || u.size >= width * height)
        require(v == null || v.size >= width * height)
    }

    internal fun ensureCompatible(otherWidth: Int, otherHeight: Int): Boolean =
        width == otherWidth && height == otherHeight &&
            y.size >= otherWidth * otherHeight &&
            (u == null || u.size >= otherWidth * otherHeight) &&
            (v == null || v.size >= otherWidth * otherHeight)
}

/** Reuses destination buffers when dimensions and chroma availability stay unchanged. */
fun ImageProxy.toLumaChromaFrame(
    reuse: LumaChromaFrame? = null,
    targetLongEdge: Int = DocumentDetectionConfig.TARGET_LONG_EDGE
): LumaChromaFrame {
    require(targetLongEdge >= 32)
    val scale = targetLongEdge.toFloat() / max(width, height).toFloat()
    val outW = max(1, (width * scale).toInt())
    val outH = max(1, (height * scale).toInt())
    val planes = planes
    require(planes.isNotEmpty()) { "ImageProxy has no Y plane" }
    val yPlane = planes[0]
    val uPlane = planes.getOrNull(1)
    val vPlane = planes.getOrNull(2)
    val useChroma = uPlane != null && vPlane != null
    val pixels = outW * outH
    val target = if (reuse != null && reuse.width == outW && reuse.height == outH &&
        reuse.y.size >= pixels && ((reuse.u != null && reuse.v != null) == useChroma)) reuse else null
    val outY = target?.y ?: ByteArray(pixels)
    val outU = if (useChroma) target?.u ?: ByteArray(pixels) else null
    val outV = if (useChroma) target?.v ?: ByteArray(pixels) else null

    val yBuffer = yPlane.buffer
    val yBase = yBuffer.position()
    val yLimit = yBuffer.limit()
    val uBuffer = uPlane?.buffer
    val vBuffer = vPlane?.buffer
    val uBase = uBuffer?.position() ?: 0
    val vBase = vBuffer?.position() ?: 0
    val uLimit = uBuffer?.limit() ?: 0
    val vLimit = vBuffer?.limit() ?: 0

    for (dy in 0 until outH) {
        val y0 = dy * height / outH
        val y1 = max(y0 + 1, (dy + 1) * height / outH).coerceAtMost(height)
        for (dx in 0 until outW) {
            val x0 = dx * width / outW
            val x1 = max(x0 + 1, (dx + 1) * width / outW).coerceAtMost(width)
            var sumY = 0
            var countY = 0
            var sumU = 0
            var sumV = 0
            var countUV = 0
            for (sy in y0 until y1) {
                var index = yBase + sy * yPlane.rowStride + x0 * yPlane.pixelStride
                for (sx in x0 until x1) {
                    if (index in yBase until yLimit) {
                        sumY += yBuffer.get(index).toInt() and 0xff
                        countY++
                    }
                    index += yPlane.pixelStride
                }
                if (useChroma) {
                    val cy = sy / 2
                    var ui = uBase + cy * uPlane!!.rowStride + (x0 / 2) * uPlane!!.pixelStride
                    var vi = vBase + cy * vPlane!!.rowStride + (x0 / 2) * vPlane!!.pixelStride
                    for (sx in x0 until x1) {
                        if (sx % 2 == 0) {
                            if (ui in uBase until uLimit) sumU += uBuffer!!.get(ui).toInt() and 0xff
                            if (vi in vBase until vLimit) sumV += vBuffer!!.get(vi).toInt() and 0xff
                            countUV++
                        }
                        ui += uPlane!!.pixelStride
                        vi += vPlane!!.pixelStride
                    }
                }
            }
            val index = dy * outW + dx
            outY[index] = (if (countY > 0) sumY / countY else 0).toByte()
            if (useChroma) {
                outU!![index] = (if (countUV > 0) sumU / countUV else 128).toByte()
                outV!![index] = (if (countUV > 0) sumV / countUV else 128).toByte()
            }
        }
    }
    return if (target != null) target else LumaChromaFrame(outW, outH, outY, outU, outV)
}

data class DocumentDetection(
    val quad: List<PointF>?,
    val confidence: Float,
    val rejectReason: String?
)

object DocumentDetectionConfig {
    const val TARGET_LONG_EDGE = 160
    const val MIN_COMPONENT_AREA_RATIO = 0.15f
    const val MAX_COMPONENT_AREA_RATIO = 0.88f
    const val MIN_QUAD_TO_HULL_AREA_RATIO = 0.90f
    const val MIN_COMPONENT_TO_HULL_AREA_RATIO = 0.88f
    const val MIN_EDGE_MARGIN_RATIO = 0.01f
    const val MIN_INTERIOR_ANGLE_DEGREES = 55.0
    const val MAX_INTERIOR_ANGLE_DEGREES = 125.0
    const val MIN_OPPOSING_SIDE_RATIO = 0.5f
    const val MAX_OPPOSING_SIDE_RATIO = 2.0f
    const val MIN_SIDE_TO_DIAGONAL_RATIO = 0.15f
    const val MIN_RECTANGLE_ASPECT_RATIO = 1.0f
    const val MAX_RECTANGLE_ASPECT_RATIO = 2.6f
    const val MIN_EDGE_CONTRAST = 14.0f
    const val SKIN_CHROMA_RATIO = 0.35f
    const val SKIN_CONFIDENCE_MULTIPLIER = 0.72f
    const val MIN_ACCEPT_CONFIDENCE = 0.48f
    const val OTSU_MIN_THRESHOLD = 18
    const val OTSU_MAX_THRESHOLD = 238
}

/**
 * Classical low-memory detector. No model or native library is used.
 * Thresholds are initial engineering values and have NOT been validated on real devices.
 */
class DocumentQuadDetector {
    private data class Candidate(
        val quad: List<P>,
        val confidence: Float,
        val reason: String
    )
    private data class P(val x: Float, val y: Float)
    private data class Component(val pixels: IntArray, val boundsArea: Int, val area: Int)

    fun detect(frame: LumaChromaFrame): DocumentDetection {
        val w = frame.width
        val h = frame.height
        if (w < 24 || h < 24) return DocumentDetection(null, 0f, "Bild zu klein")
        val n = w * h
        val smooth = IntArray(n)
        val hist = IntArray(256)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0
            var count = 0
            for (dy in -1..1) for (dx in -1..1) {
                val xx = (x + dx).coerceIn(0, w - 1)
                val yy = (y + dy).coerceIn(0, h - 1)
                sum += frame.y[yy * w + xx].toInt() and 0xff
                count++
            }
            val value = sum / count
            smooth[y * w + x] = value
            hist[value]++
        }
        val threshold = otsu(hist, n).coerceIn(
            DocumentDetectionConfig.OTSU_MIN_THRESHOLD,
            DocumentDetectionConfig.OTSU_MAX_THRESHOLD
        )
        var best: Candidate? = null
        var bestReject = "kein geschlossenes Viereck"
        for (brightForeground in booleanArrayOf(true, false)) {
            val binary = BooleanArray(n) { i -> if (brightForeground) smooth[i] >= threshold else smooth[i] < threshold }
            closeHoles(binary, w, h)
            open3x3(binary, w, h)
            val components = components(binary, w, h)
            for (component in components) {
                // Check the bounding box too: morphology can erode a large
                // rectangle enough that its filled-pixel ratio looks acceptable.
                val boundsRatio = component.boundsArea.toFloat() / n
                if (boundsRatio > DocumentDetectionConfig.MAX_COMPONENT_AREA_RATIO) {
                    bestReject = "zu groß"
                    continue
                }
                val ratio = component.area.toFloat() / n
                if (ratio !in DocumentDetectionConfig.MIN_COMPONENT_AREA_RATIO..DocumentDetectionConfig.MAX_COMPONENT_AREA_RATIO) {
                    bestReject = if (ratio < DocumentDetectionConfig.MIN_COMPONENT_AREA_RATIO) "zu klein" else "zu groß"
                    continue
                }
                val hull = convexHull(component.pixels, w)
                if (hull.size < 4) { bestReject = "keine vier Ecken"; continue }
                val quad = simplifyToQuad(hull)
                if (quad.size != 4) { bestReject = "keine vier Ecken"; continue }
                val hullArea = polygonArea(hull)
                val quadArea = polygonArea(quad)
                if (hullArea <= 0.0 || quadArea <= 0.0) { bestReject = "ungültige Fläche"; continue }
                if (quadArea / hullArea < DocumentDetectionConfig.MIN_QUAD_TO_HULL_AREA_RATIO) {
                    bestReject = "nicht rechteckig"; continue
                }
                if (component.area / hullArea < DocumentDetectionConfig.MIN_COMPONENT_TO_HULL_AREA_RATIO) {
                    bestReject = "zu viele Lücken"; continue
                }
                if (!isConvex(quad)) { bestReject = "nicht konvex"; continue }
                if (quad.any { it.x < w * DocumentDetectionConfig.MIN_EDGE_MARGIN_RATIO ||
                        it.y < h * DocumentDetectionConfig.MIN_EDGE_MARGIN_RATIO ||
                        it.x > w * (1f - DocumentDetectionConfig.MIN_EDGE_MARGIN_RATIO) ||
                        it.y > h * (1f - DocumentDetectionConfig.MIN_EDGE_MARGIN_RATIO) }) {
                    bestReject = "am Bildrand abgeschnitten"; continue
                }
                val sides = (0..3).map { distance(quad[it], quad[(it + 1) % 4]) }
                val diagonal = max(distance(quad[0], quad[2]), distance(quad[1], quad[3]))
                if (diagonal <= 0f || sides.minOrNull()!! < diagonal * DocumentDetectionConfig.MIN_SIDE_TO_DIAGONAL_RATIO) {
                    bestReject = "Seite zu kurz"; continue
                }
                val angles = (0..3).map { interiorAngle(quad[(it + 3) % 4], quad[it], quad[(it + 1) % 4]) }
                if (angles.any { it < DocumentDetectionConfig.MIN_INTERIOR_ANGLE_DEGREES ||
                        it > DocumentDetectionConfig.MAX_INTERIOR_ANGLE_DEGREES }) {
                    bestReject = "Innenwinkel unplausibel"; continue
                }
                val topBottom = sides[0] / max(0.001f, sides[2])
                val rightLeft = sides[1] / max(0.001f, sides[3])
                if (topBottom !in DocumentDetectionConfig.MIN_OPPOSING_SIDE_RATIO..DocumentDetectionConfig.MAX_OPPOSING_SIDE_RATIO ||
                    rightLeft !in DocumentDetectionConfig.MIN_OPPOSING_SIDE_RATIO..DocumentDetectionConfig.MAX_OPPOSING_SIDE_RATIO) {
                    bestReject = "Gegenseiten unplausibel"; continue
                }
                val meanLong = max((sides[0] + sides[2]) / 2f, (sides[1] + sides[3]) / 2f)
                val meanShort = min((sides[0] + sides[2]) / 2f, (sides[1] + sides[3]) / 2f)
                val aspect = meanLong / max(0.001f, meanShort)
                if (aspect !in DocumentDetectionConfig.MIN_RECTANGLE_ASPECT_RATIO..DocumentDetectionConfig.MAX_RECTANGLE_ASPECT_RATIO) {
                    bestReject = "Seitenverhältnis unplausibel"; continue
                }
                val contrast = edgeContrast(quad, smooth, w, h)
                if (contrast < DocumentDetectionConfig.MIN_EDGE_CONTRAST) {
                    bestReject = "Randkontrast zu gering"; continue
                }
                val fill = (component.area / hullArea).toFloat().coerceIn(0f, 1f)
                val rectangularity = (quadArea / hullArea).toFloat().coerceIn(0f, 1f)
                val edgeScore = (contrast / 64f).coerceIn(0f, 1f)
                var confidence = (0.40f * fill + 0.35f * rectangularity + 0.25f * edgeScore).coerceIn(0f, 1f)
                val skinRatio = skinRatio(frame, quad)
                if (skinRatio > DocumentDetectionConfig.SKIN_CHROMA_RATIO) confidence *= DocumentDetectionConfig.SKIN_CONFIDENCE_MULTIPLIER
                if (confidence < DocumentDetectionConfig.MIN_ACCEPT_CONFIDENCE) {
                    bestReject = if (skinRatio > DocumentDetectionConfig.SKIN_CHROMA_RATIO) "Hautfarbe / geringe Konfidenz" else "Konfidenz zu gering"
                    continue
                }
                if (best == null || confidence > best!!.confidence) {
                    best = Candidate(quad, confidence, if (skinRatio > DocumentDetectionConfig.SKIN_CHROMA_RATIO) "Hautanteil: confidence reduziert" else "erkannt")
                }
            }
        }
        val winner = best ?: return DocumentDetection(null, 0f, bestReject)
        val ordered = orderCorners(winner.quad)
        return DocumentDetection(
            ordered.map { PointF((it.x / w).coerceIn(0f, 1f), (it.y / h).coerceIn(0f, 1f)) },
            winner.confidence.coerceIn(0f, 1f),
            winner.reason
        )
    }

    private fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0
        for (i in hist.indices) sum += i.toDouble() * hist[i]
        var sumB = 0.0
        var weightB = 0
        var bestVariance = -1.0
        var best = 128
        for (i in hist.indices) {
            weightB += hist[i]
            if (weightB == 0) continue
            val weightF = total - weightB
            if (weightF == 0) break
            sumB += i.toDouble() * hist[i]
            val meanB = sumB / weightB
            val meanF = (sum - sumB) / weightF
            val variance = weightB.toDouble() * weightF * (meanB - meanF) * (meanB - meanF)
            if (variance > bestVariance) { bestVariance = variance; best = i }
        }
        return best
    }

    private fun closeHoles(mask: BooleanArray, w: Int, h: Int) {
        val outside = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        var head = 0
        var tail = 0
        fun add(index: Int) {
            if (!mask[index] && !outside[index]) { outside[index] = true; queue[tail++] = index }
        }
        for (x in 0 until w) { add(x); add((h - 1) * w + x) }
        for (y in 0 until h) { add(y * w); add(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) add(yy * w + xx)
            }
        }
        for (i in mask.indices) if (!mask[i] && !outside[i]) mask[i] = true
    }

    private fun open3x3(mask: BooleanArray, w: Int, h: Int) {
        val eroded = BooleanArray(mask.size)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            var all = true
            loop@ for (dy in -1..1) for (dx in -1..1) if (!mask[(y + dy) * w + x + dx]) { all = false; break@loop }
            eroded[y * w + x] = all
        }
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            if (!eroded[y * w + x]) continue
            for (dy in -1..1) for (dx in -1..1) mask[(y + dy) * w + x + dx] = true
        }
        for (x in 0 until w) { mask[x] = false; mask[(h - 1) * w + x] = false }
        for (y in 0 until h) { mask[y * w] = false; mask[y * w + w - 1] = false }
    }

    private fun components(mask: BooleanArray, w: Int, h: Int): List<Component> {
        val seen = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val output = ArrayList<Component>()
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            var minX = w; var maxX = 0; var minY = h; var maxY = 0
            while (head < tail) {
                val i = queue[head++]; val x = i % w; val y = i / w
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx; val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val j = yy * w + xx
                    if (mask[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            output += Component(queue.copyOf(tail), (maxX - minX + 1) * (maxY - minY + 1), tail)
        }
        return output.sortedByDescending { it.area }
    }

    private fun convexHull(indices: IntArray, w: Int): List<P> {
        val points = indices.map { P((it % w).toFloat(), (it / w).toFloat()) }.distinct()
            .sortedWith(compareBy<P> { it.x }.thenBy { it.y })
        if (points.size < 4) return points
        fun cross(o: P, a: P, b: P) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = ArrayList<P>()
        for (p in points) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) lower.removeAt(lower.lastIndex)
            lower.add(p)
        }
        val upper = ArrayList<P>()
        for (p in points.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) upper.removeAt(upper.lastIndex)
            upper.add(p)
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private fun simplifyToQuad(input: List<P>): List<P> {
        val points = input.toMutableList()
        while (points.size > 4) {
            var bestIndex = -1
            var smallest = Float.MAX_VALUE
            for (i in points.indices) {
                val prev = points[(i + points.size - 1) % points.size]
                val cur = points[i]
                val next = points[(i + 1) % points.size]
                val contribution = abs((cur.x - prev.x) * (next.y - prev.y) - (cur.y - prev.y) * (next.x - prev.x)) / 2f
                if (contribution < smallest) { smallest = contribution; bestIndex = i }
            }
            if (bestIndex < 0) break
            points.removeAt(bestIndex)
        }
        return points
    }

    private fun polygonArea(points: List<P>): Double {
        var area = 0.0
        for (i in points.indices) {
            val a = points[i]; val b = points[(i + 1) % points.size]
            area += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return abs(area) / 2.0
    }

    private fun isConvex(p: List<P>): Boolean {
        var sign = 0
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]; val c = p[(i + 2) % p.size]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 0.01f) return false
            val current = if (cross > 0) 1 else -1
            if (sign != 0 && current != sign) return false
            sign = current
        }
        return true
    }

    private fun interiorAngle(a: P, b: P, c: P): Double {
        val ux = a.x - b.x; val uy = a.y - b.y
        val vx = c.x - b.x; val vy = c.y - b.y
        val denom = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy).toDouble())
        if (denom <= 0.0) return 0.0
        return Math.toDegrees(kotlin.math.acos((((ux * vx + uy * vy) / denom).toDouble()).coerceIn(-1.0, 1.0)))
    }

    private fun distance(a: P, b: P): Float = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    private fun edgeContrast(quad: List<P>, image: IntArray, w: Int, h: Int): Float {
        var sum = 0.0
        var count = 0
        for (i in quad.indices) {
            val a = quad[i]; val b = quad[(i + 1) % quad.size]
            val dx = b.x - a.x; val dy = b.y - a.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 2f) continue
            val nx = -dy / len; val ny = dx / len
            val steps = max(8, len.toInt() / 2)
            for (s in 1 until steps) {
                val t = s.toFloat() / steps
                val x = (a.x + dx * t).toInt(); val y = (a.y + dy * t).toInt()
                val x1 = (x + nx * 2f).toInt().coerceIn(0, w - 1)
                val y1 = (y + ny * 2f).toInt().coerceIn(0, h - 1)
                val x2 = (x - nx * 2f).toInt().coerceIn(0, w - 1)
                val y2 = (y - ny * 2f).toInt().coerceIn(0, h - 1)
                sum += abs(image[y1 * w + x1] - image[y2 * w + x2])
                count++
            }
        }
        return if (count == 0) 0f else (sum / count).toFloat()
    }

    private fun skinRatio(frame: LumaChromaFrame, quad: List<P>): Float {
        val u = frame.u ?: return 0f
        val v = frame.v ?: return 0f
        val w = frame.width; val h = frame.height
        val minX = quad.minOf { it.x }.toInt().coerceIn(0, w - 1)
        val maxX = quad.maxOf { it.x }.toInt().coerceIn(0, w - 1)
        val minY = quad.minOf { it.y }.toInt().coerceIn(0, h - 1)
        val maxY = quad.maxOf { it.y }.toInt().coerceIn(0, h - 1)
        var total = 0; var skin = 0
        for (y in minY..maxY step 2) for (x in minX..maxX step 2) {
            if (!pointInQuad(x.toFloat(), y.toFloat(), quad)) continue
            val i = y * w + x
            val cb = u[i].toInt() and 0xff
            val cr = v[i].toInt() and 0xff
            total++
            if (cr in 133..173 && cb in 77..127) skin++
        }
        return if (total == 0) 0f else skin.toFloat() / total
    }

    private fun pointInQuad(x: Float, y: Float, q: List<P>): Boolean {
        var sign = 0
        for (i in q.indices) {
            val a = q[i]; val b = q[(i + 1) % q.size]
            val c = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
            if (abs(c) < 0.001f) continue
            val s = if (c > 0) 1 else -1
            if (sign != 0 && s != sign) return false
            sign = s
        }
        return true
    }

    private fun orderCorners(points: List<P>): List<P> {
        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        val ordered = points.sortedBy { atan2((it.y - centerY).toDouble(), (it.x - centerX).toDouble()) }
        val start = ordered.indices.minByOrNull { ordered[it].x + ordered[it].y } ?: 0
        val rotated = (ordered.drop(start) + ordered.take(start)).toMutableList()
        if (rotated.size == 4) {
            val cross = (rotated[1].x - rotated[0].x) * (rotated[2].y - rotated[1].y) -
                (rotated[1].y - rotated[0].y) * (rotated[2].x - rotated[1].x)
            if (cross < 0f) return listOf(rotated[0], rotated[3], rotated[2], rotated[1])
        }
        return rotated
    }
}
