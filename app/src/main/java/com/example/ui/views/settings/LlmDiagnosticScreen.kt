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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
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
    private val engine get() = service.llamaCppEngine
    private val selectedModel get() = service.getSelectedModel()
    private val modelFile get() = service.getModelFile(selectedModel.id)

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
            "Geladener Modellpfad: " + (engine.nativeLoadedModelPath() ?: "nicht geladen/unbekannt"),
            "n_ctx: " + engine.nativeContextSize(),
            "Threads (Default): " + Runtime.getRuntime().availableProcessors().coerceIn(2, 6),
            "Letzter Modus: " + engine.lastInferenceMode,
            "finish_reason: " + (engine.lastFinishReason ?: "—")
        ).joinToString("\n")
    }

    suspend fun testPrompt() {
        _status.value = "Testprompt läuft"
        withTimeout(DOC_TIMEOUT_MS) {
            engine.executeChatInferenceWithTools(
                modelFile = modelFile,
                userMessage = "Antworte exakt mit LLM-DIAGNOSE-OK.",
                systemPrompt = "Du bist ein kurzer Funktionstest.",
                params = LlamaCppInferenceEngine.InferenceParams(maxTokens = 24, threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
            )
        }
        _status.value = "Testprompt abgeschlossen; Modus=" + engine.lastInferenceMode
    }

    fun unload() {
        engine.unloadNativeEngine()
        _status.value = "Native Engine entladen"
    }

    suspend fun runSequential() {
        if (_running.value) return
        _running.value = true
        _rows.value = emptyList()
        try {
            val names = assets()
            for ((index, name) in names.withIndex()) {
                _status.value = "Lauf 1/3: " + (index + 1) + "/" + names.size + " – " + name
                _rows.value = _rows.value + measure(name) { text ->
                    val matchCount = rules.matchingRules(text).size
                    service.classifyDocumentFirstRun(text)
                    service.classifyDocumentSecondRun(text)
                    "Regeltreffer=" + matchCount + "; Vorschlag nur im Arbeitsspeicher, nicht gespeichert"
                }
            }
            _status.value = "Lauf 1/3 abgeschlossen"
        } catch (cancelled: CancellationException) {
            _status.value = "Lauf abgebrochen"
            throw cancelled
        } finally { _running.value = false }
    }

    suspend fun runParallel() {
        if (_running.value) return
        _running.value = true
        _status.value = "Lauf 2/3: Chat + Analyse + Re-Run"
        try {
            val text = read(assets().first())
            val file = modelFile
            val result = coroutineScope {
                listOf(
                    async { measure("PARALLEL-CHAT") { value -> engine.executeChatInferenceWithTools(file, "Fasse kurz zusammen: " + value.take(500), params = LlamaCppInferenceEngine.InferenceParams(maxTokens = 96)); "Chat-Aufruf" } },
                    async { measure("PARALLEL-ANALYSE") { value -> service.classifyDocumentFirstRun(value); "First-Run-Analyse" } },
                    async { measure("PARALLEL-RE-RUN") { value -> service.classifyDocumentSecondRun(value); "Second-Run-Analyse" } }
                ).map { it.await() }
            }
            _rows.value = _rows.value + result
            _status.value = "Parallellauf abgeschlossen; native Engine serialisiert Inferenz intern"
        } catch (cancelled: CancellationException) {
            _status.value = "Parallellauf abgebrochen"
            throw cancelled
        } catch (error: Throwable) {
            _rows.value = _rows.value + errorRow("PARALLEL", error)
            _status.value = "Parallellauf mit Fehler beendet"
        } finally { _running.value = false }
    }

    suspend fun runAbortTest() {
        if (_running.value) return
        _running.value = true
        _status.value = "Lauf 3/3: Langtext mit 1,5-s-Abbruchfenster"
        try {
            val name = assets().firstOrNull { it.contains("langer_ocrtext") } ?: assets().last()
            val before = HeapSample.capture()
            val start = SystemClock.elapsedRealtime()
            try {
                withTimeout(1_500L) { service.classifyDocumentSecondRun(read(name)) }
                _rows.value = _rows.value + row(name, start, before, "OK", "Analyse war vor Timeout fertig")
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                _rows.value = _rows.value + row(name, start, before, "ABGEBROCHEN", "Kotlin-Abbruch nach 1,5 s; laufender synchroner JNI-Aufruf kann bis zur Rückkehr weiterlaufen")
            }
            _status.value = "Abbruchtest abgeschlossen"
        } finally { _running.value = false }
    }

    fun cancelMessage() {
        _status.value = "Abbruch angefordert; nativen synchronen JNI-Aufruf kann Kotlin nicht gewaltsam stoppen"
    }

    fun report(): String = buildString {
        appendLine("LLM-Diagnose")
        appendLine(snapshot())
        appendLine()
        appendLine("OK=" + _rows.value.count { it.status == "OK" })
        appendLine("Fallback=" + _rows.value.count { it.status == "FALLBACK" })
        appendLine("Timeout=" + _rows.value.count { it.status == "TIMEOUT" })
        appendLine("Fehler=" + _rows.value.count { it.status == "FEHLER" })
        appendLine("Abgebrochen=" + _rows.value.count { it.status == "ABGEBROCHEN" })
        appendLine("Längste Wartezeit=" + (_rows.value.maxOfOrNull { it.elapsedMs } ?: 0L) + " ms")
        if (_rows.value.isNotEmpty()) {
            appendLine("Java-Heap-Trend=" + (_rows.value.last().javaAfter - _rows.value.first().javaBefore) + " Bytes")
            appendLine("Native-Heap-Trend=" + (_rows.value.last().nativeAfter - _rows.value.first().nativeBefore) + " Bytes")
        }
        _rows.value.forEach { r ->
            appendLine("[" + r.status + "] " + r.name + " | " + r.elapsedMs + " ms | " + r.mode + " | finish_reason=" + r.finishReason + " | Tokens=" + r.tokens)
            appendLine("Java-Heap: " + r.javaBefore + " -> " + r.javaAfter + "; Native-Heap: " + r.nativeBefore + " -> " + r.nativeAfter)
            if (r.detail.isNotBlank()) appendLine("Details: " + r.detail)
            if (r.stacktrace.isNotBlank()) appendLine("Stacktrace:\n" + r.stacktrace)
        }
    }

    private suspend fun measure(name: String, action: suspend (String) -> String): LlmDiagnosticRow = withContext(Dispatchers.IO) {
        val before = HeapSample.capture()
        val start = SystemClock.elapsedRealtime()
        var detail = ""
        var status = "OK"
        var stack = ""
        try {
            withTimeout(DOC_TIMEOUT_MS) { detail = action(read(name)) }
            if (engine.lastInferenceMode == LlamaCppInferenceEngine.InferenceMode.RULE_FALLBACK) status = "FALLBACK"
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            status = "TIMEOUT"; detail = timeout.message ?: "Timeout"; stack = Log.getStackTraceString(timeout)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Throwable) {
            status = "FEHLER"; detail = error.message ?: error.javaClass.simpleName; stack = Log.getStackTraceString(error)
        }
        row(name, start, before, status, detail, stack)
    }

    private fun row(name: String, start: Long, before: HeapSample, status: String, detail: String, stack: String = ""): LlmDiagnosticRow {
        val after = HeapSample.capture()
        val mode = if (engine.lastInferenceMode == LlamaCppInferenceEngine.InferenceMode.LOCAL_LLM) "LLM" else "Fallback"
        return LlmDiagnosticRow(name, SystemClock.elapsedRealtime() - start, mode, engine.lastFinishReason ?: if (status == "FALLBACK") "fallback" else "unknown", engine.lastInferenceMetrics?.generatedTokens ?: 0, before.java, after.java, before.native, after.native, status, detail, stack)
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
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(runner.report()) }
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
                Button(enabled = !running, onClick = { activeJob = scope.launch { runCatching { runner.testPrompt() }; snapshot = runner.snapshot() } }) { Icon(Icons.Default.PlayArrow, null); Text("Testprompt") }
                OutlinedButton(enabled = !running, onClick = { runner.unload(); snapshot = runner.snapshot() }) { Icon(Icons.Default.Memory, null); Text("Entladen") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !running, onClick = { activeJob = scope.launch { runner.runSequential() } }) { Text("Stresstest 20") }
                OutlinedButton(enabled = !running, onClick = { activeJob = scope.launch { runner.runParallel() } }) { Text("3 parallel") }
                OutlinedButton(enabled = !running, onClick = { activeJob = scope.launch { runner.runAbortTest() } }) { Text("Abbruchtest") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = running, onClick = { activeJob?.cancel(); runner.cancelMessage() }) { Icon(Icons.Default.Stop, null); Text("Stop") }
                OutlinedButton(onClick = { snapshot = runner.snapshot() }) { Text("Aktualisieren") }
                OutlinedButton(enabled = rows.isNotEmpty(), onClick = { export.launch("LLM-Diagnose.txt") }) { Icon(Icons.Default.UploadFile, null); Text("Export") }
            }
            Text("Status: " + status)
            Text("OK " + rows.count { it.status == "OK" } + " · Fallback " + rows.count { it.status == "FALLBACK" } + " · Timeout " + rows.count { it.status == "TIMEOUT" } + " · Fehler " + rows.count { it.status == "FEHLER" })
            rows.forEach { r ->
                HorizontalDivider()
                Text(r.name + ": " + r.status + " · " + r.elapsedMs + " ms · " + r.mode + " · " + r.finishReason + " · " + r.tokens + " Tokens", style = MaterialTheme.typography.bodySmall)
                if (r.detail.isNotBlank()) Text(r.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
