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

    private fun getTpsBucket(tps: Float): String {
        return when {
            tps < 5f -> "under_5_tps"
            tps < 15f -> "5_to_15_tps"
            tps < 30f -> "15_to_30_tps"
            else -> "over_30_tps"
        }
    }

    private fun getQualityBucket(score: Int): String {
        return when {
            score >= 85 -> "high_85_100"
            score >= 60 -> "medium_60_84"
            else -> "low_under_60"
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
        preferenceMode: String,
        devicePerfTier: String = "STANDARD"
    ) {
        if (analyticsEnabled.value) {
            val bundle = android.os.Bundle().apply {
                putString("model_id", modelId.take(40))
                putLong("latency_ms", latencyMs)
                putFloat("tps", tokensPerSecond)
                putString("tps_bucket", getTpsBucket(tokensPerSecond))
                putInt("confidence_pct", (confidence * 100).toInt().coerceIn(0, 100))
                putString("pref_mode", preferenceMode)
            }
            logEvent("llm_inference_completed", bundle)
            setUserProperty("last_llm_model", modelId.take(40))
            setUserProperty("llm_pref_mode", preferenceMode)
            setUserProperty("device_perf_tier", devicePerfTier)
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
        tps: Float,
        devicePerfTier: String = "STANDARD"
    ) {
        if (analyticsEnabled.value) {
            val bundle = android.os.Bundle().apply {
                putString("model_id", modelId.take(40))
                putInt("quality_score", qualityScore.coerceIn(0, 100))
                putString("quality_bucket", getQualityBucket(qualityScore))
                putInt("quantity_score", quantityScore.coerceIn(0, 100))
                putInt("overall_score", overallScore.coerceIn(0, 100))
                putFloat("tps", tps)
                putString("tps_bucket", getTpsBucket(tps))
            }
            logEvent("llm_benchmark_completed", bundle)
            setUserProperty("device_perf_tier", devicePerfTier)
        }
    }
}
