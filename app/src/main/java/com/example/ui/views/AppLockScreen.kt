package com.example.ui.views

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.example.ui.DocAnizerViewModel
import com.example.ui.components.AppLogoBanner

@Composable
fun AppLockScreen(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var enteredPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    val syncPasswordKey by viewModel.syncPasswordKey.collectAsState()

    val isBiometricAvailable = remember {
        viewModel.biometricAuthManager.checkBiometricAvailability() == com.example.service.BiometricAuthManager.BiometricHardwareStatus.AVAILABLE
    }

    // Automatische Auslösung der Biometrie beim Start nur wenn Hardware verfügbar ist
    LaunchedEffect(Unit) {
        if (activity != null && isBiometricAvailable) {
            viewModel.authenticateBiometric(
                activity = activity,
                onSuccess = {
                    errorMessage = null
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPasswordDialog = false 
                passwordError = null
            },
            icon = { Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Mit Passwort / PIN entsperren", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (syncPasswordKey.isBlank()) 
                            "Es wurde kein separates Passwort vergeben. Du kannst den Tresor direkt entsperren oder ein Passwort eingeben." 
                        else 
                            "Gib dein Tresor-Passwort bzw. deine PIN ein:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = enteredPassword,
                        onValueChange = { 
                            enteredPassword = it 
                            passwordError = null
                        },
                        label = { Text("Passwort / PIN") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (syncPasswordKey.isBlank() || enteredPassword == syncPasswordKey) {
                                viewModel.unlockApp()
                                showPasswordDialog = false
                            } else {
                                passwordError = "Falsches Passwort"
                            }
                        }),
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth().testTag("input_lock_password")
                    )
                    passwordError?.let { err ->
                        Text(text = err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (syncPasswordKey.isBlank() || enteredPassword == syncPasswordKey) {
                            viewModel.unlockApp()
                            showPasswordDialog = false
                        } else {
                            passwordError = "Falsches Passwort"
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_password_unlock")
                ) {
                    Text("Tresor entsperren")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_lock_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Offizielles myDocAnizer Logo-Banner
                AppLogoBanner(
                    iconHeight = 110.dp,
                    fontSize = 24f,
                    includeContainer = true,
                    showTagline = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Geschützter Dokumenten-Tresor",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Deine Dokumente und Belege sind gesichert. Bitte bestätige deine Identität per Fingerabdruck, Gesicht oder Passwort.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }

                errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        if (activity != null) {
                            viewModel.authenticateBiometric(
                                activity = activity,
                                onSuccess = {
                                    errorMessage = null
                                },
                                onError = { err ->
                                    errorMessage = err
                                }
                            )
                        } else {
                            viewModel.unlockApp()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_unlock_biometric"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Biometrisch entsperren",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { 
                        if (syncPasswordKey.isBlank()) {
                            viewModel.unlockApp()
                        } else {
                            showPasswordDialog = true 
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_unlock_with_password"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (syncPasswordKey.isBlank()) "Direkt entsperren (Kein Passwort gesetzt)" else "Mit Passwort / PIN entsperren",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
