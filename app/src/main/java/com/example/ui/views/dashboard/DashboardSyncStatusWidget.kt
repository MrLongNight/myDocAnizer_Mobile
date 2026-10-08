package com.example.ui.views.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.OverallBackupSyncHealth
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.WidgetEditModeHeader

@Composable
fun DashboardSyncStatusWidget(
    viewModel: DocAnizerViewModel,
    isEditMode: Boolean,
    backupSyncHealth: OverallBackupSyncHealth,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column {
            if (isEditMode) {
                WidgetEditModeHeader(
                    title = "3-2-1 Datensicherheits-Score",
                    onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_SECURITY_SCORE") },
                    onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_SECURITY_SCORE") },
                    onHide = { viewModel.setShowSecurityScoreWidget(false) }
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "3-2-1 Datensicherheits-Score",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val score = calculateSecurityScore(backupSyncHealth)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (score >= 70) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$score% GESCHÜTZT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (score >= 70) Color(0xFF10B981) else Color(0xFFF59E0B),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                val syncedCount = (backupSyncHealth.totalConfiguredLocations - backupSyncHealth.unsyncedLocationsCount).coerceAtLeast(0)
                Text(
                    text = "Lokale Hardware-Verschlüsselung (AES-256) aktiv. Konfigurierte Backup-Ziele: $syncedCount von ${backupSyncHealth.totalConfiguredLocations.coerceAtLeast(1)} gesichert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun calculateSecurityScore(health: OverallBackupSyncHealth): Int {
    var score = 50 // Basis-Verschlüsselung AES-256 lokal
    if (health.totalConfiguredLocations > 0) score += 20
    if (health.unsyncedLocationsCount == 0 && health.totalConfiguredLocations > 0) score += 20
    if (!health.hasWarnings) score += 10
    return score.coerceIn(10, 100)
}
