package com.swiftapp.data.model

import android.graphics.Bitmap
import java.io.File
import java.util.UUID

/**
 * Splitting mode options for Split PDF feature.
 */
enum class SplitMode(val title: String, val subtitle: String) {
    CUSTOM_RANGES("Custom Ranges", "Split into specified page segments"),
    EXTRACT_PAGES("Extract Pages", "Pick specific pages to extract"),
    BURST_ALL("Split All Pages", "Separate every page into its own PDF")
}

/**
 * Represents a single page range (1-indexed for display).
 */
data class PageRange(
    val id: String = UUID.randomUUID().toString(),
    val fromPage: Int = 1,
    val toPage: Int = 1
) {
    fun isValid(totalPages: Int): Boolean {
        return fromPage in 1..totalPages && toPage in 1..totalPages && fromPage <= toPage
    }

    val pageCount: Int
        get() = if (toPage >= fromPage) (toPage - fromPage + 1) else 0
}

/**
 * Represents a page thumbnail item in the visual grid.
 */
data class SplitPageThumbnailItem(
    val pageIndex: Int, // 0-indexed
    val pageNumber: Int, // 1-indexed
    val thumbnailBitmap: Bitmap? = null,
    val isSelected: Boolean = false
)

/**
 * Configuration options for the PDF splitting process.
 */
data class SplitPdfConfig(
    val mode: SplitMode = SplitMode.CUSTOM_RANGES,
    val ranges: List<PageRange> = listOf(PageRange(fromPage = 1, toPage = 1)),
    val mergeRangesIntoSingleFile: Boolean = false, // In Range Mode: merge all ranges into 1 PDF
    val extractIntoSingleFile: Boolean = true, // In Extract Mode: extract into 1 merged PDF vs individual files
    val rangeTextInput: String = ""
)

/**
 * Result of the Split PDF operation.
 */
data class SplitPdfResult(
    val primaryOutputFile: File, // Single PDF file or ZIP archive
    val outputFiles: List<File>, // All individual generated PDF files
    val isSingleFile: Boolean,
    val totalGeneratedFiles: Int,
    val fileSize: Long
)

/**
 * Sealed UI State for Split PDF screen.
 */
sealed class SplitUiState {
    data object Idle : SplitUiState()
    data class Loading(val message: String, val progress: Float = -1f) : SplitUiState()
    data class PasswordRequired(val file: File, val message: String? = null) : SplitUiState()
    data class Success(val result: SplitPdfResult) : SplitUiState()
    data class Error(val message: String) : SplitUiState()
}
