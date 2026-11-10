package com.assistant.gemini.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.assistant.gemini.GeminiAssistantApp
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GoogleAuthManager {

    /**
     * Executes Google Sign-in flow using Credential Manager.
     * @param serverClientId Web Client ID from Google Cloud Console (can be passed or set in strings)
     */
    suspend fun signInWithGoogle(
        context: Context,
        serverClientId: String = "YOUR_SERVER_CLIENT_ID_OPTIONAL.apps.googleusercontent.com"
    ): Result<String> = withContext(Dispatchers.IO) {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        try {
            val response = credentialManager.getCredential(
                request = request,
                context = context
            )
            val credential = response.credential
            if (credential is androidx.credentials.CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdToken.id
                val displayName = googleIdToken.displayName ?: email

                GeminiAssistantApp.instance.userEmail = email
                GeminiAssistantApp.instance.userName = displayName

                Result.success(displayName)
            } else {
                Result.failure(Exception("Unsupported credential type returned."))
            }
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        GeminiAssistantApp.instance.userEmail = null
        GeminiAssistantApp.instance.userName = null
    }

    val isSignedIn: Boolean
        get() = !GeminiAssistantApp.instance.userEmail.isNullOrBlank()

    val currentUserInfo: String
        get() = GeminiAssistantApp.instance.userName ?: GeminiAssistantApp.instance.userEmail ?: "Not signed in"
}
