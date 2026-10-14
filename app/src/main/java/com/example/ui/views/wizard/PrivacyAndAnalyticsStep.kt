package com.example.ui.views.wizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.DocAnizerViewModel

@Composable
fun StepPrivacyAndAnalytics(viewModel: DocAnizerViewModel) {
    val analyticsEnabled by viewModel.analyticsEnabled.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Gemeinsam die App verbessern",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Deine Privatsphäre ist uns heilig", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "myDocAnizer ist so konzipiert, dass deine Dokumente und Daten ausschließlich verschlüsselt auf DEINEM Gerät bleiben. Wir haben keinen Zugriff darauf – und das bleibt auch so.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text("Deine Unterstützung hilft uns riesig!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                Text(
                    "Wir stecken viel Herzblut in myDocAnizer. Um die App für dich noch stabiler und besser zu machen, würden wir uns riesig über deine freiwillige Unterstützung durch anonyme Statistiken freuen (z. B. zur Fehlerbehebung oder Optimierung).",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    "Während viele andere Apps Daten heimlich oder als Pflicht erfassen, setzen wir auf echte Fairness: Du entscheidest bei uns völlig frei und ohne jeden Druck. Du hast absolut keine Nachteile, wenn du ablehnst – das ist unser Versprechen für volle Transparenz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ja, ich helfe anonym mit", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Switch(
                        checked = analyticsEnabled,
                        onCheckedChange = { viewModel.setAnalyticsEnabled(it) }
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Icon(
                Icons.Default.Info, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                "Du kannst diese Entscheidung jederzeit in den Einstellungen ändern.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
