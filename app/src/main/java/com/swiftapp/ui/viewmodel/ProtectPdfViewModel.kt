package com.swiftapp.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.ProtectPdfService
import com.swiftapp.data.model.PasswordStrength
import com.swiftapp.data.model.PdfPermissionsConfig
import com.swiftapp.data.model.ProtectPdfItem
import com.swiftapp.data.model.ProtectUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ProtectPdfViewModel : ViewModel() {

    private val _selectedItem = MutableStateFlow<ProtectPdfItem?>(null)
    val selectedItem: StateFlow<ProtectPdfItem?> = _selectedItem.asStateFlow()

    private val _userPassword = MutableStateFlow("")
    val userPassword: StateFlow<String> = _userPassword.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword: StateFlow<String> = _confirmPassword.asStateFlow()

    private val _ownerPassword = MutableStateFlow("")
    val ownerPassword: StateFlow<String> = _ownerPassword.asStateFlow()

    private val _isUserPasswordVisible = MutableStateFlow(false)
    val isUserPasswordVisible: StateFlow<Boolean> = _isUserPasswordVisible.asStateFlow()

    private val _isConfirmPasswordVisible = MutableStateFlow(false)
    val isConfirmPasswordVisible: StateFlow<Boolean> = _isConfirmPasswordVisible.asStateFlow()

    private val _isOwnerPasswordVisible = MutableStateFlow(false)
    val isOwnerPasswordVisible: StateFlow<Boolean> = _isOwnerPasswordVisible.asStateFlow()

    private val _isAdvancedOpen = MutableStateFlow(false)
    val isAdvancedOpen: StateFlow<Boolean> = _isAdvancedOpen.asStateFlow()

    private val _permissionsConfig = MutableStateFlow(PdfPermissionsConfig())
    val permissionsConfig: StateFlow<PdfPermissionsConfig> = _permissionsConfig.asStateFlow()

    private val _passwordStrength = MutableStateFlow(PasswordStrength.EMPTY)
    val passwordStrength: StateFlow<PasswordStrength> = _passwordStrength.asStateFlow()

    private val _uiState = MutableStateFlow<ProtectUiState>(ProtectUiState.Idle)
    val uiState: StateFlow<ProtectUiState> = _uiState.asStateFlow()

    fun setPdfFile(context: Context, file: File, uri: Uri? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = ProtectPdfService.inspectPdfFile(context, file, uri)
            _selectedItem.value = item
            if (item.isAlreadyEncrypted) {
                _uiState.value = ProtectUiState.Error(
                    "This document is already password-protected. Please unlock it first or select another unencrypted file."
                )
            } else {
                _uiState.value = ProtectUiState.FileSelected(item)
            }
        }
    }

    fun updateUserPassword(pass: String) {
        _userPassword.value = pass
        _passwordStrength.value = ProtectPdfService.calculatePasswordStrength(pass)
    }

    fun updateConfirmPassword(pass: String) {
        _confirmPassword.value = pass
    }

    fun updateOwnerPassword(pass: String) {
        _ownerPassword.value = pass
    }

    fun toggleUserPasswordVisibility() {
        _isUserPasswordVisible.value = !_isUserPasswordVisible.value
    }

    fun toggleConfirmPasswordVisibility() {
        _isConfirmPasswordVisible.value = !_isConfirmPasswordVisible.value
    }

    fun toggleOwnerPasswordVisibility() {
        _isOwnerPasswordVisible.value = !_isOwnerPasswordVisible.value
    }

    fun toggleAdvancedAccordion() {
        _isAdvancedOpen.value = !_isAdvancedOpen.value
    }

    fun updatePermissions(config: PdfPermissionsConfig) {
        _permissionsConfig.value = config
    }

    fun encryptAndProtect(context: Context) {
        val item = _selectedItem.value ?: return
        val userPass = _userPassword.value.trim()
        val confirmPass = _confirmPassword.value.trim()

        if (userPass.isEmpty()) {
            _uiState.value = ProtectUiState.Error("Please enter a password to protect the document.")
            return
        }

        if (userPass != confirmPass) {
            _uiState.value = ProtectUiState.Error("Passwords do not match. Please verify your password.")
            return
        }

        if (item.isAlreadyEncrypted) {
            _uiState.value = ProtectUiState.Error("This file is already protected.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = ProtectUiState.Processing("Encrypting PDF with AES-256...", 0.1f)

            val result = ProtectPdfService.encryptPdf(
                context = context,
                sourcePdf = item.file,
                userPassword = userPass,
                ownerPassword = _ownerPassword.value.takeIf { it.isNotBlank() },
                permissions = _permissionsConfig.value,
                onProgress = { p ->
                    _uiState.value = ProtectUiState.Processing("Applying encryption keys...", p)
                }
            )

            result.fold(
                onSuccess = { outputFile ->
                    _uiState.value = ProtectUiState.Success(
                        outputFile = outputFile,
                        pageCount = item.pageCount,
                        fileSize = outputFile.length(),
                        passwordUsed = userPass
                    )
                },
                onFailure = { error ->
                    _uiState.value = ProtectUiState.Error(error.localizedMessage ?: "Failed to encrypt PDF")
                }
            )
        }
    }

    fun resetState() {
        _uiState.value = ProtectUiState.Idle
        _selectedItem.value = null
        _userPassword.value = ""
        _confirmPassword.value = ""
        _ownerPassword.value = ""
        _passwordStrength.value = PasswordStrength.EMPTY
        _isAdvancedOpen.value = false
        _permissionsConfig.value = PdfPermissionsConfig()
    }

    fun clearAll() {
        resetState()
    }

    fun dismissSuccess() {
        _uiState.value = ProtectUiState.Idle
        _selectedItem.value = null
        _userPassword.value = ""
        _confirmPassword.value = ""
    }
}
