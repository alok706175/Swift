package com.swiftapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.PdfToImagesService
import com.swiftapp.data.model.ConvertedImageItem
import com.swiftapp.data.model.ImageDpiOption
import com.swiftapp.data.model.ImageOutputFormat
import com.swiftapp.data.model.PdfImageExtractMode
import com.swiftapp.data.model.PdfPageThumbnailItem
import com.swiftapp.data.model.PdfToImagesConfig
import com.swiftapp.data.model.PdfToImagesUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class PdfToImagesViewModel : ViewModel() {

    private val _selectedFile = MutableStateFlow<File?>(null)
    val selectedFile: StateFlow<File?> = _selectedFile.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _thumbnails = MutableStateFlow<List<PdfPageThumbnailItem>>(emptyList())
    val thumbnails: StateFlow<List<PdfPageThumbnailItem>> = _thumbnails.asStateFlow()

    private val _config = MutableStateFlow(PdfToImagesConfig())
    val config: StateFlow<PdfToImagesConfig> = _config.asStateFlow()

    private val _password = MutableStateFlow<String?>(null)
    val password: StateFlow<String?> = _password.asStateFlow()

    private val _previewImage = MutableStateFlow<ConvertedImageItem?>(null)
    val previewImage: StateFlow<ConvertedImageItem?> = _previewImage.asStateFlow()

    private val _uiState = MutableStateFlow<PdfToImagesUiState>(PdfToImagesUiState.Idle)
    val uiState: StateFlow<PdfToImagesUiState> = _uiState.asStateFlow()

    fun loadPdfDocument(context: Context, file: File, pass: String? = null) {
        _selectedFile.value = file
        _password.value = pass

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = PdfToImagesUiState.Loading("Reading PDF document...")
            val pageCount = PdfToImagesService.getPageCount(file)
            _totalPages.value = pageCount

            if (pageCount <= 0) {
                _uiState.value = PdfToImagesUiState.Error("Unable to open PDF document. File may be encrypted or damaged.")
                return@launch
            }

            // Create initial thumbnail placeholders
            val initialList = (0 until pageCount).map { index ->
                PdfPageThumbnailItem(pageIndex = index, thumbnailBitmap = null, isSelected = true)
            }
            _thumbnails.value = initialList
            _uiState.value = PdfToImagesUiState.Idle

            // Sequentially render thumbnails
            for (i in 0 until pageCount) {
                val thumb = PdfToImagesService.renderThumbnail(context, file, i, 360)
                _thumbnails.update { list ->
                    list.map { item ->
                        if (item.pageIndex == i) item.copy(thumbnailBitmap = thumb) else item
                    }
                }
            }
        }
    }

    fun togglePageSelection(pageIndex: Int) {
        _thumbnails.update { list ->
            list.map { item ->
                if (item.pageIndex == pageIndex) item.copy(isSelected = !item.isSelected) else item
            }
        }
    }

    fun selectAllPages() {
        _thumbnails.update { list -> list.map { it.copy(isSelected = true) } }
        _config.update { it.copy(pageRangeText = "") }
    }

    fun deselectAllPages() {
        _thumbnails.update { list -> list.map { it.copy(isSelected = false) } }
    }

    fun updatePageRangeText(rangeText: String) {
        _config.update { it.copy(pageRangeText = rangeText) }
        val parsedSet = PdfToImagesService.parsePageRanges(rangeText, _totalPages.value)
        _thumbnails.update { list ->
            list.map { it.copy(isSelected = parsedSet.contains(it.pageIndex)) }
        }
    }

    fun updateExtractMode(mode: PdfImageExtractMode) {
        _config.update { it.copy(extractMode = mode) }
    }

    fun updateOutputFormat(format: ImageOutputFormat) {
        _config.update { it.copy(format = format) }
    }

    fun updateDpi(dpi: ImageDpiOption) {
        _config.update { it.copy(dpi = dpi) }
    }

    fun updateJpgQuality(quality: Int) {
        _config.update { it.copy(jpgQuality = quality) }
    }

    fun openPreview(item: ConvertedImageItem) {
        _previewImage.value = item
    }

    fun closePreview() {
        _previewImage.value = null
    }

    fun convertPdf(context: Context) {
        val file = _selectedFile.value ?: return
        val currentThumbs = _thumbnails.value
        val selectedIndices = currentThumbs.filter { it.isSelected }.map { it.pageIndex }.toSet()

        if (selectedIndices.isEmpty()) {
            _uiState.value = PdfToImagesUiState.Error("Please select at least one page to convert.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = PdfToImagesUiState.Processing("Initializing image converter...", 0.05f)

            val result = PdfToImagesService.convertPdfToImages(
                context = context,
                sourcePdf = file,
                password = _password.value,
                selectedPages = selectedIndices,
                config = _config.value,
                onProgress = { p ->
                    _uiState.value = PdfToImagesUiState.Processing(
                        "Processing images... ${(p * 100).toInt()}%",
                        p
                    )
                }
            )

            result.fold(
                onSuccess = { res ->
                    _uiState.value = PdfToImagesUiState.Success(res)
                },
                onFailure = { err ->
                    _uiState.value = PdfToImagesUiState.Error(err.localizedMessage ?: "Failed to convert PDF to images")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = PdfToImagesUiState.Idle
        _selectedFile.value = null
        _totalPages.value = 0
        _thumbnails.value = emptyList()
        _config.value = PdfToImagesConfig()
        _password.value = null
        _previewImage.value = null
    }

    fun clearAll() {
        resetState()
    }

    fun dismissSuccess() {
        _uiState.value = PdfToImagesUiState.Idle
    }
}
