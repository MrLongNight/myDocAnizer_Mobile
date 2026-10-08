package com.example.ui.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.RulesAndAiSettingsTab

@Composable
fun ModuleSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    var activeSubSection by remember { mutableStateOf(0) } // 0: Scan, 1: PDF, 2: Finanzen

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScrollableTabRow(
            selectedTabIndex = activeSubSection,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            listOf("Scan", "PDF", "Finanzen").forEachIndexed { index, title ->
                Tab(
                    selected = activeSubSection == index,
                    onClick = { activeSubSection = index },
                    text = { Text(title, fontSize = 11.sp) }
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (activeSubSection) {
                0 -> ScannerSettingsTab(viewModel)
                1 -> PdfSettingsTab(viewModel)
                2 -> FinanceModuleContent(viewModel)
                else -> ScannerSettingsTab(viewModel)
            }
        }
    }
}

@Composable
fun FinanceModuleContent(viewModel: DocAnizerViewModel) {
    // Current logic from ModuleSettingsTab for Finance
    val enableTracking by viewModel.enableIncomeExpenseTracking.collectAsState()
    val enableCashTracker by viewModel.enableCashTracker.collectAsState()
    val enableReceiptExpenses by viewModel.enableReceiptExpenses.collectAsState()
    val enableBankStatementImport by viewModel.enableBankStatementImport.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Finanz- & Belegmanagement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Kassenbons, Barzahlungen und Bankauszüge", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = enableTracking, onCheckedChange = { viewModel.setEnableIncomeExpenseTracking(it) })
            }
        }
        
        if (enableTracking) {
             // Individual Toggles
             FinanceToggleItem("Bargeld Tracker", enableCashTracker) { viewModel.setEnableCashTracker(it) }
             FinanceToggleItem("Beleg Erfassung", enableReceiptExpenses) { viewModel.setEnableReceiptExpenses(it) }
             FinanceToggleItem("Bankauszüge", enableBankStatementImport) { viewModel.setEnableBankStatementImport(it) }
        }
    }
}

@Composable
fun FinanceToggleItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
