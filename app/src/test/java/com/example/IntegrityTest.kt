package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.service.DiagnosticService
import com.example.service.TestStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class IntegrityTest {

    private lateinit var diagnosticService: DiagnosticService
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = AppDatabase.getDatabase(context)
        diagnosticService = DiagnosticService(context, db)
    }

    @Test
    fun runRoutine1_IntegrityCheck() = runBlocking {
        // Diese Routine läuft vollautomatisch in der AI Studio Testumgebung (Robolectric)
        diagnosticService.runIntegrityCheck()
        
        val steps = diagnosticService.steps.value
        assertTrue("Es sollten Diagnoseschritte vorhanden sein", steps.isNotEmpty())
        
        steps.forEach { step ->
            println("Prüfe: ${step.name} -> ${step.status} (${step.message})")
            assertTrue("Schritt ${step.name} darf nicht fehlschlagen", step.status != TestStatus.ERROR)
        }
    }

    @Test
    fun runRoutine4_PersonaSimulation() = runBlocking {
        val persona = com.example.util.UserPersonaFactory.PERSONA_FAMILY
        val result = diagnosticService.startStressSimulation(persona)
        
        assertTrue("Dokumente sollten verarbeitet worden sein", result.documentCount > 0)
        assertTrue("Finanzvolumen sollte größer 0 sein", result.totalAmount > 0.0)
        assertTrue("Fristen sollten erkannt worden sein", result.deadlinesDetected > 0)
        
        val report = diagnosticService.formatSimulationReport(result)
        println("Simulations-Report:\n$report")
        assertTrue("Report sollte Profilnamen enthalten", report.contains("Familie Weber"))
    }
}
