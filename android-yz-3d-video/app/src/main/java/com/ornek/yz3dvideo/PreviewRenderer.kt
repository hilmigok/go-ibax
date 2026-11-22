package com.ornek.yz3dvideo

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Ekrandaki önizleme: sahneyi döngü hâlinde, gerçek zamanlı oynatır. */
class PreviewRenderer : GLSurfaceView.Renderer {

    @Volatile
    private var scene: Scene3D? = null

    @Volatile
    private var startNs = System.nanoTime()

    private val renderer = SceneRenderer()
    private var width = 1
    private var height = 1

    fun setScene(s: Scene3D?) {
        scene = s
        startNs = System.nanoTime()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        renderer.init()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
    }

    override fun onDrawFrame(gl: GL10?) {
        val s = scene
        val t = if (s == null) 0f else ((System.nanoTime() - startNs) / 1e9f) % s.duration
        renderer.render(s, t, width, height)
    }
}
