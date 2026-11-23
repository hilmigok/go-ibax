package com.ornek.yzgercekci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GrokTest {

    private fun shot(prompt: String, narration: String = "", dialogue: List<Line> = emptyList()) =
        Shot(prompt, "", CameraMove.STATIC, Effect.NONE, 4f, narration = narration, dialogue = dialogue)

    @Test
    fun requestBodyHasRatioDurationAndReferences() {
        val req = XaiVideoClient.Request(
            model = "", prompt = "p", portrait = true, durationSeconds = 99, resolution = "1080p",
            image = byteArrayOf(1, 2, 3), references = List(20) { byteArrayOf(9) }
        )
        val b = XaiVideoClient.buildBody(req)
        assertEquals(XaiVideoClient.DEFAULT_MODEL, b.getString("model"))
        assertEquals("9:16", b.getString("aspect_ratio"))
        assertEquals(15, b.getInt("duration"))                       // üst sınır
        assertEquals("720p", b.getString("resolution"))              // referansla en fazla 720p
        assertTrue(b.getJSONObject("image").getString("url").startsWith("data:image/jpeg;base64,"))
        assertEquals(CharacterMatcher.MAX_REFERENCES, b.getJSONArray("reference_images").length())
        val noRefs = XaiVideoClient.buildBody(req.copy(references = emptyList(), image = null, portrait = false))
        assertFalse(noRefs.has("reference_images"))
        assertFalse(noRefs.has("image"))
        assertEquals("16:9", noRefs.getString("aspect_ratio"))
    }

    @Test
    fun parsesStartAndPoll() {
        assertEquals("abc-123", XaiVideoClient.parseStart("""{"request_id":"abc-123"}"""))
        assertEquals(XaiVideoClient.Status.Pending, XaiVideoClient.parsePoll("""{"status":"pending"}"""))
        assertEquals(XaiVideoClient.Status.Pending, XaiVideoClient.parsePoll("""{}"""))
        val done = XaiVideoClient.parsePoll("""{"status":"done","video":{"url":"https://vidgen.x.ai/a.mp4","duration":15}}""")
        assertEquals(XaiVideoClient.Status.Done("https://vidgen.x.ai/a.mp4"), done)
    }

    @Test
    fun pollFailureIsReported() {
        try {
            XaiVideoClient.parsePoll("""{"status":"failed","error":{"message":"content policy"}}""")
            fail("hata bekleniyordu")
        } catch (e: ServiceException) {
            assertTrue(e.message!!.contains("content policy"))
        }
    }

    @Test(expected = ServiceException::class)
    fun badStartIsError() {
        XaiVideoClient.parseStart("""{"error":"x"}""")
    }

    @Test
    fun errorHints() {
        assertTrue(XaiVideoClient.errorMessage(401, "{}").contains("anahtarı geçersiz"))
        assertTrue(XaiVideoClient.errorMessage(402, """{"error":"no credits"}""").contains("kredi"))
        assertTrue(XaiVideoClient.errorMessage(400, """{"error":{"message":"bad field"}}""").contains("bad field"))
    }

    @Test
    fun matchesCharactersByName() {
        val kovan = CharacterRef("1", "KOVAN", "/a.jpg")
        val ayse = CharacterRef("2", "Ayşe Teyze", "/b.jpg")
        val ali = CharacterRef("3", "Ali", "/c.jpg")
        val all = listOf(kovan, ayse, ali)
        // Türkçe ek: "Kovan'ın"
        assertEquals(listOf(kovan), CharacterMatcher.match(shot("Kovan'ın elinde bir poşet"), all))
        // Konuşmacı olarak geçiyor
        assertEquals(listOf(ali), CharacterMatcher.match(shot("a dark corridor", dialogue = listOf(Line("Ali", "Gel buraya"))), all))
        // Çok kelimeli ad: iki kelime de geçmeli
        assertEquals(listOf(ayse), CharacterMatcher.match(shot("Ayşe Teyze kapıyı açıyor"), all))
        assertTrue(CharacterMatcher.match(shot("Ayşe kapıyı açıyor"), all).isEmpty())
        // "Alican" içinde "ali" sözcük başı olarak eşleşir ama "Halil" eşleşmez
        assertTrue(CharacterMatcher.match(shot("Halil yürüyor"), all).isEmpty())
        val many = List(20) { CharacterRef("$it", "Kişi$it", "/x.jpg") }
        val text = many.joinToString(" ") { it.name }
        assertEquals(CharacterMatcher.MAX_REFERENCES, CharacterMatcher.match(shot(text), many).size)
    }

    @Test
    fun grokPromptContainsMotionDialogueAndReferences() {
        val s = shot("two friends in a corridor", dialogue = listOf(Line("KOVAN", "Başımıza iş alacağız")))
            .copy(motion = "Kovan sighs and lifts the bag", sound = "footsteps")
        val p = StoryboardPrompt.grokPrompt(s, VisualStyle.CINEMATIC, portrait = true, referenceNames = listOf("KOVAN"))
        assertTrue(p.contains("Kovan sighs"))
        assertTrue(p.contains("KOVAN says in Turkish"))
        assertTrue(p.contains("\"Başımıza iş alacağız\""))
        assertTrue(p.contains("KOVAN is reference image 1"))
        assertTrue(p.contains("realistic footsteps"))
        assertTrue(p.contains("no background music"))
        assertTrue(p.contains("vertical 9:16"))
    }

    @Test
    fun narrationWithoutSpokenDialogue() {
        val s = shot("x", narration = "Gece çöktü. Başımıza iş alacağız ama hadi bakalım...",
            dialogue = listOf(Line("KOVAN", "Başımıza iş alacağız ama hadi bakalım...")))
        assertEquals("Gece çöktü", Soundtrack.withoutDialogue(s))
        val only = shot("x", narration = "Gel buraya", dialogue = listOf(Line("Ali", "Gel buraya")))
        assertEquals("", Soundtrack.withoutDialogue(only))
    }

    @Test
    fun scriptDialogueAndJsonCharacters() {
        val b = ScriptParser.parse("•KOVAN: (İç çekerek) Başımıza iş alacağız ama hadi bakalım...")
        assertEquals(listOf(Line("KOVAN", "Başımıza iş alacağız ama hadi bakalım...")), b.shots[0].dialogue)
        assertEquals(listOf("KOVAN"), b.characters)
        val j = StoryboardParser.parse("""{"characters":["KOVAN","ZEHRA"],"shots":[{"prompt":"x","motion":"walks","dialogue":[{"character":"ZEHRA","line":"Aç şunu"}]}]}""")
        assertEquals(listOf("KOVAN", "ZEHRA"), j.characters)
        assertEquals("walks", j.shots[0].motion)
        assertEquals(Line("ZEHRA", "Aç şunu"), j.shots[0].dialogue[0])
    }
}
