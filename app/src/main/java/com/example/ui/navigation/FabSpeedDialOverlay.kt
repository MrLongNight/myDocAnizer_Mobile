package com.example.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.ui.components.TourElementBounds
import com.example.ui.components.TourShapeType

/**
 * Speed-Dial Overlay for Scan & Import actions.
 * Animates both action buttons outward diagonally upward from the FAB origin
 * so that they rest side-by-side above the bottom bar with a smooth Dribbble-style spring bounce.
 */
@Composable
fun FabSpeedDialOverlay(
    isFabExpanded: Boolean,
    onDismiss: () -> Unit,
    onImportClick: () -> Unit,
    onScanClick: () -> Unit,
    onReportElementBounds: ((step: Int, bounds: TourElementBounds) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Bouncy spring animation for Option 1 (Scan - shoots diagonally up-left from FAB)
    val animProgressScan by animateFloatAsState(
        targetValue = if (isFabExpanded) 1f else 0f,
        animationSpec = if (isFabExpanded) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "fab_option_scan_anim"
    )

    // Bouncy spring animation for Option 2 (Import - shoots diagonally up-right from FAB)
    val animProgressImport by animateFloatAsState(
        targetValue = if (isFabExpanded) 1f else 0f,
        animationSpec = if (isFabExpanded) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "fab_option_import_anim"
    )

    AnimatedVisibility(
        visible = isFabExpanded,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Abdunkelnder Scrim (Klick außerhalb schließt das Menü)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onDismiss()
                    }
            )

            // 2. Interaktive Aktions-Karten NEBENEINANDER schräg nach oben über dem FAB
            val density = LocalDensity.current
            val diagonalOffsetY = with(density) { 82.dp.toPx() }
            val diagonalOffsetXScan = with(density) { 72.dp.toPx() }
            val diagonalOffsetXImport = with(density) { (-18).dp.toPx() }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Option 1: Dokument scannen Button (Links in der Reihe, schießt diagonal nach oben-links)
                Surface(
                    onClick = onScanClick,
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = (1f - animProgressScan) * diagonalOffsetXScan
                            translationY = (1f - animProgressScan) * diagonalOffsetY
                            scaleX = animProgressScan.coerceIn(0.1f, 1.15f)
                            scaleY = animProgressScan.coerceIn(0.1f, 1.15f)
                            alpha = animProgressScan.coerceIn(0f, 1f)
                        }
                        .onGloballyPositioned { coords ->
                            if (isFabExpanded) {
                                val bounds = coords.boundsInRoot()
                                onReportElementBounds?.invoke(
                                    2,
                                    TourElementBounds(
                                        centerX = bounds.center.x,
                                        centerY = bounds.center.y,
                                        widthPx = bounds.width,
                                        heightPx = bounds.height,
                                        shapeType = TourShapeType.ROUNDED_RECT
                                    )
                                )
                            }
                        }
                        .testTag("fab_option_scanner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "Dokument scannen",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Dokument scannen",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Option 2: Datei-Import Button (Rechts in der Reihe, schießt diagonal nach oben-rechts)
                Surface(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = (1f - animProgressImport) * diagonalOffsetXImport
                            translationY = (1f - animProgressImport) * diagonalOffsetY
                            scaleX = animProgressImport.coerceIn(0.1f, 1.15f)
                            scaleY = animProgressImport.coerceIn(0.1f, 1.15f)
                            alpha = animProgressImport.coerceIn(0f, 1f)
                        }
                        .testTag("fab_option_import")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Datei importieren",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Datei importieren",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
