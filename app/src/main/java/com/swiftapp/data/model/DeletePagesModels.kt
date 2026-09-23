package com.swiftapp.data.model

import android.graphics.Bitmap
import java.io.File

data class DeletePageItem(
    val pageIndex: Int,
    val pageNumber: Int,
    val bitmap: Bitmap? = null,
    val isSelectedForDeletion: Boolean = false
)

data class DeletePagesResult(
    val originalFile: File,
    val outputFile: File,
    val initialPageCount: Int,
    val deletedPageCount: Int,
    val remainingPageCount: Int,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val processingTimeMs: Long
)

sealed interface DeletePagesUiState {
    object Idle : DeletePagesUiState
    data class LoadingThumbnails(val progress: Float, val current: Int, val total: Int) : DeletePagesUiState
    object Ready : DeletePagesUiState
    data class Processing(val progress: Float, val statusMessage: String) : DeletePagesUiState
    data class Success(val result: DeletePagesResult) : DeletePagesUiState
    data class Error(val message: String) : DeletePagesUiState
    data class PasswordRequired(val file: File) : DeletePagesUiState
}
