package com.example.service

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import com.example.model.*
import com.example.data.AppDatabase
import com.example.service.DocRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object DocumentStorageService {
    // Explicit restore bounds defend against ZIP bombs and hostile backup containers.
    private const val MAX_RESTORE_ENCRYPTED_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_RESTORE_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_RESTORE_DOCUMENT_BYTES = 512L * 1024 * 1024
    private const val MAX_RESTORE_INDEX_BYTES = 32L * 1024 * 1024
    private const val MAX_RESTORE_METADATA_BYTES = 32L * 1024 * 1024
    private const val MAX_RESTORE_ENTRIES = 20_000
    private const val MAX_RESTORE_ENTRY_NAME_CHARS = 4096

    /**
     * Ermittelt das übergeordnete Basis-Verzeichnis für alle Dokumente
     */
    fun getBaseDocumentsDirectory(context: Context, locationType: String = "APP_STORAGE", customPath: String = ""): File {
        val baseDir = when (locationType) {
            "DOCUMENTS" -> {
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) 
                    ?: File(context.filesDir, "Documents")
            }
            "EXTERNAL_STORAGE" -> {
                context.getExternalFilesDir(null) ?: File(context.filesDir, "Documents")
            }
            "CUSTOM" -> {
                if (customPath.isNotBlank()) File(customPath) else File(context.filesDir, "Documents")
            }
            else -> File(context.filesDir, "Documents")
        }
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return baseDir
    }

    /**
     * Liest die real existierende 2-Ebenen Ordnerstruktur aus dem Basisverzeichnis
     */
    fun getExistingFolderStructure(context: Context, locationType: String = "APP_STORAGE", customPath: String = ""): List<File> {
        val base = getBaseDocumentsDirectory(context, locationType, customPath)
        val list = mutableListOf<File>()
        if (base.exists() && base.isDirectory) {
            base.listFiles()?.filter { it.isDirectory }?.forEach { mainDir ->
                list.add(mainDir)
                mainDir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                    list.add(subDir)
                }
            }
        }
        return list
    }

    /**
     * Erstellt einen neuen realen Ordner (Ebene 1 oder Ebene 2)
     */
    fun createFolder(context: Context, relativePath: String, locationType: String = "APP_STORAGE", customPath: String = ""): File {
        val base = getBaseDocumentsDirectory(context, locationType, customPath)
        val target = File(base, relativePath)
        if (!target.exists()) {
            target.mkdirs()
        }
        return target
    }

    /**
     * Löscht einen leeren realen Ordner
     */
    fun deleteFolderIfEmpty(context: Context, relativePath: String, locationType: String = "APP_STORAGE", customPath: String = ""): Boolean {
        val base = getBaseDocumentsDirectory(context, locationType, customPath)
        val target = File(base, relativePath)
        return if (target.exists() && target.isDirectory) {
            target.delete()
        } else false
    }

    /**
     * Ermittelt die 2-Ebenen-Ordnerstruktur:
     * Wenn useIdPrefixes = true & folderPrefixStyle != "NONE":
     *   Ebene 1: A01_Stromversorger oder [A01] Stromversorger etc.
     *   Ebene 2: B1.01-Rechnungen oder [B1.01] Rechnungen etc.
     * Falls leer oder deaktiviert: Fallback auf saubere Namen
     */
    fun getDocumentDirectory(
        context: Context,
        mainCategory: String,
        subCategory: String,
        mainCategoryId: String = "",
        subCategoryId: String = "",
        useIdPrefixes: Boolean = true,
        folderPrefixStyle: String = "AKTENPLAN_STANDARD",
        locationType: String = "APP_STORAGE",
        customPath: String = ""
    ): File {
        val safeMain = sanitizeFolderName(mainCategory)
        val safeSub = sanitizeFolderName(subCategory)

        val isPrefixActive = useIdPrefixes && folderPrefixStyle != "NONE"

        val folder1 = if (isPrefixActive && mainCategoryId.isNotBlank()) {
            formatMainCategoryFolder(mainCategoryId, safeMain, folderPrefixStyle)
        } else {
            safeMain
        }

        val folder2 = if (isPrefixActive && subCategoryId.isNotBlank()) {
            formatSubCategoryFolder(subCategoryId, safeSub, folderPrefixStyle)
        } else {
            safeSub
        }

        val baseDir = getBaseDocumentsDirectory(context, locationType, customPath)
        val dir = File(baseDir, "$folder1/$folder2")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun formatMainCategoryFolder(id: String, safeName: String, style: String = "AKTENPLAN_STANDARD"): String {
        val trimmed = id.trim()
        val digits = trimmed.replace(Regex("[^0-9]"), "")
        return when (style) {
            "NONE" -> safeName
            "SIMPLE_NUMBER" -> {
                val num = if (digits.isNotBlank()) String.format("%02d", digits.toIntOrNull() ?: 1) else "01"
                "${num}_$safeName"
            }
            "LETTER_NUMBER" -> {
                val letter = trimmed.filter { it.isLetter() }.take(1).uppercase().ifBlank { "A" }
                "${letter}_$safeName"
            }
            "NUMERIC_DOT" -> {
                val num = if (digits.isNotBlank()) String.format("%02d", digits.toIntOrNull() ?: 1) else "01"
                "${num}._$safeName"
            }
            "BRACKETS" -> "[$trimmed] $safeName"
            "COMPACT" -> {
                val compact = trimmed.replace(Regex("0(\\d)"), "$1")
                "${compact}_$safeName"
            }
            "FILE_NUMBER" -> "Az-${trimmed}_$safeName"
            "DECIMAL" -> {
                val num = if (digits.isNotBlank()) String.format("%02d", digits.toIntOrNull() ?: 1) else "01"
                "${num}_$safeName"
            }
            "HIERARCHICAL_INHERIT" -> "${trimmed}_$safeName"
            else -> "${trimmed}_$safeName" // "AKTENPLAN_STANDARD"
        }
    }

    fun formatSubCategoryFolder(id: String, safeName: String, style: String = "AKTENPLAN_STANDARD"): String {
        val trimmed = id.trim()
        return when (style) {
            "NONE" -> safeName
            "SIMPLE_NUMBER" -> {
                val clean = trimmed.replace(Regex("[^0-9.]"), "")
                "${if (clean.isNotBlank()) clean else "01.01"}_$safeName"
            }
            "LETTER_NUMBER" -> {
                val digitsOnly = trimmed.replace(Regex("[^0-9]"), "")
                val letter = trimmed.filter { it.isLetter() }.take(1).uppercase().ifBlank { "A" }
                "${letter}${digitsOnly.ifBlank { "1" }}_$safeName"
            }
            "NUMERIC_DOT" -> {
                val clean = trimmed.replace(Regex("[^0-9.]"), "")
                "${if (clean.isNotBlank()) clean else "01.01"}.-$safeName"
            }
            "BRACKETS" -> "[$trimmed] $safeName"
            "COMPACT" -> {
                val compact = trimmed.replace(Regex("0(\\d)"), "$1")
                "${compact}-$safeName"
            }
            "FILE_NUMBER" -> "Az-${trimmed}-$safeName"
            "DECIMAL" -> {
                val clean = trimmed.replace(Regex("[^0-9.]"), "")
                "${if (clean.isNotBlank()) clean else "01.01"}-$safeName"
            }
            "HIERARCHICAL_INHERIT" -> "${trimmed}-$safeName"
            else -> "${trimmed}-$safeName" // "AKTENPLAN_STANDARD"
        }
    }

    private fun sanitizeFolderName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9äöüÄÖÜß_\\- ]"), "").trim().replace(" ", "_")
    }

    /**
     * Formatiert den Dateinamen gemäß der gewählten Konvention (Simple & Komplexe Optionen)
     */
    fun formatFileName(
        sender: String,
        title: String,
        date: Date = Date(),
        extension: String = "pdf",
        namingStyle: String = "DATE_SENDER_TITLE",
        mainCategoryId: String = "",
        subCategoryId: String = "",
        mainCategory: String = "",
        subCategory: String = ""
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val compactDateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dateStr = dateFormat.format(date)
        val compactDateStr = compactDateFormat.format(date)
        val cleanSender = sanitizeFolderName(sender)
        val cleanTitle = sanitizeFolderName(title)
        val ext = extension.removePrefix(".")
        val safeMain = sanitizeFolderName(mainCategory)
        val safeSub = sanitizeFolderName(subCategory)
        val e1 = sanitizeFolderName(mainCategoryId)
        val e2 = sanitizeFolderName(subCategoryId)

        val baseName = when (namingStyle) {
            "SENDER_TITLE_DATE" -> {
                when {
                    cleanSender.isNotBlank() && cleanTitle.isNotBlank() -> "${cleanSender}_${cleanTitle}_$dateStr"
                    cleanTitle.isNotBlank() -> "${cleanTitle}_$dateStr"
                    cleanSender.isNotBlank() -> "${cleanSender}_$dateStr"
                    else -> "Dokument_$dateStr"
                }
            }
            "DATE_TITLE" -> {
                if (cleanTitle.isNotBlank()) "${dateStr}_$cleanTitle" else "${dateStr}_Dokument"
            }
            "FOLDER_HIERARCHY_DATE_SENDER" -> {
                val hierarchy = listOf(e1, e2).filter { it.isNotBlank() }.joinToString("_")
                val prefix = if (hierarchy.isNotBlank()) "${hierarchy}_" else ""
                when {
                    cleanSender.isNotBlank() && cleanTitle.isNotBlank() -> "${prefix}${dateStr}_${cleanSender}_${cleanTitle}"
                    cleanTitle.isNotBlank() -> "${prefix}${dateStr}_${cleanTitle}"
                    cleanSender.isNotBlank() -> "${prefix}${dateStr}_${cleanSender}"
                    else -> "${prefix}${dateStr}_Dokument"
                }
            }
            "FOLDER_ID_DOC_NUMBER" -> {
                val hierarchy = listOf(e1, e2).filter { it.isNotBlank() }.joinToString("-")
                val prefix = if (hierarchy.isNotBlank()) "${hierarchy}_" else ""
                when {
                    cleanSender.isNotBlank() && cleanTitle.isNotBlank() -> "${prefix}${compactDateStr}_${cleanSender}_${cleanTitle}"
                    cleanTitle.isNotBlank() -> "${prefix}${compactDateStr}_${cleanTitle}"
                    else -> "${prefix}${compactDateStr}_Dokument"
                }
            }
            "CATEGORY_DATE_SENDER" -> {
                val catPrefix = listOf(safeMain, safeSub).filter { it.isNotBlank() }.joinToString("_")
                val prefix = if (catPrefix.isNotBlank()) "${catPrefix}_" else ""
                when {
                    cleanSender.isNotBlank() && cleanTitle.isNotBlank() -> "${prefix}${dateStr}_${cleanSender}_${cleanTitle}"
                    cleanTitle.isNotBlank() -> "${prefix}${dateStr}_${cleanTitle}"
                    else -> "${prefix}${dateStr}_Dokument"
                }
            }
            else -> { // "DATE_SENDER_TITLE"
                when {
                    cleanSender.isNotBlank() && cleanTitle.isNotBlank() -> "${dateStr}_${cleanSender}_${cleanTitle}"
                    cleanTitle.isNotBlank() -> "${dateStr}_${cleanTitle}"
                    cleanSender.isNotBlank() -> "${dateStr}_${cleanSender}"
                    else -> "${dateStr}_Dokument"
                }
            }
        }
        return "$baseName.$ext"
    }

    /**
     * Erzeugt einen absolut eindeutigen, internen Dateinamen (UUID-basiert).
     * Der ursprüngliche Name bleibt in den Metadaten (DB) erhalten.
     */
    fun generateInternalFileName(extension: String = "pdf", isEncrypted: Boolean = true): String {
        val uuid = UUID.randomUUID().toString()
        val suffix = if (isEncrypted) ".enc" else ""
        return "doc_${uuid}.${extension.removePrefix(".")}$suffix"
    }

    suspend fun secureSave(
        context: Context,
        inputAction: suspend (File) -> Unit,
        targetDir: File,
        internalFileName: String,
        isEncrypted: Boolean,
        encryptionKey: ByteArray? = null,
        passwordKey: String? = null
    ): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_save_${UUID.randomUUID()}.tmp")
        try {
            // 1. In temporäre Datei schreiben
            inputAction(tempFile)
            if (!tempFile.exists() || tempFile.length() == 0L) {
                throw java.io.IOException("Fehler beim Schreiben der temporären Datei.")
            }

            // 2. Zielpfad vorbereiten
            targetDir.mkdirs()
            val finalFile = File(targetDir, internalFileName)

            // 3. Verschlüsseln oder Verschieben
            if (isEncrypted) {
                when {
                    encryptionKey != null -> encryptFile(tempFile, finalFile, encryptionKey)
                    !passwordKey.isNullOrBlank() -> encryptFile(tempFile, finalFile, passwordKey)
                    else -> throw IllegalArgumentException("Weder Encryption-Key noch Passwort für Verschlüsselung vorhanden.")
                }
            } else {
                if (!tempFile.renameTo(finalFile)) {
                    // Fallback falls renameTo fehlschlägt (z.B. verschiedene Mount-Points)
                    tempFile.copyTo(finalFile, overwrite = true)
                }
            }

            if (!finalFile.exists()) {
                throw java.io.IOException("Zieldatei konnte nicht finalisiert werden.")
            }
            finalFile
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    /**
     * Speichert eine Bilddatei (JPEG oder PNG) direkt im gewählten DMS-Ordner
     */
    suspend fun saveImageFile(
        context: Context,
        bitmap: Bitmap,
        targetDir: File,
        internalName: String,
        isEncrypted: Boolean,
        encryptionKey: ByteArray? = null,
        passwordKey: String? = null,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 92
    ): File = secureSave(
        context = context,
        targetDir = targetDir,
        internalFileName = internalName,
        isEncrypted = isEncrypted,
        encryptionKey = encryptionKey,
        passwordKey = passwordKey,
        inputAction = { tempFile ->
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(format, quality, out)
            }
        }
    )

    /**
     * Erstellt ein durchsuchbares Mehrseiten-PDF aus einer Liste von Seiten (Bitmaps)
     */
    suspend fun generatePdf(
        context: Context,
        pageBitmaps: List<Bitmap>,
        ocrText: String,
        metadataTags: String,
        docType: String,
        mainCategory: String,
        subCategory: String,
        targetDir: File,
        internalName: String,
        isEncrypted: Boolean,
        encryptionKey: ByteArray? = null,
        passwordKey: String? = null,
        settings: PdfSettings
    ): File = secureSave(
        context = context,
        targetDir = targetDir,
        internalFileName = internalName,
        isEncrypted = isEncrypted,
        encryptionKey = encryptionKey,
        passwordKey = passwordKey,
        inputAction = { tempFile ->
            val document = PdfDocument()

        // Standard A4 in Points: 595 x 842 Punkte
        val pageWidth = 595
        val pageHeight = 842

        pageBitmaps.forEachIndexed { index, bitmap ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Weißer Hintergrund
            val bgPaint = Paint().apply { color = Color.WHITE }
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

            // Bitmap einpassen mit ensureSoftwareBitmap und seitenverhältnis-erhaltender Skalierung & Zentrierung
            var safeBmp = ImageProcessingService.ensureSoftwareBitmap(bitmap)
            
            // Basic DPI scaling optimization & A4 max pixel dimension limit
            val targetDpi = settings.dpi.coerceIn(72, 600)
            val maxDimPx = ((targetDpi / 72f) * 842f).toInt()
            if (safeBmp.width > maxDimPx || safeBmp.height > maxDimPx) {
                val ratio = minOf(maxDimPx.toFloat() / safeBmp.width, maxDimPx.toFloat() / safeBmp.height)
                val newW = (safeBmp.width * ratio).toInt().coerceAtLeast(1)
                val newH = (safeBmp.height * ratio).toInt().coerceAtLeast(1)
                val scaledBmp = Bitmap.createScaledBitmap(safeBmp, newW, newH, true)
                if (safeBmp !== bitmap) safeBmp.recycle()
                safeBmp = scaledBmp
            } else {
                val scaleFactor = targetDpi / 300f
                if (scaleFactor < 0.9f) {
                    val newW = (safeBmp.width * scaleFactor).toInt().coerceAtLeast(1)
                    val newH = (safeBmp.height * scaleFactor).toInt().coerceAtLeast(1)
                    val scaledBmp = Bitmap.createScaledBitmap(safeBmp, newW, newH, true)
                    if (safeBmp !== bitmap) safeBmp.recycle()
                    safeBmp = scaledBmp
                }
            }

            val margin = 20f
            val availW = pageWidth - 2 * margin
            val availH = pageHeight - 2 * margin - 56f
            val bmpW = safeBmp.width.toFloat().coerceAtLeast(1f)
            val bmpH = safeBmp.height.toFloat().coerceAtLeast(1f)
            val scale = minOf(availW / bmpW, availH / bmpH)
            val drawW = bmpW * scale
            val drawH = bmpH * scale
            val left = margin + (availW - drawW) / 2f
            val top = margin + 28f + (availH - drawH) / 2f
            val destRect = RectF(left, top, left + drawW, top + drawH)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(safeBmp, null, destRect, paint)
            
            // Memory cleanup
            if (safeBmp !== bitmap) safeBmp.recycle()

            // Kopfzeile mit Metadaten
            val headerPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 8.5f
                isAntiAlias = true
            }
            canvas.drawText("myDocAnizer • $mainCategory ➔ $subCategory", margin, margin + 14f, headerPaint)
            if (docType.isNotBlank()) {
                canvas.drawText("Doc-Typ: $docType", pageWidth - margin - 120f, margin + 14f, headerPaint)
            }

            // Fußzeile: Seitenzahl & unscheinbarer OCR-Hinweis
            val footerPaint = Paint().apply {
                color = Color.GRAY
                textSize = 8f
                isAntiAlias = true
            }
            canvas.drawText("Seite ${index + 1} von ${pageBitmaps.size}", margin, pageHeight - margin + 14f, footerPaint)

            document.finishPage(page)
        }

        try {
            FileOutputStream(tempFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }
    }
)

    /**
     * Erstellt ein formatiertes, durchsuchbares A4-PDF-Dokument aus einer E-Mail mit Kopfdaten (mehrseitige Unterstützung)
     */
    suspend fun generateEmailPdf(
        context: Context,
        sender: String,
        recipient: String,
        dateStr: String,
        subject: String,
        bodyText: String,
        targetDir: File,
        internalName: String,
        isEncrypted: Boolean,
        encryptionKey: ByteArray? = null,
        passwordKey: String? = null,
        settings: PdfSettings
    ): File = secureSave(
        context = context,
        targetDir = targetDir,
        internalFileName = internalName,
        isEncrypted = isEncrypted,
        encryptionKey = encryptionKey,
        passwordKey = passwordKey,
        inputAction = { tempFile ->
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            var pageNumber = 1

        val bgPaint = Paint().apply { color = Color.WHITE }
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 8f
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 11f
            isAntiAlias = true
        }

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // Weißer Hintergrund
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        // Header-Kasten
        val headerBoxPaint = Paint().apply {
            color = Color.rgb(241, 245, 249) // Slate 100
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 130f, headerBoxPaint)

        // Oberer blauer Akzentstreifen
        val barPaint = Paint().apply {
            color = Color.rgb(37, 99, 235)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 5f, barPaint)

        // App-Label
        val labelPaint = Paint().apply {
            color = Color.rgb(37, 99, 235)
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }
        canvas.drawText("MYDOCANIZER • E-MAIL ARCHIVBELEG", 36f, 26f, labelPaint)

        // Betreff / Subject
        val subjPaint = Paint().apply {
            color = Color.rgb(15, 23, 42)
            textSize = 15f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val safeSubj = if (subject.length > 55) subject.take(52) + "..." else subject
        canvas.drawText(safeSubj.ifBlank { "E-Mail ohne Betreff" }, 36f, 50f, subjPaint)

        // Header-Felder
        val metaPaint = Paint().apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10.5f
            isAntiAlias = true
        }
        canvas.drawText("Von: ${sender.ifBlank { "Unbekannt" }}", 36f, 74f, metaPaint)
        if (recipient.isNotBlank()) {
            canvas.drawText("An: $recipient", 36f, 92f, metaPaint)
        }
        canvas.drawText("Datum: ${dateStr.ifBlank { "Unbekannt" }}", 36f, 110f, metaPaint)

        // Trennlinie
        val divPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        canvas.drawLine(36f, 142f, (pageWidth - 36).toFloat(), 142f, divPaint)

        var y = 170f

        fun startNewPage() {
            canvas.drawText("Archiviert mit myDocAnizer • Volltext-indexiert (Seite $pageNumber)", 36f, (pageHeight - 20).toFloat(), footerPaint)
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
            y = 50f
        }

        val lines = bodyText.split("\n")
        for (rawLine in lines) {
            val words = rawLine.split(" ")
            var currentLine = ""
            for (w in words) {
                if ((currentLine + w).length > 70) {
                    if (y > pageHeight - 50) {
                        startNewPage()
                    }
                    canvas.drawText(currentLine, 36f, y, bodyPaint)
                    y += 16f
                    currentLine = "$w "
                } else {
                    currentLine += "$w "
                }
            }
            if (currentLine.isNotBlank()) {
                if (y > pageHeight - 50) {
                    startNewPage()
                }
                canvas.drawText(currentLine, 36f, y, bodyPaint)
                y += 16f
            }
        }

        canvas.drawText("Archiviert mit myDocAnizer • Volltext-indexiert (Seite $pageNumber)", 36f, (pageHeight - 20).toFloat(), footerPaint)
        document.finishPage(page)

        try {
            FileOutputStream(tempFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }
    }
)

    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(passwordChars, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        try {
            val secretKey = factory.generateSecret(spec)
            val keyBytes = secretKey.encoded
            val keySpec = SecretKeySpec(keyBytes, "AES")
            keyBytes.fill(0)
            return keySpec
        } finally {
            spec.clearPassword()
            passwordChars.fill('\u0000')
        }
    }

    /**
     * Client-side AES-256-GCM Encryption mit PBKDF2 (100.000 Iterationen),
     * 16-Byte kryptografischem Salt, 12-Byte Zufalls-IV und 128-Bit Authentifizierungs-Tag.
     * Nutzt 64KB Streaming-Puffer, um OutOfMemoryErrors bei großen Dateien zu verhindern.
     */
    fun encryptFile(inputFile: File, outputFile: File, encryptionKey: ByteArray): File {
        val salt = ByteArray(SALT_LENGTH_BYTES) // Salt is kept for structure consistency, though DEK is already random
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(iv)

        val secretKey = SecretKeySpec(encryptionKey, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            fos.write(salt)
            fos.write(iv)

            FileInputStream(inputFile).use { fis ->
                val buffer = ByteArray(64 * 1024)
                try {
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val outputChunk = cipher.update(buffer, 0, bytesRead)
                        if (outputChunk != null && outputChunk.isNotEmpty()) {
                            fos.write(outputChunk)
                        }
                    }
                    val finalChunk = cipher.doFinal()
                    if (finalChunk != null && finalChunk.isNotEmpty()) {
                        fos.write(finalChunk)
                    }
                } finally {
                    buffer.fill(0)
                }
            }
        }
        return outputFile
    }

    fun encryptFile(inputFile: File, outputFile: File, passwordKey: String): File {
        if (passwordKey.isBlank()) {
            throw IllegalArgumentException("Master-Passwort darf für die Verschlüsselung nicht leer sein")
        }
        val salt = ByteArray(SALT_LENGTH_BYTES)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(salt)
        secureRandom.nextBytes(iv)

        val secretKey = deriveKey(passwordKey, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        outputFile.parentFile?.mkdirs()
        FileOutputStream(outputFile).use { fos ->
            fos.write(salt)
            fos.write(iv)

            FileInputStream(inputFile).use { fis ->
                val buffer = ByteArray(64 * 1024)
                try {
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val outputChunk = cipher.update(buffer, 0, bytesRead)
                        if (outputChunk != null && outputChunk.isNotEmpty()) {
                            fos.write(outputChunk)
                        }
                    }
                    val finalChunk = cipher.doFinal()
                    if (finalChunk != null && finalChunk.isNotEmpty()) {
                        fos.write(finalChunk)
                    }
                } finally {
                    buffer.fill(0)
                }
            }
        }
        return outputFile
    }

    /**
     * Entschlüsselt eine AES-256-GCM Datei (.enc) mit PBKDF2 Schlüsselableitung.
     * Nutzt 64KB Streaming-Puffer für minimale Speichernutzung.
     */
    fun decryptFile(inputFile: File, outputFile: File, encryptionKey: ByteArray): Boolean {
        return try {
            val fileLength = inputFile.length()
            val minHeaderSize = SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES + 16
            if (fileLength < minHeaderSize) return false

            FileInputStream(inputFile).use { fis ->
                val salt = ByteArray(SALT_LENGTH_BYTES)
                var readTotal = 0
                while (readTotal < SALT_LENGTH_BYTES) {
                    val r = fis.read(salt, readTotal, SALT_LENGTH_BYTES - readTotal)
                    if (r == -1) return false
                    readTotal += r
                }

                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                readTotal = 0
                while (readTotal < GCM_IV_LENGTH_BYTES) {
                    val r = fis.read(iv, readTotal, GCM_IV_LENGTH_BYTES - readTotal)
                    if (r == -1) return false
                    readTotal += r
                }

                val secretKey = SecretKeySpec(encryptionKey, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    try {
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            val outputChunk = cipher.update(buffer, 0, bytesRead)
                            if (outputChunk != null && outputChunk.isNotEmpty()) {
                                fos.write(outputChunk)
                            }
                        }
                        val finalChunk = cipher.doFinal()
                        if (finalChunk != null && finalChunk.isNotEmpty()) {
                            fos.write(finalChunk)
                        }
                    } finally {
                        buffer.fill(0)
                    }
                }
            }
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        }
    }

    fun decryptFile(inputFile: File, outputFile: File, passwordKey: String): Boolean {
        if (passwordKey.isBlank()) return false
        return try {
            val fileLength = inputFile.length()
            val minHeaderSize = SALT_LENGTH_BYTES + GCM_IV_LENGTH_BYTES + 16
            if (fileLength < minHeaderSize) return false

            FileInputStream(inputFile).use { fis ->
                val salt = ByteArray(SALT_LENGTH_BYTES)
                var readTotal = 0
                while (readTotal < SALT_LENGTH_BYTES) {
                    val r = fis.read(salt, readTotal, SALT_LENGTH_BYTES - readTotal)
                    if (r == -1) return false
                    readTotal += r
                }

                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                readTotal = 0
                while (readTotal < GCM_IV_LENGTH_BYTES) {
                    val r = fis.read(iv, readTotal, GCM_IV_LENGTH_BYTES - readTotal)
                    if (r == -1) return false
                    readTotal += r
                }

                val secretKey = deriveKey(passwordKey, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    try {
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            val outputChunk = cipher.update(buffer, 0, bytesRead)
                            if (outputChunk != null && outputChunk.isNotEmpty()) {
                                fos.write(outputChunk)
                            }
                        }
                        val finalChunk = cipher.doFinal()
                        if (finalChunk != null && finalChunk.isNotEmpty()) {
                            fos.write(finalChunk)
                        }
                    } finally {
                        buffer.fill(0)
                    }
                }
            }
            true
        } catch (e: Exception) {
            outputFile.delete()
            false
        }
    }

    /**
     * 1-Klick-Sicherung & Export:
     * Packt alle archivierten Dokumente und die Index-Metadaten in ein ZIP-Container
     * und verschlüsselt diesen vollständig clientseitig mit AES-256-GCM.
     */
    private fun documentToJson(doc: DocumentEntity): JSONObject {
        return JSONObject().apply {
            put("id", doc.id)
            put("title", doc.title)
            put("sender", doc.sender)
            put("fileName", doc.fileName)
            put("filePath", doc.filePath)
            put("mainCategory", doc.mainCategory)
            put("subCategory", doc.subCategory)
            put("mainCategoryId", doc.mainCategoryId)
            put("subCategoryId", doc.subCategoryId)
            put("docType", doc.docType)
            put("tags", doc.tags)
            put("ocrText", doc.ocrText)
            put("colorMode", doc.colorMode)
            put("pageCount", doc.pageCount)
            put("fileSizeFormatted", doc.fileSizeFormatted)
            put("isEncrypted", doc.isEncrypted)
            put("isSynced", doc.isSynced)
            put("createdAt", doc.createdAt)
            put("isDeleted", doc.isDeleted)
            if (doc.deletedAt != null) put("deletedAt", doc.deletedAt)
            if (doc.contractEndDate != null) put("contractEndDate", doc.contractEndDate)
            if (doc.cancellationDeadline != null) put("cancellationDeadline", doc.cancellationDeadline)
            put("reminderDaysBefore", doc.reminderDaysBefore)
            put("hasCalendarReminder", doc.hasCalendarReminder)
            put("calendarEventType", doc.calendarEventType)
            put("reminderNotes", doc.reminderNotes)
            if (doc.amount != null) put("amount", doc.amount)
            put("customIcon", doc.customIcon)
            put("companyLogo", doc.companyLogo)
        }
    }

    private fun jsonToDocument(obj: JSONObject, targetDir: File, defaultId: Long): DocumentEntity {
        val id = obj.optLong("id", defaultId)
        val fileName = obj.optString("fileName", "doc_$id.pdf")
        val safeName = File(fileName).name
        val uniqueRestoredFile = File(targetDir, "${id}_$safeName")
        val legacyRestoredFile = File(targetDir, safeName)
        val resolvedPath = when {
            uniqueRestoredFile.exists() -> uniqueRestoredFile.absolutePath
            legacyRestoredFile.exists() -> legacyRestoredFile.absolutePath
            else -> obj.optString("filePath", uniqueRestoredFile.absolutePath)
        }
        return DocumentEntity(
            id = id,
            title = obj.optString("title", "Wiederhergestelltes Dokument"),
            sender = obj.optString("sender", ""),
            fileName = fileName,
            filePath = resolvedPath,
            mainCategory = obj.optString("mainCategory", "Allgemein"),
            subCategory = obj.optString("subCategory", "Diverses"),
            mainCategoryId = obj.optString("mainCategoryId", "A01"),
            subCategoryId = obj.optString("subCategoryId", "B1.01"),
            docType = obj.optString("docType", "Dokument"),
            tags = obj.optString("tags", ""),
            ocrText = obj.optString("ocrText", ""),
            colorMode = obj.optString("colorMode", "BW"),
            pageCount = obj.optInt("pageCount", 1),
            fileSizeFormatted = obj.optString("fileSizeFormatted", "${if (File(resolvedPath).exists()) (File(resolvedPath).length() / 1024).coerceAtLeast(1) else 10} KB"),
            isEncrypted = obj.optBoolean("isEncrypted", true),
            isSynced = obj.optBoolean("isSynced", false),
            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
            isDeleted = obj.optBoolean("isDeleted", false),
            deletedAt = if (obj.has("deletedAt") && !obj.isNull("deletedAt")) obj.getLong("deletedAt") else null,
            contractEndDate = if (obj.has("contractEndDate") && !obj.isNull("contractEndDate")) obj.getLong("contractEndDate") else null,
            cancellationDeadline = if (obj.has("cancellationDeadline") && !obj.isNull("cancellationDeadline")) obj.getLong("cancellationDeadline") else null,
            reminderDaysBefore = obj.optInt("reminderDaysBefore", 14),
            hasCalendarReminder = obj.optBoolean("hasCalendarReminder", false),
            calendarEventType = obj.optString("calendarEventType", "BOTH"),
            reminderNotes = obj.optString("reminderNotes", ""),
            amount = if (obj.has("amount") && !obj.isNull("amount")) obj.getDouble("amount") else null,
            customIcon = obj.optString("customIcon", ""),
            companyLogo = obj.optString("companyLogo", "")
        )
    }

    data class VaultMetadata(
        val customFields: List<CustomFieldEntity> = emptyList(),
        val customFieldValues: List<DocumentCustomFieldValueEntity> = emptyList(),
        val cashTransactions: List<CashTransactionEntity> = emptyList(),
        val bankStatementEntries: List<BankStatementEntryEntity> = emptyList(),
        val customDashboardWidgets: List<CustomDashboardWidgetEntity> = emptyList(),
        val rules: List<DocRule> = emptyList()
    )

    fun vaultMetadataToJson(metadata: VaultMetadata): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())

        val cfArr = JSONArray()
        metadata.customFields.forEach { f ->
            cfArr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("description", f.description)
                put("type", f.type.name)
                put("options", f.options)
                put("scope", f.scope.name)
                put("targetMainCategory", f.targetMainCategory)
                put("targetSubCategory", f.targetSubCategory)
                put("defaultValue", f.defaultValue)
                put("isRequired", f.isRequired)
                put("iconName", f.iconName)
                put("createdAt", f.createdAt)
            })
        }
        root.put("custom_fields", cfArr)

        val cfvArr = JSONArray()
        metadata.customFieldValues.forEach { v ->
            cfvArr.put(JSONObject().apply {
                put("documentId", v.documentId)
                put("customFieldId", v.customFieldId)
                put("fieldValue", v.fieldValue)
                put("updatedAt", v.updatedAt)
            })
        }
        root.put("document_custom_field_values", cfvArr)

        val txArr = JSONArray()
        metadata.cashTransactions.forEach { tx ->
            txArr.put(JSONObject().apply {
                put("id", tx.id)
                put("title", tx.title)
                put("amount", tx.amount)
                put("type", tx.type.name)
                put("category", tx.category)
                put("date", tx.date)
                if (tx.relatedDocumentId != null) put("relatedDocumentId", tx.relatedDocumentId)
                put("note", tx.note)
                put("isMatchedWithBankStatement", tx.isMatchedWithBankStatement)
                if (tx.matchReconciliationMonth != null) put("matchReconciliationMonth", tx.matchReconciliationMonth)
            })
        }
        root.put("cash_transactions", txArr)

        val bsArr = JSONArray()
        metadata.bankStatementEntries.forEach { b ->
            bsArr.put(JSONObject().apply {
                put("id", b.id)
                put("date", b.date)
                put("bookingText", b.bookingText)
                put("purpose", b.purpose)
                put("amount", b.amount)
                put("isCashWithdrawal", b.isCashWithdrawal)
                if (b.matchedCashTransactionId != null) put("matchedCashTransactionId", b.matchedCashTransactionId)
                put("monthYear", b.monthYear)
            })
        }
        root.put("bank_statement_entries", bsArr)

        val widgetArr = JSONArray()
        metadata.customDashboardWidgets.forEach { w ->
            widgetArr.put(JSONObject().apply {
                put("id", w.id)
                put("title", w.title)
                put("subtitle", w.subtitle)
                put("widgetType", w.widgetType.name)
                put("targetMainCategory", w.targetMainCategory)
                put("targetSubCategory", w.targetSubCategory)
                put("targetCustomFieldId", w.targetCustomFieldId)
                put("targetCustomFieldValue", w.targetCustomFieldValue)
                put("numericLimit", w.numericLimit)
                put("noteContent", w.noteContent)
                put("colorHex", w.colorHex)
                put("iconName", w.iconName)
                put("isEnabled", w.isEnabled)
                put("position", w.position)
                put("createdAt", w.createdAt)
            })
        }
        root.put("custom_dashboard_widgets", widgetArr)

        val rulesArr = JSONArray()
        metadata.rules.forEach { r ->
            rulesArr.put(DocRuleRepository.ruleToJson(r))
        }
        root.put("rules", rulesArr)

        return root
    }

    fun jsonToVaultMetadata(obj: JSONObject): VaultMetadata {
        val customFields = mutableListOf<CustomFieldEntity>()
        val cfArr = obj.optJSONArray("custom_fields")
        if (cfArr != null) {
            for (i in 0 until cfArr.length()) {
                val o = cfArr.getJSONObject(i)
                customFields.add(
                    CustomFieldEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "Feld"),
                        description = o.optString("description", ""),
                        type = try { CustomFieldType.valueOf(o.optString("type", "TEXT")) } catch (_: Exception) { CustomFieldType.TEXT },
                        options = o.optString("options", ""),
                        scope = try { CustomFieldScope.valueOf(o.optString("scope", "GLOBAL")) } catch (_: Exception) { CustomFieldScope.GLOBAL },
                        targetMainCategory = o.optString("targetMainCategory", ""),
                        targetSubCategory = o.optString("targetSubCategory", ""),
                        defaultValue = o.optString("defaultValue", ""),
                        isRequired = o.optBoolean("isRequired", false),
                        iconName = o.optString("iconName", "label"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val customFieldValues = mutableListOf<DocumentCustomFieldValueEntity>()
        val cfvArr = obj.optJSONArray("document_custom_field_values")
        if (cfvArr != null) {
            for (i in 0 until cfvArr.length()) {
                val o = cfvArr.getJSONObject(i)
                customFieldValues.add(
                    DocumentCustomFieldValueEntity(
                        documentId = o.getLong("documentId"),
                        customFieldId = o.getString("customFieldId"),
                        fieldValue = o.optString("fieldValue", ""),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val cashTransactions = mutableListOf<CashTransactionEntity>()
        val txArr = obj.optJSONArray("cash_transactions")
        if (txArr != null) {
            for (i in 0 until txArr.length()) {
                val o = txArr.getJSONObject(i)
                cashTransactions.add(
                    CashTransactionEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        title = o.optString("title", ""),
                        amount = o.optDouble("amount", 0.0),
                        type = try { CashTransactionType.valueOf(o.optString("type", "EXPENSE")) } catch (_: Exception) { CashTransactionType.EXPENSE },
                        category = o.optString("category", "Allgemein"),
                        date = o.optLong("date", System.currentTimeMillis()),
                        relatedDocumentId = if (o.has("relatedDocumentId") && !o.isNull("relatedDocumentId")) o.getLong("relatedDocumentId") else null,
                        note = o.optString("note", ""),
                        isMatchedWithBankStatement = o.optBoolean("isMatchedWithBankStatement", false),
                        matchReconciliationMonth = if (o.has("matchReconciliationMonth") && !o.isNull("matchReconciliationMonth")) o.getString("matchReconciliationMonth") else null
                    )
                )
            }
        }

        val bankStatementEntries = mutableListOf<BankStatementEntryEntity>()
        val bsArr = obj.optJSONArray("bank_statement_entries")
        if (bsArr != null) {
            for (i in 0 until bsArr.length()) {
                val o = bsArr.getJSONObject(i)
                bankStatementEntries.add(
                    BankStatementEntryEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        date = o.optLong("date", System.currentTimeMillis()),
                        bookingText = o.optString("bookingText", ""),
                        purpose = o.optString("purpose", ""),
                        amount = o.optDouble("amount", 0.0),
                        isCashWithdrawal = o.optBoolean("isCashWithdrawal", false),
                        matchedCashTransactionId = if (o.has("matchedCashTransactionId") && !o.isNull("matchedCashTransactionId")) o.getString("matchedCashTransactionId") else null,
                        monthYear = o.optString("monthYear", "")
                    )
                )
            }
        }

        val widgets = mutableListOf<CustomDashboardWidgetEntity>()
        val widgetArr = obj.optJSONArray("custom_dashboard_widgets")
        if (widgetArr != null) {
            for (i in 0 until widgetArr.length()) {
                val o = widgetArr.getJSONObject(i)
                widgets.add(
                    CustomDashboardWidgetEntity(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        title = o.optString("title", ""),
                        subtitle = o.optString("subtitle", ""),
                        widgetType = try { CustomWidgetType.valueOf(o.optString("widgetType", "FOLDER_MONITOR")) } catch (_: Exception) { CustomWidgetType.FOLDER_MONITOR },
                        targetMainCategory = o.optString("targetMainCategory", ""),
                        targetSubCategory = o.optString("targetSubCategory", ""),
                        targetCustomFieldId = o.optString("targetCustomFieldId", ""),
                        targetCustomFieldValue = o.optString("targetCustomFieldValue", ""),
                        numericLimit = o.optDouble("numericLimit", 0.0),
                        noteContent = o.optString("noteContent", ""),
                        colorHex = o.optLong("colorHex", 0xFF2563EB),
                        iconName = o.optString("iconName", "Folder"),
                        isEnabled = o.optBoolean("isEnabled", true),
                        position = o.optInt("position", 0),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val rules = mutableListOf<DocRule>()
        val rulesArr = obj.optJSONArray("rules")
        if (rulesArr != null) {
            for (i in 0 until rulesArr.length()) {
                rules.add(DocRuleRepository.jsonToRule(rulesArr.getJSONObject(i)))
            }
        }

        return VaultMetadata(
            customFields = customFields,
            customFieldValues = customFieldValues,
            cashTransactions = cashTransactions,
            bankStatementEntries = bankStatementEntries,
            customDashboardWidgets = widgets,
            rules = rules
        )
    }

    /**
     * 1-Klick-Sicherung & Export:
     * Packt alle archivierten Dokumente, Index-Metadaten (doc_index.json) sowie
     * optional den gesamten Vault-Zustand (vault_metadata.json: Zusatzfelder,
     * Transaktionen, Dashboard-Widgets, Regeln) in einen ZIP-Container
     * und verschlüsselt diesen vollständig clientseitig mit AES-256-GCM.
     */
    suspend fun createEncryptedBackupArchive(
        context: Context,
        docs: List<DocumentEntity>,
        outputEncFile: File,
        passwordKey: String,
        vaultMetadata: VaultMetadata? = null,
        includeVaultMetadata: Boolean = true
    ): File = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "backup_bundle_${UUID.randomUUID()}.zip")
        try {
            val metadataToSave = if (vaultMetadata != null) {
                vaultMetadata
            } else if (includeVaultMetadata) {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val ruleRepo = DocRuleRepository(context)
                    VaultMetadata(
                        customFields = db.customFieldDao().getAllCustomFieldsList(),
                        customFieldValues = db.customFieldDao().getAllDocumentCustomFieldValuesList(),
                        cashTransactions = db.financeDao().getAllCashTransactionsList(),
                        bankStatementEntries = db.financeDao().getAllBankStatementEntriesList(),
                        customDashboardWidgets = db.customDashboardWidgetDao().getAllWidgetsList(),
                        rules = ruleRepo.rules.value
                    )
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
                // 1. Central Disaster Recovery Index beilegen (abwärtskompatibel zu doc_index.json)
                val jsonArray = JSONArray()
                docs.forEach { doc ->
                    jsonArray.put(documentToJson(doc))
                }
                val indexEntry = ZipEntry("doc_index.json")
                zos.putNextEntry(indexEntry)
                zos.write(jsonArray.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. Vault-Metadaten beilegen
                if (metadataToSave != null) {
                    val metaObj = vaultMetadataToJson(metadataToSave)
                    val metaEntry = ZipEntry("vault_metadata.json")
                    zos.putNextEntry(metaEntry)
                    zos.write(metaObj.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                // 3. Dokument-PDFs archivieren (mit eindeutigen Präfixen zur Vermeidung von ZipException Kollisionen)
                for (doc in docs) {
                    val file = File(doc.filePath)
                    if (file.exists()) {
                        val safeName = File(doc.fileName).name
                        val uniqueArchiveName = "${doc.id}_$safeName"
                        val entry = ZipEntry("documents/$uniqueArchiveName")
                        zos.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            encryptFile(tempZip, outputEncFile, passwordKey)
            outputEncFile
        } finally {
            if (tempZip.exists()) tempZip.delete()
        }
    }

    /**
     * Stellt ein verschlüsseltes Backup-Archiv (.enc) wieder her:
     * 1. Entschlüsselt die Datei mit dem Master-Passwort nach temporärem ZIP
     * 2. Extrahiert alle Dokumente in das Dokumentenverzeichnis über getDocumentDirectory (2-Ebenen-Ordnerstruktur)
     * 3. Stellt bei vorhandener vault_metadata.json alle Zusatzfelder, Transaktionen, Widgets und Regeln wieder her
     * 4. Liest doc_index.json und gibt die Liste wiederhergestellter DocumentEntity zurück
     */
    suspend fun restoreEncryptedBackupArchive(
        context: Context,
        inputEncFile: File,
        passwordKey: String,
        database: AppDatabase = AppDatabase.getDatabase(context),
        ruleRepository: DocRuleRepository = DocRuleRepository(context)
    ): Result<List<DocumentEntity>> = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "restore_bundle_${UUID.randomUUID()}.zip")
        val tempExtractDir = File(context.cacheDir, "temp_extract_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            if (!inputEncFile.isFile || inputEncFile.length() <= 0L ||
                inputEncFile.length() > MAX_RESTORE_ENCRYPTED_BYTES) {
                throw SecurityException("Backup-Datei fehlt, ist leer oder überschreitet das erlaubte Größenlimit.")
            }
            val decrypted = decryptFile(inputEncFile, tempZip, passwordKey)
            if (!decrypted || !tempZip.isFile || tempZip.length() > MAX_RESTORE_TOTAL_BYTES) {
                return@withContext Result.failure(Exception("Entschlüsselung fehlgeschlagen oder Backup überschreitet das Größenlimit."))
            }

            var indexJsonString: String? = null
            var vaultMetadataJsonString: String? = null
            var totalExpandedBytes = 0L
            var entryCount = 0
            val seenEntryNames = HashSet<String>()

            // 1. Validate and stage the whole archive before mutating app data.
            java.util.zip.ZipInputStream(tempZip.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    entryCount++
                    if (entryCount > MAX_RESTORE_ENTRIES) {
                        throw SecurityException("Zu viele Einträge im Backup-Archiv.")
                    }
                    val entryName = entry.name
                    if (entryName.length > MAX_RESTORE_ENTRY_NAME_CHARS ||
                        entryName.any { it.isISOControl() } ||
                        !seenEntryNames.add(entryName)) {
                        throw SecurityException("Ungültiger oder doppelter Archiveintrag.")
                    }
                    if (entry.size > MAX_RESTORE_DOCUMENT_BYTES) {
                        throw SecurityException("Archiveintrag überschreitet das Größenlimit.")
                    }

                    when {
                        entryName == "doc_index.json" && !entry.isDirectory -> {
                            if (indexJsonString != null) throw SecurityException("Doppelter Dokumentindex im Backup.")
                            val bytes = readCurrentZipEntryBounded(zis, minOf(MAX_RESTORE_INDEX_BYTES, MAX_RESTORE_TOTAL_BYTES - totalExpandedBytes))
                            totalExpandedBytes += bytes.size
                            if (totalExpandedBytes > MAX_RESTORE_TOTAL_BYTES) {
                                bytes.fill(0)
                                throw SecurityException("Backup überschreitet das Gesamtgrößenlimit.")
                            }
                            indexJsonString = try { String(bytes, Charsets.UTF_8) } finally { bytes.fill(0) }
                        }
                        entryName == "vault_metadata.json" && !entry.isDirectory -> {
                            if (vaultMetadataJsonString != null) throw SecurityException("Doppelte Tresor-Metadaten im Backup.")
                            val bytes = readCurrentZipEntryBounded(zis, minOf(MAX_RESTORE_METADATA_BYTES, MAX_RESTORE_TOTAL_BYTES - totalExpandedBytes))
                            totalExpandedBytes += bytes.size
                            if (totalExpandedBytes > MAX_RESTORE_TOTAL_BYTES) {
                                bytes.fill(0)
                                throw SecurityException("Backup überschreitet das Gesamtgrößenlimit.")
                            }
                            vaultMetadataJsonString = try { String(bytes, Charsets.UTF_8) } finally { bytes.fill(0) }
                        }
                        entryName.startsWith("documents/") && !entry.isDirectory -> {
                            if (!isSafeBackupDocumentEntryName(entryName)) {
                                throw SecurityException("Ungültiger Dokumentpfad im Backup.")
                            }
                            val safeFileName = entryName.substringAfter("documents/")
                            val destFile = File(tempExtractDir, safeFileName)
                            if (destFile.canonicalFile.parentFile != tempExtractDir.canonicalFile) {
                                throw SecurityException("Ungültiger Pfad im Archiv erkannt (Zip Slip Schutz).")
                            }
                            val copiedBytes = copyCurrentZipEntryBounded(zis, destFile, minOf(MAX_RESTORE_DOCUMENT_BYTES, MAX_RESTORE_TOTAL_BYTES - totalExpandedBytes))
                            totalExpandedBytes += copiedBytes
                            if (totalExpandedBytes > MAX_RESTORE_TOTAL_BYTES) {
                                destFile.delete()
                                throw SecurityException("Backup überschreitet das Gesamtgrößenlimit.")
                            }
                        }
                        else -> throw SecurityException("Nicht unterstützter Archiveintrag im Backup.")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // The index is mandatory and parsed before any DB writes occur.
            val parsedIndex = indexJsonString?.let { JSONArray(it) }
                ?: throw SecurityException("Backup enthält keinen Dokumentindex.")
            if (parsedIndex.length() > MAX_RESTORE_ENTRIES) {
                throw SecurityException("Zu viele Dokumente im Backup-Index.")
            }
            for (index in 0 until parsedIndex.length()) {
                // Validate every entry's JSON type before restoring metadata or files.
                parsedIndex.getJSONObject(index)
            }
            val metadataToRestore = vaultMetadataJsonString?.let {
                try { jsonToVaultMetadata(JSONObject(it)) }
                catch (e: Exception) { throw SecurityException("Tresor-Metadaten im Backup sind ungültig.", e) }
            }

            // 2. Restore optional metadata only after archive structure and JSON validation.
            if (metadataToRestore != null) {
                val metadata = metadataToRestore
                if (metadata.customFields.isNotEmpty()) {
                    database.customFieldDao().insertCustomFields(metadata.customFields)
                }
                if (metadata.customFieldValues.isNotEmpty()) {
                    database.customFieldDao().insertOrUpdateFieldValues(metadata.customFieldValues)
                }
                if (metadata.cashTransactions.isNotEmpty()) {
                    database.financeDao().insertCashTransactions(metadata.cashTransactions)
                }
                if (metadata.bankStatementEntries.isNotEmpty()) {
                    database.financeDao().insertBankStatementEntries(metadata.bankStatementEntries)
                }
                if (metadata.customDashboardWidgets.isNotEmpty()) {
                    database.customDashboardWidgetDao().insertOrUpdateWidgets(metadata.customDashboardWidgets)
                }
                if (metadata.rules.isNotEmpty()) {
                    ruleRepository.addRules(metadata.rules)
                }
            }

            // 3. Restore staged documents in the configured two-level category structure.
            val restoredDocs = mutableListOf<DocumentEntity>()
            run {
                val array = parsedIndex
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optLong("id", System.currentTimeMillis() + i)
                    val rawFileName = obj.optString("fileName", "doc_$id.pdf")
                    val safeFileName = File(rawFileName).name
                    val mainCat = obj.optString("mainCategory", "Allgemein")
                    val subCat = obj.optString("subCategory", "Diverses")
                    val mainCatId = obj.optString("mainCategoryId", "")
                    val subCatId = obj.optString("subCategoryId", "")

                    // Zielverzeichnis über getDocumentDirectory (2-Ebenen-Ordnerstruktur)
                    val targetDir = getDocumentDirectory(
                        context = context,
                        mainCategory = mainCat,
                        subCategory = subCat,
                        mainCategoryId = mainCatId,
                        subCategoryId = subCatId
                    )

                    val targetFile = File(targetDir, safeFileName)
                    val finalDestFile = if (targetFile.exists()) {
                        File(targetDir, "${id}_$safeFileName")
                    } else {
                        targetFile
                    }
                    if (!finalDestFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        throw SecurityException("Ungültiger Zielpfad für Dokument (Zip Slip Schutz).")
                    }

                    // Suche nach der entpackten Datei im tempExtractDir
                    val candidate1 = File(tempExtractDir, "${id}_$safeFileName")
                    val candidate2 = File(tempExtractDir, safeFileName)
                    val sourceFile = when {
                        candidate1.exists() -> candidate1
                        candidate2.exists() -> candidate2
                        else -> null
                    }

                    if (sourceFile != null && sourceFile.exists()) {
                        sourceFile.copyTo(finalDestFile, overwrite = true)
                        sourceFile.delete()
                    }

                    val resolvedPath = if (finalDestFile.exists()) {
                        finalDestFile.absolutePath
                    } else {
                        obj.optString("filePath", finalDestFile.absolutePath)
                    }

                    val docEntity = DocumentEntity(
                        id = id,
                        title = obj.optString("title", "Wiederhergestelltes Dokument"),
                        sender = obj.optString("sender", ""),
                        fileName = safeFileName,
                        filePath = resolvedPath,
                        mainCategory = mainCat,
                        subCategory = subCat,
                        mainCategoryId = if (mainCatId.isNotBlank()) mainCatId else "A01",
                        subCategoryId = if (subCatId.isNotBlank()) subCatId else "B1.01",
                        docType = obj.optString("docType", "Dokument"),
                        tags = obj.optString("tags", ""),
                        ocrText = obj.optString("ocrText", ""),
                        colorMode = obj.optString("colorMode", "BW"),
                        pageCount = obj.optInt("pageCount", 1),
                        fileSizeFormatted = obj.optString(
                            "fileSizeFormatted",
                            "${if (File(resolvedPath).exists()) (File(resolvedPath).length() / 1024).coerceAtLeast(1) else 10} KB"
                        ),
                        isEncrypted = obj.optBoolean("isEncrypted", true),
                        isSynced = obj.optBoolean("isSynced", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        isDeleted = obj.optBoolean("isDeleted", false),
                        deletedAt = if (obj.has("deletedAt") && !obj.isNull("deletedAt")) obj.getLong("deletedAt") else null,
                        contractEndDate = if (obj.has("contractEndDate") && !obj.isNull("contractEndDate")) obj.getLong("contractEndDate") else null,
                        cancellationDeadline = if (obj.has("cancellationDeadline") && !obj.isNull("cancellationDeadline")) obj.getLong("cancellationDeadline") else null,
                        reminderDaysBefore = obj.optInt("reminderDaysBefore", 14),
                        hasCalendarReminder = obj.optBoolean("hasCalendarReminder", false),
                        calendarEventType = obj.optString("calendarEventType", "BOTH"),
                        reminderNotes = obj.optString("reminderNotes", ""),
                        amount = if (obj.has("amount") && !obj.isNull("amount")) obj.getDouble("amount") else null,
                        customIcon = obj.optString("customIcon", ""),
                        companyLogo = obj.optString("companyLogo", "")
                    )
                    restoredDocs.add(docEntity)
                }
            }

            Result.success(restoredDocs)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (tempZip.exists()) tempZip.delete()
            if (tempExtractDir.exists()) tempExtractDir.deleteRecursively()
        }
    }

    /**
     * Checks that a ZIP document entry is exactly one filename segment below documents/.
     * Archive paths are never normalized or flattened into an apparently safe filename.
     */
    internal fun isSafeBackupDocumentEntryName(entryName: String): Boolean {
        if (entryName.length > MAX_RESTORE_ENTRY_NAME_CHARS || entryName.any { it.isISOControl() }) return false
        if (!entryName.startsWith("documents/") || '\\' in entryName) return false
        val child = entryName.substringAfter("documents/", missingDelimiterValue = "")
        return child.isNotBlank() && child != "." && child != ".." &&
            '/' !in child && '\\' !in child
    }

    private fun readCurrentZipEntryBounded(
        input: java.util.zip.ZipInputStream,
        maxBytes: Long
    ): ByteArray {
        require(maxBytes >= 0L) { "Kein Restbudget für Backupdaten vorhanden." }
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                if (total > maxBytes) throw SecurityException("Archiveintrag überschreitet das Größenlimit.")
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        } finally {
            buffer.fill(0)
        }
    }

    private fun copyCurrentZipEntryBounded(
        input: java.util.zip.ZipInputStream,
        destination: File,
        maxBytes: Long
    ): Long {
        if (!destination.createNewFile()) throw SecurityException("Doppelter Dateiname im Backup-Archiv.")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        try {
            FileOutputStream(destination).use { output ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) throw SecurityException("Dokument im Backup überschreitet das Größenlimit.")
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
            return total
        } catch (e: Exception) {
            destination.delete()
            throw e
        } finally {
            buffer.fill(0)
        }
    }

    /**
     * Creates encrypted index for cloud backup
     */
    fun buildEncryptedIndex(context: Context, docs: List<DocumentEntity>, passwordKey: String): File {
        val jsonArray = JSONArray()
        docs.forEach { doc ->
            jsonArray.put(documentToJson(doc))
        }

        val rawFile = File(context.cacheDir, "doc_index_plain.json")
        return try {
            rawFile.writeText(jsonArray.toString())
            val encFile = File(context.cacheDir, "doc_index.enc")
            encryptFile(rawFile, encFile, passwordKey)
            encFile
        } finally {
            if (rawFile.exists()) rawFile.delete()
        }
    }
}
