package com.ornek.yzgercekci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticlesTest {

    @Test
    fun deterministicAndInRange() {
        val a = Particles.P()
        val b = Particles.P()
        for (effect in Effect.entries) {
            for (i in 0 until Particles.count(effect)) {
                for (t in listOf(0f, 1.3f, 17.9f, 120f)) {
                    Particles.compute(effect, i, t, a)
                    Particles.compute(effect, i, t, b)
                    assertEquals(a.x, b.x, 0f)
                    assertEquals(a.y, b.y, 0f)
                    assertTrue("$effect x", a.x in 0f..1f)
                    assertTrue("$effect alpha", a.alpha in 0f..1f)
                    if (effect != Effect.SMOKE && effect != Effect.FOG) assertTrue("$effect y", a.y in 0f..1f)
                }
            }
        }
    }

    @Test
    fun randIsUniformish() {
        var sum = 0f
        val n = 5000
        for (i in 0 until n) {
            val r = Particles.rand(i, 3)
            assertTrue(r in 0f..1f)
            sum += r
        }
        assertEquals(0.5f, sum / n, 0.03f)
    }

    @Test
    fun noneHasNoParticles() {
        assertEquals(0, Particles.count(Effect.NONE))
    }
}
