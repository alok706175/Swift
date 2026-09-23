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
import com.swiftapp.ui.viewmodel.LanguageViewModel
import com.swiftapp.ui.viewmodel.ThemeMode
import com.swiftapp.ui.viewmodel.ThemeViewModel
import com.swiftapp.utils.HapticManager
import com.swiftapp.utils.StorageLocationManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }
        
        super.onCreate(savedInstanceState)
        HapticManager.init(this)
        StorageLocationManager.init(this)
        
        setContent {
            val themeViewModel: ThemeViewModel = viewModel()
            val languageViewModel: LanguageViewModel = viewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()
            val currentLanguage by languageViewModel.currentLanguage.collectAsState()

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
                    MainScreen(
                        themeViewModel = themeViewModel,
                        languageViewModel = languageViewModel
                    )
                }
            }
        }
    }
}

