package com.ornek.yzgercekci

/**
 * Google modelleri sık sık emekliye ayırıyor. Bir model artık yoksa, API anahtarıyla
 * kullanılabilen modeller arasından iş türüne en uygun olanı seçer. Saf hesaplama.
 */
object ModelPicker {

    enum class Kind { TEXT, IMAGE, TTS }

    private val VERSION = Regex("^(?:gemini|imagen)-(\\d+(?:\\.\\d+)?)")

    fun version(name: String): Float = VERSION.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

    private fun stable(name: String) = !name.contains("preview") && !name.contains("exp")

    fun pick(kind: Kind, models: List<ModelInfo>, suggested: String? = null): String? {
        val gen = models.filter { "generateContent" in it.methods }
        fun ok(m: ModelInfo) = when (kind) {
            Kind.TEXT -> isText(m.name)
            Kind.IMAGE -> m.name.startsWith("gemini") && m.name.contains("image")
            Kind.TTS -> m.name.contains("tts")
        }
        // Google'ın önerdiği model listede varsa ve iş türüne uyuyorsa onu kullan.
        suggested?.let { s -> gen.firstOrNull { it.name == s && ok(it) }?.let { return it.name } }

        val candidates = gen.filter { ok(it) }
        if (kind == Kind.TEXT) {
            candidates.firstOrNull { it.name == "gemini-flash-latest" }?.let { return it.name }
        }
        val best = candidates.sortedWith(
            compareByDescending<ModelInfo> { it.name.contains("flash") && !it.name.contains("lite") }
                .thenByDescending { version(it.name) }
                .thenByDescending { stable(it.name) }
                .thenBy { it.name.length }
        ).firstOrNull()?.name
        if (best != null) return best

        if (kind == Kind.IMAGE) {
            // Gemini görüntü modeli yoksa Imagen.
            return models.filter { it.name.startsWith("imagen") && "predict" in it.methods }
                .sortedWith(compareByDescending<ModelInfo> { version(it.name) }
                    .thenBy { it.name.contains("ultra") }
                    .thenByDescending { stable(it.name) })
                .firstOrNull()?.name
        }
        return null
    }

    private fun isText(name: String): Boolean {
        if (!name.startsWith("gemini")) return false
        val banned = listOf("image", "tts", "audio", "live", "embedding", "vision", "robotics", "computer-use", "native")
        return banned.none { name.contains(it) }
    }
}
