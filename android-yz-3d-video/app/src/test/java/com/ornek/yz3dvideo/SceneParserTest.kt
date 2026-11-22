package com.ornek.yz3dvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneParserTest {

    private fun assertVec(expected: Vec3, actual: Vec3, delta: Float = 1e-4f) {
        assertEquals(expected.x, actual.x, delta)
        assertEquals(expected.y, actual.y, delta)
        assertEquals(expected.z, actual.z, delta)
    }

    @Test
    fun demoSceneParses() {
        val s = SceneParser.parse(DemoScene.JSON)
        assertEquals("Yolda giden araba", s.title)
        assertEquals(8f, s.duration, 0f)
        assertEquals(5, s.objects.size)
        val car = s.objects.first { it.id == "car" }
        assertEquals(6, car.parts.size)
        assertEquals(Shape.CYLINDER, car.parts[2].shape)
        assertVec(Vec3(90f, 0f, 0f), car.parts[2].rotation)
        assertEquals(2, s.captions.size)
    }

    @Test(expected = SceneParseException::class)
    fun invalidJsonThrows() {
        SceneParser.parse("bu json değil")
    }

    @Test(expected = SceneParseException::class)
    fun sceneWithoutDrawableObjectsThrows() {
        SceneParser.parse("""{"objects":[{"id":"x","parts":[{"shape":"teapot"}]}]}""")
    }

    @Test
    fun valuesAreClampedAndDefaulted() {
        val s = SceneParser.parse(
            """
            {"duration": 999, "light": {"ambient": 7},
             "objects": [{"position": [1e9, -5, "x"], "scale": 0,
                          "parts": [{"shape": "CUBE", "color": "nope", "scale": [0, 1, 1e9]}],
                          "animations": [{"type":"rotate","axis":[0,0,0],"speed":99999},
                                         {"type":"unknown"}]}]}
            """.trimIndent()
        )
        assertEquals(20f, s.duration, 0f)
        assertEquals(1f, s.ambient, 0f)
        val o = s.objects[0]
        assertVec(Vec3(100f, -5f, 0f), o.position)
        assertEquals(0.05f, o.scale, 0f)
        val p = o.parts[0]
        assertEquals(Rgb(0.8f, 0.8f, 0.8f), p.color)
        assertVec(Vec3(0.01f, 1f, 400f), p.scale)
        assertEquals(1, o.anims.size)
        val r = o.anims[0] as Anim.Rotate
        assertVec(Vec3.UP, r.axis)           // sıfır eksen yukarı eksene düşer
        assertEquals(720f, r.speed, 0f)
        assertEquals(20f, r.end, 0f)         // bitiş verilmezse süre sonu
    }

    @Test
    fun objectAndPartCountsAreLimited() {
        val part = """{"shape":"sphere"}"""
        val parts = List(50) { part }.joinToString(",")
        val obj = """{"parts":[$parts]}"""
        val objs = List(60) { obj }.joinToString(",")
        val s = SceneParser.parse("""{"objects":[$objs]}""")
        assertEquals(SceneParser.MAX_OBJECTS, s.objects.size)
        assertEquals(SceneParser.MAX_PARTS, s.objects[0].parts.size)
    }

    @Test
    fun colorParsing() {
        assertEquals(Rgb(1f, 0f, 0f), SceneParser.color("#FF0000", Rgb(0f, 0f, 0f)))
        assertEquals(Rgb(0f, 1f, 0f), SceneParser.color("00ff00", Rgb(0f, 0f, 0f)))
        val fb = Rgb(0.1f, 0.2f, 0.3f)
        assertEquals(fb, SceneParser.color("#12345", fb))
        assertEquals(fb, SceneParser.color("#GGGGGG", fb))
        assertEquals(fb, SceneParser.color(42, fb))
    }

    @Test
    fun cameraTargetEqualToPositionIsFixed() {
        val s = SceneParser.parse(
            """{"camera":{"start":{"position":[1,2,3],"target":[1,2,3]}},
                "objects":[{"parts":[{"shape":"cube"}]}]}"""
        )
        assertTrue((s.cameraStart.target - s.cameraStart.position).length() > 0.5f)
        assertFalse(s.captions.any())
    }
}
