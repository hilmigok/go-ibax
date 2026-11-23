package com.ornek.yzgercekci

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
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
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Kareleri [FrameRenderer] ile bir Bitmap'e çizer, OpenGL dokusu olarak donanım
 * H.264 kodlayıcısının giriş yüzeyine aktarır, MP4'e yazar ve galeriye
 * (Filmler/YZGercekci) kaydeder. EGL ile her kareye kesin zaman damgası verilir.
 * Tek bir arka plan iş parçacığında çağrılmalıdır.
 */
class VideoExporter(private val context: Context) {

    fun export(
        board: Storyboard,
        images: List<Bitmap?>,
        videoFormat: VideoFormat,
        captions: Boolean,
        faces: List<List<FaceRig>> = emptyList(),
        mimics: Boolean = true,
        audio: ShortArray? = null,
        onProgress: (Float) -> Unit
    ): Uri {
        val width = videoFormat.width
        val height = videoFormat.height
        val timeline = Timeline(board.shots.map { it.duration })
        val tmp = File(context.cacheDir, "yzg_${System.currentTimeMillis()}.mp4")

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
        // Ses önceden AAC'ye kodlanır; video kodlayıcısı biçimini bildirince ikisi birlikte yazılır.
        if (audio != null && audio.isNotEmpty()) {
            runCatching { encodeAac(audio, state) }.onFailure { state.audioFormat = null; state.audioSamples.clear() }
        }
        var egl: EglWindow? = null
        var frameBitmap: Bitmap? = null
        var success = false

        try {
            egl = EglWindow(inputSurface)
            egl.makeCurrent()
            val blitter = TextureBlitter()
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            frameBitmap = bmp
            val canvas = Canvas(bmp)
            val renderer = FrameRenderer().also { it.mimics = mimics }

            val total = (timeline.total * FPS).roundToInt().coerceAtLeast(1)
            for (i in 0 until total) {
                drain(encoder, muxer, state, endOfStream = false)
                renderer.draw(canvas, board, images, timeline, i.toFloat() / FPS, width, height, captions, faces)
                GLES20.glViewport(0, 0, width, height)
                blitter.draw(bmp)
                egl.setPresentationTime(i * 1_000_000_000L / FPS)
                egl.swap()
                onProgress((i + 1).toFloat() / total)
            }
            drain(encoder, muxer, state, endOfStream = true)
            blitter.release()
            success = true
        } finally {
            egl?.release()
            frameBitmap?.recycle()
            runCatching { encoder.stop() }
            encoder.release()
            inputSurface.release()
            if (state.started) runCatching { muxer.stop() }
            muxer.release()
            if (!success) tmp.delete()
        }

        try {
            val suffix = if (videoFormat.isPortrait) "9x16" else "16x9"
            return saveToGallery(tmp, "${board.title}_$suffix")
        } finally {
            tmp.delete()
        }
    }

    private class MuxState {
        var track = -1
        var started = false
        var audioFormat: MediaFormat? = null
        val audioSamples = ArrayList<Pair<ByteArray, MediaCodec.BufferInfo>>()
    }

    /** Mono 16 bit PCM'i AAC-LC'ye kodlar ve örnekleri belleğe alır. */
    private fun encodeAac(pcm: ShortArray, state: MuxState) {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, Soundtrack.SR, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        try {
            var pos = 0
            var inputDone = false
            val info = MediaCodec.BufferInfo()
            var idle = 0
            while (true) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        buf.clear()
                        val n = minOf(pcm.size - pos, buf.capacity() / 2, 4096)
                        val pts = pos * 1_000_000L / Soundtrack.SR
                        if (n <= 0) {
                            codec.queueInputBuffer(idx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, pos, n)
                            codec.queueInputBuffer(idx, 0, n * 2, pts, 0)
                            pos += n
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> state.audioFormat = codec.outputFormat
                    out >= 0 -> {
                        idle = 0
                        val buf = codec.getOutputBuffer(out)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val bytes = ByteArray(info.size)
                            buf.position(info.offset)
                            buf.get(bytes)
                            val copy = MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                            state.audioSamples += bytes to copy
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                    out == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone -> if (++idle > 500) throw IOException("Ses kodlayıcı yanıt vermiyor")
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
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
                    val af = state.audioFormat
                    val audioTrack = if (af != null && state.audioSamples.isNotEmpty()) muxer.addTrack(af) else -1
                    muxer.start()
                    state.started = true
                    if (audioTrack >= 0) {
                        for ((bytes, bi) in state.audioSamples) muxer.writeSampleData(audioTrack, ByteBuffer.wrap(bytes), bi)
                        state.audioSamples.clear()
                    }
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
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${GalleryNames.safe(title)}_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/YZGercekci")
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

    /** Bir Bitmap'i tam ekran dörtgen olarak çizen en basit GL programı. */
    private class TextureBlitter {
        private val program: Int
        private val texture: Int
        private val quad: FloatBuffer
        private val aPos: Int
        private val aUv: Int
        private val uTex: Int

        init {
            program = link(VERTEX, FRAGMENT)
            aPos = GLES20.glGetAttribLocation(program, "aPos")
            aUv = GLES20.glGetAttribLocation(program, "aUv")
            uTex = GLES20.glGetUniformLocation(program, "uTex")
            // x, y, u, v — Bitmap'in üstü (v=0) ekranın üstüne (y=1) gelsin.
            val data = floatArrayOf(
                -1f, -1f, 0f, 1f,
                1f, -1f, 1f, 1f,
                -1f, 1f, 0f, 0f,
                1f, 1f, 1f, 0f
            )
            quad = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            quad.put(data).position(0)
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texture = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        private var allocated = false

        fun draw(bmp: Bitmap) {
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            if (!allocated) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                allocated = true
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bmp)
            }
            GLES20.glUniform1i(uTex, 0)
            quad.position(0)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aPos)
            quad.position(2)
            GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 16, quad)
            GLES20.glEnableVertexAttribArray(aUv)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        fun release() {
            GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
            GLES20.glDeleteProgram(program)
        }

        companion object {
            private const val VERTEX = """
                attribute vec2 aPos;
                attribute vec2 aUv;
                varying vec2 vUv;
                void main() { vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); }
            """
            private const val FRAGMENT = """
                precision mediump float;
                varying vec2 vUv;
                uniform sampler2D uTex;
                void main() { gl_FragColor = texture2D(uTex, vUv); }
            """

            private fun compile(type: Int, src: String): Int {
                val s = GLES20.glCreateShader(type)
                GLES20.glShaderSource(s, src)
                GLES20.glCompileShader(s)
                val ok = IntArray(1)
                GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
                if (ok[0] == 0) throw IOException("Gölgelendirici derlenemedi: " + GLES20.glGetShaderInfoLog(s))
                return s
            }

            private fun link(vs: String, fs: String): Int {
                val p = GLES20.glCreateProgram()
                GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
                GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
                GLES20.glLinkProgram(p)
                val ok = IntArray(1)
                GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
                if (ok[0] == 0) throw IOException("GL programı bağlanamadı: " + GLES20.glGetProgramInfoLog(p))
                return p
            }
        }
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
