package com.example.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure-Kotlin high-resolution document refinement and normalization.
 *
 * Keep input images at or below 2200px on their longest side. Processing buffers are
 * bounded to the source dimensions; shadow removal uses row-sized pixel storage rather
 * than a second full-size ARGB bitmap.
 */
object HighResDocumentProcessor {
    private const val DEFAULT_MAX_DIMENSION = 2200
    private const val SAMPLE_COUNT = 20
    private const val SEARCH_RADIUS = 16
    private const val SMALL_SCALE = 8
    private const val LINE_EPSILON = 1e-7

    fun loadAndNormalizeHighResBitmap(
        imageBytes: ByteArray,
        maxDimension: Int = DEFAULT_MAX_DIMENSION
    ): Bitmap {
        require(maxDimension > 0) { "maxDimension must be positive" }
        val orientation = try {
            ExifInterface(ByteArrayInputStream(imageBytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val bounds = BitmapFactoryCompat.decodeBounds(imageBytes)
        require(bounds.first > 0 && bounds.second > 0) { "Image data has invalid dimensions" }
        val sample = calculateInSampleSize(bounds.first, bounds.second, maxDimension)
        var decoded = BitmapFactoryCompat.decodeSampled(imageBytes, sample)
            ?: throw IllegalArgumentException("Unable to decode image bytes")

        val exifMatrix = Matrix()
        val needsTransform = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { exifMatrix.setScale(-1f, 1f); true }
            ExifInterface.ORIENTATION_ROTATE_180 -> { exifMatrix.setRotate(180f); true }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { exifMatrix.setScale(1f, -1f); true }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                exifMatrix.setValues(floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)); true
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> { exifMatrix.setRotate(90f); true }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                exifMatrix.setValues(floatArrayOf(0f, -1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)); true
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> { exifMatrix.setRotate(270f); true }
            else -> false
        }
        if (needsTransform) {
            val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, exifMatrix, true)
            if (transformed !== decoded) decoded.recycle()
            decoded = transformed
        }

        val longest = max(decoded.width, decoded.height)
        if (longest > maxDimension) {
            val scale = maxDimension.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                max(1, (decoded.width * scale).toInt()),
                max(1, (decoded.height * scale).toInt()),
                true
            )
            if (scaled !== decoded) decoded.recycle()
            decoded = scaled
        }
        return decoded
    }

    /**
     * Refines the four sides using a one-dimensional normal gradient search, then
     * robustly estimates each side from the medians of two point groups.
     */
    fun refineCornersSubPixel(bitmap: Bitmap, coarseQuad: NormalizedQuad): NormalizedQuad {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 3 || height < 3) return coarseQuad
        val original = pointsInPixels(coarseQuad, width, height)
        val centerX = original.sumOf { it.x.toDouble() }.toFloat() / 4f
        val centerY = original.sumOf { it.y.toDouble() }.toFloat() / 4f
        val edgePairs = arrayOf(0 to 1, 1 to 2, 2 to 3, 3 to 0)
        val lines = ArrayList<Line>(4)

        for ((startIndex, endIndex) in edgePairs) {
            val start = original[startIndex]
            val end = original[endIndex]
            val dx = end.x - start.x
            val dy = end.y - start.y
            val length = hypot(dx, dy)
            if (length < 2f) return coarseQuad
            var nx = -dy / length
            var ny = dx / length
            val midX = (start.x + end.x) * 0.5f
            val midY = (start.y + end.y) * 0.5f
            if (nx * (centerX - midX) + ny * (centerY - midY) > 0f) {
                nx = -nx
                ny = -ny
            }

            val refined = ArrayList<Point>(SAMPLE_COUNT)
            for (sampleIndex in 0 until SAMPLE_COUNT) {
                val t = 0.20f + 0.60f * sampleIndex / (SAMPLE_COUNT - 1).toFloat()
                val px = start.x + dx * t
                val py = start.y + dy * t
                var bestK = 0
                var bestGradient = -1f
                for (k in -SEARCH_RADIUS..SEARCH_RADIUS) {
                    val gx = px + k * nx
                    val gy = py + k * ny
                    val before = luminanceBilinear(bitmap, gx - nx, gy - ny)
                    val after = luminanceBilinear(bitmap, gx + nx, gy + ny)
                    val gradient = abs(after - before)
                    if (gradient > bestGradient) {
                        bestGradient = gradient
                        bestK = k
                    }
                }
                refined.add(Point(px + bestK * nx, py + bestK * ny))
            }
            lines.add(robustLineFromMedians(refined))
        }

        // Side ordering is top, right, bottom, left.
        val proposed = arrayOf(
            intersection(lines[0], lines[3]),
            intersection(lines[0], lines[1]),
            intersection(lines[2], lines[1]),
            intersection(lines[2], lines[3])
        )
        val diagonal = hypot(width.toFloat(), height.toFloat())
        val maxDrift = diagonal * 0.04f
        val accepted = Array(4) { index ->
            val point = proposed[index]
            val fallback = original[index]
            if (point == null ||
                !point.x.isFinite() || !point.y.isFinite() ||
                point.x < 0f || point.x > width.toFloat() ||
                point.y < 0f || point.y > height.toFloat() ||
                hypot(point.x - fallback.x, point.y - fallback.y) > maxDrift
            ) fallback else point
        }
        val refinedQuad = NormalizedQuad(
            tl = android.graphics.PointF(accepted[0].x / width, accepted[0].y / height),
            tr = android.graphics.PointF(accepted[1].x / width, accepted[1].y / height),
            br = android.graphics.PointF(accepted[2].x / width, accepted[2].y / height),
            bl = android.graphics.PointF(accepted[3].x / width, accepted[3].y / height),
            confidence = coarseQuad.confidence
        )
        return if (refinedQuad.isConvex()) refinedQuad else coarseQuad
    }

    /**
     * Estimates the physical width/height ratio from the inverse planar homography.
     * The focal length is estimated from orthogonal vanishing directions and the
     * principal point is fixed at the image center as requested.
     */
    fun estimateTrueAspectRatio(quad: NormalizedQuad, imageWidth: Int, imageHeight: Int): Float {
        if (imageWidth <= 1 || imageHeight <= 1) return 1f
        val p = pointsInPixels(quad, imageWidth, imageHeight)
        val w1 = hypot(p[1].x - p[0].x, p[1].y - p[0].y)
        val w2 = hypot(p[2].x - p[3].x, p[2].y - p[3].y)
        val h1 = hypot(p[3].x - p[0].x, p[3].y - p[0].y)
        val h2 = hypot(p[2].x - p[1].x, p[2].y - p[1].y)
        val naiveRatio = max(w1, w2) / max(h1, h2).coerceAtLeast(1f)
        val fallback = naiveRatio.coerceIn(0.35f, 2.8f)

        val top = lineThrough(p[0], p[1])
        val bottom = lineThrough(p[3], p[2])
        val left = lineThrough(p[0], p[3])
        val right = lineThrough(p[1], p[2])
        val vanishingX = intersection(top, bottom) ?: return fallback
        val vanishingY = intersection(left, right) ?: return fallback
        val u0 = imageWidth / 2f
        val v0 = imageHeight / 2f
        val fSquared = -(
            (vanishingX.x - u0) * (vanishingY.x - u0) +
                (vanishingX.y - v0) * (vanishingY.y - v0)
            )
        if (!fSquared.isFinite() || fSquared <= 1f) return fallback
        val focal = sqrt(fSquared)
        val homography = homographyToUnitSquare(p) ?: return fallback
        val inverse = invert3x3(homography) ?: return fallback
        val widthVector = applyInverseIntrinsics(
            inverse[0], inverse[3], inverse[6], u0, v0, focal
        )
        val heightVector = applyInverseIntrinsics(
            inverse[1], inverse[4], inverse[7], u0, v0, focal
        )
        val widthNorm = vectorNorm(widthVector)
        val heightNorm = vectorNorm(heightVector)
        if (!widthNorm.isFinite() || !heightNorm.isFinite() ||
            widthNorm < 1e-8f || heightNorm < 1e-8f
        ) return fallback
        return (widthNorm / heightNorm).coerceIn(0.35f, 2.8f)
    }

    fun warpPerspective(
        source: Bitmap,
        quad: NormalizedQuad,
        maxOutputLongEdge: Int = 2000
    ): Bitmap {
        require(maxOutputLongEdge > 0) { "maxOutputLongEdge must be positive" }
        val ar = estimateTrueAspectRatio(quad, source.width, source.height)
            .coerceIn(0.35f, 2.8f)
        val outWidth: Int
        val outHeight: Int
        if (ar >= 1f) {
            outWidth = maxOutputLongEdge
            outHeight = max(1, (maxOutputLongEdge / ar).toInt())
        } else {
            outHeight = maxOutputLongEdge
            outWidth = max(1, (maxOutputLongEdge * ar).toInt())
        }

        val srcPts = floatArrayOf(
            quad.tl.x * source.width, quad.tl.y * source.height,
            quad.tr.x * source.width, quad.tr.y * source.height,
            quad.br.x * source.width, quad.br.y * source.height,
            quad.bl.x * source.width, quad.bl.y * source.height
        )
        val dstPts = floatArrayOf(
            0f, 0f, outWidth.toFloat(), 0f,
            outWidth.toFloat(), outHeight.toFloat(), 0f, outHeight.toFloat()
        )
        val matrix = Matrix()
        check(matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
            "Unable to compute perspective transform for document corners"
        }
        val outBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(outBitmap).drawBitmap(
            source,
            matrix,
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        return outBitmap
    }

    /**
     * Division-normalizes illumination using a max-pooled, three-pass box-blurred
     * background estimate. Uses a single full-resolution luminance plane and one
     * row of pixels to avoid a second full-size bitmap.
     */
    fun removeShadowsDivisionNormalization(
        warped: Bitmap,
        enhanceContrast: Boolean = true
    ): Bitmap {
        val width = warped.width
        val height = warped.height
        if (width <= 0 || height <= 0) return warped
        val yPlane = ByteArray(width * height)
        val rowPixels = IntArray(width)
        for (y in 0 until height) {
            warped.getPixels(rowPixels, 0, width, 0, y, width, 1)
            val rowOffset = y * width
            for (x in 0 until width) {
                val color = rowPixels[x]
                val r = (color ushr 16) and 0xff
                val g = (color ushr 8) and 0xff
                val b = color and 0xff
                yPlane[rowOffset + x] =
                    (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255).toByte()
            }
        }

        val smallWidth = max(1, (width + SMALL_SCALE - 1) / SMALL_SCALE)
        val smallHeight = max(1, (height + SMALL_SCALE - 1) / SMALL_SCALE)
        val small = FloatArray(smallWidth * smallHeight)
        val horizontal = FloatArray(small.size)
        val vertical = FloatArray(small.size)
        for (sy in 0 until smallHeight) {
            for (sx in 0 until smallWidth) {
                var maximum = 0
                val xEnd = min(width, (sx + 1) * SMALL_SCALE)
                val yEnd = min(height, (sy + 1) * SMALL_SCALE)
                for (y in sy * SMALL_SCALE until yEnd) {
                    val offset = y * width
                    for (x in sx * SMALL_SCALE until xEnd) {
                        maximum = max(maximum, yPlane[offset + x].toInt() and 0xff)
                    }
                }
                small[sy * smallWidth + sx] = maximum.toFloat().coerceAtLeast(1f)
            }
        }
        boxBlurPass(small, horizontal, smallWidth, smallHeight, radius = 5, horizontalPass = true)
        boxBlurPass(horizontal, vertical, smallWidth, smallHeight, radius = 5, horizontalPass = false)
        boxBlurPass(vertical, small, smallWidth, smallHeight, radius = 5, horizontalPass = true)

        for (y in 0 until height) {
            warped.getPixels(rowPixels, 0, width, 0, y, width, 1)
            val gy = y.toFloat() / SMALL_SCALE
            val y0 = floor(gy).toInt().coerceIn(0, smallHeight - 1)
            val y1 = min(y0 + 1, smallHeight - 1)
            val fy = (gy - y0).coerceIn(0f, 1f)
            for (x in 0 until width) {
                val gx = x.toFloat() / SMALL_SCALE
                val x0 = floor(gx).toInt().coerceIn(0, smallWidth - 1)
                val x1 = min(x0 + 1, smallWidth - 1)
                val fx = (gx - x0).coerceIn(0f, 1f)
                val top = small[y0 * smallWidth + x0] * (1f - fx) +
                    small[y0 * smallWidth + x1] * fx
                val bottom = small[y1 * smallWidth + x0] * (1f - fx) +
                    small[y1 * smallWidth + x1] * fx
                val background = (top * (1f - fy) + bottom * fy).coerceAtLeast(1f)
                val originalY = yPlane[y * width + x].toInt() and 0xff
                var normalized = ((originalY / background) * 255f).coerceIn(0f, 255f)
                if (enhanceContrast) {
                    normalized = ((normalized - 110f) / (238f - 110f) * 255f)
                        .coerceIn(0f, 255f)
                }
                val gray = normalized.toInt().coerceIn(0, 255)
                rowPixels[x] = (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
            }
            warped.setPixels(rowPixels, 0, width, 0, y, width, 1)
        }
        return warped
    }

    private data class Point(val x: Float, val y: Float)
    private data class Line(val a: Float, val b: Float, val c: Float)

    private fun pointsInPixels(quad: NormalizedQuad, width: Int, height: Int) = arrayOf(
        Point(quad.tl.x * width, quad.tl.y * height),
        Point(quad.tr.x * width, quad.tr.y * height),
        Point(quad.br.x * width, quad.br.y * height),
        Point(quad.bl.x * width, quad.bl.y * height)
    )

    private fun robustLineFromMedians(points: List<Point>): Line {
        fun median(values: List<Float>): Float {
            val sorted = values.sorted()
            return if (sorted.size % 2 == 0) {
                (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) * 0.5f
            } else sorted[sorted.size / 2]
        }
        val first = points.take(points.size / 2)
        val second = points.drop(points.size / 2)
        val p1 = Point(median(first.map { it.x }), median(first.map { it.y }))
        val p2 = Point(median(second.map { it.x }), median(second.map { it.y }))
        return lineThrough(p1, p2)
    }

    private fun lineThrough(p1: Point, p2: Point): Line {
        val a = p1.y - p2.y
        val b = p2.x - p1.x
        val c = p1.x * p2.y - p2.x * p1.y
        val norm = hypot(a, b).coerceAtLeast(1e-8f)
        return Line(a / norm, b / norm, c / norm)
    }

    private fun intersection(first: Line, second: Line): Point? {
        val det = first.a * second.b - second.a * first.b
        if (!det.isFinite() || abs(det) < LINE_EPSILON) return null
        val x = (first.b * second.c - second.b * first.c) / det
        val y = (second.a * first.c - first.a * second.c) / det
        if (!x.isFinite() || !y.isFinite()) return null
        return Point(x, y)
    }

    private fun luminanceBilinear(bitmap: Bitmap, x: Float, y: Float): Float {
        val cx = x.coerceIn(0f, (bitmap.width - 1).toFloat())
        val cy = y.coerceIn(0f, (bitmap.height - 1).toFloat())
        val x0 = floor(cx).toInt()
        val y0 = floor(cy).toInt()
        val x1 = min(x0 + 1, bitmap.width - 1)
        val y1 = min(y0 + 1, bitmap.height - 1)
        val fx = cx - x0
        val fy = cy - y0
        val l00 = luminance(bitmap.getPixel(x0, y0))
        val l10 = luminance(bitmap.getPixel(x1, y0))
        val l01 = luminance(bitmap.getPixel(x0, y1))
        val l11 = luminance(bitmap.getPixel(x1, y1))
        val top = l00 * (1f - fx) + l10 * fx
        val bottom = l01 * (1f - fx) + l11 * fx
        return top * (1f - fy) + bottom * fy
    }

    private fun luminance(color: Int): Float {
        val r = (color ushr 16) and 0xff
        val g = (color ushr 8) and 0xff
        val b = color and 0xff
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (max(width / (sample * 2), height / (sample * 2)) >= maxDimension) {
            sample *= 2
        }
        return sample
    }

    private fun homographyToUnitSquare(p: Array<Point>): FloatArray? {
        val dstX = floatArrayOf(0f, 1f, 1f, 0f)
        val dstY = floatArrayOf(0f, 0f, 1f, 1f)
        val system = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = p[i].x.toDouble()
            val y = p[i].y.toDouble()
            val u = dstX[i].toDouble()
            val v = dstY[i].toDouble()
            system[i * 2] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            system[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        for (col in 0 until 8) {
            var pivot = col
            for (row in col + 1 until 8) {
                if (abs(system[row][col]) > abs(system[pivot][col])) pivot = row
            }
            if (abs(system[pivot][col]) < 1e-10) return null
            val temp = system[col]
            system[col] = system[pivot]
            system[pivot] = temp
            val divisor = system[col][col]
            for (j in col until 9) system[col][j] /= divisor
            for (row in 0 until 8) {
                if (row == col) continue
                val factor = system[row][col]
                for (j in col until 9) system[row][j] -= factor * system[col][j]
            }
        }
        return FloatArray(9) { index ->
            if (index == 8) 1f else system[index][8].toFloat()
        }
    }

    private fun invert3x3(m: FloatArray): FloatArray? {
        val a = m[0]; val b = m[1]; val c = m[2]
        val d = m[3]; val e = m[4]; val f = m[5]
        val g = m[6]; val h = m[7]; val i = m[8]
        val A = e * i - f * h
        val B = c * h - b * i
        val C = b * f - c * e
        val D = f * g - d * i
        val E = a * i - c * g
        val F = c * d - a * f
        val G = d * h - e * g
        val H = b * g - a * h
        val I = a * e - b * d
        val determinant = a * A + b * D + c * G
        if (!determinant.isFinite() || abs(determinant) < 1e-10f) return null
        return floatArrayOf(A, B, C, D, E, F, G, H, I).map { it / determinant }.toFloatArray()
    }

    private fun applyInverseIntrinsics(x: Float, y: Float, z: Float, u0: Float, v0: Float, f: Float): FloatArray =
        floatArrayOf((x - u0 * z) / f, (y - v0 * z) / f, z)

    private fun vectorNorm(v: FloatArray) = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    private fun boxBlurPass(
        source: FloatArray,
        destination: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        horizontalPass: Boolean
    ) {
        if (horizontalPass) {
            for (y in 0 until height) {
                var sum = 0f
                for (x in 0..min(radius, width - 1)) sum += source[y * width + x]
                for (x in 0 until width) {
                    val left = max(0, x - radius)
                    val right = min(width - 1, x + radius)
                    if (x == 0) {
                        sum = 0f
                        for (k in left..right) sum += source[y * width + k]
                    } else {
                        val removeIndex = x - radius - 1
                        val addIndex = x + radius
                        if (removeIndex >= 0) sum -= source[y * width + removeIndex]
                        if (addIndex < width) sum += source[y * width + addIndex]
                    }
                    destination[y * width + x] = sum / (right - left + 1)
                }
            }
        } else {
            for (x in 0 until width) {
                var sum = 0f
                for (y in 0..min(radius, height - 1)) sum += source[y * width + x]
                for (y in 0 until height) {
                    val top = max(0, y - radius)
                    val bottom = min(height - 1, y + radius)
                    if (y == 0) {
                        sum = 0f
                        for (k in top..bottom) sum += source[k * width + x]
                    } else {
                        val removeIndex = y - radius - 1
                        val addIndex = y + radius
                        if (removeIndex >= 0) sum -= source[removeIndex * width + x]
                        if (addIndex < height) sum += source[addIndex * width + x]
                    }
                    destination[y * width + x] = sum / (bottom - top + 1)
                }
            }
        }
    }

    /** BitmapFactory wrapper keeps decode setup local and deterministic. */
    private object BitmapFactoryCompat {
        fun decodeBounds(bytes: ByteArray): Pair<Int, Int> {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            return options.outWidth to options.outHeight
        }

        fun decodeSampled(bytes: ByteArray, sample: Int): Bitmap? {
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }
}
