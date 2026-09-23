package com.swiftapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.swiftapp.data.model.CompressPdfItem
import com.swiftapp.data.model.CompressResult
import com.swiftapp.data.model.CompressionPreset
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object CompressPdfService {

    /**
     * Resolves PDF metadata, detects encryption, and extracts a first-page thumbnail.
     */
    suspend fun extractMetadata(
        context: Context,
        uri: Uri,
        password: String? = null,
    ): CompressPdfItem = withContext(Dispatchers.IO) {
        val (fileName, fileSizeBytes) = queryFileDetails(context, uri)
        var pageCount = 0
        var isEncrypted = false
        var isUnlocked = true
        var isCorrupted = false
        var errorMessage: String? = null
        var thumbnail: Bitmap? = null

        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return@withContext CompressPdfItem(
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

        if (!isCorrupted && (!isEncrypted || isUnlocked)) {
            thumbnail = renderFirstPageThumbnail(context, uri)
        }

        CompressPdfItem(
            uri = uri,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            pageCount = pageCount,
            thumbnail = thumbnail,
            isEncrypted = isEncrypted,
            isUnlocked = !isEncrypted || isUnlocked,
            password = password,
            isCorrupted = isCorrupted,
            errorMessage = errorMessage,
        )
    }

    /**
     * Compresses a single PDF file using scaled rasterization & JPEG stream optimization.
     */
    suspend fun compressFile(
        context: Context,
        item: CompressPdfItem,
        preset: CompressionPreset,
        targetSizeKb: Int? = null,
        onProgress: (step: String, progress: Float) -> Unit,
    ): Result<CompressResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(item.isValid) { "Cannot compress an invalid or locked document." }

            onProgress("Analyzing PDF objects and streams...", 0.10f)

            // Determine dynamic or preset scale and quality
            val (scaleFactor, quality) = if (preset == CompressionPreset.CUSTOM_TARGET && targetSizeKb != null && targetSizeKb > 0) {
                calculateTargetParameters(targetSizeKb, item.pageCount)
            } else {
                Pair(preset.scaleFactor, preset.quality)
            }

            val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
            val baseName = item.fileName.removeSuffix(".pdf")
            val destinationFile = File(outputDir, "${baseName}_compressed.pdf")

            // Open ParcelFileDescriptor for native rendering
            var tempUnlockedFile: File? = null
            val pfd: ParcelFileDescriptor = if (item.isEncrypted && !item.password.isNullOrEmpty()) {
                // If encrypted, load with PDFBox and save temporarily unlocked for PdfRenderer
                tempUnlockedFile = File(context.cacheDir, "temp_unlocked_${System.currentTimeMillis()}.pdf")
                val stream = context.contentResolver.openInputStream(item.uri) ?: throw IllegalArgumentException("Cannot open stream")
                stream.use { s ->
                    val doc = PDDocument.load(s, item.password)
                    try {
                        doc.isAllSecurityToBeRemoved = true
                        FileOutputStream(tempUnlockedFile).use { fos -> doc.save(fos) }
                    } finally {
                        doc.close()
                    }
                }
                ParcelFileDescriptor.open(tempUnlockedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(item.uri, "r")
                    ?: throw IllegalArgumentException("Cannot open descriptor for ${item.fileName}")
            }

            val targetDoc = PDDocument()

            try {
                pfd.use { descriptor ->
                    val renderer = PdfRenderer(descriptor)
                    try {
                        val totalPages = renderer.pageCount
                        require(totalPages > 0) { "PDF has no renderable pages." }

                        for (i in 0 until totalPages) {
                            val progressVal = 0.15f + (0.75f * ((i + 1).toFloat() / totalPages.toFloat()))
                            onProgress("Optimizing page ${i + 1} of $totalPages...", progressVal)

                            val page = renderer.openPage(i)
                            try {
                                val targetWidth = (page.width * scaleFactor).toInt().coerceAtLeast(100)
                                val targetHeight = (page.height * scaleFactor).toInt().coerceAtLeast(100)

                                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val pdImage = JPEGFactory.createFromImage(targetDoc, bitmap, quality)
                                val newPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                                targetDoc.addPage(newPage)

                                PDPageContentStream(targetDoc, newPage).use { contentStream ->
                                    contentStream.drawImage(pdImage, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                                }

                                bitmap.recycle()
                            } finally {
                                page.close()
                            }
                        }
                    } finally {
                        renderer.close()
                    }
                }

                onProgress("Writing optimized PDF...", 0.95f)

                FileOutputStream(destinationFile).use { fos ->
                    targetDoc.save(fos)
                }

                val finalSize = destinationFile.length()
                val isAlreadyOptimized = finalSize >= item.fileSizeBytes && item.fileSizeBytes > 0

                onProgress("Compression complete!", 1.0f)

                CompressResult(
                    originalItem = item,
                    compressedFile = destinationFile,
                    originalSizeBytes = item.fileSizeBytes,
                    compressedSizeBytes = finalSize,
                    totalPages = item.pageCount,
                    isAlreadyOptimized = isAlreadyOptimized,
                )
            } finally {
                targetDoc.close()
                tempUnlockedFile?.delete()
            }
        }
    }

    private fun calculateTargetParameters(targetKb: Int, totalPages: Int): Pair<Float, Float> {
        val targetBytes = targetKb * 1024L
        val bytesPerPage = targetBytes / maxOf(1, totalPages)

        return when {
            bytesPerPage < 35_000L -> Pair(0.70f, 0.28f) // Extremely compact (~30KB/page)
            bytesPerPage < 75_000L -> Pair(0.90f, 0.45f) // Balanced compact (~60KB/page)
            bytesPerPage < 150_000L -> Pair(1.20f, 0.65f) // Medium (~100KB/page)
            else -> Pair(1.50f, 0.80f) // High quality
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
