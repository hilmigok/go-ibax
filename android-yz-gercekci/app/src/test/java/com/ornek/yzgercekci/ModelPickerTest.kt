package com.ornek.yzgercekci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerTest {

    private val gen = setOf("generateContent", "countTokens")
    private val models = listOf(
        ModelInfo("gemini-2.0-flash", gen),
        ModelInfo("gemini-3.8-flash", gen),
        ModelInfo("gemini-3.8-flash-lite", gen),
        ModelInfo("gemini-3.8-pro", gen),
        ModelInfo("gemini-3.8-flash-image", gen),
        ModelInfo("gemini-3.1-flash-image-preview", gen),
        ModelInfo("gemini-3.8-flash-preview-tts", gen),
        ModelInfo("imagen-5.0-generate-001", setOf("predict")),
        ModelInfo("text-embedding-004", setOf("embedContent"))
    )

    @Test
    fun parsesSuggestionFromRealGoogleError() {
        val body = """{"error":{"code":404,"message":"This model models/gemini-2.5-flash is no longer available to new users. Please update your code to use models/gemini-3.8-flash for the latest features and improvements. We recommend you to use the Interactions API.","status":"NOT_FOUND"}}"""
        assertEquals("gemini-3.8-flash", GeminiClient.suggestedModel(body))
        assertNull(GeminiClient.suggestedModel("""{"error":{"message":"API key not valid"}}"""))
    }

    @Test
    fun exceptionDetectsUnavailableModel() {
        assertTrue(GeminiException("x", code = 404).modelUnavailable)
        assertTrue(GeminiException("This model is no longer available to new users", code = 400).modelUnavailable)
        assertTrue(!GeminiException("Kota", code = 429).modelUnavailable)
    }

    @Test
    fun picksBestModelPerKind() {
        assertEquals("gemini-3.8-flash", ModelPicker.pick(ModelPicker.Kind.TEXT, models))
        assertEquals("gemini-3.8-flash-image", ModelPicker.pick(ModelPicker.Kind.IMAGE, models))
        assertEquals("gemini-3.8-flash-preview-tts", ModelPicker.pick(ModelPicker.Kind.TTS, models))
    }

    @Test
    fun prefersSuggestionAndLatestAlias() {
        assertEquals("gemini-3.8-pro", ModelPicker.pick(ModelPicker.Kind.TEXT, models, suggested = "gemini-3.8-pro"))
        // Önerilen model iş türüne uymuyorsa yok sayılır.
        assertEquals("gemini-3.8-flash", ModelPicker.pick(ModelPicker.Kind.TEXT, models, suggested = "gemini-3.8-flash-image"))
        val withAlias = models + ModelInfo("gemini-flash-latest", gen)
        assertEquals("gemini-flash-latest", ModelPicker.pick(ModelPicker.Kind.TEXT, withAlias))
    }

    @Test
    fun fallsBackToImagenWhenNoGeminiImageModel() {
        val noGeminiImage = models.filter { !(it.name.startsWith("gemini") && it.name.contains("image")) }
        assertEquals("imagen-5.0-generate-001", ModelPicker.pick(ModelPicker.Kind.IMAGE, noGeminiImage))
        assertNull(ModelPicker.pick(ModelPicker.Kind.TTS, emptyList()))
    }

    @Test
    fun parsesModelList() {
        val json = """{"models":[{"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]},{"name":"models/imagen-5.0-generate-001","supportedGenerationMethods":["predict"]}]}"""
        val list = GeminiClient.parseModels(json)
        assertEquals(2, list.size)
        assertEquals("gemini-3.8-flash", list[0].name)
        assertTrue("predict" in list[1].methods)
    }
}
