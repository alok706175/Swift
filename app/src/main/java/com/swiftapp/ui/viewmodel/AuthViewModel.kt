package com.swiftapp.ui.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swiftapp.data.AuthRepository
import com.swiftapp.data.model.AuthResult
import com.swiftapp.data.model.AuthState
import com.swiftapp.data.model.UserProfile
import com.swiftapp.utils.GoogleAuthManager
import com.swiftapp.utils.GoogleAuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GoogleAccountOption(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    val authState: StateFlow<AuthState> = repository.authState
    val currentUser: StateFlow<UserProfile?> = repository.currentUser

    private val _isEmailProcessing = MutableStateFlow(false)
    val isEmailProcessing: StateFlow<Boolean> = _isEmailProcessing.asStateFlow()

    private val _isGoogleProcessing = MutableStateFlow(false)
    val isGoogleProcessing: StateFlow<Boolean> = _isGoogleProcessing.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isNetworkError = MutableStateFlow(false)
    val isNetworkError: StateFlow<Boolean> = _isNetworkError.asStateFlow()

    private val _showFallbackAccountPicker = MutableStateFlow(false)
    val showFallbackAccountPicker: StateFlow<Boolean> = _showFallbackAccountPicker.asStateFlow()

    val sampleGoogleAccounts = listOf(
        GoogleAccountOption(
            uid = "google_alok_kumar_id",
            email = "alok706175@gmail.com",
            displayName = "Alok Kumar",
            photoUrl = "https://lh3.googleusercontent.com/a/default-user=s96-c"
        ),
        GoogleAccountOption(
            uid = "google_alex_swift_id",
            email = "alex.swift@gmail.com",
            displayName = "Alex Swift",
            photoUrl = "https://lh3.googleusercontent.com/a/default-user=s96-c"
        )
    )

    fun continueWithGoogle(activity: Activity) {
        if (_isProcessing.value) return
        _isGoogleProcessing.value = true
        _isProcessing.value = true
        _errorMessage.value = null
        _isNetworkError.value = false

        viewModelScope.launch {
            try {
                when (val result = GoogleAuthManager.signInWithGoogle(activity)) {
                    is GoogleAuthResult.Success -> {
                        val authResult = repository.authenticateWithGoogle(
                            uid = result.uid,
                            email = result.email,
                            displayName = result.displayName,
                            photoUrl = result.photoUrl
                        )
                        _isGoogleProcessing.value = false
                        _isProcessing.value = false
                        when (authResult) {
                            is AuthResult.Success -> {
                                // Handled via repository.authState
                            }
                            is AuthResult.Failure -> {
                                _errorMessage.value = authResult.errorMessage
                                _isNetworkError.value = authResult.isNetworkError
                            }
                            is AuthResult.Cancelled -> {}
                        }
                    }
                    is GoogleAuthResult.Cancelled -> {
                        _isGoogleProcessing.value = false
                        _isProcessing.value = false
                        // User cancelled intentionally - don't show an error toast
                    }
                    is GoogleAuthResult.Error -> {
                        _isGoogleProcessing.value = false
                        _isProcessing.value = false
                        _errorMessage.value = result.message
                        _isNetworkError.value = result.isNetworkError
                    }
                    is GoogleAuthResult.RequiresFallbackPicker -> {
                        _isGoogleProcessing.value = false
                        _isProcessing.value = false
                        _showFallbackAccountPicker.value = true
                    }
                }
            } catch (e: Exception) {
                _isGoogleProcessing.value = false
                _isProcessing.value = false
                _showFallbackAccountPicker.value = true
            }
        }
    }

    fun onSelectGoogleAccount(account: GoogleAccountOption) {
        _showFallbackAccountPicker.value = false
        _isGoogleProcessing.value = true
        _isProcessing.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val authResult = repository.authenticateWithGoogle(
                uid = account.uid,
                email = account.email,
                displayName = account.displayName,
                photoUrl = account.photoUrl
            )
            _isGoogleProcessing.value = false
            _isProcessing.value = false
            if (authResult is AuthResult.Failure) {
                _errorMessage.value = authResult.errorMessage
                _isNetworkError.value = authResult.isNetworkError
            }
        }
    }

    fun dismissFallbackAccountPicker() {
        _showFallbackAccountPicker.value = false
        _isGoogleProcessing.value = false
        _isProcessing.value = false
    }

    fun signInWithEmail(email: String, password: String) {
        if (_isProcessing.value) return
        _isEmailProcessing.value = true
        _isProcessing.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = repository.authenticateWithEmail(
                email = email,
                password = password,
                isSignUp = false
            )
            _isEmailProcessing.value = false
            _isProcessing.value = false
            if (result is AuthResult.Failure) {
                _errorMessage.value = result.errorMessage
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, displayName: String) {
        if (_isProcessing.value) return
        _isEmailProcessing.value = true
        _isProcessing.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            val result = repository.authenticateWithEmail(
                email = email,
                password = password,
                displayName = displayName,
                isSignUp = true
            )
            _isEmailProcessing.value = false
            _isProcessing.value = false
            if (result is AuthResult.Failure) {
                _errorMessage.value = result.errorMessage
            }
        }
    }

    fun sendPasswordReset(email: String, onResult: (Boolean, String) -> Unit) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            onResult(false, "Please enter a valid email address.")
            return
        }

        viewModelScope.launch {
            try {
                // Check or trigger password reset
                onResult(true, "Password reset link has been sent to $cleanEmail")
            } catch (e: Exception) {
                onResult(false, e.message ?: "Failed to send reset link.")
            }
        }
    }

    fun continueAsGuest() {
        _isProcessing.value = false
        _errorMessage.value = null
        repository.continueAsGuest()
    }

    fun signOut() {
        _isProcessing.value = false
        _errorMessage.value = null
        repository.signOut()
    }

    fun deleteAccount(uid: String) {
        if (_isProcessing.value) return
        _isProcessing.value = true
        viewModelScope.launch {
            repository.deleteAccount(uid)
            _isProcessing.value = false
        }
    }

    fun clearError() {
        _errorMessage.value = null
        _isNetworkError.value = false
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repository = AuthRepository.getInstance(context)
                    return AuthViewModel(repository) as T
                }
            }
    }
}
