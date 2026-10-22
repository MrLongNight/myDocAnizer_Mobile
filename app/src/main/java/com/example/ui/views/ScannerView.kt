package com.example.ui.views

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.example.model.DocTypeItem
import com.example.model.DocumentEntity
import com.example.model.ScannerSettings
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.CameraPreview
import com.example.ui.components.CameraFrameAnalysis
import com.example.ui.components.FlashOption
import com.example.ui.components.TemplateSelectModal
import com.example.ui.components.imageProxyToBitmap
import com.example.ui.views.scanner.CompactScanStatusBar
import com.example.ui.views.scanner.MiniSpiritLevel
import com.example.ui.views.scanner.ScannerCameraPreviewPane
import com.example.ui.views.scanner.ScannerPageReviewPane
import java.util.Locale
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
private fun quadMovedBeyondThreshold(reference: List<android.graphics.PointF>?, current: List<android.graphics.PointF>?): Boolean {
    if (reference == null || reference.size != 4 || current == null || current.size != 4) return false
    val meanCornerShift = reference.indices.map { i ->
        val dx = reference[i].x - current[i].x
        val dy = reference[i].y - current[i].y
        sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }.average().toFloat()
    // Normalized coordinates: image diagonal is sqrt(2), so 15% is about 0.212.
    return meanCornerShift > 0.212f
}

@Composable
fun ScannerView(
    viewModel: DocAnizerViewModel,
    onDocumentScanned: (DocumentEntity) -> Unit,
    onNavigateToBatchInbox: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onReportElementBounds: ((step: Int, bounds: com.example.ui.components.TourElementBounds) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedTemplate by viewModel.selectedTemplate.collectAsStateWithLifecycle()
    val isColorMode by viewModel.isColorMode.collectAsStateWithLifecycle()
    val capturedPages by viewModel.capturedPages.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingScan.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val docTypes by viewModel.docTypes.collectAsStateWithLifecycle()
    val scannerSettings by viewModel.scannerSettings.collectAsStateWithLifecycle()
    val lastBulkSavedDoc by viewModel.lastBulkSavedDoc.collectAsStateWithLifecycle()
    val batchQueue by viewModel.batchQueue.collectAsStateWithLifecycle()
    val isBatchModeActive by viewModel.isBatchModeActive.collectAsStateWithLifecycle()
    val pendingRuleSuggestion by viewModel.pendingRuleSuggestion.collectAsStateWithLifecycle()

    var showTemplateModal by remember { mutableStateOf(false) }
    var flashOption by remember(scannerSettings.flashMode) {
        mutableStateOf(
            when (scannerSettings.flashMode) {
                "FLASH" -> FlashOption.FLASH
                "TORCH" -> FlashOption.TORCH
                else -> FlashOption.OFF
            }
        )
    }
    var frameAnalysis by remember { mutableStateOf(CameraFrameAnalysis()) }
    var imageCaptureInstance by remember { mutableStateOf<ImageCapture?>(null) }
    var isCameraHardwareAvailable by remember { mutableStateOf(true) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = showTemplateModal) {
        showTemplateModal = false
    }

    BackHandler(enabled = !showTemplateModal && capturedPages.isNotEmpty()) {
        viewModel.clearPages()
    }

    LaunchedEffect(scanErrorMessage) {
        if (scanErrorMessage != null) {
            kotlinx.coroutines.delay(4000L)
            scanErrorMessage = null
        }
    }

    // Countdown Zustand für zeitverzögerten Selbstauslöser
    var countdownValue by remember { mutableStateOf(0) }
    var isCountdownRunning by remember { mutableStateOf(false) }

    // Auto-Scan Schutz: Stoppt sofort nach Erfassung eines Dokuments, bis ein neues Blatt aufgelegt wird
    var hasCapturedCurrentDocument by remember { mutableStateOf(false) }
    var motionDetectedAfterCapture by remember { mutableStateOf(false) }
    var autoDetectProgress by remember { mutableStateOf(0f) }
    var stableSinceMs by remember { mutableLongStateOf(0L) }
    var focusRequestToken by remember { mutableIntStateOf(0) }
    var autofocusRequestedForStableDocument by remember { mutableStateOf(false) }
    var capturedQuadAtScan by remember { mutableStateOf<List<android.graphics.PointF>?>(null) }

    // Sensoren für Scan-Qualitätstools (Wasserwaage, Schärfe, Kontrast, Ausleuchtung)
    var sensorTiltX by remember { mutableStateOf(0f) }
    var sensorTiltY by remember { mutableStateOf(0f) }
    var sensorTiltZ by remember { mutableStateOf(9.8f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    // Jitter-Filter gegen unnötige Dauer-Recompositions
                    if (kotlin.math.abs(x - sensorTiltX) > 0.06f ||
                        kotlin.math.abs(y - sensorTiltY) > 0.06f ||
                        kotlin.math.abs(z - sensorTiltZ) > 0.06f
                    ) {
                        sensorTiltX = x
                        sensorTiltY = y
                        sensorTiltZ = z
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    // 1. Wasserwaage / Telefon in Waage (Minimierung von Trapezverzerrungen)
    val xyMagnitude = sqrt((sensorTiltX * sensorTiltX + sensorTiltY * sensorTiltY).toDouble()).toFloat()
    val tiltAngleDegrees = (atan2(xyMagnitude.toDouble(), abs(sensorTiltZ).toDouble()) * (180.0 / Math.PI)).toFloat()
    val isPhoneInWaage = tiltAngleDegrees <= 3.2f

    // Schärfe ist nur ein weicher Qualitätsindikator: bei längerer Stabilität folgt ein AF-Versuch.
    val isSharpnessOptimal = frameAnalysis.sharpnessScore >= 26f

    LaunchedEffect(frameAnalysis.isDocumentDetected, frameAnalysis.isDocumentStable) {
        if (frameAnalysis.isDocumentDetected && frameAnalysis.isDocumentStable) {
            if (stableSinceMs == 0L) stableSinceMs = android.os.SystemClock.elapsedRealtime()
        } else {
            stableSinceMs = 0L
            if (!frameAnalysis.isDocumentDetected) autofocusRequestedForStableDocument = false
        }
    }

    val isAutoMode = scannerSettings.scanMode == "AUTO"

    // Vorab-Qualitätsprüfung & Ecken-Justierung
    var isQualityCheckModeActive by remember { mutableStateOf(false) }
    var pendingCheckBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Interaktive Dokumentenprüfung & Ecken-Justierung in einer einzigen Ansicht
    var capturedRawBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var preScanSecondsRemaining by remember { mutableIntStateOf(0) }
    var isPreScanActive by remember { mutableStateOf(false) }
    var postScanSecondsRemaining by remember { mutableIntStateOf(0) }

    // Funktion zum Ausführen des eigentlichen Bildabzugs
    val executeCapture: () -> Unit = {
        hasCapturedCurrentDocument = true
        motionDetectedAfterCapture = false
        autoDetectProgress = 0f

        val capture = imageCaptureInstance
        val onBmpReady: (android.graphics.Bitmap) -> Unit = { bmp ->
            hasCapturedCurrentDocument = true
            motionDetectedAfterCapture = false
            autoDetectProgress = 0f

            // Akustisches Feedback & optionale Vibration
            com.example.service.ScanFeedbackService.playCaptureSound(scannerSettings.soundProfile)
            com.example.service.ScanFeedbackService.triggerVibration(context, scannerSettings.enableVibration)

            if (isQualityCheckModeActive) {
                // Vorab-Qualitätsprüfung anzeigen
                pendingCheckBitmap = bmp
            } else if (scannerSettings.enableCropPreview && !isBatchModeActive) {
                // Do not start native LLM work before the user confirms the crop.
                // Classification runs once on the final bitmap, so it cannot block the
                // crop preview or reuse OCR text from the uncropped camera image.
                capturedRawBitmap = bmp
            } else {
                if (isBatchModeActive) {
                    // Neuer Stapel-Puffer Modus: Sofort in die Inbox (entkoppelt für schnellen Stativscan)
                    viewModel.enqueueBatchDocument(bmp)
                } else if (scannerSettings.isBulkMode || isAutoMode) {
                    // Einzel-/Bulk-Modus: Direktes Speichern mit intelligenter Regelprüfung & KI-Vorschlag
                    viewModel.saveSinglePageBulk(bmp) { savedDoc ->
                        // Bleibt im Scanner, zeigt Bestätigung
                    }
                } else {
                    // Manueller Mehrseiten-Modus: Seite der Liste hinzufügen
                    viewModel.addPage(bmp)
                }
            }
        }

        if (capture != null) {
            try {
                capture.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                            val bmp = try {
                                imageProxyToBitmap(imageProxy)
                            } catch (t: Throwable) {
                                Log.e("ScannerView", "Fehler beim Dekodieren des ImageProxy: ${t.message}", t)
                                null
                            } finally {
                                imageProxy.close()
                            }
                            if (bmp != null) {
                                onBmpReady(bmp)
                            } else {
                                Log.e("ScannerView", "Bild konnte nicht decodiert werden")
                                scanErrorMessage = "Kamerabild konnte nicht decodiert werden. Bitte erneut auslösen."
                                hasCapturedCurrentDocument = false
                                autoDetectProgress = 0f
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("ScannerView", "Kamera-Fehler bei Aufnahme: ${exception.message}", exception)
                            scanErrorMessage = "Kamerafehler: ${exception.localizedMessage ?: "Auslösung fehlgeschlagen"}"
                            hasCapturedCurrentDocument = false
                            autoDetectProgress = 0f
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("ScannerView", "Ausnahme beim Kamera-Auslösen: ${e.message}", e)
                scanErrorMessage = "Auslöser-Fehler: ${e.localizedMessage ?: "Auslösung fehlgeschlagen"}"
                hasCapturedCurrentDocument = false
                autoDetectProgress = 0f
            }
        } else {
            if (!isCameraHardwareAvailable) {
                // Nur im Emulator ohne Hardware-Kamera: Simuliertes Testblatt
                val template = selectedTemplate ?: templates.firstOrNull()
                val title = template?.subCategory ?: "Dokument"
                val sender = template?.defaultSender ?: "Scan"
                val isColor = if (isAutoMode) viewModel.pdfSettings.value.defaultColorMode == "COLOR" else isColorMode
                val mockBmp = com.example.service.ImageProcessingService.createSampleDocumentBitmap(title, sender, !isColor)
                onBmpReady(mockBmp)
            } else {
                Log.w("ScannerView", "Kamera noch nicht bereit")
                scanErrorMessage = "Kamera wird initialisiert... Bitte kurz warten."
                hasCapturedCurrentDocument = false
                autoDetectProgress = 0f
            }
        }
    }

    val showFirstStepsTour by viewModel.showFirstStepsTour.collectAsStateWithLifecycle()
    val currentTourStep by viewModel.tourStep.collectAsStateWithLifecycle()

    // Trigger-Logik (berücksichtigt Countdown / Sofort & setzt Blattstatus zurück)
    val startCaptureProcess: () -> Unit = {
        if (!isProcessing && !isCountdownRunning && capturedRawBitmap == null && pendingRuleSuggestion == null) {
            hasCapturedCurrentDocument = false
            motionDetectedAfterCapture = false
            if (showFirstStepsTour && currentTourStep == 4) {
                viewModel.setTourStep(5)
            }
            if (scannerSettings.triggerMode == "COUNTDOWN") {
                isCountdownRunning = true
                countdownValue = scannerSettings.countdownSeconds
            } else {
                executeCapture()
            }
        }
    }

    // Countdown Coroutine
    LaunchedEffect(isCountdownRunning, countdownValue) {
        if (isCountdownRunning) {
            if (countdownValue > 0) {
                kotlinx.coroutines.delay(1000L)
                countdownValue -= 1
            } else {
                isCountdownRunning = false
                executeCapture()
            }
        }
    }

    // Hardware-Button Event (Lautstärketasten)
    LaunchedEffect(Unit) {
        viewModel.hardwareTriggerEvent.collect {
            startCaptureProcess()
        }
    }

    // 1. Stativ-Vorlaufzeit: Vorlaufzeit VOR Erkennungsstart (0s = sofort aktiv, bis 60s)
    LaunchedEffect(scannerSettings.scanMode, scannerSettings.tripodAutoScan, scannerSettings.preScanDelaySeconds) {
        val isAuto = scannerSettings.scanMode == "AUTO" &&
            (scannerSettings.tripodAutoScan || scannerSettings.triggerMode == "AUTO_DETECT")
        if (isAuto && scannerSettings.preScanDelaySeconds > 0) {
            isPreScanActive = true
            preScanSecondsRemaining = scannerSettings.preScanDelaySeconds
            while (preScanSecondsRemaining > 0) {
                kotlinx.coroutines.delay(1000L)
                preScanSecondsRemaining--
            }
            isPreScanActive = false
        } else {
            isPreScanActive = false
            preScanSecondsRemaining = 0
        }
    }

    // 2. Automatischer Scan-Ablauf: Lock + Stabilität sind Pflicht, Wasserwaage/Schärfe sind weich.
    LaunchedEffect(
        isPreScanActive,
        hasCapturedCurrentDocument,
        isProcessing,
        isCountdownRunning,
        scannerSettings.scanMode,
        scannerSettings.tripodAutoScan,
        scannerSettings.triggerMode,
        scannerSettings.toolsInfoOnly,
        tiltAngleDegrees,
        frameAnalysis.isDocumentDetected,
        frameAnalysis.isDocumentStable,
        frameAnalysis.averageLuminance,
        capturedRawBitmap,
        pendingRuleSuggestion
    ) {
        val isTripodOrAuto = scannerSettings.scanMode == "AUTO" &&
            (scannerSettings.tripodAutoScan || scannerSettings.triggerMode == "AUTO_DETECT")

        if (isTripodOrAuto && !isPreScanActive && !hasCapturedCurrentDocument && !isProcessing && !isCountdownRunning && capturedRawBitmap == null && pendingRuleSuggestion == null) {
            snapshotFlow {
                frameAnalysis.isDocumentDetected &&
                    frameAnalysis.isDocumentStable &&
                    (!scannerSettings.tripodAutoScan || tiltAngleDegrees <= 8f) &&
                    frameAnalysis.averageLuminance >= 50f
            }.collectLatest { isReady ->
                if (isReady) {
                    autoDetectProgress = 0.5f
                    kotlinx.coroutines.delay(650L)
                    if (frameAnalysis.isDocumentDetected && frameAnalysis.isDocumentStable) {
                        if (frameAnalysis.sharpnessScore < 26f) {
                            val since = stableSinceMs
                            val stableFor = if (since > 0L) android.os.SystemClock.elapsedRealtime() - since else 0L
                            if (stableFor < 1500L) kotlinx.coroutines.delay(1500L - stableFor)
                            if (frameAnalysis.isDocumentDetected && frameAnalysis.isDocumentStable &&
                                frameAnalysis.sharpnessScore < 26f && !autofocusRequestedForStableDocument
                            ) {
                                focusRequestToken += 1
                                autofocusRequestedForStableDocument = true
                                kotlinx.coroutines.delay(1000L)
                            }
                        }
                        if (frameAnalysis.isDocumentDetected && frameAnalysis.isDocumentStable) {
                            capturedQuadAtScan = frameAnalysis.documentQuad?.map {
                                android.graphics.PointF(it.x, it.y)
                            }
                            autoDetectProgress = 1.0f
                            executeCapture()
                            hasCapturedCurrentDocument = true
                            motionDetectedAfterCapture = false
                        }
                    }
                    autoDetectProgress = 0f
                } else {
                    autoDetectProgress = 0f
                }
            }
        } else {
            autoDetectProgress = 0f
        }
    }

    // 3. Pausenzeit NACH dem Scan (Blattwechsel-Zeitfenster, 0s = sofort bis 60s):
    // Gibt dem Nutzer Zeit, die Seite umzublättern oder das nächste Dokument aufzulegen,
    // bevor die Erkennung wieder aktiv wird.
    LaunchedEffect(
        hasCapturedCurrentDocument,
        scannerSettings.postScanDelaySeconds,
        isCameraHardwareAvailable
    ) {
        if (hasCapturedCurrentDocument) {
            val postSec = scannerSettings.postScanDelaySeconds.coerceAtLeast(0)
            if (postSec > 0) {
                postScanSecondsRemaining = postSec
                while (postScanSecondsRemaining > 0) {
                    kotlinx.coroutines.delay(1000L)
                    postScanSecondsRemaining--
                }
            } else {
                postScanSecondsRemaining = 0
            }
            if (isCameraHardwareAvailable && postSec == 0) kotlinx.coroutines.delay(300L)

            // Auto-Scan erst wieder freigeben, wenn das Blatt weg ist oder sich deutlich bewegt hat.
            val capturedQuad = capturedQuadAtScan
            if (capturedQuad != null) {
                while (frameAnalysis.isDocumentDetected &&
                    !quadMovedBeyondThreshold(capturedQuad, frameAnalysis.documentQuad)
                ) {
                    kotlinx.coroutines.delay(100L)
                }
                capturedQuadAtScan = null
            }
            hasCapturedCurrentDocument = false
            motionDetectedAfterCapture = false
            autofocusRequestedForStableDocument = false
        } else {
            postScanSecondsRemaining = 0
        }
    }

    // Foto / Galerie Picker für Emulator & Gerät
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        if (scannerSettings.isBulkMode || isAutoMode) {
                            viewModel.saveSinglePageBulk(bmp)
                        } else {
                            viewModel.addPage(bmp)
                        }
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    // Kamera-Berechtigung
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var showCameraPermissionExplanation by remember { mutableStateOf(!hasCameraPermission) }

    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            showCameraPermissionExplanation = false
            if (showFirstStepsTour && currentTourStep == 3) {
                viewModel.setTourStep(4)
            }
        }
    }

    if (showCameraPermissionExplanation || (showFirstStepsTour && currentTourStep == 3)) {
        com.example.ui.components.CameraPermissionExplanationDialog(
            isAlreadyGranted = hasCameraPermission,
            isTourStep = showFirstStepsTour && currentTourStep == 3,
            onGrantPermissionRequest = {
                if (!hasCameraPermission) {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    showCameraPermissionExplanation = false
                    if (showFirstStepsTour && currentTourStep == 3) {
                        viewModel.setTourStep(4)
                    }
                }
            },
            onDismiss = {
                showCameraPermissionExplanation = false
                if (showFirstStepsTour && currentTourStep == 3) {
                    viewModel.setTourStep(4)
                }
            }
        )
    }

    // Animierter Scan-Laser
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    ScannerCameraPreviewPane(
        modifier = modifier,
        hasCameraPermission = hasCameraPermission,
        flashOption = flashOption,
        onFrameAnalysis = { analysis -> frameAnalysis = analysis },
        onImageCaptureCreated = { capture -> imageCaptureInstance = capture },
        onCameraAvailableChanged = { available -> isCameraHardwareAvailable = available },
        onPermissionRequest = {
            if (!hasCameraPermission) {
                showCameraPermissionExplanation = true
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onNavigateBack = onNavigateBack,
        laserProgress = laserProgress,
        isPhoneInWaage = isPhoneInWaage,
        sensorTiltX = sensorTiltX,
        sensorTiltY = sensorTiltY,
        isQualityCheckModeActive = isQualityCheckModeActive,
        onToggleQualityCheck = { isQualityCheckModeActive = !isQualityCheckModeActive },
        onImportGallery = { galleryLauncher.launch("image/*") },
        hasCapturedCurrentDocument = hasCapturedCurrentDocument,
        onResetCapturedDocument = {
            hasCapturedCurrentDocument = false
            capturedQuadAtScan = null
            motionDetectedAfterCapture = false
        },
        autoDetectProgress = autoDetectProgress,
        focusRequestToken = focusRequestToken,
        preScanSecondsRemaining = preScanSecondsRemaining,
        postScanSecondsRemaining = postScanSecondsRemaining,
        scannerSettings = scannerSettings,
        lastBulkSavedDoc = lastBulkSavedDoc,
        scanErrorMessage = scanErrorMessage,
        selectedTemplate = selectedTemplate,
        templates = templates,
        onToggleTemplateModal = { showTemplateModal = true },
        isAutoMode = isAutoMode,
        capturedPages = capturedPages,
        isProcessing = isProcessing,
        isCountdownRunning = isCountdownRunning,
        countdownValue = countdownValue,
        startCaptureProcess = { startCaptureProcess() },
        onClearPages = { viewModel.clearPages() },
        onRemovePage = { index -> viewModel.removePage(index) },
        onFinishScan = {
            viewModel.finishAndSaveMultiPageScan { createdDoc ->
                onDocumentScanned(createdDoc)
            }
        },
        onReportElementBounds = onReportElementBounds
    )
        // Auto-Dismiss für Bulk-Erfolgsanzeige nach 2.5 Sekunden
    LaunchedEffect(lastBulkSavedDoc) {
        if (lastBulkSavedDoc != null) {
            kotlinx.coroutines.delay(2500L)
            viewModel.clearLastBulkSavedDoc()
        }
    }

    if (showTemplateModal) {
        TemplateSelectModal(
            templates = templates,
            selectedTemplate = selectedTemplate,
            availableDocTypes = docTypes,
            onTemplateSelected = { tmpl ->
                viewModel.selectTemplate(tmpl)
            },
            onSaveTemplate = { updated ->
                viewModel.updateTemplate(updated)
            },
            onDeleteTemplate = { id ->
                viewModel.deleteTemplate(id)
            },
            onDismissRequest = { showTemplateModal = false }
        )
    }

    pendingCheckBitmap?.let { checkBmp ->
        com.example.ui.components.ScanQualityCheckDialog(
            rawBitmap = checkBmp,
            isColorMode = if (isAutoMode) viewModel.pdfSettings.value.defaultColorMode == "COLOR" else isColorMode,
            onAcceptProcessed = { finalBmp ->
                pendingCheckBitmap = null
                if (isBatchModeActive) {
                    viewModel.enqueueBatchDocument(finalBmp)
                } else if (scannerSettings.isBulkMode || isAutoMode) {
                    viewModel.saveSinglePageBulk(finalBmp) { savedDoc -> }
                } else {
                    viewModel.addPage(finalBmp)
                }
            },
            onRetake = {
                pendingCheckBitmap = null
                hasCapturedCurrentDocument = false
                autoDetectProgress = 0f
            }
        )
    }

    BackHandler(enabled = capturedRawBitmap != null) {
        capturedRawBitmap = null
        hasCapturedCurrentDocument = false
        autoDetectProgress = 0f
    }

    capturedRawBitmap?.let { rawBmp ->
        com.example.ui.views.scanner.DocumentReviewAndCropScreen(
            capturedBitmap = rawBmp,
            onCancel = {
                capturedRawBitmap = null
                hasCapturedCurrentDocument = false
                autoDetectProgress = 0f
            },
            onConfirmFinalDocument = { finalBmp ->
                capturedRawBitmap = null
                if (scannerSettings.isBulkMode || isAutoMode) {
                    viewModel.finalizeScanWithOptimizedBitmap(finalBmp)
                } else {
                    viewModel.addPage(finalBmp)
                }
            }
        )
    }

    pendingRuleSuggestion?.let { suggestion ->
        PendingRuleSuggestionDialog(
            suggestion = suggestion,
            availableDocTypes = docTypes,
            viewModel = viewModel
        )
    }
}


