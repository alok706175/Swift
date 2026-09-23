package com.swiftapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.CompressPdfService
import com.swiftapp.data.model.CompressPdfItem
import com.swiftapp.data.model.CompressResult
import com.swiftapp.data.model.CompressUiState
import com.swiftapp.data.model.CompressionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompressPdfViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<CompressPdfItem>>(emptyList())
    val items: StateFlow<List<CompressPdfItem>> = _items.asStateFlow()

    private val _selectedPreset = MutableStateFlow(CompressionPreset.BALANCED)
    val selectedPreset: StateFlow<CompressionPreset> = _selectedPreset.asStateFlow()

    private val _targetSizeKb = MutableStateFlow("200")
    val targetSizeKb: StateFlow<String> = _targetSizeKb.asStateFlow()

    private val _uiState = MutableStateFlow<CompressUiState>(CompressUiState.Idle)
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    fun addUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = CompressUiState.LoadingMetadata(0, uris.size)
            val currentList = _items.value.toMutableList()

            for ((index, uri) in uris.withIndex()) {
                _uiState.value = CompressUiState.LoadingMetadata(index + 1, uris.size)
                val item = CompressPdfService.extractMetadata(context, uri)
                currentList.add(item)
            }

            _items.value = currentList
            _uiState.value = CompressUiState.Idle
        }
    }

    fun removeItem(id: String) {
        _items.value = _items.value.filter { it.id != id }
    }

    fun clearAll() {
        _items.value = emptyList()
        _uiState.value = CompressUiState.Idle
    }

    fun selectPreset(preset: CompressionPreset) {
        _selectedPreset.value = preset
    }

    fun setTargetSizeKb(sizeText: String) {
        _targetSizeKb.value = sizeText.filter { it.isDigit() }
    }

    fun unlockItem(context: Context, id: String, password: String) {
        val target = _items.value.find { it.id == id } ?: return
        viewModelScope.launch {
            val updated = CompressPdfService.extractMetadata(context, target.uri, password)
            _items.value = _items.value.map { if (it.id == id) updated else it }
        }
    }

    fun startCompression(context: Context) {
        val validItems = _items.value.filter { it.isValid }
        if (validItems.isEmpty()) {
            _uiState.value = CompressUiState.Error("Please select at least 1 valid PDF document to compress.")
            return
        }

        viewModelScope.launch {
            _uiState.value = CompressUiState.Processing("Initializing compression engine...", 0.05f)
            val results = mutableListOf<CompressResult>()
            val preset = _selectedPreset.value
            val targetKb = _targetSizeKb.value.toIntOrNull()

            for ((index, item) in validItems.withIndex()) {
                val baseProgress = (index.toFloat() / validItems.size.toFloat())
                val result = CompressPdfService.compressFile(
                    context = context,
                    item = item,
                    preset = preset,
                    targetSizeKb = targetKb,
                    onProgress = { step, progress ->
                        val combinedProgress = (baseProgress + (progress / validItems.size.toFloat())).coerceIn(0f, 1f)
                        _uiState.value = CompressUiState.Processing(step, combinedProgress)
                    }
                )

                result.onSuccess {
                    results.add(it)
                }.onFailure { error ->
                    _uiState.value = CompressUiState.Error(error.localizedMessage ?: "Failed to compress '${item.fileName}'")
                    return@launch
                }
            }

            _uiState.value = CompressUiState.Success(results)
            val firstFile = results.firstOrNull()?.compressedFile
            val notifMsg = if (results.size == 1) {
                val r = results[0]
                "${r.originalItem.fileName} reduced by ${r.reductionPercent}%"
            } else {
                "${results.size} files compressed successfully"
            }
            com.swiftapp.utils.NotificationHelper.showOperationCompleteNotification(
                context = context,
                title = "PDF Compression Complete",
                message = notifMsg,
                file = firstFile
            )
        }
    }

    fun resetCompression() {
        _uiState.value = CompressUiState.Idle
        _items.value = emptyList()
        _selectedPreset.value = CompressionPreset.BALANCED
    }

    fun dismissState() {
        _uiState.value = CompressUiState.Idle
    }
}
