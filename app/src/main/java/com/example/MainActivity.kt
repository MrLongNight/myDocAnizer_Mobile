package com.example

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.AppStartSplashAnimation
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.views.MainScreen

open class MainActivity : FragmentActivity() {
    private val viewModel: DocAnizerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.example.BuildConfig.DEBUG) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        else window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
        // Init moved to DocAnizerApp.onCreate()
        try {
            handleWidgetIntent(intent)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "handleWidgetIntent failed: ${t.message}")
        }
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val colorSkin by viewModel.colorSkin.collectAsStateWithLifecycle()
            val highContrastMode by viewModel.highContrastMode.collectAsStateWithLifecycle()
            val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()
            val vaultStatus by viewModel.vaultStatus.collectAsStateWithLifecycle()

            androidx.compose.runtime.LaunchedEffect(isAppUnlocked, vaultStatus.isUnencryptedMode) {
                if (com.example.BuildConfig.DEBUG) window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                else window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }

            var showSplash by rememberSaveable { mutableStateOf(!com.example.BuildConfig.DEBUG) }

            MyApplicationTheme(
                darkTheme = isDark,
                colorSkin = colorSkin,
                highContrast = highContrastMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!showSplash) {
                            MainScreen(viewModel = viewModel)
                        } else {
                            AppStartSplashAnimation(
                                onAnimationFinished = { showSplash = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val settings = viewModel.scannerSettings.value
            if (settings.scanMode == "MANUAL" && settings.enableHardwareButtons) {
                viewModel.triggerHardwareCapture()
                return true // Konsumiert den Tastendruck für die Kamera-Auslösung nur im manuellen Modus
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent == null) return
        val vault = com.example.service.VaultSecurityManager.getInstance(this)
        // Drop, do not defer, external actions while authentication is incomplete.
        if (!viewModel.isAppUnlocked.value || (vault.vaultHeader.value != null && !vault.isUnlocked())) return
        val tab = ExternalIntentPolicy.validTab(runCatching { intent.getStringExtra("EXTRA_START_TAB") }.getOrNull())
        if (tab != null) viewModel.setTargetNavigationTab(tab)
        val action = runCatching { intent.getStringExtra("EXTRA_OPEN_ACTION") }.getOrNull()
        if (ExternalIntentPolicy.isAllowedAction(action)) viewModel.setDirectOpenCashTracker(true)
        val reconcile = runCatching { intent.getBooleanExtra("EXTRA_OPEN_RECONCILIATION", false) }.getOrDefault(false)
        if (reconcile) viewModel.setDirectOpenReconciliation(true)
    }
}
