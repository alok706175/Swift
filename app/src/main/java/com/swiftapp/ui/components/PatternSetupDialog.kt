package com.swiftapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    val hasExistingPattern = remember { AppLockManager.hasPatternSet(context) }
    // Step: 0 = Verify Old Pattern, 1 = Draw New Pattern, 2 = Confirm New Pattern
    var step by remember { mutableIntStateOf(if (hasExistingPattern) 0 else 1) }
    var firstPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isErrorPattern by remember { mutableStateOf(false) }

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
                } else {
                    HapticManager.light()
                    firstPattern = pattern
                    step = 2
                }
            }
            2 -> {
                // Confirm pattern
                if (pattern == firstPattern) {
                    HapticManager.success()
                    AppLockManager.setPattern(context, pattern)
                    onPatternSetSuccess()
                    onDismiss()
                } else {
                    HapticManager.error()
                    isErrorPattern = true
                    errorMessage = "Patterns do not match. Draw again."
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
                                1 -> "Connect at least 4 dots"
                                else -> "Draw pattern again to confirm"
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
                    onPatternCompleted = { handlePattern(it) }
                )

                // Reset / Retry action
                if (step == 2) {
                    TextButton(onClick = {
                        step = 1
                        firstPattern = emptyList()
                        errorMessage = null
                        isErrorPattern = false
                    }) {
                        Text("Reset Pattern", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
