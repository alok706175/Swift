package com.swiftapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.ScanPdfService
import com.swiftapp.data.model.MarginOption
import com.swiftapp.data.model.PageSizeOption
import com.swiftapp.data.model.PolygonCorners
import com.swiftapp.data.model.ScanExportConfig
import com.swiftapp.data.model.ScanFilter
import com.swiftapp.data.model.ScanPageItem
import com.swiftapp.data.model.ScanUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ScanPdfViewModel : ViewModel() {

    private val _pages = MutableStateFlow<List<ScanPageItem>>(emptyList())
    val pages: StateFlow<List<ScanPageItem>> = _pages.asStateFlow()

    private val _activePageIndex = MutableStateFlow(0)
    val activePageIndex: StateFlow<Int> = _activePageIndex.asStateFlow()

    private val _exportConfig = MutableStateFlow(
        ScanExportConfig(
            fileName = defaultFileName(),
            pageSize = PageSizeOption.A4,
            margin = MarginOption.NO_MARGIN,
            compressionQuality = 85
        )
    )
    val exportConfig: StateFlow<ScanExportConfig> = _exportConfig.asStateFlow()

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _isCropDialogVisible = MutableStateFlow(false)
    val isCropDialogVisible: StateFlow<Boolean> = _isCropDialogVisible.asStateFlow()

    private val _isExportDialogVisible = MutableStateFlow(false)
    val isExportDialogVisible: StateFlow<Boolean> = _isExportDialogVisible.asStateFlow()

    private fun defaultFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "Scan_${sdf.format(Date())}"
    }

    fun openCropDialog() {
        _isCropDialogVisible.value = true
    }

    fun closeCropDialog() {
        _isCropDialogVisible.value = false
    }

    fun openExportDialog() {
        _isExportDialogVisible.value = true
    }

    fun closeExportDialog() {
        _isExportDialogVisible.value = false
    }

    fun selectPage(index: Int) {
        if (index in _pages.value.indices) {
            _activePageIndex.value = index
        }
    }

    /**
     * Add an image captured directly from device camera.
     */
    fun addCapturedImage(context: Context, rawFile: File) {
        viewModelScope.launch(Dispatchers.IO) {
            com.swiftapp.utils.ScannerSettingsManager.playShutterSound()
            val dimensions = ScanPdfService.getImageDimensions(rawFile)
            val defaultFilter = com.swiftapp.utils.ScannerSettingsManager.defaultFilterFlow.value
            val initialCorners = com.swiftapp.utils.ScannerSettingsManager.getInitialCorners()

            val newItem = ScanPageItem(
                id = UUID.randomUUID().toString(),
                originalImageFile = rawFile,
                width = dimensions.first,
                height = dimensions.second,
                corners = initialCorners,
                filter = defaultFilter,
                isProcessing = true
            )

            _pages.update { it + newItem }
            val newIndex = _pages.value.size - 1
            _activePageIndex.value = newIndex

            // Generate initial preview
            val previewFile = ScanPdfService.generatePreviewImage(context, newItem)
            _pages.update { list ->
                list.mapIndexed { idx, item ->
                    if (idx == newIndex) item.copy(previewImageFile = previewFile, isProcessing = false) else item
                }
            }
        }
    }

    /**
     * Import multiple images from device gallery or file manager.
     */
    fun importGalleryImages(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ScanUiState.Processing("Importing images...", 0.1f)
            val newItems = mutableListOf<ScanPageItem>()
            val defaultFilter = com.swiftapp.utils.ScannerSettingsManager.defaultFilterFlow.value
            val initialCorners = com.swiftapp.utils.ScannerSettingsManager.getInitialCorners()

            for (uri in uris) {
                val tempFile = ScanPdfService.copyUriToCacheFile(context, uri) ?: continue
                val dimensions = ScanPdfService.getImageDimensions(tempFile)
                val item = ScanPageItem(
                    id = UUID.randomUUID().toString(),
                    originalImageFile = tempFile,
                    width = dimensions.first,
                    height = dimensions.second,
                    corners = initialCorners,
                    filter = defaultFilter,
                    isProcessing = true
                )
                newItems.add(item)
            }

            if (newItems.isNotEmpty()) {
                val startingIndex = _pages.value.size
                _pages.update { it + newItems }
                _activePageIndex.value = startingIndex

                // Generate preview for each
                for ((i, item) in newItems.withIndex()) {
                    val previewFile = ScanPdfService.generatePreviewImage(context, item)
                    val targetIndex = startingIndex + i
                    _pages.update { list ->
                        list.mapIndexed { idx, itm ->
                            if (idx == targetIndex) itm.copy(previewImageFile = previewFile, isProcessing = false) else itm
                        }
                    }
                }
            }

            _uiState.value = ScanUiState.Idle
        }
    }

    /**
     * Update crop polygon corners for a page.
     */
    fun updateCorners(context: Context, pageIndex: Int, corners: PolygonCorners) {
        if (pageIndex !in _pages.value.indices) return
        _pages.update { list ->
            list.mapIndexed { idx, item ->
                if (idx == pageIndex) item.copy(corners = corners, isProcessing = true) else item
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val updatedItem = _pages.value[pageIndex]
            val previewFile = ScanPdfService.generatePreviewImage(context, updatedItem)
            _pages.update { list ->
                list.mapIndexed { idx, item ->
                    if (idx == pageIndex) item.copy(previewImageFile = previewFile, isProcessing = false) else item
                }
            }
        }
    }

    /**
     * Update document filter for a page (Original, Magic Color, B&W Clean, Grayscale).
     */
    fun updateFilter(context: Context, pageIndex: Int, filter: ScanFilter) {
        if (pageIndex !in _pages.value.indices) return
        _pages.update { list ->
            list.mapIndexed { idx, item ->
                if (idx == pageIndex) item.copy(filter = filter, isProcessing = true) else item
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val updatedItem = _pages.value[pageIndex]
            val previewFile = ScanPdfService.generatePreviewImage(context, updatedItem)
            _pages.update { list ->
                list.mapIndexed { idx, item ->
                    if (idx == pageIndex) item.copy(previewImageFile = previewFile, isProcessing = false) else item
                }
            }
        }
    }

    /**
     * Rotate page by 90 degrees.
     */
    fun rotatePage(context: Context, pageIndex: Int, clockwise: Boolean = true) {
        if (pageIndex !in _pages.value.indices) return
        val currentRotation = _pages.value[pageIndex].rotationDegrees
        val newRotation = if (clockwise) (currentRotation + 90) % 360 else (currentRotation + 270) % 360

        _pages.update { list ->
            list.mapIndexed { idx, item ->
                if (idx == pageIndex) item.copy(rotationDegrees = newRotation, isProcessing = true) else item
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val updatedItem = _pages.value[pageIndex]
            val previewFile = ScanPdfService.generatePreviewImage(context, updatedItem)
            _pages.update { list ->
                list.mapIndexed { idx, item ->
                    if (idx == pageIndex) item.copy(previewImageFile = previewFile, isProcessing = false) else item
                }
            }
        }
    }

    /**
     * Delete page at index.
     */
    fun deletePage(pageIndex: Int) {
        if (pageIndex !in _pages.value.indices) return
        _pages.update { list ->
            val mutable = list.toMutableList()
            mutable.removeAt(pageIndex)
            mutable
        }
        if (_activePageIndex.value >= _pages.value.size) {
            _activePageIndex.value = maxOf(0, _pages.value.size - 1)
        }
    }

    /**
     * Reorder pages (Move left/right).
     */
    fun movePage(fromIndex: Int, toIndex: Int) {
        val currentList = _pages.value
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return

        val mutable = currentList.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        _pages.value = mutable
        _activePageIndex.value = toIndex
    }

    /**
     * Update export configuration.
     */
    fun updateExportConfig(config: ScanExportConfig) {
        _exportConfig.value = config
    }

    /**
     * Compile and generate the final PDF file.
     */
    fun generatePdf(context: Context) {
        val pagesToExport = _pages.value
        if (pagesToExport.isEmpty()) {
            _uiState.value = ScanUiState.Error("Please scan or import at least one page.")
            return
        }

        val config = _exportConfig.value
        _isExportDialogVisible.value = false

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ScanUiState.Processing("Compiling PDF pages...", 0.05f)

            val result = ScanPdfService.compileToPdf(
                context = context,
                pages = pagesToExport,
                config = config,
                onProgress = { progress ->
                    _uiState.value = ScanUiState.Processing(
                        "Processing page ${(progress * pagesToExport.size).toInt() + 1} of ${pagesToExport.size}...",
                        progress
                    )
                }
            )

            result.fold(
                onSuccess = { outputFile ->
                    _uiState.value = ScanUiState.Success(
                        outputFile = outputFile,
                        pageCount = pagesToExport.size,
                        fileSize = outputFile.length()
                    )
                },
                onFailure = { error ->
                    _uiState.value = ScanUiState.Error(error.localizedMessage ?: "Failed to compile scanned PDF")
                }
            )
        }
    }

    fun clearAll() {
        resetState()
    }

    fun resetState() {
        _uiState.value = ScanUiState.Idle
        _pages.value = emptyList()
        _activePageIndex.value = 0
        _exportConfig.value = ScanExportConfig(
            fileName = defaultFileName(),
            pageSize = PageSizeOption.A4,
            margin = MarginOption.NO_MARGIN,
            compressionQuality = 85
        )
    }

    fun dismissSuccess() {
        _uiState.value = ScanUiState.Idle
    }
}
