package com.swiftapp.utils

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaActionSound
import com.swiftapp.data.model.PolygonCorners
import com.swiftapp.data.model.ScanFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScannerSettingsManager {
    private const val PREFS_NAME = "swift_pdf_scanner_prefs"
    private const val KEY_DEFAULT_FILTER = "scanner_default_filter"
    private const val KEY_SHUTTER_SOUND = "scanner_shutter_sound"
    private const val KEY_AUTO_EDGE_DETECTION = "scanner_auto_edge_detection"

    private var prefs: SharedPreferences? = null
    private var mediaActionSound: MediaActionSound? = null

    private val _defaultFilterFlow = MutableStateFlow(ScanFilter.MAGIC_COLOR)
    val defaultFilterFlow: StateFlow<ScanFilter> = _defaultFilterFlow.asStateFlow()

    private val _isShutterSoundEnabledFlow = MutableStateFlow(true)
    val isShutterSoundEnabledFlow: StateFlow<Boolean> = _isShutterSoundEnabledFlow.asStateFlow()

    private val _autoEdgeDetectionFlow = MutableStateFlow(true)
    val autoEdgeDetectionFlow: StateFlow<Boolean> = _autoEdgeDetectionFlow.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            val filterName = prefs?.getString(KEY_DEFAULT_FILTER, ScanFilter.MAGIC_COLOR.name)
            val filter = runCatching { ScanFilter.valueOf(filterName ?: ScanFilter.MAGIC_COLOR.name) }.getOrDefault(ScanFilter.MAGIC_COLOR)
            val shutterSound = prefs?.getBoolean(KEY_SHUTTER_SOUND, true) ?: true
            val autoEdge = prefs?.getBoolean(KEY_AUTO_EDGE_DETECTION, true) ?: true

            _defaultFilterFlow.value = filter
            _isShutterSoundEnabledFlow.value = shutterSound
            _autoEdgeDetectionFlow.value = autoEdge

            runCatching {
                mediaActionSound = MediaActionSound().apply {
                    load(MediaActionSound.SHUTTER_CLICK)
                }
            }
        }
    }

    fun setDefaultFilter(context: Context, filter: ScanFilter) {
        init(context)
        _defaultFilterFlow.value = filter
        prefs?.edit()?.putString(KEY_DEFAULT_FILTER, filter.name)?.apply()
    }

    fun setShutterSoundEnabled(context: Context, enabled: Boolean) {
        init(context)
        _isShutterSoundEnabledFlow.value = enabled
        prefs?.edit()?.putBoolean(KEY_SHUTTER_SOUND, enabled)?.apply()
    }

    fun setAutoEdgeDetection(context: Context, enabled: Boolean) {
        init(context)
        _autoEdgeDetectionFlow.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_EDGE_DETECTION, enabled)?.apply()
    }

    fun playShutterSound() {
        if (_isShutterSoundEnabledFlow.value) {
            try {
                if (mediaActionSound == null) {
                    mediaActionSound = MediaActionSound().apply {
                        load(MediaActionSound.SHUTTER_CLICK)
                    }
                }
                mediaActionSound?.play(MediaActionSound.SHUTTER_CLICK)
            } catch (_: Exception) {}
        }
    }

    fun getInitialCorners(): PolygonCorners {
        return if (_autoEdgeDetectionFlow.value) {
            PolygonCorners.DEFAULT
        } else {
            PolygonCorners.FULL
        }
    }
}
