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
}
