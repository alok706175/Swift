package com.swiftapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.DeletePdfPagesService
import com.swiftapp.data.UnlockPdfService
import com.swiftapp.data.model.DeletePageItem
import com.swiftapp.data.model.DeletePagesResult
import com.swiftapp.data.model.DeletePagesUiState
import com.swiftapp.data.model.EncryptionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DeletePagesViewModel : ViewModel() {

    private val _selectedFile = MutableStateFlow<File?>(null)
    val selectedFile: StateFlow<File?> = _selectedFile.asStateFlow()

    private val _pages = MutableStateFlow<List<DeletePageItem>>(emptyList())
    val pages: StateFlow<List<DeletePageItem>> = _pages.asStateFlow()

    private val _uiState = MutableStateFlow<DeletePagesUiState>(DeletePagesUiState.Idle)
    val uiState: StateFlow<DeletePagesUiState> = _uiState.asStateFlow()

    private var currentPassword: String? = null

    fun loadFile(context: Context, file: File?, uri: Uri? = null, password: String? = null) {
        viewModelScope.launch {
            _uiState.value = DeletePagesUiState.LoadingThumbnails(0f, 0, 0)
            currentPassword = password

            val targetFile = file ?: run {
                if (uri == null) return@launch
                withContext(Dispatchers.IO) {
                    try {
                        val temp = File(context.cacheDir, "del_load_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(temp).use { output ->
                                input.copyTo(output)
                            }
                        }
                        temp
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            if (targetFile == null || !targetFile.exists()) {
                _uiState.value = DeletePagesUiState.Error("Could not access or copy the selected PDF file.")
                return@launch
            }

            _selectedFile.value = targetFile

            // Check encryption
            val inspect = UnlockPdfService.inspectPdfFile(context, targetFile)
            if (inspect.encryptionStatus == EncryptionStatus.PASSWORD_PROTECTED && password == null) {
                _uiState.value = DeletePagesUiState.PasswordRequired(targetFile)
                return@launch
            }

            val service = DeletePdfPagesService(context)
            val inspectResult = service.inspectPdf(targetFile, password)

            inspectResult.fold(
                onSuccess = { totalPages ->
                    if (totalPages <= 0) {
                        _uiState.value = DeletePagesUiState.Error("This PDF document contains no pages.")
                        return@fold
                    }

                    val pageList = mutableListOf<DeletePageItem>()
                    for (i in 0 until totalPages) {
                        pageList.add(
                            DeletePageItem(
                                pageIndex = i,
                                pageNumber = i + 1,
                                bitmap = null,
                                isSelectedForDeletion = false
                            )
                        )
                    }
                    _pages.value = pageList
                    _uiState.value = DeletePagesUiState.Ready

                    // Render page thumbnails in background
                    viewModelScope.launch(Dispatchers.IO) {
                        val updatedList = _pages.value.toMutableList()
                        for (i in 0 until totalPages) {
                            val bmp = service.renderThumbnail(targetFile, i, 300)
                            if (i < updatedList.size) {
                                updatedList[i] = updatedList[i].copy(bitmap = bmp)
                                _pages.value = updatedList.toList()
                            }
                        }
                    }
                },
                onFailure = { error ->
                    if (error is SecurityException) {
                        _uiState.value = DeletePagesUiState.PasswordRequired(targetFile)
                    } else {
                        _uiState.value = DeletePagesUiState.Error(error.localizedMessage ?: "Failed to inspect PDF.")
                    }
                }
            )
        }
    }

    fun togglePageDeletion(pageIndex: Int) {
        val current = _pages.value.toMutableList()
        val index = current.indexOfFirst { it.pageIndex == pageIndex }
        if (index >= 0) {
            val item = current[index]
            current[index] = item.copy(isSelectedForDeletion = !item.isSelectedForDeletion)
            _pages.value = current
        }
    }

    fun selectAll() {
        _pages.value = _pages.value.map { it.copy(isSelectedForDeletion = true) }
    }

    fun deselectAll() {
        _pages.value = _pages.value.map { it.copy(isSelectedForDeletion = false) }
    }

    fun selectOddPages() {
        _pages.value = _pages.value.map { item ->
            item.copy(isSelectedForDeletion = (item.pageNumber % 2 != 0))
        }
    }

    fun selectEvenPages() {
        _pages.value = _pages.value.map { item ->
            item.copy(isSelectedForDeletion = (item.pageNumber % 2 == 0))
        }
    }

    fun deleteSelectedPages(context: Context, customName: String? = null) {
        val file = _selectedFile.value ?: return
        val pagesList = _pages.value
        val deletedIndices = pagesList.filter { it.isSelectedForDeletion }.map { it.pageIndex }.toSet()

        if (deletedIndices.isEmpty()) {
            _uiState.value = DeletePagesUiState.Error("Please select at least one page to delete.")
            return
        }

        if (deletedIndices.size >= pagesList.size) {
            _uiState.value = DeletePagesUiState.Error("You cannot delete all pages. At least one page must remain.")
            return
        }

        viewModelScope.launch {
            _uiState.value = DeletePagesUiState.Processing(0f, "Starting...")
            val service = DeletePdfPagesService(context)

            val result = service.deletePagesAndSave(
                sourceFile = file,
                password = currentPassword,
                deletedPageIndices = deletedIndices,
                customOutputName = customName,
                onProgress = { prog, msg ->
                    _uiState.value = DeletePagesUiState.Processing(prog, msg)
                }
            )

            result.fold(
                onSuccess = { res ->
                    _uiState.value = DeletePagesUiState.Success(res)
                },
                onFailure = { error ->
                    _uiState.value = DeletePagesUiState.Error(error.localizedMessage ?: "Failed to remove pages.")
                }
            )
        }
    }

    fun reset() {
        _selectedFile.value = null
        _pages.value = emptyList()
        _uiState.value = DeletePagesUiState.Idle
        currentPassword = null
    }
}
