package com.swiftapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swiftapp.utils.HapticManager

@Composable
fun NotificationPermissionDialog(
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val dialogBg = if (isDark) Color(0xFF222326) else Color.White
    val titleColor = if (isDark) Color.White else Color(0xFF1F1F1F)
    val iconTint = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF444746)
    val buttonColor = if (isDark) Color(0xFFFF6F59) else Color(0xFFFF5E38)

    Dialog(
        onDismissRequest = onDeny,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = dialogBg,
            shadowElevation = if (isDark) 6.dp else 12.dp,
            border = if (!isDark) BorderStroke(1.dp, Color(0xFFE5E7EB)) else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Outline Icon
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Title: Allow Swift to send you notifications?
                Text(
                    text = buildAnnotatedString {
                        append("Allow ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                            append("Swift")
                        }
                        append(" to send you\nnotifications?")
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 19.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center,
                    color = titleColor
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Vertical Actions: Allow / Don't allow
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(color = buttonColor.copy(alpha = 0.25f)),
                            onClick = {
                                HapticManager.performHaptic()
                                onAllow()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Allow",
                        color = buttonColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(color = buttonColor.copy(alpha = 0.25f)),
                            onClick = {
                                HapticManager.light()
                                onDeny()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Don’t allow",
                        color = buttonColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
