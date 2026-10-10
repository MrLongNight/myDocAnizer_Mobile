package com.example.ui.views.p2p.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.views.PcConnectionType

@Composable
fun Step4SummaryAndTest(
    connectionType: PcConnectionType,
    serverIp: String,
    serverPort: Int,
    autoSortWithAi: Boolean,
    isSimulating: Boolean,
    feedback: String?,
    onSendTestScan: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF16A34A).copy(alpha = 0.12f),
            border = BorderStroke(1.5.dp, Color(0xFF16A34A).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(28.dp))
                Column {
                    Text("PC-Schnittstelle ist einsatzbereit!", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                    Text(
                        text = "Gewählte Methode: " + when (connectionType) {
                            PcConnectionType.WEB_BROWSER -> "Web-Portal im Browser (http://$serverIp:$serverPort)"
                            PcConnectionType.WATCHFOLDER_NAS -> "Netzwerk-Watchfolder"
                            PcConnectionType.DESKTOP_CLIENT -> "myDocAnizer-Desktop (Windows, macOS, Linux)"
                            PcConnectionType.USB_CABLE -> "USB-Kabel Übertragung"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Test-Übertragung
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Funktionsprüfung & Test-Scan:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = "Möchtest du prüfen, wie Dokumente vom PC in myDocAnizer ankommen? Drücke auf den Test-Button, um eine Testübertragung zu simulieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onSendTestScan,
                    enabled = !isSimulating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSimulating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Empfange PC-Testdokument...")
                    } else {
                        Icon(Icons.Default.Sensors, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test-Dokument vom PC simulieren")
                    }
                }

                feedback?.let { msg ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Text(
            text = "💡 Hinweis: Du kannst diesen Assistenten oder die Verbindungsdaten jederzeit im Hauptmenü unter Einstellungen > 'PC- & Scanner-Anbindung' erneut aufrufen.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
