package com.swiftapp.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.AppLockType
import com.swiftapp.utils.AutoLockTimeout
import com.swiftapp.utils.BiometricStatus
import com.swiftapp.utils.HapticManager

@Composable
fun AppLockSelectionDialog(
    currentType: AppLockType,
    languageViewModel: LanguageViewModel,
    onRequestSetPin: () -> Unit = {},
    onLockTypeChanged: (AppLockType) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var selectedType by remember { mutableStateOf(currentType) }
    val bioStatus = remember { AppLockManager.getBiometricStatus(context) }
    val isBiometricAvailable = bioStatus == BiometricStatus.AVAILABLE
    val isDeviceSecure = remember { AppLockManager.isDeviceSecure(context) }

    var hasPin by remember { mutableStateOf(AppLockManager.hasPinSet(context)) }
    var pinLength by remember { mutableIntStateOf(AppLockManager.getPinLength(context)) }
    var hasPattern by remember { mutableStateOf(AppLockManager.hasPatternSet(context)) }

    val autoLockTimeout by AppLockManager.autoLockTimeoutFlow.collectAsState()
    var selectedTimeout by remember { mutableStateOf(autoLockTimeout) }

    val isLockEnabled = selectedType != AppLockType.NONE

    // Internal sub-dialog states for seamless setup
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var pinSetupTargetLength by remember { mutableIntStateOf(4) }
    var showPatternSetupDialog by remember { mutableStateOf(false) }
    var showBiometricEnrollDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
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
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = languageViewModel.getString("settings_app_lock"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = languageViewModel.getString("settings_app_lock_sub"),
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

                // Master Toggle: Enable App Lock
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Lock Protection",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Require authentication to open Swift PDF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isLockEnabled,
                            onCheckedChange = { enabled ->
                                HapticManager.light()
                                if (!enabled) {
                                    selectedType = AppLockType.NONE
                                } else {
                                    if (hasPin) {
                                        selectedType = if (pinLength == 6) AppLockType.PIN_6 else AppLockType.PIN_4
                                    } else if (hasPattern) {
                                        selectedType = AppLockType.PATTERN
                                    } else if (isBiometricAvailable) {
                                        selectedType = AppLockType.BIOMETRIC_OR_PIN
                                        pinSetupTargetLength = 4
                                        showPinSetupDialog = true
                                    } else {
                                        selectedType = AppLockType.PIN_4
                                        pinSetupTargetLength = 4
                                        showPinSetupDialog = true
                                    }
                                }
                            }
                        )
                    }
                }

                // Authentication Methods (5 Unlock Options)
                if (isLockEnabled) {
                    Text(
                        text = "Unlock Method",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 1. 4-Digit PIN Passcode
                        LockOptionItem(
                            title = languageViewModel.getString("lock_type_pin_4"),
                            icon = Icons.Outlined.Pin,
                            isSelected = selectedType == AppLockType.PIN_4,
                            trailingAction = if (hasPin && pinLength == 4) "Change" else null,
                            onTrailingActionClick = {
                                pinSetupTargetLength = 4
                                showPinSetupDialog = true
                            },
                            onClick = {
                                HapticManager.light()
                                if (!hasPin || pinLength != 4) {
                                    pinSetupTargetLength = 4
                                    showPinSetupDialog = true
                                } else {
                                    selectedType = AppLockType.PIN_4
                                }
                            }
                        )

                        // 2. 6-Digit PIN Passcode
                        LockOptionItem(
                            title = languageViewModel.getString("lock_type_pin_6"),
                            icon = Icons.Outlined.Password,
                            isSelected = selectedType == AppLockType.PIN_6,
                            trailingAction = if (hasPin && pinLength == 6) "Change" else null,
                            onTrailingActionClick = {
                                pinSetupTargetLength = 6
                                showPinSetupDialog = true
                            },
                            onClick = {
                                HapticManager.light()
                                if (!hasPin || pinLength != 6) {
                                    pinSetupTargetLength = 6
                                    showPinSetupDialog = true
                                } else {
                                    selectedType = AppLockType.PIN_6
                                }
                            }
                        )

                        // 3. Drawing a Pattern
                        LockOptionItem(
                            title = languageViewModel.getString("lock_type_pattern"),
                            icon = Icons.Outlined.Gesture,
                            isSelected = selectedType == AppLockType.PATTERN,
                            trailingAction = if (hasPattern) "Change" else null,
                            onTrailingActionClick = {
                                showPatternSetupDialog = true
                            },
                            onClick = {
                                HapticManager.light()
                                if (!hasPattern) {
                                    showPatternSetupDialog = true
                                } else {
                                    selectedType = AppLockType.PATTERN
                                }
                            }
                        )

                        // 4. Biometric (Fingerprint + Face Lock) + PIN Passcode
                        LockOptionItem(
                            title = languageViewModel.getString("lock_type_bio_pin"),
                            icon = Icons.Outlined.Fingerprint,
                            isSelected = selectedType == AppLockType.BIOMETRIC_OR_PIN,
                            trailingAction = if (hasPin) "Change PIN" else null,
                            onTrailingActionClick = {
                                pinSetupTargetLength = pinLength
                                showPinSetupDialog = true
                            },
                            onClick = {
                                HapticManager.light()
                                val currentBioStatus = AppLockManager.getBiometricStatus(context)
                                when (currentBioStatus) {
                                    BiometricStatus.NO_HARDWARE, BiometricStatus.UNAVAILABLE -> {
                                        Toast.makeText(
                                            context,
                                            "Biometric hardware (Fingerprint/Face) is not available on this phone.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    BiometricStatus.NOT_ENROLLED -> {
                                        showBiometricEnrollDialog = true
                                    }
                                    BiometricStatus.AVAILABLE -> {
                                        if (!hasPin) {
                                            selectedType = AppLockType.BIOMETRIC_OR_PIN
                                            pinSetupTargetLength = 4
                                            showPinSetupDialog = true
                                        } else {
                                            val act = context as? FragmentActivity
                                            if (act != null) {
                                                AppLockManager.authenticateWithBiometrics(
                                                    activity = act,
                                                    title = "Verify Biometrics",
                                                    subtitle = "Touch fingerprint sensor or look at camera to register",
                                                    negativeButtonText = "Cancel",
                                                    onSuccess = {
                                                        selectedType = AppLockType.BIOMETRIC_OR_PIN
                                                        Toast.makeText(
                                                            context,
                                                            "Biometric registered successfully",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    },
                                                    onError = { err ->
                                                        Toast.makeText(
                                                            context,
                                                            err,
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                )
                                            } else {
                                                selectedType = AppLockType.BIOMETRIC_OR_PIN
                                            }
                                        }
                                    }
                                }
                            }
                        )

                        // 5. Device Lock (System Master PIN/Pattern + Biometrics)
                        LockOptionItem(
                            title = languageViewModel.getString("lock_type_device"),
                            icon = Icons.Outlined.LockPerson,
                            isSelected = selectedType == AppLockType.DEVICE_CREDENTIAL,
                            trailingAction = null,
                            onTrailingActionClick = null,
                            onClick = {
                                HapticManager.light()
                                selectedType = AppLockType.DEVICE_CREDENTIAL
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Auto-Lock Timeout Section (5 intervals)
                    Text(
                        text = "Auto-Lock Interval",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AutoLockTimeout.entries.forEach { timeout ->
                            val timeoutTitle = when (timeout) {
                                AutoLockTimeout.IMMEDIATE -> languageViewModel.getString("timeout_immediate")
                                AutoLockTimeout.ONE_MINUTE -> languageViewModel.getString("timeout_1_min")
                                AutoLockTimeout.FIVE_MINUTES -> languageViewModel.getString("timeout_5_min")
                                AutoLockTimeout.TEN_MINUTES -> languageViewModel.getString("timeout_10_min")
                                AutoLockTimeout.SCREEN_CLOSED -> languageViewModel.getString("timeout_screen_closed")
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        HapticManager.light()
                                        selectedTimeout = timeout
                                    },
                                color = if (selectedTimeout == timeout) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (timeout == AutoLockTimeout.SCREEN_CLOSED) Icons.Outlined.PhoneAndroid else Icons.Outlined.Timer,
                                            contentDescription = null,
                                            tint = if (selectedTimeout == timeout) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = timeoutTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selectedTimeout == timeout) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    RadioButton(
                                        selected = selectedTimeout == timeout,
                                        onClick = {
                                            HapticManager.light()
                                            selectedTimeout = timeout
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons: Cancel and Save (Only saves when Save is clicked)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TactileOutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(languageViewModel.getString("btn_cancel"))
                    }

                    TactileButton(
                        onClick = {
                            if (selectedType == AppLockType.PIN_4 && (!hasPin || pinLength != 4)) {
                                pinSetupTargetLength = 4
                                showPinSetupDialog = true
                                return@TactileButton
                            }
                            if (selectedType == AppLockType.PIN_6 && (!hasPin || pinLength != 6)) {
                                pinSetupTargetLength = 6
                                showPinSetupDialog = true
                                return@TactileButton
                            }
                            if (selectedType == AppLockType.PATTERN && !hasPattern) {
                                showPatternSetupDialog = true
                                return@TactileButton
                            }
                            if (selectedType == AppLockType.BIOMETRIC_OR_PIN && !hasPin) {
                                pinSetupTargetLength = 4
                                showPinSetupDialog = true
                                return@TactileButton
                            }

                            AppLockManager.setLockType(context, selectedType)
                            AppLockManager.setAutoLockTimeout(context, selectedTimeout)
                            onLockTypeChanged(selectedType)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(languageViewModel.getString("btn_save"))
                    }
                }
            }
        }
    }

    // Embedded Sub-Dialogs for Seamless Setup Flow
    if (showPinSetupDialog) {
        PinSetupDialog(
            languageViewModel = languageViewModel,
            targetLength = pinSetupTargetLength,
            onPinSetSuccess = {
                hasPin = true
                pinLength = pinSetupTargetLength
                if (selectedType == AppLockType.BIOMETRIC_OR_PIN) {
                    val act = context as? FragmentActivity
                    if (act != null && AppLockManager.getBiometricStatus(context) == BiometricStatus.AVAILABLE) {
                        AppLockManager.authenticateWithBiometrics(
                            activity = act,
                            title = "Verify Biometrics",
                            subtitle = "Touch fingerprint sensor or look at camera to register",
                            negativeButtonText = "Skip",
                            onSuccess = {
                                selectedType = AppLockType.BIOMETRIC_OR_PIN
                                Toast.makeText(context, "Biometric registered successfully!", Toast.LENGTH_SHORT).show()
                            },
                            onError = {
                                selectedType = AppLockType.BIOMETRIC_OR_PIN
                            }
                        )
                    }
                } else if (pinSetupTargetLength == 6) {
                    selectedType = AppLockType.PIN_6
                } else {
                    selectedType = AppLockType.PIN_4
                }
                showPinSetupDialog = false
            },
            onDismiss = { showPinSetupDialog = false }
        )
    }

    if (showPatternSetupDialog) {
        PatternSetupDialog(
            languageViewModel = languageViewModel,
            onPatternSetSuccess = {
                hasPattern = true
                selectedType = AppLockType.PATTERN
                showPatternSetupDialog = false
            },
            onDismiss = { showPatternSetupDialog = false }
        )
    }

    if (showBiometricEnrollDialog) {
        AlertDialog(
            onDismissRequest = { showBiometricEnrollDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Register Fingerprint or Face",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "Your phone supports biometric security, but no fingerprint or face has been registered in your phone Settings yet.\n\nPlease register in Settings to enable Biometric unlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBiometricEnrollDialog = false
                        AppLockManager.openBiometricEnrollment(context)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBiometricEnrollDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun LockOptionItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    subtitle: String? = null,
    badge: String? = null,
    trailingAction: String? = null,
    onTrailingActionClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() },
        color = containerColor,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (badge != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (!subtitle.isNullOrEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingAction != null && onTrailingActionClick != null) {
                    TextButton(
                        onClick = onTrailingActionClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = trailingAction,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
