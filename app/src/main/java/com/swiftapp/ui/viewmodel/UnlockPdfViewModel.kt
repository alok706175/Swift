package com.swiftapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.UnlockPdfService
import com.swiftapp.data.model.EncryptionStatus
import com.swiftapp.data.model.UnlockPdfItem
import com.swiftapp.data.model.UnlockUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class UnlockPdfViewModel : ViewModel() {

    private val _selectedItem = MutableStateFlow<UnlockPdfItem?>(null)
    val selectedItem: StateFlow<UnlockPdfItem?> = _selectedItem.asStateFlow()

    private val _passwordInput = MutableStateFlow("")
    val passwordInput: StateFlow<String> = _passwordInput.asStateFlow()

    private val _isPasswordVisible = MutableStateFlow(false)
    val isPasswordVisible: StateFlow<Boolean> = _isPasswordVisible.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _uiState = MutableStateFlow<UnlockUiState>(UnlockUiState.Idle)
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    fun setPdfFile(context: Context, file: File, uri: Uri? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _errorMessage.value = null
            _passwordInput.value = ""
            val item = UnlockPdfService.inspectPdfFile(context, file, uri)
            _selectedItem.value = item
            _uiState.value = UnlockUiState.FileSelected(item)
        }
    }

    fun updatePassword(pass: String) {
        _passwordInput.value = pass
        _errorMessage.value = null
    }

    fun togglePasswordVisibility() {
        _isPasswordVisible.value = !_isPasswordVisible.value
    }

    fun unlockDocument(context: Context) {
        val item = _selectedItem.value ?: return
        val pass = _passwordInput.value

        if (item.encryptionStatus == EncryptionStatus.PASSWORD_PROTECTED && pass.isBlank()) {
            _errorMessage.value = "Please enter the document password."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UnlockUiState.Processing("Decrypting and stripping security restrictions...", 0.2f)

            val result = UnlockPdfService.unlockAndStripSecurity(
                context = context,
                sourcePdf = item.file,
                password = pass.takeIf { it.isNotBlank() },
                onProgress = { p ->
                    _uiState.value = UnlockUiState.Processing("Removing security policies...", p)
                }
            )

            result.fold(
                onSuccess = { outputFile ->
                    _errorMessage.value = null
                    _uiState.value = UnlockUiState.Success(
                        outputFile = outputFile,
                        pageCount = item.pageCount,
                        fileSize = outputFile.length()
                    )
                },
                onFailure = { error ->
                    val msg = error.localizedMessage ?: "Failed to unlock PDF"
                    _errorMessage.value = msg
                    _uiState.value = UnlockUiState.FileSelected(item)
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = UnlockUiState.Idle
        _selectedItem.value = null
        _passwordInput.value = ""
        _errorMessage.value = null
    }

    fun clearAll() {
        resetState()
    }

    fun dismissSuccess() {
        _uiState.value = UnlockUiState.Idle
        _selectedItem.value = null
        _passwordInput.value = ""
        _errorMessage.value = null
    }
}
