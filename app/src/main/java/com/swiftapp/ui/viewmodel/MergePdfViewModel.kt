package com.swiftapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.MergePdfService
import com.swiftapp.data.model.MergeConfig
import com.swiftapp.data.model.MergePdfItem
import com.swiftapp.data.model.MergeUiState
import com.swiftapp.data.model.PageOrientationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MergePdfViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<MergePdfItem>>(emptyList())
    val items: StateFlow<List<MergePdfItem>> = _items.asStateFlow()

    private val _config = MutableStateFlow(
        MergeConfig(
            outputFileName = com.swiftapp.utils.FileNamingManager.generateFileName("Merged")
        )
    )
    val config: StateFlow<MergeConfig> = _config.asStateFlow()

    private val _uiState = MutableStateFlow<MergeUiState>(MergeUiState.Idle)
    val uiState: StateFlow<MergeUiState> = _uiState.asStateFlow()

    fun addUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = MergeUiState.LoadingMetadata(0, uris.size)
            val currentList = _items.value.toMutableList()

            for ((index, uri) in uris.withIndex()) {
                _uiState.value = MergeUiState.LoadingMetadata(index + 1, uris.size)
                val item = MergePdfService.extractMetadata(context, uri)
                currentList.add(item)
            }

            _items.value = currentList
            _uiState.value = MergeUiState.Idle
        }
    }

    fun removeItem(id: String) {
        _items.value = _items.value.filter { it.id != id }
    }

    fun moveUp(index: Int) {
        if (index <= 0 || index >= _items.value.size) return
        val current = _items.value.toMutableList()
        val temp = current[index]
        current[index] = current[index - 1]
        current[index - 1] = temp
        _items.value = current
    }

    fun moveDown(index: Int) {
        if (index < 0 || index >= _items.value.size - 1) return
        val current = _items.value.toMutableList()
        val temp = current[index]
        current[index] = current[index + 1]
        current[index + 1] = temp
        _items.value = current
    }

    fun clearAll() {
        _items.value = emptyList()
        _uiState.value = MergeUiState.Idle
    }

    fun updatePageRange(id: String, pageRange: String) {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(pageRange = pageRange.trim()) else it
        }
    }

    fun unlockItem(context: Context, id: String, password: String) {
        val target = _items.value.find { it.id == id } ?: return
        viewModelScope.launch {
            val updated = MergePdfService.extractMetadata(context, target.uri, password)
            _items.value = _items.value.map { if (it.id == id) updated else it }
        }
    }

    fun setOutputFileName(name: String) {
        _config.value = _config.value.copy(outputFileName = name)
    }

    fun setOrientation(mode: PageOrientationMode) {
        _config.value = _config.value.copy(orientationMode = mode)
    }

    fun startMerge(context: Context) {
        val validItems = _items.value.filter { it.isValid }
        if (validItems.size < 2) {
            _uiState.value = MergeUiState.Error("Please select at least 2 valid PDF documents to merge.")
            return
        }

        viewModelScope.launch {
            _uiState.value = MergeUiState.Processing("Preparing files...", 0f)
            val result = MergePdfService.performMerge(
                context = context,
                items = validItems,
                config = _config.value,
                onProgress = { step, progress ->
                    _uiState.value = MergeUiState.Processing(step, progress)
                }
            )

            result.onSuccess { file ->
                var totalPages = 0
                for (item in validItems) {
                    val count = MergePdfService.parsePageRange(item.pageRange, item.pageCount).size
                    totalPages += count
                }
                _uiState.value = MergeUiState.Success(
                    file = file,
                    fileName = file.name,
                    fileSizeBytes = file.length(),
                    totalPages = totalPages,
                )
                com.swiftapp.utils.NotificationHelper.showOperationCompleteNotification(
                    context = context,
                    title = "PDF Merge Complete",
                    message = "${file.name} created successfully with $totalPages pages",
                    file = file
                )
            }.onFailure { error ->
                _uiState.value = MergeUiState.Error(error.localizedMessage ?: "Merge failed due to an unknown error.")
            }
        }
    }

    fun resetMerge() {
        _uiState.value = MergeUiState.Idle
        _items.value = emptyList()
        _config.value = MergeConfig(
            outputFileName = com.swiftapp.utils.FileNamingManager.generateFileName("Merged")
        )
    }

    fun dismissState() {
        _uiState.value = MergeUiState.Idle
    }
}
