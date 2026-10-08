package com.example.ui.views.wizard

/**
 * Versiegelte Klasse (Sealed Class) für die Wizard-Schritte und deren IDs.
 */
sealed class WizardStep(val id: Int) {
    data object Welcome : WizardStep(1)
    data object ViewModeAndRestore : WizardStep(2)
    data object IncomeExpenseConfig : WizardStep(12)
    data object StorageLocation : WizardStep(3)
    data object ScanCaptureAndTrigger : WizardStep(4)
    data object QualityAndAutoColor : WizardStep(5)
    data object LlmModelSelection : WizardStep(6)
    data object LlmInferenceSettings : WizardStep(7)
    data object SecurityAndBiometrics : WizardStep(8)
    data object BackupAndRecoveryStrategy : WizardStep(9)
    data object DatabaseAndMaintenance : WizardStep(10)
    data object ReadySummary : WizardStep(11)

    companion object {
        fun fromId(id: Int): WizardStep = when (id) {
            1 -> Welcome
            2 -> ViewModeAndRestore
            12 -> IncomeExpenseConfig
            3 -> StorageLocation
            4 -> ScanCaptureAndTrigger
            5 -> QualityAndAutoColor
            6 -> LlmModelSelection
            7 -> LlmInferenceSettings
            8 -> SecurityAndBiometrics
            9 -> BackupAndRecoveryStrategy
            10 -> DatabaseAndMaintenance
            11 -> ReadySummary
            else -> Welcome
        }
    }
}
