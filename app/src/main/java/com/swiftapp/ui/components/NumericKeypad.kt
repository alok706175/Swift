package com.swiftapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swiftapp.ui.components.bounceClick

/**
 * Premium iOS & flagship-Android inspired Numeric Keypad with letters, haptic touch reaction,
 * and tactile animations.
 */
@Composable
fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: (() -> Unit)? = null,
    onNextClick: (() -> Unit)? = null,
    isNextEnabled: Boolean = false,
    nextIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    backspaceOnLeft: Boolean = false,
    modifier: Modifier = Modifier
) {
    val lastRow = if (onNextClick != null || backspaceOnLeft) {
        listOf("back" to "", "0" to "+", "next" to "")
    } else {
        listOf("bio" to "", "0" to "+", "back" to "")
    }

    val keypadRows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        lastRow
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        for (row in keypadRows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for ((key, sub) in row) {
                    when (key) {
                        "back" -> {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .bounceClick(scaleDownFactor = 0.88f) { onBackspaceClick() }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        "bio" -> {
                            if (onBiometricClick != null) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .bounceClick(scaleDownFactor = 0.88f) { onBiometricClick() }
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.2.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Fingerprint,
                                        contentDescription = "Biometric Unlock",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(72.dp))
                            }
                        }
                        "next" -> {
                            if (onNextClick != null) {
                                val nextBgColor by animateColorAsState(
                                    targetValue = if (isNextEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    label = "NextBtnBg"
                                )
                                val nextIconColor by animateColorAsState(
                                    targetValue = if (isNextEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    label = "NextBtnIcon"
                                )

                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .then(
                                            if (isNextEnabled) {
                                                Modifier.bounceClick(scaleDownFactor = 0.88f) { onNextClick() }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .background(nextBgColor)
                                        .border(
                                            width = 1.dp,
                                            color = if (isNextEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = nextIcon,
                                        contentDescription = "Next",
                                        tint = nextIconColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(72.dp))
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .bounceClick(scaleDownFactor = 0.88f) { onDigitClick(key) }
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontSize = 26.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (sub.isNotEmpty()) {
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.5.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
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
}
