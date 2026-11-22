package com.ornek.yzgercekci

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Karelerin zaman çizelgesi. Ardışık kareler [fade] saniye boyunca üst üste biner
 * (çapraz geçiş). Saf hesaplama; Android'e bağımlı değil, birim testi yapılabilir.
 */
class Timeline(durations: List<Float>, fadeSeconds: Float = DEFAULT_FADE) {

    val starts: FloatArray
    val durations: FloatArray = durations.toFloatArray()
    val fade: Float
    val total: Float

    init {
        require(durations.isNotEmpty()) { "en az bir kare gerekli" }
        // Geçiş, en kısa karenin yarısından uzun olamaz.
        fade = min(fadeSeconds, (durations.minOrNull() ?: 1f) / 2f).coerceAtLeast(0f)
        starts = FloatArray(durations.size)
        var t = 0f
        for (i in durations.indices) {
            starts[i] = t
            t += durations[i] - fade
        }
        total = starts.last() + durations.last()
    }

    /** t anında görünen kare(ler). [next] null değilse [nextAlpha] oranında üstüne bindirilir. */
    data class Frame(val index: Int, val progress: Float, val next: Int?, val nextProgress: Float, val nextAlpha: Float)

    fun at(time: Float): Frame {
        val t = time.coerceIn(0f, total)
        var i = 0
        while (i + 1 < starts.size && t >= starts[i + 1] + fade) i++
        val p = ((t - starts[i]) / durations[i]).coerceIn(0f, 1f)
        val n = i + 1
        if (n < starts.size && t >= starts[n] && fade > 0f) {
            val a = ((t - starts[n]) / fade).coerceIn(0f, 1f)
            val np = ((t - starts[n]) / durations[n]).coerceIn(0f, 1f)
            return Frame(i, p, n, np, smooth(a))
        }
        return Frame(i, p, null, 0f, 0f)
    }

    /** Altyazının kare içindeki görünürlüğü (başta ve sonda yumuşak geçiş). */
    fun captionAlpha(progress: Float, duration: Float): Float {
        val edge = min(0.6f, duration / 4f) / duration
        return min(smooth((progress / edge).coerceIn(0f, 1f)), smooth(((1f - progress) / edge).coerceIn(0f, 1f)))
    }

    companion object {
        const val DEFAULT_FADE = 0.9f
        fun smooth(x: Float): Float = (0.5f - 0.5f * cos(PI * x.coerceIn(0f, 1f))).toFloat()
    }
}

/**
 * Ken Burns kamera hareketi. Görüntü kareyi tamamen kaplayacak şekilde ölçeklenir,
 * sonra [zoom] kadar büyütülür ve fazlalık alan içinde [panX]/[panY] (-1..1) kadar kaydırılır.
 */
data class KenBurns(val zoom: Float, val panX: Float, val panY: Float) {

    companion object {
        private const val ZOOM_AMOUNT = 0.16f
        private const val PAN_ZOOM = 1.18f

        fun at(move: CameraMove, progress: Float): KenBurns {
            val e = Timeline.smooth(progress)
            return when (move) {
                CameraMove.ZOOM_IN -> KenBurns(1f + ZOOM_AMOUNT * e, 0f, -0.15f * e)
                CameraMove.ZOOM_OUT -> KenBurns(1f + ZOOM_AMOUNT * (1f - e), 0f, -0.15f * (1f - e))
                CameraMove.PAN_LEFT -> KenBurns(PAN_ZOOM, 1f - 2f * e, 0f)
                CameraMove.PAN_RIGHT -> KenBurns(PAN_ZOOM, -1f + 2f * e, 0f)
                CameraMove.PAN_UP -> KenBurns(PAN_ZOOM, 0f, 1f - 2f * e)
                CameraMove.PAN_DOWN -> KenBurns(PAN_ZOOM, 0f, -1f + 2f * e)
                CameraMove.STATIC -> KenBurns(1.04f + 0.02f * e, 0f, 0f)
            }
        }

        /**
         * Görüntünün kare içindeki yerleşimi: [left, top, ölçek].
         * Görüntü her zaman kareyi tamamen kaplar (kenarda boşluk kalmaz).
         */
        fun placement(kb: KenBurns, imgW: Int, imgH: Int, frameW: Int, frameH: Int): FloatArray {
            val base = max(frameW.toFloat() / imgW, frameH.toFloat() / imgH)
            val s = base * max(1f, kb.zoom)
            val slackX = imgW * s - frameW
            val slackY = imgH * s - frameH
            val left = -slackX * (0.5f + 0.5f * kb.panX.coerceIn(-1f, 1f))
            val top = -slackY * (0.5f + 0.5f * kb.panY.coerceIn(-1f, 1f))
            return floatArrayOf(left, top, s)
        }
    }
}
