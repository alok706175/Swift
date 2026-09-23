package com.swiftapp.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.swiftapp.data.model.PasswordStrength
import com.swiftapp.data.model.PdfPermissionsConfig
import com.swiftapp.data.model.ProtectPdfItem
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ProtectPdfService {

    /**
     * Inspects a PDF file and extracts metadata, page count, and encryption state.
     */
    suspend fun inspectPdfFile(context: Context, file: File, uri: Uri? = null): ProtectPdfItem = withContext(Dispatchers.IO) {
        var isEncrypted = false
        var pageCount = 0

        try {
            PDDocument.load(file).use { doc ->
                isEncrypted = doc.isEncrypted
                pageCount = doc.numberOfPages
            }
        } catch (e: Exception) {
            // If encrypted with open password, PDDocument.load throws an exception
            if (e.message?.contains("encrypted", ignoreCase = true) == true ||
                e.message?.contains("password", ignoreCase = true) == true
            ) {
                isEncrypted = true
            }
        }

        ProtectPdfItem(
            file = file,
            name = file.name,
            size = file.length(),
            pageCount = pageCount,
            isAlreadyEncrypted = isEncrypted,
            uri = uri
        )
    }

    /**
     * Computes real-time password complexity score.
     */
    fun calculatePasswordStrength(password: String): PasswordStrength {
        if (password.isBlank()) return PasswordStrength.EMPTY

        var points = 0
        if (password.length >= 8) points += 1
        if (password.length >= 12) points += 1
        if (password.any { it.isUpperCase() }) points += 1
        if (password.any { it.isLowerCase() }) points += 1
        if (password.any { it.isDigit() }) points += 1
        if (password.any { !it.isLetterOrDigit() }) points += 1

        return when {
            points <= 2 -> PasswordStrength.WEAK
            points <= 4 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
    }

    /**
     * Encrypts a PDF document with AES-256 standard encryption and custom access permissions.
     */
    suspend fun encryptPdf(
        context: Context,
        sourcePdf: File,
        userPassword: String,
        ownerPassword: String?,
        permissions: PdfPermissionsConfig,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        if (userPassword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Password cannot be empty"))
        }

        var document: PDDocument? = null
        try {
            onProgress(0.15f)
            document = PDDocument.load(sourcePdf)

            if (document.isEncrypted) {
                return@withContext Result.failure(IllegalStateException("This document is already password-protected."))
            }

            onProgress(0.35f)

            // Configure Granular Permissions
            val accessPermission = AccessPermission().apply {
                setCanPrint(permissions.allowPrinting)
                setCanExtractContent(permissions.allowCopying)
                setCanModify(permissions.allowModifying)
                setCanModifyAnnotations(permissions.allowAnnotations)
                setCanAssembleDocument(permissions.allowPageAssembly)
                setCanFillInForm(permissions.allowFormFill)
            }

            onProgress(0.55f)

            // Owner password fallback
            val effectiveOwnerPassword = if (!ownerPassword.isNullOrBlank()) {
                ownerPassword.trim()
            } else {
                "${userPassword}_owner_${System.currentTimeMillis()}"
            }

            // Create Standard AES-256 Protection Policy
            val policy = StandardProtectionPolicy(
                effectiveOwnerPassword,
                userPassword.trim(),
                accessPermission
            ).apply {
                encryptionKeyLength = 256
                setPreferAES(true)
            }

            document.protect(policy)

            onProgress(0.80f)

            // Save encrypted output file
            val outputDir = File(context.cacheDir, "protected_pdfs").apply { if (!exists()) mkdirs() }
            val baseName = sourcePdf.nameWithoutExtension
            val outputFile = File(outputDir, "${baseName}_protected.pdf")

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
     * Saves the protected PDF file to public Downloads folder.
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
     * Copy selected Uri to cache file.
     */
    suspend fun copyUriToCacheFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = "protect_raw_${System.currentTimeMillis()}.pdf"
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
