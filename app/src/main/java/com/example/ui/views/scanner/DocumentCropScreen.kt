package com.example.ui.views.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ImageProcessingService
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Interaktiver Zuschnitt-Editor mit 4 verschiebbaren Eckpunkten,
 * Bézier-Ecken, Abdunkelung des Außenbereichs und schwebender 2x-Lupe über dem Finger.
 */
@Composable
fun DocumentCropScreen(
    capturedBitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirmCrop: (List<PointF>) -> Unit,
    modifier: Modifier = Modifier
) {
    val bmpWidth = capturedBitmap.width.toFloat()
    val bmpHeight = capturedBitmap.height.toFloat()

    // 4 Ecken im Bitmap-Koordinatensystem [TL, TR, BR, BL]
    var cornersInBitmap by remember {
        mutableStateOf(ImageProcessingService.detectDocumentCorners(capturedBitmap))
    }

    // Aktiver Ziehpunkt (0..3) oder null
    var activeCornerIndex by remember { mutableStateOf<Int?>(null) }
    var touchScreenOffset by remember { mutableStateOf(Offset.Zero) }

    // Bild-Darstellungsabmessungen im View-Koordinatensystem
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var displayedImageRect by remember { mutableStateOf(Rect.Zero) }

    val density = LocalDensity.current

    // Berechne Skalierungs- und Offset-Mapping zwischen Canvas und Bitmap
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

    val imageBitmap = remember(capturedBitmap) { capturedBitmap.asImageBitmap() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("document_crop_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // OBERE LEISTE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("btn_crop_cancel")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Abbrechen",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Zuschnitt anpassen",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Eckpunkte mit Lupe pixelgenau ausrichten",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = {
                        cornersInBitmap = ImageProcessingService.getDefaultCorners(bmpWidth, bmpHeight, 0f)
                    },
                    modifier = Modifier.testTag("btn_crop_reset")
                ) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Ganzes Bild",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }

            // INTERAKTIVES CANVAS-FELD
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    // Finde den nächstgelegenen Eckpunkt innerhalb von 60dp Touch-Radius
                                    val touchThresholdPx = with(density) { 48.dp.toPx() }
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

                                    activeCornerIndex = closestIndex
                                    touchScreenOffset = startOffset
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val currentIndex = activeCornerIndex
                                    if (currentIndex != null && currentIndex in cornersInBitmap.indices) {
                                        touchScreenOffset = change.position
                                        val newBitmapPt = screenToBitmap(change.position)
                                        val updated = cornersInBitmap.toMutableList()
                                        updated[currentIndex] = newBitmapPt
                                        cornersInBitmap = updated
                                    }
                                },
                                onDragEnd = {
                                    activeCornerIndex = null
                                },
                                onDragCancel = {
                                    activeCornerIndex = null
                                }
                            )
                        }
                ) {
                    canvasSize = size

                    // Bild mit preserve aspect ratio einpassen
                    val scale = minOf(size.width / bmpWidth, size.height / bmpHeight)
                    val dispW = bmpWidth * scale
                    val dispH = bmpHeight * scale
                    val offsetX = (size.width - dispW) / 2f
                    val offsetY = (size.height - dispH) / 2f
                    displayedImageRect = Rect(offsetX, offsetY, offsetX + dispW, offsetY + dispH)

                    // 1. Hintergrundbild zeichnen
                    drawImage(
                        image = imageBitmap,
                        dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                        dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt())
                    )

                    // 2. Eckpunkte im Screen-Raum berechnen
                    val p0 = bitmapToScreen(cornersInBitmap[0]) // Top-Left
                    val p1 = bitmapToScreen(cornersInBitmap[1]) // Top-Right
                    val p2 = bitmapToScreen(cornersInBitmap[2]) // Bottom-Right
                    val p3 = bitmapToScreen(cornersInBitmap[3]) // Bottom-Left

                    // 3. Dokument-Pfad mit abgerundeten Bézier-Ecken erstellen
                    val docPath = Path().apply {
                        val cornerRadius = 18f
                        moveTo(p0.x, p0.y)
                        lineTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        close()
                    }

                    // 4. Außenbereich abdunkeln
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
                        color = Color.Black.copy(alpha = 0.58f)
                    )

                    // 5. Verbindungsrahmen mit glühendem Farbverlauf zeichnen
                    val strokeColor = Color(0xFF10B981) // Smaragdgrün
                    val glowColor = Color(0xFF38BDF8) // Cyan Glow
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

                    // 6. 4 Eckpunkt-Handles zeichnen
                    val screenPoints = listOf(p0, p1, p2, p3)
                    screenPoints.forEachIndexed { idx, pt ->
                        val isActive = (idx == activeCornerIndex)
                        val handleRadius = if (isActive) 16.dp.toPx() else 12.dp.toPx()

                        // Äußerer Glow-Ring
                        drawCircle(
                            color = glowColor.copy(alpha = if (isActive) 0.6f else 0.3f),
                            radius = handleRadius + 6.dp.toPx(),
                            center = pt
                        )

                        // Weißer Trägerkreis
                        drawCircle(
                            color = Color.White,
                            radius = handleRadius,
                            center = pt
                        )

                        // Farbiger innerer Ankerpunkt
                        drawCircle(
                            color = if (isActive) Color(0xFF10B981) else Color(0xFF0284C7),
                            radius = handleRadius * 0.55f,
                            center = pt
                        )
                    }
                }

                // 7. SCHWEBENDE 2X-VERGRÖSSERUNGSLUPE ÜBER DEM FINGER (LUPE)
                val currentActive = activeCornerIndex
                if (currentActive != null && currentActive in cornersInBitmap.indices) {
                    val activePt = bitmapToScreen(cornersInBitmap[currentActive])
                    // Platziere die Lupe 85dp oberhalb des Touchpunkts, zentriert
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

                            // Zielausschnitt aus dem Quellbild zentriert auf activeBmpPt
                            val sampleAreaW = (size.width / zoomFactor)
                            val sampleAreaH = (size.height / zoomFactor)

                            val srcLeft = (activeBmpPt.x - (sampleAreaW / 2f)).toInt().coerceIn(0, (bmpWidth - sampleAreaW).toInt().coerceAtLeast(0))
                            val srcTop = (activeBmpPt.y - (sampleAreaH / 2f)).toInt().coerceIn(0, (bmpHeight - sampleAreaH).toInt().coerceAtLeast(0))

                            drawImage(
                                image = imageBitmap,
                                srcOffset = IntOffset(srcLeft, srcTop),
                                srcSize = IntSize(sampleAreaW.toInt(), sampleAreaH.toInt()),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )

                            // Fadenkreuz in der Lupenmitte
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
            }

            // UNTERE AKTIONEN
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            cornersInBitmap = ImageProcessingService.detectDocumentCorners(capturedBitmap)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("btn_crop_auto_detect")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Auto-Erkennung")
                    }

                    Button(
                        onClick = {
                            onConfirmCrop(cornersInBitmap)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_crop_confirm")
                    ) {
                        Text(
                            text = "Weiter zur Vorschau",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
