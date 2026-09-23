package com.swiftapp.utils

import android.content.Context
import android.content.SharedPreferences
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

    private var prefs: SharedPreferences? = null
    private val _isHapticEnabledFlow = MutableStateFlow(true)
    val isHapticEnabledFlow: StateFlow<Boolean> = _isHapticEnabledFlow.asStateFlow()

    var isHapticEnabled: Boolean = true
        private set

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val enabled = prefs?.getBoolean(KEY_HAPTIC_ENABLED, true) ?: true
            isHapticEnabled = enabled
            _isHapticEnabledFlow.value = enabled
        }
    }

    fun setHapticEnabled(context: Context, enabled: Boolean) {
        init(context)
        isHapticEnabled = enabled
        _isHapticEnabledFlow.value = enabled
        prefs?.edit()?.putBoolean(KEY_HAPTIC_ENABLED, enabled)?.apply()
    }

    fun performHaptic(
        view: View?,
        haptic: HapticFeedback? = null,
        feedbackConstant: Int = HapticFeedbackConstants.KEYBOARD_TAP
    ) {
        if (!isHapticEnabled) return
        try {
            view?.performHapticFeedback(feedbackConstant)
        } catch (_: Exception) {
            try {
                haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }
}
