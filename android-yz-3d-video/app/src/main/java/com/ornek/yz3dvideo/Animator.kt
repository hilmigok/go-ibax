package com.ornek.yz3dvideo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bir sahnenin t anındaki durumunu hesaplar. Saf Kotlin'dir (Android'e bağlı
 * değildir), bu yüzden birim testlerle doğrulanabilir. Önizleme ve video
 * dışa aktarma aynı hesapları kullanır; video, önizlemenin birebir aynısı olur.
 */
object Animator {

    data class Spin(val axis: Vec3, val degrees: Float)

    data class Pose(
        val position: Vec3,
        val rotation: Vec3,
        val spins: List<Spin>,
        val scale: Float
    )

    /** Yumuşak başlayıp yumuşak biten geçiş (smoothstep). */
    fun ease(f: Float): Float {
        val x = f.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun progress(t: Float, start: Float, end: Float): Float =
        if (end <= start) (if (t >= start) 1f else 0f)
        else ((t - start) / (end - start)).coerceIn(0f, 1f)

    fun pose(o: SceneObject, t: Float): Pose {
        var position = o.position
        var scale = o.scale
        var bounceY = 0f
        val spins = ArrayList<Spin>()
        var moveDone = false
        var scaleDone = false

        for (a in o.anims.sortedBy { it.start }) {
            when (a) {
                // Hareketler sırayla zincirlenir: biri bitince sonraki onun bittiği yerden başlar.
                is Anim.Move -> if (!moveDone) {
                    if (t <= a.start) {
                        moveDone = true
                    } else {
                        val f = ease(progress(t, a.start, a.end))
                        position = position.lerp(a.to, f)
                        if (f < 1f) moveDone = true
                    }
                }
                is Anim.Scale -> if (!scaleDone) {
                    if (t <= a.start) {
                        scaleDone = true
                    } else {
                        val f = ease(progress(t, a.start, a.end))
                        scale += (a.to - scale) * f
                        if (f < 1f) scaleDone = true
                    }
                }
                is Anim.Rotate -> {
                    val active = (minOf(t, a.end) - a.start).coerceAtLeast(0f)
                    if (active > 0f) spins.add(Spin(a.axis, a.speed * active))
                }
                is Anim.Bounce -> if (t >= a.start && t <= a.end) {
                    bounceY += a.height * abs(sin(PI.toFloat() * a.speed * (t - a.start)))
                }
                is Anim.Orbit -> if (t >= a.start) {
                    val active = minOf(t, a.end) - a.start
                    val rad = Math.toRadians((a.speed * active).toDouble()).toFloat()
                    position = Vec3(
                        a.center.x + a.radius * cos(rad),
                        a.center.y,
                        a.center.z + a.radius * sin(rad)
                    )
                }
            }
        }
        return Pose(position + Vec3(0f, bounceY, 0f), o.rotation, spins, scale)
    }

    /** Kamera konumu ve baktığı nokta. */
    fun camera(s: Scene3D, t: Float): Pair<Vec3, Vec3> {
        val f = ease(t / s.duration)
        return s.cameraStart.position.lerp(s.cameraEnd.position, f) to
            s.cameraStart.target.lerp(s.cameraEnd.target, f)
    }

    fun captionAt(s: Scene3D, t: Float): String? =
        s.captions.firstOrNull { t >= it.start && t < it.end }?.text
}
