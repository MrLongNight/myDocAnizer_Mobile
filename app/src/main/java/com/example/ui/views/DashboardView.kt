package com.example.ui.views

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CustomDashboardWidget
import com.example.model.DocumentEntity
import com.example.model.ElementPeriodScope
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.dashboard.*
import com.example.ui.views.reconciliation.*
import com.example.widget.BargeldTrackerWidget
import com.example.widget.BelegQuickScanWidget
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

enum class DashboardPeriod(val label: String) {
    ALL("Gesamt"),
    YEAR("Dieses Jahr"),
    MONTH("Dieser Monat")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardView(
    viewModel: DocAnizerViewModel,
    onNavigateToScan: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allDocuments by viewModel.allDocuments.collectAsState()
    val contractDeadlines by viewModel.contractDeadlines.collectAsState()
    val backupSyncHealth by viewModel.backupSyncHealth.collectAsState()

    // Standard Widget Sichtbarkeiten
    val showBelegQuickScanWidget by viewModel.showBelegQuickScanWidget.collectAsState()
    val showCashTrackerWidget by viewModel.showCashTrackerWidget.collectAsState()
    val showKpiWidgets by viewModel.showKpiWidgets.collectAsState()
    val showDeadlinesWidget by viewModel.showDeadlinesWidget.collectAsState()
    val showFinanceWidget by viewModel.showFinanceWidget.collectAsState()
    val showReconciliationWidget by viewModel.showReconciliationWidget.collectAsState()
    val showCategoryDistributionWidget by viewModel.showCategoryDistributionWidget.collectAsState()
    val showSecurityScoreWidget by viewModel.showSecurityScoreWidget.collectAsState()
    val showCustomFieldsWidget by viewModel.showCustomFieldsWidget.collectAsState()
    val showRecentDocsWidget by viewModel.showRecentDocsWidget.collectAsState()
    val showFolderShortcutsWidget by viewModel.showFolderShortcutsWidget.collectAsState()
    val showInboxDocsWidget by viewModel.showInboxDocsWidget.collectAsState()
    val showBudgetWatchWidget by viewModel.showBudgetWatchWidget.collectAsState()
    val showQuickActionsWidget by viewModel.showQuickActionsWidget.collectAsState()
    val showQuickNoteWidget by viewModel.showQuickNoteWidget.collectAsState()
    val quickNoteText by viewModel.quickNoteText.collectAsState()

    // Individuelle Dashboard Elemente, Scopes & Sortierung
    val customWidgets by viewModel.customDashboardWidgets.collectAsState()
    val widgetOrder by viewModel.dashboardWidgetOrder.collectAsState()
    val elementPeriodScopes by viewModel.elementPeriodScopes.collectAsState()

    val customFields by viewModel.customFields.collectAsState()
    val allCustomFieldValues by viewModel.allDocumentCustomFieldValues.collectAsState()
    val cashTransactions by viewModel.cashTransactions.collectAsState()

    var isEditMode by remember { mutableStateOf(false) }
    var showTemplatesCatalogDialog by remember { mutableStateOf(false) }
    var showCreateCustomWidgetDialog by remember { mutableStateOf(false) }
    var editingCustomWidget by remember { mutableStateOf<CustomDashboardWidget?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showReconciliationDialog by remember { mutableStateOf(false) }
    var showBankImportDialog by remember { mutableStateOf(false) }
    var showQuickAddCashExpenseDialog by remember { mutableStateOf(false) }
    var quickExpenseInitialAmount by remember { mutableStateOf(0.0) }

    val pinWidgetToHomescreen: (Class<*>) -> Unit = { providerClass ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = context.getSystemService(AppWidgetManager::class.java)
            if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                val provider = ComponentName(context, providerClass)
                appWidgetManager.requestPinAppWidget(provider, null, null)
                Toast.makeText(context, "Android Homescreen-Widget wird hinzugefügt", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Tippe lange auf deinen Homescreen, um das Widget hinzuzufügen", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Tippe lange auf deinen Homescreen, um das Widget hinzuzufügen", Toast.LENGTH_LONG).show()
        }
    }

    val directOpenCashTracker by viewModel.directOpenCashTracker.collectAsState()
    val directOpenReconciliation by viewModel.directOpenReconciliation.collectAsState()

    LaunchedEffect(directOpenCashTracker, directOpenReconciliation) {
        if (directOpenCashTracker || directOpenReconciliation) {
            showReconciliationDialog = true
            viewModel.setDirectOpenCashTracker(false)
            viewModel.setDirectOpenReconciliation(false)
        }
    }

    val now = System.currentTimeMillis()

    val kpiScope = elementPeriodScopes["STANDARD_KPI"] ?: ElementPeriodScope.ALL
    val kpiDocs = remember(allDocuments, kpiScope) { kpiScope.filterDocuments(allDocuments, now) }
    val kpiTotalDocCount = kpiDocs.size
    val kpiTotalAmount = kpiDocs.mapNotNull { it.amount }.sum()

    val financeScope = elementPeriodScopes["STANDARD_FINANCE_CHART"] ?: ElementPeriodScope.YEAR
    val financeDocs = remember(allDocuments, financeScope) { financeScope.filterDocuments(allDocuments, now) }
    val financeTotalAmount = financeDocs.mapNotNull { it.amount }.sum()

    val deadlinesScope = elementPeriodScopes["STANDARD_DEADLINES_RADAR"] ?: ElementPeriodScope.ALL
    val deadlinesDocs = remember(contractDeadlines, deadlinesScope) {
        deadlinesScope.filterDocuments(contractDeadlines, now)
    }
    val activeDeadlines = deadlinesDocs.filter {
        val deadline = it.cancellationDeadline ?: it.contractEndDate
        deadline != null && deadline >= now - 86400000L * 2
    }

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale.GERMANY).apply {
            maximumFractionDigits = 2
        }
    }

    val categories = remember(allDocuments) {
        allDocuments.map { it.mainCategory.ifBlank { "Allgemein" } }.distinct()
    }

    val activeOrderedWidgetIds = remember(
        widgetOrder,
        showKpiWidgets,
        showRecentDocsWidget,
        showQuickActionsWidget,
        showDeadlinesWidget,
        showFinanceWidget,
        showSecurityScoreWidget,
        showBelegQuickScanWidget,
        showCashTrackerWidget,
        showReconciliationWidget,
        showCategoryDistributionWidget,
        showCustomFieldsWidget,
        showFolderShortcutsWidget,
        showInboxDocsWidget,
        showBudgetWatchWidget,
        showQuickNoteWidget,
        customWidgets
    ) {
        val result = mutableListOf<String>()
        val customMap = customWidgets.associateBy { it.id }

        widgetOrder.forEach { id ->
            val isEnabled = when (id) {
                "STANDARD_KPI" -> showKpiWidgets
                "STANDARD_RECENT_DOCS" -> showRecentDocsWidget
                "STANDARD_QUICK_ACTIONS" -> showQuickActionsWidget
                "STANDARD_DEADLINES_RADAR", "STANDARD_DEADLINES_MONTH" -> showDeadlinesWidget
                "STANDARD_FINANCE_CHART" -> showFinanceWidget
                "STANDARD_SECURITY_SCORE" -> showSecurityScoreWidget
                "STANDARD_QUICK_SCAN" -> showBelegQuickScanWidget
                "STANDARD_CASH_TRACKER" -> showCashTrackerWidget
                "STANDARD_RECONCILIATION" -> showReconciliationWidget
                "STANDARD_CATEGORY_PIE" -> showCategoryDistributionWidget
                "STANDARD_CUSTOM_FIELDS" -> showCustomFieldsWidget
                "STANDARD_FOLDER_SHORTCUTS" -> showFolderShortcutsWidget
                "STANDARD_INBOX_DOCS" -> showInboxDocsWidget
                "STANDARD_BUDGET_WATCH" -> showBudgetWatchWidget
                "STANDARD_QUICK_NOTE" -> showQuickNoteWidget
                else -> customMap[id]?.isEnabled == true
            }
            if (isEnabled) {
                result.add(id)
            }
        }

        customWidgets.filter { it.isEnabled && !result.contains(it.id) }.forEach {
            result.add(it.id)
        }

        result
    }

    BackHandler(enabled = isEditMode) {
        isEditMode = false
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("dashboard_view"),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. HEADER
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Dashboard",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Individuelles Cockpit & Vorlagen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isEditMode = !isEditMode },
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Tune,
                                contentDescription = if (isEditMode) "Bearbeitung beenden" else "Anordnung anpassen",
                                tint = if (isEditMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { showTemplatesCatalogDialog = true },
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = "Vorlagen-Katalog & Elemente",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bericht & Export",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (isEditMode) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Anordnungs-Modus aktiv",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = "Verschiebe Elemente mit ▲/▼ oder blende sie mit ✕ aus.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { isEditMode = false },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Fertig", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AssistChip(
                        onClick = { showTemplatesCatalogDialog = true },
                        label = { Text("Vorlagen-Katalog", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary)
                    )

                    AssistChip(
                        onClick = { showCreateCustomWidgetDialog = true },
                        label = { Text("+ Eigenes Element", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.secondary)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "${activeOrderedWidgetIds.size} aktiv",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. WIDGETS
        if (activeOrderedWidgetIds.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Dein Dashboard ist aktuell leer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Wähle fertige Vorlagen aus dem Katalog (Dokumente, Finanzen, Fristen, Sicherheit) oder erstelle eigene persönliche Elemente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { showTemplatesCatalogDialog = true }) {
                                Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Vorlagen öffnen")
                            }
                            OutlinedButton(onClick = { showCreateCustomWidgetDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Eigenes Element")
                            }
                        }
                    }
                }
            }
        } else {
            activeOrderedWidgetIds.forEach { widgetId ->
                item(key = widgetId) {
                    when (widgetId) {
                        "STANDARD_KPI" -> {
                            DashboardKpiWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                kpiScope = kpiScope,
                                kpiDocs = kpiDocs,
                                kpiTotalDocCount = kpiTotalDocCount,
                                kpiTotalAmount = kpiTotalAmount,
                                activeDeadlines = activeDeadlines,
                                currencyFormat = currencyFormat
                            )
                        }

                        "STANDARD_RECENT_DOCS" -> {
                            DashboardRecentDocsWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                allDocuments = allDocuments
                            )
                        }

                        "STANDARD_QUICK_ACTIONS" -> {
                            DashboardActionShortcutsWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                onNavigateToScan = onNavigateToScan,
                                onNavigateToImport = onNavigateToImport
                            )
                        }

                        "STANDARD_QUICK_SCAN" -> {
                            DashboardQuickScanWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                onNavigateToScan = onNavigateToScan,
                                onNavigateToImport = onNavigateToImport,
                                pinWidgetToHomescreen = pinWidgetToHomescreen,
                                quickScanWidgetClass = BelegQuickScanWidget::class.java
                            )
                        }

                        "STANDARD_CASH_TRACKER" -> {
                            DashboardCashTrackerWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                cashTransactions = cashTransactions,
                                currencyFormat = currencyFormat,
                                pinWidgetToHomescreen = pinWidgetToHomescreen,
                                cashTrackerWidgetClass = BargeldTrackerWidget::class.java,
                                onOpenQuickAddExpense = { amt ->
                                    quickExpenseInitialAmount = amt
                                    showQuickAddCashExpenseDialog = true
                                }
                            )
                        }

                        "STANDARD_DEADLINES_RADAR", "STANDARD_DEADLINES_MONTH" -> {
                            DashboardDeadlinesWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                widgetId = widgetId,
                                deadlinesScope = deadlinesScope,
                                activeDeadlines = activeDeadlines,
                                now = now
                            )
                        }

                        "STANDARD_FINANCE_CHART" -> {
                            if (showFinanceWidget) {
                                DashboardFinanceOverviewWidget(
                                    viewModel = viewModel,
                                    isEditMode = isEditMode,
                                    financeScope = financeScope,
                                    financeDocs = financeDocs,
                                    financeTotalAmount = financeTotalAmount,
                                    calculateMonthlyAmounts = ::calculateMonthlyAmounts
                                )
                            }
                        }

                        "STANDARD_RECONCILIATION" -> {
                            if (showReconciliationWidget) {
                                DashboardReconciliationWidget(
                                    viewModel = viewModel,
                                    isEditMode = isEditMode,
                                    onOpenReconciliation = { showReconciliationDialog = true }
                                )
                            }
                        }

                        "STANDARD_SECURITY_SCORE" -> {
                            DashboardSyncStatusWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                backupSyncHealth = backupSyncHealth
                            )
                        }

                        "STANDARD_QUICK_NOTE" -> {
                            DashboardQuickNoteWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                quickNoteText = quickNoteText
                            )
                        }

                        "STANDARD_CATEGORY_PIE" -> {
                            val categoryScope = elementPeriodScopes["STANDARD_CATEGORY_PIE"] ?: ElementPeriodScope.ALL
                            val catDocs = remember(allDocuments, categoryScope) {
                                categoryScope.filterDocuments(allDocuments, now)
                            }
                            DashboardCategoryPieWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                allDocuments = allDocuments,
                                categoryScope = categoryScope,
                                catDocs = catDocs,
                                now = now
                            )
                        }

                        "STANDARD_CUSTOM_FIELDS" -> {
                            DashboardCustomFieldsWidget(
                                viewModel = viewModel,
                                isEditMode = isEditMode,
                                customFields = customFields,
                                allCustomFieldValues = allCustomFieldValues
                            )
                        }

                        else -> {
                            val customWidget = customWidgets.find { it.id == widgetId }
                            if (customWidget != null) {
                                RenderCustomDashboardElementCard(
                                    widget = customWidget,
                                    allDocuments = allDocuments,
                                    isEditMode = isEditMode,
                                    onMoveUp = { viewModel.moveDashboardWidgetUp(customWidget.id) },
                                    onMoveDown = { viewModel.moveDashboardWidgetDown(customWidget.id) },
                                    onHide = { viewModel.toggleCustomDashboardWidget(customWidget.id, false) },
                                    onEdit = {
                                        editingCustomWidget = customWidget
                                        showCreateCustomWidgetDialog = true
                                    },
                                    onScopeChange = { newScope ->
                                        viewModel.updateCustomDashboardWidget(customWidget.copy(periodScope = newScope))
                                    },
                                    onUpdateChecklist = { updatedList ->
                                        viewModel.updateCustomWidgetChecklist(customWidget.id, updatedList)
                                    },
                                    onUpdateNote = { newNote ->
                                        viewModel.updateCustomWidgetNote(customWidget.id, newNote)
                                    },
                                    onNavigateToCategory = {
                                        viewModel.setTargetNavigationTab("DOCANIZER")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showTemplatesCatalogDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Vorlagen-Katalog", fontSize = 12.sp)
                }

                FilledTonalButton(
                    onClick = {
                        editingCustomWidget = null
                        showCreateCustomWidgetDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Eigenes Element", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showTemplatesCatalogDialog) {
        DashboardTemplatesCatalogDialog(
            viewModel = viewModel,
            showQuickScan = showBelegQuickScanWidget,
            showCashTracker = showCashTrackerWidget,
            showKpi = showKpiWidgets,
            showDeadlines = showDeadlinesWidget,
            showFinance = showFinanceWidget,
            showReconciliation = showReconciliationWidget,
            showCategoryDistribution = showCategoryDistributionWidget,
            showSecurityScore = showSecurityScoreWidget,
            showCustomFields = showCustomFieldsWidget,
            showRecentDocs = showRecentDocsWidget,
            showFolderShortcuts = showFolderShortcutsWidget,
            showInboxDocs = showInboxDocsWidget,
            showBudgetWatch = showBudgetWatchWidget,
            showQuickActions = showQuickActionsWidget,
            showQuickNote = showQuickNoteWidget,
            elementPeriodScopes = elementPeriodScopes,
            customWidgets = customWidgets,
            onOpenCreateCustomWidget = {
                editingCustomWidget = null
                showCreateCustomWidgetDialog = true
            },
            onEditCustomWidget = { widgetToEdit ->
                editingCustomWidget = widgetToEdit
                showCreateCustomWidgetDialog = true
            },
            onDismiss = { showTemplatesCatalogDialog = false }
        )
    }

    if (showCreateCustomWidgetDialog) {
        CreateOrEditCustomWidgetDialog(
            initialWidget = editingCustomWidget,
            categories = categories,
            onSave = { savedWidget ->
                if (editingCustomWidget != null) {
                    viewModel.updateCustomDashboardWidget(savedWidget)
                } else {
                    viewModel.addCustomDashboardWidget(savedWidget)
                }
                showCreateCustomWidgetDialog = false
                editingCustomWidget = null
                Toast.makeText(context, "Element '${savedWidget.title}' gespeichert", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showCreateCustomWidgetDialog = false
                editingCustomWidget = null
            }
        )
    }

    if (showExportDialog) {
        ExportReportDialog(
            context = context,
            allDocuments = allDocuments,
            activeDeadlines = activeDeadlines,
            onDismiss = { showExportDialog = false }
        )
    }

    if (showReconciliationDialog) {
        ReconciliationDialog(
            viewModel = viewModel,
            onDismiss = { showReconciliationDialog = false },
            onNavigateToScan = onNavigateToScan
        )
    }

    if (showBankImportDialog) {
        BankStatementImportDialog(
            viewModel = viewModel,
            onDismiss = { showBankImportDialog = false }
        )
    }

    if (showQuickAddCashExpenseDialog) {
        val cal = Calendar.getInstance()
        val curMonth = SimpleDateFormat("yyyy-MM", Locale.GERMANY).format(cal.time)
        AddCashTransactionDialog(
            defaultMonthKey = curMonth,
            initialAmount = quickExpenseInitialAmount,
            onDismiss = { showQuickAddCashExpenseDialog = false },
            onSave = { tx ->
                viewModel.addCashTransaction(tx)
                showQuickAddCashExpenseDialog = false
                Toast.makeText(context, "Barausgabe '${tx.title}' erfasst", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun WidgetEditModeHeader(
    title: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    DashboardElementEditBar(
        title = title,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onHide = onHide,
        onConfigure = onConfigure,
        accentColor = accentColor
    )
}
