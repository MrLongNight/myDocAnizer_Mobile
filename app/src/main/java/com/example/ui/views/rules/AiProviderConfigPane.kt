package com.example.ui.views.rules

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DocAnizerViewModel

@Composable
fun OnDeviceAiAgentSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HuggingFaceModelsContent(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun HuggingFaceModelsContent(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val models by viewModel.availableModels.collectAsState()
    val hardwareInfo by viewModel.deviceHardwareInfo.collectAsState()
    val inferenceConfig by viewModel.llmInferenceConfig.collectAsState()
    val isExpertMode by viewModel.isExpertMode.collectAsState()
    val isCheckingNewModels by viewModel.isCheckingNewModels.collectAsState()
    val newModelsNotification by viewModel.newModelsNotification.collectAsState()
    val autoCheckEnabled by viewModel.llmCatalogAutoCheckEnabled.collectAsState()
    val checkInterval by viewModel.llmCatalogCheckInterval.collectAsState()

    val importModelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomModelFromUri(uri) { ok, msg ->
                Toast.makeText(context, if (ok) "✅ $msg" else "❌ $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    var onlyShowCompatible by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("Alle") }

    val displayedModels = remember(models, onlyShowCompatible, hardwareInfo, selectedCategoryFilter) {
        models.filter { model ->
            val matchHardware = if (onlyShowCompatible && hardwareInfo.totalRamGb > 0) {
                model.recommendedRamGb <= hardwareInfo.totalRamGb + 0.5f
            } else true
            val matchCategory = when (selectedCategoryFilter) {
                "Alle" -> true
                "Empfohlen" -> model.isHardwareRecommended || (model.recommendedRamGb <= hardwareInfo.totalRamGb + 0.2f)
                "DMS & Rechnungen" -> model.modelCategory.contains("DMS", ignoreCase = true) || model.modelCategory.contains("Rechnung", ignoreCase = true)
                else -> model.modelCategory == selectedCategoryFilter
            }
            matchHardware && matchCategory
        }
    }

    val recommendedModel = remember(models, hardwareInfo) {
        models.find { it.isHardwareRecommended }
            ?: models.minByOrNull { kotlin.math.abs(it.recommendedRamGb - hardwareInfo.totalRamGb) }
            ?: models.firstOrNull()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        newModelsNotification?.let { notifText ->
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = notifText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { viewModel.dismissNewModelsNotification() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().testTag("card_reconnect_downloaded_models")
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gedownloadete Modelle wiederverbinden",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Bereits vorhandene .gguf-Modelle aus Downloads/Gerätespeicher nach App-Neuinstallation sofort neu verknüpfen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.relinkDownloadedModels { count ->
                                    Toast.makeText(
                                        context,
                                        if (count > 0) "✅ $count bestehende(s) Modell(e) erfolgreich verknüpft!" else "ℹ️ Speicher durchsucht: Alle vorhandenen Modelle sind aktuell.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(36.dp).testTag("btn_rescan_existing_models")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Speicher scannen", fontSize = 11.sp, maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = {
                                importModelPicker.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f).height(36.dp).testTag("btn_import_gguf_file")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(".GGUF importieren", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("card_catalog_sync_settings")
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = "Automatischer Katalog-Check",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Prüft neue verifizierte HuggingFace GGUF-Modelle im Hintergrund",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Switch(
                            checked = autoCheckEnabled,
                            onCheckedChange = { viewModel.setLlmCatalogAutoCheckEnabled(it) },
                            modifier = Modifier.testTag("switch_auto_catalog_check")
                        )
                    }

                    if (autoCheckEnabled) {
                        Text("Prüf-Intervall:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "EVERY_START" to "Bei Start",
                                "DAILY" to "Täglich",
                                "WEEKLY" to "Wöchentlich",
                                "DISABLED" to "Deaktiviert"
                            ).forEach { (key, label) ->
                                FilterChip(
                                    selected = checkInterval == key,
                                    onClick = { viewModel.setLlmCatalogCheckInterval(key) },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "🔬 Live-Hardware-Prüfung & KI-Diagnose",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", hardwareInfo.totalRamGb)} GB RAM • ${hardwareInfo.cpuCores} CPU-Kerne • ${hardwareInfo.performanceTier}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "100% Offline",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    recommendedModel?.let { rec ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(15.dp))
                                        Text(
                                            text = "Empfehlung für dein Gerät: ${rec.name}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = rec.hardwareRecommendationReason.ifBlank { "Optimal abgestimmt auf deinen Arbeitsspeicher (${rec.ramBadge})." },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                }

                                if (!rec.isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledTonalButton(
                                        onClick = { viewModel.selectHuggingFaceModel(rec.id) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Wählen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Aktiv",
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isExpertMode) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Inferenz-Parameter (Experten-Modus)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Threads: ${inferenceConfig.threadCount} • Temp: ${inferenceConfig.temperature}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HuggingFace Modelle (${displayedModels.size}):",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Geprüfte GGUF-Modelle für 100% Offline-Inferenz",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isExpertMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { viewModel.syncModelCatalogFromRemote(forceCheck = true) },
                            enabled = !isCheckingNewModels,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp).testTag("btn_sync_model_catalog")
                        ) {
                            if (isCheckingNewModels) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prüfe...", fontSize = 11.sp, maxLines = 1)
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Katalog aktualisieren", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Alle", "Empfohlen", "DMS & Rechnungen", "Reasoning & Logik", "Verträge & Jura", "Finanzen & Tabellen", "Mehrsprachig").forEach { cat ->
                        val isSelected = when (cat) {
                            "Alle" -> selectedCategoryFilter == "Alle"
                            "Empfohlen" -> selectedCategoryFilter == "Empfohlen"
                            "DMS & Rechnungen" -> selectedCategoryFilter == "DMS & Rechnungen" || selectedCategoryFilter == "DMS & Rechnungsextraktion"
                            else -> selectedCategoryFilter == cat
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { 
                                selectedCategoryFilter = cat
                            },
                            label = { 
                                val labelText = when (cat) {
                                    "Alle" -> "Alle (${models.size})"
                                    "Empfohlen" -> "⭐ Empfohlen"
                                    else -> cat
                                }
                                Text(labelText, fontSize = 11.sp, maxLines = 1) 
                            },
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
            }
        }

        items(displayedModels, key = { it.id }) { model ->
            val compatibility = hardwareInfo.getCompatibility(model.recommendedRamGb)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = if (model.isSelected) 2.dp else 0.dp,
                border = BorderStroke(
                    width = if (model.isSelected) 1.5.dp else 1.dp,
                    color = if (model.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth().testTag("model_card_${model.id}")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                shape = RoundedCornerShape(8.dp),
                                color = if (model.isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = if (model.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = model.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (model.isNewRelease) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.tertiary
                                        ) {
                                            Text(
                                                text = "NEU",
                                                color = MaterialTheme.colorScheme.onTertiary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${model.parameterSize} • ${model.modelCategory}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (compatibility) {
                                com.example.model.ModelCompatibilityLevel.OPTIMAL -> Color(0xFF16A34A).copy(alpha = 0.12f)
                                com.example.model.ModelCompatibilityLevel.LIMITED -> Color(0xFFEAB308).copy(alpha = 0.15f)
                                com.example.model.ModelCompatibilityLevel.NOT_RECOMMENDED -> Color(0xFFEF4444).copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                text = when (compatibility) {
                                    com.example.model.ModelCompatibilityLevel.OPTIMAL -> "● Optimal"
                                    com.example.model.ModelCompatibilityLevel.LIMITED -> "● Hohe Last"
                                    com.example.model.ModelCompatibilityLevel.NOT_RECOMMENDED -> "● Viel RAM"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = when (compatibility) {
                                    com.example.model.ModelCompatibilityLevel.OPTIMAL -> Color(0xFF15803D)
                                    com.example.model.ModelCompatibilityLevel.LIMITED -> Color(0xFFB45309)
                                    com.example.model.ModelCompatibilityLevel.NOT_RECOMMENDED -> Color(0xFFDC2626)
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = model.descriptionDe,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${model.downloadSizeMb} MB • ${model.ramBadge}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        if (model.isDownloading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { model.downloadProgress },
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "${(model.downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        } else if (!model.isDownloaded) {
                            FilledTonalButton(
                                onClick = { viewModel.downloadModel(model.id) },
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(28.dp).testTag("btn_download_${model.id}")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Laden", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.deleteModel(model.id) },
                                    modifier = Modifier.size(28.dp).testTag("btn_delete_${model.id}")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Löschen",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }

                                if (!model.isSelected) {
                                    Button(
                                        onClick = { viewModel.selectModel(model.id) },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp).testTag("btn_select_${model.id}")
                                    ) {
                                        Text("Aktivieren", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                                            Text(
                                                text = "Aktiv",
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
