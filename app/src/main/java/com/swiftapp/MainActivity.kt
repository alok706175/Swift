package com.swiftapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swiftapp.ui.screens.MainScreen
import com.swiftapp.ui.theme.PDFUtilityAppTheme
import com.swiftapp.ui.viewmodel.ThemeMode
import com.swiftapp.ui.viewmodel.ThemeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }
        
        super.onCreate(savedInstanceState)
        
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val isAmoled by themeViewModel.isAmoledBlack.collectAsState()

            val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemInDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> systemInDark
            }

            PDFUtilityAppTheme(
                darkTheme = darkTheme,
                isAmoled = isAmoled,
                dynamicColor = false
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MainScreen(themeViewModel = themeViewModel)
                }
            }
        }
    }
}

