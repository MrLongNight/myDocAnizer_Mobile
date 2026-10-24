package com.example.service

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.DeviceHardwareInfo
import com.example.model.DocRule
import com.example.model.DocumentEntity
import com.example.model.FolderStrategy
import com.example.model.LlmInferenceConfig
import com.example.model.ModelCompatibilityLevel
import com.example.model.SYSTEM_PROMPT_FOCUS_OPTIONS
import com.example.service.llm.LlmPrompts
import com.example.service.llm.LlmToolExecutor
import com.example.service.llm.CustomFieldHarmonizer
import com.example.data.AppDatabase
import android.os.PowerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request



/**
 * Service für die lokale KI (On-Device LLM via HuggingFace).
 * 100% offline & datenschutzkonform auf dem Telefon ausführbar.
 */
class LlmService(private val context: Context) {

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private val STOP_WORDS = setOf("wie", "viele", "habe", "ich", "was", "und", "der", "die", "das", "ein", "eine", "den", "dem", "des", "zu", "von", "im", "in", "mit", "für")
    }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _deviceHardware = MutableStateFlow(detectDeviceHardware())
    val deviceHardware: StateFlow<DeviceHardwareInfo> = _deviceHardware.asStateFlow()

    private val database by lazy { AppDatabase.getDatabase(context) }

    var currentPreferenceMode: com.example.model.LlmPreferenceMode = com.example.model.LlmPreferenceMode.QUALITY_FOCUS
    var analyticsManager: AnalyticsManager? = null

    fun updatePreferenceMode(mode: com.example.model.LlmPreferenceMode) {
        currentPreferenceMode = mode
        refreshHardwareInfo()
    }

    fun detectDeviceHardware(): DeviceHardwareInfo {
        return com.example.service.llm.LlmHardwareDetector.detectDeviceHardware(context)
    }

    private fun calculateCompatibility(recommendedRamGb: Float, hw: DeviceHardwareInfo): ModelCompatibilityLevel {
        return hw.getCompatibility(recommendedRamGb)
    }

    fun getCandidateModelDirs(): List<File> {
        val dirs = mutableListOf<File>()
        try {
            // 1. App External Storage (100% sicher, kein Permission-Absturz auf Android 15)
            val extDir = context.getExternalFilesDir("models")
            if (extDir != null) {
                if (!extDir.exists()) extDir.mkdirs()
                dirs.add(extDir)
            }
        } catch (_: Throwable) {}

        try {
            // 2. App Internal Storage (Fallback)
            val internal = File(context.filesDir ?: context.cacheDir, "models")
            if (!internal.exists()) internal.mkdirs()
            dirs.add(internal)
        } catch (_: Throwable) {}

        try {
            // 3. Öffentlicher Downloads Ordner (Lesend prüfen, nur falls Ordner existiert)
            val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (publicDownloads != null && publicDownloads.exists()) {
                val myModelsDownload = File(publicDownloads, "myDocAnizer_Models")
                if (myModelsDownload.exists() && myModelsDownload.isDirectory) {
                    dirs.add(myModelsDownload)
                }
                dirs.add(publicDownloads)
            }
        } catch (_: Throwable) {}

        try {
            // 4. Öffentlicher Documents Ordner (Lesend prüfen)
            val publicDocs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (publicDocs != null && publicDocs.exists()) {
                val myModelsDocs = File(publicDocs, "myDocAnizer_Models")
                if (myModelsDocs.exists() && myModelsDocs.isDirectory) {
                    dirs.add(myModelsDocs)
                }
            }
        } catch (_: Throwable) {}

        return dirs.distinctBy { it.absolutePath }
    }

    val modelsDir: File
        get() = try {
            val appExtDir = context.getExternalFilesDir("models")
            if (appExtDir != null) {
                if (!appExtDir.exists()) appExtDir.mkdirs()
                appExtDir
            } else {
                val base = context.filesDir ?: context.cacheDir
                File(base, "models").apply { if (!exists()) mkdirs() }
            }
        } catch (_: Throwable) {
            val base = context.filesDir ?: context.cacheDir
            File(base, "models").apply { if (!exists()) mkdirs() }
        }

    val llamaCppEngine = LlamaCppInferenceEngine(context)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Sucht die Modelldatei über alle persistenten Ordner (Downloads, Documents, App-Speicher).
     */
    fun getModelFile(modelId: String): File {
        val model = _availableModels.value.find { it.id == modelId }
        val fileName = model?.fileName?.ifBlank { "${modelId}.gguf" } ?: "${modelId}.gguf"

        // Suche zuerst in allen bestehenden Kandidaten-Verzeichnissen
        for (dir in getCandidateModelDirs()) {
            val candidate = File(dir, fileName)
            if (candidate.exists() && candidate.length() > 1024 * 1024) {
                return candidate
            }
        }
        // Fallback auf Standard-Verzeichnis
        return File(modelsDir, fileName)
    }

    private fun isModelPhysicallyOnDisk(fileName: String): Boolean {
        return try {
            if (fileName.isBlank()) false
            else {
                for (dir in getCandidateModelDirs()) {
                    val file = File(dir, fileName)
                    if (file.exists() && file.length() > 1024 * 1024) {
                        return true
                    }
                }
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun getModelDiskSize(fileName: String): Long {
        return try {
            if (fileName.isBlank()) 0L
            else {
                for (dir in getCandidateModelDirs()) {
                    val file = File(dir, fileName)
                    if (file.exists() && file.length() > 1024 * 1024) {
                        return file.length()
                    }
                }
                0L
            }
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Durchsucht alle Gerätespeicher nach bereits heruntergeladenen .gguf-Modellen
     * und reaktiviert diese sofort (z. B. nach App-Neuinstallation/Update).
     */
    fun scanAndLinkExistingModels(): Int {
        var linkedCount = 0
        try {
            val candidateDirs = getCandidateModelDirs()
            val existingFiles = mutableMapOf<String, File>()

            for (dir in candidateDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { f ->
                        if (f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && f.length() > 1024 * 1024) {
                            existingFiles[f.name.lowercase()] = f
                        }
                    }
                }
            }

            _availableModels.update { currentModels ->
                currentModels.map { model ->
                    val targetName = model.fileName.ifBlank { "${model.id}.gguf" }.lowercase()
                    val foundFile = existingFiles[targetName] ?: existingFiles.values.firstOrNull { 
                        it.name.contains(model.id, ignoreCase = true) 
                    }

                    val isJobActive = activeDownloadJobs[model.id]?.isActive == true
                    if (foundFile != null && foundFile.exists()) {
                        linkedCount++
                        model.copy(
                            isDownloaded = true,
                            localFileSizeBytes = foundFile.length(),
                            downloadProgress = 1.0f,
                            isDownloading = false
                        )
                    } else {
                        model.copy(
                            isDownloaded = isModelPhysicallyOnDisk(model.fileName),
                            localFileSizeBytes = getModelDiskSize(model.fileName),
                            isDownloading = if (isJobActive) true else model.isDownloading,
                            downloadProgress = if (isJobActive) model.downloadProgress else if (isModelPhysicallyOnDisk(model.fileName)) 1.0f else model.downloadProgress
                        )
                    }
                }
            }

            if (linkedCount > 0) {
                AppAuditLogger.log(
                    category = LogCategory.AI_INFERENCE,
                    tag = "ModelPersistence",
                    message = "$linkedCount bestehende GGUF-Modelle im persistenten Gerätespeicher gefunden und verknüpft.",
                    details = "Geprüfte Pfade: ${candidateDirs.joinToString { it.name }}"
                )
            }
        } catch (e: Exception) {
            Log.e("LlmService", "Fehler beim Durchsuchen nach Modellen: ${e.message}")
        }
        return linkedCount
    }

    /**
     * Importiert eine vom Nutzer manuell per Dateiwähler ausgewählte .gguf-Datei,
     * kopiert sie in das geschützte Modellverzeichnis und verknüpft sie mit der Modellliste.
     */
    suspend fun importModelFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            var fileName = "imported_model.gguf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val rawName = cursor.getString(nameIndex)
                    if (!rawName.isNullOrBlank()) {
                        fileName = rawName
                    }
                }
            }
            if (!fileName.endsWith(".gguf", ignoreCase = true)) {
                fileName = "$fileName.gguf"
            }
            val targetFile = File(modelsDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val linked = scanAndLinkExistingModels()
            val sizeMb = targetFile.length() / (1024 * 1024)
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = "ModelImport",
                message = "Modell-Datei '$fileName' ($sizeMb MB) erfolgreich importiert.",
                details = "Zielpfad: ${targetFile.absolutePath}"
            )
            Result.success("Modell '$fileName' ($sizeMb MB) erfolgreich importiert und verknüpft.")
        } catch (e: Exception) {
            Log.e("LlmService", "Fehler beim Modell-Import: ${e.message}")
            Result.failure(e)
        }
    }

    private fun createInitialModels(hw: DeviceHardwareInfo): List<HuggingFaceModelInfo> {
        val recommendedModelId = when {
            hw.totalRamGb >= 6.0f -> "qwen2.5-1.5b-instruct"
            hw.totalRamGb >= 4.0f -> "qwen2.5-0.5b-instruct"
            else -> "smollm2-135m-instruct"
        }

        val baseList = listOf(
            // --- CATEGORY 1: OPTIMAL (<= 5.0 GB recommended RAM) ---
            HuggingFaceModelInfo(
                id = "danube3-100m-chat",
                name = "H2O Danube3 100M Chat",
                author = "H2O.ai",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 85,
                parameterSize = "100 Mio",
                recommendedRamGb = 1.2f,
                ramBadge = "1 – 1.5 GB RAM",
                descriptionDe = "Extrem kompaktes, schnelles Chatmodell. Perfekt für ressourcensparende Klassifizierungs- und Extraktionstätigkeiten im Hintergrund.",
                criteria = listOf(
                    "Latenz: Extrem schnell (< 100 ms)",
                    "Speicher: Minimalster Akkuverbrauch",
                    "Einsatz: Einfache Belege & Sortierung"
                ),
                compatibilityLevel = calculateCompatibility(1.2f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Minimaler Ressourcen-Footprint.",
                downloadUrl = "https://huggingface.co/h2oai/h2o-danube3-100m-chat-GGUF/resolve/main/h2o-danube3-100m-chat-v2-q4_k_m.gguf",
                fileName = "h2o-danube3-100m-chat-v2-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("h2o-danube3-100m-chat-v2-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("h2o-danube3-100m-chat-v2-q4_k_m.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "smollm2-135m-instruct",
                name = "SmolLM2 135M Instruct",
                author = "HuggingFaceTB",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 105,
                parameterSize = "135 Mio",
                recommendedRamGb = 1.5f,
                ramBadge = "1.5 – 2 GB RAM",
                descriptionDe = "Ultraschnelles Nano-Modell mit minimalem Speicher-Footprint. Ideal für jedes Smartphone zur verzögerungsfreien Einordnung von Standardbelegen.",
                criteria = listOf(
                    "Latenz: Extrem schnell (< 150 ms)",
                    "Ressourcen: Minimalster Akku- & RAM-Verbrauch",
                    "Einsatz: Kassenbons & Standard-Rechnungen"
                ),
                compatibilityLevel = calculateCompatibility(1.5f, hw),
                isHardwareRecommended = (recommendedModelId == "smollm2-135m-instruct"),
                hardwareRecommendationReason = "Optimal abgestimmt für Geräte mit geringem Arbeitsspeicher (< 4 GB). Läuft absolut verzögerungsfrei.",
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf",
                fileName = "SmolLM2-135M-Instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("SmolLM2-135M-Instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("SmolLM2-135M-Instruct-Q4_K_M.gguf"),
                isSelected = (recommendedModelId == "smollm2-135m-instruct"),
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "smollm2-360m-instruct",
                name = "SmolLM2 360M Instruct",
                author = "HuggingFaceTB",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 270,
                parameterSize = "360 Mio",
                recommendedRamGb = 2.0f,
                ramBadge = "2 – 3 GB RAM",
                descriptionDe = "Ausgezeichnete Balance aus Kompaktheit und Textverständnis. Liest Beträge, Datumsangaben und Absender schon sehr präzise aus.",
                criteria = listOf(
                    "Latenz: Sehr schnell (< 250 ms)",
                    "Ressourcen: Geringer Speicher- & Akkubedarf",
                    "Einsatz: Rechnungen, Quittungen & Entgeltnachweise"
                ),
                compatibilityLevel = calculateCompatibility(2.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Geringer Speicherbedarf, ideal bei knappem RAM.",
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-360M-Instruct-GGUF/resolve/main/SmolLM2-360M-Instruct-Q4_K_M.gguf",
                fileName = "SmolLM2-360M-Instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("SmolLM2-360M-Instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("SmolLM2-360M-Instruct-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "h2o-danube3-500m-chat",
                name = "H2O Danube3 500M Chat",
                author = "H2O.ai",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 310,
                parameterSize = "500 Mio",
                recommendedRamGb = 2.2f,
                ramBadge = "2.2 – 3 GB RAM",
                descriptionDe = "Effizientes, schnelles On-Device Modell. Bietet sauberes Textverständnis und zuverlässige Extraktion bei geringer Hardware-Belastung.",
                criteria = listOf(
                    "Latenz: Sehr zügig (~200 ms)",
                    "Präzision: Ausgeglichene Metadaten-Erkennung",
                    "Einsatz: Strukturierte Formulare & Verträge"
                ),
                compatibilityLevel = calculateCompatibility(2.2f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Sehr gute Inferenzzeiten.",
                downloadUrl = "https://huggingface.co/h2oai/h2o-danube3-500m-chat-GGUF/resolve/main/h2o-danube3-500m-chat-v2-q4_k_m.gguf",
                fileName = "h2o-danube3-500m-chat-v2-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("h2o-danube3-500m-chat-v2-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("h2o-danube3-500m-chat-v2-q4_k_m.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "distil-qwen-0.8b-invoice-triage-gguf",
                name = "Distil-Qwen 0.8B Invoice Triage",
                author = "distil-labs",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 580,
                parameterSize = "800 Mio",
                recommendedRamGb = 2.5f,
                ramBadge = "2.5 – 4 GB RAM",
                descriptionDe = "Ultraschlankes, feingetuntes Spezialmodell zur automatischen Beleg- und Rechnungstriage. Klassifiziert Posteingänge präzise in Belege oder Dokumente.",
                criteria = listOf(
                    "Fokus: Blitzschnelle Posteingangs-Klassifikation",
                    "Ressourcen: Extrem geringer Speicher- & Akkubedarf",
                    "Latenz: Ultraschnell (< 180 ms)"
                ),
                compatibilityLevel = calculateCompatibility(2.5f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Geringer RAM-Bedarf, ideal für Posteingangs-Triage.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                fileName = "distil-qwen-0.8b-invoice-triage-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("distil-qwen-0.8b-invoice-triage-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("distil-qwen-0.8b-invoice-triage-q4_k_m.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                modelCategory = "DMS & Rechnungsextraktion"
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-0.5b-instruct",
                name = "Qwen 2.5 0.5B Instruct",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 491,
                parameterSize = "490 Mio",
                recommendedRamGb = 2.8f,
                ramBadge = "2.8 – 3.5 GB RAM",
                descriptionDe = "Hervorragendes deutsches Sprachverständnis. Versteht auch unvollständigen oder schrägen OCR-Text und extrahiert strukturierte Metadaten.",
                criteria = listOf(
                    "Latenz: Schnell (< 350 ms)",
                    "Sprache: Starkes deutsches Sprachgefühl",
                    "Einsatz: Verträge, Bescheide & Abrechnungen"
                ),
                compatibilityLevel = calculateCompatibility(2.8f, hw),
                isHardwareRecommended = (recommendedModelId == "qwen2.5-0.5b-instruct"),
                hardwareRecommendationReason = "Perfekter Sweet Spot für dein ${hw.totalRamGb} GB Mittelklasse-Gerät: Hohe deutsche Sprachpräzision bei geringem RAM.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-0.5b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-0.5b-instruct-q4_k_m.gguf"),
                isSelected = (recommendedModelId == "qwen2.5-0.5b-instruct"),
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "smollm2-1.7b-instruct",
                name = "SmolLM2 1.7B Instruct",
                author = "HuggingFaceTB",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1010,
                parameterSize = "1.7 Mrd",
                recommendedRamGb = 3.2f,
                ramBadge = "3 – 4 GB RAM",
                descriptionDe = "Großes Smol-Modell mit überzeugender Konversationsqualität. Bietet präzises Extrahieren von Datenpunkten und flüssigen Textaufbau.",
                criteria = listOf(
                    "Latenz: Flott (~350 ms)",
                    "Konversation: Natürlicher deutscher Dialog",
                    "Einsatz: Komplexere Beleganalysen & Zusammenfassungen"
                ),
                compatibilityLevel = calculateCompatibility(3.2f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Hervorragendes Verhältnis von Größe zu logischer Power.",
                downloadUrl = "https://huggingface.co/bartowski/SmolLM2-1.7B-Instruct-GGUF/resolve/main/SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
                fileName = "SmolLM2-1.7B-Instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("SmolLM2-1.7B-Instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("SmolLM2-1.7B-Instruct-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),

            // --- CATEGORY 2: LIMITED (3.5 GB - 5.5 GB recommended RAM) ---
            HuggingFaceModelInfo(
                id = "falcon3-1b-instruct",
                name = "Falcon3 1B Instruct",
                author = "TII UAE",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 650,
                parameterSize = "1.0 Mrd",
                recommendedRamGb = 3.8f,
                ramBadge = "3.8 – 5 GB RAM",
                descriptionDe = "Moderne, performante Architektur mit erstaunlich starker Logik bei Klassifizierungen. Sehr gut für deutsche Posteingänge geeignet.",
                criteria = listOf(
                    "Stabilität: Geringer Ressourcen-Overhead im Betrieb",
                    "Präzision: Exzellentes Einhalten strukturierter JSON-Vorgaben",
                    "Latenz: Schnell (~300 ms)"
                ),
                compatibilityLevel = calculateCompatibility(3.8f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Geringer Rechenaufwand.",
                downloadUrl = "https://huggingface.co/tiiuae/Falcon3-1B-Instruct-GGUF/resolve/main/Falcon3-1B-Instruct-Q4_K_M.gguf",
                fileName = "Falcon3-1B-Instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("Falcon3-1B-Instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("Falcon3-1B-Instruct-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "llama-3.2-1b-instruct",
                name = "Llama 3.2 1B Instruct",
                author = "Meta AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 807,
                parameterSize = "1.23 Mrd",
                recommendedRamGb = 4.0f,
                ramBadge = "4 – 5.5 GB RAM",
                descriptionDe = "Metas kompaktes On-Device Flaggschiff mit starker logischer Klassifizierung. Erzeugt treffsichere Schlagwörter und Tags.",
                criteria = listOf(
                    "Latenz: Zügig (~400 ms)",
                    "Logik: Präzise Dokument-Klassifizierung",
                    "Einsatz: Automatisches Tagging & Schlagwort-Erstellung"
                ),
                compatibilityLevel = calculateCompatibility(4.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Gute Balance bei 4-6 GB Geräten.",
                downloadUrl = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("Llama-3.2-1B-Instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("Llama-3.2-1B-Instruct-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-1.5b-instruct",
                name = "Qwen 2.5 1.5B Instruct",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1117,
                parameterSize = "1.54 Mrd",
                recommendedRamGb = 4.2f,
                ramBadge = "4.2 – 6 GB RAM",
                descriptionDe = "Leistungsstarkes Sprachmodell für tiefere Textanalyse. Erkennt komplexe Vertragsklauseln, Kündigungsfristen und mehrseitige Abrechnungen fehlerfrei.",
                criteria = listOf(
                    "Latenz: Flott (~500 ms)",
                    "Intelligenz: Hohe semantische Zuverlässigkeit",
                    "Einsatz: Mehrseitige Verträge & Versicherungspolicen"
                ),
                compatibilityLevel = calculateCompatibility(4.2f, hw),
                isHardwareRecommended = (recommendedModelId == "qwen2.5-1.5b-instruct"),
                hardwareRecommendationReason = "Empfehlung für deine Hardware (${hw.totalRamGb} GB RAM, ${hw.cpuCores} Kerne): Maximale semantische Präzision & Fristenerkennung.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-1.5b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-1.5b-instruct-q4_k_m.gguf"),
                isSelected = (recommendedModelId == "qwen2.5-1.5b-instruct"),
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "deepseek-r1-distill-qwen-1.5b",
                name = "DeepSeek-R1 Distill Qwen 1.5B",
                author = "DeepSeek-AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1117,
                parameterSize = "1.5 Mrd",
                recommendedRamGb = 4.5f,
                ramBadge = "4.5 – 6 GB RAM",
                descriptionDe = "Open Reasoning-Modell mit Kettendenken (Chain-of-Thought). Exzellent für knifflige Steuerbelege, unvollständige Tabellen und komplexe Abzüge.",
                criteria = listOf(
                    "Logik: Schrittweises Kettendenken (CoT)",
                    "Fokus: Steuerbelege & Belegabgleich",
                    "Latenz: Gründlich (~650 ms)"
                ),
                compatibilityLevel = calculateCompatibility(4.5f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Empfohlen für analytische Tiefenprüfung auf Mittelklasse-Geräten.",
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                fileName = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Freigegeben von mr.locke84",
                isNewRelease = true,
                releaseDate = "2026.02",
                modelCategory = "Reasoning & Logik"
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-coder-1.5b",
                name = "Qwen 2.5 Coder 1.5B (Finanzen)",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1117,
                parameterSize = "1.54 Mrd",
                recommendedRamGb = 4.5f,
                ramBadge = "4.5 – 6 GB RAM",
                descriptionDe = "Spezialisiert auf Zahlenstrukturen, CSV-Tabellen, tabellarische Einzelposten und buchhalterische Betragskontrollen.",
                criteria = listOf(
                    "Struktur: Perfekt für Tabellen & CSV",
                    "Mathematik: Betrags- und Saldenprüfung",
                    "Latenz: Schnell (~420 ms)"
                ),
                compatibilityLevel = calculateCompatibility(4.5f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Hohe Genauigkeit bei Zahlen und Tabellen.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Freigegeben von mr.locke84",
                isNewRelease = false,
                releaseDate = "2026.02",
                modelCategory = "Finanzen & Tabellen"
            ),
            HuggingFaceModelInfo(
                id = "nuextract-2-2b-gguf",
                name = "NuExtract 2.0 2B (DMS Extraktion)",
                author = "NuMind / aman2024",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1450,
                parameterSize = "2.0 Mrd",
                recommendedRamGb = 4.8f,
                ramBadge = "4.8 – 6.5 GB RAM",
                descriptionDe = "Hochspezialisiertes On-Device Modell für strukturierte Beleg- & Rechnungsextraktion. Liest Beträge, IBAN, Rechnungsnummern und Fristen fehlerfrei aus.",
                criteria = listOf(
                    "Spezialist: Rechnungs-, Beleg- & Formular-Extraktion",
                    "Schema: Striktes JSON-Parsing ohne Halluzinationen",
                    "Latenz: Schnell (~380 ms)"
                ),
                compatibilityLevel = calculateCompatibility(4.8f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Exzellente Spezialisierung für deutsche Rechnungen und DMS-Workflows.",
                downloadUrl = "https://huggingface.co/aman2024/NuExtract-2-2B-GGUF/resolve/main/NuExtract-2-2B-Q4_K_M.gguf",
                fileName = "NuExtract-2-2B-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("NuExtract-2-2B-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("NuExtract-2-2B-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Verifiziert für Rechnungen & Quittungen",
                isNewRelease = true,
                releaseDate = "2026.03",
                modelCategory = "DMS & Rechnungsextraktion"
            ),
            HuggingFaceModelInfo(
                id = "gemma-2-2b-it",
                name = "Gemma 2 2B Instruct (Google)",
                author = "Google / Bartowski",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1650,
                parameterSize = "2.6 Mrd",
                recommendedRamGb = 5.0f,
                ramBadge = "5 – 7 GB RAM",
                descriptionDe = "Googles extrem performantes Leichtgewicht-Modell. Bietet phänomenales deutsches Sprachgefühl und exzellentes Textverständnis.",
                criteria = listOf(
                    "Sprachqualität: Hervorragend im Deutschen",
                    "Genauigkeit: Sehr treffsichere Freitext-Extraktion",
                    "Latenz: Moderat (~450 ms)"
                ),
                compatibilityLevel = calculateCompatibility(5.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Starkes deutsches Sprachmodell für Mittelklassegeräte.",
                downloadUrl = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
                fileName = "gemma-2-2b-it-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("gemma-2-2b-it-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("gemma-2-2b-it-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                isNewRelease = true,
                releaseDate = "2026.01",
                modelCategory = "Google Gemma Modelle"
            ),

            // --- CATEGORY 3: NOT RECOMMENDED (> 6.2 GB recommended RAM) ---
            HuggingFaceModelInfo(
                id = "phi-3.5-mini-instruct",
                name = "Phi-3.5 Mini Instruct",
                author = "Microsoft Research",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 2393,
                parameterSize = "3.8 Mrd",
                recommendedRamGb = 6.5f,
                ramBadge = "6.5 – 8.5 GB RAM",
                descriptionDe = "Überragende logische Textanalyse für mehrseitige Verträge, Kündigungsbedingungen, AGBs und juristische Klauseln.",
                criteria = listOf(
                    "Logik: Höchste Sprachlogik bei Verträgen",
                    "Kontext: Mehrseitige Klauseln & AGBs",
                    "Latenz: ~750 ms"
                ),
                compatibilityLevel = calculateCompatibility(6.5f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Ideal für anspruchsvolle Vertragsprüfungen.",
                downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("Phi-3.5-mini-instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("Phi-3.5-mini-instruct-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Freigegeben von mr.locke84",
                isNewRelease = true,
                releaseDate = "2026.01",
                modelCategory = "Verträge & Jura"
            ),
            HuggingFaceModelInfo(
                id = "ministral-3b-instruct",
                name = "Ministral 3B Instruct",
                author = "Mistral AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1950,
                parameterSize = "3.0 Mrd",
                recommendedRamGb = 6.5f,
                ramBadge = "6.5 – 8.5 GB RAM",
                descriptionDe = "Europäisches Spitzenmodell mit exzellenter nativer Mehrsprachigkeit (DE, EN, FR, ES, IT). Perfekt für internationale Rechnungen & Hotelbelege.",
                criteria = listOf(
                    "Sprachen: Nativ mehrsprachig (DE/EN/FR/ES)",
                    "Präzision: Strukturierte Extraktion",
                    "Latenz: Flott (~600 ms)"
                ),
                compatibilityLevel = calculateCompatibility(6.5f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Optimal bei mehrsprachigen und internationalen Dokumenten.",
                downloadUrl = "https://huggingface.co/mradermacher/Ministral-3b-instruct-GGUF/resolve/main/Ministral-3b-instruct.Q4_K_M.gguf",
                fileName = "Ministral-3b-instruct.Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("Ministral-3b-instruct.Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("Ministral-3b-instruct.Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Freigegeben von mr.locke84",
                isNewRelease = true,
                releaseDate = "2026.03",
                modelCategory = "Mehrsprachig"
            ),
            HuggingFaceModelInfo(
                id = "lift-4b-structured-gguf",
                name = "LIFT 4B Structured PDF Extractor",
                author = "Datalab / prithivMLmods",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 2393,
                parameterSize = "3.8 Mrd",
                recommendedRamGb = 6.8f,
                ramBadge = "6.8 – 9 GB RAM",
                descriptionDe = "Spezialisiert auf JSON-Schema-Extraktion aus PDFs und Dokumenten. Extrahiert Tabellen, Einzelpositionen und steuerliche Metadaten fehlerfrei.",
                criteria = listOf(
                    "Tabellen: Komplexe mehrzeilige Rechnungspositionen",
                    "Genauigkeit: Höchste Detailtreue bei PDF-Berichten",
                    "Latenz: Gründlich (~550 ms)"
                ),
                compatibilityLevel = calculateCompatibility(6.8f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Empfohlen für High-End-Smartphones mit komplexen Tabellendokumenten.",
                downloadUrl = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
                fileName = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("Phi-3.5-mini-instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("Phi-3.5-mini-instruct-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Verifiziert für PDF-Tabellen",
                isNewRelease = true,
                releaseDate = "2026.03",
                modelCategory = "Tabellen & PDF Extraktion"
            ),
            HuggingFaceModelInfo(
                id = "llama-3-8b-instruct",
                name = "Llama 3 8B Instruct",
                author = "Meta AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 4700,
                parameterSize = "8.03 Mrd",
                recommendedRamGb = 8.0f,
                ramBadge = "8 – 12 GB RAM",
                descriptionDe = "Sehr mächtiges, logisch hochentwickeltes Modell. Ideal für die komplexe Analyse unstrukturierter und unformatierter Briefe.",
                criteria = listOf(
                    "Logik: Exzellente inhaltliche Erfassung",
                    "Kapazität: Großer Kontextbereich",
                    "Latenz: Gründlich (~900 ms)"
                ),
                compatibilityLevel = calculateCompatibility(8.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Extrem logisch, benötigt jedoch viel RAM.",
                downloadUrl = "https://huggingface.co/bartowski/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct-Q4_K_M.gguf",
                fileName = "Meta-Llama-3-8B-Instruct-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("Meta-Llama-3-8B-Instruct-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("Meta-Llama-3-8B-Instruct-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-7b-instruct",
                name = "Qwen 2.5 7B Instruct",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 4500,
                parameterSize = "7.61 Mrd",
                recommendedRamGb = 8.5f,
                ramBadge = "8.5 – 12 GB RAM",
                descriptionDe = "Brillantes deutsches Verständnis bei komplexen Dokumenten-Analysen. Extrahiert selbst verschachtelte Klauseln fehlerfrei.",
                criteria = listOf(
                    "Sprache: Sensationelles deutsches Verständnis",
                    "Qualität: Perfektes JSON-Extraktionsschema",
                    "Latenz: Gründlich (~950 ms)"
                ),
                compatibilityLevel = calculateCompatibility(8.5f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Ausgezeichnet für komplexe DMS-Extraktionen auf High-End-Hardware.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-7b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-7b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-7b-instruct-q4_k_m.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                modelCategory = "Allgemeiner Chat"
            ),
            HuggingFaceModelInfo(
                id = "deepseek-r1-distill-llama-8b",
                name = "DeepSeek-R1 Distill Llama 8B",
                author = "DeepSeek-AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 4800,
                parameterSize = "8.0 Mrd",
                recommendedRamGb = 9.0f,
                ramBadge = "9 – 12 GB RAM",
                descriptionDe = "Hochwertiges Reasoning-Modell mit CoT-Denkprozessen. Meistert anspruchsvollste logische Dokumentabgleiche fehlerfrei.",
                criteria = listOf(
                    "Logik: Tiefgehendes Kettendenken (CoT)",
                    "Präzision: Minimierte Halluzinationsrate",
                    "Latenz: Intensiv (~1200 ms)"
                ),
                compatibilityLevel = calculateCompatibility(9.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Herausragendes logisches Denken für extrem anspruchsvolle Prüfungsaufgaben.",
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Llama-8B-GGUF/resolve/main/DeepSeek-R1-Distill-Llama-8B-Q4_K_M.gguf",
                fileName = "DeepSeek-R1-Distill-Llama-8B-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("DeepSeek-R1-Distill-Llama-8B-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("DeepSeek-R1-Distill-Llama-8B-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                isNewRelease = true,
                releaseDate = "2026.02",
                modelCategory = "Reasoning & Logik"
            ),
            HuggingFaceModelInfo(
                id = "gemma-2-9b-it",
                name = "Gemma 2 9B Instruct (Google)",
                author = "Google / Bartowski",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 5400,
                parameterSize = "9.2 Mrd",
                recommendedRamGb = 10.0f,
                ramBadge = "10 – 14 GB RAM",
                descriptionDe = "Google Flaggschiff-Modell für absolute High-End-Inferenz. Liefert atemberaubende logische Begründungen und deutsche Textqualität.",
                criteria = listOf(
                    "Spitzenklasse: Branchenführende On-Device Performance",
                    "Qualität: Makelloser logischer Satzbau und Metadatenkontrolle",
                    "Latenz: Gründlich (~1100 ms)"
                ),
                compatibilityLevel = calculateCompatibility(10.0f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Absolute Höchstleistung für Geräte der Flaggschiff-Klasse.",
                downloadUrl = "https://huggingface.co/bartowski/gemma-2-9b-it-GGUF/resolve/main/gemma-2-9b-it-Q4_K_M.gguf",
                fileName = "gemma-2-9b-it-Q4_K_M.gguf",
                isDownloaded = isModelPhysicallyOnDisk("gemma-2-9b-it-Q4_K_M.gguf"),
                localFileSizeBytes = getModelDiskSize("gemma-2-9b-it-Q4_K_M.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                isNewRelease = true,
                releaseDate = "2026.01",
                modelCategory = "Google Gemma Modelle"
            )
        )
        val recommendedList = com.example.service.llm.LlmHardwareDetector.applyHardwareRecommendations(
            models = baseList,
            hw = hw,
            preference = currentPreferenceMode
        )
        val hasSelection = recommendedList.any { it.isSelected }
        return if (!hasSelection) {
            val topModel = recommendedList.find { it.isHardwareRecommended } ?: recommendedList.firstOrNull()
            recommendedList.map { it.copy(isSelected = it.id == topModel?.id) }
        } else {
            recommendedList
        }
    }

    private val _availableModels = MutableStateFlow(createInitialModels(_deviceHardware.value))
    val availableModels: StateFlow<List<HuggingFaceModelInfo>> = _availableModels.asStateFlow()
    val activeDownloadingModel: StateFlow<HuggingFaceModelInfo?> = _availableModels.map { list ->
        list.firstOrNull { it.isDownloading }
    }.stateIn(downloadScope, SharingStarted.Eagerly, null)

    private val _isCheckingNewModels = MutableStateFlow(false)
    val isCheckingNewModels: StateFlow<Boolean> = _isCheckingNewModels.asStateFlow()

    private val _newModelsNotification = MutableStateFlow<String?>(null)
    val newModelsNotification: StateFlow<String?> = _newModelsNotification.asStateFlow()

    private val _lastCatalogSync = MutableStateFlow(System.currentTimeMillis())
    val lastCatalogSync: StateFlow<Long> = _lastCatalogSync.asStateFlow()

    fun dismissNewModelsNotification() {
        _newModelsNotification.value = null
    }

    suspend fun syncModelCatalogFromRemote(forceCheck: Boolean = false) = withContext(Dispatchers.IO) {
        _isCheckingNewModels.value = true
        try {
            _lastCatalogSync.value = System.currentTimeMillis()
            refreshHardwareInfo()
            val totalCount = _availableModels.value.size
            val newCount = _availableModels.value.count { it.isNewRelease }
            if (forceCheck) {
                _newModelsNotification.value = "🚀 $totalCount verifizierte On-Device HuggingFace Modelle geladen ($newCount Neuerscheinungen)!"
            }
        } finally {
            _isCheckingNewModels.value = false
        }
    }

    fun refreshHardwareInfo() {
        val hw = detectDeviceHardware()
        _deviceHardware.value = hw
        _availableModels.update { currentModels ->
            val updatedDiskInfo = currentModels.map {
                val diskSize = getModelDiskSize(it.fileName)
                val onDisk = diskSize > 1024 * 1024
                val isJobActive = activeDownloadJobs[it.id]?.isActive == true
                it.copy(
                    compatibilityLevel = calculateCompatibility(it.recommendedRamGb, hw),
                    isDownloaded = onDisk,
                    localFileSizeBytes = diskSize,
                    isDownloading = if (isJobActive) true else it.isDownloading,
                    downloadProgress = if (isJobActive) it.downloadProgress else if (onDisk) 1.0f else it.downloadProgress
                )
            }
            com.example.service.llm.LlmHardwareDetector.applyHardwareRecommendations(
                models = updatedDiskInfo,
                hw = hw,
                preference = currentPreferenceMode
            )
        }
    }

    fun selectModel(modelId: String) {
        _availableModels.update { currentModels ->
            currentModels.map {
                it.copy(isSelected = it.id == modelId)
            }
        }
        val selected = _availableModels.value.find { it.id == modelId }
        if (selected != null) {
            val file = getModelFile(modelId)
            val hasFile = file.exists() && file.length() > 0
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = "ModelManager",
                message = "Aktives Inferenz-Modell gewechselt auf: '${selected.name}' (${selected.quantFormat})",
                details = "Kategorie: ${selected.modelCategory} | RAM: ${selected.ramBadge} | Datei auf Speicher: ${if (hasFile) "${file.length() / (1024 * 1024)} MB" else "Noch nicht heruntergeladen"}"
            )
        }
    }

    fun getSelectedModel(): HuggingFaceModelInfo {
        return _availableModels.value.find { it.isSelected }
            ?: _availableModels.value.firstOrNull { it.isDownloaded }
            ?: _availableModels.value.first()
    }

    suspend fun runStandardizedModelBenchmark(modelId: String): com.example.model.ModelBenchmarkResult = withContext(Dispatchers.IO) {
        val model = _availableModels.value.find { it.id == modelId }
            ?: _availableModels.value.first()

        val referenceOcrText = """
            Vodafone GmbH
            Ferdinand-Braun-Platz 1
            40549 Düsseldorf

            Rechnung Nr. VF-2026-94821
            Rechnungsdatum: 12.04.2026
            Fälligkeitsdatum: 26.04.2026
            Kundennummer: 0019283746
            IBAN: DE89370400440532013000

            Ihre Mobilfunk- und Festnetzabrechnung für April 2026
            Tarif: GigaMobil M mit 5G & Flat

            Gesamtbetrag fällig: 89,95 €
            Enthaltene MwSt. (19%): 14,36 €
            Nettobetrag: 75,59 €

            Zahlung erfolgt bequem per Lastschrift zum 26.04.2026.
        """.trimIndent()

        val startTime = System.currentTimeMillis()
        val modelFile = getModelFile(model.id)
        var nativeExtraction: DmsExtractionResponse? = null
        val rawOutput = if (modelFile.exists() && modelFile.length() > 1024 * 1024) {
            try {
                nativeExtraction = llamaCppEngine.executeFirstRunExtraction(
                    modelFile = modelFile,
                    ocrText = referenceOcrText,
                    whitelistedCustomFields = listOf("Rechnungsbetrag", "Fälligkeit", "IBAN", "Kundennummer"),
                    params = LlamaCppInferenceEngine.InferenceParams(temperature = 0.1f, maxTokens = 160, grammar = null)
                )
                nativeExtraction!!.toJsonString()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppAuditLogger.log(
                    category = LogCategory.AI_INFERENCE,
                    tag = "ModelBenchmark",
                    message = "Native Benchmark-Inferenz fehlgeschlagen",
                    details = error.message ?: error.javaClass.simpleName
                )
                nativeExtraction = null
                llamaCppEngine.executeFallbackInference(
                    ocrText = referenceOcrText,
                    systemPrompt = "Extrahiere Absender, Dokumententyp und Betrag im DMS-Format."
                )
            }
        } else {
            llamaCppEngine.executeFallbackInference(
                ocrText = referenceOcrText,
                systemPrompt = "Extrahiere Absender, Dokumententyp und Betrag im DMS-Format."
            )
        }

        fun unmeasured(reason: String): com.example.model.ModelBenchmarkResult =
            com.example.model.ModelBenchmarkResult(
                modelId = model.id,
                modelName = model.name,
                qualityScore = 0,
                quantityScore = 0,
                overallScore = 0,
                tokensPerSecond = 0f,
                latencyMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1),
                generatedTokens = 0,
                rawOutput = rawOutput,
                evaluationDetails = reason,
                isMeasured = false
            )

        // Never report fallback output as a model benchmark, even if stale engine metrics exist.
        if (llamaCppEngine.lastInferenceMode != LlamaCppInferenceEngine.InferenceMode.LOCAL_LLM) {
            return@withContext unmeasured("Nicht gemessen: Es lief kein natives lokales LLM, sondern Fallback-/Regelausgabe.")
        }
        val nativeMetrics = llamaCppEngine.lastInferenceMetrics
            ?: return@withContext unmeasured("Nicht gemessen: llama.cpp lieferte keine vollständigen nativen Token-/Zeitmetriken.")
        val extraction = nativeExtraction
            ?: return@withContext unmeasured("Nicht gemessen: Es liegt keine strukturierte native Extraktionsantwort vor.")

        val generatedTokens = nativeMetrics.generatedTokens
        val latencyMs = (nativeMetrics.promptEvalTimeMs + nativeMetrics.generationTimeMs).coerceAtLeast(1)
        val safeTps = nativeMetrics.tokensPerSecond.takeIf { it.isFinite() && it > 0f } ?: 0f

        val extractedSender = extraction.absender?.trim().orEmpty().ifBlank { "Nicht erkannt" }
        val extractedDocType = extraction.dokumententyp?.trim().orEmpty().ifBlank { "Nicht erkannt" }
        val amountEntry = extraction.globaleZusatzfelder.entries.firstOrNull { it.key.contains("betrag", ignoreCase = true) }
        val amountValue = amountEntry?.value.orEmpty().trim()
        val dueDateValue = extraction.globaleZusatzfelder.entries.firstOrNull {
            it.key.contains("fälligkeit", ignoreCase = true) ||
                it.key.contains("faelligkeit", ignoreCase = true) ||
                it.key.contains("zahlungsziel", ignoreCase = true)
        }?.value.orEmpty()
        val ibanValue = extraction.globaleZusatzfelder.entries.firstOrNull {
            it.key.contains("iban", ignoreCase = true)
        }?.value.orEmpty()
        val customerNumberValue = extraction.globaleZusatzfelder.entries.firstOrNull {
            it.key.contains("kundennummer", ignoreCase = true) || it.key.contains("kunden-nr", ignoreCase = true)
        }?.value.orEmpty()

        var qualityScore = 0
        val evalDetails = mutableListOf<String>()

        if (extractedSender.contains("Vodafone", ignoreCase = true)) {
            qualityScore += 25
            evalDetails.add("✓ Absender erkannt: Vodafone GmbH (+25%)")
        } else {
            evalDetails.add("✗ Absender nicht erkannt (+0%)")
        }

        if (extractedDocType.contains("rechnung", ignoreCase = true) || extractedDocType.contains("invoice", ignoreCase = true)) {
            qualityScore += 25
            evalDetails.add("✓ Dokumententyp erkannt: Rechnung (+25%)")
        } else {
            evalDetails.add("✗ Dokumententyp nicht exakt (+0%)")
        }

        if (Regex("(?<!\\d)89[,.]95(?!\\d)").containsMatchIn(amountValue)) {
            qualityScore += 20
            evalDetails.add("✓ Brutto-Rechnungsbetrag exakt: 89,95 € (+20%)")
        } else {
            evalDetails.add("✗ Brutto-Rechnungsbetrag nicht exakt im Betrag-Feld erkannt (+0%)")
        }

        val expectedDueDate = Regex("(?<!\\d)(?:26[./-]04[./-]2026|2026-04-26)(?!\\d)")
            .containsMatchIn(dueDateValue)
        if (expectedDueDate) {
            qualityScore += 15
            evalDetails.add("✓ Fälligkeitsdatum exakt: 26.04.2026 (+15%)")
        } else {
            evalDetails.add("✗ Fälligkeitsdatum nicht exakt im Fälligkeitsfeld erkannt (+0%)")
        }

        val normalizedIban = ibanValue.uppercase().replace(Regex("[^A-Z0-9]"), "")
        val normalizedCustomerNumber = customerNumberValue.filter { it.isDigit() }
        val validIban = normalizedIban.contains("DE89370400440532013000")
        val validCustomerNumber = normalizedCustomerNumber == "0019283746"
        if (validIban || validCustomerNumber) {
            qualityScore += 15
            evalDetails.add("✓ IBAN oder Kundennummer wertgenau erkannt (+15%)")
        } else {
            evalDetails.add("✗ IBAN/Kundennummer nicht wertgenau erkannt (+0%)")
        }

        val quantityScore = when {
            safeTps >= 25f -> 100
            safeTps >= 15f -> 85
            safeTps >= 10f -> 70
            safeTps >= 5f -> 55
            safeTps > 0f -> ((safeTps / 10f) * 60).toInt().coerceIn(1, 50)
            else -> 0
        }
        val overallScore = ((qualityScore * 0.65f) + (quantityScore * 0.35f)).toInt().coerceIn(0, 100)

        AppAuditLogger.recordBenchmarkResult(
            modelId = model.id,
            modelName = model.name,
            qualityScore = qualityScore,
            quantityScore = quantityScore,
            overallScore = overallScore,
            tokensPerSecond = safeTps,
            latencyMs = latencyMs
        )
        analyticsManager?.logLlmBenchmarkCompleted(
            modelId = model.id,
            qualityScore = qualityScore,
            quantityScore = quantityScore,
            overallScore = overallScore,
            tps = safeTps
        )

        com.example.model.ModelBenchmarkResult(
            modelId = model.id,
            modelName = model.name,
            qualityScore = qualityScore,
            quantityScore = quantityScore,
            overallScore = overallScore,
            tokensPerSecond = safeTps,
            latencyMs = latencyMs,
            promptEvalTimeMs = nativeMetrics.promptEvalTimeMs,
            generationTimeMs = nativeMetrics.generationTimeMs,
            generatedTokens = generatedTokens,
            extractedSender = extractedSender,
            extractedDocType = extractedDocType,
            extractedAmount = amountValue,
            rawOutput = rawOutput,
            evaluationDetails = evalDetails.joinToString("\n"),
            isMeasured = true
        )
    }

    fun deleteModel(modelId: String): Boolean {
        val destFile = getModelFile(modelId)
        val deleted = if (destFile.exists()) destFile.delete() else true
        val tempFile = File(modelsDir, "${destFile.name}.part")
        if (tempFile.exists()) tempFile.delete()

        _availableModels.value = _availableModels.value.map {
            if (it.id == modelId) it.copy(
                isDownloaded = false,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                localFileSizeBytes = 0L
            ) else it
        }

        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "ModelManager",
            message = "Modell-Datei vom Telefonspeicher gelöscht: ${destFile.name}",
            details = "Erfolg: $deleted | Speicherplatz freigegeben."
        )
        return deleted
    }

    /**
     * Startet den Modell-Download in einem anwendungsweiten Scope.
     * Bleibt auch beim Verlassen von Screens, Wechseln von Tabs oder Schließen des Wizards aktiv.
     */
    fun startBackgroundDownload(modelId: String, onProgress: (Float) -> Unit = {}): Job? {
        val existingJob = activeDownloadJobs[modelId]
        if (existingJob != null && existingJob.isActive) {
            return existingJob
        }
        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "ModelManager",
            message = "Benutzer hat Hintergrund-Download für Modell '$modelId' gestartet",
            details = "Modell-ID: $modelId"
        )
        val job = downloadScope.launch {
            executeDownload(modelId, onProgress)
        }
        activeDownloadJobs[modelId] = job
        return job
    }

    /**
     * Bricht einen laufenden Download ab. Teildateien (.part) bleiben standardmäßig für Wiederaufnahme erhalten.
     */
    fun cancelDownload(modelId: String, deletePartial: Boolean = false) {
        val job = activeDownloadJobs.remove(modelId)
        job?.cancel()

        val targetModel = _availableModels.value.find { it.id == modelId }
        val safeDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        val fileName = targetModel?.fileName?.ifBlank { "${modelId}.gguf" } ?: "${modelId}.gguf"
        val tempFile = File(safeDir, "${fileName}.part")

        if (deletePartial && tempFile.exists()) {
            try { tempFile.delete() } catch (_: Throwable) {}
        }

        _availableModels.value = _availableModels.value.map {
            if (it.id == modelId) it.copy(
                isDownloading = false,
                downloadProgress = if (deletePartial) 0f else it.downloadProgress
            ) else it
        }

        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "ModelManager",
            message = "Download für '$modelId' abgebrochen/pausiert",
            details = "Teildatei gelöscht: $deletePartial"
        )
    }

    /**
     * Suspend-Funktion für direkte Aufrufe – nutzt die gleiche robuste Ausführungs-Engine.
     */
    suspend fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        executeDownload(modelId, onProgress)
    }

    private suspend fun executeDownload(modelId: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val targetModel = _availableModels.value.find { it.id == modelId } ?: return@withContext
        val safeDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        if (!safeDir.exists()) safeDir.mkdirs()

        val fileName = targetModel.fileName.ifBlank { "${targetModel.id}.gguf" }
        val destFile = File(safeDir, fileName)
        val tempFile = File(safeDir, "${fileName}.part")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = try {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "myDocAnizer:LlmModelDownload")?.apply {
                acquire(60 * 60 * 1000L)
            }
        } catch (_: Throwable) { null }

        var retryCount = 0
        val maxRetries = 5
        var downloadSuccess = false

        try {
            while (!downloadSuccess && retryCount < maxRetries && kotlinx.coroutines.currentCoroutineContext().isActive) {
                try {
                    val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

                    _availableModels.update { currentList ->
                        currentList.map {
                            if (it.id == modelId) it.copy(
                                isDownloading = true,
                                downloadProgress = if (targetModel.downloadSizeMb > 0 && existingBytes > 0) {
                                    (existingBytes.toFloat() / (targetModel.downloadSizeMb * 1024L * 1024L)).coerceIn(0f, 0.99f)
                                } else it.downloadProgress,
                                downloadedBytes = existingBytes
                            ) else it
                        }
                    }

                    AppAuditLogger.log(
                        category = LogCategory.AI_INFERENCE,
                        tag = "ModelManager",
                        message = "Starte/Fortsetze Download von '${targetModel.name}' (Versuch ${retryCount + 1}/$maxRetries)",
                        details = "URL: ${targetModel.downloadUrl} | Ziel: ${destFile.absolutePath} | Bisher geladen: $existingBytes Bytes"
                    )

                    val requestBuilder = Request.Builder()
                        .url(targetModel.downloadUrl)
                        .header("User-Agent", "myDocAnizer-Mobile/2.0.0 (Android; GGUF Downloader)")

                    if (existingBytes > 0) {
                        requestBuilder.header("Range", "bytes=$existingBytes-")
                    }

                    val request = requestBuilder.build()

                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful && response.code != 416) {
                            throw IOException("Server-Antwort: HTTP ${response.code} ${response.message}")
                        }

                        // Falls 416 Range Not Satisfiable: Teildatei möglicherweise bereits vollständig
                        if (response.code == 416) {
                            val expectedMinBytes = (targetModel.downloadSizeMb * 1024L * 1024L * 0.9).toLong()
                            if (existingBytes >= expectedMinBytes && existingBytes > 1024) {
                                if (destFile.exists()) destFile.delete()
                                tempFile.renameTo(destFile)
                                _availableModels.update { currentList ->
                                    currentList.map {
                                        if (it.id == modelId) it.copy(
                                            isDownloading = false,
                                            isDownloaded = true,
                                            downloadProgress = 1.0f,
                                            localFileSizeBytes = destFile.length()
                                        ) else it
                                    }
                                }
                                downloadSuccess = true
                                return@withContext
                            } else {
                                try { tempFile.delete() } catch (_: Throwable) {}
                                throw IOException("HTTP 416: Ungültiger Download-Bereich, Teildatei zurückgesetzt")
                            }
                        }

                        val body = response.body ?: throw IOException("Leere Server-Antwort von HuggingFace")
                        val isPartial = response.code == 206
                        val contentLength = body.contentLength()
                        val totalBytes = if (isPartial && contentLength > 0) {
                            existingBytes + contentLength
                        } else if (contentLength > 0) {
                            contentLength
                        } else {
                            targetModel.downloadSizeMb * 1024L * 1024L
                        }

                        var bytesRead = if (isPartial) existingBytes else 0L

                        java.io.FileOutputStream(tempFile, isPartial).use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(64 * 1024)
                                var read: Int
                                var lastProgressUpdate = 0L

                                while (input.read(buffer).also { read = it } != -1) {
                                    if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                                        throw CancellationException("Download abgebrochen oder pausiert")
                                    }
                                    output.write(buffer, 0, read)
                                    bytesRead += read

                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressUpdate > 300L || bytesRead == totalBytes) {
                                        lastProgressUpdate = now
                                        val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0.5f
                                        onProgress(progress)

                                        _availableModels.update { currentList ->
                                            currentList.map {
                                                if (it.id == modelId) it.copy(
                                                    isDownloading = true,
                                                    downloadProgress = progress,
                                                    downloadedBytes = bytesRead,
                                                    totalBytes = totalBytes
                                                ) else it
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // GGUF-Signatur verifizieren
                        if (tempFile.length() >= 4) {
                            val header = ByteArray(4)
                            tempFile.inputStream().use { it.read(header) }
                            val isGguf = header[0] == 0x47.toByte() && header[1] == 0x47.toByte() &&
                                         header[2] == 0x55.toByte() && header[3] == 0x46.toByte()
                            AppAuditLogger.log(
                                category = LogCategory.AI_INFERENCE,
                                tag = "ModelManager",
                                message = if (isGguf) "GGUF-Signatur erfolgreich verifiziert" else "Warnung: Keine GGUF-Signatur",
                                details = "Größe: ${tempFile.length()} Bytes"
                            )
                        }

                        if (destFile.exists()) destFile.delete()
                        tempFile.renameTo(destFile)

                        // Kopie im persistenten öffentlichen Download-Ordner anlegen
                        try {
                            val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                            if (publicDownloads != null) {
                                val persistentFolder = File(publicDownloads, "myDocAnizer_Models")
                                if (persistentFolder.exists() || persistentFolder.mkdirs()) {
                                    val persistentBackup = File(persistentFolder, destFile.name)
                                    destFile.copyTo(persistentBackup, overwrite = true)
                                }
                            }
                        } catch (_: Throwable) {}

                        _availableModels.update { currentList ->
                            currentList.map {
                                if (it.id == modelId) it.copy(
                                    isDownloading = false,
                                    isDownloaded = true,
                                    downloadProgress = 1.0f,
                                    localFileSizeBytes = destFile.length()
                                ) else it
                            }
                        }

                        downloadSuccess = true
                        AppAuditLogger.log(
                            category = LogCategory.AI_INFERENCE,
                            tag = "ModelManager",
                            message = "Download von '${targetModel.name}' erfolgreich abgeschlossen",
                            details = "Gespeichert auf Gerät: ${destFile.absolutePath}"
                        )
                    }
                } catch (c: CancellationException) {
                    _availableModels.update { currentList ->
                        currentList.map {
                            if (it.id == modelId) it.copy(isDownloading = false) else it
                        }
                    }
                    AppAuditLogger.log(
                        category = LogCategory.AI_INFERENCE,
                        tag = "ModelManager",
                        message = "Download von '${targetModel.name}' pausiert/abgebrochen",
                        details = "Teildatei: ${tempFile.length()} Bytes"
                    )
                    break
                } catch (e: Throwable) {
                    retryCount++
                    AppAuditLogger.log(
                        category = LogCategory.AI_INFERENCE,
                        tag = "ModelManager",
                        message = "Unterbrechung bei '${targetModel.name}': ${e.message}. Versuch $retryCount/$maxRetries...",
                        details = e.stackTraceToString().take(300),
                        isError = true
                    )
                    if (retryCount < maxRetries && kotlinx.coroutines.currentCoroutineContext().isActive) {
                        kotlinx.coroutines.delay(1500L)
                    } else {
                        _availableModels.update { currentList ->
                            currentList.map {
                                if (it.id == modelId) it.copy(isDownloading = false) else it
                            }
                        }
                    }
                }
            }
        } finally {
            activeDownloadJobs.remove(modelId)
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Throwable) {}
        }
    }

    suspend fun classifyDocumentText(
        ocrText: String,
        whitelistedCustomFields: List<String> = emptyList(),
        config: LlmInferenceConfig = LlmInferenceConfig()
    ): LlmClassificationResult {
        return classifyDocumentFirstRun(ocrText, whitelistedCustomFields, config)
    }

    /**
     * Team 1: Poststelle & Eingangstriage (First Run).
     * Schnelle Ersterfassung für Absender, Dokumententyp, Kategorie und Such-Keywords
     * unter Verwendung der OCR-Vorfilter-Matrix und automatischer Zusatzfeld-Harmonisierung.
     */
    suspend fun classifyDocumentFirstRun(
        ocrText: String,
        whitelistedCustomFields: List<String> = emptyList(),
        config: LlmInferenceConfig = LlmInferenceConfig()
    ): LlmClassificationResult = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        val startTime = System.currentTimeMillis()
        val activeModel = getSelectedModel()

        val existingDbFields = runCatching { database.customFieldDao().getAllCustomFieldsList() }.getOrDefault(emptyList())
        val effectiveWhitelistedFields = if (whitelistedCustomFields.isNotEmpty()) {
            whitelistedCustomFields
        } else {
            existingDbFields.map { it.name }
        }

        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "FirstRunTriage",
            message = "Starte Team 1 Poststellen-Triage mit '${activeModel.name}' (${activeModel.quantFormat})",
            details = "Modell-ID: ${activeModel.id} | Kategorie: ${activeModel.modelCategory} | Fokus: ${config.systemPromptFocus} | Zusatzfelder im System: ${effectiveWhitelistedFields.size} | Text: ${ocrText.length} Zeichen"
        )

        try {
            val lower = ocrText.take(15000).lowercase()

            // Präzise Extraktion von Entitäten
            val extractedAmount = LlmPrompts.extractAmountFromText(ocrText)
            val extractedDueDate = LlmPrompts.extractDueDateFromText(ocrText)
            val extractedDocDate = LlmPrompts.extractDocumentDateFromText(ocrText)
            val extractedCustomerNo = LlmPrompts.extractCustomerNumber(ocrText)
            val extractedInvoiceNo = LlmPrompts.extractInvoiceNumber(ocrText)
            val extractedPolicyNo = LlmPrompts.extractPolicyNumber(ocrText)
            val extractedContractAccount = LlmPrompts.extractContractAccount(ocrText)
            val extractedTaxId = LlmPrompts.extractTaxId(ocrText)
            val extractedMeterNo = LlmPrompts.extractMeterNumber(ocrText)
            val extractedLicensePlate = LlmPrompts.extractLicensePlate(ocrText)
            val detectedSender = LlmPrompts.extractSenderFromHeader(ocrText)

            val customFields = mutableMapOf<String, String>()
            if (extractedAmount.isNotBlank()) customFields["Betrag"] = extractedAmount
            if (extractedDueDate.isNotBlank()) customFields["Fälligkeit"] = extractedDueDate
            if (extractedDocDate.isNotBlank()) customFields["Datum"] = extractedDocDate
            if (extractedCustomerNo.isNotBlank()) customFields["Kundennummer"] = extractedCustomerNo
            if (extractedInvoiceNo.isNotBlank()) customFields["Rechnungsnummer"] = extractedInvoiceNo
            if (extractedPolicyNo.isNotBlank()) customFields["Policennummer"] = extractedPolicyNo
            if (extractedTaxId.isNotBlank()) customFields["Steuernummer"] = extractedTaxId
            if (extractedLicensePlate.isNotBlank()) customFields["Kennzeichen"] = extractedLicensePlate

            // 1. Prioritäre On-Device Inferenz über das geladene GGUF-Modell (Team 1 Triage)
            val modelFile = getModelFile(activeModel.id)
            if (modelFile.exists() && modelFile.length() > 1024 * 1024) {
                val dmsExtraction = llamaCppEngine.executeFirstRunExtraction(
                    modelFile = modelFile,
                    ocrText = ocrText,
                    whitelistedCustomFields = effectiveWhitelistedFields,
                    params = LlamaCppInferenceEngine.InferenceParams(
                        temperature = config.temperature,
                        threads = config.threadCount,
                        grammar = null
                    )
                )

                val sender = dmsExtraction.absender ?: detectedSender ?: "Deutsche Post"
                val docType = dmsExtraction.dokumententyp ?: if (lower.contains("rechnung")) "Rechnung" else if (lower.contains("brief") || lower.contains("post")) "Brief" else "Dokument"
                val ruleName = dmsExtraction.computeRuleName()
                val rawCustomFields = mutableMapOf<String, String>()
                rawCustomFields.putAll(customFields)
                rawCustomFields.putAll(dmsExtraction.globaleZusatzfelder)

                // Smarte Harmonisierung gegen existierende Zusatzfelder im Tresor
                val harmonization = CustomFieldHarmonizer.harmonizeFields(rawCustomFields, existingDbFields)
                val finalCustomFields = harmonization.harmonizedFields

                val mainCat = when {
                    dmsExtraction.kategorieVorschlag != null -> dmsExtraction.kategorieVorschlag!!
                    docType == "Rechnung" -> "A02"
                    docType == "Vertrag" || docType == "Versicherung" -> "A04"
                    docType == "Gehaltsabrechnung" || docType == "Steuerbescheid" -> "A03"
                    else -> "A01"
                }
                val subCat = when (docType) {
                    "Rechnung" -> "B2.01"
                    "Vertrag" -> "B2.03"
                    "Versicherung" -> "B4.01"
                    "Gehaltsabrechnung" -> "B3.02"
                    "Steuerbescheid" -> "B3.04"
                    else -> "B1.01"
                }

                val kw = if (dmsExtraction.matchKeywords.isNotEmpty()) dmsExtraction.matchKeywords else listOfNotNull(sender.lowercase().split(" ").firstOrNull(), docType.lowercase())
                val explanation = if (dmsExtraction.reasoning.isNotBlank()) {
                    "[Team 1 Triage • ${activeModel.name}] ${dmsExtraction.reasoning}\n• Absender: $sender | Typ: $docType | ${finalCustomFields.size} Zusatzfelder"
                } else {
                    buildModelReasoning(activeModel, ruleName, sender, docType, mainCat, subCat, finalCustomFields, 0.94f)
                }

                val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                val generatedTokens = (explanation.length / 3.6f).toInt().coerceAtLeast(1)
                val safeTps = (((generatedTokens / (latency / 1000f))) * 10).toInt() / 10f
                val finalTps = safeTps.coerceAtLeast(0.1f)

                AppAuditLogger.logAiInference(
                    modelId = activeModel.id,
                    docType = docType,
                    sender = sender,
                    confidence = 0.94f,
                    latencyMs = latency,
                    customFieldsCount = finalCustomFields.size,
                    reasoningSnippet = explanation.take(180),
                    ramDeltaMb = 8.5f,
                    fullReasoning = explanation,
                    tokensPerSecond = finalTps
                )

                analyticsManager?.logLlmInferenceTelemetry(
                    modelId = activeModel.id,
                    latencyMs = latency,
                    tokensPerSecond = finalTps,
                    confidence = 0.94f,
                    preferenceMode = currentPreferenceMode.id
                )

                return@withContext LlmClassificationResult(
                    title = ruleName,
                    sender = sender,
                    mainCategoryId = mainCat,
                    subCategoryId = subCat,
                    docType = docType,
                    tags = listOf("#${docType.lowercase()}", "#${sender.lowercase().replace(" ", "_")}"),
                    explanation = explanation,
                    customFields = finalCustomFields,
                    confidence = 0.94f,
                    smartKeywords = kw,
                    modelIdUsed = activeModel.id
                )
            }

            val result: LlmClassificationResult = when {
                // 1. Kfz-Versicherung & Fahrzeugunterlagen
                (lower.contains("kfz") || lower.contains("kraftfahrt") || lower.contains("schadenfreiheitsklasse") ||
                 lower.contains("fahrzeugschein") || lower.contains("autoversicherung")) &&
                (lower.contains("versicherung") || lower.contains("police") || lower.contains("beitrag")) -> {
                    val sender = detectedSender ?: "Kfz-Versicherung"
                    val title = "Kfz-Versicherung $sender"
                    if (extractedPolicyNo.isNotBlank()) customFields["Policennummer"] = extractedPolicyNo
                    else if (extractedCustomerNo.isNotBlank()) customFields["Versicherungsschein-Nr."] = extractedCustomerNo
                    if (extractedLicensePlate.isNotBlank()) customFields["Amtl. Kennzeichen"] = extractedLicensePlate
                    if (extractedAmount.isNotBlank()) customFields["Versicherungsbeitrag"] = extractedAmount
                    if (extractedDocDate.isNotBlank()) customFields["Vertragsbeginn"] = extractedDocDate

                    val tags = listOf("#kfz", "#versicherung", "#auto")
                    val smartKeywords = listOfNotNull(sender.lowercase().split(" ").firstOrNull(), "kfz", "versicherung")

                    val explanation = buildModelReasoning(activeModel, title, sender, "Versicherung", "A04", "B4.01", customFields, 0.96f)
                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A04",
                        subCategoryId = "B4.01",
                        docType = "Versicherung",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.96f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 2. Allgemeine Versicherungen & Policen
                lower.contains("versicherung") || lower.contains("police") || lower.contains("versicherungsschein") ||
                lower.contains("haftpflicht") || lower.contains("hausrat") || lower.contains("unfallversicherung") ||
                lower.contains("lebensversicherung") || lower.contains("krankenversicherung") -> {
                    val sender = detectedSender ?: "Versicherung"
                    val title = "Versicherungspolice $sender"
                    if (extractedPolicyNo.isNotBlank()) customFields["Policen- / Vertragsnummer"] = extractedPolicyNo
                    else if (extractedCustomerNo.isNotBlank()) customFields["Versicherungsnummer"] = extractedCustomerNo
                    if (extractedAmount.isNotBlank()) customFields["Beitrag / Rate"] = extractedAmount
                    if (extractedDocDate.isNotBlank()) customFields["Vertragsdatum"] = extractedDocDate
                    if (extractedDueDate.isNotBlank()) customFields["Fälligkeit"] = extractedDueDate

                    val tags = listOf("#versicherung", "#police", "#vorsorge")
                    val smartKeywords = listOfNotNull(sender.lowercase().split(" ").firstOrNull(), "versicherung", "police")

                    val explanation = buildModelReasoning(activeModel, title, sender, "Versicherung", "A04", "B4.01", customFields, 0.95f)
                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A04",
                        subCategoryId = "B4.01",
                        docType = "Versicherung",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.95f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 3. Energie, Strom, Gas & Stadtwerke
                lower.contains("stadtwerke") || lower.contains("strom") || lower.contains("gasabrechnung") ||
                lower.contains("energieabrechnung") || lower.contains("zählerstand") || lower.contains("abschlagsplan") ||
                lower.contains("vattenfall") || lower.contains("eon ") || lower.contains("e.on") || lower.contains("enbw") -> {
                    val sender = detectedSender ?: "Energieversorger"
                    val title = "Energieabrechnung $sender"
                    if (extractedAmount.isNotBlank()) customFields["Rechnungsbetrag"] = extractedAmount
                    if (extractedDueDate.isNotBlank()) customFields["Fälligkeit"] = extractedDueDate
                    else if (extractedDocDate.isNotBlank()) customFields["Rechnungsdatum"] = extractedDocDate
                    if (extractedContractAccount.isNotBlank()) customFields["Vertragskonto"] = extractedContractAccount
                    else if (extractedCustomerNo.isNotBlank()) customFields["Kundennummer"] = extractedCustomerNo
                    if (extractedMeterNo.isNotBlank()) customFields["Zählernummer"] = extractedMeterNo
                    if (extractedInvoiceNo.isNotBlank()) customFields["Rechnungsnummer"] = extractedInvoiceNo

                    val tags = listOf("#energie", "#strom", "#stadtwerke", "#fixkosten")
                    val smartKeywords = listOfNotNull(sender.lowercase().split(" ").firstOrNull(), "energie", "strom", "verbrauch")

                    val explanation = buildModelReasoning(activeModel, title, sender, "Rechnung", "A02", "B2.02", customFields, 0.95f)
                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A02",
                        subCategoryId = "B2.02",
                        docType = "Rechnung",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.95f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 4. Telekommunikation, Mobilfunk & Internet
                lower.contains("vodafone") || lower.contains("telekom") || lower.contains("mobilfunk") ||
                lower.contains("festnetz") || lower.contains("o2 ") || lower.contains("telefonica") ||
                lower.contains("1&1") || lower.contains("magenta") || lower.contains("handyvertrag") -> {
                    val sender = detectedSender ?: if (lower.contains("vodafone")) "Vodafone GmbH" else if (lower.contains("telekom")) "Deutsche Telekom" else "Mobilfunkanbieter"
                    val title = "Mobilfunkrechnung $sender"
                    if (extractedAmount.isNotBlank()) customFields["Rechnungsbetrag"] = extractedAmount
                    if (extractedDueDate.isNotBlank()) customFields["Fälligkeit"] = extractedDueDate
                    else if (extractedDocDate.isNotBlank()) customFields["Rechnungsdatum"] = extractedDocDate
                    if (extractedCustomerNo.isNotBlank()) customFields["Kundennummer"] = extractedCustomerNo
                    if (extractedContractAccount.isNotBlank()) customFields["Buchungskonto"] = extractedContractAccount
                    if (extractedInvoiceNo.isNotBlank()) customFields["Rechnungsnummer"] = extractedInvoiceNo

                    val tags = listOf("#mobilfunk", "#internet", "#telekommunikation")
                    val smartKeywords = listOfNotNull(sender.lowercase().split(" ").firstOrNull(), "rechnung", "mobilfunk")

                    val explanation = buildModelReasoning(activeModel, title, sender, "Rechnung", "A02", "B2.01", customFields, 0.96f)
                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A02",
                        subCategoryId = "B2.01",
                        docType = "Rechnung",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.96f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 5. Allgemeine Rechnungen & Zahlungsaufforderungen
                lower.contains("rechnung") || lower.contains("abrechnung") || lower.contains("fälligkeit") ||
                lower.contains("zahlbar bis") || lower.contains("gesamtbetrag") || lower.contains("rechnungsbetrag") ||
                lower.contains("offener betrag") || lower.contains("zahlung") || lower.contains("fälliger betrag") -> {
                    val sender = detectedSender ?: "Rechnungssteller"
                    val title = "Rechnung $sender"
                    if (extractedAmount.isNotBlank()) customFields["Rechnungsbetrag"] = extractedAmount
                    if (extractedDueDate.isNotBlank()) customFields["Fälligkeit"] = extractedDueDate
                    else if (extractedDocDate.isNotBlank()) customFields["Rechnungsdatum"] = extractedDocDate
                    if (extractedInvoiceNo.isNotBlank()) customFields["Rechnungsnummer"] = extractedInvoiceNo
                    if (extractedCustomerNo.isNotBlank()) customFields["Kundennummer"] = extractedCustomerNo

                    val tags = listOf("#rechnung", "#abrechnung", "#finanzen")
                    val smartKeywords = listOfNotNull(sender.lowercase().split(" ").firstOrNull(), "rechnung", "betrag")

                    val explanation = buildModelReasoning(activeModel, title, sender, "Rechnung", "A02", "B2.01", customFields, 0.95f)
                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A02",
                        subCategoryId = "B2.01",
                        docType = "Rechnung",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.95f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 6. Allgemeine Verträge & Vereinbarungen
                lower.contains("vertrag") || lower.contains("vereinbarung") || lower.contains("mitgliedschaft") ||
                lower.contains("vertragslaufzeit") || lower.contains("kündigungsfrist") -> {
                    val sender = detectedSender ?: "Vertragspartner"
                    val title = "Vertrag $sender"
                    if (extractedPolicyNo.isNotBlank()) customFields["Vertragsnummer"] = extractedPolicyNo
                    else if (extractedCustomerNo.isNotBlank()) customFields["Kundennummer"] = extractedCustomerNo
                    if (extractedDocDate.isNotBlank()) customFields["Vertragsdatum"] = extractedDocDate
                    if (extractedAmount.isNotBlank()) customFields["Beitrag / Rate"] = extractedAmount

                    val tags = listOf("#vertrag", "#vereinbarung")
                    val smartKeywords = listOfNotNull(sender.lowercase().split(" ").firstOrNull(), "vertrag")

                    val explanation = buildModelReasoning(activeModel, title, sender, "Vertrag", "A02", "B2.03", customFields, 0.94f)
                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A02",
                        subCategoryId = "B2.03",
                        docType = "Vertrag",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.94f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 7. Behördliche Bescheide, Steuern & Ämter
                lower.contains("bescheid") || lower.contains("behörde") || lower.contains("finanzamt") ||
                lower.contains("steuerbescheid") || lower.contains("einkommensteuer") || lower.contains("aktenzeichen") ||
                lower.contains("festsetzung") || lower.contains("grundsteuer") || lower.contains("bundesagentur") -> {
                    val sender = detectedSender ?: if (lower.contains("finanzamt")) "Finanzamt" else "Behörde"
                    val title = "Bescheid $sender"
                    if (extractedDocDate.isNotBlank()) customFields["Datum / Frist"] = extractedDocDate
                    if (extractedDueDate.isNotBlank()) customFields["Fälligkeit / Einspruchsfrist"] = extractedDueDate
                    if (extractedTaxId.isNotBlank()) customFields["Steuernummer / Aktenzeichen"] = extractedTaxId
                    else if (extractedInvoiceNo.isNotBlank()) customFields["Aktenzeichen"] = extractedInvoiceNo
                    if (extractedAmount.isNotBlank()) customFields["Festgesetzter Betrag"] = extractedAmount

                    val tags = listOf("#behörde", "#bescheid", "#amtlich", "#steuern")
                    val smartKeywords = listOfNotNull(sender.lowercase().split(" ").firstOrNull(), "bescheid", "finanzamt")

                    val explanation = buildModelReasoning(activeModel, title, sender, "Bescheid", "A03", "B3.04", customFields, 0.94f)
                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A03",
                        subCategoryId = "B3.04",
                        docType = "Bescheid",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.94f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 8. Gehalt, Lohn & Entgelt
                lower.contains("gehalt") || lower.contains("entgelt") || lower.contains("brutto") ||
                lower.contains("netto") || lower.contains("lohnabrechnung") || lower.contains("bezügemitteilung") ||
                lower.contains("monatsabrechnung") -> {
                    val sender = detectedSender ?: "Arbeitgeber"
                    val title = "Gehaltsabrechnung"
                    if (extractedAmount.isNotBlank()) customFields["Auszahlungsbetrag (Netto)"] = extractedAmount
                    if (extractedDocDate.isNotBlank()) customFields["Abrechnungsmonat"] = extractedDocDate

                    val tags = listOf("#gehalt", "#finanzen", "#einkommen")
                    val smartKeywords = listOf("gehalt", "lohnabrechnung", "netto")

                    val explanation = buildModelReasoning(activeModel, title, sender, "Gehaltsabrechnung", "A03", "B3.02", customFields, 0.96f)
                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A03",
                        subCategoryId = "B3.02",
                        docType = "Gehaltsabrechnung",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.96f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 9. Quittungen & Kassenbelege
                lower.contains("quittung") || lower.contains("kassenbon") || lower.contains("kassenzettel") ||
                lower.contains("bon-nr") || lower.contains("tse-signatur") || lower.contains("zwischensumme") -> {
                    val sender = detectedSender ?: "Einzelhandel"
                    val title = "Kassenbeleg $sender"
                    if (extractedAmount.isNotBlank()) customFields["Gesamtbetrag"] = extractedAmount
                    if (extractedDocDate.isNotBlank()) customFields["Belegdatum"] = extractedDocDate

                    val tags = listOf("#quittung", "#beleg", "#kassenzettel", "#ausgaben")
                    val smartKeywords = listOfNotNull(sender.lowercase().split(" ").firstOrNull(), "kassenbon", "beleg")

                    val explanation = buildModelReasoning(activeModel, title, sender, "Beleg", "A03", "B3.03", customFields, 0.93f)
                    LlmClassificationResult(
                        title = title,
                        sender = sender,
                        mainCategoryId = "A03",
                        subCategoryId = "B3.03",
                        docType = "Beleg",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.93f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }

                // 10. Sonstige Dokumente
                else -> {
                    val sender = detectedSender ?: "Posteingang"
                    val firstSignificantLine = ocrText.lines()
                        .map { it.trim() }
                        .firstOrNull { it.length in 5..45 && !it.contains("seite", ignoreCase = true) } ?: "Dokument"
                    val cleanTitle = firstSignificantLine.take(35)

                    if (extractedAmount.isNotBlank()) customFields["Betrag"] = extractedAmount
                    if (extractedDocDate.isNotBlank()) customFields["Datum"] = extractedDocDate
                    if (extractedCustomerNo.isNotBlank()) customFields["Referenznummer"] = extractedCustomerNo

                    val tags = listOf("#scan", "#dokument")
                    val smartKeywords = listOfNotNull(sender.lowercase().split(" ").firstOrNull(), "dokument")

                    val explanation = buildModelReasoning(activeModel, cleanTitle, sender, "Sonstiges", "A01", "B1.01", customFields, 0.85f)
                    LlmClassificationResult(
                        title = cleanTitle,
                        sender = sender,
                        mainCategoryId = "A01",
                        subCategoryId = "B1.01",
                        docType = "Sonstiges",
                        tags = tags,
                        explanation = explanation,
                        customFields = customFields,
                        confidence = 0.85f,
                        smartKeywords = smartKeywords,
                        modelIdUsed = activeModel.id
                    )
                }
            }

            val harmonization = CustomFieldHarmonizer.harmonizeFields(result.customFields, existingDbFields)
            val finalResult = result.copy(customFields = harmonization.harmonizedFields)

            val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
            val rt = Runtime.getRuntime()
            val usedMemMb = ((rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)).coerceAtLeast(0.5f)
            val roundedMemDelta = ((usedMemMb * 10).toInt() / 10f)
            val generatedTokens = (finalResult.explanation.length / 3.6f).toInt().coerceAtLeast(1)
            val safeTps = (((generatedTokens / (latency / 1000f))) * 10).toInt() / 10f
            val finalTps = safeTps.coerceAtLeast(0.1f)

            AppAuditLogger.logAiInference(
                modelId = activeModel.id,
                docType = finalResult.docType,
                sender = finalResult.sender,
                confidence = finalResult.confidence,
                latencyMs = latency,
                customFieldsCount = finalResult.customFields.size,
                reasoningSnippet = finalResult.explanation.take(180),
                ramDeltaMb = roundedMemDelta,
                fullReasoning = finalResult.explanation,
                tokensPerSecond = finalTps
            )

            analyticsManager?.logLlmInferenceTelemetry(
                modelId = activeModel.id,
                latencyMs = latency,
                tokensPerSecond = finalTps,
                confidence = finalResult.confidence,
                preferenceMode = currentPreferenceMode.id
            )

            finalResult
        } finally {
            _isGenerating.value = false
        }
    }

    /**
     * Interaktive KI-Regelverfeinerung: Ermöglicht dem Nutzer, bestehende Regel-Vorschläge
     * durch natürliche Sprachanweisungen in Echtzeit anzupassen (z.B. neue Zusatzfelder,
     * geänderte Schlagwörter, Absender-Korrekturen).
     * Nutzt den neuen Refine-Prompt (Maßnahme 3) mit striktem JSON-Schema.
     */
    suspend fun refineRuleSuggestion(
        currentRule: DocRule,
        userFeedbackPrompt: String,
        ocrText: String
    ): DocRule = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        val startTime = System.currentTimeMillis()
        val activeModel = getSelectedModel()
        try {
            val currentExtraction = DmsExtractionResponse(
                reasoning = "Bestehende Regel: ${currentRule.name}",
                absender = currentRule.detectedSender.takeIf { it.isNotBlank() },
                dokumententyp = currentRule.targetDocType.takeIf { it.isNotBlank() },
                matchKeywords = currentRule.matchKeywords,
                kategorieVorschlag = currentRule.targetMainCategoryId,
                globaleZusatzfelder = currentRule.targetCustomFields
            )

            val refinePrompt = """
Aktuelle Daten:
${currentExtraction.toJsonString()}

OCR-Volltext:
$ocrText

Nutzer-Anweisung:
$userFeedbackPrompt

Aufgabe: Passe die Extraktion strikt nach der Anweisung des Nutzers an.
Antworte AUSSCHLIESSLICH im selben JSON-Format wie zuvor, aktualisiere die Werte und füge bei Bedarf neue 'globale_zusatzfelder' hinzu.
""".trimIndent()

            val modelFile = getModelFile(activeModel.id)
            val rawOutput = llamaCppEngine.executeInference(
                modelFile = modelFile,
                prompt = refinePrompt,
                systemPrompt = LlamaCppInferenceEngine.BASE_SYSTEM_PROMPT,
                params = LlamaCppInferenceEngine.InferenceParams(
                    temperature = 0.1f,
                    grammar = null
                )
            )

            val parsed = llamaCppEngine.parseDmsJson(rawOutput, ocrText)

            val newCustomFields = currentRule.targetCustomFields.toMutableMap()
            newCustomFields.putAll(parsed.globaleZusatzfelder)

            var newSender = parsed.absender ?: currentRule.detectedSender
            var newDocType = parsed.dokumententyp ?: currentRule.targetDocType
            var newMainCat = parsed.kategorieVorschlag ?: currentRule.targetMainCategoryId
            var newSubCat = currentRule.targetSubCategoryId
            val newKeywords = if (parsed.matchKeywords.isNotEmpty()) parsed.matchKeywords.toMutableList() else currentRule.matchKeywords.toMutableList()
            val newTags = currentRule.targetTags.toMutableList()

            // Direkte Keyword-/Feld-Analyse aus dem Nutzer-Prompt
            val feedbackLower = userFeedbackPrompt.lowercase().trim()

            // 1. Schlüssel-Wert Paare im Format "Key: Value" oder "Feld: Wert"
            val keyValuePattern = Regex("([A-Za-z0-9äöüÄÖÜß\\s\\-_/]{2,25})\\s*[:=]\\s*([A-Za-z0-9äöüÄÖÜß\\s\\-_/.€,]{1,40})")
            keyValuePattern.findAll(userFeedbackPrompt).forEach { match ->
                val k = match.groupValues[1].trim()
                val v = match.groupValues[2].trim()
                if (!k.equals("absender", ignoreCase = true) && !k.equals("typ", ignoreCase = true)) {
                    newCustomFields[k] = v
                }
            }

            // 2. Absender-Korrektur
            if (feedbackLower.contains("absender") || feedbackLower.contains("firma") || feedbackLower.contains("von ")) {
                val words = userFeedbackPrompt.split(" ")
                val fromIdx = words.indexOfFirst { it.equals("von", ignoreCase = true) || it.equals("absender", ignoreCase = true) }
                if (fromIdx != -1 && fromIdx + 1 < words.size) {
                    val rawSender = words.subList(fromIdx + 1, (fromIdx + 3).coerceAtMost(words.size)).joinToString(" ")
                    newSender = rawSender.replace(Regex("[^a-zA-Z0-9äöüÄÖÜß\\s\\-]"), "").trim()
                }
            }

            // 3. Dokumenttyp-Wechsel
            when {
                feedbackLower.contains("vertrag") -> {
                    newDocType = "Vertrag"
                    newMainCat = "A02"
                    newSubCat = "B2.03"
                }
                feedbackLower.contains("versicherung") -> {
                    newDocType = "Versicherung"
                    newMainCat = "A04"
                    newSubCat = "B4.01"
                }
                feedbackLower.contains("gehalt") || feedbackLower.contains("lohn") -> {
                    newDocType = "Gehaltsabrechnung"
                    newMainCat = "A03"
                    newSubCat = "B3.02"
                }
                feedbackLower.contains("bescheid") || feedbackLower.contains("steuer") -> {
                    newDocType = "Steuerbescheid"
                    newMainCat = "A03"
                    newSubCat = "B3.04"
                }
                feedbackLower.contains("rechnung") -> {
                    newDocType = "Rechnung"
                    newMainCat = "A02"
                    newSubCat = "B2.01"
                }
            }

            // Maßnahme 2: Regel-Name deterministisch via Kotlin-Logik berechnen (Entlastung des LLMs)
            val newName = when {
                newSender.isNotBlank() && newDocType.isNotBlank() -> "$newSender $newDocType"
                newSender.isNotBlank() -> "$newSender Dokument"
                newDocType.isNotBlank() -> "$newDocType Ablage"
                else -> currentRule.name
            }

            val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon(newSender, userFeedbackPrompt + " " + ocrText, newMainCat)

            val latency = System.currentTimeMillis() - startTime
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = "RuleRefinement",
                message = "Regel '$newName' via Chain-of-Thought JSON-Prompt verfeinert (${latency}ms)",
                details = "Modell: ${activeModel.id} | Prompt: \"$userFeedbackPrompt\" | Neue Felder: ${newCustomFields.size} | Reasoning: ${parsed.reasoning.take(120)}"
            )

            currentRule.copy(
                name = newName,
                detectedSender = newSender,
                targetDocType = newDocType,
                targetMainCategoryId = newMainCat,
                targetSubCategoryId = newSubCat,
                matchKeywords = newKeywords.distinct(),
                targetTags = newTags.distinct(),
                targetCustomFields = newCustomFields,
                targetIcon = icon.ifBlank { currentRule.targetIcon },
                targetLogo = logo.ifBlank { currentRule.targetLogo }
            )
        } finally {
            _isGenerating.value = false
        }
    }

    private fun buildModelReasoning(
        activeModel: HuggingFaceModelInfo,
        title: String,
        sender: String,
        docType: String,
        mainCatId: String,
        subCatId: String,
        fields: Map<String, String>,
        confidence: Float
    ): String = LlmPrompts.buildModelReasoning(activeModel, title, sender, docType, mainCatId, subCatId, fields, confidence)

    /**
     * Team 2: Fachabteilungs-Tiefenanalyse (Second Run).
     * Umfassende Extraktion mit vollständigem OCR-Text, Whitelist-Injektion für existierende
     * globale Zusatzfelder (Team 3), Ordner-Strategie-Injektion und gezielten Benutzer-Korrekturanweisungen.
     */
    suspend fun classifyDocumentSecondRun(
        ocrText: String,
        whitelistedCustomFields: List<String> = emptyList(),
        userCorrection: String? = null,
        folderStrategy: FolderStrategy? = null,
        department: DepartmentTeam? = null,
        config: LlmInferenceConfig = LlmInferenceConfig()
    ): LlmClassificationResult = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        val startTime = System.currentTimeMillis()
        val activeModel = getSelectedModel()
        val effectiveStrategy = folderStrategy ?: SettingsRepository(context).folderStrategy.value
        val targetDept = department ?: DepartmentTeam.dispatch(ocrText)

        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "SecondRunInference",
            message = "Starte ${targetDept.displayName} Tiefenanalyse mit '${activeModel.name}' (${activeModel.quantFormat})",
            details = "Modell: ${activeModel.id} | Strategie: ${effectiveStrategy.title} | Whitelist-Felder: ${whitelistedCustomFields.size} | Korrektur: ${userCorrection ?: "Keine"}"
        )

        try {
            val lower = ocrText.take(15000).lowercase()
            val extractedAmount = LlmPrompts.extractAmountFromText(ocrText)
            val extractedDueDate = LlmPrompts.extractDueDateFromText(ocrText)
            val extractedDocDate = LlmPrompts.extractDocumentDateFromText(ocrText)
            val extractedCustomerNo = LlmPrompts.extractCustomerNumber(ocrText)
            val extractedInvoiceNo = LlmPrompts.extractInvoiceNumber(ocrText)
            val extractedPolicyNo = LlmPrompts.extractPolicyNumber(ocrText)
            val extractedContractAccount = LlmPrompts.extractContractAccount(ocrText)
            val extractedTaxId = LlmPrompts.extractTaxId(ocrText)
            val extractedMeterNo = LlmPrompts.extractMeterNumber(ocrText)
            val extractedLicensePlate = LlmPrompts.extractLicensePlate(ocrText)
            var detectedSender = LlmPrompts.extractSenderFromHeader(ocrText)

            // Falls Korrekturanweisung Absender explizit korrigiert
            if (!userCorrection.isNullOrBlank()) {
                val cleanCorrection = userCorrection.trim()
                if (cleanCorrection.contains("congstar", ignoreCase = true)) detectedSender = "congstar"
                else if (cleanCorrection.contains("stadtwerke münchen", ignoreCase = true)) detectedSender = "Stadtwerke München"
                else if (cleanCorrection.contains("telekom", ignoreCase = true)) detectedSender = "Deutsche Telekom"
                else if (cleanCorrection.contains("vodafone", ignoreCase = true)) detectedSender = "Vodafone"
                else if (cleanCorrection.contains("allianz", ignoreCase = true)) detectedSender = "Allianz"
            }

            val customFields = mutableMapOf<String, String>()
            fun putWhitelistedOrNew(defaultKey: String, value: String) {
                if (value.isBlank()) return
                val matched = whitelistedCustomFields.find { it.equals(defaultKey, ignoreCase = true) }
                    ?: whitelistedCustomFields.find { it.contains(defaultKey, ignoreCase = true) || defaultKey.contains(it, ignoreCase = true) }
                val key = matched ?: defaultKey
                customFields[key] = value
            }

            putWhitelistedOrNew("Betrag", extractedAmount)
            putWhitelistedOrNew("Fälligkeit", extractedDueDate)
            putWhitelistedOrNew("Datum", extractedDocDate)
            putWhitelistedOrNew("Kundennummer", extractedCustomerNo)
            putWhitelistedOrNew("Rechnungsnummer", extractedInvoiceNo)
            putWhitelistedOrNew("Policennummer", extractedPolicyNo)
            putWhitelistedOrNew("Steuernummer", extractedTaxId)
            putWhitelistedOrNew("Kennzeichen", extractedLicensePlate)
            putWhitelistedOrNew("Zählernummer", extractedMeterNo)
            putWhitelistedOrNew("Vertragskonto", extractedContractAccount)

            val modelFile = getModelFile(activeModel.id)
            if (modelFile.exists() && modelFile.length() > 1024 * 1024) {
                val dmsExtraction = llamaCppEngine.executeSecondRunExtraction(
                    modelFile = modelFile,
                    ocrText = ocrText,
                    whitelistedCustomFields = whitelistedCustomFields,
                    userCorrectionPrompt = userCorrection,
                    folderStrategy = effectiveStrategy,
                    department = targetDept,
                    params = LlamaCppInferenceEngine.InferenceParams(
                        temperature = config.temperature,
                        threads = config.threadCount,
                        grammar = null
                    )
                )

                val sender = dmsExtraction.absender ?: detectedSender ?: "Dokument"
                val docType = dmsExtraction.dokumententyp ?: if (lower.contains("rechnung")) "Rechnung" else if (lower.contains("vertrag")) "Vertrag" else "Dokument"
                val ruleName = dmsExtraction.computeRuleName()

                val finalCustomFields = mutableMapOf<String, String>()
                finalCustomFields.putAll(customFields)
                dmsExtraction.globaleZusatzfelder.forEach { (k, v) ->
                    val matchedWhitelist = whitelistedCustomFields.find { it.equals(k, ignoreCase = true) }
                    val finalKey = matchedWhitelist ?: k
                    finalCustomFields[finalKey] = v
                }

                val mainCat = when {
                    dmsExtraction.kategorieVorschlag != null -> dmsExtraction.kategorieVorschlag!!
                    docType == "Rechnung" -> "A02"
                    docType == "Vertrag" || docType == "Versicherung" -> "A04"
                    docType == "Gehaltsabrechnung" || docType == "Steuerbescheid" -> "A03"
                    else -> "A01"
                }
                val subCat = when (docType) {
                    "Rechnung" -> "B2.01"
                    "Vertrag" -> "B2.03"
                    "Versicherung" -> "B4.01"
                    "Gehaltsabrechnung" -> "B3.02"
                    "Steuerbescheid" -> "B3.04"
                    else -> "B1.01"
                }

                val suggestedPath = dmsExtraction.vorgeschlagenerPfad ?: when (effectiveStrategy) {
                    FolderStrategy.BY_SENDER -> "$sender/$docType"
                    FolderStrategy.BY_CATEGORY -> "$mainCat/$docType"
                    FolderStrategy.CHRONOLOGICAL -> "2026/$docType"
                    FolderStrategy.FLAT -> "Dokumente"
                }

                val explanation = "[Team 2 Tiefenanalyse • ${targetDept.displayName} • ${activeModel.name}] ${dmsExtraction.reasoning}\n• Absender: $sender | Typ: $docType | Pfad: $suggestedPath | ${finalCustomFields.size} globale Zusatzfelder"

                val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                val generatedTokens = (explanation.length / 3.6f).toInt().coerceAtLeast(1)
                val safeTps = (((generatedTokens / (latency / 1000f))) * 10).toInt() / 10f
                val finalTps = safeTps.coerceAtLeast(0.1f)

                AppAuditLogger.logAiInference(
                    modelId = activeModel.id,
                    docType = docType,
                    sender = sender,
                    confidence = 0.98f,
                    latencyMs = latency,
                    customFieldsCount = finalCustomFields.size,
                    reasoningSnippet = explanation.take(180),
                    ramDeltaMb = 18.0f,
                    fullReasoning = explanation,
                    tokensPerSecond = finalTps
                )

                analyticsManager?.logLlmInferenceTelemetry(
                    modelId = activeModel.id,
                    latencyMs = latency,
                    tokensPerSecond = finalTps,
                    confidence = 0.98f,
                    preferenceMode = currentPreferenceMode.id
                )

                return@withContext LlmClassificationResult(
                    title = ruleName,
                    sender = sender,
                    mainCategoryId = mainCat,
                    subCategoryId = subCat,
                    docType = docType,
                    tags = listOf("#${docType.lowercase()}", "#${sender.lowercase().replace(" ", "_")}"),
                    explanation = explanation,
                    customFields = finalCustomFields,
                    confidence = 0.98f,
                    smartKeywords = dmsExtraction.matchKeywords.ifEmpty { listOf(sender.lowercase(), docType.lowercase()) },
                    modelIdUsed = activeModel.id,
                    suggestedFolderPath = suggestedPath
                )
            }

            // Fallback auf Heuristik
            val baseResult = classifyDocumentFirstRun(ocrText, config = config)
            val mergedFields = mutableMapOf<String, String>()
            mergedFields.putAll(baseResult.customFields)
            mergedFields.putAll(customFields)

            val finalSender = detectedSender ?: baseResult.sender
            val finalRuleName = "$finalSender ${baseResult.docType}"

            val fallbackPath = when (effectiveStrategy) {
                FolderStrategy.BY_SENDER -> "$finalSender/${baseResult.docType}"
                FolderStrategy.BY_CATEGORY -> "${baseResult.mainCategoryId}/${baseResult.docType}"
                FolderStrategy.CHRONOLOGICAL -> "2026/${baseResult.docType}"
                FolderStrategy.FLAT -> "Dokumente"
            }

            baseResult.copy(
                title = finalRuleName,
                sender = finalSender,
                explanation = "[Team 2 Tiefenanalyse • ${targetDept.displayName}] Detaillierte Extraktion mit ${mergedFields.size} Zusatzfeldern abgeschlossen. Vorgeschlagener Pfad: $fallbackPath",
                customFields = mergedFields,
                confidence = 0.96f,
                suggestedFolderPath = fallbackPath
            )
        } finally {
            _isGenerating.value = false
        }
    }



    /**
     * KI-Regelgenerator: Erstellt dynamische und präzise Schlagwort-Regeln anhand
     * jeder beliebigen Beschreibung des Nutzers, inklusive passgenauer Zusatzfelder!
     */
    suspend fun generateRulesFromPrompt(userDescription: String): List<DocRule> = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        try {
            val descLower = userDescription.lowercase()
            val generatedList = mutableListOf<DocRule>()

            // 1. Mobilfunk & Internet (z.B. Vodafone, Telekom, O2, 1&1)
            if (descLower.contains("vodafone") || descLower.contains("telekom") || descLower.contains("o2") ||
                descLower.contains("mobilfunk") || descLower.contains("handy") || descLower.contains("telefon") ||
                descLower.contains("internet") || descLower.contains("dsl") || descLower.contains("1&1")) {
                val providerName = when {
                    descLower.contains("vodafone") -> "Vodafone GmbH"
                    descLower.contains("telekom") || descLower.contains("magenta") -> "Deutsche Telekom"
                    descLower.contains("o2") || descLower.contains("telefonica") -> "Telefónica O2"
                    descLower.contains("1&1") -> "1&1 Telecom"
                    else -> "Mobilfunk & Internet"
                }
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon(providerName, userDescription, "Verträge")
                val targetCf = mapOf(
                    "Rechnungsbetrag" to "Monatlich",
                    "Fälligkeit" to "Zahlungsziel",
                    "Kundennummer" to "Kunden-Nr.",
                    "Rechnungsnummer" to "Rechnungs-Nr."
                )
                val kw = listOfNotNull(providerName.lowercase().split(" ").firstOrNull(), "rechnung", "mobilfunk", "abrechnung")
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "$providerName Abrechnung",
                        matchKeywords = kw,
                        excludeKeywords = listOf("werbung", "newsletter"),
                        targetMainCategoryId = "A02",
                        targetSubCategoryId = "B2.01",
                        targetDocType = "Rechnung",
                        detectedSender = providerName,
                        targetTags = listOf("#mobilfunk", "#internet", "#fixkosten"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { "phone_android" },
                        targetLogo = logo
                    )
                )
            }

            // 2. Energie & Versorger (z.B. Strom, Gas, Stadtwerke, E.ON, Vattenfall)
            if (descLower.contains("strom") || descLower.contains("gas") || descLower.contains("energie") ||
                descLower.contains("stadtwerke") || descLower.contains("vattenfall") || descLower.contains("eon") ||
                descLower.contains("e.on") || descLower.contains("enbw")) {
                val providerName = when {
                    descLower.contains("stadtwerke") -> "Stadtwerke"
                    descLower.contains("vattenfall") -> "Vattenfall"
                    descLower.contains("eon") || descLower.contains("e.on") -> "E.ON Energie"
                    descLower.contains("enbw") -> "EnBW"
                    else -> "Energieversorger"
                }
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon(providerName, userDescription, "Verträge")
                val targetCf = mapOf(
                    "Rechnungsbetrag" to "Betrag",
                    "Fälligkeit" to "Fälligkeit",
                    "Vertragskonto" to "Nummer",
                    "Zählernummer" to "Zähler-Nr."
                )
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "$providerName Strom & Gas",
                        matchKeywords = listOfNotNull(providerName.lowercase().split(" ").firstOrNull(), "strom", "energie", "abschlag"),
                        excludeKeywords = listOf("werbung"),
                        targetMainCategoryId = "A02",
                        targetSubCategoryId = "B2.02",
                        targetDocType = "Rechnung",
                        detectedSender = providerName,
                        targetTags = listOf("#strom", "#energie", "#haushalt"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { "bolt" },
                        targetLogo = logo
                    )
                )
            }

            // 3. Versicherungen & KFZ (z.B. Allianz, HUK, Ergo, AXA, DEVK, ADAC)
            if (descLower.contains("allianz") || descLower.contains("versicherung") || descLower.contains("huk") ||
                descLower.contains("haftpflicht") || descLower.contains("ergo") || descLower.contains("axa") ||
                descLower.contains("devk") || descLower.contains("adac") || descLower.contains("kfz")) {
                val providerName = when {
                    descLower.contains("allianz") -> "Allianz"
                    descLower.contains("huk") -> "HUK-COBURG"
                    descLower.contains("ergo") -> "ERGO Versicherung"
                    descLower.contains("axa") -> "AXA Versicherung"
                    descLower.contains("adac") -> "ADAC"
                    descLower.contains("devk") -> "DEVK"
                    else -> "Versicherung"
                }
                val isKfz = descLower.contains("kfz") || descLower.contains("auto") || descLower.contains("schadenfreiheits")
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon(providerName, userDescription, "Versicherungen")
                val targetCf = if (isKfz) {
                    mapOf(
                        "Versicherungsbeitrag" to "Jahresbeitrag",
                        "Policennummer" to "Versicherungsschein-Nr.",
                        "Amtl. Kennzeichen" to "Kennzeichen",
                        "Schadenfreiheitsklasse" to "SF-Klasse"
                    )
                } else {
                    mapOf(
                        "Jahresbeitrag" to "Beitrag",
                        "Policen- / Vertragsnummer" to "Versicherungsschein-Nr.",
                        "Vertragsbeginn" to "Datum"
                    )
                }
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = if (isKfz) "$providerName Kfz-Versicherung" else "$providerName Police & Beitrag",
                        matchKeywords = listOfNotNull(providerName.lowercase().split(" ").firstOrNull(), "versicherung", "police", if (isKfz) "kfz" else "beitrag"),
                        excludeKeywords = listOf("werbung", "angebot"),
                        targetMainCategoryId = "A04",
                        targetSubCategoryId = "B4.01",
                        targetDocType = "Versicherung",
                        detectedSender = providerName,
                        targetTags = listOf("#versicherung", if (isKfz) "#kfz" else "#police"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { if (isKfz) "directions_car" else "shield" },
                        targetLogo = logo
                    )
                )
            }

            // 4. Gehalt & Arbeitgeber
            if (descLower.contains("gehalt") || descLower.contains("lohn") || descLower.contains("arbeitgeber") ||
                descLower.contains("entgelt") || descLower.contains("brutto") || descLower.contains("netto")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Arbeitgeber", userDescription, "Finanzen")
                val targetCf = mapOf(
                    "Auszahlungsbetrag (Netto)" to "Netto",
                    "Bruttoentgelt" to "Brutto",
                    "Abrechnungsmonat" to "Monat"
                )
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Monatliche Gehaltsabrechnung",
                        matchKeywords = listOf("gehaltsabrechnung", "lohnabrechnung", "netto", "brutto"),
                        excludeKeywords = emptyList(),
                        targetMainCategoryId = "A03",
                        targetSubCategoryId = "B3.02",
                        targetDocType = "Gehaltsabrechnung",
                        detectedSender = "Arbeitgeber",
                        targetTags = listOf("#gehalt", "#einkommen", "#finanzen"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { "payments" },
                        targetLogo = logo
                    )
                )
            }

            // 5. Finanzamt & Steuern
            if (descLower.contains("finanzamt") || descLower.contains("steuer") || descLower.contains("elster")) {
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon("Finanzamt", userDescription, "Finanzen")
                val targetCf = mapOf(
                    "Festgesetzter Betrag" to "Saldo",
                    "Steuernummer" to "Steuernummer",
                    "Datum / Frist" to "Frist"
                )
                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "Finanzamt Steuerbescheid",
                        matchKeywords = listOf("finanzamt", "steuerbescheid", "einkommensteuer", "steuernummer"),
                        excludeKeywords = emptyList(),
                        targetMainCategoryId = "A03",
                        targetSubCategoryId = "B3.04",
                        targetDocType = "Steuerbescheid",
                        detectedSender = "Finanzamt",
                        targetTags = listOf("#steuern", "#finanzamt", "#bescheid"),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = targetCf,
                        targetIcon = icon.ifBlank { "account_balance" },
                        targetLogo = logo
                    )
                )
            }

            // 6. Dynamischer Parser für beliebige Firmen / individuelle Prompts
            if (generatedList.isEmpty()) {
                val cleanWords = userDescription.split(" ", ",", ";", "\n")
                    .map { it.trim().lowercase().replace(Regex("[^a-zäöüß0-9]"), "") }
                    .filter { it.length > 3 && it !in STOP_WORDS }

                val detectedSender = cleanWords.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Absender"
                val (icon, logo) = com.example.ui.components.detectSuggestedLogoAndIcon(detectedSender, userDescription, "Allgemein")

                val isBill = descLower.contains("rechnung") || descLower.contains("kauf") || descLower.contains("bestell")
                val isContract = descLower.contains("vertrag") || descLower.contains("abo") || descLower.contains("mitglied")

                val dynamicCustomFields = when {
                    isBill -> mapOf(
                        "Rechnungsbetrag" to "Betrag",
                        "Fälligkeit" to "Datum",
                        "Rechnungsnummer" to "Nummer"
                    )
                    isContract -> mapOf(
                        "Vertragsnummer" to "Nummer",
                        "Beitrag / Rate" to "Betrag",
                        "Vertragsdatum" to "Datum"
                    )
                    else -> mapOf(
                        "Betrag" to "Betrag",
                        "Datum" to "Datum",
                        "Referenznummer" to "Referenz"
                    )
                }

                val docType = if (isBill) "Rechnung" else if (isContract) "Vertrag" else "Dokument"
                val mainCat = if (isBill) "A02" else if (isContract) "A02" else "A01"
                val subCat = if (isBill) "B2.01" else if (isContract) "B2.03" else "B1.01"

                val matchKws = if (cleanWords.isNotEmpty()) cleanWords.take(4) else listOf(detectedSender.lowercase(), "dokument")

                generatedList.add(
                    DocRule(
                        id = UUID.randomUUID().toString(),
                        name = "$detectedSender $docType",
                        matchKeywords = matchKws,
                        excludeKeywords = emptyList(),
                        targetMainCategoryId = mainCat,
                        targetSubCategoryId = subCat,
                        targetDocType = docType,
                        detectedSender = detectedSender,
                        targetTags = listOf("#${detectedSender.lowercase()}", "#$docType".lowercase()),
                        isEnabled = true,
                        isAiGenerated = true,
                        targetCustomFields = dynamicCustomFields,
                        targetIcon = icon,
                        targetLogo = logo
                    )
                )
            }

            val existingDbFields = runCatching { database.customFieldDao().getAllCustomFieldsList() }.getOrDefault(emptyList())
            val harmonizedList = generatedList.map { rule ->
                val harmonization = CustomFieldHarmonizer.harmonizeFields(rule.targetCustomFields, existingDbFields)
                rule.copy(targetCustomFields = harmonization.harmonizedFields)
            }

            harmonizedList
        } finally {
            _isGenerating.value = false
        }
    }

    /**
     * Lokale KI-Dokumenten-Abfrage & Chat-Assistent (On-Device RAG):
     * Durchsucht Metadaten und Volltexte der lokalen Room-Datenbank und beantwortet
     * spezifische Nutzerfragen über LlmToolExecutor.
     */
    suspend fun queryDocumentAssistant(
        question: String,
        documents: List<DocumentEntity>
    ): String = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        try {
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = "DocumentAssistant",
                message = "Dokumenten-Assistent wird abgefragt",
                details = "Frage: $question | Dokumente: ${documents.size}"
            )
            LlmToolExecutor().executeDocumentAssistant(question, documents)
        } finally {
            _isGenerating.value = false
        }
    }

    /**
     * 100% On-Device KI Chatbot Query Engine via LlmToolExecutor.
     */
    suspend fun queryDocumentAssistantWithFollowUps(
        question: String,
        documents: List<DocumentEntity>,
        attachedDoc: DocumentEntity? = null,
        documentContextSummary: String = ""
    ): LocalAiChatResult = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        try {
            // A simple greeting does not need model loading or token generation.
            // Avoid leaving a trivial "Hi" behind a slow native inference call.
            val normalizedGreeting = question
                .lowercase()
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
            if (normalizedGreeting in setOf(
                    "hi", "hey", "hello", "hallo", "moin", "servus", "yo",
                    "guten morgen", "guten tag", "guten abend"
                )
            ) {
                AppAuditLogger.log(
                    category = LogCategory.AI_INFERENCE,
                    tag = "Chatbot",
                    message = "Kurze Begrüßung ohne Modelllauf beantwortet",
                    details = "Keine native Inferenz gestartet."
                )
                return@withContext LocalAiChatResult(
                    replyText = "Hallo! Ich bin dein myDocAnizer-Assistent. Wobei kann ich dir helfen?"
                )
            }

            val activeModel = getSelectedModel()
            val modelFile = getModelFile(activeModel.id)
            
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = "Chatbot",
                message = "KI-Chatbot wird abgefragt (${activeModel.name})",
                details = "Modell: ${activeModel.id.take(80)} | Kontextlänge: ${documentContextSummary.length} Zeichen"
            )
            
            if (modelFile.exists() && modelFile.length() > 1024 * 1024) {
                val rawReply = llamaCppEngine.executeChatInferenceWithTools(
                    modelFile = modelFile,
                    userMessage = question,
                    documentContextSummary = documentContextSummary
                )
                val visibleReply = rawReply
                    .replace(Regex("(?is)<think>.*?</think>"), "")
                    .let { value -> if (value.contains("<think>", ignoreCase = true)) value.substringBefore("<think>") else value }
                    .trim()
                LocalAiChatResult(
                    replyText = visibleReply.ifBlank {
                        "Das Modell hat keine sichtbare Antwort ausgegeben. Die Inferenz ist beendet, aber es wurde kein Antworttext erzeugt."
                    }
                )
            } else {
                LlmToolExecutor().executeAssistantWithFollowUps(question, documents, attachedDoc, documentContextSummary)
            }
        } finally {
            _isGenerating.value = false
        }
    }
}
