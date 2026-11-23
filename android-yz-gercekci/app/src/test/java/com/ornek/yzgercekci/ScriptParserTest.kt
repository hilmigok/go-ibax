package com.ornek.yzgercekci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptParserTest {

    @Test
    fun readsJsonEvenInsideCodeFenceWithChatter() {
        val text = """Tabii! İşte senaryon:
            ```json
            {"title":"Yıkıntı","music":"dark","shots":[{"prompt":"ruined city","narration":"Şehir sustu.","expression":"sad"}]}
            ```
            Başka bir şey ister misin?"""
        val b = ScriptParser.parse(text)
        assertEquals("Yıkıntı", b.title)
        assertEquals(Music.DARK, b.music)
        assertEquals("Şehir sustu.", b.shots[0].narration)
    }

    @Test
    fun plainParagraphsBecomeShots() {
        val text = """
            Savaşla yıkılmış bir şehrin harabeleri, gökyüzünde kara kanatlı bir melek.

            Başörtülü bir anne çocuğuna sarılmış ağlıyor.

            Yağmur başlıyor, sokaklar boş.
        """.trimIndent()
        val b = ScriptParser.parse(text)
        assertEquals(3, b.shots.size)
        assertEquals(Ambience.WAR, b.shots[0].ambience)
        assertEquals(Effect.SMOKE, b.shots[0].effect)
        assertEquals(Expression.CRYING, b.shots[1].expression)
        assertEquals("woman crying", b.shots[1].sound)
        assertEquals(Effect.RAIN, b.shots[2].effect)
        assertEquals(Music.DARK, b.music)
    }

    @Test
    fun labelsHeadingsAndDialogue() {
        val text = """
            Başlık: Poşet
            Sahne 1: Gece, eski bir apartman koridoru
            Görsel: two friends standing in a dim corridor holding a plastic bag
            •KOVAN: (İç çekerek) Başımıza iş alacağız ama hadi bakalım...
            •EKRAN YAZISI (Cliffhanger): Devamı 2. Bölümde... Poşetin içinden ne çıkacak?
            Sahne 2
            Anlatım: Poşetin içinden bir ışık sızıyordu.
        """.trimIndent()
        val b = ScriptParser.parse(text)
        assertEquals("Poşet", b.title)
        assertEquals(2, b.shots.size)
        val s1 = b.shots[0]
        assertTrue(s1.prompt.contains("dim corridor"))
        assertTrue(s1.prompt.contains("apartman koridoru"))
        assertEquals("Başımıza iş alacağız ama hadi bakalım...", s1.narration)
        assertTrue(s1.caption.startsWith("Devamı 2. Bölümde"))
        assertEquals(Expression.SAD, s1.expression)        // "iç çek"
        // Görsel tarifi yoksa anlatım tarif olarak kullanılır.
        assertEquals("Poşetin içinden bir ışık sızıyordu.", b.shots[1].prompt)
    }

    @Test(expected = StoryboardException::class)
    fun emptyIsRejected() {
        ScriptParser.parse("   ")
    }

    @Test
    fun pollinationsUrlIsEncoded() {
        val url = PollinationsClient.buildUrl("a crying mother, ruins & dust", 768, 1344, 42, "flux")
        assertTrue(url.startsWith("https://image.pollinations.ai/prompt/a%20crying%20mother%2C%20ruins%20%26%20dust?"))
        assertTrue(url.contains("width=768&height=1344&seed=42"))
        assertTrue(url.contains("model=flux"))
        assertTrue(!url.contains("+"))
        assertEquals(768 to 1344, PollinationsClient.size(true))
    }
}
