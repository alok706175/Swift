package com.swiftapp.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.AppLockType
import com.swiftapp.utils.AutoLockTimeout
import com.swiftapp.utils.BiometricStatus
import com.swiftapp.utils.HapticManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSelectionDialog(
    currentType: AppLockType,
    languageViewModel: LanguageViewModel,
    onRequestSetPin: () -> Unit = {},
    onLockTypeChanged: (AppLockType) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf(if (currentType == AppLockType.DEVICE_CREDENTIAL) AppLockType.NONE else currentType) }
    val bioStatus = remember { AppLockManager.getBiometricStatus(context) }
    val isBiometricAvailable = bioStatus == BiometricStatus.AVAILABLE
    val isDeviceSecure = remember { AppLockManager.isDeviceSecure(context) }

    var hasPin by remember { mutableStateOf(AppLockManager.hasPinSet(context)) }
    var pinLength by remember { mutableIntStateOf(AppLockManager.getPinLength(context)) }
    var hasBioPin by remember { mutableStateOf(AppLockManager.hasBioPinSet(context)) }
    var bioPinLength by remember { mutableIntStateOf(AppLockManager.getBioPinLength(context)) }
    var hasPattern by remember { mutableStateOf(AppLockManager.hasPatternSet(context)) }

    val autoLockTimeout by AppLockManager.autoLockTimeoutFlow.collectAsState()
    var selectedTimeout by remember { mutableStateOf(autoLockTimeout) }

    val isLockEnabled = selectedType != AppLockType.NONE

    // Internal sub-dialog states for seamless setup
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var pinSetupTargetLength by remember { mutableIntStateOf(4) }
    var isBioPinSetup by remember { mutableStateOf(false) }
    var showPatternSetupDialog by remember { mutableStateOf(false) }
    var showBiometricEnrollDialog by remember { mutableStateOf(false) }
    var showAddMethodDialog by remember { mutableStateOf(false) }

    if (showAddMethodDialog) {
        BackHandler { showAddMethodDialog = false }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Add Unlock Method",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            HapticManager.light()
                            showAddMethodDialog = false
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header informative banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Choose Authentication Method",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Select how you would like to secure Swift PDF. You can switch methods anytime in settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "AVAILABLE UNLOCK METHODS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Method 1: 4-Digit PIN
                    MethodSelectionItem(
                        icon = Icons.Outlined.Pin,
                        title = languageViewModel.getString("lock_type_pin_4"),
                        subtitle = "Fast and easy 4-digit code",
                        onClick = {
                            pinSetupTargetLength = 4
                            showPinSetupDialog = true
                        }
                    )

                    // Method 2: 6-Digit PIN
                    MethodSelectionItem(
                        icon = Icons.Outlined.Password,
                        title = languageViewModel.getString("lock_type_pin_6"),
                        subtitle = "Enhanced 6-digit passcode security",
                        onClick = {
                            pinSetupTargetLength = 6
                            showPinSetupDialog = true
                        }
                    )

                    // Method 3: Pattern Lock
                    MethodSelectionItem(
                        icon = Icons.Outlined.Gesture,
                        title = languageViewModel.getString("lock_type_pattern"),
                        subtitle = "Draw a continuous pattern to unlock",
                        onClick = {
                            showPatternSetupDialog = true
                        }
                    )

                    // Method 4: Biometric + PIN
                    MethodSelectionItem(
                        icon = Icons.Outlined.Fingerprint,
                        title = languageViewModel.getString("lock_type_bio_pin"),
                        subtitle = "Fingerprint / Face with PIN backup",
                        onClick = {
                            val currentBioStatus = AppLockManager.getBiometricStatus(context)
                            when (currentBioStatus) {
                                BiometricStatus.NO_HARDWARE, BiometricStatus.UNAVAILABLE -> {
                                    Toast.makeText(
                                        context,
                                        "Biometric hardware is not available on this device",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                BiometricStatus.NOT_ENROLLED -> {
                                    showBiometricEnrollDialog = true
                                }
                                BiometricStatus.AVAILABLE -> {
                                    isBioPinSetup = true
                                    pinSetupTargetLength = 4
                                    showPinSetupDialog = true
                                }
                            }
                        }
                    )
                }
            }
        }
    } else {
        BackHandler { onDismiss() }

        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = languageViewModel.getString("settings_app_lock"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        HapticManager.light()
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

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
                                    // Turning OFF: Delete all saved lock types, PIN, and pattern completely
                                    AppLockManager.clearAllLockData(context)
                                    selectedType = AppLockType.NONE
                                    hasPin = false
                                    pinLength = 4
                                    hasPattern = false
                                    onLockTypeChanged(AppLockType.NONE)
                                    Toast.makeText(
                                        context,
                                        "App lock disabled & saved credentials removed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    // Turning ON: Prompt user to add a new unlock method
                                    showAddMethodDialog = true
                                }
                            }
                        )
                    }
                }

                if (!isLockEnabled) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Outlined.Shield,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Security & Privacy",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = "Enable App Lock to require authentication each time you launch Swift PDF. You can secure your sensitive documents with a PIN passcode, pattern, or biometric fingerprint unlock.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
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
                            trailingAction = if (selectedType == AppLockType.PIN_4 && hasPin && pinLength == 4) "Change" else null,
                            onTrailingActionClick = {
                                pinSetupTargetLength = 4
                                showPinSetupDialog = true
                            },
                            onClick = {
                                HapticManager.light()
                                if (selectedType != AppLockType.PIN_4 || !hasPin || pinLength != 4) {
                                    pinSetupTargetLength = 4
                                    showPinSetupDialog = true
                                }
                            }
                        )

                        // 2. 6-Digit PIN Passcode
                        LockOptionItem(
                            title = languageViewModel.getString("lock_type_pin_6"),
                            icon = Icons.Outlined.Password,
                            isSelected = selectedType == AppLockType.PIN_6,
                            trailingAction = if (selectedType == AppLockType.PIN_6 && hasPin && pinLength == 6) "Change" else null,
                            onTrailingActionClick = {
                                pinSetupTargetLength = 6
                                showPinSetupDialog = true
                            },
                            onClick = {
                                HapticManager.light()
                                if (selectedType != AppLockType.PIN_6 || !hasPin || pinLength != 6) {
                                    pinSetupTargetLength = 6
                                    showPinSetupDialog = true
                                }
                            }
                        )

                        // 3. Drawing a Pattern
                        LockOptionItem(
                            title = languageViewModel.getString("lock_type_pattern"),
                            icon = Icons.Outlined.Gesture,
                            isSelected = selectedType == AppLockType.PATTERN,
                            trailingAction = if (selectedType == AppLockType.PATTERN && hasPattern) "Change" else null,
                            onTrailingActionClick = {
                                showPatternSetupDialog = true
                            },
                            onClick = {
                                HapticManager.light()
                                if (selectedType != AppLockType.PATTERN || !hasPattern) {
                                    showPatternSetupDialog = true
                                }
                            }
                        )

                        // 4. Biometric (Fingerprint + Face Lock) + PIN Passcode
                        LockOptionItem(
                            title = languageViewModel.getString("lock_type_bio_pin"),
                            icon = Icons.Outlined.Fingerprint,
                            isSelected = selectedType == AppLockType.BIOMETRIC_OR_PIN,
                            trailingAction = if (selectedType == AppLockType.BIOMETRIC_OR_PIN && hasBioPin) "Change PIN" else null,
                            onTrailingActionClick = {
                                isBioPinSetup = true
                                pinSetupTargetLength = bioPinLength
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
                                        isBioPinSetup = true
                                        pinSetupTargetLength = if (hasBioPin) bioPinLength else 4
                                        showPinSetupDialog = true
                                    }
                                }
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
                                        AppLockManager.setAutoLockTimeout(context, timeout)
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
                                            AppLockManager.setAutoLockTimeout(context, timeout)
                                        }
                                    )
                                }
                            }
                        }
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
            allowLengthToggle = isBioPinSetup,
            isBioPin = isBioPinSetup,
            titlePrefix = if (isBioPinSetup) "Biometric" else null,
            onPinSetSuccess = { lengthSet ->
                if (isBioPinSetup) {
                    hasBioPin = true
                    bioPinLength = lengthSet
                    hasPin = false
                    hasPattern = false
                    val act = context as? FragmentActivity
                    if (act != null && AppLockManager.getBiometricStatus(context) == BiometricStatus.AVAILABLE) {
                        AppLockManager.authenticateWithBiometrics(
                            activity = act,
                            title = "Register Fingerprint",
                            subtitle = "Touch fingerprint sensor to register with Swift PDF",
                            negativeButtonText = "Cancel",
                            onSuccess = {
                                selectedType = AppLockType.BIOMETRIC_OR_PIN
                                AppLockManager.setLockType(context, AppLockType.BIOMETRIC_OR_PIN)
                                onLockTypeChanged(AppLockType.BIOMETRIC_OR_PIN)
                                showPinSetupDialog = false
                                showAddMethodDialog = false
                                isBioPinSetup = false
                                Toast.makeText(context, "Biometric registered successfully!", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, "Biometric registration failed: $err", Toast.LENGTH_SHORT).show()
                                showPinSetupDialog = false
                                isBioPinSetup = false
                            }
                        )
                    } else {
                        selectedType = AppLockType.BIOMETRIC_OR_PIN
                        AppLockManager.setLockType(context, AppLockType.BIOMETRIC_OR_PIN)
                        onLockTypeChanged(AppLockType.BIOMETRIC_OR_PIN)
                        showPinSetupDialog = false
                        showAddMethodDialog = false
                        isBioPinSetup = false
                    }
                } else {
                    hasPin = true
                    pinLength = lengthSet
                    hasPattern = false
                    hasBioPin = false
                    val finalType = if (lengthSet == 6) AppLockType.PIN_6 else AppLockType.PIN_4
                    selectedType = finalType
                    AppLockManager.setLockType(context, finalType)
                    onLockTypeChanged(finalType)
                    showPinSetupDialog = false
                    showAddMethodDialog = false
                }
            },
            onDismiss = {
                showPinSetupDialog = false
                isBioPinSetup = false
                selectedType = AppLockManager.lockTypeFlow.value
                hasPin = AppLockManager.hasPinSet(context)
                pinLength = AppLockManager.getPinLength(context)
                hasBioPin = AppLockManager.hasBioPinSet(context)
                bioPinLength = AppLockManager.getBioPinLength(context)
                hasPattern = AppLockManager.hasPatternSet(context)
            }
        )
    }

    if (showPatternSetupDialog) {
        PatternSetupDialog(
            languageViewModel = languageViewModel,
            onPatternSetSuccess = {
                hasPattern = true
                hasPin = false
                hasBioPin = false
                selectedType = AppLockType.PATTERN
                AppLockManager.setLockType(context, AppLockType.PATTERN)
                onLockTypeChanged(AppLockType.PATTERN)
                showPatternSetupDialog = false
                showAddMethodDialog = false
            },
            onDismiss = {
                showPatternSetupDialog = false
                selectedType = AppLockManager.lockTypeFlow.value
                hasPin = AppLockManager.hasPinSet(context)
                pinLength = AppLockManager.getPinLength(context)
                hasBioPin = AppLockManager.hasBioPinSet(context)
                bioPinLength = AppLockManager.getBioPinLength(context)
                hasPattern = AppLockManager.hasPatternSet(context)
            }
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

@Composable
private fun MethodSelectionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                HapticManager.light()
                onClick()
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
