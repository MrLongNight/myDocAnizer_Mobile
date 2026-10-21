package com.example.service

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * FIDO2 / WebAuthn Passkey Manager unter Verwendung von AndroidX CredentialManager.
 * Ermöglicht passwortloses, kryptografisch gesichertes Anmelden via biometrische Hardware-Keys.
 */
class PasskeyAuthManager(private val context: Context) {

    private val credentialManager by lazy {
        try {
            CredentialManager.create(context)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Erstellt eine standardkonforme WebAuthn JSON-Registrierungsanforderung (Registration JSON).
     */
    fun createRegistrationJson(userName: String, rpId: String = "mydocanizer.com"): String {
        val challengeBase64 = createChallenge()
        val userIdBase64 = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(userName.toByteArray())

        val pubKeyCredParams = JSONArray().apply {
            put(JSONObject().apply {
                put("type", "public-key")
                put("alg", -7) // ES256
            })
            put(JSONObject().apply {
                put("type", "public-key")
                put("alg", -257) // RS256
            })
        }

        val json = JSONObject().apply {
            put("challenge", challengeBase64)
            put("rp", JSONObject().apply {
                put("name", "myDocAnizer App")
                put("id", rpId)
            })
            put("user", JSONObject().apply {
                put("id", userIdBase64)
                put("name", userName)
                put("displayName", userName)
            })
            put("pubKeyCredParams", pubKeyCredParams)
            put("timeout", 60000)
            put("attestation", "none")
            put("authenticatorSelection", JSONObject().apply {
                put("authenticatorAttachment", "platform")
                put("requireResidentKey", true)
                put("residentKey", "required")
                put("userVerification", "required")
            })
        }
        return json.toString()
    }

    /**
     * Erstellt eine standardkonforme WebAuthn JSON-Authentifizierungsanforderung (Authentication JSON).
     */
    fun createAuthenticationJson(rpId: String = "mydocanizer.com"): String {
        val challengeBase64 = createChallenge()
        val json = JSONObject().apply {
            put("challenge", challengeBase64)
            put("timeout", 60000)
            put("rpId", rpId)
            put("userVerification", "required")
        }
        return json.toString()
    }

    private fun createChallenge(): String {
        val challenge = ByteArray(32)
        java.security.SecureRandom().nextBytes(challenge)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
    }

    /**
     * Führt die Passkey-Erstellung auf dem Android-Gerät über den System-CredentialManager aus.
     */
    suspend fun registerPasskey(
        activityContext: Context,
        userName: String,
        rpId: String = "mydocanizer.com"
    ): Result<String> = withContext(Dispatchers.Main) {
        val manager = credentialManager ?: return@withContext Result.failure(Exception("Credential Manager auf diesem Gerät nicht verfügbar."))
        try {
            val requestJson = createRegistrationJson(userName, rpId)
            val createRequest = CreatePublicKeyCredentialRequest(requestJson = requestJson)
            val result = manager.createCredential(activityContext, createRequest)
            Result.success("Passkey für '$userName' erfolgreich im Google Credential Manager / Hardware-Keystore registriert.")
        } catch (e: CreateCredentialCancellationException) {
            Result.failure(Exception("Passkey-Einrichtung vom Benutzer abgebrochen."))
        } catch (e: CreateCredentialException) {
            val detail = e.localizedMessage ?: e.message.orEmpty()
            val message = if (detail.contains("no create options available", ignoreCase = true)) {
                "Kein Passkey-Anbieter verfügbar. Prüfe, ob Google Passwort-Manager aktiviert ist und mydocanizer.com die Android-App über Digital Asset Links (/.well-known/assetlinks.json) verknüpft."
            } else {
                "Passkey-Fehler: $detail"
            }
            Result.failure(Exception(message, e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Führt die Passkey-Authentifizierung über den System-CredentialManager aus.
     */
    suspend fun authenticatePasskey(
        activityContext: Context,
        rpId: String = "mydocanizer.com"
    ): Result<String> = withContext(Dispatchers.Main) {
        val manager = credentialManager ?: return@withContext Result.failure(Exception("Credential Manager auf diesem Gerät nicht verfügbar."))
        try {
            val requestJson = createAuthenticationJson(rpId)
            val getOption = GetPublicKeyCredentialOption(requestJson = requestJson)
            val getRequest = GetCredentialRequest(listOf(getOption))
            val result = manager.getCredential(activityContext, getRequest)
            Result.success("Passkey-Authentifizierung erfolgreich!")
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Passkey-Anmeldung abgebrochen."))
        } catch (e: GetCredentialException) {
            Result.failure(Exception("Passkey-Prüfung fehlgeschlagen: ${e.localizedMessage ?: e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
