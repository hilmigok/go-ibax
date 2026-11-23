package com.ornek.yzgercekci

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Videonun canlı önizlemesi: [FrameRenderer] ile döngüde çizer.
 * Durdurulabilir, sürdürülebilir ve istenen saniyeye sarılabilir.
 */
class PreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val renderer = FrameRenderer()
    private var board: Storyboard? = null
    private var images: List<Bitmap?> = emptyList()
    private var timeline: Timeline? = null
    private var faces: List<List<FaceRig>> = emptyList()
    private var clips: List<ClipFrames?> = emptyList()
    var mimics: Boolean
        get() = renderer.mimics
        set(value) { renderer.mimics = value; invalidate() }
    private var startNanos = 0L
    private var pausedAt = 0f
    var captions = true
        set(value) { field = value; invalidate() }

    /** Dışa aktarma sırasında çizimi tamamen durdurmak için. */
    var playing = true
        set(value) {
            field = value
            if (value) invalidate()
        }

    /** Kullanıcı durdurdu mu? */
    var paused = false
        private set

    /** Her karede çağrılır: (geçen süre, toplam süre). */
    var onTime: ((Float, Float) -> Unit)? = null

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        textAlign = Paint.Align.CENTER
    }

    val total: Float get() = timeline?.total ?: 0f

    fun setContent(
        board: Storyboard?, images: List<Bitmap?>, faces: List<List<FaceRig>> = this.faces,
        clips: List<ClipFrames?> = this.clips
    ) {
        val restart = board !== this.board
        this.board = board
        this.images = images
        this.faces = faces
        this.clips = clips
        timeline = board?.let { Timeline(it.shots.map { s -> s.duration }) }
        if (restart) seekTo(0f)
        invalidate()
    }

    /** Şu anki önizleme zamanı (sn). */
    fun currentTime(): Float {
        val tot = total
        if (tot <= 0f) return 0f
        val t = if (paused) pausedAt else (System.nanoTime() - startNanos) / 1e9f
        return ((t % tot) + tot) % tot
    }

    fun pause() {
        if (paused) return
        pausedAt = currentTime()
        paused = true
        invalidate()
    }

    fun play() {
        if (!paused) return
        startNanos = System.nanoTime() - (pausedAt * 1e9f).toLong()
        paused = false
        invalidate()
    }

    fun seekTo(t: Float) {
        val c = t.coerceIn(0f, maxOf(0f, total))
        pausedAt = c
        startNanos = System.nanoTime() - (c * 1e9f).toLong()
        invalidate()
    }

    /** Önizlemeyi baştan başlatır. */
    fun restart() = seekTo(0f)

    override fun onDraw(canvas: Canvas) {
        val b = board
        val tl = timeline
        if (b == null || tl == null) {
            canvas.drawColor(Color.rgb(18, 18, 22))
            hintPaint.textSize = width * 0.045f
            canvas.drawText(context.getString(R.string.preview_empty), width / 2f, height / 2f, hintPaint)
            return
        }
        val t = currentTime()
        renderer.draw(canvas, b, images, tl, t, width, height, captions, faces, clips)
        onTime?.invoke(t, tl.total)
        if (playing && !paused && isShown) postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) invalidate()
    }
}
