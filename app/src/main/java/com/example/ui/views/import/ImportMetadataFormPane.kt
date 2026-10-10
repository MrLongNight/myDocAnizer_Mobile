package com.example.ui.views.import

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.DocTypeItem

@Composable
fun ImportMetadataFormPane(
    title: String,
    onTitleChange: (String) -> Unit,
    selectedMainCategory: String,
    selectedSubCategory: String,
    onOpenFolderPicker: () -> Unit,
    docTypes: List<DocTypeItem>,
    selectedDocType: String,
    onDocTypeSelected: (String) -> Unit,
    tagsInput: String,
    onTagsChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Ablage & Metadaten",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Titel / Dateiname *") },
                placeholder = { Text("z.B. Mobilfunk_Vodafone_03-2026") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Zielordner (DMS):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedCard(
                    onClick = onOpenFolderPicker,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "$selectedMainCategory ➔ $selectedSubCategory",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Dokument-Typ (Doc-Typ):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(docTypes) { dt ->
                        val isSelected = selectedDocType == dt.name
                        FilterChip(
                            selected = isSelected,
                            onClick = { onDocTypeSelected(dt.name) },
                            label = { Text(dt.name) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = tagsInput,
                onValueChange = onTagsChange,
                label = { Text("Tags / Schlagwörter (kommagetrennt)") },
                placeholder = { Text("z.B. Rechnung, Mobilfunk, 2026") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
