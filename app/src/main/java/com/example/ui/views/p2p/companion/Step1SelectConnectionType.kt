package com.example.ui.views.p2p.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.views.PcConnectionType

@Composable
fun Step1SelectConnectionType(
    selectedType: PcConnectionType,
    onSelect: (PcConnectionType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Offizielles myDocAnizer-Light Logo Banner
        com.example.ui.components.MyDocAnizerLightLogo(
            modifier = Modifier.fillMaxWidth(),
            height = 140.dp,
            showSubtitle = true
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Wie möchtest du deinen Computer oder PC-Scanner einbinden?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Wähle die passende Methode für deine Arbeitsweise. Du kannst die Optionen später jederzeit anpassen oder kombinieren.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // OPTION 1: Lokales Web-Interface im Browser mit Zero-Knowledge Verschlüsselung
        ConnectionOptionCard(
            title = "1. Web-Portal im PC-Browser (Zero-Knowledge AES-256 Verschlüsselung)",
            badge = "Empfohlen • 100% Ende-zu-Ende Verschlüsselt",
            icon = Icons.Default.EnhancedEncryption,
            color = Color(0xFF0284C7),
            isSelected = selectedType == PcConnectionType.WEB_BROWSER,
            description = "Öffne einfach Chrome, Edge oder Firefox am PC. Dokumente werden direkt im Browser-RAM mit AES-256-GCM verschlüsselt, bevor sie das Gerät verlassen.",
            highlights = listOf(
                "Zero-Knowledge Kryptographie: Lokale WebCrypto API verschlüsselt vor dem Senden",
                "Keinerlei Klartext-Übertragung im WLAN – abhörsicher selbst ohne öffentliche Zertifizierungsstelle",
                "Optional: 1-Klick lokales Root-Zertifikat für HTTPS mit grünem Schloss ohne Warnung",
                "Funktioniert ohne Software-Installation auch an Firmen-Laptops"
            ),
            onClick = { onSelect(PcConnectionType.WEB_BROWSER) }
        )

        // OPTION 2: Netzwerk-Scanner / SMBv3 / HTTPS-WebDAV Watchfolder
        ConnectionOptionCard(
            title = "2. Netzwerk-Scanner / Watchfolder (SMBv3 / HTTPS-WebDAV)",
            badge = "Voll verschlüsselt für Multifunktionsdrucker",
            icon = Icons.Default.Print,
            color = Color(0xFF16A34A),
            isSelected = selectedType == PcConnectionType.WATCHFOLDER_NAS,
            description = "Dein Dokumentenscanner scannt per 'Scan-to-Folder' verschlüsselt über SMB 3.1.1 oder HTTPS-WebDAV auf dein Heim-NAS, deine Fritz!Box oder einen freigegebenen Ordner.",
            highlights = listOf(
                "Ein Knopfdruck am Scanner genügt – kein PC muss geöffnet sein",
                "Transportverschlüsselung über TLS/HTTPS oder SMB3 mit Integritätsprüfung",
                "Automatischer Hintergrund-Import & lokale Texterkennung (OCR)"
            ),
            onClick = { onSelect(PcConnectionType.WATCHFOLDER_NAS) }
        )

        // OPTION 3: myDocAnizer-Desktop (Windows, macOS, Linux)
        ConnectionOptionCard(
            title = "3. myDocAnizer-Desktop (Windows, macOS, Linux)",
            badge = "Open-Source • Lokaler Voll-Spiegel",
            icon = Icons.Default.Laptop,
            color = Color(0xFF7C3AED),
            isSelected = selectedType == PcConnectionType.DESKTOP_CLIENT,
            description = "Verbinde per myDocAnizer-Desktop dein Windows, macOS oder Linux Desktop-PC um Dokumenten Scanner & Drucker zu verwenden oder für Datei Zugriff im Tresor.",
            highlights = listOf(
                "QR-Code Kopplung: Einmal mit der Smartphone-Kamera vom Monitor scannen",
                "Scan-to-Android: Am PC scannen (WIA/TWAIN/SANE) & mobil per KI indexieren lassen",
                "Lokale FTS5-Volltextsuche, Filter, Bearbeiten & Löschen direkt am PC",
                "PDF-Export & nativer Druckerdialog ohne Cloud"
            ),
            onClick = { onSelect(PcConnectionType.DESKTOP_CLIENT) }
        )

        // OPTION 4: USB-Kabel Direktübertragung (Air-Gapped)
        ConnectionOptionCard(
            title = "4. USB-Kabel Direktübertragung (MTP / Air-Gapped)",
            badge = "100% Offline • Physisch abhörsicher",
            icon = Icons.Default.Usb,
            color = Color(0xFFD97706),
            isSelected = selectedType == PcConnectionType.USB_CABLE,
            description = "Smartphone per USB-C-Kabel an den PC anschließen. Scans in 'Documents/myDocAnizer/Import' kopieren. Vollkommen isoliert ohne jedes Funknetzwerk.",
            highlights = listOf(
                "Kein WLAN, kein Netzwerk, keine Funkverbindung – physische Barriere",
                "Höchste Übertragungsrate für riesige Aktenordner",
                "Ideal für streng vertrauliche Geheimhaltungsstufen"
            ),
            onClick = { onSelect(PcConnectionType.USB_CABLE) }
        )
    }
}

@Composable
private fun ConnectionOptionCard(
    title: String,
    badge: String,
    icon: ImageVector,
    color: Color,
    isSelected: Boolean,
    description: String,
    highlights: List<String>,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Surface(
                        shape = CircleShape,
                        color = color.copy(alpha = 0.15f),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                RadioButton(selected = isSelected, onClick = onClick)
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = 0.12f)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                highlights.forEach { h ->
                    Text("• $h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
