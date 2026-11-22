package com.ornek.yzgercekci

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Atmosfer parçacıklarının (toz, kar, yağmur, kıvılcım, duman) konumunu hesaplar.
 * Tamamen deterministiktir: aynı (efekt, i, t) her zaman aynı sonucu verir; böylece
 * önizleme ile video birebir aynı olur. Konumlar 0..1 aralığında (kareye göre) döner.
 */
object Particles {

    class P {
        var x = 0f
        var y = 0f
        var size = 0f     // kare yüksekliğine oranla
        var alpha = 0f    // 0..1
        var length = 0f   // yağmur çizgisi uzunluğu (oran)
    }

    fun count(effect: Effect): Int = when (effect) {
        Effect.NONE -> 0
        Effect.DUST -> 90
        Effect.SNOW -> 140
        Effect.RAIN -> 220
        Effect.EMBERS -> 70
        Effect.SMOKE -> 9
        Effect.FOG -> 6
    }

    /** i. parçacık için 0..1 arası sözde rastgele sayı (k: farklı özellikler için kanal). */
    fun rand(i: Int, k: Int): Float {
        var h = i * 374761393 + k * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun wrap(v: Float): Float {
        val f = v - kotlin.math.floor(v)
        return if (f < 0f) f + 1f else f
    }

    fun compute(effect: Effect, i: Int, t: Float, out: P): P {
        val r0 = rand(i, 0)
        val r1 = rand(i, 1)
        val r2 = rand(i, 2)
        val r3 = rand(i, 3)
        val tau = (2 * PI).toFloat()
        out.length = 0f
        when (effect) {
            Effect.DUST -> {
                out.x = wrap(r0 + 0.012f * t * (r2 - 0.3f) + 0.01f * sin(t * 0.7f + r3 * tau))
                out.y = wrap(r1 - 0.008f * t * (0.4f + r3) + 0.01f * sin(t * 0.5f + r2 * tau))
                out.size = 0.0012f + 0.0035f * r2 * r2
                out.alpha = 0.18f + 0.45f * abs(sin(t * (0.4f + r3) + r0 * tau))
            }
            Effect.SNOW -> {
                out.x = wrap(r0 + 0.02f * sin(t * (0.6f + r3) + r2 * tau))
                out.y = wrap(r1 + t * (0.04f + 0.06f * r2))
                out.size = 0.0015f + 0.004f * r2
                out.alpha = 0.55f + 0.4f * r3
            }
            Effect.RAIN -> {
                out.x = wrap(r0 - t * 0.12f)
                out.y = wrap(r1 + t * (0.9f + 0.6f * r2))
                out.size = 0.0007f + 0.0006f * r3
                out.length = 0.025f + 0.03f * r2
                out.alpha = 0.18f + 0.25f * r3
            }
            Effect.EMBERS -> {
                out.x = wrap(r0 + 0.03f * sin(t * (0.8f + r3) + r2 * tau) + 0.01f * t)
                out.y = wrap(r1 - t * (0.05f + 0.08f * r2))
                out.size = 0.0015f + 0.003f * r2
                out.alpha = (0.35f + 0.65f * abs(sin(t * (2f + 3f * r3) + r0 * tau))) * (0.4f + 0.6f * out.y)
            }
            Effect.SMOKE -> {
                out.x = wrap(r0 + t * (0.01f + 0.02f * r2))
                out.y = 0.45f + 0.5f * r1 + 0.02f * sin(t * 0.3f + r3 * tau)
                out.size = 0.35f + 0.35f * r2
                out.alpha = 0.10f + 0.10f * abs(sin(t * 0.25f + r3 * tau))
            }
            Effect.FOG -> {
                out.x = wrap(r0 + t * (0.006f + 0.01f * r2))
                out.y = 0.55f + 0.4f * r1
                out.size = 0.6f + 0.4f * r2
                out.alpha = 0.08f + 0.06f * abs(sin(t * 0.2f + r3 * tau))
            }
            Effect.NONE -> {
                out.alpha = 0f
            }
        }
        out.alpha = out.alpha.coerceIn(0f, 1f)
        return out
    }
}
