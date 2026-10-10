package com.example.ui.views.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DocumentEntity
import com.example.model.ElementPeriodScope
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.dashboard.custom.DashboardElementEditBar
import com.example.ui.views.dashboard.custom.ElementPeriodScopeChip

@Composable
fun DashboardCategoryPieWidget(
    viewModel: DocAnizerViewModel,
    isEditMode: Boolean,
    allDocuments: List<DocumentEntity>,
    categoryScope: ElementPeriodScope,
    catDocs: List<DocumentEntity>,
    now: Long
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column {
            if (isEditMode) {
                DashboardElementEditBar(
                    title = "Kategorien-Verteilung",
                    onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_CATEGORY_PIE") },
                    onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_CATEGORY_PIE") },
                    onHide = { viewModel.setShowCategoryDistributionWidget(false) }
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Kategorien-Verteilung", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    ElementPeriodScopeChip(
                        currentScope = categoryScope,
                        onScopeChange = { viewModel.setElementPeriodScope("STANDARD_CATEGORY_PIE", it) }
                    )
                }
                val categoryStats = remember(catDocs) {
                    catDocs.groupBy { it.mainCategory.ifBlank { "Allgemein" } }.mapValues { it.value.size }.toList().sortedByDescending { it.second }
                }
                if (categoryStats.isNotEmpty()) {
                    CategoryDistributionBar(
                        stats = categoryStats,
                        total = catDocs.size,
                        modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}
