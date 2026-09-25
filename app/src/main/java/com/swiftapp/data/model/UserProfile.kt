package com.swiftapp.data.model

import org.json.JSONObject

enum class AuthProvider {
    GOOGLE,
    EMAIL,
    GUEST;

    companion object {
        fun fromString(value: String?): AuthProvider {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GUEST
        }
    }
}

enum class AccountTier(val displayName: String, val quotaBytes: Long) {
    FREE("Free Tier", 5L * 1024L * 1024L * 1024L), // 5 GB
    PRO("Swift Pro", 50L * 1024L * 1024L * 1024L), // 50 GB
    GUEST("Local Guest", 500L * 1024L * 1024L); // 500 MB

    companion object {
        fun fromString(value: String?): AccountTier {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FREE
        }
    }
}

data class UserProfile(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val provider: AuthProvider = AuthProvider.GOOGLE,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val accountTier: AccountTier = AccountTier.FREE,
    val cloudQuotaBytes: Long = AccountTier.FREE.quotaBytes,
    val cloudUsedBytes: Long = 0L,
    val isEmailVerified: Boolean = true,
    val isNewUser: Boolean = false,
) {
    val isGuest: Boolean
        get() = provider == AuthProvider.GUEST

    val initials: String
        get() {
            if (displayName.isBlank()) return "U"
            val parts = displayName.trim().split(" ")
            return if (parts.size >= 2) {
                "${parts[0].firstOrNull()?.uppercaseChar() ?: ""}${parts[1].firstOrNull()?.uppercaseChar() ?: ""}"
            } else {
                "${displayName.firstOrNull()?.uppercaseChar() ?: "U"}"
            }
        }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("uid", uid)
            put("email", email)
            put("displayName", displayName)
            put("photoUrl", photoUrl ?: "")
            put("provider", provider.name)
            put("createdAt", createdAt)
            put("lastLoginAt", lastLoginAt)
            put("accountTier", accountTier.name)
            put("cloudQuotaBytes", cloudQuotaBytes)
            put("cloudUsedBytes", cloudUsedBytes)
            put("isEmailVerified", isEmailVerified)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): UserProfile {
            return UserProfile(
                uid = json.optString("uid", ""),
                email = json.optString("email", ""),
                displayName = json.optString("displayName", "User"),
                photoUrl = json.optString("photoUrl", "").takeIf { it.isNotBlank() },
                provider = AuthProvider.fromString(json.optString("provider", AuthProvider.GOOGLE.name)),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                lastLoginAt = json.optLong("lastLoginAt", System.currentTimeMillis()),
                accountTier = AccountTier.fromString(json.optString("accountTier", AccountTier.FREE.name)),
                cloudQuotaBytes = json.optLong("cloudQuotaBytes", AccountTier.FREE.quotaBytes),
                cloudUsedBytes = json.optLong("cloudUsedBytes", 0L),
                isEmailVerified = json.optBoolean("isEmailVerified", true),
                isNewUser = false
            )
        }

        fun createGuestUser(): UserProfile {
            val guestId = "guest_${System.currentTimeMillis()}"
            return UserProfile(
                uid = guestId,
                email = "guest@swift.local",
                displayName = "Guest User",
                photoUrl = null,
                provider = AuthProvider.GUEST,
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis(),
                accountTier = AccountTier.GUEST,
                cloudQuotaBytes = AccountTier.GUEST.quotaBytes,
                cloudUsedBytes = 0L,
                isEmailVerified = false,
                isNewUser = true
            )
        }
    }
}

sealed class AuthState {
    data object Initial : AuthState()
    data object Loading : AuthState()
    data class Authenticated(val user: UserProfile, val isNewUser: Boolean = false) : AuthState()
    data class Guest(val user: UserProfile) : AuthState()
    data class Unauthenticated(val message: String? = null) : AuthState()
    data class Error(val message: String, val isNetworkError: Boolean = false) : AuthState()
}

sealed class AuthResult {
    data class Success(val user: UserProfile, val isNewUser: Boolean) : AuthResult()
    data class Failure(val errorMessage: String, val isNetworkError: Boolean = false) : AuthResult()
    data object Cancelled : AuthResult()
}
