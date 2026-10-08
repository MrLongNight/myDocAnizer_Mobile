package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Rendert das offizielle SVG-Icon 'assets/Local-AI-Agent_button-icon.svg'
 * direkt und pixelgenau auf der Compose Canvas in jeder beliebigen Größe und Farbe.
 */
@Composable
fun LocalAiAgentIcon(
    modifier: Modifier = Modifier.size(44.dp),
    tint: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 512f
        val scaleY = size.height / 512f

        val stroke18 = 18f * scaleX
        val stroke14 = 14f * scaleX

        // 1. Smartphone Outer Frame (rx = 44, ry = 44)
        drawRoundRect(
            color = tint,
            topLeft = Offset(122f * scaleX, 32f * scaleY),
            size = Size(268f * scaleX, 448f * scaleY),
            cornerRadius = CornerRadius(44f * scaleX, 44f * scaleY),
            style = Stroke(width = stroke18, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 2. Top Speaker Notch
        drawRoundRect(
            color = tint,
            topLeft = Offset(213f * scaleX, 56f * scaleY),
            size = Size(86f * scaleX, 14f * scaleY),
            cornerRadius = CornerRadius(7f * scaleX, 7f * scaleY)
        )

        // 3. Left Circuit Traces
        // Trace 1
        val leftTrace1 = Path().apply {
            moveTo(150f * scaleX, 222f * scaleY)
            lineTo(102f * scaleX, 182f * scaleY)
            lineTo(56f * scaleX, 182f * scaleY)
        }
        drawPath(leftTrace1, color = tint, style = Stroke(width = stroke18, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Trace 2
        val leftTrace2 = Path().apply {
            moveTo(150f * scaleX, 256f * scaleY)
            lineTo(112f * scaleX, 256f * scaleY)
            lineTo(80f * scaleX, 282f * scaleY)
            lineTo(56f * scaleX, 282f * scaleY)
        }
        drawPath(leftTrace2, color = tint, style = Stroke(width = stroke18, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Trace 3
        val leftTrace3 = Path().apply {
            moveTo(150f * scaleX, 290f * scaleY)
            lineTo(102f * scaleX, 364f * scaleY)
            lineTo(56f * scaleX, 364f * scaleY)
        }
        drawPath(leftTrace3, color = tint, style = Stroke(width = stroke18, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Left Circular Nodes (Hollow Rings)
        drawCircle(color = tint, radius = 20f * scaleX, center = Offset(36f * scaleX, 182f * scaleY), style = Stroke(width = stroke18))
        drawCircle(color = tint, radius = 20f * scaleX, center = Offset(36f * scaleX, 282f * scaleY), style = Stroke(width = stroke18))
        drawCircle(color = tint, radius = 20f * scaleX, center = Offset(36f * scaleX, 364f * scaleY), style = Stroke(width = stroke18))

        // 4. Right Circuit Traces
        // Trace 1
        val rightTrace1 = Path().apply {
            moveTo(362f * scaleX, 222f * scaleY)
            lineTo(410f * scaleX, 182f * scaleY)
            lineTo(456f * scaleX, 182f * scaleY)
        }
        drawPath(rightTrace1, color = tint, style = Stroke(width = stroke18, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Trace 2
        val rightTrace2 = Path().apply {
            moveTo(362f * scaleX, 256f * scaleY)
            lineTo(400f * scaleX, 256f * scaleY)
            lineTo(432f * scaleX, 282f * scaleY)
            lineTo(456f * scaleX, 282f * scaleY)
        }
        drawPath(rightTrace2, color = tint, style = Stroke(width = stroke18, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Trace 3
        val rightTrace3 = Path().apply {
            moveTo(362f * scaleX, 290f * scaleY)
            lineTo(410f * scaleX, 364f * scaleY)
            lineTo(456f * scaleX, 364f * scaleY)
        }
        drawPath(rightTrace3, color = tint, style = Stroke(width = stroke18, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Right Circular Nodes (Hollow Rings)
        drawCircle(color = tint, radius = 20f * scaleX, center = Offset(476f * scaleX, 182f * scaleY), style = Stroke(width = stroke18))
        drawCircle(color = tint, radius = 20f * scaleX, center = Offset(476f * scaleX, 282f * scaleY), style = Stroke(width = stroke18))
        drawCircle(color = tint, radius = 20f * scaleX, center = Offset(476f * scaleX, 364f * scaleY), style = Stroke(width = stroke18))

        // 5. 12 Chip Pins
        // Top Pins
        drawLine(color = tint, start = Offset(224f * scaleX, 188f * scaleY), end = Offset(224f * scaleX, 156f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(256f * scaleX, 188f * scaleY), end = Offset(256f * scaleX, 156f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(288f * scaleX, 188f * scaleY), end = Offset(288f * scaleX, 156f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)

        // Bottom Pins
        drawLine(color = tint, start = Offset(224f * scaleX, 324f * scaleY), end = Offset(224f * scaleX, 356f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(256f * scaleX, 324f * scaleY), end = Offset(256f * scaleX, 356f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(288f * scaleX, 324f * scaleY), end = Offset(288f * scaleX, 356f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)

        // Left Pins
        drawLine(color = tint, start = Offset(188f * scaleX, 222f * scaleY), end = Offset(156f * scaleX, 222f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(188f * scaleX, 256f * scaleY), end = Offset(156f * scaleX, 256f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(188f * scaleX, 290f * scaleY), end = Offset(156f * scaleX, 290f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)

        // Right Pins
        drawLine(color = tint, start = Offset(324f * scaleX, 222f * scaleY), end = Offset(356f * scaleX, 222f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(324f * scaleX, 256f * scaleY), end = Offset(356f * scaleX, 256f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(324f * scaleX, 290f * scaleY), end = Offset(356f * scaleX, 290f * scaleY), strokeWidth = stroke14, cap = StrokeCap.Round)

        // 6. Center AI Chip Body
        drawRoundRect(
            color = tint,
            topLeft = Offset(188f * scaleX, 188f * scaleY),
            size = Size(136f * scaleX, 136f * scaleY),
            cornerRadius = CornerRadius(20f * scaleX, 20f * scaleY),
            style = Stroke(width = stroke18, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 7. Bold AI Letters
        val aPath = Path().apply {
            moveTo(212f * scaleX, 286f * scaleY)
            lineTo(228f * scaleX, 226f * scaleY)
            lineTo(244f * scaleX, 226f * scaleY)
            lineTo(260f * scaleX, 286f * scaleY)
            lineTo(245f * scaleX, 286f * scaleY)
            lineTo(241f * scaleX, 269f * scaleY)
            lineTo(222f * scaleX, 269f * scaleY)
            lineTo(218f * scaleX, 286f * scaleY)
            close()
            moveTo(226f * scaleX, 255f * scaleY)
            lineTo(237f * scaleX, 255f * scaleY)
            lineTo(232f * scaleX, 237f * scaleY)
            close()
        }
        drawPath(aPath, color = tint)

        // Letter I
        drawRect(
            color = tint,
            topLeft = Offset(272f * scaleX, 226f * scaleY),
            size = Size(15f * scaleX, 60f * scaleY)
        )
    }
}
