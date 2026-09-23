package com.swiftapp.data.model

import android.net.Uri
import java.io.File

/**
 * Real-time evaluation of password complexity.
 */
enum class PasswordStrength(val label: String, val score: Float) {
    EMPTY("Enter password", 0f),
    WEAK("Weak", 0.33f),
    MEDIUM("Medium", 0.66f),
    STRONG("Strong", 1.0f)
}

/**
 * Granular document permissions for the encrypted PDF.
 */
data class PdfPermissionsConfig(
    val allowPrinting: Boolean = true,
    val allowCopying: Boolean = false,
    val allowModifying: Boolean = false,
    val allowAnnotations: Boolean = false,
    val allowPageAssembly: Boolean = false,
    val allowFormFill: Boolean = true
)

/**
 * Details of the selected PDF document to protect.
 */
data class ProtectPdfItem(
    val file: File,
    val name: String,
    val size: Long,
    val pageCount: Int,
    val isAlreadyEncrypted: Boolean = false,
    val uri: Uri? = null
)

/**
 * Reactive UI states for the Protect PDF screen.
 */
sealed interface ProtectUiState {
    object Idle : ProtectUiState
    data class FileSelected(val item: ProtectPdfItem) : ProtectUiState
    data class Processing(val message: String, val progress: Float) : ProtectUiState
    data class Success(
        val outputFile: File,
        val pageCount: Int,
        val fileSize: Long,
        val passwordUsed: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : ProtectUiState
    data class Error(val message: String) : ProtectUiState
}
