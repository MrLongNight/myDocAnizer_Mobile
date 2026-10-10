package com.example.ui.views.p2p

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*

@Composable
fun P2pConflictAndProgressCard(
    pendingRequests: List<IncomingPairingRequest>,
    activeConflicts: List<SyncConflict>,
    syncProgress: P2pSyncProgressState,
    onAuthorizeRequest: (String, Boolean) -> Unit,
    onResolveConflict: (SyncConflict, ConflictResolutionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pending authorization requests
        if (pendingRequests.isNotEmpty()) {
            pendingRequests.forEach { req ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "⚠️ Autorisierung erforderlich!",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ein neues Gerät im lokalen WLAN möchte sich für den Master-Master-Sync mit dieser App verbinden:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = "Gerätename: ${req.clientDeviceName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = "IP-Adresse: ${req.clientIp}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                Text(text = "Eingegebener PIN: ${req.pinEntered}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { onAuthorizeRequest(req.requestId, false) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Ablehnen")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = { onAuthorizeRequest(req.requestId, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Erlauben & Verbinden")
                            }
                        }
                    }
                }
            }
        }

        // Active conflicts
        if (activeConflicts.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.5.dp, Color(0xFFDC2626))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${activeConflicts.size} Sync-Konflikt(e) erfordern Klärung",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                    Text(
                        text = "Um Datenverlust auszuschließen, wurde der Sync an folgenden Dokumenten pausiert. Bitte wähle, welche Version behalten werden soll:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF7F1D1D),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    activeConflicts.forEach { conflict ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = conflict.docTitle,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF1E293B)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(conflict.causeType.badgeColorHex).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = conflict.causeType.title,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color(conflict.causeType.badgeColorHex),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ursache: ${conflict.diagnosticMessage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF475569)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text("📱 Lokale Version", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF334155))
                                        Text("Titel: ${conflict.localSummary.title}", fontSize = 11.sp, maxLines = 1)
                                        Text("Absender: ${conflict.localSummary.sender.ifBlank { "-" }}", fontSize = 11.sp, maxLines = 1)
                                        Text("Ordner: ${conflict.localSummary.mainCategory}", fontSize = 11.sp, maxLines = 1)
                                        Text("Geändert: ${conflict.localSummary.modifiedFormatted}", fontSize = 10.sp, color = Color(0xFF64748B))
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text("💻 ${conflict.peerName}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF334155))
                                        Text("Titel: ${conflict.remoteSummary.title}", fontSize = 11.sp, maxLines = 1)
                                        Text("Absender: ${conflict.remoteSummary.sender.ifBlank { "-" }}", fontSize = 11.sp, maxLines = 1)
                                        Text("Ordner: ${conflict.remoteSummary.mainCategory}", fontSize = 11.sp, maxLines = 1)
                                        Text("Geändert: ${conflict.remoteSummary.modifiedFormatted}", fontSize = 10.sp, color = Color(0xFF64748B))
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onResolveConflict(conflict, ConflictResolutionAction.KEEP_LOCAL) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text("Lokal behalten", fontSize = 11.sp, textAlign = TextAlign.Center)
                                    }
                                    OutlinedButton(
                                        onClick = { onResolveConflict(conflict, ConflictResolutionAction.ACCEPT_REMOTE) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text("Remote nutzen", fontSize = 11.sp, textAlign = TextAlign.Center)
                                    }
                                    Button(
                                        onClick = { onResolveConflict(conflict, ConflictResolutionAction.KEEP_BOTH_AS_COPY) },
                                        modifier = Modifier.weight(1.1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text("Beide als Kopie", fontSize = 11.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Sync Progress
        AnimatedVisibility(visible = syncProgress.isSyncing) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Synchronisiere mit ${syncProgress.peerName}...",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { syncProgress.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = syncProgress.currentStage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
