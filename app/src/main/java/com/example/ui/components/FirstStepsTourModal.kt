package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.MainNavigationTab
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Datenmodell für eine Station der interaktiven Erste-Schritte Tour mit Ziel-Element und Spotlight.
 */
data class TourStation(
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val targetElementName: String,
    val clickPath: String,
    val icon: ImageVector,
    val badgeLabel: String,
    val description: String,
    val tips: List<String>,
    val targetTab: MainNavigationTab,
    val targetSubTab: Int? = null,
    val spotlightAlignment: Alignment = Alignment.Center,
    val spotlightOffsetX: Float = 0f,
    val spotlightOffsetY: Float = 0f,
    val actionButtonText: String = "Weiter"
)

/**
 * Interaktive, animierte Erste-Schritte Tour für myDocAnizer-Mobile:
 * 
 * Verhindert das Verdecken der App-UI:
 * 1. Beim Wechsel zu einem Schritt minimiert sich das Tour-Popup automatisch zu einer kompakten Status-Leiste.
 * 2. Die App schaltet im Hintergrund sofort auf den passenden Tab / Screen.
 * 3. Ein animierter Spotlight-Fokusring mit pulsierender Animation hebt das relevante Element / Funktion hervor.
 * 4. Anschließend maximiert sich das Popup geschmeidig wieder, um die fundierte Erklärung & Tipps bereitzustellen.
 * 5. Der Nutzer kann jederzeit manuell zwischen minimierter UI-Ansicht und maximierter Erklärung umschalten.
 */
@Composable
fun FirstStepsTourModal(
    viewModel: DocAnizerViewModel,
    onDismiss: () -> Unit,
    onNavigateToTab: (MainNavigationTab) -> Unit = {}
) {
    val stations = remember {
        listOf(
            TourStation(
                stepNumber = 1,
                title = "Dokumenten Tresor",
                subtitle = "Dokumente und Ordnerstruktur",
                targetElementName = "Ordnerleiste & Dokumenten-Übersicht",
                clickPath = "1️⃣ Unten im Menü auf [Dokumente] tippen  ➔  2️⃣ Hauptkategorie wählen (z. B. A02 Finanzen)",
                icon = Icons.Default.FolderSpecial,
                badgeLabel = "Kernbereich • Dokumenten-Tresor",
                description = "Der Dokumenten Tresor ist der Bereich für deine Dokumente und Ordnerstruktur in myDocAnizer-Mobile. Hier werden alle gescannten und importierten Unterlagen in einer 2-stufigen Ordnerstruktur (Hauptkategorien A01–A08 & Unterkategorien) strukturiert archiviert.",
                tips = listOf(
                    "Tippe auf einen Ordner, um sofort alle zugehörigen Dokumente zu filtern.",
                    "Nutze die Suchleiste: Sie durchsucht dank Offline-OCR jedes Wort im gesamten Text!",
                    "Über das '+' Symbol kannst du neue Dokumente direkt im gewählten Ordner anlegen."
                ),
                targetTab = MainNavigationTab.DOCANIZER,
                spotlightAlignment = Alignment.TopCenter,
                spotlightOffsetY = 120f,
                actionButtonText = "Tresor erkunden"
            ),
            TourStation(
                stepNumber = 2,
                title = "Scan InBox & Kamera",
                subtitle = "Dokumente & Belege digitalisieren mit Schärfefilter",
                targetElementName = "Kamera-Sucher & Schnellauslöser",
                clickPath = "1️⃣ Unten im Menü auf [Scan Eingang] tippen  ➔  2️⃣ Auslöser drücken oder Lautstärketaste nutzen",
                icon = Icons.Default.DocumentScanner,
                badgeLabel = "Schnellerfassung",
                description = "Digitalisiere Briefe, Kassenbelege oder mehrseitige Verträge in Sekunden. Die Kamera erkennt Papierränder automatisch und korrigiert Schatten.",
                tips = listOf(
                    "Drücke Lautstärke 'Leiser' oder 'Lauter' am Smartphone, um freihändig auszulösen.",
                    "Im Stapel-Modus kannst du 10 Seiten nacheinander scannen – die App bündelt sie automatisch zu einem PDF.",
                    "Alle Scans landen zuerst in der InBox zur schnellen Prüfung vor der endgültigen Ablage."
                ),
                targetTab = MainNavigationTab.SCANNER,
                spotlightAlignment = Alignment.Center,
                spotlightOffsetY = 40f,
                actionButtonText = "Kamera testen"
            ),
            TourStation(
                stepNumber = 3,
                title = "Lokaler KI Agent & Regeln",
                subtitle = "Automatische Erkennung zu 100% offline ohne Cloud",
                targetElementName = "Lokale On-Device Modelle & Schlagwort-Regeln",
                clickPath = "1️⃣ Unten im Menü auf [Einstellungen] tippen  ➔  2️⃣ Oben den Reiter [Lokaler KI Agent] wählen",
                icon = Icons.Default.Psychology,
                badgeLabel = "Lokaler KI Agent",
                description = "Der lokale KI Agent analysiert Texte automatisiert direkt auf dem Mobilprozessor. Er erkennt Absender (z.B. Deutsche Post, Telekom), Rechnungsbetrag, Fälligkeitsdatum und ordnet Belege passend zu.",
                tips = listOf(
                    "Kein Datenabfluss: Deine sensiblen Dokumente verlassen niemals dein Telefon!",
                    "Wähle in den Einstellungen unter 'Lokaler KI Agent' aus verschiedenen quantisierten On-Device Modellen.",
                    "Eigene Schlagwort-Regeln erfassen wiederkehrende Dokumente blitzschnell und deterministisch."
                ),
                targetTab = MainNavigationTab.SETTINGS,
                targetSubTab = 1,
                spotlightAlignment = Alignment.Center,
                spotlightOffsetY = -60f,
                actionButtonText = "Lokalen KI Agenten verstehen"
            ),
            TourStation(
                stepNumber = 4,
                title = "Dashboard & Finanzen",
                subtitle = "Einnahmen, Ausgaben & Fristen-Radar im Blick",
                targetElementName = "Fristen-Radar & monatliches Finanz-Cockpit",
                clickPath = "1️⃣ Unten im Menü auf [Dashboard] tippen  ➔  2️⃣ Fristen-Radar & Fixkosten prüfen",
                icon = Icons.Default.Dashboard,
                badgeLabel = "Überblick & Kontrolle",
                description = "Das Dashboard fasst deinen aktuellen Status zusammen: Welche Rechnungen sind fällig? Wann läuft ein Vertrag aus? Und wie steht es um deine monatlichen Fixkosten?",
                tips = listOf(
                    "Fristen-Radar: Verpasse nie wieder Kündigungstermine oder Zahlungsziele.",
                    "Einnahmen-/Ausgaben-Tracking: Erfasse Barausgaben oder lade Kontoauszüge zum automatischen Beleg-Abgleich.",
                    "Passe Widgets nach deinen Wünschen an: Schalte Anzeigen einfach ein oder aus."
                ),
                targetTab = MainNavigationTab.DASHBOARD,
                spotlightAlignment = Alignment.TopCenter,
                spotlightOffsetY = 160f,
                actionButtonText = "Dashboard ansehen"
            ),
            TourStation(
                stepNumber = 5,
                title = "Sicherheit & PC-Kopplung",
                subtitle = "Biometrischer Tresorschutz & kabelloser Desktop-Sync",
                targetElementName = "Tresor-Verschlüsselung & Lokale Desktop-Kopplung",
                clickPath = "1️⃣ Unten im Menü auf [Einstellungen] tippen  ➔  2️⃣ Oben den Reiter [Sicherheit] oder [WLAN & P2P Sync] wählen",
                icon = Icons.Default.Security,
                badgeLabel = "Datenschutz & P2P",
                description = "Volle Kontrolle über deine Privatsphäre: Schütze deine Akten mit Fingerabdruck oder Passkeys und übertrage Dateien ohne Cloud direkt an deinen PC.",
                tips = listOf(
                    "Aktiviere 'Biometrischen Tresorschutz' in den Einstellungen für sicheres Entsperren.",
                    "Desktop-Kopplung: Scanne einfach den QR-Code der myDocAnizer-Desktop App am PC für lokalen WLAN-Datenaustausch.",
                    "AES-256 Backups auf Google Drive, USB-Stick oder lokales NAS sorgen für absolute Ausfallsicherheit."
                ),
                targetTab = MainNavigationTab.SETTINGS,
                targetSubTab = 3,
                spotlightAlignment = Alignment.BottomCenter,
                spotlightOffsetY = -120f,
                actionButtonText = "Tour abschließen & Loslegen!"
            )
        )
    }

    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentStation = stations[currentStepIndex.coerceIn(0, stations.size - 1)]
    val isLastStep = currentStepIndex == stations.size - 1

    // Status: Ob das Popup minimiert ist, um die darunterliegende UI & Animation zu zeigen
    var isMinimizedForSpotlight by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // Brand-Farben
    val cyanAccent = Color(0xFF00E5FF)
    val magentaAccent = Color(0xFFFF007F)

    // Automatische Animation & Tab-Umschaltung beim Schrittwechsel
    LaunchedEffect(currentStepIndex) {
        isMinimizedForSpotlight = true
        // Schritt 1: Zuerst auf Haupt-Tab schalten und dem Nutzer das Navigations-Element in der Leiste zeigen
        onNavigateToTab(currentStation.targetTab)
        if (currentStation.targetSubTab != null) {
            delay(1200L) // 1.2s Verweilzeit, damit der Nutzer sieht, welcher Haupt-Tab gedrückt wurde
            // Schritt 2: Nun das Untermenü / Subtab ansteuern
            viewModel.setTargetSettingsSubTab(currentStation.targetSubTab)
            delay(4800L)
        } else {
            delay(6000L)
        }
        isMinimizedForSpotlight = false
    }

    // Unendliche Puls-Animation für den Spotlight-Fokusring
    val infiniteTransition = rememberInfiniteTransition(label = "spotlight_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val arrowBounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_bounce"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .testTag("first_steps_tour_dialog")
    ) {
        // HINTERGRUND-ABDECKUNG (Nur wenn Erklärung maximiert ist, dezent abgedunkelt)
        if (!isMinimizedForSpotlight) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Klick auf den Hintergrund minimiert das Popup, um die UI anzusehen
                        isMinimizedForSpotlight = true
                    }
            )
        }

        // ANIMIERTER SPOTLIGHT-FOKUSRING & ZEIGER AUF DAS RELEVANTE ELEMENT
        AnimatedVisibility(
            visible = isMinimizedForSpotlight,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.8f),
            exit = fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(currentStation.spotlightAlignment)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .offset(
                        x = currentStation.spotlightOffsetX.dp,
                        y = (currentStation.spotlightOffsetY + arrowBounce).dp
                    )
                    .padding(16.dp)
                    .clickable {
                        // Klick auf Spotlight öffnet Erklärung sofort
                        isMinimizedForSpotlight = false
                    }
            ) {
                // Spotlight Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.92f),
                    border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(cyanAccent, magentaAccent))),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = cyanAccent.copy(alpha = 0.25f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = currentStation.icon,
                                    contentDescription = null,
                                    tint = cyanAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "👉 Ziel: ${currentStation.targetElementName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "📍 Navigation: ${currentStation.clickPath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Pulsierender leuchtender Fokus-Kreis
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(80.dp)
                ) {
                    // Äußerer Puls-Ring
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        cyanAccent.copy(alpha = pulseAlpha * 0.45f),
                                        magentaAccent.copy(alpha = pulseAlpha * 0.15f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    // Innerer Ziel-Ring
                    Surface(
                        shape = CircleShape,
                        color = cyanAccent.copy(alpha = 0.2f),
                        border = BorderStroke(2.dp, cyanAccent),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Fokus",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // MINIMIERTE FLOATING-PILL (WENN SPOTLIGHT AKTIV IST)
        AnimatedVisibility(
            visible = isMinimizedForSpotlight,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(cyanAccent, magentaAccent))),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isMinimizedForSpotlight = false }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = cyanAccent
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Schritt ${currentStepIndex + 1}/${stations.size}: ${currentStation.title}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Zeige Funktion in der App...",
                                style = MaterialTheme.typography.labelSmall,
                                color = cyanAccent,
                                fontSize = 10.sp
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { isMinimizedForSpotlight = false },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Erklärung öffnen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // MAXIMIERTES ERKLÄRUNGS-POPUP (KOMPAKT AM UNTEREN BILDSCHIRMRAND)
        AnimatedVisibility(
            visible = !isMinimizedForSpotlight,
            enter = slideInVertically(animationSpec = tween(250)) { it } + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(cyanAccent, magentaAccent))),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // HEADER: TITEL, SCHRITTZÄHLER & MINIMIEREN/SCHLIESSEN BUTTONS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = cyanAccent.copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = currentStation.icon,
                                        contentDescription = null,
                                        tint = cyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = currentStation.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = magentaAccent.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = currentStation.badgeLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = magentaAccent,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Schritt ${currentStepIndex + 1} von ${stations.size} • ${currentStation.subtitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Button um UI wieder anzuschauen (Minimieren)
                            IconButton(
                                onClick = { isMinimizedForSpotlight = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = "UI ansehen (Minimieren)",
                                    tint = cyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            TextButton(
                                onClick = onDismiss,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_skip_tour")
                            ) {
                                Text("Beenden", fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // FORTSCHRITTS-BALKEN
                    LinearProgressIndicator(
                        progress = { (currentStepIndex + 1) / stations.size.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = cyanAccent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // SCROLLBARER INHALTSBEREICH (KOMPAKT & STRUKTURIERT)
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // WO FINDE ICH DIESE FUNKTION? (KLICK-PFAD)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = cyanAccent.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, cyanAccent.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = cyanAccent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "So erreichst du diese Funktion in der App:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = cyanAccent
                                    )
                                }
                                Text(
                                    text = currentStation.clickPath,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        // BESCHREIBUNG
                        Text(
                            text = currentStation.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 17.sp
                        )

                        // SCHNELLE TIPPS
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Bedien-Tipps:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                currentStation.tips.forEach { tip ->
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text("•", color = cyanAccent, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = tip,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // UNTERE NAVIGATION- & AKTIONEN-LEISTE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStepIndex > 0) {
                            OutlinedButton(
                                onClick = { currentStepIndex-- },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp).testTag("btn_tour_back")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Zurück", fontSize = 12.sp)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { isMinimizedForSpotlight = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp), tint = cyanAccent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("UI zeigen", fontSize = 12.sp)
                            }
                        }

                        // SCHRITT-INDIKATOR DOTS
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            stations.indices.forEach { index ->
                                val isSelected = index == currentStepIndex
                                Box(
                                    modifier = Modifier
                                        .size(if (isSelected) 8.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) cyanAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable { currentStepIndex = index }
                                )
                            }
                        }

                        // WEITER- / FERTIGSTELLEN-BUTTON
                        Button(
                            onClick = {
                                if (isLastStep) {
                                    onDismiss()
                                } else {
                                    currentStepIndex++
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLastStep) Color(0xFF10B981) else cyanAccent,
                                contentColor = if (isLastStep) Color.White else Color(0xFF00363F)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp).testTag("btn_tour_next")
                        ) {
                            Text(
                                text = if (isLastStep) "Fertig" else "Weiter",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
