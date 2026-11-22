package com.ornek.yzgercekci

/** Galeriye yazılacak dosya adlarını güvenli hale getirir. */
object GalleryNames {
    fun safe(title: String): String =
        title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(48).ifEmpty { "sahne" }
}
