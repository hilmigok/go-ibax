package com.ornek.yzgercekci

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * xAI Grok Imagine Video istemcisi (ücretli, xAI API anahtarı gerekir).
 * Akış: [start] → request_id → [poll] ile durum sorgulama → bitince [download].
 * İstek/cevap biçimi tek bu sınıfta; sunucu hata mesajı kullanıcıya aynen gösterilir.
 */
class XaiVideoClient(private val apiKey: String) {

    /** Sorgulama sonucu. */
    sealed class Status {
        object Pending : Status()
        data class Done(val url: String) : Status()
    }

    data class Request(
        val model: String,
        val prompt: String,
        val portrait: Boolean,
        val durationSeconds: Int,
        val resolution: String,
        /** İlk kare olarak kullanılacak görüntü (JPEG), yoksa null. */
        val image: ByteArray? = null,
        /** Karakter referans görüntüleri (JPEG). */
        val references: List<ByteArray> = emptyList()
    )

    fun start(req: Request): String = parseStart(send(URL("$BASE/videos/generations"), buildBody(req)))

    fun poll(requestId: String): Status {
        require(ID.matches(requestId)) { "geçersiz istek kimliği" }
        return parsePoll(send(URL("$BASE/videos/$requestId"), null))
    }

    /** Videoyu indirir. Anahtar yalnızca xAI'nin kendi adresine gönderilir. */
    fun download(url: String, file: File) {
        if (!url.startsWith("https://")) throw ServiceException("Geçersiz video adresi")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            if (url.startsWith("https://api.x.ai/")) setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw ServiceException("Video indirilemedi ($code)", retryable = code >= 500)
            val tmp = File(file.path + ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(file)) throw ServiceException("Video kaydedilemedi")
        } catch (e: IOException) {
            throw ServiceException("Video indirilirken bağlantı koptu: ${e.message}", retryable = true)
        } finally {
            conn.disconnect()
        }
    }

    private fun send(url: URL, body: JSONObject?): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = if (body != null) "POST" else "GET"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = body != null
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ServiceException(errorMessage(code, text), retryable = code == 429 && !text.contains("credit", true) || code >= 500)
            return text
        } catch (e: IOException) {
            throw ServiceException("Bağlantı hatası: ${e.message ?: "internet bağlantını kontrol et"}", retryable = true)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://api.x.ai/v1"
        private val ID = Regex("^[A-Za-z0-9_-]{1,200}$")
        const val DEFAULT_MODEL = "grok-imagine-video-1.5"
        const val MAX_SECONDS = 15

        fun dataUri(jpeg: ByteArray) = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)

        fun buildBody(req: Request): JSONObject = JSONObject().apply {
            put("model", req.model.trim().ifEmpty { DEFAULT_MODEL })
            put("prompt", req.prompt)
            put("duration", req.durationSeconds.coerceIn(1, MAX_SECONDS))
            put("aspect_ratio", if (req.portrait) "9:16" else "16:9")
            // Referanslı üretim en fazla 720p.
            val res = req.resolution.trim().ifEmpty { "720p" }
            put("resolution", if (req.references.isNotEmpty() && res == "1080p") "720p" else res)
            req.image?.let { put("image", JSONObject().put("url", dataUri(it))) }
            if (req.references.isNotEmpty()) {
                val arr = JSONArray()
                req.references.take(CharacterMatcher.MAX_REFERENCES).forEach { arr.put(JSONObject().put("url", dataUri(it))) }
                put("reference_images", arr)
            }
        }

        fun parseStart(json: String): String {
            val o = try { JSONObject(json) } catch (e: JSONException) { throw ServiceException("Grok'tan anlaşılmayan cevap geldi") }
            val id = o.optString("request_id").ifEmpty { o.optString("id") }
            if (!ID.matches(id)) throw ServiceException("Grok video işini başlatamadı: ${json.take(160)}")
            return id
        }

        fun parsePoll(json: String): Status {
            val o = try { JSONObject(json) } catch (e: JSONException) { throw ServiceException("Grok'tan anlaşılmayan cevap geldi", retryable = true) }
            val url = o.optJSONObject("video")?.optString("url").orEmpty()
                .ifEmpty { o.optJSONObject("response")?.optJSONObject("video")?.optString("url").orEmpty() }
            if (url.isNotEmpty()) return Status.Done(url)
            val status = o.optString("status").lowercase()
            if (status in setOf("failed", "error", "expired", "cancelled", "canceled", "rejected")) {
                val err = o.opt("error")
                val msg = when (err) {
                    is JSONObject -> err.optString("message")
                    is String -> err
                    else -> ""
                }.ifEmpty { o.optString("message") }
                throw ServiceException("Grok bu videoyu üretemedi ($status)${if (msg.isNotEmpty()) ": ${msg.take(200)}" else ""}")
            }
            if (status == "done" || status == "completed" || status == "succeeded") {
                throw ServiceException("Grok video bitti dedi ama adres göndermedi. Tekrar dene.", retryable = true)
            }
            return Status.Pending
        }

        fun errorMessage(code: Int, body: String): String {
            val msg = try {
                val o = JSONObject(body)
                when (val e = o.opt("error")) {
                    is JSONObject -> e.optString("message")
                    is String -> e
                    else -> o.optString("message")
                }
            } catch (e: JSONException) {
                body.take(200)
            }
            val hint = when (code) {
                400 -> "Grok isteği kabul etmedi."
                401, 403 -> "xAI anahtarı geçersiz ya da yetkisiz. Ayarlar’dan kontrol et."
                402 -> "xAI hesabında kredi yok. console.x.ai’den kredi yükle."
                404 -> "Grok video modeli bulunamadı. Ayarlar’daki model adını kontrol et."
                429 -> if (body.contains("credit", true)) "xAI kredisi bitti." else "Grok istek sınırı aşıldı. Biraz bekleyip tekrar dene."
                in 500..599 -> "Grok sunucusunda geçici sorun var."
                else -> "Beklenmeyen hata."
            }
            return if (msg.isNotBlank()) "$hint ($code: ${msg.take(200)})" else "$hint ($code)"
        }
    }
}
