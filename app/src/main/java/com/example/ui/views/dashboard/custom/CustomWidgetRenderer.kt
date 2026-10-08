package com.example.ui.views.dashboard.custom

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChecklistItem
import com.example.model.CustomDashboardWidget
import com.example.model.CustomDashboardWidgetType
import com.example.model.DocumentEntity
import com.example.model.ElementPeriodScope
import java.text.NumberFormat
import java.util.Locale

/**
 * Wandelt Farb-Skin Namen in Composable UI Color um
 */
fun getWidgetAccentColor(colorSkin: String): Color {
    return when (colorSkin.uppercase()) {
        "MYDOCANIZER" -> Color(0xFF00E5FF)
        "BLUE" -> Color(0xFF3B82F6)
        "EMERALD" -> Color(0xFF10B981)
        "AMBER" -> Color(0xFFF59E0B)
        "ROSE" -> Color(0xFFD946EF)
        "PURPLE" -> Color(0xFF8B5CF6)
        "SLATE" -> Color(0xFF64748B)
        else -> Color(0xFF00E5FF)
    }
}

/**
 * Wandelt Icon-Namen in ImageVector um
 */
fun getWidgetIconVector(iconName: String): ImageVector {
    return when (iconName.lowercase()) {
        "folder", "folder_open", "folder_special" -> Icons.Default.FolderOpen
        "label", "bookmark" -> Icons.Default.Label
        "analytics", "bar_chart" -> Icons.Default.Analytics
        "edit_note" -> Icons.Default.EditNote
        "checklist", "playlist_add_check" -> Icons.Default.PlaylistAddCheck
        "savings", "account_balance_wallet" -> Icons.Default.Savings
        "shield" -> Icons.Default.Shield
        "star" -> Icons.Default.Star
        "event" -> Icons.Default.Event
        "notifications_active" -> Icons.Default.NotificationsActive
        "history" -> Icons.Default.History
        "pie_chart" -> Icons.Default.PieChart
        "sync_alt" -> Icons.Default.SyncAlt
        "bolt" -> Icons.Default.Tune
        else -> Icons.Default.DashboardCustomize
    }
}

/**
 * Kompakte Zeitraum-Scope Auswahl-Pille für Dashboard Elemente
 */
@Composable
fun ElementPeriodScopeChip(
    currentScope: ElementPeriodScope,
    onScopeChange: (ElementPeriodScope) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.clickable { showMenu = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = currentScope.shortLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            ElementPeriodScope.values().forEach { scope ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(scope.label, fontWeight = if (scope == currentScope) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                            Text(scope.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        }
                    },
                    onClick = {
                        onScopeChange(scope)
                        showMenu = false
                    },
                    leadingIcon = {
                        if (scope == currentScope) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                )
            }
        }
    }
}

/**
 * Reorder- und Aktions-Leiste im Bearbeitungsmodus jedes Dashboard Elements
 */
@Composable
fun DashboardElementEditBar(
    title: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (onConfigure != null) {
                    IconButton(onClick = onConfigure, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Konfigurieren", modifier = Modifier.size(14.dp), tint = accentColor)
                    }
                }

                IconButton(onClick = onMoveUp, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Nach oben verschieben", modifier = Modifier.size(14.dp), tint = accentColor)
                }

                IconButton(onClick = onMoveDown, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Nach unten verschieben", modifier = Modifier.size(14.dp), tint = accentColor)
                }

                IconButton(onClick = onHide, modifier = Modifier.size(26.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Ausblenden", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Composable zur Darstellung eines individuellen Dashboard Elements
 */
@Composable
fun RenderCustomDashboardElementCard(
    widget: CustomDashboardWidget,
    allDocuments: List<DocumentEntity>,
    isEditMode: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
    onEdit: () -> Unit,
    onScopeChange: (ElementPeriodScope) -> Unit,
    onUpdateChecklist: (List<ChecklistItem>) -> Unit,
    onUpdateNote: (String) -> Unit,
    onNavigateToCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = getWidgetAccentColor(widget.colorSkin)
    val currFmt = remember { NumberFormat.getCurrencyInstance(Locale.GERMANY) }

    // Dokumente nach dem element-spezifischen Zeitraum filtern!
    val scopedDocs = remember(allDocuments, widget.periodScope) {
        widget.periodScope.filterDocuments(allDocuments)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column {
            if (isEditMode) {
                DashboardElementEditBar(
                    title = "Eigenes Element: ${widget.title}",
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onHide = onHide,
                    onConfigure = onEdit,
                    accentColor = accentColor
                )
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header mit Zeitraum-Chip
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
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(accentColor.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getWidgetIconVector(widget.iconName),
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = widget.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (widget.subtitle.isNotBlank()) {
                                Text(
                                    text = widget.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ElementPeriodScopeChip(
                            currentScope = widget.periodScope,
                            onScopeChange = onScopeChange
                        )

                        IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                // Inhaltsbereich je nach Typ
                when (widget.type) {
                    CustomDashboardWidgetType.FILTER_DOCUMENTS -> {
                        FilterDocumentsWidgetContent(
                            widget = widget,
                            scopedDocs = scopedDocs,
                            accentColor = accentColor,
                            currFmt = currFmt,
                            onNavigateToCategory = onNavigateToCategory
                        )
                    }

                    CustomDashboardWidgetType.TAG_FILTER -> {
                        TagFilterWidgetContent(
                            widget = widget,
                            scopedDocs = scopedDocs
                        )
                    }

                    CustomDashboardWidgetType.STAT_COUNTER -> {
                        StatCounterWidgetContent(
                            widget = widget,
                            scopedDocs = scopedDocs,
                            accentColor = accentColor,
                            currFmt = currFmt
                        )
                    }

                    CustomDashboardWidgetType.NOTE_MEMO -> {
                        NoteMemoWidgetContent(
                            widget = widget,
                            accentColor = accentColor,
                            onUpdateNote = onUpdateNote
                        )
                    }

                    CustomDashboardWidgetType.CHECKLIST -> {
                        ChecklistWidgetContent(
                            widget = widget,
                            onUpdateChecklist = onUpdateChecklist
                        )
                    }

                    CustomDashboardWidgetType.BUDGET_GOAL -> {
                        BudgetGoalWidgetContent(
                            widget = widget,
                            scopedDocs = scopedDocs,
                            accentColor = accentColor,
                            currFmt = currFmt
                        )
                    }
                }
            }
        }
    }
}
