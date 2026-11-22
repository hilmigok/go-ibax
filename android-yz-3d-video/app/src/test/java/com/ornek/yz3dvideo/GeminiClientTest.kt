package com.ornek.yz3dvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiClientTest {

    @Test
    fun extractsCandidateText() {
        val response = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"{\"title\":\"x\"}"}]},
              "finishReason":"STOP"}]}
        """.trimIndent()
        assertEquals("""{"title":"x"}""", GeminiClient.extractText(response))
    }

    @Test
    fun stripsMarkdownFences() {
        assertEquals("{\"a\":1}", GeminiClient.stripFences("```json\n{\"a\":1}\n```"))
        assertEquals("{\"a\":1}", GeminiClient.stripFences("  {\"a\":1}  "))
    }

    @Test(expected = GeminiException::class)
    fun blockedPromptThrows() {
        GeminiClient.extractText("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
    }

    @Test(expected = GeminiException::class)
    fun emptyCandidateThrows() {
        GeminiClient.extractText("""{"candidates":[{"content":{"parts":[]},"finishReason":"MAX_TOKENS"}]}""")
    }

    @Test
    fun errorMessagesAreHelpful() {
        val msg = GeminiClient.errorMessage(403, """{"error":{"code":403,"message":"API key not valid"}}""")
        assertTrue(msg.contains("anahtar"))
        assertTrue(msg.contains("API key not valid"))
        assertTrue(GeminiClient.errorMessage(500, "<html>").contains("500"))
    }

    @Test
    fun extractedDemoLikeSceneParses() {
        val inner = DemoScene.JSON.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val response = """{"candidates":[{"content":{"parts":[{"text":"$inner"}]}}]}"""
        val scene = SceneParser.parse(GeminiClient.extractText(response))
        assertEquals(5, scene.objects.size)
    }
}
