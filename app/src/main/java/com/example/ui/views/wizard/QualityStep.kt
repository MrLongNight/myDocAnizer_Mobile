package com.example.ui.views.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PdfSettings
import com.example.ui.DocAnizerViewModel

@Composable
fun StepQualityAndAutoColor(
    viewModel: DocAnizerViewModel,
    pdfSettings: PdfSettings
) {
    var activeInfoDialog by remember { mutableStateOf<String?>(null) }

    if (activeInfoDialog == "COLOR_MODE") {
        AlertDialog(
            onDismissRequest = { activeInfoDialog = null },
            icon = { Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("🎨 Farbmodus-Erkennung", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "• Vollautomatisch: Die App prüft jede Seite. Reiner Text wird platzsparend in Schwarz/Weiß umgewandelt. Farbige Stempel, Logos oder Fotos bleiben originalgetreu erhalten.\n\n" +
                            "• Immer S/W: Erzeugt kleinste PDF-Dateien mit maximalem Kontrast.\n\n" +
                            "• Immer Farbe: Speichert jede Seite exakt so, wie sie fotografiert wurde.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { activeInfoDialog = null }) {
                    Text("Verstanden", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (activeInfoDialog == "OPTIMIZATION") {
        AlertDialog(
            onDismissRequest = { activeInfoDialog = null },
            icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("✨ Auto-Entzerrung & Filter", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Beseitigt automatisch typische Scan-Fehler:\n\n" +
                            "• Schiefe Dokumente werden begradigt (Deskew).\n" +
                            "• Schatten (z.B. durch das Smartphone oder die Hand) werden herausgerechnet.\n" +
                            "• Der Papierhintergrund wird gleichmäßig weiß aufgehellt.",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { activeInfoDialog = null }) {
                    Text("Verstanden", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(
                text = "Bildoptimierung & Farberkennung",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Automatische Entzerrung, Schärfekorrektur und intelligente Farberkennung.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Farbmodus-Einstellung:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                IconButton(
                    onClick = { activeInfoDialog = "COLOR_MODE" },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Info Farbmodus", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }

            val colorOptions = listOf(
                Triple("AUTO", "Vollautomatisch (Empfohlen)", "Text wird kompaktes S/W; Stempel & Logos bleiben farbig"),
                Triple("BW", "Immer Schwarz/Weiß", "Minimale Dateigröße, reiner Kontrast-Text"),
                Triple("COLOR", "Immer Farbe", "Originalgetreue Farbwiedergabe für alle Seiten")
            )

            colorOptions.forEach { (mode, title, desc) ->
                val isSelected = pdfSettings.defaultColorMode == mode
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updatePdfSettings(pdfSettings.copy(defaultColorMode = mode)) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.updatePdfSettings(pdfSettings.copy(defaultColorMode = mode)) },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (mode == "AUTO") {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                }
                                Text(text = title, style = MaterialTheme.typography.bodySmall, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                            }
                            Text(text = desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-Entzerrung & Schattenentfernung", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Auto-Deskew, Kanten glätten und Hintergrund weißen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = pdfSettings.isOptimizationEnabled,
                    onCheckedChange = { viewModel.updatePdfSettings(pdfSettings.copy(isOptimizationEnabled = it)) }
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PDF-Kompression & Dateigröße:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Standard" to "Optimal (Standard)", "Hoch" to "Kompakt (E-Mail)", "Verlustfrei" to "High-Res (300 DPI)").forEach { (level, label) ->
                        val isSel = pdfSettings.compressionLevel == level
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.updatePdfSettings(pdfSettings.copy(compressionLevel = level)) }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 7.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatische Begradigung (Auto-Deskew)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Richtet schief aufliegende Dokumente exakt gerade aus", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = pdfSettings.autoDeskewEnabled,
                    onCheckedChange = { viewModel.updatePdfSettings(pdfSettings.copy(autoDeskewEnabled = it)) }
                )
            }
        }
    }
}
