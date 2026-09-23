package com.swiftapp.data.model

import android.net.Uri
import java.io.File

/**
 * Status of the PDF document encryption.
 */
enum class EncryptionStatus {
    NOT_ENCRYPTED,
    RESTRICTIONS_ONLY,
    PASSWORD_PROTECTED
}

/**
 * Detailed information about a selected PDF file for unlocking.
 */
data class UnlockPdfItem(
    val file: File,
    val name: String,
    val size: Long,
    val pageCount: Int,
    val encryptionStatus: EncryptionStatus,
    val uri: Uri? = null
)

/**
 * Reactive UI state for Unlock PDF.
 */
sealed interface UnlockUiState {
    object Idle : UnlockUiState
    data class FileSelected(val item: UnlockPdfItem) : UnlockUiState
    data class Processing(val message: String, val progress: Float) : UnlockUiState
    data class Success(
        val outputFile: File,
        val pageCount: Int,
        val fileSize: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : UnlockUiState
    data class Error(val message: String) : UnlockUiState
}
