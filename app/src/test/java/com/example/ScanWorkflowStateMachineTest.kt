package com.example

import com.example.ui.ScanWorkflowPhase
import com.example.ui.ScanWorkflowStateMachine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanWorkflowStateMachineTest {
    @Test
    fun stateSequenceDoesNotReportSavedBeforePersistenceCompletes() {
        val workflow = ScanWorkflowStateMachine()

        assertEquals(ScanWorkflowPhase.Idle, workflow.phase.value)
        workflow.transition(ScanWorkflowPhase.Analyzing)
        assertEquals(ScanWorkflowPhase.Analyzing, workflow.phase.value)
        workflow.transition(ScanWorkflowPhase.AwaitingRuleDecision)
        assertEquals(ScanWorkflowPhase.AwaitingRuleDecision, workflow.phase.value)
        workflow.transition(ScanWorkflowPhase.Saving)
        assertEquals(ScanWorkflowPhase.Saving, workflow.phase.value)
        assertFalse(workflow.phase.value == ScanWorkflowPhase.Saved)

        // The caller invokes Saved only after PDF and database persistence succeed.
        workflow.transition(ScanWorkflowPhase.Saved)
        assertEquals(ScanWorkflowPhase.Saved, workflow.phase.value)
        assertNull(workflow.error.value)
    }

    @Test
    fun lockedVaultProducesFailureAndNeverSaved() {
        val workflow = ScanWorkflowStateMachine()
        workflow.transition(ScanWorkflowPhase.Saving)
        workflow.fail(
            ScanWorkflowStateMachine.failureMessage(
                IllegalStateException("Vault is locked; refusing document write"),
                "Speichern"
            )
        )

        assertEquals(ScanWorkflowPhase.Failed, workflow.phase.value)
        assertTrue(workflow.error.value.orEmpty().contains("Tresor ist gesperrt"))
        assertFalse(workflow.phase.value == ScanWorkflowPhase.Saved)
    }

    @Test
    fun ocrTimeoutProducesActionableFailure() = runBlocking {
        val timeout = try {
            withTimeout(1L) { delay(100L) }
            null
        } catch (error: TimeoutCancellationException) {
            error
        }
        assertTrue("Test must create a timeout", timeout != null)

        val message = ScanWorkflowStateMachine.failureMessage(timeout!!, "OCR")
        assertTrue(message.contains("12 Sekunden"))
        assertTrue(message.contains("erneut versuchen"))
    }

    @Test
    fun outOfMemoryProducesRetryableFailureMessage() {
        val message = ScanWorkflowStateMachine.failureMessage(
            OutOfMemoryError("allocation failed"),
            "Speichern"
        )

        assertTrue(message.contains("Arbeitsspeicher"))
        assertTrue(message.contains("Scan bleibt erhalten"))
        assertTrue(message.contains("erneut versuchen"))
    }

    @Test
    fun discardResetsFailureWithoutClaimingSuccess() {
        val workflow = ScanWorkflowStateMachine()
        workflow.fail("Vault gesperrt")

        workflow.reset()

        assertEquals(ScanWorkflowPhase.Idle, workflow.phase.value)
        assertNull(workflow.error.value)
        assertFalse(workflow.phase.value == ScanWorkflowPhase.Saved)
    }
}
