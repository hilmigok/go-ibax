package com.ornek.yz3dvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFormatTest {

    @Test
    fun formatsHaveSocialMediaSizes() {
        assertEquals(1080, VideoFormat.PORTRAIT_9_16.width)
        assertEquals(1920, VideoFormat.PORTRAIT_9_16.height)
        assertTrue(VideoFormat.PORTRAIT_9_16.isPortrait)
        assertEquals(1920, VideoFormat.LANDSCAPE_16_9.width)
        assertEquals(1080, VideoFormat.LANDSCAPE_16_9.height)
        assertFalse(VideoFormat.LANDSCAPE_16_9.isPortrait)
    }

    @Test
    fun promptCarriesFormat() {
        val v = ScenePrompt.userMessage("  bir roket fırlıyor ", VideoFormat.PORTRAIT_9_16)
        assertTrue(v.startsWith("bir roket fırlıyor\n\n[FORMAT] VERTICAL 9:16"))
        val h = ScenePrompt.userMessage("deniz", VideoFormat.LANDSCAPE_16_9)
        assertTrue(h.endsWith("[FORMAT] HORIZONTAL 16:9 video for YouTube."))
        assertTrue(ScenePrompt.SYSTEM.contains("[FORMAT]"))
    }
}
