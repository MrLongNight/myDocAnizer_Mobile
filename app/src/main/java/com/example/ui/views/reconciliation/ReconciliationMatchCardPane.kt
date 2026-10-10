package com.example.ui.views.reconciliation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BankStatementEntryEntity
import com.example.model.CashTransactionEntity
import com.example.model.DocumentEntity
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ReconciliationMatchCardPane(
    monthBankWithdrawals: List<BankStatementEntryEntity>,
    monthReceiptDocs: List<DocumentEntity>,
    monthCashTx: List<CashTransactionEntity>,
    totalWithdrawals: Double,
    totalReceiptsAndExpenses: Double,
    totalPrivateWithdrawals: Double,
    currencyFormat: NumberFormat,
    onAddBankEntryClick: () -> Unit,
    onImportClick: () -> Unit,
    onAddCashEntryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. SPALTE: KONTOAUSZUG ABHEBUNGEN
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏦 Abhebungen", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = currencyFormat.format(totalWithdrawals),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider()

                if (monthBankWithdrawals.isEmpty()) {
                    Text(
                        text = "Keine Einträge",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val df = SimpleDateFormat("dd.MM.", Locale.GERMANY)
                    monthBankWithdrawals.forEach { w ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${df.format(Date(w.date))} ${w.bookingText.take(10)}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                            Text(
                                text = currencyFormat.format(abs(w.amount)),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddBankEntryClick,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Manuell", fontSize = 10.sp)
                    }

                    FilledTonalButton(
                        onClick = onImportClick,
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Import (CSV)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. SPALTE: BELEGE & BAR-AUSGABEN
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧾 Bar-Belege", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        text = currencyFormat.format(totalReceiptsAndExpenses + totalPrivateWithdrawals),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }

                HorizontalDivider()

                if (monthReceiptDocs.isEmpty() && monthCashTx.isEmpty()) {
                    Text(
                        text = "Keine Belege",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val df = SimpleDateFormat("dd.MM.", Locale.GERMANY)
                    monthReceiptDocs.take(3).forEach { doc ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${df.format(Date(doc.createdAt))} ${doc.title.take(10)}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                            Text(
                                text = currencyFormat.format(doc.amount ?: 0.0),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    monthCashTx.take(3).forEach { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${df.format(Date(tx.date))} ${tx.title.take(10)}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                            Text(
                                text = currencyFormat.format(tx.amount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddCashEntryClick,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bar-Ausgabe +", fontSize = 11.sp)
                }
            }
        }
    }
}
