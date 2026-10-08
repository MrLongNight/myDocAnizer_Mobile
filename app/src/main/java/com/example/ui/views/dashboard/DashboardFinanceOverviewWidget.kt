package com.example.ui.views.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DocumentEntity
import com.example.model.ElementPeriodScope
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.ElementPeriodScopeChip
import com.example.ui.views.ExpenseBarChart
import com.example.ui.views.WidgetEditModeHeader
import java.text.NumberFormat
import java.util.*

@Composable
fun DashboardFinanceOverviewWidget(
    viewModel: DocAnizerViewModel,
    isEditMode: Boolean,
    financeScope: ElementPeriodScope,
    financeDocs: List<DocumentEntity>,
    financeTotalAmount: Double,
    calculateMonthlyAmounts: (List<DocumentEntity>) -> List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.GERMANY) }

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
                    title = "Finanz- & Ausgaben-Verlauf",
                    onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_FINANCE_CHART") },
                    onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_FINANCE_CHART") },
                    onHide = { viewModel.setShowFinanceWidget(false) }
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Finanz- & Ausgaben-Verlauf",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ElementPeriodScopeChip(
                            currentScope = financeScope,
                            onScopeChange = { viewModel.setElementPeriodScope("STANDARD_FINANCE_CHART", it) }
                        )

                        Text(
                            text = currencyFormat.format(financeTotalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }

                val monthlyBuckets = remember(financeDocs) {
                    calculateMonthlyAmounts(financeDocs)
                }

                ExpenseBarChart(
                    buckets = monthlyBuckets,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }
    }
}
