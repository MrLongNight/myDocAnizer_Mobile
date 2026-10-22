package com.example.ui.views.rules

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ModelCompatibilityLevel
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.launch

@Composable
fun OnDeviceAiAgentSettingsTab(
    viewModel: DocAnizerViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HuggingFaceModelsContent(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val preferenceMode by viewModel.llmPreferenceMode.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showGgufInfoDialog by remember { mutableStateOf(false) }
    var showCatalogInfoDialog by remember { mutableStateOf(false) }

    val importModelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomModelFromUri(uri) { ok, msg ->
                Toast.makeText(context, if (ok) "✅ $msg" else "❌ $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    // No category filter chips, showing all models directly
    val displayedModels = models

    val recommendedModel = remember(models, hardwareInfo) {
        models.find { it.isHardwareRecommended }
            ?: models.minByOrNull { kotlin.math.abs(it.recommendedRamGb - hardwareInfo.totalRamGb) }
            ?: models.firstOrNull()
    }

    // Split displayedModels into 3 compatibility groups
    val optimalModels = remember(displayedModels, hardwareInfo) {
        displayedModels.filter { hardwareInfo.getCompatibility(it.recommendedRamGb) == ModelCompatibilityLevel.OPTIMAL }
    }
    val limitedModels = remember(displayedModels, hardwareInfo) {
        displayedModels.filter { hardwareInfo.getCompatibility(it.recommendedRamGb) == ModelCompatibilityLevel.LIMITED }
    }
    val notRecommendedModels = remember(displayedModels, hardwareInfo) {
        displayedModels.filter { hardwareInfo.getCompatibility(it.recommendedRamGb) == ModelCompatibilityLevel.NOT_RECOMMENDED }
    }

    // Explanatory Popups as requested
    if (showGgufInfoDialog) {
        AlertDialog(
            onDismissRequest = { showGgufInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Lokale .GGUF-Modelle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Mit diesen Werkzeugen kannst du eigene oder bereits heruntergeladene LLMs im .GGUF-Format verwalten:\n\n" +
                           "• Speicher scannen: Durchsucht den Gerätespeicher und Downloads-Ordner nach passenden .gguf-Dateien und verknüpft diese sofort.\n\n" +
                           "• Importieren: Ermöglicht es, gezielt eine .gguf-Modelldatei auszuwählen und manuell zur App hinzuzufügen.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showGgufInfoDialog = false }) {
                    Text("Verstanden")
                }
            }
        )
    }

    if (showCatalogInfoDialog) {
        AlertDialog(
            onDismissRequest = { showCatalogInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Automatischer Katalog-Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Diese Funktion prüft im Hintergrund, ob neue verifizierte und für myDocAnizer optimierte KI-Modelle auf HuggingFace bereitgestellt wurden.\n\n" +
                           "Du kannst das Prüfungsintervall anpassen. Sobald ein neues Modell verfügbar ist, wirst du benachrichtigt und kannst es direkt offline herunterladen.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showCatalogInfoDialog = false }) {
                    Text("Verstanden")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        newModelsNotification?.let { notifText ->
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Celebration, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = notifText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { viewModel.dismissNewModelsNotification() }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen", modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        // 1. Unified, compact card for Catalog & Local GGUF tools with Info buttons
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Rebind & Import (compact GGUF tools) with Info Popup
                    Column(modifier = Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Lokale .GGUF Dateien", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { showGgufInfoDialog = true }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.relinkDownloadedModels { count ->
                                        Toast.makeText(
                                            context,
                                            if (count > 0) {
                                                "✅ $count bestehende(s) Modell(e) verknüpft!"
                                            } else {
                                                "ℹ️ Speicher durchsucht: Alle vorhanden."
                                            },
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(28.dp).testTag("btn_rescan_existing_models"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Scannen", fontSize = 9.sp, maxLines = 1)
                            }
                            FilledTonalButton(
                                onClick = { importModelPicker.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f).height(28.dp).testTag("btn_import_gguf_file"),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Import", fontSize = 9.sp, maxLines = 1)
                            }
                        }
                    }

                    // Vertical divider line for aesthetic structure
                    Box(modifier = Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)))

                    // Right side: Auto Catalog Check (compact update settings) with Info Popup
                    Column(modifier = Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Auto-Katalog Check", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .size(13.dp)
                                        .clickable { showCatalogInfoDialog = true }
                                )
                            }
                            Switch(
                                checked = autoCheckEnabled,
                                onCheckedChange = { viewModel.setLlmCatalogAutoCheckEnabled(it) },
                                modifier = Modifier.testTag("switch_auto_catalog_check").scale(0.75f).height(20.dp)
                            )
                        }
                        if (autoCheckEnabled) {
                            val label = when (checkInterval) {
                                "EVERY_START" -> "Bei Start"
                                "DAILY" -> "Täglich"
                                "WEEKLY" -> "Wöchentlich"
                                else -> "Deaktiviert"
                            }
                            OutlinedButton(
                                onClick = {
                                    val nextInterval = when (checkInterval) {
                                        "EVERY_START" -> "DAILY"
                                        "DAILY" -> "WEEKLY"
                                        "WEEKLY" -> "DISABLED"
                                        else -> "EVERY_START"
                                    }
                                    viewModel.setLlmCatalogCheckInterval(nextInterval)
                                },
                                modifier = Modifier.fillMaxWidth().height(24.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Intervall: $label 🔄", fontSize = 9.sp, maxLines = 1)
                            }
                        } else {
                            Text("Automatische Prüfung inaktiv", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        // 2. Headings for the HuggingFace models selection
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
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
        }

        // 3. Live hardware testing & diagnostics is now visually placed directly inside the HuggingFace selection flow
        item {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Live-Hardware-Prüfung & KI-Diagnose",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", hardwareInfo.totalRamGb)} GB RAM • ${hardwareInfo.cpuCores} Kerne @ ${hardwareInfo.maxCpuFreqMhz} MHz",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "SoC: ${hardwareInfo.socModel.ifBlank { "ARM64" }}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Erweiterte Hardware-Telemetrie & Micro-Benchmark
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vulkan: ${if (hardwareInfo.hasVulkanGpu) "✓ Ja" else "✗ Nein"} • DotProd: ${if (hardwareInfo.supportsArmDotProd) "✓ Ja" else "✗ Nein"}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "CPU-Index: ${hardwareInfo.cpuBenchmarkScore}/100",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Präferenz-Umschalter (Qualität vs. Geschwindigkeit)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LLM-Empfehlungspräferenz:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (preferenceMode == com.example.model.LlmPreferenceMode.QUALITY_FOCUS) "75% Qualität / 25% Speed" else "55% Qualität / 45% Speed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 8.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = preferenceMode == com.example.model.LlmPreferenceMode.QUALITY_FOCUS,
                                    onClick = { viewModel.setLlmPreferenceMode(com.example.model.LlmPreferenceMode.QUALITY_FOCUS) },
                                    label = { Text("Qualitäts-Fokus (75%)", fontSize = 9.5.sp) },
                                    modifier = Modifier.weight(1f).height(28.dp)
                                )
                                FilterChip(
                                    selected = preferenceMode == com.example.model.LlmPreferenceMode.SPEED_FOCUS,
                                    onClick = { viewModel.setLlmPreferenceMode(com.example.model.LlmPreferenceMode.SPEED_FOCUS) },
                                    label = { Text("Speed-Fokus (45%)", fontSize = 9.5.sp) },
                                    modifier = Modifier.weight(1f).height(28.dp)
                                )
                            }
                        }
                    }

                    recommendedModel?.let { rec ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
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
                                        fontSize = 9.sp,
                                        lineHeight = 12.sp
                                    )
                                }

                                if (!rec.isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FilledTonalButton(
                                        onClick = { viewModel.selectHuggingFaceModel(rec.id) },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text("Wählen", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Inferenz-Parameter (Experten-Modus)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Threads: ${inferenceConfig.threadCount} • Temp: ${inferenceConfig.temperature}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // 4. Render sorted HuggingFace LLMs in 3 Horizontal carousels (LazyRows) with counts, header-based arrows, borders and highlighted select state colors
        if (optimalModels.isNotEmpty()) {
            item {
                HorizontalModelCarousel(
                    title = "🟢 Optimal für Ihr Gerät (${optimalModels.size})",
                    titleColor = Color(0xFF16A34A),
                    modelsList = optimalModels,
                    hardwareInfo = hardwareInfo,
                    viewModel = viewModel,
                    coroutineScope = coroutineScope
                )
            }
        }

        if (limitedModels.isNotEmpty()) {
            item {
                HorizontalModelCarousel(
                    title = "🟡 Eingeschränkt nutzbar (Hohe Last) (${limitedModels.size})",
                    titleColor = Color(0xFFD97706),
                    modelsList = limitedModels,
                    hardwareInfo = hardwareInfo,
                    viewModel = viewModel,
                    coroutineScope = coroutineScope
                )
            }
        }

        if (notRecommendedModels.isNotEmpty()) {
            item {
                HorizontalModelCarousel(
                    title = "🔴 Nicht empfohlen (Benötigt viel RAM) (${notRecommendedModels.size})",
                    titleColor = Color(0xFFDC2626),
                    modelsList = notRecommendedModels,
                    hardwareInfo = hardwareInfo,
                    viewModel = viewModel,
                    coroutineScope = coroutineScope
                )
            }
        }
    }
}

@Composable
fun HorizontalModelCarousel(
    title: String,
    titleColor: Color,
    modelsList: List<com.example.service.HuggingFaceModelInfo>,
    hardwareInfo: com.example.model.DeviceHardwareInfo,
    viewModel: DocAnizerViewModel,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val state = rememberLazyListState()
    val showLeftArrow by remember { derivedStateOf { state.canScrollBackward } }
    val showRightArrow by remember { derivedStateOf { state.canScrollForward } }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Category Header with Title and Scroll Arrows (Cleanly placed in header so cards are NEVER covered or shifted)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val targetIndex = (state.firstVisibleItemIndex - 1).coerceAtLeast(0)
                            state.animateScrollToItem(targetIndex)
                        }
                    },
                    enabled = showLeftArrow,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        contentDescription = "Scroll Links",
                        tint = if (showLeftArrow) titleColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val targetIndex = (state.firstVisibleItemIndex + 1).coerceAtMost(modelsList.size - 1)
                            state.animateScrollToItem(targetIndex)
                        }
                    },
                    enabled = showRightArrow,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Scroll Rechts",
                        tint = if (showRightArrow) titleColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Horizontal Carousel with 100% unobstructed cards
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
        ) {
            items(modelsList, key = { it.id }) { model ->
                ModelCardHorizontal(model, hardwareInfo, viewModel)
            }
        }
    }
}

@Composable
fun ModelCardHorizontal(
    model: com.example.service.HuggingFaceModelInfo,
    hardwareInfo: com.example.model.DeviceHardwareInfo,
    viewModel: DocAnizerViewModel
) {
    val compatibility = hardwareInfo.getCompatibility(model.recommendedRamGb)
    var isBenchmarking by remember { mutableStateOf(false) }
    var benchmarkResult by remember { mutableStateOf<com.example.model.ModelBenchmarkResult?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (benchmarkResult != null) {
        val bench = benchmarkResult!!
        AlertDialog(
            onDismissRequest = { benchmarkResult = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Dual-Benchmark: ${bench.modelName.take(22)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Gesamt-Score: ${bench.overallScore}/100", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Gewichtung: 65% Qualität + 35% Geschwindigkeit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (bench.isMeasured) {
                        Text("• Qualität: ${bench.qualityScore}% (heuristische Testfall-Prüfung)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("• Geschwindigkeit: ${bench.tokensPerSecond} TPS (Schätzung; ${bench.latencyMs} ms Laufzeit)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Benchmark nicht gemessen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Text("• Absender: ${bench.extractedSender} | Typ: ${bench.extractedDocType}", fontSize = 11.sp)
                    if (bench.extractedAmount.isNotBlank()) {
                        Text("• Rechnungsbetrag: ${bench.extractedAmount}", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Evaluierungsergebnis:\n${bench.evaluationDetails}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { benchmarkResult = null }) {
                    Text("Schließen")
                }
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (model.isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (model.isSelected) 2.dp else 0.dp,
        border = BorderStroke(
            width = if (model.isSelected) 1.5.dp else 1.dp,
            color = if (model.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .width(290.dp)
            .height(148.dp) // STRIKT UNIFORM FIXED SIZING & COMPACT PROFILE
            .testTag("model_card_${model.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Icon + 2-line capable title + Subtitle + Compatibility Badge
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (model.isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = if (model.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = model.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    lineHeight = 13.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (model.isNewRelease) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Text(
                                            text = "NEU",
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 7.5.sp,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${model.parameterSize} • ${if (model.estimatedTps > 0f) "≈${model.estimatedTps} TPS (Schätzung)" else model.modelCategory} • Score: ${model.recommendationScore}/100",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (compatibility) {
                            ModelCompatibilityLevel.OPTIMAL -> Color(0xFF16A34A).copy(alpha = 0.12f)
                            ModelCompatibilityLevel.LIMITED -> Color(0xFFEAB308).copy(alpha = 0.15f)
                            ModelCompatibilityLevel.NOT_RECOMMENDED -> Color(0xFFEF4444).copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = when (compatibility) {
                                ModelCompatibilityLevel.OPTIMAL -> "● Optimal"
                                ModelCompatibilityLevel.LIMITED -> "● Hohe Last"
                                ModelCompatibilityLevel.NOT_RECOMMENDED -> "● Viel RAM"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.5.sp,
                            color = when (compatibility) {
                                ModelCompatibilityLevel.OPTIMAL -> Color(0xFF15803D)
                                ModelCompatibilityLevel.LIMITED -> Color(0xFFB45309)
                                ModelCompatibilityLevel.NOT_RECOMMENDED -> Color(0xFFDC2626)
                            },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Description: 2 lines with tight line height to prevent wasted whitespace
                Box(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                    Text(
                        text = model.descriptionDe,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "${model.downloadSizeMb} MB • ${model.ramBadge}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }

                if (model.isDownloading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { model.downloadProgress },
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp
                        )
                        Text(
                            text = "${(model.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        IconButton(
                            onClick = { viewModel.cancelDownload(model.id) },
                            modifier = Modifier.size(20.dp).testTag("btn_cancel_download_${model.id}")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Download pausieren",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                } else if (!model.isDownloaded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    isBenchmarking = true
                                    benchmarkResult = viewModel.runModelBenchmark(model.id)
                                    isBenchmarking = false
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp).testTag("btn_bench_${model.id}")
                        ) {
                            if (isBenchmarking) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp)
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Bench", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        FilledTonalButton(
                            onClick = { viewModel.startBackgroundDownload(model.id) },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp).testTag("btn_download_${model.id}")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Laden", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.deleteModel(model.id) },
                            modifier = Modifier.size(24.dp).testTag("btn_delete_${model.id}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Löschen",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    isBenchmarking = true
                                    benchmarkResult = viewModel.runModelBenchmark(model.id)
                                    isBenchmarking = false
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp).testTag("btn_bench_${model.id}")
                        ) {
                            if (isBenchmarking) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.dp)
                            } else {
                                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Bench", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!model.isSelected) {
                            Button(
                                onClick = { viewModel.selectModel(model.id) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp).testTag("btn_select_${model.id}")
                            ) {
                                Text("Aktivieren", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(10.dp))
                                    Text(
                                        text = "Aktiv",
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
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
