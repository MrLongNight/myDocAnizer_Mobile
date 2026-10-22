package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.DocumentQuadDetector
import com.example.service.DocumentTracker
import com.example.service.LumaChromaFrame
import com.example.service.toLumaChromaFrame
import java.nio.ByteBuffer

enum class FlashOption(val label: String) {
    OFF("Blitz aus"),
    FLASH("Blitz an"),
    TORCH("Dauerlicht")
}

data class CameraFrameAnalysis(
    val isDocumentDetected: Boolean = false,
    // Motion scale: 0 = no corner movement; 3.0 = average corners moved ~3% of image diagonal.
    val motionScore: Float = 0f,
    val averageLuminance: Float = 128f,
    val sharpnessScore: Float = 0f,
    val documentQuad: List<android.graphics.PointF>? = null, // normalized to source ImageProxy buffer, TL/TR/BR/BL
    val documentConfidence: Float = 0f,
    val isDocumentStable: Boolean = false,
    val rejectReason: String? = null,
    val rotationDegrees: Int = 0,
    val sourceWidth: Int = 0,
    val sourceHeight: Int = 0,
    val sourceCropRect: android.graphics.Rect? = null,
    val previewQuad: List<android.graphics.PointF>? = null // normiert in PreviewView-Koordinaten nach Rotation und FILL_CENTER
)

private data class FrameQuality(val averageLuminance: Float, val sharpnessScore: Float)

private fun measureFrameQuality(
    frame: com.example.service.LumaChromaFrame,
    quad: List<android.graphics.PointF>?
): FrameQuality {
    val width = frame.width
    val height = frame.height
    var luminanceSum = 0L
    var laplacianSum = 0L
    var count = 0
    for (y in 1 until height - 1) {
        for (x in 1 until width - 1) {
            val nx = (x + 0.5f) / width
            val ny = (y + 0.5f) / height
            val inRegion = if (quad != null && quad.size == 4) {
                pointInsideQuad(nx, ny, quad)
            } else {
                nx in 0.20f..0.80f && ny in 0.20f..0.80f
            }
            if (!inRegion) continue
            val i = y * width + x
            val center = frame.y[i].toInt() and 0xff
            val left = frame.y[i - 1].toInt() and 0xff
            val right = frame.y[i + 1].toInt() and 0xff
            val top = frame.y[i - width].toInt() and 0xff
            val bottom = frame.y[i + width].toInt() and 0xff
            luminanceSum += center
            laplacianSum += kotlin.math.abs(4 * center - left - right - top - bottom).toLong()
            count++
        }
    }
    if (count == 0) return FrameQuality(128f, 0f)
    val average = luminanceSum.toFloat() / count
    val averageLaplacian = laplacianSum.toFloat() / count
    return FrameQuality(average, (averageLaplacian / 12f * 100f).coerceIn(0f, 100f))
}

private fun pointInsideQuad(x: Float, y: Float, quad: List<android.graphics.PointF>): Boolean {
    var inside = false
    var j = quad.lastIndex
    for (i in quad.indices) {
        val a = quad[i]
        val b = quad[j]
        if ((a.y > y) != (b.y > y) &&
            x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x
        ) inside = !inside
        j = i
    }
    return inside
}

private fun normalizedToPreviewPoint(x: Float, y: Float, rotationDegrees: Int): Pair<Float, Float> =
    when (((rotationDegrees % 360) + 360) % 360) {
        90 -> (1f - y) to x
        180 -> (1f - x) to (1f - y)
        270 -> y to (1f - x)
        else -> x to y
    }

private fun mapQuadToPreview(
    quad: List<android.graphics.PointF>?, rotationDegrees: Int,
    sourceWidth: Int, sourceHeight: Int, previewWidth: Int, previewHeight: Int
): List<android.graphics.PointF>? {
    if (quad == null || quad.size != 4 || sourceWidth <= 0 || sourceHeight <= 0 || previewWidth <= 0 || previewHeight <= 0) return null
    val rotation = ((rotationDegrees % 360) + 360) % 360
    val rotatedAspect = if (rotation == 90 || rotation == 270) sourceHeight.toFloat() / sourceWidth else sourceWidth.toFloat() / sourceHeight
    val previewAspect = previewWidth.toFloat() / previewHeight
    return quad.map { point ->
        val (rx, ry) = normalizedToPreviewPoint(point.x, point.y, rotation)
        val mapped = if (rotatedAspect > previewAspect) {
            val visibleWidth = (previewAspect / rotatedAspect).coerceIn(0f, 1f)
            val cropLeft = (1f - visibleWidth) / 2f
            ((rx - cropLeft) / visibleWidth) to ry
        } else {
            val visibleHeight = (rotatedAspect / previewAspect).coerceIn(0f, 1f)
            val cropTop = (1f - visibleHeight) / 2f
            rx to ((ry - cropTop) / visibleHeight)
        }
        android.graphics.PointF(mapped.first, mapped.second)
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    flashOption: FlashOption = FlashOption.OFF,
    onFrameAnalysis: (CameraFrameAnalysis) -> Unit = {},
    onImageCaptureCreated: (ImageCapture) -> Unit = {},
    onCameraAvailableChanged: (Boolean) -> Unit = {},
    focusRequestToken: Int = 0
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val highResSelector = remember {
        androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
            .setResolutionStrategy(
                androidx.camera.core.resolutionselector.ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
            )
            .setAspectRatioStrategy(
                androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
            )
            .build()
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(highResSelector)
            .setJpegQuality(100)
            .build()
    }

    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var lastFocusPoint by remember { mutableStateOf(0.5f to 0.5f) }
    var isCameraHardwareAvailable by remember { mutableStateOf<Boolean?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(focusRequestToken, cameraControl) {
        if (focusRequestToken > 0) {
            val previewView = previewViewRef
            val (x, y) = lastFocusPoint
            if (previewView != null && previewView.width > 0 && previewView.height > 0) {
                try {
                    val point = previewView.meteringPointFactory.createPoint(
                        (x * previewView.width).coerceIn(0f, previewView.width.toFloat()),
                        (y * previewView.height).coerceIn(0f, previewView.height.toFloat())
                    )
                    val action = androidx.camera.core.FocusMeteringAction.Builder(
                        point,
                        androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE
                    ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                    cameraControl?.startFocusAndMetering(action)
                } catch (_: Exception) {
                    // Camera can detach while an automatic focus request is pending.
                }
            }
        }
    }

    LaunchedEffect(flashOption, cameraControl, imageCapture) {
        try {
            when (flashOption) {
                FlashOption.TORCH -> {
                    cameraControl?.enableTorch(true)
                    imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
                }
                FlashOption.FLASH -> {
                    cameraControl?.enableTorch(false)
                    imageCapture.flashMode = ImageCapture.FLASH_MODE_ON
                }
                FlashOption.OFF -> {
                    cameraControl?.enableTorch(false)
                    imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
                }
            }
        } catch (e: Exception) {
            Log.e("CameraPreview", "Fehler bei Blitzeinstellung (${flashOption.name}): ${e.message}")
        }
    }

    val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val detector = remember { DocumentQuadDetector() }
    val documentTracker = remember { DocumentTracker() }
    val frameBuffer = remember { arrayOfNulls<LumaChromaFrame>(1) }
    val lastAnalyzedAt = remember { longArrayOf(0L) }
    val lastAutoFocusAt = remember { longArrayOf(0L) }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraControl?.enableTorch(false)
                ProcessCameraProvider.getInstance(context).get().unbindAll()
                analysisExecutor.shutdown()
            } catch (_: Exception) {
                // Ignore during teardown
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    setOnTouchListener { view, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            try {
                                val factory = meteringPointFactory
                                val point = factory.createPoint(event.x, event.y)
                                val action = androidx.camera.core.FocusMeteringAction.Builder(point, androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE)
                                    .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                cameraControl?.startFocusAndMetering(action)
                                view.performClick()
                            } catch (e: Exception) {
                                Log.e("CameraPreview", "Tap to focus error: ${e.message}")
                            }
                        }
                        true
                    }
                }

                previewViewRef = previewView

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val previewResolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                androidx.camera.core.resolutionselector.ResolutionStrategy(
                                    android.util.Size(1920, 1080),
                                    androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()

                        val preview = Preview.Builder()
                            .setResolutionSelector(previewResolutionSelector)
                            .build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                        val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                // Analyze at ~9 fps. ImageAnalysis uses KEEP_ONLY_LATEST, so skipped
                                // frames are cheap and never build up a queue.
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastAnalyzedAt[0] < 110L) return@setAnalyzer
                                lastAnalyzedAt[0] = now

                                val frame = imageProxy.toLumaChromaFrame(
                                    reuse = frameBuffer[0],
                                    targetLongEdge = 160
                                )
                                frameBuffer[0] = frame
                                val detection = detector.detect(frame)
                                val tracking = documentTracker.update(detection, now)
                                val quad = tracking.quadSmoothed
                                val quality = measureFrameQuality(frame, quad)

                                // Focus only when a temporal lock is newly acquired, and throttle
                                // requests so AF/AE does not restart continuously.
                                if (tracking.lockJustAcquired && now - lastAutoFocusAt[0] >= 1500L) {
                                    lastAutoFocusAt[0] = now
                                    val centerX = quad?.map { it.x }?.average()?.toFloat() ?: 0.5f
                                    val centerY = quad?.map { it.y }?.average()?.toFloat() ?: 0.5f
                                    val focusQuad = mapQuadToPreview(
                                        quad?.map { android.graphics.PointF(it.x, it.y) },
                                        imageProxy.imageInfo.rotationDegrees,
                                        imageProxy.width, imageProxy.height,
                                        previewView.width, previewView.height
                                    )
                                    val focusCenter = focusQuad?.let { points ->
                                        points.map { it.x }.average().toFloat() to points.map { it.y }.average().toFloat()
                                    } ?: normalizedToPreviewPoint(centerX, centerY, imageProxy.imageInfo.rotationDegrees)
                                    val (previewX, previewY) = focusCenter
                                    mainHandler.post {
                                        lastFocusPoint = focusCenter
                                        try {
                                            if (previewView.width > 0 && previewView.height > 0) {
                                                val point = previewView.meteringPointFactory.createPoint(
                                                    (previewX * previewView.width).coerceIn(0f, previewView.width.toFloat()),
                                                    (previewY * previewView.height).coerceIn(0f, previewView.height.toFloat())
                                                )
                                                val action = androidx.camera.core.FocusMeteringAction.Builder(
                                                    point,
                                                    androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE
                                                ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                                                cameraControl?.startFocusAndMetering(action)
                                            }
                                        } catch (_: Exception) {
                                            // Camera may be detaching during navigation.
                                        }
                                    }
                                }

                                val result = CameraFrameAnalysis(
                                    isDocumentDetected = tracking.isLocked,
                                    motionScore = tracking.cornerMotion * 100f,
                                    averageLuminance = quality.averageLuminance,
                                    sharpnessScore = quality.sharpnessScore,
                                    documentQuad = quad?.map { android.graphics.PointF(it.x, it.y) },
                                    documentConfidence = detection.confidence,
                                    isDocumentStable = tracking.isStable,
                                    rejectReason = if (tracking.isLocked) null else if (detection.quad != null) {
                                        "Dokument wird stabilisiert"
                                    } else detection.rejectReason,
                                    rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                                    sourceWidth = imageProxy.width,
                                    sourceHeight = imageProxy.height,
                                    sourceCropRect = android.graphics.Rect(imageProxy.cropRect),
                                    previewQuad = mapQuadToPreview(
                                        quad?.map { android.graphics.PointF(it.x, it.y) },
                                        imageProxy.imageInfo.rotationDegrees,
                                        imageProxy.width, imageProxy.height,
                                        previewView.width, previewView.height
                                    )
                                )
                                mainHandler.post { onFrameAnalysis(result) }
                            } catch (e: Exception) {
                                Log.w("CameraPreview", "Frame-Analyse fehlgeschlagen: ${e.message}")
                            } finally {
                                imageProxy.close()
                            }
                        }

                        val selector = when {
                            cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                            cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                            else -> null
                        }

                        if (selector != null) {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                selector,
                                preview,
                                imageCapture,
                                imageAnalysis
                            )
                            cameraControl = camera.cameraControl
                            isCameraHardwareAvailable = true
                            onCameraAvailableChanged(true)
                            onImageCaptureCreated(imageCapture)
                        } else {
                            isCameraHardwareAvailable = false
                            errorMessage = "Kein Kameragerät gefunden (Emulator-Modus aktiv)"
                            onCameraAvailableChanged(false)
                        }
                    } catch (exc: Exception) {
                        Log.e("CameraPreview", "Camera bind failed: ${exc.message}", exc)
                        isCameraHardwareAvailable = false
                        errorMessage = "Kamera-Initialisierung fehlgeschlagen (${exc.localizedMessage})"
                        onCameraAvailableChanged(false)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Falls die Kamera im Emulator oder Test-Device nicht existiert/blockiert ist:
        if (isCameraHardwareAvailable == false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Kamera-Echtzeitfeed nicht verfügbar",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Im Browser-Emulator oder ohne physische Kamera wird beim Klick auf 'Seite scannen' automatisch ein realistisches Testdokument für deine Vorlage erzeugt oder du kannst direkt ein Dokumentbild importieren.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Wandelt ImageProxy sicher in ein korrekt ausgerichtetes Bitmap um.
 * Nutzt primär die native CameraX image.toBitmap() Implementierung (unterstützt YUV, RGBA, JPEG).
 * Behält maximale 4K UHD Schärfe (bis 3840px) für gestochen scharfe Dokumentenlesbarkeit bei.
 */
fun imageProxyToBitmap(image: ImageProxy, maxDimension: Int = 3840): Bitmap? {
    return try {
        val originalBitmap = image.toBitmap()
        val rotationDegrees = image.imageInfo.rotationDegrees
        val rotated = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rot = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
            if (rot != originalBitmap) originalBitmap.recycle()
            rot
        } else {
            originalBitmap
        }

        val maxDim = maxOf(rotated.width, rotated.height)
        if (maxDim > maxDimension) {
            val scale = maxDimension.toFloat() / maxDim
            val targetW = (rotated.width * scale).toInt()
            val targetH = (rotated.height * scale).toInt()
            val scaled = Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
            if (scaled != rotated) rotated.recycle()
            scaled
        } else {
            rotated
        }
    } catch (e: Throwable) {
        Log.e("CameraPreview", "image.toBitmap() fehlgeschlagen, versuche direkten Byte-Puffer: ${e.message}")
        try {
            val plane = image.planes.firstOrNull() ?: return null
            val buffer: ByteBuffer = plane.buffer
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

            val rotationDegrees = image.imageInfo.rotationDegrees
            val rotated = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rot = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                if (rot != originalBitmap) originalBitmap.recycle()
                rot
            } else {
                originalBitmap
            }

            val maxDim = maxOf(rotated.width, rotated.height)
            if (maxDim > maxDimension) {
                val scale = maxDimension.toFloat() / maxDim
                val targetW = (rotated.width * scale).toInt()
                val targetH = (rotated.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
                if (scaled != rotated) rotated.recycle()
                scaled
            } else {
                rotated
            }
        } catch (e2: Throwable) {
            Log.e("CameraPreview", "Fehler bei Bildumwandlung: ${e2.message}")
            null
        }
    }
}
