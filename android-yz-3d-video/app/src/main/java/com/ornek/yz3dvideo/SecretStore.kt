package com.ornek.yz3dvideo

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** API anahtarını ve model adını cihazda şifreli saklar (Android Keystore). */
class SecretStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "yz3d_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var model: String
        get() = prefs.getString(KEY_MODEL, "").orEmpty().ifBlank { DEFAULT_MODEL }
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    companion object {
        private const val KEY_API = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
