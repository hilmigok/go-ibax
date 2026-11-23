package com.ornek.yzgercekci

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SoundTest {

    @Test
    fun synthAmbiencesAreAudibleAndBounded() {
        for (a in Ambience.entries) {
            val s = Synth.ambience(a, 3f, seed = 5)
            assertEquals(3 * Synth.SR, s.size)
            val peak = s.maxOf { abs(it) }
            assertTrue("$a çok sessiz", peak > 0.1f)
            assertTrue("$a taşıyor", peak <= 1f)
            assertTrue("$a NaN", s.none { it.isNaN() })
        }
    }

    @Test
    fun musicMoods() {
        for (m in Music.entries) {
            val s = Synth.music(m, 6f)
            val peak = s.maxOf { abs(it) }
            if (m == Music.NONE) assertEquals(0f, peak, 0f) else assertTrue("$m", peak in 0.1f..1f)
        }
    }

    @Test
    fun mixLengthMatchesTimelineAndDucksForVoice() {
        val tl = Timeline(listOf(4f, 4f))
        val amb = listOf(FloatArray(Synth.SR) { 0.5f }, FloatArray(Synth.SR) { 0.5f })
        val voice = FloatArray(Synth.SR) { if (it % 40 < 20) 0.3f else -0.3f }
        val pcm = Soundtrack.mix(tl, amb, null, listOf(voice, null))
        assertEquals((tl.total * Synth.SR).toInt(), pcm.size)
        assertTrue(pcm.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
        // Başta ve sonda sessizliğe iner.
        assertEquals(0, pcm.first().toInt())
        assertTrue(abs(pcm.last().toInt()) < 200)
    }

    @Test
    fun fitDurationsMakesRoomForNarration() {
        val b = Storyboard("a", listOf(Shot("p", "", CameraMove.STATIC, Effect.NONE, 3f), Shot("q", "", CameraMove.STATIC, Effect.NONE, 5f)))
        val f = Soundtrack.fitDurations(b, listOf(6f, 0f))
        assertTrue(f.shots[0].duration >= 6f + Soundtrack.VOICE_DELAY)
        assertEquals(5f, f.shots[1].duration, 0f)
    }

    @Test
    fun resampleAndPcm() {
        val src = FloatArray(24000) { 0.25f }
        val r = Soundtrack.resample(src, 24000)
        assertEquals(44100, r.size)
        assertEquals(0.25f, r[1000], 1e-6f)
        val bytes = byteArrayOf(0x00, 0x40, 0x00, 0xC0.toByte())   // +16384, -16384
        val f = Soundtrack.pcm16ToFloat(bytes)
        assertEquals(0.5f, f[0], 1e-6f)
        assertEquals(-0.5f, f[1], 1e-6f)
    }

    @Test
    fun loopableIsShorterAndContinuous() {
        val clip = FloatArray(Synth.SR * 3) { it.toFloat() / (Synth.SR * 3) }
        val loop = Soundtrack.makeLoopable(clip)
        assertTrue(loop.size < clip.size)
        // Döngü başı, eski sonun devamı olmalı (çıt yok).
        assertEquals(clip[loop.size], loop[0], 0.01f)
    }

    @Test
    fun parsesDeviceTtsWav() {
        // 22050 Hz, stereo, 16 bit; iki kare: (+16384,+16384) ve (-16384, 0)
        val data = byteArrayOf(0x00, 0x40, 0x00, 0x40, 0x00, 0xC0.toByte(), 0x00, 0x00)
        val wav = java.io.ByteArrayOutputStream().apply {
            fun i32(v: Int) = write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun i16(v: Int) = write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            write("RIFF".toByteArray()); i32(36 + data.size); write("WAVE".toByteArray())
            write("fmt ".toByteArray()); i32(16); i16(1); i16(2); i32(22050); i32(22050 * 4); i16(4); i16(16)
            write("data".toByteArray()); i32(data.size); write(data)
        }.toByteArray()
        val pcm = Soundtrack.parseWav(wav)
        assertEquals(4, pcm.size)                 // 2 kare × (44100 / 22050)
        assertEquals(0.5f, pcm[0], 1e-4f)
        assertTrue(pcm.last() < 0f)
    }

    @Test
    fun parsesFreesoundResults() {
        val json = """{"count":2,"results":[
            {"id":1,"name":"Wind","username":"ali","duration":30.5,"previews":{"preview-hq-mp3":"https://cdn.freesound.org/a.mp3"}},
            {"id":2,"name":"Bad","username":"x","previews":{"preview-hq-mp3":"http://insecure/b.mp3"}}
        ]}"""
        val hits = FreesoundClient.parseSearch(json)
        assertEquals(1, hits.size)
        assertEquals("https://cdn.freesound.org/a.mp3", hits[0].previewUrl)
        assertTrue(hits[0].credit.contains("CC0"))
        assertTrue(FreesoundClient.parseSearch("bozuk").isEmpty())
    }

    @Test
    fun parserReadsNewFields() {
        val b = StoryboardParser.parse("""{"music":"epic","shots":[{"prompt":"x","expression":"crying","sound":"woman crying","ambience":"war","narration":"Şehir sustu."}]}""")
        assertEquals(Music.EPIC, b.music)
        assertEquals(Expression.CRYING, b.shots[0].expression)
        assertEquals(Ambience.WAR, b.shots[0].ambience)
        assertEquals("woman crying", b.shots[0].sound)
        assertEquals("Şehir sustu.", b.shots[0].narration)
    }
}
