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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocTemplateItem
import com.example.model.DocumentEntity
import com.example.model.ScannerSettings
import com.example.ui.components.CameraFrameAnalysis
import com.example.ui.components.CameraPreview
import com.example.ui.components.FlashOption

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
    scannerSettings: ScannerSettings,
    lastBulkSavedDoc: DocumentEntity?,
    scanErrorMessage: String?,
    selectedTemplate: DocTemplateItem?,
    templates: List<DocTemplateItem>,
    onToggleTemplateModal: () -> Unit,
    isAutoMode: Boolean,
    capturedPages: List<android.graphics.Bitmap>,
    isProcessing: Boolean,
    isCountdownRunning: Boolean,
    countdownValue: Int,
    startCaptureProcess: () -> Unit,
    onClearPages: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onFinishScan: () -> Unit
) {
    val context = LocalContext.current
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
                onFrameAnalysis = onFrameAnalysis,
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
        // Overlay: Dokumenten-Rahmen und Laser
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) {
            val padH = size.width * 0.08f
            val padV = size.height * 0.12f
            val boxWidth = size.width - (padH * 2)
            val boxHeight = size.height - (padV * 2) - 40f
            val bracketLen = 42f
            val strokeW = 4.5f
            val cornerColor = Color(0xFF38BDF8)
            // 4 Eck-Klammern (je 2 Schenkel)
            drawLine(cornerColor, Offset(padH, padV), Offset(padH + bracketLen, padV), strokeW)
            drawLine(cornerColor, Offset(padH, padV), Offset(padH, padV + bracketLen), strokeW)
            drawLine(cornerColor, Offset(padH + boxWidth, padV), Offset(padH + boxWidth - bracketLen, padV), strokeW)
            drawLine(cornerColor, Offset(padH + boxWidth, padV), Offset(padH + boxWidth, padV + bracketLen), strokeW)
            drawLine(cornerColor, Offset(padH, padV + boxHeight), Offset(padH + bracketLen, padV + boxHeight), strokeW)
            drawLine(cornerColor, Offset(padH, padV + boxHeight), Offset(padH, padV + boxHeight - bracketLen), strokeW)
            drawLine(cornerColor, Offset(padH + boxWidth, padV + boxHeight), Offset(padH + boxWidth - bracketLen, padV + boxHeight), strokeW)
            drawLine(cornerColor, Offset(padH + boxWidth, padV + boxHeight), Offset(padH + boxWidth, padV + boxHeight - bracketLen), strokeW)
            // Laser
            val currentLaserY = padV + (boxHeight * laserProgress)
            drawLine(
                color = Color(0xFF06B6D4).copy(alpha = 0.85f),
                start = Offset(padH + 4f, currentLaserY),
                end = Offset(padH + boxWidth - 4f, currentLaserY),
                strokeWidth = 3f
            )
            // Wasserwaage / Bullseye
            val centerX = size.width / 2f
            val centerY = padV + (boxHeight / 2f)
            val outerRadius = 42f
            val targetRadius = 15f
            val waageColor = if (isPhoneInWaage) Color(0xFF10B981) else Color(0xFFF59E0B)
            drawCircle(
                color = waageColor.copy(alpha = 0.5f),
                radius = outerRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.5f)
            )
            drawCircle(
                color = waageColor.copy(alpha = 0.85f),
                radius = targetRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.5f)
            )
            drawLine(
                color = waageColor.copy(alpha = 0.4f),
                start = Offset(centerX - outerRadius - 12f, centerY),
                end = Offset(centerX + outerRadius + 12f, centerY),
                strokeWidth = 2f
            )
            drawLine(
                color = waageColor.copy(alpha = 0.4f),
                start = Offset(centerX, centerY - outerRadius - 12f),
                end = Offset(centerX, centerY + outerRadius + 12f),
                strokeWidth = 2f
            )
            val maxOffset = outerRadius - 8f
            val bubbleX = (sensorTiltX * 14f).coerceIn(-maxOffset, maxOffset)
            val bubbleY = (sensorTiltY * 14f).coerceIn(-maxOffset, maxOffset)
            drawCircle(
                color = if (isPhoneInWaage) Color(0xFF10B981) else Color(0xFFFBBF24),
                radius = 8f,
                center = Offset(centerX + bubbleX, centerY + bubbleY)
            )
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
                            text = "PDF wird erstellt & archiviert...",
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
                                text = "⚡ Dokument erkannt • Scannt automatisch...",
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
