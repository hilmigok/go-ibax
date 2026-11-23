package com.ornek.yzgercekci

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ornek.yzgercekci.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val store = SecretStore(this)
        binding.imageModel.setText(store.imageModel)
        if (store.xaiKey.isNotEmpty()) binding.xaiKey.hint = "•••••••• (kayıtlı — değiştirmek için yaz)"
        binding.xaiModel.setText(store.xaiModel)
        binding.xaiResolution.setText(store.xaiResolution)
        binding.clipSeconds.setText(store.clipSeconds.toString())
        if (store.freesoundKey.isNotEmpty()) binding.freesoundKey.hint = "•••••••• (kayıtlı — değiştirmek için yaz)"

        binding.btnTts.setOnClickListener {
            runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                .onFailure { Toast.makeText(this, R.string.tts_settings_missing, Toast.LENGTH_LONG).show() }
        }
        binding.btnSave.setOnClickListener {
            store.imageModel = binding.imageModel.text?.toString().orEmpty()
            val xk = binding.xaiKey.text?.toString()?.trim().orEmpty()
            if (xk.isNotEmpty()) store.xaiKey = xk
            store.xaiModel = binding.xaiModel.text?.toString().orEmpty()
            store.xaiResolution = binding.xaiResolution.text?.toString().orEmpty()
            binding.clipSeconds.text?.toString()?.trim()?.toIntOrNull()?.let { store.clipSeconds = it }
            val fs = binding.freesoundKey.text?.toString()?.trim().orEmpty()
            if (fs.isNotEmpty()) store.freesoundKey = fs
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
