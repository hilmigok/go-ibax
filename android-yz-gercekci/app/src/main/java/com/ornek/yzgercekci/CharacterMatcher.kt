package com.ornek.yzgercekci

import java.util.Locale

/** Kullanıcının eklediği karakter: ad + referans resim dosyası. */
data class CharacterRef(val id: String, val name: String, val imagePath: String)

/**
 * Bir sahnede hangi karakterlerin geçtiğini bulur: tarif, anlatım, hareket tarifi ve
 * diyalog konuşmacıları içinde karakter adını sözcük başı eşleşmesiyle arar
 * ("Kovan'ın", "KOVAN" → KOVAN; ama "Kovanlık" da eşleşir, bu kabul edilebilir).
 */
object CharacterMatcher {

    const val MAX_REFERENCES = 14
    private val TR = Locale("tr", "TR")

    fun match(shot: Shot, characters: List<CharacterRef>): List<CharacterRef> {
        if (characters.isEmpty()) return emptyList()
        val text = buildString {
            append(shot.prompt).append(' ').append(shot.narration).append(' ')
            append(shot.motion).append(' ').append(shot.caption)
            shot.dialogue.forEach { append(' ').append(it.character).append(' ').append(it.text) }
        }.lowercase(TR)
        val speakers = shot.dialogue.map { it.character.lowercase(TR).trim() }.toSet()
        return characters.filter { c ->
            val name = c.name.trim().lowercase(TR)
            if (name.isEmpty()) return@filter false
            if (name in speakers) return@filter true
            // Çok kelimeli adın her kelimesi geçmeli (ör. "Ayşe Teyze").
            name.split(' ').filter { it.isNotEmpty() }.all { Guess.has(text, it) }
        }.take(MAX_REFERENCES)
    }
}
