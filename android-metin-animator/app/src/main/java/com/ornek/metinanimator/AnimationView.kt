package com.ornek.metinanimator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Bir [Scene] alır ve onu kare kare canlandırır.
 *
 * Çizim tamamen [Canvas] ilkelleriyle (daire, dikdörtgen, çizgi, yol) yapılır;
 * hiçbir dış görsel kaynağa veya internete ihtiyaç yoktur.
 *
 * Animasyon zamana bağlıdır: her karede geçen süre `t` (saniye) hesaplanır ve
 * tüm öğeler bu `t`'ye göre konumlanır. Böylece kare hızından bağımsız,
 * akıcı ve tekrarlanabilir bir hareket elde edilir.
 */
class AnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var scene: Scene? = null
    private var startTime: Long = 0L

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val tmpRect = RectF()
    private val path = Path()

    /** Yeni bir sahne oynatmaya başla. */
    fun playScene(newScene: Scene) {
        scene = newScene
        startTime = SystemClock.uptimeMillis()
        invalidate()
    }

    /** Sahneyi temizle (boş gökyüzü). */
    fun clear() {
        scene = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val s = scene
        if (s == null) {
            canvas.drawColor(Color.parseColor("#90A4AE"))
            return
        }

        val t = (SystemClock.uptimeMillis() - startTime) / 1000f
        val groundY = h * 0.80f

        drawSky(canvas, w, h, s.background)

        if (s.background == Background.NIGHT || s.elements.contains(ElementType.STAR)) {
            drawStars(canvas, w, h, t)
        }
        if (s.elements.contains(ElementType.MOON)) drawMoon(canvas, w, h, t)
        if (s.elements.contains(ElementType.SUN)) drawSun(canvas, w, h, t)
        if (s.elements.contains(ElementType.MOUNTAIN)) drawMountains(canvas, w, groundY)
        if (s.elements.contains(ElementType.CLOUD)) drawClouds(canvas, w, h, t)

        drawGround(canvas, w, h, groundY, s.ground, t)

        if (s.elements.contains(ElementType.TREE)) drawTree(canvas, w * 0.16f, groundY, t)
        if (s.elements.contains(ElementType.FLOWER)) drawFlowers(canvas, w, groundY, t)
        if (s.elements.contains(ElementType.HOUSE)) drawHouse(canvas, w * 0.52f, groundY, t)
        if (s.elements.contains(ElementType.PERSON)) drawPerson(canvas, w * 0.74f, groundY, t)
        if (s.elements.contains(ElementType.CAR)) drawCar(canvas, w, groundY, t)

        if (s.ground == Ground.WATER && s.elements.contains(ElementType.FISH)) {
            drawFish(canvas, w, h, groundY, t)
        }
        if (s.elements.contains(ElementType.BALLOON)) drawBalloon(canvas, w, h, t)
        if (s.elements.contains(ElementType.HEART)) drawHearts(canvas, w, h, t)
        if (s.elements.contains(ElementType.BIRD)) drawBirds(canvas, w, h, t)

        if (s.elements.contains(ElementType.RAIN)) drawRain(canvas, w, h, t)
        if (s.elements.contains(ElementType.SNOW)) drawSnow(canvas, w, h, t)

        drawCaption(canvas, w, h, s.caption, t)

        // Bir sonraki kareyi iste (sürekli döngü).
        postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------------
    // Gökyüzü ve zemin
    // ---------------------------------------------------------------------

    private fun drawSky(canvas: Canvas, w: Float, h: Float, bg: Background) {
        val (top, bottom) = when (bg) {
            Background.DAY -> Color.parseColor("#4FC3F7") to Color.parseColor("#E1F5FE")
            Background.NIGHT -> Color.parseColor("#0B1026") to Color.parseColor("#223366")
            Background.SUNSET -> Color.parseColor("#FF7043") to Color.parseColor("#FFD54F")
        }
        paint.shader = LinearGradient(0f, 0f, 0f, h, top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun drawGround(canvas: Canvas, w: Float, h: Float, groundY: Float, ground: Ground, t: Float) {
        when (ground) {
            Ground.GRASS -> {
                paint.color = Color.parseColor("#66BB6A")
                canvas.drawRect(0f, groundY, w, h, paint)
                paint.color = Color.parseColor("#4CAF50")
                canvas.drawRect(0f, groundY, w, groundY + 10f, paint)
            }
            Ground.SAND -> {
                paint.color = Color.parseColor("#F4D59A")
                canvas.drawRect(0f, groundY, w, h, paint)
            }
            Ground.WATER -> {
                paint.color = Color.parseColor("#1976D2")
                canvas.drawRect(0f, groundY, w, h, paint)
                // Dalga çizgileri
                strokePaint.color = Color.parseColor("#64B5F6")
                strokePaint.strokeWidth = 3f
                var y = groundY + 18f
                while (y < h) {
                    path.reset()
                    path.moveTo(0f, y)
                    var x = 0f
                    while (x <= w) {
                        path.lineTo(x, y + sin((x / 40f) + t * 2f) * 4f)
                        x += 16f
                    }
                    canvas.drawPath(path, strokePaint)
                    y += 26f
                }
            }
            Ground.NONE -> { /* zemin yok */ }
        }
    }

    // ---------------------------------------------------------------------
    // Gökyüzü öğeleri
    // ---------------------------------------------------------------------

    private fun drawSun(canvas: Canvas, w: Float, h: Float, t: Float) {
        val targetY = h * 0.22f
        val cy = lerp(h * 1.1f, targetY, easeOut(min(1f, t / 1.5f)))
        val cx = w * 0.80f
        val r = min(w, h) * 0.09f

        // Işınlar (döner)
        strokePaint.color = Color.parseColor("#FFE082")
        strokePaint.strokeWidth = 6f
        val rays = 12
        for (i in 0 until rays) {
            val a = (i.toFloat() / rays) * 2f * Math.PI.toFloat() + t * 0.6f
            val inner = r * 1.25f
            val outer = r * (1.6f + 0.12f * sin(t * 3f + i))
            canvas.drawLine(
                cx + cos(a) * inner, cy + sin(a) * inner,
                cx + cos(a) * outer, cy + sin(a) * outer, strokePaint
            )
        }
        paint.color = Color.parseColor("#FFD54F")
        canvas.drawCircle(cx, cy, r, paint)
    }

    private fun drawMoon(canvas: Canvas, w: Float, h: Float, t: Float) {
        val targetY = h * 0.20f
        val cy = lerp(h * 1.1f, targetY, easeOut(min(1f, t / 1.5f)))
        val cx = w * 0.78f
        val r = min(w, h) * 0.08f
        paint.color = Color.parseColor("#FFF9C4")
        canvas.drawCircle(cx, cy, r, paint)
        // Hilal etkisi: gökyüzü renginde ikinci bir daire ile "ısırık" al
        paint.color = Color.parseColor("#0B1026")
        canvas.drawCircle(cx + r * 0.45f, cy - r * 0.2f, r * 0.9f, paint)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float, t: Float) {
        val count = 60
        for (i in 0 until count) {
            val rnd = Random(i * 104729L + 7L)
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h * 0.6f
            val baseR = 1.5f + rnd.nextFloat() * 2.5f
            val phase = rnd.nextFloat() * 6.28f
            val tw = 0.4f + 0.6f * abs(sin(t * 2f + phase))
            paint.color = Color.WHITE
            paint.alpha = (tw * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, baseR, paint)
        }
        paint.alpha = 255
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, t: Float) {
        val clouds = 3
        for (i in 0 until clouds) {
            val rnd = Random(i * 7331L + 3L)
            val speed = 15f + rnd.nextFloat() * 25f
            val baseY = h * (0.12f + rnd.nextFloat() * 0.25f)
            val scale = 0.7f + rnd.nextFloat() * 0.6f
            val span = w + 320f
            var cx = (rnd.nextFloat() * span + t * speed) % span - 160f
            drawCloud(canvas, cx, baseY, 44f * scale)
        }
    }

    private fun drawCloud(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        paint.color = Color.parseColor("#FFFFFF")
        paint.alpha = 235
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawCircle(cx + r * 0.9f, cy + r * 0.15f, r * 0.8f, paint)
        canvas.drawCircle(cx - r * 0.9f, cy + r * 0.2f, r * 0.7f, paint)
        tmpRect.set(cx - r * 1.3f, cy + r * 0.2f, cx + r * 1.4f, cy + r * 0.9f)
        canvas.drawRoundRect(tmpRect, r, r, paint)
        paint.alpha = 255
    }

    private fun drawMountains(canvas: Canvas, w: Float, groundY: Float) {
        paint.color = Color.parseColor("#78909C")
        path.reset()
        path.moveTo(0f, groundY)
        path.lineTo(w * 0.25f, groundY - w * 0.28f)
        path.lineTo(w * 0.5f, groundY)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = Color.parseColor("#90A4AE")
        path.reset()
        path.moveTo(w * 0.35f, groundY)
        path.lineTo(w * 0.62f, groundY - w * 0.34f)
        path.lineTo(w * 0.9f, groundY)
        path.close()
        canvas.drawPath(path, paint)

        // Kar tepeleri
        paint.color = Color.WHITE
        path.reset()
        path.moveTo(w * 0.55f, groundY - w * 0.24f)
        path.lineTo(w * 0.62f, groundY - w * 0.34f)
        path.lineTo(w * 0.69f, groundY - w * 0.24f)
        path.lineTo(w * 0.64f, groundY - w * 0.26f)
        path.lineTo(w * 0.60f, groundY - w * 0.26f)
        path.close()
        canvas.drawPath(path, paint)
    }

    // ---------------------------------------------------------------------
    // Yer öğeleri
    // ---------------------------------------------------------------------

    private fun drawTree(canvas: Canvas, cx: Float, groundY: Float, t: Float) {
        val grow = easeOut(min(1f, t / 1.2f))
        val height = 150f * grow
        val sway = sin(t * 1.5f) * 4f
        // Gövde
        paint.color = Color.parseColor("#795548")
        tmpRect.set(cx - 12f, groundY - height, cx + 12f, groundY)
        canvas.drawRect(tmpRect, paint)
        // Yapraklar
        paint.color = Color.parseColor("#2E7D32")
        val topY = groundY - height
        canvas.drawCircle(cx + sway, topY - 10f, 46f * grow, paint)
        canvas.drawCircle(cx - 34f + sway, topY + 20f, 34f * grow, paint)
        canvas.drawCircle(cx + 34f + sway, topY + 20f, 34f * grow, paint)
        paint.color = Color.parseColor("#43A047")
        canvas.drawCircle(cx + sway, topY + 6f, 32f * grow, paint)
    }

    private fun drawFlowers(canvas: Canvas, w: Float, groundY: Float, t: Float) {
        val colors = intArrayOf(
            Color.parseColor("#E91E63"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#9C27B0")
        )
        val xs = floatArrayOf(w * 0.30f, w * 0.40f, w * 0.46f)
        for (k in xs.indices) {
            val cx = xs[k]
            val grow = easeOut(min(1f, (t - k * 0.2f).coerceAtLeast(0f) / 1f))
            if (grow <= 0f) continue
            val height = 70f * grow
            val sway = sin(t * 2f + k) * 5f
            val topX = cx + sway
            val topY = groundY - height
            // Sap
            strokePaint.color = Color.parseColor("#388E3C")
            strokePaint.strokeWidth = 5f
            canvas.drawLine(cx, groundY, topX, topY, strokePaint)
            // Taç yaprakları
            paint.color = colors[k % colors.size]
            val pr = 11f * grow
            for (i in 0 until 6) {
                val a = i * (Math.PI.toFloat() / 3f)
                canvas.drawCircle(topX + cos(a) * pr, topY + sin(a) * pr, pr * 0.9f, paint)
            }
            paint.color = Color.parseColor("#FFEB3B")
            canvas.drawCircle(topX, topY, pr * 0.8f, paint)
        }
    }

    private fun drawHouse(canvas: Canvas, cx: Float, groundY: Float, t: Float) {
        val scale = easeOut(min(1f, t / 1f))
        if (scale <= 0f) return
        val bw = 150f * scale
        val bh = 120f * scale
        val left = cx - bw / 2f
        val top = groundY - bh
        // Gövde
        paint.color = Color.parseColor("#FFCCBC")
        canvas.drawRect(left, top, left + bw, groundY, paint)
        // Çatı
        paint.color = Color.parseColor("#BF360C")
        path.reset()
        path.moveTo(left - 10f, top)
        path.lineTo(cx, top - 60f * scale)
        path.lineTo(left + bw + 10f, top)
        path.close()
        canvas.drawPath(path, paint)
        // Kapı
        paint.color = Color.parseColor("#6D4C41")
        canvas.drawRect(cx - 20f, groundY - 60f * scale, cx + 20f, groundY, paint)
        // Pencere
        paint.color = Color.parseColor("#4FC3F7")
        canvas.drawRect(left + 18f, top + 20f, left + 58f, top + 60f, paint)
    }

    private fun drawPerson(canvas: Canvas, cx: Float, groundY: Float, t: Float) {
        val appear = easeOut(min(1f, t / 0.8f))
        if (appear <= 0f) return
        val scale = appear
        val headR = 18f * scale
        val bodyTop = groundY - 120f * scale
        val headCy = bodyTop - headR
        strokePaint.color = Color.parseColor("#3E2723")
        strokePaint.strokeWidth = 8f * scale
        // Baş
        paint.color = Color.parseColor("#FFCC80")
        canvas.drawCircle(cx, headCy, headR, paint)
        // Gövde
        val bodyBottom = groundY - 50f * scale
        canvas.drawLine(cx, bodyTop, cx, bodyBottom, strokePaint)
        // Bacaklar
        canvas.drawLine(cx, bodyBottom, cx - 18f * scale, groundY, strokePaint)
        canvas.drawLine(cx, bodyBottom, cx + 18f * scale, groundY, strokePaint)
        // Kollar — biri el sallıyor
        val wave = sin(t * 6f) * 0.5f
        val armY = bodyTop + 24f * scale
        canvas.drawLine(cx, armY, cx - 26f * scale, armY + 20f * scale, strokePaint)
        canvas.drawLine(
            cx, armY,
            cx + 26f * scale, armY - (20f + 10f * wave) * scale, strokePaint
        )
    }

    private fun drawCar(canvas: Canvas, w: Float, groundY: Float, t: Float) {
        val carW = 160f
        val speed = 180f
        val span = w + carW * 2f
        val x = (t * speed) % span - carW
        val baseY = groundY - 6f
        // Gövde
        paint.color = Color.parseColor("#D32F2F")
        tmpRect.set(x, baseY - 42f, x + carW, baseY - 6f)
        canvas.drawRoundRect(tmpRect, 14f, 14f, paint)
        // Tavan
        paint.color = Color.parseColor("#E53935")
        tmpRect.set(x + 32f, baseY - 70f, x + carW - 36f, baseY - 40f)
        canvas.drawRoundRect(tmpRect, 12f, 12f, paint)
        // Camlar
        paint.color = Color.parseColor("#B3E5FC")
        tmpRect.set(x + 40f, baseY - 64f, x + carW - 44f, baseY - 44f)
        canvas.drawRoundRect(tmpRect, 6f, 6f, paint)
        // Tekerlekler (dönen)
        val wheelR = 18f
        drawWheel(canvas, x + 36f, baseY, wheelR, t * speed / wheelR)
        drawWheel(canvas, x + carW - 36f, baseY, wheelR, t * speed / wheelR)
    }

    private fun drawWheel(canvas: Canvas, cx: Float, cy: Float, r: Float, angle: Float) {
        paint.color = Color.parseColor("#212121")
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = Color.parseColor("#BDBDBD")
        canvas.drawCircle(cx, cy, r * 0.4f, paint)
        strokePaint.color = Color.parseColor("#757575")
        strokePaint.strokeWidth = 3f
        for (i in 0 until 4) {
            val a = angle + i * (Math.PI.toFloat() / 2f)
            canvas.drawLine(cx, cy, cx + cos(a) * r, cy + sin(a) * r, strokePaint)
        }
    }

    // ---------------------------------------------------------------------
    // Hava / su öğeleri
    // ---------------------------------------------------------------------

    private fun drawBirds(canvas: Canvas, w: Float, h: Float, t: Float) {
        strokePaint.color = Color.parseColor("#37474F")
        strokePaint.strokeWidth = 4f
        val count = 4
        for (i in 0 until count) {
            val rnd = Random(i * 911L + 1L)
            val speed = 70f + rnd.nextFloat() * 60f
            val span = w + 120f
            val x = (rnd.nextFloat() * span + t * speed) % span - 60f
            val y = h * (0.15f + rnd.nextFloat() * 0.2f) + sin(t * 2f + i) * 10f
            val flap = sin(t * 10f + i) * 10f
            val s = 16f
            path.reset()
            path.moveTo(x - s, y)
            path.quadTo(x - s * 0.4f, y - 8f - flap, x, y)
            path.quadTo(x + s * 0.4f, y - 8f - flap, x + s, y)
            canvas.drawPath(path, strokePaint)
        }
    }

    private fun drawFish(canvas: Canvas, w: Float, h: Float, groundY: Float, t: Float) {
        val count = 3
        val colors = intArrayOf(
            Color.parseColor("#FF7043"),
            Color.parseColor("#FFCA28"),
            Color.parseColor("#26C6DA")
        )
        for (i in 0 until count) {
            val rnd = Random(i * 1777L + 5L)
            val speed = 90f + rnd.nextFloat() * 60f
            val span = w + 120f
            val x = (rnd.nextFloat() * span + t * speed) % span - 60f
            val y = lerp(groundY + 40f, h - 30f, rnd.nextFloat()) + sin(t * 3f + i) * 8f
            paint.color = colors[i % colors.size]
            // Gövde
            tmpRect.set(x - 24f, y - 12f, x + 24f, y + 12f)
            canvas.drawOval(tmpRect, paint)
            // Kuyruk (sallanan)
            val wig = sin(t * 8f + i) * 6f
            path.reset()
            path.moveTo(x + 22f, y)
            path.lineTo(x + 40f, y - 12f + wig)
            path.lineTo(x + 40f, y + 12f + wig)
            path.close()
            canvas.drawPath(path, paint)
            // Göz
            paint.color = Color.WHITE
            canvas.drawCircle(x - 12f, y - 3f, 3f, paint)
        }
    }

    private fun drawBalloon(canvas: Canvas, w: Float, h: Float, t: Float) {
        val count = 3
        val colors = intArrayOf(
            Color.parseColor("#EC407A"),
            Color.parseColor("#AB47BC"),
            Color.parseColor("#42A5F5")
        )
        for (i in 0 until count) {
            val baseX = w * (0.2f + 0.3f * i)
            val range = h + 160f
            val speed = 50f + i * 14f
            val y = h + 80f - ((t * speed + i * 120f) % range)
            val x = baseX + sin(t * 1.2f + i) * 20f
            paint.color = colors[i % colors.size]
            tmpRect.set(x - 24f, y - 32f, x + 24f, y + 24f)
            canvas.drawOval(tmpRect, paint)
            strokePaint.color = Color.parseColor("#9E9E9E")
            strokePaint.strokeWidth = 2f
            canvas.drawLine(x, y + 24f, x, y + 54f, strokePaint)
        }
    }

    private fun drawHearts(canvas: Canvas, w: Float, h: Float, t: Float) {
        val count = 6
        for (i in 0 until count) {
            val rnd = Random(i * 1313L + 9L)
            val baseX = rnd.nextFloat() * w
            val range = h + 120f
            val speed = 60f + rnd.nextFloat() * 50f
            val y = h + 60f - ((t * speed + rnd.nextFloat() * range) % range)
            val x = baseX + sin(t * 2f + i) * 18f
            val pulse = 1f + 0.12f * sin(t * 6f + i)
            val s = (14f + rnd.nextFloat() * 10f) * pulse
            paint.color = Color.parseColor("#E53935")
            drawHeart(canvas, x, y, s)
        }
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy + s * 0.75f)
        path.cubicTo(cx - s * 1.3f, cy - s * 0.3f, cx - s * 0.5f, cy - s * 1.1f, cx, cy - s * 0.35f)
        path.cubicTo(cx + s * 0.5f, cy - s * 1.1f, cx + s * 1.3f, cy - s * 0.3f, cx, cy + s * 0.75f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float, t: Float) {
        strokePaint.color = Color.parseColor("#B3E5FC")
        strokePaint.strokeWidth = 3f
        val count = 140
        for (i in 0 until count) {
            val rnd = Random(i * 7919L + 2L)
            val x = rnd.nextFloat() * w
            val speed = 700f + rnd.nextFloat() * 500f
            val len = 16f + rnd.nextFloat() * 14f
            val startY = rnd.nextFloat() * h
            val y = (startY + t * speed) % (h + len)
            canvas.drawLine(x, y, x - 5f, y + len, strokePaint)
        }
    }

    private fun drawSnow(canvas: Canvas, w: Float, h: Float, t: Float) {
        paint.color = Color.WHITE
        val count = 110
        for (i in 0 until count) {
            val rnd = Random(i * 6131L + 4L)
            val baseX = rnd.nextFloat() * w
            val speed = 90f + rnd.nextFloat() * 90f
            val r = 2.5f + rnd.nextFloat() * 3.5f
            val startY = rnd.nextFloat() * h
            val y = (startY + t * speed) % (h + r)
            val x = baseX + sin(t * 1.5f + i) * 14f
            canvas.drawCircle(x, y, r, paint)
        }
    }

    // ---------------------------------------------------------------------
    // Alt yazı (daktilo efektiyle)
    // ---------------------------------------------------------------------

    private fun drawCaption(canvas: Canvas, w: Float, h: Float, text: String, t: Float) {
        if (text.isEmpty()) return
        val visibleChars = min(text.length, (t * 26f).toInt())
        if (visibleChars <= 0) return
        val shown = text.substring(0, visibleChars)

        textPaint.textSize = (min(w, h) * 0.045f).coerceIn(28f, 54f)
        val padding = 24f
        val maxWidth = (w - padding * 2).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(shown, 0, shown.length, textPaint, maxWidth)
            .build()

        val boxHeight = layout.height + padding
        val top = h - boxHeight - 16f

        // Yarı saydam arka plan
        paint.color = Color.parseColor("#AA000000")
        tmpRect.set(12f, top, w - 12f, h - 16f)
        canvas.drawRoundRect(tmpRect, 18f, 18f, paint)

        canvas.save()
        canvas.translate(padding, top + padding / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    // ---------------------------------------------------------------------
    // Yardımcılar
    // ---------------------------------------------------------------------

    private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

    private fun easeOut(f: Float): Float {
        val x = f.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x)
    }
}
