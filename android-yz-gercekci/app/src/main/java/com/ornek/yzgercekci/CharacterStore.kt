package com.ornek.yzgercekci

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Karakter referanslarını cihazda kalıcı saklar (filesDir/characters). */
class CharacterStore(context: Context) {

    private val dir = File(context.filesDir, "characters").apply { mkdirs() }
    private val index = File(dir, "characters.json")

    fun load(): List<CharacterRef> {
        if (!index.exists()) return emptyList()
        return try {
            val arr = JSONArray(index.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val ref = CharacterRef(o.optString("id"), o.optString("name"), o.optString("image"))
                ref.takeIf { File(it.imagePath).exists() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(name: String, image: Bitmap): CharacterRef {
        val id = "c${System.currentTimeMillis()}"
        val file = File(dir, "$id.jpg")
        val scaled = ImageSaver.scaleDown(image, 1024)
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (scaled !== image) scaled.recycle()
        val ref = CharacterRef(id, name.trim().take(40), file.path)
        save(load() + ref)
        return ref
    }

    fun rename(id: String, name: String) = save(load().map { if (it.id == id) it.copy(name = name.trim().take(40)) else it })

    fun remove(id: String) {
        val list = load()
        list.firstOrNull { it.id == id }?.let { File(it.imagePath).delete() }
        save(list.filter { it.id != id })
    }

    private fun save(list: List<CharacterRef>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("image", it.imagePath)) }
        index.writeText(arr.toString())
    }
}
