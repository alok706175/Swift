package com.swiftapp.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.HapticManager

@Composable
fun PinSetupDialog(
    languageViewModel: LanguageViewModel,
    targetLength: Int = 4,
    onPinSetSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasExistingPin = remember { AppLockManager.hasPinSet(context) }
    val existingPinLength = remember { AppLockManager.getPinLength(context) }
    var selectedLength by remember { mutableIntStateOf(targetLength) }

    // Step: 0 = Verify Old PIN, 1 = Enter New PIN, 2 = Confirm New PIN
    var step by remember { mutableIntStateOf(if (hasExistingPin) 0 else 1) }
    var oldPin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val activeDotCount = if (step == 0) existingPinLength else selectedLength
    val currentPin = when (step) {
        0 -> oldPin
        1 -> firstPin
        else -> confirmPin
    }

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

    fun handleDigit(digit: String) {
        errorMessage = null
        HapticManager.light()

        when (step) {
            0 -> {
                if (oldPin.length < existingPinLength) {
                    val updated = oldPin + digit
                    oldPin = updated
                    if (updated.length == existingPinLength) {
                        if (AppLockManager.verifyPin(updated)) {
                            HapticManager.success()
                            step = 1
                        } else {
                            HapticManager.error()
                            errorMessage = languageViewModel.getString("pin_incorrect_error")
                            oldPin = ""
                        }
                    }
                }
            }
            1 -> {
                if (firstPin.length < selectedLength) {
                    val updated = firstPin + digit
                    firstPin = updated
                    if (updated.length == selectedLength) {
                        step = 2
                    }
                }
            }
            2 -> {
                if (confirmPin.length < selectedLength) {
                    val updated = confirmPin + digit
                    confirmPin = updated
                    if (updated.length == selectedLength) {
                        if (updated == firstPin) {
                            HapticManager.success()
                            AppLockManager.setPin(context, updated, selectedLength)
                            onPinSetSuccess()
                            onDismiss()
                        } else {
                            HapticManager.error()
                            errorMessage = languageViewModel.getString("pin_mismatch_error")
                            confirmPin = ""
                        }
                    }
                }
            }
        }
    }

    fun handleBackspace() {
        errorMessage = null
        HapticManager.light()

        when (step) {
            0 -> {
                if (oldPin.isNotEmpty()) {
                    oldPin = oldPin.dropLast(1)
                }
            }
            1 -> {
                if (firstPin.isNotEmpty()) {
                    firstPin = firstPin.dropLast(1)
                } else if (hasExistingPin) {
                    step = 0
                    oldPin = ""
                }
            }
            2 -> {
                if (confirmPin.isNotEmpty()) {
                    confirmPin = confirmPin.dropLast(1)
                } else {
                    step = 1
                    firstPin = ""
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .graphicsLayer { translationX = shakeOffset.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            val title = when (step) {
                                0 -> "Enter Current PIN"
                                1 -> if (selectedLength == 6) "Set 6-Digit PIN" else "Set 4-Digit PIN"
                                else -> "Confirm $selectedLength-Digit PIN"
                            }
                            val desc = when (step) {
                                0 -> "Verify your identity before setting a new PIN"
                                1 -> "Enter a $selectedLength-digit security PIN"
                                else -> "Re-enter your $selectedLength-digit PIN"
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TactileIconButton(
                        onClick = onDismiss,
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        size = 32.dp
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                // Length Selector (Visible in Step 1 if user wants to switch between 4 and 6 digits)
                if (step == 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    onClick = {
                                        selectedLength = 4
                                        firstPin = ""
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedLength == 4) MaterialTheme.colorScheme.primary else Color.Transparent
                                ) {
                                    Text(
                                        text = "4-Digit PIN",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selectedLength == 4) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    onClick = {
                                        selectedLength = 6
                                        firstPin = ""
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedLength == 6) MaterialTheme.colorScheme.primary else Color.Transparent
                                ) {
                                    Text(
                                        text = "6-Digit PIN",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selectedLength == 6) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // PIN Dots Indicator (4 or 6 dots)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    for (i in 0 until activeDotCount) {
                        val isFilled = i < currentPin.length
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
                                    width = 1.5.dp,
                                    color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                // Numeric Keypad Grid
                NumericKeypad(
                    onDigitClick = { handleDigit(it) },
                    onBackspaceClick = { handleBackspace() }
                )
            }
        }
    }
}

@Composable
fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: (() -> Unit)? = null
) {
    val digits = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("bio", "0", "back")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        for (row in digits) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (key in row) {
                    when (key) {
                        "back" -> {
                            Surface(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .bounceClick { onBackspaceClick() },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = CircleShape
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "Backspace",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        "bio" -> {
                            if (onBiometricClick != null) {
                                Surface(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .bounceClick { onBiometricClick() },
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    shape = CircleShape
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Fingerprint,
                                            contentDescription = "Biometric",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.size(62.dp))
                            }
                        }
                        else -> {
                            Surface(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .bounceClick { onDigitClick(key) },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = CircleShape
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
