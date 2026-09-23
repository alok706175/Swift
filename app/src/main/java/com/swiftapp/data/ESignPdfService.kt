package com.swiftapp.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.ui.graphics.toArgb
import com.swiftapp.data.model.HandwritingStyle
import com.swiftapp.data.model.SavedSignatureItem
import com.swiftapp.data.model.SignElementItem
import com.swiftapp.data.model.SignElementType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max

object ESignPdfService {

    /**
     * Renders a PDF page to a Bitmap for the interactive viewer.
     */
    suspend fun renderPageToBitmap(
        context: Context,
        pdfFile: File,
        pageIndex: Int,
        targetWidth: Int = 1200
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex !in 0 until renderer.pageCount) return@withContext null

            renderer.openPage(pageIndex).use { page ->
                val scale = targetWidth.toFloat() / page.width.toFloat()
                val targetHeight = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                // Fill white background for document
                val canvas = Canvas(bitmap)
                canvas.drawColor(AndroidColor.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    /**
     * Get total page count of a PDF file.
     */
    suspend fun getPageCount(pdfFile: File): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    /**
     * Generates a transparent high-resolution bitmap of a typed cursive signature.
     */
    fun createTypedSignatureBitmap(
        text: String,
        style: HandwritingStyle,
        colorInt: Int
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            textSize = 72f
            isDither = true
            isSubpixelText = true
        }

        val typeface = when (style) {
            HandwritingStyle.CURSIVE_CLASSIC -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            HandwritingStyle.ELEGANT_SERIF -> Typeface.create("casual", Typeface.ITALIC)
            HandwritingStyle.MODERN_SIGNATURE -> Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
            HandwritingStyle.CASUAL_HANDWRITING -> Typeface.create("sans-serif-condensed", Typeface.ITALIC)
        }
        paint.typeface = typeface

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val padding = 24
        val width = max(100, bounds.width() + padding * 2)
        val height = max(60, bounds.height() + padding * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Draw transparent
        canvas.drawColor(AndroidColor.TRANSPARENT)

        val x = padding.toFloat() - bounds.left
        val y = padding.toFloat() - bounds.top
        canvas.drawText(text, x, y, paint)

        return bitmap
    }

    /**
     * Creates a transparent bitmap for text or date annotations.
     */
    fun createTextAnnotationBitmap(
        text: String,
        colorInt: Int,
        isBold: Boolean = false
    ): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            textSize = 54f
            typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val padding = 16
        val width = max(80, bounds.width() + padding * 2)
        val height = max(50, bounds.height() + padding * 2)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.TRANSPARENT)

        val x = padding.toFloat() - bounds.left
        val y = padding.toFloat() - bounds.top
        canvas.drawText(text, x, y, paint)

        return bitmap
    }

    /**
     * Removes white or near-white background from an uploaded signature photo to make it transparent.
     */
    fun removeWhiteBackground(srcBitmap: Bitmap, threshold: Int = 220): Bitmap {
        val width = srcBitmap.width
        val height = srcBitmap.height
        val pixels = IntArray(width * height)
        srcBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = AndroidColor.red(color)
            val g = AndroidColor.green(color)
            val b = AndroidColor.blue(color)
            val alpha = AndroidColor.alpha(color)

            if (alpha > 0) {
                // Check if pixel is near white/light gray
                if (r >= threshold && g >= threshold && b >= threshold) {
                    pixels[i] = AndroidColor.TRANSPARENT
                } else {
                    // Boost contrast of signature strokes to dark ink
                    val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    if (luminance < 140) {
                        pixels[i] = AndroidColor.argb(alpha, 15, 30, 80) // Dark Navy Ink
                    }
                }
            }
        }

        val transparentBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        transparentBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return transparentBitmap
    }

    /**
     * Save a created signature bitmap to local storage for quick reuse.
     */
    suspend fun saveSignatureToStorage(
        context: Context,
        bitmap: Bitmap,
        name: String
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "saved_signatures").apply { if (!exists()) mkdirs() }
        val file = File(dir, "sig_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        file
    }

    /**
     * Load all saved signatures from internal storage.
     */
    suspend fun loadSavedSignatures(context: Context): List<SavedSignatureItem> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "saved_signatures")
        if (!dir.exists()) return@withContext emptyList()

        val files = dir.listFiles { f -> f.extension.equals("png", ignoreCase = true) } ?: return@withContext emptyList()
        files.map { file ->
            SavedSignatureItem(
                id = file.nameWithoutExtension,
                name = "Signature",
                file = file,
                dateCreated = file.lastModified()
            )
        }.sortedByDescending { it.dateCreated }
    }

    /**
     * Delete a saved signature.
     */
    suspend fun deleteSavedSignature(file: File): Boolean = withContext(Dispatchers.IO) {
        file.delete()
    }

    /**
     * Flattens and stamps all placed signatures, initials, text, and dates permanently into the PDF.
     */
    suspend fun flattenAndSignPdf(
        context: Context,
        sourcePdf: File,
        password: String?,
        elements: List<SignElementItem>,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            onProgress(0.1f)
            document = if (!password.isNullOrBlank()) {
                PDDocument.load(sourcePdf, password)
            } else {
                PDDocument.load(sourcePdf)
            }

            val totalPages = document.numberOfPages
            val elementsByPage = elements.groupBy { it.pageIndex }

            for (pageIndex in 0 until totalPages) {
                onProgress(0.1f + (pageIndex.toFloat() / totalPages) * 0.7f)
                val pageElements = elementsByPage[pageIndex] ?: continue

                val page = document.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageW = mediaBox.width
                val pageH = mediaBox.height

                PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { contentStream ->
                    for (element in pageElements) {
                        // Render element bitmap
                        val elemBitmap: Bitmap = when {
                            element.bitmap != null -> element.bitmap
                            !element.text.isNullOrBlank() -> {
                                when (element.type) {
                                    SignElementType.CHECKMARK -> createTextAnnotationBitmap("\u2713", element.textColor.toArgb(), isBold = true)
                                    SignElementType.CROSS -> createTextAnnotationBitmap("\u2715", element.textColor.toArgb(), isBold = true)
                                    else -> createTextAnnotationBitmap(element.text, element.textColor.toArgb(), isBold = false)
                                }
                            }
                            else -> continue
                        }

                        // Calculate PDF Coordinates
                        // In UI: (0,0) is top-left
                        // In PDF: (0,0) is bottom-left
                        val elemPdfWidth = element.widthNorm * pageW
                        val elemPdfHeight = element.heightNorm * pageH
                        val elemPdfX = element.xNorm * pageW
                        val elemPdfY = pageH - (element.yNorm * pageH) - elemPdfHeight

                        val pdImage = LosslessFactory.createFromImage(document, elemBitmap)
                        contentStream.drawImage(pdImage, elemPdfX, elemPdfY, elemPdfWidth, elemPdfHeight)
                    }
                }
            }

            onProgress(0.85f)

            // Save output file
            val outputDir = File(context.cacheDir, "signed_pdfs").apply { if (!exists()) mkdirs() }
            val baseName = sourcePdf.nameWithoutExtension
            val outputFile = File(outputDir, "${baseName}_signed_${System.currentTimeMillis()}.pdf")

            FileOutputStream(outputFile).use { fos ->
                document.save(fos)
            }

            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try {
                document?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Saves the signed PDF to public Downloads directory via MediaStore.
     */
    suspend fun savePdfToDownloads(context: Context, file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = file.name
            val savedFile = com.swiftapp.utils.StorageLocationManager.savePdfToStorage(context, file, fileName)
            "Saved to ${savedFile.absolutePath}"
        }
    }

    /**
     * Copies selected PDF Uri to a local cache file.
     */
    suspend fun copyUriToCacheFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "doc_${System.currentTimeMillis()}.pdf"
            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        }.getOrNull()
    }
}
