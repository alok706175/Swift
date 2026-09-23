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
import com.swiftapp.data.model.DeletePageItem
import com.swiftapp.data.model.DeletePagesResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

class DeletePdfPagesService(private val context: Context) {

    /**
     * Inspects a PDF and returns the total page count.
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
        } catch (_: Exception) {
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Deletes specified pages from the source PDF and saves the modified PDF document.
     */
    suspend fun deletePagesAndSave(
        sourceFile: File,
        password: String? = null,
        deletedPageIndices: Set<Int>,
        customOutputName: String? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<DeletePagesResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var sourceDoc: PDDocument? = null
        var newDoc: PDDocument? = null

        try {
            onProgress(0.1f, "Opening document...")
            sourceDoc = if (password.isNullOrEmpty()) {
                PDDocument.load(sourceFile)
            } else {
                PDDocument.load(sourceFile, password)
            }

            val totalInitialPages = sourceDoc.numberOfPages
            val remainingPagesCount = totalInitialPages - deletedPageIndices.size

            if (remainingPagesCount <= 0) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot delete all pages. At least one page must remain in the document.")
                )
            }

            if (deletedPageIndices.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("No pages selected for deletion.")
                )
            }

            onProgress(0.3f, "Removing selected pages...")
            newDoc = PDDocument()

            // Keep all pages that are NOT in deletedPageIndices
            var processed = 0
            for (i in 0 until totalInitialPages) {
                if (!deletedPageIndices.contains(i)) {
                    val page = sourceDoc.getPage(i)
                    newDoc.importPage(page)
                    processed++
                    val prog = 0.3f + 0.4f * (processed.toFloat() / remainingPagesCount)
                    onProgress(prog, "Preserving page $processed of $remainingPagesCount...")
                }
            }

            onProgress(0.75f, "Writing new PDF document...")
            val baseName = sourceFile.nameWithoutExtension
            val outputFileName = if (!customOutputName.isNullOrBlank()) {
                if (customOutputName.endsWith(".pdf", ignoreCase = true)) customOutputName else "$customOutputName.pdf"
            } else {
                "${baseName}_pages_removed.pdf"
            }

            val tempFile = File(context.cacheDir, "del_${System.currentTimeMillis()}_$outputFileName")
            FileOutputStream(tempFile).use { fos ->
                newDoc.save(fos)
            }

            onProgress(0.9f, "Saving to storage...")
            val finalOutputFile = saveToUserStorage(tempFile, outputFileName)

            onProgress(1.0f, "Completed!")
            val result = DeletePagesResult(
                originalFile = sourceFile,
                outputFile = finalOutputFile,
                initialPageCount = totalInitialPages,
                deletedPageCount = deletedPageIndices.size,
                remainingPageCount = remainingPagesCount,
                originalSizeBytes = sourceFile.length(),
                outputSizeBytes = finalOutputFile.length(),
                processingTimeMs = System.currentTimeMillis() - startTime
            )

            Result.success(result)
        } catch (e: InvalidPasswordException) {
            Result.failure(SecurityException("Password required or incorrect password."))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { newDoc?.close() } catch (_: Exception) {}
            try { sourceDoc?.close() } catch (_: Exception) {}
        }
    }

    private fun saveToUserStorage(sourceFile: File, outputFileName: String): File {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/SwiftPDF")
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        FileInputStream(sourceFile).use { `is` -> `is`.copyTo(os) }
                    }
                }
            } catch (_: Exception) {}
        }

        // Direct directory save as well
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "SwiftPDF"
        ).apply { if (!exists()) mkdirs() }

        var target = File(dir, outputFileName)
        var count = 1
        val baseName = outputFileName.removeSuffix(".pdf")
        while (target.exists()) {
            target = File(dir, "${baseName}_$count.pdf")
            count++
        }

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
        return target
    }
}
