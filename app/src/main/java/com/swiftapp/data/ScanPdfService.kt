package com.swiftapp.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import com.swiftapp.data.model.MarginOption
import com.swiftapp.data.model.PageSizeOption
import com.swiftapp.data.model.PolygonCorners
import com.swiftapp.data.model.ScanExportConfig
import com.swiftapp.data.model.ScanFilter
import com.swiftapp.data.model.ScanPageItem
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ScanPdfService {

    /**
     * Decode image bounds without loading the full bitmap to memory.
     */
    fun getImageDimensions(imageFile: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, options)
        return Pair(options.outWidth, options.outHeight)
    }

    /**
     * Decode and downscale bitmap for memory safety.
     */
    private fun decodeSampledBitmap(file: File, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val origWidth = options.outWidth
        val origHeight = options.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        var sampleSize = 1
        var largest = max(origWidth, origHeight)
        while (largest / 2 >= maxDimension) {
            largest /= 2
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    }

    /**
     * Computes perspective deskew and applies enhancements to a page.
     */
    fun processPageBitmap(
        imageFile: File,
        corners: PolygonCorners,
        rotation: Int,
        filter: ScanFilter,
        maxDimension: Int = 1800
    ): Bitmap? {
        val srcBitmap = decodeSampledBitmap(imageFile, maxDimension) ?: return null
        val srcW = srcBitmap.width.toFloat()
        val srcH = srcBitmap.height.toFloat()

        // 1. Perspective Transform
        val tlX = corners.topLeft.x * srcW
        val tlY = corners.topLeft.y * srcH
        val trX = corners.topRight.x * srcW
        val trY = corners.topRight.y * srcH
        val brX = corners.bottomRight.x * srcW
        val brY = corners.bottomRight.y * srcH
        val blX = corners.bottomLeft.x * srcW
        val blY = corners.bottomLeft.y * srcH

        // Destination dimensions based on quadrilateral edges
        val topEdge = hypot((trX - tlX).toDouble(), (trY - tlY).toDouble()).toFloat()
        val bottomEdge = hypot((brX - blX).toDouble(), (brY - blY).toDouble()).toFloat()
        val leftEdge = hypot((blX - tlX).toDouble(), (blY - tlY).toDouble()).toFloat()
        val rightEdge = hypot((brY - trY).toDouble(), (brY - trY).toDouble()).toFloat()

        val destW = max(100, max(topEdge, bottomEdge).roundToInt())
        val destH = max(100, max(leftEdge, rightEdge).roundToInt())

        val srcPoints = floatArrayOf(
            tlX, tlY,
            trX, trY,
            brX, brY,
            blX, blY
        )
        val destPoints = floatArrayOf(
            0f, 0f,
            destW.toFloat(), 0f,
            destW.toFloat(), destH.toFloat(),
            0f, destH.toFloat()
        )

        val warpMatrix = Matrix()
        val matrixSet = warpMatrix.setPolyToPoly(srcPoints, 0, destPoints, 0, 4)

        val warpedBitmap = if (matrixSet) {
            val warped = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(warped)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(srcBitmap, warpMatrix, paint)
            srcBitmap.recycle()
            warped
        } else {
            srcBitmap
        }

        // 2. Rotation
        val rotatedBitmap = if (rotation % 360 != 0) {
            val rotMatrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(
                warpedBitmap, 0, 0, warpedBitmap.width, warpedBitmap.height, rotMatrix, true
            )
            if (rotated != warpedBitmap) warpedBitmap.recycle()
            rotated
        } else {
            warpedBitmap
        }

        // 3. Document Filter
        val finalBitmap = applyFilter(rotatedBitmap, filter)
        if (finalBitmap != rotatedBitmap) {
            rotatedBitmap.recycle()
        }

        return finalBitmap
    }

    /**
     * Applies document filters (Original, Magic Color, B&W Clean, Grayscale).
     */
    private fun applyFilter(src: Bitmap, filter: ScanFilter): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        when (filter) {
            ScanFilter.ORIGINAL -> {
                // Subtle contrast boost
                val cm = ColorMatrix(floatArrayOf(
                    1.05f, 0f, 0f, 0f, 5f,
                    0f, 1.05f, 0f, 0f, 5f,
                    0f, 0f, 1.05f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
            ScanFilter.MAGIC_COLOR -> {
                // High contrast, brightened whites, vibrant text
                val cm = ColorMatrix(floatArrayOf(
                    1.35f, 0f, 0f, 0f, 20f,
                    0f, 1.35f, 0f, 0f, 20f,
                    0f, 0f, 1.35f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
            ScanFilter.WHITEBOARD -> {
                // Whiteboard: Enhance contrast, whiten yellowish backgrounds, sharpen ink colors
                val cm = ColorMatrix(floatArrayOf(
                    1.45f, 0f, 0f, 0f, 25f,
                    0f, 1.45f, 0f, 0f, 25f,
                    0f, 0f, 1.45f, 0f, 25f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
            ScanFilter.GRAYSCALE -> {
                val cm = ColorMatrix().apply {
                    setSaturation(0f)
                }
                // slight contrast
                val contrastCm = ColorMatrix(floatArrayOf(
                    1.15f, 0f, 0f, 0f, 10f,
                    0f, 1.15f, 0f, 0f, 10f,
                    0f, 0f, 1.15f, 0f, 10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(contrastCm)
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
            ScanFilter.BW_DOCUMENT -> {
                // Clean binarized document effect
                val grayCm = ColorMatrix().apply { setSaturation(0f) }
                // High slope contrast: (x - 0.5) * 2.5 + 0.5 with bright offset
                val bwCm = ColorMatrix(floatArrayOf(
                    2.8f, 0f, 0f, 0f, -160f,
                    0f, 2.8f, 0f, 0f, -160f,
                    0f, 0f, 2.8f, 0f, -160f,
                    0f, 0f, 0f, 1f, 0f
                ))
                grayCm.postConcat(bwCm)
                paint.colorFilter = ColorMatrixColorFilter(grayCm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
        }

        return result
    }

    /**
     * Splits an open two-page book image spread into Left Page and Right Page files.
     */
    suspend fun splitBookPages(context: Context, imageFile: File): Pair<File, File>? = withContext(Dispatchers.IO) {
        val bitmap = decodeSampledBitmap(imageFile, 2400) ?: return@withContext null
        try {
            val width = bitmap.width
            val height = bitmap.height
            val halfWidth = width / 2

            val leftBitmap = Bitmap.createBitmap(bitmap, 0, 0, halfWidth, height)
            val rightBitmap = Bitmap.createBitmap(bitmap, halfWidth, 0, halfWidth, height)

            val leftFile = File(context.cacheDir, "book_left_${System.currentTimeMillis()}.jpg")
            val rightFile = File(context.cacheDir, "book_right_${System.currentTimeMillis() + 1}.jpg")

            FileOutputStream(leftFile).use { fos -> leftBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos) }
            FileOutputStream(rightFile).use { fos -> rightBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos) }

            leftBitmap.recycle()
            rightBitmap.recycle()

            Pair(leftFile, rightFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Merges Front & Back sides of an ID Card onto a single formatted A4 sheet.
     */
    suspend fun mergeIdCardSides(
        context: Context,
        frontFile: File,
        backFile: File
    ): File? = withContext(Dispatchers.IO) {
        val frontBitmap = decodeSampledBitmap(frontFile, 1400) ?: return@withContext null
        val backBitmap = decodeSampledBitmap(backFile, 1400) ?: return@withContext null

        try {
            // A4 sheet canvas: 1600 x 2260
            val sheetWidth = 1600
            val sheetHeight = 2260
            val mergedBitmap = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mergedBitmap)

            // Pure white background
            canvas.drawColor(android.graphics.Color.WHITE)

            val cardTargetWidth = 1250
            val cardTargetHeight = (cardTargetWidth * (frontBitmap.height.toFloat() / frontBitmap.width.toFloat())).toInt().coerceIn(600, 800)

            val leftMargin = (sheetWidth - cardTargetWidth) / 2f
            val topMargin = 220f
            val spacing = 180f

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.DKGRAY
                textSize = 36f
                isFakeBoldText = true
            }

            // --- Front Card ---
            canvas.drawText("FRONT SIDE", leftMargin + 10, topMargin - 20, textPaint)
            val frontRect = android.graphics.RectF(leftMargin, topMargin, leftMargin + cardTargetWidth, topMargin + cardTargetHeight)
            val scaledFront = Bitmap.createScaledBitmap(frontBitmap, cardTargetWidth, cardTargetHeight, true)
            canvas.drawBitmap(scaledFront, leftMargin, topMargin, paint)
            canvas.drawRoundRect(frontRect, 24f, 24f, strokePaint)
            if (scaledFront != frontBitmap) scaledFront.recycle()

            // --- Back Card ---
            val backTop = topMargin + cardTargetHeight + spacing
            canvas.drawText("BACK SIDE", leftMargin + 10, backTop - 20, textPaint)
            val backRect = android.graphics.RectF(leftMargin, backTop, leftMargin + cardTargetWidth, backTop + cardTargetHeight)
            val scaledBack = Bitmap.createScaledBitmap(backBitmap, cardTargetWidth, cardTargetHeight, true)
            canvas.drawBitmap(scaledBack, leftMargin, backTop, paint)
            canvas.drawRoundRect(backRect, 24f, 24f, strokePaint)
            if (scaledBack != backBitmap) scaledBack.recycle()

            val mergedFile = File(context.cacheDir, "id_card_merged_${System.currentTimeMillis()}.jpg")
            FileOutputStream(mergedFile).use { fos ->
                mergedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }

            mergedBitmap.recycle()
            mergedFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            frontBitmap.recycle()
            backBitmap.recycle()
        }
    }

    /**
     * Render and save a fast preview image of a scan page to cache.
     */
    suspend fun generatePreviewImage(
        context: Context,
        pageItem: ScanPageItem
    ): File? = withContext(Dispatchers.IO) {
        val previewBitmap = processPageBitmap(
            imageFile = pageItem.originalImageFile,
            corners = pageItem.corners,
            rotation = pageItem.rotationDegrees,
            filter = pageItem.filter,
            maxDimension = 700
        ) ?: return@withContext null

        val cacheFile = File(context.cacheDir, "scan_preview_${pageItem.id}.jpg")
        try {
            FileOutputStream(cacheFile).use { fos ->
                previewBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            previewBitmap.recycle()
        }
        cacheFile
    }

    /**
     * Compile multiple scanned pages into a single PDF document.
     */
    suspend fun compileToPdf(
        context: Context,
        pages: List<ScanPageItem>,
        config: ScanExportConfig,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (pages.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No scanned pages to compile"))
        }

        var document: PDDocument? = null
        try {
            document = PDDocument()
            val totalPages = pages.size

            for (index in pages.indices) {
                val pageItem = pages[index]
                onProgress((index.toFloat() / totalPages) * 0.9f)

                // Render high resolution bitmap for final export
                val pageBitmap = processPageBitmap(
                    imageFile = pageItem.originalImageFile,
                    corners = pageItem.corners,
                    rotation = pageItem.rotationDegrees,
                    filter = pageItem.filter,
                    maxDimension = 2200
                ) ?: continue

                // Page dimensions
                val pdPage = when (config.pageSize) {
                    PageSizeOption.A4 -> PDPage(PDRectangle.A4)
                    PageSizeOption.LETTER -> PDPage(PDRectangle.LETTER)
                    PageSizeOption.FIT_IMAGE -> {
                        val ptW = pageBitmap.width * 72f / 150f
                        val ptH = pageBitmap.height * 72f / 150f
                        PDPage(PDRectangle(ptW, ptH))
                    }
                }
                document.addPage(pdPage)

                val mediaBox = pdPage.mediaBox
                val margin = config.margin.marginPoints

                val availW = mediaBox.width - (margin * 2)
                val availH = mediaBox.height - (margin * 2)

                val imgW = pageBitmap.width.toFloat()
                val imgH = pageBitmap.height.toFloat()

                // Calculate aspect ratio fit
                val scale = min(availW / imgW, availH / imgH)
                val renderW = imgW * scale
                val renderH = imgH * scale

                val posX = margin + (availW - renderW) / 2f
                val posY = margin + (availH - renderH) / 2f

                // Embed compressed image
                val quality = (config.compressionQuality.coerceIn(40, 100)) / 100f
                val pdImage = JPEGFactory.createFromImage(document, pageBitmap, quality)

                PDPageContentStream(document, pdPage).use { contentStream ->
                    contentStream.drawImage(pdImage, posX, posY, renderW, renderH)
                }

                pageBitmap.recycle()
            }

            onProgress(0.95f)

            // Output file
            val outputDir = File(context.cacheDir, "scans").apply { if (!exists()) mkdirs() }
            val cleanName = if (config.fileName.endsWith(".pdf", ignoreCase = true)) {
                config.fileName
            } else {
                "${config.fileName}.pdf"
            }
            val outputFile = File(outputDir, cleanName)

            FileOutputStream(outputFile).use { fos ->
                document.save(fos)
            }

            onProgress(1f)
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
     * Copy imported image URI to a temporary cache file.
     */
    suspend fun copyUriToCacheFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "scan_raw_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        }.getOrNull()
    }

    /**
     * Save the generated PDF file to device public Downloads folder.
     */
    suspend fun savePdfToDownloads(context: Context, file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = file.name
            val savedFile = com.swiftapp.utils.StorageLocationManager.savePdfToStorage(context, file, fileName)
            "Saved to ${savedFile.absolutePath}"
        }
    }

    /**
     * Save an individual scanned page as a high-quality JPEG to device gallery/Pictures.
     */
    suspend fun savePageAsJpeg(
        context: Context,
        pageItem: ScanPageItem,
        pageNumber: Int = 1,
        totalCount: Int = 1
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val pageBitmap = processPageBitmap(
                imageFile = pageItem.originalImageFile,
                corners = pageItem.corners,
                rotation = pageItem.rotationDegrees,
                filter = pageItem.filter,
                maxDimension = 2400
            ) ?: throw IllegalStateException("Could not render page image")

            val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val suffix = if (totalCount > 1) "_p$pageNumber" else ""
            val fileName = "SwiftScan_${timestamp}${suffix}.jpg"

            val targetFile: File
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/SwiftScans")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Failed to create MediaStore entry")

                context.contentResolver.openOutputStream(uri)?.use { os ->
                    pageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                targetFile = File(context.cacheDir, fileName)
                FileOutputStream(targetFile).use { fos ->
                    pageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SwiftScans")
                if (!picturesDir.exists()) picturesDir.mkdirs()
                targetFile = File(picturesDir, fileName)
                FileOutputStream(targetFile).use { fos ->
                    pageBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("image/jpeg"),
                    null
                )
            }

            pageBitmap.recycle()
            targetFile
        }
    }

    /**
     * Save all scanned pages as high-quality JPEGs to the device gallery.
     */
    suspend fun saveAllPagesAsJpegs(
        context: Context,
        pages: List<ScanPageItem>
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val savedFiles = mutableListOf<File>()
            for ((index, page) in pages.withIndex()) {
                val result = savePageAsJpeg(context, page, pageNumber = index + 1, totalCount = pages.size)
                result.getOrNull()?.let { savedFiles.add(it) }
            }
            if (savedFiles.isEmpty()) throw IllegalStateException("Failed to save pages as images")
            savedFiles
        }
    }
}
