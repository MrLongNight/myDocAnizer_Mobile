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
fun AddPrivateWithdrawalDialog(
    amount: Double,
    monthKey: String,
    onDismiss: () -> Unit,
    onConfirm: (CashTransactionEntity) -> Unit
) {
    var note by remember { mutableStateOf("Privatentnahme Haushalt / Lebenshaltung") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Als Barausgabe eintragen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Möchtest du den Betrag von ${String.format(Locale.GERMANY, "%.2f €", amount)} als Barausgabe (z. B. für Einkäufe ohne Quittung, Restaurant oder Taschengeld) verbuchen?",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Zweck / Notiz (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        CashTransactionEntity(
                            title = if (note.isNotBlank()) note else "Barausgabe / Eigenbedarf",
                            amount = amount,
                            type = CashTransactionType.EXPENSE,
                            category = "Bargeld",
                            note = note,
                            matchReconciliationMonth = monthKey
                        )
                    )
                }
            ) {
                Text("Jetzt eintragen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
