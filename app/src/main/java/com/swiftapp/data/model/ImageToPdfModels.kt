package com.swiftapp.data.model

import android.net.Uri
import java.io.File

/**
 * Standard page size options.
 */
enum class ImagePageSize(val displayName: String, val subtitle: String) {
    FIT_IMAGE("Fit to Image", "Match each photo's aspect ratio"),
    A4("A4 Standard", "210 x 297 mm"),
    LETTER("US Letter", "8.5 x 11 in")
}

/**
 * Page orientation strategy for standard sizes.
 */
enum class ImageOrientationMode(val displayName: String) {
    AUTO_SMART("Auto (Smart Match)"),
    PORTRAIT("Portrait Only"),
    LANDSCAPE("Landscape Only")
}

/**
 * Image scaling / fitting mode.
 */
enum class ImageScalingMode(val displayName: String) {
    FIT_CONTAIN("Fit with Margins"),
    FILL_COVER("Fill Page (Cover)")
}

/**
 * Margins options for the PDF pages.
 */
enum class ImageMarginMode(val displayName: String, val marginPoints: Float) {
    NO_MARGIN("No Margin (0mm)", 0f),
    SMALL("Small Margin (5mm)", 14.17f),
    STANDARD("Standard Margin (12mm)", 34.0f)
}

/**
 * Image quality and compression preset.
 */
enum class ImageQualityPreset(val displayName: String, val qualityPercent: Int, val description: String) {
    HIGH("High Quality (95%)", 95, "Maximum detail preservation"),
    BALANCED("Balanced (80%)", 80, "Optimal quality & file size (Recommended)"),
    LOW_SIZE("Small Size (55%)", 55, "Smallest file size for strict upload limits")
}

/**
 * Represents a single image item in the conversion queue.
 */
data class ImageToPdfItem(
    val id: String,
    val file: File,
    val name: String,
    val width: Int,
    val height: Int,
    val size: Long,
    val rotationDegrees: Int = 0,
    val uri: Uri? = null
)

/**
 * Layout and compilation configuration.
 */
data class ImageToPdfConfig(
    val fileName: String,
    val pageSize: ImagePageSize = ImagePageSize.A4,
    val orientation: ImageOrientationMode = ImageOrientationMode.AUTO_SMART,
    val scaling: ImageScalingMode = ImageScalingMode.FIT_CONTAIN,
    val margins: ImageMarginMode = ImageMarginMode.NO_MARGIN,
    val quality: ImageQualityPreset = ImageQualityPreset.BALANCED
)

/**
 * Reactive UI state for the Images to PDF converter.
 */
sealed interface ImageToPdfUiState {
    object Idle : ImageToPdfUiState
    data class Processing(val message: String, val progress: Float) : ImageToPdfUiState
    data class Success(
        val outputFile: File,
        val pageCount: Int,
        val fileSize: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : ImageToPdfUiState
    data class Error(val message: String) : ImageToPdfUiState
}
