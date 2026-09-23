package com.swiftapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.ImageToPdfService
import com.swiftapp.data.model.ImageMarginMode
import com.swiftapp.data.model.ImageOrientationMode
import com.swiftapp.data.model.ImagePageSize
import com.swiftapp.data.model.ImageQualityPreset
import com.swiftapp.data.model.ImageScalingMode
import com.swiftapp.data.model.ImageToPdfConfig
import com.swiftapp.data.model.ImageToPdfItem
import com.swiftapp.data.model.ImageToPdfUiState
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

class ImageToPdfViewModel : ViewModel() {

    private val _items = MutableStateFlow<List<ImageToPdfItem>>(emptyList())
    val items: StateFlow<List<ImageToPdfItem>> = _items.asStateFlow()

    private val _config = MutableStateFlow(
        ImageToPdfConfig(
            fileName = defaultFileName(),
            pageSize = ImagePageSize.A4,
            orientation = ImageOrientationMode.AUTO_SMART,
            scaling = ImageScalingMode.FIT_CONTAIN,
            margins = ImageMarginMode.NO_MARGIN,
            quality = ImageQualityPreset.BALANCED
        )
    )
    val config: StateFlow<ImageToPdfConfig> = _config.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _previewItem = MutableStateFlow<ImageToPdfItem?>(null)
    val previewItem: StateFlow<ImageToPdfItem?> = _previewItem.asStateFlow()

    private val _uiState = MutableStateFlow<ImageToPdfUiState>(ImageToPdfUiState.Idle)
    val uiState: StateFlow<ImageToPdfUiState> = _uiState.asStateFlow()

    private fun defaultFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "Images_Document_${sdf.format(Date())}"
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun openPreview(item: ImageToPdfItem) {
        _previewItem.value = item
    }

    fun closePreview() {
        _previewItem.value = null
    }

    fun updateConfig(newConfig: ImageToPdfConfig) {
        _config.value = newConfig
    }

    fun addImagesFromUris(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ImageToPdfUiState.Processing("Importing selected images...", 0.1f)
            val newItems = mutableListOf<ImageToPdfItem>()

            for (uri in uris) {
                val tempFile = ImageToPdfService.copyUriToCacheFile(context, uri) ?: continue
                val dims = ImageToPdfService.getImageDimensions(tempFile)
                val item = ImageToPdfItem(
                    id = UUID.randomUUID().toString(),
                    file = tempFile,
                    name = tempFile.name,
                    width = dims.first,
                    height = dims.second,
                    size = tempFile.length(),
                    rotationDegrees = 0,
                    uri = uri
                )
                newItems.add(item)
            }

            _items.update { it + newItems }
            _uiState.value = ImageToPdfUiState.Idle
        }
    }

    fun rotateImage(id: String, clockwise: Boolean = true) {
        _items.update { list ->
            list.map { item ->
                if (item.id == id) {
                    val delta = if (clockwise) 90 else 270
                    item.copy(rotationDegrees = (item.rotationDegrees + delta) % 360)
                } else item
            }
        }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        val current = _items.value
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return

        val mutable = current.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        _items.value = mutable
    }

    fun deleteImage(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
    }

    fun clearAll() {
        _items.value = emptyList()
    }

    fun convertImagesToPdf(context: Context) {
        val listToConvert = _items.value
        if (listToConvert.isEmpty()) {
            _uiState.value = ImageToPdfUiState.Error("Please select at least one image to convert.")
            return
        }

        val currentConfig = _config.value
        _isSettingsOpen.value = false

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ImageToPdfUiState.Processing("Compiling images into PDF...", 0.1f)

            val result = ImageToPdfService.compileImagesToPdf(
                context = context,
                items = listToConvert,
                config = currentConfig,
                onProgress = { p ->
                    _uiState.value = ImageToPdfUiState.Processing(
                        "Processing image ${(p * listToConvert.size).toInt() + 1} of ${listToConvert.size}...",
                        p
                    )
                }
            )

            result.fold(
                onSuccess = { outputFile ->
                    _uiState.value = ImageToPdfUiState.Success(
                        outputFile = outputFile,
                        pageCount = listToConvert.size,
                        fileSize = outputFile.length()
                    )
                },
                onFailure = { error ->
                    _uiState.value = ImageToPdfUiState.Error(error.localizedMessage ?: "Failed to convert images to PDF")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = ImageToPdfUiState.Idle
        _items.value = emptyList()
        _config.value = ImageToPdfConfig(
            fileName = defaultFileName(),
            pageSize = ImagePageSize.A4,
            orientation = ImageOrientationMode.AUTO_SMART,
            scaling = ImageScalingMode.FIT_CONTAIN,
            margins = ImageMarginMode.NO_MARGIN,
            quality = ImageQualityPreset.BALANCED
        )
    }

    fun dismissSuccess() {
        _uiState.value = ImageToPdfUiState.Idle
    }
}
