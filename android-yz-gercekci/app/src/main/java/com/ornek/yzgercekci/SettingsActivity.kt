package com.ornek.yzgercekci

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
        // Kayıtlı anahtar ekranda gösterilmez; alan boş bırakılırsa mevcut anahtar korunur.
        if (store.apiKey.isNotEmpty()) binding.apiKey.hint = "•••••••• (kayıtlı — değiştirmek için yaz)"
        binding.textModel.setText(store.textModel)
        binding.imageModel.setText(store.imageModel)
        binding.consistency.isChecked = store.consistency
        if (store.freesoundKey.isNotEmpty()) binding.freesoundKey.hint = "•••••••• (kayıtlı — değiştirmek için yaz)"
        binding.ttsModel.setText(store.ttsModel)
        binding.voice.setText(store.voice)

        binding.btnSave.setOnClickListener {
            val newKey = binding.apiKey.text?.toString()?.trim().orEmpty()
            if (newKey.isNotEmpty()) store.apiKey = newKey
            store.textModel = binding.textModel.text?.toString().orEmpty()
            store.imageModel = binding.imageModel.text?.toString().orEmpty()
            store.consistency = binding.consistency.isChecked
            val fs = binding.freesoundKey.text?.toString()?.trim().orEmpty()
            if (fs.isNotEmpty()) store.freesoundKey = fs
            store.ttsModel = binding.ttsModel.text?.toString().orEmpty()
            store.voice = binding.voice.text?.toString().orEmpty()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
