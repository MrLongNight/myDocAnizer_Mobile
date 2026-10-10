package com.example.ui.views.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DocumentEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DocumentMetadataEditorPane(
    document: DocumentEntity,
    isImage: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailRow("Format", if (isImage) "Original Bilddatei (JPG)" else "Archiv-PDF (A4)")
            val prefixE1 = if (document.mainCategoryId.isNotBlank()) "${document.mainCategoryId}_" else ""
            val prefixE2 = if (document.subCategoryId.isNotBlank()) "${document.subCategoryId}-" else ""
            DetailRow("Ablageort", "$prefixE1${document.mainCategory} ➔ $prefixE2${document.subCategory}")
            DetailRow("Seitenanzahl", if (isImage) "1 Bild" else "${document.pageCount} Seite(n)")
            DetailRow("Farbmodus", if (document.colorMode == "COLOR") "Farbe" else "Schwarz/Weiß (S/W)")
            DetailRow("Dateigröße", document.fileSizeFormatted)
            DetailRow("Erstellt am", formatDate(document.createdAt))
        }
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    return sdf.format(Date(timestamp))
}
