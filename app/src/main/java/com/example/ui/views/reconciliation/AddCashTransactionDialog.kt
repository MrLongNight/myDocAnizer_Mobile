package com.example.ui.views.reconciliation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.CashTransactionEntity
import com.example.model.CashTransactionType
import java.util.Locale

@Composable
fun AddCashTransactionDialog(
    defaultMonthKey: String,
    initialAmount: Double = 0.0,
    onDismiss: () -> Unit,
    onSave: (CashTransactionEntity) -> Unit
) {
    var title by remember { mutableStateOf(if (initialAmount > 0) "Bar-Ausgabe" else "") }
    var amountStr by remember { mutableStateOf(if (initialAmount > 0) String.format(Locale.GERMANY, "%.2f", initialAmount) else "") }
    var category by remember { mutableStateOf("Tagesausgaben") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bar-Ausgabe / Quittung erfassen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Bezeichnung / Zweck (z.B. Tanken, Porto)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it.replace(',', '.') },
                    label = { Text("Betrag in € (z.B. 45.50)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Kategorie") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz / Quittungsnummer (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amt > 0.0) {
                        onSave(
                            CashTransactionEntity(
                                title = title,
                                amount = amt,
                                type = CashTransactionType.EXPENSE,
                                category = category,
                                note = note,
                                matchReconciliationMonth = defaultMonthKey
                            )
                        )
                    }
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
