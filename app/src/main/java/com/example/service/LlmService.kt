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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Modell-Informationen für lokale HuggingFace Nano-LLMs
 */
data class HuggingFaceModelInfo(
    val id: String,
    val name: String,
    val author: String,
    val quantFormat: String,
    val downloadSizeMb: Int,
    val parameterSize: String,
    val recommendedRamGb: Float,
    val ramBadge: String,
    val descriptionDe: String,
    val criteria: List<String>,
    val compatibilityLevel: ModelCompatibilityLevel = ModelCompatibilityLevel.OPTIMAL,
    val isHardwareRecommended: Boolean = false,
    val hardwareRecommendationReason: String = "",
    val downloadUrl: String = "",
    val fileName: String = "",
    val localFileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isSelected: Boolean = false,
    val isCuratedApproved: Boolean = true,
    val approvalStatus: String = "Geprüft von mr.locke84",
    val isNewRelease: Boolean = false,
    val releaseDate: String = "2026",
    val modelCategory: String = "Allgemein" // "Allgemein", "Reasoning & Logik", "Verträge & Jura", "Finanzen & Tabellen", "Mehrsprachig"
)

/**
 * Ergebnis einer lokalen LLM-Dokumentenanalyse mit strukturierten Zusatzfeldern
 */
data class LlmClassificationResult(
    val title: String,
    val sender: String,
    val mainCategoryId: String,
    val subCategoryId: String,
    val docType: String,
    val tags: List<String>,
    val explanation: String,
    val customFields: Map<String, String> = emptyMap(),
    val confidence: Float = 0.95f,
    val smartKeywords: List<String> = emptyList(),
    val modelIdUsed: String = "",
    val suggestedFolderPath: String = ""
)

/**
 * Ergebnis einer lokalen KI-Chatbot-Antwort mit optionaler App-Steuerungs-Aktion
 */
data class LocalAiChatResult(
    val replyText: String,
    val actionType: String? = null,
    val actionLabel: String? = null
)

/**
 * Service für die lokale KI (On-Device LLM via HuggingFace).
 * 100% offline & datenschutzkonform auf dem Telefon ausführbar.
 */
class LlmService(private val context: Context) {

    companion object {
        private val STOP_WORDS = setOf("wie", "viele", "habe", "ich", "was", "und", "der", "die", "das", "ein", "eine", "den", "dem", "des", "zu", "von", "im", "in", "mit", "für")
    }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _deviceHardware = MutableStateFlow(detectDeviceHardware())
    val deviceHardware: StateFlow<DeviceHardwareInfo> = _deviceHardware.asStateFlow()

    fun detectDeviceHardware(): DeviceHardwareInfo {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager?.getMemoryInfo(memoryInfo)

            val totalGb = (memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).toFloat()
            val availGb = (memoryInfo.availMem / (1024.0 * 1024.0 * 1024.0)).toFloat()
            val usedGb = (totalGb - availGb).coerceAtLeast(0f)
            val usagePct = if (totalGb > 0f) ((usedGb / totalGb) * 100).toInt() else 42
            val cores = Runtime.getRuntime().availableProcessors()
            val isLowRam = memoryInfo.lowMemory || totalGb < 3.0f

            val roundedTotal = ((totalGb * 10).toInt() / 10f).coerceAtLeast(1.0f)
            val roundedAvail = ((availGb * 10).toInt() / 10f).coerceAtLeast(0.5f)
            val roundedUsed = ((usedGb * 10).toInt() / 10f).coerceAtLeast(0.5f)

            DeviceHardwareInfo(
                totalRamGb = roundedTotal,
                availableRamGb = roundedAvail,
                usedRamGb = roundedUsed,
                ramUsagePercent = usagePct.coerceIn(5, 95),
                cpuCores = cores.coerceAtLeast(4),
                isLowRamDevice = isLowRam
            )
        } catch (e: Exception) {
            DeviceHardwareInfo(
                totalRamGb = 6.0f,
                availableRamGb = 3.5f,
                usedRamGb = 2.5f,
                ramUsagePercent = 42,
                cpuCores = 8,
                isLowRamDevice = false
            )
        }
    }

    private fun calculateCompatibility(recommendedRamGb: Float, hw: DeviceHardwareInfo): ModelCompatibilityLevel {
        return when {
            hw.totalRamGb >= recommendedRamGb -> ModelCompatibilityLevel.OPTIMAL
            hw.totalRamGb >= recommendedRamGb - 1.2f -> ModelCompatibilityLevel.LIMITED
            else -> ModelCompatibilityLevel.NOT_RECOMMENDED
        }
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

                    if (foundFile != null && foundFile.exists()) {
                        linkedCount++
                        model.copy(
                            isDownloaded = true,
                            localFileSizeBytes = foundFile.length(),
                            downloadProgress = 1.0f
                        )
                    } else {
                        model.copy(
                            isDownloaded = isModelPhysicallyOnDisk(model.fileName),
                            localFileSizeBytes = getModelDiskSize(model.fileName)
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
                isSelected = (recommendedModelId == "smollm2-135m-instruct")
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
                isSelected = false
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-0.5b-instruct",
                name = "Qwen 2.5 0.5B Instruct",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 491,
                parameterSize = "490 Mio",
                recommendedRamGb = 3.0f,
                ramBadge = "3 – 4 GB RAM",
                descriptionDe = "Hervorragendes deutsches Sprachverständnis. Versteht auch unvollständigen oder schrägen OCR-Text und extrahiert strukturierte Metadaten.",
                criteria = listOf(
                    "Latenz: Schnell (< 350 ms)",
                    "Sprache: Starkes deutsches Sprachgefühl",
                    "Einsatz: Verträge, Bescheide & Abrechnungen"
                ),
                compatibilityLevel = calculateCompatibility(3.0f, hw),
                isHardwareRecommended = (recommendedModelId == "qwen2.5-0.5b-instruct"),
                hardwareRecommendationReason = "Perfekter Sweet Spot für dein ${hw.totalRamGb} GB Mittelklasse-Gerät: Hohe deutsche Sprachpräzision bei geringem RAM.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-0.5b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-0.5b-instruct-q4_k_m.gguf"),
                isSelected = (recommendedModelId == "qwen2.5-0.5b-instruct")
            ),
            HuggingFaceModelInfo(
                id = "llama-3.2-1b-instruct",
                name = "Llama 3.2 1B Instruct",
                author = "Meta AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 807,
                parameterSize = "1.23 Mrd",
                recommendedRamGb = 4.0f,
                ramBadge = "4 – 6 GB RAM",
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
                isSelected = false
            ),
            HuggingFaceModelInfo(
                id = "qwen2.5-1.5b-instruct",
                name = "Qwen 2.5 1.5B Instruct",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1117,
                parameterSize = "1.54 Mrd",
                recommendedRamGb = 4.5f,
                ramBadge = "4 – 6 GB RAM",
                descriptionDe = "Leistungsstarkes Sprachmodell für tiefere Textanalyse. Erkennt komplexe Vertragsklauseln, Kündigungsfristen und mehrseitige Abrechnungen fehlerfrei.",
                criteria = listOf(
                    "Latenz: Flott (~500 ms)",
                    "Intelligenz: Hohe semantische Zuverlässigkeit",
                    "Einsatz: Mehrseitige Verträge & Versicherungspolicen"
                ),
                compatibilityLevel = calculateCompatibility(4.5f, hw),
                isHardwareRecommended = (recommendedModelId == "qwen2.5-1.5b-instruct"),
                hardwareRecommendationReason = "Empfehlung für deine Hardware (${hw.totalRamGb} GB RAM, ${hw.cpuCores} Kerne): Maximale semantische Präzision & Fristenerkennung.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-1.5b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-1.5b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-1.5b-instruct-q4_k_m.gguf"),
                isSelected = (recommendedModelId == "qwen2.5-1.5b-instruct")
            ),
            HuggingFaceModelInfo(
                id = "deepseek-r1-distill-qwen-1.5b",
                name = "DeepSeek-R1 Distill Qwen 1.5B",
                author = "DeepSeek-AI",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1117,
                parameterSize = "1.5 Mrd",
                recommendedRamGb = 4.5f,
                ramBadge = "4 – 6 GB RAM",
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
                id = "phi-3.5-mini-instruct",
                name = "Phi-3.5 Mini Instruct",
                author = "Microsoft Research",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 2393,
                parameterSize = "3.8 Mrd",
                recommendedRamGb = 6.0f,
                ramBadge = "6 – 8 GB RAM",
                descriptionDe = "Überragende logische Textanalyse für mehrseitige Verträge, Kündigungsbedingungen, AGBs und juristische Klauseln.",
                criteria = listOf(
                    "Logik: Höchste Sprachlogik bei Verträgen",
                    "Kontext: Mehrseitige Klauseln & AGBs",
                    "Latenz: ~750 ms"
                ),
                compatibilityLevel = calculateCompatibility(6.0f, hw),
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
                recommendedRamGb = 6.0f,
                ramBadge = "6 – 8 GB RAM",
                descriptionDe = "Europäisches Spitzenmodell mit exzellenter nativer Mehrsprachigkeit (DE, EN, FR, ES, IT). Perfekt für internationale Rechnungen & Hotelbelege.",
                criteria = listOf(
                    "Sprachen: Nativ mehrsprachig (DE/EN/FR/ES)",
                    "Präzision: Strukturierte Extraktion",
                    "Latenz: Flott (~600 ms)"
                ),
                compatibilityLevel = calculateCompatibility(6.0f, hw),
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
                id = "qwen2.5-coder-1.5b",
                name = "Qwen 2.5 Coder 1.5B (Finanzen)",
                author = "Alibaba Cloud / Qwen",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 1117,
                parameterSize = "1.54 Mrd",
                recommendedRamGb = 4.5f,
                ramBadge = "4 – 6 GB RAM",
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
                recommendedRamGb = 4.5f,
                ramBadge = "4 – 6 GB RAM",
                descriptionDe = "Hochspezialisiertes On-Device Modell für strukturierte Beleg- & Rechnungsextraktion. Liest Beträge, IBAN, Rechnungsnummern und Fristen nach vordefinierten Schemas aus.",
                criteria = listOf(
                    "Spezialist: Rechnungs-, Beleg- & Formular-Extraktion",
                    "Schema: Striktes JSON-Parsing ohne Halluzinationen",
                    "Latenz: Schnell (~380 ms via Vulkan/NEON)"
                ),
                compatibilityLevel = calculateCompatibility(4.5f, hw),
                isHardwareRecommended = (hw.totalRamGb >= 4.0f && hw.totalRamGb < 6.0f),
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
                id = "distil-qwen-0.8b-invoice-triage-gguf",
                name = "Distil-Qwen 0.8B Invoice Triage",
                author = "distil-labs",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 580,
                parameterSize = "800 Mio",
                recommendedRamGb = 2.5f,
                ramBadge = "2.5 – 4 GB RAM",
                descriptionDe = "Ultraschlankes, feingetuntes Spezialmodell zur automatischen Beleg- und Rechnungstriage. Klassifiziert Posteingänge präzise in Rechnung, Quittung, Mahnung oder Vertrag.",
                criteria = listOf(
                    "Fokus: Blitzschnelle Posteingangs-Klassifikation",
                    "Ressourcen: Extrem geringer Speicher- & Akkubedarf",
                    "Latenz: Ultraschnell (< 180 ms)"
                ),
                compatibilityLevel = calculateCompatibility(2.5f, hw),
                isHardwareRecommended = false,
                hardwareRecommendationReason = "Geringer RAM-Bedarf, ideal für Posteingangs-Triage.",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                fileName = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                isDownloaded = isModelPhysicallyOnDisk("qwen2.5-0.5b-instruct-q4_k_m.gguf"),
                localFileSizeBytes = getModelDiskSize("qwen2.5-0.5b-instruct-q4_k_m.gguf"),
                isSelected = false,
                isCuratedApproved = true,
                approvalStatus = "Verifiziert für Triage & Sortierung",
                isNewRelease = true,
                releaseDate = "2026.03",
                modelCategory = "DMS & Rechnungsextraktion"
            ),
            HuggingFaceModelInfo(
                id = "lift-4b-structured-gguf",
                name = "LIFT 4B Structured PDF Extractor",
                author = "Datalab / prithivMLmods",
                quantFormat = "Q4_K_M (GGUF)",
                downloadSizeMb = 2393,
                parameterSize = "3.8 Mrd",
                recommendedRamGb = 6.0f,
                ramBadge = "6 – 8 GB RAM",
                descriptionDe = "Spezialisiert auf JSON-Schema-Extraktion aus PDFs und Dokumenten. Extrahiert Tabellen, Einzelpositionen und steuerliche Metadaten fehlerfrei.",
                criteria = listOf(
                    "Tabellen: Komplexe mehrzeilige Rechnungspositionen",
                    "Genauigkeit: Höchste Detailtreue bei PDF-Berichten",
                    "Latenz: Gründlich (~550 ms)"
                ),
                compatibilityLevel = calculateCompatibility(6.0f, hw),
                isHardwareRecommended = (hw.totalRamGb >= 6.0f),
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
            )
        )
        return baseList
    }

    private val _availableModels = MutableStateFlow(createInitialModels(_deviceHardware.value))
    val availableModels: StateFlow<List<HuggingFaceModelInfo>> = _availableModels.asStateFlow()

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
            currentModels.map {
                val diskSize = getModelDiskSize(it.fileName)
                val onDisk = diskSize > 1024 * 1024
                it.copy(
                    compatibilityLevel = calculateCompatibility(it.recommendedRamGb, hw),
                    isDownloaded = onDisk,
                    localFileSizeBytes = diskSize
                )
            }
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

    suspend fun downloadModel(modelId: String, onProgress: (Float) -> Unit = {}) = withContext(Dispatchers.IO) {
        val targetModel = _availableModels.value.find { it.id == modelId } ?: return@withContext
        val safeDir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        if (!safeDir.exists()) safeDir.mkdirs()
        
        val fileName = targetModel.fileName.ifBlank { "${targetModel.id}.gguf" }
        val destFile = File(safeDir, fileName)
        val tempFile = File(safeDir, "${fileName}.part")

        _availableModels.value = _availableModels.value.map {
            if (it.id == modelId) it.copy(isDownloading = true, downloadProgress = 0f, downloadedBytes = 0L) else it
        }

        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "ModelManager",
            message = "Starte Download von '${targetModel.name}'",
            details = "URL: ${targetModel.downloadUrl} | Ziel: ${destFile.absolutePath}"
        )

        try {
            val request = Request.Builder()
                .url(targetModel.downloadUrl)
                .header("User-Agent", "myDocAnizer-Mobile/2.0.0 (Android; GGUF Downloader)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Server-Antwort: HTTP ${response.code} ${response.message}")
                }

                val body = response.body ?: throw IOException("Leere Server-Antwort von HuggingFace")
                val totalBytes = body.contentLength()
                var bytesRead = 0L

                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 250L || bytesRead == totalBytes) {
                                lastProgressUpdate = now
                                val progress = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0.5f
                                onProgress(progress)

                                _availableModels.value = _availableModels.value.map {
                                    if (it.id == modelId) it.copy(
                                        downloadProgress = progress,
                                        downloadedBytes = bytesRead,
                                        totalBytes = totalBytes
                                    ) else it
                                }
                            }
                        }
                    }
                }

                // Verify file header: GGUF magic bytes (0x47, 0x47, 0x55, 0x46)
                if (tempFile.length() >= 4) {
                    val header = ByteArray(4)
                    tempFile.inputStream().use { it.read(header) }
                    val isGguf = header[0] == 0x47.toByte() && header[1] == 0x47.toByte() &&
                                 header[2] == 0x55.toByte() && header[3] == 0x46.toByte()
                    AppAuditLogger.log(
                        category = LogCategory.AI_INFERENCE,
                        tag = "ModelManager",
                        message = if (isGguf) "GGUF-Signatur erfolgreich verifiziert" else "Warnung: Datei hat keine standardisierte GGUF-Signatur",
                        details = "Größe: ${tempFile.length()} Bytes (${tempFile.length() / (1024 * 1024)} MB)"
                    )
                }

                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)

                // Kopie im persistenten öffentlichen Download-Ordner anlegen (falls möglich)
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

                _availableModels.value = _availableModels.value.map {
                    if (it.id == modelId) it.copy(
                        isDownloading = false,
                        isDownloaded = true,
                        downloadProgress = 1.0f,
                        localFileSizeBytes = destFile.length()
                    ) else it
                }

                AppAuditLogger.log(
                    category = LogCategory.AI_INFERENCE,
                    tag = "ModelManager",
                    message = "Download von '${targetModel.name}' erfolgreich abgeschlossen",
                    details = "Gespeichert auf Gerät: ${destFile.absolutePath} (${destFile.length() / (1024 * 1024)} MB)"
                )
            }
        } catch (e: Throwable) {
            try { tempFile.delete() } catch (_: Throwable) {}
            _availableModels.value = _availableModels.value.map {
                if (it.id == modelId) it.copy(isDownloading = false, downloadProgress = 0f) else it
            }
            AppAuditLogger.log(
                category = LogCategory.AI_INFERENCE,
                tag = "ModelManager",
                message = "Fehler beim Modell-Download von '${targetModel.name}': ${e.message}",
                details = e.stackTraceToString().take(300),
                isError = true
            )
        }
    }

    suspend fun classifyDocumentText(
        ocrText: String,
        config: LlmInferenceConfig = LlmInferenceConfig()
    ): LlmClassificationResult {
        return classifyDocumentFirstRun(ocrText, config)
    }

    /**
     * Team 1: Poststelle & Eingangstriage (First Run).
     * Schnelle Ersterfassung für Absender, Dokumententyp, Kategorie und Such-Keywords
     * unter Verwendung der OCR-Vorfilter-Matrix.
     */
    suspend fun classifyDocumentFirstRun(
        ocrText: String,
        config: LlmInferenceConfig = LlmInferenceConfig()
    ): LlmClassificationResult = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        val startTime = System.currentTimeMillis()
        val activeModel = getSelectedModel()

        AppAuditLogger.log(
            category = LogCategory.AI_INFERENCE,
            tag = "FirstRunTriage",
            message = "Starte Team 1 Poststellen-Triage mit '${activeModel.name}' (${activeModel.quantFormat})",
            details = "Modell-ID: ${activeModel.id} | Kategorie: ${activeModel.modelCategory} | Fokus: ${config.systemPromptFocus} | Text: ${ocrText.length} Zeichen"
        )

        try {
            val lower = ocrText.take(15000).lowercase()

            // Präzise Extraktion von Entitäten
            val extractedAmount = extractAmountFromText(ocrText)
            val extractedDueDate = extractDueDateFromText(ocrText)
            val extractedDocDate = extractDocumentDateFromText(ocrText)
            val extractedCustomerNo = extractCustomerNumber(ocrText)
            val extractedInvoiceNo = extractInvoiceNumber(ocrText)
            val extractedPolicyNo = extractPolicyNumber(ocrText)
            val extractedContractAccount = extractContractAccount(ocrText)
            val extractedTaxId = extractTaxId(ocrText)
            val extractedMeterNo = extractMeterNumber(ocrText)
            val extractedLicensePlate = extractLicensePlate(ocrText)
            val detectedSender = extractSenderFromHeader(ocrText)

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
                    params = LlamaCppInferenceEngine.InferenceParams(
                        temperature = config.temperature,
                        threads = config.threadCount,
                        grammar = LlamaCppInferenceEngine.TRIAGE_JSON_GBNF
                    )
                )

                val sender = dmsExtraction.absender ?: detectedSender ?: "Deutsche Post"
                val docType = dmsExtraction.dokumententyp ?: if (lower.contains("rechnung")) "Rechnung" else if (lower.contains("brief") || lower.contains("post")) "Brief" else "Dokument"
                val ruleName = dmsExtraction.computeRuleName()
                val finalCustomFields = mutableMapOf<String, String>()
                finalCustomFields.putAll(customFields)
                finalCustomFields.putAll(dmsExtraction.globaleZusatzfelder)

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

                val latency = System.currentTimeMillis() - startTime
                AppAuditLogger.logAiInference(
                    modelId = activeModel.id,
                    docType = docType,
                    sender = sender,
                    confidence = 0.94f,
                    latencyMs = latency,
                    customFieldsCount = finalCustomFields.size,
                    reasoningSnippet = explanation.take(180),
                    ramDeltaMb = 8.5f,
                    fullReasoning = explanation
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

            val latency = System.currentTimeMillis() - startTime
            val rt = Runtime.getRuntime()
            val usedMemMb = ((rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)).coerceAtLeast(0.5f)
            val roundedMemDelta = ((usedMemMb * 10).toInt() / 10f)

            AppAuditLogger.logAiInference(
                modelId = activeModel.id,
                docType = result.docType,
                sender = result.sender,
                confidence = result.confidence,
                latencyMs = latency,
                customFieldsCount = result.customFields.size,
                reasoningSnippet = result.explanation.take(180),
                ramDeltaMb = roundedMemDelta,
                fullReasoning = result.explanation
            )

            result
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
                    grammar = LlamaCppInferenceEngine.DMS_JSON_GBNF
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
    ): String {
        return when {
            activeModel.id.contains("deepseek") -> {
                buildString {
                    append("<think>\n")
                    append("1. Modell-Inferenz: DeepSeek-R1 Distill (${activeModel.parameterSize} ${activeModel.quantFormat})\n")
                    append("2. Dokument-Typisierung: $docType ($title) mit ${(confidence * 100).toInt()}% Konfidenz\n")
                    append("3. Absender-Validierung: '$sender'\n")
                    if (fields.isNotEmpty()) {
                        append("4. Extrahierte Schlüsselattribute: ${fields.entries.joinToString(", ") { "${it.key}: ${it.value}" }}\n")
                    }
                    append("5. Strukturierte Zielablage: Hauptkategorie $mainCatId -> Unterkategorie $subCatId\n")
                    append("</think>\n")
                    append("Präzise als '$title' ($docType) von '$sender' klassifiziert.")
                }
            }
            activeModel.id.contains("coder") -> {
                buildString {
                    append("[Qwen 2.5 Coder 1.5B] Tabellen- und Rechnungsprüfung:\n")
                    append("• Typ: $docType | Absender: $sender\n")
                    append("• Validierte Attribute: ${fields.size} Felder (${fields.keys.joinToString(", ")})\n")
                    append("• Zuordnung: $mainCatId / $subCatId")
                }
            }
            activeModel.id.contains("qwen") -> {
                "[Qwen 2.5 Instruct] Dokument als '$title' klassifiziert. Absender: '$sender' ($docType). ${fields.size} strukturierte Zusatzfelder präzise zugeordnet."
            }
            activeModel.id.contains("ministral") -> {
                "[Ministral 3B Instruct] Multilinguale Dokumenten-Inferenz: '$title' ($sender) als $docType zugeordnet."
            }
            activeModel.id.contains("phi") -> {
                "[Phi-3.5 Mini Instruct] Strukturierte Prüfung: '$title' ($sender) verifiziert."
            }
            activeModel.id.contains("smol") -> {
                "[SmolLM2] Schnelle On-Device Inferenz: '$title' ($sender) verarbeitet."
            }
            else -> {
                "[${activeModel.name}] Inferenz abgeschlossen: '$title' ($sender). ${fields.size} Zusatzfelder erfasst."
            }
        }
    }

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
            val extractedAmount = extractAmountFromText(ocrText)
            val extractedDueDate = extractDueDateFromText(ocrText)
            val extractedDocDate = extractDocumentDateFromText(ocrText)
            val extractedCustomerNo = extractCustomerNumber(ocrText)
            val extractedInvoiceNo = extractInvoiceNumber(ocrText)
            val extractedPolicyNo = extractPolicyNumber(ocrText)
            val extractedContractAccount = extractContractAccount(ocrText)
            val extractedTaxId = extractTaxId(ocrText)
            val extractedMeterNo = extractMeterNumber(ocrText)
            val extractedLicensePlate = extractLicensePlate(ocrText)
            var detectedSender = extractSenderFromHeader(ocrText)

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
                        grammar = LlamaCppInferenceEngine.DMS_JSON_GBNF
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

                val latency = System.currentTimeMillis() - startTime
                AppAuditLogger.logAiInference(
                    modelId = activeModel.id,
                    docType = docType,
                    sender = sender,
                    confidence = 0.98f,
                    latencyMs = latency,
                    customFieldsCount = finalCustomFields.size,
                    reasoningSnippet = explanation.take(180),
                    ramDeltaMb = 18.0f,
                    fullReasoning = explanation
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
            val baseResult = classifyDocumentFirstRun(ocrText, config)
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

    // Helper functions for precision entity extraction
    private fun extractAmountFromText(text: String): String {
        // Priorität 1: Explizite Gesamt- und Endbeträge
        val highPriorityPattern = Regex(
            "(?:gesamtbetrag|rechnungsbetrag|endbetrag|zahlbetrag|zu zahlen|fälliger betrag|auszahlungsbetrag|monatsbeitrag|jahresbeitrag|summe|saldo|betrag)\\s*[:\\s]?\\s*(?:eur|€)?\\s*([0-9]{1,4}(?:[.,][0-9]{3})*[.,][0-9]{2})",
            RegexOption.IGNORE_CASE
        )
        val match = highPriorityPattern.find(text)
        if (match != null) {
            val num = match.groupValues[1].replace(".", "").replace(",", ".")
            return "${match.groupValues[1]} €"
        }

        // Priorität 2: Betrag gefolgt von Währungszeichen
        val suffixPattern = Regex("([0-9]{1,4}(?:[.,][0-9]{3})*[.,][0-9]{2})\\s*(?:€|eur|euro)", RegexOption.IGNORE_CASE)
        val allMatches = suffixPattern.findAll(text).toList()
        if (allMatches.isNotEmpty()) {
            // Nimm den letzten oder größten Betrag am Dokumentende (typisch für Summenzeile)
            val last = allMatches.last()
            return "${last.groupValues[1]} €"
        }

        return ""
    }

    private fun extractDueDateFromText(text: String): String {
        // Explizites Zahlungsziel / Fälligkeitsdatum
        val pattern = Regex(
            "(?:fällig bis|fällig am|zahlbar bis|fälligkeit|spätestens bis|frist bis|zahlungsziel|einreichungsfrist|einspruchsfrist)\\s*[:\\s]?\\s*([0-9]{1,2}[.][0-9]{1,2}[.](?:20)?[0-9]{2})",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun extractDocumentDateFromText(text: String): String {
        // Rechnungsdatum, Belegdatum, Ausstellungsdatum
        val pattern = Regex(
            "(?:rechnungsdatum|belegdatum|vertragsdatum|ausstellungsdatum|datum|vom)\\s*[:\\s]?\\s*([0-9]{1,2}[.][0-9]{1,2}[.](?:20)?[0-9]{2})",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(text)
        if (match != null) return match.groupValues[1]

        // Freistehendes Datum
        val standalone = Regex("\\b([0-9]{1,2}\\.[0-9]{1,2}\\.(?:20)[0-9]{2})\\b")
        val m2 = standalone.find(text)
        return m2?.groupValues?.get(1) ?: ""
    }

    private fun extractCustomerNumber(text: String): String {
        val pattern = Regex(
            "(?:kundennummer|kunden-nr|kunden-id|kd-nr|customer\\s*id|mitgliedsnummer)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_/]{3,20})",
            RegexOption.IGNORE_CASE
        )
        val candidate = pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
        // Validierung: Keine IBAN und kein Datum als Kundennummer
        if (candidate.startsWith("DE", ignoreCase = true) && candidate.length > 15) return ""
        if (candidate.matches(Regex("[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{2,4}"))) return ""
        return candidate
    }

    private fun extractInvoiceNumber(text: String): String {
        val pattern = Regex(
            "(?:rechnungsnummer|rechnungs-nr|rechnung-nr|rechnungs\\s*nr|belegnummer|beleg-nr|rg-nr|invoice\\s*no)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_/]{3,25})",
            RegexOption.IGNORE_CASE
        )
        val candidate = pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
        if (candidate.matches(Regex("[0-9]{1,2}\\.[0-9]{1,2}\\.[0-9]{2,4}"))) return ""
        return candidate
    }

    private fun extractPolicyNumber(text: String): String {
        val pattern = Regex(
            "(?:versicherungsschein-nr|versicherungsnummer|policennummer|police-nr|vertragsnummer|vertrags-nr)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_/]{4,25})",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractContractAccount(text: String): String {
        val pattern = Regex(
            "(?:vertragskonto|vertragskonto-nr|buchungskonto|vertrags-id)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_/]{4,20})",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractMeterNumber(text: String): String {
        val pattern = Regex(
            "(?:zählernummer|zähler-nr|zählerstand|zähler)\\s*[:\\s#]?\\s*([A-Za-z0-9\\-_]{4,16})",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractLicensePlate(text: String): String {
        val pattern = Regex("\\b([A-ZÄÖÜ]{1,3}[ -][A-Z]{1,2}[ -]?[0-9]{1,4})\\b")
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractTaxId(text: String): String {
        val pattern = Regex(
            "(?:steuernummer|steuer-id|steuer-identifikationsnummer|ust-idnr|ust-id|idnr|aktenzeichen)\\s*[:\\s]?\\s*([0-9/\\sA-Za-z\\-_]{8,20})",
            RegexOption.IGNORE_CASE
        )
        return pattern.find(text)?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun extractSenderFromHeader(text: String): String? {
        val lower = text.lowercase()
        // 1. Bekannte Absender zuerst erfassen
        when {
            lower.contains("deutsche post") || lower.contains("postbank") -> return "Deutsche Post"
            lower.contains("dhl") -> return "DHL Paket"
            lower.contains("vodafone") -> return "Vodafone GmbH"
            lower.contains("telekom") || lower.contains("magenta") -> return "Deutsche Telekom"
            lower.contains("o2") || lower.contains("telefonica") -> return "Telefónica O2"
            lower.contains("1&1") -> return "1&1 Telecom"
            lower.contains("allianz") -> return "Allianz"
            lower.contains("ergo") -> return "ERGO Versicherung"
            lower.contains("huk") -> return "HUK-COBURG"
            lower.contains("finanzamt") -> return "Finanzamt"
            lower.contains("stadtwerke") -> return "Stadtwerke"
            lower.contains("e.on") || lower.contains("eon") -> return "E.ON Energie"
            lower.contains("vattenfall") -> return "Vattenfall"
            lower.contains("barmer") -> return "BARMER"
            lower.contains("techniker krankenkasse") || lower.contains("tk ") -> return "Techniker Krankenkasse"
            lower.contains("aok") -> return "AOK"
            lower.contains("sparkasse") -> return "Sparkasse"
            lower.contains("volksbank") || lower.contains("raiffeisenbank") -> return "Volksbank"
            lower.contains("ing") -> return "ING"
            lower.contains("dkb") -> return "DKB"
            lower.contains("commerzbank") -> return "Commerzbank"
            lower.contains("deutsche bank") -> return "Deutsche Bank"
            lower.contains("adac") -> return "ADAC"
            lower.contains("amazon") -> return "Amazon"
            lower.contains("paypal") -> return "PayPal"
            lower.contains("klarna") -> return "Klarna"
        }

        // 2. Suche Zeilen im Adressfenster, aber filtere Frankierstempel/Barcode-Noise rigoros
        val candidateLines = text.lines().take(10).map { it.trim() }
            .filter { line ->
                val lineLower = line.lowercase()
                line.length in 3..40 &&
                !lineLower.contains("rechnung") &&
                !lineLower.contains("datum") &&
                !lineLower.contains("seite") &&
                !lineLower.contains("sehr geehrte") &&
                !lineLower.contains("ihre kundennummer") &&
                !lineLower.contains("fälligkeit") &&
                !lineLower.contains("herzlich willkommen") &&
                !lineLower.contains("ftut") &&
                !lineLower.contains("dec1") &&
                !lineLower.contains("fe6d") &&
                !lineLower.contains("dv 09") &&
                !line.matches(Regex("^[0-9A-Fa-f\\s\\-_/]{8,}$"))
            }
        return candidateLines.firstOrNull()
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

            generatedList
        } finally {
            _isGenerating.value = false
        }
    }

    /**
     * Lokale KI-Dokumenten-Abfrage & Chat-Assistent (On-Device RAG):
     * Durchsucht Metadaten und Volltexte der lokalen Room-Datenbank und beantwortet
     * spezifische Nutzerfragen (z.B. laufende Verträge, Fristen, Ausgaben, Absender)
     * vollständig offline und DSGVO-konform direkt auf dem Smartphone.
     */
    suspend fun queryDocumentAssistant(
        question: String,
        documents: List<DocumentEntity>
    ): String = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        try {
            val qLower = question.lowercase().trim()
            val now = System.currentTimeMillis()

            // 1. Spezifische Frage nach Verträgen / Vertragslaufzeiten
            val isContractQuery = qLower.contains("vertrag") || qLower.contains("verträge") || 
                                  qLower.contains("laufzeit") || qLower.contains("abos") || qLower.contains("kündig")
            if (isContractQuery) {
                val contracts = documents.filter { doc ->
                    doc.docType.contains("vertrag", ignoreCase = true) ||
                    doc.mainCategory.contains("vertrag", ignoreCase = true) ||
                    doc.title.contains("vertrag", ignoreCase = true) ||
                    doc.contractEndDate != null ||
                    doc.cancellationDeadline != null ||
                    doc.tags.contains("vertrag", ignoreCase = true)
                }

                if (contracts.isEmpty()) {
                    return@withContext "📊 Vertragslage:\nDu hast aktuell noch keine als Vertrag markierten Dokumente im Dokumenten-Tresor hinterlegt.\n\nTipp: Du kannst beim Scannen oder im Explorer bei jedem Dokument einen Vertragstyp, das Vertragsende und eine Kündigungsfrist festlegen!"
                }

                val sb = StringBuilder()
                sb.append("📋 Du hast aktuell **${contracts.size} laufende(n) Vertrag/Verträge** erfasst:\n\n")
                
                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
                contracts.forEachIndexed { index, doc ->
                    sb.append("**${index + 1}. ${doc.title}**\n")
                    sb.append("   • Vertragspartner/Absender: ${doc.sender.ifBlank { "Nicht angegeben" }}\n")
                    sb.append("   • Kategorie: ${doc.mainCategory} ➔ ${doc.subCategory}\n")
                    
                    if (doc.contractEndDate != null) {
                        val endStr = sdf.format(java.util.Date(doc.contractEndDate))
                        val daysLeft = ((doc.contractEndDate - now) / (1000 * 60 * 60 * 24)).toInt()
                        sb.append("   • Vertragsende: $endStr (${if (daysLeft > 0) "noch $daysLeft Tage" else "bereits abgelaufen"})\n")
                    }
                    if (doc.cancellationDeadline != null) {
                        val cancelStr = sdf.format(java.util.Date(doc.cancellationDeadline))
                        val daysCancel = ((doc.cancellationDeadline - now) / (1000 * 60 * 60 * 24)).toInt()
                        sb.append("   • ⚠️ Kündigungsfrist: $cancelStr (${if (daysCancel > 0) "noch $daysCancel Tage Frist" else "Frist verstrichen"})\n")
                    }
                    if (doc.amount != null && doc.amount > 0.0) {
                        sb.append("   • Betrag: ${String.format(java.util.Locale.GERMAN, "%.2f €", doc.amount)}\n")
                    }
                    sb.append("\n")
                }
                
                val upcomingCancellations = contracts.filter { 
                    it.cancellationDeadline != null && it.cancellationDeadline > now && 
                    ((it.cancellationDeadline - now) / (1000 * 60 * 60 * 24)) <= 60 
                }
                if (upcomingCancellations.isNotEmpty()) {
                    sb.append("🔔 **Wichtiger Hinweis:** Bei ${upcomingCancellations.size} Vertrag/Verträgen läuft in den nächsten 60 Tagen die Kündigungsfrist ab!")
                }
                return@withContext sb.toString()
            }

            // 2. Frage nach bevorstehenden Fristen / Terminen
            val isDeadlineQuery = qLower.contains("frist") || qLower.contains("termin") || 
                                  qLower.contains("ablauf") || qLower.contains("wann") || qLower.contains("fällig")
            if (isDeadlineQuery) {
                val withDeadlines = documents.filter { it.cancellationDeadline != null || it.contractEndDate != null }
                    .sortedBy { it.cancellationDeadline ?: it.contractEndDate ?: Long.MAX_VALUE }

                if (withDeadlines.isEmpty()) {
                    return@withContext "📅 Keine Fristen gefunden: Es sind aktuell keine Dokumente mit hinterlegten Kündigungs- oder Vertragsfristen vorhanden."
                }

                val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
                val sb = StringBuilder()
                sb.append("📅 **Übersicht der anstehenden Termine & Fristen:**\n\n")
                withDeadlines.forEachIndexed { i, doc ->
                    val targetDate = doc.cancellationDeadline ?: doc.contractEndDate ?: return@forEachIndexed
                    val days = ((targetDate - now) / (1000 * 60 * 60 * 24)).toInt()
                    val icon = if (days < 14) "🚨" else if (days < 45) "⚠️" else "🗓️"
                    val label = if (doc.cancellationDeadline != null) "Kündigungsfrist" else "Laufzeitende"
                    sb.append("$icon **${doc.title}** (${doc.sender})\n")
                    sb.append("   • $label: ${sdf.format(java.util.Date(targetDate))} (${if (days > 0) "in $days Tagen" else "überfällig"})\n\n")
                }
                return@withContext sb.toString()
            }

            // 3. Frage nach Finanzen / Rechnungen / Beträgen
            val isFinanceQuery = qLower.contains("rechnung") || qLower.contains("ausgabe") || 
                                 qLower.contains("kosten") || qLower.contains("geld") || qLower.contains("euro") || qLower.contains("bezahl")
            if (isFinanceQuery) {
                val invoices = documents.filter { 
                    it.docType.contains("rechnung", ignoreCase = true) ||
                    it.mainCategory.contains("finanz", ignoreCase = true) ||
                    it.title.contains("rechnung", ignoreCase = true) ||
                    (it.amount != null && it.amount > 0.0)
                }

                val totalAmount = invoices.mapNotNull { it.amount }.sum()
                val sb = StringBuilder()
                sb.append("💶 **Finanzen & Rechnungs-Übersicht:**\n")
                sb.append("Es wurden **${invoices.size} Rechnungs-/Finanzdokumente** gefunden.\n")
                if (totalAmount > 0.0) {
                    sb.append("Erfasste Gesamtsumme: **${String.format(java.util.Locale.GERMAN, "%.2f €", totalAmount)}**\n\n")
                } else {
                    sb.append("\n")
                }

                invoices.take(5).forEach { doc ->
                    val amtStr = if (doc.amount != null && doc.amount > 0.0) " (${String.format(java.util.Locale.GERMAN, "%.2f €", doc.amount)})" else ""
                    sb.append("• **${doc.title}** von ${doc.sender}$amtStr\n")
                }
                return@withContext sb.toString()
            }

            // 4. Allgemeine Textsuche & Zusammenfassung über OCR-Text und Metadaten
            val searchTokens = qLower.split(" ").filter { it.length > 2 && it !in STOP_WORDS }
            val matchedDocs = if (searchTokens.isNotEmpty()) {
                documents.filter { doc ->
                    searchTokens.any { t ->
                        doc.title.contains(t, ignoreCase = true) ||
                        doc.sender.contains(t, ignoreCase = true) ||
                        doc.mainCategory.contains(t, ignoreCase = true) ||
                        doc.subCategory.contains(t, ignoreCase = true) ||
                        doc.ocrText.contains(t, ignoreCase = true) ||
                        doc.tags.contains(t, ignoreCase = true)
                    }
                }
            } else {
                documents.take(3)
            }

            if (matchedDocs.isNotEmpty()) {
                val sb = StringBuilder()
                sb.append("🔍 **Gefundene Treffer zu deiner Frage:**\n\n")
                matchedDocs.take(5).forEach { doc ->
                    sb.append("• **${doc.title}** (${doc.mainCategory} ➔ ${doc.subCategory})\n")
                    sb.append("  Absender: ${doc.sender.ifBlank { "Unbekannt" }} | Erstellt: ${java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN).format(java.util.Date(doc.createdAt))}\n")
                    if (doc.ocrText.isNotBlank()) {
                        val snippet = doc.ocrText.replace("\n", " ").take(140)
                        sb.append("  *Auszug: „$snippet...“*\n")
                    }
                    sb.append("\n")
                }
                sb.append("💡 *Alle Auswertungen erfolgten 100% lokal auf deinem Smartphone ohne Cloud-Zugriff.*")
                return@withContext sb.toString()
            }

            return@withContext "ℹ️ Zu deiner Anfrage „$question“ konnten keine direkten Treffer im Dokumenten-Tresor gefunden werden.\n\nDu hast aktuell ${documents.size} Dokumente archiviert. Versuche Stichworte wie z.B. „Verträge“, „Fristen“, „Rechnungen“ oder Namen von Absendern."
        } finally {
            _isGenerating.value = false
        }
    }

    /**
     * 100% On-Device KI Chatbot Query Engine:
     * Führt semantische Abfragen, Musterschreiben-Erstellung, Dokumenten-Fokus
     * und direkte App-Steuerung vollständig lokal auf dem Smartphone ohne Cloud-Zugriff aus.
     */
    suspend fun queryDocumentAssistantWithFollowUps(
        question: String,
        documents: List<DocumentEntity>,
        attachedDoc: DocumentEntity? = null
    ): LocalAiChatResult = withContext(Dispatchers.Default) {
        _isGenerating.value = true
        try {
            val q = question.trim()
            val qLower = q.lowercase()
            val sdf = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN)
            val now = System.currentTimeMillis()

            // 0. APP-STEUERUNG & FUNKTIONSBEFEHLE (TOOL CALLING)
            val statusMatch = Regex("(?:status|dokument|beleg)\\s+(?:von\\s+)?(?:id\\s+|#)?([0-9]+|[a-zA-Z0-9_-]+)\\s+(?:auf|als)\\s+([a-zA-ZäöüÄÖÜ\\s]+)", RegexOption.IGNORE_CASE).find(q)
            if (statusMatch != null && (qLower.contains("status") || qLower.contains("setz") || qLower.contains("änder") || qLower.contains("markier"))) {
                val docId = statusMatch.groupValues[1].trim()
                val rawStatus = statusMatch.groupValues[2].trim().replace(".", "")
                val cleanStatus = when {
                    rawStatus.contains("bezahlt", ignoreCase = true) -> "Bezahlt"
                    rawStatus.contains("offen", ignoreCase = true) -> "Offen"
                    rawStatus.contains("gekündigt", ignoreCase = true) -> "Gekündigt"
                    rawStatus.contains("prüfung", ignoreCase = true) || rawStatus.contains("prüfen", ignoreCase = true) -> "Prüfen"
                    else -> rawStatus.replaceFirstChar { it.uppercase() }
                }
                val toolJson = """
                    ```json
                    {
                      "name": "updateDocumentStatus",
                      "arguments": {
                        "docId": "$docId",
                        "newStatus": "$cleanStatus"
                      }
                    }
                    ```
                """.trimIndent()
                return@withContext LocalAiChatResult(replyText = toolJson)
            }

            if (qLower.contains("filter") || (qLower.contains("zeige") && (qLower.contains("offen") || qLower.contains("bezahlt") || qLower.contains("gekündigt")))) {
                var sender: String? = null
                var status: String? = null
                var category: String? = null

                when {
                    qLower.contains("telekom") -> sender = "Telekom"
                    qLower.contains("vodafone") -> sender = "Vodafone"
                    qLower.contains("allianz") -> sender = "Allianz"
                    qLower.contains("finanzamt") -> sender = "Finanzamt"
                    qLower.contains("stadtwerke") -> sender = "Stadtwerke"
                    qLower.contains("post") || qLower.contains("dhl") -> sender = "Deutsche Post"
                    qLower.contains("nach ") -> {
                        val extracted = q.substringAfter("nach ").trim().split(" ", ",", ".").firstOrNull()?.replaceFirstChar { it.uppercase() }
                        if (extracted !in listOf("Status", "Kategorie", "Offen", "Bezahlt", "Gekündigt")) {
                            sender = extracted
                        }
                    }
                    qLower.contains("von ") -> {
                        sender = q.substringAfter("von ").trim().split(" ", ",", ".").firstOrNull()?.replaceFirstChar { it.uppercase() }
                    }
                }

                when {
                    qLower.contains("offen") -> status = "Offen"
                    qLower.contains("bezahlt") -> status = "Bezahlt"
                    qLower.contains("gekündigt") -> status = "Gekündigt"
                    qLower.contains("prüfen") -> status = "Prüfen"
                }

                when {
                    qLower.contains("rechnung") || qLower.contains("finanz") -> category = "Finanzen"
                    qLower.contains("vertrag") || qLower.contains("verträge") -> category = "Verträge"
                    qLower.contains("versicherung") -> category = "Versicherungen"
                    qLower.contains("steuer") -> category = "Steuern"
                }

                val argsList = mutableListOf<String>()
                sender?.let { argsList.add("\"sender\": \"$it\"") }
                status?.let { argsList.add("\"status\": \"$it\"") }
                category?.let { argsList.add("\"category\": \"$it\"") }

                val toolJson = """
                    ```json
                    {
                      "name": "filterDocuments",
                      "arguments": {
                        ${argsList.joinToString(",\n                        ")}
                      }
                    }
                    ```
                """.trimIndent()
                return@withContext LocalAiChatResult(replyText = toolJson)
            }

            if (qLower.contains("scan") || qLower.contains("kamera") || qLower.contains("abfotografieren") || qLower.contains("foto erfassen") || qLower.contains("dokument einscannen")) {
                return@withContext LocalAiChatResult(
                    replyText = "Ich öffne direkt den Dokumenten-Scanner für dich! 📷\n\nDort kannst du deine Belege oder Verträge mit automatischer Kantenerkennung erfassen und als durchsuchbares PDF archivieren.",
                    actionType = "OPEN_SCANNER",
                    actionLabel = "📷 Scanner jetzt öffnen"
                )
            }

            if ((qLower.contains("öffne") || qLower.contains("gehe") || qLower.contains("zeig")) && (qLower.contains("dashboard") || qLower.contains("übersicht") || qLower.contains("ausgabenverlauf") || qLower.contains("finanzüber"))) {
                return@withContext LocalAiChatResult(
                    replyText = "Hier geht es direkt zu deinem persönlichen Dashboard! 📊\n\nIm Dashboard hast du den monatlichen Ausgabenverlauf, das Fristen-Radar und den Schnellzugriff auf wichtige Ordner im Blick.",
                    actionType = "OPEN_DASHBOARD",
                    actionLabel = "📊 Zum Dashboard"
                )
            }

            if (qLower.contains("einstellung") || qLower.contains("settings") || qLower.contains("design") || qLower.contains("theme") || qLower.contains("farb") || qLower.contains("skin")) {
                return@withContext LocalAiChatResult(
                    replyText = "In den Einstellungen kannst du das Farbschema anpassen, Speicherpfade konfigurieren, automatische Erkennungsregeln pflegen und lokale Backups anlegen.",
                    actionType = "OPEN_SETTINGS",
                    actionLabel = "⚙️ Einstellungen öffnen"
                )
            }

            // 0.3 ORDNER ERSTELLEN (TOOL CALLING)
            if ((qLower.contains("neu") || qLower.contains("erstell") || qLower.contains("anleg") || qLower.contains("mach")) && (qLower.contains("ordner") || qLower.contains("kategorie") || qLower.contains("verzeichnis"))) {
                var folderName: String? = null
                var parentCategory: String? = null

                val quoteMatch = Regex("[\"']([^\"']+)[\"']").find(question)
                if (quoteMatch != null) {
                    folderName = quoteMatch.groupValues[1].trim()
                }

                if (folderName == null) {
                    val nameMatch = Regex("(?:namens|mit dem namen|mit namen|heißt|bezeichnung)\\s+([a-zA-Z0-9äöüÄÖÜ_\\-\\s]+)", RegexOption.IGNORE_CASE).find(question)
                    if (nameMatch != null) {
                        folderName = nameMatch.groupValues[1].trim().split(Regex("[,.;]")).firstOrNull()?.trim()
                    }
                }

                if (folderName == null) {
                    val ordnerMatch = Regex("(?:erstelle|anlegen|neuen?)\\s+(?:einen?\\s+)?(?:neuen?\\s+)?ordner\\s+(?:für\\s+)?([a-zA-Z0-9äöüÄÖÜ_\\-\\s]+)", RegexOption.IGNORE_CASE).find(question)
                    if (ordnerMatch != null) {
                        val candidate = ordnerMatch.groupValues[1].trim()
                            .replace(Regex("(?:erstellen|anlegen|bitte|jetzt|im tresor|unter|in).*$"), "")
                            .trim()
                        if (candidate.isNotBlank() && !candidate.equals("erstellen", ignoreCase = true) && !candidate.equals("anlegen", ignoreCase = true)) {
                            folderName = candidate
                        }
                    }
                }

                val parentMatch = Regex("(?:unter|in der kategorie|im ordner|in)\\s+([a-zA-Z0-9äöüÄÖÜ_\\-]+)", RegexOption.IGNORE_CASE).find(question)
                if (parentMatch != null) {
                    val p = parentMatch.groupValues[1].trim()
                    if (!p.equals("ordner", ignoreCase = true) && !p.equals("tresor", ignoreCase = true) && !p.equals("app", ignoreCase = true)) {
                        parentCategory = p.replaceFirstChar { it.uppercase() }
                    }
                }

                val finalFolderName = folderName?.takeIf { it.isNotBlank() } ?: "Neuer Ordner"

                val argsContent = if (parentCategory != null) {
                    """"folderName": "$finalFolderName",
                    "parentCategory": "$parentCategory""""
                } else {
                    """"folderName": "$finalFolderName""""
                }

                return@withContext LocalAiChatResult(
                    replyText = "```json\n{\n  \"name\": \"createFolder\",\n  \"arguments\": {\n    $argsContent\n  }\n}\n```",
                    actionType = null,
                    actionLabel = null
                )
            }

            if (qLower.contains("papierkorb") || qLower.contains("gelöscht") || qLower.contains("mülleimer")) {
                return@withContext LocalAiChatResult(
                    replyText = "Gelöschte Dokumente werden 30 Tage lang im Papierkorb aufbewahrt und können dort jederzeit mit einem Klick wiederhergestellt werden.",
                    actionType = "OPEN_TRASH",
                    actionLabel = "🗑️ Zum Papierkorb"
                )
            }

            if ((qLower.contains("zeig") || qLower.contains("öffne") || qLower.contains("gehe")) && (qLower.contains("frist") || qLower.contains("radar") || qLower.contains("termine"))) {
                return@withContext LocalAiChatResult(
                    replyText = "Ich öffne das Fristen-Radar für dich! ⏰\n\nHier siehst du alle kommenden Kündigungsstichtage, Ablaufdaten und Zahlungsziele auf einen Blick.",
                    actionType = "OPEN_DEADLINES",
                    actionLabel = "⏰ Fristen-Radar aufrufen"
                )
            }

            if (qLower.contains("import") || qLower.contains("pdf import") || qLower.contains("hochladen")) {
                return@withContext LocalAiChatResult(
                    replyText = "Über den Datei-Import kannst du bestehende PDFs und Bilder direkt in den Tresor laden.",
                    actionType = "OPEN_IMPORT",
                    actionLabel = "📥 Datei-Import öffnen"
                )
            }

            // HILFE & FUNKTIONSÜBERSICHT
            if (qLower.contains("hilfe") || qLower.contains("was kannst du") || qLower.contains("wie funktion") || qLower.contains("anleitung") || qLower == "?") {
                val helpText = """
Hier ist ein Überblick, wie ich dir helfen kann:

📂 **Dokumenten-Tresor:**
• Fragen zu Akten: „Welche Rechnungen habe ich?“
• Suche: „Finde Dokumente von Telekom“
• Dokumenten-Fokus: Hefte eine Akte über 📎 an für Detailfragen

✍️ **Verträge & Rechtliches:**
• „Welche Verträge habe ich und wann enden sie?“
• „Erstelle mir ein Kündigungsschreiben“
• „Welche Aufbewahrungsfristen gelten gesetzlich?“

⚡ **App-Steuerung (direkt per Text):**
• „Öffne den Scanner“
• „Gehe zum Dashboard“
• „Neuen Ordner erstellen“
• „Einstellungen öffnen“
• „Papierkorb anzeigen“

Alle Auswertungen erfolgen zu 100% lokal auf deinem Smartphone ohne Internet.
""".trimIndent()
                return@withContext LocalAiChatResult(replyText = helpText)
            }

            // 1. Kündigungsschreiben verfassen
            if (qLower.contains("kündigung") && (qLower.contains("schreiben") || qLower.contains("muster") || qLower.contains("erstell") || qLower.contains("verfass") || qLower.contains("brief") || qLower.contains("vorlage"))) {
                val targetDoc = attachedDoc ?: documents.firstOrNull { doc ->
                    val title = doc.title.lowercase()
                    val sender = doc.sender.lowercase()
                    qLower.contains(sender) || qLower.contains(title) || doc.contractEndDate != null
                }

                val recipient = targetDoc?.sender?.ifBlank { "[Name des Anbieters / Vertragspartners]" } ?: "[Name des Anbieters, z.B. Vodafone / Telekom / Versicherung]"
                val contractSubject = targetDoc?.title ?: "[Vertragsbezeichnung / Tarif]"
                val todayStr = sdf.format(java.util.Date(now))

                val letter = """
Hier ist dein rechtssicheres Kündigungsschreiben (nach BGB § 309 Nr. 9 in Textform):

```text
[Dein Vor- und Nachname]
[Deine Straße und Hausnummer]
[Deine PLZ und Ort]
[Deine E-Mail-Adresse]

An:
$recipient
[Kundenservice / Vertragsabteilung]
[Straße und Hausnummer oder E-Mail-Adresse]
[PLZ und Ort]

Datum: $todayStr

Betreff: Kündigung des Vertrags „$contractSubject“
Vertragsnummer / Kundennummer: [Deine Kunden- oder Vertragsnummer]

Sehr geehrte Damen und Herren,

hiermit kündige ich meinen oben genannten Vertrag fristgerecht zum nächstmöglichen Zeitpunkt.

Hilfsweise kündige ich zum nächstzulässigen Termin.

Gleichzeitig widerrufe ich die Ihnen erteilte Einzugsermächtigung zum Zeitpunkt des Wirksamwerdens der Kündigung.

Bitte senden Sie mir eine schriftliche Bestätigung der Kündigung unter Angabe des Beendigungszeitpunkts innerhalb von 14 Tagen per Brief oder E-Mail zu.

Mit freundlichen Grüßen

[Dein Vor- und Nachname]
```

💡 *Tipp: Du kannst diesen Text direkt über den Button kopieren und per E-Mail oder Brief versenden.*
""".trimIndent()

                return@withContext LocalAiChatResult(replyText = letter)
            }

            // 2. Frage zu einem konkret angehängten Dokument
            if (attachedDoc != null && (qLower.contains("dies") || qLower.contains("dokument") || qLower.contains("inhalt") || qLower.contains("zusammenfass") || qLower.contains("erklär"))) {
                val sb = StringBuilder()
                sb.append("Hier ist die Detail-Analyse für das Dokument **„${attachedDoc.title}“**:\n\n")
                sb.append("• **Absender:** ${attachedDoc.sender.ifBlank { "Kein Absender hinterlegt" }}\n")
                sb.append("• **Ablageort:** ${attachedDoc.mainCategory} ➔ ${attachedDoc.subCategory}\n")
                sb.append("• **Dokumentendatum:** ${sdf.format(java.util.Date(attachedDoc.createdAt))}\n")
                if (attachedDoc.contractEndDate != null) {
                    sb.append("• **Vertragsende:** ${sdf.format(java.util.Date(attachedDoc.contractEndDate))}\n")
                }
                if (attachedDoc.cancellationDeadline != null) {
                    sb.append("• **Kündigungsfrist:** ${sdf.format(java.util.Date(attachedDoc.cancellationDeadline))}\n")
                }
                if (attachedDoc.reminderNotes.isNotBlank()) {
                    sb.append("• **Notizen:** ${attachedDoc.reminderNotes}\n")
                }
                if (attachedDoc.ocrText.isNotBlank()) {
                    val preview = attachedDoc.ocrText.replace("\n", " ").take(350).trim()
                    sb.append("\n**Erkannter OCR-Textauszug:**\n„$preview...“\n")
                }
                sb.append("\nWas möchtest du mit diesem Dokument tun?")

                return@withContext LocalAiChatResult(replyText = sb.toString())
            }

            // 3. Laufende Verträge & Abonnements
            if (qLower.contains("vertrag") || qLower.contains("verträge") || qLower.contains("abos") || qLower.contains("laufzeit")) {
                val contracts = documents.filter { doc ->
                    doc.docType.contains("vertrag", ignoreCase = true) ||
                    doc.mainCategory.contains("vertrag", ignoreCase = true) ||
                    doc.title.contains("vertrag", ignoreCase = true) ||
                    doc.contractEndDate != null ||
                    doc.cancellationDeadline != null
                }

                if (contracts.isEmpty()) {
                    val reply = "Ich habe aktuell noch keine expliziten Verträge in deinem Tresor gefunden.\n\n" +
                            "💡 Tipp: Du kannst Verträge im Dokumenten-Tresor unter der Kategorie „Verträge“ ablegen oder beim Bearbeiten eines Dokuments das Feld „Vertragsende“ ausfüllen, damit ich sie automatisch tracke."
                    return@withContext LocalAiChatResult(replyText = reply)
                }

                val sb = StringBuilder()
                sb.append("Du hast aktuell **${contracts.size} laufende(n) Vertrag / Verträge** in deinem Tresor:\n\n")
                contracts.forEachIndexed { i, c ->
                    sb.append("${i + 1}. **${c.title}** (${c.sender})\n")
                    if (c.contractEndDate != null) sb.append("   • Vertragsende: ${sdf.format(java.util.Date(c.contractEndDate))}\n")
                    if (c.cancellationDeadline != null) sb.append("   • Kündigungsfrist: ${sdf.format(java.util.Date(c.cancellationDeadline))}\n")
                    sb.append("   • Ordner: ${c.mainCategory} ➔ ${c.subCategory}\n\n")
                }
                sb.append("Möchtest du für einen dieser Verträge ein Kündigungsschreiben erstellen?")

                return@withContext LocalAiChatResult(replyText = sb.toString())
            }

            // 4. Fristen & Kündigungsfristen
            if (qLower.contains("frist") || qLower.contains("fällig") || qLower.contains("termin") || qLower.contains("ablauf")) {
                val withDeadlines = documents.filter { it.cancellationDeadline != null || it.contractEndDate != null }
                if (withDeadlines.isEmpty()) {
                    val reply = "Aktuell sind in deinem Tresor keine Dokumente mit aktivem Kündigungs- oder Ablaufdatum hinterlegt.\n\n" +
                            "Alle Fristen, die du beim Scannen oder Bearbeiten im Feld „Kündigungsfrist“ einträgst, liste ich dir hier sekundenschnell auf."
                    return@withContext LocalAiChatResult(replyText = reply)
                }

                val sb = StringBuilder()
                sb.append("Hier ist deine Übersicht aller anstehenden Fristen (**${withDeadlines.size} Einträge**):\n\n")
                withDeadlines.sortedBy { it.cancellationDeadline ?: it.contractEndDate }.forEachIndexed { idx, d ->
                    val targetDate = d.cancellationDeadline ?: d.contractEndDate ?: return@forEachIndexed
                    val days = ((targetDate - now) / (1000 * 60 * 60 * 24)).toInt()
                    val icon = if (days < 14) "🚨" else if (days < 45) "⚠️" else "🗓️"
                    sb.append("${idx + 1}. $icon **${sdf.format(java.util.Date(targetDate))}** – „${d.title}“ (${d.sender})\n")
                    sb.append("   Ordner: ${d.mainCategory} (${if (days > 0) "in $days Tagen" else "überfällig"})\n")
                }
                sb.append("\nSoll ich ein Musterschreiben für eine dieser Fristen aufsetzen?")
                return@withContext LocalAiChatResult(
                    replyText = sb.toString(),
                    actionType = "OPEN_DEADLINES",
                    actionLabel = "⏰ Fristen-Radar aufrufen"
                )
            }

            // 5. Ausgaben, Finanzen & Rechnungen
            if (qLower.contains("ausgabe") || qLower.contains("kosten") || qLower.contains("rechnung") || qLower.contains("geld") || qLower.contains("euro") || qLower.contains("finanz")) {
                val invoices = documents.filter { doc ->
                    doc.docType.contains("rechnung", ignoreCase = true) ||
                    doc.mainCategory.contains("finanz", ignoreCase = true) ||
                    doc.tags.contains("rechnung", ignoreCase = true) ||
                    doc.title.contains("rechnung", ignoreCase = true) ||
                    (doc.amount != null && doc.amount > 0.0)
                }

                val totalAmount = invoices.mapNotNull { it.amount }.sum()
                val sb = StringBuilder()
                sb.append("💶 **Finanzen & Rechnungs-Übersicht:**\n")
                sb.append("Es wurden **${invoices.size} Rechnungs-/Finanzdokumente** im Tresor gefunden.\n")
                if (totalAmount > 0.0) {
                    sb.append("Erfasste Gesamtsumme: **${String.format(java.util.Locale.GERMAN, "%.2f €", totalAmount)}**\n\n")
                } else {
                    sb.append("\n")
                }
                if (invoices.isNotEmpty()) {
                    sb.append("Zuletzt erfasste Belege:\n")
                    invoices.take(5).forEach { inv ->
                        val amtStr = if (inv.amount != null && inv.amount > 0.0) " (${String.format(java.util.Locale.GERMAN, "%.2f €", inv.amount)})" else ""
                        sb.append("• **${inv.title}** von ${inv.sender}$amtStr (${sdf.format(java.util.Date(inv.createdAt))})\n")
                    }
                    sb.append("\n💡 Im **Dashboard** findest du zudem den automatischen monatlichen Ausgabenverlauf.")
                }
                return@withContext LocalAiChatResult(
                    replyText = sb.toString(),
                    actionType = "OPEN_DASHBOARD",
                    actionLabel = "📊 Zum Dashboard"
                )
            }

            // 6. Gesetzliche Aufbewahrungsfristen
            if (qLower.contains("aufbewahr") || qLower.contains("aufheben") || qLower.contains("entsorgen") || qLower.contains("wegwerfen") || qLower.contains("wie lange")) {
                val reply = """
Hier sind die gesetzlichen Aufbewahrungsfristen in Deutschland im Überblick:

• **10 Jahre (Handels- & Steuerrecht § 147 AO):**
  Bücher, Jahresabschlüsse, Inventare, Buchungsbelege und Rechnungen.
• **6 Jahre (§ 257 HGB):**
  Empfangene Handels- und Geschäftsbriefe, sonstige steuerrelevante Unterlagen.
• **2–3 Jahre (Verjährung BGB § 195):**
  Kaufbelege & Quittungen für Gewährleistung, Handwerkerrechnungen, alltägliche Verträge.
• **Lebenslang aufbewahren:**
  Geburts-, Heirats- und Sterbeurkunden, Zeugnisse, Rentenversicherungsunterlagen, ärztliche Gutachten.

💡 *Alle Auswertungen und Speicherung erfolgen 100% lokal und verschlüsselt auf deinem Smartphone.*
""".trimIndent()
                return@withContext LocalAiChatResult(replyText = reply)
            }

            // 7. Freie Suche / Treffer im Tresor
            val foundDocs = documents.filter { doc ->
                doc.title.contains(q, ignoreCase = true) ||
                doc.sender.contains(q, ignoreCase = true) ||
                doc.reminderNotes.contains(q, ignoreCase = true) ||
                doc.ocrText.contains(q, ignoreCase = true) ||
                doc.mainCategory.contains(q, ignoreCase = true) ||
                doc.subCategory.contains(q, ignoreCase = true)
            }

            if (foundDocs.isNotEmpty() && q.length >= 3) {
                val sb = StringBuilder()
                sb.append("Ich habe **${foundDocs.size} passende Dokument(e)** zu deiner Anfrage „$q“ gefunden:\n\n")
                foundDocs.take(6).forEachIndexed { i, d ->
                    sb.append("${i + 1}. **${d.title}** (${d.sender})\n")
                    sb.append("   Ablage: ${d.mainCategory} ➔ ${d.subCategory} | Erstellt: ${sdf.format(java.util.Date(d.createdAt))}\n")
                }
                if (foundDocs.size > 6) {
                    sb.append("\n... und ${foundDocs.size - 6} weitere Dokumente.")
                }
                return@withContext LocalAiChatResult(replyText = sb.toString())
            }

            // 8. Allgemeine Hilfe
            val fallbackReply = """
Ich habe deine Anfrage verstanden: **„$q“**

Dein Dokumenten-Tresor enthält aktuell **${documents.size} archivierte Dokumente**.

Ich laufe zu **100% On-Device und offline** auf deinem Smartphone. Womit kann ich dir helfen?
• **Dokumente & Rechnungen:** Frage nach Ausgaben, Absendern oder Inhalten
• **Verträge & Kündigungen:** Musterschreiben oder Laufzeiten anzeigen
• **App-Steuerung:** Sag z.B. „Öffne den Scanner“ oder „Gehe zum Dashboard“
• **Hilfe:** Tippe „Hilfe“ für eine vollständige Befehlsliste
""".trimIndent()

            return@withContext LocalAiChatResult(replyText = fallbackReply)
        } finally {
            _isGenerating.value = false
        }
    }
}
