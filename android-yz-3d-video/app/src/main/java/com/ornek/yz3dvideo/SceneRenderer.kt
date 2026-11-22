package com.ornek.yz3dvideo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.GLES20.*
import android.opengl.GLUtils
import android.opengl.Matrix
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs

/**
 * Bir [Scene3D]'yi verilen t anında, o an bağlı olan OpenGL ES 2.0 bağlamına çizer.
 * Hem ekrandaki önizleme hem de video dışa aktarma bu sınıfı kullanır.
 * [init] her yeni GL bağlamında bir kez çağrılmalıdır.
 */
class SceneRenderer {

    private var meshProg = 0
    private var aPos = 0
    private var aNormal = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uLight = 0
    private var uAmbient = 0

    private var skyProg = 0
    private var skyPos = 0
    private var uTop = 0
    private var uBottom = 0

    private var texProg = 0
    private var texPos = 0
    private var texUv = 0
    private var uTex = 0

    private var meshes: Map<Shape, Mesh> = emptyMap()
    private val skyQuad = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val captionQuad = floatBuffer(FloatArray(16))
    private val caption = CaptionTexture()

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val obj = FloatArray(16)
    private val part = FloatArray(16)
    private val mvp = FloatArray(16)

    fun init() {
        meshProg = program(MESH_VS, MESH_FS)
        aPos = glGetAttribLocation(meshProg, "aPos")
        aNormal = glGetAttribLocation(meshProg, "aNormal")
        uMvp = glGetUniformLocation(meshProg, "uMvp")
        uModel = glGetUniformLocation(meshProg, "uModel")
        uColor = glGetUniformLocation(meshProg, "uColor")
        uLight = glGetUniformLocation(meshProg, "uLight")
        uAmbient = glGetUniformLocation(meshProg, "uAmbient")

        skyProg = program(SKY_VS, SKY_FS)
        skyPos = glGetAttribLocation(skyProg, "aPos")
        uTop = glGetUniformLocation(skyProg, "uTop")
        uBottom = glGetUniformLocation(skyProg, "uBottom")

        texProg = program(TEX_VS, TEX_FS)
        texPos = glGetAttribLocation(texProg, "aPos")
        texUv = glGetAttribLocation(texProg, "aUv")
        uTex = glGetUniformLocation(texProg, "uTex")

        if (meshes.isEmpty()) meshes = Meshes.build()
        caption.reset()
    }

    fun render(scene: Scene3D?, t: Float, width: Int, height: Int) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        glViewport(0, 0, w, h)
        if (scene == null) {
            glClearColor(0.12f, 0.13f, 0.17f, 1f)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            return
        }
        glClearColor(0f, 0f, 0f, 1f)
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glDisable(GL_BLEND)

        drawSky(scene)

        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)

        val (eye, target) = Animator.camera(scene, t)
        // Neredeyse dik aşağı bakılıyorsa "yukarı" vektörü bakış yönüne paralel olur; başka eksen seç.
        val dir = target - eye
        val horiz = abs(dir.x) + abs(dir.z)
        val up = if (horiz < 0.001f * (abs(dir.y) + 1f)) Vec3(0f, 0f, -1f) else Vec3.UP
        // Dikey kadrajda yatay görüş açısı daralır; dikey açıyı büyüterek kenarların kesilmesini önle.
        val fovY = if (h > w) 75f else 50f
        Matrix.perspectiveM(proj, 0, fovY, w.toFloat() / h, 0.3f, 500f)
        Matrix.setLookAtM(view, 0, eye.x, eye.y, eye.z, target.x, target.y, target.z, up.x, up.y, up.z)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)

        glUseProgram(meshProg)
        glUniform3f(uLight, scene.lightDir.x, scene.lightDir.y, scene.lightDir.z)
        glUniform1f(uAmbient, scene.ambient)

        if (scene.groundVisible) {
            Matrix.setIdentityM(obj, 0)
            Matrix.scaleM(obj, 0, 400f, 1f, 400f)
            drawMesh(Shape.PLANE, obj, scene.groundColor)
        }

        for (o in scene.objects) {
            val pose = Animator.pose(o, t)
            Matrix.setIdentityM(obj, 0)
            Matrix.translateM(obj, 0, pose.position.x, pose.position.y, pose.position.z)
            rotateEuler(obj, pose.rotation)
            for (s in pose.spins) Matrix.rotateM(obj, 0, s.degrees, s.axis.x, s.axis.y, s.axis.z)
            Matrix.scaleM(obj, 0, pose.scale, pose.scale, pose.scale)

            for (p in o.parts) {
                System.arraycopy(obj, 0, part, 0, 16)
                Matrix.translateM(part, 0, p.position.x, p.position.y, p.position.z)
                rotateEuler(part, p.rotation)
                Matrix.scaleM(part, 0, p.scale.x, p.scale.y, p.scale.z)
                drawMesh(p.shape, part, p.color)
            }
        }

        glDisable(GL_DEPTH_TEST)
        Animator.captionAt(scene, t)?.let { drawCaption(it, w, h) }
    }

    private fun rotateEuler(m: FloatArray, r: Vec3) {
        if (r.y != 0f) Matrix.rotateM(m, 0, r.y, 0f, 1f, 0f)
        if (r.x != 0f) Matrix.rotateM(m, 0, r.x, 1f, 0f, 0f)
        if (r.z != 0f) Matrix.rotateM(m, 0, r.z, 0f, 0f, 1f)
    }

    private fun drawMesh(shape: Shape, model: FloatArray, color: Rgb) {
        val mesh = meshes[shape] ?: return
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        glUniformMatrix4fv(uModel, 1, false, model, 0)
        glUniform3f(uColor, color.r, color.g, color.b)
        mesh.buffer.position(0)
        glVertexAttribPointer(aPos, 3, GL_FLOAT, false, 24, mesh.buffer)
        glEnableVertexAttribArray(aPos)
        mesh.buffer.position(3)
        glVertexAttribPointer(aNormal, 3, GL_FLOAT, false, 24, mesh.buffer)
        glEnableVertexAttribArray(aNormal)
        glDrawArrays(GL_TRIANGLES, 0, mesh.vertexCount)
    }

    private fun drawSky(scene: Scene3D) {
        glDisable(GL_DEPTH_TEST)
        glUseProgram(skyProg)
        glUniform3f(uTop, scene.skyTop.r, scene.skyTop.g, scene.skyTop.b)
        glUniform3f(uBottom, scene.skyBottom.r, scene.skyBottom.g, scene.skyBottom.b)
        skyQuad.position(0)
        glVertexAttribPointer(skyPos, 2, GL_FLOAT, false, 8, skyQuad)
        glEnableVertexAttribArray(skyPos)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawCaption(text: String, w: Int, h: Int) {
        val tex = caption.textureFor(text, w, h)
        if (tex == 0) return
        // Dikey videoda TikTok/Reels/Shorts alt kısma kendi arayüzünü koyar; altyazıyı yukarı al.
        val bottom = if (h > w) -1f + 2f * 0.20f else -1f
        val top = bottom + 2f * caption.heightFraction
        captionQuad.clear()
        captionQuad.put(floatArrayOf(-1f, bottom, 0f, 1f, 1f, bottom, 1f, 1f, -1f, top, 0f, 0f, 1f, top, 1f, 0f))
        glEnable(GL_BLEND)
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA) // bitmap önceden çarpılmış alfa kullanır
        glUseProgram(texProg)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, tex)
        glUniform1i(uTex, 0)
        captionQuad.position(0)
        glVertexAttribPointer(texPos, 2, GL_FLOAT, false, 16, captionQuad)
        glEnableVertexAttribArray(texPos)
        captionQuad.position(2)
        glVertexAttribPointer(texUv, 2, GL_FLOAT, false, 16, captionQuad)
        glEnableVertexAttribArray(texUv)
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        glDisable(GL_BLEND)
    }

    /** Altyazıyı bir bitmap'e yazıp dokuya yükler; metin değişmedikçe yeniden üretmez. */
    private class CaptionTexture {
        private var tex = 0
        private var key = ""
        var heightFraction = 0.2f
            private set

        fun reset() {
            tex = 0
            key = ""
        }

        fun textureFor(text: String, w: Int, h: Int): Int {
            val k = "$w|$h|$text"
            if (k == key && tex != 0) return tex

            // Boyutlar kısa kenara göre: yatay ve dikey videoda yazı aynı oranda görünür.
            val base = minOf(w, h).toFloat()
            val bh = (base * 0.3f).toInt().coerceAtLeast(32)
            heightFraction = bh.toFloat() / h
            val bmp = Bitmap.createBitmap(w, bh, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = base * 0.05f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val margin = w * 0.06f
            val pad = base * 0.02f
            val textWidth = (w - 2 * margin - 2 * pad).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            val boxH = layout.height + 2 * pad
            val boxTop = (bh - boxH - base * 0.03f).coerceAtLeast(0f)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
            canvas.drawRoundRect(RectF(margin, boxTop, w - margin, boxTop + boxH), pad, pad, bg)
            canvas.translate(margin + pad, boxTop + pad)
            layout.draw(canvas)

            if (tex == 0) {
                val ids = IntArray(1)
                glGenTextures(1, ids, 0)
                tex = ids[0]
            }
            glBindTexture(GL_TEXTURE_2D, tex)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GL_TEXTURE_2D, 0, bmp, 0)
            bmp.recycle()
            key = k
            return tex
        }
    }

    companion object {
        private const val MESH_VS = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            attribute vec3 aPos;
            attribute vec3 aNormal;
            varying vec3 vNormal;
            void main() {
                vNormal = (uModel * vec4(aNormal, 0.0)).xyz;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """
        private const val MESH_FS = """
            precision mediump float;
            uniform vec3 uColor;
            uniform vec3 uLight;
            uniform float uAmbient;
            varying vec3 vNormal;
            void main() {
                float d = max(dot(normalize(vNormal), normalize(-uLight)), 0.0);
                gl_FragColor = vec4(uColor * (uAmbient + (1.0 - uAmbient) * d), 1.0);
            }
        """
        private const val SKY_VS = """
            attribute vec2 aPos;
            varying float vY;
            void main() {
                vY = aPos.y * 0.5 + 0.5;
                gl_Position = vec4(aPos, 0.999, 1.0);
            }
        """
        private const val SKY_FS = """
            precision mediump float;
            uniform vec3 uTop;
            uniform vec3 uBottom;
            varying float vY;
            void main() { gl_FragColor = vec4(mix(uBottom, uTop, vY), 1.0); }
        """
        private const val TEX_VS = """
            attribute vec2 aPos;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() { vUv = aUv; gl_Position = vec4(aPos, 0.0, 1.0); }
        """
        private const val TEX_FS = """
            precision mediump float;
            uniform sampler2D uTex;
            varying vec2 vUv;
            void main() { gl_FragColor = texture2D(uTex, vUv); }
        """

        private fun floatBuffer(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(a); position(0)
            }

        private fun shader(type: Int, src: String): Int {
            val s = glCreateShader(type)
            glShaderSource(s, src)
            glCompileShader(s)
            val ok = IntArray(1)
            glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = glGetShaderInfoLog(s)
                glDeleteShader(s)
                throw RuntimeException("Gölgelendirici derlenemedi: $log")
            }
            return s
        }

        private fun program(vs: String, fs: String): Int {
            val p = glCreateProgram()
            glAttachShader(p, shader(GL_VERTEX_SHADER, vs))
            glAttachShader(p, shader(GL_FRAGMENT_SHADER, fs))
            glLinkProgram(p)
            val ok = IntArray(1)
            glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) throw RuntimeException("Program bağlanamadı: ${glGetProgramInfoLog(p)}")
            return p
        }
    }
}
