package com.swiftapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.swiftapp.data.model.MergeConfig
import com.swiftapp.data.model.MergePdfItem
import com.swiftapp.data.model.PageOrientationMode
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object MergePdfService {

    /**
     * Resolves PDF metadata, detects password encryption, counts total pages,
     * and renders a first-page thumbnail.
     */
    suspend fun extractMetadata(
        context: Context,
        uri: Uri,
        password: String? = null,
    ): MergePdfItem = withContext(Dispatchers.IO) {
        val (fileName, fileSizeBytes) = queryFileDetails(context, uri)
        var pageCount = 0
        var isEncrypted = false
        var isUnlocked = true
        var isCorrupted = false
        var errorMessage: String? = null
        var thumbnail: Bitmap? = null

        // Step 1: Inspect PDF with PDFBox
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return@withContext MergePdfItem(
                    uri = uri,
                    fileName = fileName,
                    fileSizeBytes = fileSizeBytes,
                    pageCount = 0,
                    isCorrupted = true,
                    errorMessage = "Could not open file stream",
                )
            }

            inputStream.use { stream ->
                val document = if (password != null) {
                    PDDocument.load(stream, password)
                } else {
                    PDDocument.load(stream)
                }

                try {
                    if (document.isEncrypted) {
                        isEncrypted = true
                        isUnlocked = password != null
                    }
                    pageCount = document.numberOfPages
                } finally {
                    document.close()
                }
            }
        } catch (e: InvalidPasswordException) {
            isEncrypted = true
            isUnlocked = false
            errorMessage = "Password protected"
        } catch (e: Exception) {
            isCorrupted = true
            errorMessage = e.localizedMessage ?: "Invalid or corrupted PDF file"
        }

        // Step 2: Render first page thumbnail if unlocked and valid
        if (!isCorrupted && (!isEncrypted || isUnlocked)) {
            thumbnail = renderFirstPageThumbnail(context, uri)
        }

        MergePdfItem(
            uri = uri,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            pageCount = pageCount,
            thumbnail = thumbnail,
            pageRange = "All",
            isEncrypted = isEncrypted,
            isUnlocked = !isEncrypted || isUnlocked,
            password = password,
            isCorrupted = isCorrupted,
            errorMessage = errorMessage,
        )
    }

    /**
     * Parse page range string (e.g. "All", "1-3, 5, 8-10") into a list of 0-based page indices.
     */
    fun parsePageRange(rangeStr: String, totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        val trimmed = rangeStr.trim()
        if (trimmed.isEmpty() || trimmed.equals("All", ignoreCase = true)) {
            return (0 until totalPages).toList()
        }

        val resultIndices = linkedSetOf<Int>()
        val parts = trimmed.split(",")

        for (part in parts) {
            val token = part.trim()
            if (token.isEmpty()) continue

            if (token.contains("-")) {
                val rangeParts = token.split("-")
                if (rangeParts.size == 2) {
                    val start = rangeParts[0].trim().toIntOrNull()
                    val end = rangeParts[1].trim().toIntOrNull()
                    if (start != null && end != null && start > 0 && end > 0) {
                        val minVal = minOf(start, end)
                        val maxVal = maxOf(start, end)
                        for (p in minVal..maxVal) {
                            if (p in 1..totalPages) {
                                resultIndices.add(p - 1)
                            }
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

        return if (resultIndices.isEmpty()) {
            (0 until totalPages).toList()
        } else {
            resultIndices.toList()
        }
    }

    /**
     * Executes the merge operation with custom page ranges and orientation normalization.
     */
    suspend fun performMerge(
        context: Context,
        items: List<MergePdfItem>,
        config: MergeConfig,
        onProgress: (step: String, progress: Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(items.size >= 2) { "At least 2 valid PDF files are required to merge." }

            onProgress("Initializing merge engine...", 0.10f)

            val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
            val finalFileName = if (config.outputFileName.endsWith(".pdf", ignoreCase = true)) {
                config.outputFileName
            } else {
                "${config.outputFileName}.pdf"
            }
            val destinationFile = File(outputDir, finalFileName)

            val targetDoc = PDDocument()
            val openSourceDocs = mutableListOf<PDDocument>()
            val openStreams = mutableListOf<InputStream>()

            try {
                val totalItems = items.size
                var totalPagesMerged = 0

                for ((index, item) in items.withIndex()) {
                    val stepPercent = 0.15f + (0.75f * (index.toFloat() / totalItems.toFloat()))
                    onProgress("Processing '${item.fileName}'...", stepPercent)

                    val inputStream = context.contentResolver.openInputStream(item.uri)
                        ?: throw IllegalArgumentException("Cannot open stream for ${item.fileName}")
                    openStreams.add(inputStream)

                    val srcDoc = if (item.password != null) {
                        PDDocument.load(inputStream, item.password)
                    } else {
                        PDDocument.load(inputStream)
                    }
                    openSourceDocs.add(srcDoc)

                    val selectedIndices = parsePageRange(item.pageRange, srcDoc.numberOfPages)
                    for (pageIdx in selectedIndices) {
                        if (pageIdx in 0 until srcDoc.numberOfPages) {
                            val srcPage = srcDoc.getPage(pageIdx)
                            val importedPage: PDPage = targetDoc.importPage(srcPage)

                            // Apply Orientation Normalization
                            normalizePageOrientation(importedPage, config.orientationMode)
                            totalPagesMerged++
                        }
                    }
                }

                require(totalPagesMerged > 0) { "No valid pages were selected across the input documents." }

                onProgress("Writing merged document to disk...", 0.92f)

                FileOutputStream(destinationFile).use { fos ->
                    targetDoc.save(fos)
                }

                onProgress("Merge Complete!", 1.0f)
                destinationFile
            } finally {
                try {
                    targetDoc.close()
                } catch (_: Exception) {}

                openSourceDocs.forEach { doc ->
                    try {
                        doc.close()
                    } catch (_: Exception) {}
                }

                openStreams.forEach { stream ->
                    try {
                        stream.close()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun normalizePageOrientation(page: PDPage, mode: PageOrientationMode) {
        when (mode) {
            PageOrientationMode.ORIGINAL -> {
                // Keep page's original rotation and mediaBox
            }
            PageOrientationMode.FORCE_PORTRAIT -> {
                val mediaBox = page.mediaBox
                val isLandscape = (mediaBox.width > mediaBox.height) || (page.rotation == 90 || page.rotation == 270)
                if (isLandscape && page.rotation == 0) {
                    page.rotation = 90
                }
            }
            PageOrientationMode.FORCE_LANDSCAPE -> {
                val mediaBox = page.mediaBox
                val isPortrait = (mediaBox.height > mediaBox.width) && (page.rotation == 0 || page.rotation == 180)
                if (isPortrait) {
                    page.rotation = 90
                }
            }
        }
    }

    private fun renderFirstPageThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                pfd.use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    try {
                        if (renderer.pageCount > 0) {
                            val page = renderer.openPage(0)
                            val targetWidth = 140
                            val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(100, 200)
                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            bitmap
                        } else null
                    } finally {
                        renderer.close()
                    }
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun queryFileDetails(context: Context, uri: Uri): Pair<String, Long> {
        var name = "document_${System.currentTimeMillis()}.pdf"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        return Pair(name, size)
    }
}
