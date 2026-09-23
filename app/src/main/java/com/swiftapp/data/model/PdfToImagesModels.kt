package com.swiftapp.data.model

import android.graphics.Bitmap
import android.net.Uri
import java.io.File

/**
 * Operational extraction mode.
 */
enum class PdfImageExtractMode(val title: String, val subtitle: String) {
    RENDER_PAGES("Convert Entire Pages", "Renders complete pages including text, backgrounds, and layout"),
    EXTRACT_EMBEDDED("Extract Embedded Images Only", "Extracts only raw photos and graphics without page margins")
}

/**
 * Image output format.
 */
enum class ImageOutputFormat(val displayName: String, val extension: String, val mimeType: String) {
    JPG("JPG / JPEG", "jpg", "image/jpeg"),
    PNG("PNG (Lossless)", "png", "image/png")
}

/**
 * Resolution / DPI option.
 */
enum class ImageDpiOption(val displayName: String, val dpi: Int, val scaleFactor: Float) {
    SCREEN_72("Screen (72 DPI)", 72, 1.0f),
    STANDARD_150("Standard (150 DPI)", 150, 2.0f),
    HIGH_300("High Res (300 DPI)", 300, 3.5f)
}

/**
 * Thumbnail item for visual page selection.
 */
data class PdfPageThumbnailItem(
    val pageIndex: Int,
    val thumbnailBitmap: Bitmap? = null,
    val isSelected: Boolean = true
)

/**
 * Details of a generated/converted image.
 */
data class ConvertedImageItem(
    val index: Int,
    val name: String,
    val file: File,
    val width: Int,
    val height: Int,
    val size: Long
)

/**
 * Configuration settings for the PDF to Images conversion.
 */
data class PdfToImagesConfig(
    val extractMode: PdfImageExtractMode = PdfImageExtractMode.RENDER_PAGES,
    val format: ImageOutputFormat = ImageOutputFormat.JPG,
    val dpi: ImageDpiOption = ImageDpiOption.STANDARD_150,
    val jpgQuality: Int = 85,
    val pageRangeText: String = ""
)

/**
 * Output result payload.
 */
data class PdfToImagesResult(
    val outputZipFile: File?,
    val individualImages: List<ConvertedImageItem>,
    val totalImages: Int,
    val totalSize: Long,
    val isSingleImage: Boolean = false
)

/**
 * Reactive UI state for the PDF to Images converter.
 */
sealed interface PdfToImagesUiState {
    object Idle : PdfToImagesUiState
    data class Loading(val message: String) : PdfToImagesUiState
    data class Processing(val message: String, val progress: Float) : PdfToImagesUiState
    data class Success(val result: PdfToImagesResult) : PdfToImagesUiState
    data class Error(val message: String) : PdfToImagesUiState
}
