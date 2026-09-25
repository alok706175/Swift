package com.swiftapp.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StoragePermissionState {
    GRANTED,
    LIMITED,
    DENIED
}

/**
 * Utility to check, observe, and manage storage and all-files permissions across Android versions.
 */
object StoragePermissionManager {

    private const val PREFS_NAME = "swift_storage_prefs"
    private const val KEY_PROMPTED_FIRST_TIME = "has_prompted_first_time"

    private val _permissionStateFlow = MutableStateFlow(StoragePermissionState.DENIED)
    val permissionStateFlow: StateFlow<StoragePermissionState> = _permissionStateFlow.asStateFlow()

    fun hasPromptedFirstTime(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PROMPTED_FIRST_TIME, false)
    }

    fun setPromptedFirstTime(context: Context, prompted: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PROMPTED_FIRST_TIME, prompted).apply()
    }

    fun checkPermission(context: Context): StoragePermissionState {
        val state = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    StoragePermissionState.GRANTED
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                ) {
                    StoragePermissionState.LIMITED
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    StoragePermissionState.LIMITED
                } else {
                    StoragePermissionState.DENIED
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                val write = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                if (write && read) {
                    StoragePermissionState.GRANTED
                } else if (read) {
                    StoragePermissionState.LIMITED
                } else {
                    StoragePermissionState.DENIED
                }
            }
            else -> StoragePermissionState.GRANTED
        }

        _permissionStateFlow.value = state
        return state
    }

    fun openStorageSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to standard App Details Settings
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
