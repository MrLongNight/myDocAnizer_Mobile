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
        logScope.launch { persistLiveReport() }
    }

    private fun startTracking() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = logScope.launch {
            while (_running.value) {
                persistLiveReport()
                delay(1_000L)
            }
            persistLiveReport()
        }
    }

    private fun stopTracking() {
        _activeStep.value = "—"
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
        markStep("TESTPROMPT")
        setStatus("Testprompt läuft: Modell wird bei Bedarf geladen und eine kurze Antwort erzeugt")
        startTracking()
        try {
            val response = withTimeout(DOC_TIMEOUT_MS) {
                engine.executeChatInferenceWithTools(
                    modelFile = modelFile,
                    userMessage = "Antworte exakt mit LLM-DIAGNOSE-OK.",
                    systemPrompt = "Du bist ein kurzer Funktionstest.",
                    params = LlamaCppInferenceEngine.InferenceParams(maxTokens = 24, threads = 2, temperature = 0f)
                )
            }
            val mode = engine.lastInferenceMode
            val markerFound = response.contains("LLM-DIAGNOSE-OK", ignoreCase = true)
            setStatus(when {
                mode != LlamaCppInferenceEngine.InferenceMode.LOCAL_LLM ->
                    "TEST NICHT BESTANDEN: Es lief nur der Regel-/Fallbackpfad (Modus=$mode, finish_reason=${engine.lastFinishReason ?: "—"})."
                !markerFound ->
                    "LLM wurde aufgerufen, aber die erwartete Testantwort fehlt. Antwort: " + response.take(180)
                else ->
                    "NATIVER LLM-TEST BESTANDEN: Testmarker gefunden; finish_reason=${engine.lastFinishReason ?: "—"}."
            })
        } catch (cancelled: CancellationException) {
            setStatus("Testprompt abgebrochen")
            throw cancelled
        } catch (error: Throwable) {
            setStatus("TEST FEHLGESCHLAGEN: ${error.javaClass.simpleName}: ${error.message ?: "keine Fehlermeldung"}")
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
        setStatus("OCR-Stresstest wird vorbereitet")
        startTracking()
        try {
            val names = assets()
            for ((index, name) in names.withIndex()) {
                val ocrText = read(name)
                setStatus("OCR-Stresstest: " + (index + 1) + "/" + names.size + " – First-Run: " + name)
                var matchCount = 0
                val firstRow = measure("$name / First-Run", ocrText) { text ->
                    matchCount = rules.matchingRules(text).size
                    service.classifyDocumentFirstRun(text)
                    "Regeltreffer=" + matchCount +
                        "; mode=" + engine.lastInferenceMode +
                        "; finish_reason=" + (engine.lastFinishReason ?: "unknown") +
                        "; tokens=" + (engine.lastInferenceMetrics?.generatedTokens ?: 0) +
                        "; Pipeline=First-Run; Vorschlag nicht gespeichert"
                }
                _rows.value = _rows.value + firstRow
                logScope.launch { persistLiveReport() }

                setStatus("OCR-Stresstest: " + (index + 1) + "/" + names.size + " – Second-Run: " + name)
                val secondRow = measure("$name / Second-Run", ocrText) { text ->
                    service.classifyDocumentSecondRun(text)
                    "mode=" + engine.lastInferenceMode +
                        "; finish_reason=" + (engine.lastFinishReason ?: "unknown") +
                        "; tokens=" + (engine.lastInferenceMetrics?.generatedTokens ?: 0) +
                        "; Pipeline=Second-Run; Vorschlag nicht gespeichert"
                }
                _rows.value = _rows.value + secondRow
                logScope.launch { persistLiveReport() }
            }
            setStatus("OCR-Stresstest abgeschlossen: " + names.size + " Texte, First- und Second-Run getrennt protokolliert")
        } catch (cancelled: CancellationException) {
            setStatus("Lauf abgebrochen")
            throw cancelled
        } finally { stopTracking() }
    }

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
                    engine.requestNativeCancellation()
                }
                try {
                    service.classifyDocumentSecondRun(read(name))
                } finally {
                    cancelJob.cancel()
                }
            }
            val wasCancelled = engine.lastFinishReason == "cancelled"
            _rows.value = _rows.value + row(name, start, before, if (wasCancelled) "ABGEBROCHEN" else "OK", if (wasCancelled) "Native Token-Generierung hat Abbruchsignal verarbeitet" else "Analyse vor Abbruchsignal abgeschlossen")
            setStatus("Abbruchtest abgeschlossen")
        } finally { stopTracking() }
    }

    fun cancelMessage() {
        engine.requestNativeCancellation()
        setStatus("Abbruchsignal an native Prompt-Evaluation und Token-Generierung gesendet")
    }

    fun noteExportResult(message: String) {
        _status.value = message
        logScope.launch { persistLiveReport() }
    }

    fun report(): String = buildString {
        appendLine("LLM-Diagnose")
        appendLine("Berichtsversion=2")
        appendLine("Exportzeitpunkt=" + java.time.Instant.now().toString())
        appendLine("Hinweis=Der Bericht darf auch während eines laufenden oder festhängenden Tests exportiert werden.")
        appendLine("Zeitpunkt (Epoch-ms)=" + System.currentTimeMillis())
        appendLine("Status=" + _status.value)
        appendLine("Läuft=" + _running.value)
        appendLine("Aktiver Schritt=" + _activeStep.value)
        appendLine("Aktive Schrittzeit=" + if (activeStartedAtMs > 0L) (SystemClock.elapsedRealtime() - activeStartedAtMs).toString() + " ms" else "—")
        appendLine("Native Inferenzfortschritt=" + engine.inferenceProgress.value)
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
        row(name, start, before, status, detail, stack)
    }

    private fun row(name: String, start: Long, before: HeapSample, status: String, detail: String, stack: String = ""): LlmDiagnosticRow {
        val after = HeapSample.capture()
        val mode = if (engine.lastInferenceMode == LlamaCppInferenceEngine.InferenceMode.LOCAL_LLM) "LLM" else "Fallback"
        val finishReason = when (status) { "FEHLER" -> "error"; "TIMEOUT" -> "timeout"; "ABGEBROCHEN" -> "cancelled"; else -> engine.lastFinishReason ?: if (status == "FALLBACK") "fallback" else "unknown" }
        return LlmDiagnosticRow(name, SystemClock.elapsedRealtime() - start, mode, finishReason, engine.lastInferenceMetrics?.generatedTokens ?: 0, before.java, after.java, before.native, after.native, status, detail, stack)
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
    val runner = remember(context, viewModel) { LlmDiagnosticRunner(context.applicationContext, viewModel.llmService, viewModel.ruleRepo) }
    val rows by runner.rows.collectAsStateWithLifecycle()
    val running by runner.running.collectAsStateWithLifecycle()
    val status by runner.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var activeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var snapshot by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) {
            runner.noteExportResult("Logexport abgebrochen: kein Speicherziel ausgewählt")
        } else {
            val result = runCatching {
                val content = runner.report()
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Android hat keinen Dateiausgabestrom bereitgestellt")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(content) }
            }
            runner.noteExportResult(
                if (result.isSuccess) "LOG EXPORTIERT: Diagnosebericht wurde gespeichert."
                else "LOG-EXPORT FEHLGESCHLAGEN: ${result.exceptionOrNull()?.javaClass?.simpleName}: ${result.exceptionOrNull()?.message ?: "unbekannter Dateifehler"}"
            )
        }
    }
    LaunchedEffect(Unit) { snapshot = runner.snapshot() }
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("LLM-Diagnose", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text("Nur Debug-Build. Kein Kamerazugriff, kein Dialog, keine Speicherung im echten Tresor.", style = MaterialTheme.typography.bodySmall)
            Text(snapshot, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !running, onClick = { activeJob = scope.launch { runCatching { runner.testPrompt() }; snapshot = runner.snapshot() } }) { Icon(Icons.Default.PlayArrow, null); Text("Modell laden + Test") }
                OutlinedButton(enabled = !running, onClick = { activeJob = scope.launch { runCatching { runner.testPrompt() }; snapshot = runner.snapshot() } }) { Text("Testprompt wiederholen") }
                OutlinedButton(enabled = !running, onClick = { activeJob = scope.launch(Dispatchers.IO) { runner.unload(); withContext(Dispatchers.Main) { snapshot = runner.snapshot() } } }) { Icon(Icons.Default.Memory, null); Text("Entladen") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !running, onClick = { activeJob = scope.launch { runner.runSequential() } }) { Text("OCR-Stresstest (20)") }
                OutlinedButton(enabled = !running, onClick = { activeJob = scope.launch { runner.runParallel() } }) { Text("3 Aufrufwege (sequenziell)") }
                OutlinedButton(enabled = !running, onClick = { activeJob = scope.launch { runner.runAbortTest() } }) { Text("Abbruchtest") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = running, onClick = { activeJob?.cancel(); runner.cancelMessage() }) { Icon(Icons.Default.Stop, null); Text("Stop") }
                OutlinedButton(onClick = { snapshot = runner.snapshot() }) { Text("Aktualisieren") }
                OutlinedButton(onClick = { export.launch("LLM-Diagnose.txt") }) { Icon(Icons.Default.UploadFile, null); Text(if (running) "Live-Log speichern" else "Log exportieren") }
            }
            Text("Status: " + status)
            Text("OK " + rows.count { it.status == "OK" } + " · Fallback " + rows.count { it.status == "FALLBACK" } + " · Timeout " + rows.count { it.status == "TIMEOUT" } + " · Fehler " + rows.count { it.status == "FEHLER" })
            val javaTrend = if (rows.isNotEmpty()) rows.last().javaAfter - rows.first().javaBefore else 0L
            val nativeTrend = if (rows.isNotEmpty()) rows.last().nativeAfter - rows.first().nativeBefore else 0L
            Text("Längste Wartezeit: " + (rows.maxOfOrNull { it.elapsedMs } ?: 0L) + " ms", style = MaterialTheme.typography.bodySmall)
            Text("Heap-Trend: Java " + javaTrend + " B · Native " + nativeTrend + " B · über 150 MB: " + ((javaTrend + nativeTrend) > 150L * 1024L * 1024L), style = MaterialTheme.typography.bodySmall)
            rows.forEach { r ->
                HorizontalDivider()
                Text(r.name + ": " + r.status + " · " + r.elapsedMs + " ms · " + r.mode + " · " + r.finishReason + " · " + r.tokens + " Tokens", style = MaterialTheme.typography.bodySmall)
                if (r.detail.isNotBlank()) Text(r.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
