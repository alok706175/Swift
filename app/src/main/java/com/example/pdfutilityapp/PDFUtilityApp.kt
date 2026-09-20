package com.example.pdfutilityapp

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Application class for PDFUtilityApp.
 * Initializes the PDFBox resource loader required for PDF manipulation.
 */
class PDFUtilityApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize PDFBox-Android resource loader
        PDFBoxResourceLoader.init(applicationContext)
    }
}
