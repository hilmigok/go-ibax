package com.ornek.yzgercekci

/** Bir karede kameranın görüntü üzerinde yaptığı hareket (Ken Burns etkisi). */
enum class CameraMove(val key: String) {
    ZOOM_IN("zoom_in"),
    ZOOM_OUT("zoom_out"),
    PAN_LEFT("pan_left"),
    PAN_RIGHT("pan_right"),
    PAN_UP("pan_up"),
    PAN_DOWN("pan_down"),
    STATIC("static");

    companion object {
        fun from(s: String?): CameraMove =
            entries.firstOrNull { it.key == s?.trim()?.lowercase() } ?: ZOOM_IN
    }
}

/** Görüntünün üstüne çizilen atmosfer efekti. */
enum class Effect(val key: String) {
    NONE("none"),
    DUST("dust"),
    SMOKE("smoke"),
    FOG("fog"),
    RAIN("rain"),
    SNOW("snow"),
    EMBERS("embers");

    companion object {
        fun from(s: String?): Effect =
            entries.firstOrNull { it.key == s?.trim()?.lowercase() } ?: NONE
    }
}

/** Karakterlerin yüzüne uygulanacak mimik. */
enum class Expression(val key: String) {
    NEUTRAL("neutral"),
    SAD("sad"),
    CRYING("crying"),
    FEAR("fear"),
    ANGRY("angry"),
    SHOCK("shock"),
    SMILE("smile");

    companion object {
        fun from(s: String?): Expression =
            entries.firstOrNull { it.key == s?.trim()?.lowercase() } ?: NEUTRAL
    }
}

/** Ses bulunamazsa cihazda sentezlenecek ortam sesi türü. */
enum class Ambience(val key: String) {
    SILENCE("silence"),
    WIND("wind"),
    RAIN("rain"),
    FIRE("fire"),
    WAR("war"),
    CITY("city"),
    NATURE("nature");

    companion object {
        fun from(s: String?): Ambience =
            entries.firstOrNull { it.key == s?.trim()?.lowercase() } ?: WIND
    }
}

/** Fon müziğinin havası (cihazda sentezlenir). */
enum class Music(val key: String) {
    NONE("none"),
    DARK("dark"),
    SAD("sad"),
    EPIC("epic"),
    TENSE("tense"),
    HOPEFUL("hopeful");

    companion object {
        fun from(s: String?): Music =
            entries.firstOrNull { it.key == s?.trim()?.lowercase() } ?: SAD
    }
}

/** Senaryodaki tek bir sahne/kare. [prompt] görüntü modeline giden İngilizce tariftir. */
data class Shot(
    val prompt: String,
    val caption: String,
    val camera: CameraMove,
    val effect: Effect,
    val duration: Float,
    val expression: Expression = Expression.NEUTRAL,
    /** Freesound'da aranacak İngilizce ses sözcükleri (ör. "wind ruins"). */
    val sound: String = "",
    val ambience: Ambience = Ambience.WIND,
    /** Anlatıcının bu karede okuyacağı Türkçe cümle (boş olabilir). */
    val narration: String = "",
    /** Video modeli için hareketin İngilizce tarifi. */
    val motion: String = "",
    /** Karakterlerin bu sahnede söylediği sözler (Grok modunda karakterler kendi sesiyle söyler). */
    val dialogue: List<Line> = emptyList()
)

/** Bir diyalog satırı. */
data class Line(val character: String, val text: String)

data class Storyboard(
    val title: String,
    val shots: List<Shot>,
    val music: Music = Music.SAD,
    /** Senaryoda geçen karakter adları. */
    val characters: List<String> = emptyList()
)

/** Kullanıcının seçebileceği görsel üslup; [suffix] her görüntü tarifinin sonuna eklenir. */
enum class VisualStyle(val label: String, val suffix: String) {
    CINEMATIC(
        "Sinematik gerçekçi",
        "cinematic photorealistic film still, shot on ARRI Alexa with a 35mm anamorphic lens, " +
            "natural skin texture, dramatic volumetric lighting, shallow depth of field, " +
            "subtle film grain, rich color grading, ultra detailed, 8k"
    ),
    DARK_FANTASY(
        "Karanlık fantastik",
        "dark fantasy photorealistic matte painting, epic scale, ominous storm sky, " +
            "desaturated cold palette with deep shadows, volumetric god rays through smoke and dust, " +
            "hyper detailed textures, cinematic composition, film grain, 8k"
    ),
    DOCUMENTARY(
        "Belgesel / foto muhabiri",
        "award-winning photojournalism photograph, shot on Canon EOS R5 with a 35mm lens, " +
            "raw and authentic, natural available light, real human emotion, dust in the air, " +
            "documentary realism, high detail, slight film grain"
    ),
    HISTORICAL(
        "Epik tarih",
        "epic historical cinematic still, period-accurate costumes and architecture, " +
            "golden hour light, atmospheric haze, photorealistic, sweeping composition, " +
            "highly detailed, film grain"
    ),
    SCIFI(
        "Bilim kurgu",
        "photorealistic science fiction film still, massive scale, practical lighting, " +
            "neon and volumetric haze, detailed hard-surface design, anamorphic lens flares, " +
            "cinematic color grade, 8k"
    );
}
