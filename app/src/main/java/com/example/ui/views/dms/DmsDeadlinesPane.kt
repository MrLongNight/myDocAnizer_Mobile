package com.example.ui.views.dms

import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.DocumentEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reiter: Fristen- & Kalender-Zentrale
 * Verträge, Kündigungsfristen, Laufzeiten mit Dringlichkeits-Farbcodierung & Google-Kalender Export
 */
@Composable
fun DmsDeadlinesPane(
    documentsWithDeadlines: List<DocumentEntity>,
    calendarIntegrationMode: String,
    onSelectDocument: (DocumentEntity) -> Unit,
    onSetCalendarMode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN)
    val now = System.currentTimeMillis()

    val urgentCount = documentsWithDeadlines.count {
        val target = it.cancellationDeadline ?: it.contractEndDate ?: 0L
        target in now..(now + 30L * 24L * 60L * 60L * 1000L)
    }

    val totalCosts = documentsWithDeadlines.mapNotNull { it.amount }.sum()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // Übersichtskarte mit Dringlichkeits-Zähler & Kalender-Modus
        item(key = "calendar_summary_card") {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Verträge & Fristen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (urgentCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.error
                            ) {
                                Text(
                                    text = "$urgentCount dringend",
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Laufende Verträge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${documentsWithDeadlines.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (totalCosts > 0.0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Erfasste Monats-/Jahresbeiträge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.GERMAN, "%.2f €", totalCosts), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    // Kalender-Integrationsmodus
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Kalender-Synchronisation:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = calendarIntegrationMode == "INTERNAL",
                            onClick = { onSetCalendarMode("INTERNAL") },
                            label = { Text("Nur intern", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = calendarIntegrationMode == "EXTERNAL",
                            onClick = { onSetCalendarMode("EXTERNAL") },
                            label = { Text("Google Kalender", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = calendarIntegrationMode == "BOTH",
                            onClick = { onSetCalendarMode("BOTH") },
                            label = { Text("Beides", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (documentsWithDeadlines.isEmpty()) {
            item(key = "calendar_empty_state") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Keine Fristen oder Termine hinterlegt",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Tippe bei einem beliebigen Dokument im Explorer auf '+ Frist setzen', um Mindestlaufzeiten, Kündigungstermine und Erinnerungen zu hinterlegen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(
                items = documentsWithDeadlines.sortedBy { it.cancellationDeadline ?: it.contractEndDate ?: Long.MAX_VALUE },
                key = { "deadline_${it.id}" }
            ) { doc ->
                val deadlineDate = doc.cancellationDeadline ?: doc.contractEndDate ?: 0L
                val daysRemaining = ((deadlineDate - now) / (1000 * 60 * 60 * 24)).toInt()

                val urgencyColor = when {
                    daysRemaining < 0 -> MaterialTheme.colorScheme.error
                    daysRemaining < 14 -> MaterialTheme.colorScheme.error
                    daysRemaining < 45 -> Color(0xFFF57C00) // Orange
                    else -> MaterialTheme.colorScheme.primary
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectDocument(doc) }
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = doc.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (doc.amount != null && doc.amount > 0.0) {
                                Text(
                                    text = String.format(Locale.GERMAN, "%.2f €", doc.amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            text = "Absender: ${doc.sender.ifBlank { "Unbekannt" }} • ${doc.mainCategory}/${doc.subCategory}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Frist-Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = urgencyColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (daysRemaining < 14) Icons.Default.Warning else Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = urgencyColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (doc.cancellationDeadline != null) {
                                        "Kündigen bis: ${sdf.format(Date(doc.cancellationDeadline))} (${if (daysRemaining >= 0) "noch $daysRemaining Tage" else "Frist abgelaufen"})"
                                    } else {
                                        "Laufzeitende: ${sdf.format(Date(deadlineDate))} (${if (daysRemaining >= 0) "noch $daysRemaining Tage" else "Vorbei"})"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = urgencyColor
                                )
                            }
                        }

                        if (doc.reminderNotes.isNotBlank()) {
                            Text(
                                text = "Hinweis: ${doc.reminderNotes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontStyle = FontStyle.Italic
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val desc = "Kündigungsfrist für ${doc.title} (${doc.sender}). Notiz: ${doc.reminderNotes}"
                                    try {
                                        val intent = Intent(Intent.ACTION_INSERT).apply {
                                            data = CalendarContract.Events.CONTENT_URI
                                            putExtra(CalendarContract.Events.TITLE, "Kündigungsfrist: ${doc.title}")
                                            putExtra(CalendarContract.Events.DESCRIPTION, desc)
                                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, deadlineDate)
                                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, deadlineDate + 3600000L)
                                            putExtra(CalendarContract.Events.ALL_DAY, true)
                                            putExtra(CalendarContract.Events.HAS_ALARM, 1)
                                        }
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Kalender-App nicht gefunden", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("In Google Kalender", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
