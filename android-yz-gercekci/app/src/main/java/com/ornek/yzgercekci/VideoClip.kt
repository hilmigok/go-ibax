package com.ornek.yzgercekci

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.IOException

/** Bir video klibinden zamana göre kare veren kaynak. */
interface ClipFrames {
    /** Klibin süresi (sn). */
    val duration: Float
    /** Klibin [t]. saniyesindeki kare (süre aşılırsa son kare). */
    fun frameAt(t: Float): Bitmap?
    fun release()
}

private class ClipInfo(val frames: Int, val duration: Float)

private fun readInfo(r: MediaMetadataRetriever): ClipInfo {
    val frames = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
    val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    if (frames <= 0 || ms <= 0) throw IOException("Video klibi okunamadı")
    return ClipInfo(frames, ms / 1000f)
}

object VideoClips {
    /** Klibin süresi (sn). */
    fun duration(file: File): Float {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            return readInfo(r).duration
        } finally {
            r.release()
        }
    }
}

/**
 * Önizleme için: klip bir kez çözülür, küçük (RGB_565) ve saniyede [PREVIEW_FPS]
 * kareye seyreltilmiş halde bellekte tutulur; gerçek zamanlı oynatma için hafiftir.
 */
class PreviewClip private constructor(private val frames: Array<Bitmap>, override val duration: Float) : ClipFrames {

    override fun frameAt(t: Float): Bitmap? {
        if (frames.isEmpty()) return null
        val i = (t / duration * frames.size).toInt().coerceIn(0, frames.size - 1)
        return frames[i].takeUnless { it.isRecycled }
    }

    override fun release() = frames.forEach { it.recycle() }

    companion object {
        private const val PREVIEW_FPS = 12f
        private const val PREVIEW_WIDTH = 300
        private const val CHUNK = 24

        /** Arka plan iş parçacığında çağırın. */
        fun load(file: File): PreviewClip {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(file.path)
                val info = readInfo(r)
                val wanted = (info.duration * PREVIEW_FPS).toInt().coerceIn(1, info.frames)
                val step = info.frames.toFloat() / wanted
                val out = ArrayList<Bitmap>(wanted)
                var next = 0f
                var start = 0
                while (start < info.frames && out.size < wanted) {
                    val count = minOf(CHUNK, info.frames - start)
                    val chunk = r.getFramesAtIndex(start, count)
                    for ((k, bmp) in chunk.withIndex()) {
                        if (start + k >= next && out.size < wanted) {
                            val h = (PREVIEW_WIDTH.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
                            val small = Bitmap.createScaledBitmap(bmp, PREVIEW_WIDTH, h, true)
                            out += small.copy(Bitmap.Config.RGB_565, false)
                            if (small !== bmp) small.recycle()
                            next += step
                        }
                        bmp.recycle()
                    }
                    start += count
                }
                return PreviewClip(out.toTypedArray(), info.duration)
            } finally {
                r.release()
            }
        }
    }
}

/** Video dışa aktarımı için: tam çözünürlüklü kareleri parça parça, sıralı erişimle çözer. */
class StreamingClip(file: File) : ClipFrames {
    private val retriever = MediaMetadataRetriever().apply { setDataSource(file.path) }
    private val info = readInfo(retriever)
    override val duration: Float get() = info.duration
    private var chunk: List<Bitmap> = emptyList()
    private var chunkStart = -1

    override fun frameAt(t: Float): Bitmap? {
        val idx = (t / info.duration * info.frames).toInt().coerceIn(0, info.frames - 1)
        if (chunkStart < 0 || idx < chunkStart || idx >= chunkStart + chunk.size) {
            chunk.forEach { it.recycle() }
            chunk = try {
                retriever.getFramesAtIndex(idx, minOf(CHUNK, info.frames - idx))
            } catch (e: Exception) {
                emptyList()
            }
            chunkStart = idx
        }
        return chunk.getOrNull(idx - chunkStart)
    }

    override fun release() {
        chunk.forEach { it.recycle() }
        chunk = emptyList()
        retriever.release()
    }

    companion object {
        private const val CHUNK = 12
    }
}
