package com.swiftapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swiftapp.ui.screens.AnimatedSplashScreen
import com.swiftapp.ui.screens.AppLockScreen
import com.swiftapp.ui.screens.MainScreen
import com.swiftapp.ui.theme.PDFUtilityAppTheme
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.ui.viewmodel.ThemeMode
import com.swiftapp.ui.viewmodel.ThemeViewModel
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.AppLockType
import com.swiftapp.utils.FileNamingManager
import com.swiftapp.utils.HapticManager
import com.swiftapp.utils.ScannerSettingsManager
import com.swiftapp.utils.StorageLocationManager
import com.swiftapp.utils.StoragePermissionManager

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
            val themeMode by themeViewModel.themeMode.collectAsState()
            val isSessionUnlocked by AppLockManager.isSessionUnlocked.collectAsState()
            val lockType by AppLockManager.lockTypeFlow.collectAsState()
            var showSplashScreen by remember { mutableStateOf(true) }

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
                                        languageViewModel = languageViewModel
                                    )
                                }
                            }
                        }
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
