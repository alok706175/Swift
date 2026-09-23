package com.swiftapp.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
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
import java.text.SimpleDateFormat
import java.util.*

enum class HomeSubSection(val title: String) {
    RECENT("Recent"),
    ALL_FILES("All Files")
}

enum class PdfListFilter(val title: String) {
    ALL("All PDFs"),
    RECENT("Recent (7 Days)"),
    LARGE("Large (>10 MB)")
}

enum class PdfSortOption(val title: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First")
}

data class PdfFileItem(
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long, // in seconds
    val uri: Uri,
    val pageCount: Int? = null
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

    // Cache to prevent continuous disk churn
    private var cachedFiles: List<PdfFileItem>? = null
    private var lastScanTimeMs: Long = 0L
    private const val CACHE_EXPIRY_MS = 10_000L // 10 seconds cache unless force refreshed

    /**
     * High-speed storage scanner combining MediaStore queries and targeted directory discovery.
     */
    suspend fun searchPdfFiles(context: Context, query: String = "", forceRefresh: Boolean = false): List<PdfFileItem> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && query.isBlank() && cachedFiles != null && (now - lastScanTimeMs) < CACHE_EXPIRY_MS) {
            return@withContext cachedFiles!!
        }

        val uniqueMap = LinkedHashMap<String, PdfFileItem>(128)

        // 1. High-speed MediaStore Query
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )

            val selection = "(${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.pdf')"
            val selectionArgs = arrayOf("application/pdf")
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    if (path.isNullOrBlank()) continue

                    val id = if (idCol >= 0) cursor.getLong(idCol) else -1
                    val name = if (nameCol >= 0) cursor.getString(nameCol) else null
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val date = if (dateCol >= 0) cursor.getLong(dateCol) else (System.currentTimeMillis() / 1000)

                    val contentUri = if (id != -1L) {
                        ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                    } else {
                        Uri.fromFile(File(path))
                    }

                    val cleanName = name ?: path.substringAfterLast(File.separatorChar)
                    uniqueMap[path] = PdfFileItem(
                        name = cleanName,
                        path = path,
                        size = size,
                        dateModified = date,
                        uri = contentUri
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Targeted directory discovery for app & standard public locations
        val searchDirs = ArrayList<File>(8)
        try {
            searchDirs.add(StorageLocationManager.getTargetDirectory(context))
            searchDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
            searchDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
            searchDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SwiftPDF"))
            searchDirs.add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SwiftPDF"))
            context.getExternalFilesDir(null)?.let { searchDirs.add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { searchDirs.add(it) }
            searchDirs.add(context.filesDir)

            // Direct external root scan only if permission is granted and MediaStore is sparse
            if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()) && uniqueMap.size < 5) {
                val root = Environment.getExternalStorageDirectory()
                if (root != null && root.exists()) {
                    searchDirs.add(root)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                scanDirectoryRecursively(dir, uniqueMap, currentDepth = 0, maxDepth = 2)
            }
        }

        val allItems = uniqueMap.values.toList()
        if (query.isBlank()) {
            cachedFiles = allItems
            lastScanTimeMs = System.currentTimeMillis()
            allItems
        } else {
            allItems.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        map: MutableMap<String, PdfFileItem>,
        currentDepth: Int = 0,
        maxDepth: Int = 2
    ) {
        if (currentDepth > maxDepth) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            try {
                val name = f.name
                // Skip hidden folders, Android system dirs, and cache
                if (name.startsWith(".") || name.equals("Android", ignoreCase = true) || name.equals("cache", ignoreCase = true)) {
                    continue
                }
                if (f.isDirectory) {
                    scanDirectoryRecursively(f, map, currentDepth + 1, maxDepth)
                } else if (f.isFile && name.endsWith(".pdf", ignoreCase = true)) {
                    val path = f.path
                    if (!map.containsKey(path)) {
                        val size = f.length()
                        if (size > 0) {
                            map[path] = PdfFileItem(
                                name = name,
                                path = path,
                                size = size,
                                dateModified = f.lastModified() / 1000,
                                uri = Uri.fromFile(f)
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Safely renames a PDF file in storage and updates its file reference.
     */
    suspend fun renamePdf(file: File, newDisplayName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.failure(Exception("File does not exist"))

            val cleanName = if (newDisplayName.endsWith(".pdf", ignoreCase = true)) newDisplayName else "$newDisplayName.pdf"
            val targetFile = File(file.parentFile, cleanName)

            if (targetFile.exists() && targetFile.canonicalPath != file.canonicalPath) {
                return@withContext Result.failure(Exception("A file with that name already exists."))
            }

            val success = file.renameTo(targetFile)
            if (success) {
                Result.success(targetFile)
            } else {
                Result.failure(Exception("Unable to rename file."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Safely deletes a PDF file from storage.
     */
    suspend fun deletePdf(context: Context, file: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext Result.success(true)

            // Try MediaStore delete first
            try {
                val uri = MediaStore.Files.getContentUri("external")
                context.contentResolver.delete(
                    uri,
                    "${MediaStore.Files.FileColumns.DATA} = ?",
                    arrayOf(file.canonicalPath)
                )
            } catch (_: Exception) {}

            val deleted = file.delete()
            if (deleted || !file.exists()) {
                Result.success(true)
            } else {
                Result.failure(Exception("Could not delete file from device."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads page count from PDF document.
     */
    suspend fun getPdfPageCount(file: File): Int? = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file).use { doc ->
                doc.numberOfPages
            }
        } catch (_: Exception) {
            null
        }
    }

    fun formatRelativeDate(timestampSec: Long): String {
        val millis = timestampSec * 1000
        val now = System.currentTimeMillis()
        val calFile = Calendar.getInstance().apply { timeInMillis = millis }
        val calNow = Calendar.getInstance()

        val isSameDay = calFile.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calFile.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
        if (isSameDay) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            return "Today, ${timeFormat.format(Date(millis))}"
        }

        val calYesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = calFile.get(Calendar.YEAR) == calYesterday.get(Calendar.YEAR) &&
                calFile.get(Calendar.DAY_OF_YEAR) == calYesterday.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            return "Yesterday, ${timeFormat.format(Date(millis))}"
        }

        val isSameYear = calFile.get(Calendar.YEAR) == calNow.get(Calendar.YEAR)
        val dateFormat = if (isSameYear) {
            SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())
        } else {
            SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        }
        return dateFormat.format(Date(millis))
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
