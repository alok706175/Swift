package com.swiftapp.ui.screens

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.swiftapp.ui.components.TactileButton
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.AppLockType

@Composable
fun AppLockScreen(
    languageViewModel: LanguageViewModel,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val lockType by AppLockManager.lockTypeFlow.collectAsState()
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Shake animation on error
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
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
        val activity = context as? FragmentActivity
        if (activity != null) {
            AppLockManager.authenticateWithBiometrics(
                activity = activity,
                title = languageViewModel.getString("app_name"),
                subtitle = languageViewModel.getString("lock_screen_subtitle"),
                negativeButtonText = languageViewModel.getString("btn_use_pin"),
                onSuccess = {
                    onUnlocked()
                },
                onError = { err ->
                    errorMessage = err
                }
            )
        }
    }

    // Auto-prompt biometrics once on screen display if configured
    LaunchedEffect(lockType) {
        if (lockType == AppLockType.BIOMETRIC || lockType == AppLockType.BIOMETRIC_OR_PIN) {
            promptBiometrics()
        }
    }

    fun handleDigit(digit: String) {
        errorMessage = null
        if (enteredPin.length < 4) {
            val updated = enteredPin + digit
            enteredPin = updated
            if (updated.length == 4) {
                if (AppLockManager.verifyPin(updated)) {
                    AppLockManager.unlockSession()
                    onUnlocked()
                } else {
                    errorMessage = languageViewModel.getString("pin_incorrect_error")
                    enteredPin = ""
                }
            }
        }
    }

    fun handleBackspace() {
        errorMessage = null
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
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
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(72.dp),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_swift_logo),
                            contentDescription = "Swift PDF Logo",
                            modifier = Modifier.size(44.dp)
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
                    text = languageViewModel.getString("lock_screen_subtitle"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // PIN Dots Indicator & Error message
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.graphicsLayer { translationX = shakeOffset.value }
            ) {
                if (lockType != AppLockType.BIOMETRIC || AppLockManager.hasPinSet(context)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 4) {
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
                                    .size(18.dp)
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

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Keypad or Biometric Trigger
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                if (lockType == AppLockType.BIOMETRIC && !AppLockManager.hasPinSet(context)) {
                    TactileButton(
                        onClick = { promptBiometrics() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = languageViewModel.getString("btn_unlock_biometrics"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    NumericKeypad(
                        onDigitClick = { handleDigit(it) },
                        onBackspaceClick = { handleBackspace() },
                        onBiometricClick = if (lockType == AppLockType.BIOMETRIC_OR_PIN || lockType == AppLockType.BIOMETRIC) {
                            { promptBiometrics() }
                        } else null
                    )
                }
            }
        }
    }
}
