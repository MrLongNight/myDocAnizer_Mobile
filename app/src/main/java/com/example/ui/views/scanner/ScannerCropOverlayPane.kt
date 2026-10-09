package com.example.ui.views.scanner

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mini-Wasserwaage mit dynamischer Blasen-Animation statt reiner Grad-Zahlen.
 * Zeigt unmittelbar visuell an, ob das Smartphone waagerecht über dem Dokument liegt.
 */
@Composable
fun MiniSpiritLevel(
    sensorTiltX: Float,
    sensorTiltY: Float,
    isPhoneInWaage: Boolean,
    modifier: Modifier = Modifier
) {
    val animX by animateFloatAsState(
        targetValue = (sensorTiltX * 4f).coerceIn(-6f, 6f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "spirit_bubble_x"
    )
    val animY by animateFloatAsState(
        targetValue = (sensorTiltY * 4f).coerceIn(-6f, 6f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "spirit_bubble_y"
    )

    Box(
        modifier = modifier
            .size(20.dp)
            .testTag("mini_spirit_level"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.width / 2f - 1f
            val targetRadius = 4f
            val color = if (isPhoneInWaage) Color(0xFF10B981) else Color(0xFFF59E0B)

            // Äußerer Kreis
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.5f)
            )

            // Innerer Ziel-Kreis (Fokuspunkt)
            drawCircle(
                color = color.copy(alpha = 0.9f),
                radius = targetRadius,
                center = center,
                style = Stroke(width = 1.2f)
            )

            // Feine Fadenkreuz-Linien
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(center.x - outerRadius, center.y),
                end = Offset(center.x + outerRadius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = color.copy(alpha = 0.35f),
                start = Offset(center.x, center.y - outerRadius),
                end = Offset(center.x, center.y + outerRadius),
                strokeWidth = 1f
            )

            // Wasserwaagen-Blase (animiert)
            drawCircle(
                color = if (isPhoneInWaage) Color(0xFF34D399) else Color(0xFFFBBF24),
                radius = 2.8f,
                center = Offset(center.x + animX, center.y + animY)
            )
        }
    }
}

/**
 * Einziges zentrales, platzsparendes Status-Element:
 * 1. Mini-Waage (Animation)
 * 2. Fokus / Schärfe (Icon)
 * 3. Dokument / Kontrast (Icon)
 * 4. Ausleuchtung / Licht (Icon)
 * 5. Knackige, kurze Statusbezeichnung
 */
@Composable
fun CompactScanStatusBar(
    sensorTiltX: Float,
    sensorTiltY: Float,
    isPhoneInWaage: Boolean,
    isSharpnessOptimal: Boolean,
    isDocumentDetected: Boolean,
    hasGoodLight: Boolean,
    hasCapturedCurrentDoc: Boolean,
    isAutoScanning: Boolean,
    toolsInfoOnly: Boolean = false,
    onToggleInfoOnly: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isAllOptimal = isPhoneInWaage && isSharpnessOptimal && isDocumentDetected

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                hasCapturedCurrentDoc -> Color(0xFF10B981).copy(alpha = 0.8f)
                isAutoScanning -> Color(0xFF38BDF8).copy(alpha = 0.8f)
                toolsInfoOnly -> Color(0xFF38BDF8).copy(alpha = 0.6f)
                isAllOptimal -> Color(0xFF10B981).copy(alpha = 0.5f)
                else -> Color.White.copy(alpha = 0.15f)
            }
        ),
        modifier = modifier
            .clickable { onToggleInfoOnly() }
            .testTag("scan_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Mini-Waage mit Blasen-Animation statt Zahlen
            MiniSpiritLevel(
                sensorTiltX = sensorTiltX,
                sensorTiltY = sensorTiltY,
                isPhoneInWaage = isPhoneInWaage
            )

            // Vertikaler Trenner
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // 2. Schärfe / Erschütterung
            Icon(
                imageVector = Icons.Default.FilterCenterFocus,
                contentDescription = "Schärfe",
                tint = if (isSharpnessOptimal) Color(0xFF34D399) else Color(0xFFFBBF24),
                modifier = Modifier.size(15.dp)
            )

            // Vertikaler Trenner
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // 3. Dokumenterkennung / Kontrast
            Icon(
                imageVector = Icons.Default.CropFree,
                contentDescription = "Dokument",
                tint = if (isDocumentDetected) Color(0xFF38BDF8) else Color(0xFF64748B),
                modifier = Modifier.size(15.dp)
            )

            // Vertikaler Trenner
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // 4. Ausleuchtung / Licht
            Icon(
                imageVector = Icons.Default.LightMode,
                contentDescription = "Licht",
                tint = if (hasGoodLight) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                modifier = Modifier.size(15.dp)
            )

            // Vertikaler Trenner
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )

            // 5. Kurze, typische Statusbezeichnung
            val (statusText, statusColor) = when {
                hasCapturedCurrentDoc -> "✓ Erfasst" to Color(0xFF34D399)
                isAutoScanning -> "Scannt..." to Color(0xFF38BDF8)
                toolsInfoOnly -> "Info" to Color(0xFF38BDF8)
                !isPhoneInWaage -> "Ausrichten" to Color(0xFFFBBF24)
                !isSharpnessOptimal -> "Ruhig halten" to Color(0xFFFBBF24)
                !isDocumentDetected -> "Kein Dok." to Color(0xFF94A3B8)
                else -> "Bereit" to Color(0xFF34D399)
            }
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
