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
import com.swiftapp.data.model.RotatePageItem
import com.swiftapp.data.model.RotatePdfResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class RotatePdfService(private val context: Context) {

    /**
     * Inspects a PDF file using PDFBox to extract page count, dimensions, and native page rotations.
     */
    suspend fun inspectPdf(
        file: File,
        password: String? = null
    ): Result<List<RotatePageItem>> = withContext(Dispatchers.IO) {
        try {
            val doc = if (password.isNullOrEmpty()) {
                PDDocument.load(file)
            } else {
                PDDocument.load(file, password)
            }

            val pages = mutableListOf<RotatePageItem>()
            val pageCount = doc.numberOfPages

            for (i in 0 until pageCount) {
                val pdPage = doc.getPage(i)
                val mediaBox = pdPage.mediaBox
                val rotation = pdPage.rotation // 0, 90, 180, 270

                pages.add(
                    RotatePageItem(
                        pageIndex = i,
                        pageNumber = i + 1,
                        originalRotation = rotation,
                        rotationDelta = 0,
                        width = mediaBox?.width ?: 595f,
                        height = mediaBox?.height ?: 842f,
                        thumbnailBitmap = null,
                        isSelected = false
                    )
                )
            }

            doc.close()
            Result.success(pages)
        } catch (e: InvalidPasswordException) {
            Result.failure(SecurityException("Password required or incorrect password."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Renders a single page thumbnail using Android's native PdfRenderer.
     * Renders the page in its original orientation so that Compose UI can animate rotation deltas cleanly.
     */
    suspend fun renderThumbnail(
        file: File,
        pageIndex: Int,
        targetWidth: Int = 320
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
            canvas.drawColor(Color.WHITE) // Pure white background

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
     * Losslessly applies rotation deltas directly to each page's dictionary `/Rotate` attribute.
     * Zero rasterization, preserving vector text, fonts, bookmarks, annotations, and form fields.
     */
    suspend fun rotatePdfLossless(
        sourceFile: File,
        pageDeltas: Map<Int, Int>, // pageIndex -> rotation delta (90, 180, 270, etc.)
        password: String? = null,
        customName: String? = null
    ): Result<RotatePdfResult> = withContext(Dispatchers.IO) {
        try {
            val doc = if (password.isNullOrEmpty()) {
                PDDocument.load(sourceFile)
            } else {
                PDDocument.load(sourceFile, password)
            }

            var modifiedPagesCount = 0
            val totalPages = doc.numberOfPages

            for (i in 0 until totalPages) {
                val delta = pageDeltas[i] ?: 0
                val normalizedDelta = ((delta % 360) + 360) % 360
                if (normalizedDelta != 0) {
                    val pdPage = doc.getPage(i)
                    val currentRotation = pdPage.rotation
                    val newRotation = ((currentRotation + normalizedDelta) % 360 + 360) % 360
                    pdPage.rotation = newRotation
                    modifiedPagesCount++
                }
            }

            val baseName = if (!customName.isNullOrBlank()) {
                if (customName.endsWith(".pdf", ignoreCase = true)) customName else "$customName.pdf"
            } else {
                val origName = sourceFile.nameWithoutExtension
                "${origName}_rotated.pdf"
            }

            val outputFile = File(context.cacheDir, baseName)
            doc.save(outputFile)
            doc.close()

            Result.success(
                RotatePdfResult(
                    file = outputFile,
                    fileName = outputFile.name,
                    fileSize = outputFile.length(),
                    totalPages = totalPages,
                    modifiedPagesCount = modifiedPagesCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Saves a rotated PDF to the public Downloads/SwiftPDF directory using MediaStore scoped storage.
     */
    suspend fun saveToDownloads(file: File, displayName: String? = null): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val finalName = displayName?.takeIf { it.isNotBlank() } ?: file.name
            val savedFile = com.swiftapp.utils.StorageLocationManager.savePdfToStorage(context, file, finalName)
            Result.success(Uri.fromFile(savedFile))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
