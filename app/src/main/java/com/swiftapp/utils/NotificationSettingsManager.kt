package com.swiftapp.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages notification preferences (operation completion alerts).
 */
object NotificationSettingsManager {

    private const val PREFS_NAME = "swift_notification_settings"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"

    private val _isNotificationEnabledFlow = MutableStateFlow(true)
    val isNotificationEnabledFlow: StateFlow<Boolean> = _isNotificationEnabledFlow.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = getPrefs(context)
        val enabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        _isNotificationEnabledFlow.value = enabled
        isInitialized = true
    }

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        _isNotificationEnabledFlow.value = enabled
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
