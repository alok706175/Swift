package com.swiftapp.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.swiftapp.data.model.ConvertedImageItem
import com.swiftapp.data.model.ImageOutputFormat
import com.swiftapp.data.model.PdfImageExtractMode
import com.swiftapp.data.model.PdfToImagesConfig
import com.swiftapp.data.model.PdfToImagesResult
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

object PdfToImagesService {

    /**
     * Rapidly renders a low-resolution thumbnail for a PDF page.
     */
    suspend fun renderThumbnail(
        context: Context,
        file: File,
        pageIndex: Int,
        targetWidth: Int = 360
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex !in 0 until renderer.pageCount) return@withContext null

            renderer.openPage(pageIndex).use { page ->
                val scale = targetWidth.toFloat() / max(1, page.width).toFloat()
                val targetHeight = (page.height * scale).toInt()

                val bitmap = Bitmap.createBitmap(targetWidth, max(1, targetHeight), Bitmap.Config.ARGB_8888)
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
     * Gets total number of pages in a PDF file.
     */
    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
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
     * Parses custom page range string like "1-3, 5, 8-10" into a set of 0-based page indices.
     */
    fun parsePageRanges(rangeText: String, totalPages: Int): Set<Int> {
        if (rangeText.isBlank()) return (0 until totalPages).toSet()

        val result = mutableSetOf<Int>()
        val tokens = rangeText.split(",", ";", " ")

        for (token in tokens) {
            val clean = token.trim()
            if (clean.isEmpty()) continue

            if (clean.contains("-")) {
                val parts = clean.split("-")
                if (parts.size == 2) {
                    val start = parts[0].toIntOrNull()?.let { it - 1 } ?: continue
                    val end = parts[1].toIntOrNull()?.let { it - 1 } ?: continue
                    for (i in start..end) {
                        if (i in 0 until totalPages) result.add(i)
                    }
                }
            } else {
                val page = clean.toIntOrNull()?.let { it - 1 } ?: continue
                if (page in 0 until totalPages) result.add(page)
            }
        }

        return if (result.isNotEmpty()) result else (0 until totalPages).toSet()
    }

    /**
     * Converts PDF pages to images or extracts embedded image objects.
     */
    suspend fun convertPdfToImages(
        context: Context,
        sourcePdf: File,
        password: String?,
        selectedPages: Set<Int>,
        config: PdfToImagesConfig,
        onProgress: (Float) -> Unit
    ): Result<PdfToImagesResult> = withContext(Dispatchers.IO) {
        val outputDir = File(context.cacheDir, "pdf_converted_images_${System.currentTimeMillis()}").apply {
            if (!exists()) mkdirs()
        }
        val generatedItems = mutableListOf<ConvertedImageItem>()

        try {
            onProgress(0.05f)

            if (config.extractMode == PdfImageExtractMode.RENDER_PAGES) {
                // Mode A: Render complete pages
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = ParcelFileDescriptor.open(sourcePdf, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    val totalPages = renderer.pageCount
                    val pagesToRender = selectedPages.filter { it in 0 until totalPages }.sorted()

                    for ((idx, pageIndex) in pagesToRender.withIndex()) {
                        onProgress(0.05f + (idx.toFloat() / pagesToRender.size) * 0.75f)

                        renderer.openPage(pageIndex).use { page ->
                            val scale = config.dpi.scaleFactor
                            val renderW = (page.width * scale).toInt()
                            val renderH = (page.height * scale).toInt()

                            val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(AndroidColor.WHITE)

                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            // Save to file
                            val pageNumFormatted = String.format("%03d", pageIndex + 1)
                            val ext = config.format.extension
                            val imgFile = File(outputDir, "page_${pageNumFormatted}.$ext")

                            FileOutputStream(imgFile).use { fos ->
                                if (config.format == ImageOutputFormat.JPG) {
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, config.jpgQuality.coerceIn(50, 100), fos)
                                } else {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                }
                            }

                            generatedItems.add(
                                ConvertedImageItem(
                                    index = pageIndex + 1,
                                    name = imgFile.name,
                                    file = imgFile,
                                    width = renderW,
                                    height = renderH,
                                    size = imgFile.length()
                                )
                            )

                            bitmap.recycle()
                        }
                    }
                } finally {
                    renderer?.close()
                    pfd?.close()
                }
            } else {
                // Mode B: Extract raw embedded image objects
                var document: PDDocument? = null
                try {
                    document = if (!password.isNullOrBlank()) PDDocument.load(sourcePdf, password) else PDDocument.load(sourcePdf)
                    val totalPages = document.numberOfPages
                    val pagesToScan = selectedPages.filter { it in 0 until totalPages }.sorted()
                    var imgCounter = 1

                    for ((idx, pageIndex) in pagesToScan.withIndex()) {
                        onProgress(0.05f + (idx.toFloat() / pagesToScan.size) * 0.75f)
                        val page = document.getPage(pageIndex)
                        val resources = page.resources

                        for (xObjectName in resources.xObjectNames) {
                            val xObject = resources.getXObject(xObjectName)
                            if (xObject is PDImageXObject) {
                                val imageBmp = xObject.image ?: continue
                                val ext = config.format.extension
                                val imgFile = File(outputDir, "extracted_img_${String.format("%03d", imgCounter)}.$ext")

                                FileOutputStream(imgFile).use { fos ->
                                    if (config.format == ImageOutputFormat.JPG) {
                                        imageBmp.compress(Bitmap.CompressFormat.JPEG, config.jpgQuality.coerceIn(50, 100), fos)
                                    } else {
                                        imageBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                                    }
                                }

                                generatedItems.add(
                                    ConvertedImageItem(
                                        index = imgCounter,
                                        name = imgFile.name,
                                        file = imgFile,
                                        width = imageBmp.width,
                                        height = imageBmp.height,
                                        size = imgFile.length()
                                    )
                                )

                                imageBmp.recycle()
                                imgCounter++
                            }
                        }
                    }
                } finally {
                    document?.close()
                }
            }

            if (generatedItems.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No images could be extracted from the document."))
            }

            onProgress(0.85f)

            // Package into ZIP if multiple images
            var zipFile: File? = null
            val isSingle = (generatedItems.size == 1)

            if (!isSingle) {
                val baseName = sourcePdf.nameWithoutExtension
                zipFile = File(outputDir, "${baseName}_images.zip")

                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    val buffer = ByteArray(8192)
                    for (item in generatedItems) {
                        val entry = ZipEntry(item.file.name)
                        zos.putNextEntry(entry)
                        FileInputStream(item.file).use { fis ->
                            var count: Int
                            while (fis.read(buffer).also { count = it } != -1) {
                                zos.write(buffer, 0, count)
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }

            val totalSize = generatedItems.sumOf { it.size }
            onProgress(1.0f)

            Result.success(
                PdfToImagesResult(
                    outputZipFile = zipFile,
                    individualImages = generatedItems,
                    totalImages = generatedItems.size,
                    totalSize = totalSize,
                    isSingleImage = isSingle
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Saves a converted file (ZIP or Image) to public Downloads directory.
     */
    suspend fun saveFileToDownloads(
        context: Context,
        file: File,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = file.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SwiftPDF")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Failed to create MediaStore entry")

                context.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)

                "Saved to Downloads/SwiftPDF/$fileName"
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "SwiftPDF").apply { if (!exists()) mkdirs() }
                val targetFile = File(targetDir, fileName)

                file.copyTo(targetFile, overwrite = true)
                "Saved to ${targetFile.absolutePath}"
            }
        }
    }

    /**
     * Copy selected Uri to local cache file.
     */
    suspend fun copyUriToCacheFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "pdf_to_img_raw_${System.currentTimeMillis()}.pdf"
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
