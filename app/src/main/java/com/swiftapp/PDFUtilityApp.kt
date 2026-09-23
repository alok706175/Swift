package com.swiftapp

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application class for PDFUtilityApp.
 * Initializes the PDFBox resource loader asynchronously to keep app launch fast and responsive.
 */
class PDFUtilityApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Async initialization on background thread to prevent UI freezing during app startup
        CoroutineScope(Dispatchers.Default).launch {
            PDFBoxResourceLoader.init(applicationContext)
        }
    }
}
