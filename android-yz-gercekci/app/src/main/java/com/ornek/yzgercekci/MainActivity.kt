package com.ornek.yzgercekci

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import com.ornek.yzgercekci.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var board: Storyboard? = null
    private val images = ArrayList<Bitmap?>()
    private var boardPortrait = true          // senaryo/görsellerin üretildiği yön
    private var format = VideoFormat.PORTRAIT_9_16
    private var style = VisualStyle.CINEMATIC
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.formatGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            format = if (checkedId == R.id.btnLandscape) VideoFormat.LANDSCAPE_16_9 else VideoFormat.PORTRAIT_9_16
            applyPreviewSize()
            if (board != null && boardPortrait != format.isPortrait) {
                binding.status.text = getString(R.string.format_changed_hint)
            }
        }

        val styles = VisualStyle.entries
        binding.style.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, styles.map { it.label }))
        binding.style.setText(style.label, false)
        binding.style.setOnItemClickListener { _, _, pos, _ -> style = styles[pos] }

        binding.shotCount.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            binding.shotCountLabel.text = getString(R.string.shot_count, value.toInt())
        })
        binding.shotCountLabel.text = getString(R.string.shot_count, binding.shotCount.value.toInt())

        binding.captions.setOnCheckedChangeListener { _, checked -> binding.preview.captions = checked }

        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.btnGenerate.setOnClickListener { generateAll() }
        binding.btnExport.setOnClickListener { exportVideo() }
        binding.btnSavePhotos.setOnClickListener { savePhotos() }
        binding.btnCancel.setOnClickListener { job?.cancel() }

        binding.root.post { applyPreviewSize() }
        binding.preview.setContent(null, emptyList())
        updateButtons()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.root.post { applyPreviewSize() }
    }

    /** Önizlemeyi seçilen orana getirir; ekran yüksekliğinin %62'sini aşmaz ve ortalanır. */
    private fun applyPreviewSize() {
        val container = binding.preview.parent as? View ?: return
        val availW = container.width - container.paddingLeft - container.paddingRight
        if (availW <= 0) return
        val maxH = (resources.displayMetrics.heightPixels * 0.62f).toInt()
        val ratio = format.width.toFloat() / format.height
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

    private fun apiKeyOrSettings(): SecretStore? {
        val store = SecretStore(this)
        if (store.apiKey.isBlank()) {
            binding.status.text = getString(R.string.need_key)
            startActivity(Intent(this, SettingsActivity::class.java))
            return null
        }
        return store
    }

    // ---------------------------------------------------------------- üretim

    private fun generateAll() {
        val text = binding.prompt.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            binding.prompt.error = getString(R.string.empty_prompt)
            return
        }
        val store = apiKeyOrSettings() ?: return
        val portrait = format.isPortrait
        val count = binding.shotCount.value.toInt()
        val captions = binding.captions.isChecked
        val chosenStyle = style
        hideKeyboard()

        job = lifecycleScope.launch {
            setBusy(true, getString(R.string.status_planning), indeterminate = true)
            try {
                val client = GeminiClient(store.apiKey)
                val newBoard = withContext(Dispatchers.IO) {
                    retrying {
                        val json = client.generateStoryboard(
                            store.textModel,
                            StoryboardPrompt.userMessage(text, count, portrait, captions)
                        )
                        StoryboardParser.parse(json)
                    }
                }
                clearImages()
                board = newBoard
                boardPortrait = portrait
                images.addAll(List(newBoard.shots.size) { null })
                rebuildThumbs()
                binding.preview.setContent(newBoard, images)

                var failures = 0
                var lastError = ""
                for (i in newBoard.shots.indices) {
                    setBusy(true, getString(R.string.status_image, i + 1, newBoard.shots.size), indeterminate = false)
                    binding.progress.setProgressCompat(i * 100 / newBoard.shots.size, true)
                    try {
                        images[i] = renderShot(client, store, newBoard, i, chosenStyle, portrait)
                    } catch (e: GeminiException) {
                        failures++
                        lastError = e.message.orEmpty()
                        // Kota/anahtar hatasında diğer kareleri denemenin anlamı yok.
                        if (lastError.contains("(401") || lastError.contains("(403") || lastError.contains("(404") ||
                            lastError.contains("faturalandırma")
                        ) throw e
                    }
                    rebuildThumbs()
                    binding.preview.setContent(newBoard, images)
                }
                setBusy(false)
                binding.status.text = if (failures == 0) {
                    getString(R.string.status_ready, newBoard.title)
                } else {
                    getString(R.string.status_partial, failures, lastError)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                setBusy(false, getString(R.string.status_cancelled))
            } catch (e: Exception) {
                setBusy(false)
                binding.status.text = getString(R.string.error_prefix, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Tek bir kareyi üretir; tutarlılık açıksa en yakın önceki kareyi referans gönderir. */
    private suspend fun renderShot(
        client: GeminiClient, store: SecretStore, b: Storyboard, i: Int, s: VisualStyle, portrait: Boolean,
        promptOverride: String? = null
    ): Bitmap {
        val ref = if (store.consistency && !GeminiClient.isImagen(store.imageModel)) {
            (i - 1 downTo 0).firstNotNullOfOrNull { images.getOrNull(it) }
                ?: if (i == 0) images.drop(1).firstOrNull { it != null } else null
        } else null
        val refBytes = ref?.let { withContext(Dispatchers.Default) { ImageSaver.referenceJpeg(it) } }
        val prompt = StoryboardPrompt.imagePrompt(promptOverride ?: b.shots[i].prompt, s, portrait)
        return withContext(Dispatchers.IO) {
            retrying {
                val bytes = client.generateImage(store.imageModel, prompt, portrait, refBytes)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw GeminiException("Görüntü çözülemedi", retryable = true)
            }
        }
    }

    /** Geçici hatalarda (429, 5xx, bağlantı) artan beklemeyle en fazla 3 kez dener. */
    private suspend fun <T> retrying(block: suspend () -> T): T {
        var wait = 4_000L
        repeat(2) {
            try {
                return block()
            } catch (e: GeminiException) {
                if (!e.retryable) throw e
                delay(wait)
                wait *= 3
            }
        }
        return block()
    }

    /** Küçük resme dokununca: tarifi düzenleyip o kareyi yeniden üret ya da fotoğrafı kaydet. */
    private fun onThumbClicked(i: Int) {
        val b = board ?: return
        if (job?.isActive == true) return
        val input = EditText(this).apply {
            setText(b.shots[i].prompt)
            minLines = 4
            maxLines = 10
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.shot_title, i + 1))
            .setMessage(R.string.shot_dialog_info)
            .setView(box)
            .setPositiveButton(R.string.regenerate) { _, _ -> regenerate(i, input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
        if (images.getOrNull(i) != null) {
            dialog.setNeutralButton(R.string.save_photo) { _, _ -> savePhoto(i) }
        }
        dialog.show()
    }

    private fun regenerate(i: Int, newPrompt: String) {
        val b = board ?: return
        val store = apiKeyOrSettings() ?: return
        val chosenStyle = style
        val portrait = boardPortrait
        job = lifecycleScope.launch {
            setBusy(true, getString(R.string.status_image, i + 1, b.shots.size), indeterminate = true)
            try {
                val prompt = newPrompt.ifEmpty { b.shots[i].prompt }
                val bmp = renderShot(GeminiClient(store.apiKey), store, b, i, chosenStyle, portrait, prompt)
                val updated = b.copy(shots = b.shots.toMutableList().also { it[i] = it[i].copy(prompt = prompt) })
                board = updated
                images[i]?.let { old -> binding.preview.post { old.recycle() } }
                images[i] = bmp
                rebuildThumbs()
                binding.preview.setContent(updated, images)
                setBusy(false, getString(R.string.status_ready, updated.title))
            } catch (e: kotlinx.coroutines.CancellationException) {
                setBusy(false, getString(R.string.status_cancelled))
            } catch (e: Exception) {
                setBusy(false)
                binding.status.text = getString(R.string.error_prefix, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    // ---------------------------------------------------------------- kaydetme

    private fun exportVideo() {
        val b = board ?: return
        if (images.none { it != null }) return
        val targetFormat = format
        val captions = binding.captions.isChecked
        val snapshot = images.toList()
        job = lifecycleScope.launch {
            setBusy(true, getString(R.string.status_exporting, 0), indeterminate = false)
            binding.btnCancel.visibility = View.GONE   // kodlama yarıda kesilemez
            binding.preview.playing = false
            try {
                val uri = withContext(Dispatchers.Default) {
                    VideoExporter(applicationContext).export(b, snapshot, targetFormat, captions) { p ->
                        val pct = (p * 100).toInt()
                        binding.root.post {
                            binding.progress.setProgressCompat(pct, true)
                            binding.status.text = getString(R.string.status_exporting, pct)
                        }
                    }
                }
                setBusy(false, getString(R.string.status_saved))
                share(uri, "video/mp4")
            } catch (e: Exception) {
                setBusy(false)
                binding.status.text = getString(R.string.error_prefix, e.message ?: e.javaClass.simpleName)
            } finally {
                binding.preview.playing = true
            }
        }
    }

    private fun savePhoto(i: Int) {
        val b = board ?: return
        val bmp = images.getOrNull(i) ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { ImageSaver.saveToGallery(applicationContext, bmp, "${b.title}_${i + 1}") }
                Toast.makeText(this@MainActivity, R.string.photo_saved, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.status.text = getString(R.string.error_prefix, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun savePhotos() {
        val b = board ?: return
        val list = images.withIndex().filter { it.value != null }
        if (list.isEmpty()) return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    for ((i, bmp) in list) ImageSaver.saveToGallery(applicationContext, bmp!!, "${b.title}_${i + 1}")
                }
                binding.status.text = getString(R.string.photos_saved, list.size)
            } catch (e: Exception) {
                binding.status.text = getString(R.string.error_prefix, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun share(uri: Uri, mime: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.share_title)))
    }

    // ---------------------------------------------------------------- arayüz

    private fun clearImages() {
        val old = images.toList()
        images.clear()
        // Önizleme eski bitmap'leri çizmeyi bıraktıktan sonra serbest bırak.
        binding.preview.post { old.forEach { it?.recycle() } }
    }

    private fun rebuildThumbs() {
        val strip = binding.thumbs
        strip.removeAllViews()
        val b = board ?: return
        val d = resources.displayMetrics.density
        val h = (96 * d).toInt()
        val w = if (boardPortrait) (h * 9 / 16) else (h * 16 / 9)
        for (i in b.shots.indices) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(w, h).apply { marginEnd = (6 * d).toInt() }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF22232A.toInt())
                contentDescription = getString(R.string.shot_title, i + 1)
                images.getOrNull(i)?.let { setImageBitmap(it) } ?: setImageResource(android.R.drawable.ic_menu_gallery)
                setOnClickListener { onThumbClicked(i) }
            }
            strip.addView(iv)
        }
        binding.thumbsHint.visibility = View.VISIBLE
    }

    private fun setBusy(value: Boolean, message: String? = null, indeterminate: Boolean = true) {
        binding.progress.apply {
            if (value) {
                visibility = View.INVISIBLE
                isIndeterminate = indeterminate
                if (!indeterminate) progress = 0
                visibility = View.VISIBLE
            } else {
                visibility = View.INVISIBLE
            }
        }
        message?.let { binding.status.text = it }
        if (!value) job = null
        updateButtons(value)
    }

    private fun updateButtons(busy: Boolean = job?.isActive == true) {
        val hasImages = images.any { it != null }
        binding.btnGenerate.isEnabled = !busy
        binding.btnExport.isEnabled = !busy && board != null && hasImages
        binding.btnSavePhotos.isEnabled = !busy && hasImages
        binding.btnCancel.visibility = if (busy) View.VISIBLE else View.GONE
        binding.shotCount.isEnabled = !busy
        for (i in 0 until binding.formatGroup.childCount) binding.formatGroup.getChildAt(i).isEnabled = !busy
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.prompt.windowToken, 0)
    }
}
