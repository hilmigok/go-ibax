package com.ornek.yz3dvideo

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SceneParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * YZ'nin döndürdüğü JSON'u [Scene3D]'ye çevirir.
 *
 * YZ çıktısı güvenilir kabul edilmez: eksik alanlar varsayılanla doldurulur,
 * tüm sayılar makul aralıklara sıkıştırılır ve nesne/parça sayıları sınırlanır.
 * Böylece hatalı ya da aşırı büyük bir cevap uygulamayı çökertmez veya kilitlemez.
 */
object SceneParser {

    const val MAX_OBJECTS = 40
    const val MAX_PARTS = 24
    const val MAX_ANIMS = 8
    const val MAX_CAPTIONS = 10
    const val MAX_CAPTION_LEN = 120
    private const val MAX_COORD = 100f

    fun parse(json: String): Scene3D {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw SceneParseException("YZ geçerli bir sahne JSON'u döndürmedi", e)
        }

        val duration = num(root, "duration", 8f, 2f, 20f)

        val sky = root.optJSONObject("sky")
        val ground = root.optJSONObject("ground")
        val light = root.optJSONObject("light")
        val camera = root.optJSONObject("camera")

        val cameraStart = cameraKey(camera?.optJSONObject("start"), CameraKey(Vec3(0f, 4f, 14f), Vec3(0f, 1f, 0f)))
        val cameraEnd = cameraKey(camera?.optJSONObject("end"), cameraStart)

        val objects = ArrayList<SceneObject>()
        root.optJSONArray("objects")?.let { arr ->
            for (i in 0 until minOf(arr.length(), MAX_OBJECTS)) {
                val o = arr.optJSONObject(i) ?: continue
                parseObject(o, i, duration)?.let(objects::add)
            }
        }
        if (objects.isEmpty()) throw SceneParseException("Sahnede çizilecek nesne yok")

        val captions = ArrayList<Caption>()
        root.optJSONArray("captions")?.let { arr ->
            for (i in 0 until minOf(arr.length(), MAX_CAPTIONS)) {
                val c = arr.optJSONObject(i) ?: continue
                val text = c.optString("text", "").trim().take(MAX_CAPTION_LEN)
                if (text.isEmpty()) continue
                val start = num(c, "start", 0f, 0f, duration)
                val end = num(c, "end", duration, start, duration)
                captions.add(Caption(text, start, end))
            }
        }

        return Scene3D(
            title = root.optString("title", "").trim().take(80).ifEmpty { "3D sahne" },
            duration = duration,
            skyTop = color(sky?.opt("top"), Rgb(0.23f, 0.48f, 0.84f)),
            skyBottom = color(sky?.opt("bottom"), Rgb(0.75f, 0.89f, 0.97f)),
            groundColor = color(ground?.opt("color"), Rgb(0.36f, 0.68f, 0.36f)),
            groundVisible = ground?.optBoolean("visible", true) ?: true,
            lightDir = vec(light?.optJSONArray("direction"), Vec3(-0.4f, -1f, -0.3f), 1f)
                .normalizedOr(Vec3(0f, -1f, 0f)),
            ambient = light?.let { num(it, "ambient", 0.35f, 0f, 1f) } ?: 0.35f,
            cameraStart = cameraStart,
            cameraEnd = cameraEnd,
            objects = objects,
            captions = captions
        )
    }

    private fun parseObject(o: JSONObject, index: Int, duration: Float): SceneObject? {
        val parts = ArrayList<Part>()
        o.optJSONArray("parts")?.let { arr ->
            for (i in 0 until minOf(arr.length(), MAX_PARTS)) {
                val p = arr.optJSONObject(i) ?: continue
                val shape = shape(p.optString("shape", "")) ?: continue
                parts.add(
                    Part(
                        shape = shape,
                        color = color(p.opt("color"), Rgb(0.8f, 0.8f, 0.8f)),
                        position = vec(p.optJSONArray("position"), Vec3.ZERO, MAX_COORD),
                        scale = scaleVec(p.optJSONArray("scale")),
                        rotation = vec(p.optJSONArray("rotation"), Vec3.ZERO, 360f)
                    )
                )
            }
        }
        if (parts.isEmpty()) return null

        val anims = ArrayList<Anim>()
        o.optJSONArray("animations")?.let { arr ->
            for (i in 0 until minOf(arr.length(), MAX_ANIMS)) {
                arr.optJSONObject(i)?.let { a -> parseAnim(a, duration)?.let(anims::add) }
            }
        }

        return SceneObject(
            id = o.optString("id", "").take(40).ifEmpty { "nesne$index" },
            parts = parts,
            position = vec(o.optJSONArray("position"), Vec3.ZERO, MAX_COORD),
            rotation = vec(o.optJSONArray("rotation"), Vec3.ZERO, 360f),
            scale = num(o, "scale", 1f, 0.05f, 20f),
            anims = anims
        )
    }

    private fun parseAnim(a: JSONObject, duration: Float): Anim? {
        val start = num(a, "start", 0f, 0f, duration)
        val end = num(a, "end", duration, start, duration)
        return when (a.optString("type", "").lowercase()) {
            "move" -> Anim.Move(vec(a.optJSONArray("to"), Vec3.ZERO, MAX_COORD), start, end)
            "rotate" -> Anim.Rotate(
                vec(a.optJSONArray("axis"), Vec3.UP, 1f).normalizedOr(Vec3.UP),
                num(a, "speed", 90f, -720f, 720f), start, end
            )
            "bounce" -> Anim.Bounce(num(a, "height", 1f, 0f, 20f), num(a, "speed", 1f, 0.05f, 10f), start, end)
            "scale" -> Anim.Scale(num(a, "to", 1f, 0.05f, 20f), start, end)
            "orbit" -> Anim.Orbit(
                vec(a.optJSONArray("center"), Vec3.ZERO, MAX_COORD),
                num(a, "radius", 3f, 0f, 50f),
                num(a, "speed", 45f, -720f, 720f), start, end
            )
            else -> null
        }
    }

    private fun cameraKey(o: JSONObject?, fallback: CameraKey): CameraKey {
        if (o == null) return fallback
        val pos = vec(o.optJSONArray("position"), fallback.position, MAX_COORD)
        var target = vec(o.optJSONArray("target"), fallback.target, MAX_COORD)
        // Kamera hedefiyle aynı noktadaysa bakış yönü tanımsız olur.
        if ((target - pos).length() < 0.01f) target = pos + Vec3(0f, 0f, -1f)
        return CameraKey(pos, target)
    }

    fun shape(name: String): Shape? = when (name.trim().lowercase()) {
        "cube", "box" -> Shape.CUBE
        "sphere", "ball" -> Shape.SPHERE
        "cylinder" -> Shape.CYLINDER
        "cone" -> Shape.CONE
        "plane" -> Shape.PLANE
        else -> null
    }

    /** "#RRGGBB" biçimini okur; geçersizse [fallback]. */
    fun color(value: Any?, fallback: Rgb): Rgb {
        val s = (value as? String)?.trim()?.removePrefix("#") ?: return fallback
        if (s.length != 6) return fallback
        val n = s.toLongOrNull(16) ?: return fallback
        return Rgb(((n shr 16) and 0xFF) / 255f, ((n shr 8) and 0xFF) / 255f, (n and 0xFF) / 255f)
    }

    private fun num(o: JSONObject, key: String, default: Float, min: Float, max: Float): Float {
        val d = o.optDouble(key, default.toDouble())
        if (d.isNaN() || d.isInfinite()) return default.coerceIn(min, max)
        return d.toFloat().coerceIn(min, max)
    }

    private fun vec(arr: JSONArray?, fallback: Vec3, limit: Float): Vec3 {
        if (arr == null || arr.length() < 3) return fallback
        fun c(i: Int, f: Float): Float {
            val d = arr.optDouble(i, f.toDouble())
            return if (d.isNaN() || d.isInfinite()) f else d.toFloat().coerceIn(-limit, limit)
        }
        return Vec3(c(0, fallback.x), c(1, fallback.y), c(2, fallback.z))
    }

    private fun scaleVec(arr: JSONArray?): Vec3 {
        val v = vec(arr, Vec3.ONE, 400f)
        fun s(f: Float) = f.coerceIn(0.01f, 400f)
        return Vec3(s(v.x), s(v.y), s(v.z))
    }
}
