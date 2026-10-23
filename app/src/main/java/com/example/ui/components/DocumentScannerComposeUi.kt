package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.service.NormalizedQuad
import com.example.service.TrackerStatus
import kotlin.math.hypot

private val ScannerGreen = Color(0xFF00E676)
private val ScannerFill = Color(0x3300E676)

/** Polygon and capture-stability indicator aligned to a fit-center 3:4 camera image. */
@Composable
fun LiveScannerOverlay(
    status: TrackerStatus,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val bounds = ImageViewportBounds.computeFitCenterBounds(
            containerWidth = size.width,
            containerHeight = size.height,
            imageAspectRatio = 3f / 4f
        )
        val quad = status.smoothedQuad ?: return@Canvas
        if (quad.size != 4 || bounds.width <= 0f || bounds.height <= 0f) return@Canvas

        val points = quad.map(bounds::normToScreen)
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[3].x, points[3].y)
            close()
        }
        drawPath(path, ScannerFill)
        drawPath(path, ScannerGreen, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

        val progress = status.stabilityProgress.coerceIn(0f, 1f)
        if (progress > 0f) {
            val diameter = 52.dp.toPx()
            val center = Offset(size.width - diameter / 2f - 16.dp.toPx(),
                size.height - diameter / 2f - 24.dp.toPx())
            drawCircle(
                color = ScannerGreen.copy(alpha = 0.22f),
                radius = diameter / 2f,
                center = center,
                style = Stroke(width = 4.dp.toPx())
            )
            drawArc(
                color = ScannerGreen,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(center.x - diameter / 2f, center.y - diameter / 2f),
                size = Size(diameter, diameter),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Manual corner adjustment with image-coordinate hit testing and a 2.5x circular loupe.
 * The image and every pointer coordinate use the same exact fit-center viewport.
 */
@Composable
fun ManualCropMagnifierScreen(
    bitmap: Bitmap,
    initialQuad: NormalizedQuad,
    onConfirm: (NormalizedQuad) -> Unit,
    onRetake: () -> Unit
) {
    var quad by remember(initialQuad) { mutableStateOf(initialQuad) }
    var activeCornerIndex by remember { mutableStateOf<Int?>(null) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF101010)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Ecken ziehen, um den Zuschnitt anzupassen",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier.fillMaxSize().pointerInput(bitmap) {
                    val touchRadius = 42.dp.toPx()
                    detectDragGestures(
                        onDragStart = { touch ->
                            val bounds = ImageViewportBounds.computeFitCenterBounds(
                                size.width.toFloat(),
                                size.height.toFloat(),
                                bitmap.width.toFloat() / bitmap.height.toFloat()
                            )
                            val corners = listOf(quad.tl, quad.tr, quad.br, quad.bl)
                                .map(bounds::normToScreen)
                            activeCornerIndex = corners.indices
                                .filter { i ->
                                    hypot(corners[i].x - touch.x, corners[i].y - touch.y) <= touchRadius
                                }
                                .minByOrNull { i ->
                                    hypot(corners[i].x - touch.x, corners[i].y - touch.y)
                                }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val index = activeCornerIndex ?: return@detectDragGestures
                            val bounds = ImageViewportBounds.computeFitCenterBounds(
                                size.width.toFloat(),
                                size.height.toFloat(),
                                bitmap.width.toFloat() / bitmap.height.toFloat()
                            )
                            if (bounds.width <= 0f || bounds.height <= 0f) return@detectDragGestures
                            val point = bounds.screenToNorm(change.position)
                            val points = listOf(quad.tl, quad.tr, quad.br, quad.bl).map {
                                PointF(it.x, it.y)
                            }.toMutableList()
                            points[index] = point
                            val candidate = NormalizedQuad(
                                tl = points[0], tr = points[1], br = points[2], bl = points[3],
                                confidence = quad.confidence
                            )
                            if (candidate.isConvex()) quad = candidate
                        },
                        onDragEnd = { activeCornerIndex = null },
                        onDragCancel = { activeCornerIndex = null }
                    )
                }
            ) {
                val bounds = ImageViewportBounds.computeFitCenterBounds(
                    size.width, size.height,
                    bitmap.width.toFloat() / bitmap.height.toFloat()
                )
                if (bounds.width <= 0f || bounds.height <= 0f) return@Canvas

                drawImage(
                    image = image,
                    dstOffset = androidx.compose.ui.unit.IntOffset(bounds.left.toInt(), bounds.top.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(bounds.width.toInt(), bounds.height.toInt())
                )

                val corners = listOf(quad.tl, quad.tr, quad.br, quad.bl).map(bounds::normToScreen)
                val polygon = Path().apply {
                    moveTo(corners[0].x, corners[0].y)
                    corners.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(polygon, Color(0x4400E676))
                drawPath(polygon, ScannerGreen, style = Stroke(3.dp.toPx()))
                corners.forEachIndexed { index, point ->
                    drawCircle(Color.White, 12.dp.toPx(), point)
                    drawCircle(
                        if (activeCornerIndex == index) Color.Yellow else ScannerGreen,
                        9.dp.toPx(), point
                    )
                }

                val activeIndex = activeCornerIndex
                if (activeIndex != null) {
                    val normPt = listOf(quad.tl, quad.tr, quad.br, quad.bl)[activeIndex]
                    val loupeDiameter = 120.dp.toPx()
                    val margin = 12.dp.toPx()
                    val placeRight = normPt.x < 0.4f && normPt.y < 0.4f
                    val loupeLeft = if (placeRight) size.width - loupeDiameter - margin else margin
                    val loupeTop = margin
                    val center = Offset(loupeLeft + loupeDiameter / 2f, loupeTop + loupeDiameter / 2f)
                    val sourceCenterX = (normPt.x * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                    val sourceCenterY = (normPt.y * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                    // The loupe displays a source window that is 1/2.5 of the
                    // corresponding screen-space width and height.
                    val sourceWidth = (bitmap.width * loupeDiameter / (bounds.width * 2.5f))
                        .toInt().coerceAtLeast(1).coerceAtMost(bitmap.width)
                    val sourceHeight = (bitmap.height * loupeDiameter / (bounds.height * 2.5f))
                        .toInt().coerceAtLeast(1).coerceAtMost(bitmap.height)
                    val sourceLeft = (sourceCenterX - sourceWidth / 2).coerceIn(0, bitmap.width - sourceWidth)
                    val sourceTop = (sourceCenterY - sourceHeight / 2).coerceIn(0, bitmap.height - sourceHeight)
                    val circlePath = Path().apply {
                        addOval(
                            androidx.compose.ui.geometry.Rect(
                                loupeLeft, loupeTop, loupeLeft + loupeDiameter, loupeTop + loupeDiameter
                            )
                        )
                    }
                    drawCircle(Color.White, loupeDiameter / 2f + 2.dp.toPx(), center)
                    clipPath(circlePath) {
                        drawImage(
                            image = image,
                            srcOffset = androidx.compose.ui.unit.IntOffset(sourceLeft, sourceTop),
                            srcSize = androidx.compose.ui.unit.IntSize(sourceWidth, sourceHeight),
                            dstOffset = androidx.compose.ui.unit.IntOffset(loupeLeft.toInt(), loupeTop.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(loupeDiameter.toInt(), loupeDiameter.toInt())
                        )
                    }
                    drawCircle(Color.White, loupeDiameter / 2f, center, style = Stroke(2.dp.toPx()))
                    drawLine(Color.Black, Offset(center.x - 10.dp.toPx(), center.y),
                        Offset(center.x + 10.dp.toPx(), center.y), 1.dp.toPx())
                    drawLine(Color.Black, Offset(center.x, center.y - 10.dp.toPx()),
                        Offset(center.x, center.y + 10.dp.toPx()), 1.dp.toPx())
                    drawCircle(ScannerGreen, 3.dp.toPx(), center)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text("Erneut aufnehmen")
            }
            Button(
                onClick = { onConfirm(quad) },
                modifier = Modifier.weight(1f),
                enabled = quad.isConvex()
            ) {
                Text("Zuschnitt bestätigen")
            }
        }
    }
}
