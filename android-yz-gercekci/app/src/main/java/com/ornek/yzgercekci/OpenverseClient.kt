package com.ornek.yzgercekci

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Openverse'te bulunan bir ses kaydı. */
data class OpenverseHit(val id: String, val title: String, val creator: String, val url: String, val source: String) {
    val credit: String get() = "\"$title\" — $creator ($source, ${"CC0 / kamu malı"})"
}

/**
 * Openverse (openverse.org) — Freesound, Wikimedia, Jamendo gibi kaynaklardaki açık lisanslı
 * kayıtları tarayan ücretsiz arama motoru. ANAHTAR GEREKTİRMEZ. Yalnızca CC0 ve kamu malı
 * (public domain) kayıtlar seçilir; atıf ya da telif sorunu yoktur.
 */
class OpenverseClient {

    fun search(query: String, limit: Int = 8): List<OpenverseHit> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL("https://api.openverse.org/v1/audio/?q=$q&license=cc0,pdm&page_size=$limit&mature=false")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "YZGercekciSahne/2 (Android)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("Openverse hatası ($code)")
            return parseSearch(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    fun download(hit: OpenverseHit, dir: File): File {
        val ext = hit.url.substringAfterLast('.', "mp3").substringBefore('?').take(4).lowercase()
            .takeIf { it in setOf("mp3", "ogg", "wav", "flac", "m4a", "oga") } ?: "mp3"
        val safeId = hit.id.filter { it.isLetterOrDigit() || it == '-' }.take(40)
        val file = File(dir, "ov_$safeId.$ext")
        if (file.length() > 0) return file
        val conn = (URL(hit.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "YZGercekciSahne/2 (Android)")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("Ses indirilemedi (${conn.responseCode})")
            val len = conn.contentLengthLong
            if (len > MAX_BYTES) throw IOException("Ses dosyası çok büyük")
            val tmp = File(dir, "ov_$safeId.part")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("Ses dosyası çok büyük")
                        out.write(buf, 0, n)
                    }
                }
            }
            if (!tmp.renameTo(file)) throw IOException("Ses kaydedilemedi")
            return file
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val MAX_BYTES = 15L * 1024 * 1024

        fun parseSearch(json: String): List<OpenverseHit> {
            val arr = try {
                JSONObject(json).optJSONArray("results")
            } catch (e: JSONException) {
                null
            } ?: return emptyList()
            val out = ArrayList<OpenverseHit>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val license = o.optString("license").lowercase()
                if (license != "cc0" && license != "pdm") continue
                val url = o.optString("url")
                if (!url.startsWith("https://")) continue
                // Süre milisaniye; çok kısa (efekt patlaması) ya da çok uzun kayıtları atla.
                val ms = o.optLong("duration", 0L)
                if (ms in 1 until 3_000 || ms > 600_000) continue
                out += OpenverseHit(
                    o.optString("id"), o.optString("title").take(80), o.optString("creator").take(60),
                    url, o.optString("source").ifEmpty { "openverse" }
                )
            }
            return out
        }
    }
}
