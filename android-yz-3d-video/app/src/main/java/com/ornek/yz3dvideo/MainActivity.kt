package com.ornek.yz3dvideo

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ornek.yz3dvideo.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val previewRenderer = PreviewRenderer()
    private var currentScene: Scene3D? = null
    private var sceneFormat: VideoFormat? = null   // sahnenin hangi biçim için üretildiği (YZ sahneleri)
    private var format = VideoFormat.PORTRAIT_9_16
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.preview.apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            preserveEGLContextOnPause = true
            setRenderer(previewRenderer)
        }

        binding.formatGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            format = if (checkedId == R.id.btnLandscape) VideoFormat.LANDSCAPE_16_9 else VideoFormat.PORTRAIT_9_16
            applyPreviewSize()
            if (sceneFormat != null && sceneFormat != format) {
                binding.status.text = getString(R.string.format_changed_hint)
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnDemo.setOnClickListener { showScene(SceneParser.parse(DemoScene.JSON), null) }
        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnExport.setOnClickListener { exportVideo() }

        binding.root.post { applyPreviewSize() }
        showScene(SceneParser.parse(DemoScene.JSON), null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.root.post { applyPreviewSize() }
    }

    override fun onResume() {
        super.onResume()
        binding.preview.onResume()
    }

    override fun onPause() {
        binding.preview.onPause()
        super.onPause()
    }

    /** Önizlemeyi seçilen orana getirir; ekran yüksekliğinin %60'ını aşmaz ve ortalanır. */
    private fun applyPreviewSize() {
        val container = binding.preview.parent as? View ?: return
        val availW = container.width - container.paddingLeft - container.paddingRight
        if (availW <= 0) return
        val maxH = (resources.displayMetrics.heightPixels * 0.6f).toInt()
        val ratio = format.width.toFloat() / format.height   // genişlik / yükseklik
        var w = availW
        var h = (w / ratio).toInt()
        if (h > maxH) {
            h = maxH
            w = (h * ratio).toInt()
        }
        val lp = binding.preview.layoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            binding.preview.layoutParams = lp
        }
    }

    private fun showScene(scene: Scene3D, generatedFor: VideoFormat?) {
        currentScene = scene
        sceneFormat = generatedFor
        previewRenderer.setScene(scene)
        binding.status.text = getString(R.string.status_ready, scene.title)
        updateButtons()
    }

    private fun generate() {
        val prompt = binding.prompt.text?.toString()?.trim().orEmpty()
        if (prompt.isEmpty()) {
            binding.prompt.error = getString(R.string.empty_prompt)
            return
        }
        val store = SecretStore(this)
        val key = store.apiKey
        if (key.isBlank()) {
            binding.status.text = getString(R.string.need_key)
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        val targetFormat = format
        hideKeyboard()
        setBusy(true, getString(R.string.status_generating), indeterminate = true)
        lifecycleScope.launch {
            try {
                val scene = withContext(Dispatchers.IO) {
                    val json = GeminiClient(key, store.model)
                        .generateScene(ScenePrompt.userMessage(prompt, targetFormat))
                    SceneParser.parse(json)
                }
                setBusy(false)
                showScene(scene, targetFormat)
            } catch (e: Exception) {
                setBusy(false)
                binding.status.text = getString(R.string.error_prefix, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun exportVideo() {
        val scene = currentScene ?: return
        val targetFormat = format
        setBusy(true, getString(R.string.status_exporting, 0), indeterminate = false)
        lifecycleScope.launch {
            try {
                val uri = withContext(Dispatchers.Default) {
                    VideoExporter(applicationContext).export(scene, targetFormat) { p ->
                        val pct = (p * 100).toInt()
                        binding.root.post {
                            binding.progress.setProgressCompat(pct, true)
                            binding.status.text = getString(R.string.status_exporting, pct)
                        }
                    }
                }
                setBusy(false)
                binding.status.text = getString(R.string.status_saved)
                share(uri)
            } catch (e: Exception) {
                setBusy(false)
                binding.status.text = getString(R.string.error_prefix, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun share(uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
    }

    private fun setBusy(value: Boolean, message: String? = null, indeterminate: Boolean = true) {
        busy = value
        binding.progress.apply {
            if (value) {
                // Belirsiz/belirli mod yalnızca gizliyken değiştirilebilir.
                visibility = View.INVISIBLE
                isIndeterminate = indeterminate
                if (!indeterminate) progress = 0
                visibility = View.VISIBLE
            } else {
                visibility = View.INVISIBLE
            }
        }
        message?.let { binding.status.text = it }
        updateButtons()
    }

    private fun updateButtons() {
        binding.btnGenerate.isEnabled = !busy
        binding.btnDemo.isEnabled = !busy
        binding.btnExport.isEnabled = !busy && currentScene != null
        for (i in 0 until binding.formatGroup.childCount) binding.formatGroup.getChildAt(i).isEnabled = !busy
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.prompt.windowToken, 0)
    }
}
