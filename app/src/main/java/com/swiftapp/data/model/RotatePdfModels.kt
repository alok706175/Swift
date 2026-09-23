package com.swiftapp.data.model

import android.graphics.Bitmap
import java.io.File

/**
 * Filter type for selective bulk page rotation.
 */
enum class RotatePageFilter(val label: String) {
    ALL("All Pages"),
    PORTRAIT("Portrait Only"),
    LANDSCAPE("Landscape Only"),
    ODD("Odd Pages"),
    EVEN("Even Pages")
}

/**
 * State of an individual page in the Rotate PDF grid.
 */
data class RotatePageItem(
    val pageIndex: Int, // 0-indexed
    val pageNumber: Int, // 1-indexed for display
    val originalRotation: Int = 0, // 0, 90, 180, 270 from PDF dictionary
    val rotationDelta: Int = 0, // User applied delta (can be 0, 90, 180, 270)
    val width: Float = 0f,
    val height: Float = 0f,
    val thumbnailBitmap: Bitmap? = null,
    val isSelected: Boolean = false
) {
    /**
     * Effective normalized rotation angle (0, 90, 180, 270).
     */
    val effectiveRotation: Int
        get() = ((originalRotation + rotationDelta) % 360 + 360) % 360

    /**
     * Whether the page is currently in landscape orientation considering its dimensions and effective rotation.
     */
    val isCurrentlyLandscape: Boolean
        get() {
            val isBaseLandscape = width > height
            val isRotated90or270 = (effectiveRotation == 90 || effectiveRotation == 270)
            return if (isRotated90or270) !isBaseLandscape else isBaseLandscape
        }

    /**
     * Normalized display rotation delta (0, 90, 180, 270).
     */
    val normalizedDelta: Int
        get() = ((rotationDelta % 360) + 360) % 360

    /**
     * Whether this page has been modified from its original orientation.
     */
    val hasChanged: Boolean
        get() = normalizedDelta != 0
}

/**
 * Result of the lossless rotation export.
 */
data class RotatePdfResult(
    val file: File,
    val fileName: String,
    val fileSize: Long,
    val totalPages: Int,
    val modifiedPagesCount: Int
)

/**
 * Sealed UI State for the Rotate PDF screen.
 */
sealed class RotateUiState {
    data object Idle : RotateUiState()
    data class Loading(val message: String, val progress: Float = -1f) : RotateUiState()
    data class PasswordRequired(val file: File, val message: String? = null) : RotateUiState()
    data class Success(val result: RotatePdfResult) : RotateUiState()
    data class Error(val message: String) : RotateUiState()
}
