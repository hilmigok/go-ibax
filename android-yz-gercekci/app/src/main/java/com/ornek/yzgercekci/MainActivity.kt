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
    private val faces = ArrayList<List<FaceRig>>()
    private var soundtrack: ShortArray? = null
    private val player = AudioPlayer()
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
        binding.mimics.setOnCheckedChangeListener { _, checked -> binding.preview.mimics = checked }
        binding.sounds.setOnCheckedChangeListener { _, checked ->
            binding.music.isEnabled = checked
            restartPlayback()
        }
        binding.music.setOnCheckedChangeListener { _, _ ->
            if (mixParts == null || job?.isActive == true) return@setOnCheckedChangeListener
            lifecycleScope.launch {
                soundtrack = remix()
                restartPlayback()
            }
        }

        binding.btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        binding.btnGenerate.setOnClickListener { generateAll() }
        binding.btnExport.setOnClickListener { exportVideo() }
        binding.btnSavePhotos.setOnClickListener { savePhotos() }
        binding.btnCancel.setOnClickListener { job?.cancel() }

        binding.root.post { applyPreviewSize() }
        binding.preview.setContent(null, emptyList(), emptyList())
        updateButtons()
    }

    override fun onResume() {
        super.onResume()
        restartPlayback()
    }

    override fun onPause() {
        player.stop()
        super.onPause()
    }

    override fun onDestroy() {
        player.stop()
        super.onDestroy()
    }

    /** Önizlemeyi ve sesi birlikte baştan başlatır. */
    private fun restartPlayback() {
        player.stop()
        val pcm = soundtrack
        if (binding.sounds.isChecked && pcm != null && board != null) player.play(pcm)
        binding.preview.restart()
    }

    private fun showContent() {
        binding.preview.setContent(board, images, faces.toList())
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
        val narration = binding.narration.isChecked && binding.sounds.isChecked
        val chosenStyle = style
        hideKeyboard()

        job = lifecycleScope.launch {
            setBusy(true, getString(R.string.status_planning), indeterminate = true)
            try {
                val client = GeminiClient(store.apiKey)
                val newBoard = withModel(ModelPicker.Kind.TEXT, store, client) { model ->
                    val json = client.generateStoryboard(
                        model,
                        StoryboardPrompt.userMessage(text, count, portrait, captions, narration)
                    )
                    StoryboardParser.parse(json)
                }
                clearImages()
                board = newBoard
                boardPortrait = portrait
                images.addAll(List(newBoard.shots.size) { null })
                faces.addAll(List(newBoard.shots.size) { emptyList() })
                rebuildThumbs()
                showContent()

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
                    showContent()
                }

                // Mimikler için yüzleri bul (cihaz içinde, ücretsiz).
                setBusy(true, getString(R.string.status_faces), indeterminate = true)
                detectFaces()

                // Sesler: gerçek kayıtlar / sentez, fon müziği, anlatıcı.
                var soundNote = ""
                if (binding.sounds.isChecked) {
                    val (fitted, pcm, note) = buildSound(client, store, board ?: newBoard, narration)
                    board = fitted
                    soundtrack = pcm
                    soundNote = note
                    showContent()
                }
                restartPlayback()
                setBusy(false)
                val b = board ?: newBoard
                binding.status.text = if (failures == 0) {
                    if (soundNote.isNotEmpty() || faces.any { it.isNotEmpty() }) {
                        getString(R.string.status_ready_sound, b.title, faces.sumOf { it.size }, soundNote)
                    } else getString(R.string.status_ready, b.title)
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

    private suspend fun detectFaces(only: Int? = null) {
        for (i in images.indices) {
            if (only != null && i != only) continue
            val bmp = images[i]
            val found = if (bmp == null) emptyList() else withContext(Dispatchers.Default) { FaceFinder.find(bmp) }
            while (faces.size <= i) faces.add(emptyList())
            faces[i] = found
        }
        showContent()
    }

    private data class SoundResult(val board: Storyboard, val pcm: ShortArray, val note: String)

    /**
     * Anlatıcı sesini üretir, kare sürelerini ona göre ayarlar, her kare için gerçek bir ses
     * kaydı arar (Freesound CC0; yoksa sentez), fon müziğini üretir ve hepsini karıştırır.
     */
    private suspend fun buildSound(client: GeminiClient, store: SecretStore, b: Storyboard, narration: Boolean): SoundResult {
        val n = b.shots.size
        val voices = ArrayList<FloatArray?>()
        var voiceError = ""
        for ((i, shot) in b.shots.withIndex()) {
            if (!narration || shot.narration.isEmpty() || voiceError.isNotEmpty()) { voices += null; continue }
            binding.status.text = getString(R.string.status_voice, i + 1, n)
            voices += try {
                withModel(ModelPicker.Kind.TTS, store, client) { model -> client.generateSpeech(model, store.voice, shot.narration) }
            } catch (e: GeminiException) {
                voiceError = e.message.orEmpty()   // bir kez başarısız olursa diğerlerini deneme
                null
            }
        }
        val fitted = Soundtrack.fitDurations(b, voices.map { (it?.size ?: 0).toFloat() / Soundtrack.SR })

        val fs = store.freesoundKey.takeIf { it.isNotBlank() }?.let { FreesoundClient(it) }
        val cache = HashMap<String, FloatArray?>()
        val ambience = ArrayList<FloatArray?>()
        var real = 0
        for ((i, shot) in fitted.shots.withIndex()) {
            binding.status.text = getString(R.string.status_sound, i + 1, n)
            var clip: FloatArray? = null
            if (shot.sound.isNotEmpty()) {
                // Önce Freesound (anahtar varsa), sonra anahtarsız Openverse; ikisi de yoksa sentez.
                clip = cache.getOrPut(shot.sound.lowercase()) {
                    withContext(Dispatchers.IO) {
                        (if (fs != null) fetchFreesound(fs, shot.sound) else null) ?: fetchOpenverse(shot.sound)
                    }
                }
                if (clip != null) real++
            }
            ambience += clip ?: withContext(Dispatchers.Default) {
                Synth.ambience(shot.ambience, maxOf(8f, shot.duration), seed = i + 1)
            }
        }

        binding.status.text = getString(R.string.status_mixing)
        val music = withContext(Dispatchers.Default) {
            Synth.music(fitted.music, Timeline(fitted.shots.map { it.duration }).total)
        }
        mixParts = MixParts(fitted, ambience, music, voices)
        val pcm = remix() ?: ShortArray(0)
        var note = if (real > 0) getString(R.string.sound_real, real) else getString(R.string.sound_synth)
        if (voiceError.isNotEmpty()) note += " · anlatıcı yok: ${voiceError.take(90)}"
        return SoundResult(fitted, pcm, note)
    }

    /** Karıştırmanın parçaları: fon müziği açılıp kapanınca baştan üretmeden yeniden karıştırmak için. */
    private class MixParts(val board: Storyboard, val ambience: List<FloatArray?>, val music: FloatArray, val voices: List<FloatArray?>)
    private var mixParts: MixParts? = null

    private suspend fun remix(): ShortArray? {
        val p = mixParts ?: return null
        val withMusic = binding.music.isChecked
        return withContext(Dispatchers.Default) {
            Soundtrack.mix(Timeline(p.board.shots.map { it.duration }), p.ambience, p.music.takeIf { withMusic }, p.voices)
        }
    }

    /** Openverse'te (anahtarsız) CC0 / kamu malı kayıt arar. */
    private fun fetchOpenverse(query: String): FloatArray? {
        val ov = OpenverseClient()
        val hits = try { ov.search(query) } catch (e: Exception) { return null }
        for (hit in hits.take(3)) {
            try {
                val pcm = AudioDecoder.decode(ov.download(hit, cacheDir))
                if (pcm.size > Soundtrack.SR * 2) return Soundtrack.makeLoopable(Synth.normalize(pcm, 0.8f))
            } catch (e: Exception) {
                // sıradaki kayda geç
            }
        }
        return null
    }

    /** Freesound'da arar; ilk çözülebilen kaydı döngüye uygun hale getirip döndürür. */
    private fun fetchFreesound(fs: FreesoundClient, query: String): FloatArray? {
        val hits = try { fs.search(query) } catch (e: Exception) { return null }
        for (hit in hits.take(3)) {
            try {
                val file = fs.download(hit, cacheDir)
                val pcm = AudioDecoder.decode(file)
                if (pcm.size > Soundtrack.SR * 2) return Soundtrack.makeLoopable(Synth.normalize(pcm, 0.8f))
            } catch (e: Exception) {
                // sıradaki kayda geç
            }
        }
        return null
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
        return withModel(ModelPicker.Kind.IMAGE, store, client) { model ->
            // Imagen referans görüntü desteklemez.
            val bytes = client.generateImage(model, prompt, portrait, refBytes.takeUnless { GeminiClient.isImagen(model) })
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw GeminiException("Görüntü çözülemedi", retryable = true)
        }
    }

    private var modelList: List<ModelInfo>? = null

    /**
     * Modeli çağırır; Google modeli emekliye ayırmışsa ya da bu anahtara kapalıysa,
     * kullanılabilir modeller arasından uygun olanı seçer, Ayarlar'a kaydeder ve tekrar dener.
     */
    private suspend fun <T> withModel(
        kind: ModelPicker.Kind, store: SecretStore, client: GeminiClient, block: suspend (String) -> T
    ): T {
        val current = when (kind) {
            ModelPicker.Kind.TEXT -> store.textModel
            ModelPicker.Kind.IMAGE -> store.imageModel
            ModelPicker.Kind.TTS -> store.ttsModel
        }
        try {
            return withContext(Dispatchers.IO) { retrying { block(current) } }
        } catch (e: GeminiException) {
            if (!e.modelUnavailable) throw e
            val models = modelList ?: withContext(Dispatchers.IO) { client.listModels() }.also { modelList = it }
            val replacement = ModelPicker.pick(kind, models.filter { it.name != current }, e.suggestedModel)
                ?: throw GeminiException(
                    "“$current” artık kullanılamıyor ve anahtarınla uygun başka bir model bulunamadı. " +
                        "Ayarlar’dan model adını değiştir. (${e.message.orEmpty().take(120)})"
                )
            when (kind) {
                ModelPicker.Kind.TEXT -> store.textModel = replacement
                ModelPicker.Kind.IMAGE -> store.imageModel = replacement
                ModelPicker.Kind.TTS -> store.ttsModel = replacement
            }
            binding.status.text = getString(R.string.model_switched, current, replacement)
            return withContext(Dispatchers.IO) { retrying { block(replacement) } }
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
                faces[i] = withContext(Dispatchers.Default) { FaceFinder.find(bmp) }
                rebuildThumbs()
                showContent()
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
        val faceSnapshot = faces.toList()
        val mimics = binding.mimics.isChecked
        val audio = soundtrack.takeIf { binding.sounds.isChecked }
        job = lifecycleScope.launch {
            setBusy(true, getString(R.string.status_exporting, 0), indeterminate = false)
            binding.btnCancel.visibility = View.GONE   // kodlama yarıda kesilemez
            binding.preview.playing = false
            player.stop()
            try {
                val uri = withContext(Dispatchers.Default) {
                    VideoExporter(applicationContext).export(b, snapshot, targetFormat, captions, faceSnapshot, mimics, audio) { p ->
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
                restartPlayback()
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
        player.stop()
        soundtrack = null
        mixParts = null
        faces.clear()
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
