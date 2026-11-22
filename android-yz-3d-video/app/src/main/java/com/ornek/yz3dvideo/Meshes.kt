package com.ornek.yz3dvideo

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Köşe başına 6 float: konum (x,y,z) + normal (nx,ny,nz). Üçgen listesi olarak çizilir. */
class Mesh(val buffer: FloatBuffer, val vertexCount: Int)

/** Birim boyutlu ilkel şekilleri üretir (bkz. ScenePrompt'taki boyut tanımları). */
object Meshes {

    fun build(): Map<Shape, Mesh> = mapOf(
        Shape.CUBE to cube(),
        Shape.SPHERE to sphere(18, 28),
        Shape.CYLINDER to cylinder(28),
        Shape.CONE to cone(28),
        Shape.PLANE to plane()
    )

    private class Builder {
        val data = ArrayList<Float>(4096)
        fun v(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float) {
            data.add(px); data.add(py); data.add(pz); data.add(nx); data.add(ny); data.add(nz)
        }
        fun mesh(): Mesh {
            val arr = data.toFloatArray()
            val fb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            fb.put(arr).position(0)
            return Mesh(fb, arr.size / 6)
        }
    }

    private fun cube(): Mesh {
        val b = Builder()
        // (normal, u ekseni, v ekseni)
        val faces = arrayOf(
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            floatArrayOf(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
            floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
            floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, -1f, 1f, 0f, 0f, 0f, 1f, 0f)
        )
        for (f in faces) {
            fun corner(su: Float, sv: Float) = floatArrayOf(
                f[0] * 0.5f + f[3] * su + f[6] * sv,
                f[1] * 0.5f + f[4] * su + f[7] * sv,
                f[2] * 0.5f + f[5] * su + f[8] * sv
            )
            val c = listOf(corner(-0.5f, -0.5f), corner(0.5f, -0.5f), corner(0.5f, 0.5f), corner(-0.5f, 0.5f))
            for (i in intArrayOf(0, 1, 2, 0, 2, 3)) b.v(c[i][0], c[i][1], c[i][2], f[0], f[1], f[2])
        }
        return b.mesh()
    }

    private fun sphere(stacks: Int, slices: Int): Mesh {
        val b = Builder()
        fun point(i: Int, j: Int) {
            val theta = PI.toFloat() * i / stacks
            val phi = 2f * PI.toFloat() * j / slices
            val nx = sin(theta) * cos(phi)
            val ny = cos(theta)
            val nz = sin(theta) * sin(phi)
            b.v(nx * 0.5f, ny * 0.5f, nz * 0.5f, nx, ny, nz)
        }
        for (i in 0 until stacks) for (j in 0 until slices) {
            point(i, j); point(i + 1, j); point(i + 1, j + 1)
            point(i, j); point(i + 1, j + 1); point(i, j + 1)
        }
        return b.mesh()
    }

    private fun cylinder(segments: Int): Mesh {
        val b = Builder()
        for (j in 0 until segments) {
            val a0 = 2f * PI.toFloat() * j / segments
            val a1 = 2f * PI.toFloat() * (j + 1) / segments
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            // Yan yüzey
            b.v(c0 * 0.5f, -0.5f, s0 * 0.5f, c0, 0f, s0)
            b.v(c1 * 0.5f, -0.5f, s1 * 0.5f, c1, 0f, s1)
            b.v(c1 * 0.5f, 0.5f, s1 * 0.5f, c1, 0f, s1)
            b.v(c0 * 0.5f, -0.5f, s0 * 0.5f, c0, 0f, s0)
            b.v(c1 * 0.5f, 0.5f, s1 * 0.5f, c1, 0f, s1)
            b.v(c0 * 0.5f, 0.5f, s0 * 0.5f, c0, 0f, s0)
            // Üst ve alt kapak
            b.v(0f, 0.5f, 0f, 0f, 1f, 0f); b.v(c0 * 0.5f, 0.5f, s0 * 0.5f, 0f, 1f, 0f); b.v(c1 * 0.5f, 0.5f, s1 * 0.5f, 0f, 1f, 0f)
            b.v(0f, -0.5f, 0f, 0f, -1f, 0f); b.v(c1 * 0.5f, -0.5f, s1 * 0.5f, 0f, -1f, 0f); b.v(c0 * 0.5f, -0.5f, s0 * 0.5f, 0f, -1f, 0f)
        }
        return b.mesh()
    }

    private fun cone(segments: Int): Mesh {
        val b = Builder()
        // Yan yüzey normali: (cos a, r/h, sin a) normalize; r = 0.5, h = 1
        fun n(a: Float): FloatArray {
            val x = cos(a); val y = 0.5f; val z = sin(a)
            val l = sqrt(x * x + y * y + z * z)
            return floatArrayOf(x / l, y / l, z / l)
        }
        for (j in 0 until segments) {
            val a0 = 2f * PI.toFloat() * j / segments
            val a1 = 2f * PI.toFloat() * (j + 1) / segments
            val n0 = n(a0); val n1 = n(a1); val nm = n((a0 + a1) / 2f)
            b.v(cos(a0) * 0.5f, -0.5f, sin(a0) * 0.5f, n0[0], n0[1], n0[2])
            b.v(cos(a1) * 0.5f, -0.5f, sin(a1) * 0.5f, n1[0], n1[1], n1[2])
            b.v(0f, 0.5f, 0f, nm[0], nm[1], nm[2])
            // Taban
            b.v(0f, -0.5f, 0f, 0f, -1f, 0f)
            b.v(cos(a1) * 0.5f, -0.5f, sin(a1) * 0.5f, 0f, -1f, 0f)
            b.v(cos(a0) * 0.5f, -0.5f, sin(a0) * 0.5f, 0f, -1f, 0f)
        }
        return b.mesh()
    }

    private fun plane(): Mesh {
        val b = Builder()
        b.v(-0.5f, 0f, -0.5f, 0f, 1f, 0f); b.v(0.5f, 0f, -0.5f, 0f, 1f, 0f); b.v(0.5f, 0f, 0.5f, 0f, 1f, 0f)
        b.v(-0.5f, 0f, -0.5f, 0f, 1f, 0f); b.v(0.5f, 0f, 0.5f, 0f, 1f, 0f); b.v(-0.5f, 0f, 0.5f, 0f, 1f, 0f)
        return b.mesh()
    }
}
