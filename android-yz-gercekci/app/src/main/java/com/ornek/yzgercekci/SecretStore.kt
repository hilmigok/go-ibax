package com.ornek.yzgercekci

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** Ayarları cihazda şifreli saklar (Android Keystore). Hiçbir anahtar zorunlu değildir. */
class SecretStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "yzg_secure_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Freesound.org API anahtarı (isteğe bağlı; yoksa Openverse kullanılır). */
    var freesoundKey: String
        get() = prefs.getString(KEY_FREESOUND, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_FREESOUND, value.trim()).apply()

    /** Pollinations görüntü modeli. */
    var imageModel: String
        get() = prefs.getString(KEY_POLLINATIONS_MODEL, "").orEmpty().ifBlank { PollinationsClient.DEFAULT_MODEL }
        set(value) = prefs.edit().putString(KEY_POLLINATIONS_MODEL, value.trim()).apply()

    companion object {
        private const val KEY_FREESOUND = "freesound_key"
        private const val KEY_POLLINATIONS_MODEL = "pollinations_model"
    }
}
