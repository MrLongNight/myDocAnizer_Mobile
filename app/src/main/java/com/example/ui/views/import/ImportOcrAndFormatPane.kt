package com.example.ui.views.import

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ImportOcrAndFormatPane(
    targetFormat: String,
    onTargetFormatChange: (String) -> Unit,
    isColorMode: Boolean,
    onColorModeChange: (Boolean) -> Unit,
    runOcr: Boolean,
    onRunOcrChange: (Boolean) -> Unit,
    isOcrRunning: Boolean,
    recognizedOcrText: String,
    onOcrTextChanged: (String) -> Unit,
    isOcrExpanded: Boolean,
    onToggleOcrExpanded: () -> Unit,
    onStartOcr: () -> Unit,
    showImageFormatOptions: Boolean,
    canRunOcr: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showImageFormatOptions) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Zielformat für Bilddatei",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = targetFormat == "PDF",
                            onClick = { onTargetFormatChange("PDF") },
                            label = { Text("Als PDF archivieren") },
                            leadingIcon = {
                                if (targetFormat == "PDF") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = targetFormat == "IMAGE",
                            onClick = { onTargetFormatChange("IMAGE") },
                            label = { Text("Als Bilddatei (JPG)") },
                            leadingIcon = {
                                if (targetFormat == "IMAGE") Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isColorMode) "Farbmodus: Original-Farbe" else "Farbmodus: S/W Optimiert",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = isColorMode,
                            onCheckedChange = onColorModeChange
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OCR-Textanalyse (bei Bedarf)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = runOcr,
                        onCheckedChange = onRunOcrChange
                    )
                }

                Text(
                    text = "Liest Volltext aus dem Bild aus, um es im DocAnizer vollständig durchsuchbar zu machen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (runOcr && canRunOcr) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onStartOcr,
                            enabled = !isOcrRunning
                        ) {
                            if (isOcrRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Analysiere Text...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (recognizedOcrText.isBlank()) "OCR jetzt starten" else "OCR neu analysieren")
                            }
                        }

                        if (recognizedOcrText.isNotBlank()) {
                            TextButton(onClick = onToggleOcrExpanded) {
                                Text(if (isOcrExpanded) "Text einklappen" else "Text anzeigen (${recognizedOcrText.length} Z.)")
                            }
                        }
                    }

                    AnimatedVisibility(visible = isOcrExpanded && recognizedOcrText.isNotBlank()) {
                        OutlinedTextField(
                            value = recognizedOcrText,
                            onValueChange = onOcrTextChanged,
                            label = { Text("Erkannter OCR-Text (editierbar)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 220.dp),
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
