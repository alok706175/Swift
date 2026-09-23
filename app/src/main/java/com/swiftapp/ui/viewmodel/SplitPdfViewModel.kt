package com.swiftapp.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.SplitPdfService
import com.swiftapp.data.model.PageRange
import com.swiftapp.data.model.SplitMode
import com.swiftapp.data.model.SplitPageThumbnailItem
import com.swiftapp.data.model.SplitPdfConfig
import com.swiftapp.data.model.SplitUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SplitPdfViewModel(application: Application) : AndroidViewModel(application) {

    private val service = SplitPdfService(application.applicationContext)

    private val _selectedFile = MutableStateFlow<File?>(null)
    val selectedFile: StateFlow<File?> = _selectedFile.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _thumbnails = MutableStateFlow<List<SplitPageThumbnailItem>>(emptyList())
    val thumbnails: StateFlow<List<SplitPageThumbnailItem>> = _thumbnails.asStateFlow()

    private val _config = MutableStateFlow(SplitPdfConfig())
    val config: StateFlow<SplitPdfConfig> = _config.asStateFlow()

    private val _uiState = MutableStateFlow<SplitUiState>(SplitUiState.Idle)
    val uiState: StateFlow<SplitUiState> = _uiState.asStateFlow()

    private var currentPassword: String? = null

    val selectedPagesCount: Int
        get() = _thumbnails.value.count { it.isSelected }

    fun loadFile(context: Context, file: File?, uri: Uri? = null, password: String? = null) {
        viewModelScope.launch {
            _uiState.value = SplitUiState.Loading("Inspecting document...")
            val targetFile = file ?: uri?.let { copyUriToTemp(context, it) }
            if (targetFile == null || !targetFile.exists()) {
                _uiState.value = SplitUiState.Error("Selected file could not be opened.")
                return@launch
            }

            _selectedFile.value = targetFile
            currentPassword = password

            val inspectResult = service.inspectPdf(targetFile, password)
            inspectResult.fold(
                onSuccess = { count ->
                    _totalPages.value = count
                    _thumbnails.value = (0 until count).map { idx ->
                        SplitPageThumbnailItem(
                            pageIndex = idx,
                            pageNumber = idx + 1,
                            thumbnailBitmap = null,
                            isSelected = true // Select all by default for Extract mode
                        )
                    }
                    _config.value = SplitPdfConfig(
                        mode = SplitMode.CUSTOM_RANGES,
                        ranges = listOf(PageRange(fromPage = 1, toPage = minOf(count, 1))),
                        mergeRangesIntoSingleFile = false,
                        extractIntoSingleFile = true,
                        rangeTextInput = if (count > 0) "1-$count" else "1"
                    )
                    _uiState.value = SplitUiState.Idle
                    loadThumbnails(targetFile)
                },
                onFailure = { error ->
                    if (error is SecurityException) {
                        _uiState.value = SplitUiState.PasswordRequired(targetFile, error.message)
                    } else {
                        _uiState.value = SplitUiState.Error("Failed to open PDF: ${error.localizedMessage}")
                    }
                }
            )
        }
    }

    private fun loadThumbnails(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val total = _totalPages.value
            for (i in 0 until total) {
                val bitmap = service.renderThumbnail(file, i, targetWidth = 300)
                if (bitmap != null) {
                    _thumbnails.value = _thumbnails.value.map { current ->
                        if (current.pageIndex == i) current.copy(thumbnailBitmap = bitmap)
                        else current
                    }
                }
            }
        }
    }

    fun setSplitMode(mode: SplitMode) {
        _config.value = _config.value.copy(mode = mode)
    }

    fun addRange() {
        val total = _totalPages.value
        val currentRanges = _config.value.ranges
        val lastToPage = currentRanges.lastOrNull()?.toPage ?: 0
        val nextFrom = if (lastToPage < total) lastToPage + 1 else 1
        val nextTo = if (nextFrom < total) nextFrom else total

        _config.value = _config.value.copy(
            ranges = currentRanges + PageRange(fromPage = nextFrom, toPage = nextTo)
        )
    }

    fun removeRange(id: String) {
        val currentRanges = _config.value.ranges
        if (currentRanges.size > 1) {
            _config.value = _config.value.copy(
                ranges = currentRanges.filterNot { it.id == id }
            )
        }
    }

    fun updateRange(id: String, from: Int, to: Int) {
        val total = _totalPages.value
        val clampedFrom = from.coerceIn(1, total)
        val clampedTo = to.coerceIn(1, total)

        _config.value = _config.value.copy(
            ranges = _config.value.ranges.map { range ->
                if (range.id == id) range.copy(fromPage = clampedFrom, toPage = clampedTo)
                else range
            }
        )
    }

    fun setMergeRangesIntoSingleFile(merge: Boolean) {
        _config.value = _config.value.copy(mergeRangesIntoSingleFile = merge)
    }

    fun setExtractIntoSingleFile(single: Boolean) {
        _config.value = _config.value.copy(extractIntoSingleFile = single)
    }

    fun togglePageSelection(pageIndex: Int) {
        _thumbnails.value = _thumbnails.value.map { item ->
            if (item.pageIndex == pageIndex) item.copy(isSelected = !item.isSelected)
            else item
        }
        updateRangeTextFromSelection()
    }

    fun selectAllPages() {
        _thumbnails.value = _thumbnails.value.map { it.copy(isSelected = true) }
        updateRangeTextFromSelection()
    }

    fun deselectAllPages() {
        _thumbnails.value = _thumbnails.value.map { it.copy(isSelected = false) }
        _config.value = _config.value.copy(rangeTextInput = "")
    }

    fun selectOddPages() {
        _thumbnails.value = _thumbnails.value.map { it.copy(isSelected = it.pageNumber % 2 != 0) }
        updateRangeTextFromSelection()
    }

    fun selectEvenPages() {
        _thumbnails.value = _thumbnails.value.map { it.copy(isSelected = it.pageNumber % 2 == 0) }
        updateRangeTextFromSelection()
    }

    fun setRangeTextInput(text: String) {
        _config.value = _config.value.copy(rangeTextInput = text)
        val selectedIndices = service.parsePageRangeInput(text, _totalPages.value)
        _thumbnails.value = _thumbnails.value.map { item ->
            item.copy(isSelected = selectedIndices.contains(item.pageIndex))
        }
    }

    private fun updateRangeTextFromSelection() {
        val selected = _thumbnails.value.filter { it.isSelected }.map { it.pageNumber }.sorted()
        if (selected.isEmpty()) {
            _config.value = _config.value.copy(rangeTextInput = "")
            return
        }

        // Generate compact representation e.g. 1-3, 5, 7-9
        val ranges = mutableListOf<String>()
        var start = selected[0]
        var prev = selected[0]

        for (i in 1 until selected.size) {
            val curr = selected[i]
            if (curr == prev + 1) {
                prev = curr
            } else {
                ranges.add(if (start == prev) "$start" else "$start-$prev")
                start = curr
                prev = curr
            }
        }
        ranges.add(if (start == prev) "$start" else "$start-$prev")
        _config.value = _config.value.copy(rangeTextInput = ranges.joinToString(", "))
    }

    fun executeSplit(customName: String? = null) {
        val file = _selectedFile.value ?: return
        val currentConfig = _config.value
        val total = _totalPages.value

        // Validation
        when (currentConfig.mode) {
            SplitMode.CUSTOM_RANGES -> {
                val hasInvalid = currentConfig.ranges.any { !it.isValid(total) }
                if (hasInvalid) {
                    _uiState.value = SplitUiState.Error("Please ensure all page ranges are valid (between 1 and $total).")
                    return
                }
            }
            SplitMode.EXTRACT_PAGES -> {
                val selectedIndices = _thumbnails.value.filter { it.isSelected }.map { it.pageIndex }
                if (selectedIndices.isEmpty()) {
                    _uiState.value = SplitUiState.Error("Please select at least one page to extract.")
                    return
                }
            }
            SplitMode.BURST_ALL -> {
                if (total <= 0) {
                    _uiState.value = SplitUiState.Error("No pages found to split.")
                    return
                }
            }
        }

        val selectedIndices = _thumbnails.value.filter { it.isSelected }.map { it.pageIndex }

        viewModelScope.launch {
            _uiState.value = SplitUiState.Loading("Splitting PDF document...")
            val result = service.splitPdf(
                sourceFile = file,
                config = currentConfig,
                selectedPageIndices = selectedIndices,
                password = currentPassword,
                customName = customName
            )

            result.fold(
                onSuccess = { splitResult ->
                    _uiState.value = SplitUiState.Success(splitResult)
                },
                onFailure = { error ->
                    _uiState.value = SplitUiState.Error("Failed to split PDF: ${error.localizedMessage}")
                }
            )
        }
    }

    suspend fun saveToDownloads(file: File, displayName: String? = null): Result<Uri> {
        return service.saveToDownloads(file, displayName)
    }

    fun resetState() {
        _uiState.value = SplitUiState.Idle
    }

    private suspend fun copyUriToTemp(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val fileName = "split_src_${System.currentTimeMillis()}.pdf"
            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }
}
