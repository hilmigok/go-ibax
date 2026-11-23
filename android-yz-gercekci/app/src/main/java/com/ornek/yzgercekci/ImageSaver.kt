package com.ornek.yzgercekci

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Üretilen kareleri galeriye (Resimler/YZGercekci) kaydeder ve referans için sıkıştırır. */
object ImageSaver {

    fun saveToGallery(context: Context, bmp: Bitmap, title: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${GalleryNames.safe(title)}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/YZGercekci")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Galeriye kayıt oluşturulamadı")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Galeri dosyası açılamadı")
            out.use { if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, it)) throw IOException("Görüntü yazılamadı") }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** En uzun kenarı [maxSide]'ı aşmayacak şekilde küçültür (gerekmiyorsa aynısını döndürür). */
    fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        } else bmp
    }

    /** Video modeline gönderilecek JPEG (yükleme boyutunu düşük tutar). */
    fun referenceJpeg(bmp: Bitmap, maxSide: Int = 1024): ByteArray {
        val small = scaleDown(bmp, maxSide)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (small !== bmp) small.recycle()
        return out.toByteArray()
    }
}
