package com.swiftapp.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.swiftapp.data.model.AccountTier
import com.swiftapp.data.model.AuthProvider
import com.swiftapp.data.model.AuthResult
import com.swiftapp.data.model.AuthState
import com.swiftapp.data.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AuthRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val securePrefs: SharedPreferences = createSecurePreferences(context)
    private val usersDbFile: File = File(context.filesDir, "swift_users_db.json")

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    init {
        restoreSession()
    }

    private fun createSecurePreferences(ctx: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                ctx,
                "swift_secure_auth_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to standard prefs", e)
            ctx.getSharedPreferences("swift_auth_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    /**
     * Cold start session restoration.
     */
    fun restoreSession() {
        val isLoggedIn = securePrefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val isGuest = securePrefs.getBoolean(KEY_IS_GUEST, false)
        val sessionUid = securePrefs.getString(KEY_SESSION_UID, null)

        if (isLoggedIn && !sessionUid.isNullOrBlank()) {
            val user = findUserByUid(sessionUid)
            if (user != null) {
                _currentUser.value = user
                _authState.value = AuthState.Authenticated(user = user, isNewUser = false)
                return
            }
        }

        if (isGuest && !sessionUid.isNullOrBlank()) {
            val guestProfile = findUserByUid(sessionUid) ?: UserProfile.createGuestUser()
            _currentUser.value = guestProfile
            _authState.value = AuthState.Guest(user = guestProfile)
            return
        }

        _currentUser.value = null
        _authState.value = AuthState.Unauthenticated()
    }

    /**
     * Unified "Continue with Google" sign-in and sign-up with deduplication.
     */
    suspend fun authenticateWithGoogle(
        uid: String,
        email: String,
        displayName: String,
        photoUrl: String?
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val trimmedEmail = email.trim().lowercase()
            val existingByUid = findUserByUid(uid)
            val existingByEmail = findUserByEmail(trimmedEmail)
            val existingUser = existingByUid ?: existingByEmail

            if (existingUser != null) {
                // Deduplication: User already exists.
                // Do NOT overwrite existing preferences, creation timestamp, or tier.
                val updatedUser = existingUser.copy(
                    uid = uid, // Ensure linked with current Google UID
                    email = trimmedEmail,
                    displayName = if (displayName.isNotBlank()) displayName else existingUser.displayName,
                    photoUrl = photoUrl ?: existingUser.photoUrl,
                    provider = AuthProvider.GOOGLE,
                    lastLoginAt = System.currentTimeMillis(),
                    isEmailVerified = true,
                    isNewUser = false
                )
                saveOrUpdateUserInternal(updatedUser)
                saveSession(updatedUser, isGuest = false)

                _currentUser.value = updatedUser
                _authState.value = AuthState.Authenticated(user = updatedUser, isNewUser = false)

                Log.d(TAG, "Existing user logged in with Google deduplication: $trimmedEmail")
                AuthResult.Success(user = updatedUser, isNewUser = false)
            } else {
                // New User Registration
                val newUser = UserProfile(
                    uid = uid.ifBlank { "google_${UUID.randomUUID()}" },
                    email = trimmedEmail,
                    displayName = displayName.ifBlank { trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() } },
                    photoUrl = photoUrl,
                    provider = AuthProvider.GOOGLE,
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis(),
                    accountTier = AccountTier.FREE,
                    cloudQuotaBytes = AccountTier.FREE.quotaBytes,
                    cloudUsedBytes = 0L,
                    isEmailVerified = true,
                    isNewUser = true
                )
                saveOrUpdateUserInternal(newUser)
                saveSession(newUser, isGuest = false)

                _currentUser.value = newUser
                _authState.value = AuthState.Authenticated(user = newUser, isNewUser = true)

                Log.d(TAG, "New user registered with Google: $trimmedEmail")
                AuthResult.Success(user = newUser, isNewUser = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google authentication failed", e)
            AuthResult.Failure(errorMessage = e.message ?: "Authentication failed")
        }
    }

    /**
     * Email / Password Authentication & Deduplication support.
     */
    suspend fun authenticateWithEmail(
        email: String,
        password: String,
        displayName: String? = null,
        isSignUp: Boolean = false
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                return@withContext AuthResult.Failure(errorMessage = "Please enter a valid email address.")
            }
            if (password.length < 6) {
                return@withContext AuthResult.Failure(errorMessage = "Password must be at least 6 characters.")
            }

            val existingUser = findUserByEmail(cleanEmail)

            if (isSignUp) {
                if (existingUser != null) {
                    // Account already exists with this email -> seamlessly log them in or link
                    val updated = existingUser.copy(
                        lastLoginAt = System.currentTimeMillis()
                    )
                    saveOrUpdateUserInternal(updated)
                    saveSession(updated, isGuest = false)
                    _currentUser.value = updated
                    _authState.value = AuthState.Authenticated(user = updated, isNewUser = false)
                    return@withContext AuthResult.Success(user = updated, isNewUser = false)
                }

                val newUser = UserProfile(
                    uid = "email_${UUID.randomUUID()}",
                    email = cleanEmail,
                    displayName = displayName?.trim()?.ifBlank { null } ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                    photoUrl = null,
                    provider = AuthProvider.EMAIL,
                    createdAt = System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis(),
                    accountTier = AccountTier.FREE,
                    cloudQuotaBytes = AccountTier.FREE.quotaBytes,
                    cloudUsedBytes = 0L,
                    isEmailVerified = true,
                    isNewUser = true
                )
                saveOrUpdateUserInternal(newUser)
                saveSession(newUser, isGuest = false)
                _currentUser.value = newUser
                _authState.value = AuthState.Authenticated(user = newUser, isNewUser = true)
                AuthResult.Success(user = newUser, isNewUser = true)
            } else {
                // Sign In
                if (existingUser == null) {
                    // Auto-register or create account seamlessly
                    val newUser = UserProfile(
                        uid = "email_${UUID.randomUUID()}",
                        email = cleanEmail,
                        displayName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                        photoUrl = null,
                        provider = AuthProvider.EMAIL,
                        createdAt = System.currentTimeMillis(),
                        lastLoginAt = System.currentTimeMillis(),
                        accountTier = AccountTier.FREE,
                        cloudQuotaBytes = AccountTier.FREE.quotaBytes,
                        cloudUsedBytes = 0L,
                        isEmailVerified = true,
                        isNewUser = true
                    )
                    saveOrUpdateUserInternal(newUser)
                    saveSession(newUser, isGuest = false)
                    _currentUser.value = newUser
                    _authState.value = AuthState.Authenticated(user = newUser, isNewUser = true)
                    AuthResult.Success(user = newUser, isNewUser = true)
                } else {
                    val updated = existingUser.copy(lastLoginAt = System.currentTimeMillis())
                    saveOrUpdateUserInternal(updated)
                    saveSession(updated, isGuest = false)
                    _currentUser.value = updated
                    _authState.value = AuthState.Authenticated(user = updated, isNewUser = false)
                    AuthResult.Success(user = updated, isNewUser = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Email auth error", e)
            AuthResult.Failure(errorMessage = e.message ?: "Authentication error")
        }
    }

    /**
     * Guest mode: continue without an account to use offline PDF tools.
     */
    fun continueAsGuest(): UserProfile {
        val guest = UserProfile.createGuestUser()
        saveOrUpdateUserInternal(guest)
        saveSession(guest, isGuest = true)
        _currentUser.value = guest
        _authState.value = AuthState.Guest(user = guest)
        return guest
    }

    /**
     * Clean Sign Out: revokes active session, clears in-memory tokens and profile.
     */
    fun signOut() {
        securePrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .putBoolean(KEY_IS_GUEST, false)
            .remove(KEY_SESSION_UID)
            .remove(KEY_AUTH_TOKEN)
            .apply()

        _currentUser.value = null
        _authState.value = AuthState.Unauthenticated()
        Log.d(TAG, "User signed out successfully")
    }

    /**
     * Delete Account: completely removes the user profile and wipes session data.
     */
    suspend fun deleteAccount(uid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val users = loadAllUsersInternal().toMutableList()
            val removed = users.removeAll { it.uid == uid }
            if (removed) {
                saveAllUsersInternal(users)
            }
            signOut()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete user account: $uid", e)
            false
        }
    }

    // --- Internal Storage & Deduplication Helpers ---

    private fun saveSession(user: UserProfile, isGuest: Boolean) {
        securePrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, !isGuest)
            .putBoolean(KEY_IS_GUEST, isGuest)
            .putString(KEY_SESSION_UID, user.uid)
            .putString(KEY_AUTH_TOKEN, "token_${user.uid}_${System.currentTimeMillis()}")
            .putLong(KEY_LAST_LOGIN_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    fun findUserByUid(uid: String): UserProfile? {
        return loadAllUsersInternal().firstOrNull { it.uid == uid }
    }

    fun findUserByEmail(email: String): UserProfile? {
        val normalized = email.trim().lowercase()
        return loadAllUsersInternal().firstOrNull { it.email.trim().lowercase() == normalized }
    }

    @Synchronized
    private fun saveOrUpdateUserInternal(user: UserProfile) {
        val users = loadAllUsersInternal().toMutableList()
        val index = users.indexOfFirst { it.uid == user.uid || (it.email.isNotBlank() && it.email.equals(user.email, ignoreCase = true)) }
        if (index >= 0) {
            users[index] = user
        } else {
            users.add(user)
        }
        saveAllUsersInternal(users)
    }

    @Synchronized
    private fun loadAllUsersInternal(): List<UserProfile> {
        return try {
            if (!usersDbFile.exists()) return emptyList()
            val text = usersDbFile.readText()
            if (text.isBlank()) return emptyList()
            val jsonArray = JSONArray(text)
            val list = mutableListOf<UserProfile>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(UserProfile.fromJson(obj))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error reading users db", e)
            emptyList()
        }
    }

    @Synchronized
    private fun saveAllUsersInternal(users: List<UserProfile>) {
        try {
            val jsonArray = JSONArray()
            users.forEach { jsonArray.put(it.toJson()) }
            usersDbFile.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error writing users db", e)
        }
    }

    companion object {
        private const val TAG = "AuthRepository"
        private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
        private const val KEY_IS_GUEST = "key_is_guest"
        private const val KEY_SESSION_UID = "key_session_uid"
        private const val KEY_AUTH_TOKEN = "key_auth_token"
        private const val KEY_LAST_LOGIN_TIMESTAMP = "key_last_login_timestamp"

        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(context: Context): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
