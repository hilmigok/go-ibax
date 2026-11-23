package com.ornek.yzgercekci

import org.json.JSONException
import org.json.JSONObject

class StoryboardException(message: String) : Exception(message)

/** Gemini'nin yazdığı senaryo JSON'unu doğrular ve güvenli sınırlara çeker. */
object StoryboardParser {

    const val MAX_SHOTS = 12
    const val MIN_DURATION = 2.5f
    const val MAX_DURATION = 8f

    fun parse(json: String): Storyboard {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw StoryboardException("Senaryo okunamadı. Tekrar dene.")
        }
        val arr = root.optJSONArray("shots") ?: throw StoryboardException("Senaryoda hiç sahne yok. Tekrar dene.")
        val shots = ArrayList<Shot>()
        for (i in 0 until arr.length()) {
            if (shots.size >= MAX_SHOTS) break
            val o = arr.optJSONObject(i) ?: continue
            val prompt = o.optString("prompt").trim()
            if (prompt.isEmpty()) continue
            val d = o.optDouble("duration", 4.0).toFloat()
            shots += Shot(
                prompt = prompt.take(2000),
                caption = o.optString("caption").trim().take(80),
                camera = CameraMove.from(o.optString("camera")),
                effect = Effect.from(o.optString("effect")),
                duration = if (d.isNaN()) 4f else d.coerceIn(MIN_DURATION, MAX_DURATION),
                expression = Expression.from(o.optString("expression")),
                sound = o.optString("sound").trim().take(60),
                ambience = Ambience.from(o.optString("ambience")),
                narration = o.optString("narration").trim().take(300),
                motion = o.optString("motion").trim().take(600),
                dialogue = parseDialogue(o.optJSONArray("dialogue"))
            )
        }
        if (shots.isEmpty()) throw StoryboardException("Senaryoda geçerli sahne yok. Tekrar dene.")
        val title = root.optString("title").trim().take(48).ifEmpty { "Sahne" }
        val chars = ArrayList<String>()
        root.optJSONArray("characters")?.let { arr ->
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i)?.optString("name") ?: arr.optString(i)
                if (!c.isNullOrBlank()) chars += c.trim().take(40)
            }
        }
        return Storyboard(title, shots, Music.from(root.optString("music")), chars)
    }

    private fun parseDialogue(arr: org.json.JSONArray?): List<Line> {
        if (arr == null) return emptyList()
        val out = ArrayList<Line>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = o.optString("line").ifEmpty { o.optString("text") }.trim()
            if (text.isEmpty()) continue
            out += Line(o.optString("character").trim().take(40), text.take(300))
        }
        return out
    }
}
