package com.voicetext.transcribe

import android.content.Context

/** Persists the user's OpenAI API key in app-private SharedPreferences. */
class ApiKeyStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String?
        get() = prefs.getString(KEY_OPENAI_API_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            val trimmed = value?.trim()
            if (trimmed.isNullOrEmpty()) {
                prefs.edit().remove(KEY_OPENAI_API_KEY).apply()
            } else {
                prefs.edit().putString(KEY_OPENAI_API_KEY, trimmed).apply()
            }
        }

    /** The saved key with only its prefix and last 4 characters visible, e.g. "sk-…abcd". */
    fun maskedKey(): String? = apiKey?.let { key ->
        if (key.length <= 8) "••••" else key.take(3) + "…" + key.takeLast(4)
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_OPENAI_API_KEY = "openai_api_key"
    }
}
