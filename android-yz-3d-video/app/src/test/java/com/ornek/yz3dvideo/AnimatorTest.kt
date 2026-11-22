package com.ornek.yz3dvideo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimatorTest {

    private fun obj(vararg anims: Anim, position: Vec3 = Vec3.ZERO) = SceneObject(
        id = "o",
        parts = listOf(Part(Shape.CUBE, Rgb(1f, 1f, 1f), Vec3.ZERO, Vec3.ONE, Vec3.ZERO)),
        position = position,
        rotation = Vec3.ZERO,
        scale = 1f,
        anims = anims.toList()
    )

    private fun assertVec(expected: Vec3, actual: Vec3, delta: Float = 1e-3f) {
        assertEquals(expected.x, actual.x, delta)
        assertEquals(expected.y, actual.y, delta)
        assertEquals(expected.z, actual.z, delta)
    }

    @Test
    fun movesChainInOrder() {
        val o = obj(
            Anim.Move(Vec3(10f, 0f, 0f), 0f, 2f),
            Anim.Move(Vec3(10f, 0f, 10f), 2f, 4f)
        )
        assertVec(Vec3.ZERO, Animator.pose(o, 0f).position)
        assertVec(Vec3(5f, 0f, 0f), Animator.pose(o, 1f).position)   // ease(0.5) = 0.5
        assertVec(Vec3(10f, 0f, 0f), Animator.pose(o, 2f).position)
        assertVec(Vec3(10f, 0f, 5f), Animator.pose(o, 3f).position)
        assertVec(Vec3(10f, 0f, 10f), Animator.pose(o, 9f).position)
    }

    @Test
    fun rotateAccumulatesWithinWindow() {
        val o = obj(Anim.Rotate(Vec3.UP, 90f, 1f, 3f))
        assertEquals(0, Animator.pose(o, 0.5f).spins.size)
        assertEquals(90f, Animator.pose(o, 2f).spins[0].degrees, 1e-4f)
        assertEquals(180f, Animator.pose(o, 10f).spins[0].degrees, 1e-4f)
    }

    @Test
    fun bounceStaysAboveBase() {
        val o = obj(Anim.Bounce(2f, 1f, 0f, 10f), position = Vec3(0f, 1f, 0f))
        var t = 0f
        while (t < 10f) {
            val y = Animator.pose(o, t).position.y
            assert(y >= 1f - 1e-4f && y <= 3f + 1e-4f) { "y=$y t=$t" }
            t += 0.1f
        }
        assertEquals(3f, Animator.pose(o, 0.5f).position.y, 1e-3f)
    }

    @Test
    fun scaleInterpolates() {
        val o = obj(Anim.Scale(3f, 0f, 2f))
        assertEquals(1f, Animator.pose(o, 0f).scale, 1e-4f)
        assertEquals(2f, Animator.pose(o, 1f).scale, 1e-4f)
        assertEquals(3f, Animator.pose(o, 5f).scale, 1e-4f)
    }

    @Test
    fun orbitCirclesCenter() {
        val o = obj(Anim.Orbit(Vec3(0f, 2f, 0f), 5f, 90f, 0f, 10f))
        assertVec(Vec3(5f, 2f, 0f), Animator.pose(o, 0f).position)
        assertVec(Vec3(0f, 2f, 5f), Animator.pose(o, 1f).position)
    }

    @Test
    fun cameraAndCaptions() {
        val s = SceneParser.parse(DemoScene.JSON)
        val (startPos, _) = Animator.camera(s, 0f)
        val (endPos, endTarget) = Animator.camera(s, s.duration)
        assertVec(s.cameraStart.position, startPos)
        assertVec(s.cameraEnd.position, endPos)
        assertVec(s.cameraEnd.target, endTarget)
        assertNull(Animator.captionAt(s, 0.1f))
        assertEquals("Güneşli bir günde araba yolda ilerliyor", Animator.captionAt(s, 1f))
        assertEquals("Örnek sahne", Animator.captionAt(s, 6f))
    }
}
