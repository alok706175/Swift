package com.swiftapp.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.RotatePdfService
import com.swiftapp.data.model.RotatePageFilter
import com.swiftapp.data.model.RotatePageItem
import com.swiftapp.data.model.RotatePdfResult
import com.swiftapp.data.model.RotateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RotatePdfViewModel(application: Application) : AndroidViewModel(application) {

    private val service = RotatePdfService(application.applicationContext)

    private val _selectedFile = MutableStateFlow<File?>(null)
    val selectedFile: StateFlow<File?> = _selectedFile.asStateFlow()

    private val _pages = MutableStateFlow<List<RotatePageItem>>(emptyList())
    val pages: StateFlow<List<RotatePageItem>> = _pages.asStateFlow()

    private val _activeFilter = MutableStateFlow(RotatePageFilter.ALL)
    val activeFilter: StateFlow<RotatePageFilter> = _activeFilter.asStateFlow()

    private val _uiState = MutableStateFlow<RotateUiState>(RotateUiState.Idle)
    val uiState: StateFlow<RotateUiState> = _uiState.asStateFlow()

    private var currentPassword: String? = null

    /**
     * Number of selected pages.
     */
    val selectedCount: Int
        get() = _pages.value.count { it.isSelected }

    /**
     * Total number of pages that have non-zero rotation deltas.
     */
    val modifiedCount: Int
        get() = _pages.value.count { it.hasChanged }

    /**
     * Loads a PDF file from an existing File or creates a temporary copy from Uri.
     */
    fun loadFile(context: Context, file: File?, uri: Uri? = null, password: String? = null) {
        viewModelScope.launch {
            _uiState.value = RotateUiState.Loading("Inspecting document...")
            val targetFile = file ?: uri?.let { copyUriToTemp(context, it) }
            if (targetFile == null || !targetFile.exists()) {
                _uiState.value = RotateUiState.Error("Selected file could not be opened.")
                return@launch
            }

            _selectedFile.value = targetFile
            currentPassword = password

            val inspectResult = service.inspectPdf(targetFile, password)
            inspectResult.fold(
                onSuccess = { initialPages ->
                    _pages.value = initialPages
                    _uiState.value = RotateUiState.Idle
                    loadThumbnails(targetFile)
                },
                onFailure = { error ->
                    if (error is SecurityException) {
                        _uiState.value = RotateUiState.PasswordRequired(targetFile, error.message)
                    } else {
                        _uiState.value = RotateUiState.Error("Failed to open PDF: ${error.localizedMessage}")
                    }
                }
            )
        }
    }

    /**
     * Asynchronously loads thumbnails page by page in the background.
     */
    private fun loadThumbnails(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = _pages.value
            for (item in currentList) {
                val bitmap = service.renderThumbnail(file, item.pageIndex, targetWidth = 320)
                if (bitmap != null) {
                    _pages.value = _pages.value.map { current ->
                        if (current.pageIndex == item.pageIndex) current.copy(thumbnailBitmap = bitmap)
                        else current
                    }
                }
            }
        }
    }

    /**
     * Rotates all pages by a given delta (+90 or -90).
     */
    fun rotateAll(delta: Int) {
        _pages.value = _pages.value.map { page ->
            val newDelta = page.rotationDelta + delta
            page.copy(rotationDelta = newDelta)
        }
    }

    /**
     * Resets all rotation deltas to 0.
     */
    fun resetAll() {
        _pages.value = _pages.value.map { page ->
            page.copy(rotationDelta = 0, isSelected = false)
        }
    }

    /**
     * Rotates a single page by +90 or -90.
     */
    fun rotatePage(pageIndex: Int, delta: Int) {
        _pages.value = _pages.value.map { page ->
            if (page.pageIndex == pageIndex) {
                page.copy(rotationDelta = page.rotationDelta + delta)
            } else {
                page
            }
        }
    }

    /**
     * Rotates all currently selected pages by delta.
     */
    fun rotateSelected(delta: Int) {
        _pages.value = _pages.value.map { page ->
            if (page.isSelected) {
                page.copy(rotationDelta = page.rotationDelta + delta)
            } else {
                page
            }
        }
    }

    /**
     * Rotates pages matching a specific filter (Portrait, Landscape, Odd, Even).
     */
    fun rotateFiltered(filter: RotatePageFilter, delta: Int) {
        _activeFilter.value = filter
        _pages.value = _pages.value.map { page ->
            val matches = when (filter) {
                RotatePageFilter.ALL -> true
                RotatePageFilter.PORTRAIT -> !page.isCurrentlyLandscape
                RotatePageFilter.LANDSCAPE -> page.isCurrentlyLandscape
                RotatePageFilter.ODD -> page.pageNumber % 2 != 0
                RotatePageFilter.EVEN -> page.pageNumber % 2 == 0
            }
            if (matches) {
                page.copy(rotationDelta = page.rotationDelta + delta)
            } else {
                page
            }
        }
    }

    /**
     * Sets the active filter for UI display.
     */
    fun setActiveFilter(filter: RotatePageFilter) {
        _activeFilter.value = filter
    }

    /**
     * Toggles selection state of a specific page.
     */
    fun togglePageSelection(pageIndex: Int) {
        _pages.value = _pages.value.map { page ->
            if (page.pageIndex == pageIndex) page.copy(isSelected = !page.isSelected)
            else page
        }
    }

    /**
     * Selects all pages.
     */
    fun selectAllPages() {
        _pages.value = _pages.value.map { it.copy(isSelected = true) }
    }

    /**
     * Deselects all pages.
     */
    fun deselectAllPages() {
        _pages.value = _pages.value.map { it.copy(isSelected = false) }
    }

    /**
     * Selects pages based on active filter.
     */
    fun selectByFilter(filter: RotatePageFilter) {
        _pages.value = _pages.value.map { page ->
            val matches = when (filter) {
                RotatePageFilter.ALL -> true
                RotatePageFilter.PORTRAIT -> !page.isCurrentlyLandscape
                RotatePageFilter.LANDSCAPE -> page.isCurrentlyLandscape
                RotatePageFilter.ODD -> page.pageNumber % 2 != 0
                RotatePageFilter.EVEN -> page.pageNumber % 2 == 0
            }
            page.copy(isSelected = matches)
        }
    }

    /**
     * Executes lossless PDF rotation and generates final document.
     */
    fun executeRotation(customName: String? = null) {
        val file = _selectedFile.value ?: return
        val currentPages = _pages.value
        if (currentPages.isEmpty()) return

        val deltas = currentPages.associate { it.pageIndex to it.rotationDelta }

        viewModelScope.launch {
            _uiState.value = RotateUiState.Loading("Applying lossless rotation...")
            val result = service.rotatePdfLossless(
                sourceFile = file,
                pageDeltas = deltas,
                password = currentPassword,
                customName = customName
            )

            result.fold(
                onSuccess = { rotateResult ->
                    _uiState.value = RotateUiState.Success(rotateResult)
                },
                onFailure = { error ->
                    _uiState.value = RotateUiState.Error("Failed to rotate PDF: ${error.localizedMessage}")
                }
            )
        }
    }

    /**
     * Saves resulting file to MediaStore Downloads directory.
     */
    suspend fun saveToDownloads(file: File, displayName: String? = null): Result<Uri> {
        return service.saveToDownloads(file, displayName)
    }

    /**
     * Resets current state to initial idle.
     */
    fun resetState() {
        _uiState.value = RotateUiState.Idle
    }

    private suspend fun copyUriToTemp(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val fileName = "rotate_src_${System.currentTimeMillis()}.pdf"
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
