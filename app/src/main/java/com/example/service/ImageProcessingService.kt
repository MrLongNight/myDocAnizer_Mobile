package com.example.service

import android.graphics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

object ImageProcessingService {

    internal fun ensureSoftwareBitmap(src: Bitmap): Bitmap {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE) {
            src.copy(Bitmap.Config.ARGB_8888, false) ?: src
        } else {
            src
        }
    }

    data class ImageAnalysisResult(
        val isRecommendedBw: Boolean,
        val brightness: Float, // 0.0 to 1.0
        val isWellLit: Boolean,
        val skewAngleDegrees: Float
    )

    /**
     * Analyzes image for automatic B&W vs Color selection and lighting guidelines
     */
    suspend fun analyzeImage(bitmap: Bitmap): ImageAnalysisResult = withContext(Dispatchers.Default) {
        val workingBitmap = ensureSoftwareBitmap(bitmap)
        val sampleSize = 40

        val small = Bitmap.createScaledBitmap(workingBitmap, sampleSize, sampleSize, false)
        val pixels = IntArray(sampleSize * sampleSize)
        small.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)

        var totalLuminance = 0.0
        var colorDiffTotal = 0.0
        var pixelCount = 0

        for (i in 0 until sampleSize * sampleSize) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Perceived luminance
            val lum = (0.299 * r + 0.587 * g + 0.114 * b)
            totalLuminance += lum

            // Color saturation indicator (difference between RGB channels)
            val maxC = maxOf(r, maxOf(g, b))
            val minC = minOf(r, minOf(g, b))
            colorDiffTotal += (maxC - minC)

            pixelCount++
        }

        if (small !== workingBitmap && small !== bitmap) {
            small.recycle()
        }

        val avgLuminance = if (pixelCount > 0) (totalLuminance / pixelCount) / 255.0 else 0.5
        val avgColorDiff = if (pixelCount > 0) colorDiffTotal / pixelCount else 0.0

        // If color differences are very low (< 25), it is predominantly monochrome/printed document
        val isRecommendedBw = avgColorDiff < 25.0
        val isWellLit = avgLuminance in 0.35..0.90

        ImageAnalysisResult(
            isRecommendedBw = isRecommendedBw,
            brightness = avgLuminance.toFloat(),
            isWellLit = isWellLit,
            skewAngleDegrees = 0.0f
        )
    }

    /**
     * High contrast document Black & White thresholding for crystal clear text,
     * taking optional template configuration into account
     */
    suspend fun convertToOptimizedBw(
        src: Bitmap,
        contrast: Float = 1.8f,
        brightnessOffset: Float = -60f,
        cleanBackgroundWhite: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            val workingSrc = ensureSoftwareBitmap(src)
            val width = workingSrc.width
            val height = workingSrc.height
            val bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmpGrayscale)
            val paint = Paint()

            val bOffset = if (cleanBackgroundWhite) brightnessOffset else (brightnessOffset * 0.5f)
            val cm = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, bOffset,
                0f, contrast, 0f, 0f, bOffset,
                0f, 0f, contrast, 0f, bOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
            cm.preConcat(grayMatrix)

            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(workingSrc, 0f, 0f, paint)

            bmpGrayscale
        } catch (_: Throwable) {
            src
        }
    }

    /**
     * Color enhancement filter (document photo boost) with template parameters
     */
    suspend fun enhanceColor(
        src: Bitmap,
        contrast: Float = 1.15f,
        brightnessOffset: Float = -10f
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            val workingSrc = ensureSoftwareBitmap(src)
            val width = workingSrc.width
            val height = workingSrc.height
            val bmpEnhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmpEnhanced)
            val paint = Paint()

            val cm = ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightnessOffset,
                0f, contrast, 0f, 0f, brightnessOffset,
                0f, 0f, contrast, 0f, brightnessOffset,
                0f, 0f, 0f, 1f, 0f
            ))
            val satMatrix = ColorMatrix().apply { setSaturation(1.15f) }
            cm.preConcat(satMatrix)

            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(workingSrc, 0f, 0f, paint)

            bmpEnhanced
        } catch (_: Throwable) {
            src
        }
    }

    /**
     * Automatische Ausrichtung (Straighten) basierend auf dem erkannten Schrägwinkel.
     * Korrigiert Dokumentenbilder, die beim Fotografieren leicht schräg gehalten wurden.
     */
    fun straightenBitmap(src: Bitmap, angle: Float): Bitmap {
        if (Math.abs(angle) < 0.1f) return src
        return try {
            val workingSrc = ensureSoftwareBitmap(src)
            val matrix = Matrix().apply { postRotate(angle) }
            Bitmap.createBitmap(workingSrc, 0, 0, workingSrc.width, workingSrc.height, matrix, true)
        } catch (_: Throwable) {
            src
        }
    }

    /**
     * Misst die Bildschärfe anhand hochfrequenter Kanten- und Kontrastgradienten (Laplace-Schärfegrad).
     * Gibt einen normierten Prozentwert von 0.0 bis 100.0 zurück.
     */
    fun measureSharpness(bitmap: Bitmap): Float {
        return try {
            val workingBmp = ensureSoftwareBitmap(bitmap)
            val sampleW = 200
            val sampleH = (200 * (workingBmp.height.toFloat() / workingBmp.width.toFloat())).toInt().coerceAtLeast(100)
            val small = Bitmap.createScaledBitmap(workingBmp, sampleW, sampleH, false)
            val pixels = IntArray(sampleW * sampleH)
            small.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
            var edgeSum = 0.0
            var count = 0

            for (y in 1 until sampleH - 1) {
                for (x in 1 until sampleW - 1) {
                    val pCenter = (pixels[y * sampleW + x] shr 16) and 0xFF
                    val pLeft = (pixels[y * sampleW + (x - 1)] shr 16) and 0xFF
                    val pRight = (pixels[y * sampleW + (x + 1)] shr 16) and 0xFF
                    val pTop = (pixels[(y - 1) * sampleW + x] shr 16) and 0xFF
                    val pBottom = (pixels[(y + 1) * sampleW + x] shr 16) and 0xFF

                    val laplace = Math.abs(4 * pCenter - pLeft - pRight - pTop - pBottom)
                    edgeSum += laplace
                    count++
                }
            }
            small.recycle()
            val avgLaplace = if (count > 0) (edgeSum / count) else 0.0
            // Normalisierung auf 0 bis 100%
            ((avgLaplace / 12.0) * 100.0).toFloat().coerceIn(10f, 99f)
        } catch (_: Throwable) {
            85.0f
        }
    }

    /**
     * Führt eine 4-Punkt-Perspektiventzerrung (Homographie) durch:
     * Transformiert das durch die 4 Ecken [TL, TR, BR, BL] aufgespannte Viereck
     * in ein planes, rechteckiges Dokumentbild.
     */
    fun cropAndWarpPerspective(src: Bitmap, corners: List<PointF>): Bitmap {
        val workingSrc = ensureSoftwareBitmap(src)
        if (corners.size != 4) return autoCropDocument(workingSrc)
        return try {
            val tl = corners[0]
            val tr = corners[1]
            val br = corners[2]
            val bl = corners[3]

            val widthTop = Math.hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
            val widthBottom = Math.hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
            val targetW = Math.max(widthTop, widthBottom).toInt().coerceIn(400, 3000)

            val heightLeft = Math.hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
            val heightRight = Math.hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
            val targetH = Math.max(heightLeft, heightRight).toInt().coerceIn(400, 4000)

            val srcPoints = floatArrayOf(
                tl.x, tl.y,
                tr.x, tr.y,
                br.x, br.y,
                bl.x, bl.y
            )
            val dstPoints = floatArrayOf(
                0f, 0f,
                targetW.toFloat(), 0f,
                targetW.toFloat(), targetH.toFloat(),
                0f, targetH.toFloat()
            )

            val matrix = Matrix()
            matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

            val resultBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultBitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            canvas.drawBitmap(workingSrc, matrix, paint)

            resultBitmap
        } catch (_: Throwable) {
            autoCropDocument(workingSrc)
        }
    }

    enum class DocumentFilterMode(val label: String) {
        AUTO("Auto"),
        BW_CLEAN("S/W Bereinigt"),
        COLOR_ENHANCE("Farbe"),
        ORIGINAL("Original")
    }

    private data class BrightPaperRegion(
        val bounds: Rect,
        val corners: List<PointF>
    )

    /**
     * Finds a connected paper-like component and estimates its actual four corners.
     * The component must fill its inferred quadrilateral; round objects are rejected.
     */
    private fun detectBrightPaperRegion(bitmap: Bitmap): BrightPaperRegion? {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 160 || height < 160) return null

        val scale = minOf(0.12f, 320f / maxOf(width, height).toFloat())
        val smallW = (width * scale).roundToInt().coerceAtLeast(20)
        val smallH = (height * scale).roundToInt().coerceAtLeast(20)
        val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, false)
        try {
            val total = smallW * smallH
            val pixels = IntArray(total)
            small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
            val bright = BooleanArray(total)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF
                bright[i] = (0.299 * red + 0.587 * green + 0.114 * blue) >= 140.0
            }

            val visited = BooleanArray(total)
            val queue = IntArray(total)
            var bestScore = 0.0
            var bestBounds: IntArray? = null
            var bestCorners: List<PointF>? = null
            for (start in 0 until total) {
                if (!bright[start] || visited[start]) continue
                var head = 0
                var tail = 0
                queue[tail++] = start
                visited[start] = true
                var minX = smallW
                var maxX = -1
                var minY = smallH
                var maxY = -1
                while (head < tail) {
                    val index = queue[head++]
                    val x = index % smallW
                    val y = index / smallW
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    minY = minOf(minY, y)
                    maxY = maxOf(maxY, y)
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = x + dx
                            val ny = y + dy
                            if (nx !in 0 until smallW || ny !in 0 until smallH) continue
                            val neighbour = ny * smallW + nx
                            if (bright[neighbour] && !visited[neighbour]) {
                                visited[neighbour] = true
                                queue[tail++] = neighbour
                            }
                        }
                    }
                }

                val boxW = maxX - minX + 1
                val boxH = maxY - minY + 1
                if (tail < total * 0.08 ||
                    boxW < smallW * 0.35f || boxH < smallH * 0.35f
                ) continue
                val boxArea = boxW * boxH
                val fillRatio = tail.toFloat() / boxArea.toFloat()
                val frameRatio = boxArea.toFloat() / total.toFloat()
                val aspectRatio = boxW.toFloat() / boxH.toFloat()
                val hasMargins = minX > smallW * 0.01f && minY > smallH * 0.01f &&
                    maxX < smallW * 0.99f && maxY < smallH * 0.99f
                if (fillRatio < 0.42f || frameRatio !in 0.12f..0.92f ||
                    aspectRatio !in 0.32f..3.1f || !hasMargins
                ) continue

                val component = queue.copyOf(tail)
                val topLeftIndex = component.minByOrNull { (it % smallW) + (it / smallW) } ?: continue
                val topRightIndex = component.maxByOrNull { (it % smallW) - (it / smallW) } ?: continue
                val bottomRightIndex = component.maxByOrNull { (it % smallW) + (it / smallW) } ?: continue
                val bottomLeftIndex = component.minByOrNull { (it % smallW) - (it / smallW) } ?: continue
                fun pointFor(index: Int) = PointF(
                    (index % smallW).toFloat() + 0.5f,
                    (index / smallW).toFloat() + 0.5f
                )
                val candidateCorners = listOf(
                    pointFor(topLeftIndex),
                    pointFor(topRightIndex),
                    pointFor(bottomRightIndex),
                    pointFor(bottomLeftIndex)
                )
                if (!isValidConvexQuadrilateral(candidateCorners)) continue

                // Circular objects have many bright pixels outside their inferred diamond.
                val insideCount = component.count { index ->
                    isPointInsideConvexQuadrilateral(
                        (index % smallW).toFloat() + 0.5f,
                        (index / smallW).toFloat() + 0.5f,
                        candidateCorners
                    )
                }
                val containmentRatio = insideCount.toFloat() / component.size.toFloat()
                if (containmentRatio < 0.72f) continue

                val score = tail.toDouble() * fillRatio.toDouble() * containmentRatio.toDouble()
                if (score > bestScore) {
                    bestScore = score
                    bestBounds = intArrayOf(minX, minY, maxX + 1, maxY + 1)
                    bestCorners = candidateCorners
                }
            }

            val bounds = bestBounds ?: return null
            val corners = bestCorners ?: return null
            val sx = width.toFloat() / smallW.toFloat()
            val sy = height.toFloat() / smallH.toFloat()
            val scaledBounds = Rect(
                (bounds[0] * sx).toInt().coerceIn(0, width - 1),
                (bounds[1] * sy).toInt().coerceIn(0, height - 1),
                ((bounds[2] * sx).roundToInt()).coerceIn(1, width),
                ((bounds[3] * sy).roundToInt()).coerceIn(1, height)
            )
            val scaledCorners = corners.map { corner ->
                PointF(
                    (corner.x * sx).coerceIn(0f, width.toFloat()),
                    (corner.y * sy).coerceIn(0f, height.toFloat())
                )
            }
            val centerX = scaledCorners.map { it.x }.average().toFloat()
            val centerY = scaledCorners.map { it.y }.average().toFloat()
            val expandedCorners = scaledCorners.map { corner ->
                PointF(
                    (centerX + (corner.x - centerX) * 1.025f).coerceIn(0f, width.toFloat()),
                    (centerY + (corner.y - centerY) * 1.025f).coerceIn(0f, height.toFloat())
                )
            }
            if (!isValidConvexQuadrilateral(expandedCorners) ||
                quadrilateralArea(expandedCorners) < width.toFloat() * height.toFloat() * 0.08f
            ) return null
            return BrightPaperRegion(scaledBounds, expandedCorners)
        } finally {
            if (small !== bitmap) small.recycle()
        }
    }

    // Keep the rectangle API for non-perspective auto-cropping callers.
    private fun detectBrightPaperBounds(bitmap: Bitmap): Rect? = detectBrightPaperRegion(bitmap)?.bounds

    private fun isValidConvexQuadrilateral(points: List<PointF>): Boolean {
        if (points.size != 4) return false
        val crosses = points.indices.map { index ->
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val c = points[(index + 2) % points.size]
            (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        }
        return crosses.all { it > 0.01f } || crosses.all { it < -0.01f }
    }

    private fun isPointInsideConvexQuadrilateral(x: Float, y: Float, points: List<PointF>): Boolean {
        var hasPositive = false
        var hasNegative = false
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            val cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x)
            if (cross > 0.01f) hasPositive = true
            if (cross < -0.01f) hasNegative = true
            if (hasPositive && hasNegative) return false
        }
        return true
    }

    private fun quadrilateralArea(points: List<PointF>): Float {
        if (points.size != 4) return 0f
        var doubleArea = 0f
        for (index in points.indices) {
            val a = points[index]
            val b = points[(index + 1) % points.size]
            doubleArea += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(doubleArea) / 2f
    }

    /**
     * Estimates the four initial corners [TL, TR, BR, BL]. If a clear paper-like region
     * cannot be detected, it returns a conservative inset rectangle for manual correction.
     */
    fun detectDocumentCorners(bitmap: Bitmap): List<PointF> {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val workingBitmap = try { ensureSoftwareBitmap(bitmap) } catch (_: Throwable) { bitmap }
        val region = try {
            detectBrightPaperRegion(workingBitmap)
        } catch (_: Throwable) {
            null
        } finally {
            if (workingBitmap !== bitmap && !workingBitmap.isRecycled) {
                runCatching { workingBitmap.recycle() }
            }
        }
        if (region == null) return getDefaultCorners(width, height)
        val bounds = region.bounds
        if (bounds.width() < width * 0.3f || bounds.height() < height * 0.3f) {
            return getDefaultCorners(width, height)
        }
        return region.corners
    }

    fun getDefaultCorners(width: Float, height: Float, marginFraction: Float = 0.08f): List<PointF> {
        val mx = width * marginFraction
        val my = height * marginFraction
        return listOf(
            PointF(mx, my),
            PointF(width - mx, my),
            PointF(width - mx, height - my),
            PointF(mx, height - my)
        )
    }

    /**
     * Rotiert ein Bitmap um exakt den übergebenen Winkel (z.B. 90f, -90f).
     */
    fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        if (Math.abs(degrees) < 0.1f) return src
        return try {
            val workingSrc = ensureSoftwareBitmap(src)
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(workingSrc, 0, 0, workingSrc.width, workingSrc.height, matrix, true)
        } catch (_: Throwable) {
            src
        }
    }

    /**
     * Wendet den ausgewählten Dokumenten-Optimierungsfilter an.
     */
    suspend fun applyDocumentFilter(bitmap: Bitmap, mode: DocumentFilterMode): Bitmap = withContext(Dispatchers.Default) {
        when (mode) {
            DocumentFilterMode.AUTO -> {
                val analysis = analyzeImage(bitmap)
                if (analysis.isRecommendedBw) {
                    convertToOptimizedBw(bitmap, contrast = 1.85f, brightnessOffset = -55f, cleanBackgroundWhite = true)
                } else {
                    enhanceColor(bitmap, contrast = 1.15f, brightnessOffset = -10f)
                }
            }
            DocumentFilterMode.BW_CLEAN -> {
                convertToOptimizedBw(bitmap, contrast = 1.9f, brightnessOffset = -60f, cleanBackgroundWhite = true)
            }
            DocumentFilterMode.COLOR_ENHANCE -> {
                enhanceColor(bitmap, contrast = 1.2f, brightnessOffset = -8f)
            }
            DocumentFilterMode.ORIGINAL -> {
                bitmap
            }
        }
    }

    data class DocumentOptimizationConfig(
        val isEnabled: Boolean = true,
        val isColorMode: Boolean = false,
        val contrast: Float = 1.8f,
        val brightnessOffset: Float = -55f,
        val cleanBackgroundWhite: Boolean = true,
        val rotationAngle: Float = 0f
    )

    /**
     * Wendet anpassbare Optimierungs-Parameter für den interaktiven Split-Review-Screen an.
     * Ermöglicht das stufenlose Nachjustieren von Kontrast, Schatten-Entfernung und Drehung
     * oder das vollständige Deaktivieren der Optimierung.
     */
    suspend fun applyCustomDocumentOptimization(
        bitmap: Bitmap,
        config: DocumentOptimizationConfig
    ): Bitmap = withContext(Dispatchers.Default) {
        val workingSrc = ensureSoftwareBitmap(bitmap)
        val rotated = if (Math.abs(config.rotationAngle) > 0.1f) {
            rotateBitmap(workingSrc, config.rotationAngle)
        } else {
            workingSrc
        }

        if (!config.isEnabled) {
            rotated
        } else {
            if (config.isColorMode) {
                enhanceColor(rotated, contrast = config.contrast, brightnessOffset = config.brightnessOffset * 0.25f)
            } else {
                convertToOptimizedBw(
                    src = rotated,
                    contrast = config.contrast,
                    brightnessOffset = config.brightnessOffset,
                    cleanBackgroundWhite = config.cleanBackgroundWhite
                )
            }
        }
    }

    /**
     * Erstellt eine optimierte Voransicht zur Gegenüberstellung (Original vs. Optimiert).
     */
    suspend fun generateComparisonPreview(src: Bitmap, isColor: Boolean): Bitmap = withContext(Dispatchers.Default) {
        if (isColor) {
            enhanceColor(src, contrast = 1.2f, brightnessOffset = -5f)
        } else {
            convertToOptimizedBw(src, contrast = 1.85f, brightnessOffset = -55f, cleanBackgroundWhite = true)
        }
    }

    /**
     * Crops only a dominant paper-like connected component. Unrelated bright objects are ignored.
     * If a reliable paper area is not found, preserve the original image rather than over-cropping.
     */
    fun autoCropDocument(src: Bitmap): Bitmap {
        val workingSrc = ensureSoftwareBitmap(src)
        return try {
            val bounds = detectBrightPaperBounds(workingSrc) ?: return src
            val padX = (bounds.width() * 0.025f).toInt().coerceAtLeast(8)
            val padY = (bounds.height() * 0.025f).toInt().coerceAtLeast(8)
            val left = (bounds.left - padX).coerceAtLeast(0)
            val top = (bounds.top - padY).coerceAtLeast(0)
            val right = (bounds.right + padX).coerceAtMost(workingSrc.width)
            val bottom = (bounds.bottom + padY).coerceAtMost(workingSrc.height)
            val cropW = right - left
            val cropH = bottom - top
            if (cropW < workingSrc.width * 0.25f ||
                cropH < workingSrc.height * 0.25f ||
                cropW >= workingSrc.width * 0.98f ||
                cropH >= workingSrc.height * 0.98f
            ) {
                src
            } else {
                Bitmap.createBitmap(workingSrc, left, top, cropW, cropH)
            }
        } catch (_: Throwable) {
            src
        }
    }

    /**
     * Verarbeitet ein Dokument-Bitmap vollautomatisch und adaptiv:
     * Bei mode == "AUTO" analysiert das System die Farbsättigung des Dokuments.
     * Monochromer / gedruckter Text -> convertToOptimizedBw (hoher Kontrast, minimale Dateigröße)
     * Farbinhalte (Logos, Stempel, Fotos, Textmarker) -> enhanceColor (kräftige, natürliche Farben)
     * Gibt das optimierte Bitmap und den erkannten Farbmodus (isColor) zurück.
     */
    suspend fun processDocumentAdaptive(
        src: Bitmap,
        preferredMode: String = "AUTO"
    ): Pair<Bitmap, Boolean> = withContext(Dispatchers.Default) {
        // 1. Perspektiven-Zuschnitt (Auto-Crop) - Rotation entfernt für Performance
        val cropped = autoCropDocument(src)

        val shouldUseColor = when (preferredMode.uppercase()) {
            "COLOR" -> true
            "BW" -> false
            else -> {
                // Intelligente Erkennung basierend auf Farbkanaldifferenz
                val analysis = analyzeImage(cropped)
                !analysis.isRecommendedBw
            }
        }

        val processedBitmap = if (shouldUseColor) {
            enhanceColor(cropped)
        } else {
            convertToOptimizedBw(cropped)
        }

        // Speicherbereinigung
        if (cropped != src && cropped != processedBitmap) runCatching { cropped.recycle() }

        Pair(processedBitmap, shouldUseColor)
    }

    /**
     * Synthesizes high-fidelity sample document page for preview or test scans
     */
    fun createSampleDocumentBitmap(title: String, sender: String, modeBw: Boolean): Bitmap {
        val width = 1200
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paper background
        val bgPaint = Paint().apply {
            color = if (modeBw) Color.WHITE else Color.rgb(250, 250, 248)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Header border line
        val borderPaint = Paint().apply {
            color = if (modeBw) Color.BLACK else Color.rgb(37, 99, 235)
            strokeWidth = 6f
        }
        canvas.drawLine(100f, 180f, 1100f, 180f, borderPaint)

        // Sender & Title
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 54f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText(sender.uppercase(), 100f, 140f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = if (modeBw) Color.DKGRAY else Color.rgb(30, 58, 138)
            textSize = 40f
            isAntiAlias = true
        }
        canvas.drawText("Betreff: $title", 100f, 260f, subTitlePaint)

        // Simulated invoice / contract lines
        val bodyPaint = Paint().apply {
            color = Color.rgb(40, 40, 40)
            textSize = 28f
            isAntiAlias = true
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
        val today = dateFormat.format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 14)
        val dueDate = dateFormat.format(cal.time)

        val lines = listOf(
            "Rechnungsnummer: INV-${(10000..99999).random()}",
            "Kundennummer: KD-${(1000..9999).random()}",
            "Rechnungsdatum: $today",
            "",
            "Sehr geehrte Damen und Herren,",
            "vielen Dank für Ihr Vertrauen in unsere Dienstleistungen.",
            "Nachfolgend finden Sie die Aufschlüsselung der Abrechnung für den laufenden Monat:",
            "- Grundpreis monatlich: 24,90 EUR",
            "- Arbeitspreis Abrechnung: 78,40 EUR",
            "- Umsatzsteuer (19%): 19,63 EUR",
            "--------------------------------------------------",
            "Rechnungsbetrag: 122,93 EUR",
            "Fälligkeit: $dueDate",
            "",
            "Zahlungsziel: Innerhalb von 14 Tagen ohne Abzug.",
            "IBAN: DE89 3704 0044 0532 0130 00",
            "Für Rückfragen steht Ihnen unser Serviceteam gerne zur Verfügung.",
            "Mit freundlichen Grüßen,",
            "$sender Service & Abrechnungszentrum"
        )

        var currentY = 360f
        lines.forEach { line ->
            canvas.drawText(line, 100f, currentY, bodyPaint)
            currentY += 48f
        }

        // QR / Security barcode placeholder at the bottom
        val codePaint = Paint().apply {
            color = Color.BLACK
        }
        canvas.drawRect(100f, 1380f, 320f, 1500f, codePaint)
        val codeTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 22f
        }
        canvas.drawText("VERIFIED SCAN", 120f, 1445f, codeTextPaint)

        return bitmap
    }
}
