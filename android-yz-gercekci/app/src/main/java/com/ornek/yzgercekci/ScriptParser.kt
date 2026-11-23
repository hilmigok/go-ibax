package com.ornek.yzgercekci

import java.util.Locale

/**
 * Kullanıcının başka bir yapay zekâya yazdırıp yapıştırdığı senaryoyu okur.
 *  1. JSON ise (şablondaki biçim) doğrudan [StoryboardParser] ile okunur.
 *  2. Düz metinse: boş satırlar ya da "Sahne 1", "Kare 2" başlıkları sahneleri ayırır.
 *     "Görsel:", "Anlatım:", "Altyazı:", "Ses:" gibi etiketler tanınır; "KARAKTER: söz"
 *     biçimindeki diyaloglar anlatıma eklenir. Duygu, ortam sesi ve efektler metindeki
 *     sözcüklerden tahmin edilir.
 */
object ScriptParser {

    private val TR = Locale("tr", "TR")
    private val HEADING = Regex("^\\s*[#*•\\-]*\\s*(sahne|kare|scene|shot|bölüm)\\s*\\d+.*$", RegexOption.IGNORE_CASE)
    private val LABEL = Regex("^\\s*[•*\\-]*\\s*\\**([\\p{L} ()/]{2,30}?)\\**\\s*:\\s*(.*)$")
    private val PAREN = Regex("\\([^)]*\\)")
    private val MARKDOWN = Regex("[*_#`]+")

    fun parse(text: String): Storyboard {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw StoryboardException("Senaryo boş.")
        tryJson(trimmed)?.let { return it }
        return parsePlain(trimmed)
    }

    private fun tryJson(text: String): Storyboard? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            StoryboardParser.parse(text.substring(start, end + 1))
        } catch (e: StoryboardException) {
            null
        }
    }

    private class Block {
        val desc = StringBuilder()
        val narration = StringBuilder()
        val dialogue = ArrayList<Line>()
        var caption = ""
        var sound = ""
        fun isEmpty() = desc.isBlank() && narration.isBlank() && caption.isBlank()
    }

    private fun parsePlain(text: String): Storyboard {
        var title = ""
        val characters = ArrayList<String>()
        val blocks = ArrayList<Block>()
        var cur = Block()
        fun flush() {
            if (!cur.isEmpty()) blocks += cur
            cur = Block()
        }
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) { flush(); continue }
            if (HEADING.matches(line)) {
                flush()
                // "Sahne 1: Harabeler" → başlıktaki açıklamayı da tarife ekle
                val rest = line.substringAfter(':', "").let { clean(it) }
                if (rest.isNotEmpty()) cur.desc.append(rest).append(". ")
                continue
            }
            val m = LABEL.matchEntire(line)
            if (m != null) {
                val label = clean(m.groupValues[1]).lowercase(TR)
                val value = clean(m.groupValues[2])
                when {
                    label.startsWith("başlık") || label == "title" -> { if (title.isEmpty()) title = value; continue }
                    label.containsAny("görsel", "görüntü", "prompt", "image", "tarif", "sahne", "mekan", "mekân", "ortam") ->
                        cur.desc.append(value).append(' ')
                    label.containsAny("altyazı", "caption", "ekran yazısı", "yazı") -> cur.caption = value
                    label.containsAny("efekt", "sound", "ses efekti", "müzik") -> cur.sound = value
                    label.containsAny("anlatım", "anlatıcı", "seslendirme", "narration", "dış ses", "voice") ->
                        cur.narration.append(value).append(' ')
                    else -> {
                        // Büyük harfli isim: diyalog. Sözü anlatıma ekle (karakter adı ve parantezli yönergeler olmadan).
                        val spoken = PAREN.replace(value, "").trim()
                        val speaker = clean(m.groupValues[1]).trim()
                        if (spoken.isNotEmpty()) {
                            cur.narration.append(spoken).append(' ')
                            // Büyük harfle başlayan kısa bir ad ise konuşan karakterdir.
                            val isName = speaker.isNotEmpty() && speaker[0].isUpperCase() && speaker.split(' ').size <= 3
                            if (isName) {
                                cur.dialogue += Line(speaker, spoken)
                                if (characters.none { it.equals(speaker, true) }) characters += speaker
                            }
                        }
                        val direction = PAREN.find(value)?.value?.trim('(', ')')
                        cur.desc.append(m.groupValues[1].trim()).append(if (direction != null) " ($direction)" else "").append(". ")
                    }
                }
                continue
            }
            cur.desc.append(clean(line)).append(' ')
        }
        flush()
        if (blocks.isEmpty()) throw StoryboardException("Senaryoda sahne bulunamadı.")

        val shots = blocks.take(StoryboardParser.MAX_SHOTS).mapIndexed { i, b ->
            val desc = b.desc.toString().trim()
            val narration = b.narration.toString().trim()
            val all = "$desc $narration ${b.caption} ${b.sound}".lowercase(TR)
            val amb = Guess.ambience(all)
            Shot(
                prompt = desc.ifEmpty { narration }.take(2000),
                caption = b.caption.take(80),
                camera = Guess.camera(i),
                effect = Guess.effect(all, amb),
                duration = 4.5f,
                expression = Guess.expression(all),
                sound = Guess.soundQuery(b.sound.lowercase(TR).ifEmpty { all }, amb),
                ambience = amb,
                narration = narration.take(300),
                dialogue = b.dialogue.toList()
            )
        }
        val whole = text.lowercase(TR)
        return Storyboard(title.ifEmpty { "Senaryo" }.take(48), shots, Guess.music(whole), characters)
    }

    private fun clean(s: String) = MARKDOWN.replace(s, "").trim()

    private fun String.containsAny(vararg keys: String) = keys.any { contains(it) }
}

/** Türkçe/İngilizce sözcüklerden duygu, ortam sesi, efekt ve müzik tahmini. */
object Guess {
    private val NON_LETTER = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Sözcük başı eşleşmesi: "ağl" → "ağlıyor" eşleşir ama "bağlı" eşleşmez.
     * Boşluk içeren anahtarlar (ör. "iç çek") metnin içinde aranır.
     */
    fun has(text: String, vararg keys: String): Boolean {
        val words = text.split(NON_LETTER)
        return keys.any { k -> if (k.contains(' ')) text.contains(k) else words.any { it.startsWith(k) } }
    }

    fun ambience(t: String): Ambience = when {
        has(t, "savaş", "bomba", "patlama", "asker", "silah", "füze", "tank", "war", "explosion") -> Ambience.WAR
        has(t, "yağmur", "fırtına", "sağanak", "rain", "storm") -> Ambience.RAIN
        has(t, "ateş", "yangın", "alev", "şömine", "fire", "flame") -> Ambience.FIRE
        has(t, "şehir", "cadde", "sokak", "trafik", "pazar", "city", "street") -> Ambience.CITY
        has(t, "orman", "doğa", "kuş", "nehir", "çayır", "forest", "nature", "bird") -> Ambience.NATURE
        has(t, "sessiz", "oda", "ev içi", "silence", "room") -> Ambience.SILENCE
        else -> Ambience.WIND
    }

    fun effect(t: String, amb: Ambience): Effect = when {
        has(t, "karlı", "kış", "kar yağ", "kartopu", "snow") -> Effect.SNOW
        amb == Ambience.RAIN -> Effect.RAIN
        amb == Ambience.FIRE -> Effect.EMBERS
        amb == Ambience.WAR -> Effect.SMOKE
        has(t, "sis", "pus", "fog", "mist") -> Effect.FOG
        has(t, "duman", "smoke") -> Effect.SMOKE
        has(t, "harabe", "yıkıntı", "enkaz", "toz", "çöl", "ruin", "dust") -> Effect.DUST
        else -> Effect.NONE
    }

    fun expression(t: String): Expression = when {
        has(t, "ağl", "gözyaş", "hıçkır", "cry", "sob", "tear") -> Expression.CRYING
        has(t, "kork", "dehşet", "panik", "fear", "terrif") -> Expression.FEAR
        has(t, "şaşkın", "şok", "şaşır", "shock", "surpris") -> Expression.SHOCK
        has(t, "öfke", "sinirl", "bağır", "kızgın", "angry", "rage") -> Expression.ANGRY
        has(t, "gülüm", "gülüyor", "güldü", "gülerek", "kahkaha", "mutlu", "sevin", "neşe", "smile", "happy", "laugh") -> Expression.SMILE
        has(t, "üzgün", "hüzün", "acı", "yas", "keder", "iç çek", "sad", "grief") -> Expression.SAD
        else -> Expression.NEUTRAL
    }

    /** Ses kütüphanelerinde (İngilizce) aranacak sözcükler. */
    fun soundQuery(t: String, amb: Ambience): String = when {
        has(t, "ağl", "cry", "sob") -> "woman crying"
        has(t, "kalabalık", "çığlık", "crowd", "scream") -> "crowd panic"
        has(t, "gök gürült", "thunder") -> "thunder storm"
        has(t, "deniz", "dalga", "sea", "wave") -> "ocean waves"
        has(t, "kapı", "door") -> "door creak"
        has(t, "ayak sesi", "adım", "footstep") -> "footsteps"
        else -> when (amb) {
            Ambience.WAR -> "distant explosions"
            Ambience.RAIN -> "heavy rain"
            Ambience.FIRE -> "fire crackling"
            Ambience.CITY -> "city street ambience"
            Ambience.NATURE -> "forest birds"
            Ambience.SILENCE -> "room tone"
            Ambience.WIND -> "wind ambience"
        }
    }

    fun music(t: String): Music = when {
        has(t, "gerilim", "korku", "tehlike", "tense", "horror") -> Music.TENSE
        has(t, "savaş", "karanlık", "kötü", "dark", "war") -> Music.DARK
        has(t, "epik", "kahraman", "zafer", "epic", "hero") -> Music.EPIC
        has(t, "umut", "mutlu", "sevinç", "hope", "happy") -> Music.HOPEFUL
        else -> Music.SAD
    }

    private val CAMERAS = arrayOf(
        CameraMove.ZOOM_IN, CameraMove.PAN_DOWN, CameraMove.ZOOM_OUT,
        CameraMove.PAN_RIGHT, CameraMove.ZOOM_IN, CameraMove.PAN_UP, CameraMove.PAN_LEFT
    )

    fun camera(i: Int): CameraMove = CAMERAS[i % CAMERAS.size]
}
