package com.example.ui.views.wizard.cloud

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BackupTierNasCard(
    enableNas: Boolean,
    onToggleNas: (Boolean) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    webDavUrl: String,
    onWebDavUrlChange: (String) -> Unit,
    webDavUser: String,
    onWebDavUserChange: (String) -> Unit,
    webDavPass: String,
    onWebDavPassChange: (String) -> Unit,
    isTestingNas: Boolean,
    nasStatusMessage: String?,
    onTestNasConnection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNasSafetyNotice by remember { mutableStateOf(false) }

    val borderWidth by animateDpAsState(if (enableNas) 1.5.dp else 1.dp, label = "s3_border")
    val borderColor by animateColorAsState(if (enableNas) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), label = "s3_color")
    val bgColor by animateColorAsState(if (enableNas) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), label = "s3_bg")

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Checkbox(
                        checked = enableNas,
                        onCheckedChange = onToggleNas,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Stufe 3: Lokales Heim-NAS / WebDAV",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Details ein-/ausklappen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showNasSafetyNotice = !showNasSafetyNotice },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Text("Brandschutz-Warnung zum Heim-NAS:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                                Text(
                                    text = if (showNasSafetyNotice) "▲ Ausblenden" else "▼ Details",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            AnimatedVisibility(visible = showNasSafetyNotice) {
                                Text(
                                    text = "Dein NAS und dein Smartphone befinden sich üblicherweise in derselben Wohnung. Bei Feuer, Hochwasser oder Blitzeinschlag sind BEIDE Medien auf einen Schlag zerstört! Nutze NAS daher nur in Kombination mit extern gelagerten USB-Sticks oder Cloud.",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = webDavUrl,
                        onValueChange = onWebDavUrlChange,
                        label = { Text("Server WebDAV-URL") },
                        placeholder = { Text("https://192.168.1.100/remote.php/dav/files/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = webDavUser,
                            onValueChange = onWebDavUserChange,
                            label = { Text("Benutzer") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = webDavPass,
                            onValueChange = onWebDavPassChange,
                            label = { Text("Passwort") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = onTestNasConnection,
                        enabled = !isTestingNas,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingNas) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("NAS wird kontaktiert...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("NAS-Verbindung einrichten & testen", fontSize = 12.sp)
                        }
                    }

                    nasStatusMessage?.let { msg ->
                        Text(text = msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
