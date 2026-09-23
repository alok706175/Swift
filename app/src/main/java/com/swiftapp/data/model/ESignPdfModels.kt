package com.swiftapp.data.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * Types of elements that can be placed onto a PDF page.
 */
enum class SignElementType(val displayName: String) {
    SIGNATURE("Signature"),
    INITIALS("Initials"),
    TEXT("Text"),
    DATE("Date"),
    CHECKMARK("Checkmark"),
    CROSS("Cross")
}

/**
 * Modes available for creating signatures.
 */
enum class SignatureCreationMode(val title: String) {
    DRAW("Draw"),
    TYPE("Type"),
    UPLOAD("Upload Image"),
    SAVED("Saved")
}

/**
 * Stylized handwriting fonts for typed signatures.
 */
enum class HandwritingStyle(val styleName: String) {
    CURSIVE_CLASSIC("Classic Script"),
    ELEGANT_SERIF("Elegant Cursive"),
    MODERN_SIGNATURE("Modern Signature"),
    CASUAL_HANDWRITING("Casual Flow")
}

/**
 * Represents an active draggable / resizable annotation on a PDF page.
 * Coordinates (xNorm, yNorm, widthNorm, heightNorm) are stored normalized (0.0f..1.0f)
 * relative to the PDF page width and height to maintain exact positioning under any zoom or resolution.
 */
data class SignElementItem(
    val id: String,
    val type: SignElementType,
    val pageIndex: Int,
    val xNorm: Float,
    val yNorm: Float,
    val widthNorm: Float,
    val heightNorm: Float,
    val bitmap: Bitmap? = null,
    val text: String? = null,
    val textColor: Color = Color.Black,
    val fontSizeSp: Float = 16f
)

/**
 * Represents a saved signature or initial stored locally.
 */
data class SavedSignatureItem(
    val id: String,
    val name: String,
    val file: File,
    val dateCreated: Long = System.currentTimeMillis()
)

/**
 * PDF Document details and page cache.
 */
data class ESignDocumentInfo(
    val file: File,
    val pageCount: Int,
    val isEncrypted: Boolean = false,
    val password: String? = null
)

/**
 * UI State for the E-Sign PDF module.
 */
sealed interface ESignUiState {
    object Idle : ESignUiState
    data class Loading(val message: String) : ESignUiState
    data class DocumentLoaded(val info: ESignDocumentInfo) : ESignUiState
    data class Processing(val message: String, val progress: Float) : ESignUiState
    data class Success(
        val outputFile: File,
        val pageCount: Int,
        val fileSize: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : ESignUiState
    data class Error(val message: String) : ESignUiState
}
