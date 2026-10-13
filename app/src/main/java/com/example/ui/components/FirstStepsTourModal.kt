package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.MainNavigationTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class TourShapeType {
    CIRCLE,
    ROUNDED_RECT
}

enum class TooltipArrowPosition {
    TOP,
    BOTTOM,
    NONE
}

/**
 * Exakte Positions- und Größendaten des zu fokussierenden UI-Elements
 */
data class TourElementBounds(
    val centerX: Float,
    val centerY: Float,
    val widthPx: Float,
    val heightPx: Float,
    val shapeType: TourShapeType
)

/**
 * Tour-Station Datenstruktur für den chronologischen Ablauf
 */
data class CompactTourStation(
    val step: Int,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val targetTab: MainNavigationTab
)

@Composable
fun FirstStepsTourModal(
    viewModel: DocAnizerViewModel,
    targetBounds: Map<Int, TourElementBounds>,
    onDismiss: () -> Unit,
    onStepInteracted: (Int) -> Unit = {},
    onNavigateToTab: (MainNavigationTab) -> Unit = {}
) {
    val stations = remember {
        listOf(
            CompactTourStation(
                step = 1,
                title = "Dokumente erfassen",
                description = "Tippe auf den Scan-Button (+), um das Erfassungs-Menü zu öffnen.",
                icon = Icons.Default.AddCircleOutline,
                targetTab = MainNavigationTab.DOCANIZER
            ),
            CompactTourStation(
                step = 2,
                title = "Dokument scannen",
                description = "Wähle „Dokument scannen“ für die automatische Beleg- & Texterkennung.",
                icon = Icons.Default.DocumentScanner,
                targetTab = MainNavigationTab.DOCANIZER
            ),
            CompactTourStation(
                step = 3,
                title = "Kamera-Berechtigung",
                description = "Wähle in der Systemabfrage Option 1 oder 2 für den Scanner-Zugriff.",
                icon = Icons.Default.CameraAlt,
                targetTab = MainNavigationTab.SCANNER
            ),
            CompactTourStation(
                step = 4,
                title = "Ausrichten & Scannen",
                description = "Richte die Kamera über dem Beleg aus und tippe auf den Auslöser.",
                icon = Icons.Default.PhotoCamera,
                targetTab = MainNavigationTab.SCANNER
            ),
            CompactTourStation(
                step = 5,
                title = "Lokaler KI-Regelvorschlag",
                description = "Das On-Device LLM schlägt Absender & Ordner vor. Klicke auf „Regel speichern“!",
                icon = Icons.Default.AutoAwesome,
                targetTab = MainNavigationTab.SCANNER
            ),
            CompactTourStation(
                step = 6,
                title = "Dokumenten Tresor",
                description = "Revisionssichere Ordnerstruktur und blitzschnelle OCR-Volltextsuche.",
                icon = Icons.Default.FolderSpecial,
                targetTab = MainNavigationTab.DOCANIZER
            ),
            CompactTourStation(
                step = 7,
                title = "Lokaler KI-Agent",
                description = "100% offline: Tippe auf den KI-Agenten, um den Assistenten zu öffnen.",
                icon = Icons.Default.Psychology,
                targetTab = MainNavigationTab.DOCANIZER
            ),
            CompactTourStation(
                step = 8,
                title = "KI-Konversation & Kontext",
                description = "Stelle Fragen zu Rechnungen, Fristen & Summen – komplett offline ohne Cloud!",
                icon = Icons.Default.ChatBubbleOutline,
                targetTab = MainNavigationTab.DOCANIZER
            ),
            CompactTourStation(
                step = 9,
                title = "Fragen & Befehle",
                description = "Tippe deine Frage ein oder nutze Beleg-Aktionen. Tippe auf Weiter für das Dashboard.",
                icon = Icons.Default.EditNote,
                targetTab = MainNavigationTab.DOCANIZER
            ),
            CompactTourStation(
                step = 10,
                title = "Dashboard & Fristen",
                description = "Dein Gesamtüberblick: Ausgaben-Trends, Zahlungsfristen & Backup-Status.",
                icon = Icons.Default.SpaceDashboard,
                targetTab = MainNavigationTab.DASHBOARD
            ),
            CompactTourStation(
                step = 11,
                title = "Tour abgeschlossen!",
                description = "Herzlichen Glückwunsch! Du hast die wichtigsten Kernfunktionen kennengelernt.",
                icon = Icons.Default.CheckCircle,
                targetTab = MainNavigationTab.DASHBOARD
            )
        )
    }

    val currentStep by viewModel.tourStep.collectAsState()
    val stationIndex = (currentStep - 1).coerceIn(0, stations.size - 1)
    val currentStation = stations[stationIndex]
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // 1. SCHRITT 3 & 5 SIND EIGENSTÄNDIGE MODALE DIALOGE
    // (Kamera-Berechtigung & KI-Regelvorschlag).
    // FirstStepsTourModal rendert in diesen Schritten KEINEN Scrim und KEINE Tooltips,
    // um Doppel-Abdunklung und zufällig aufblitzende Boxen komplett zu verhindern!
    if (currentStep == 3 || currentStep == 5) {
        return
    }

    // 2. SCHRITT 11: FEIERLICHER TOUR-ABSCHLUSS (COMPLETION DIALOG)
    if (currentStep == 11) {
        TourCompletionDialog(
            onFinish = {
                onStepInteracted(11)
                onDismiss()
            }
        )
        return
    }

    // 3. ELEMENT-BASIERTE SCHRITTE (1, 2, 4, 6, 7, 8, 9, 10):
    // Wenn exakte Koordinaten noch nicht gemessen sind, greift eine robuste Fallback-Position,
    // damit die Tour-Stationen (insb. KI-Chatbot Schritte 8 & 9) NIEMALS übersprungen werden!
    val rawBounds = targetBounds[currentStep]

    // 4. DYNAMISCHES ABDUNKELN:
    // Der Hintergrund dunkelt sich erst langsam ein (~350ms), wenn das Ziel-Element
    // tatsächlich existiert und klickbereit ist.
    // Nach Klick sofort blitzschnell aufhellen (~90ms)!
    val scrimAlpha = remember { Animatable(0f) }
    var isFadingOut by remember { mutableStateOf(false) }

    LaunchedEffect(currentStep, rawBounds) {
        if (!isFadingOut) {
            scrimAlpha.animateTo(
                targetValue = 0.38f,
                animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing)
            )
        }
    }

    fun triggerAdvance(targetStep: Int? = null) {
        coroutineScope.launch {
            isFadingOut = true
            // Sehr schnelle Fade-Dauer (~90ms) beim Aufhellen nach Klick
            scrimAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 90, easing = FastOutLinearInEasing)
            )
            onStepInteracted(currentStep)
            val next = targetStep ?: (currentStep + 1)
            if (next <= stations.size) {
                viewModel.setTourStep(next)
                val nextStation = stations[next - 1]
                onNavigateToTab(nextStation.targetTab)
            } else {
                onDismiss()
            }
            delay(100)
            isFadingOut = false
        }
    }

    fun triggerBack() {
        if (currentStep > 1) {
            coroutineScope.launch {
                isFadingOut = true
                scrimAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 90, easing = FastOutLinearInEasing)
                )
                val prev = currentStep - 1
                viewModel.setTourStep(prev)
                val prevStation = stations[prev - 1]
                onNavigateToTab(prevStation.targetTab)
                delay(100)
                isFadingOut = false
            }
        }
    }

    // Radar & Puls-Animationen für das hervorgehobene Element
    val infiniteTransition = rememberInfiniteTransition(label = "tour_animations")

    val circleRadarScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "circle_radar_scale"
    )
    val circleRadarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "circle_radar_alpha"
    )

    val tabPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tab_pulse_alpha"
    )
    val tabPulseExpandDp by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tab_pulse_expand"
    )

    val isAiStep = currentStep == 7
    val aiHaloRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ai_halo_rot"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        // Abdunkelungs-Hintergrund (nur sichtbar, wenn aktiv gewartet wird)
        if (scrimAlpha.value > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha.value))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        triggerAdvance()
                    }
            )
        }

        // Schnelle Überspringen-Schaltfläche dauerhaft am oberen rechten Bildschirmrand
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp).copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismiss()
                    }
                    .testTag("btn_screen_skip_tour")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Tour überspringen",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Tour überspringen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Element-Hervorhebung mit robustem Fallback bei Animationen
        val measuredBounds = rawBounds ?: when (currentStep) {
            8 -> TourElementBounds(
                centerX = screenWidthPx * 0.44f,
                centerY = screenHeightPx * 0.26f,
                widthPx = (screenWidthPx * 0.78f).coerceAtMost(with(density) { 320.dp.toPx() }),
                heightPx = with(density) { 120.dp.toPx() },
                shapeType = TourShapeType.ROUNDED_RECT
            )
            9 -> TourElementBounds(
                centerX = screenWidthPx * 0.44f,
                centerY = screenHeightPx * 0.85f,
                widthPx = screenWidthPx * 0.78f,
                heightPx = with(density) { 92.dp.toPx() },
                shapeType = TourShapeType.ROUNDED_RECT
            )
            10 -> TourElementBounds(
                centerX = screenWidthPx * 0.88f,
                centerY = screenHeightPx - with(density) { 36.dp.toPx() },
                widthPx = with(density) { 60.dp.toPx() },
                heightPx = with(density) { 48.dp.toPx() },
                shapeType = TourShapeType.ROUNDED_RECT
            )
            else -> TourElementBounds(
                centerX = screenWidthPx * 0.5f,
                centerY = screenHeightPx * 0.5f,
                widthPx = with(density) { 200.dp.toPx() },
                heightPx = with(density) { 120.dp.toPx() },
                shapeType = TourShapeType.ROUNDED_RECT
            )
        }

        val elemCenterX = measuredBounds.centerX
        val elemCenterY = measuredBounds.centerY
        val isCircle = measuredBounds.shapeType == TourShapeType.CIRCLE
        val measuredWidthDp = with(density) { measuredBounds.widthPx.toDp() }
        val measuredHeightDp = with(density) { measuredBounds.heightPx.toDp() }
        val targetWidthDp = if (isCircle) measuredWidthDp else (measuredWidthDp + 8.dp)
        val targetHeightDp = if (isCircle) measuredHeightDp else (measuredHeightDp + 4.dp)
        val targetWidthPx = with(density) { targetWidthDp.toPx() }
        val targetHeightPx = with(density) { targetHeightDp.toPx() }

        val accentColor = if (isAiStep) MaterialTheme.colorScheme.secondary else Color(0xFF00E5FF)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (elemCenterX - targetWidthPx / 2f).roundToInt(),
                        y = (elemCenterY - targetHeightPx / 2f).roundToInt()
                    )
                }
                .size(targetWidthDp, targetHeightDp),
            contentAlignment = Alignment.Center
        ) {
            if (isCircle) {
                Surface(
                    shape = CircleShape,
                    color = accentColor.copy(alpha = circleRadarAlpha * 0.75f),
                    modifier = Modifier.size(targetWidthDp * circleRadarScale)
                ) {}
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(2.6.dp, accentColor),
                    modifier = Modifier
                        .size(targetWidthDp + 6.dp)
                        .graphicsLayer {
                            if (isAiStep) rotationZ = aiHaloRotation
                        }
                ) {}
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = accentColor.copy(alpha = 0.18f * tabPulseAlpha),
                    border = BorderStroke(2.4.dp, accentColor.copy(alpha = tabPulseAlpha)),
                    modifier = Modifier.size(
                        width = targetWidthDp + tabPulseExpandDp.dp,
                        height = targetHeightDp + (tabPulseExpandDp * 0.7f).dp
                    )
                ) {}
            }

            // Klick auf das Element schaltet Tour sofort fort
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        triggerAdvance()
                    }
            )
        }

        // Kompakte Speech-Bubble
        val tooltipWidthDp = 250.dp
        val tooltipWidthPx = with(density) { tooltipWidthDp.toPx() }
        val estimatedTooltipHeightPx = with(density) { 150.dp.toPx() }

        val targetTooltipLeftX = (elemCenterX - tooltipWidthPx / 2f).coerceIn(
            with(density) { 12.dp.toPx() },
            (screenWidthPx - tooltipWidthPx - with(density) { 12.dp.toPx() })
        )

        val elemTopY = elemCenterY - measuredBounds.heightPx / 2f
        val elemBottomY = elemCenterY + measuredBounds.heightPx / 2f

        val minTopY = with(density) { 64.dp.toPx() } // Sicher unterhalb von Statusleiste, Notches & AppBars
        val maxTopY = (screenHeightPx - estimatedTooltipHeightPx - with(density) { 64.dp.toPx() }).coerceAtLeast(minTopY)

        val arrowPosition: TooltipArrowPosition
        val targetTooltipTopY: Float

        if (currentStep == 8) {
            // Schritt 8 (KI-Agent Konversation): Platzierung direkt unter dem oberen Highlight
            // mit sicherem Abstand zu oberen Leisten und zur unteren Eingabeleiste!
            targetTooltipTopY = (elemBottomY + with(density) { 12.dp.toPx() }).coerceIn(minTopY, maxTopY)
            arrowPosition = TooltipArrowPosition.TOP
        } else if (elemCenterY < screenHeightPx * 0.46f) {
            // Element liegt in der oberen Bildschirmhälfte -> Tooltip UNTERHALB des Elements
            targetTooltipTopY = (elemBottomY + with(density) { 10.dp.toPx() }).coerceIn(minTopY, maxTopY)
            arrowPosition = TooltipArrowPosition.TOP
        } else {
            // Element liegt in der unteren Bildschirmhälfte -> Tooltip OBERHALB des Elements
            targetTooltipTopY = (elemTopY - estimatedTooltipHeightPx - with(density) { 10.dp.toPx() }).coerceIn(minTopY, maxTopY)
            arrowPosition = TooltipArrowPosition.BOTTOM
        }

        val animTooltipX by animateFloatAsState(
            targetValue = targetTooltipLeftX,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
            label = "tooltip_anim_x"
        )
        val animTooltipY by animateFloatAsState(
            targetValue = targetTooltipTopY,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioLowBouncy),
            label = "tooltip_anim_y"
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        x = animTooltipX.roundToInt(),
                        y = animTooltipY.roundToInt()
                    )
                }
                .width(tooltipWidthDp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                val arrowOffsetInBubble = (elemCenterX - animTooltipX).coerceIn(
                    with(density) { 16.dp.toPx() },
                    tooltipWidthPx - with(density) { 16.dp.toPx() }
                )

                if (arrowPosition == TooltipArrowPosition.TOP) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .drawBehind {
                                val path = Path().apply {
                                    moveTo(arrowOffsetInBubble - 6.dp.toPx(), 7.dp.toPx())
                                    lineTo(arrowOffsetInBubble + 6.dp.toPx(), 7.dp.toPx())
                                    lineTo(arrowOffsetInBubble, 0f)
                                    close()
                                }
                                drawPath(path, color = accentColor)
                            }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp),
                    border = BorderStroke(1.5.dp, accentColor),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tour_compact_tooltip")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            triggerAdvance()
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { currentStep.toFloat() / stations.size.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                            color = accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "$currentStep/${stations.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 9.5.sp,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = currentStation.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onDismiss() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("btn_close_tour")
                            ) {
                                Text(
                                    text = "Überspringen",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Tour beenden",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Text(
                            text = currentStation.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 14.5.sp,
                            maxLines = 3
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (currentStep > 1) {
                                TextButton(
                                    onClick = { triggerBack() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Zurück", fontSize = 10.sp)
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                repeat(stations.size) { idx ->
                                    val isCur = idx == (currentStep - 1)
                                    Box(
                                        modifier = Modifier
                                            .size(if (isCur) 5.dp else 3.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isCur) accentColor
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                            )
                                    )
                                }
                            }

                            Button(
                                onClick = { triggerAdvance() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(24.dp)
                                    .testTag("btn_tour_next")
                            ) {
                                Text(
                                    text = "Weiter",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(11.dp))
                            }
                        }
                    }
                }

                if (arrowPosition == TooltipArrowPosition.BOTTOM) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .drawBehind {
                                val path = Path().apply {
                                    moveTo(arrowOffsetInBubble - 6.dp.toPx(), 0f)
                                    lineTo(arrowOffsetInBubble + 6.dp.toPx(), 0f)
                                    lineTo(arrowOffsetInBubble, 7.dp.toPx())
                                    close()
                                }
                                drawPath(path, color = accentColor)
                            }
                    )
                }
            }
        }
    }
}

/**
 * Feierlicher Abschluss-Dialog am Ende der Tour.
 * Gibt dem Nutzer eine klare Zusammenfassung aller Kernfunktionen
 * und einen eindeutigen Call-To-Action zum Starten.
 */
@Composable
fun TourCompletionDialog(
    onFinish: () -> Unit
) {
    Dialog(
        onDismissRequest = onFinish,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 10.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("dialog_tour_completion")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Feierliches Icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Tour abgeschlossen! 🎉",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Du kennst jetzt alle Kernfunktionen von myDocAnizer:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }

                // Checkliste der erlernten Funktionen
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TourFeatureItem(
                            icon = Icons.Default.DocumentScanner,
                            title = "Dokumente & Belege scannen",
                            description = "Präziser Beleg-Scan & automatische Texterkennung (OCR)."
                        )
                        TourFeatureItem(
                            icon = Icons.Default.AutoAwesome,
                            title = "Lokale KI-Regeln",
                            description = "Automatische Ordner- & Absenderzuordnung 100% ohne Cloud."
                        )
                        TourFeatureItem(
                            icon = Icons.Default.FolderSpecial,
                            title = "Dokumenten Tresor",
                            description = "Revisionssichere Ordner, Metadaten & schnelle Volltextsuche."
                        )
                        TourFeatureItem(
                            icon = Icons.Default.Psychology,
                            title = "Lokaler KI-Agent",
                            description = "Stelle Fragen zu Rechnungen, Fristen & Summen komplett offline."
                        )
                    }
                }

                // Großer Abschluss-Button
                Button(
                    onClick = onFinish,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_tour_finish_complete")
                ) {
                    Text(
                        text = "Jetzt myDocAnizer nutzen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TourFeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.5.sp
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}
