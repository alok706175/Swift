package com.swiftapp.utils

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

sealed class GoogleAuthResult {
    data class Success(
        val uid: String,
        val email: String,
        val displayName: String,
        val photoUrl: String?,
        val idToken: String?
    ) : GoogleAuthResult()

    data class Error(
        val message: String,
        val isNetworkError: Boolean = false
    ) : GoogleAuthResult()

    data object Cancelled : GoogleAuthResult()
    data object RequiresFallbackPicker : GoogleAuthResult()
}

object GoogleAuthManager {

    private const val TAG = "GoogleAuthManager"

    // Default Web Client ID placeholder (can be overridden in resources or strings)
    private const val DEFAULT_SERVER_CLIENT_ID = "1083948572019-swift-pdf-default-client.apps.googleusercontent.com"

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            true // assume true if cannot check
        }
    }

    suspend fun signInWithGoogle(
        activity: Activity,
        serverClientId: String = DEFAULT_SERVER_CLIENT_ID
    ): GoogleAuthResult {
        val context = activity.applicationContext

        // Network connectivity check
        if (!isNetworkAvailable(context)) {
            Log.w(TAG, "Sign in failed: No internet connection")
            return GoogleAuthResult.Error(
                message = "Network error. Please check your internet connection and try again.",
                isNetworkError = true
            )
        }

        val credentialManager = CredentialManager.create(activity)

        try {
            val rawNonce = UUID.randomUUID().toString()
            val bytes = rawNonce.toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            val hashedNonce = digest.fold("") { str, it -> str + "%02x".format(it) }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .setNonce(hashedNonce)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                request = request,
                context = activity
            )

            val credential = response.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                val uid = googleIdToken.id.ifBlank { "google_${googleIdToken.id.hashCode()}" }
                val email = googleIdToken.id
                val displayName = googleIdToken.displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
                val photoUrl = googleIdToken.profilePictureUri?.toString()

                return GoogleAuthResult.Success(
                    uid = uid,
                    email = email,
                    displayName = displayName,
                    photoUrl = photoUrl,
                    idToken = googleIdToken.idToken
                )
            } else {
                Log.w(TAG, "Unexpected credential type: ${credential.type}")
                return GoogleAuthResult.RequiresFallbackPicker
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "User cancelled Google Sign-in flow")
            return GoogleAuthResult.Cancelled
        } catch (e: NoCredentialException) {
            Log.w(TAG, "No Google credentials available on device: ${e.message}")
            return GoogleAuthResult.RequiresFallbackPicker
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Credential Manager error: ${e.message}")
            return GoogleAuthResult.RequiresFallbackPicker
        } catch (e: Exception) {
            Log.e(TAG, "Unhandled error during Google sign-in", e)
            return GoogleAuthResult.RequiresFallbackPicker
        }
    }
}
