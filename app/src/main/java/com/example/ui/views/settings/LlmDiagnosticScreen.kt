package com.example.ui.views.settings

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.service.DocRuleRepository
import com.example.service.LlamaCppInferenceEngine
import com.example.service.LlmService
import com.example.ui.DocAnizerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream

private const val DOC_TIMEOUT_MS = 90_000L

data class LlmDiagnosticRow(
    val name: String, val elapsedMs: Long, val mode: String, val finishReason: String,
    val tokens: Int, val javaBefore: Long, val javaAfter: Long, val nativeBefore: Long,
    val nativeAfter: Long, val status: String, val detail: String = "", val stacktrace: String = ""
)

private data class HeapSample(val java: Long, val native: Long) {
    companion object {
        fun capture(): HeapSample {
            val runtime = Runtime.getRuntime()
            return HeapSample((runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L), Debug.getNativeHeapAllocatedSize().coerceAtLeast(0L))
        }
    }
}

class LlmDiagnosticRunner(private val context: Context, private val service: LlmService, private val rules: DocRuleRepository) {
    private val _rows = MutableStateFlow<List<LlmDiagnosticRow>>(emptyList())
    val rows = _rows.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()
    private val _status = MutableStateFlow("Bereit")
    val status = _status.asStateFlow()
    private val _activeStep = MutableStateFlow("—")
    val activeStep = _activeStep.asStateFlow()
    private val _activeElapsedMs = MutableStateFlow(0L)
    val activeElapsedMs = _activeElapsedMs.asStateFlow()
    private val _nativeProgress = MutableStateFlow(-1)
    val nativeProgress = _nativeProgress.asStateFlow()
    private val _nativeStage = MutableStateFlow("idle_or_unavailable")
    val nativeStage = _nativeStage.asStateFlow()
    private val _currentDocumentIndex = MutableStateFlow(0)
    val currentDocumentIndex = _currentDocumentIndex.asStateFlow()
    private val _totalDocuments = MutableStateFlow(0)
    val totalDocuments = _totalDocuments.asStateFlow()
    private val _currentDocumentName = MutableStateFlow("")
    val currentDocumentName = _currentDocumentName.asStateFlow()
    private val _pipelineLabel = MutableStateFlow("")
    val pipelineLabel = _pipelineLabel.asStateFlow()
    private val _completedSteps = MutableStateFlow(0)
    val completedSteps = _completedSteps.asStateFlow()
    private val _totalSteps = MutableStateFlow(0)
    val totalSteps = _totalSteps.asStateFlow()
    @Volatile private var activeStartedAtMs = 0L
    private val logFile = File(context.cacheDir, "llm-diagnostic-live.txt")
    private val logLock = Any()
    private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var heartbeatJob: Job? = null
    private val engine get() = service.llamaCppEngine
    private val selectedModel get() = service.getSelectedModel()
    private val modelFile get() = service.getModelFile(selectedModel.id)

    private fun setStatus(value: String) {
        _status.value = value
        logScope.launch { persistLiveReport() }
    }

    private fun markStep(name: String) {
        _activeStep.value = name
        activeStartedAtMs = SystemClock.elapsedRealtime()
        _activeElapsedMs.value = 0L
        logScope.launch { persistLiveReport() }
    }

    private fun startTracking() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = logScope.launch {
            while (_running.value) {
                _activeElapsedMs.value = if (activeStartedAtMs > 0L) {
                    (SystemClock.elapsedRealtime() - activeStartedAtMs).coerceAtLeast(0L)
                } else 0L
                _nativeProgress.value = engine.inferenceProgress.value
                _nativeStage.value = engine.nativeInferenceStageName()
                persistLiveReport()
                delay(500L)
            }
            _nativeProgress.value = -1
            _nativeStage.value = "idle_or_unavailable"
            persistLiveReport()
        }
    }

    private fun stopTracking() {
        _activeStep.value = "—"
        _activeElapsedMs.value = 0L
        activeStartedAtMs = 0L
        _running.value = false
        logScope.launch { persistLiveReport() }
    }

    private fun persistLiveReport() {
        runCatching {
            val content = report()
            synchronized(logLock) {
                logFile.writeText(content, Charsets.UTF_8)
            }
        }.onFailure {
            Log.e("LlmDiagnostic", "Live-Protokoll konnte nicht aktualisiert werden: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    fun snapshot(): String {
        val file = modelFile
        val signature = runCatching { FileInputStream(file).use { input -> val b = ByteArray(4); if (input.read(b) == 4) String(b, Charsets.US_ASCII) else "nicht lesbar" } }.getOrDefault("nicht lesbar")
        val heap = HeapSample.capture()
        return listOf(
            "Modell: " + selectedModel.name + " (" + selectedModel.id + ")",
            "Pfad: " + file.absolutePath,
            "Größe: " + file.length() + " Bytes",
            "GGUF-Signatur: " + signature,
            "ABI: " + android.os.Build.SUPPORTED_ABIS.joinToString(),
            "Java-Heap: " + heap.java + " Bytes",
            "Native-Heap: " + heap.native + " Bytes",
            "Engine geladen: " + engine.nativeEngineLoaded(),
            "Geladener Modellpfad: " + (engine.nativeLoadedModelPath() ?: if (engine.inferenceProgress.value in 5..99) "Inferenz läuft (Abfrage nicht blockierend)" else "nicht geladen/unbekannt"),
            "n_ctx: " + engine.nativeContextSize(),
            "Threads (Default): " + Runtime.getRuntime().availableProcessors().coerceIn(2, 4),
            "Letzter Modus: " + engine.lastInferenceMode,
            "finish_reason: " + (engine.lastFinishReason ?: "—"),
            "Live-Log: " + logFile.absolutePath
        ).joinToString("\n")
    }

    suspend fun testPrompt() {
        if (_running.value) return
        _running.value = true
        _completedSteps.value = 0
        _totalSteps.value = 1
        _totalDocuments.value = 0
        _currentDocumentIndex.value = 0
        _currentDocumentName.value = ""
        _pipelineLabel.value = "Modell-Kurztest"
        markStep("MODELL-KURZTEST")
        setStatus("Modell-Kurztest läuft")
        startTracking()
        try {
            val response = withTimeout(15_000L) {
                engine.executeChatInferenceWithTools(
                    modelFile = modelFile,
                    userMessage = "Antworte nur: OK",
                    systemPrompt = "Antworte kurz.",
                    params = LlamaCppInferenceEngine.InferenceParams(
                        maxTokens = 8, threads = 2, temperature = 0f, timeoutMs = 12_000L
                    )
                )
            }
            val passed = engine.lastInferenceMode == LlamaCppInferenceEngine.InferenceMode.LOCAL_LLM &&
                response.trim().contains("OK", ignoreCase = true)
            if (passed) _completedSteps.value = 1
            setStatus(
                when {
                    passed -> "Modell-Kurztest erfolgreich"
                    engine.lastFinishReason == "timeout" -> "Kurztest fehlgeschlagen: Modell hat nicht rechtzeitig geantwortet"
                    else -> "Kurztest fehlgeschlagen: keine gültige lokale Modellantwort"
                }
            )
        } catch (cancelled: CancellationException) {
            setStatus("Kurztest gestoppt")
            throw cancelled
        } catch (_: Throwable) {
            setStatus("Kurztest fehlgeschlagen")
        } finally {
            stopTracking()
        }
    }

    fun unload() {
        engine.unloadNativeEngine()
        setStatus("Native Engine entladen")
    }

    suspend fun runSequential() {
        if (_running.value) return
        _running.value = true
        _rows.value = emptyList()
        _completedSteps.value = 0
        _currentDocumentIndex.value = 0
        _currentDocumentName.value = ""
        _pipelineLabel.value = "Vorbereitung"
        setStatus("OCR-Test wird vorbereitet")
        startTracking()
        var stoppedEarly = false
        try {
            val names = assets()
            if (names.isEmpty()) {
                setStatus("Keine OCR-Testdokumente gefunden")
                return
            }
            _totalDocuments.value = names.size
            _totalSteps.value = names.size * 2
            for ((index, name) in names.withIndex()) {
                _currentDocumentIndex.value = index + 1
                _currentDocumentName.value = displayDocumentName(name)
                _pipelineLabel.value = "Ersterkennung"
                setStatus("OCR-Test läuft")
                var matchCount = 0
                val firstRow = measure("$name / First-Run", read(name)) { text ->
                    matchCount = rules.matchingRules(text).size
                    service.classifyDocumentFirstRun(text)
                    "Regeltreffer=" + matchCount +
                        "; mode=" + engine.lastInferenceMode +
                        "; finish_reason=" + (engine.lastFinishReason ?: "unknown") +
                        "; tokens=" + (engine.lastInferenceMetrics?.generatedTokens ?: 0) +
                        "; Pipeline=First-Run; Vorschlag nicht gespeichert"
                }
                _rows.value = _rows.value + firstRow
                _completedSteps.value = _completedSteps.value + 1
                logScope.launch { persistLiveReport() }
                if (firstRow.status != "OK") {
                    stoppedEarly = true
                    setStatus(
                        when (firstRow.status) {
                            "TIMEOUT" -> "Automatisch gestoppt: Modell-Kurzlauf hat das Zeitlimit erreicht"
                            "FALLBACK" -> "Automatisch gestoppt: keine native Modellantwort"
                            else -> "Automatisch gestoppt: Fehler bei der Ersterkennung"
                        }
                    )
                    break
                }

                _pipelineLabel.value = "Tiefenanalyse"
                val secondRow = measure("$name / Second-Run", read(name)) { text ->
                    service.classifyDocumentSecondRun(text)
                    "mode=" + engine.lastInferenceMode +
                        "; finish_reason=" + (engine.lastFinishReason ?: "unknown") +
                        "; tokens=" + (engine.lastInferenceMetrics?.generatedTokens ?: 0) +
                        "; Pipeline=Second-Run; Vorschlag nicht gespeichert"
                }
                _rows.value = _rows.value + secondRow
                _completedSteps.value = _completedSteps.value + 1
                logScope.launch { persistLiveReport() }
                if (secondRow.status != "OK") {
                    stoppedEarly = true
                    setStatus(
                        when (secondRow.status) {
                            "TIMEOUT" -> "Automatisch gestoppt: Tiefenanalyse hat das Zeitlimit erreicht"
                            "FALLBACK" -> "Automatisch gestoppt: keine native Modellantwort in der Tiefenanalyse"
                            else -> "Automatisch gestoppt: Fehler bei der Tiefenanalyse"
                        }
                    )
                    break
                }
            }
            if (!stoppedEarly) setStatus("OCR-Test abgeschlossen")
        } catch (cancelled: CancellationException) {
            setStatus("Test durch Benutzer gestoppt")
            throw cancelled
        } catch (_: Throwable) {
            _rows.value = _rows.value + errorRow("OCR-Test", IllegalStateException("Diagnoselauf fehlgeschlagen"))
            setStatus("OCR-Test fehlgeschlagen")
        } finally {
            stopTracking()
        }
    }

    private fun displayDocumentName(fileName: String): String =
        fileName.substringAfter('_', fileName).substringBeforeLast('.').replace('_', ' ')
    
    suspend fun runParallel() {
        if (_running.value) return
        _running.value = true
        setStatus("Lauf 2/3: Chat + Analyse + Re-Run (nacheinander, eine native Engine)")
        startTracking()
        try {
            val text = read(assets().first())
            val file = modelFile
            // One process-wide llama.cpp engine and shared last-run metrics cannot provide
            // three independent native inferences at once. Running these as async siblings
            // only queues them behind the native mutex and races the shared diagnostics state.
            // Exercise all three entry points sequentially to avoid CPU saturation and false results.
            val result = listOf(
                measure("CHAT-SEQUENZIELL", text) { value ->
                    engine.executeChatInferenceWithTools(file, "Fasse kurz zusammen: " + value.take(500), params = LlamaCppInferenceEngine.InferenceParams(maxTokens = 96, threads = 2))
                    "Chat-Aufruf"
                },
                measure("ANALYSE-SEQUENZIELL", text) { value ->
                    service.classifyDocumentFirstRun(value)
                    "First-Run-Analyse"
                },
                measure("RE-RUN-SEQUENZIELL", text) { value ->
                    service.classifyDocumentSecondRun(value)
                    "Second-Run-Analyse"
                }
            )
            _rows.value = _rows.value + result
            setStatus("Sequenztest abgeschlossen; alle drei Aufrufwege separat gemessen")
        } catch (cancelled: CancellationException) {
            setStatus("Sequenztest abgebrochen")
            throw cancelled
        } catch (error: Throwable) {
            _rows.value = _rows.value + errorRow("SEQUENZTEST", error)
            setStatus("Sequenztest mit Fehler beendet")
        } finally { stopTracking() }
    }

    suspend fun runAbortTest() {
        if (_running.value) return
        _running.value = true
        setStatus("Lauf 3/3: Langtext mit 1,5-s-Abbruchfenster")
        startTracking()
        try {
            val name = assets().firstOrNull { it.contains("langer_ocrtext") } ?: assets().last()
            val before = HeapSample.capture()
            val start = SystemClock.elapsedRealtime()
            coroutineScope {
                val cancelJob = launch {
                    delay(1_500L)
                    // A one-shot signal can be swallowed if JNI resets the cancellation
                    // flag while initializing. Repeat until the inference call returns.
                    while (isActive) {
                        engine.requestNativeCancellation()
                        delay(100L)
                    }
                }
                try {
                    service.classifyDocumentSecondRun(read(name))
                } finally {
                    cancelJob.cancel()
                }
            }
            val wasCancelled = engine.lastFinishReason == "cancelled"
            val resultRow = row(
                name, start, before,
                if (wasCancelled) "ABGEBROCHEN" else "OK",
                if (wasCancelled) "Native Inferenz hat das wiederholte Abbruchsignal verarbeitet"
                else "Analyse vor erfolgreichem Abbruchnachweis abgeschlossen; finish_reason=" + (engine.lastFinishReason ?: "unknown")
            )
            _rows.value = _rows.value + resultRow
            setStatus(if (wasCancelled) "Abbruchtest erfolgreich: Native Inferenz abgebrochen" else "Abbruchtest beendet, aber Abbruch wurde nicht bestätigt")
        } catch (cancelled: CancellationException) {
            setStatus("Abbruchtest durch Benutzer gestoppt")
            throw cancelled
        } catch (error: Throwable) {
            _rows.value = _rows.value + errorRow("ABBRUCHTEST", error)
            setStatus("ABBRUCHTEST FEHLGESCHLAGEN: " + error.javaClass.simpleName + ": " + (error.message ?: "keine Fehlermeldung"))
        } finally { stopTracking() }
    }

    fun cancelMessage() {
        engine.requestNativeCancellation()
        setStatus("Abbruch wird ausgeführt")
    }

    fun noteExportResult(message: String) {
        _status.value = message
        logScope.launch { persistLiveReport() }
    }

    fun report(): String = buildString {
        appendLine("LLM-Diagnose")
        appendLine("Berichtsversion=3")
        appendLine("Exportzeitpunkt=" + java.time.Instant.now().toString())
        appendLine("Hinweis=Der Bericht darf auch während eines laufenden oder festhängenden Tests exportiert werden.")
        appendLine("Zeitpunkt (Epoch-ms)=" + System.currentTimeMillis())
        appendLine("Status=" + _status.value)
        appendLine("Aktives Dokument=" + _currentDocumentName.value)
        appendLine("Aktive Pipeline=" + _pipelineLabel.value)
        appendLine("Läuft=" + _running.value)
        appendLine("Aktiver Schritt=" + _activeStep.value)
        appendLine("Aktive Schrittzeit=" + if (activeStartedAtMs > 0L) (SystemClock.elapsedRealtime() - activeStartedAtMs).toString() + " ms" else "—")
        appendLine("Native Inferenzfortschritt=" + engine.inferenceProgress.value)
        appendLine("Native Inferenzphase (aktuell)=" + engine.nativeInferenceStageName())
        appendLine("Letzte native Phase=" + (engine.lastNativePhase ?: "—"))
        appendLine("Letztes natives finish_reason=" + (engine.lastNativeFinishReason ?: "—"))
        appendLine("Native Tokens (auch bei Timeout)=" + engine.lastNativeGeneratedTokens)
        appendLine("Prompt-Tokens gesamt=" + engine.lastNativePromptTokens)
        appendLine("Prompt-Tokens verarbeitet=" + engine.lastNativePromptTokensEvaluated)
        appendLine("Native Prompt-Auswertung=" + engine.lastNativePromptEvalTimeMs + " ms")
        appendLine("Native Generierung=" + engine.lastNativeGenerationTimeMs + " ms")
        appendLine("Native Aufrufdauer=" + engine.lastNativeElapsedMs + " ms")
        appendLine("Native Phase bei Timeout=" + (engine.lastNativeStageAtTimeout ?: "—"))
        appendLine("Fortschritt bei Timeout=" + engine.lastNativeProgressAtTimeout)
        appendLine("Timeout-Budget=" + engine.lastNativeTimeoutBudgetMs + " ms")
        appendLine("Live-Log-Datei=" + logFile.absolutePath)
        appendLine(snapshot())
        appendLine()
        appendLine("OK=" + _rows.value.count { it.status == "OK" })
        appendLine("Fallback=" + _rows.value.count { it.status == "FALLBACK" })
        appendLine("Timeout=" + _rows.value.count { it.status == "TIMEOUT" })
        appendLine("Fehler=" + _rows.value.count { it.status == "FEHLER" })
        appendLine("Abgebrochen=" + _rows.value.count { it.status == "ABGEBROCHEN" })
        appendLine("Längste Wartezeit=" + (_rows.value.maxOfOrNull { it.elapsedMs } ?: 0L) + " ms")
        if (_rows.value.isNotEmpty()) {
            val javaTrend = _rows.value.last().javaAfter - _rows.value.first().javaBefore
            val nativeTrend = _rows.value.last().nativeAfter - _rows.value.first().nativeBefore
            appendLine("Java-Heap-Trend=" + javaTrend + " Bytes")
            appendLine("Native-Heap-Trend=" + nativeTrend + " Bytes")
            appendLine("Heap-Wachstum > 150 MB=" + ((javaTrend + nativeTrend) > 150L * 1024L * 1024L))
        }
        _rows.value.forEach { r ->
            appendLine("[" + r.status + "] " + r.name + " | " + r.elapsedMs + " ms | " + r.mode + " | finish_reason=" + r.finishReason + " | Tokens=" + r.tokens)
            appendLine("Java-Heap: " + r.javaBefore + " -> " + r.javaAfter + "; Native-Heap: " + r.nativeBefore + " -> " + r.nativeAfter)
            if (r.detail.isNotBlank()) appendLine("Details: " + r.detail)
            if (r.stacktrace.isNotBlank()) appendLine("Stacktrace:\n" + r.stacktrace)
        }
    }

    private suspend fun measure(name: String, inputText: String? = null, action: suspend (String) -> String): LlmDiagnosticRow = withContext(Dispatchers.IO) {
        markStep(name)
        val before = HeapSample.capture()
        persistLiveReport()
        val start = SystemClock.elapsedRealtime()
        var detail = ""
        var status = "OK"
        var stack = ""
        try {
            withTimeout(DOC_TIMEOUT_MS) { detail = action(inputText ?: read(name)) }
            val inferenceTimedOut = engine.lastFinishReason == "timeout" ||
                detail.contains("firstFinish=timeout") ||
                detail.contains("secondFinish=timeout")
            status = when {
                inferenceTimedOut -> "TIMEOUT"
                engine.lastInferenceMode == LlamaCppInferenceEngine.InferenceMode.RULE_FALLBACK ||
                    detail.contains("pipelineFallback=true") -> "FALLBACK"
                else -> "OK"
            }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            status = "TIMEOUT"; detail = timeout.message ?: "Timeout"; stack = Log.getStackTraceString(timeout)
        } catch (cancelled: CancellationException) {
            // The heartbeat log preserves this active step even if cancellation interrupts
            // native inference before a normal result row can be returned.
            setStatus("Schritt abgebrochen: " + name)
            throw cancelled
        } catch (error: Throwable) {
            status = "FEHLER"; detail = error.message ?: error.javaClass.simpleName; stack = Log.getStackTraceString(error)
        }
        detail = listOf(detail, engine.lastNativeDiagnosticSummary()).filter { it.isNotBlank() }.joinToString("; ")
        row(name, start, before, status, detail, stack)
    }

    private fun row(name: String, start: Long, before: HeapSample, status: String, detail: String, stack: String = ""): LlmDiagnosticRow {
        val after = HeapSample.capture()
        val mode = if (engine.lastInferenceMode == LlamaCppInferenceEngine.InferenceMode.LOCAL_LLM) "LLM" else "Fallback"
        val finishReason = when (status) { "FEHLER" -> "error"; "TIMEOUT" -> "timeout"; "ABGEBROCHEN" -> "cancelled"; else -> engine.lastFinishReason ?: if (status == "FALLBACK") "fallback" else "unknown" }
        val tokenCount = engine.lastInferenceMetrics?.generatedTokens ?: engine.lastNativeGeneratedTokens
        return LlmDiagnosticRow(name, SystemClock.elapsedRealtime() - start, mode, finishReason, tokenCount, before.java, after.java, before.native, after.native, status, detail, stack)
    }

    private fun errorRow(name: String, error: Throwable): LlmDiagnosticRow {
        val h = HeapSample.capture()
        return LlmDiagnosticRow(name, 0, "Fallback", "error", 0, h.java, h.java, h.native, h.native, "FEHLER", error.message ?: "Fehler", Log.getStackTraceString(error))
    }

    private fun assets(): List<String> = context.assets.list("testdata/ocr")?.filter { it.endsWith(".txt") }?.sorted().orEmpty()
    private fun read(name: String): String = context.assets.open("testdata/ocr/" + name).bufferedReader(Charsets.UTF_8).use { it.readText() }
}

@Composable
fun LlmDiagnosticScreen(viewModel: DocAnizerViewModel) {
    if (!BuildConfig.DEBUG) return
    val context = LocalContext.current
    val runner = remember(context, viewModel) {
        LlmDiagnosticRunner(context.applicationContext, viewModel.llmService, viewModel.ruleRepo)
    }
    val rows by runner.rows.collectAsStateWithLifecycle()
    val running by runner.running.collectAsStateWithLifecycle()
    val status by runner.status.collectAsStateWithLifecycle()
    val elapsedMs by runner.activeElapsedMs.collectAsStateWithLifecycle()
    val nativeProgress by runner.nativeProgress.collectAsStateWithLifecycle()
    val nativeStage by runner.nativeStage.collectAsStateWithLifecycle()
    val documentIndex by runner.currentDocumentIndex.collectAsStateWithLifecycle()
    val totalDocuments by runner.totalDocuments.collectAsStateWithLifecycle()
    val documentName by runner.currentDocumentName.collectAsStateWithLifecycle()
    val pipelineLabel by runner.pipelineLabel.collectAsStateWithLifecycle()
    val completedSteps by runner.completedSteps.collectAsStateWithLifecycle()
    val totalSteps by runner.totalSteps.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var activeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            runner.noteExportResult("Logexport abgebrochen")
        } else {
            val result = runCatching {
                val content = runner.report()
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Datei konnte nicht geöffnet werden")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            }
            runner.noteExportResult(if (result.isSuccess) "Diagnosebericht exportiert" else "Export fehlgeschlagen")
        }
    }

    val progressValue = if (totalSteps > 0) {
        ((completedSteps.toFloat() + if (nativeProgress in 0..100) nativeProgress / 100f else 0f) / totalSteps).coerceIn(0f, 1f)
    } else if (!running && status.contains("erfolgreich", ignoreCase = true)) 1f else 0f
    val stageText = when (nativeStage) {
        "model_load" -> "Modell wird geladen"
        "context_init" -> "Rechenkontext wird vorbereitet"
        "prompt_prepare" -> "Dokumenttext wird vorbereitet"
        "prompt_eval" -> "Modell verarbeitet den Text"
        "generation" -> "Modell erstellt die Antwort"
        "cancelled" -> "Abbruch läuft"
        "error" -> "Modellfehler"
        else -> if (running) "Test wird vorbereitet" else ""
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("LLM-Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("lokal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "Prüft, ob das installierte Modell tatsächlich antwortet. Technische Details gibt es nur im Export.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (running) {
                if (totalDocuments > 0) {
                    Text(
                        buildString {
                            append("Dokument ")
                            append(documentIndex.coerceAtLeast(1))
                            append("/")
                            append(totalDocuments)
                            if (documentName.isNotBlank()) {
                                append(" · ")
                                append(documentName)
                            }
                            if (pipelineLabel.isNotBlank()) {
                                append(" · ")
                                append(pipelineLabel)
                            }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2
                    )
                } else {
                    Text(pipelineLabel.ifBlank { "Modell-Kurztest" }, style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(progress = progressValue, modifier = Modifier.fillMaxWidth().height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    Text(stageText.ifBlank { status }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(formatDiagnosticElapsed(elapsedMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = !running,
                    onClick = { activeJob = scope.launch { runner.testPrompt() } },
                    modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Modell-Kurztest", maxLines = 1)
                }
                OutlinedButton(
                    enabled = !running,
                    onClick = { activeJob = scope.launch { runner.runSequential() } },
                    modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp)
                ) { Text("OCR-Test", maxLines = 1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = running,
                    onClick = { activeJob?.cancel(); runner.cancelMessage() },
                    modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop")
                }
                OutlinedButton(
                    onClick = { export.launch("LLM-Diagnose.txt") },
                    modifier = Modifier.weight(1f).heightIn(min = 36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (running) "Live-Log exportieren" else "Log exportieren", maxLines = 1)
                }
            }

            if (rows.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "Ergebnisse · OK ${rows.count { it.status == "OK" }} · Zeitüberschreitungen ${rows.count { it.status == "TIMEOUT" }} · Fehler ${rows.count { it.status == "FEHLER" || it.status == "ABGEBROCHEN" }}",
                    style = MaterialTheme.typography.labelMedium
                )
                rows.takeLast(5).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        val ok = row.status == "OK"
                        Text(
                            if (ok) "✓" else "!",
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            row.name.substringBefore(" / ").removePrefix("0").replace("_", " ").substringBeforeLast("."),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                        Text(
                            when (row.status) {
                                "OK" -> "OK"
                                "TIMEOUT" -> "Zeitüberschreitung"
                                "FALLBACK" -> "Fallback"
                                "ABGEBROCHEN" -> "Gestoppt"
                                else -> "Fehler"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(formatDiagnosticElapsed(row.elapsedMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) { Text(if (showAdvanced) "Weitere Tests ausblenden" else "Weitere Tests") }
            if (showAdvanced) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = !running,
                        onClick = { activeJob = scope.launch { runner.runParallel() } },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) { Text("3 Aufrufwege", maxLines = 1) }
                    OutlinedButton(
                        enabled = !running,
                        onClick = { activeJob = scope.launch { runner.runAbortTest() } },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) { Text("Abbruchtest", maxLines = 1) }
                    OutlinedButton(
                        enabled = !running,
                        onClick = { activeJob = scope.launch(Dispatchers.IO) { runner.unload() } },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(15.dp))
                        Text("Entladen", maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun formatDiagnosticElapsed(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1000L
    return if (seconds < 60L) "${seconds}s" else "${seconds / 60L}m ${seconds % 60L}s"
}
