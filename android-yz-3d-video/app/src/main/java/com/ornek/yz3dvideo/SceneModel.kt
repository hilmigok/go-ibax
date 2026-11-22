package com.ornek.yz3dvideo

import kotlin.math.sqrt

/** Basit 3 boyutlu vektör (metre). Y yukarıdır, zemin y = 0 düzlemidir. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    fun lerp(o: Vec3, f: Float) = Vec3(x + (o.x - x) * f, y + (o.y - y) * f, z + (o.z - z) * f)
    fun length() = sqrt(x * x + y * y + z * z)

    fun normalizedOr(fallback: Vec3): Vec3 {
        val l = length()
        return if (l < 1e-5f) fallback else Vec3(x / l, y / l, z / l)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
        val UP = Vec3(0f, 1f, 0f)
    }
}

/** 0..1 aralığında renk. */
data class Rgb(val r: Float, val g: Float, val b: Float)

/** Tüm nesneler bu ilkel şekillerden kurulur. */
enum class Shape { CUBE, SPHERE, CYLINDER, CONE, PLANE }

/** Bir nesnenin parçası; konum/dönüş/ölçek nesnenin merkezine göredir. Dönüş derece cinsindendir. */
data class Part(
    val shape: Shape,
    val color: Rgb,
    val position: Vec3,
    val scale: Vec3,
    val rotation: Vec3
)

/** Nesne animasyonları. Zamanlar saniyedir. */
sealed class Anim {
    abstract val start: Float
    abstract val end: Float

    data class Move(val to: Vec3, override val start: Float, override val end: Float) : Anim()
    data class Rotate(val axis: Vec3, val speed: Float, override val start: Float, override val end: Float) : Anim()
    data class Bounce(val height: Float, val speed: Float, override val start: Float, override val end: Float) : Anim()
    data class Scale(val to: Float, override val start: Float, override val end: Float) : Anim()
    data class Orbit(
        val center: Vec3, val radius: Float, val speed: Float,
        override val start: Float, override val end: Float
    ) : Anim()
}

data class SceneObject(
    val id: String,
    val parts: List<Part>,
    val position: Vec3,
    val rotation: Vec3,
    val scale: Float,
    val anims: List<Anim>
)

data class CameraKey(val position: Vec3, val target: Vec3)

data class Caption(val text: String, val start: Float, val end: Float)

data class Scene3D(
    val title: String,
    val duration: Float,
    val skyTop: Rgb,
    val skyBottom: Rgb,
    val groundColor: Rgb,
    val groundVisible: Boolean,
    val lightDir: Vec3,
    val ambient: Float,
    val cameraStart: CameraKey,
    val cameraEnd: CameraKey,
    val objects: List<SceneObject>,
    val captions: List<Caption>
)
