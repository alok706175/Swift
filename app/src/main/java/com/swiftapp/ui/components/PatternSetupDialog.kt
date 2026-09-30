package com.swiftapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.swiftapp.utils.AppLockType
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.HapticManager

@Composable
fun PatternSetupDialog(
    languageViewModel: LanguageViewModel,
    onPatternSetSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hasExistingPattern = remember { AppLockManager.hasPatternSet(context) && AppLockManager.lockTypeFlow.value == AppLockType.PATTERN }
    // Step: 0 = Verify Old Pattern, 1 = Draw New Pattern, 2 = Confirm New Pattern
    var step by remember { mutableIntStateOf(if (hasExistingPattern) 0 else 1) }
    var firstPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var confirmPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isErrorPattern by remember { mutableStateOf(false) }
    var patternResetTrigger by remember { mutableIntStateOf(0) }

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

    fun handlePattern(pattern: List<Int>) {
        errorMessage = null
        isErrorPattern = false

        when (step) {
            0 -> {
                // Verify existing pattern
                if (AppLockManager.verifyPattern(pattern)) {
                    HapticManager.success()
                    step = 1
                    patternResetTrigger++
                } else {
                    HapticManager.error()
                    isErrorPattern = true
                    errorMessage = "Incorrect pattern. Try again."
                }
            }
            1 -> {
                // Draw new pattern
                if (pattern.size < 4) {
                    HapticManager.error()
                    isErrorPattern = true
                    errorMessage = "Connect at least 4 dots to create pattern."
                    firstPattern = emptyList()
                } else {
                    HapticManager.light()
                    firstPattern = pattern
                    errorMessage = null
                    isErrorPattern = false
                }
            }
            2 -> {
                // Confirm pattern: user draws, must click Done to set
                if (pattern.size < 4) {
                    HapticManager.error()
                    isErrorPattern = true
                    errorMessage = "Connect at least 4 dots."
                    confirmPattern = emptyList()
                } else {
                    HapticManager.light()
                    confirmPattern = pattern
                    errorMessage = null
                    isErrorPattern = false
                }
            }
        }
    }

    fun handleDonePattern() {
        if (confirmPattern == firstPattern) {
            HapticManager.success()
            AppLockManager.setPattern(context, confirmPattern)
            onPatternSetSuccess()
            onDismiss()
        } else {
            HapticManager.error()
            isErrorPattern = true
            errorMessage = "Patterns do not match. Draw again."
            confirmPattern = emptyList()
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                    imageVector = Icons.Outlined.Gesture,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            val title = when (step) {
                                0 -> "Draw Current Pattern"
                                1 -> "Draw New Pattern"
                                else -> "Confirm Pattern"
                            }
                            val desc = when (step) {
                                0 -> "Verify your identity first"
                                1 -> if (firstPattern.size >= 4) "Pattern recorded. Tap Next to continue" else "Connect at least 4 dots"
                                else -> if (confirmPattern.isNotEmpty()) "Pattern recorded. Tap Done to set lock" else "Draw pattern again to confirm"
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

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }

                // Interactive Pattern View
                PatternLockView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    isError = isErrorPattern,
                    resetTrigger = patternResetTrigger,
                    onErrorCleared = { isErrorPattern = false },
                    onPatternCompleted = { handlePattern(it) }
                )

                // Step 1: Next button / Step 2: Reset action
                if (step == 1) {
                    if (firstPattern.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    HapticManager.light()
                                    firstPattern = emptyList()
                                    errorMessage = null
                                    isErrorPattern = false
                                    patternResetTrigger++
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Clear", fontWeight = FontWeight.SemiBold)
                            }
                            Button(
                                onClick = {
                                    if (firstPattern.size >= 4) {
                                        HapticManager.light()
                                        step = 2
                                        errorMessage = null
                                        isErrorPattern = false
                                        patternResetTrigger++
                                    }
                                },
                                enabled = firstPattern.size >= 4,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Next", fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                } else if (step == 2) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                HapticManager.light()
                                step = 1
                                firstPattern = emptyList()
                                confirmPattern = emptyList()
                                errorMessage = null
                                isErrorPattern = false
                                patternResetTrigger++
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Change Pattern",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (confirmPattern.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        HapticManager.light()
                                        confirmPattern = emptyList()
                                        errorMessage = null
                                        isErrorPattern = false
                                        patternResetTrigger++
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Clear", fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = { handleDonePattern() },
                                    enabled = confirmPattern.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Done", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
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
