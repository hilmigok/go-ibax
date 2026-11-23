package com.ornek.yzgercekci

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * İnternetten ses bulunamadığında ortam seslerini ve fon müziğini cihazda sıfırdan
 * üretir (telif yok). Saf Kotlin; mono, [SR] Hz, -1..1 aralığında FloatArray döndürür.
 */
object Synth {

    const val SR = 44100
    private const val TAU = (2 * PI).toFloat()

    private class OnePole(fc: Float) {
        var a = coef(fc)
        var y = 0f
        fun set(fc: Float) { a = coef(fc) }
        fun lp(x: Float): Float { y += a * (x - y); return y }
        companion object {
            fun coef(fc: Float) = (1.0 - exp(-2.0 * PI * fc / SR)).toFloat()
        }
    }

    fun ambience(type: Ambience, seconds: Float, seed: Int = 1): FloatArray {
        val n = (seconds * SR).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        val rnd = Random(seed)
        when (type) {
            Ambience.SILENCE -> roomTone(out, rnd, 0.02f)
            Ambience.WIND -> wind(out, rnd, 0.5f)
            Ambience.RAIN -> rain(out, rnd)
            Ambience.FIRE -> fire(out, rnd)
            Ambience.WAR -> war(out, rnd)
            Ambience.CITY -> city(out, rnd)
            Ambience.NATURE -> nature(out, rnd)
        }
        return normalize(out, 0.8f)
    }

    private fun roomTone(out: FloatArray, rnd: Random, gain: Float) {
        val f = OnePole(400f)
        for (i in out.indices) out[i] += gain * f.lp(rnd.nextFloat() * 2 - 1) * 4f
    }

    private fun wind(out: FloatArray, rnd: Random, gain: Float) {
        val f1 = OnePole(400f)
        val f2 = OnePole(400f)
        val g1 = rnd.nextFloat() * TAU
        val g2 = rnd.nextFloat() * TAU
        for (i in out.indices) {
            val t = i.toFloat() / SR
            // Rüzgâr şiddeti ve tonu yavaşça değişir (esinti).
            val gust = 0.55f + 0.3f * sin(t * 0.23f * TAU + g1) + 0.15f * sin(t * 0.61f * TAU + g2)
            val fc = 180f + 650f * gust
            if (i % 64 == 0) { f1.set(fc); f2.set(fc * 1.3f) }
            val x = rnd.nextFloat() * 2 - 1
            out[i] += gain * gust * f2.lp(f1.lp(x)) * 6f
        }
    }

    private fun rain(out: FloatArray, rnd: Random) {
        val lp = OnePole(2500f)
        val low = OnePole(250f)
        var drop = 0f
        for (i in out.indices) {
            val x = rnd.nextFloat() * 2 - 1
            val hiss = x - lp.lp(x)                    // yüksek frekanslı hışırtı
            if (rnd.nextFloat() < 90f / SR) drop = 0.4f + 0.6f * rnd.nextFloat()
            drop *= 0.992f
            out[i] = 0.25f * hiss + drop * (rnd.nextFloat() * 2 - 1) * 0.5f + low.lp(x) * 0.8f
        }
    }

    private fun fire(out: FloatArray, rnd: Random) {
        val roar = OnePole(300f)
        val hp = OnePole(1500f)
        var crack = 0f
        var decay = 0.99f
        for (i in out.indices) {
            val x = rnd.nextFloat() * 2 - 1
            if (rnd.nextFloat() < 22f / SR) {
                crack = 0.5f + 0.5f * rnd.nextFloat()
                decay = 0.985f + 0.012f * rnd.nextFloat()
            }
            crack *= decay
            val c = x - hp.lp(x)
            out[i] = roar.lp(x) * 1.6f + c * crack
        }
    }

    private fun war(out: FloatArray, rnd: Random) {
        val rumble = OnePole(90f)
        val rumble2 = OnePole(90f)
        for (i in out.indices) out[i] = rumble2.lp(rumble.lp(rnd.nextFloat() * 2 - 1)) * 10f
        wind(out, rnd, 0.25f)
        // Uzaktan gelen patlamalar: 2–5 sn'de bir.
        var t = 0.6f + rnd.nextFloat() * 1.5f
        val total = out.size.toFloat() / SR
        while (t < total) {
            boom(out, (t * SR).toInt(), 0.5f + 0.5f * rnd.nextFloat(), rnd)
            // Bazen kısa silah sesi kümeleri (uzak, boğuk).
            if (rnd.nextFloat() < 0.4f) {
                var g = t + 0.6f + rnd.nextFloat()
                repeat(3 + rnd.nextInt(6)) {
                    pop(out, (g * SR).toInt(), 0.15f + 0.1f * rnd.nextFloat(), rnd)
                    g += 0.08f + 0.12f * rnd.nextFloat()
                }
            }
            t += 2f + rnd.nextFloat() * 3f
        }
    }

    private fun boom(out: FloatArray, at: Int, amp: Float, rnd: Random) {
        val len = (2.2f * SR).toInt()
        val f = OnePole(160f)
        var ph = 0f
        for (k in 0 until len) {
            val i = at + k
            if (i >= out.size) break
            val t = k.toFloat() / SR
            val env = exp(-t * 2.2f) * min(1f, t * 200f)
            val freq = 30f + 30f * exp(-t * 4f)
            ph += TAU * freq / SR
            out[i] += amp * env * (0.8f * sin(ph) + 3f * f.lp(rnd.nextFloat() * 2 - 1))
        }
    }

    private fun pop(out: FloatArray, at: Int, amp: Float, rnd: Random) {
        val len = (0.12f * SR).toInt()
        val f = OnePole(900f)
        for (k in 0 until len) {
            val i = at + k
            if (i >= out.size) break
            val env = exp(-k.toFloat() / SR * 45f)
            out[i] += amp * env * f.lp(rnd.nextFloat() * 2 - 1) * 3f
        }
    }

    private fun city(out: FloatArray, rnd: Random) {
        val f = OnePole(500f)
        val g = OnePole(500f)
        for (i in out.indices) {
            val t = i.toFloat() / SR
            out[i] = g.lp(f.lp(rnd.nextFloat() * 2 - 1)) * 5f + 0.04f * sin(TAU * 60f * t)
        }
        wind(out, rnd, 0.15f)
    }

    private fun nature(out: FloatArray, rnd: Random) {
        wind(out, rnd, 0.3f)
        // Kuş cıvıltıları: kısa, perdesi kayan ıslıklar.
        var t = rnd.nextFloat()
        val total = out.size.toFloat() / SR
        while (t < total) {
            val base = 2500f + 2500f * rnd.nextFloat()
            repeat(1 + rnd.nextInt(4)) { k ->
                chirp(out, ((t + k * 0.13f) * SR).toInt(), base, 0.12f + 0.1f * rnd.nextFloat())
            }
            t += 0.8f + 2.5f * rnd.nextFloat()
        }
    }

    private fun chirp(out: FloatArray, at: Int, base: Float, amp: Float) {
        val len = (0.09f * SR).toInt()
        var ph = 0f
        for (k in 0 until len) {
            val i = at + k
            if (i >= out.size) break
            val p = k.toFloat() / len
            ph += TAU * base * (1f + 0.5f * p) / SR
            out[i] += amp * sin(PI.toFloat() * p) * sin(ph)
        }
    }

    // ---------------------------------------------------------------- müzik

    private val A_MINOR = arrayOf(floatArrayOf(220f, 261.63f, 329.63f), floatArrayOf(174.61f, 220f, 261.63f),
        floatArrayOf(196f, 261.63f, 329.63f), floatArrayOf(196f, 246.94f, 293.66f))
    private val C_MAJOR = arrayOf(floatArrayOf(261.63f, 329.63f, 392f), floatArrayOf(196f, 246.94f, 392f),
        floatArrayOf(220f, 261.63f, 329.63f), floatArrayOf(174.61f, 220f, 349.23f))
    private val D_DARK = arrayOf(floatArrayOf(146.83f, 174.61f, 220f), floatArrayOf(116.54f, 146.83f, 174.61f),
        floatArrayOf(146.83f, 174.61f, 207.65f), floatArrayOf(130.81f, 155.56f, 196f))
    private val E_EPIC = arrayOf(floatArrayOf(164.81f, 196f, 246.94f), floatArrayOf(130.81f, 164.81f, 196f),
        floatArrayOf(146.83f, 196f, 246.94f), floatArrayOf(123.47f, 146.83f, 185f))
    private val TENSE = arrayOf(floatArrayOf(110f, 116.54f, 164.81f), floatArrayOf(110f, 130.81f, 155.56f))

    fun music(mood: Music, seconds: Float): FloatArray {
        val n = (seconds * SR).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        if (mood == Music.NONE) return out
        val (chords, chordLen) = when (mood) {
            Music.SAD -> A_MINOR to 4.5f
            Music.HOPEFUL -> C_MAJOR to 4f
            Music.DARK -> D_DARK to 6f
            Music.EPIC -> E_EPIC to 4f
            Music.TENSE -> TENSE to 5f
            Music.NONE -> A_MINOR to 4f
        }
        val fade = 1.6f
        var start = 0f
        var idx = 0
        while (start < seconds) {
            val chord = chords[idx % chords.size]
            val from = (start * SR).toInt()
            val len = ((chordLen + fade) * SR).toInt()
            for ((v, f) in chord.withIndex()) pad(out, from, len, f, 0.16f, fade, v)
            pad(out, from, len, chord[0] / 2f, 0.2f, fade, 9)          // kalın alt ses
            start += chordLen
            idx++
        }
        when (mood) {
            Music.EPIC -> { var t = 0f; while (t < seconds) { drum(out, (t * SR).toInt(), 0.7f, 55f); t += 2f } }
            Music.TENSE -> {
                var t = 0.5f
                while (t < seconds) {
                    drum(out, (t * SR).toInt(), 0.45f, 50f)
                    drum(out, ((t + 0.28f) * SR).toInt(), 0.3f, 50f)
                    t += 1.1f
                }
            }
            else -> Unit
        }
        return normalize(out, 0.8f)
    }

    /** Yumuşak, hafif titreşimli tel/pad sesi (toplamsal sentez). */
    private fun pad(out: FloatArray, from: Int, len: Int, freq: Float, amp: Float, fade: Float, voice: Int) {
        val fadeN = (fade * SR).toInt()
        val ph = FloatArray(6)
        val detune = 1f + 0.0015f * (voice - 1)
        for (k in 0 until len) {
            val i = from + k
            if (i >= out.size) break
            val env = min(1f, min(k.toFloat() / fadeN, (len - k).toFloat() / fadeN))
            val vib = 1f + 0.002f * sin(TAU * 4.8f * k / SR + voice)
            var s = 0f
            for (h in 0 until 6) {
                ph[h] += TAU * freq * detune * vib * (h + 1) / SR
                if (ph[h] > TAU) ph[h] -= TAU
                s += sin(ph[h]) / ((h + 1) * (h + 1)).toFloat()
            }
            out[i] += amp * env * s
        }
    }

    private fun drum(out: FloatArray, at: Int, amp: Float, freq: Float) {
        val len = (0.7f * SR).toInt()
        var ph = 0f
        for (k in 0 until len) {
            val i = at + k
            if (i >= out.size || i < 0) break
            val t = k.toFloat() / SR
            ph += TAU * freq * (1f + exp(-t * 18f)) / SR
            out[i] += amp * exp(-t * 6f) * sin(ph)
        }
    }

    fun normalize(a: FloatArray, peak: Float): FloatArray {
        var m = 0f
        for (v in a) if (kotlin.math.abs(v) > m) m = kotlin.math.abs(v)
        if (m > 1e-6f) { val g = peak / m; for (i in a.indices) a[i] *= g }
        return a
    }
}
