package com.example.ui.views

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.DocAnizerViewModel
import com.example.ui.views.rules.AuditLogContent as RulesAuditLogContent
import com.example.ui.views.rules.DocumentRulesSettingsTab as RulesDocumentRulesSettingsTab
import com.example.ui.views.rules.OnDeviceAiAgentSettingsTab as RulesOnDeviceAiAgentSettingsTab

/**
 * Fassaden-View für die Einstellungen zu Regeln und KI.
 * Delegiert an die spezialisierten Panes in com.example.ui.views.rules.*
 */

@Composable
fun DocumentRulesSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    RulesDocumentRulesSettingsTab(viewModel, modifier)
}

@Composable
fun OnDeviceAiAgentSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    RulesOnDeviceAiAgentSettingsTab(viewModel, modifier)
}

@Composable
fun RulesAndAiSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    DocumentRulesSettingsTab(viewModel = viewModel, modifier = modifier)
}

@Composable
fun SystemDiagnosticsAndLogTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    AuditLogContent(
        viewModel = viewModel,
        modifier = modifier.fillMaxSize()
    )
}

@Composable
fun AuditLogContent(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    RulesAuditLogContent(viewModel, modifier)
}
