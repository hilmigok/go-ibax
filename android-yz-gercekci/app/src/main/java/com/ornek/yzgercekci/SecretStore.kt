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

    /** xAI (Grok) API anahtarı — gerçek video için, ücretli. */
    var xaiKey: String
        get() = prefs.getString(KEY_XAI, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_XAI, value.trim()).apply()

    var xaiModel: String
        get() = prefs.getString(KEY_XAI_MODEL, "").orEmpty().ifBlank { XaiVideoClient.DEFAULT_MODEL }
        set(value) = prefs.edit().putString(KEY_XAI_MODEL, value.trim()).apply()

    var xaiResolution: String
        get() = prefs.getString(KEY_XAI_RES, "").orEmpty().ifBlank { "720p" }
        set(value) = prefs.edit().putString(KEY_XAI_RES, value.trim()).apply()

    /** Grok klip süresi (sn), varsayılan en uzun: 15. */
    var clipSeconds: Int
        get() = prefs.getInt(KEY_CLIP_SECONDS, XaiVideoClient.MAX_SECONDS).coerceIn(1, XaiVideoClient.MAX_SECONDS)
        set(value) = prefs.edit().putInt(KEY_CLIP_SECONDS, value.coerceIn(1, XaiVideoClient.MAX_SECONDS)).apply()

    /** Ana ekrandaki "Grok ile gerçek video" düğmesinin son durumu. */
    var grokEnabled: Boolean
        get() = prefs.getBoolean(KEY_GROK_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_GROK_ON, value).apply()

    companion object {
        private const val KEY_XAI = "xai_key"
        private const val KEY_XAI_MODEL = "xai_model"
        private const val KEY_XAI_RES = "xai_resolution"
        private const val KEY_CLIP_SECONDS = "clip_seconds"
        private const val KEY_GROK_ON = "grok_enabled"
        private const val KEY_FREESOUND = "freesound_key"
        private const val KEY_POLLINATIONS_MODEL = "pollinations_model"
    }
}
