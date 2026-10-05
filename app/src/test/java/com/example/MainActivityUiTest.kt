package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Automatisierter UI-Start- und Integrationstest:
 * Überprüft zwingend, dass die gesamte Compose UI von MainActivity in der Testumgebung
 * erfolgreich initialisiert, gerendert, aufgebaut und im Idle-Zustand fehlerfrei dargestellt wird.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MainActivityUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyAppUiStartsAndRendersSuccessfully() {
        // Wartet, bis alle Composables, Render-Zyklen und LaunchedEffects der MainActivity vollständig ausgeführt sind
        composeTestRule.waitForIdle()

        // Überprüft, dass die Activity aktiv und nicht beendet oder abgestürzt ist
        assertNotNull(composeTestRule.activity)
        assertFalse(composeTestRule.activity.isFinishing)
        assertFalse(composeTestRule.activity.isDestroyed)

        // Gibt die gerenderte UI-Struktur zur Fehlerdiagnose aus
        composeTestRule.onRoot().printToLog("MainActivityUiTest")
    }
}
