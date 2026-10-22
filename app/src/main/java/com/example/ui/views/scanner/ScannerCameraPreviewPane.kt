package com.example.ui.views.scanner

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocTypeItem
import com.example.model.DocumentEntity
import com.example.model.ScannerSettings
import com.example.model.TemplateItem
import com.example.ui.components.CameraFrameAnalysis
import com.example.ui.components.CameraPreview
import com.example.ui.components.FlashOption
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.ui.components.TourElementBounds
import com.example.ui.components.TourShapeType

@Composable
fun ScannerCameraPreviewPane(
    modifier: Modifier = Modifier,
    hasCameraPermission: Boolean,
    flashOption: FlashOption,
    onFrameAnalysis: (CameraFrameAnalysis) -> Unit,
    onImageCaptureCreated: (ImageCapture) -> Unit,
    onCameraAvailableChanged: (Boolean) -> Unit,
    onPermissionRequest: () -> Unit,
    onNavigateBack: () -> Unit,
    laserProgress: Float,
    isPhoneInWaage: Boolean,
    sensorTiltX: Float,
    sensorTiltY: Float,
    isQualityCheckModeActive: Boolean,
    onToggleQualityCheck: () -> Unit,
    onImportGallery: () -> Unit,
    hasCapturedCurrentDocument: Boolean,
    onResetCapturedDocument: () -> Unit,
    autoDetectProgress: Float,
    focusRequestToken: Int = 0,
    preScanSecondsRemaining: Int = 0,
    postScanSecondsRemaining: Int = 0,
    scannerSettings: ScannerSettings,
    lastBulkSavedDoc: DocumentEntity?,
    scanErrorMessage: String?,
    selectedTemplate: TemplateItem?,
    templates: List<TemplateItem>,
    onToggleTemplateModal: () -> Unit,
    isAutoMode: Boolean,
    capturedPages: List<android.graphics.Bitmap>,
    isProcessing: Boolean,
    isCountdownRunning: Boolean,
    countdownValue: Int,
    startCaptureProcess: () -> Unit,
    onClearPages: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onFinishScan: () -> Unit,
    onReportElementBounds: ((step: Int, bounds: TourElementBounds) -> Unit)? = null
) {
    val context = LocalContext.current
    var isDocumentDetected by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("scanner_view_container")
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp)
                    .clickable {
                        if (hasCapturedCurrentDocument) {
                            onResetCapturedDocument()
                        }
                    }
                    .testTag("camera_preview_surface"),
                flashOption = flashOption,
                onFrameAnalysis = { analysis ->
                    isDocumentDetected = analysis.isDocumentDetected
                    onFrameAnalysis(analysis)
                },
                onImageCaptureCreated = onImageCaptureCreated,
                onCameraAvailableChanged = onCameraAvailableChanged
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp)
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Kamerazugriff erforderlich",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "myDocAnizer benötigt Zugriff auf die Kamera, um Dokumente, Belege und Akten einzuscannen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onPermissionRequest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kamera-Berechtigung erteilen")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.fromParts("package", context.packageName, null)
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("ScannerCameraPreviewPane", "Einstellungen konnten nicht geöffnet werden: ${e.message}")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("In Android-Einstellungen erlauben")
                    }
                }
            }
        }
        // Live-Quad: previewQuad ist bereits in PreviewView-Koordinaten (Rotation + FILL_CENTER).
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) {
            val quad = frameAnalysis.previewQuad
            val hasQuad = quad != null && quad.size == 4
            val scanStarting = isDocumentDetected && frameAnalysis.isDocumentStable && autoDetectProgress > 0f
            val quadColor = when {
                scanStarting -> Color(0xFF10B981)
                isDocumentDetected || hasQuad -> Color(0xFF38BDF8)
                else -> Color(0xFF94A3B8)
            }
            if (hasQuad) {
                val points = quad!!
                val quadPath = Path().apply {
                    moveTo(points[0].x * size.width, points[0].y * size.height)
                    for (i in 1..3) lineTo(points[i].x * size.width, points[i].y * size.height)
                    close()
                }
                drawPath(quadPath, quadColor.copy(alpha = if (scanStarting) 0.12f else 0.06f))
                drawPath(quadPath, quadColor.copy(alpha = 0.28f), style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(quadPath, quadColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                points.forEach { point ->
                    drawCircle(quadColor, radius = 5f, center = Offset(point.x * size.width, point.y * size.height))
                }
            } else {
                // Gestrichelte Platzierungshilfe nur solange kein Quad vorliegt.
                val padH = size.width * 0.08f
                val padV = size.height * 0.12f
                val boxWidth = size.width - 2f * padH
                val boxHeight = size.height - 2f * padV - 40f
                val guide = Path().apply {
                    moveTo(padH, padV)
                    lineTo(padH + boxWidth, padV)
                    lineTo(padH + boxWidth, padV + boxHeight)
                    lineTo(padH, padV + boxHeight)
                    close()
                }
                drawPath(guide, Color(0xFF94A3B8), style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))))
            }

            // Wasserwaage bleibt eine Orientierungshilfe und blockiert den Handscan nicht.
            val centerX = size.width / 2f
            val centerY = size.height * 0.52f
            val outerRadius = 42f
            val targetRadius = 15f
            val waageColor = if (isPhoneInWaage) Color(0xFF10B981) else Color(0xFFF59E0B)
            drawCircle(waageColor.copy(alpha = 0.5f), outerRadius, Offset(centerX, centerY), style = Stroke(width = 2.5f))
            drawCircle(waageColor.copy(alpha = 0.85f), targetRadius, Offset(centerX, centerY), style = Stroke(width = 2.5f))
            drawLine(waageColor.copy(alpha = 0.4f), Offset(centerX - outerRadius - 12f, centerY), Offset(centerX + outerRadius + 12f, centerY), strokeWidth = 2f)
            drawLine(waageColor.copy(alpha = 0.4f), Offset(centerX, centerY - outerRadius - 12f), Offset(centerX, centerY + outerRadius + 12f), strokeWidth = 2f)
            val maxOffset = outerRadius - 8f
            drawCircle(
                if (isPhoneInWaage) Color(0xFF10B981) else Color(0xFFFBBF24),
                radius = 8f,
                center = Offset(
                    centerX + (sensorTiltX * 14f).coerceIn(-maxOffset, maxOffset),
                    centerY + (sensorTiltY * 14f).coerceIn(-maxOffset, maxOffset)
                )
            )
        }
        if (scannerSettings.debugDocumentDetection) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 66.dp),
                color = Color(0xDD0F172A),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "conf " + frameAnalysis.documentConfidence + " • reject " + (frameAnalysis.rejectReason ?: "–") +
                        "\nsharp " + frameAnalysis.sharpnessScore + " • motion " + frameAnalysis.motionScore,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
        }
        // OBERE LEISTE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onNavigateBack,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color(0xFF0F172A).copy(alpha = 0.7f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(38.dp)
                    .testTag("btn_scanner_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zurück zum Dokumenten Tresor",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.7f)
            ) {
                Text(
                    text = "Dokumenten Scan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalIconButton(
                    onClick = onToggleQualityCheck,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isQualityCheckModeActive) MaterialTheme.colorScheme.primary else Color(0xFF0F172A).copy(alpha = 0.7f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("btn_toggle_quality_check")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Aufnahme-Qualitäts-Check vor dem Speichern",
                        tint = if (isQualityCheckModeActive) Color.White else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = onImportGallery,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Color(0xFF0F172A).copy(alpha = 0.7f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("btn_import_gallery_quick")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Dokument aus Galerie importieren",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        // Schnell Bulk-Erfolgs-Overlay & Fehler-Banner
        AnimatedVisibility(
            visible = lastBulkSavedDoc != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF065F46).copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "✓ Gespeichert: ${lastBulkSavedDoc?.fileName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "In '${lastBulkSavedDoc?.mainCategory} ➔ ${lastBulkSavedDoc?.subCategory}' • Nächstes Dokument auflegen",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA7F3D0)
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = scanErrorMessage != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF7F1D1D).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFCA5A5), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = scanErrorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
        // UNTERES BEDIENFELD
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleTemplateModal() }
                        .testTag("template_selector_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val prefixStr = if (selectedTemplate?.mainCategoryId?.isNotBlank() == true) {
                                "${selectedTemplate?.mainCategoryId}_${selectedTemplate?.subCategoryId}- "
                            } else ""
                            val templateLabel = if (selectedTemplate != null)
                                "$prefixStr${selectedTemplate?.mainCategory} ➔ ${selectedTemplate?.subCategory}"
                            else
                                "Zielordner / Vorlage wählen"
                            Text(
                                text = templateLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = "Wechseln",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (!isAutoMode && capturedPages.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${capturedPages.size} Seite(n) erfasst",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = onClearPages,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("Verwerfen", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        itemsIndexed(capturedPages) { index, _ ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Seite ${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onRemovePage(index) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Entfernen", modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (isProcessing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dokument wird verarbeitet...",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (hasCapturedCurrentDocument) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF065F46).copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onResetCapturedDocument()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "✓ Blatt erfasst • Neues Blatt auflegen oder weiter scannen",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else if (preScanSecondsRemaining > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⏱️ Stativ-Vorlauf: Dokument-Erkennung startet in ${preScanSecondsRemaining}s...",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                } else if (postScanSecondsRemaining > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🔄 Pausenzeit für Blattwechsel (${postScanSecondsRemaining}s)...",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                } else if (autoDetectProgress > 0f) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { autoDetectProgress },
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚡ Dokument erkannt • Scannt...",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                if (capturedPages.isEmpty()) {
                    Button(
                        onClick = startCaptureProcess,
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(21.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(44.dp)
                            .onGloballyPositioned { coords ->
                                val bounds = coords.boundsInRoot()
                                onReportElementBounds?.invoke(
                                    4,
                                    TourElementBounds(
                                        centerX = bounds.center.x,
                                        centerY = bounds.center.y,
                                        widthPx = bounds.width,
                                        heightPx = bounds.height,
                                        shapeType = TourShapeType.ROUNDED_RECT
                                    )
                                )
                            }
                            .testTag("btn_capture_page")
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProcessing) "Wird verarbeitet..." else if (isCountdownRunning) "Selbstauslöser ($countdownValue)..." else "Dokument scannen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(44.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = startCaptureProcess,
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(21.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("btn_capture_page")
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Seite", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = onFinishScan,
                            enabled = capturedPages.isNotEmpty() && !isProcessing,
                            shape = RoundedCornerShape(21.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                                .testTag("btn_finish_scan")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF (${capturedPages.size} S.) fertig", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
