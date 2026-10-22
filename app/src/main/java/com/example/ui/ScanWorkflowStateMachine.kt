package com.example.ui

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScanWorkflowPhase {
    Idle,
    Analyzing,
    AwaitingRuleDecision,
    Saving,
    Saved,
    Failed
}

/**
 * Small, JVM-testable state machine shared by the scanner ViewModel and its UI.
 * The caller may enter Saved only after PDF generation and database persistence complete.
 */
class ScanWorkflowStateMachine {
    private val _phase = MutableStateFlow(ScanWorkflowPhase.Idle)
    val phase: StateFlow<ScanWorkflowPhase> = _phase.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun transition(next: ScanWorkflowPhase) {
        if (next != ScanWorkflowPhase.Failed) _error.value = null
        _phase.value = next
    }

    fun fail(message: String) {
        _error.value = message
        _phase.value = ScanWorkflowPhase.Failed
    }

    fun reset() {
        _error.value = null
        _phase.value = ScanWorkflowPhase.Idle
    }

    companion object {
        fun failureMessage(error: Throwable, phase: String): String = when {
            error.message?.contains("Vault is locked", ignoreCase = true) == true ->
                "Der Tresor ist gesperrt. Bitte entsperren und danach erneut versuchen."
            error is OutOfMemoryError ->
                "Zu wenig Arbeitsspeicher während $phase. Der Scan bleibt erhalten; bitte andere Apps schließen und erneut versuchen."
            error is TimeoutCancellationException && phase == "OCR" ->
                "Die Texterkennung hat das Zeitlimit von 12 Sekunden überschritten. Der Scan bleibt erhalten; bitte erneut versuchen."
            error is TimeoutCancellationException ->
                "Das Speichern hat länger als 60 Sekunden gedauert. Der Scan bleibt erhalten; bitte erneut versuchen."
            else -> "$phase fehlgeschlagen: ${error.message ?: error.javaClass.simpleName}. Der Scan bleibt erhalten."
        }
    }
}
