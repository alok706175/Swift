package com.swiftapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swiftapp.ui.components.FirstTimePermissionDialog
import com.swiftapp.ui.screens.AnimatedSplashScreen
import com.swiftapp.ui.screens.AppLockScreen
import com.swiftapp.ui.screens.AuthScreen
import com.swiftapp.ui.screens.MainScreen
import com.swiftapp.ui.theme.PDFUtilityAppTheme
import com.swiftapp.ui.viewmodel.AuthViewModel
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.ui.viewmodel.ThemeMode
import com.swiftapp.ui.viewmodel.ThemeViewModel
import com.swiftapp.data.model.AuthState
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.AppLockType
import com.swiftapp.utils.FileNamingManager
import com.swiftapp.utils.HapticManager
import com.swiftapp.utils.ScannerSettingsManager
import com.swiftapp.utils.StorageLocationManager
import com.swiftapp.utils.StoragePermissionManager
import com.swiftapp.utils.StoragePermissionState

class MainActivity : FragmentActivity() {
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == android.content.Intent.ACTION_SCREEN_OFF) {
                AppLockManager.onScreenClosed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)

        HapticManager.init(this)
        StorageLocationManager.init(this)
        FileNamingManager.init(this)
        AppLockManager.init(this)
        AppLockManager.applyPrivacyShield(this)
        ScannerSettingsManager.init(this)
        com.swiftapp.utils.NotificationSettingsManager.init(this)
        com.swiftapp.utils.NotificationHelper.init(this)
        StoragePermissionManager.checkPermission(this)
        
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val languageViewModel: LanguageViewModel = viewModel()
            val authViewModel: com.swiftapp.ui.viewmodel.AuthViewModel = viewModel(
                factory = com.swiftapp.ui.viewmodel.AuthViewModel.provideFactory(this)
            )
            val themeMode by themeViewModel.themeMode.collectAsState()
            val authState by authViewModel.authState.collectAsState()
            val isSessionUnlocked by AppLockManager.isSessionUnlocked.collectAsState()
            val lockType by AppLockManager.lockTypeFlow.collectAsState()
            val permissionState by StoragePermissionManager.permissionStateFlow.collectAsState()
            var showSplashScreen by remember { mutableStateOf(true) }
            var showFirstTimePermissionDialog by remember {
                mutableStateOf(
                    permissionState != StoragePermissionState.GRANTED &&
                    !StoragePermissionManager.hasPromptedFirstTime(this@MainActivity)
                )
            }

            LaunchedEffect(permissionState) {
                if (permissionState == StoragePermissionState.GRANTED) {
                    showFirstTimePermissionDialog = false
                }
            }

            val requestStorageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { _ ->
                StoragePermissionManager.checkPermission(this@MainActivity)
            }

            val manageStorageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                StoragePermissionManager.checkPermission(this@MainActivity)
            }

            fun requestAppPermissions() {
                StoragePermissionManager.setPromptedFirstTime(this@MainActivity, true)
                showFirstTimePermissionDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        manageStorageLauncher.launch(intent)
                    } catch (e: Exception) {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        manageStorageLauncher.launch(intent)
                    }
                } else {
                    val permissions = arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                    requestStorageLauncher.launch(permissions)
                }
            }

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> false
            }

            PDFUtilityAppTheme(darkTheme = darkTheme, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Crossfade(
                        targetState = showSplashScreen,
                        animationSpec = tween(350),
                        label = "SplashScreenCrossfade"
                    ) { isSplash ->
                        if (isSplash) {
                            AnimatedSplashScreen(
                                onSplashFinished = {
                                    showSplashScreen = false
                                }
                            )
                        } else {
                            val isAuthNeeded = authState is com.swiftapp.data.model.AuthState.Unauthenticated

                            Crossfade(
                                targetState = isAuthNeeded,
                                animationSpec = tween(300),
                                label = "AuthCrossfade"
                            ) { needsAuth ->
                                if (needsAuth) {
                                    com.swiftapp.ui.screens.AuthScreen(
                                        authViewModel = authViewModel,
                                        languageViewModel = languageViewModel,
                                        onAuthSuccess = {},
                                        onContinueAsGuest = {}
                                    )
                                } else {
                                    val isLocked = !isSessionUnlocked && lockType != AppLockType.NONE

                                    Crossfade(
                                        targetState = isLocked,
                                        animationSpec = tween(250),
                                        label = "AppLockTransition"
                                    ) { locked ->
                                        if (locked) {
                                            AppLockScreen(
                                                languageViewModel = languageViewModel,
                                                onUnlocked = {
                                                    AppLockManager.unlockSession()
                                                }
                                            )
                                        } else {
                                            MainScreen(
                                                themeViewModel = themeViewModel,
                                                languageViewModel = languageViewModel,
                                                authViewModel = authViewModel
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!showSplashScreen && showFirstTimePermissionDialog) {
                        FirstTimePermissionDialog(
                            languageViewModel = languageViewModel,
                            onAllow = {
                                requestAppPermissions()
                            },
                            onDeny = {
                                StoragePermissionManager.setPromptedFirstTime(this@MainActivity, true)
                                showFirstTimePermissionDialog = false
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
    }

    override fun onStart() {
        super.onStart()
        AppLockManager.onAppForegrounded()
        AppLockManager.applyPrivacyShield(this)
    }

    override fun onResume() {
        super.onResume()
        AppLockManager.onAppForegrounded()
        AppLockManager.applyPrivacyShield(this)
        StoragePermissionManager.checkPermission(this)
    }

    override fun onStop() {
        super.onStop()
        AppLockManager.onAppBackgrounded()
    }
}
