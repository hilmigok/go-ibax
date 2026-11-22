package com.ornek.yzgercekci

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Videonun canlı önizlemesi: [FrameRenderer] ile sürekli döngüde çizer. */
class PreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val renderer = FrameRenderer()
    private var board: Storyboard? = null
    private var images: List<Bitmap?> = emptyList()
    private var timeline: Timeline? = null
    private var startNanos = 0L
    var captions = true
    var playing = true
        set(value) {
            field = value
            if (value) invalidate()
        }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        textAlign = Paint.Align.CENTER
    }

    fun setContent(board: Storyboard?, images: List<Bitmap?>) {
        val restart = board !== this.board
        this.board = board
        this.images = images
        timeline = board?.let { Timeline(it.shots.map { s -> s.duration }) }
        if (restart) startNanos = System.nanoTime()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val b = board
        val tl = timeline
        if (b == null || tl == null) {
            canvas.drawColor(Color.rgb(18, 18, 22))
            hintPaint.textSize = width * 0.045f
            canvas.drawText(context.getString(R.string.preview_empty), width / 2f, height / 2f, hintPaint)
            return
        }
        val t = ((System.nanoTime() - startNanos) / 1e9f) % tl.total
        renderer.draw(canvas, b, images, tl, t, width, height, captions)
        if (playing && isShown) postInvalidateOnAnimation()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) invalidate()
    }
}
