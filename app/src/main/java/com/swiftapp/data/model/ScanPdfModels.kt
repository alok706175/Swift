package com.swiftapp.data.model

import androidx.compose.ui.geometry.Offset
import java.io.File

/**
 * Image enhancement filter options for scanned pages.
 */
enum class ScanFilter(val displayName: String, val description: String) {
    ORIGINAL("Original", "Natural photo colors with balanced contrast"),
    MAGIC_COLOR("Magic Color", "Sharp text with brightened background"),
    BW_DOCUMENT("B&W Document", "High-contrast clean black & white for documents"),
    GRAYSCALE("Grayscale", "Smooth monochrome grayscale transitions")
}

/**
 * Target PDF page dimension options.
 */
enum class PageSizeOption(val displayName: String, val subtitle: String) {
    A4("A4 Standard", "210 x 297 mm"),
    LETTER("US Letter", "8.5 x 11 in"),
    FIT_IMAGE("Fit to Image", "Match original image aspect ratio")
}

/**
 * Page margin options.
 */
enum class MarginOption(val displayName: String, val marginPoints: Float) {
    NO_MARGIN("Full Bleed (0mm)", 0f),
    STANDARD("Standard (10mm)", 28.35f)
}

/**
 * Normalized 4-point quadrilateral (0.0f..1.0f relative to image width/height).
 * Order: Top-Left, Top-Right, Bottom-Right, Bottom-Left
 */
data class PolygonCorners(
    val topLeft: Offset = Offset(0.05f, 0.05f),
    val topRight: Offset = Offset(0.95f, 0.05f),
    val bottomRight: Offset = Offset(0.95f, 0.95f),
    val bottomLeft: Offset = Offset(0.05f, 0.95f)
) {
    fun toList(): List<Offset> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    companion object {
        val DEFAULT = PolygonCorners(
            topLeft = Offset(0.05f, 0.05f),
            topRight = Offset(0.95f, 0.05f),
            bottomRight = Offset(0.95f, 0.95f),
            bottomLeft = Offset(0.05f, 0.95f)
        )
        val FULL = PolygonCorners(
            topLeft = Offset(0f, 0f),
            topRight = Offset(1f, 0f),
            bottomRight = Offset(1f, 1f),
            bottomLeft = Offset(0f, 1f)
        )
    }
}

/**
 * Represents a single captured or imported page.
 */
data class ScanPageItem(
    val id: String,
    val originalImageFile: File,
    val width: Int,
    val height: Int,
    val corners: PolygonCorners = PolygonCorners.DEFAULT,
    val rotationDegrees: Int = 0,
    val filter: ScanFilter = ScanFilter.MAGIC_COLOR,
    val previewImageFile: File? = null,
    val isProcessing: Boolean = false
)

/**
 * Export configuration for final PDF compilation.
 */
data class ScanExportConfig(
    val fileName: String,
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val margin: MarginOption = MarginOption.NO_MARGIN,
    val compressionQuality: Int = 85
)

/**
 * UI State for the Scan to PDF screen.
 */
sealed interface ScanUiState {
    object Idle : ScanUiState
    data class Processing(val message: String, val progress: Float) : ScanUiState
    data class Success(
        val outputFile: File,
        val pageCount: Int,
        val fileSize: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : ScanUiState
    data class Error(val message: String) : ScanUiState
}
