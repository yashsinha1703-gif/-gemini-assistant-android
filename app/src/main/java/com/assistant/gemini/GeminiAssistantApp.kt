package com.assistant.gemini

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class GeminiAssistantApp : Application() {

    companion object {
        lateinit var instance: GeminiAssistantApp
            private set

        private const val PREFS_NAME = "gemini_assistant_prefs"
        private const val KEY_API_KEY = "key_gemini_api"
        private const val KEY_USER_EMAIL = "key_user_email"
        private const val KEY_USER_NAME = "key_user_name"
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var geminiApiKey: String
        get() {
            val saved = prefs.getString(KEY_API_KEY, "") ?: ""
            return if (saved.isNotBlank()) saved else BuildConfig.GEMINI_API_KEY
        }
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value.trim()).apply()
        }

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()
}
