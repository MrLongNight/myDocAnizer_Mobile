package com.example.service

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object OcrService {

    private const val OCR_TIMEOUT_MS = 12_000L

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Echte Offline-OCR Volltext-Extraktion über Google ML Kit (100% lokal auf dem Gerät).
     * Liest Textblöcke, Zeilen und Zeichenketten aus dem übergebenen Bitmap.
     */
    suspend fun recognizeText(bitmap: Bitmap, senderHint: String = "", titleHint: String = ""): String = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        var workingBitmap = bitmap
        var needsRecycle = false

        try {
            // Downsampling: ML Kit arbeitet optimal bei ca. 1500-2000px. 
            // Extrem hohe Auflösungen verbrauchen nur unnötig RAM ohne Genauigkeitsgewinn.
            val maxEdge = 2200
            if (bitmap.width > maxEdge || bitmap.height > maxEdge) {
                val scale = maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height)
                val targetW = (bitmap.width * scale).toInt()
                val targetH = (bitmap.height * scale).toInt()
                workingBitmap = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                needsRecycle = true
            }

            val inputImage = InputImage.fromBitmap(workingBitmap, 0)
            val ocrTask = recognizer.process(inputImage)
            val recognizedText = withTimeoutOrNull(OCR_TIMEOUT_MS) {
                suspendCancellableCoroutine<String> { continuation ->
                    ocrTask
                        .addOnSuccessListener { visionText ->
                            val fullText = visionText.text.trim()
                            if (continuation.isActive) continuation.resume(fullText)
                        }
                        .addOnFailureListener {
                            // Use the no-text fallback rather than storing an SDK error as OCR text.
                            if (continuation.isActive) continuation.resume("")
                        }
                }
            }

            if (recognizedText == null) {
                AppAuditLogger.logError(
                    "OcrService",
                    "OCR-Zeitlimit nach ${OCR_TIMEOUT_MS} ms; Scan wird ohne OCR fortgesetzt",
                    java.util.concurrent.TimeoutException("ML Kit OCR timed out after ${OCR_TIMEOUT_MS} ms")
                )
                if (needsRecycle) {
                    // ML Kit may still be reading this bitmap after the coroutine times out.
                    // Transfer ownership to the Task completion callback to avoid a recycle race.
                    val bitmapToRelease = workingBitmap
                    needsRecycle = false
                    ocrTask.addOnCompleteListener {
                        if (bitmapToRelease !== bitmap && !bitmapToRelease.isRecycled) {
                            runCatching { bitmapToRelease.recycle() }
                        }
                    }
                }
            }

            val finalResult = recognizedText?.takeIf { it.isNotBlank() } ?: buildString {
                if (senderHint.isNotBlank()) append("Absender: $senderHint\n")
                if (titleHint.isNotBlank()) append("Titel: $titleHint\n")
                append("Dokumenten-Scan erfasst.")
            }

            val durationMs = System.currentTimeMillis() - startTime
            AppAuditLogger.logOcr(finalResult.length, durationMs, finalResult.take(120))

            finalResult
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            val fallback = buildString {
                if (senderHint.isNotBlank()) append("Absender: $senderHint\n")
                if (titleHint.isNotBlank()) append("Titel: $titleHint\n")
                append("Dokument erfasst.")
            }
            AppAuditLogger.logError("OcrService", "Fehler bei OCR-Texterkennung: ${e.message}", e)
            fallback
        } finally {
            if (needsRecycle) {
                runCatching { workingBitmap.recycle() }
            }
        }
    }
}
