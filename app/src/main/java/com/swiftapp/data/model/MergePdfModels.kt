package com.swiftapp.data.model

import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Orientation modes for merging PDF documents.
 */
enum class PageOrientationMode(val displayName: String, val description: String) {
    ORIGINAL("Original", "Keep each page's native orientation"),
    FORCE_PORTRAIT("Portrait", "Normalize all pages to portrait mode"),
    FORCE_LANDSCAPE("Landscape", "Normalize all pages to landscape mode"),
}

/**
 * Representation of an individual PDF file in the merge queue.
 */
data class MergePdfItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val thumbnail: Bitmap? = null,
    val pageRange: String = "All", // "All" or custom like "1-3, 5, 8-10"
    val isEncrypted: Boolean = false,
    val isUnlocked: Boolean = !isEncrypted,
    val password: String? = null,
    val isCorrupted: Boolean = false,
    val errorMessage: String? = null,
) {
    val isValid: Boolean
        get() = !isCorrupted && isUnlocked && pageCount > 0
}

/**
 * Configuration options for the merge operation.
 */
data class MergeConfig(
    val outputFileName: String = "merged_document_${System.currentTimeMillis()}.pdf",
    val orientationMode: PageOrientationMode = PageOrientationMode.ORIGINAL,
)

/**
 * UI State for the Merge PDF Screen.
 */
sealed interface MergeUiState {
    data object Idle : MergeUiState
    data class LoadingMetadata(val currentCount: Int, val totalCount: Int) : MergeUiState
    data class Processing(val stepDescription: String, val progress: Float) : MergeUiState
    data class Success(val file: File, val fileName: String, val fileSizeBytes: Long, val totalPages: Int) : MergeUiState
    data class Error(val message: String) : MergeUiState
}
