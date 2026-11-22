package com.ornek.yzgercekci

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.random.Random

/**
 * Bir video karesini Canvas'a çizer: Ken Burns kamera hareketi, çapraz geçiş,
 * atmosfer parçacıkları, vinyet, film greni ve altyazı. Önizleme ve video
 * dışa aktarımı aynı sınıfı kullanır; çıktı birebir aynıdır.
 */
class FrameRenderer {

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        // Hafif sinematik renk: biraz kontrast, biraz az doygunluk.
        colorFilter = ColorMatrixColorFilter(cinematicMatrix())
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val blobPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val vignettePaint = Paint()
    private val grainPaint = Paint()
    private val captionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(8f, 0f, 2f, Color.argb(220, 0, 0, 0))
    }
    private val matrix = Matrix()
    private val particle = Particles.P()
    private val grainShader: BitmapShader
    private val grainMatrix = Matrix()
    private val blob: Bitmap = makeBlob()
    private val smokeFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.75f, 0.75f, 0.75f, 1f) })
    private var captionLayout: StaticLayout? = null
    private var captionKey = ""

    private var vignetteW = 0
    private var vignetteH = 0

    init {
        val noise = makeNoise()
        grainShader = BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        grainPaint.shader = grainShader
    }

    /**
     * @param images her karenin görüntüsü; henüz üretilmemiş olanlar null.
     */
    fun draw(canvas: Canvas, board: Storyboard, images: List<Bitmap?>, timeline: Timeline, t: Float, w: Int, h: Int, captions: Boolean) {
        canvas.drawColor(Color.BLACK)
        val f = timeline.at(t)
        drawShot(canvas, board, images, f.index, f.progress, 1f, t, w, h)
        if (f.next != null) drawShot(canvas, board, images, f.next, f.nextProgress, f.nextAlpha, t, w, h)

        drawVignette(canvas, w, h)
        drawGrain(canvas, t, w, h)

        if (captions) {
            drawCaption(canvas, board, timeline, f.index, f.progress, 1f - f.nextAlpha, w, h)
            if (f.next != null) drawCaption(canvas, board, timeline, f.next, f.nextProgress, f.nextAlpha, w, h)
        }
    }

    private fun drawShot(canvas: Canvas, board: Storyboard, images: List<Bitmap?>, i: Int, progress: Float, alpha: Float, t: Float, w: Int, h: Int) {
        val shot = board.shots[i]
        val bmp = images.getOrNull(i)
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        if (bmp != null && !bmp.isRecycled) {
            val kb = KenBurns.at(shot.camera, progress)
            val p = KenBurns.placement(kb, bmp.width, bmp.height, w, h)
            matrix.setScale(p[2], p[2])
            matrix.postTranslate(p[0], p[1])
            imagePaint.alpha = a
            canvas.drawBitmap(bmp, matrix, imagePaint)
        } else {
            dotPaint.color = Color.argb(a, 24, 24, 28)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), dotPaint)
        }
        drawEffect(canvas, shot.effect, t, alpha, w, h)
    }

    private fun drawEffect(canvas: Canvas, effect: Effect, t: Float, alpha: Float, w: Int, h: Int) {
        val n = Particles.count(effect)
        if (n == 0) return
        for (i in 0 until n) {
            val p = Particles.compute(effect, i, t, particle)
            val a = (p.alpha * alpha * 255).toInt().coerceIn(0, 255)
            if (a == 0) continue
            val x = p.x * w
            val y = p.y * h
            when (effect) {
                Effect.RAIN -> {
                    linePaint.color = Color.argb(a, 200, 210, 225)
                    linePaint.strokeWidth = (p.size * h).coerceAtLeast(1f)
                    val len = p.length * h
                    canvas.drawLine(x, y, x - len * 0.13f, y + len, linePaint)
                }
                Effect.EMBERS -> {
                    dotPaint.color = Color.argb(a, 255, 140 + (Particles.rand(i, 5) * 80).toInt(), 40)
                    canvas.drawCircle(x, y, p.size * h, dotPaint)
                }
                Effect.SNOW -> {
                    dotPaint.color = Color.argb(a, 245, 248, 255)
                    canvas.drawCircle(x, y, p.size * h, dotPaint)
                }
                Effect.DUST -> {
                    dotPaint.color = Color.argb(a, 225, 210, 185)
                    canvas.drawCircle(x, y, p.size * h, dotPaint)
                }
                Effect.SMOKE, Effect.FOG -> {
                    val r = p.size * h
                    blobPaint.alpha = a
                    blobPaint.colorFilter = if (effect == Effect.SMOKE) smokeFilter else null
                    matrix.setScale(2 * r / blob.width, 1.2f * r / blob.height)
                    matrix.postTranslate(x - r, y - 0.6f * r)
                    canvas.drawBitmap(blob, matrix, blobPaint)
                    // Ekranın kenarından taşan dumanı diğer taraftan da göster (kesintisiz akış).
                    matrix.postTranslate(if (x > w / 2f) -w.toFloat() else w.toFloat(), 0f)
                    canvas.drawBitmap(blob, matrix, blobPaint)
                }
                Effect.NONE -> Unit
            }
        }
    }

    private fun drawVignette(canvas: Canvas, w: Int, h: Int) {
        if (w != vignetteW || h != vignetteH) {
            vignetteW = w
            vignetteH = h
            val r = maxOf(w, h) * 0.75f
            vignettePaint.shader = RadialGradient(
                w / 2f, h / 2f, r,
                intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(170, 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), vignettePaint)
    }

    private fun drawGrain(canvas: Canvas, t: Float, w: Int, h: Int) {
        // Her karede gren dokusunu farklı kaydır (deterministik).
        val frame = (t * 24).toInt()
        grainMatrix.setTranslate(Particles.rand(frame, 7) * NOISE_SIZE, Particles.rand(frame, 8) * NOISE_SIZE)
        val s = maxOf(1f, h / 1280f)
        grainMatrix.preScale(s, s)
        grainShader.setLocalMatrix(grainMatrix)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), grainPaint)
    }

    private fun drawCaption(canvas: Canvas, board: Storyboard, timeline: Timeline, i: Int, progress: Float, alpha: Float, w: Int, h: Int) {
        val text = board.shots[i].caption
        if (text.isEmpty() || alpha <= 0.01f) return
        val a = timeline.captionAlpha(progress, timeline.durations[i]) * alpha
        if (a <= 0.01f) return
        val portrait = h > w
        captionPaint.textSize = (if (portrait) w * 0.058f else h * 0.055f)
        captionPaint.alpha = (a * 255).toInt()
        val maxW = (w * 0.86f).toInt()
        val key = "$text|$maxW|${captionPaint.textSize}"
        val layout = captionLayout?.takeIf { captionKey == key }
            ?: StaticLayout.Builder.obtain(text, 0, text.length, captionPaint, maxW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(3)
                .build()
                .also { captionLayout = it; captionKey = key }
        // Dikeyde platform arayüzü (beğen/yorum, açıklama) alt kısmı kapattığı için daha yukarıda.
        val bottom = if (portrait) h * 0.74f else h * 0.9f
        canvas.save()
        canvas.translate((w - maxW) / 2f, bottom - layout.height)
        layout.draw(canvas)
        canvas.restore()
    }

    companion object {
        private const val NOISE_SIZE = 256

        private fun cinematicMatrix(): ColorMatrix {
            val sat = ColorMatrix().apply { setSaturation(0.9f) }
            val c = 1.08f
            val off = 128f * (1f - c)
            val contrast = ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, off,
                    0f, c, 0f, 0f, off,
                    0f, 0f, c, 0f, off,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            sat.postConcat(contrast)
            return sat
        }

        private fun makeNoise(): Bitmap {
            val rnd = Random(42)
            val px = IntArray(NOISE_SIZE * NOISE_SIZE) {
                val v = rnd.nextInt(256)
                Color.argb(14, v, v, v)
            }
            return Bitmap.createBitmap(px, NOISE_SIZE, NOISE_SIZE, Bitmap.Config.ARGB_8888)
        }

        /** Duman/sis için yumuşak kenarlı beyaz leke. */
        private fun makeBlob(): Bitmap {
            val size = 128
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    size / 2f, size / 2f, size / 2f,
                    intArrayOf(Color.argb(255, 210, 210, 210), Color.argb(110, 200, 200, 200), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
                )
            }
            c.drawCircle(size / 2f, size / 2f, size / 2f, p)
            return bmp
        }
    }
}
