package com.ornek.yzgercekci

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** API anahtarını ve model adlarını cihazda şifreli saklar (Android Keystore). */
class SecretStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "yzg_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var textModel: String
        get() = prefs.getString(KEY_TEXT_MODEL, "").orEmpty().ifBlank { DEFAULT_TEXT_MODEL }
        set(value) = prefs.edit().putString(KEY_TEXT_MODEL, value.trim()).apply()

    var imageModel: String
        get() = prefs.getString(KEY_IMAGE_MODEL, "").orEmpty().ifBlank { DEFAULT_IMAGE_MODEL }
        set(value) = prefs.edit().putString(KEY_IMAGE_MODEL, value.trim()).apply()

    /** Sonraki karelerde önceki kareyi referans göndererek karakter/mekân tutarlılığı. */
    var consistency: Boolean
        get() = prefs.getBoolean(KEY_CONSISTENCY, true)
        set(value) = prefs.edit().putBoolean(KEY_CONSISTENCY, value).apply()

    companion object {
        private const val KEY_API = "gemini_api_key"
        private const val KEY_TEXT_MODEL = "text_model"
        private const val KEY_IMAGE_MODEL = "image_model"
        private const val KEY_CONSISTENCY = "consistency"
        const val DEFAULT_TEXT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_IMAGE_MODEL = "gemini-2.5-flash-image"
    }
}
