package com.swiftapp.utils

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class SaveLocation(val id: String, val defaultPath: String) {
    SWIFT_FOLDER("swift_folder", "Documents/SwiftPDF"),
    DOWNLOADS("downloads", "Download"),
    DOCUMENTS("documents", "Documents");

    companion object {
        fun fromId(id: String): SaveLocation {
            return entries.find { it.id == id } ?: SWIFT_FOLDER
        }
    }
}

object StorageLocationManager {
    private const val PREFS_NAME = "swift_pdf_storage_prefs"
    private const val KEY_SAVE_LOCATION = "default_save_location"

    private var prefs: SharedPreferences? = null
    private val _currentLocationFlow = MutableStateFlow(SaveLocation.SWIFT_FOLDER)
    val currentLocationFlow: StateFlow<SaveLocation> = _currentLocationFlow.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedId = prefs?.getString(KEY_SAVE_LOCATION, SaveLocation.SWIFT_FOLDER.id) ?: SaveLocation.SWIFT_FOLDER.id
            _currentLocationFlow.value = SaveLocation.fromId(savedId)
        }
    }

    fun getLocation(context: Context): SaveLocation {
        init(context)
        return _currentLocationFlow.value
    }

    fun setLocation(context: Context, location: SaveLocation) {
        init(context)
        _currentLocationFlow.value = location
        prefs?.edit()?.putString(KEY_SAVE_LOCATION, location.id)?.apply()
    }

    fun getTargetDirectory(context: Context): File {
        init(context)
        val location = _currentLocationFlow.value
        val dir = when (location) {
            SaveLocation.SWIFT_FOLDER -> {
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir
                File(base, "SwiftPDF")
            }
            SaveLocation.DOWNLOADS -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: context.filesDir
            }
            SaveLocation.DOCUMENTS -> {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir
            }
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getRelativePath(context: Context): String {
        init(context)
        return when (_currentLocationFlow.value) {
            SaveLocation.SWIFT_FOLDER -> "${Environment.DIRECTORY_DOCUMENTS}/SwiftPDF"
            SaveLocation.DOWNLOADS -> Environment.DIRECTORY_DOWNLOADS
            SaveLocation.DOCUMENTS -> Environment.DIRECTORY_DOCUMENTS
        }
    }

    /**
     * Helper to save a generated PDF file to the user's selected default save location.
     */
    fun savePdfToStorage(context: Context, sourceFile: File, outputFileName: String): File {
        init(context)
        val relativePath = getRelativePath(context)

        // 1. Android 10+ MediaStore insertion for system-wide accessibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        FileInputStream(sourceFile).use { `is` -> `is`.copyTo(os) }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Direct File storage
        val targetDir = getTargetDirectory(context)
        var targetFile = File(targetDir, outputFileName)
        var count = 1
        val baseName = outputFileName.removeSuffix(".pdf")
        while (targetFile.exists()) {
            targetFile = File(targetDir, "${baseName}_$count.pdf")
            count++
        }

        FileInputStream(sourceFile).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        return targetFile
    }
}
