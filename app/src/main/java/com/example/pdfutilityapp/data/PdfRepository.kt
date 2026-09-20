package com.example.pdfutilityapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Repository interface for PDF operations including merging, image conversion, splitting, compression, and e-signature.
 */
interface PdfRepository {
    suspend fun mergePdfFiles(
        pdfUris: List<Uri>,
        outputFile: File? = null,
    ): Result<File>

    suspend fun convertImagesToPdf(
        imageUris: List<Uri>,
        outputFile: File? = null,
    ): Result<File>

    suspend fun splitPdf(
        pdfUri: Uri,
        outputDir: File? = null,
    ): Result<File>

    suspend fun rotatePdf(
        pdfUri: Uri,
        rotation: Int,
        outputFile: File? = null,
    ): Result<File>

    suspend fun protectPdf(
        pdfUri: Uri,
        password: String,
        outputFile: File? = null,
    ): Result<File>

    suspend fun unlockPdf(
        pdfUri: Uri,
        password: String,
        outputFile: File? = null,
    ): Result<File>

    suspend fun addWatermark(
        pdfUri: Uri,
        watermarkText: String,
        outputFile: File? = null,
    ): Result<File>

    suspend fun pdfToImages(
        pdfUri: Uri,
        outputDir: File? = null,
    ): Result<List<File>>

    suspend fun textToPdf(
        text: String,
        outputFile: File? = null,
    ): Result<File>

    suspend fun deletePages(
        pdfUri: Uri,
        pageIndices: List<Int>,
        outputFile: File? = null,
    ): Result<File>

    suspend fun reorderPages(
        pdfUri: Uri,
        newOrder: List<Int>,
        outputFile: File? = null,
    ): Result<File>

    suspend fun extractPages(
        pdfUri: Uri,
        pageIndices: List<Int>,
        outputFile: File? = null,
    ): Result<File>

    suspend fun compressPdf(
        pdfUri: Uri,
        compressionLevel: Float = 0.5f,
        outputFile: File? = null,
    ): Result<File>

    suspend fun signPdf(
        pdfUri: Uri,
        signatureText: String,
        outputFile: File? = null,
    ): Result<File>
}

/**
 * Default implementation of [PdfRepository] using PDFBox-Android.
 * Executes all heavy I/O and PDF processing on [Dispatchers.IO].
 */
class PdfRepositoryImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PdfRepository {

    override suspend fun mergePdfFiles(
        pdfUris: List<Uri>,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(pdfUris.isNotEmpty()) { "Cannot merge an empty list of PDF files." }

            val destinationFile = outputFile ?: createOutputFile("merged_${System.currentTimeMillis()}.pdf")
            val merger = PDFMergerUtility().apply {
                destinationFileName = destinationFile.absolutePath
            }

            val openStreams = mutableListOf<InputStream>()
            try {
                for (uri in pdfUris) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Unable to open input stream for URI: $uri")
                    openStreams.add(inputStream)
                    merger.addSource(inputStream)
                }

                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
            } finally {
                openStreams.forEach { stream ->
                    try {
                        stream.close()
                    } catch (_: Exception) {}
                }
            }

            destinationFile
        }
    }

    override suspend fun convertImagesToPdf(
        imageUris: List<Uri>,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            require(imageUris.isNotEmpty()) { "Cannot convert an empty list of image files." }

            val destinationFile = outputFile ?: createOutputFile("converted_images_${System.currentTimeMillis()}.pdf")
            val document = PDDocument()

            try {
                for (uri in imageUris) {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    } ?: throw IllegalArgumentException("Failed to decode image from URI: $uri")

                    try {
                        val pdImage: PDImageXObject = if (bitmap.hasAlpha()) {
                            LosslessFactory.createFromImage(document, bitmap)
                        } else {
                            JPEGFactory.createFromImage(document, bitmap, 0.85f)
                        }

                        val pageWidth = bitmap.width.toFloat()
                        val pageHeight = bitmap.height.toFloat()
                        val page = PDPage(PDRectangle(pageWidth, pageHeight))
                        document.addPage(page)

                        PDPageContentStream(document, page).use { contentStream ->
                            contentStream.drawImage(pdImage, 0f, 0f, pageWidth, pageHeight)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }

                FileOutputStream(destinationFile).use { fos ->
                    document.save(fos)
                }
            } finally {
                document.close()
            }

            destinationFile
        }
    }

    override suspend fun splitPdf(
        pdfUri: Uri,
        outputDir: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Unable to open input stream for URI: $pdfUri")

            val targetDir = outputDir ?: (context.getExternalFilesDir(null) ?: context.filesDir)
            val timestamp = System.currentTimeMillis()
            var primaryOutputFile: File? = null

            inputStream.use { stream ->
                val document = PDDocument.load(stream)
                try {
                    val splitter = Splitter()
                    val pages = splitter.split(document)
                    require(pages.isNotEmpty()) { "The selected PDF has no pages to split." }

                    pages.forEachIndexed { index, pageDoc ->
                        val splitFile = File(targetDir, "split_page_${index + 1}_$timestamp.pdf")
                        FileOutputStream(splitFile).use { fos ->
                            pageDoc.save(fos)
                        }
                        pageDoc.close()
                        if (index == 0) {
                            primaryOutputFile = splitFile
                        }
                    }
                } finally {
                    document.close()
                }
            }

            primaryOutputFile ?: throw IllegalStateException("No split pages were generated.")
        }
    }

    override suspend fun rotatePdf(
        pdfUri: Uri,
        rotation: Int,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Unable to open input stream")

            val destinationFile = outputFile ?: createOutputFile("rotated_${System.currentTimeMillis()}.pdf")

            inputStream.use { stream ->
                val document = PDDocument.load(stream)
                try {
                    for (page in document.pages) {
                        page.rotation = (page.rotation + rotation) % 360
                    }
                    FileOutputStream(destinationFile).use { fos ->
                        document.save(fos)
                    }
                } finally {
                    document.close()
                }
            }
            destinationFile
        }
    }

    override suspend fun protectPdf(
        pdfUri: Uri,
        password: String,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Unable to open input stream")

            val destinationFile = outputFile ?: createOutputFile("protected_${System.currentTimeMillis()}.pdf")

            inputStream.use { stream ->
                val document = PDDocument.load(stream)
                try {
                    val ap = com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission()
                    val spp = com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy(password, password, ap)
                    spp.encryptionKeyLength = 128
                    document.protect(spp)
                    FileOutputStream(destinationFile).use { fos ->
                        document.save(fos)
                    }
                } finally {
                    document.close()
                }
            }
            destinationFile
        }
    }

    override suspend fun unlockPdf(
        pdfUri: Uri,
        password: String,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Unable to open input stream")

            val destinationFile = outputFile ?: createOutputFile("unlocked_${System.currentTimeMillis()}.pdf")

            inputStream.use { stream ->
                val document = PDDocument.load(stream, password)
                try {
                    document.isAllSecurityToBeRemoved = true
                    FileOutputStream(destinationFile).use { fos ->
                        document.save(fos)
                    }
                } finally {
                    document.close()
                }
            }
            destinationFile
        }
    }

    override suspend fun addWatermark(
        pdfUri: Uri,
        watermarkText: String,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Unable to open input stream")

            val destinationFile = outputFile ?: createOutputFile("watermarked_${System.currentTimeMillis()}.pdf")

            inputStream.use { stream ->
                val document = PDDocument.load(stream)
                try {
                    val font = PDType1Font.HELVETICA_BOLD
                    for (page in document.pages) {
                        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                            contentStream.beginText()
                            contentStream.setFont(font, 50f)
                            @Suppress("DEPRECATION")
                            contentStream.setNonStrokingColor(200, 200, 200)
                            contentStream.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(45.0), 100f, 100f))
                            contentStream.showText(watermarkText)
                            contentStream.endText()
                        }
                    }
                    FileOutputStream(destinationFile).use { fos ->
                        document.save(fos)
                    }
                } finally {
                    document.close()
                }
            }
            destinationFile
        }
    }

    override suspend fun pdfToImages(
        pdfUri: Uri,
        outputDir: File?,
    ): Result<List<File>> = withContext(ioDispatcher) {
        runCatching {
            val fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: throw IllegalArgumentException("Unable to open file descriptor")
            
            val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
            val targetDir = outputDir ?: (context.getExternalFilesDir(null) ?: context.filesDir)
            val imageFiles = mutableListOf<File>()
            val timestamp = System.currentTimeMillis()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                val imageFile = File(targetDir, "pdf_page_${i + 1}_$timestamp.png")
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                imageFiles.add(imageFile)
                page.close()
                bitmap.recycle()
            }
            renderer.close()
            fileDescriptor.close()
            imageFiles
        }
    }

    override suspend fun textToPdf(
        text: String,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val destinationFile = outputFile ?: createOutputFile("text_converted_${System.currentTimeMillis()}.pdf")
            val document = PDDocument()
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)

            PDPageContentStream(document, page).use { contentStream ->
                contentStream.beginText()
                contentStream.setFont(PDType1Font.HELVETICA, 12f)
                contentStream.newLineAtOffset(50f, 750f)
                
                val lines = text.split("\n")
                var yOffset = 750f
                for (line in lines) {
                    if (yOffset < 50f) break
                    contentStream.showText(line)
                    contentStream.newLineAtOffset(0f, -15f)
                    yOffset -= 15f
                }
                contentStream.endText()
            }

            FileOutputStream(destinationFile).use { fos ->
                document.save(fos)
            }
            document.close()
            destinationFile
        }
    }

    override suspend fun deletePages(
        pdfUri: Uri,
        pageIndices: List<Int>,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Unable to open input stream")
            
            val destinationFile = outputFile ?: createOutputFile("pages_deleted_${System.currentTimeMillis()}.pdf")
            
            inputStream.use { stream ->
                val document = PDDocument.load(stream)
                try {
                    val sortedIndices = pageIndices.sortedDescending()
                    for (index in sortedIndices) {
                        if (index < document.numberOfPages) {
                            document.removePage(index)
                        }
                    }
                    FileOutputStream(destinationFile).use { fos ->
                        document.save(fos)
                    }
                } finally {
                    document.close()
                }
            }
            destinationFile
        }
    }

    override suspend fun reorderPages(
        pdfUri: Uri,
        newOrder: List<Int>,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Unable to open input stream")
            
            val destinationFile = outputFile ?: createOutputFile("pages_reordered_${System.currentTimeMillis()}.pdf")
            
            inputStream.use { stream ->
                val sourceDoc = PDDocument.load(stream)
                val destDoc = PDDocument()
                try {
                    for (index in newOrder) {
                        if (index < sourceDoc.numberOfPages) {
                            destDoc.addPage(sourceDoc.getPage(index))
                        }
                    }
                    FileOutputStream(destinationFile).use { fos ->
                        destDoc.save(fos)
                    }
                } finally {
                    sourceDoc.close()
                    destDoc.close()
                }
            }
            destinationFile
        }
    }

    override suspend fun extractPages(
        pdfUri: Uri,
        pageIndices: List<Int>,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Unable to open input stream")
            
            val destinationFile = outputFile ?: createOutputFile("pages_extracted_${System.currentTimeMillis()}.pdf")
            
            inputStream.use { stream ->
                val sourceDoc = PDDocument.load(stream)
                val destDoc = PDDocument()
                try {
                    for (index in pageIndices) {
                        if (index < sourceDoc.numberOfPages) {
                            destDoc.addPage(sourceDoc.getPage(index))
                        }
                    }
                    FileOutputStream(destinationFile).use { fos ->
                        destDoc.save(fos)
                    }
                } finally {
                    sourceDoc.close()
                    destDoc.close()
                }
            }
            destinationFile
        }
    }

    override suspend fun compressPdf(
        pdfUri: Uri,
        compressionLevel: Float,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: throw IllegalArgumentException("Unable to open file descriptor")
            
            val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
            val destinationFile = outputFile ?: createOutputFile("compressed_${System.currentTimeMillis()}.pdf")
            val newDoc = PDDocument()

            try {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Render page at scaled down resolution for compression
                    val scaledWidth = (page.width * 1.2f).toInt()
                    val scaledHeight = (page.height * 1.2f).toInt()
                    val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val pdImage = JPEGFactory.createFromImage(newDoc, bitmap, compressionLevel)
                    val newPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                    newDoc.addPage(newPage)

                    PDPageContentStream(newDoc, newPage).use { contentStream ->
                        contentStream.drawImage(pdImage, 0f, 0f, page.width.toFloat(), page.height.toFloat())
                    }
                    page.close()
                    bitmap.recycle()
                }

                FileOutputStream(destinationFile).use { fos ->
                    newDoc.save(fos)
                }
            } finally {
                newDoc.close()
                renderer.close()
                fileDescriptor.close()
            }
            destinationFile
        }
    }

    override suspend fun signPdf(
        pdfUri: Uri,
        signatureText: String,
        outputFile: File?,
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            val inputStream = context.contentResolver.openInputStream(pdfUri)
                ?: throw IllegalArgumentException("Unable to open input stream")

            val destinationFile = outputFile ?: createOutputFile("signed_${System.currentTimeMillis()}.pdf")

            inputStream.use { stream ->
                val document = PDDocument.load(stream)
                try {
                    val page = document.getPage(document.numberOfPages - 1) // Add signature on last page
                    val font = PDType1Font.HELVETICA_OBLIQUE
                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                        contentStream.beginText()
                        contentStream.setFont(font, 24f)
                        @Suppress("DEPRECATION")
                        contentStream.setNonStrokingColor(0, 51, 102) // Dark blue ink color
                        contentStream.newLineAtOffset(350f, 80f)
                        contentStream.showText(signatureText)
                        contentStream.endText()
                    }
                    FileOutputStream(destinationFile).use { fos ->
                        document.save(fos)
                    }
                } finally {
                    document.close()
                }
            }
            destinationFile
        }
    }

    private fun createOutputFile(fileName: String): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(baseDir, fileName)
    }
}
