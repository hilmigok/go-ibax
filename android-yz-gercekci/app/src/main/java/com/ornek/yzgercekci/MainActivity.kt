package com.ornek.yzgercekci

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
    private var seedBase = 0
    private var pickIndex = -1

    /** Bir kareye galeriden görsel seçmek için. */
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val i = pickIndex
        pickIndex = -1
        if (uri != null && i >= 0) useGalleryImage(i, uri)
    }

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

        binding.btnTemplate.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Senaryo şablonu", StoryboardPrompt.TEMPLATE))
            Toast.makeText(this, R.string.template_copied, Toast.LENGTH_LONG).show()
        }
        binding.btnPaste.setOnClickListener {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) binding.prompt.setText(text)
        }

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

    // ---------------------------------------------------------------- üretim

    private fun generateAll() {
        val text = binding.prompt.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            binding.prompt.error = getString(R.string.empty_prompt)
            return
        }
        val store = SecretStore(this)
        val portrait = format.isPortrait
        val captions = binding.captions.isChecked
        val narration = binding.narration.isChecked && binding.sounds.isChecked
        val autoImages = binding.autoImages.isChecked
        val chosenStyle = style
        hideKeyboard()

        job = lifecycleScope.launch {
            setBusy(true, getString(R.string.status_planning), indeterminate = true)
            try {
                var newBoard = withContext(Dispatchers.Default) { ScriptParser.parse(text) }
                if (captions) {
                    // Ekran yazısı yoksa anlatımı altyazı olarak göster (TikTok tarzı).
                    newBoard = newBoard.copy(shots = newBoard.shots.map {
                        if (it.caption.isEmpty() && it.narration.isNotEmpty()) it.copy(caption = it.narration.take(110)) else it
                    })
                }
                seedBase = (System.currentTimeMillis() % 100_000).toInt()
                clearImages()
                board = newBoard
                boardPortrait = portrait
                images.addAll(List(newBoard.shots.size) { null })
                faces.addAll(List(newBoard.shots.size) { emptyList() })
                rebuildThumbs()
                showContent()

                var failures = 0
                var lastError = ""
                binding.status.text = getString(R.string.status_parsed, newBoard.shots.size)
                for (i in newBoard.shots.indices) {
                    if (!autoImages) break
                    setBusy(true, getString(R.string.status_image, i + 1, newBoard.shots.size), indeterminate = false)
                    binding.progress.setProgressCompat(i * 100 / newBoard.shots.size, true)
                    try {
                        images[i] = renderShot(store, newBoard, i, chosenStyle, portrait, seedBase + i)
                    } catch (e: ServiceException) {
                        failures++
                        lastError = e.message.orEmpty()
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
                    val (fitted, pcm, note) = buildSound(store, board ?: newBoard, narration)
                    board = fitted
                    soundtrack = pcm
                    soundNote = note
                    showContent()
                }
                restartPlayback()
                setBusy(false)
                val b = board ?: newBoard
                binding.status.text = if (!autoImages) {
                    getString(R.string.status_need_images)
                } else if (failures == 0) {
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
    private suspend fun buildSound(store: SecretStore, b: Storyboard, narration: Boolean): SoundResult {
        val n = b.shots.size
        val voices = ArrayList<FloatArray?>()
        var voiceError = ""
        val narrator = if (narration && b.shots.any { it.narration.isNotEmpty() }) DeviceNarrator(this) else null
        try {
            if (narrator != null) voiceError = narrator.open().orEmpty()
            for ((i, shot) in b.shots.withIndex()) {
                if (narrator == null || shot.narration.isEmpty() || voiceError.isNotEmpty()) { voices += null; continue }
                binding.status.text = getString(R.string.status_voice, i + 1, n)
                voices += try {
                    narrator.speak(shot.narration, cacheDir)
                } catch (e: Exception) {
                    voiceError = e.message ?: "anlatım üretilemedi"   // bir kez başarısız olursa diğerlerini deneme
                    null
                }
            }
        } catch (e: Exception) {
            voiceError = e.message ?: "metin okuma motoru açılamadı"
            while (voices.size < n) voices += null
        } finally {
            narrator?.close()
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

    /** Tek bir kareyi ücretsiz Pollinations ile üretir. */
    private suspend fun renderShot(
        store: SecretStore, b: Storyboard, i: Int, s: VisualStyle, portrait: Boolean, seed: Int,
        promptOverride: String? = null
    ): Bitmap {
        val prompt = StoryboardPrompt.imagePrompt(promptOverride ?: b.shots[i].prompt, s, portrait)
        val (w, h) = PollinationsClient.size(portrait)
        val client = PollinationsClient(store.imageModel)
        return withContext(Dispatchers.IO) {
            retrying {
                val bytes = client.generate(prompt, w, h, seed)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: throw ServiceException("Görüntü çözülemedi", retryable = true)
            }
        }
    }

    /** Geçici hatalarda (yoğunluk, bağlantı) artan beklemeyle en fazla 4 kez dener. */
    private suspend fun <T> retrying(block: suspend () -> T): T {
        for (wait in longArrayOf(8_000L, 20_000L, 40_000L)) {
            try {
                return block()
            } catch (e: ServiceException) {
                if (!e.retryable) throw e
                delay(wait)
            }
        }
        return block()
    }

    /** Galeriden seçilen görseli kareye koyar (yazılımsal bitmap; video çizimi için gerekli). */
    private fun useGalleryImage(i: Int, uri: Uri) {
        lifecycleScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) {
                    val src = ImageDecoder.createSource(contentResolver, uri)
                    ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val max = maxOf(info.size.width, info.size.height)
                        if (max > 2048) {
                            val k = 2048f / max
                            decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                        }
                    }
                }
                if (i >= images.size) return@launch
                images[i]?.let { old -> binding.preview.post { old.recycle() } }
                images[i] = bmp
                faces[i] = withContext(Dispatchers.Default) { FaceFinder.find(bmp) }
                rebuildThumbs()
                showContent()
                updateButtons()
            } catch (e: Exception) {
                binding.status.text = getString(R.string.error_prefix, getString(R.string.image_load_failed))
            }
        }
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
        dialog.setNegativeButton(R.string.pick_gallery) { _, _ ->
            pickIndex = i
            pickImage.launch("image/*")
        }
        if (images.getOrNull(i) != null) {
            dialog.setNeutralButton(R.string.save_photo) { _, _ -> savePhoto(i) }
        }
        dialog.show()
    }

    private fun regenerate(i: Int, newPrompt: String) {
        val b = board ?: return
        val store = SecretStore(this)
        val chosenStyle = style
        val portrait = boardPortrait
        job = lifecycleScope.launch {
            setBusy(true, getString(R.string.status_image, i + 1, b.shots.size), indeterminate = true)
            try {
                val prompt = newPrompt.ifEmpty { b.shots[i].prompt }
                val seed = (System.nanoTime() % 1_000_000).toInt().let { if (it < 0) -it else it }
                val bmp = renderShot(store, b, i, chosenStyle, portrait, seed, prompt)
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
        for (i in 0 until binding.formatGroup.childCount) binding.formatGroup.getChildAt(i).isEnabled = !busy
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(binding.prompt.windowToken, 0)
    }
}
