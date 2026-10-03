package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.MainNavigationTab

/**
 * Station der virtuellen Erste-Schritte Tour
 */
data class TourStation(
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badgeLabel: String,
    val description: String,
    val tips: List<String>,
    val targetTab: MainNavigationTab? = null,
    val actionButtonText: String = "Weiter"
)

/**
 * Virtuelle Erste-Schritte Tour für myDocAnizer-Mobile:
 * Zeigt neuen und interessierten Benutzern simpel, interaktiv und effektiv
 * die Kernbedienung der wichtigsten App-Features.
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
                subtitle = "Deine private Aktenablage nach DIN-Aktenplan",
                icon = Icons.Default.FolderSpecial,
                badgeLabel = "Kernbereich • DIN 5008",
                description = "Der Dokumenten Tresor ist das Herzstück von myDocAnizer. Hier werden alle gescannten und importierten Unterlagen nach einem 2-stufigen Aktenplan (Hauptkategorien A01–A08 & Unterkategorien) archiviert.",
                tips = listOf(
                    "Tippe auf einen Ordner, um sofort alle zugehörigen Dokumente zu filtern.",
                    "Nutze die Suchleiste: Sie durchsucht dank Offline-OCR jedes Wort im gesamten Text!",
                    "Über das '+' Symbol kannst du neue Dokumente direkt im gewählten Ordner anlegen."
                ),
                targetTab = MainNavigationTab.DOCANIZER,
                actionButtonText = "Tresor erkunden"
            ),
            TourStation(
                stepNumber = 2,
                title = "Scan InBox & Kamera",
                subtitle = "Dokumente & Belege digitalisieren mit Schärfefilter",
                icon = Icons.Default.DocumentScanner,
                badgeLabel = "Schnellerfassung",
                description = "Digitalisiere Briefe, Kassenbelege oder mehrseitige Verträge in Sekunden. Die Kamera erkennt Papierränder automatisch und korrigiert Schatten.",
                tips = listOf(
                    "Drücke Lautstärke 'Leiser' oder 'Lauter' am Smartphone, um freihändig auszulösen.",
                    "Im Stapel-Modus kannst du 10 Seiten nacheinander scannen – die App bündelt sie automatisch zu einem PDF.",
                    "Alle Scans landen zuerst in der InBox zur schnellen Prüfung vor der endgültigen Ablage."
                ),
                targetTab = MainNavigationTab.SCANNER,
                actionButtonText = "Kamera testen"
            ),
            TourStation(
                stepNumber = 3,
                title = "On-Device KI & Regeln",
                subtitle = "Automatische Erkennung zu 100% offline ohne Cloud",
                icon = Icons.Default.Psychology,
                badgeLabel = "Zero-Cloud KI",
                description = "myDocAnizer liest Texte direkt auf deinem Prozessor. Es erkennt Absender (z.B. Deutsche Post, Telekom), Rechnungsbetrag, Fälligkeitsdatum und ordnet alles selbstständig zu.",
                tips = listOf(
                    "Kein Datenabfluss: Deine sensiblen Dokumente verlassen niemals dein Telefon!",
                    "Wähle in den Einstellungen unter 'Regeln & KI' aus verschiedenen quantisierten HuggingFace LLM-Modellen.",
                    "Eigene Schlagwort-Regeln erfassen wiederkehrende Dokumente blitzschnell und deterministisch."
                ),
                targetTab = MainNavigationTab.SETTINGS,
                actionButtonText = "KI & Regeln verstehen"
            ),
            TourStation(
                stepNumber = 4,
                title = "Dashboard & Finanzen",
                subtitle = "Einnahmen, Ausgaben & Fristen-Radar im Blick",
                icon = Icons.Default.Dashboard,
                badgeLabel = "Überblick & Kontrolle",
                description = "Das Dashboard fasst deinen aktuellen Status zusammen: Welche Rechnungen sind fällig? Wann läuft ein Vertrag aus? Und wie steht es um deine monatlichen Fixkosten?",
                tips = listOf(
                    "Fristen-Radar: Verpasse nie wieder Kündigungstermine oder Zahlungsziele.",
                    "Einnahmen-/Ausgaben-Tracking: Erfasse Barausgaben oder lade Kontoauszüge zum automatischen Beleg-Abgleich.",
                    "Passe Widgets nach deinen Wünschen an: Schalte Anzeigen einfach ein oder aus."
                ),
                targetTab = MainNavigationTab.DASHBOARD,
                actionButtonText = "Dashboard ansehen"
            ),
            TourStation(
                stepNumber = 5,
                title = "Sicherheit & PC-Kopplung",
                subtitle = "Biometrischer Tresorschutz & kabelloser Desktop-Sync",
                icon = Icons.Default.Security,
                badgeLabel = "Datenschutz & P2P",
                description = "Volle Kontrolle über deine Privatsphäre: Schütze deine Akten mit Fingerabdruck oder Passkeys und übertrage Dateien ohne Cloud direkt an deinen PC.",
                tips = listOf(
                    "Aktiviere 'Biometrischen Tresorschutz' in den Einstellungen für sicheres Entsperren.",
                    "Desktop-Kopplung: Scanne einfach den QR-Code der myDocAnizer-Desktop App am PC für lokalen WLAN-Datenaustausch.",
                    "AES-256 Backups auf Google Drive, USB-Stick oder lokales NAS sorgen für absolute Ausfallsicherheit."
                ),
                targetTab = MainNavigationTab.SETTINGS,
                actionButtonText = "Tour abschließen & Loslegen!"
            )
        )
    }

    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    val currentStation = stations[currentStepIndex.coerceIn(0, stations.size - 1)]
    val isLastStep = currentStepIndex == stations.size - 1

    // myDocAnizer Logo-Farben für die Tour
    val cyanAccent = Color(0xFF00E5FF)
    val magentaAccent = Color(0xFFFF007F)
    val violetAccent = Color(0xFFA78BFA)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(22.dp))
                .testTag("first_steps_tour_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(
                1.5.dp,
                Brush.linearGradient(listOf(cyanAccent, magentaAccent))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // KOPFZEILE MIT LOGO, TITEL & SCHLIESSEN-BUTTON
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = cyanAccent.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = cyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Erste Schritte Tour",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Schritt ${currentStepIndex + 1} von ${stations.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_skip_tour")
                    ) {
                        Text("Überspringen", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // FORTSCHRITTS-BALKEN IM LOGO-FARBEN GRADIENT
                LinearProgressIndicator(
                    progress = { (currentStepIndex + 1) / stations.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = cyanAccent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // SCROLLBARER INHALTSBEREICH FÜR DIE AKTUELLE STATION
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // HERO-HERVORHEBUNGS-KARTE
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = magentaAccent.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = currentStation.badgeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = magentaAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = cyanAccent.copy(alpha = 0.2f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = currentStation.icon,
                                            contentDescription = null,
                                            tint = cyanAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = currentStation.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = currentStation.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cyanAccent
                            )

                            Text(
                                text = currentStation.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // INTERAKTIVE TIPPS & PRAXISHILFEN
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "So bedienst du dieses Feature:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            currentStation.tips.forEachIndexed { idx, tip ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = cyanAccent.copy(alpha = 0.2f),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(top = 2.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${idx + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = cyanAccent,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = tip,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 17.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // UNTERE NAVIGATION- & AKTIONEN-LEISTE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(44.dp).testTag("btn_tour_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Zurück")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(44.dp))
                    }

                    // SCHRITT-INDIKATOR DOTS
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        stations.indices.forEach { index ->
                            val isSelected = index == currentStepIndex
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 10.dp else 7.dp)
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
                                currentStation.targetTab?.let { target ->
                                    // Biete optionale direkte Navigation
                                    onNavigateToTab(target)
                                }
                                currentStepIndex++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLastStep) Color(0xFF10B981) else cyanAccent,
                            contentColor = if (isLastStep) Color.White else Color(0xFF00363F)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(44.dp).testTag("btn_tour_next")
                    ) {
                        Text(
                            text = if (isLastStep) "Fertig & Starten" else "Weiter",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
