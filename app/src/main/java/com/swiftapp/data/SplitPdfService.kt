package com.swiftapp.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.swiftapp.data.model.SplitMode
import com.swiftapp.data.model.SplitPdfConfig
import com.swiftapp.data.model.SplitPdfResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

class SplitPdfService(private val context: Context) {

    /**
     * Inspects a PDF to verify accessibility, password protection, and retrieve total page count.
     */
    suspend fun inspectPdf(
        file: File,
        password: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val doc = if (password.isNullOrEmpty()) {
                PDDocument.load(file)
            } else {
                PDDocument.load(file, password)
            }
            val pageCount = doc.numberOfPages
            doc.close()
            Result.success(pageCount)
        } catch (e: InvalidPasswordException) {
            Result.failure(SecurityException("Password required or incorrect password."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Renders a crisp thumbnail preview for a single page.
     */
    suspend fun renderThumbnail(
        file: File,
        pageIndex: Int,
        targetWidth: Int = 300
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

            page = renderer.openPage(pageIndex)
            val aspectRatio = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspectRatio).roundToInt().coerceAtLeast(100)

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (e: Exception) {
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Executes lossless PDF splitting across all 3 modes: Custom Ranges, Extract Pages, and Burst All.
     */
    suspend fun splitPdf(
        sourceFile: File,
        config: SplitPdfConfig,
        selectedPageIndices: List<Int>, // 0-indexed
        password: String? = null,
        customName: String? = null
    ): Result<SplitPdfResult> = withContext(Dispatchers.IO) {
        var srcDoc: PDDocument? = null
        val generatedFiles = mutableListOf<File>()

        try {
            srcDoc = if (password.isNullOrEmpty()) {
                PDDocument.load(sourceFile)
            } else {
                PDDocument.load(sourceFile, password)
            }

            val totalPages = srcDoc.numberOfPages
            val baseName = if (!customName.isNullOrBlank()) {
                customName.removeSuffix(".pdf")
            } else {
                sourceFile.nameWithoutExtension
            }

            val timestamp = System.currentTimeMillis()

            when (config.mode) {
                SplitMode.CUSTOM_RANGES -> {
                    val validRanges = config.ranges.filter { it.isValid(totalPages) }
                    if (validRanges.isEmpty()) {
                        return@withContext Result.failure(IllegalArgumentException("No valid page ranges defined."))
                    }

                    if (config.mergeRangesIntoSingleFile) {
                        // Merge all ranges into 1 PDF
                        val mergedDoc = PDDocument()
                        for (range in validRanges) {
                            for (p in (range.fromPage - 1) until range.toPage) {
                                mergedDoc.importPage(srcDoc.getPage(p))
                            }
                        }
                        val outputFile = File(context.cacheDir, "${baseName}_ranges_merged_$timestamp.pdf")
                        mergedDoc.save(outputFile)
                        mergedDoc.close()
                        generatedFiles.add(outputFile)
                    } else {
                        // Separate PDF for each range
                        validRanges.forEachIndexed { idx, range ->
                            val rangeDoc = PDDocument()
                            for (p in (range.fromPage - 1) until range.toPage) {
                                rangeDoc.importPage(srcDoc.getPage(p))
                            }
                            val rangeFile = File(
                                context.cacheDir,
                                "${baseName}_part_${idx + 1}_p${range.fromPage}-p${range.toPage}_$timestamp.pdf"
                            )
                            rangeDoc.save(rangeFile)
                            rangeDoc.close()
                            generatedFiles.add(rangeFile)
                        }
                    }
                }

                SplitMode.EXTRACT_PAGES -> {
                    val validIndices = selectedPageIndices.filter { it in 0 until totalPages }.sorted()
                    if (validIndices.isEmpty()) {
                        return@withContext Result.failure(IllegalArgumentException("No pages selected for extraction."))
                    }

                    if (config.extractIntoSingleFile) {
                        // Single merged document with all selected pages
                        val extractedDoc = PDDocument()
                        for (idx in validIndices) {
                            extractedDoc.importPage(srcDoc.getPage(idx))
                        }
                        val outputFile = File(context.cacheDir, "${baseName}_extracted_$timestamp.pdf")
                        extractedDoc.save(outputFile)
                        extractedDoc.close()
                        generatedFiles.add(outputFile)
                    } else {
                        // Individual 1-page PDF for each selected page
                        for (idx in validIndices) {
                            val singleDoc = PDDocument()
                            singleDoc.importPage(srcDoc.getPage(idx))
                            val singleFile = File(
                                context.cacheDir,
                                "${baseName}_page_${String.format("%03d", idx + 1)}_$timestamp.pdf"
                            )
                            singleDoc.save(singleFile)
                            singleDoc.close()
                            generatedFiles.add(singleFile)
                        }
                    }
                }

                SplitMode.BURST_ALL -> {
                    // Split every page into its own 1-page PDF
                    for (i in 0 until totalPages) {
                        val singleDoc = PDDocument()
                        singleDoc.importPage(srcDoc.getPage(i))
                        val singleFile = File(
                            context.cacheDir,
                            "${baseName}_page_${String.format("%03d", i + 1)}_$timestamp.pdf"
                        )
                        singleDoc.save(singleFile)
                        singleDoc.close()
                        generatedFiles.add(singleFile)
                    }
                }
            }

            if (generatedFiles.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No output files were generated."))
            }

            // Packaging: Single PDF or ZIP bundle
            if (generatedFiles.size == 1) {
                val singleOutput = generatedFiles.first()
                Result.success(
                    SplitPdfResult(
                        primaryOutputFile = singleOutput,
                        outputFiles = generatedFiles,
                        isSingleFile = true,
                        totalGeneratedFiles = 1,
                        fileSize = singleOutput.length()
                    )
                )
            } else {
                val zipFile = File(context.cacheDir, "${baseName}_split_files_$timestamp.zip")
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    val buffer = ByteArray(8192)
                    for (file in generatedFiles) {
                        FileInputStream(file).use { fis ->
                            val zipEntry = ZipEntry(file.name)
                            zos.putNextEntry(zipEntry)
                            var len: Int
                            while (fis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                            }
                            zos.closeEntry()
                        }
                    }
                }

                Result.success(
                    SplitPdfResult(
                        primaryOutputFile = zipFile,
                        outputFiles = generatedFiles,
                        isSingleFile = false,
                        totalGeneratedFiles = generatedFiles.size,
                        fileSize = zipFile.length()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { srcDoc?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Parses a free-text page range string like "1, 3, 5-8" into a 0-indexed list of page integers.
     */
    fun parsePageRangeInput(input: String, totalPages: Int): List<Int> {
        val clean = input.trim()
        if (clean.isEmpty()) return emptyList()

        val resultIndices = mutableSetOf<Int>()
        val tokens = clean.split(",", ";", " ").map { it.trim() }.filter { it.isNotEmpty() }

        for (token in tokens) {
            if (token.contains("-")) {
                val parts = token.split("-").map { it.trim() }
                if (parts.size == 2) {
                    val start = parts[0].toIntOrNull()
                    val end = parts[1].toIntOrNull()
                    if (start != null && end != null && start in 1..totalPages && end in 1..totalPages) {
                        val rangeStart = minOf(start, end)
                        val rangeEnd = maxOf(start, end)
                        for (p in rangeStart..rangeEnd) {
                            resultIndices.add(p - 1)
                        }
                    }
                }
            } else {
                val pageNum = token.toIntOrNull()
                if (pageNum != null && pageNum in 1..totalPages) {
                    resultIndices.add(pageNum - 1)
                }
            }
        }
        return resultIndices.sorted()
    }

    /**
     * Saves resulting PDF or ZIP file to public Downloads/SwiftPDF directory via MediaStore.
     */
    suspend fun saveToDownloads(file: File, displayName: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val finalName = displayName?.takeIf { it.isNotBlank() } ?: file.name
            val mimeType = if (file.name.endsWith(".zip", ignoreCase = true)) "application/zip" else "application/pdf"
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SwiftPDF")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create download entry in MediaStore"))

                resolver.openOutputStream(uri)?.use { outStream ->
                    file.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Result.success(uri)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val swiftDir = File(downloadsDir, "SwiftPDF").apply { if (!exists()) mkdirs() }
                val destFile = File(swiftDir, finalName)

                file.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success(Uri.fromFile(destFile))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
