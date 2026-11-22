package com.ornek.yz3dvideo

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class GeminiException(message: String) : Exception(message)

/**
 * Google Gemini REST API'sine (generateContent) istek atar ve modelin
 * ürettiği sahne JSON metnini döndürür. Ağ çağrısı yaptığı için ana
 * iş parçacığında değil, arka planda çağrılmalıdır.
 */
class GeminiClient(private val apiKey: String, private val model: String) {

    fun generateScene(userPrompt: String): String {
        val modelPath = URLEncoder.encode(model.trim(), "UTF-8")
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelPath:generateContent")

        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", ScenePrompt.SYSTEM))))
            put(
                "contents", JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
                )
            )
            put(
                "generationConfig", JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.8)
            )
        }

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // Anahtar URL'de değil başlıkta gider; böylece loglara/URL geçmişine düşmez.
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw GeminiException(errorMessage(code, text))
            return extractText(text)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Gemini cevabından modelin yazdığı metni çıkarır. */
        fun extractText(responseJson: String): String {
            val root = try {
                JSONObject(responseJson)
            } catch (e: JSONException) {
                throw GeminiException("Gemini'den anlaşılmayan bir cevap geldi")
            }
            root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
                throw GeminiException("Gemini bu isteği reddetti ($it). Metni değiştirip tekrar dene.")
            }
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw GeminiException("Gemini bir sahne üretmedi. Tekrar dene.")
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
            val text = stripFences(sb.toString())
            if (text.isEmpty()) {
                val reason = candidate.optString("finishReason", "")
                throw GeminiException("Gemini boş cevap döndürdü${if (reason.isNotEmpty()) " ($reason)" else ""}.")
            }
            return text
        }

        /** Model JSON'u ```json ... ``` bloğuna sarmışsa sarmalayıcıyı kaldırır. */
        fun stripFences(s: String): String {
            var t = s.trim()
            if (t.startsWith("```")) {
                t = t.substringAfter('\n', "")
                t = t.substringBeforeLast("```").trim()
            }
            return t
        }

        fun errorMessage(code: Int, body: String): String {
            val msg = try {
                JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            } catch (e: JSONException) {
                ""
            }
            val hint = when (code) {
                400 -> "İstek geçersiz (model adı yanlış olabilir)."
                401, 403 -> "API anahtarı geçersiz ya da yetkisiz. Ayarlar’dan kontrol et."
                404 -> "Model bulunamadı. Ayarlar’daki model adını kontrol et."
                429 -> "Kota/istek sınırı aşıldı. Biraz bekleyip tekrar dene."
                in 500..599 -> "Gemini sunucusunda geçici bir sorun var. Tekrar dene."
                else -> "Beklenmeyen hata."
            }
            return if (msg.isNotEmpty()) "$hint ($code: ${msg.take(200)})" else "$hint ($code)"
        }
    }
}
