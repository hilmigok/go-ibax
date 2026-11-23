package com.ornek.yzgercekci

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Bir yüzün görüntüdeki önemli noktaları (piksel). Yüz bulucu (ML Kit) doldurur;
 * ağız noktaları bulunamazsa göz konumlarından tahmin edilir.
 */
data class FaceRig(
    val leftEyeX: Float, val leftEyeY: Float,
    val rightEyeX: Float, val rightEyeY: Float,
    val mouthLeftX: Float, val mouthLeftY: Float,
    val mouthRightX: Float, val mouthRightY: Float,
    val mouthBottomX: Float, val mouthBottomY: Float,
    /** Her yüzün farklı zamanda göz kırpması için tohum. */
    val seed: Int
) {
    /** Gözler arası mesafe: tüm ölçülerin birimi. */
    val eyeDist: Float get() = max(1f, hypot(rightEyeX - leftEyeX, rightEyeY - leftEyeY))
    val centerX: Float get() = (leftEyeX + rightEyeX + mouthLeftX + mouthRightX) / 4f
    val centerY: Float get() = (leftEyeY + rightEyeY + mouthLeftY + mouthRightY) / 4f

    /** Mimiklerin uygulanacağı kare bölge (görüntü pikseli): [left, top, right, bottom]. */
    fun patch(imgW: Int, imgH: Int): IntArray {
        val e = eyeDist
        val l = (min(leftEyeX, rightEyeX) - 1.0f * e).toInt().coerceIn(0, imgW - 1)
        val r = (max(leftEyeX, rightEyeX) + 1.0f * e).toInt().coerceIn(l + 1, imgW)
        val t = (min(leftEyeY, rightEyeY) - 1.1f * e).toInt().coerceIn(0, imgH - 1)
        val b = (maxOf(mouthLeftY, mouthRightY, mouthBottomY) + 0.9f * e).toInt().coerceIn(t + 1, imgH)
        return intArrayOf(l, t, r, b)
    }

    companion object {
        /** Ağız noktaları yoksa yüz oranlarından tahmin eder. */
        fun fromEyes(lx: Float, ly: Float, rx: Float, ry: Float, seed: Int): FaceRig {
            val e = hypot(rx - lx, ry - ly)
            val cx = (lx + rx) / 2f
            val cy = (ly + ry) / 2f
            val my = cy + 1.05f * e
            return FaceRig(lx, ly, rx, ry, cx - 0.42f * e, my, cx + 0.42f * e, my, cx, my + 0.18f * e, seed)
        }
    }
}

/**
 * Fotoğraftaki yüze ince mimikler uygular: göz kırpma, nefes, hafif baş hareketi ve
 * duyguya göre kaş/göz/ağız hareketleri. Görüntü, kontrol noktaları etrafında yumuşak
 * (Gauss) bir yer değiştirme alanıyla esnetilir. Bölge kenarında yer değiştirme sıfırdır;
 * böylece esnetilen yüz, görüntünün geri kalanıyla dikişsiz birleşir.
 * Saf hesaplama: deterministik ve birim testi yapılabilir.
 */
class Mimic(private val face: FaceRig, private val expression: Expression) {

    private class Ctrl {
        var x = 0f; var y = 0f; var dx = 0f; var dy = 0f; var r2 = 1f
    }

    private val ctrls = Array(MAX_CTRL) { Ctrl() }
    private var count = 0
    private var rotation = 0f
    private var lift = 0f

    /** t: video zamanı (sn), progress: karenin ilerleyişi (0..1). Sonraki [displace] çağrıları için hazırlar. */
    fun prepare(t: Float, progress: Float) {
        count = 0
        val e = face.eyeDist
        // İfade karenin ilk yarısında yavaşça belirir.
        val k = Timeline.smooth(min(1f, progress * 2.2f + 0.15f))

        // Göz kırpma: her yüz kendi ritminde, ~4 sn'de bir, 0,2 sn sürer.
        val period = 3.4f + 2.2f * Particles.rand(face.seed, 11)
        val phase = (t + Particles.rand(face.seed, 12) * period) % period
        val blink = if (phase < BLINK) sin(PI * phase / BLINK).toFloat() else 0f
        // Ağlarken gözler daha sık, yarı kısık.
        val squint = when (expression) {
            Expression.CRYING -> 0.25f * k
            Expression.SMILE -> 0.15f * k
            Expression.ANGRY -> 0.12f * k
            else -> 0f
        }
        val widen = when (expression) {
            Expression.FEAR, Expression.SHOCK -> 0.22f * k
            else -> 0f
        }
        val lid = (max(blink, squint) - widen).coerceIn(-0.3f, 1f)
        for (eye in 0..1) {
            val ex = if (eye == 0) face.leftEyeX else face.rightEyeX
            val ey = if (eye == 0) face.leftEyeY else face.rightEyeY
            // Üst göz kapağı aşağı (kırpma) ya da yukarı (korku).
            add(ex, ey - 0.13f * e, 0f, lid * 0.16f * e, 0.2f * e)
            // Alt kapak hafifçe yukarı.
            add(ex, ey + 0.1f * e, 0f, -max(0f, lid) * 0.04f * e, 0.16f * e)
        }

        // Kaşlar: göz noktasının üstünde tahmin edilir. iç = burna yakın uç.
        val inner = when (expression) {
            Expression.SAD, Expression.CRYING -> -0.07f
            Expression.FEAR, Expression.SHOCK -> -0.09f
            Expression.ANGRY -> 0.07f
            else -> 0f
        } * k * e
        val outer = when (expression) {
            Expression.FEAR, Expression.SHOCK -> -0.07f
            Expression.SAD, Expression.CRYING -> 0.015f
            Expression.ANGRY -> -0.02f
            else -> 0f
        } * k * e
        val midX = (face.leftEyeX + face.rightEyeX) / 2f
        for (eye in 0..1) {
            val ex = if (eye == 0) face.leftEyeX else face.rightEyeX
            val ey = if (eye == 0) face.leftEyeY else face.rightEyeY
            val toward = if (midX > ex) 1f else -1f
            val by = ey - 0.36f * e
            add(ex + toward * 0.2f * e, by, toward * (if (expression == Expression.ANGRY) 0.02f * k * e else 0f), inner, 0.17f * e)
            add(ex - toward * 0.22f * e, by, 0f, outer, 0.17f * e)
        }

        // Ağız köşeleri.
        val cornerDy = when (expression) {
            Expression.SAD -> 0.07f
            Expression.CRYING -> 0.09f
            Expression.FEAR -> 0.03f
            Expression.ANGRY -> 0.02f
            Expression.SMILE -> -0.08f
            else -> 0f
        } * k * e
        val cornerDx = when (expression) {
            Expression.SMILE -> 0.05f
            Expression.FEAR -> 0.04f
            Expression.SHOCK -> -0.03f
            else -> 0f
        } * k * e
        val ml = if (face.mouthLeftX < face.mouthRightX) 1f else -1f
        add(face.mouthLeftX, face.mouthLeftY, -ml * cornerDx, cornerDy, 0.2f * e)
        add(face.mouthRightX, face.mouthRightY, ml * cornerDx, cornerDy, 0.2f * e)

        // Alt dudak: şaşkınlıkta ağız açılır, ağlarken titrer.
        var lip = when (expression) {
            Expression.SHOCK -> 0.13f * k
            Expression.FEAR -> 0.05f * k
            Expression.CRYING -> 0.03f * k
            else -> 0f
        }
        if (expression == Expression.CRYING) lip += 0.018f * k * sin(t * 2f * PI.toFloat() * 6.5f)
        add(face.mouthBottomX, face.mouthBottomY + 0.05f * e, 0f, lip * e, 0.2f * e)

        // Nefes: yüz hafifçe yukarı-aşağı; ağlarken hıçkırık gibi daha hızlı.
        val breathHz = if (expression == Expression.CRYING) 0.75f else 0.25f
        val breathAmp = if (expression == Expression.CRYING) 0.025f else 0.012f
        lift = breathAmp * e * sin(t * 2f * PI.toFloat() * breathHz + Particles.rand(face.seed, 13) * 6.28f)
        // Baş çok hafif sallanır (±1°).
        rotation = (1.0f * PI.toFloat() / 180f) * sin(t * 0.55f + Particles.rand(face.seed, 14) * 6.28f)
    }

    private fun add(x: Float, y: Float, dx: Float, dy: Float, r: Float) {
        if (count >= MAX_CTRL || (abs(dx) < 1e-4f && abs(dy) < 1e-4f)) return
        val c = ctrls[count++]
        c.x = x; c.y = y; c.dx = dx; c.dy = dy; c.r2 = r * r
    }

    /**
     * (x, y) noktasının yeni konumunu [out]'a yazar. [window] 0..1: bölge kenarında 0,
     * içeride 1 (dikişsiz birleşme için yer değiştirmeyi bununla çarpılır).
     */
    fun displace(x: Float, y: Float, window: Float, out: FloatArray) {
        var dx = 0f
        var dy = 0f
        for (i in 0 until count) {
            val c = ctrls[i]
            val ddx = x - c.x
            val ddy = y - c.y
            val w = exp(-(ddx * ddx + ddy * ddy) / c.r2)
            dx += c.dx * w
            dy += c.dy * w
        }
        // Baş dönmesi + nefes: çene altındaki noktanın etrafında.
        val px = face.centerX
        val py = face.centerY + face.eyeDist
        val rx = x - px
        val ry = y - py
        val cs = cos(rotation)
        val sn = sin(rotation)
        dx += rx * cs - ry * sn - rx
        dy += rx * sn + ry * cs - ry + lift
        out[0] = x + dx * window
        out[1] = y + dy * window
    }

    companion object {
        private const val MAX_CTRL = 16
        private const val BLINK = 0.2f

        /** Bölge kenarında 0, iç kısımda 1 olan yumuşak pencere (u, v: 0..1). */
        fun window(u: Float, v: Float): Float {
            val edge = min(min(u, 1f - u), min(v, 1f - v))
            return Timeline.smooth((edge / 0.22f).coerceIn(0f, 1f))
        }
    }
}
