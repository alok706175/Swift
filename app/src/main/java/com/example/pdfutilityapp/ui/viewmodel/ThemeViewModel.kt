package com.example.pdfutilityapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

enum class ThemeMode {
    SYSTEM, LIGHT, DARK, SCHEDULED
}

class ThemeViewModel : ViewModel() {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isDynamicColor = MutableStateFlow(true)
    val isDynamicColor: StateFlow<Boolean> = _isDynamicColor.asStateFlow()

    private val _startTime = MutableStateFlow(LocalTime.of(20, 0)) // 8 PM
    val startTime: StateFlow<LocalTime> = _startTime.asStateFlow()

    private val _endTime = MutableStateFlow(LocalTime.of(6, 0)) // 6 AM
    val endTime: StateFlow<LocalTime> = _endTime.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        _isDynamicColor.value = enabled
    }

    fun setSchedule(start: LocalTime, end: LocalTime) {
        _startTime.value = start
        _endTime.value = end
    }

    fun toggleLightDark() {
        _themeMode.value = if (_themeMode.value == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
    }
}
