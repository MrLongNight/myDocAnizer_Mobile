package com.example.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LogLevel {
    NONE,
    STANDARD,
    DEVELOP
}

enum class LogCategory(val label: String, val icon: String) {
    AI_INFERENCE("Lokale KI", "🤖"),
    OCR("Texterkennung", "👁️"),
    RULE_ENGINE("Regel-Engine", "⚡"),
    STORAGE_DMS("Dokumenten-Tresor", "📁"),
    P2P_SYNC("WLAN Sync", "🔄"),
    SYSTEM("System & App", "⚙️"),
    ERROR("Fehler", "🚨")
}

data class ModelBenchmarkStats(
    val modelId: String,
    val modelName: String,
    val totalInferences: Int = 0,
    val avgLatencyMs: Long = 0L,
    val avgConfidence: Float = 0f,
    val avgFieldsExtracted: Float = 0f,
    val peakRamDeltaMb: Float = 0f,
    val lastUsedTimestamp: Long = System.currentTimeMillis(),
    val tokensPerSecond: Float = 0f,
    val avgTokensPerSecond: Float = 0f,
    val lastBenchmarkQualityScore: Int = 0,
    val lastBenchmarkQuantityScore: Int = 0,
    val lastBenchmarkOverallScore: Int = 0
)

data class AuditLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: LogCategory,
    val tag: String,
    val message: String,
    val details: String = "",
    val isError: Boolean = false,
    val modelId: String? = null,
    val latencyMs: Long? = null,
    val ramDeltaMb: Float? = null,
    val reasoningTrace: String? = null,
    val tokensPerSecond: Float = 0f
) {
    val formattedTime: String
        get() = SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS", Locale.GERMAN).format(Date(timestamp))

    fun toLogLine(): String {
        val errPrefix = if (isError) "[🚨 ERROR] " else ""
        val detailsStr = if (details.isNotBlank()) " | Details: $details" else ""
        val tpsStr = if (tokensPerSecond > 0f) ", Speed: ${tokensPerSecond} TPS" else ""
        val hwStr = if (ramDeltaMb != null && latencyMs != null) " [RAM-Δ: ${ramDeltaMb}MB, Latenz: ${latencyMs}ms$tpsStr]" else ""
        return "[$formattedTime] [${category.icon} ${category.name}] [$tag]$hwStr $errPrefix$message$detailsStr"
    }

    fun toFormattedBlock(): String {
        val statusIcon = if (isError) "🚨 FEHLER" else "${category.icon} ${category.label}"
        val sb = StringBuilder()
        sb.append("[$formattedTime] [$statusIcon] Modul: [$tag]\n")
        sb.append("   ↳ Status:  $message\n")
        if (details.isNotBlank()) {
            sb.append("   ↳ Details: $details\n")
        }
        if (!reasoningTrace.isNullOrBlank()) {
            sb.append("   ↳ KI-Gedankengang (Reasoning Trace):\n")
            reasoningTrace.lines().forEach { line ->
                sb.append("     │ $line\n")
            }
        }
        if (ramDeltaMb != null || latencyMs != null) {
            val tpsStr = if (tokensPerSecond > 0f) " | Speed: ${tokensPerSecond} TPS" else ""
            sb.append("   ↳ Hardware-Ressourcen: RAM-Verbrauch: ${ramDeltaMb ?: 0f} MB | Laufzeit: ${latencyMs ?: 0L} ms$tpsStr\n")
        }
        sb.append("--------------------------------------------------------------------------------\n")
        return sb.toString()
    }
}

/**
 * 100% lokales, datenschutzkonformes Ereignis- & KI-Diagnose-Protokoll.
 * Protokolliert alle wesentlichen App-Aktionen, OCR-Ergebnisse und KI-Inferenz-Details
 * direkt auf dem Gerät, damit der Nutzer jederzeit nachvollziehen kann, wie Dokumente
 * eingestuft wurden und welche Daten lokal verarbeitet werden.
 */
object AppAuditLogger {

    private const val MAX_MEMORY_LOGS = 300
    private const val MAX_LOG_FILE_SIZE = 1024 * 1024 * 2L // 2 MB

    private val _logs = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    val logs: StateFlow<List<AuditLogEntry>> = _logs.asStateFlow()

    private val _modelBenchmarks = MutableStateFlow<Map<String, ModelBenchmarkStats>>(emptyMap())
    val modelBenchmarks: StateFlow<Map<String, ModelBenchmarkStats>> = _modelBenchmarks.asStateFlow()

    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val logLock = Any()

    private var _currentLogLevel = LogLevel.STANDARD
    var currentLogLevel: LogLevel
        get() = _currentLogLevel
        set(value) {
            _currentLogLevel = value
        }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        synchronized(logLock) {
            if (appContext == null) {
                appContext = appCtx
                scope.launch {
                    loadInitialLogsFromFile(appCtx)
                }
                log(LogCategory.SYSTEM, "AppAuditLogger", "Diagnose-Logger erfolgreich initialisiert (100% lokales Audit-Protokoll)")
            }
        }
    }

    fun log(
        category: LogCategory,
        tag: String,
        message: String,
        details: String = "",
        isError: Boolean = false
    ) {
        if (_currentLogLevel == LogLevel.NONE) return

        val entry = AuditLogEntry(
            category = category,
            tag = tag,
            message = message,
            details = details,
            isError = isError
        )

        // Logcat für Android-Debugging
        try {
            if (isError) {
                Log.e("myDocAnizer", "[${category.name}] [$tag] $message $details")
            } else {
                Log.i("myDocAnizer", "[${category.name}] [$tag] $message $details")
            }
        } catch (_: Throwable) {
            // Safe fallback for JVM unit tests
        }

        // In-Memory Liste aktualisieren
        _logs.update { list ->
            val current = list.toMutableList()
            current.add(0, entry) // Neueste Einträge zuerst
            if (current.size > MAX_MEMORY_LOGS) {
                current.removeAt(current.size - 1)
            }
            current
        }

        // Im Hintergrund auf die rotierende Logdatei schreiben
        val ctx = appContext
        if (ctx != null) {
            scope.launch {
                appendToFile(ctx, entry)
            }
        }
    }

    fun logAiInference(
        modelId: String,
        docType: String,
        sender: String,
        confidence: Float,
        latencyMs: Long,
        customFieldsCount: Int,
        reasoningSnippet: String,
        ramDeltaMb: Float = 0f,
        fullReasoning: String = "",
        tokensPerSecond: Float = 0f
    ) {
        val entry = AuditLogEntry(
            category = LogCategory.AI_INFERENCE,
            tag = "InferenceEngine",
            message = "Dokument als '$docType' ($sender) eingestuft (${(confidence * 100).toInt()}% Konfidenz)",
            details = "Modell: $modelId | Latenz: ${latencyMs}ms | TPS: ${if (tokensPerSecond > 0f) tokensPerSecond else "-"} | RAM-Δ: ${ramDeltaMb}MB | Felder: $customFieldsCount | Auszug: $reasoningSnippet",
            modelId = modelId,
            latencyMs = latencyMs,
            ramDeltaMb = ramDeltaMb,
            reasoningTrace = if (fullReasoning.isNotBlank()) fullReasoning else reasoningSnippet,
            tokensPerSecond = tokensPerSecond
        )

        _logs.update { list ->
            val current = list.toMutableList()
            current.add(0, entry)
            if (current.size > MAX_MEMORY_LOGS) {
                current.removeAt(current.size - 1)
            }
            current
        }

        // Benchmark-Statistik pro Modell aktualisieren
        _modelBenchmarks.update { map ->
            val currentBenchmarks = map.toMutableMap()
            val prev = currentBenchmarks[modelId] ?: ModelBenchmarkStats(
                modelId = modelId,
                modelName = modelId
            )
            val newTotal = prev.totalInferences + 1
            val newAvgLatency = ((prev.avgLatencyMs * prev.totalInferences) + latencyMs) / newTotal
            val newAvgConf = ((prev.avgConfidence * prev.totalInferences) + confidence) / newTotal
            val newAvgFields = ((prev.avgFieldsExtracted * prev.totalInferences) + customFieldsCount) / newTotal
            val newAvgTps = if (tokensPerSecond > 0f) {
                if (prev.avgTokensPerSecond <= 0f) tokensPerSecond
                else ((prev.avgTokensPerSecond * prev.totalInferences) + tokensPerSecond) / newTotal
            } else prev.avgTokensPerSecond
            val peakRam = maxOf(prev.peakRamDeltaMb, ramDeltaMb)

            currentBenchmarks[modelId] = prev.copy(
                totalInferences = newTotal,
                avgLatencyMs = newAvgLatency,
                avgConfidence = newAvgConf,
                avgFieldsExtracted = newAvgFields,
                peakRamDeltaMb = peakRam,
                lastUsedTimestamp = System.currentTimeMillis(),
                tokensPerSecond = tokensPerSecond,
                avgTokensPerSecond = ((newAvgTps * 10).toInt() / 10f)
            )
            currentBenchmarks
        }

        val ctx = appContext
        if (ctx != null) {
            scope.launch {
                appendToFile(ctx, entry)
            }
        }
    }

    fun recordBenchmarkResult(
        modelId: String,
        modelName: String,
        qualityScore: Int,
        quantityScore: Int,
        overallScore: Int,
        tokensPerSecond: Float,
        latencyMs: Long
    ) {
        _modelBenchmarks.update { map ->
            val currentBenchmarks = map.toMutableMap()
            val prev = currentBenchmarks[modelId] ?: ModelBenchmarkStats(
                modelId = modelId,
                modelName = modelName
            )
            currentBenchmarks[modelId] = prev.copy(
                modelName = modelName,
                tokensPerSecond = tokensPerSecond,
                avgTokensPerSecond = if (prev.avgTokensPerSecond > 0f) ((prev.avgTokensPerSecond + tokensPerSecond) / 2f) else tokensPerSecond,
                lastBenchmarkQualityScore = qualityScore,
                lastBenchmarkQuantityScore = quantityScore,
                lastBenchmarkOverallScore = overallScore,
                avgLatencyMs = if (prev.avgLatencyMs > 0L) ((prev.avgLatencyMs + latencyMs) / 2L) else latencyMs,
                lastUsedTimestamp = System.currentTimeMillis()
            )
            currentBenchmarks
        }
        log(
            category = LogCategory.AI_INFERENCE,
            tag = "LlmBenchmark",
            message = "Dual-Benchmark für '$modelName' abgeschlossen: Gesamt $overallScore/100 (Qualität: $qualityScore%, Geschwindigkeit: $quantityScore%, $tokensPerSecond TPS, ${latencyMs}ms)"
        )
    }

    fun logOcr(charCount: Int, durationMs: Long, preview: String) {
        if (_currentLogLevel == LogLevel.NONE) return
        
        val detailsStr = if (_currentLogLevel == LogLevel.DEVELOP) {
            "Textprobe: \"${preview.replace("\n", " ").take(100)}\""
        } else {
            "Textinhalt ausgeblendet (Standard-Log-Modus)"
        }

        log(
            category = LogCategory.OCR,
            tag = "MLKitOcr",
            message = "$charCount Zeichen erfolgreich offline erkannt (${durationMs}ms)",
            details = detailsStr
        )
    }

    fun logRuleMatch(ruleName: String, keywordsMatched: List<String>) {
        log(
            category = LogCategory.RULE_ENGINE,
            tag = "DocRuleRepo",
            message = "Treffer durch deterministische Regel '$ruleName'",
            details = "Schlagwörter: ${keywordsMatched.joinToString(", ")}"
        )
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val stackTrace = try { throwable?.let { Log.getStackTraceString(it) } ?: "" } catch (_: Throwable) { throwable?.message ?: "" }
        log(
            category = LogCategory.ERROR,
            tag = tag,
            message = message,
            details = stackTrace.take(300),
            isError = true
        )
    }

    private fun getLogFile(context: Context): File {
        val dir = File(context.filesDir, "logs").apply { if (!exists()) mkdirs() }
        return File(dir, "myDocAnizer_audit.log")
    }

    private fun appendToFile(context: Context, entry: AuditLogEntry) {
        synchronized(logLock) {
            try {
                val file = getLogFile(context)
                if (file.exists() && file.length() > MAX_LOG_FILE_SIZE) {
                    val oldFile = File(file.parentFile, "myDocAnizer_audit.old.log")
                    if (oldFile.exists()) oldFile.delete()
                    file.renameTo(oldFile)
                }
                FileOutputStream(file, true).use { fos ->
                    fos.write((entry.toLogLine() + "\n").toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.w("AppAuditLogger", "Fehler beim Schreiben in Logdatei: ${e.message}")
            }
        }
    }

    private fun loadInitialLogsFromFile(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) {
                val lines = file.readLines(Charsets.UTF_8).takeLast(MAX_MEMORY_LOGS)
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss.SSS", Locale.GERMAN)
                val timestampRegex = Regex("\\[(\\d{2}\\.\\d{2}\\.\\d{4} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]")
                
                val parsedList = lines.mapNotNull { line ->
                    val match = timestampRegex.find(line)
                    val ts = if (match != null) try { sdf.parse(match.groupValues[1])?.time } catch(_: Exception) { null } else null
                    
                    AuditLogEntry(
                        timestamp = ts ?: System.currentTimeMillis(),
                        category = when {
                            line.contains("AI_INFERENCE]") -> LogCategory.AI_INFERENCE
                            line.contains("OCR]") -> LogCategory.OCR
                            line.contains("RULE_ENGINE]") -> LogCategory.RULE_ENGINE
                            line.contains("STORAGE_DMS]") -> LogCategory.STORAGE_DMS
                            line.contains("P2P_SYNC]") -> LogCategory.P2P_SYNC
                            line.contains("ERROR]") -> LogCategory.ERROR
                            else -> LogCategory.SYSTEM
                        },
                        tag = "Historie",
                        message = line.substringAfter("] ").take(160),
                        details = line,
                        isError = line.contains("[ERROR]")
                    )
                }.reversed()
                _logs.update { current ->
                    (current + parsedList).distinctBy { it.details + it.timestamp }.take(MAX_MEMORY_LOGS)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Erstellt eine saubere, exportierbare .log-Datei mit allen System- & KI-Logs
     */
    fun exportLogFile(context: Context): File {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val dateStr = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())
        val prettyDate = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMAN).format(Date())
        val exportFile = File(exportDir, "myDocAnizer_Diagnose_Protokoll_$dateStr.log")

        val currentLogs = _logs.value
        val totalCount = currentLogs.size
        val errorCount = currentLogs.count { it.isError }
        val aiCount = currentLogs.count { it.category == LogCategory.AI_INFERENCE }
        val ocrCount = currentLogs.count { it.category == LogCategory.OCR }
        val ruleCount = currentLogs.count { it.category == LogCategory.RULE_ENGINE }
        val dmsCount = currentLogs.count { it.category == LogCategory.STORAGE_DMS }
        val sysCount = currentLogs.count { it.category == LogCategory.SYSTEM }

        exportFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("================================================================================\n")
            writer.write("                 myDocAnizer-Mobile - DIAGNOSE & AUDIT-PROTOKOLL\n")
            writer.write("================================================================================\n")
            writer.write("Erstellt am:   $prettyDate\n")
            writer.write("Datenschutz:   100% On-Device Audit-Log (Vollständig offline)\n")
            writer.write("Gerät:         ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\n")
            writer.write("App-Version:   myDocAnizer-Mobile v${com.example.BuildConfig.VERSION_NAME}\n")
            writer.write("--------------------------------------------------------------------------------\n")
            writer.write("ÜBERSICHT & STATUS:\n")
            writer.write("  • Gesamt-Ereignisse: $totalCount\n")
            writer.write("  • 🚨 Fehler erfasst: $errorCount\n")
            writer.write("  • Aufschlüsselung:   🤖 KI: $aiCount | 👁️ OCR: $ocrCount | ⚡ Regeln: $ruleCount | 📁 Tresor: $dmsCount | ⚙️ System: $sysCount\n")
            writer.write("================================================================================\n")
            writer.write("                           EREIGNISSE (CHRONOLOGISCH)\n")
            writer.write("================================================================================\n\n")

            currentLogs.reversed().forEach { entry ->
                writer.write(entry.toFormattedBlock())
            }
        }
        return exportFile
    }

    fun clearLogs(context: Context) {
        synchronized(logLock) {
            _logs.update { emptyList() }
            try {
                val file = getLogFile(context)
                if (file.exists()) file.delete()
                val oldFile = File(file.parentFile, "myDocAnizer_audit.old.log")
                if (oldFile.exists()) oldFile.delete()
            } catch (_: Exception) {}
            log(LogCategory.SYSTEM, "AppAuditLogger", "Diagnose-Logdatei wurde vom Nutzer geleert.")
        }
    }
}
