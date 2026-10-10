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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerView(
    viewModel: DocAnizerViewModel,
    onDocumentScanned: (DocumentEntity) -> Unit,
    onNavigateToBatchInbox: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
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

    // 2. Bildschärfe-Prüfung (Erschütterung / Stillstandsanalyse)
    val sharpnessScore = when {
        xyMagnitude < 0.45f -> 100
        xyMagnitude < 0.95f -> 95
        xyMagnitude < 1.6f -> 85
        xyMagnitude < 2.5f -> 65
        else -> 40
    }
    val isSharpnessOptimal = sharpnessScore >= 85

    val isAutoMode = scannerSettings.scanMode == "AUTO"

    // Vorab-Qualitätsprüfung & Ecken-Justierung
    var isQualityCheckModeActive by remember { mutableStateOf(false) }
    var pendingCheckBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

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

    // Trigger-Logik (berücksichtigt Countdown / Sofort & setzt Blattstatus zurück)
    val startCaptureProcess: () -> Unit = {
        if (!isProcessing && !isCountdownRunning) {
            hasCapturedCurrentDocument = false
            motionDetectedAfterCapture = false
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

    // 1. Automatische Erkennung des Blattwechsels:
    // Nachdem ein Blatt gescannt wurde, wartet das System darauf, dass das gescannte Blatt entfernt
    // und das nächste Dokument im Scanbereich platziert wird.
    // Ein Blattwechsel liegt vor, wenn:
    // - Das Dokument kurzzeitig nicht mehr im Bild ist (!frameAnalysis.isDocumentDetected)
    // - ODER signifikante Bewegung (Hand/Papierwechsel) gemessen wird.
    // Sobald das neue Blatt ruhig im Bild liegt, schaltet das System sofort wieder für den nächsten Auto-Scan frei!
    LaunchedEffect(
        hasCapturedCurrentDocument,
        frameAnalysis.motionScore,
        xyMagnitude,
        isPhoneInWaage,
        isSharpnessOptimal,
        frameAnalysis.isDocumentDetected,
        scannerSettings.toolsInfoOnly,
        scannerSettings.sheetChangeSensitivity,
        isCameraHardwareAvailable
    ) {
        if (hasCapturedCurrentDocument) {
            val motionThreshold = when (scannerSettings.sheetChangeSensitivity) {
                "HIGH" -> 3.5f
                "LOW" -> 7.5f
                else -> 5.0f
            }

            // Schritt 1: Wurde das alte Blatt weggenommen oder bewegt?
            if (!frameAnalysis.isDocumentDetected || frameAnalysis.motionScore > motionThreshold || xyMagnitude > 0.65f) {
                motionDetectedAfterCapture = true
            }

            // Schritt 2: Liegt das neue Blatt ruhig im Scanbereich?
            if (motionDetectedAfterCapture) {
                val isCalm = frameAnalysis.motionScore < (motionThreshold - 1.0f).coerceAtLeast(2.0f) && xyMagnitude < 0.5f
                val isDocReady = frameAnalysis.isDocumentDetected && (scannerSettings.toolsInfoOnly || (isPhoneInWaage && isSharpnessOptimal))

                if (isCalm && isDocReady) {
                    val postDelay = (scannerSettings.postScanDelaySeconds * 1000L).coerceIn(1000L, 60000L)
                    kotlinx.coroutines.delay(postDelay)
                    hasCapturedCurrentDocument = false
                    motionDetectedAfterCapture = false
                }
            }

            // Schritt 3: Im Emulator oder bei Kamera-Simulation nach eingestellter Nachlaufzeit automatisch freigeben
            if (!isCameraHardwareAvailable) {
                val postDelay = (scannerSettings.postScanDelaySeconds * 1000L).coerceIn(1000L, 60000L)
                kotlinx.coroutines.delay(postDelay)
                hasCapturedCurrentDocument = false
                motionDetectedAfterCapture = false
            }
        }
    }

    // 2. Automatischer Scan-Ablauf:
    // Startet VOLLAUTOMATISCH, wenn ein neues Dokument ruhig und scharf im Scanbereich liegt (oder sofort bei Info-Only),
    // und STOPPT nach dem Auslösen sofort solange, bis wieder ein neues Dokument platziert wurde!
    LaunchedEffect(
        scannerSettings.triggerMode,
        scannerSettings.tripodAutoScan,
        scannerSettings.preScanDelaySeconds,
        scannerSettings.postScanDelaySeconds,
        scannerSettings.scanMode,
        scannerSettings.toolsInfoOnly,
        hasCapturedCurrentDocument,
        isProcessing,
        isCountdownRunning,
        isPhoneInWaage,
        isSharpnessOptimal,
        frameAnalysis.isDocumentDetected
    ) {
        val isTripodOrAuto = scannerSettings.scanMode == "AUTO" &&
            (scannerSettings.tripodAutoScan || scannerSettings.triggerMode == "AUTO_DETECT")

        // Nur starten wenn Auto-Scan aktiv, noch KEIN Blatt erfasst wurde und keine Verarbeitung läuft
        if (isTripodOrAuto && !hasCapturedCurrentDocument && !isProcessing && !isCountdownRunning) {
            val isReady = frameAnalysis.isDocumentDetected && (scannerSettings.toolsInfoOnly || (isPhoneInWaage && isSharpnessOptimal))
            if (!isReady) {
                autoDetectProgress = 0f
                return@LaunchedEffect
            }

            val detectionDelay = (scannerSettings.preScanDelaySeconds * 1000L).coerceIn(1000L, 60000L)
            val steps = (scannerSettings.preScanDelaySeconds * 10).coerceIn(10, 100)
            val stepTime = (detectionDelay / steps)

            autoDetectProgress = 0f
            for (i in 1..steps) {
                kotlinx.coroutines.delay(stepTime)
                // Bei Verwackeln, Verkippen oder Entfernen des Blatts abbrechen (außer bei Info-Only)
                val checkCondition = frameAnalysis.isDocumentDetected && (scannerSettings.toolsInfoOnly || (isPhoneInWaage && isSharpnessOptimal))
                if (!checkCondition) {
                    autoDetectProgress = 0f
                    return@LaunchedEffect
                }
                autoDetectProgress = i / steps.toFloat()
            }

            // Automatisch auslösen!
            executeCapture()
            // Scan SOFORT stoppen und auf nächstes Blatt warten
            hasCapturedCurrentDocument = true
            motionDetectedAfterCapture = false
            autoDetectProgress = 0f
        } else {
            autoDetectProgress = 0f
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

    val showFirstStepsTour by viewModel.showFirstStepsTour.collectAsStateWithLifecycle()

    LaunchedEffect(hasCameraPermission, showFirstStepsTour) {
        if (!hasCameraPermission && !showFirstStepsTour) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
        onPermissionRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
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
            motionDetectedAfterCapture = false
        },
        autoDetectProgress = autoDetectProgress,
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
        }
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

    pendingRuleSuggestion?.let { suggestion ->
        PendingRuleSuggestionDialog(
            suggestion = suggestion,
            availableDocTypes = docTypes,
            viewModel = viewModel
        )
    }
}


