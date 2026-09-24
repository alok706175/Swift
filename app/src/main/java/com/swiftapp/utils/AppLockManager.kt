package com.swiftapp.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * Supported Lock Types for Swift PDF.
 */
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

/**
 * Auto-Lock Timeout Intervals when the app goes into the background.
 */
enum class AutoLockTimeout(val id: String, val durationMs: Long, val label: String) {
    IMMEDIATE("immediate", 0L, "Immediately"),
    ONE_MINUTE("1_min", 60_000L, "After 1 minute"),
    FIVE_MINUTES("5_min", 300_000L, "After 5 minutes");

    companion object {
        fun fromId(id: String): AutoLockTimeout {
            return entries.find { it.id == id } ?: IMMEDIATE
        }
    }
}

/**
 * Hardware & Enrollment status for Biometrics.
 */
enum class BiometricStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

/**
 * Production-Ready "App Lock & Biometric Security" Engine for Swift PDF.
 *
 * Technical Highlights:
 * - Native AndroidX BiometricPrompt with BIOMETRIC_STRONG & BIOMETRIC_WEAK fallback.
 * - Hardware & Enrollment detection (BiometricManager).
 * - Salted SHA-256 PIN hashing with private preferences storage.
 * - Brute-force rate limiting: 5 fails -> 30s lockout, 10+ fails -> 120s lockout.
 * - Anti-peep shield (FLAG_SECURE) to prevent recent app screenshot sniffing.
 * - Configurable auto-lock background timeout (Immediate, 1m, 5m).
 * - Session lock state management.
 */
object AppLockManager {
    private const val PREFS_NAME = "swift_pdf_security_prefs"
    private const val KEY_LOCK_TYPE = "security_lock_type"
    private const val KEY_PIN_HASH = "security_pin_hash"
    private const val KEY_AUTO_LOCK_TIMEOUT = "security_auto_lock_timeout"
    private const val KEY_PRIVACY_SHIELD = "security_privacy_shield_enabled"
    private const val SALT = "SwiftPDF_Security_Salt_2026"

    private var prefs: SharedPreferences? = null

    private val _lockTypeFlow = MutableStateFlow(AppLockType.NONE)
    val lockTypeFlow: StateFlow<AppLockType> = _lockTypeFlow.asStateFlow()

    private val _isSessionUnlocked = MutableStateFlow(false)
    val isSessionUnlocked: StateFlow<Boolean> = _isSessionUnlocked.asStateFlow()

    private val _autoLockTimeoutFlow = MutableStateFlow(AutoLockTimeout.IMMEDIATE)
    val autoLockTimeoutFlow: StateFlow<AutoLockTimeout> = _autoLockTimeoutFlow.asStateFlow()

    private val _isPrivacyShieldEnabledFlow = MutableStateFlow(true)
    val isPrivacyShieldEnabledFlow: StateFlow<Boolean> = _isPrivacyShieldEnabledFlow.asStateFlow()

    private var backgroundTimestamp: Long = 0L
    private var failedAttempts: Int = 0
    private var lockoutUntilUptimeMs: Long = 0L

    fun init(context: Context) {
        if (prefs == null) {
            val app = context.applicationContext
            prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val savedType = prefs?.getString(KEY_LOCK_TYPE, AppLockType.NONE.id) ?: AppLockType.NONE.id
            val lockType = AppLockType.fromId(savedType)
            _lockTypeFlow.value = lockType

            val savedTimeout = prefs?.getString(KEY_AUTO_LOCK_TIMEOUT, AutoLockTimeout.IMMEDIATE.id) ?: AutoLockTimeout.IMMEDIATE.id
            _autoLockTimeoutFlow.value = AutoLockTimeout.fromId(savedTimeout)

            val privacyShield = prefs?.getBoolean(KEY_PRIVACY_SHIELD, true) ?: true
            _isPrivacyShieldEnabledFlow.value = privacyShield

            // If lock is disabled, session is always unlocked
            if (lockType == AppLockType.NONE) {
                _isSessionUnlocked.value = true
            } else {
                _isSessionUnlocked.value = false
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

    fun getBiometricStatus(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            else -> BiometricStatus.UNAVAILABLE
        }
    }

    fun isBiometricHardwareAvailable(context: Context): Boolean {
        return getBiometricStatus(context) == BiometricStatus.AVAILABLE
    }

    fun setPin(context: Context, pin: String) {
        init(context)
        val hash = hashPin(pin)
        prefs?.edit()?.putString(KEY_PIN_HASH, hash)?.apply()
    }

    fun verifyPin(pin: String): Boolean {
        if (isLockedOut()) return false

        val savedHash = prefs?.getString(KEY_PIN_HASH, null) ?: return false
        val matched = hashPin(pin) == savedHash

        if (matched) {
            recordSuccessfulAttempt()
        } else {
            recordFailedAttempt()
        }
        return matched
    }

    fun setLockType(context: Context, type: AppLockType) {
        init(context)
        _lockTypeFlow.value = type
        prefs?.edit()?.putString(KEY_LOCK_TYPE, type.id)?.apply()
        if (type == AppLockType.NONE) {
            _isSessionUnlocked.value = true
        }
    }

    fun setAutoLockTimeout(context: Context, timeout: AutoLockTimeout) {
        init(context)
        _autoLockTimeoutFlow.value = timeout
        prefs?.edit()?.putString(KEY_AUTO_LOCK_TIMEOUT, timeout.id)?.apply()
    }

    fun setPrivacyShieldEnabled(activity: Activity?, enabled: Boolean) {
        if (activity != null) {
            init(activity)
            _isPrivacyShieldEnabledFlow.value = enabled
            prefs?.edit()?.putBoolean(KEY_PRIVACY_SHIELD, enabled)?.apply()
            applyPrivacyShield(activity)
        }
    }

    fun applyPrivacyShield(activity: Activity) {
        val enabled = _isPrivacyShieldEnabledFlow.value
        try {
            if (enabled) {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (_: Exception) {}
    }

    fun disableLock(context: Context) {
        init(context)
        _lockTypeFlow.value = AppLockType.NONE
        prefs?.edit()
            ?.putString(KEY_LOCK_TYPE, AppLockType.NONE.id)
            ?.remove(KEY_PIN_HASH)
            ?.apply()
        _isSessionUnlocked.value = true
        recordSuccessfulAttempt()
    }

    fun unlockSession() {
        _isSessionUnlocked.value = true
        recordSuccessfulAttempt()
    }

    fun lockSession() {
        if (_lockTypeFlow.value != AppLockType.NONE) {
            _isSessionUnlocked.value = false
        }
    }

    // --- Lifecycle Background Departure & Resumption ---

    fun onAppBackgrounded() {
        backgroundTimestamp = SystemClock.uptimeMillis()
    }

    fun onAppForegrounded() {
        if (_lockTypeFlow.value != AppLockType.NONE && _isSessionUnlocked.value) {
            val timeout = _autoLockTimeoutFlow.value
            val elapsed = SystemClock.uptimeMillis() - backgroundTimestamp
            if (backgroundTimestamp > 0L && elapsed >= timeout.durationMs) {
                _isSessionUnlocked.value = false
            }
        }
    }

    // --- Brute-Force Rate Limiting ---

    fun isLockedOut(): Boolean {
        return SystemClock.uptimeMillis() < lockoutUntilUptimeMs
    }

    fun getRemainingLockoutSeconds(): Long {
        val remainingMs = lockoutUntilUptimeMs - SystemClock.uptimeMillis()
        return if (remainingMs > 0L) (remainingMs / 1000L) + 1L else 0L
    }

    private fun recordFailedAttempt(): Long {
        failedAttempts++
        val now = SystemClock.uptimeMillis()
        if (failedAttempts >= 10) {
            lockoutUntilUptimeMs = now + 120_000L // 2 minutes lockout
        } else if (failedAttempts >= 5) {
            lockoutUntilUptimeMs = now + 30_000L // 30 seconds lockout
        }
        return getRemainingLockoutSeconds()
    }

    private fun recordSuccessfulAttempt() {
        failedAttempts = 0
        lockoutUntilUptimeMs = 0L
    }

    // --- Biometric Authentication Prompt ---

    fun authenticateWithBiometrics(
        activity: FragmentActivity,
        title: String = "Unlock Swift PDF",
        subtitle: String = "Touch fingerprint sensor or look at camera",
        negativeButtonText: String = "Use PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (isLockedOut()) {
            onError("Security lockout active. Try again later.")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
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
                recordFailedAttempt()
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
