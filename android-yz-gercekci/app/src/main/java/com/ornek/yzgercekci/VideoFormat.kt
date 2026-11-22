package com.ornek.yzgercekci

/** Çıktı video biçimleri. */
enum class VideoFormat(val width: Int, val height: Int, val bitRate: Int) {
    /** YouTube (yatay). */
    LANDSCAPE_16_9(1920, 1080, 12_000_000),

    /** TikTok, Instagram Reels, YouTube Shorts (dikey). */
    PORTRAIT_9_16(1080, 1920, 12_000_000);

    val isPortrait: Boolean get() = height > width
}
