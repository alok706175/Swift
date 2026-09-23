package com.swiftapp.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.swiftapp.data.model.ImageMarginMode
import com.swiftapp.data.model.ImageOrientationMode
import com.swiftapp.data.model.ImagePageSize
import com.swiftapp.data.model.ImageScalingMode
import com.swiftapp.data.model.ImageToPdfConfig
import com.swiftapp.data.model.ImageToPdfItem
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageToPdfService {

    /**
     * Efficiently reads width and height of an image file without loading pixel data.
     */
    fun getImageDimensions(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return Pair(max(1, options.outWidth), max(1, options.outHeight))
    }

    /**
     * Decodes and downsamples bitmap safely, composites over solid white background (for PNG/WebP transparency),
     * and applies rotation.
     */
    fun decodeProcessedBitmap(file: File, userRotation: Int, maxDimension: Int = 2200): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val origW = options.outWidth
        val origH = options.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        var largest = max(origW, origH)
        while (largest / 2 >= maxDimension) {
            largest /= 2
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val srcBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        // 1. Composite over white background to prevent black boxes for transparent PNGs
        val solidBitmap = Bitmap.createBitmap(srcBitmap.width, srcBitmap.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(solidBitmap)
        canvas.drawColor(AndroidColor.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBitmap, 0f, 0f, paint)
        srcBitmap.recycle()

        // 2. Rotate if needed
        val finalBitmap = if (userRotation % 360 != 0) {
            val matrix = Matrix().apply { postRotate(userRotation.toFloat()) }
            val rotated = Bitmap.createBitmap(
                solidBitmap, 0, 0, solidBitmap.width, solidBitmap.height, matrix, true
            )
            if (rotated != solidBitmap) solidBitmap.recycle()
            rotated
        } else {
            solidBitmap
        }

        return finalBitmap
    }

    /**
     * Compiles the list of images into a single multi-page PDF document.
     */
    suspend fun compileImagesToPdf(
        context: Context,
        items: List<ImageToPdfItem>,
        config: ImageToPdfConfig,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No images to convert"))
        }

        var document: PDDocument? = null
        try {
            document = PDDocument()
            val total = items.size

            for (index in items.indices) {
                onProgress(0.05f + (index.toFloat() / total) * 0.85f)
                val item = items[index]

                val pageBitmap = decodeProcessedBitmap(
                    file = item.file,
                    userRotation = item.rotationDegrees,
                    maxDimension = 2200
                ) ?: continue

                val bmpW = pageBitmap.width.toFloat()
                val bmpH = pageBitmap.height.toFloat()
                val isImageLandscape = bmpW > bmpH

                // Determine PDF page dimensions
                val pdPage = when (config.pageSize) {
                    ImagePageSize.FIT_IMAGE -> {
                        val ptW = bmpW * 72f / 150f
                        val ptH = bmpH * 72f / 150f
                        PDPage(PDRectangle(ptW, ptH))
                    }
                    ImagePageSize.A4, ImagePageSize.LETTER -> {
                        val base = if (config.pageSize == ImagePageSize.A4) PDRectangle.A4 else PDRectangle.LETTER
                        val portraitW = min(base.width, base.height)
                        val portraitH = max(base.width, base.height)

                        when (config.orientation) {
                            ImageOrientationMode.AUTO_SMART -> {
                                if (isImageLandscape) PDPage(PDRectangle(portraitH, portraitW))
                                else PDPage(PDRectangle(portraitW, portraitH))
                            }
                            ImageOrientationMode.PORTRAIT -> PDPage(PDRectangle(portraitW, portraitH))
                            ImageOrientationMode.LANDSCAPE -> PDPage(PDRectangle(portraitH, portraitW))
                        }
                    }
                }
                document.addPage(pdPage)

                val mediaBox = pdPage.mediaBox
                val pageW = mediaBox.width
                val pageH = mediaBox.height
                val margin = config.margins.marginPoints

                val availW = pageW - (margin * 2)
                val availH = pageH - (margin * 2)

                // Scaling calculations
                val scale = when (config.scaling) {
                    ImageScalingMode.FIT_CONTAIN -> min(availW / bmpW, availH / bmpH)
                    ImageScalingMode.FILL_COVER -> max(availW / bmpW, availH / bmpH)
                }

                val renderW = bmpW * scale
                val renderH = bmpH * scale
                val posX = margin + (availW - renderW) / 2f
                val posY = margin + (availH - renderH) / 2f

                // Compress & Draw Image
                val qualityFloat = (config.quality.qualityPercent.coerceIn(40, 100)) / 100f
                val pdImage = JPEGFactory.createFromImage(document, pageBitmap, qualityFloat)

                PDPageContentStream(document, pdPage).use { contentStream ->
                    contentStream.drawImage(pdImage, posX, posY, renderW, renderH)
                }

                pageBitmap.recycle()
            }

            onProgress(0.95f)

            // Save output file
            val outputDir = File(context.cacheDir, "images_converted_pdfs").apply { if (!exists()) mkdirs() }
            val cleanName = if (config.fileName.endsWith(".pdf", ignoreCase = true)) {
                config.fileName
            } else {
                "${config.fileName}.pdf"
            }
            val outputFile = File(outputDir, cleanName)

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
     * Saves the converted PDF to public Downloads directory via MediaStore.
     */
    suspend fun savePdfToDownloads(context: Context, file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = file.name
            val savedFile = com.swiftapp.utils.StorageLocationManager.savePdfToStorage(context, file, fileName)
            "Saved to ${savedFile.absolutePath}"
        }
    }

    /**
     * Copy imported image Uri to app cache.
     */
    suspend fun copyUriToCacheFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "img_raw_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
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
