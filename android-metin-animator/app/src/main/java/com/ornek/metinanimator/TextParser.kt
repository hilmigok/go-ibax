package com.ornek.metinanimator

import java.util.Locale

/**
 * Türkçe bir metni tarayıp bir [Scene] üretir.
 *
 * Tamamen çevrimdışı ve kural tabanlıdır: metin, anahtar kelime kökleriyle
 * eşleştirilir. "güneşli", "güneşte" gibi ekli hâller de "güneş" köküyle yakalanır
 * çünkü eşleşme `contains` ile yapılır.
 */
class TextParser {

    fun parse(raw: String): Scene {
        val text = normalize(raw)

        // Metinde verilen kelimelerden herhangi biri geçiyor mu?
        fun has(vararg keywords: String): Boolean =
            keywords.any { it.isNotEmpty() && text.contains(it) }

        // Tekrarı önlemek için küme; ekleme sırası korunsun diye LinkedHashSet.
        val elements = LinkedHashSet<ElementType>()

        var background = Background.DAY
        var ground = Ground.GRASS

        // --- Atmosfer / zaman ---
        if (has("gece", "karanlık", "mehtap")) background = Background.NIGHT
        if (has("gün batımı", "günbatımı", "akşam", "şafak", "gün doğumu")) background = Background.SUNSET

        // --- Gökyüzü öğeleri ---
        if (has("güneş", "gündüz", "sıcak", "güneşli", "yazın")) {
            elements.add(ElementType.SUN)
            if (background == Background.NIGHT) background = Background.DAY
        }
        if (has(" ay ", "ay'", "mehtap", "dolunay", "hilal")) {
            elements.add(ElementType.MOON)
            background = Background.NIGHT
        }
        if (has("yıldız", "yildiz")) {
            elements.add(ElementType.STAR)
            if (background == Background.DAY) background = Background.NIGHT
        }
        if (has("bulut", "bulutlu", "sisli")) elements.add(ElementType.CLOUD)
        if (has("yağmur", "yağış", "yagmur")) {
            elements.add(ElementType.RAIN)
            elements.add(ElementType.CLOUD)
        }
        if (has("kar", "karlı", "kış", "tipi", "kis")) {
            elements.add(ElementType.SNOW)
            elements.add(ElementType.CLOUD)
        }

        // --- Zemin ---
        if (has("deniz", "göl", "nehir", "okyanus", "dalga", " su ", "suda")) ground = Ground.WATER
        if (has("kum", "çöl", "plaj", "sahil", "col")) ground = Ground.SAND

        // --- Yer / hava öğeleri ---
        if (has("araba", "otomobil", "taksi", "kamyon", "araç")) elements.add(ElementType.CAR)
        if (has("ağaç", "orman", "agac", "palmiye")) elements.add(ElementType.TREE)
        if (has("çiçek", "gül", "papatya", "lale", "cicek")) elements.add(ElementType.FLOWER)
        if (has("kalp", "aşk", "sevgi", "seviyorum", "ask")) elements.add(ElementType.HEART)
        if (has("kuş", "martı", "serçe", "kartal", "kus")) elements.add(ElementType.BIRD)
        if (has("balık", "balik", "yunus")) {
            elements.add(ElementType.FISH)
            if (ground != Ground.SAND) ground = Ground.WATER
        }
        if (has(" ev ", "ev'", "köşk", "kulübe", "evde", "evin", "evi", "mahalle")) {
            elements.add(ElementType.HOUSE)
        }
        if (has("insan", "adam", "kadın", "çocuk", "kız", "oğlan", "kişi", "ben ", "o ")) {
            elements.add(ElementType.PERSON)
        }
        if (has("balon", "uçan balon")) elements.add(ElementType.BALLOON)
        if (has("dağ", "tepe", "zirve", "dag")) elements.add(ElementType.MOUNTAIN)

        // Kar yağarken parlak güneş tutarsız durur; güneşi gizle.
        if (elements.contains(ElementType.SNOW)) elements.remove(ElementType.SUN)

        // Hiçbir şey eşleşmezse varsayılan güzel bir manzara göster.
        if (elements.isEmpty()) {
            elements.add(ElementType.SUN)
            elements.add(ElementType.CLOUD)
            elements.add(ElementType.TREE)
        }

        return Scene(
            background = background,
            ground = ground,
            elements = elements.toList(),
            caption = raw.trim()
        )
    }

    /** Küçük harfe indirger (Türkçe kurallarıyla) ve noktalama işaretlerini boşluğa çevirir. */
    private fun normalize(raw: String): String {
        val lower = raw.lowercase(Locale("tr", "TR"))
        val sb = StringBuilder(lower.length + 2)
        sb.append(' ')
        for (ch in lower) {
            sb.append(if (ch.isLetterOrDigit() || ch == '\'') ch else ' ')
        }
        sb.append(' ')
        return sb.toString()
    }
}
