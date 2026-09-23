package com.swiftapp.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

enum class AppLockType(val id: String) {
    NONE("none"),
    PIN("pin"),
    BIOMETRIC("biometric"),
    BIOMETRIC_OR_PIN("biometric_or_pin");

    companion object {
        fun fromId(id: String): AppLockType {
            return entries.find { it.id == id } ?: NONE
        }
    }
}

object AppLockManager {
    private const val PREFS_NAME = "swift_pdf_security_prefs"
    private const val KEY_LOCK_TYPE = "security_lock_type"
    private const val KEY_PIN_HASH = "security_pin_hash"
    private const val KEY_BIOMETRIC_ENABLED = "security_biometric_enabled"
    private const val SALT = "SwiftPDF_Security_Salt_2026"

    private var prefs: SharedPreferences? = null

    private val _lockTypeFlow = MutableStateFlow(AppLockType.NONE)
    val lockTypeFlow: StateFlow<AppLockType> = _lockTypeFlow.asStateFlow()

    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedType = prefs?.getString(KEY_LOCK_TYPE, AppLockType.NONE.id) ?: AppLockType.NONE.id
            val lockType = AppLockType.fromId(savedType)
            _lockTypeFlow.value = lockType

            // If lock is disabled, session is always unlocked
            if (lockType == AppLockType.NONE) {
                _isSessionUnlocked.value = true
            }
        }
    }

    fun isLockConfigured(context: Context): Boolean {
        init(context)
        return _lockTypeFlow.value != AppLockType.NONE
    }

    fun hasPinSet(context: Context): Boolean {
        init(context)
        return !prefs?.getString(KEY_PIN_HASH, null).isNullOrEmpty()
    }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun setPin(context: Context, pin: String) {
        init(context)
        val hash = hashPin(pin)
        prefs?.edit()?.putString(KEY_PIN_HASH, hash)?.apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs?.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin) == savedHash
    }

    fun setLockType(context: Context, type: AppLockType) {
        init(context)
        _lockTypeFlow.value = type
        prefs?.edit()?.putString(KEY_LOCK_TYPE, type.id)?.apply()
        if (type == AppLockType.NONE) {
            _isSessionUnlocked.value = true
        }
    }

    fun disableLock(context: Context) {
        init(context)
        _lockTypeFlow.value = AppLockType.NONE
        prefs?.edit()
            ?.putString(KEY_LOCK_TYPE, AppLockType.NONE.id)
            ?.remove(KEY_PIN_HASH)
            ?.apply()
        _isSessionUnlocked.value = true
    }

    fun unlockSession() {
        _isSessionUnlocked.value = true
    }

    fun lockSession() {
        if (_lockTypeFlow.value != AppLockType.NONE) {
            _isSessionUnlocked.value = false
        }
    }

    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String = "Unlock Swift PDF",
        subtitle: String = "Touch fingerprint sensor or look at camera",
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                unlockSession()
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometric authentication failed. Try again.")
            }
        })

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Biometric prompt failed")
        }
    }

    private fun hashPin(pin: String): String {
        val bytes = "$SALT$pin$SALT".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
