package com.example.ui.views.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ImageProcessingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Integrierte Dokumenten-Prüfung in einer einzigen Ansicht:
 * 1. Eckpunkte frei verschiebbar mit Bézier-Kurven und 2x-Lupe über dem Finger
 * 2. Verschiebbare Vorher/Nachher-Trennlinie direkt im Dokument (Original vs. Optimiert)
 * 3. Nur ein Bestätigungs-Button ("Dokument übernehmen")
 * 4. FAB-Button zur individuellen Anpassung oder Deaktivierung der Dokumenten-Optimierung
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReviewAndCropScreen(
    capturedBitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirmFinalDocument: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val bmpWidth = capturedBitmap.width.toFloat()
    val bmpHeight = capturedBitmap.height.toFloat()

    // 4 Ecken im Bitmap-Koordinatensystem [TL, TR, BR, BL] - automatisch initialisiert
    var cornersInBitmap by remember {
        mutableStateOf(ImageProcessingService.detectDocumentCorners(capturedBitmap))
    }

    // Aktiver Ziehpunkt (0..3) oder null
    var activeCornerIndex by remember { mutableStateOf<Int?>(null) }
    var touchScreenOffset by remember { mutableStateOf(Offset.Zero) }

    // Verschiebbare Trennlinie: Split-Position von 0.0f (nur optimiert) bis 1.0f (nur original), Standard 0.5f
    var splitPosition by remember { mutableFloatStateOf(0.5f) }
    var isDraggingSplitter by remember { mutableStateOf(false) }

    // 2-Finger Schärfe-Check-Lupe (frei im Dokument beweglich, Pinch-to-Zoom)
    var isSharpnessLoupeActive by remember { mutableStateOf(false) }
    var sharpnessLoupePos by remember { mutableStateOf(Offset.Zero) }
    var sharpnessZoom by remember { mutableFloatStateOf(1.8f) }

    // Optimierungs-Konfiguration (anpassbar über FAB)
    var isOptimizationEnabled by remember { mutableStateOf(true) }
    var contrastValue by remember { mutableFloatStateOf(1.8f) }
    var brightnessOffsetValue by remember { mutableFloatStateOf(-55f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var showOptimizationSheet by remember { mutableStateOf(false) }

    // Berechnete Bitmaps (Entzerrtes Original & Optimierte Version & Vollbild-Optimierung)
    var warpedOriginalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var warpedOptimizedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var optimizedFullBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isUpdatingBitmaps by remember { mutableStateOf(false) }

    // Vollbild-Optimierung für die Live-Gegenüberstellung (Original vs. Optimiert)
    LaunchedEffect(
        capturedBitmap,
        isOptimizationEnabled,
        contrastValue,
        brightnessOffsetValue
    ) {
        val fullOpt = withContext(Dispatchers.Default) {
            if (isOptimizationEnabled) {
                ImageProcessingService.applyCustomDocumentOptimization(
                    bitmap = capturedBitmap,
                    config = ImageProcessingService.DocumentOptimizationConfig(
                        isEnabled = true,
                        contrast = contrastValue,
                        brightnessOffset = brightnessOffsetValue,
                        cleanBackgroundWhite = true,
                        rotationAngle = 0f
                    )
                )
            } else {
                null
            }
        }
        optimizedFullBitmap = fullOpt
    }

    // Neuberechnung bei Eckpunkt- oder Einstellungsänderung
    LaunchedEffect(
        cornersInBitmap,
        isOptimizationEnabled,
        contrastValue,
        brightnessOffsetValue,
        rotationAngle
    ) {
        isUpdatingBitmaps = true
        val warped = withContext(Dispatchers.Default) {
            val cropped = ImageProcessingService.cropAndWarpPerspective(capturedBitmap, cornersInBitmap)
            if (Math.abs(rotationAngle) > 0.1f) {
                ImageProcessingService.rotateBitmap(cropped, rotationAngle)
            } else {
                cropped
            }
        }
        val optimized = withContext(Dispatchers.Default) {
            ImageProcessingService.applyCustomDocumentOptimization(
                bitmap = warped,
                config = ImageProcessingService.DocumentOptimizationConfig(
                    isEnabled = isOptimizationEnabled,
                    contrast = contrastValue,
                    brightnessOffset = brightnessOffsetValue,
                    cleanBackgroundWhite = true,
                    rotationAngle = 0f
                )
            )
        }
        warpedOriginalBitmap = warped
        warpedOptimizedBitmap = optimized
        isUpdatingBitmaps = false
    }

    val density = LocalDensity.current
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var displayedImageRect by remember { mutableStateOf(Rect.Zero) }

    val rawImageBitmap = remember(capturedBitmap) { capturedBitmap.asImageBitmap() }

    // Hilfsfunktionen für Koordinaten-Mapping
    fun bitmapToScreen(pt: PointF): Offset {
        if (displayedImageRect.width <= 0f || bmpWidth <= 0f) return Offset.Zero
        val scaleX = displayedImageRect.width / bmpWidth
        val scaleY = displayedImageRect.height / bmpHeight
        return Offset(
            displayedImageRect.left + pt.x * scaleX,
            displayedImageRect.top + pt.y * scaleY
        )
    }

    fun screenToBitmap(offset: Offset): PointF {
        if (displayedImageRect.width <= 0f || bmpWidth <= 0f) return PointF(0f, 0f)
        val scaleX = bmpWidth / displayedImageRect.width
        val scaleY = bmpHeight / displayedImageRect.height
        val bx = ((offset.x - displayedImageRect.left) * scaleX).coerceIn(0f, bmpWidth)
        val by = ((offset.y - displayedImageRect.top) * scaleY).coerceIn(0f, bmpHeight)
        return PointF(bx, by)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("document_review_and_crop_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. OBERE LEISTE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("btn_review_cancel")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Abbrechen",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Dokument prüfen & anpassen",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ecken justieren • 2 Finger: Schärfe-Lupe • Trennlinie: Split",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = {
                        rotationAngle = (rotationAngle + 90f) % 360f
                    },
                    modifier = Modifier.testTag("btn_review_quick_rotate")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "90° drehen",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }

            // 2. DOKUMENTEN-FLÄCHE MIT TRENNLINIE & ECKPUNKT-HANDLES
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(cornersInBitmap, splitPosition, displayedImageRect) {
                            awaitEachGesture {
                                var initialTwoFingerDist: Float? = null
                                val touchThresholdPx = with(density) { 48.dp.toPx() }

                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                val startOffset = firstDown.position

                                var closestIndex: Int? = null
                                var minDistance = Float.MAX_VALUE
                                cornersInBitmap.forEachIndexed { index, pt ->
                                    val screenPt = bitmapToScreen(pt)
                                    val dist = hypot(screenPt.x - startOffset.x, screenPt.y - startOffset.y)
                                    if (dist < minDistance && dist <= touchThresholdPx) {
                                        minDistance = dist
                                        closestIndex = index
                                    }
                                }

                                if (closestIndex != null) {
                                    activeCornerIndex = closestIndex
                                    isDraggingSplitter = false
                                    touchScreenOffset = startOffset
                                } else {
                                    val splitterX = displayedImageRect.left + (displayedImageRect.width * splitPosition)
                                    if (kotlin.math.abs(startOffset.x - splitterX) < touchThresholdPx * 1.2f) {
                                        isDraggingSplitter = true
                                        activeCornerIndex = null
                                    }
                                }

                                do {
                                    val event = awaitPointerEvent()
                                    val pressedPointers = event.changes.filter { it.pressed }

                                    if (pressedPointers.size >= 2) {
                                        // 2-FINGER GESTE: Schärfe-Check-Lupe aktivieren
                                        activeCornerIndex = null
                                        isDraggingSplitter = false

                                        val p1 = pressedPointers[0].position
                                        val p2 = pressedPointers[1].position
                                        val mid = (p1 + p2) / 2f
                                        val dist = hypot(p1.x - p2.x, p1.y - p2.y)

                                        if (initialTwoFingerDist == null) {
                                            initialTwoFingerDist = dist.coerceAtLeast(10f)
                                            isSharpnessLoupeActive = true
                                            sharpnessLoupePos = mid
                                            sharpnessZoom = 1.8f
                                        } else {
                                            sharpnessLoupePos = mid
                                            val ratio = if (initialTwoFingerDist > 10f) dist / initialTwoFingerDist else 1f
                                            sharpnessZoom = (1.8f * ratio).coerceIn(1.5f, 5.0f)
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (pressedPointers.size == 1) {
                                        if (isSharpnessLoupeActive) {
                                            // Sobald ein Finger losgelassen wird: Lupe automatisch schließen
                                            isSharpnessLoupeActive = false
                                            initialTwoFingerDist = null
                                        } else {
                                            val change = pressedPointers[0]
                                            change.consume()
                                            val currentIndex = activeCornerIndex
                                            if (currentIndex != null && currentIndex in cornersInBitmap.indices) {
                                                touchScreenOffset = change.position
                                                val newBitmapPt = screenToBitmap(change.position)
                                                val updated = cornersInBitmap.toMutableList()
                                                updated[currentIndex] = newBitmapPt
                                                cornersInBitmap = updated
                                            } else if (isDraggingSplitter && displayedImageRect.width > 0f) {
                                                val relX = (change.position.x - displayedImageRect.left) / displayedImageRect.width
                                                splitPosition = relX.coerceIn(0.05f, 0.95f)
                                            }
                                        }
                                    } else {
                                        if (isSharpnessLoupeActive) {
                                            isSharpnessLoupeActive = false
                                            initialTwoFingerDist = null
                                        }
                                        activeCornerIndex = null
                                        isDraggingSplitter = false
                                    }
                                } while (event.changes.any { it.pressed })

                                isSharpnessLoupeActive = false
                                initialTwoFingerDist = null
                                activeCornerIndex = null
                                isDraggingSplitter = false
                            }
                        }
                ) {
                    canvasSize = size

                    val scale = minOf(size.width / bmpWidth, size.height / bmpHeight)
                    val dispW = bmpWidth * scale
                    val dispH = bmpHeight * scale
                    val offsetX = (size.width - dispW) / 2f
                    val offsetY = (size.height - dispH) / 2f
                    displayedImageRect = Rect(offsetX, offsetY, offsetX + dispW, offsetY + dispH)

                    val p0 = bitmapToScreen(cornersInBitmap[0]) // TL
                    val p1 = bitmapToScreen(cornersInBitmap[1]) // TR
                    val p2 = bitmapToScreen(cornersInBitmap[2]) // BR
                    val p3 = bitmapToScreen(cornersInBitmap[3]) // BL

                    // 1. Dokument-Pfad mit abgerundeten Bézier-Ecken
                    val docPath = Path().apply {
                        moveTo(p0.x, p0.y)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }

                    // 2. Basisfoto zeichnen
                    drawImage(
                        image = rawImageBitmap,
                        dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                        dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt())
                    )

                    // 3. Trennlinien-Position berechnen
                    val splitX = displayedImageRect.left + (displayedImageRect.width * splitPosition)

                    // 4. Rechts von der Trennlinie: Optimiertes Bild innerhalb des Dokument-Ausschnitts zeichnen
                    val optBmp = optimizedFullBitmap?.asImageBitmap()
                    if (optBmp != null && isOptimizationEnabled) {
                        clipRect(left = splitX, top = 0f, right = size.width, bottom = size.height) {
                            clipPath(path = docPath) {
                                drawImage(
                                    image = optBmp,
                                    dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                                    dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt())
                                )
                            }
                        }
                    }

                    // 5. Außenbereich abdunkeln
                    val outerPath = Path().apply {
                        addRect(Rect(0f, 0f, size.width, size.height))
                    }
                    val dimScrimPath = Path.combine(
                        operation = PathOperation.Difference,
                        path1 = outerPath,
                        path2 = docPath
                    )
                    drawPath(
                        path = dimScrimPath,
                        color = Color.Black.copy(alpha = 0.55f)
                    )

                    // 6. Verbindungsrahmen mit glühendem Farbverlauf
                    val strokeColor = Color(0xFF10B981) // Smaragdgrün
                    val glowColor = Color(0xFF38BDF8) // Cyan
                    drawPath(
                        path = docPath,
                        color = glowColor.copy(alpha = 0.4f),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    drawPath(
                        path = docPath,
                        color = strokeColor,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 7. VERSCHIEBBARE TRENNLINIE (SPLITTER) ZEICHNEN
                    val docMinY = minOf(p0.y, p1.y).coerceAtLeast(displayedImageRect.top)
                    val docMaxY = maxOf(p2.y, p3.y).coerceAtMost(displayedImageRect.bottom)

                    // Trennlinie
                    drawLine(
                        color = Color.Black.copy(alpha = 0.5f),
                        start = Offset(splitX + 1.dp.toPx(), docMinY),
                        end = Offset(splitX + 1.dp.toPx(), docMaxY),
                        strokeWidth = 3.dp.toPx()
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(splitX, docMinY),
                        end = Offset(splitX, docMaxY),
                        strokeWidth = 2.5.dp.toPx()
                    )

                    // Runder Schiebe-Knopf in der Mitte der Trennlinie
                    val handleCenterY = (docMinY + docMaxY) / 2f
                    drawCircle(
                        color = Color(0xFF0F172A),
                        radius = 16.dp.toPx(),
                        center = Offset(splitX, handleCenterY)
                    )
                    drawCircle(
                        color = Color(0xFF38BDF8),
                        radius = 16.dp.toPx(),
                        center = Offset(splitX, handleCenterY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 5.dp.toPx(),
                        center = Offset(splitX, handleCenterY)
                    )

                    // 8. 4 ECKPUNKT-HANDLES ZEICHNEN
                    val screenPoints = listOf(p0, p1, p2, p3)
                    screenPoints.forEachIndexed { idx, pt ->
                        val isActive = (idx == activeCornerIndex)
                        val handleRadius = if (isActive) 16.dp.toPx() else 12.dp.toPx()

                        drawCircle(
                            color = glowColor.copy(alpha = if (isActive) 0.65f else 0.35f),
                            radius = handleRadius + 6.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = Color.White,
                            radius = handleRadius,
                            center = pt
                        )
                        drawCircle(
                            color = if (isActive) Color(0xFF10B981) else Color(0xFF0284C7),
                            radius = handleRadius * 0.55f,
                            center = pt
                        )
                    }
                }

                // VORHER / NACHHER BADGES AUF DEM DOKUMENT
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "◀ Original",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOptimizationEnabled) Color(0xFF065F46).copy(alpha = 0.9f) else Color(0xFF475569).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOptimizationEnabled) Color(0xFF10B981) else Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = if (isOptimizationEnabled) "Optimiert ▶" else "Ungefiltert ▶",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // 9. SCHWEBENDE 2X-VERGRÖSSERUNGSLUPE ÜBER DEM FINGER
                val currentActive = activeCornerIndex
                if (currentActive != null && currentActive in cornersInBitmap.indices) {
                    val activePt = bitmapToScreen(cornersInBitmap[currentActive])
                    val loupeSizeDp = 104.dp
                    val loupeSizePx = with(density) { loupeSizeDp.toPx() }
                    val loupeOffsetYDp = (-88).dp

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (activePt.x - (loupeSizePx / 2f)).roundToInt().coerceIn(10, (canvasSize.width - loupeSizePx - 10f).roundToInt().coerceAtLeast(10)),
                                    y = (activePt.y + with(density) { loupeOffsetYDp.toPx() } - (loupeSizePx / 2f)).roundToInt().coerceAtLeast(10)
                                )
                            }
                            .size(loupeSizeDp)
                            .shadow(16.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(3.dp, Color(0xFF38BDF8), CircleShape)
                            .testTag("corner_magnifier_loupe")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val activeBmpPt = cornersInBitmap[currentActive]
                            val zoomFactor = 2.2f

                            val sampleAreaW = (size.width / zoomFactor)
                            val sampleAreaH = (size.height / zoomFactor)

                            val srcLeft = (activeBmpPt.x - (sampleAreaW / 2f)).toInt().coerceIn(0, (bmpWidth - sampleAreaW).toInt().coerceAtLeast(0))
                            val srcTop = (activeBmpPt.y - (sampleAreaH / 2f)).toInt().coerceIn(0, (bmpHeight - sampleAreaH).toInt().coerceAtLeast(0))

                            drawImage(
                                image = rawImageBitmap,
                                srcOffset = IntOffset(srcLeft, srcTop),
                                srcSize = IntSize(sampleAreaW.toInt(), sampleAreaH.toInt()),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )

                            // Fadenkreuz
                            val cX = size.width / 2f
                            val cY = size.height / 2f
                            val reticleLen = 14.dp.toPx()

                            drawLine(
                                color = Color(0xFF10B981),
                                start = Offset(cX - reticleLen, cY),
                                end = Offset(cX + reticleLen, cY),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xFF10B981),
                                start = Offset(cX, cY - reticleLen),
                                end = Offset(cX, cY + reticleLen),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.dp.toPx(),
                                center = Offset(cX, cY)
                            )
                        }
                    }
                }

                // 10. INTERAKTIVE 2-FINGER SCHÄRFE-CHECK-LUPE (FREI IM DOKUMENT BEWEGLICH & STUFENLOS ZOOMBAR)
                if (isSharpnessLoupeActive && activeCornerIndex == null) {
                    val loupeSizeDp = 168.dp
                    val loupeSizePx = with(density) { loupeSizeDp.toPx() }

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (sharpnessLoupePos.x - (loupeSizePx / 2f)).roundToInt().coerceIn(10, (canvasSize.width - loupeSizePx - 10f).roundToInt().coerceAtLeast(10)),
                                    y = (sharpnessLoupePos.y - (loupeSizePx / 2f)).roundToInt().coerceIn(10, (canvasSize.height - loupeSizePx - 10f).roundToInt().coerceAtLeast(10))
                                )
                            }
                            .size(loupeSizeDp)
                            .shadow(24.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(3.5.dp, Color(0xFF10B981), CircleShape)
                            .testTag("sharpness_magnifier_loupe")
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val bmpPt = screenToBitmap(sharpnessLoupePos)
                            val scale = if (bmpWidth > 0f && displayedImageRect.width > 0f) displayedImageRect.width / bmpWidth else 1f

                            // Berechne den Quellausschnitt basierend auf der aktuellen 2-Finger-Vergrößerung
                            val sampleAreaW = (size.width / scale) / sharpnessZoom
                            val sampleAreaH = (size.height / scale) / sharpnessZoom

                            val srcLeft = (bmpPt.x - (sampleAreaW / 2f)).toInt().coerceIn(0, (bmpWidth - sampleAreaW).toInt().coerceAtLeast(0))
                            val srcTop = (bmpPt.y - (sampleAreaH / 2f)).toInt().coerceIn(0, (bmpHeight - sampleAreaH).toInt().coerceAtLeast(0))

                            // Wenn Lupe auf der rechten Seite der Trennlinie ist: optimiertes Dokument anzeigen
                            val splitterScreenX = displayedImageRect.left + (displayedImageRect.width * splitPosition)
                            val showOptimized = isOptimizationEnabled && (sharpnessLoupePos.x >= splitterScreenX)
                            val imageToDraw = if (showOptimized && optimizedFullBitmap != null) {
                                optimizedFullBitmap!!.asImageBitmap()
                            } else {
                                rawImageBitmap
                            }

                            drawImage(
                                image = imageToDraw,
                                srcOffset = IntOffset(srcLeft, srcTop),
                                srcSize = IntSize(sampleAreaW.toInt().coerceAtLeast(1), sampleAreaH.toInt().coerceAtLeast(1)),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )

                            // Fokus-Fadenkreuz & Kreis
                            val cX = size.width / 2f
                            val cY = size.height / 2f
                            val reticleLen = 18.dp.toPx()

                            drawCircle(
                                color = Color(0xFF10B981).copy(alpha = 0.5f),
                                radius = 26.dp.toPx(),
                                center = Offset(cX, cY),
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            drawLine(
                                color = Color(0xFF10B981),
                                start = Offset(cX - reticleLen, cY),
                                end = Offset(cX + reticleLen, cY),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawLine(
                                color = Color(0xFF10B981),
                                start = Offset(cX, cY - reticleLen),
                                end = Offset(cX, cY + reticleLen),
                                strokeWidth = 2.dp.toPx()
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 3.5.dp.toPx(),
                                center = Offset(cX, cY)
                            )
                        }

                        // Vergrößerungs-Badge unten in der Lupe
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A).copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.8f)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Schärfe: ${(sharpnessZoom * 10).roundToInt() / 10f}x",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Schärfe-Hinweis-Banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A).copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = "🔍 Schärfe-Check aktiv • Finger spreizen = Zoom • Loslassen = Schließen",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // 3. UNTERER BEREICH: NUR EIN BESTÄTIGUNGS-BUTTON & EIN FAB ZUR OPTIMIERUNGS-ANPASSUNG
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // EINZIGER HAUPT-BUTTON ZUM BESTÄTIGEN DES DOKUMENTS
                    Button(
                        onClick = {
                            val finalToUse = if (isOptimizationEnabled) {
                                warpedOptimizedBitmap ?: warpedOriginalBitmap ?: capturedBitmap
                            } else {
                                warpedOriginalBitmap ?: capturedBitmap
                            }
                            onConfirmFinalDocument(finalToUse)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("btn_review_confirm_document")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dokument übernehmen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // FAB ZUR ANPASSUNG ODER DEAKTIVIERUNG DER DOKUMENTEN-OPTIMIERUNG
                    FloatingActionButton(
                        onClick = { showOptimizationSheet = true },
                        containerColor = if (isOptimizationEnabled) Color(0xFF0284C7) else Color(0xFF475569),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("fab_document_optimization_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Dokumenten-Optimierung anpassen oder deaktivieren",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 4. MODAL-SHEET ZUR FEINJUSTIERUNG ODER DEAKTIVIERUNG DER OPTIMIERUNG
        if (showOptimizationSheet) {
            ModalBottomSheet(
                onDismissRequest = { showOptimizationSheet = false },
                containerColor = Color(0xFF1E293B),
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Dokumenten-Optimierung",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Automatische Bereinigung anpassen oder abschalten",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        Switch(
                            checked = isOptimizationEnabled,
                            onCheckedChange = { isOptimizationEnabled = it }
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                    if (isOptimizationEnabled) {
                        // KONTRAST-REGLER
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Kontrast & Textschärfe:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${(contrastValue * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            Slider(
                                value = contrastValue,
                                onValueChange = { contrastValue = it },
                                valueRange = 1.0f..2.6f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // HINTERGRUND-BEREINIGUNG (SCHATTENFILTER)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Hintergrund-Aufhellung / Schattenfilter:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${((-brightnessOffsetValue) / 90f * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                            Slider(
                                value = brightnessOffsetValue,
                                onValueChange = { brightnessOffsetValue = it },
                                valueRange = -90f..(-20f),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF334155).copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Optimierung ist deaktiviert. Das Dokument wird mit seinen unveränderten Originalfarben und Schatten abgespeichert.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // DREHUNG & ZURÜCKSETZEN
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isOptimizationEnabled = true
                                contrastValue = 1.8f
                                brightnessOffsetValue = -55f
                                rotationAngle = 0f
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Standardwerte")
                        }

                        Button(
                            onClick = { showOptimizationSheet = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Übernehmen")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}
