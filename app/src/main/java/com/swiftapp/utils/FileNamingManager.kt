package com.swiftapp.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class NamingFormat(val id: String, val titleKey: String, val examplePrefix: String) {
    TIMESTAMP_COMPACT("timestamp_compact", "naming_format_compact", "Scan_yyyyMMdd_HHmm.pdf"),
    TIMESTAMP_DETAILED("timestamp_detailed", "naming_format_detailed", "Scan_yyyy-MM-dd_HHmmss.pdf"),
    DATE_ONLY("date_only", "naming_format_date", "Scan_yyyyMMdd.pdf");

    companion object {
        fun fromId(id: String): NamingFormat {
            return entries.find { it.id == id } ?: TIMESTAMP_COMPACT
        }
    }
}

object FileNamingManager {
    private const val PREFS_NAME = "swift_pdf_naming_prefs"
    private const val KEY_NAMING_FORMAT = "file_naming_format"
    private const val KEY_AUTO_TIMESTAMP = "auto_append_timestamp"

    private var prefs: SharedPreferences? = null

    private val _namingFormatFlow = MutableStateFlow(NamingFormat.TIMESTAMP_COMPACT)
    val namingFormatFlow: StateFlow<NamingFormat> = _namingFormatFlow.asStateFlow()

    private val _autoTimestampFlow = MutableStateFlow(true)
    val autoTimestampFlow: StateFlow<Boolean> = _autoTimestampFlow.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val formatId = prefs?.getString(KEY_NAMING_FORMAT, NamingFormat.TIMESTAMP_COMPACT.id)
                ?: NamingFormat.TIMESTAMP_COMPACT.id
            val autoTime = prefs?.getBoolean(KEY_AUTO_TIMESTAMP, true) ?: true

            _namingFormatFlow.value = NamingFormat.fromId(formatId)
            _autoTimestampFlow.value = autoTime
        }
    }

    fun getFormat(context: Context): NamingFormat {
        init(context)
        return _namingFormatFlow.value
    }

    fun isAutoTimestampEnabled(context: Context): Boolean {
        init(context)
        return _autoTimestampFlow.value
    }

    fun setFormat(context: Context, format: NamingFormat) {
        init(context)
        _namingFormatFlow.value = format
        prefs?.edit()?.putString(KEY_NAMING_FORMAT, format.id)?.apply()
    }

    fun setAutoTimestampEnabled(context: Context, enabled: Boolean) {
        init(context)
        _autoTimestampFlow.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_TIMESTAMP, enabled)?.apply()
    }

    /**
     * Generates a formatted timestamp string based on current user preference.
     * e.g., "20260924_0100" or "2026-09-24_010000"
     */
    fun getFormattedTimestamp(format: NamingFormat = _namingFormatFlow.value, date: Date = Date()): String {
        val pattern = when (format) {
            NamingFormat.TIMESTAMP_COMPACT -> "yyyyMMdd_HHmm"
            NamingFormat.TIMESTAMP_DETAILED -> "yyyy-MM-dd_HHmmss"
            NamingFormat.DATE_ONLY -> "yyyyMMdd"
        }
        return SimpleDateFormat(pattern, Locale.US).format(date)
    }

    /**
     * Generates a standardized file name for any PDF operation.
     * Examples:
     * - Scan: "Scan_20260924_0100.pdf"
     * - Merge: "Merged_20260924_0100.pdf"
     * - Compress with original "invoice.pdf": "invoice_compressed_20260924_0100.pdf"
     */
    fun generateFileName(
        prefix: String,
        originalFileName: String? = null,
        extension: String = "pdf"
    ): String {
        val ext = extension.removePrefix(".")
        val timestamp = getFormattedTimestamp(_namingFormatFlow.value)
        val shouldAppendTime = _autoTimestampFlow.value

        return if (!originalFileName.isNullOrBlank()) {
            val cleanOriginal = originalFileName.removeSuffix(".pdf").removeSuffix(".PDF")
            val actionTag = when (prefix.lowercase(Locale.ROOT)) {
                "scan" -> "scanned"
                "merge", "merged" -> "merged"
                "compress", "compressed" -> "compressed"
                "protect", "protected" -> "protected"
                "unlock", "unlocked" -> "unlocked"
                "rotate", "rotated" -> "rotated"
                "sign", "signed", "esign" -> "signed"
                "split" -> "split"
                "delete_pages", "delete", "edit" -> "edited"
                else -> prefix.lowercase(Locale.ROOT)
            }
            if (shouldAppendTime) {
                "${cleanOriginal}_${actionTag}_$timestamp.$ext"
            } else {
                "${cleanOriginal}_$actionTag.$ext"
            }
        } else {
            val cleanPrefix = prefix.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            if (shouldAppendTime) {
                "${cleanPrefix}_$timestamp.$ext"
            } else {
                "${cleanPrefix}_document.$ext"
            }
        }
    }

    /**
     * Sample preview helper for UI display.
     */
    fun getPreviewSample(format: NamingFormat, autoTime: Boolean): String {
        val timestamp = getFormattedTimestamp(format)
        return if (autoTime) {
            "Scan_$timestamp.pdf"
        } else {
            "Scan_document.pdf"
        }
    }
}
