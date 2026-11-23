package com.ornek.yzgercekci

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.tanh

/**
 * Videonun ses kanalını karıştırır: her karenin ortam sesi (karelerle birlikte çapraz
 * geçişli), tüm film boyunca fon müziği ve anlatıcı sesi. Anlatıcı konuşurken ortam ve
 * müzik kısılır (ducking). Saf hesaplama; mono 16 bit PCM döndürür.
 */
object Soundtrack {

    const val SR = Synth.SR
    const val AMBIENCE_GAIN = 0.55f
    const val MUSIC_GAIN = 0.32f
    const val VOICE_GAIN = 1.0f
    /** Anlatım, karenin başlangıcından bu kadar sonra başlar. */
    const val VOICE_DELAY = 0.45f

    /** Anlatıcı sesinin sığması için karelerin süresini uzatır. */
    fun fitDurations(board: Storyboard, voiceSeconds: List<Float>): Storyboard =
        board.copy(shots = board.shots.mapIndexed { i, s ->
            val v = voiceSeconds.getOrNull(i) ?: 0f
            if (v <= 0f) s else s.copy(duration = maxOf(s.duration, v + VOICE_DELAY + Timeline.DEFAULT_FADE + 0.5f).coerceAtMost(14f))
        })

    fun mix(timeline: Timeline, ambience: List<FloatArray?>, music: FloatArray?, voices: List<FloatArray?>): ShortArray {
        val n = (timeline.total * SR).toInt().coerceAtLeast(1)
        val bed = FloatArray(n)
        val fadeN = (timeline.fade * SR).toInt().coerceAtLeast(1)
        val last = timeline.starts.size - 1

        // Ortam sesleri: her kare kendi sesini döngüyle doldurur, geçişlerde yumuşak bindirme.
        for (i in 0..last) {
            val clip = ambience.getOrNull(i) ?: continue
            if (clip.isEmpty()) continue
            val from = (timeline.starts[i] * SR).toInt()
            val len = min((timeline.durations[i] * SR).toInt(), n - from)
            for (k in 0 until len) {
                var g = AMBIENCE_GAIN
                if (i > 0 && k < fadeN) g *= k.toFloat() / fadeN
                if (i < last && k > len - fadeN) g *= (len - k).toFloat() / fadeN
                bed[from + k] += g * clip[k % clip.size]
            }
        }
        if (music != null && music.isNotEmpty()) {
            for (k in 0 until n) bed[k] += MUSIC_GAIN * music[k % music.size]
        }

        // Anlatım ve ducking.
        val voice = FloatArray(n)
        val duck = FloatArray(n) { 1f }
        val ramp = (0.25f * SR).toInt()
        for (i in 0..last) {
            val v = voices.getOrNull(i) ?: continue
            if (v.isEmpty()) continue
            val from = ((timeline.starts[i] + VOICE_DELAY) * SR).toInt()
            for (k in v.indices) {
                val j = from + k
                if (j >= n) break
                voice[j] += VOICE_GAIN * v[k]
            }
            val a = from - ramp
            val b = from + v.size + ramp
            for (j in maxOf(0, a - ramp) until min(n, b + ramp)) {
                val d = when {
                    j < a -> 1f - 0.6f * (j - (a - ramp)).toFloat() / ramp
                    j > b -> 0.4f + 0.6f * (j - b).toFloat() / ramp
                    else -> 0.4f
                }
                if (d < duck[j]) duck[j] = d
            }
        }

        // Genel giriş/çıkış ve yumuşak sınırlayıcı.
        val inN = (0.6f * SR).toInt()
        val outN = (1.5f * SR).toInt()
        val mixed = FloatArray(n)
        var peak = 0f
        for (k in 0 until n) {
            var env = 1f
            if (k < inN) env = k.toFloat() / inN
            if (k > n - outN) env = min(env, (n - k).toFloat() / outN)
            val s = tanh((bed[k] * duck[k] + voice[k]) * env)
            mixed[k] = s
            if (abs(s) > peak) peak = abs(s)
        }
        val gain = if (peak > 1e-4f) min(4f, 0.9f / peak) else 1f
        return ShortArray(n) { (mixed[it] * gain * 32767f).toInt().coerceIn(-32768, 32767).toShort() }
    }

    /** Doğrusal yeniden örnekleme (ör. 24 kHz anlatıcı → 44,1 kHz). */
    fun resample(src: FloatArray, fromRate: Int, toRate: Int = SR): FloatArray {
        if (fromRate == toRate || src.isEmpty()) return src
        val n = (src.size.toLong() * toRate / fromRate).toInt().coerceAtLeast(1)
        val step = fromRate.toDouble() / toRate
        return FloatArray(n) {
            val x = it * step
            val i = x.toInt()
            val f = (x - i).toFloat()
            val a = src[min(i, src.size - 1)]
            val b = src[min(i + 1, src.size - 1)]
            a + (b - a) * f
        }
    }

    /** 16 bit little-endian PCM baytlarını (mono) -1..1 aralığına çevirir. */
    fun pcm16ToFloat(bytes: ByteArray): FloatArray = FloatArray(bytes.size / 2) {
        val lo = bytes[2 * it].toInt() and 0xFF
        val hi = bytes[2 * it + 1].toInt()
        ((hi shl 8) or lo).toShort() / 32768f
    }

    /**
     * WAV (PCM 16 bit, mono/stereo) dosyasını okuyup mono, [SR] Hz FloatArray'e çevirir.
     * Cihazın metin okuma motoru anlatımı bu biçimde yazar.
     */
    fun parseWav(bytes: ByteArray): FloatArray {
        fun u16(i: Int) = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
        fun u32(i: Int) = u16(i) or (u16(i + 2) shl 16)
        if (bytes.size < 12 || String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" ||
            String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE"
        ) throw IllegalArgumentException("WAV değil")
        var pos = 12
        var channels = 1
        var rate = 22050
        var bits = 16
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            var size = u32(pos + 4)
            val body = pos + 8
            if (size < 0 || body + size > bytes.size) size = bytes.size - body   // akışla yazılmış eksik başlık
            if (id == "fmt ") {
                channels = u16(body + 2).coerceAtLeast(1)
                rate = u32(body + 4)
                bits = u16(body + 14)
            } else if (id == "data") {
                if (bits != 16) throw IllegalArgumentException("Yalnızca 16 bit WAV destekleniyor")
                val frames = size / (2 * channels)
                val mono = FloatArray(frames) { f ->
                    var sum = 0
                    for (c in 0 until channels) sum += u16(body + (f * channels + c) * 2).toShort().toInt()
                    sum / channels / 32768f
                }
                return resample(mono, rate)
            }
            pos = body + size + (size and 1)
        }
        throw IllegalArgumentException("WAV verisi bulunamadı")
    }

    /** Döngüye girecek bir kaydın başını ve sonunu birbirine yedirir (çıt sesi olmasın). */
    fun makeLoopable(clip: FloatArray, fadeSeconds: Float = 0.4f): FloatArray {
        val f = min((fadeSeconds * SR).toInt(), clip.size / 3)
        if (f < 16) return clip
        val out = clip.copyOf(clip.size - f)
        for (k in 0 until f) {
            val t = k.toFloat() / f
            out[k] = clip[k] * t + clip[clip.size - f + k] * (1f - t)
        }
        return out
    }
}
