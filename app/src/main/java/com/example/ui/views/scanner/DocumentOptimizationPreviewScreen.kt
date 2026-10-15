package com.example.ui.views.scanner

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.ImageProcessingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vorschau der automatischen Dokumenten-Optimierung mit Filtern (Auto, S/W, Farbe, Original)
 * und 90°-Drehung vor der endgültigen Ablage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentOptimizationPreviewScreen(
    croppedBitmap: Bitmap,
    onBackToCrop: () -> Unit,
    onConfirmFinalDocument: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentBaseBitmap by remember { mutableStateOf(croppedBitmap) }
    var selectedFilterMode by remember {
        mutableStateOf(ImageProcessingService.DocumentFilterMode.AUTO)
    }
    var isProcessingFilter by remember { mutableStateOf(false) }
    var filteredBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Filter anwenden, sobald sich Basiseingang oder Filtermodus ändert
    LaunchedEffect(currentBaseBitmap, selectedFilterMode) {
        isProcessingFilter = true
        val result = withContext(Dispatchers.Default) {
            ImageProcessingService.applyDocumentFilter(currentBaseBitmap, selectedFilterMode)
        }
        filteredBitmap = result
        isProcessingFilter = false
    }

    val displayBitmap = filteredBitmap ?: currentBaseBitmap

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .testTag("document_optimization_preview_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // OBERE LEISTE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackToCrop,
                    modifier = Modifier.testTag("btn_preview_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück zum Zuschnitt",
                        tint = Color.White
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Dokumenten-Vorschau",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Optimierung & Lesbarkeit prüfen",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val rotated = ImageProcessingService.rotateBitmap(currentBaseBitmap, 90f)
                            currentBaseBitmap = rotated
                        }
                    },
                    modifier = Modifier.testTag("btn_preview_rotate")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "90° drehen",
                        tint = Color(0xFF38BDF8)
                    )
                }
            }

            // BILD-VORSCHAU BEREICH
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = displayBitmap.asImageBitmap(),
                            contentDescription = "Optimiertes Dokument",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )

                        if (isProcessingFilter) {
                            CircularProgressIndicator(
                                color = Color(0xFF10B981),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // FILTER-AUSWAHLLEISTE
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Bildoptimierung:",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ImageProcessingService.DocumentFilterMode.values().forEach { mode ->
                            val isSelected = (mode == selectedFilterMode)
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilterMode = mode },
                                label = {
                                    Text(
                                        text = mode.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF10B981),
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White,
                                    containerColor = Color(0xFF334155),
                                    labelColor = Color.White
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // UNTERE AKTIONEN (SPEICHERN / BESTÄTIGEN)
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onBackToCrop,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("btn_preview_adjust_crop")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Zuschnitt anpassen")
                    }

                    Button(
                        onClick = {
                            onConfirmFinalDocument(displayBitmap)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_preview_save")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dokument übernehmen",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
