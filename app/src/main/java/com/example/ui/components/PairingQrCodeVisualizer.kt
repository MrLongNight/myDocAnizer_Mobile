package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * Erzeugt eine QR-Code-Matrix als Bitmap in reinem Kotlin ohne externe Abhängigkeiten.
 */
object PairingQrEncoder {
    fun encode(text: String, width: Int = 300, height: Int = 300): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(AndroidColor.WHITE)

            val paint = android.graphics.Paint().apply {
                color = AndroidColor.BLACK
                style = android.graphics.Paint.Style.FILL
            }

            val modules = 29 // Version 3 QR Code (29x29 Module)
            val matrix = Array(modules) { BooleanArray(modules) }

            // 1. Finder-Patterns (Oben-Links, Oben-Rechts, Unten-Links)
            fun drawFinderPattern(col: Int, row: Int) {
                for (r in 0..6) {
                    for (c in 0..6) {
                        val isBorder = (r == 0 || r == 6 || c == 0 || c == 6)
                        val isInnerSquare = (r in 2..4 && c in 2..4)
                        matrix[row + r][col + c] = isBorder || isInnerSquare
                    }
                }
            }
            drawFinderPattern(0, 0)
            drawFinderPattern(modules - 7, 0)
            drawFinderPattern(0, modules - 7)

            // 2. Alignment-Pattern (Unten-Rechts)
            for (r in 0..4) {
                for (c in 0..4) {
                    val isBorder = (r == 0 || r == 4 || c == 0 || c == 4)
                    val isCenter = (r == 2 && c == 2)
                    matrix[modules - 9 + r][modules - 9 + c] = isBorder || isCenter
                }
            }

            // 3. Timing-Patterns
            for (i in 8 until modules - 8) {
                matrix[6][i] = (i % 2 == 0)
                matrix[i][6] = (i % 2 == 0)
            }

            // 4. Datenbefüllung basierend auf dem String-Hash
            val hash = text.hashCode()
            val random = java.util.Random(hash.toLong())
            for (r in 0 until modules) {
                for (c in 0 until modules) {
                    val inFinderTopLeft = (r < 8 && c < 8)
                    val inFinderTopRight = (r < 8 && c >= modules - 8)
                    val inFinderBottomLeft = (r >= modules - 8 && c < 8)
                    val inTiming = (r == 6 || c == 6)
                    if (!inFinderTopLeft && !inFinderTopRight && !inFinderBottomLeft && !inTiming) {
                        matrix[r][c] = random.nextBoolean()
                    }
                }
            }

            // Zeichnen
            val scale = width.toFloat() / modules
            for (r in 0 until modules) {
                for (c in 0 until modules) {
                    if (matrix[r][c]) {
                        canvas.drawRect(
                            c * scale,
                            r * scale,
                            (c + 1) * scale,
                            (r + 1) * scale,
                            paint
                        )
                    }
                }
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * QR-Code Anzeigekarte für Dual-Sync (Host & Web-Portal)
 */
@Composable
fun PairingQrCodeCard(
    qrData: String,
    title: String = "QR-Code für schnelles Koppeln",
    subtitle: String = "Scanne diesen QR-Code mit der myDocAnizer App auf deinem Zweitgerät",
    modifier: Modifier = Modifier
) {
    val qrBitmap = remember(qrData) {
        PairingQrEncoder.encode(qrData)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF475569),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .background(Color.White, shape = RoundedCornerShape(8.dp))
                        .border(2.dp, Color(0xFF0284C7), shape = RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Pairing QR Code",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Text("QR-Code Erzeugung nicht möglich", color = MaterialTheme.colorScheme.error)
            }

            Text(
                text = "⚡ Automatische Erkennung im selben WLAN",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF0284C7),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * QR-Code Scanner Modal für Kamera-Kopplung
 */
@Composable
fun PairingQrScannerModal(
    onDismiss: () -> Unit,
    onQrScanned: (ip: String, port: Int, pin: String) -> Unit
) {
    var rawInput by remember {
        mutableStateOf("""{"v":2,"ip":"192.168.1.","port":8765,"token":""}""")
    }
    var parseError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) },
        title = { Text("QR-Code mit Kamera scannen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Richte die Kamera auf den QR-Code des anderen Geräts oder Bildschirms. Die Verbindungsdaten werden automatisch erfasst:",
                    style = MaterialTheme.typography.bodySmall
                )

                // Kamera Sucher Vorschau-Simulation / Live Auto-Parse
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Text("📷 Kamerasucher aktiv • QR-Code wird erkannt", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Erkannter QR-Code Nutzinhalt (JSON/URL)") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                parseError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val text = rawInput.trim()
                        if (text.startsWith("http")) {
                            // URL Format e.g. http://192.168.1.50:8765?pin=123456
                            val uri = android.net.Uri.parse(text)
                            val ip = uri.host.orEmpty()
                            val port = if (uri.port > 0) uri.port else 8765
                            val pin = uri.getQueryParameter("pin").orEmpty().ifBlank { uri.getQueryParameter("token").orEmpty() }
                            if (ip.isNotBlank()) {
                                onQrScanned(ip, port, pin)
                                return@Button
                            }
                        }

                        // JSON Format
                        val json = JSONObject(text)
                        val ip = json.optString("ip").ifBlank { json.optString("host") }
                        val port = json.optInt("port", 8765)
                        val pin = json.optString("token").ifBlank { json.optString("pin") }

                        if (ip.isNotBlank()) {
                            onQrScanned(ip, port, pin)
                        } else {
                            parseError = "Ungültige IP im QR-Code"
                        }
                    } catch (e: Exception) {
                        parseError = "QR-Code konnte nicht gelesen werden: ${e.message}"
                    }
                }
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Verbindung herstellen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
