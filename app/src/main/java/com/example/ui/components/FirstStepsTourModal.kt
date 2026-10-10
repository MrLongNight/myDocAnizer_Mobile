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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.MainNavigationTab

/**
 * Datenmodell für eine Station der interaktiven Erste-Schritte Tour.
 */
data class TourStation(
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val targetElementName: String,
    val step1Menu: String,
    val step2Action: String,
    val icon: ImageVector,
    val badgeLabel: String,
    val description: String,
    val tips: List<String>,
    val targetTab: MainNavigationTab,
    val targetSubTab: Int? = null,
    val actionButtonText: String = "Weiter"
)

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
                step1Menu = "Unten in der Menüleiste auf [Dokumente] tippen",
                step2Action = "Hauptkategorie wählen (z.B. A02 Finanzen) oder oben filtern / suchen",
                icon = Icons.Default.FolderSpecial,
                badgeLabel = "Kernbereich • Dokumenten-Tresor",
                description = "Der Dokumenten Tresor ist der zentrale Ort für deine Ordnerstruktur in myDocAnizer-Mobile. Hier werden alle gescannten und importierten Unterlagen in einer sauberen 2-stufigen Ordnerstruktur strukturiert archiviert.",
                tips = listOf(
                    "Tippe auf einen Ordner, um sofort alle zugehörigen Dokumente zu filtern.",
                    "Nutze die Suchleiste: Sie durchsucht dank Offline-OCR jedes Wort im gesamten Dokument!",
                    "Über das '+' Symbol kannst du neue Dokumente direkt im gewählten Ordner anlegen."
                ),
                targetTab = MainNavigationTab.DOCANIZER,
                targetSubTab = null,
                actionButtonText = "Tresor erkunden"
            ),
            TourStation(
                stepNumber = 2,
                title = "Scan InBox & Kamera",
                subtitle = "Dokumente & Belege digitalisieren mit Schärfefilter",
                targetElementName = "Kamera-Sucher & Schnellauslöser",
                step1Menu = "Unten in der Menüleiste auf [Scan Eingang] tippen",
                step2Action = "Auslöser drücken oder Lautstärketaste am Smartphone nutzen",
                icon = Icons.Default.DocumentScanner,
                badgeLabel = "Schnellerfassung",
                description = "Digitalisiere Briefe, Kassenbelege oder mehrseitige Verträge in Sekunden. Die Kamera erkennt Papierränder automatisch und korrigiert Schatten.",
                tips = listOf(
                    "Drücke Lautstärke 'Leiser' oder 'Lauter' am Smartphone, um freihändig auszulösen.",
                    "Im Stapel-Modus kannst du 10 Seiten nacheinander scannen – die App bündelt sie automatisch zu einem PDF.",
                    "Alle Scans landen zuerst in der InBox zur schnellen Prüfung vor der endgültigen Ablage."
                ),
                targetTab = MainNavigationTab.SCANNER,
                targetSubTab = null,
                actionButtonText = "Kamera verstehen"
            ),
            TourStation(
                stepNumber = 3,
                title = "Lokaler KI Agent & Regeln",
                subtitle = "Automatische Erkennung zu 100% offline ohne Cloud",
                targetElementName = "Lokale On-Device Modelle & Schlagwort-Regeln",
                step1Menu = "Unten in der Menüleiste auf [Einstellungen] tippen",
                step2Action = "Oben im Tab-Menü den Reiter [Lokaler KI Agent] antippen",
                icon = Icons.Default.Psychology,
                badgeLabel = "Lokaler KI Agent",
                description = "Der lokale KI Agent analysiert Texte automatisiert direkt auf dem Mobilprozessor. Er erkennt Absender (z.B. Deutsche Post, Telekom), Rechnungsbetrag, Fälligkeitsdatum und ordnet Belege passend zu.",
                tips = listOf(
                    "Kein Datenabfluss: Deine sensiblen Dokumente verlassen niemals dein Telefon!",
                    "Wähle in den Einstellungen unter 'Lokaler KI Agent' aus verschiedenen quantisierten On-Device Modellen.",
                    "Eigene Schlagwort-Regeln erfassen wiederkehrende Dokumente blitzschnell und deterministisch."
                ),
                targetTab = MainNavigationTab.SETTINGS,
                targetSubTab = 2, // Index 2 in SettingsView: Lokaler KI Agent
                actionButtonText = "KI Agent verstehen"
            ),
            TourStation(
                stepNumber = 4,
                title = "Dashboard & Finanzen",
                subtitle = "Einnahmen, Ausgaben & Fristen-Radar im Blick",
                targetElementName = "Fristen-Radar & monatliches Finanz-Cockpit",
                step1Menu = "Unten in der Menüleiste auf [Dashboard] tippen",
                step2Action = "Widgets für Fristen-Radar, Ausgaben und Schnellaktionen durchscrollen",
                icon = Icons.Default.Dashboard,
                badgeLabel = "Überblick & Kontrolle",
                description = "Das Dashboard fasst deinen aktuellen Status zusammen: Welche Rechnungen sind fällig? Wann läuft ein Vertrag aus? Und wie steht es um deine monatlichen Fixkosten?",
                tips = listOf(
                    "Fristen-Radar: Verpasse nie wieder Kündigungstermine oder Zahlungsziele.",
                    "Einnahmen-/Ausgaben-Tracking: Erfasse Barausgaben oder lade Kontoauszüge zum automatischen Beleg-Abgleich.",
                    "Passe Widgets nach deinen Wünschen an: Schalte Anzeigen einfach ein oder aus."
                ),
                targetTab = MainNavigationTab.DASHBOARD,
                targetSubTab = null,
                actionButtonText = "Dashboard ansehen"
            ),
            TourStation(
                stepNumber = 5,
                title = "Sicherheit & Dual-Sync",
                subtitle = "Biometrischer Tresorschutz & kabelloser PC-Sync",
                targetElementName = "Tresor-Verschlüsselung & Lokale Desktop-Kopplung",
                step1Menu = "Unten in der Menüleiste auf [Einstellungen] tippen",
                step2Action = "Oben im Tab-Menü den Reiter [Sicherheit] oder [WLAN & Dual-Sync] antippen",
                icon = Icons.Default.Security,
                badgeLabel = "Datenschutz & Dual-Sync",
                description = "Volle Kontrolle über deine Privatsphäre: Schütze deine Akten mit Fingerabdruck oder Passkeys und übertrage Dateien ohne Cloud direkt an deinen PC.",
                tips = listOf(
                    "Aktiviere 'Biometrischen Tresorschutz' in den Einstellungen für sicheres Entsperren.",
                    "Dual-Sync PC-Kopplung: Verbinde dich per QR-Code oder LAN direkt mit der Desktop-App.",
                    "AES-256 Backups auf Google Drive, USB-Stick oder lokales NAS sorgen für absolute Ausfallsicherheit."
                ),
                targetTab = MainNavigationTab.SETTINGS,
                targetSubTab = 4, // Index 4: Sicherheit
                actionButtonText = "Tour abschließen & Loslegen!"
            )
        )
    }

    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentStation = stations[currentStepIndex.coerceIn(0, stations.size - 1)]
    val isLastStep = currentStepIndex == stations.size - 1

    var isTourPaused by remember { mutableStateOf(false) }

    fun navigateToStationTarget() {
        onNavigateToTab(currentStation.targetTab)
        currentStation.targetSubTab?.let { subTab ->
            viewModel.setTargetSettingsSubTab(subTab)
        }
    }

    // Synchronisieren bei Schrittwechsel
    LaunchedEffect(currentStepIndex) {
        navigateToStationTarget()
    }

    // Brand-Farben
    val cyanAccent = Color(0xFF00E5FF)
    val magentaAccent = Color(0xFFFF007F)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(999f)
            .testTag("first_steps_tour_dialog")
    ) {
        // HINTERGRUND-ABDECKUNG
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Klick auf Hintergrund: Zeigt Erklärungsbox
                    isTourPaused = !isTourPaused
                }
        )

        // HAUPT-POPUP (UNTEN ANIMIERT)
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(cyanAccent, magentaAccent))),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .heightIn(max = 540.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // HEADER LEISTE
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
                            modifier = Modifier.size(34.dp)
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

                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("btn_skip_tour")
                    ) {
                        Text("Beenden ✕", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // FORTSCHRITTSBALKEN
                LinearProgressIndicator(
                    progress = { (currentStepIndex + 1) / stations.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = cyanAccent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // INHALTS-BEREICH SCROLLBAR
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // PAUSE BANNER (Z.B. KAMERA-BERECHTIGUNG ODER MANUELLE INSPEKTION)
                    if (isTourPaused) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PauseCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text("⏸️ Tour pausiert", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Text("Du kannst die App im Hintergrund in Ruhe testen.", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Button(
                                    onClick = { isTourPaused = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                ) {
                                    Text("Fortsetzen ▶", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // EXAKTE WEGBESCHREIBUNG: KLICK-ANLEITUNG WITH BREADCRUMBS
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.5.dp, cyanAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = null,
                                    tint = cyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Exakte Klick-Anleitung (Wo muss ich klicken?):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = cyanAccent.copy(alpha = 0.2f),
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cyanAccent)
                                            }
                                        }
                                        Text(
                                            text = currentStation.step1Menu,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = magentaAccent.copy(alpha = 0.2f),
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("2", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = magentaAccent)
                                            }
                                        }
                                        Text(
                                            text = currentStation.step2Action,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            // DIVERSE AKTIONEN: DIREKT-SPRINGEN BUTTON
                            Button(
                                onClick = {
                                    navigateToStationTarget()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = cyanAccent, contentColor = Color(0xFF00363F)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🚀 ${currentStation.actionButtonText} (Direkt springen)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // BESCHREIBUNG
                    Text(
                        text = currentStation.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 17.sp
                    )

                    // BEDIEN-TIPPS
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

                // UNTERE NAVIGATION-LEISTE
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
                            onClick = {
                                isTourPaused = !isTourPaused
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(if (isTourPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(14.dp), tint = cyanAccent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isTourPaused) "Fortsetzen" else "Pausieren", fontSize = 12.sp)
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
                            text = if (isLastStep) "Fertig ✓" else "Weiter ➔",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
