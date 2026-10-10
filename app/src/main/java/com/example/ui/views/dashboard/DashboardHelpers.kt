package com.example.ui.views.dashboard

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.DocumentEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

fun calculateMonthlyAmounts(documents: List<DocumentEntity>): List<Pair<String, Double>> {
    val cal = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("MMM", Locale.GERMANY)
    val map = LinkedHashMap<String, Double>()
    
    for (i in 5 downTo 0) {
        cal.time = Date()
        cal.add(Calendar.MONTH, -i)
        val monthKey = dateFormat.format(cal.time)
        map[monthKey] = 0.0
    }
    
    documents.forEach { doc ->
        val amount = doc.amount ?: 0.0
        if (amount > 0.0) {
            cal.timeInMillis = doc.createdAt
            val monthKey = dateFormat.format(cal.time)
            if (map.containsKey(monthKey)) {
                map[monthKey] = map[monthKey]!! + amount
            }
        }
    }
    return map.toList()
}

@Composable
fun ExpenseBarChart(
    buckets: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val maxVal = (buckets.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
    val primaryColor = MaterialTheme.colorScheme.primary
    val barColor = Color(0xFF10B981)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEach { (_, amount) ->
                val fraction = (amount / maxVal).toFloat().coerceIn(0.05f, 1f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(barColor, primaryColor)
                                )
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            buckets.forEach { (label, _) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CategoryDistributionBar(
    stats: List<Pair<String, Int>>,
    total: Int,
    modifier: Modifier = Modifier
) {
    if (total <= 0 || stats.isEmpty()) return
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFF10B981),
        Color(0xFFF59E0B),
        Color(0xFFEF4444)
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
        ) {
            stats.forEachIndexed { index, (_, count) ->
                val weight = count.toFloat() / total.toFloat()
                if (weight > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(colors[index % colors.size])
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            stats.take(3).forEachIndexed { index, (catName, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(colors[index % colors.size], CircleShape)
                    )
                    Text(
                        text = "$catName ($count)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ExportReportDialog(
    context: Context,
    allDocuments: List<DocumentEntity>,
    activeDeadlines: List<DocumentEntity>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Share, contentDescription = null) },
        title = { Text("Bericht & Export") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exportiere eine Zusammenfassung aller Dokumente und aktiven Fristen als Bericht.")
                Text("Gesamte Dokumente: ${allDocuments.size}", style = MaterialTheme.typography.bodySmall)
                Text("Aktive Fristen: ${activeDeadlines.size}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val file = File(context.cacheDir, "myDocAnizer_Report.txt")
                        file.writeText("myDocAnizer-Mobile Export\nStand: ${Date()}\nDokumente: ${allDocuments.size}\nFristen: ${activeDeadlines.size}")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Bericht teilen"))
                        onDismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export fehlgeschlagen: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Als Text teilen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
