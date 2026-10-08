package com.example.ui.views

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.ChecklistItem
import com.example.model.CustomDashboardWidget
import com.example.model.DocumentEntity
import com.example.model.ElementPeriodScope
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.dashboard.custom.CreateOrEditCustomWidgetDialog as ImplCreateOrEditCustomWidgetDialog
import com.example.ui.views.dashboard.custom.DashboardElementEditBar as ImplDashboardElementEditBar
import com.example.ui.views.dashboard.custom.DashboardTemplatesCatalogDialog as ImplDashboardTemplatesCatalogDialog
import com.example.ui.views.dashboard.custom.ElementPeriodScopeChip as ImplElementPeriodScopeChip
import com.example.ui.views.dashboard.custom.RenderCustomDashboardElementCard as ImplRenderCustomDashboardElementCard
import com.example.ui.views.dashboard.custom.getWidgetAccentColor as implGetWidgetAccentColor
import com.example.ui.views.dashboard.custom.getWidgetIconVector as implGetWidgetIconVector

/**
 * Lean interface / facade for custom dashboard widgets, delegating to com.example.ui.views.dashboard.custom.*
 */

fun getWidgetAccentColor(colorSkin: String): Color = implGetWidgetAccentColor(colorSkin)

fun getWidgetIconVector(iconName: String): ImageVector = implGetWidgetIconVector(iconName)

@Composable
fun ElementPeriodScopeChip(
    currentScope: ElementPeriodScope,
    onScopeChange: (ElementPeriodScope) -> Unit,
    modifier: Modifier = Modifier
) {
    ImplElementPeriodScopeChip(
        currentScope = currentScope,
        onScopeChange = onScopeChange,
        modifier = modifier
    )
}

@Composable
fun DashboardTemplatesCatalogDialog(
    viewModel: DocAnizerViewModel,
    showQuickScan: Boolean,
    showCashTracker: Boolean,
    showKpi: Boolean,
    showDeadlines: Boolean,
    showFinance: Boolean,
    showReconciliation: Boolean,
    showCategoryDistribution: Boolean,
    showSecurityScore: Boolean,
    showCustomFields: Boolean,
    showRecentDocs: Boolean,
    showFolderShortcuts: Boolean,
    showInboxDocs: Boolean,
    showBudgetWatch: Boolean,
    showQuickActions: Boolean,
    showQuickNote: Boolean,
    elementPeriodScopes: Map<String, ElementPeriodScope>,
    customWidgets: List<CustomDashboardWidget>,
    onOpenCreateCustomWidget: () -> Unit,
    onEditCustomWidget: (CustomDashboardWidget) -> Unit,
    onDismiss: () -> Unit
) {
    ImplDashboardTemplatesCatalogDialog(
        viewModel = viewModel,
        showQuickScan = showQuickScan,
        showCashTracker = showCashTracker,
        showKpi = showKpi,
        showDeadlines = showDeadlines,
        showFinance = showFinance,
        showReconciliation = showReconciliation,
        showCategoryDistribution = showCategoryDistribution,
        showSecurityScore = showSecurityScore,
        showCustomFields = showCustomFields,
        showRecentDocs = showRecentDocs,
        showFolderShortcuts = showFolderShortcuts,
        showInboxDocs = showInboxDocs,
        showBudgetWatch = showBudgetWatch,
        showQuickActions = showQuickActions,
        showQuickNote = showQuickNote,
        elementPeriodScopes = elementPeriodScopes,
        customWidgets = customWidgets,
        onOpenCreateCustomWidget = onOpenCreateCustomWidget,
        onEditCustomWidget = onEditCustomWidget,
        onDismiss = onDismiss
    )
}

@Composable
fun CreateOrEditCustomWidgetDialog(
    initialWidget: CustomDashboardWidget? = null,
    categories: List<String>,
    onSave: (CustomDashboardWidget) -> Unit,
    onDismiss: () -> Unit
) {
    ImplCreateOrEditCustomWidgetDialog(
        initialWidget = initialWidget,
        categories = categories,
        onSave = onSave,
        onDismiss = onDismiss
    )
}

@Composable
fun DashboardElementEditBar(
    title: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    ImplDashboardElementEditBar(
        title = title,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onHide = onHide,
        onConfigure = onConfigure,
        accentColor = accentColor
    )
}

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
    ImplRenderCustomDashboardElementCard(
        widget = widget,
        allDocuments = allDocuments,
        isEditMode = isEditMode,
        onMoveUp = onMoveUp,
        onMoveDown = onMoveDown,
        onHide = onHide,
        onEdit = onEdit,
        onScopeChange = onScopeChange,
        onUpdateChecklist = onUpdateChecklist,
        onUpdateNote = onUpdateNote,
        onNavigateToCategory = onNavigateToCategory,
        modifier = modifier
    )
}
