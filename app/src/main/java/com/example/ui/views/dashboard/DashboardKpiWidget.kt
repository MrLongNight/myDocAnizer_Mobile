package com.example.ui.views.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DocumentEntity
import com.example.model.ElementPeriodScope
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.dashboard.custom.DashboardElementEditBar
import com.example.ui.views.dashboard.custom.ElementPeriodScopeChip
import java.text.NumberFormat
import java.util.*

@Composable
fun DashboardKpiWidget(
    viewModel: DocAnizerViewModel,
    isEditMode: Boolean,
    kpiScope: ElementPeriodScope,
    kpiDocs: List<DocumentEntity>,
    kpiTotalDocCount: Int,
    kpiTotalAmount: Double,
    activeDeadlines: List<DocumentEntity>,
    currencyFormat: NumberFormat
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isEditMode) {
            DashboardElementEditBar(
                title = "KPI-Zusammenfassung",
                onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_KPI") },
                onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_KPI") },
                onHide = { viewModel.setShowKpiWidgets(false) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KENNZAHLEN-ÜBERSICHT",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            ElementPeriodScopeChip(
                currentScope = kpiScope,
                onScopeChange = { viewModel.setElementPeriodScope("STANDARD_KPI", it) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KpiStatCard(
                title = "Dokumente",
                value = "$kpiTotalDocCount",
                subtext = "${kpiDocs.count { it.isSynced }} gesichert",
                icon = Icons.Default.Folder,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            KpiStatCard(
                title = "Belegsumme",
                value = currencyFormat.format(kpiTotalAmount),
                subtext = "${kpiDocs.count { (it.amount ?: 0.0) > 0 }} Belege",
                icon = Icons.Default.AccountBalanceWallet,
                accentColor = Color(0xFF10B981),
                modifier = Modifier.weight(1f)
            )

            KpiStatCard(
                title = "Fristen",
                value = "${activeDeadlines.size}",
                subtext = if (activeDeadlines.isEmpty()) "Keine fällig" else "Aktiv überwacht",
                icon = Icons.Default.Event,
                accentColor = if (activeDeadlines.isNotEmpty()) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
