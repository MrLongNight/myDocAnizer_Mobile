package com.example.service

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.flow.StateFlow

/**
 * Manager für Firebase Analytics.
 * Stellt sicher, dass Analytics nur erfasst werden, wenn der Nutzer ausdrücklich zugestimmt hat.
 */
class AnalyticsManager(context: Context, private val analyticsEnabled: StateFlow<Boolean>) {

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(context)

    init {
        // Initialer Check des Consent-Status
        updateConsent(analyticsEnabled.value)
    }

    /**
     * Aktualisiert den Erfassungsstatus von Firebase Analytics basierend auf der Nutzereinwilligung.
     */
    fun updateConsent(enabled: Boolean) {
        firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
    }

    /**
     * Loggt ein Ereignis, falls Analytics aktiviert ist.
     */
    fun logEvent(name: String, params: android.os.Bundle? = null) {
        if (analyticsEnabled.value) {
            firebaseAnalytics.logEvent(name, params)
        }
    }

    /**
     * Setzt eine Nutzereigenschaft, falls Analytics aktiviert ist.
     */
    fun setUserProperty(name: String, value: String?) {
        if (analyticsEnabled.value) {
            firebaseAnalytics.setUserProperty(name, value)
        }
    }

    /**
     * Datenschutzkonforme Übermittlung von LLM-Inferenz-Telemetriedaten (ohne private Dokument- oder Textinhalte).
     */
    fun logLlmInferenceTelemetry(
        modelId: String,
        latencyMs: Long,
        tokensPerSecond: Float,
        confidence: Float,
        preferenceMode: String
    ) {
        if (analyticsEnabled.value) {
            val bundle = android.os.Bundle().apply {
                putString("model_id", modelId.take(40))
                putLong("latency_ms", latencyMs)
                putFloat("tps", tokensPerSecond)
                putInt("confidence_pct", (confidence * 100).toInt().coerceIn(0, 100))
                putString("pref_mode", preferenceMode)
            }
            logEvent("llm_inference_completed", bundle)
            setUserProperty("last_llm_model", modelId.take(40))
            setUserProperty("llm_pref_mode", preferenceMode)
        }
    }

    /**
     * Datenschutzkonforme Übermittlung von LLM-Benchmark-Ergebnissen (Modell, Scores und Durchsatz).
     */
    fun logLlmBenchmarkCompleted(
        modelId: String,
        qualityScore: Int,
        quantityScore: Int,
        overallScore: Int,
        tps: Float
    ) {
        if (analyticsEnabled.value) {
            val bundle = android.os.Bundle().apply {
                putString("model_id", modelId.take(40))
                putInt("quality_score", qualityScore.coerceIn(0, 100))
                putInt("quantity_score", quantityScore.coerceIn(0, 100))
                putInt("overall_score", overallScore.coerceIn(0, 100))
                putFloat("tps", tps)
            }
            logEvent("llm_benchmark_completed", bundle)
        }
    }
}
