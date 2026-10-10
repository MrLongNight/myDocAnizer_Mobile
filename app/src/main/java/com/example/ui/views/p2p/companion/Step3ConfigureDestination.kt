package com.example.ui.views.p2p.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun Step3ConfigureDestination(
    autoSortWithAi: Boolean,
    onToggleAutoSort: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Wohin sollen vom PC empfangene Dokumente wandern?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // Option A: Scan InBox (Empfohlen)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (!autoSortWithAi) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(1.5.dp, if (!autoSortWithAi) MaterialTheme.colorScheme.primary else Color.Transparent),
            onClick = { onToggleAutoSort(false) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = !autoSortWithAi, onClick = { onToggleAutoSort(false) })
                Column(modifier = Modifier.weight(1f)) {
                    Text("In die 'Scan InBox' legen (Empfohlen)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Du behältst die volle Kontrolle. Neue Dokumente vom PC landen in der InBox, wo du sie kurz sichten, den KI-Vorschlag prüfen und mit einem Klick archivieren kannst.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Option B: Direkte KI-Autosortierung
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (autoSortWithAi) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            border = BorderStroke(1.5.dp, if (autoSortWithAi) MaterialTheme.colorScheme.primary else Color.Transparent),
            onClick = { onToggleAutoSort(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = autoSortWithAi, onClick = { onToggleAutoSort(true) })
                Column(modifier = Modifier.weight(1f)) {
                    Text("Direkt per KI-Regeln einsortieren (Zero-Touch)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Gescannte Dokumente werden sofort vom lokalen KI Agenten analysiert und nach deinen Schlagwort-Regeln automatisch in die passenden Zielordner verschoben.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
