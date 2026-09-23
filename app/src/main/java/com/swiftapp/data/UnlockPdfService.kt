package com.swiftapp.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.swiftapp.data.model.EncryptionStatus
import com.swiftapp.data.model.UnlockPdfItem
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object UnlockPdfService {

    /**
     * Inspects a PDF document to determine its exact encryption status and metadata.
     */
    suspend fun inspectPdfFile(context: Context, file: File, uri: Uri? = null): UnlockPdfItem = withContext(Dispatchers.IO) {
        var status = EncryptionStatus.NOT_ENCRYPTED
        var pageCount = 0

        try {
            PDDocument.load(file).use { doc ->
                if (doc.isEncrypted) {
                    // Document is encrypted with owner restrictions only (opens with blank password)
                    status = EncryptionStatus.RESTRICTIONS_ONLY
                } else {
                    status = EncryptionStatus.NOT_ENCRYPTED
                }
                pageCount = doc.numberOfPages
            }
        } catch (e: InvalidPasswordException) {
            status = EncryptionStatus.PASSWORD_PROTECTED
        } catch (e: Exception) {
            if (e.message?.contains("password", ignoreCase = true) == true ||
                e.message?.contains("encrypted", ignoreCase = true) == true
            ) {
                status = EncryptionStatus.PASSWORD_PROTECTED
            } else {
                status = EncryptionStatus.NOT_ENCRYPTED
            }
        }

        UnlockPdfItem(
            file = file,
            name = file.name,
            size = file.length(),
            pageCount = pageCount,
            encryptionStatus = status,
            uri = uri
        )
    }

    /**
     * Decrypts the PDF with the provided password and completely strips all security restrictions.
     */
    suspend fun unlockAndStripSecurity(
        context: Context,
        sourcePdf: File,
        password: String?,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var document: PDDocument? = null
        try {
            onProgress(0.2f)

            // Attempt opening with provided password or blank password
            document = if (!password.isNullOrBlank()) {
                PDDocument.load(sourcePdf, password.trim())
            } else {
                PDDocument.load(sourcePdf)
            }

            onProgress(0.5f)

            // Completely remove all security dictionaries and permission restrictions
            document.setAllSecurityToBeRemoved(true)

            onProgress(0.8f)

            // Save clean, permanently unlocked output file
            val outputDir = File(context.cacheDir, "unlocked_pdfs").apply { if (!exists()) mkdirs() }
            val baseName = sourcePdf.nameWithoutExtension.removeSuffix("_protected")
            val outputFile = File(outputDir, "${baseName}_unlocked.pdf")

            FileOutputStream(outputFile).use { fos ->
                document.save(fos)
            }

            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: InvalidPasswordException) {
            Result.failure(IllegalArgumentException("Incorrect password. Please verify and try again."))
        } catch (e: Exception) {
            if (e.message?.contains("password", ignoreCase = true) == true) {
                Result.failure(IllegalArgumentException("Incorrect password. Please verify and try again."))
            } else {
                e.printStackTrace()
                Result.failure(e)
            }
        } finally {
            try {
                document?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Saves the unlocked PDF file to public Downloads folder.
     */
    suspend fun savePdfToDownloads(context: Context, file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = file.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
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
            val fileName = "unlock_raw_${System.currentTimeMillis()}.pdf"
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
