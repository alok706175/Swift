package com.swiftapp.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "swift_pdf_language_prefs"
    private const val KEY_LANGUAGE = "selected_language_code"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getSavedLanguage(context: Context): AppLanguage {
        val code = getPrefs(context).getString(KEY_LANGUAGE, AppLanguage.ENGLISH.code) ?: AppLanguage.ENGLISH.code
        return AppLanguage.fromCode(code)
    }

    fun saveLanguage(context: Context, language: AppLanguage) {
        getPrefs(context).edit().putString(KEY_LANGUAGE, language.code).apply()
        updateLocale(context, language)
    }

    fun updateLocale(context: Context, language: AppLanguage): Context {
        val locale = Locale(language.code)
        Locale.setDefault(locale)

        val resources = context.resources
        val configuration = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }

        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)

        return context.createConfigurationContext(configuration)
    }
}
