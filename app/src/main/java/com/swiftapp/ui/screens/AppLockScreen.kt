package com.swiftapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockPerson
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    (-14f) at 50
                    14f at 100
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

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                // Security Badge Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "SWIFT SECURE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // App Icon / Shield Badge with subtle glow
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_swift_logo),
                        contentDescription = "Swift PDF Logo",
                        modifier = Modifier.size(46.dp)
                    )
                }

                Text(
                    text = when (lockType) {
                        AppLockType.PATTERN -> "Draw Pattern"
                        AppLockType.DEVICE_CREDENTIAL -> "Device Unlock"
                        AppLockType.PIN_6 -> "Enter 6-Digit Passcode"
                        AppLockType.PIN_4 -> "Enter 4-Digit Passcode"
                        else -> "Enter Passcode"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = when (lockType) {
                        AppLockType.PATTERN -> "Draw your saved pattern to continue"
                        AppLockType.DEVICE_CREDENTIAL -> "Authenticate with your device screen lock"
                        AppLockType.BIOMETRIC_OR_PIN -> "Touch fingerprint sensor or enter PIN"
                        else -> "Enter your security PIN to unlock Swift PDF"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Middle Section (Lockout Banner, Error Message, Indicators)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .graphicsLayer { translationX = shakeOffset.value }
                    .padding(vertical = 8.dp)
            ) {
                if (remainingLockoutSeconds > 0L) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        ),
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
                                text = "Locked. Try again in ${remainingLockoutSeconds}s",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (lockType == AppLockType.PIN_4 || lockType == AppLockType.PIN_6 || lockType == AppLockType.BIOMETRIC_OR_PIN) {
                    // Animated Scaling PIN Dots with Glow
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until targetPinDigits) {
                            val isFilled = i < enteredPin.length
                            val scale by animateFloatAsState(
                                targetValue = if (isFilled) 1.25f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "PinDotScale_$i"
                            )

                            val dotColor by animateColorAsState(
                                targetValue = when {
                                    errorMessage != null -> MaterialTheme.colorScheme.error
                                    isFilled -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                animationSpec = tween(150),
                                label = "PinDotColor_$i"
                            )

                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(
                                        width = if (isFilled) 0.dp else 1.5.dp,
                                        color = if (isFilled) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
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
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Bottom Section: Interactive Unlock Mechanism
            when (lockType) {
                AppLockType.PATTERN -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        PatternLockView(
                            onPatternCompleted = { handlePattern(it) },
                            isError = isPatternError,
                            modifier = Modifier.size(310.dp)
                        )
                    }
                }

                AppLockType.DEVICE_CREDENTIAL -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp)
                    ) {
                        TactileButton(
                            onClick = { promptDeviceCredential() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
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
