package com.swiftapp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import com.swiftapp.ui.screens.AppLockScreen
import com.swiftapp.ui.screens.MainScreen
import com.swiftapp.ui.theme.PDFUtilityAppTheme
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.ui.viewmodel.ThemeMode
import com.swiftapp.ui.viewmodel.ThemeViewModel
import com.swiftapp.utils.AppLockManager
import com.swiftapp.utils.FileNamingManager
import com.swiftapp.utils.HapticManager
import com.swiftapp.utils.ScannerSettingsManager
import com.swiftapp.utils.StorageLocationManager

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HapticManager.init(this)
        StorageLocationManager.init(this)
        FileNamingManager.init(this)
        AppLockManager.init(this)
        ScannerSettingsManager.init(this)
        com.swiftapp.utils.NotificationSettingsManager.init(this)
        com.swiftapp.utils.NotificationHelper.init(this)
        com.swiftapp.utils.StoragePermissionManager.checkPermission(this)
        
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val languageViewModel: LanguageViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val isSessionUnlocked by AppLockManager.isSessionUnlocked.collectAsState()
            val lockType by AppLockManager.lockTypeFlow.collectAsState()
            var showSplashScreen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

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
                    AnimatedContent(
                        targetState = showSplashScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = androidx.compose.animation.core.tween(350)) togetherWith
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(350))
                        },
                        label = "SplashScreenTransition"
                    ) { isSplash ->
                        if (isSplash) {
                            com.swiftapp.ui.screens.AnimatedSplashScreen(
                                onSplashFinished = {
                                    showSplashScreen = false
                                }
                            )
                        } else {
                            val isLocked = !isSessionUnlocked && lockType != com.swiftapp.utils.AppLockType.NONE

                            AnimatedContent(
                                targetState = isLocked,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
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

    override fun onResume() {
        super.onResume()
        com.swiftapp.utils.StoragePermissionManager.checkPermission(this)
    }
}

