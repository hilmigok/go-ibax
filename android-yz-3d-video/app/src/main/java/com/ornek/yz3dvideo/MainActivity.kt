package com.ornek.yz3dvideo

import android.content.Intent
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

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnDemo.setOnClickListener { showScene(SceneParser.parse(DemoScene.JSON)) }
        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnExport.setOnClickListener { exportVideo() }

        showScene(SceneParser.parse(DemoScene.JSON))
    }

    override fun onResume() {
        super.onResume()
        binding.preview.onResume()
    }

    override fun onPause() {
        binding.preview.onPause()
        super.onPause()
    }

    private fun showScene(scene: Scene3D) {
        currentScene = scene
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
        hideKeyboard()
        setBusy(true, getString(R.string.status_generating), indeterminate = true)
        lifecycleScope.launch {
            try {
                val scene = withContext(Dispatchers.IO) {
                    val json = GeminiClient(key, store.model).generateScene(prompt)
                    SceneParser.parse(json)
                }
                setBusy(false)
                showScene(scene)
            } catch (e: Exception) {
                setBusy(false)
                binding.status.text = getString(R.string.error_prefix, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun exportVideo() {
        val scene = currentScene ?: return
        setBusy(true, getString(R.string.status_exporting, 0), indeterminate = false)
        lifecycleScope.launch {
            try {
                val uri = withContext(Dispatchers.Default) {
                    VideoExporter(applicationContext).export(scene) { p ->
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
            visibility = if (value) View.VISIBLE else View.INVISIBLE
            if (value) {
                // Belirsiz/belirli mod yalnızca gizliyken değiştirilebilir.
                if (isIndeterminate != indeterminate) {
                    visibility = View.INVISIBLE
                    isIndeterminate = indeterminate
                    visibility = View.VISIBLE
                }
                if (!indeterminate) progress = 0
            }
        }
        message?.let { binding.status.text = it }
        updateButtons()
    }

    private fun updateButtons() {
        binding.btnGenerate.isEnabled = !busy
        binding.btnDemo.isEnabled = !busy
        binding.btnExport.isEnabled = !busy && currentScene != null
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.prompt.windowToken, 0)
    }
}
