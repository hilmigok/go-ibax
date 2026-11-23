package com.ornek.yzgercekci

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

class GeminiException(
    message: String,
    val retryable: Boolean = false,
    /** HTTP durum kodu (ağ dışı hatalarda 0). */
    val code: Int = 0,
    /** Google'ın hata mesajında önerdiği yeni model adı (varsa). */
    val suggestedModel: String? = null
) : Exception(message) {
    /** Model artık yok / bu kullanıcıya kapalı mı? */
    val modelUnavailable: Boolean
        get() = code == 404 || (code in 400..403 && (message.orEmpty().contains("no longer available", true) ||
            message.orEmpty().contains("is not found", true) || message.orEmpty().contains("not supported", true)))
}

/** Google'ın sunduğu bir model ve desteklediği yöntemler. */
data class ModelInfo(val name: String, val methods: Set<String>)

/**
 * Google Gemini REST API istemcisi.
 *  - [generateStoryboard]: metin modeli (ör. gemini-2.5-flash) ile senaryo JSON'u yazar.
 *  - [generateImage]: görüntü modeli ile fotoğraf gerçekliğinde bir kare üretir.
 *    "gemini-…-image" modelleri generateContent ile, "imagen-…" modelleri predict ile çağrılır.
 * Ağ çağrısı yapar; ana iş parçacığında çağırmayın.
 */
class GeminiClient(private val apiKey: String) {

    fun generateStoryboard(model: String, userPrompt: String): String {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", StoryboardPrompt.SYSTEM))))
            put("contents", JSONArray().put(userContent(JSONArray().put(JSONObject().put("text", userPrompt)))))
            put(
                "generationConfig", JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("temperature", 0.9)
            )
        }
        val text = post(endpoint(model, "generateContent"), body)
        return extractText(text)
    }

    /**
     * @param reference önceki kare (JPEG baytları); karakter/mekân tutarlılığı için. Yalnızca
     *                  Gemini görüntü modelleri kullanır, Imagen yok sayar.
     * @return üretilen görüntünün baytları (PNG/JPEG)
     */
    fun generateImage(model: String, prompt: String, portrait: Boolean, reference: ByteArray? = null): ByteArray {
        val aspect = if (portrait) "9:16" else "16:9"
        return if (isImagen(model)) {
            val body = JSONObject().apply {
                put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
                put(
                    "parameters", JSONObject()
                        .put("sampleCount", 1)
                        .put("aspectRatio", aspect)
                        .put("personGeneration", "allow_adult")
                )
            }
            extractImagenImage(post(endpoint(model, "predict"), body))
        } else {
            val parts = JSONArray()
            if (reference != null) {
                parts.put(
                    JSONObject().put(
                        "inlineData", JSONObject()
                            .put("mimeType", "image/jpeg")
                            .put("data", Base64.getEncoder().encodeToString(reference))
                    )
                )
                parts.put(JSONObject().put("text", StoryboardPrompt.REFERENCE_HINT + prompt))
            } else {
                parts.put(JSONObject().put("text", prompt))
            }
            val body = JSONObject().apply {
                put("contents", JSONArray().put(userContent(parts)))
                put(
                    "generationConfig", JSONObject()
                        .put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
                        .put("imageConfig", JSONObject().put("aspectRatio", aspect))
                )
            }
            extractGeminiImage(post(endpoint(model, "generateContent"), body))
        }
    }

    /**
     * Gemini'nin gerçekçi sesiyle Türkçe anlatım üretir.
     * @return mono PCM, -1..1, [Soundtrack.SR] Hz
     */
    fun generateSpeech(model: String, voice: String, text: String): FloatArray {
        val instruction = "Aşağıdaki cümleyi duygulu, yavaş, alçak ve etkileyici bir belgesel anlatıcısı " +
            "gibi Türkçe oku: "
        val body = JSONObject().apply {
            put("contents", JSONArray().put(userContent(JSONArray().put(JSONObject().put("text", instruction + text)))))
            put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig", JSONObject().put(
                            "voiceConfig", JSONObject().put(
                                "prebuiltVoiceConfig", JSONObject().put("voiceName", voice.trim().ifEmpty { "Charon" })
                            )
                        )
                    )
            )
        }
        val (pcm, rate) = extractAudio(post(endpoint(model, "generateContent"), body))
        return Soundtrack.resample(Soundtrack.pcm16ToFloat(pcm), rate)
    }

    /** Bu API anahtarıyla kullanılabilen modelleri listeler. */
    fun listModels(): List<ModelInfo> {
        val out = ArrayList<ModelInfo>()
        var token = ""
        repeat(5) {
            val q = if (token.isEmpty()) "" else "&pageToken=" + URLEncoder.encode(token, "UTF-8")
            val conn = (URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000$q").openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }
            try {
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw GeminiException(errorMessage(code, text), code = code)
                out += parseModels(text)
                token = try { JSONObject(text).optString("nextPageToken") } catch (e: JSONException) { "" }
            } catch (e: IOException) {
                throw GeminiException("Bağlantı hatası: ${e.message ?: "internet bağlantını kontrol et"}", retryable = true)
            } finally {
                conn.disconnect()
            }
            if (token.isEmpty()) return out
        }
        return out
    }

    private fun userContent(parts: JSONArray) = JSONObject().put("role", "user").put("parts", parts)

    private fun endpoint(model: String, method: String): URL {
        val path = URLEncoder.encode(model.trim(), "UTF-8")
        return URL("https://generativelanguage.googleapis.com/v1beta/models/$path:$method")
    }

    private fun post(url: URL, body: JSONObject): String {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            // Anahtar URL'de değil başlıkta gider; loglara/URL geçmişine düşmez.
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw GeminiException(
                errorMessage(code, text), retryable = code == 429 || code >= 500,
                code = code, suggestedModel = suggestedModel(text)
            )
            return text
        } catch (e: IOException) {
            throw GeminiException("Bağlantı hatası: ${e.message ?: "internet bağlantını kontrol et"}", retryable = true)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** "...Please update your code to use models/gemini-x-flash..." → "gemini-x-flash" */
        fun suggestedModel(body: String): String? {
            val msg = try {
                JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            } catch (e: JSONException) {
                body
            }
            return Regex("use (?:the )?(?:model )?models/([A-Za-z0-9._-]+)").find(msg)?.groupValues?.get(1)
                ?.trimEnd('.', '-')?.takeIf { it.isNotEmpty() }
        }

        fun parseModels(json: String): List<ModelInfo> {
            val arr = try {
                JSONObject(json).optJSONArray("models")
            } catch (e: JSONException) {
                null
            } ?: return emptyList()
            val out = ArrayList<ModelInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").removePrefix("models/")
                if (name.isEmpty()) continue
                val m = o.optJSONArray("supportedGenerationMethods")
                val methods = HashSet<String>()
                if (m != null) for (k in 0 until m.length()) methods += m.optString(k)
                out += ModelInfo(name, methods)
            }
            return out
        }

        fun isImagen(model: String) = model.trim().lowercase().startsWith("imagen")

        private fun parseRoot(responseJson: String): JSONObject = try {
            JSONObject(responseJson)
        } catch (e: JSONException) {
            throw GeminiException("Gemini'den anlaşılmayan bir cevap geldi")
        }

        private fun checkBlocked(root: JSONObject) {
            root.optJSONObject("promptFeedback")?.optString("blockReason")?.takeIf { it.isNotEmpty() }?.let {
                throw GeminiException("İstek güvenlik filtresine takıldı ($it). Tarifi yumuşatıp tekrar dene.")
            }
        }

        /** Gemini cevabından modelin yazdığı metni çıkarır. */
        fun extractText(responseJson: String): String {
            val root = parseRoot(responseJson)
            checkBlocked(root)
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw GeminiException("Gemini bir senaryo üretmedi. Tekrar dene.", retryable = true)
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
            }
            val text = stripFences(sb.toString())
            if (text.isEmpty()) {
                val reason = candidate.optString("finishReason", "")
                throw GeminiException("Gemini boş cevap döndürdü${if (reason.isNotEmpty()) " ($reason)" else ""}.", retryable = true)
            }
            return text
        }

        /** generateContent cevabındaki ilk görüntüyü (inlineData) çözer. */
        fun extractGeminiImage(responseJson: String): ByteArray {
            val root = parseRoot(responseJson)
            checkBlocked(root)
            val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
                ?: throw GeminiException("Görüntü üretilmedi. Tekrar dene.", retryable = true)
            val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
            var modelText = ""
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i) ?: continue
                    val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data")
                    val data = inline?.optString("data").orEmpty()
                    if (data.isNotEmpty()) return decode(data)
                    if (modelText.isEmpty()) modelText = p.optString("text").trim()
                }
            }
            val reason = candidate.optString("finishReason", "")
            val msg = when {
                reason.contains("SAFETY") || reason.contains("PROHIBITED") || reason.contains("BLOCK") ->
                    "Bu kare güvenlik filtresine takıldı ($reason). Tarifi yumuşatıp tekrar dene."
                modelText.isNotEmpty() -> "Model görüntü yerine yazı döndürdü: ${modelText.take(160)}"
                else -> "Görüntü üretilmedi${if (reason.isNotEmpty()) " ($reason)" else ""}. Tekrar dene."
            }
            throw GeminiException(msg, retryable = modelText.isNotEmpty() || reason.isEmpty())
        }

        /** TTS cevabındaki ham PCM'i ve örnekleme hızını çıkarır (mimeType: audio/L16;codec=pcm;rate=24000). */
        fun extractAudio(responseJson: String): Pair<ByteArray, Int> {
            val root = parseRoot(responseJson)
            checkBlocked(root)
            val parts = root.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i) ?: continue
                    val inline = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data") ?: continue
                    val data = inline.optString("data")
                    if (data.isEmpty()) continue
                    val mime = inline.optString("mimeType").ifEmpty { inline.optString("mime_type") }
                    val rate = Regex("rate=(\\d+)").find(mime)?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                    return decode(data) to rate
                }
            }
            throw GeminiException("Anlatıcı sesi üretilemedi.", retryable = true)
        }

        /** Imagen predict cevabındaki ilk görüntüyü çözer. */
        fun extractImagenImage(responseJson: String): ByteArray {
            val root = parseRoot(responseJson)
            val pred = root.optJSONArray("predictions")?.optJSONObject(0)
            val data = pred?.optString("bytesBase64Encoded").orEmpty()
            if (data.isNotEmpty()) return decode(data)
            val reason = pred?.optString("raiFilteredReason").orEmpty()
            throw GeminiException(
                if (reason.isNotEmpty()) "Imagen bu kareyi filtreledi: ${reason.take(160)}"
                else "Imagen görüntü döndürmedi (büyük olasılıkla güvenlik filtresi). Tarifi değiştirip tekrar dene."
            )
        }

        private fun decode(data: String): ByteArray = try {
            Base64.getMimeDecoder().decode(data)
        } catch (e: IllegalArgumentException) {
            throw GeminiException("Görüntü verisi bozuk geldi. Tekrar dene.", retryable = true)
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
            val needsBilling = msg.contains("bill", true) || msg.contains("paid", true)
            val hint = when {
                needsBilling && code in 400..403 -> "Bu görüntü modeli faturalandırma (ücretli plan) gerektiriyor."
                else -> when (code) {
                    400 -> "İstek geçersiz (model adı yanlış olabilir)."
                    401, 403 -> "API anahtarı geçersiz ya da yetkisiz. Ayarlar’dan kontrol et."
                    404 -> "Model bulunamadı. Ayarlar’daki model adını kontrol et."
                    429 -> "Kota/istek sınırı aşıldı. Görüntü modelleri genelde ücretli kota ister; biraz bekleyip tekrar dene."
                    in 500..599 -> "Gemini sunucusunda geçici bir sorun var. Tekrar dene."
                    else -> "Beklenmeyen hata."
                }
            }
            return if (msg.isNotEmpty()) "$hint ($code: ${msg.take(200)})" else "$hint ($code)"
        }
    }
}
