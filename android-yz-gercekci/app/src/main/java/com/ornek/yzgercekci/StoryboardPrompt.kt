package com.ornek.yzgercekci

/** Senaryo şablonu ve görüntü tarifleri. */
object StoryboardPrompt {

    /**
     * Kullanıcının ChatGPT / Gemini / Claude gibi bir yapay zekâya yapıştıracağı istek.
     * Sonuç doğrudan uygulamaya yapıştırılır.
     */
    const val TEMPLATE = """Sen ödüllü bir film yönetmeni ve görüntü yönetmenisin. Aşağıdaki hikâyeyi, yapay zekâ ile fotoğraf gerçekliğinde çizilip kısa bir videoya dönüştürülecek sahnelere böl.

SADECE aşağıdaki biçimde bir JSON döndür, başka hiçbir şey yazma:
{
  "title": "kısa Türkçe başlık",
  "music": "none | dark | sad | epic | tense | hopeful",
  "characters": ["KARAKTER ADI", "..."],
  "shots": [
    {
      "prompt": "İNGİLİZCE görsel tarifi, 60-120 kelime",
      "caption": "kısa Türkçe ekran yazısı ya da boş",
      "narration": "bu sahnede anlatıcının okuyacağı Türkçe cümle (en fazla 20 kelime) ya da boş",
      "dialogue": [{"character": "KARAKTER ADI", "line": "karakterin Türkçe sözü"}],
      "motion": "İNGİLİZCE hareket tarifi, 25-60 kelime: kim ne yapıyor, saç/kıyafet/duman nasıl hareket ediyor, tek bir yavaş kamera hareketi",
      "camera": "zoom_in | zoom_out | pan_left | pan_right | pan_up | pan_down | static",
      "effect": "none | dust | smoke | fog | rain | snow | embers",
      "expression": "neutral | sad | crying | fear | angry | shock | smile",
      "sound": "bu sahnede duyulan sesi anlatan 2-4 İngilizce kelime (ör. distant explosions, woman crying, heavy rain)",
      "ambience": "silence | wind | rain | fire | war | city | nature",
      "duration": 4.5
    }
  ]
}

"prompt" kuralları:
- İngilizce, tek paragraf, donmuş TEK bir anı anlatsın: kişi, eylem, yüz ifadesi ve duygu, kıyafet, mekân, ön/orta/arka plan, günün saati, hava, ışığın yönü ve niteliği, renk paleti, kamera açısı ve lens (ör. "low angle, 24mm wide lens", "close-up, 85mm").
- Gerçek bir fotoğraf ya da film karesi gibi görünsün; yazı, logo, filigran isteme.
- Tekrar eden karakterleri bir kez tanımla (yaş, yüz, saç, kıyafet renkleri) ve göründükleri HER sahnede aynı tanımı kelimesi kelimesine tekrarla. Mekân tanımını da tekrarla.
- Çekim ölçeklerini değiştir (geniş açı, orta plan, yüze yakın plan, detay) ve duyguyu sona doğru yükselt.
- Dikey 9:16 video için kompozisyonu dikey kur (gökyüzü / büyük figür üstte, insanlar alt üçte birde).
- Kan ve vahşet gösterme; trajediyi ışık, atmosfer ve duyguyla anlat.

Karakter adlarını "characters" listesinde, "dialogue" içinde ve "prompt" metninde AYNI yaz (ör. KOVAN).
Diğer kurallar: 4-8 sahne yaz. "duration" 3-6 saniye. "expression" sahnedeki yüzlerin baskın ifadesi (yüz yoksa "neutral").

HİKÂYEM:
[BURAYA HİKÂYENİ YAZ]"""

    /** Grok video modeline gidecek tarif: sahne + hareket + diyalog + ses. */
    fun grokPrompt(shot: Shot, style: VisualStyle, portrait: Boolean, referenceNames: List<String>): String = buildString {
        append(shot.prompt.trim().trimEnd('.')).append(". ")
        val motion = shot.motion.ifEmpty {
            "Subtle natural motion: characters breathe, blink and react with believable facial expressions, " +
                "hair and clothes move in the wind, slow cinematic push in."
        }
        append("Motion: ").append(motion.trim().trimEnd('.')).append(". ")
        if (referenceNames.isNotEmpty()) {
            append("The characters must look exactly like the reference images: ")
            append(referenceNames.mapIndexed { i, n -> "$n is reference image ${i + 1}" }.joinToString("; "))
            append(". Keep their faces, hair and clothing consistent. ")
        }
        for (l in shot.dialogue) {
            val who = l.character.ifEmpty { "A character" }
            append(who).append(" says in Turkish, with natural emotion and lip sync: \"").append(l.text).append("\" ")
        }
        val sound = if (shot.sound.isNotEmpty()) "realistic ${shot.sound}" else "realistic ambient sound"
        append("Audio: ").append(sound).append(" and natural sound effects, no background music. ")
        append("Style: ").append(style.suffix).append(", ")
        append(if (portrait) "vertical 9:16 framing" else "wide 16:9 framing")
        append(", photorealistic live-action footage, no text, no subtitles, no watermark.")
    }

    /** Görüntü üreticisine gidecek son tarif: senaryodaki tarif + üslup + oran ipucu. */
    fun imagePrompt(shotPrompt: String, style: VisualStyle, portrait: Boolean): String {
        val framing = if (portrait) "vertical 9:16 composition" else "wide 16:9 composition"
        return "${shotPrompt.trim()}. Style: ${style.suffix}, $framing. Photorealistic, no text, no watermark."
    }
}
