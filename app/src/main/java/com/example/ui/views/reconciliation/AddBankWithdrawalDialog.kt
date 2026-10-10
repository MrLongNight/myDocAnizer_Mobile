package com.example.ui.views.reconciliation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.BankStatementEntryEntity

@Composable
fun AddBankWithdrawalDialog(
    defaultMonthKey: String,
    onDismiss: () -> Unit,
    onSave: (BankStatementEntryEntity) -> Unit
) {
    var text by remember { mutableStateOf("Geldautomat Barabhebung") }
    var amountStr by remember { mutableStateOf("200.00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kontoauszug-Abhebung eintragen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Buchungstext (z.B. Geldautomat Sparkasse)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.replace(',', '.') },
                    label = { Text("Abgehobener Betrag in € (z.B. 200.00)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onSave(
                            BankStatementEntryEntity(
                                date = System.currentTimeMillis(),
                                bookingText = text,
                                purpose = "Bargeldauszahlung",
                                amount = -amt,
                                isCashWithdrawal = true,
                                monthYear = defaultMonthKey
                            )
                        )
                    }
                }
            ) {
                Text("Eintragen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
