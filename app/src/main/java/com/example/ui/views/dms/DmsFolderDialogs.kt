package com.example.ui.views.dms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CreateFolderDialog(
    parentCategory: String?,
    existingMainCategories: List<String>,
    prefixesEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (mainCat: String, mainPrefix: String, subCat: String, subPrefix: String) -> Unit
) {
    var selectedMainCategory by remember { mutableStateOf(parentCategory ?: existingMainCategories.firstOrNull().orEmpty()) }
    var newMainCategory by remember { mutableStateOf("") }
    var mainPrefix by remember { mutableStateOf("") }
    var subCategory by remember { mutableStateOf("") }
    var subPrefix by remember { mutableStateOf("") }
    var isNewMainCategoryMode by remember { mutableStateOf(parentCategory == null && existingMainCategories.isEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (parentCategory != null) "Neuer Unterordner" else "Neuer Ordner",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (parentCategory != null) {
                    Text(
                        text = "Hauptordner: $parentCategory",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    if (existingMainCategories.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isNewMainCategoryMode,
                                onClick = { isNewMainCategoryMode = false },
                                label = { Text("Bestehender Hauptordner") }
                            )
                            FilterChip(
                                selected = isNewMainCategoryMode,
                                onClick = { isNewMainCategoryMode = true },
                                label = { Text("Neuer Hauptordner") }
                            )
                        }
                    }

                    if (isNewMainCategoryMode || existingMainCategories.isEmpty()) {
                        if (prefixesEnabled) {
                            OutlinedTextField(
                                value = mainPrefix,
                                onValueChange = { mainPrefix = it.take(6) },
                                label = { Text("Hauptordner-Präfix (z.B. 01)") },
                                placeholder = { Text("01") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = newMainCategory,
                            onValueChange = { newMainCategory = it },
                            label = { Text("Name des Hauptordners") },
                            placeholder = { Text("z.B. Finanzen, Verträge") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Unterordner Eingabe
                HorizontalDivider()
                Text(
                    text = "Unterordner (optional)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )

                if (prefixesEnabled) {
                    OutlinedTextField(
                        value = subPrefix,
                        onValueChange = { subPrefix = it.take(6) },
                        label = { Text("Unterordner-Präfix (z.B. 01_01)") },
                        placeholder = { Text("01_01") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = subCategory,
                    onValueChange = { subCategory = it },
                    label = { Text("Name des Unterordners (optional)") },
                    placeholder = { Text("z.B. Rechnungen, Gehalt") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalMain = if (parentCategory != null) {
                        parentCategory
                    } else if (isNewMainCategoryMode || existingMainCategories.isEmpty()) {
                        newMainCategory.trim()
                    } else {
                        selectedMainCategory.trim()
                    }

                    if (finalMain.isNotBlank()) {
                        onConfirm(finalMain, mainPrefix.trim(), subCategory.trim(), subPrefix.trim())
                    }
                },
                enabled = (parentCategory != null || (isNewMainCategoryMode && newMainCategory.isNotBlank()) || (!isNewMainCategoryMode && selectedMainCategory.isNotBlank()))
            ) {
                Text("Erstellen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun RenameFolderDialog(
    currentName: String,
    prefix: String,
    isSubCategory: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSubCategory) "Unterordner umbenennen" else "Hauptordner umbenennen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (prefix.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Präfix: $prefix (geschützt)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Neuer Ordnername") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Hinweis: Es wird nur die Bezeichnung geändert. Alle enthaltenen Dokumente bleiben unberührt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onConfirm(newName.trim())
                    }
                },
                enabled = newName.isNotBlank() && newName.trim() != currentName
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun DeleteFolderDialog(
    folderName: String,
    docCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var step by remember { mutableStateOf(1) }

    if (docCount == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Ordner löschen?") },
            text = {
                Text("Möchtest du den leeren Ordner \"$folderName\" wirklich löschen?")
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Abbrechen")
                }
            }
        )
    } else {
        if (step == 1) {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Ordner enthält Dokumente!") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Achtung: Der Ordner \"$folderName\" enthält aktuell $docCount Dokument(e).",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Wenn du fortfährst, werden alle darin abgelegten Dokumente ebenfalls gelöscht.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { step = 2 },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Weiter zum Löschen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.Dangerous, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Endgültige Bestätigung") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Bist du dir absolut sicher?",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Möchtest du den Ordner \"$folderName\" samt den $docCount Dokumenten unwiderruflich löschen? Diese Aktion kann nicht rückgängig gemacht werden.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Ja, Ordner & $docCount Dokumente löschen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                }
            )
        }
    }
}

@Composable
fun CreateDocTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, prefix: String, colorHex: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("") }
    val colorOptions = listOf(
        0xFF2563EB, // Blau
        0xFF16A34A, // Grün
        0xFFD97706, // Bernstein
        0xFFDC2626, // Rot
        0xFF9333EA, // Lila
        0xFF0D9488, // Türkis
        0xFFE11D48, // Rose
        0xFF4F46E5  // Indigo
    )
    var selectedColor by remember { mutableStateOf(colorOptions.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Neue Dokumenten-Gruppe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name der Gruppe") },
                    placeholder = { Text("z.B. Versicherung, Rechnung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it.take(4) },
                    label = { Text("Kürzel (optional, max 4 Zeichen)") },
                    placeholder = { Text("z.B. RE, V") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Farbe auswählen:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colorOptions.forEach { colorHex ->
                        val isSelected = selectedColor == colorHex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorHex))
                                .clickable { selectedColor = colorHex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), prefix.trim(), selectedColor)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Hinzufügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
