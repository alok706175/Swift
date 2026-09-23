package com.swiftapp.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HapticManager {
    private const val PREFS_NAME = "swift_pdf_haptic_prefs"
    private const val KEY_HAPTIC_ENABLED = "haptic_feedback_enabled"

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var vibrator: Vibrator? = null

    private val _isHapticEnabledFlow = MutableStateFlow(true)
    val isHapticEnabledFlow: StateFlow<Boolean> = _isHapticEnabledFlow.asStateFlow()

    var isHapticEnabled: Boolean = true
        private set

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        if (prefs == null) {
            prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs?.getBoolean(KEY_HAPTIC_ENABLED, true) ?: true
            isHapticEnabled = enabled
            _isHapticEnabledFlow.value = enabled
        }
        if (vibrator == null) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        init(context)
        isHapticEnabled = enabled
        _isHapticEnabledFlow.value = enabled
        prefs?.edit()?.putBoolean(KEY_HAPTIC_ENABLED, enabled)?.apply()
        if (enabled) {
            performHaptic()
        }
    }

    fun performHaptic(
        view: View? = null,
        haptic: HapticFeedback? = null,
        feedbackConstant: Int = HapticFeedbackConstants.KEYBOARD_TAP
    ) {
        if (!isHapticEnabled) return

        // 1. Hardware Vibrator for immediate and physically felt tactile tap
        try {
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(30)
                    }
                    return
                }
            }
        } catch (_: Exception) {}

        // 2. View Haptic Feedback fallback
        try {
            view?.isHapticFeedbackEnabled = true
            val flags = HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            val handled = view?.performHapticFeedback(feedbackConstant, flags) ?: false
            if (!handled) {
                view?.performHapticFeedback(feedbackConstant)
            }
        } catch (_: Exception) {
            try {
                haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }
}

