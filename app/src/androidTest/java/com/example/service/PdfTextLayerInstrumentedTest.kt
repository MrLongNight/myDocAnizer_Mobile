package com.example.service

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.example.model.PdfSettings
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * D14 audit: proves whether generated scan PDFs embed OCR text or only page images.
 *
 * Current expected behavior: the category/header/footer are PDF text, but OCR text
 * is not embedded. Searchable OCR content is stored separately in Room/FTS.
 * If a PDF OCR text layer becomes a product requirement, update this test only
 * alongside an approved implementation plan and explicit acceptance criteria.
 */
class PdfTextLayerInstrumentedTest {

    @Test
    fun generatedScanPdfContainsHeadersButDoesNotEmbedOcrTextLayer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context.applicationContext)

        val ocrSentinel = "D14_OCR_TEXT_LAYER_SENTINEL_${UUID.randomUUID().toString().replace("-", "")}"
        val outputDir = File(context.cacheDir, "d14-pdf-audit-${UUID.randomUUID()}")
        val bitmap = Bitmap.createBitmap(120, 180, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }

        try {
            val pdf = DocumentStorageService.generatePdf(
                context = context,
                pageBitmaps = listOf(bitmap),
                ocrText = ocrSentinel,
                metadataTags = "D14",
                docType = "Test",
                mainCategory = "Audit",
                subCategory = "PDF",
                targetDir = outputDir,
                internalName = "scan-audit.pdf",
                isEncrypted = false,
                settings = PdfSettings()
            )

            assertTrue("Generated PDF must exist and be non-empty", pdf.isFile && pdf.length() > 0L)

            val extractedText = PDDocument.load(pdf).use { document ->
                PDFTextStripper().getText(document)
            }

            assertTrue(
                "Expected the visible PDF header to be extractable as text",
                extractedText.contains("myDocAnizer")
            )
            assertFalse(
                "OCR text must not be reported as embedded in the PDF; current search uses Room/FTS",
                extractedText.contains(ocrSentinel)
            )
        } finally {
            bitmap.recycle()
            outputDir.deleteRecursively()
        }
    }
}
