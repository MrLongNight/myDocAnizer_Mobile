package com.example.ui.views.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CashTransactionEntity
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.dashboard.custom.DashboardElementEditBar
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardCashTrackerWidget(
    viewModel: DocAnizerViewModel,
    isEditMode: Boolean,
    cashTransactions: List<CashTransactionEntity>,
    currencyFormat: NumberFormat,
    pinWidgetToHomescreen: (Class<*>) -> Unit,
    cashTrackerWidgetClass: Class<*>,
    onOpenQuickAddExpense: (Double) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
    ) {
        Column {
            if (isEditMode) {
                DashboardElementEditBar(
                    title = "Bargeld-Tracker",
                    onMoveUp = { viewModel.moveDashboardWidgetUp("STANDARD_CASH_TRACKER") },
                    onMoveDown = { viewModel.moveDashboardWidgetDown("STANDARD_CASH_TRACKER") },
                    onHide = { viewModel.setShowCashTrackerWidget(false) }
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val currentMonthCashExpenses = remember(cashTransactions) {
                    val cal = Calendar.getInstance()
                    val curMonth = SimpleDateFormat("yyyy-MM", Locale.GERMANY).format(cal.time)
                    cashTransactions.filter { tx ->
                        cal.timeInMillis = tx.date
                        SimpleDateFormat("yyyy-MM", Locale.GERMANY).format(cal.time) == curMonth
                    }.sumOf { it.amount }
                }

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
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Bargeld-Tracker",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Diesen Monat bar ausgegeben: ${currencyFormat.format(currentMonthCashExpenses)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AssistChip(
                        onClick = { pinWidgetToHomescreen(cashTrackerWidgetClass) },
                        label = { Text("Homescreen", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.height(28.dp)
                    )
                }

                Text(
                    text = "Barausgabe schnell erfassen:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5.0, 10.0, 20.0, 50.0).forEach { amt ->
                        FilledTonalButton(
                            onClick = { onOpenQuickAddExpense(amt) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("-${amt.toInt()} €", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    FilledTonalButton(
                        onClick = { onOpenQuickAddExpense(0.0) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+ Frei", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
