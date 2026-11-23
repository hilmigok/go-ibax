package com.ornek.yzgercekci

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Bir çevrim içi hizmetin hatası; [retryable] ise biraz bekleyip tekrar denenebilir. */
class ServiceException(message: String, val retryable: Boolean = false) : Exception(message)

/**
 * Pollinations.ai — ücretsiz, ANAHTAR GEREKTİRMEYEN görüntü üretimi (varsayılan model: Flux).
 * Tarif adrese yazılır, cevap doğrudan JPEG görüntüdür. Anonim kullanımda istekler sıraya
 * alınır; çok sık istek atılırsa 429 döner, bu yüzden kareler tek tek ve aralıklı üretilir.
 */
class PollinationsClient(private val model: String) {

    fun generate(prompt: String, width: Int, height: Int, seed: Int): ByteArray {
        val url = buildUrl(prompt, width, height, seed, model)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 180_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "YZGercekciSahne/3 (Android)")
            setRequestProperty("Accept", "image/*")
        }
        try {
            val code = conn.responseCode
            if (code == 429) throw ServiceException("Ücretsiz görsel sunucusu yoğun (429). Biraz bekleyip tekrar deneniyor…", retryable = true)
            if (code in 500..599) throw ServiceException("Görsel sunucusunda geçici sorun ($code).", retryable = true)
            if (code !in 200..299) throw ServiceException("Görsel üretilemedi ($code). Tarifi değiştirip tekrar dene.")
            val type = conn.contentType.orEmpty()
            val bytes = conn.inputStream.use { it.readBytes() }
            if (!type.startsWith("image/") || bytes.size < 1000) {
                throw ServiceException("Görsel sunucusu görüntü yerine başka bir cevap döndürdü. Tekrar dene.", retryable = true)
            }
            return bytes
        } catch (e: IOException) {
            throw ServiceException("Bağlantı hatası: ${e.message ?: "internet bağlantını kontrol et"}", retryable = true)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_MODEL = "flux"
        private const val MAX_PROMPT = 1400

        fun buildUrl(prompt: String, width: Int, height: Int, seed: Int, model: String): String {
            // Yol içinde '+' boşluk sayılmaz; boşluklar %20 olmalı.
            val p = URLEncoder.encode(prompt.trim().take(MAX_PROMPT), "UTF-8").replace("+", "%20")
            val m = URLEncoder.encode(model.trim().ifEmpty { DEFAULT_MODEL }, "UTF-8")
            return "https://image.pollinations.ai/prompt/$p?width=$width&height=$height&seed=$seed" +
                "&model=$m&nologo=true&private=true&enhance=false&safe=false"
        }

        /** Seçilen yöne uygun görüntü boyutu (8'in katı). */
        fun size(portrait: Boolean): Pair<Int, Int> = if (portrait) 768 to 1344 else 1344 to 768
    }
}
