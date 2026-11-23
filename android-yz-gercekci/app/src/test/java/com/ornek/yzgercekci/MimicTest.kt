package com.ornek.yzgercekci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MimicTest {

    private val face = FaceRig(400f, 500f, 480f, 500f, 410f, 590f, 470f, 590f, 440f, 605f, seed = 3)

    @Test
    fun windowIsZeroAtEdgesAndOneInside() {
        assertEquals(0f, Mimic.window(0f, 0.5f), 1e-6f)
        assertEquals(0f, Mimic.window(0.5f, 1f), 1e-6f)
        assertEquals(1f, Mimic.window(0.5f, 0.5f), 1e-6f)
    }

    @Test
    fun edgesNeverMove() {
        val m = Mimic(face, Expression.SHOCK)
        val out = FloatArray(2)
        for (t in listOf(0f, 0.7f, 2.3f)) {
            m.prepare(t, 0.8f)
            m.displace(123f, 456f, 0f, out)
            assertEquals(123f, out[0], 1e-4f)
            assertEquals(456f, out[1], 1e-4f)
        }
    }

    @Test
    fun sadMouthCornersGoDownSmileGoesUp() {
        val out = FloatArray(2)
        val sad = Mimic(face, Expression.SAD).apply { prepare(1.1f, 1f) }
        val smile = Mimic(face, Expression.SMILE).apply { prepare(1.1f, 1f) }
        val neutral = Mimic(face, Expression.NEUTRAL).apply { prepare(1.1f, 1f) }
        neutral.displace(face.mouthLeftX, face.mouthLeftY, 1f, out); val n = out[1]
        sad.displace(face.mouthLeftX, face.mouthLeftY, 1f, out); val s = out[1]
        smile.displace(face.mouthLeftX, face.mouthLeftY, 1f, out); val sm = out[1]
        assertTrue("üzgün ağız köşesi aşağı inmeli", s > n + 1f)
        assertTrue("gülümseme köşesi yukarı çıkmalı", sm < n - 1f)
    }

    @Test
    fun fearRaisesBrows() {
        val out = FloatArray(2)
        val browY = face.leftEyeY - 0.36f * face.eyeDist
        val fear = Mimic(face, Expression.FEAR).apply { prepare(1.1f, 1f) }
        val neutral = Mimic(face, Expression.NEUTRAL).apply { prepare(1.1f, 1f) }
        neutral.displace(face.leftEyeX, browY, 1f, out); val n = out[1]
        fear.displace(face.leftEyeX, browY, 1f, out)
        assertTrue(out[1] < n - 1f)
    }

    @Test
    fun displacementIsSmall() {
        // Mimikler ince olmalı: hiçbir nokta göz mesafesinin %20'sinden fazla kaymamalı.
        val out = FloatArray(2)
        for (ex in Expression.entries) {
            val m = Mimic(face, ex)
            for (step in 0..40) {
                m.prepare(step * 0.1f, step / 40f)
                for (x in 360..520 step 8) for (y in 440..660 step 8) {
                    m.displace(x.toFloat(), y.toFloat(), 1f, out)
                    assertTrue("$ex", abs(out[0] - x) < 0.2f * face.eyeDist)
                    assertTrue("$ex", abs(out[1] - y) < 0.2f * face.eyeDist)
                }
            }
        }
    }

    @Test
    fun patchContainsFaceAndStaysInImage() {
        val r = face.patch(1000, 1000)
        assertTrue(r[0] < face.leftEyeX && r[2] > face.rightEyeX)
        assertTrue(r[1] < face.leftEyeY && r[3] > face.mouthBottomY)
        val edge = FaceRig.fromEyes(5f, 5f, 60f, 5f, 1).patch(100, 100)
        assertTrue(edge[0] >= 0 && edge[1] >= 0 && edge[2] <= 100 && edge[3] <= 100)
    }

    @Test
    fun blinksHappen() {
        val m = Mimic(face, Expression.NEUTRAL)
        val out = FloatArray(2)
        val lidX = face.leftEyeX
        val lidY = face.leftEyeY - 0.13f * face.eyeDist
        var maxDown = 0f
        for (i in 0 until 600) {
            m.prepare(i / 60f, 0.5f)
            m.displace(lidX, lidY, 1f, out)
            maxDown = maxOf(maxDown, out[1] - lidY)
        }
        assertTrue("10 saniyede en az bir göz kırpma", maxDown > 0.08f * face.eyeDist)
    }
}
