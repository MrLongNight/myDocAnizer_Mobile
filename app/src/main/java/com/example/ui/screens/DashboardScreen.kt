package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.CustomDashboardWidget
import com.example.model.CustomDashboardWidgetType

@Composable
fun DashboardScreen(
    widgets: List<CustomDashboardWidget>,
    onToggleTodo: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        items(widgets) { widget ->
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = widget.title, style = MaterialTheme.typography.titleMedium)
                    if (widget.type == CustomDashboardWidgetType.CHECKLIST) {
                        widget.checklistItems.forEach { item ->
                            Row {
                                Checkbox(
                                    checked = item.isDone,
                                    onCheckedChange = { onToggleTodo(widget.id, item.id) }
                                )
                                Text(text = item.text)
                            }
                        }
                    }
                }
            }
        }
    }
}
