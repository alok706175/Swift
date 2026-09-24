package com.swiftapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LockPerson
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.swiftapp.R
import com.swiftapp.ui.components.NumericKeypad
import com.swiftapp.ui.components.PatternLockView
import com.swiftapp.ui.components.TactileButton
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.AppLockType
import com.swiftapp.utils.HapticManager
import kotlinx.coroutines.delay

@Composable
fun AppLockScreen(
    languageViewModel: LanguageViewModel,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val lockType by AppLockManager.lockTypeFlow.collectAsState()
    val pinLength = remember { AppLockManager.getPinLength(context) }
    val targetPinDigits = if (lockType == AppLockType.PIN_6) 6 else if (lockType == AppLockType.PIN_4) 4 else pinLength

    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPatternError by remember { mutableStateOf(false) }
    var remainingLockoutSeconds by remember { mutableLongStateOf(AppLockManager.getRemainingLockoutSeconds()) }

    // Lockout countdown timer loop
    LaunchedEffect(remainingLockoutSeconds) {
        while (remainingLockoutSeconds > 0L) {
            delay(1000L)
            remainingLockoutSeconds = AppLockManager.getRemainingLockoutSeconds()
            if (remainingLockoutSeconds == 0L) {
                errorMessage = null
            }
        }
    }

    // Shake animation on error
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null && remainingLockoutSeconds == 0L) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 350
                    0f at 0
                    (-15f) at 50
                    15f at 100
                    (-10f) at 150
                    10f at 200
                    (-5f) at 250
                    5f at 300
                    0f at 350
                }
            )
        }
    }

    fun promptBiometrics() {
        if (remainingLockoutSeconds > 0L) return
        val activity = context as? FragmentActivity
        if (activity != null) {
            AppLockManager.authenticateWithBiometrics(
                activity = activity,
                title = languageViewModel.getString("app_name"),
                subtitle = languageViewModel.getString("lock_screen_subtitle"),
                negativeButtonText = languageViewModel.getString("btn_use_pin"),
                onSuccess = {
                    HapticManager.success()
                    onUnlocked()
                },
                onError = { err ->
                    HapticManager.error()
                    remainingLockoutSeconds = AppLockManager.getRemainingLockoutSeconds()
                    if (remainingLockoutSeconds > 0L) {
                        errorMessage = "Too many failed attempts. Locked for ${remainingLockoutSeconds}s."
                    } else {
                        errorMessage = err
                    }
                }
            )
        }
    }

    fun promptDeviceCredential() {
        if (remainingLockoutSeconds > 0L) return
        val activity = context as? FragmentActivity
        if (activity != null) {
            AppLockManager.authenticateWithDeviceCredential(
                activity = activity,
                title = languageViewModel.getString("app_name"),
                subtitle = languageViewModel.getString("lock_type_device_desc"),
                onSuccess = {
                    HapticManager.success()
                    onUnlocked()
                },
                onError = { err ->
                    HapticManager.error()
                    remainingLockoutSeconds = AppLockManager.getRemainingLockoutSeconds()
                    if (remainingLockoutSeconds > 0L) {
                        errorMessage = "Too many failed attempts. Locked for ${remainingLockoutSeconds}s."
                    } else {
                        errorMessage = err
                    }
                }
            )
        }
    }

    // Auto-prompt biometrics or device lock on initial screen display
    LaunchedEffect(lockType) {
        if (remainingLockoutSeconds == 0L) {
            when (lockType) {
                AppLockType.BIOMETRIC_OR_PIN -> promptBiometrics()
                AppLockType.DEVICE_CREDENTIAL -> promptDeviceCredential()
                else -> {}
            }
        }
    }

    fun handleDigit(digit: String) {
        if (remainingLockoutSeconds > 0L) return
        errorMessage = null
        HapticManager.light()
        if (enteredPin.length < targetPinDigits) {
            val updated = enteredPin + digit
            enteredPin = updated
            if (updated.length == targetPinDigits) {
                if (AppLockManager.verifyPin(updated)) {
                    HapticManager.success()
                    AppLockManager.unlockSession()
                    onUnlocked()
                } else {
                    HapticManager.error()
                    remainingLockoutSeconds = AppLockManager.getRemainingLockoutSeconds()
                    if (remainingLockoutSeconds > 0L) {
                        errorMessage = "Too many failed attempts. Locked for ${remainingLockoutSeconds}s."
                    } else {
                        errorMessage = languageViewModel.getString("pin_incorrect_error")
                    }
                    enteredPin = ""
                }
            }
        }
    }

    fun handleBackspace() {
        if (remainingLockoutSeconds > 0L) return
        errorMessage = null
        HapticManager.light()
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
        }
    }

    fun handlePattern(pattern: List<Int>) {
        if (remainingLockoutSeconds > 0L) return
        errorMessage = null
        isPatternError = false

        if (AppLockManager.verifyPattern(pattern)) {
            HapticManager.success()
            AppLockManager.unlockSession()
            onUnlocked()
        } else {
            HapticManager.error()
            isPatternError = true
            remainingLockoutSeconds = AppLockManager.getRemainingLockoutSeconds()
            if (remainingLockoutSeconds > 0L) {
                errorMessage = "Too many failed attempts. Locked for ${remainingLockoutSeconds}s."
            } else {
                errorMessage = "Incorrect pattern. Try again."
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(68.dp),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_swift_logo),
                            contentDescription = "Swift PDF Logo",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Text(
                    text = languageViewModel.getString("app_name"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = when (lockType) {
                        AppLockType.PATTERN -> "Draw your pattern to unlock"
                        AppLockType.DEVICE_CREDENTIAL -> "Authenticate with device lock"
                        AppLockType.PIN_6 -> "Enter 6-digit PIN"
                        AppLockType.PIN_4 -> "Enter 4-digit PIN"
                        else -> languageViewModel.getString("lock_screen_subtitle")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Middle Section (Lockout Banner, Error Message, Indicators)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }
            ) {
                if (remainingLockoutSeconds > 0L) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Try again in ${remainingLockoutSeconds}s",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (lockType == AppLockType.PIN_4 || lockType == AppLockType.PIN_6 || lockType == AppLockType.BIOMETRIC_OR_PIN) {
                    // Numeric PIN Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until targetPinDigits) {
                            val isFilled = i < enteredPin.length
                            val dotColor = if (errorMessage != null) {
                                MaterialTheme.colorScheme.error
                            } else if (isFilled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }

                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(
                                        width = 2.dp,
                                        color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }

                if (errorMessage != null && remainingLockoutSeconds == 0L) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom Section: Interactive Unlock Mechanism
            when (lockType) {
                AppLockType.PATTERN -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        PatternLockView(
                            onPatternCompleted = { handlePattern(it) },
                            isError = isPatternError,
                            modifier = Modifier.size(300.dp)
                        )
                    }
                }

                AppLockType.DEVICE_CREDENTIAL -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                    ) {
                        TactileButton(
                            onClick = { promptDeviceCredential() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = remainingLockoutSeconds == 0L
                        ) {
                            Icon(Icons.Outlined.LockPerson, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = languageViewModel.getString("btn_unlock_device"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                else -> {
                    // PIN_4, PIN_6, BIOMETRIC_OR_PIN
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        NumericKeypad(
                            onDigitClick = { handleDigit(it) },
                            onBackspaceClick = { handleBackspace() },
                            onBiometricClick = if (lockType == AppLockType.BIOMETRIC_OR_PIN) {
                                { promptBiometrics() }
                            } else null
                        )
                    }
                }
            }
        }
    }
}
