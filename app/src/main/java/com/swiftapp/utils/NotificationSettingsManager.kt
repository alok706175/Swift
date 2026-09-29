package com.swiftapp.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages notification preferences (operation completion alerts).
 */
object NotificationSettingsManager {

    private const val PREFS_NAME = "swift_notification_settings"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_PROMPTED_FIRST_TIME = "has_prompted_notification_first_time"

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

    fun hasPromptedFirstTime(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PROMPTED_FIRST_TIME, false)
    }

    fun setPromptedFirstTime(context: Context, prompted: Boolean = true) {
        getPrefs(context).edit().putBoolean(KEY_PROMPTED_FIRST_TIME, prompted).apply()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        _isNotificationEnabledFlow.value = enabled
        getPrefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
