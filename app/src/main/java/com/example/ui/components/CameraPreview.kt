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
import java.nio.ByteBuffer

enum class FlashOption(val label: String) {
    OFF("Blitz aus"),
    FLASH("Blitz an"),
    TORCH("Dauerlicht")
}

data class CameraFrameAnalysis(
    val isDocumentDetected: Boolean = false,
    val motionScore: Float = 0f, // 0f = komplett still, > 15f = Bewegung / Blattwechsel
    val averageLuminance: Float = 128f,
    val sharpnessScore: Float = 0f
)

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    flashOption: FlashOption = FlashOption.OFF,
    onFrameAnalysis: (CameraFrameAnalysis) -> Unit = {},
    onImageCaptureCreated: (ImageCapture) -> Unit = {},
    onCameraAvailableChanged: (Boolean) -> Unit = {}
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
    var isCameraHardwareAvailable by remember { mutableStateOf<Boolean?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
    val previousSamplesRef = remember { arrayOfNulls<ByteArray>(1) }

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
                                val plane = imageProxy.planes.firstOrNull()
                                if (plane != null) {
                                    val buffer = plane.buffer
                                    val width = imageProxy.width
                                    val height = imageProxy.height
                                    val rowStride = plane.rowStride
                                    val pixelStride = plane.pixelStride

                                    // Schnelle 8x8-Abtastung. Ein Dokument muss eine große,
                                    // helle zusammenhängend wirkende Fläche mit Randkontrast haben.
                                    val gridSize = 8
                                    var innerSum = 0L
                                    var innerCount = 0
                                    var outerSum = 0L
                                    var outerCount = 0
                                    val innerLumas = mutableListOf<Int>()
                                    val currentSamples = ByteArray(gridSize * gridSize)
                                    var sampleIdx = 0

                                    for (gy in 1..gridSize) {
                                        val y = (height * gy / (gridSize + 1)).coerceIn(0, height - 1)
                                        val isEdgeY = (gy == 1 || gy == gridSize)
                                        for (gx in 1..gridSize) {
                                            val x = (width * gx / (gridSize + 1)).coerceIn(0, width - 1)
                                            val isEdgeX = (gx == 1 || gx == gridSize)
                                            val index = (y * rowStride) + (x * pixelStride)
                                            if (index in 0 until buffer.capacity()) {
                                                val luma = buffer.get(index).toInt() and 0xFF
                                                currentSamples[sampleIdx++] = luma.toByte()

                                                if (isEdgeX || isEdgeY) {
                                                    outerSum += luma
                                                    outerCount++
                                                } else {
                                                    innerSum += luma
                                                    innerCount++
                                                    innerLumas.add(luma)
                                                }
                                            }
                                        }
                                    }

                                    val safeInnerCount = innerCount.coerceAtLeast(1)
                                    val safeOuterCount = outerCount.coerceAtLeast(1)
                                    val avgInnerLuma = (innerSum / safeInnerCount).toFloat()
                                    val avgOuterLuma = (outerSum / safeOuterCount).toFloat()

                                    // Varianz im inneren Bereich (Dokument-Textur / Schriftzeilen)
                                    var varianceSum = 0.0
                                    for (luma in innerLumas) {
                                        val diff = luma - avgInnerLuma
                                        varianceSum += (diff * diff)
                                    }
                                    val innerStdDev = Math.sqrt(varianceSum / safeInnerCount).toFloat()
                                    val brightInnerRatio =
                                        innerLumas.count { it >= 135 }.toFloat() / safeInnerCount.toFloat()

                                    // Tatsächliche Bildschärfe aus lokalen Luma-Kanten messen.
                                    // Das ist unabhängig von der Neigung/Bewegung des Smartphones.
                                    val focusGridSize = 80
                                    var laplacianSum = 0L
                                    var laplacianCount = 0
                                    for (fy in 1..focusGridSize) {
                                        val y = (height * fy / (focusGridSize + 1)).coerceIn(1, height - 2)
                                        for (fx in 1..focusGridSize) {
                                            val x = (width * fx / (focusGridSize + 1)).coerceIn(1, width - 2)
                                            val centerIndex = y * rowStride + x * pixelStride
                                            val leftIndex = y * rowStride + (x - 1) * pixelStride
                                            val rightIndex = y * rowStride + (x + 1) * pixelStride
                                            val topIndex = (y - 1) * rowStride + x * pixelStride
                                            val bottomIndex = (y + 1) * rowStride + x * pixelStride
                                            if (centerIndex in 0 until buffer.capacity() &&
                                                leftIndex in 0 until buffer.capacity() &&
                                                rightIndex in 0 until buffer.capacity() &&
                                                topIndex in 0 until buffer.capacity() &&
                                                bottomIndex in 0 until buffer.capacity()
                                            ) {
                                                val center = buffer.get(centerIndex).toInt() and 0xFF
                                                val left = buffer.get(leftIndex).toInt() and 0xFF
                                                val right = buffer.get(rightIndex).toInt() and 0xFF
                                                val top = buffer.get(topIndex).toInt() and 0xFF
                                                val bottom = buffer.get(bottomIndex).toInt() and 0xFF
                                                laplacianSum += kotlin.math.abs(4 * center - left - right - top - bottom)
                                                laplacianCount++
                                            }
                                        }
                                    }
                                    val averageLaplacian = if (laplacianCount > 0) {
                                        laplacianSum.toFloat() / laplacianCount.toFloat()
                                    } else 0f
                                    val sharpnessScore = (averageLaplacian / 12f * 100f).coerceIn(0f, 100f)

                                    // Bewegung / Blattwechsel ermitteln
                                    val prev = previousSamplesRef[0]
                                    var motionDiff = 0f
                                    if (prev != null && prev.size == currentSamples.size) {
                                        var diffSum = 0f
                                        for (i in prev.indices) {
                                            val d = Math.abs((currentSamples[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
                                            diffSum += d
                                        }
                                        motionDiff = diffSum / prev.size
                                    }
                                    previousSamplesRef[0] = currentSamples

                                    // Pflanzen/Kerzen bestehen die alte Prüfung oft allein wegen Textur.
                                    // Papier muss den Innenbereich mehrheitlich aufhellen und zum Außenrand passen.
                                    val hasPaperContrast =
                                        (avgInnerLuma - avgOuterLuma) >= 10f &&
                                        brightInnerRatio >= 0.68f &&
                                        innerStdDev in 4f..58f
                                    val isDoc =
                                        avgInnerLuma in 125f..245f &&
                                        hasPaperContrast &&
                                        motionDiff < 6.0f &&
                                        sharpnessScore >= 18f
                                    mainHandler.post {
                                        onFrameAnalysis(
                                            CameraFrameAnalysis(
                                                isDocumentDetected = isDoc,
                                                motionScore = motionDiff,
                                                averageLuminance = avgInnerLuma,
                                                sharpnessScore = sharpnessScore
                                            )
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignorieren bei Puffer-Race-Conditions
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
