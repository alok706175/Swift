package com.example.pdfutilityapp.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

data class PdfFileItem(
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long,
    val uri: Uri,
)

object PdfHelper {

    suspend fun createSamplePdf(
        context: Context,
        fileName: String = "sample_generated.pdf",
        title: String = "PDF Utility App - Generated Document",
        author: String = "PDFUtilityApp User",
        bodyText: List<String> = listOf(
            "Welcome to PDFUtilityApp!",
            "This document was created directly on Android using PDFBox-Android.",
            "Features included in this template:",
            "  • Material 3 UI Design with Jetpack Compose",
            "  • Coil image loading integration",
            "  • Java 17 Compatibility & Gradle Kotlin DSL",
            "  • High-performance PDF generation & manipulation"
        )
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
            val pdfFile = File(outputDir, fileName)

            val document = PDDocument()
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)

            val info = PDDocumentInformation().apply {
                this.title = title
                this.author = author
                this.creator = "PDFUtilityApp"
                this.creationDate = Calendar.getInstance()
            }
            document.documentInformation = info

            PDPageContentStream(document, page).use { contentStream ->
                contentStream.beginText()
                contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                contentStream.newLineAtOffset(50f, 750f)
                contentStream.showText(title)
                contentStream.endText()

                contentStream.beginText()
                contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 12f)
                contentStream.newLineAtOffset(50f, 725f)
                contentStream.showText("Author: $author | Date: ${Calendar.getInstance().time}")
                contentStream.endText()

                contentStream.setLineWidth(1.5f)
                contentStream.moveTo(50f, 710f)
                contentStream.lineTo(545f, 710f)
                contentStream.stroke()

                var yOffset = 680f
                contentStream.setFont(PDType1Font.HELVETICA, 12f)
                for (line in bodyText) {
                    contentStream.beginText()
                    contentStream.newLineAtOffset(50f, yOffset)
                    contentStream.showText(line)
                    contentStream.endText()
                    yOffset -= 24f
                }
            }

            document.save(pdfFile)
            document.close()

            pdfFile
        }
    }

    suspend fun searchPdfFiles(context: Context, query: String): List<PdfFileItem> = withContext(Dispatchers.IO) {
        val pdfList = mutableListOf<PdfFileItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns._ID
        )

        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("application/pdf", "%$query%")
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameColumn)
                    val path = cursor.getString(dataColumn)
                    val size = cursor.getLong(sizeColumn)
                    val date = cursor.getLong(dateColumn)
                    val id = cursor.getLong(idColumn)
                    val contentUri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())

                    pdfList.add(PdfFileItem(name, path, size, date, contentUri))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pdfList
    }

    suspend fun searchTextInPdf(file: File, query: String): List<Int> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val matchingPages = mutableListOf<Int>()
        runCatching {
            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper()
                for (i in 1..document.numberOfPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    val text = stripper.getText(document)
                    if (text.contains(query, ignoreCase = true)) {
                        matchingPages.add(i - 1)
                    }
                }
            }
        }.onFailure { it.printStackTrace() }
        matchingPages
    }

    fun getPdfFileItem(file: File): PdfFileItem? {
        if (!file.exists()) return null
        return PdfFileItem(
            name = file.name,
            path = file.absolutePath,
            size = file.length(),
            dateModified = file.lastModified() / 1000,
            uri = Uri.fromFile(file),
        )
    }
}
