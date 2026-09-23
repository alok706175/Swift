package com.swiftapp.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

enum class ThemeMode {
    SYSTEM, LIGHT, DARK, SCHEDULED
}

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("swift_theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        try {
            val saved = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(saved)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isAmoledBlack = MutableStateFlow(
        prefs.getBoolean("is_amoled_black", false)
    )
    val isAmoledBlack: StateFlow<Boolean> = _isAmoledBlack.asStateFlow()

    private val _isDynamicColor = MutableStateFlow(true)
    val isDynamicColor: StateFlow<Boolean> = _isDynamicColor.asStateFlow()

    private val _startTime = MutableStateFlow(LocalTime.of(20, 0)) // 8 PM
    val startTime: StateFlow<LocalTime> = _startTime.asStateFlow()

    private val _endTime = MutableStateFlow(LocalTime.of(6, 0)) // 6 AM
    val endTime: StateFlow<LocalTime> = _endTime.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setAmoledBlack(enabled: Boolean) {
        _isAmoledBlack.value = enabled
        prefs.edit().putBoolean("is_amoled_black", enabled).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _isDynamicColor.value = enabled
    }

    fun setSchedule(start: LocalTime, end: LocalTime) {
        _startTime.value = start
        _endTime.value = end
    }

    fun toggleLightDark() {
        val next = if (_themeMode.value == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeMode(next)
    }
}

