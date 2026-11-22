package com.ornek.yz3dvideo

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Sahneyi ekran dışı bir OpenGL yüzeyine kare kare çizer, donanım H.264
 * kodlayıcısıyla MP4'e dönüştürür ve galeriye (Filmler/YZ3DVideo) kaydeder.
 *
 * Tüm iş tek bir iş parçacığında, baştan sona askıya alınmadan yapılmalıdır
 * (EGL bağlamı iş parçacığına bağlıdır). Ana iş parçacığında çağırmayın.
 */
class VideoExporter(private val context: Context) {

    fun export(scene: Scene3D, videoFormat: VideoFormat, onProgress: (Float) -> Unit): Uri {
        val tmp = File(context.cacheDir, "yz3d_${System.currentTimeMillis()}.mp4")
        val width = videoFormat.width
        val height = videoFormat.height

        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, videoFormat.bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MIME)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(tmp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val state = MuxState()
        var egl: EglWindow? = null
        var success = false

        try {
            egl = EglWindow(inputSurface)
            egl.makeCurrent()
            val renderer = SceneRenderer()
            renderer.init()

            val total = (scene.duration * FPS).roundToInt().coerceAtLeast(1)
            for (i in 0 until total) {
                drain(encoder, muxer, state, endOfStream = false)
                renderer.render(scene, i.toFloat() / FPS, width, height)
                egl.setPresentationTime(i * 1_000_000_000L / FPS)
                egl.swap()
                onProgress((i + 1).toFloat() / total)
            }
            drain(encoder, muxer, state, endOfStream = true)
            success = true
        } finally {
            egl?.release()
            runCatching { encoder.stop() }
            encoder.release()
            inputSurface.release()
            if (state.started) runCatching { muxer.stop() }
            muxer.release()
            if (!success) tmp.delete()
        }

        try {
            val suffix = if (videoFormat.isPortrait) "9x16" else "16x9"
            return saveToGallery(tmp, "${scene.title}_$suffix")
        } finally {
            tmp.delete()
        }
    }

    private class MuxState {
        var track = -1
        var started = false
    }

    private fun drain(encoder: MediaCodec, muxer: MediaMuxer, state: MuxState, endOfStream: Boolean) {
        if (endOfStream) encoder.signalEndOfInputStream()
        val info = MediaCodec.BufferInfo()
        var idleTries = 0
        while (true) {
            val index = encoder.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    if (++idleTries > 500) throw IOException("Video kodlayıcı yanıt vermiyor")
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (state.started) throw IOException("Video biçimi beklenmedik şekilde değişti")
                    state.track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    state.started = true
                }
                index >= 0 -> {
                    val buffer = encoder.getOutputBuffer(index)
                        ?: throw IOException("Kodlayıcı çıktısı alınamadı")
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && state.started) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(state.track, buffer, info)
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun saveToGallery(file: File, title: String): Uri {
        val safe = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(48).ifEmpty { "sahne" }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${safe}_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/YZ3DVideo")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Galeriye kayıt oluşturulamadı")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Galeri dosyası açılamadı")
            out.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    /** Kodlayıcının giriş yüzeyine bağlı EGL bağlamı. */
    private class EglWindow(surface: Surface) {
        private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        private val context: EGLContext
        private val eglSurface: EGLSurface

        init {
            if (display == EGL14.EGL_NO_DISPLAY) throw IOException("EGL ekranı yok")
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw IOException("EGL başlatılamadı")

            val attribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 16,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) {
                throw IOException("Uygun EGL yapılandırması bulunamadı")
            }
            val config = configs[0]!!
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            if (context == EGL14.EGL_NO_CONTEXT) throw IOException("EGL bağlamı oluşturulamadı")
            eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) throw IOException("EGL yüzeyi oluşturulamadı")
        }

        fun makeCurrent() {
            if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) throw IOException("EGL etkinleştirilemedi")
        }

        fun setPresentationTime(ns: Long) {
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, ns)
        }

        fun swap() {
            EGL14.eglSwapBuffers(display, eglSurface)
        }

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
    }

    companion object {
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val FPS = 30
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
