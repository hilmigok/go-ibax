package com.ornek.yzgercekci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardParserTest {

    @Test
    fun parsesValidStoryboard() {
        val json = """
            {"title":"Yıkıntıdaki Melek","shots":[
              {"prompt":"a dark winged angel over ruins","caption":"Sessizlik","camera":"pan_down","effect":"dust","duration":4.5},
              {"prompt":"close-up of a crying mother","caption":"","camera":"zoom_in","effect":"smoke","duration":3}
            ]}
        """.trimIndent()
        val b = StoryboardParser.parse(json)
        assertEquals("Yıkıntıdaki Melek", b.title)
        assertEquals(2, b.shots.size)
        assertEquals(CameraMove.PAN_DOWN, b.shots[0].camera)
        assertEquals(Effect.DUST, b.shots[0].effect)
        assertEquals(4.5f, b.shots[0].duration, 0.001f)
        assertEquals(Effect.SMOKE, b.shots[1].effect)
    }

    @Test
    fun clampsAndDefaults() {
        val json = """{"shots":[{"prompt":"x","camera":"spin","effect":"lava","duration":99},{"prompt":"y","duration":0.1}]}"""
        val b = StoryboardParser.parse(json)
        assertEquals("Sahne", b.title)
        assertEquals(CameraMove.ZOOM_IN, b.shots[0].camera)
        assertEquals(Effect.NONE, b.shots[0].effect)
        assertEquals(StoryboardParser.MAX_DURATION, b.shots[0].duration, 0.001f)
        assertEquals(StoryboardParser.MIN_DURATION, b.shots[1].duration, 0.001f)
    }

    @Test
    fun skipsEmptyPromptsAndLimitsCount() {
        val shots = (1..12).joinToString(",") { """{"prompt":"p$it"}""" }
        val b = StoryboardParser.parse("""{"shots":[{"prompt":"  "},$shots]}""")
        assertEquals(StoryboardParser.MAX_SHOTS, b.shots.size)
        assertEquals("p1", b.shots[0].prompt)
    }

    @Test(expected = StoryboardException::class)
    fun rejectsInvalidJson() {
        StoryboardParser.parse("bu json değil")
    }

    @Test(expected = StoryboardException::class)
    fun rejectsNoShots() {
        StoryboardParser.parse("""{"title":"a","shots":[]}""")
    }

    @Test
    fun imagePromptContainsStyleAndFraming() {
        val p = StoryboardPrompt.imagePrompt("an angel", VisualStyle.DARK_FANTASY, portrait = true)
        assertTrue(p.startsWith("an angel"))
        assertTrue(p.contains("vertical 9:16"))
        assertTrue(p.contains("dark fantasy"))
        assertTrue(StoryboardPrompt.TEMPLATE.contains("[BURAYA HİKÂYENİ YAZ]"))
    }
}
