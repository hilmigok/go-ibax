package com.ornek.yzgercekci

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class GeminiClientTest {

    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
    private val b64 = Base64.getEncoder().encodeToString(png)

    @Test
    fun extractsGeminiInlineImage() {
        val json = """{"candidates":[{"content":{"parts":[{"text":"İşte görüntü"},{"inlineData":{"mimeType":"image/png","data":"$b64"}}]}}]}"""
        assertArrayEquals(png, GeminiClient.extractGeminiImage(json))
    }

    @Test
    fun extractsSnakeCaseInlineImage() {
        val json = """{"candidates":[{"content":{"parts":[{"inline_data":{"mime_type":"image/png","data":"$b64"}}]}}]}"""
        assertArrayEquals(png, GeminiClient.extractGeminiImage(json))
    }

    @Test
    fun reportsSafetyBlockForImage() {
        val json = """{"candidates":[{"finishReason":"IMAGE_SAFETY","content":{"parts":[]}}]}"""
        try {
            GeminiClient.extractGeminiImage(json)
            fail("hata bekleniyordu")
        } catch (e: GeminiException) {
            assertTrue(e.message!!.contains("güvenlik"))
            assertTrue(!e.retryable)
        }
    }

    @Test
    fun textOnlyAnswerIsRetryable() {
        val json = """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"I cannot draw that."}]}}]}"""
        try {
            GeminiClient.extractGeminiImage(json)
            fail("hata bekleniyordu")
        } catch (e: GeminiException) {
            assertTrue(e.retryable)
            assertTrue(e.message!!.contains("I cannot draw that."))
        }
    }

    @Test
    fun extractsImagenImage() {
        val json = """{"predictions":[{"mimeType":"image/png","bytesBase64Encoded":"$b64"}]}"""
        assertArrayEquals(png, GeminiClient.extractImagenImage(json))
    }

    @Test(expected = GeminiException::class)
    fun imagenEmptyIsError() {
        GeminiClient.extractImagenImage("""{"predictions":[]}""")
    }

    @Test
    fun extractsTextAndStripsFences() {
        val json = """{"candidates":[{"content":{"parts":[{"text":"```json\n{\"a\":1}\n```"}]}}]}"""
        assertEquals("{\"a\":1}", GeminiClient.extractText(json))
    }

    @Test
    fun promptBlockIsReported() {
        try {
            GeminiClient.extractText("""{"promptFeedback":{"blockReason":"SAFETY"}}""")
            fail("hata bekleniyordu")
        } catch (e: GeminiException) {
            assertTrue(e.message!!.contains("SAFETY"))
        }
    }

    @Test
    fun detectsImagenModels() {
        assertTrue(GeminiClient.isImagen("imagen-4.0-generate-001"))
        assertTrue(!GeminiClient.isImagen("gemini-2.5-flash-image"))
    }

    @Test
    fun errorMessages() {
        assertTrue(GeminiClient.errorMessage(429, "").contains("Kota"))
        assertTrue(GeminiClient.errorMessage(400, """{"error":{"message":"Imagen API is only accessible to billed users"}}""").contains("faturalandırma"))
        assertTrue(GeminiClient.errorMessage(404, "{}").contains("Model bulunamadı"))
    }
}
