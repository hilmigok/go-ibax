package com.ornek.yzgercekci

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Freesound'dan bulunan bir kayıt. */
data class SoundHit(val id: Long, val name: String, val user: String, val previewUrl: String, val duration: Double) {
    val credit: String get() = "\"$name\" — $user (freesound.org, CC0)"
}

/**
 * Freesound.org'da gerçek ses kayıtları arar ve indirir. Yalnızca CC0 (telifsiz, atıf
 * gerektirmeyen) kayıtlar seçilir; videoyu sosyal medyada rahatça paylaşabilirsin.
 * Ücretsiz API anahtarı: https://freesound.org/apiv2/apply
 */
class FreesoundClient(private val apiKey: String) {

    fun search(query: String, limit: Int = 6): List<SoundHit> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val filter = URLEncoder.encode("license:\"Creative Commons 0\" duration:[4 TO 120]", "UTF-8")
        val fields = "id,name,username,previews,duration"
        val url = URL("https://freesound.org/apiv2/search/text/?query=$q&filter=$filter&fields=$fields&page_size=$limit")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Token ${apiKey.trim()}")
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401 || code == 403) throw IOException("Freesound anahtarı geçersiz ($code)")
            if (code !in 200..299) throw IOException("Freesound hatası ($code)")
            return parseSearch(text)
        } finally {
            conn.disconnect()
        }
    }

    /** Önizleme MP3'ünü [dir] içine indirir. */
    fun download(hit: SoundHit, dir: File): File {
        val file = File(dir, "fs_${hit.id}.mp3")
        if (file.length() > 0) return file
        val conn = (URL(hit.previewUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("Ses indirilemedi (${conn.responseCode})")
            val tmp = File(dir, "fs_${hit.id}.part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(file)) throw IOException("Ses kaydedilemedi")
            return file
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        fun parseSearch(json: String): List<SoundHit> {
            val root = try {
                JSONObject(json)
            } catch (e: JSONException) {
                return emptyList()
            }
            val arr = root.optJSONArray("results") ?: return emptyList()
            val out = ArrayList<SoundHit>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val previews = o.optJSONObject("previews") ?: continue
                val url = previews.optString("preview-hq-mp3").ifEmpty { previews.optString("preview-lq-mp3") }
                if (!url.startsWith("https://")) continue
                out += SoundHit(o.optLong("id"), o.optString("name"), o.optString("username"), url, o.optDouble("duration", 0.0))
            }
            return out
        }
    }
}
