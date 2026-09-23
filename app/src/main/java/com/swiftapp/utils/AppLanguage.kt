package com.swiftapp.utils

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String
) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "हिन्दी", "Hindi"),
    MARATHI("mr", "मराठी", "Marathi"),
    BENGALI("bn", "বাংলা", "Bengali"),
    GUJARATI("gu", "ગુજરાતી", "Gujarati"),
    TAMIL("ta", "தமிழ்", "Tamil"),
    TELUGU("te", "తెలుగు", "Telugu"),
    KANNADA("kn", "ಕನ್ನಡ", "Kannada"),
    PUNJABI("pa", "ਪੰਜਾਬੀ", "Punjabi"),
    MALAYALAM("ml", "മലയാളം", "Malayalam"),
    ODIA("or", "ଓଡ଼ିଆ", "Odia"),
    URDU("ur", "اردو", "Urdu");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}
