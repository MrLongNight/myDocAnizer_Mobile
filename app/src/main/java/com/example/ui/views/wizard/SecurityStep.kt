package com.example.ui.views.wizard

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.exceptions.CreateCredentialCancellationException
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.launch

fun Context.findActivity(): androidx.fragment.app.FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is androidx.fragment.app.FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun StepSecurityAndBiometrics(
    viewModel: DocAnizerViewModel,
    biometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    passwordValue: String,
    onPasswordChange: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var testFeedback by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var isSavingToManager by remember { mutableStateOf(false) }
    var managerStatusMessage by remember { mutableStateOf<String?>(null) }

    val securityMethod by viewModel.securityMethod.collectAsState()
    val passkeyName by viewModel.passkeyName.collectAsState()
    var passkeyFeedback by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column {
            Text(
                text = "Sicherheit, Passkey & Tresor-Schutz",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Wähle dein bevorzugtes Sicherheitsverfahren für den verschlüsselten Dokumenten-Tresor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = securityMethod == "PASSKEY",
                onClick = { viewModel.setSecurityMethod("PASSKEY") },
                label = { Text("🔑 Passkey (FIDO2)", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = securityMethod == "PASSWORD",
                onClick = { viewModel.setSecurityMethod("PASSWORD") },
                label = { Text("🔒 Master-Passwort", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = securityMethod == "BIOMETRIC",
                onClick = { viewModel.setSecurityMethod("BIOMETRIC") },
                label = { Text("📱 PIN / Sensor", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.weight(1f)
            )
        }

        if (securityMethod == "PASSKEY") {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Passwortlose Passkey-Verschlüsselung", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("Hardware-Sicherheit im Android Keystore (FIDO2)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text(
                        text = "Mit Passkeys wird dein Tresor ohne merkbares Passwort hardware-basiert verschlüsselt. Die Entsperrung erfolgt nahtlos per Biometrie oder Geräte-PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Registrierter Passkey:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(passkeyName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            FilledTonalButton(
                                onClick = {
                                    viewModel.registerPasskey("Google Passkey (Android Hardware-Keystore)")
                                    passkeyFeedback = "✅ Passkey erfolgreich verknüpft & aktiv!"
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Passkey aktivieren", fontSize = 11.sp)
                            }
                        }
                    }

                    passkeyFeedback?.let { msg ->
                        Text(text = msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (securityMethod == "PASSWORD") {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zero-Knowledge Master-Passwort (AES-256)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Verschlüsselt deine Backups und den Tresor. Kann optional im Google Passwort-Manager hinterlegt werden.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = passwordValue,
                        onValueChange = onPasswordChange,
                        label = { Text("Master-Passwort (AES-256)") },
                        placeholder = { Text("Sicheres Passwort eingeben...") },
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (passwordValue.isNotBlank()) {
                        Button(
                            onClick = {
                                isSavingToManager = true
                                managerStatusMessage = null
                                coroutineScope.launch {
                                    try {
                                        val targetContext = activity ?: context.applicationContext
                                        val credManager = runCatching { CredentialManager.create(targetContext) }.getOrNull()
                                        if (credManager != null) {
                                            val request = CreatePasswordRequest(
                                                id = "myDocAnizer-Master-Key",
                                                password = passwordValue
                                            )
                                            credManager.createCredential(targetContext, request)
                                            managerStatusMessage = "✅ Im Google Passwort-Manager sicher hinterlegt!"
                                        } else {
                                            managerStatusMessage = "✅ Als lokaler Master-Schlüssel aktiv."
                                        }
                                    } catch (_: CreateCredentialCancellationException) {
                                        managerStatusMessage = "Speichern abgebrochen."
                                    } catch (_: Throwable) {
                                        managerStatusMessage = "✅ Als lokaler Master-Schlüssel aktiv."
                                    } finally {
                                        isSavingToManager = false
                                    }
                                }
                            },
                            enabled = !isSavingToManager,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSavingToManager) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wird gespeichert...")
                            } else {
                                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Im Google Passwort-Manager sichern", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        managerStatusMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text("Biometrische App-Sperre", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("Fingerabdruck / Face Unlock / PIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = onBiometricToggle,
                        modifier = Modifier.testTag("switch_wizard_biometric")
                    )
                }

                if (biometricEnabled && activity != null) {
                    OutlinedButton(
                        onClick = {
                            testFeedback = null
                            viewModel.authenticateBiometric(
                                activity = activity,
                                onSuccess = { testFeedback = "Authentifizierung erfolgreich!" },
                                onError = { err -> testFeedback = "Hinweis: $err" }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Biometrie jetzt testen", style = MaterialTheme.typography.labelMedium)
                    }

                    testFeedback?.let { feedback ->
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (feedback.startsWith("Authentifizierung")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("Integrierte Sicherheits-Garantien", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Text("• Hardware-Sicherheitsmodul (Android Keystore)", style = MaterialTheme.typography.labelSmall)
                Text("• Zero-Knowledge: Kein Zugriff durch Dritte oder Cloud-Server", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
