package com.example.ui.views.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val stats by viewModel.stats.collectAsState()
    val todos by viewModel.todos.collectAsState()
    val appointments by viewModel.appointments.collectAsState()
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(stats.chartData) {
        modelProducer.runTransaction {
            columnSeries { series(stats.chartData) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Dokumenten-Tresor Dashboard", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Aktivitäts-Statistik", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    CartesianChartHost(
                        chart = rememberCartesianChart(rememberColumnCartesianLayer()),
                        modelProducer = modelProducer,
                        modifier = Modifier.height(150.dp)
                    )
                }
            }
        }
        item {
            Text("To-Dos", style = MaterialTheme.typography.titleMedium)
        }
        items(todos) { todo ->
            ListItem(
                headlineContent = { Text(todo.title) },
                supportingContent = { todo.docId?.let { Text("Verknüpft: $it") } }
            )
        }
        item {
            Text("Termine", style = MaterialTheme.typography.titleMedium)
        }
        items(appointments) { appt ->
            ListItem(
                headlineContent = { Text(appt.title) },
                supportingContent = { Text(appt.date + if (appt.hasReminder) " (Erinnerung aktiv)" else "") }
            )
        }
    }
}
