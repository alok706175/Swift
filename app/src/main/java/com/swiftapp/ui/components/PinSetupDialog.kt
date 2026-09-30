package com.swiftapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.HapticManager

@Composable
fun PinSetupDialog(
    languageViewModel: LanguageViewModel,
    targetLength: Int = 4,
    allowLengthToggle: Boolean = false,
    isBioPin: Boolean = false,
    titlePrefix: String? = null,
    onPinSetSuccess: (Int) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedLength by remember(targetLength) { mutableIntStateOf(targetLength) }

    // Step: 1 = Enter New PIN, 2 = Confirm New PIN
    var step by remember(targetLength) { mutableIntStateOf(1) }
    var firstPin by remember(targetLength) { mutableStateOf("") }
    var confirmPin by remember(targetLength) { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val activeDotCount = selectedLength
    val currentPin = if (step == 1) firstPin else confirmPin

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
            1 -> {
                if (firstPin.length < selectedLength) {
                    firstPin = firstPin + digit
                }
            }
            2 -> {
                if (confirmPin.length < selectedLength) {
                    confirmPin = confirmPin + digit
                }
            }
        }
    }

    fun handleBackspace() {
        errorMessage = null
        HapticManager.light()

        when (step) {
            1 -> {
                if (firstPin.isNotEmpty()) {
                    firstPin = firstPin.dropLast(1)
                }
            }
            2 -> {
                if (confirmPin.isNotEmpty()) {
                    confirmPin = confirmPin.dropLast(1)
                } else {
                    step = 1
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
                                    imageVector = if (isBioPin) Icons.Outlined.Fingerprint else Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            val pfx = if (!titlePrefix.isNullOrEmpty()) "$titlePrefix " else ""
                            val title = if (step == 1) {
                                "${pfx}Set $selectedLength-Digit PIN"
                            } else {
                                "${pfx}Confirm $selectedLength-Digit PIN"
                            }
                            val desc = if (step == 1) {
                                "Enter a $selectedLength-digit ${if (isBioPin) "backup " else ""}security PIN"
                            } else {
                                "Re-enter your $selectedLength-digit ${if (isBioPin) "backup " else ""}PIN"
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

                // Length Selector Toggle (4 Digits vs 6 Digits)
                if (step == 1 && allowLengthToggle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(4, 6).forEach { len ->
                            val isSelected = selectedLength == len
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (selectedLength != len) {
                                            HapticManager.light()
                                            selectedLength = len
                                            firstPin = ""
                                            confirmPin = ""
                                            errorMessage = null
                                        }
                                    },
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$len-Digit PIN",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // PIN Dots Indicator (4 or 6 dots)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    for (i in 0 until activeDotCount) {
                        val isFilled = i < currentPin.length
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

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                // Step 2 indicator / helper
                if (step == 2) {
                    TextButton(
                        onClick = {
                            HapticManager.light()
                            step = 1
                            confirmPin = ""
                            errorMessage = null
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Change PIN",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Numeric Keypad Grid: Backspace on Left of 0, Next/Confirm on Right of 0
                NumericKeypad(
                    onDigitClick = { handleDigit(it) },
                    onBackspaceClick = { handleBackspace() },
                    onNextClick = {
                        if (step == 1 && firstPin.length == selectedLength) {
                            HapticManager.light()
                            step = 2
                            errorMessage = null
                        } else if (step == 2 && confirmPin.length == selectedLength) {
                            if (confirmPin == firstPin) {
                                HapticManager.success()
                                if (isBioPin) {
                                    AppLockManager.setBioPin(context, confirmPin, selectedLength)
                                } else {
                                    AppLockManager.setPin(context, confirmPin, selectedLength)
                                }
                                onPinSetSuccess(selectedLength)
                                onDismiss()
                            } else {
                                HapticManager.error()
                                errorMessage = languageViewModel.getString("pin_mismatch_error")
                                confirmPin = ""
                            }
                        }
                    },
                    isNextEnabled = if (step == 1) firstPin.length == selectedLength else confirmPin.length == selectedLength,
                    nextIcon = if (step == 1) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                    backspaceOnLeft = true
                )
            }
        }
    }
}
