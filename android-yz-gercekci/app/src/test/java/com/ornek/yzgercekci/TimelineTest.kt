package com.ornek.yzgercekci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    @Test
    fun totalAccountsForOverlap() {
        val tl = Timeline(listOf(4f, 4f, 4f), fadeSeconds = 1f)
        assertEquals(10f, tl.total, 0.001f)
        assertEquals(3f, tl.starts[1], 0.001f)
        assertEquals(6f, tl.starts[2], 0.001f)
    }

    @Test
    fun singleShotHasNoTransition() {
        val tl = Timeline(listOf(5f))
        val f = tl.at(2.5f)
        assertEquals(0, f.index)
        assertNull(f.next)
        assertEquals(0.5f, f.progress, 0.001f)
    }

    @Test
    fun crossfadeBetweenShots() {
        val tl = Timeline(listOf(4f, 4f), fadeSeconds = 1f)
        val before = tl.at(2.9f)
        assertEquals(0, before.index)
        assertNull(before.next)

        val mid = tl.at(3.5f)
        assertEquals(0, mid.index)
        assertEquals(1, mid.next)
        assertEquals(0.5f, mid.nextAlpha, 0.001f)

        val after = tl.at(4.2f)
        assertEquals(1, after.index)
        assertNull(after.next)
    }

    @Test
    fun fadeNeverExceedsHalfOfShortestShot() {
        val tl = Timeline(listOf(1f, 6f), fadeSeconds = 3f)
        assertEquals(0.5f, tl.fade, 0.001f)
    }

    @Test
    fun timeIsClamped() {
        val tl = Timeline(listOf(3f, 3f))
        assertEquals(1, tl.at(999f).index)
        assertEquals(1f, tl.at(999f).progress, 0.001f)
        assertEquals(0, tl.at(-5f).index)
    }

    @Test
    fun captionFadesInAndOut() {
        val tl = Timeline(listOf(4f))
        assertEquals(0f, tl.captionAlpha(0f, 4f), 0.001f)
        assertEquals(1f, tl.captionAlpha(0.5f, 4f), 0.001f)
        assertEquals(0f, tl.captionAlpha(1f, 4f), 0.001f)
    }

    @Test
    fun kenBurnsAlwaysCoversFrame() {
        val w = 1080
        val h = 1920
        for (move in CameraMove.entries) {
            for (step in 0..10) {
                val kb = KenBurns.at(move, step / 10f)
                // Hem dikey hem yatay kaynak görüntüyle dene.
                for ((iw, ih) in listOf(768 to 1344, 1344 to 768, 1024 to 1024)) {
                    val p = KenBurns.placement(kb, iw, ih, w, h)
                    val left = p[0]
                    val top = p[1]
                    val s = p[2]
                    assertTrue("$move sol boşluk", left <= 0.01f)
                    assertTrue("$move üst boşluk", top <= 0.01f)
                    assertTrue("$move sağ boşluk", left + iw * s >= w - 0.01f)
                    assertTrue("$move alt boşluk", top + ih * s >= h - 0.01f)
                }
            }
        }
    }

    @Test
    fun zoomInGrows() {
        val start = KenBurns.at(CameraMove.ZOOM_IN, 0f)
        val end = KenBurns.at(CameraMove.ZOOM_IN, 1f)
        assertTrue(end.zoom > start.zoom)
        val pan0 = KenBurns.at(CameraMove.PAN_UP, 0f)
        val pan1 = KenBurns.at(CameraMove.PAN_UP, 1f)
        assertTrue(pan1.panY < pan0.panY)
    }
}
